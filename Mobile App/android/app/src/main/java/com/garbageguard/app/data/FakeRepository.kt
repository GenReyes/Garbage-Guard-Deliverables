package com.garbageguard.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * In-memory stand-in for the Pi. Counts rise and fall on a slow wave so an
 * alert fires about once every 90 seconds, which is enough to demo the
 * alert, mute and auto-mute paths. Follows the same rules as the real
 * server: no accumulation row while muted, and a mute lifts itself when
 * the count drops below the re-arm value.
 */
class FakeRepository(private val scope: CoroutineScope) : GgRepository {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var nextId = 100L
    private var tick = 0
    private val recentRaw = ArrayDeque<Int>()

    private val _state = MutableStateFlow(
        LiveState(
            screenMode = "off",
            buzzer = Buzzer(),
            clean = CleanCopies(mode = "all", count = 19, bytes = 19L * 61_000),
            roi = listOf(
                RoiPoint(0.10f, 0.18f), RoiPoint(0.82f, 0.12f), RoiPoint(0.94f, 0.78f),
                RoiPoint(0.55f, 0.92f), RoiPoint(0.08f, 0.82f),
            ),
        )
    )
    override val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _alerts = MutableStateFlow(seedHistory())
    override val alerts: StateFlow<List<AlertRow>> = _alerts.asStateFlow()

    private val _battery = MutableStateFlow(Battery())
    override val battery: StateFlow<Battery> = _battery.asStateFlow()

    /** A pack that runs down for three minutes, then charges for three. */
    private fun stepBattery() {
        val phase = (tick / 180) % 2
        val f = (tick % 180) / 180f
        val charging = phase == 1
        val percent = if (charging) (58 + 30 * f).roundToInt() else (88 - 30 * f).roundToInt()
        val volts = 14.2f + 2.4f * percent / 100f
        val amps = if (charging) 1.15f + 0.1f * sin(tick / 3.0).toFloat() else -0.78f - 0.08f * sin(tick / 3.0).toFloat()
        val cell = volts / 4f
        _battery.value = withPower(Battery(
            ok = true, percent = percent, voltage = volts, current = amps, power = volts * amps,
            onAc = charging, charging = charging,
            minutesLeft = if (charging) ((100 - percent) * 2.1f).roundToInt() else (percent * 4.3f).roundToInt(),
            capacityMah = 5000 * percent / 100,
            cells = listOf(cell, cell + 0.004f, cell - 0.006f, cell + 0.002f),
            inputV = if (charging) 15.1f else 0f, inputA = if (charging) 2.2f else 0f, inputW = if (charging) 33.2f else 0f,
        ))
    }

    init {
        scope.launch {
            while (isActive) {
                step()
                delay(1000)
            }
        }
    }

    // ---------- simulation ----------

    private fun step() {
        tick++
        val s = _state.value
        val wave = s.threshold * (0.6 + 0.55 * sin(tick / 14.0))
        val raw = if (simulating) simCount else (wave + Random.nextInt(-2, 3)).roundToInt().coerceAtLeast(0)
        recentRaw.addLast(raw)
        if (recentRaw.size > 5) recentRaw.removeFirst()
        val smoothed = recentRaw.sorted()[recentRaw.size / 2]

        val bottle = (smoothed * 0.45).roundToInt()
        val bag = (smoothed * 0.30).roundToInt()
        val food = (smoothed - bottle - bag).coerceAtLeast(0)

        var next = s.copy(
            raw = raw, smoothed = smoothed,
            bottle = bottle, bag = bag, foodContainer = food,
            latencyMs = 60f + Random.nextFloat() * 15f,
            avgLatencyMs = 67f + Random.nextFloat() * 2f,
            fps = 14.2f + Random.nextFloat() * 0.8f,
            camFps = 14.8f + Random.nextFloat() * 0.3f,
            viewerApps = if (simSecondPhone) 2 else 1,
            viewers = listOf(
                Viewer(
                    ip = "192.168.1.23", kind = "app", id = "demo", device = "This phone (demo)",
                    os = "Android", appVersion = "demo", mode = "foreground", connectedSeconds = tick,
                )
            ) + if (simSecondPhone) listOf(
                Viewer(
                    ip = "192.168.1.31", kind = "app", id = "demo2", device = "Second phone (simulated)",
                    os = "Android", appVersion = "demo", mode = "background", connectedSeconds = tick - simPhoneSince,
                )
            ) else emptyList(),
        )

        // Mute countdown.
        if (next.muted && next.muteLeft != null) {
            val left = next.muteLeft!! - 1
            next = if (left <= 0) {
                log(EventType.UNMUTED, next)
                next.copy(muted = false, muteLeft = 0, muteTotal = 0, autoMuted = false)
            } else next.copy(muteLeft = left)
        }

        // Alert raise and clear.
        if (!next.alertActive && smoothed >= next.threshold) {
            next = next.copy(alertActive = true)
            if (!next.muted) {
                log(EventType.ACCUMULATION, next, snapshot = true)
                if (next.autoMute) {
                    next = muted(next, next.autoMuteS, auto = true)
                    log(EventType.AUTO_MUTED, next)
                }
            }
        } else if (next.alertActive && smoothed < next.rearm) {
            next = next.copy(alertActive = false)
            if (next.muted) {
                log(EventType.UNMUTED, next)
                next = next.copy(muted = false, muteLeft = 0, muteTotal = 0, autoMuted = false)
            }
        }
        // log() may have counted a new clean copy while this step was being built.
        _state.value = next.copy(clean = _state.value.clean)
        if (simulating) simBattery() else stepBattery()
    }

