package com.garbageguard.app.data

/**
 * Data shapes that mirror the Pi's API (mobile-app/GarbageGuard_API.md).
 * Field names follow the JSON so the real repository can map them one to one.
 */

enum class ConnState { ONLINE, NO_CAMERA, NO_PI }

/** One ROI point, normalised 0..1 like the "roi" field in /api/state. */
data class RoiPoint(val x: Float, val y: Float)

/** Mirrors GET /api/state. */
data class LiveState(
    val conn: ConnState = ConnState.ONLINE,
    val bottle: Int = 0,
    val bag: Int = 0,
    val foodContainer: Int = 0,
    val raw: Int = 0,
    val smoothed: Int = 0,
    val threshold: Int = 20,
    val rearm: Int = 15,
    val conf: Float = 0.40f,
    val roi: List<RoiPoint> = emptyList(),
    val latencyMs: Float = 0f,
    val avgLatencyMs: Float = 0f,
    val fps: Float = 0f,
    val camFps: Float = 0f,
    val alertActive: Boolean = false,
    val muted: Boolean = false,
    /** Seconds left. null means no expiry, 0 means not muted. */
    val muteLeft: Int? = 0,
    val muteTotal: Int = 0,
    val autoMute: Boolean = false,
    val autoMuteS: Int = 900,
    val autoMuted: Boolean = false,
    /** How many times the Pi has restarted the camera stream. */
    val reconnects: Int = 0,
    /** Phones and browsers that polled the Pi in the last few seconds. */
    val viewerApps: Int = 0,
    val viewerBrowsers: Int = 0,
)

/** The seven event values a history row can carry. */
enum class EventType(val api: String, val label: String) {
    ACCUMULATION("accumulation", "Limit reached"),
    SUPPRESSED("suppressed", "Suppressed"),
    TEST("test", "Test alert"),
    MANUAL("manual", "Manual capture"),
    MUTED("muted", "Alert muted"),
    AUTO_MUTED("auto_muted", "Auto-muted"),
    UNMUTED("unmuted", "Alert unmuted");

    val isMuteEvent get() = this == MUTED || this == AUTO_MUTED || this == UNMUTED

    companion object {
        fun fromApi(value: String) = entries.firstOrNull { it.api == value } ?: SUPPRESSED
    }
}

/** Mirrors one row of GET /api/alerts. */
data class AlertRow(
    val id: Long,
    val timestamp: String,
    val event: EventType,
    val smoothedCount: Int,
    val nBottle: Int,
    val nBag: Int,
    val nFoodContainer: Int,
    /** Fraction 0..1, can be null. */
    val peakConfidence: Float?,
    val thresholdUsed: Int,
    val read: Boolean,
    /** Bare file name for GET /snapshots/<name>. null means no image. */
    val snapshot: String?,
)

/** Same formula as rearm_level() in pi/gg_counter.py. */
fun rearmFor(threshold: Int): Int = maxOf(0, threshold - maxOf(1, threshold / 4))

/**
 * Mirrors GET /api/battery, the Waveshare UPS HAT (E) readout. [ok] is false
 * when the Pi has no UPS or does not answer. Current and power are negative
 * while the pack is feeding the Pi, the way the HAT reports them.
 */
data class Battery(
    val ok: Boolean = false,
    val percent: Int = 0,
    val voltage: Float = 0f,
    val current: Float = 0f,
    val power: Float = 0f,
    val onAc: Boolean = false,
    val charging: Boolean = false,
    /** Minutes to full while charging, to empty on battery. Null when idle on AC. */
    val minutesLeft: Int? = null,
    val capacityMah: Int? = null,
    /** The four 21700 cells, in volts. */
    val cells: List<Float> = emptyList(),
    val inputV: Float? = null,
    val inputA: Float? = null,
    val inputW: Float? = null,
)
/** One adapter from GET /api/net/details. */
data class NetInterface(
    val device: String,
    /** "wifi", "wired", or "camera" for the cable that goes to the camera. */
    val role: String,
    val state: String,
    val connection: String,
    val ips: List<String>,
    val gateway: String,
    val dns: List<String>,
    val mac: String,
    val speed: String,
    /** "auto" or "manual". */
    val method: String,
    /** True for the adapter that carries the internet. */
    val isDefault: Boolean,
    val ssid: String = "",
    val signal: Int? = null,
    val frequency: String = "",
    val channel: String = "",
    val rate: String = "",
    val security: String = "",
)

/** Mirrors GET /api/net/details. Read-only in the app. */
data class NetDetails(
    val internet: Boolean,
    val cameraIp: String,
    val interfaces: List<NetInterface>,
)