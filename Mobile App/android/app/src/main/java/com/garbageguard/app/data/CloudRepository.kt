package com.garbageguard.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Pi through Firebase, for when the phone is away from the Pi's Wi-Fi.
 * Plain REST against the Realtime Database, following mobile-app/FIREBASE.md,
 * the same way pi/gg_cloud.py talks to it. No Firebase SDK, because the user
 * can type a different database and key at any time.
 *
 * This is the fallback only. It is slower than the local link (state every
 * 3 s, a still picture instead of video, commands answered in about 2 s) and
 * every download counts against a free monthly allowance, so it polls no more
 * than the guide asks and stops the costly parts when nobody is looking.
 */
class CloudRepository(
    private val scope: CoroutineScope,
    /** Reuses the local link's JSON parsing, since /gg/state mirrors /api/state. */
    private val parser: PiRepository,
) : GgRepository {

    @Volatile
    var config = FirebaseConfig()

    /** This phone's id, the same one sent locally as X-GG-Client-Id. */
    @Volatile
    var clientId: String = ""

    /** device, os and app_version for the watcher entry. */
    @Volatile
    var about: Map<String, String> = emptyMap()

    @Volatile
    var foreground: Boolean = true

    /** Where downloaded snapshots are kept between launches. Set once at start-up. */
    @Volatile
    var cacheDir: File? = null

    /**
     * True while the live screen is open. The Pi sends the preview only then,
     * so the watcher entry is rewritten at once when this changes.
     */
    @Volatile
    var liveScreen: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (running && watching) scope.launch { putWatcher() }
        }

    /** Plain-words messages for the user, such as "Pi did not answer." */
    @Volatile
    var onNotice: (String) -> Unit = {}

    private val _state = MutableStateFlow(LiveState(conn = ConnState.NO_PI))
    override val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertRow>>(emptyList())
    override val alerts: StateFlow<List<AlertRow>> = _alerts.asStateFlow()

    private val _battery = MutableStateFlow(Battery())
    override val battery: StateFlow<Battery> = _battery.asStateFlow()

    /** True once the history has really come from Firebase at least once. */
    val loaded = MutableStateFlow(false)

    /** The free-allowance meter from /gg/usage. */
    val usage = MutableStateFlow<CloudUsage?>(null)

    /** The Pi's current local addresses from /gg/pi/local_urls, so the app can find its way back to Wi-Fi. */
    val localUrls = MutableStateFlow<List<String>>(emptyList())

    private var job: Job? = null
    val running: Boolean get() = job?.isActive == true

    private val signIn = Mutex()
    private var token: String? = null
    private var tokenFor: FirebaseConfig? = null
    private var tokenExpires = 0L
    private var uid = ""

    // What the Pi last wrote, kept so the mute countdown can run between updates.
    private var lastState: LiveState? = null
    private var lastStateAt = 0L
    private var piSeenAt = 0L
    private var backgroundSince = 0L
    private var watching = false

    // /gg/pi/alerts_rev changes whenever the Pi changes the history, so the
    // whole list is downloaded only then. Null on a Pi that does not write it.
    private var alertsRev: Long? = null
    private var alertsRevLoaded: Long? = null

    // /gg/state arrives over one streaming connection while the app is in use, so
    // only changes are downloaded. Polling takes over whenever the stream is down.
    private var stream: Job? = null
    @Volatile private var streamConn: HttpURLConnection? = null
    @Volatile private var streamLive = false

    override val frameUrl: String?
        get() = if (running) PREVIEW_URL else null

    override fun snapshotUrl(name: String): String? =
        if (name.startsWith(SNAP_PREFIX)) "$SCHEME" + "snaps/" + name.removePrefix(SNAP_PREFIX) else null

    fun start() {
        if (running || !config.complete) return
        loaded.value = false
        _state.value = LiveState(conn = ConnState.NO_PI)
        backgroundSince = 0L
        job = scope.launch {
            var tick = 0
            while (isActive) {
                // After a minute in the background only the history is still read, slowly,
                // and the watcher entry is removed so the Pi stops sending pictures.
                val away = !foreground
                if (away && backgroundSince == 0L) backgroundSince = System.currentTimeMillis()
                if (!away) backgroundSince = 0L
                val asleep = away && System.currentTimeMillis() - backgroundSince > 60_000

                val slow = tick % (if (asleep) 30 else 10) == 0
                if (slow) pollPi()
                if (asleep) closeStateStream() else openStateStream()
                if (!asleep && !streamLive && tick % 3 == 0) pollState()
                if (slow && (alertsRev == null || alertsRev != alertsRevLoaded)) pollAlerts()
                if (!asleep && tick % 10 == 0) putWatcher()
                if (asleep && watching) deleteWatcher()
                if (!asleep && tick % 60 == 0) pollUsage()
                countDown()
                tick++
                delay(1000)
            }
        }
    }

    fun stop() {
        val wasWatching = watching
        job?.cancel()
        job = null
        closeStateStream()
        if (wasWatching) scope.launch { deleteWatcher() }
    }

    // ---------- reading ----------

    private suspend fun pollPi() {
        // With no internet this read fails. That must never stop the app; the
        // state poll is what reports the lost link.
        val pi = try {
            getObject("gg/pi")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        piSeenAt = pi.optLong("ts")
        alertsRev = if (pi.has("alerts_rev")) pi.optLong("alerts_rev") else null
        val urls = pi.optJSONArray("local_urls")
        if (urls != null) localUrls.value = (0 until urls.length()).map { urls.optString(it) }.filter { it.startsWith("http") }
    }

    private suspend fun pollState() {
        val o = try {
            getObject("gg/state")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(conn = ConnState.NO_PI) }
            return
        }
        if (o == null) {
            _state.update { it.copy(conn = ConnState.NO_PI) }
            return
        }
        applyState(o)
    }

    private fun applyState(o: JSONObject) {
        lastState = parser.parseState(o)
        lastStateAt = o.optLong("ts", System.currentTimeMillis())
        _battery.value = o.optJSONObject("battery")?.let { parser.parseBattery(it) } ?: Battery()
        countDown()
    }

    private fun openStateStream() {
        if (stream?.isActive == true) return
        stream = scope.launch(Dispatchers.IO) {
            var fails = 0
            while (isActive) {
                try {
                    listenState()
                    fails = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fails++
                }
                streamLive = false
                delay((2_000L * (fails + 1)).coerceAtMost(30_000L))
            }
        }
    }

    private fun closeStateStream() {
        stream?.cancel()
        stream = null
        streamConn?.disconnect()
        streamConn = null
        streamLive = false
    }

    /**
     * Firebase's streaming REST API: the whole node once ("put" at "/"), then
     * only what changed, plus a keep-alive line every 30 s. Returns when the
     * connection ends or the sign-in token runs out, so the caller reconnects.
     */
    private suspend fun listenState() {
        val url = "${config.url.trimEnd('/')}/gg/state.json?auth=${enc(auth())}"
        val conn = URL(url).openConnection() as HttpURLConnection
        streamConn = conn
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.connectTimeout = 8000
        conn.readTimeout = 75_000
        try {
            if (conn.responseCode != 200) throw IOException("stream HTTP ${conn.responseCode}")
            var mirror = JSONObject()
            var event = ""
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: return
                    when {
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") && (event == "put" || event == "patch") -> {
                            val msg = JSONObject(line.removePrefix("data:").trim())
                            val key = msg.optString("path", "/").trim('/').substringBefore('/')
                            val data = msg.opt("data")
                            if (event == "put" && key.isEmpty()) {
                                mirror = data as? JSONObject ?: JSONObject()
                            } else if (key.isEmpty() && data is JSONObject) {
                                data.keys().forEach { mirror.put(it, data.get(it)) }
                            } else if (key.isNotEmpty()) {
                                // The Pi always writes the whole state, so deeper paths are rare; refetch them whole.
                                mirror = getObject("gg/state") ?: mirror
                            }
                            val copy = JSONObject(mirror.toString())
                            withContext(Dispatchers.Main) {
                                if (copy.length() == 0) _state.update { it.copy(conn = ConnState.NO_PI) } else applyState(copy)
                            }
                            streamLive = true
                        }
                        line.startsWith("data:") && (event == "cancel" || event == "auth_revoked") -> return
                    }
                }
            }
        } finally {
            conn.disconnect()
            if (streamConn === conn) streamConn = null
        }
    }

    /**
     * Publishes the last state with the mute time counted down from when the
     * Pi wrote it, and marks the Pi offline when its heartbeat is over 90 s old.
     */
    private fun countDown() {
        val base = lastState ?: return
        val now = System.currentTimeMillis()
        val piGone = piSeenAt > 0 && now - piSeenAt > 90_000
        val left = base.muteLeft?.let { if (it > 0) (it - ((now - lastStateAt) / 1000).toInt()).coerceAtLeast(0) else it }
        _state.value = base.copy(
            conn = if (piGone) ConnState.NO_PI else base.conn,
            muteLeft = left,
            piOfflineSince = if (piGone) CLOCK.format(Instant.ofEpochMilli(piSeenAt).atZone(ZoneId.systemDefault())) else null,
        )
    }

    private suspend fun pollAlerts() {
        try {
            val rev = alertsRev
            val raw = rest("GET", "gg/alerts", query = "&orderBy=" + enc("\"\$key\"") + "&limitToLast=200")
            _alerts.value = if (raw == null) emptyList() else parseAlertNode(raw)
            alertsRevLoaded = rev
            pruneCache(_alerts.value.map { it.id }.toSet())
            loaded.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the last list; the state poll reports a lost link.
        }
    }

    /**
     * /gg/alerts is keyed by row id. Firebase hands such a node back as an
     * object, or as an array with gaps when the ids run close to 0, 1, 2.
     */
    private fun parseAlertNode(raw: String): List<AlertRow> {
        val rows = JSONArray()
        val hasSnap = HashMap<Long, Boolean>()
        val hasClean = HashMap<Long, Boolean>()
        fun take(o: JSONObject?) {
            if (o == null) return
            rows.put(o)
            hasSnap[o.optLong("id")] = o.optBoolean("has_snap", false)
            hasClean[o.optLong("id")] = o.optBoolean("has_clean", false)
        }
        if (raw.trimStart().startsWith("[")) {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) take(a.optJSONObject(i))
        } else {
            val o = JSONObject(raw)
            o.keys().forEach { take(o.optJSONObject(it)) }
        }
        // The picture is not the Pi's file here but a compressed copy under /gg/snaps/<id>.
        return parser.parseAlerts(rows)
            // Clean copies are never uploaded: the row only says one is on the Pi.
            .map { it.copy(snapshot = if (hasSnap[it.id] == true) SNAP_PREFIX + it.id else null, clean = null, hasClean = hasClean[it.id] == true) }
            .sortedByDescending { it.id }
    }

    private suspend fun pollUsage() {
        try {
            usage.value = parser.parseUsage(getObject("gg/usage"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * A picture stored in the database as base64: "preview", "snaps/<id>" or
     * "snaps/<id>/thumb". Snapshots never change, so they are kept on disk and
     * downloaded once. A snapshot from before thumbnails existed falls back to
     * the full picture.
     */
    suspend fun image(path: String): Bitmap? = try {
        val file = if (path.startsWith("snaps/")) cacheDir?.let { File(it, path.replace('/', '_') + ".jpg") } else null
        val cached = file?.takeIf { it.exists() }?.let { f -> withContext(Dispatchers.IO) { f.readBytes() } }
        val bytes = cached ?: getObject("gg/$path")?.optString("jpg").orEmpty().takeIf { it.isNotEmpty() }
            ?.let { Base64.decode(it, Base64.DEFAULT) }
        if (bytes != null && cached == null && file != null) withContext(Dispatchers.IO) { saveToCache(file, bytes) }
        when {
            bytes != null -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            path.endsWith("/thumb") -> image(path.removeSuffix("/thumb"))
            else -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun saveToCache(file: File, bytes: ByteArray) = runCatching {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        val files = file.parentFile?.listFiles()?.sortedBy { it.lastModified() } ?: return@runCatching
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /** Drops cached snapshots of history rows that no longer exist, since a row id can come back. */
    private suspend fun pruneCache(ids: Set<Long>) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir?.listFiles()?.forEach { f ->
                val id = f.name.removePrefix("snaps_").substringBefore('_').substringBefore('.').toLongOrNull()
                if (id != null && id !in ids) f.delete()
            }
        }
    }

    // ---------- telling the Pi a phone is watching ----------

    private suspend fun putWatcher() {
        if (clientId.isEmpty()) return
        val body = JSONObject()
            .put("ts", JSONObject().put(".sv", "timestamp"))
            .put("device", about["device"].orEmpty())
            .put("os", about["os"].orEmpty())
            .put("app_version", about["app_version"].orEmpty())
            .put("mode", if (foreground) "foreground" else "background")
            .put("screen", if (liveScreen && foreground) "live" else "other")
        try {
            rest("PUT", "gg/watchers/$clientId", body.toString(), query = "&print=silent")
            watching = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun deleteWatcher() {
        watching = false
        if (clientId.isEmpty()) return
        try {
            rest("DELETE", "gg/watchers/$clientId", query = "&print=silent")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // ---------- commands ----------

    /**
     * Writes a command for the Pi and waits for its answer. The Pi checks
     * every 2 s while a phone watches and every 10 s otherwise, so this takes
     * a moment; fifteen seconds without an answer is reported rather than
     * left hanging.
     */
    private suspend fun command(cmd: String, args: JSONObject): JSONObject? {
        val body = JSONObject()
            .put("cmd", cmd)
            .put("args", args)
            .put("ts", JSONObject().put(".sv", "timestamp"))
            .put("by", about["device"].orEmpty().ifEmpty { "Android app" })
        val pushId = try {
            JSONObject(rest("POST", "gg/commands", body.toString()) ?: "{}").optString("name")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
        if (pushId.isEmpty()) {
            onNotice("Could not send that through Firebase.")
            commandFailed()
            return null
        }
        repeat(15) {
            delay(1000)
            val result = try {
                getObject("gg/results/$pushId")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result != null) {
                if (!result.optBoolean("ok", true)) {
                    onNotice("The Pi could not do that.")
                    commandFailed()
                }
                return result
            }
        }
        onNotice("Pi did not answer.")
        commandFailed()
        return null
    }

    override suspend fun testConnection(address: String): Result<Long> = test(config).map { 0L }

    override suspend fun mute(seconds: Int) {
        command("mute", JSONObject().put("seconds", seconds))
        pollState()
        pollAlerts()
    }

    override suspend fun unmute() {
        command("mute", JSONObject().put("cancel", true))
        pollState()
        pollAlerts()
    }

    override suspend fun setAutoMute(on: Boolean?, seconds: Int?) {
        val args = JSONObject()
        if (on != null) args.put("on", on)
        if (seconds != null) args.put("seconds", seconds)
        command("auto_mute", args)
        pollState()
    }

    override suspend fun saveSettings(confPercent: Int, threshold: Int) {
        command("settings", JSONObject().put("conf", confPercent).put("threshold", threshold))
        pollState()
    }

    override suspend fun setRoi(points: List<RoiPoint>) {
        val array = JSONArray()
        points.forEach { array.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
        command("roi", JSONObject().put("points", array))
        pollState()
    }

    override suspend fun testAlert() {
        command("test_alert", JSONObject())
        pollAlerts()
    }

    override suspend fun capture() {
        command("capture", JSONObject())
        pollAlerts()
    }

    override suspend fun markRead(ids: List<Long>?, read: Boolean) {
        val args = JSONObject()
        if (ids == null) args.put("all", true) else args.put("ids", JSONArray(ids))
        if (!read) args.put("read", false)
        // Shown at once; the Pi's own copy follows within a few seconds.
        _alerts.update { rows -> rows.map { if (ids == null || it.id in ids) it.copy(read = read) else it } }
        command("alerts_read", args)
    }

    override suspend fun deleteAlerts(ids: List<Long>) {
        command("alerts_delete", JSONObject().put("ids", JSONArray(ids)))
        pollAlerts()
    }

    override suspend fun deleteRange(from: String, to: String) {
        val args = JSONObject()
        if (from.isNotBlank()) args.put("from", from.trim())
        if (to.isNotBlank()) args.put("to", to.trim())
        command("alerts_delete", args)
        pollAlerts()
    }

    override suspend fun deleteAll() {
        command("alerts_delete", JSONObject().put("all", true))
        pollAlerts()
    }

    override suspend fun reconnectCamera() {
        command("camera_reconnect", JSONObject())
        pollState()
    }

    /** Restart is deliberately not offered over Firebase. */
    override suspend fun restart() {
        onNotice("Restart only works on the Pi's own Wi-Fi.")
        commandFailed()
    }

    // ---------- Pi power, the same commands the Pi's own endpoints take ----------

    override suspend fun setScreenSaver(mode: String?, timeoutS: Int?, timeoutBatteryS: Int?, dimPct: Int?, wakeOnAlert: Boolean?) {
        val args = JSONObject()
        if (mode != null) args.put("mode", mode)
        if (timeoutS != null) args.put("timeout_s", timeoutS)
        if (timeoutBatteryS != null) args.put("timeout_battery_s", timeoutBatteryS)
        if (dimPct != null) args.put("dim_pct", dimPct)
        if (wakeOnAlert != null) args.put("wake_on_alert", wakeOnAlert)
        command("screen_saver", args)
        pollState()
    }

    override suspend fun screen(action: String) {
        command("screen", JSONObject().put("action", action))
        pollState()
    }

    override suspend fun setAutoShutdown(on: Boolean?, pct: Int?) {
        val args = JSONObject()
        if (on != null) args.put("on", on)
        if (pct != null) args.put("pct", pct)
        command("auto_shutdown", args)
        pollState()
    }

    override suspend fun cancelShutdown(): Boolean {
        val result = command("auto_shutdown", JSONObject().put("cancel", true))
        pollState()
        return result?.optBoolean("ok", false) == true
    }

    override suspend fun shutdown() {
        command("shutdown", JSONObject().put("confirm", true))
        pollState()
        pollAlerts()
    }

    // ---------- Pi buzzer ----------

    /**
     * Folds a command's answer into the last state. The Pi answers a buzzer
     * or vibration command with the new settings, so the screen can show them
     * without reading the state node again. False when the answer had none.
     */
    private fun applyBuzzer(result: JSONObject?): Boolean {
        val base = lastState ?: return false
        val old = base.buzzer ?: return false
        if (result == null || !(result.has("buzzer_on") || result.has("vibe_on"))) return false
        lastState = base.copy(buzzer = parser.parseBuzzer(result, old))
        countDown()
        return true
    }

    override suspend fun setBuzzer(changes: Map<String, Any>) {
        if (!applyBuzzer(command("buzzer", JSONObject(changes)))) pollState()
    }

    override suspend fun setVibe(on: Boolean?, strength: String?) {
        val args = JSONObject()
        if (on != null) args.put("on", on)
        if (strength != null) args.put("strength", strength)
        if (!applyBuzzer(command("vibe", args))) pollState()
    }

    /** Nothing changes on the Pi, so nothing is read back afterwards. */
    override suspend fun testBuzzer(sound: String) {
        command("buzzer_test", JSONObject().put("sound", sound))
    }

    /** The Pi has no Firebase command for this one. */
    override suspend fun testVibe() {
        onNotice("Test vibration only works on the Pi's own Wi-Fi.")
        commandFailed()
    }

    override suspend fun setClean(mode: String) {
        val result = command("clean", JSONObject().put("mode", mode))
        val base = lastState
        val clean = if (result == null) null else parser.parseClean(result)
        if (base != null && clean != null) {
            lastState = base.copy(clean = clean)
            countDown()
        } else {
            pollState()
        }
    }

    /** The Pi's network screen is local only. */
    override suspend fun networkDetails(): NetDetails? = null

    // ---------- sign-in and REST ----------

    /**
     * Signs in with [cfg] and reads the account's line in /gg_users, the same
     * check the Pi's Test button makes. Success carries the role ("app").
     */
    suspend fun test(cfg: FirebaseConfig): Result<String> = try {
        val (idToken, localId) = signInWith(cfg)
        val who = call(cfg.url, "GET", "gg_users/$localId", idToken)
        if (who == null) Result.failure(IOException("The account is not on the allowed list (gg_users)."))
        else Result.success(who.trim('"'))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IOException(friendly(e.message.orEmpty())))
    }

    private suspend fun signInWith(cfg: FirebaseConfig): Pair<String, String> {
        val body = JSONObject().put("email", cfg.email.trim()).put("password", cfg.password).put("returnSecureToken", true)
        val text = http("POST", authUrl(cfg), body.toString())
        val o = JSONObject(text)
        tokenExpires = System.currentTimeMillis() + (o.optString("expiresIn", "3600").toLongOrNull() ?: 3600L) * 1000
        return o.getString("idToken") to o.optString("localId")
    }

    /** A token for the saved settings, renewed when under five minutes remain or the settings changed. */
    private suspend fun auth(): String = signIn.withLock {
        val cfg = config
        val current = token
        if (current != null && tokenFor == cfg && System.currentTimeMillis() < tokenExpires - 300_000) return@withLock current
        val (fresh, localId) = signInWith(cfg)
        token = fresh
        tokenFor = cfg
        uid = localId
        fresh
    }

    private suspend fun getObject(path: String): JSONObject? {
        val raw = rest("GET", path) ?: return null
        return if (raw.trimStart().startsWith("{")) JSONObject(raw) else null
    }

    private suspend fun rest(method: String, path: String, body: String? = null, query: String = ""): String? =
        call(config.url, method, path, auth(), body, query)

    private suspend fun call(db: String, method: String, path: String, idToken: String, body: String? = null, query: String = ""): String? {
        val text = http(method, "${db.trimEnd('/')}/$path.json?auth=${enc(idToken)}$query", body)
        return text.takeIf { it.isNotBlank() && it.trim() != "null" }
    }

    private suspend fun http(method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        // No disconnect() once the answer is read: the connection goes back to the pool and
        // the next request skips the TLS handshake, which Firebase bills as a download.
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        try {
            if (body != null) {
                val bytes = body.toByteArray()
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException(errorOf(text, code))
            text
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        }
    }

    /** Firebase reports errors as {"error": "text"} or {"error": {"message": "CODE"}}. */
    private fun errorOf(text: String, code: Int): String {
        val o = runCatching { JSONObject(text) }.getOrNull()
        val inner = o?.optJSONObject("error")?.optString("message").orEmpty()
        val flat = if (o != null && o.optJSONObject("error") == null) o.optString("error") else ""
        return inner.ifEmpty { flat }.ifEmpty { "HTTP $code" }
    }

    /**
     * Sign-in goes to Google's identity service. A plain-http database address
     * can only be a stand-in used while developing (real Firebase is always
     * https, and the settings page rejects anything else), so for those the
     * sign-in is sent to the same stand-in.
     */
    private fun authUrl(cfg: FirebaseConfig): String =
        if (cfg.url.startsWith("http://")) "${cfg.url.trimEnd('/')}/__auth/signInWithPassword?key=${enc(cfg.key)}"
        else "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${enc(cfg.key.trim())}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val SCHEME = "gg-cloud://"
        const val PREVIEW_URL = "gg-cloud://preview"
        private const val CACHE_BYTES = 64L * 1024 * 1024
        private const val SNAP_PREFIX = "cloud:"
        private val CLOCK = DateTimeFormatter.ofPattern("H:mm")

        /** Firebase's error codes in plain words, as listed in FIREBASE.md. */
        fun friendly(message: String): String = when {
            "INVALID_LOGIN_CREDENTIALS" in message || "INVALID_PASSWORD" in message || "EMAIL_NOT_FOUND" in message ||
                "INVALID_EMAIL" in message -> "Wrong email or password."
            "API key not valid" in message || "API_KEY_INVALID" in message -> "The Web API key is wrong."
            "PASSWORD_LOGIN_DISABLED" in message || "OPERATION_NOT_ALLOWED" in message ->
                "Email/Password sign-in is off in Firebase Authentication."
            "TOO_MANY_ATTEMPTS_TRY_LATER" in message -> "Too many tries, wait a few minutes."
            "USER_DISABLED" in message -> "This account is disabled."
            "Permission denied" in message || "HTTP 401" in message ->
                "The account is not on the allowed list (gg_users), or the rules were not pasted."
            "HTTP 404" in message || "URL of your Firebase" in message -> "The database URL is wrong."
            "gg_users" in message -> message
            "Unable to resolve host" in message || "failed to connect" in message || "timeout" in message.lowercase() ->
                "No internet, or the database URL is wrong."
            else -> "Could not sign in. ${message.take(80)}"
        }

        /** The checks from the settings table in FIREBASE.md. Null means every field looks right. */
        fun problemWith(cfg: FirebaseConfig): String? {
            val url = cfg.url.trim()
            return when {
                !url.startsWith("https://") -> "The database URL must start with https://"
                url.endsWith("/") -> "Remove the / at the end of the database URL."
                !(url.endsWith(".firebasedatabase.app") || url.endsWith(".firebaseio.com")) ->
                    "The database URL should end with .firebasedatabase.app or .firebaseio.com"
                !cfg.key.trim().startsWith("AIza") || cfg.key.trim().length != 39 ->
                    "The Web API key starts with AIza and is 39 characters long."
                !Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(cfg.email.trim()) -> "That does not look like an email."
                cfg.password.length < 6 -> "The password needs at least 6 characters."
                else -> null
            }
        }
    }
}
