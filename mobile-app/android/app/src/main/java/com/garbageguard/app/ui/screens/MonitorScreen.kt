package com.garbageguard.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.AutoSwitch
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.FeedFrame
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Skeleton
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.TvFeed
import com.garbageguard.app.ui.components.breathe
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.neuShadow
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.ring
import com.garbageguard.app.ui.theme.Gg
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Lv { OK, WARN, BAD, GRAY }

data class Level(val lv: Lv, val label: String, val icon: ImageVector)

fun percentOf(s: LiveState): Int =
    if (s.threshold > 0) (s.smoothed * 100f / s.threshold).roundToInt() else 0

/** Same thresholds and wording as level() in the mockup and the dashboard. */
fun levelOf(s: LiveState): Level {
    val p = percentOf(s)
    return when {
        s.conn == ConnState.NO_PI -> Level(Lv.GRAY, "No link to Pi", GgIcons.NoLink)
        s.alertActive && s.muted -> Level(Lv.WARN, "Alert muted", GgIcons.EyeOff)
        s.alertActive -> Level(Lv.BAD, "Alert raised", GgIcons.Bell)
        p >= 80 -> Level(Lv.BAD, "Near the limit", GgIcons.Warn)
        p >= 50 -> Level(Lv.WARN, "Building up", GgIcons.Up)
        else -> Level(Lv.OK, "Normal", GgIcons.Ok)
    }
}

fun frameOf(s: LiveState) = FeedFrame(
    showBoxes = s.conn == ConnState.ONLINE,
    total = s.smoothed, bottle = s.bottle, bag = s.bag, container = s.foodContainer,
    roi = s.roi, outline = true,
)

fun fpsLabel(s: LiveState) = if (s.conn == ConnState.ONLINE) "%.1f FPS".format(s.fps) else "-- FPS"

fun muteLeftLabel(seconds: Int?): String =
    if (seconds == null || seconds <= 0) "no expiry" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} left"

val MUTE_CHOICES = listOf(300 to "5 min", 900 to "15 min", 1800 to "30 min", 3600 to "1 hr", 0 to "Always")

@Composable
fun MonitorScreen(vm: AppViewModel, state: LiveState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (vm.loading) {
            Skeleton(96.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Skeleton(60.dp, Modifier.weight(1f)) }
            }
            Skeleton(230.dp)
            Skeleton(52.dp)
            return@Column
        }
        Hero(state)
        Counts(state)
        MuteBar(vm, state)
        FeedCard(vm, state)
    }
}

/** .box.level */
@Composable
private fun Hero(s: LiveState) {
    val c = Gg.colors
    val level = levelOf(s)
    val noPi = s.conn == ConnState.NO_PI
    val p = percentOf(s)
    val fill by animateFloatAsState(if (noPi) 0f else (p.coerceAtMost(100)) / 100f, tween(550), label = "bar")
    val barColor = when (level.lv) { Lv.WARN -> c.warn; Lv.BAD -> c.bad; else -> c.ok }
    val stateColor = when (level.lv) { Lv.WARN -> c.warnText; Lv.BAD -> c.bad; else -> c.text }
    val bad = level.lv == Lv.BAD
    val glow = if (bad) breathe(1500) else 0f

    var lastUpdate by remember { mutableStateOf("") }
    LaunchedEffect(s) {
        if (!noPi) lastUpdate = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // @keyframes alarm: the border swells into a red tint ring.
                if (bad) {
                    val g = 4.dp.toPx() * glow
                    val r = 14.dp.toPx() + g
                    drawRoundRect(
                        c.badTint.copy(alpha = glow), topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g), cornerRadius = CornerRadius(r, r),
                    )
                }
            }
            .neuRaised(c, 14.dp, edge = if (bad) c.bad else c.edge)
            .drawBehind {
                if (level.lv != Lv.GRAY) {
                    drawRect(barColor.copy(alpha = 0.45f), size = Size(size.width * fill, size.height))
                }
            },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(
                if (noPi) "Last update $lastUpdate" else "$p% of limit",
                size = 0.74f, weight = FontWeight.Bold, color = c.muted,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(
                    if (noPi) "–" else "${s.smoothed}", size = 2f, weight = FontWeight.Black,
                    color = c.text, tnum = true, spacing = -0.03f, lineHeight = 1.05f,
                )
                T("of ${s.threshold} items", Modifier.padding(bottom = 3.dp), size = 0.9f, weight = FontWeight.Bold, color = c.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico(level.icon, Modifier.ring(s.alertActive && !s.muted && !noPi), size = 14.dp, tint = stateColor)
                T(level.label, size = 0.85f, color = stateColor)
            }
        }
    }
}

/** .counts */
@Composable
private fun Counts(s: LiveState) {
    val c = Gg.colors
    val noPi = s.conn == ConnState.NO_PI
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("Bottle", s.bottle, c.bottle),
            Triple("Bag", s.bag, c.bag),
            Triple("Container", s.foodContainer, c.container),
        ).forEach { (label, value, tint) ->
            Column(
                Modifier
                    .weight(1f)
                    .neuRaised(c, 14.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(GgIcons.Box, size = 12.dp, tint = tint)
                    T(label, size = 0.74f, weight = FontWeight.Bold, color = c.muted, maxLines = 1)
                }
                T(
                    if (noPi) "–" else "$value", size = 1.45f, weight = FontWeight.Black,
                    color = c.text, tnum = true, lineHeight = 1.05f,
                )
            }
        }
    }
}

