package com.garbageguard.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.data.Battery
import com.garbageguard.app.data.CloudRepository
import com.garbageguard.app.data.CloudUsage
import com.garbageguard.app.data.FirebaseConfig
import com.garbageguard.app.data.LinkMode
import com.garbageguard.app.notify.CloudStore
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.EventType
import com.garbageguard.app.data.FakeRepository
import com.garbageguard.app.data.GgRepository
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.data.NetDetails
import com.garbageguard.app.data.PiRepository
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.garbageguard.app.notify.Alerts
import com.garbageguard.app.notify.AppGraph
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.notify.WatchService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
    ALL("All events"), ALERTS("Alerts"), CAPTURES("Captures"), TESTS("Tests"), MUTED("Mutes"), POWER("Power");

    fun matches(row: AlertRow) = when (this) {
        ALL -> true
        ALERTS -> row.event == EventType.ACCUMULATION
        CAPTURES -> row.event == EventType.MANUAL
        TESTS -> row.event == EventType.TEST
        MUTED -> row.event.isMuteEvent
        POWER -> row.event == EventType.LOW_BATTERY_SHUTDOWN || row.event == EventType.MANUAL_SHUTDOWN
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
    /** The Pi's UPS pack, opened from the header. */
    data object PiBattery : Overlay
    /** Read-only view of the Pi's network adapters. */
    data object Network : Overlay
    /** Confirmation before restarting Garbage-Guard on the Pi. */
    data object Restart : Overlay

    /** Asks before powering the Pi off. */
    data object Shutdown : Overlay
    /** Who is connected to the Pi right now. */
    data object Viewers : Overlay
    /** The Firebase settings page. */
    data object Firebase : Overlay
    data object NotifPrompt : Overlay
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app.applicationContext
    private val prefs: SharedPreferences = AppGraph.prefs(app)

    // Two sources behind one interface: the real Pi over the local network,
    // and invented data for showing the app with no Pi in the room.
    private val pi = AppGraph.pi // lives beyond the screen so alerts keep arriving
    private val fake by lazy { FakeRepository(viewModelScope) }
    private val active = MutableStateFlow<GgRepository>(pi)
    val repo: GgRepository get() = active.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LiveState> =
        active.flatMapLatest { it.state }.stateIn(viewModelScope, SharingStarted.Eagerly, LiveState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val alerts: StateFlow<List<AlertRow>> =
        active.flatMapLatest { it.alerts }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val battery: StateFlow<Battery> =
        active.flatMapLatest { it.battery }.stateIn(viewModelScope, SharingStarted.Eagerly, Battery())

    val appVersion: String get() = AppGraph.appVersion
    val clientId: String get() = if (demo) "demo" else AppGraph.clientId

    /** True while the app runs on invented data. */
    var demo by mutableStateOf(false)
        private set

    /** Same Wi-Fi or Firebase. Wi-Fi always comes first; see AppGraph. */
    var linkMode by mutableStateOf(LinkMode.LOCAL)
        private set

    /** True when the picture and data are coming through Firebase rather than the Pi's own Wi-Fi. */
    val remote: Boolean get() = !demo && linkMode == LinkMode.CLOUD

    /** The Pi's estimate of the free Firebase allowance, when reached through Firebase. */
    val cloudUsage: StateFlow<CloudUsage?> = AppGraph.cloud.usage

    var cloudReady by mutableStateOf(false)
        private set

    // The Firebase settings page.
    var fbUrl by mutableStateOf("")
    var fbKey by mutableStateOf("")
    var fbEmail by mutableStateOf("")
    var fbPassword by mutableStateOf("")
    var fbBusy by mutableStateOf(false)
        private set
    var fbMessage by mutableStateOf("")
        private set
    var fbPassed by mutableStateOf(false)
        private set

    fun openFirebase() {
        val cfg = AppGraph.cloud.config
        fbUrl = cfg.url; fbKey = cfg.key; fbEmail = cfg.email; fbPassword = cfg.password
        fbMessage = ""
        fbPassed = false
        overlay = Overlay.Firebase
    }

    /** Test signs in and checks the account is allowed. With [save], a passing set is kept and used at once. */
    fun testFirebase(save: Boolean) {
        if (fbBusy) return
        val cfg = FirebaseConfig(fbUrl.trim(), fbKey.trim(), fbEmail.trim(), fbPassword)
        CloudRepository.problemWith(cfg)?.let {
            fbPassed = false
            fbMessage = it
            return
        }
        fbBusy = true
        fbMessage = "Signing in"
        viewModelScope.launch {
            val result = AppGraph.cloud.test(cfg)
            fbBusy = false
            fbPassed = result.isSuccess
            fbMessage = result.fold(
                onSuccess = {
                    if (save) {
                        AppGraph.useCloudConfig(cfg, passedTest = true)
                        cloudReady = true
                        "Signed in and saved. Firebase is ready."
                    } else "Signed in. This account is allowed."
                },
                onFailure = { (it.message ?: "Could not sign in.") + if (save) " Nothing was saved." else "" },
            )
        }
    }

    /** Puts back the last set that passed a test. */
    fun restoreFirebase() {
        val good = CloudStore.working(appContext)
        if (good == null) {
            fbPassed = false
            fbMessage = "No working set has been saved yet."
            return
        }
        fbUrl = good.url; fbKey = good.key; fbEmail = good.email; fbPassword = good.password
        AppGraph.useCloudConfig(good, passedTest = true)
        cloudReady = true
        fbPassed = true
        fbMessage = "Working set restored."
    }

    /** From the first screen, when the phone is not on the Pi's Wi-Fi. */
    fun useFirebaseOnly() {
        if (!AppGraph.cloudReady) {
            openFirebase()
            fbMessage = "Fill these in first, then press Test and save."
            return
        }
        address = ""
        prefs.edit().putBoolean(KEY_CLOUD_ONLY, true).apply()
        finishSetup(firstTime = false)
    }

    /** Where the live picture comes from. Null in demo mode, which draws its own. */
    val frameUrl: String? get() = if (setupDone) repo.frameUrl else null

    fun snapshotUrl(name: String?): String? = name?.let { repo.snapshotUrl(it) }


    // ---------- app-side state, kept in memory for now ----------

    var address by mutableStateOf(prefs.getString(AppGraph.KEY_ADDRESS, "").orEmpty())
    var setupDone by mutableStateOf(false)
    var setupState by mutableStateOf(SetupState.EMPTY)
    var setupMessage by mutableStateOf("")
    var setupLatency by mutableStateOf(0L)
    /** True once the app has connected at least once, so Setup can be backed out of. */
    var everConnected by mutableStateOf(false)
        private set

    var tab by mutableStateOf(Tab.MONITOR)
    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(AppGraph.KEY_THEME, "AUTO")!!) }.getOrDefault(ThemeMode.AUTO)
    )
        private set

    // ---------- sound and feel, remembered ----------

    var uiSounds by mutableStateOf(prefs.getBoolean(KEY_SOUNDS, true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set
    var keepScreenOn by mutableStateOf(prefs.getBoolean(KEY_AWAKE, false))
        private set

    fun toggleUiSounds() {
        uiSounds = !uiSounds
        Feel.sounds = uiSounds
        prefs.edit().putBoolean(KEY_SOUNDS, uiSounds).apply()
    }

    fun toggleHaptics() {
        haptics = !haptics
        Feel.haptics = haptics
        prefs.edit().putBoolean(KEY_HAPTICS, haptics).apply()
    }

    fun toggleKeepScreenOn() {
        keepScreenOn = !keepScreenOn
        prefs.edit().putBoolean(KEY_AWAKE, keepScreenOn).apply()
    }

    /** Opens Android's notification settings for the app. Set by the screen, which owns the Activity. */
    var openAlertSounds: () -> Unit = {}

    /** The hidden game. Opened by holding the logo; nothing about it is saved. */
    var game by mutableStateOf(false)
        private set

    fun openGame() {
        closeFullscreen()
        overlay = null
        game = true
    }

    fun closeGame() {
        game = false
    }

    /** Auto follows the phone. The choice is remembered. */
    fun chooseTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(AppGraph.KEY_THEME, mode.name).apply()
    }
    var loading by mutableStateOf(false)
        private set

    var overlay by mutableStateOf<Overlay?>(null)

    var muteChoice by mutableIntStateOf(900) // seconds, 0 = until unmuted
        private set

    var notifications by mutableStateOf(PermState.OFF)
    var batteryUnrestricted by mutableStateOf(false)
        private set
    private var askedForAlerts = false

    init {
        AppGraph.init(appContext)
        Feel.sounds = uiSounds
        Feel.haptics = haptics
        // Demo data raises notifications too, while the app is running.
        viewModelScope.launch {
            alerts.collect { rows ->
                if (demo) Alerts.onRows(appContext, "demo", rows, viewModelScope, AppGraph.foreground) { null }
            }
        }
        cloudReady = AppGraph.cloudReady
        // Follow whichever connection is in use, and show its messages.
        viewModelScope.launch {
            AppGraph.mode.collect { mode ->
                linkMode = mode
                if (!demo) active.value = if (mode == LinkMode.CLOUD) AppGraph.cloud else pi
            }
        }
        viewModelScope.launch { AppGraph.notices.collect { showToast(it) } }
        refreshSystemState()
        // A remembered address (or a Firebase-only setup) means the app goes straight back to its Pi.
        if (address.isNotEmpty() || (prefs.getBoolean(KEY_CLOUD_ONLY, false) && AppGraph.cloudReady)) {
            finishSetup(firstTime = false)
        }
    }

    /** Reads the two Android settings the Alerts card reports. Called whenever the app returns to the screen. */
    fun refreshSystemState() {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) notifications = PermState.ON else if (notifications == PermState.ON) notifications = PermState.OFF
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(appContext.packageName)
        if (granted && setupDone && !demo) WatchService.start(appContext)
    }

    // Fullscreen feed and area drawing.
    var fullscreen by mutableStateOf(false)
        private set
    var drawing by mutableStateOf(false)
        private set
    val drawPoints = mutableStateListOf<RoiPoint>()

    // History view.
    var filter by mutableStateOf(HistFilter.ALL)
    /** The date range of the History page. Null means no limit on that side. */
    var dateFrom by mutableStateOf<LocalDate?>(null)
        private set
    var dateTo by mutableStateOf<LocalDate?>(null)
        private set
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
            pi.testConnection(addr)
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

    /** Continue after a successful test: start following the real Pi. */
    fun finishSetup(firstTime: Boolean = true) {
        demo = false
        AppGraph.mute = { repo.mute(it) }
        AppGraph.connect(address)
        active.value = if (AppGraph.mode.value == LinkMode.CLOUD) AppGraph.cloud else pi
        if (address.isNotEmpty()) prefs.edit().putString(AppGraph.KEY_ADDRESS, address).putBoolean(KEY_CLOUD_ONLY, false).apply()
        if (notifications == PermState.ON) WatchService.start(appContext)
        enterApp()
    }

    /** Skip the Pi and run on invented data. */
    fun useDemo() {
        AppGraph.disconnect()
        WatchService.stop(appContext)
        prefs.edit().remove(AppGraph.KEY_ADDRESS).putBoolean(KEY_CLOUD_ONLY, false).apply()
        demo = true
        active.value = fake
        AppGraph.mute = { fake.mute(it) }
        enterApp()
    }

    private fun enterApp() {
        setupDone = true
        everConnected = true
        tab = Tab.MONITOR
        loading = true
        viewModelScope.launch {
            delay(1100)
            loading = false
            // Ask once, ever. After that the Alerts card in Settings is the way in.
            if (!askedForAlerts && !prefs.getBoolean(KEY_ASKED, false) && notifications != PermState.ON) {
                askedForAlerts = true
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
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
        if (demo) {
            fake.demoSetConn(ConnState.ONLINE)
            showToast("Connected")
        } else if (remote) {
            showToast("Waiting for the Pi to come back online")
        } else viewModelScope.launch {
            showToast(if (pi.refreshNow()) "Connected" else "Still cannot reach the Pi")
        }
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

    /** True from the tap on Reconnect until the picture is back, or we give up. */
    var reconnecting by mutableStateOf(false)
        private set

    fun reconnectCamera() {
        if (reconnecting) return
        reconnecting = true
        viewModelScope.launch {
            repo.reconnectCamera()
            showToast("Reconnecting camera")
            delay(2500) // let the old stream drop before judging the new one
            var waited = 0
            while (state.value.conn != ConnState.ONLINE && waited < 20) {
                delay(1000)
                waited++
            }
            reconnecting = false
            showToast(if (state.value.conn == ConnState.ONLINE) "Camera is back" else "Camera still not answering")
        }
    }

    /** Restarts detection, camera and server on the Pi. Settings and history are kept. */
    // ---------- Pi power: screen saver, wake, auto shutdown, shut down ----------

    /** Edits in the Pi power card that are not saved yet. Null means "as on the Pi". */
    var draftScreenMode by mutableStateOf<String?>(null)
    var draftScreenTimeout by mutableStateOf<Int?>(null)
    var draftScreenBattery by mutableStateOf<Int?>(null)
    var draftScreenDim by mutableStateOf<Int?>(null)
    var draftShutdownPct by mutableStateOf<Int?>(null)

    val powerDirty: Boolean
        get() = draftScreenMode != null || draftScreenTimeout != null || draftScreenBattery != null ||
            draftScreenDim != null || draftShutdownPct != null

    fun savePower() {
        val mode = draftScreenMode
        val timeout = draftScreenTimeout
        val onBattery = draftScreenBattery
        val dim = draftScreenDim
        val pct = draftShutdownPct
        launchAction("Power settings saved") {
            if (mode != null || timeout != null || onBattery != null || dim != null) {
                repo.setScreenSaver(mode, timeout, onBattery, dim, null)
            }
            if (pct != null) repo.setAutoShutdown(null, pct)
            draftScreenMode = null
            draftScreenTimeout = null
            draftScreenBattery = null
            draftScreenDim = null
            draftShutdownPct = null
        }
    }

    fun toggleWakeOnAlert() {
        val on = !state.value.screenWakeOnAlert
        launchAction(if (on) "Alerts wake the Pi screen" else "Alerts leave the Pi screen asleep") {
            repo.setScreenSaver(null, null, null, null, on)
        }
    }

    fun toggleAutoShutdown() {
        val on = battery.value.autoShutdown != true
        launchAction(if (on) "Auto shutdown on" else "Auto shutdown off") { repo.setAutoShutdown(on, null) }
    }

    fun wakePi() = launchAction("Waking the Pi screen") { repo.screen("wake") }

    fun sleepPi() = launchAction("Pi screen going to sleep") { repo.screen("sleep") }

    fun cancelShutdown() {
        viewModelScope.launch {
            showToast(if (repo.cancelShutdown()) "Shutdown cancelled" else "This shutdown cannot be cancelled")
        }
    }

    fun shutdownPi() {
        overlay = null
        launchAction("The Pi is shutting down") { repo.shutdown() }
    }

    fun restartPi() {
        overlay = null
        launchAction("Restarting Garbage-Guard on the Pi") { repo.restart() }
    }

    var net by mutableStateOf<NetDetails?>(null)
        private set
    var netLoading by mutableStateOf(false)
        private set

    fun openNetwork() {
        overlay = Overlay.Network
        loadNetwork()
    }

    fun loadNetwork() {
        if (netLoading) return
        netLoading = true
        viewModelScope.launch {
            net = repo.networkDetails()
            netLoading = false
        }
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
        if (granted && setupDone && !demo) WatchService.start(appContext)
        showToast(if (granted) "Alerts are on" else "Alerts are off")
    }

    /** The sheet's button opens Android's own prompt; the result is read back in refreshSystemState. */
    fun closeBatterySheet() {
        overlay = null
    }

    fun toggleTheme(darkNow: Boolean) = chooseTheme(if (darkNow) ThemeMode.LIGHT else ThemeMode.DARK)

    // ---------- history ----------

    val rangeOn: Boolean get() = dateFrom != null || dateTo != null

    fun chooseFilter(f: HistFilter) {
        filter = f
        shown = 6
        swipedId = null
    }

    /** Sets the first day. A day after To pulls To along, so the range never runs backwards. */
    fun pickFrom(day: LocalDate?) {
        val d = day?.coerceAtMost(LocalDate.now())
        dateFrom = d
        val end = dateTo
        if (d != null && end != null && end.isBefore(d)) dateTo = d
        shown = 6
    }

    /** Sets the last day. A day before From pulls From along. */
    fun pickTo(day: LocalDate?) {
        val d = day?.coerceAtMost(LocalDate.now())
        dateTo = d
        val start = dateFrom
        if (d != null && start != null && start.isAfter(d)) dateFrom = d
        shown = 6
    }

    /** Today and the days before it, [days] in all. */
    fun lastDays(days: Int) {
        val today = LocalDate.now()
        dateTo = today
        dateFrom = today.minusDays((days - 1).toLong())
        shown = 6
    }

    fun clearDates() {
        dateFrom = null
        dateTo = null
        shown = 6
    }

    private fun dayOf(row: AlertRow): LocalDate? = runCatching { LocalDate.parse(row.timestamp.take(10)) }.getOrNull()

    private fun inRange(row: AlertRow): Boolean {
        if (!rangeOn) return true
        val d = dayOf(row) ?: return false
        val start = dateFrom
        val end = dateTo
        return (start == null || !d.isBefore(start)) && (end == null || !d.isAfter(end))
    }

    /** The records the filter and the date range leave on the page. */
    fun visibleRows(all: List<AlertRow>): List<AlertRow> = all.filter { filter.matches(it) && inRange(it) }

    private fun short(d: LocalDate): String {
        val month = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        return if (d.year == LocalDate.now().year) "${d.dayOfMonth} $month" else "${d.dayOfMonth} $month ${d.year}"
    }

    /** The date chip: "Any date", "12 Oct", "10 Oct to 12 Oct", "From 10 Oct" or "Up to 12 Oct". */
    fun rangeLabel(): String {
        val start = dateFrom
        val end = dateTo
        return when {
            start == null && end == null -> "Any date"
            start != null && end != null && start == end -> short(start)
            start != null && end != null -> "${short(start)} to ${short(end)}"
            start != null -> "From ${short(start)}"
            else -> "Up to ${short(end!!)}"
        }
    }

    /** What Mark read and Delete will act on, in words: "all 7 records", "3 alerts from 10 Oct to 12 Oct". */
    fun scopeWords(count: Int): String {
        val one = count == 1
        val what = when (filter) {
            HistFilter.ALL -> if (one) "record" else "records"
            HistFilter.ALERTS -> if (one) "alert" else "alerts"
            HistFilter.CAPTURES -> if (one) "capture" else "captures"
            HistFilter.TESTS -> if (one) "test" else "tests"
            HistFilter.MUTED -> if (one) "mute record" else "mute records"
            HistFilter.POWER -> if (one) "power record" else "power records"
        }
        val start = dateFrom
        val end = dateTo
        val days = when {
            start == null && end == null -> ""
            start != null && end != null && start == end -> " on ${short(start)}"
            start != null && end != null -> " from ${short(start)} to ${short(end)}"
            start != null -> " since ${short(start)}"
            else -> " up to ${short(end!!)}"
        }
        val all = if (filter == HistFilter.ALL && !rangeOn && count != 1) "all " else ""
        return "$all$count $what$days"
    }

    fun toggleRead(row: AlertRow) = launchAction { repo.markRead(listOf(row.id), !row.read) }

    /** Marks read the unread records on the page, which is every record when nothing is filtered. */
    fun markVisibleRead() {
        val rows = visibleRows(alerts.value).filter { !it.read }
        if (rows.isEmpty()) return
        val everything = filter == HistFilter.ALL && !rangeOn
        launchAction(if (everything) "All marked read" else "${rows.size} marked read") {
            if (everything) repo.markRead(null, true) else repo.markRead(rows.map { it.id }, true)
        }
    }

    fun refreshHistory() = showToast("History refreshed")

    /**
     * Deletes one record, or with no id the records on the page. With no
     * filter that is everything; with only dates the Pi deletes the whole
     * range, including records older than the app has loaded.
     */
    fun confirmDelete() {
        val target = (overlay as? Overlay.Delete) ?: return
        val rows = visibleRows(alerts.value)
        overlay = null
        swipedId = null
        launchAction("Deleted") {
            when {
                target.id != null -> repo.deleteAlerts(listOf(target.id))
                filter == HistFilter.ALL && !rangeOn -> repo.deleteAll()
                filter == HistFilter.ALL -> repo.deleteRange(dateFrom?.toString().orEmpty(), dateTo?.toString().orEmpty())
                rows.isNotEmpty() -> repo.deleteAlerts(rows.map { it.id })
            }
        }
    }

    companion object {
        const val DEFAULT_ADDRESS = "192.168.1.101:8080"
        private const val KEY_ASKED = "asked_alerts"
        private const val KEY_CLOUD_ONLY = "cloud_only"
        private const val KEY_SOUNDS = "ui_sounds"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_AWAKE = "keep_screen_on"
    }
}
