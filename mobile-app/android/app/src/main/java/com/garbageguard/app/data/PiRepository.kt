package com.garbageguard.app.data

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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real Pi over the local network. Plain HTTP to the server in
 * pi/gg_web.py, following mobile-app/GarbageGuard_API.md. The state is
 * polled once a second and the history every few seconds, so the app and
 * the touchscreen stay in step: whatever one changes, the other shows on
 * its next poll.
 */
class PiRepository(private val scope: CoroutineScope) : GgRepository {

    @Volatile
    private var baseUrl: String = ""
    private var job: Job? = null
    private var failures = 0

    /** The address as the user typed it. */
    var address: String = ""
        private set

    /** True once the history has really come from the Pi at least once. */
    val loaded = MutableStateFlow(false)

    val running: Boolean get() = job?.isActive == true

    private val _state = MutableStateFlow(LiveState(conn = ConnState.NO_PI))
    override val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertRow>>(emptyList())
    override val alerts: StateFlow<List<AlertRow>> = _alerts.asStateFlow()

    private val _battery = MutableStateFlow(Battery())
    override val battery: StateFlow<Battery> = _battery.asStateFlow()

    override val frameUrl: String?
        get() = if (baseUrl.isEmpty()) null else "$baseUrl/frame.jpg"

    override fun snapshotUrl(name: String): String? =
        if (baseUrl.isEmpty()) null else "$baseUrl/snapshots/$name"

