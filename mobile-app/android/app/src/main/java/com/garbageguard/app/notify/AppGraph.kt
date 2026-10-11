package com.garbageguard.app.notify

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID
import com.garbageguard.app.data.PiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The parts that must outlive the screen. The connection to the Pi lives
 * here rather than in the ViewModel, so the watch service can keep it
 * polling, and alerts keep arriving, after the app is closed.
 */
object AppGraph {
    const val PREFS = "gg"
    const val KEY_ADDRESS = "pi_address"
    const val KEY_THEME = "theme_mode"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val pi = PiRepository(scope)

    /** Set by whoever currently owns the data source, so the notification's Mute button can reach it. */
    @Volatile
    var mute: (suspend (Int) -> Unit)? = { pi.mute(it) }

    private var started = false
    private const val KEY_CLIENT_ID = "client_id"

    /** This phone's id as the Pi sees it. */
    var clientId: String = ""
        private set
    var appVersion: String = ""
        private set

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Alerts.ensureChannels(app)

        // A random id made once, so the Pi can tell two phones apart behind one address.
        val store = prefs(app)
        clientId = store.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            store.edit().putString(KEY_CLIENT_ID, it).apply()
        }
        appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        pi.identity = mapOf(
            "X-GG-Client-Id" to clientId,
            "X-GG-Device" to if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim(),
            "X-GG-OS" to "Android ${Build.VERSION.RELEASE}",
            "X-GG-App-Version" to appVersion,
        )
        scope.launch {
            // Only lists that really came from the Pi count; the empty list
            // before the first answer must not become the starting point.
            pi.alerts.combine(pi.loaded) { rows, loaded -> if (loaded) rows else null }.collect { rows ->
                if (rows != null) Alerts.onRows(app, "pi:${pi.address}", rows, scope) { pi.snapshotUrl(it) }
            }
        }
    }
}
