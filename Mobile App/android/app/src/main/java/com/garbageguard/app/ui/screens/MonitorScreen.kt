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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.garbageguard.app.ui.components.RollingText
import com.garbageguard.app.ui.components.Skeleton
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.LocalOnColour
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.TvFeed
import com.garbageguard.app.ui.components.breathe
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.neuShadow
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.ring
import com.garbageguard.app.ui.components.pressable
import com.garbageguard.app.notify.Feel
import androidx.compose.ui.platform.LocalView
import com.garbageguard.app.ui.components.rememberLiveFrame
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
        PiScreenChip(vm, state)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Ico(if (noPi) GgIcons.Clock else GgIcons.Gauge, size = 12.dp, tint = c.muted)
                T(
                    if (noPi && s.piOfflineSince != null) "PI OFFLINE SINCE ${s.piOfflineSince}" else if (noPi) "LAST UPDATE $lastUpdate" else "$p% OF LIMIT",
                    size = 0.68f, color = c.muted, spacing = 0.06f,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RollingText(if (noPi) "–" else "${s.smoothed}", size = 2.2f, color = c.text, spacing = -0.03f)
                T("OF ${s.threshold} ITEMS", Modifier.padding(bottom = 4.dp), size = 0.76f, color = c.muted, spacing = 0.05f)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico(level.icon, Modifier.ring(s.alertActive && !s.muted && !noPi), size = 14.dp, tint = stateColor)
                T(level.label.uppercase(), size = 0.8f, weight = FontWeight.Black, color = stateColor, spacing = 0.05f)
            }
        }
    }
}

private data class ClassCount(val label: String, val value: Int, val tint: Color, val icon: ImageVector)

/** .counts */
@Composable
private fun Counts(s: LiveState) {
    val c = Gg.colors
    val noPi = s.conn == ConnState.NO_PI
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ClassCount("Bottle", s.bottle, c.bottle, GgIcons.Bottle),
            ClassCount("Bag", s.bag, c.bag, GgIcons.Bag),
            ClassCount("Container", s.foodContainer, c.container, GgIcons.Container),
        ).forEach { (label, value, tint, icon) ->
            Column(
                Modifier
                    .weight(1f)
                    .neuRaised(c, 14.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(icon, size = 13.dp, tint = tint)
                    T(label.uppercase(), size = 0.66f, color = c.muted, spacing = 0.05f, maxLines = 1)
                }
                RollingText(if (noPi) "–" else "$value", size = 1.5f, color = c.text)
            }
        }
    }
}

/**
 * The mute bar: one line, and the same size in every state. Status on the
 * left in two short lines, then the auto-mute key, the length picker, and
 * one button that is always there (Mute, Unmute or Retry). A timed mute
 * shows its countdown as a thin bar along the bottom edge, inside the card.
 */