    // ---------- simulation: the demo driven by hand ----------

    /**
     * True while Simulation is on. The slow wave and the battery cycle stop,
     * and the Simulation card in Settings moves the pretend Pi instead: the
     * count, the camera and the Pi going away, the battery and its countdown,
     * the Pi's screen, the buzzer, a second phone. Everything else follows
     * the same rules as with the wave, so each change raises the same alerts,
     * sounds and vibrations a real Pi would.
     */
    var simulating = false
        private set
    private var simCount = 0
    private var simPercent = 72
    private var simCharging = false
    private var simCountdown: Int? = null
    private var simSecondPhone = false
    private var simPhoneSince = 0

    fun simulate(on: Boolean) {
        if (on == simulating) return
        simulating = on
        if (on) {
            simCount = _state.value.smoothed
            simPercent = _battery.value.percent.takeIf { it > 0 } ?: 72
            simCharging = _battery.value.onAc
        } else {
            // Back to the demo running by itself, as it was before.
            simCountdown = null
            simSecondPhone = false
            shutdownState = "armed"
            _state.update { s ->
                s.copy(conn = ConnState.ONLINE, screen = "on", buzzer = s.buzzer?.copy(available = true, note = null))
            }
        }
        recentRaw.clear()
        step()
    }

    fun simSetCount(n: Int) {
        simCount = n.coerceIn(0, 99)
        recentRaw.clear() // no smoothing lag: the new count shows at once
        step()
    }

    fun simSetBattery(percent: Int) {
        simPercent = percent.coerceIn(0, 100)
        step()
    }

    fun simSetCharging(on: Boolean) {
        simCharging = on
        step()
    }

    /** The Pi's own low-battery countdown, as it starts below the shutdown level. */
    fun simStartCountdown(seconds: Int = 60) {
        if (simCharging || shutdownState == "powering_off") return
        // The Pi only counts down below its shutdown level, so the battery drops there first.
        simPercent = minOf(simPercent, autoShutdownPct)
        simCountdown = seconds
        shutdownState = "counting"
        step()
    }

    fun simSetScreen(screen: String) {
        _state.update { it.copy(screen = screen) }
    }

    fun simSetBuzzer(wired: Boolean) {
        _state.update { s ->
            s.copy(buzzer = s.buzzer?.copy(available = wired, note = if (wired) null else "no buzzer answered on GPIO12 (simulated)"))
        }
    }

    fun simSetSecondPhone(on: Boolean) {
        simSecondPhone = on
        simPhoneSince = tick
        step()
    }

