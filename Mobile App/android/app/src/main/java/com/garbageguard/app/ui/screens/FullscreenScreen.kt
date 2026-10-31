package com.garbageguard.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.notify.Sfx
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.AlertFlag
import com.garbageguard.app.ui.components.BrandLogo
import com.garbageguard.app.ui.components.FeedCanvas
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Led
import com.garbageguard.app.ui.components.OfflinePanel
import com.garbageguard.app.ui.components.RailChip
import com.garbageguard.app.ui.components.RailGauge
import com.garbageguard.app.ui.components.RailGroup
import com.garbageguard.app.ui.components.RailKey
import com.garbageguard.app.ui.components.RailLabel
import com.garbageguard.app.ui.components.RailNote
import com.garbageguard.app.ui.components.RailReadout
import com.garbageguard.app.ui.components.RailState
import com.garbageguard.app.ui.components.WideMonitor
import com.garbageguard.app.ui.components.feedAspect
import com.garbageguard.app.ui.components.pressable
import com.garbageguard.app.ui.components.rememberLiveFrame
import com.garbageguard.app.ui.components.scanLines
import com.garbageguard.app.ui.theme.Gg
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Turns the phone sideways and hides the system bars for as long as it is on screen. */
@Composable
fun LandscapeImmersive() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val before = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = before
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * The feed turned sideways, with the picture as large as the phone allows.
 * There is no bar above or below it: the picture takes the full height at
 * the camera's own shape, with one panel either side of it, readings on the
 * left and keys on the right. Only the monitor is reversed against the
 * theme, as on the Pi's dashboard; the panels follow the page. Tapping the
 * picture hides them. The same screen is used to draw the monitored area.
 */
