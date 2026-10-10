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

    /** A pack that runs down for a minute, then charges for a minute. */
    private fun stepBattery() {
        val phase = (tick / 60) % 2
        val f = (tick % 60) / 60f
        val charging = phase == 1
        val percent = if (charging) (58 + 30 * f).roundToInt() else (88 - 30 * f).roundToInt()
        val volts = 14.2f + 2.4f * percent / 100f
        val amps = if (charging) 1.15f + 0.1f * sin(tick / 3.0).toFloat() else -0.78f - 0.08f * sin(tick / 3.0).toFloat()
        val cell = volts / 4f
        _battery.value = Battery(
            ok = true, percent = percent, voltage = volts, current = amps, power = volts * amps,
            onAc = charging, charging = charging,
            minutesLeft = if (charging) ((100 - percent) * 2.1f).roundToInt() else (percent * 4.3f).roundToInt(),
            capacityMah = 5000 * percent / 100,
            cells = listOf(cell, cell + 0.004f, cell - 0.006f, cell + 0.002f),
            inputV = if (charging) 15.1f else 0f, inputA = if (charging) 2.2f else 0f, inputW = if (charging) 33.2f else 0f,
        )
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
        val raw = (wave + Random.nextInt(-2, 3)).roundToInt().coerceAtLeast(0)
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
        _state.value = next
        stepBattery()
    }

    private fun muted(s: LiveState, seconds: Int, auto: Boolean) = s.copy(
        muted = true,
        muteLeft = if (seconds > 0) seconds else null,
        muteTotal = if (seconds > 0) seconds else 0,
        autoMuted = auto,
    )

    private fun log(event: EventType, s: LiveState, snapshot: Boolean = false) {
        val now = LocalDateTime.now()
        val row = AlertRow(
            id = nextId++,
            timestamp = now.format(fmt),
            event = event,
            smoothedCount = s.smoothed,
            nBottle = s.bottle, nBag = s.bag, nFoodContainer = s.foodContainer,
            peakConfidence = if (snapshot) 0.80f + Random.nextFloat() * 0.15f else null,
            thresholdUsed = s.threshold,
            read = false,
            snapshot = if (snapshot) "fake_${now.format(DateTimeFormatter.ofPattern("HHmmss"))}.jpg" else null,
        )
        _alerts.update { listOf(row) + it }
    }

    /** Demo only: lets the app show the no-camera and no-link screens. */
    fun demoSetConn(conn: ConnState) {
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
            )
        }
    }
}
