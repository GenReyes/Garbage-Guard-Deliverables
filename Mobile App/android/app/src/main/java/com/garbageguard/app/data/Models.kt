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
    val viewers: List<Viewer> = emptyList(),
    /** Whether the Pi itself is syncing to Firebase, and how many phones watch through it. */
    val cloudEnabled: Boolean = false,
    val cloudOk: Boolean = false,
    val cloudWatchers: Int = 0,
    val cloudUsage: CloudUsage? = null,
    /** Set in Firebase mode when the Pi's heartbeat has stopped: the clock time it was last seen. */
    val piOfflineSince: String? = null,
    /** The Pi's screen saver. [screenMode] is null on an older Pi that has none: "off", "dim" or "never". */
    val screenMode: String? = null,
    val screenTimeoutS: Int = 600,
    val screenTimeoutBatteryS: Int = 60,
    val screenDimPct: Int = 20,
    val screenWakeOnAlert: Boolean = true,
    /** What the Pi's own screen is doing now: "on", "dim" or "off". */
    val screen: String = "on",
    /** A sentence from the Pi when the screen could not do what was asked. */
    val screenNote: String? = null,
    /** The Pi's buzzer and vibration motor. Null on a Pi that has neither. */
    val buzzer: Buzzer? = null,
)

/** One phone or browser from the "viewers" list in GET /api/state. */
data class Viewer(
    val ip: String,
    /** "app" or "browser". */
    val kind: String,
    val id: String = "",
    val device: String = "",
    val os: String = "",
    val browser: String = "",
    val appVersion: String = "",
    /** "foreground" or "background", as the app reports it. */
    val mode: String = "",
    val connectedSeconds: Int = 0,
    val hostname: String = "",
    val mac: String = "",
    /** "direct", "shared" (a router in between, so the address and MAC are the router's) or "remote". */
    val via: String = "",
)

/** The event values a history row can carry. */
enum class EventType(val api: String, val label: String) {
    ACCUMULATION("accumulation", "Limit reached"),
    SUPPRESSED("suppressed", "Suppressed"),
    TEST("test", "Test alert"),
    MANUAL("manual", "Manual capture"),
    MUTED("muted", "Alert muted"),
    AUTO_MUTED("auto_muted", "Auto-muted"),
    UNMUTED("unmuted", "Alert unmuted"),
    LOW_BATTERY_SHUTDOWN("low_battery_shutdown", "Low battery shutdown"),
    MANUAL_SHUTDOWN("manual_shutdown", "Pi shut down");

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
    /** Low-battery shutdown. [autoShutdown] is null on an older Pi without it. */
    val autoShutdown: Boolean? = null,
    val autoShutdownPct: Int = 15,
    /** "off", "boot_grace", "armed", "counting", "paused" or "powering_off". */
    val shutdownState: String = "off",
    /** Seconds left while counting. */
    val shutdownInS: Int? = null,
    /** The countdown is the 5% one, which cannot be cancelled. */
    val shutdownFloor: Boolean = false,
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
/** How the app is reaching the Pi right now. Same Wi-Fi always comes first. */
enum class LinkMode { LOCAL, CLOUD }

/** The four Firebase settings the user can type in. */
data class FirebaseConfig(
    val url: String = "",
    val key: String = "",
    val email: String = "",
    val password: String = "",
) {
    val complete: Boolean get() = url.isNotBlank() && key.isNotBlank() && email.isNotBlank() && password.isNotBlank()
}

/**
 * The Pi's estimate of the free monthly Firebase allowance, from /gg/usage
 * (or the "cloud" field of /api/state on the same Wi-Fi). Always an
 * estimate; the exact figure is only in the Firebase console.
 */
data class CloudUsage(
    val downBytes: Long,
    val limitBytes: Long,
    val fraction: Float,
    /** "ok", "warn", "near" or "bad". */
    val level: String,
    /** A sentence to show beside the picture when the Pi has slowed or stopped the preview. */
    val note: String?,
)

/**
 * The Pi's buzzer settings and what it reports about itself, from the
 * buzzer_*, quiet_* and vibe_* fields of GET /api/state.
 */
data class Buzzer(
    val on: Boolean = true,
    /** "low", "medium" or "high". */
    val volume: String = "medium",
    val alerts: Boolean = true,
    val countdown: Boolean = true,
    val ui: Boolean = false,
    val repeat: Boolean = true,
    val repeatS: Int = 30,
    val quietOn: Boolean = false,
    /** HH:MM on the Pi's clock. */
    val quietFrom: String = "22:00",
    val quietTo: String = "06:00",
    /** False when the buzzer could not start; [note] says why. */
    val available: Boolean = true,
    val note: String? = null,
    /** True while quiet hours are silencing the buzzer. */
    val quietNow: Boolean = false,
    /** Set when the Pi's clock is not synchronised, so quiet hours are not applied. */
    val quietNote: String? = null,
    /** The vibration motor. Null on a Pi without one. */
    val vibeOn: Boolean? = null,
    val vibeStrength: String = "medium",
    val vibeAvailable: Boolean = true,
    val vibeNote: String? = null,
)
