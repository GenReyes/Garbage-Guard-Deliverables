package com.garbageguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.Battery
import com.garbageguard.app.data.CloudUsage
import com.garbageguard.app.data.ConnState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.data.rearmFor
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.PermState
import com.garbageguard.app.ui.components.AutoSwitch
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Segmented
import com.garbageguard.app.ui.components.Stepper
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.accentOf
import com.garbageguard.app.ui.components.Tag
import com.garbageguard.app.ui.components.Tone
import com.garbageguard.app.ui.theme.Gg
import com.garbageguard.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: AppViewModel, state: LiveState) {
    val c = Gg.colors
    val savedConf = Math.round(state.conf * 100)
    val conf = vm.draftConf ?: savedConf
    val limit = vm.draftLimit ?: state.threshold
    val dirty = conf != savedConf || limit != state.threshold
    val hasArea = state.roi.size >= 3
    val battery by vm.battery.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GgCard {
            Hd(GgIcons.Gear, "Detection settings") {
                if (dirty) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Ico(GgIcons.Warn, size = 13.dp, tint = c.warnText)
                        T("UNSAVED", size = 0.74f, weight = FontWeight.Black, color = c.warnText, spacing = 0.05f)
                    }
                }
            }
            Field(GgIcons.Eye, "Detection confidence") {
                Stepper(conf, "%", onMinus = { vm.step(conf = true, delta = -5) }, onPlus = { vm.step(conf = true, delta = 5) })
                Hint("40% is the tested default.", icon = GgIcons.Ok)
            }
            Field(GgIcons.Bell, "Accumulation limit") {
                Stepper(limit, "items", onMinus = { vm.step(conf = false, delta = -1) }, onPlus = { vm.step(conf = false, delta = 1) })
                Hint("Alert fires here, resets below ${rearmFor(limit).coerceAtLeast(1)}.", icon = GgIcons.Warn)
            }
            Pair2 {
                GgButton("Save", vm::saveSettings, Modifier.weight(1f), icon = GgIcons.Save, kind = BtnKind.INK)
                GgButton("Test alert", vm::testAlert, Modifier.weight(1f), icon = GgIcons.Bell)
            }
        }

        GgCard {
            Hd(GgIcons.Poly, "Monitored area") {
                Tag(if (hasArea) "Saved" else "Whole frame", if (hasArea) Tone.OK else Tone.WARN)
            }
            Pair2 {
                GgButton("Draw", { vm.startDrawing(edit = false) }, Modifier.weight(1f), icon = GgIcons.Pen, kind = BtnKind.TEAL)
                GgButton("Edit", { vm.startDrawing(edit = true) }, Modifier.weight(1f), icon = GgIcons.Edit, enabled = hasArea)
            }
            GgButton(
                "Clear area", { vm.overlay = Overlay.ClearArea }, Modifier.fillMaxWidth(),
                icon = GgIcons.Eraser, enabled = hasArea,
            )
            Hint(
                "Tap the video to outline the water, 3 points or more.",
                icon = GgIcons.Poly,
            )
        }

        PowerCard(vm, state, battery)

        GgCard {
            Hd(GgIcons.Bell, "Alerts")
            val granted = vm.notifications == PermState.ON
            SettingRow("Notifications", "For alerts while the app is closed", GgIcons.Bell) {
                Tag(
                    when (vm.notifications) { PermState.ON -> "On"; PermState.BLOCKED -> "Blocked"; PermState.OFF -> "Off" },
                    if (granted) Tone.OK else Tone.BAD,
                )
                if (!granted) GgButton("Turn on", { vm.overlay = Overlay.NotifPrompt }, small = true)
            }
            SettingRow("Battery", "Android may put the app to sleep", GgIcons.Battery) {
                Tag(
                    if (vm.batteryUnrestricted) "Unrestricted" else "Restricted",
                    if (vm.batteryUnrestricted) Tone.OK else Tone.WARN,
                )
                if (!vm.batteryUnrestricted) GgButton("Fix", { vm.overlay = Overlay.Battery }, small = true)
            }
        }

        GgCard {
            Hd(GgIcons.Activity, "Sound and feel")
            SettingRow("Button sounds", "Soft clicks on keys, switches and tabs", GgIcons.Signal) {
                AutoSwitch(vm.uiSounds, vm::toggleUiSounds, label = null, icon = null)
            }
            SettingRow("Vibration", "A light tick under the finger", GgIcons.Phone) {
                AutoSwitch(vm.haptics, vm::toggleHaptics, label = null, icon = null)
            }
            SettingRow("Keep screen on", "Stops the phone from dimming", GgIcons.Sun) {
                AutoSwitch(vm.keepScreenOn, vm::toggleKeepScreenOn, label = null, icon = null)
            }
            SettingRow("Alert sounds", "Each kind of alert has its own sound", GgIcons.Bell) {
                GgButton("Adjust", { vm.openAlertSounds() }, small = true, icon = GgIcons.Gear)
            }
            Hint(
                "Each alert type has its own sound, even in the background.",
                icon = GgIcons.Ok,
            )
        }

        GgCard {
            Hd(GgIcons.Moon, "Appearance")
            Segmented(
                listOf("Auto", "Light", "Dark"),
                icons = listOf(GgIcons.SunMoon, GgIcons.Sun, GgIcons.Moon),
                selected = ThemeMode.entries.indexOf(vm.themeMode),
                onSelect = { vm.chooseTheme(ThemeMode.entries[it]) },
            )
        }

        GgCard {
            Hd(GgIcons.Link, "Connection")
            SettingRow("Pi address", if (vm.demo) "Demo data, no Pi" else vm.address.ifEmpty { "Not set, using Firebase" }, GgIcons.Link) {
                GgButton("Change", vm::changeAddress, small = true)
            }
            SettingRow(
                "Camera",
                "CAM 01, " + when (state.conn) {
                    ConnState.ONLINE -> "online"
                    ConnState.NO_CAMERA -> "no signal"
                    ConnState.NO_PI -> "unreachable"
                },
                GgIcons.Cam,
            ) {
                GgButton(
                    if (vm.reconnecting) "Working" else "Reconnect", vm::reconnectCamera, small = true,
                    icon = GgIcons.Refresh, enabled = state.conn != ConnState.NO_PI && !vm.reconnecting,
                )
            }
            SettingRow("Network", if (vm.remote) "Only on the Pi's Wi-Fi" else "Wi-Fi, cables and addresses on the Pi", GgIcons.Signal) {
                GgButton("Details", vm::openNetwork, small = true, enabled = state.conn != ConnState.NO_PI && !vm.remote)
            }
            SettingRow(
                "Watching now",
                if (vm.remote) "Only on the Pi's Wi-Fi" else buildString {
                    append(plural(state.viewerApps, "phone"))
                    if (state.viewerBrowsers > 0) append(", ").append(plural(state.viewerBrowsers, "browser"))
                    if (state.reconnects > 0) append(" \u00B7 camera restarted ").append(plural(state.reconnects, "time"))
                },
                GgIcons.Phone,
            ) {
                GgButton(
                    "Details", { vm.overlay = Overlay.Viewers }, small = true, icon = GgIcons.Phone,
                    enabled = state.conn != ConnState.NO_PI && !vm.remote,
                )
            }
            SettingRow(
                "Garbage-Guard on the Pi",
                if (vm.remote) "Restart only on the Pi's Wi-Fi" else "Restarts detection, camera and server",
                GgIcons.Refresh,
            ) {
                GgButton(
                    "Restart", { vm.overlay = Overlay.Restart }, small = true, kind = BtnKind.DANGER,
                    enabled = state.conn != ConnState.NO_PI && !vm.remote,
                )
            }
            Hint("Garbage-Guard app ${vm.appVersion.ifEmpty { "2.0" }}")
        }

        // Firebase: the fallback for when the phone is away from the Pi's Wi-Fi.
        GgCard {
            val usageNow by vm.cloudUsage.collectAsState()
            val usage = state.cloudUsage ?: usageNow
            Hd(GgIcons.Cloud, "Firebase") {
                when {
                    vm.demo -> Tag("Demo", Tone.WARN)
                    vm.remote -> Tag("In use", Tone.WARN, GgIcons.Cloud)
                    vm.cloudReady -> Tag("Standby", Tone.OK)
                    else -> Tag("Not set up", Tone.WARN)
                }
            }
            SettingRow(
                "Link now",
                if (vm.demo) "Demo data, no Pi" else if (vm.remote) "Through Firebase, away from the Pi" else "Same Wi-Fi as the Pi",
                if (vm.remote) GgIcons.Cloud else GgIcons.Signal,
            ) {}
            SettingRow("Account", if (vm.cloudReady) "Database and account saved" else "Needed to use the app away", GgIcons.Key) {
                GgButton(if (vm.cloudReady) "Edit" else "Set up", vm::openFirebase, small = true, icon = GgIcons.Gear)
            }
            if (!vm.remote && state.cloudEnabled) {
                SettingRow(
                    "Pi sync",
                    if (state.cloudOk) "Synced, ${plural(state.cloudWatchers, "phone")} through Firebase" else "The Pi is not syncing",
                    GgIcons.Refresh,
                ) { Tag(if (state.cloudOk) "Synced" else "Off", if (state.cloudOk) Tone.OK else Tone.WARN) }
            }
            if (usage != null) UsageBar(usage)
            Hint("Wi-Fi comes first. Firebase gives a still, not video.", icon = GgIcons.Ok)
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/**
 * The free monthly Firebase allowance, as the Pi estimates it. Green, amber,
 * orange and red follow the Pi's own levels, and the figure is labelled
 * "about" because it can be off by a third.
 */
@Composable
private fun UsageBar(u: CloudUsage) {
    val c = Gg.colors
    val tint = when (u.level) { "warn" -> c.warn; "near" -> c.cloud; "bad" -> c.bad; else -> c.ok }
    val gb = 1024f * 1024f * 1024f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Ico(GgIcons.Gauge, size = 14.dp, tint = tint)
            T(
                "ABOUT %.2f GB OF %.2f GB THIS MONTH".format(u.downBytes / gb, u.limitBytes / gb),
                Modifier.weight(1f), size = 0.7f, color = c.text, tnum = true, spacing = 0.04f, fit = true,
            )
        }
        val frac = u.fraction.coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth().height(14.dp).neuWell(c, 7.dp)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac.coerceAtLeast(0.02f))
                    .background(tint),
            )
        }
        if (u.note != null) Hint(u.note, icon = GgIcons.Warn, wrap = true)
    }
}

