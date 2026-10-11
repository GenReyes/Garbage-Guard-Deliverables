package com.garbageguard.app.notify

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.garbageguard.app.data.CloudRepository
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.FirebaseConfig
import com.garbageguard.app.data.LinkMode
import com.garbageguard.app.data.PiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The parts that must outlive the screen. Both connections to the Pi live
 * here rather than in the ViewModel, so the watch service can keep them
 * going, and alerts keep arriving, after the app is closed.
 *
 * It also decides which connection is in use. The rule from FIREBASE.md:
 * the same Wi-Fi always comes first. Firebase is used only when no local
 * address answers, and the app goes back to Wi-Fi as soon as one does.
 */
object AppGraph {
    const val PREFS = "gg"
    const val KEY_ADDRESS = "pi_address"
    const val KEY_THEME = "theme_mode"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val pi = PiRepository(scope)
    val cloud = CloudRepository(scope, pi)

    /** Which connection the app is using right now. */
    val mode = MutableStateFlow(LinkMode.LOCAL)

    /** Short messages for the screen, such as a change of connection. */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Set by whoever currently owns the data source, so the notification's Mute button can reach it. */
    @Volatile
    var mute: (suspend (Int) -> Unit)? = { pi.mute(it) }

    private var started = false
    private const val KEY_CLIENT_ID = "client_id"
    private lateinit var app: Context
    private var linkJob: Job? = null
    private var typed = ""
    @Volatile private var recheck = false

    /** This phone's id as the Pi sees it. */
    var clientId: String = ""
        private set
    var appVersion: String = ""
        private set

    val linked: Boolean get() = linkJob?.isActive == true

    /** True while the app is on screen. Both connections report it to the Pi. */
    var foreground: Boolean
        get() = pi.foreground
        set(value) {
            pi.foreground = value
            cloud.foreground = value
        }

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        Alerts.ensureChannels(app)

        // A random id made once, so the Pi can tell two phones apart behind one address.
        val store = prefs(app)
        clientId = store.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            store.edit().putString(KEY_CLIENT_ID, it).apply()
        }
        appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val device = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        val os = "Android ${Build.VERSION.RELEASE}"
        pi.identity = mapOf(
            "X-GG-Client-Id" to clientId,
            "X-GG-Device" to device,
            "X-GG-OS" to os,
            "X-GG-App-Version" to appVersion,
        )
        cloud.clientId = clientId
        cloud.about = mapOf("device" to device, "os" to os, "app_version" to appVersion)
        cloud.config = CloudStore.current(app)
        cloud.onNotice = { notices.tryEmit(it) }
        com.garbageguard.app.ui.components.RemoteImages.cloud = { path -> cloud.image(path) }

        scope.launch {
            // Only lists that really came from the Pi count; the empty list
            // before the first answer must not become the starting point.
            pi.alerts.combine(pi.loaded) { rows, loaded -> if (loaded) rows else null }.collect { rows ->
                if (rows != null && mode.value == LinkMode.LOCAL) {
                    Alerts.onRows(app, "pi", rows, scope, pi.foreground) { pi.snapshotUrl(it) }
                }
            }
        }
        scope.launch {
            // The same history through Firebase. One shared source name, so
            // changing connection never replays alerts already announced.
            cloud.alerts.combine(cloud.loaded) { rows, loaded -> if (loaded) rows else null }.collect { rows ->
                if (rows != null && mode.value == LinkMode.CLOUD) {
                    Alerts.onRows(app, "pi", rows, scope, cloud.foreground) { cloud.snapshotUrl(it) }
                }
            }
        }
        scope.launch {
            cloud.localUrls.collect { CloudStore.rememberUrls(app, it) }
        }
        // A change of network (joining or leaving the Pi's Wi-Fi) is the moment to look again.
        runCatching {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    recheck = true
                }

                override fun onLost(network: Network) {
                    recheck = true
                }
            })
        }
    }

    val cloudReady: Boolean get() = cloud.config.complete

    /** Saves new Firebase settings and puts them to use at once. */
    fun useCloudConfig(cfg: FirebaseConfig, passedTest: Boolean) {
        CloudStore.save(app, cfg, passedTest)
        cloud.config = cfg
        recheck = true
    }

    /**
     * Starts following the Pi. [address] is what the user typed, and may be
     * blank when they are setting up away from the Pi with Firebase only.
     */
    fun connect(address: String) {
        typed = address.trim()
        linkJob?.cancel()
        cloud.stop()
        if (typed.isNotEmpty()) {
            mode.value = LinkMode.LOCAL
            pi.start(typed)
        } else {
            pi.stop()
            mode.value = LinkMode.CLOUD
            cloud.start()
        }
        linkJob = scope.launch { supervise() }
    }

    fun disconnect() {
        linkJob?.cancel()
        linkJob = null
        pi.stop()
        cloud.stop()
        mode.value = LinkMode.LOCAL
    }

    /** Every local address worth trying: the typed one first, then those the Pi published. */
    private fun candidates(): List<String> =
        (listOf(typed) + cloud.localUrls.value + CloudStore.knownUrls(app)).filter { it.isNotBlank() }.distinct()

    private suspend fun firstLocalThatAnswers(): String? {
        for (address in candidates()) if (pi.probe(address)) return address
        return null
    }

    private suspend fun supervise() {
        var downFor = 0
        var sinceProbe = 0
        while (scope.isActive) {
            delay(1000)
            val nudge = recheck
            recheck = false
            if (mode.value == LinkMode.LOCAL) {
                downFor = if (pi.state.value.conn == ConnState.NO_PI) downFor + 1 else 0
                // Lost for a few seconds, or the network just changed: look for the Pi elsewhere.
                if (downFor >= 6 || (nudge && downFor >= 2)) {
                    downFor = 0
                    val found = firstLocalThatAnswers()
                    when {
                        found != null -> if (found != pi.address) pi.start(found)
                        cloudReady -> {
                            pi.stop()
                            cloud.start()
                            mode.value = LinkMode.CLOUD
                            sinceProbe = 0
                            notices.tryEmit("Away from the Pi. Using Firebase.")
                        }
                    }
                }
            } else {
                sinceProbe++
                if (sinceProbe >= 30 || nudge) {
                    sinceProbe = 0
                    val found = firstLocalThatAnswers()
                    if (found != null) {
                        cloud.stop()
                        pi.start(found)
                        mode.value = LinkMode.LOCAL
                        notices.tryEmit("Back on the Pi's Wi-Fi.")
                    }
                }
            }
        }
    }
}