    /** The battery as the Simulation card set it, and the countdown running down. */
    private fun simBattery() {
        val left = simCountdown
        if (left != null) {
            when {
                // Plugging in the charger stops the countdown, as on the Pi.
                simCharging -> { simCountdown = null; shutdownState = "armed" }
                left <= 1 -> { simCountdown = null; powerOff(EventType.LOW_BATTERY_SHUTDOWN) }
                else -> simCountdown = left - 1
            }
        }
        if (simCharging && shutdownState == "paused") shutdownState = "armed"
        val percent = simPercent
        val volts = 14.2f + 2.4f * percent / 100f
        val amps = if (simCharging) 1.15f else -0.78f
        val cell = volts / 4f
        _battery.value = withPower(Battery(
            ok = true, percent = percent, voltage = volts, current = amps, power = volts * amps,
            onAc = simCharging, charging = simCharging && percent < 100,
            minutesLeft = if (simCharging) ((100 - percent) * 2.1f).roundToInt() else (percent * 4.3f).roundToInt(),
            capacityMah = 5000 * percent / 100,
            cells = listOf(cell, cell + 0.004f, cell - 0.006f, cell + 0.002f),
            inputV = if (simCharging) 15.1f else 0f, inputA = if (simCharging) 2.2f else 0f, inputW = if (simCharging) 33.2f else 0f,
        )).copy(shutdownInS = simCountdown)
    }

    /** The Pi powering itself off. In Simulation it stays off until the card brings it back. */
    private fun powerOff(event: EventType) {
        log(event, _state.value)
        shutdownState = "powering_off"
        scope.launch {
            delay(3000)
            _state.update { it.copy(conn = ConnState.NO_PI) }
        }
    }

    private fun muted(s: LiveState, seconds: Int, auto: Boolean) = s.copy(
        muted = true,
        muteLeft = if (seconds > 0) seconds else null,
        muteTotal = if (seconds > 0) seconds else 0,
        autoMuted = auto,
    )

    private fun log(event: EventType, s: LiveState, snapshot: Boolean = false) {
        val now = LocalDateTime.now()
        // A clean copy too, as the Clean copies bar asks: every snapshot, Capture only, or none.
        val cleanMode = s.clean?.mode ?: "off"
        val keepClean = snapshot && (cleanMode == "all" || (cleanMode == "manual" && event == EventType.MANUAL))
        val name = "fake_${now.format(DateTimeFormatter.ofPattern("HHmmss"))}.jpg"
        if (keepClean) _state.update { st -> st.copy(clean = st.clean?.let { it.copy(count = it.count + 1, bytes = it.bytes + 61_000) }) }
        val row = AlertRow(
            id = nextId++,
            timestamp = now.format(fmt),
            event = event,
            smoothedCount = s.smoothed,
            nBottle = s.bottle, nBag = s.bag, nFoodContainer = s.foodContainer,
            peakConfidence = if (snapshot) 0.80f + Random.nextFloat() * 0.15f else null,
            thresholdUsed = s.threshold,
            read = false,
            snapshot = if (snapshot) name else null,
            clean = if (keepClean) name else null,
        )
        _alerts.update { listOf(row) + it }
    }

    /** Demo only: lets the app show the no-camera and no-link screens. */
    override suspend fun reconnectCamera() {
        _state.update { it.copy(conn = ConnState.NO_CAMERA, reconnects = it.reconnects + 1) }
        scope.launch {
            delay(3000)
            _state.update { it.copy(conn = ConnState.ONLINE) }
        }
    }

    // ---------- Pi power, invented so the demo can show it ----------

    private var autoShutdown = true
    private var autoShutdownPct = 15
    private var shutdownState = "armed"

    /** Adds the power fields to a fresh battery reading. */
    private fun withPower(b: Battery) = b.copy(
        autoShutdown = autoShutdown, autoShutdownPct = autoShutdownPct,
        shutdownState = if (autoShutdown || shutdownState == "powering_off") shutdownState else "off",
    )

    override suspend fun setScreenSaver(mode: String?, timeoutS: Int?, timeoutBatteryS: Int?, dimPct: Int?, wakeOnAlert: Boolean?) {
        _state.update {
            it.copy(
                screenMode = mode ?: it.screenMode,
                screenTimeoutS = (timeoutS ?: it.screenTimeoutS).coerceIn(60, 3600),
                screenTimeoutBatteryS = (timeoutBatteryS ?: it.screenTimeoutBatteryS).coerceIn(30, 3600),
                screenDimPct = (dimPct ?: it.screenDimPct).coerceIn(5, 50),
                screenWakeOnAlert = wakeOnAlert ?: it.screenWakeOnAlert,
                // Changing the mode wakes a sleeping screen, as on the Pi.
                screen = if (mode != null) "on" else it.screen,
            )
        }
    }

    override suspend fun screen(action: String) {
        _state.update { it.copy(screen = if (action == "sleep") (if (it.screenMode == "dim") "dim" else "off") else "on") }
    }