/** .field */
@Composable
private fun Field(icon: ImageVector, label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Ico(icon, size = 14.dp, tint = accentOf(icon))
            T(label.uppercase(), size = 0.76f, spacing = 0.05f)
        }
        content()
    }
}

/** .pair */
@Composable
private fun Pair2(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** .row */
@Composable
private fun SettingRow(label: String, sub: String, icon: ImageVector, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(icon, size = 17.dp, tint = accentOf(icon))
        Column(Modifier.weight(1f)) {
            T(label.uppercase(), size = 0.84f, spacing = 0.04f, fit = true)
            T(sub, size = 0.78f, weight = FontWeight.Bold, color = Gg.colors.muted, fit = true)
        }
        trailing()
    }
}

/**
 * The Pi's power settings, the phone's twin of the dashboard's Power card:
 * the screen saver, the low-battery shutdown, and Shut down. Each part is
 * left out when the Pi does not report it, so an older Pi shows nothing odd.
 */
@Composable
private fun PowerCard(vm: AppViewModel, state: LiveState, battery: Battery) {
    val c = Gg.colors
    val hasScreen = state.screenMode != null
    val hasShutdown = battery.autoShutdown != null
    if (!hasScreen && !hasShutdown) return
    val reachable = state.conn != ConnState.NO_PI
    val poweringOff = battery.shutdownState == "powering_off"

    GgCard {
        Hd(GgIcons.Power, "Pi power") {
            if (vm.powerDirty) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Ico(GgIcons.Warn, size = 13.dp, tint = c.warnText)
                    T("UNSAVED", size = 0.74f, weight = FontWeight.Black, color = c.warnText, spacing = 0.05f)
                }
            } else if (hasScreen) {
                Tag("Screen ${screenWord(state.screen)}", if (state.screen == "on") Tone.OK else Tone.WARN, if (state.screen == "on") GgIcons.Sun else GgIcons.Moon)
            }
        }

        if (hasScreen) {
            val modes = listOf("off", "dim", "never")
            val mode = vm.draftScreenMode ?: state.screenMode ?: "off"
            Field(GgIcons.Moon, "Screen saver") {
                Segmented(
                    listOf("Off", "Dim", "Never"),
                    icons = listOf(GgIcons.Moon, GgIcons.SunMoon, GgIcons.Sun),
                    selected = modes.indexOf(mode).coerceAtLeast(0),
                    onSelect = { vm.draftScreenMode = modes[it].takeIf { m -> m != state.screenMode } },
                )
            }
            if (mode != "never") {
                val minutes = (vm.draftScreenTimeout ?: state.screenTimeoutS) / 60
                fun setMinutes(m: Int) {
                    vm.draftScreenTimeout = (m.coerceIn(1, 60) * 60).takeIf { it != state.screenTimeoutS }
                }
                Field(GgIcons.Clock, "Turn off after") {
                    Stepper(minutes, "min", onMinus = { setMinutes(minutes - 1) }, onPlus = { setMinutes(minutes + 1) })
                }
                val seconds = vm.draftScreenBattery ?: state.screenTimeoutBatteryS
                fun setSeconds(s: Int) {
                    vm.draftScreenBattery = s.coerceIn(30, 3600).takeIf { it != state.screenTimeoutBatteryS }
                }
                Field(GgIcons.Battery, "On battery, turn off after") {
                    Stepper(seconds, "sec", onMinus = { setSeconds(seconds - 30) }, onPlus = { setSeconds(seconds + 30) })
                }
            }
            if (mode == "dim") {
                val dim = vm.draftScreenDim ?: state.screenDimPct
                fun setDim(p: Int) {
                    vm.draftScreenDim = p.coerceIn(5, 50).takeIf { it != state.screenDimPct }
                }
                Field(GgIcons.SunMoon, "Dim level") {
                    Stepper(dim, "%", onMinus = { setDim(dim - 5) }, onPlus = { setDim(dim + 5) })
                }
            }
            SettingRow("Wake on alert", "A new alert wakes the Pi screen", GgIcons.Bell) {
                AutoSwitch(state.screenWakeOnAlert, vm::toggleWakeOnAlert, label = null, icon = null)
            }
        }

        if (hasShutdown) {
            val on = battery.autoShutdown == true
            SettingRow("Auto shutdown", "Shuts down cleanly on low battery", GgIcons.Battery) {
                AutoSwitch(on, vm::toggleAutoShutdown, label = null, icon = null)
            }
            if (on) {
                val pct = vm.draftShutdownPct ?: battery.autoShutdownPct
                fun setPct(p: Int) {
                    vm.draftShutdownPct = p.coerceIn(10, 50).takeIf { it != battery.autoShutdownPct }
                }
                Field(GgIcons.Gauge, "Shut down at") {
                    Stepper(pct, "%", onMinus = { setPct(pct - 5) }, onPlus = { setPct(pct + 5) })
                }
            }
        }

        Pair2 {
            GgButton("Save", vm::savePower, Modifier.weight(1f), icon = GgIcons.Save, kind = BtnKind.INK, enabled = vm.powerDirty && reachable)
            if (hasScreen) {
                if (state.screen == "on") GgButton("Sleep now", vm::sleepPi, Modifier.weight(1f), icon = GgIcons.Moon, enabled = reachable)
                else GgButton("Wake", vm::wakePi, Modifier.weight(1f), icon = GgIcons.Sun, kind = BtnKind.TEAL, enabled = reachable)
            }
        }
        GgButton(
            if (poweringOff) "Shutting down" else "Shut down the Pi", { vm.overlay = Overlay.Shutdown }, Modifier.fillMaxWidth(),
            icon = GgIcons.Power, kind = BtnKind.DANGER, enabled = reachable && !poweringOff,
        )
        if (state.screenNote != null) Hint(state.screenNote, icon = GgIcons.Warn, wrap = true)
        Hint("Touch the Pi screen to wake it. The first tap only wakes.", icon = GgIcons.Ok)
    }
}
