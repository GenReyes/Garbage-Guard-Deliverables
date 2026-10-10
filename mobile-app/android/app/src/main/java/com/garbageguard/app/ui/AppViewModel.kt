package com.garbageguard.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.EventType
import com.garbageguard.app.data.FakeRepository
import com.garbageguard.app.data.GgRepository
import com.garbageguard.app.data.RoiPoint
import com.garbageguard.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab { MONITOR, HISTORY, SETTINGS }

/** Notification permission as the Settings row shows it. */
enum class PermState { ON, OFF, BLOCKED }

/** The states of the first-launch screen. */
enum class SetupState { EMPTY, TESTING, FAILED, OK }

/** The filter chips on the History sheet. */
enum class HistFilter(val label: String) {
    ALL("All events"), ALERTS("Alerts"), CAPTURES("Captures"), TESTS("Tests"), MUTED("Muted");

    fun matches(row: AlertRow) = when (this) {
        ALL -> true
        ALERTS -> row.event == EventType.ACCUMULATION
        CAPTURES -> row.event == EventType.MANUAL
        TESTS -> row.event == EventType.TEST
        MUTED -> row.event.isMuteEvent
    }
}

/** Whatever is drawn over the current screen. One at a time, like the mockup. */
sealed interface Overlay {
    data object Mute : Overlay
    data object Filter : Overlay
    /** id null means the whole date range. */
    data class Delete(val id: Long?) : Overlay
    data class Viewer(val id: Long) : Overlay
    data object ClearArea : Overlay
    data object Battery : Overlay
    data object NotifPrompt : Overlay
}

class AppViewModel : ViewModel() {

    // The one line to change when the real API is ready:
    // val repo: GgRepository = PiRepository(baseUrl = address, scope = viewModelScope)
    val repo: GgRepository = FakeRepository(viewModelScope)

    val state = repo.state
    val alerts = repo.alerts

    // ---------- app-side state, kept in memory for now ----------

    var address by mutableStateOf("")
    var setupDone by mutableStateOf(false)
    var setupState by mutableStateOf(SetupState.EMPTY)
    var setupMessage by mutableStateOf("")
    var setupLatency by mutableStateOf(0L)
    /** True once the app has connected at least once, so Setup can be backed out of. */
    var everConnected by mutableStateOf(false)
        private set

    var tab by mutableStateOf(Tab.MONITOR)
    var themeMode by mutableStateOf(ThemeMode.AUTO)
    var loading by mutableStateOf(false)
        private set

    var overlay by mutableStateOf<Overlay?>(null)

    var muteChoice by mutableIntStateOf(900) // seconds, 0 = until unmuted
        private set

    var notifications by mutableStateOf(PermState.OFF)
    var batteryUnrestricted by mutableStateOf(false)
    private var askedForAlerts = false

    // Fullscreen feed and area drawing.
    var fullscreen by mutableStateOf(false)
        private set
    var drawing by mutableStateOf(false)
        private set
    val drawPoints = mutableStateListOf<RoiPoint>()

    // History view.
    var filter by mutableStateOf(HistFilter.ALL)
    var dateFrom by mutableStateOf(LocalDate.now().minusDays(5).toString())
    var dateTo by mutableStateOf(LocalDate.now().toString())
    var shown by mutableIntStateOf(6)
    var swipedId by mutableStateOf<Long?>(null)

    // Settings drafts. null means "same as saved".
    var draftConf by mutableStateOf<Int?>(null)
    var draftLimit by mutableStateOf<Int?>(null)

    /** Short message shown above the navigation bar. */
    var toast by mutableStateOf<String?>(null)
        private set
    var toastSeq by mutableIntStateOf(0)
        private set

    fun showToast(message: String) {
        toast = message
        toastSeq++
    }

    fun clearToast() {
        toast = null
    }

