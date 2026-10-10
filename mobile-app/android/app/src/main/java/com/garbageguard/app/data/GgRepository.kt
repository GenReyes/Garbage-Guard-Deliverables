package com.garbageguard.app.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the UI needs from the Pi. FakeRepository implements this with
 * invented data. Later a PiRepository will implement the same interface with
 * real HTTP calls, and only the line that creates the repository changes.
 *
 * Each function notes the endpoint it will call.
 */
interface GgRepository {
    /** Polled from GET /api/state about once per second. */
    val state: StateFlow<LiveState>

    /** From GET /api/alerts?limit=50, newest first. */
    val alerts: StateFlow<List<AlertRow>>

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
}
