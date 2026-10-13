package com.garbageguard.app.data

import kotlin.coroutines.coroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the UI needs from the Pi. FakeRepository implements this with
 * invented data. Later a PiRepository will implement the same interface with
 * real HTTP calls, and only the line that creates the repository changes.
 *
 * Each function notes the endpoint it will call.
 */
/**
 * Rides along with the coroutine that sends a command. A repository marks it
 * when the Pi did not confirm, so the screen can tell a command that worked
 * from one that was lost, without every call having to return a result.
 */
class Outcome : AbstractCoroutineContextElement(Outcome) {
    companion object Key : CoroutineContext.Key<Outcome>

    @Volatile
    var failed = false
}

/** Marks the command now running as not confirmed. */
suspend fun commandFailed() {
    coroutineContext[Outcome]?.failed = true
}

interface GgRepository {
    /** Polled from GET /api/state about once per second. */
    val state: StateFlow<LiveState>

    /** From GET /api/alerts?limit=50, newest first. */
    val alerts: StateFlow<List<AlertRow>>

    /** Polled from GET /api/battery every two seconds. */
    val battery: StateFlow<Battery>

    /** Address of GET /frame.jpg. Null when there is no camera picture, as with demo data. */
    val frameUrl: String? get() = null

    /** Address of GET /snapshots/<name>. Null when snapshots are not real images. */
    fun snapshotUrl(name: String): String? = null

    /** Times one GET /api/state. Returns latency in ms, or an error message. */
    suspend fun testConnection(address: String): Result<Long>

    /** POST /api/mute {"seconds": n}. 0 means until unmuted. */
    suspend fun mute(seconds: Int)

    /** POST /api/mute {"cancel": true} */
    suspend fun unmute()

    /** POST /api/auto_mute. Either field may be null. */
    suspend fun setAutoMute(on: Boolean?, seconds: Int?)

    /** POST /api/settings {"conf": 40, "threshold": 20}. conf is a percentage. */
    suspend fun saveSettings(confPercent: Int, threshold: Int)

    /** POST /api/roi. An empty list clears the area. */
    suspend fun setRoi(points: List<RoiPoint>)

    /** POST /api/test_alert */
    suspend fun testAlert()

    /** POST /api/capture */
    suspend fun capture()

    /** POST /api/alerts/read with ids, or {"all": true} when ids is null. */
    suspend fun markRead(ids: List<Long>?, read: Boolean)

    /** POST /api/alerts/delete by ids. */
    suspend fun deleteAlerts(ids: List<Long>)

    /** POST /api/alerts/delete by date range, dates as yyyy-mm-dd. */
    suspend fun deleteRange(from: String, to: String)

    /** POST /api/alerts/delete {"all": true} */
    suspend fun deleteAll()

    /** POST /api/camera/reconnect. Watch the state's conn for the result. */
    suspend fun reconnectCamera()

    /** POST /api/restart. The Pi drops off for a few seconds, then comes back. */
    suspend fun restart()

    /** GET /api/net/details. Null when the Pi does not answer or is too old to have it. */
    suspend fun networkDetails(): NetDetails?

    /** POST /api/screen_saver. Any value may be left null to keep it as it is. */
    suspend fun setScreenSaver(mode: String?, timeoutS: Int?, timeoutBatteryS: Int?, dimPct: Int?, wakeOnAlert: Boolean?) {}

    /** POST /api/screen with "wake" or "sleep". */
    suspend fun screen(action: String) {}

    /** POST /api/auto_shutdown with the switch, the percentage, or both. */
    suspend fun setAutoShutdown(on: Boolean?, pct: Int?) {}

    /** POST /api/auto_shutdown {"cancel": true}. False when the Pi refuses. */
    suspend fun cancelShutdown(): Boolean = true

    /** POST /api/shutdown {"confirm": true}. Powers the Pi off. */
    suspend fun shutdown() {}
}