/** .mute, every state of the mute bar. */
@Composable
private fun MuteBar(vm: AppViewModel, s: LiveState) {
    val c = Gg.colors
    if (s.conn == ConnState.NO_PI) {
        Row(
            Modifier.fillMaxWidth().neuRaised(c, 14.dp, fillColor = c.badTint).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ico(GgIcons.NoLink, size = 18.dp, tint = c.bad)
            T("Cannot reach the Pi. Check you are on the same network.", Modifier.weight(1f), size = 0.88f, color = c.text)
            GgButton("Retry", vm::retry, icon = GgIcons.Refresh, small = true)
        }
        return
    }

    val idle = !s.alertActive && !s.muted
    val armed = s.autoMute && idle
    val fill = when {
        s.muted -> c.sunk
        armed -> c.tealTint
        idle -> c.base2
        else -> c.warnTint
    }
    val timed = s.muted && s.muteLeft != null && s.muteTotal > 0

    Column(
        Modifier.fillMaxWidth().neuRaised(c, 14.dp, fillColor = fill).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The mockup's flex row wraps at phone width: text and Auto on the
        // first line, the length picker and the button on the second.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).defaultMinSize(minHeight = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    s.muted -> {
                        Ico(GgIcons.EyeOff, size = 18.dp, tint = c.muted)
                        T(
                            "${if (s.autoMuted) "Auto-muted" else "Alert muted"}, ${muteLeftLabel(s.muteLeft)}",
                            size = 0.88f, color = c.text, tnum = true,
                        )
                    }
                    armed -> {
                        Ico(GgIcons.EyeOff, size = 18.dp, tint = c.teal)
                        T("Auto-mute armed", size = 0.88f, color = c.teal)
                    }
                    idle -> {
                        Ico(GgIcons.Bell, size = 18.dp, tint = c.muted)
                        T("No alert to mute", size = 0.88f, weight = FontWeight.Bold, color = c.muted)
                    }
                    else -> {
                        Ico(GgIcons.Bell, Modifier.ring(), size = 18.dp, tint = c.bad)
                        T("Accumulation alert is active", size = 0.88f, color = c.text)
                    }
                }
            }
            AutoSwitch(s.autoMute, vm::toggleAutoMute)
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MutePick(vm.muteChoice, vm::chooseMuteLength)
            when {
                s.muted -> GgButton("Unmute", vm::unmute, icon = GgIcons.Bell, kind = BtnKind.TEAL, small = true)
                armed -> {}
                else -> GgButton(
                    "Mute", { vm.overlay = Overlay.Mute }, icon = GgIcons.EyeOff, small = true, enabled = !idle,
                )
            }
        }
        if (timed) {
            val frac = (s.muteLeft!!.toFloat() / s.muteTotal).coerceIn(0f, 1f)
            val w by animateFloatAsState(frac, tween(900), label = "mute")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(c.sunk)
                    .border(2.dp, c.edge, CircleShape)
                    .drawBehind {
                        val inset = 2.dp.toPx()
                        val h = size.height - 2 * inset
                        drawRoundRect(
                            c.warn, topLeft = Offset(inset, inset),
                            size = Size((size.width - 2 * inset) * w, h), cornerRadius = CornerRadius(h, h),
                        )
                    },
            )
        }
    }
}

/** .mute-pick, the "for 15 min" selector. */
@Composable
fun MutePick(seconds: Int, onPick: (Int) -> Unit) {
    val c = Gg.colors
    var open by remember { mutableStateOf(false) }
    val label = MUTE_CHOICES.firstOrNull { it.first == seconds }?.second ?: "15 min"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        T("for", size = 0.78f, color = c.muted)
        Box {
            Row(
                Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .neuWell(c, 8.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                T(label, size = 0.78f, color = c.text, maxLines = 1)
                T("▾", size = 0.7f, color = c.text)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = c.raised,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, c.edge),
            ) {
                MUTE_CHOICES.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { T(text, size = 0.9f, color = if (value == seconds) c.teal else c.text) },
                        onClick = {
                            open = false
                            onPick(value)
                        },
                    )
                }
            }
        }
    }
}

/** The Live feed card. */
@Composable
private fun FeedCard(vm: AppViewModel, s: LiveState) {
    val online = s.conn == ConnState.ONLINE
    val noPi = s.conn == ConnState.NO_PI
    GgCard {
        Hd(GgIcons.Cam, "Live feed") {
            GgButton("Capture", vm::capture, icon = GgIcons.Shutter, kind = BtnKind.TEAL, small = true, enabled = online)
        }
        TvFeed(
            frame = frameOf(s),
            online = online,
            noPi = noPi,
            fpsLabel = fpsLabel(s),
            showFlag = s.alertActive && !s.muted && online,
            onFullscreen = vm::openFullscreen,
        )
        Hint(
            if (s.roi.size >= 3) "Grey boxes fall outside the monitored area and are not counted."
            else "Whole frame is monitored. Draw an area in Settings.",
            icon = GgIcons.EyeOff,
        )
        Hint(
            "Latency ${if (noPi) "–" else s.latencyMs.roundToInt()} ms, average ${if (noPi) "–" else s.avgLatencyMs.roundToInt()} ms",
            icon = GgIcons.Clock,
        )
    }
}