    private fun launchAction(message: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            if (message != null) showToast(message)
        }
    }

    // ---------- setup ----------

    fun testConnection() {
        if (setupState == SetupState.TESTING) return
        val addr = address.trim().ifEmpty { DEFAULT_ADDRESS }
        address = addr
        setupState = SetupState.TESTING
        viewModelScope.launch {
            repo.testConnection(addr)
                .onSuccess {
                    setupLatency = it
                    setupState = SetupState.OK
                }
                .onFailure {
                    setupMessage = it.message ?: "Could not reach that address."
                    setupState = SetupState.FAILED
                }
        }
    }

    fun editAddress(value: String) {
        address = value
        if (setupState != SetupState.TESTING) setupState = SetupState.EMPTY
    }

    fun finishSetup() {
        setupDone = true
        everConnected = true
        tab = Tab.MONITOR
        loading = true
        viewModelScope.launch {
            delay(1100)
            loading = false
            if (!askedForAlerts && notifications != PermState.ON) {
                askedForAlerts = true
                overlay = Overlay.NotifPrompt
            }
        }
    }

    fun changeAddress() {
        setupState = SetupState.EMPTY
        setupDone = false
    }

    fun cancelSetup() {
        if (everConnected) setupDone = true
    }

    fun selectTab(t: Tab) {
        tab = t
        overlay = null
        swipedId = null
    }

    // ---------- monitor ----------

    fun mute() {
        overlay = null
        launchAction(if (muteChoice == 0) "Muted until you unmute" else "Muted for ${muteChoice / 60} min") {
            repo.mute(muteChoice)
        }
    }

    fun unmute() = launchAction("Alert unmuted") { repo.unmute() }

    fun chooseMuteLength(seconds: Int) {
        muteChoice = seconds
        // Auto-mute uses the same length, like the touchscreen.
        launchAction { repo.setAutoMute(on = null, seconds = seconds) }
    }

    fun toggleAutoMute() {
        val on = !state.value.autoMute
        launchAction(if (on) "Auto-mute on" else "Auto-mute off") { repo.setAutoMute(on = on, seconds = muteChoice) }
    }

    fun capture() = launchAction("Snapshot saved") { repo.capture() }

    fun retry() {
        (repo as? FakeRepository)?.demoSetConn(ConnState.ONLINE)
        showToast("Connected")
    }

    /** Demo only: a long press on the status pill steps through the connection states. */
    fun demoCycleConn() {
        val fake = repo as? FakeRepository ?: return
        val next = when (state.value.conn) {
            ConnState.ONLINE -> ConnState.NO_CAMERA
            ConnState.NO_CAMERA -> ConnState.NO_PI
            ConnState.NO_PI -> ConnState.ONLINE
        }
        fake.demoSetConn(next)
    }

    fun openFullscreen() {
        drawing = false
        fullscreen = true
    }

    fun closeFullscreen() {
        fullscreen = false
        drawing = false
    }

    // ---------- settings ----------

    fun step(conf: Boolean, delta: Int) {
        if (conf) {
            val now = draftConf ?: Math.round(state.value.conf * 100)
            draftConf = (now + delta).coerceIn(5, 95)
        } else {
            val now = draftLimit ?: state.value.threshold
            draftLimit = (now + delta).coerceIn(1, 200)
        }
    }

    fun saveSettings() {
        val conf = draftConf ?: Math.round(state.value.conf * 100)
        val limit = draftLimit ?: state.value.threshold
        launchAction("Settings saved") {
            repo.saveSettings(conf, limit)
            draftConf = null
            draftLimit = null
        }
    }

    fun testAlert() = launchAction("Test alert logged") { repo.testAlert() }

    fun startDrawing(edit: Boolean) {
        drawPoints.clear()
        if (edit) drawPoints.addAll(state.value.roi)
        drawing = true
        fullscreen = true
    }

    fun applyArea() {
        if (drawPoints.size < 3) {
            showToast("Place at least 3 points")
            return
        }
        val points = drawPoints.toList()
        closeFullscreen()
        launchAction("Area applied") { repo.setRoi(points) }
    }

    fun clearArea() {
        overlay = null
        launchAction("Area cleared. Whole frame monitored.") { repo.setRoi(emptyList()) }
    }

    fun setNotifications(granted: Boolean) {
        overlay = null
        notifications = if (granted) PermState.ON else PermState.BLOCKED
        showToast(if (granted) "Alerts are on" else "Alerts are off")
    }

    fun fixBattery() {
        overlay = null
        batteryUnrestricted = true
        showToast("Battery set to Unrestricted")
    }

    // ---------- history ----------

    fun visibleRows(all: List<AlertRow>): List<AlertRow> = all.filter { filter.matches(it) }

    fun toggleRead(row: AlertRow) = launchAction { repo.markRead(listOf(row.id), !row.read) }

    fun markAllRead() = launchAction("All marked read") { repo.markRead(null, true) }

    fun refreshHistory() = showToast("History refreshed")

    fun confirmDelete() {
        val target = (overlay as? Overlay.Delete) ?: return
        overlay = null
        swipedId = null
        launchAction("Deleted") {
            if (target.id != null) repo.deleteAlerts(listOf(target.id))
            else repo.deleteRange(dateFrom, dateTo)
        }
    }

    companion object {
        const val DEFAULT_ADDRESS = "192.168.1.101:8080"
    }
}
