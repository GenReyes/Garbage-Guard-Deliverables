package com.garbageguard.app

import android.content.pm.ActivityInfo
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
        setContent { GgApp(vm) }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.foreground = true
    }

    override fun onStop() {
        // Still polling through the watch service, but no longer on screen.
        AppGraph.foreground = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Picks up changes made in Android's own settings screens.
        vm.refreshSystemState()
    }
}
