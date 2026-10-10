package com.garbageguard.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.AlertFlag
import com.garbageguard.app.ui.components.AutoSwitch
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.ConnPill
import com.garbageguard.app.ui.components.FEED_BLACK
import com.garbageguard.app.ui.components.FeedCanvas
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Led
import com.garbageguard.app.ui.components.OfflinePanel
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.BrandLogo
import com.garbageguard.app.ui.components.neuInset
import com.garbageguard.app.ui.components.rememberLiveFrame
import com.garbageguard.app.ui.components.neuRaised
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
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * The feed turned sideways. One composable covers both uses in the mockup:
 * watching (counts, state, Mute and Capture on one bar) and drawing the
 * monitored area (Undo, Clear, Cancel, Apply).
 */
@Composable
fun FullscreenScreen(vm: AppViewModel, state: LiveState) {
    val c = Gg.colors
    LandscapeImmersive()
    BackHandler { vm.closeFullscreen() }

    val online = state.conn == ConnState.ONLINE
    val level = levelOf(state)
    val p = percentOf(state)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.base)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ---- top bar ----
        Bar {
            if (vm.drawing) {
                T("DRAW AREA", weight = FontWeight.Black, color = c.text, maxLines = 1)
                val n = vm.drawPoints.size
                T(
                    "Drawing: $n point${if (n == 1) "" else "s"} placed. Apply when closed.",
                    size = 0.8f, weight = FontWeight.Bold, color = c.muted, maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                GgButton(null, vm::closeFullscreen, icon = GgIcons.X, small = true, square = true)
            } else {
                BrandLogo(38.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Led(online)
                    T("CAM 01 · LIVE", size = 0.9f, color = c.text, maxLines = 1)
                }
                ConnPill(state.conn, onlineLabel = "Camera online")
                T("${state.latencyMs.roundToInt()} ms", size = 0.8f, weight = FontWeight.Bold, color = c.muted, maxLines = 1)
                // .fps-chip
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.tealTint)
                        .border(2.dp, c.edge, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) { T(fpsLabel(state), size = 0.85f, weight = FontWeight.Black, color = if (c.isDark) c.tealLight else c.teal, tnum = true, spacing = 0.04f, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                GgButton(null, vm::closeFullscreen, icon = GgIcons.Shrink, small = true, square = true)
            }
        }

        // ---- the picture ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(FEED_BLACK)
                .neuInset(c, 13.dp)
                .border(2.dp, c.edge, RoundedCornerShape(13.dp)),
        ) {
            FeedCanvas(
                frame = frameOf(state).copy(outline = !vm.drawing),
                modifier = Modifier.fillMaxSize(),
                cover = false,
                image = rememberLiveFrame(vm.frameUrl),
                drawPoints = if (vm.drawing) vm.drawPoints else null,
                onTap = { vm.drawPoints.add(it) },
            )
            Box(Modifier.fillMaxSize().scanLines())
            if (state.alertActive && !state.muted && online && !vm.drawing) AlertFlag()
            if (!online) OfflinePanel(state.conn == ConnState.NO_PI)
        }

        // ---- bottom bar ----
        Bar {
            if (vm.drawing) {
                Spacer(Modifier.weight(1f))
                GgButton("Undo", { if (vm.drawPoints.isNotEmpty()) vm.drawPoints.removeAt(vm.drawPoints.lastIndex) }, icon = GgIcons.Undo, small = true)
                GgButton("Clear", { vm.drawPoints.clear() }, icon = GgIcons.Eraser, small = true)
                GgButton("Cancel", vm::closeFullscreen, small = true)
                GgButton("Apply", vm::applyArea, icon = GgIcons.Check, kind = BtnKind.INK, small = true)
            } else {
                val stateFill = when (level.lv) { Lv.WARN -> c.warnTint; Lv.BAD -> c.badTint; else -> c.base2 }
                val stateInk = if (level.lv == Lv.BAD) c.bad else c.text
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(stateFill)
                        .border(2.dp, c.edge, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Ico(level.icon, size = 14.dp, tint = stateInk)
                    T(level.label.uppercase(), size = 0.8f, color = stateInk, spacing = 0.04f, maxLines = 1)
                }
                T(
                    "$p% · ${state.smoothed} of ${state.threshold}",
                    size = 0.8f, weight = FontWeight.Bold, color = c.muted, tnum = true, maxLines = 1,
                )
                ClassChip(state.bottle, c.bottle)
                ClassChip(state.bag, c.bag)
                ClassChip(state.foodContainer, c.container)
                Spacer(Modifier.weight(1f))
                AutoSwitch(state.autoMute, vm::toggleAutoMute)
                if (state.alertActive) {
                    if (state.muted) GgButton("Unmute", vm::unmute, icon = GgIcons.Bell, kind = BtnKind.TEAL, small = true)
                    else GgButton("Mute", { vm.overlay = Overlay.Mute }, icon = GgIcons.EyeOff, small = true)
                }
                GgButton("Capture", vm::capture, icon = GgIcons.Shutter, kind = BtnKind.TEAL, small = true, enabled = online)
            }
        }
    }
}

/** .fst and .fsbt */
@Composable
private fun Bar(content: @Composable RowScope.() -> Unit) {
    val c = Gg.colors
    Row(
        Modifier
            .fillMaxWidth()
            .neuRaised(c, 13.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** .fchip */
@Composable
private fun ClassChip(count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Ico(GgIcons.Box, size = 15.dp, tint = tint)
        T("$count", size = 1.1f, color = Gg.colors.text, tnum = true)
    }
}