    /** Starts polling the Pi at [address], for example 192.168.1.101:8080. */
    fun start(address: String) {
        val base = toBaseUrl(address) ?: return
        if (base != baseUrl) {
            loaded.value = false
            _alerts.value = emptyList()
            _state.value = LiveState(conn = ConnState.NO_PI)
            _battery.value = Battery()
        }
        baseUrl = base
        this.address = address.trim()
        failures = 0
        job?.cancel()
        job = scope.launch {
            var tick = 0
            while (isActive) {
                pollState()
                if (tick % ALERTS_EVERY == 0) refreshAlerts()
                if (tick % 2 == 0) pollBattery()
                tick++
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** One immediate poll, for the Retry button. Returns true when the Pi answered. */
    suspend fun refreshNow(): Boolean {
        pollState()
        refreshAlerts()
        return _state.value.conn != ConnState.NO_PI
    }

    private suspend fun pollState() {
        try {
            _state.value = parseState(JSONObject(request("GET", "/api/state")))
            failures = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // One dropped poll is normal on Wi-Fi; two in a row means the link is down.
            if (++failures >= 2) _state.update { it.copy(conn = ConnState.NO_PI) }
        }
    }

    /** An older Pi without the endpoint, or one with no UPS, simply reads as "no battery". */
    private suspend fun pollBattery() {
        _battery.value = try {
            parseBattery(JSONObject(request("GET", "/api/battery")))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Battery()
        }
    }

    private fun parseBattery(o: JSONObject): Battery {
        if (!o.optBoolean("ok", false)) return Battery()
        val cells = o.optJSONArray("cells_v")
        fun f(key: String): Float? = if (o.isNull(key)) null else o.optDouble(key).toFloat()
        return Battery(
            ok = true,
            percent = o.optInt("percent").coerceIn(0, 100),
            voltage = o.optDouble("voltage_v", 0.0).toFloat(),
            current = o.optDouble("current_a", 0.0).toFloat(),
            power = o.optDouble("power_w", 0.0).toFloat(),
            onAc = o.optBoolean("on_ac", false),
            charging = o.optBoolean("charging", false),
            minutesLeft = if (o.isNull("minutes_left")) null else o.optInt("minutes_left"),
            capacityMah = if (o.isNull("capacity_mah")) null else o.optInt("capacity_mah"),
            cells = if (cells == null) emptyList() else (0 until cells.length()).map { cells.optDouble(it, 0.0).toFloat() },
            inputV = f("vbus_v"), inputA = f("vbus_a"), inputW = f("vbus_w"),
        )
    }

    private suspend fun refreshAlerts() {
        try {
            val rows = JSONObject(request("GET", "/api/alerts?limit=$ALERT_LIMIT")).optJSONArray("alerts")
            _alerts.value = parseAlerts(rows ?: JSONArray())
            loaded.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the last list; the state poll reports a lost link.
        }
    }

    // ---------- GgRepository ----------

    override suspend fun testConnection(address: String): Result<Long> {
        val base = toBaseUrl(address)
            ?: return Result.failure(IOException("That does not look like an address. Use the form 192.168.1.101:8080."))
        return try {
            val started = System.nanoTime()
            val reply = JSONObject(request("GET", "/api/state", base = base))
            if (!reply.has("smoothed")) throw IOException("not a Garbage-Guard server")
            Result.success((System.nanoTime() - started) / 1_000_000)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("GgPi", "test failed for $base", e)
            Result.failure(
                IOException("Could not reach that address. The phone and the Pi must be on the same network or hotspot.")
            )
        }
    }

    override suspend fun mute(seconds: Int) {
        post("/api/mute", JSONObject().put("seconds", seconds))
        pollState()
        refreshAlerts()
    }

    override suspend fun unmute() {
        post("/api/mute", JSONObject().put("cancel", true))
        pollState()
        refreshAlerts()
    }

    override suspend fun setAutoMute(on: Boolean?, seconds: Int?) {
        val body = JSONObject()
        if (on != null) body.put("on", on)
        if (seconds != null) body.put("seconds", seconds)
        post("/api/auto_mute", body)
        pollState()
    }

    override suspend fun saveSettings(confPercent: Int, threshold: Int) {
        post("/api/settings", JSONObject().put("conf", confPercent).put("threshold", threshold))
        pollState()
    }

    override suspend fun setRoi(points: List<RoiPoint>) {
        val array = JSONArray()
        points.forEach { array.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
        post("/api/roi", JSONObject().put("points", array))
        pollState()
    }

    override suspend fun testAlert() {
        post("/api/test_alert", null)
        refreshAlerts()
    }

    override suspend fun capture() {
        post("/api/capture", null)
        refreshAlerts()
    }

    override suspend fun markRead(ids: List<Long>?, read: Boolean) {
        val body = JSONObject()
        if (ids == null) body.put("all", true) else body.put("ids", JSONArray(ids))
        if (!read) body.put("read", false)
        post("/api/alerts/read", body)
        refreshAlerts()
    }

    override suspend fun deleteAlerts(ids: List<Long>) {
        post("/api/alerts/delete", JSONObject().put("ids", JSONArray(ids)))
        refreshAlerts()
    }

    override suspend fun deleteRange(from: String, to: String) {
        val body = JSONObject()
        if (from.isNotBlank()) body.put("from", from.trim())
        if (to.isNotBlank()) body.put("to", to.trim())
        post("/api/alerts/delete", body)
        refreshAlerts()
    }

    override suspend fun deleteAll() {
        post("/api/alerts/delete", JSONObject().put("all", true))
        refreshAlerts()
    }

    // ---------- HTTP ----------

    /** A failed command must not crash the app; the next poll shows the truth. */
    private suspend fun post(path: String, body: JSONObject?) {
        try {
            request("POST", path, body ?: JSONObject())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        base: String = baseUrl,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 2500
            conn.readTimeout = 4000
            if (body != null) {
                val bytes = body.toString().toByteArray()
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- JSON, field names as in GarbageGuard_API.md ----------

    private fun parseState(o: JSONObject): LiveState {
        val counts = o.optJSONObject("counts") ?: JSONObject()
        val roi = o.optJSONArray("roi") ?: JSONArray()
        val points = (0 until roi.length()).mapNotNull { i ->
            val p = roi.optJSONArray(i) ?: return@mapNotNull null
            RoiPoint(p.optDouble(0, 0.0).toFloat(), p.optDouble(1, 0.0).toFloat())
        }
        val threshold = o.optInt("threshold", 20)
        return LiveState(
            conn = if (o.optBoolean("connected", false)) ConnState.ONLINE else ConnState.NO_CAMERA,
            bottle = counts.optInt("bottle"),
            bag = counts.optInt("bag"),
            foodContainer = counts.optInt("food_container"),
            raw = o.optInt("raw"),
            smoothed = o.optInt("smoothed"),
            threshold = threshold,
            rearm = o.optInt("rearm", rearmFor(threshold)),
            conf = o.optDouble("conf", 0.40).toFloat(),
            roi = points,
            latencyMs = o.optDouble("latency_ms", 0.0).toFloat(),
            avgLatencyMs = o.optDouble("avg_latency_ms", 0.0).toFloat(),
            fps = o.optDouble("fps", 0.0).toFloat(),
            camFps = o.optDouble("cam_fps", 0.0).toFloat(),
            alertActive = o.optBoolean("alert_active", false),
            muted = o.optBoolean("muted", false),
            muteLeft = if (o.isNull("mute_left")) (if (o.has("mute_left")) null else 0) else o.optInt("mute_left"),
            muteTotal = o.optInt("mute_total"),
            autoMute = o.optBoolean("auto_mute", false),
            autoMuteS = o.optInt("auto_mute_s", 900),
            autoMuted = o.optBoolean("auto_muted", false),
        )
    }

    private fun parseAlerts(rows: JSONArray): List<AlertRow> =
        (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            AlertRow(
                id = o.optLong("id"),
                timestamp = o.optString("timestamp").replace('T', ' '),
                event = EventType.fromApi(o.optString("event")),
                smoothedCount = o.optInt("smoothed_count"),
                nBottle = o.optInt("n_bottle"),
                nBag = o.optInt("n_bag"),
                nFoodContainer = o.optInt("n_food_container"),
                peakConfidence = if (o.isNull("peak_confidence")) null else o.optDouble("peak_confidence").toFloat(),
                thresholdUsed = o.optInt("threshold_used"),
                read = when (val r = o.opt("read")) {
                    is Boolean -> r
                    is Number -> r.toInt() != 0
                    else -> false
                },
                snapshot = if (o.isNull("snapshot")) null else o.optString("snapshot").ifEmpty { null },
            )
        }

    companion object {
        private const val ALERTS_EVERY = 3 // seconds between history refreshes
        private const val ALERT_LIMIT = 200

        /** "192.168.1.101", "192.168.1.101:8080" or a full http:// URL. Null when it cannot be an address. */
        fun toBaseUrl(address: String): String? {
            val bare = address.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            val m = Regex("""^([A-Za-z0-9.\-]+)(?::(\d{1,5}))?$""").find(bare) ?: return null
            val host = m.groupValues[1]
            if (host.isEmpty() || !host.any { it.isLetterOrDigit() }) return null
            val port = m.groupValues[2].ifEmpty { "8080" }
            return "http://$host:$port"
        }
    }
}
