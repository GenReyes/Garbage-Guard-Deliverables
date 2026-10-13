package com.garbageguard.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.garbageguard.app.ui.theme.ThemeMode
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.notify.AppGraph
import com.garbageguard.app.ui.GgApp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay upright; only fullscreen and full-size views turn sideways.
        // Tablets may be held either way and the pages follow.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        // Android lays a grey veil behind its three buttons by default. The tabs
        // are drawn right down to the edge, so the veil is switched off and the
        // buttons sit on the tabs themselves.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { GgApp(vm) }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.foreground = true
    }

    override fun onStop() {
        // Still polling through the watch service, but no longer on screen.
        AppGraph.foreground = false
        syncIcon()
        super.onStop()
    }

    /**
     * Makes the home-screen icon match the theme the app was left in: the
     * dark logo for dark, the light logo for light. It runs as the app leaves
     * the screen, because some launchers redraw the icon at that moment and
     * a change made while the app is open can close it.
     */
    private fun syncIcon() {
        val dark = when (vm.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        fun switch(name: String, on: Boolean) {
            val entry = ComponentName(this, "com.garbageguard.app.$name")
            val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (packageManager.getComponentEnabledSetting(entry) != want) {
                packageManager.setComponentEnabledSetting(entry, want, PackageManager.DONT_KILL_APP)
            }
        }
        runCatching {
            // The new entry goes on before the old one goes off, so there is always one.
            switch(if (dark) "LauncherDark" else "LauncherLight", true)
            switch(if (dark) "LauncherLight" else "LauncherDark", false)
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up changes made in Android's own settings screens.
        vm.refreshSystemState()
    }
}