    override suspend fun setAutoShutdown(on: Boolean?, pct: Int?) {
        if (on != null) autoShutdown = on
        if (pct != null) autoShutdownPct = pct.coerceIn(10, 50)
        _battery.update { withPower(it) }
    }

    override suspend fun cancelShutdown(): Boolean {
        if (shutdownState == "counting") {
            // Paused until the charger is plugged in, as on the Pi.
            simCountdown = null
            shutdownState = "paused"
            _battery.update { withPower(it).copy(shutdownInS = null) }
        }
        return true
    }

    override suspend fun shutdown() {
        if (simulating) {
            powerOff(EventType.MANUAL_SHUTDOWN)
            _battery.update { withPower(it) }
            return
        }
        log(EventType.MANUAL_SHUTDOWN, _state.value)
        shutdownState = "powering_off"
        _battery.update { withPower(it) }
        scope.launch {
            delay(5000)
            _state.update { it.copy(conn = ConnState.NO_PI) }
            // The demo Pi comes back by itself so the rest of the demo can go on.
            delay(9000)
            shutdownState = "armed"
            _state.update { it.copy(conn = ConnState.ONLINE) }
        }
    }

    // ---------- Pi buzzer, invented so the demo can show it ----------

    override suspend fun setBuzzer(changes: Map<String, Any>) {
        _state.update { s ->
            val b = s.buzzer ?: return@update s
            s.copy(
                buzzer = b.copy(
                    on = changes["on"] as? Boolean ?: b.on,
                    volume = changes["volume"] as? String ?: b.volume,
                    alerts = changes["alerts"] as? Boolean ?: b.alerts,
                    countdown = changes["countdown"] as? Boolean ?: b.countdown,
                    ui = changes["ui"] as? Boolean ?: b.ui,
                    repeat = changes["repeat"] as? Boolean ?: b.repeat,
                    repeatS = (changes["repeat_s"] as? Int ?: b.repeatS).coerceIn(10, 300),
                    quietOn = changes["quiet_on"] as? Boolean ?: b.quietOn,
                    quietFrom = changes["quiet_from"] as? String ?: b.quietFrom,
                    quietTo = changes["quiet_to"] as? String ?: b.quietTo,
                )
            )
        }
    }

    override suspend fun setClean(mode: String) {
        _state.update { s -> s.copy(clean = (s.clean ?: CleanCopies()).copy(mode = mode)) }
    }

    override fun cleanUrl(name: String): String? = snapshotUrl(name)

    override suspend fun restart() {
        _state.update { it.copy(conn = ConnState.NO_PI) }
        scope.launch {
            delay(4000)
            _state.update { it.copy(conn = ConnState.ONLINE) }
        }
    }

    override suspend fun networkDetails(): NetDetails {
        delay(400)
        return NetDetails(
            internet = true,
            cameraIp = "192.168.8.64",
            interfaces = listOf(
                NetInterface(
                    device = "wlan0", role = "wifi", state = "connected", connection = "Demo Wi-Fi",
                    ips = listOf("192.168.1.101/24"), gateway = "192.168.1.1", dns = listOf("192.168.1.1"),
                    mac = "D8:3A:DD:00:00:01", speed = "", method = "auto", isDefault = true,
                    ssid = "Demo Wi-Fi", signal = 78, frequency = "5180 MHz", channel = "36",
                    rate = "270 Mbit/s", security = "WPA2",
                ),
                NetInterface(
                    device = "eth0", role = "camera", state = "connected", connection = "camera-link",
                    ips = listOf("192.168.8.10/24"), gateway = "", dns = emptyList(),
                    mac = "D8:3A:DD:00:00:02", speed = "100 Mb/s", method = "manual", isDefault = false,
                ),
            ),
        )
    }

    fun demoSetConn(conn: ConnState) {
        // A Pi that powered off is switched on again by bringing it back.
        if (conn == ConnState.ONLINE && shutdownState == "powering_off") {
            shutdownState = "armed"
            _battery.update { withPower(it) }
        }
        _state.update { it.copy(conn = conn) }
    }

    // ---------- GgRepository ----------

