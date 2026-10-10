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
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.data.rearmFor
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.PermState
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
                        T("Unsaved", size = 0.8f, weight = FontWeight.Black, color = c.warnText)
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
                "Drawing opens the video full screen. Tap to outline the water, 3 points or more. Anything outside is ignored.",
                icon = GgIcons.Poly,
            )
        }

        GgCard {
            Hd(GgIcons.Bell, "Alerts")
            val granted = vm.notifications == PermState.ON
            SettingRow("Notifications", "Needed for alerts when the app is closed") {
                Tag(
                    when (vm.notifications) { PermState.ON -> "On"; PermState.BLOCKED -> "Blocked"; PermState.OFF -> "Off" },
                    if (granted) Tone.OK else Tone.BAD,
                )
                if (!granted) GgButton("Turn on", { vm.overlay = Overlay.NotifPrompt }, small = true)
            }
            SettingRow("Battery", "Samsung may put the app to sleep") {
                Tag(
                    if (vm.batteryUnrestricted) "Unrestricted" else "Restricted",
                    if (vm.batteryUnrestricted) Tone.OK else Tone.WARN,
                )
                if (!vm.batteryUnrestricted) GgButton("Fix", { vm.overlay = Overlay.Battery }, small = true)
            }
        }

        GgCard {
            Hd(GgIcons.Moon, "Appearance")
            Segmented(
                listOf("Auto", "Light", "Dark"),
                selected = ThemeMode.entries.indexOf(vm.themeMode),
                onSelect = { vm.themeMode = ThemeMode.entries[it] },
            )
        }

        GgCard {
            Hd(GgIcons.Link, "Connection")
            SettingRow("Pi address", if (vm.demo) "Demo data, no Pi" else vm.address) {
                GgButton("Change", vm::changeAddress, small = true)
            }
            SettingRow(
                "Camera",
                "CAM 01, " + when (state.conn) {
                    ConnState.ONLINE -> "online"
                    ConnState.NO_CAMERA -> "no signal"
                    ConnState.NO_PI -> "unreachable"
                },
            ) {}
            Hint("Garbage-Guard app 1.0")
        }
    }
}

/** .field */
@Composable
private fun Field(icon: ImageVector, label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Ico(icon, size = 14.dp, tint = Gg.colors.muted)
            T(label, size = 0.82f)
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
private fun SettingRow(label: String, sub: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(label, size = 0.92f)
            T(sub, size = 0.78f, weight = FontWeight.Bold, color = Gg.colors.muted)
        }
        trailing()
    }
}