@Composable
fun FullscreenScreen(vm: AppViewModel, state: LiveState) {
    val c = Gg.colors
    LandscapeImmersive()
    BackHandler { if (vm.drawing) vm.stopDrawing() else vm.closeFullscreen() }

    val view = LocalView.current
    val online = state.conn == ConnState.ONLINE
    val level = levelOf(state)
    val p = percentOf(state)
    val image = rememberLiveFrame(vm.frameUrl)
    var rails by rememberSaveable { mutableStateOf(true) }
    val tone = when (level.lv) { Lv.OK -> c.ok; Lv.WARN -> c.warn; Lv.BAD -> c.bad; Lv.GRAY -> c.muted }

    WideMonitor(
        aspect = feedAspect(image),
        rails = rails || vm.drawing,
        modifier = Modifier.fillMaxSize(),
        left = {
            RailGroup {
                BrandLogo(36.dp, onLongHold = vm::openGame)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Led(online)
                    if (vm.remote) Ico(GgIcons.Cloud, size = 11.dp, tint = c.cloud)
                    RailLabel(if (vm.remote) "REMOTE" else "CAM 01")
                }
            }
            if (vm.drawing) {
                RailGroup {
                    RailLabel("DRAW AREA", GgIcons.Target, c.cloud)
                    RailReadout("${vm.drawPoints.size}", "POINTS", GgIcons.Layers, c.bottle)
                }
                RailGroup { RailNote("Tap the picture to outline the water. Apply when the shape is closed.") }
            } else {
                RailGauge(p / 100f, tone, "${state.smoothed}", "OF ${state.threshold}")
                RailGroup {
                    RailState(
                        icon = level.icon,
                        label = level.label,
                        sub = if (state.alertActive && state.muted) muteLeftLabel(state.muteLeft) else null,
                        fill = when (level.lv) { Lv.OK -> c.okTint; Lv.WARN -> c.warnTint; Lv.BAD -> c.bad; Lv.GRAY -> c.base2 },
                        ink = when (level.lv) { Lv.BAD -> c.onBad; else -> c.text },
                        pulse = state.alertActive && !state.muted,
                    )
                }
                RailGroup {
                    RailChip(GgIcons.Bottle, c.bottle, "${state.bottle}")
                    RailChip(GgIcons.Bag, c.bag, "${state.bag}")
                    RailChip(GgIcons.Container, c.container, "${state.foodContainer}")
                }
            }
        },
        right = {
            if (vm.drawing) {
                RailGroup { RailKey(GgIcons.X, "CANCEL", vm::stopDrawing, sound = Sfx.BACK) }
                RailGroup {
                    RailKey(GgIcons.Undo, "UNDO", { if (vm.drawPoints.isNotEmpty()) vm.drawPoints.removeAt(vm.drawPoints.lastIndex) }, sound = Sfx.POINT_UNDO)
                    RailKey(GgIcons.Eraser, "CLEAR", { vm.drawPoints.clear() }, sound = Sfx.DANGER)
                    // An outline needs three points; with fewer, Apply only refuses.
                    RailKey(GgIcons.Check, "APPLY", vm::applyArea, lit = true, lock = "area",
                        sound = if (vm.drawPoints.size < 3) Sfx.DENIED else Sfx.CONFIRM)
                }
            } else {
                RailGroup {
                    // Two small keys share the top row: leave fullscreen, and switch the theme.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RailKey(if (c.isDark) GgIcons.Sun else GgIcons.Moon, null, { vm.toggleTheme(c.isDark) }, Modifier.weight(1f),
                            sound = if (c.isDark) Sfx.THEME_LIGHT else Sfx.THEME_DARK)
                        RailKey(GgIcons.Shrink, null, vm::closeFullscreen, Modifier.weight(1f), sound = Sfx.BACK)
                    }
                }
                // The monitored area can be drawn from here as well as from Settings.
                RailGroup {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RailKey(GgIcons.Pen, "DRAW", { vm.startDrawing(edit = false) }, Modifier.weight(1f), sound = Sfx.OPEN)
                        RailKey(GgIcons.Edit, "EDIT", { vm.startDrawing(edit = true) }, Modifier.weight(1f),
                            enabled = state.roi.isNotEmpty(), sound = Sfx.OPEN)
                    }
                }
                RailGroup {
                    RailReadout(if (online) "%.1f".format(state.fps) else "--", "FPS", GgIcons.Activity, c.container)
                    RailReadout(if (state.conn == ConnState.NO_PI) "--" else "${state.latencyMs.roundToInt()}", "MS", GgIcons.Clock, c.bag)
                }
                RailGroup {
                    RailKey(GgIcons.BellAuto, if (state.autoMute) "AUTO ON" else "AUTO OFF", vm::toggleAutoMute, lit = state.autoMute, toggle = true, lock = "automute")
                    if (state.alertActive) {
                        if (state.muted) RailKey(GgIcons.Bell, "UNMUTE", vm::unmute, lit = true, lock = "mute")
                        else RailKey(GgIcons.EyeOff, "MUTE", { vm.overlay = Overlay.Mute }, lock = "mute")
                    }
                    RailKey(GgIcons.Shutter, "CAPTURE", vm::capture, lit = true, enabled = online, lock = "capture")
                }
            }
        },
    ) {
        FeedCanvas(
            frame = frameOf(state).copy(outline = !vm.drawing),
            modifier = Modifier.fillMaxSize(),
            cover = false,
            image = image,
            drawPoints = if (vm.drawing) vm.drawPoints else null,
            onTap = {
                Feel.play(view, Sfx.POINT)
                vm.drawPoints.add(it)
            },
        )
        Box(Modifier.fillMaxSize().scanLines())
        // A tap on the picture hides the panels, and a second tap brings them back.
        if (!vm.drawing) Box(Modifier.fillMaxSize().pressable { rails = !rails })
        if (state.alertActive && !state.muted && online && !vm.drawing) AlertFlag()
        if (!online) OfflinePanel(state.conn == ConnState.NO_PI)
    }
}