    override suspend fun testConnection(address: String): Result<Long> {
        delay(1200)
        val m = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?::(\d+))?$""").find(address.trim())
            ?: return Result.failure(Exception("That does not look like an address. Use the form 192.168.1.101:8080."))
        val ip = m.groupValues[1]
        val port = m.groupValues[2].ifEmpty { "8080" }
        if (port != "8080") {
            return Result.failure(Exception("Nothing answered on port $port. The Pi serves on 8080."))
        }
        if (ip.endsWith(".99")) {
            return Result.failure(Exception("No answer from $ip. Check that the phone and the Pi are on the same Wi-Fi."))
        }
        return Result.success(Random.nextLong(40, 90))
    }

    override suspend fun mute(seconds: Int) {
        val s = _state.value
        if (!s.alertActive) return
        _state.value = muted(s, seconds, auto = false)
        log(EventType.MUTED, _state.value)
    }

    override suspend fun unmute() {
        val s = _state.value
        if (!s.muted) return
        _state.value = s.copy(muted = false, muteLeft = 0, muteTotal = 0, autoMuted = false)
        log(EventType.UNMUTED, _state.value)
    }

    override suspend fun setAutoMute(on: Boolean?, seconds: Int?) {
        _state.update { it.copy(autoMute = on ?: it.autoMute, autoMuteS = seconds ?: it.autoMuteS) }
    }

    override suspend fun saveSettings(confPercent: Int, threshold: Int) {
        val t = threshold.coerceAtLeast(1)
        _state.update {
            it.copy(conf = confPercent.coerceIn(5, 95) / 100f, threshold = t, rearm = rearmFor(t))
        }
    }

    override suspend fun setRoi(points: List<RoiPoint>) {
        _state.update { it.copy(roi = if (points.size >= 3) points else emptyList()) }
    }

    override suspend fun testAlert() = log(EventType.TEST, _state.value, snapshot = true)

    override suspend fun capture() = log(EventType.MANUAL, _state.value, snapshot = true)

    override suspend fun markRead(ids: List<Long>?, read: Boolean) {
        _alerts.update { rows -> rows.map { if (ids == null || it.id in ids) it.copy(read = read) else it } }
    }

    override suspend fun deleteAlerts(ids: List<Long>) {
        _alerts.update { rows -> rows.filterNot { it.id in ids } }
    }

    override suspend fun deleteRange(from: String, to: String) {
        val start = from.toDateOrNull() ?: LocalDate.MIN
        val end = to.toDateOrNull() ?: LocalDate.MAX
        _alerts.update { rows ->
            rows.filterNot {
                val d = it.timestamp.take(10).toDateOrNull() ?: return@filterNot false
                !d.isBefore(start) && !d.isAfter(end)
            }
        }
    }

    override suspend fun deleteAll() {
        _alerts.value = emptyList()
    }

    private fun String.toDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(trim()) }.getOrNull()

    // ---------- seed data, same mix as the mockup ----------

    private fun seedHistory(): List<AlertRow> {
        val order = listOf(
            EventType.ACCUMULATION, EventType.MANUAL, EventType.SUPPRESSED, EventType.ACCUMULATION,
            EventType.TEST, EventType.AUTO_MUTED, EventType.UNMUTED, EventType.MANUAL,
            EventType.ACCUMULATION, EventType.SUPPRESSED, EventType.MANUAL, EventType.TEST,
        )
        val counts = listOf(21, 12, 9, 22, 20, 18, 20, 7, 23, 11, 5, 20)
        val start = LocalDateTime.now().minusMinutes(5)
        return List(24) { i ->
            val ev = order[i % order.size]
            val n = if (ev.isMuteEvent) 20 else counts[i % counts.size]
            val bottle = (n * 0.45).roundToInt()
            val bag = (n * 0.30).roundToInt()
            val ts = start.minusMinutes(47L * i)
            val hasSnap = !ev.isMuteEvent && i != 4 // one test row with no image, like the mock server
            AlertRow(
                id = (24 - i).toLong(),
                timestamp = ts.format(fmt),
                event = ev,
                smoothedCount = n,
                nBottle = bottle, nBag = bag, nFoodContainer = n - bottle - bag,
                peakConfidence = if (hasSnap) 0.78f + (i % 5) * 0.04f else null,
                thresholdUsed = 20,
                read = i >= 3,
                snapshot = if (hasSnap) "seed_$i.jpg" else null,
                clean = if (hasSnap && !ev.isMuteEvent) "seed_$i.jpg" else null,
            )
        }
    }
}