@Composable
private fun MuteBar(vm: AppViewModel, s: LiveState) {
    val c = Gg.colors
    val noPi = s.conn == ConnState.NO_PI
    val idle = !s.alertActive && !s.muted
    val armed = s.autoMute && idle

    val fill = when {
        noPi -> c.badTint
        s.muted -> c.sunk
        armed -> c.tealTint
        idle -> c.base2
        else -> c.warnTint
    }
    val icon: ImageVector
    val tint: Color
    val title: String
    val detail: String
    when {
        noPi -> { icon = GgIcons.NoLink; tint = c.bad; title = "NO LINK"; detail = "Check the network" }
        s.muted -> {
            icon = GgIcons.EyeOff; tint = c.muted
            title = if (s.autoMuted) "AUTO-MUTED" else "MUTED"
            detail = muteLeftLabel(s.muteLeft)
        }
        armed -> { icon = GgIcons.BellAuto; tint = c.teal; title = "ARMED"; detail = "Mutes next alert" }
        idle -> { icon = GgIcons.Bell; tint = c.muted; title = "NO ALERT"; detail = "Nothing to mute" }
        else -> { icon = GgIcons.Bell; tint = c.bad; title = "ALERT"; detail = "Limit reached" }
    }
    val timed = s.muted && s.muteLeft != null && s.muteTotal > 0
    val frac by animateFloatAsState(
        if (timed) (s.muteLeft!!.toFloat() / s.muteTotal).coerceIn(0f, 1f) else 0f, tween(900), label = "mute",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .neuRaised(c, 14.dp, fillColor = fill)
            .drawBehind {
                if (timed) {
                    val h = 4.dp.toPx()
                    drawRect(c.shade.copy(alpha = 0.5f), topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                    drawRect(c.warn, topLeft = Offset(0f, size.height - h), size = Size(size.width * frac, h))
                }
            }
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Ico(icon, Modifier.ring(s.alertActive && !s.muted && !noPi), size = 18.dp, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            T(title, size = 0.72f, weight = FontWeight.Black, color = if (tint == c.muted) c.text else tint, spacing = 0.03f, fit = true)
            T(detail, size = 0.64f, weight = FontWeight.Bold, color = c.muted, tnum = true, fit = true)
        }
        // The bar is tinted in most of its states, so its keys keep only their hard shadow.
        CompositionLocalProvider(LocalOnColour provides (fill != c.base2)) {
        if (noPi) {
            GgButton("Retry", vm::retry, Modifier.width(MUTE_KEY_WIDTH), icon = GgIcons.Refresh, small = true)
        } else {
            AutoSwitch(s.autoMute, vm::toggleAutoMute, label = null, lock = "automute")
            MutePick(vm.muteChoice, vm::chooseMuteLength)
            if (s.muted) {
                GgButton("Unmute", vm::unmute, Modifier.width(MUTE_KEY_WIDTH), icon = GgIcons.Bell, kind = BtnKind.TEAL, small = true, lock = "mute")
            } else {
                GgButton(
                    "Mute", { vm.overlay = Overlay.Mute }, Modifier.width(MUTE_KEY_WIDTH),
                    icon = GgIcons.EyeOff, small = true, enabled = !idle, lock = "mute",
                )
            }
        }
        }
    }
}

private val MUTE_KEY_WIDTH = 96.dp

/** The mute length selector, a fixed-width well so the bar never shifts. */
@Composable
fun MutePick(seconds: Int, onPick: (Int) -> Unit) {
    val c = Gg.colors
    var open by remember { mutableStateOf(false) }
    val view = LocalView.current
    val label = MUTE_CHOICES.firstOrNull { it.first == seconds }?.second ?: "15 min"
    Box {
        Row(
            Modifier
                .width(78.dp)
                .height(36.dp)
                .neuWell(c, 9.dp)
                .pressable { open = true }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Ico(GgIcons.Clock, size = 12.dp, tint = c.bag)
            T(label, Modifier.weight(1f), size = 0.72f, color = c.text, fit = true)
            T("\u25BE", size = 0.66f, color = c.muted)
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
                        Feel.toggle(view)
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}
/**
 * The live feed. The whole card is the television: the picture fills it
 * edge to edge and the readouts and keys sit on the set's own control deck.
 */
@Composable
private fun FeedCard(vm: AppViewModel, s: LiveState) {
    val online = s.conn == ConnState.ONLINE
    val noPi = s.conn == ConnState.NO_PI
    val usageNow by vm.cloudUsage.collectAsState()
    val note = usageNow?.note
    TvFeed(
        frame = frameOf(s),
        online = online,
        noPi = noPi,
        fpsLabel = fpsLabel(s),
        latencyLabel = if (noPi) "-- MS" else "${s.latencyMs.roundToInt()} MS \u00B7 AVG ${s.avgLatencyMs.roundToInt()}",
        caption = when {
            // The Pi's own words when it has slowed or stopped the remote picture to save the allowance.
            vm.remote && note != null -> note
            vm.remote -> "A still every 3 seconds. Join the Pi's Wi-Fi for live video."
            s.roi.size >= 3 -> "Grey boxes fall outside the monitored area and are not counted."
            else -> "Whole frame is monitored. Draw an area in Settings."
        },
        remote = vm.remote,
        showFlag = s.alertActive && !s.muted && online,
        onCapture = vm::capture,
        onReconnect = vm::reconnectCamera,
        reconnecting = vm.reconnecting,
        onFullscreen = vm::openFullscreen,
        image = rememberLiveFrame(vm.frameUrl),
    )
}