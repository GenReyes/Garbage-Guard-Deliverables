package com.garbageguard.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.garbageguard.app.ui.components.AppHeader
import com.garbageguard.app.ui.components.BottomNav
import com.garbageguard.app.ui.components.GgToast
import com.garbageguard.app.ui.components.ggBackground
import com.garbageguard.app.ui.screens.FullscreenScreen
import com.garbageguard.app.ui.screens.HistoryScreen
import com.garbageguard.app.ui.screens.MonitorScreen
import com.garbageguard.app.ui.screens.OverlayHost
import com.garbageguard.app.ui.screens.SettingsScreen
import com.garbageguard.app.ui.screens.SetupScreen
import com.garbageguard.app.ui.theme.GarbageGuardTheme
import com.garbageguard.app.ui.theme.Gg
import com.garbageguard.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay

@Composable
fun GgApp(vm: AppViewModel) {
    GarbageGuardTheme(mode = vm.themeMode) {
        val c = Gg.colors
        val state by vm.state.collectAsState()
        val alerts by vm.alerts.collectAsState()
        val context = LocalContext.current

        // Status and navigation bar icons follow the app theme, not the phone's.
        LaunchedEffect(c.isDark) {
            val transparent = android.graphics.Color.TRANSPARENT
            val style = if (c.isDark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
            (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        // The real Android notification permission (Android 13 and newer).
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.setNotifications(granted)
        }
        LaunchedEffect(Unit) {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) vm.notifications = PermState.ON
        }

        LaunchedEffect(vm.toastSeq) {
            if (vm.toast != null) {
                delay(1900)
                vm.clearToast()
            }
        }

        CompositionLocalProvider(LocalContentColor provides c.text) {
            Box(Modifier.fillMaxSize().ggBackground(c)) {
                if (vm.fullscreen) {
                    FullscreenScreen(vm, state)
                } else {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        if (!vm.setupDone) {
                            SetupScreen(vm)
                        } else {
                            AppHeader(
                                conn = state.conn,
                                onToggleTheme = { vm.themeMode = if (c.isDark) ThemeMode.LIGHT else ThemeMode.DARK },
                                onPillLongPress = { if (vm.demo) vm.demoCycleConn() },
                            )
                            Box(Modifier.weight(1f)) {
                                when (vm.tab) {
                                    Tab.MONITOR -> MonitorScreen(vm, state)
                                    Tab.HISTORY -> HistoryScreen(vm, alerts)
                                    Tab.SETTINGS -> SettingsScreen(vm, state)
                                }
                            }
                            BottomNav(vm.tab, alerts.count { !it.read }, vm::selectTab)
                        }
                    }
                }

                OverlayHost(
                    vm, alerts,
                    onAllowAlerts = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            vm.overlay = null
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.setNotifications(true)
                    },
                )

                vm.toast?.let { GgToast(it, bottom = if (vm.fullscreen || !vm.setupDone) 24.dp else 86.dp) }
            }
        }
    }
}
