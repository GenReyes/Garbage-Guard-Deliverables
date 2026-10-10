package com.garbageguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.GgApp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GgApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // Picks up changes made in Android's own settings screens.
        vm.refreshSystemState()
    }
}
