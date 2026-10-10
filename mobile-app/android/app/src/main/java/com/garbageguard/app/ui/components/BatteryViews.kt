package com.garbageguard.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.Battery
import com.garbageguard.app.ui.theme.Gg
import com.garbageguard.app.ui.theme.GgColors
import kotlin.math.abs

/*
 * The Pi's UPS pack, the same readout as the touchscreen dashboard: a pack
 * that fills with charge in four segments (one per 21700 cell), green above
 * 40%, amber to 20%, red below, with a bolt and a moving shine while charging.
 */

enum class BatteryLevel { OK, WARN, BAD, NONE }

fun batteryLevel(b: Battery): BatteryLevel = when {
    !b.ok -> BatteryLevel.NONE
    b.percent <= 20 -> BatteryLevel.BAD
    b.percent <= 40 -> BatteryLevel.WARN
    else -> BatteryLevel.OK
}

/** "+1.20", "−0.82" or "0.00", with a real minus sign, as the dashboard prints it. */
fun signed(value: Float, decimals: Int): String {
    val text = "%.${decimals}f".format(abs(value))
    val zero = text.toFloat() == 0f
    return when {
        zero -> text
        value < 0 -> "−$text"
        else -> "+$text"
    }
}

/** Amber when the pack is draining, teal when it is filling, plain when still. */
@Composable
fun flowColor(b: Battery): Color {
    val c = Gg.colors
    return when {
        b.current < -0.005f -> c.warnText
        b.current > 0.005f -> c.teal
        else -> c.text
    }
}

private fun minutesText(minutes: Int?): String = when {
    minutes == null -> ""
    minutes >= 60 -> " · ${minutes / 60} h ${minutes % 60} m"
    else -> " · $minutes m"
}

/** The dashboard's one-line summary of where the power is coming from. */
fun batterySource(b: Battery): String = when {
    !b.ok -> "No UPS found"
    b.charging -> "On AC, charging${minutesText(b.minutesLeft)}"
    b.onAc -> "On AC, ${if (b.percent >= 99) "full" else "idle"}"
    else -> "On battery${minutesText(b.minutesLeft)} left"
}

private fun fillBrush(level: BatteryLevel, c: GgColors): Brush = when (level) {
    BatteryLevel.WARN -> Brush.verticalGradient(listOf(Color(0xFFF9CF6A), c.warn))
    BatteryLevel.BAD -> Brush.verticalGradient(listOf(Color(0xFFFF8A7E), c.bad))
    else -> Brush.verticalGradient(listOf(Color(0xFF7BE08F), c.ok))
}

/** The pack itself: sunk well, charge fill, four cell segments, terminal nub. */
@Composable
fun BatteryGlyph(b: Battery, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val c = Gg.colors
    val level = batteryLevel(b)
    val fill by animateFloatAsState(
        if (b.ok) b.percent / 100f else 0f, tween(700, easing = FastOutSlowInEasing), label = "charge",
    )
    val charging = b.ok && b.charging
    val motion = rememberInfiniteTransition(label = "battery")
    val shine by motion.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)), label = "shine")
    val pop by motion.animateFloat(1f, 1.18f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pop")
    val blink by motion.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    val low = level == BatteryLevel.BAD && !charging
    val radius = (height.value * 0.26f).dp
    val shape = RoundedCornerShape(radius)
    val nub = (height.value * 0.18f).coerceAtLeast(3f).dp

    Row(modifier.alpha(if (low) blink else 1f), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width, height)
                .neuWell(c, radius)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width * fill
                        if (w > 0f) {
                            val r = (radius.toPx() * 0.5f).coerceAtMost(w / 2f)
                            drawRoundRect(
                                fillBrush(level, c), size = Size(w, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                            )
                            if (charging) {
                                // The moving shine of a pack taking charge.
                                val sw = size.width * 0.4f
                                val x = (size.width * shine - sw / 2f)
                                val left = x.coerceIn(0f, w)
                                val right = (x + sw).coerceIn(0f, w)
                                if (right > left) {
                                    drawRect(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                            startX = x, endX = x + sw,
                                        ),
                                        topLeft = Offset(left, 0f), size = Size(right - left, size.height),
                                    )
                                }
                            }
                        }
                        // Four cells, so three dividers.
                        val gap = 1.5.dp.toPx()
                        for (i in 1..3) {
                            drawRect(c.sunk, topLeft = Offset(size.width * i / 4f - gap / 2f, 0f), size = Size(gap, size.height))
                        }
                    },
            )
            if (charging) {
                val bolt = (height.value * 0.72f).dp
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Ico(GgIcons.BoltEdge, Modifier.graphicsLayer { scaleX = pop; scaleY = pop }, size = bolt, tint = c.edge)
                    Ico(GgIcons.Bolt, Modifier.graphicsLayer { scaleX = pop; scaleY = pop }, size = bolt, tint = Color.White)
                }
            }
        }
        // The terminal.
        Box(
            Modifier
                .offset(x = (-2).dp)
                .size(width = nub + 2.dp, height = (height.value * 0.4f).dp)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(c.raised)
                .border(2.dp, c.edge, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
        )
    }
}

/**
 * The header chip: pack, percentage, and the live power flow under it.
 * Tapping it opens the full readout.
 */
@Composable
fun BatteryChip(b: Battery, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Gg.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val level = batteryLevel(b)
    val low = level == BatteryLevel.BAD && !b.charging
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .offset { if (pressed) IntOffset(3.dp.roundToPx(), 3.dp.roundToPx()) else IntOffset.Zero }
            .then(if (pressed) Modifier else Modifier.neuShadow(c, 12.dp, 3.dp, Soft.SM))
            .clip(shape)
            .then(if (pressed) Modifier.background(c.sunk).neuInset(c, 12.dp) else Modifier.background(raisedBrush(c)))
            .border(2.dp, if (low) c.bad else c.edge, shape)
            .clickable(interactionSource = src, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .defaultMinSize(minHeight = 44.dp)
            .padding(start = 8.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BatteryGlyph(b, width = 40.dp, height = 22.dp)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(if (b.ok) "${b.percent}" else "–", size = 1.02f, color = c.text)
                T("%", Modifier.padding(start = 1.dp, bottom = 1.dp), size = 0.6f, color = c.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Ico(
                    if (b.ok && b.onAc) GgIcons.Plug else GgIcons.Battery, size = 9.dp,
                    tint = if (!b.ok) c.muted else if (b.charging) c.teal else if (b.onAc) c.muted else c.warnText,
                )
                T(
                    if (b.ok) "${signed(b.power, 1)} W" else "NO UPS", size = 0.56f, weight = FontWeight.Black,
                    color = if (b.ok) flowColor(b) else c.muted, tnum = true, spacing = 0.02f, maxLines = 1,
                )
            }
        }
    }
}

/** One value in the full readout: a sunk well with a caps label over a number and its unit. */
@Composable
fun ReadoutWell(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    val c = Gg.colors
    Column(
        modifier.neuWell(c, 12.dp).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        T(label, size = 0.6f, color = c.muted, spacing = 0.07f, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RollingText(value, size = 1.25f, color = color)
            T(unit, Modifier.padding(bottom = 2.dp), size = 0.7f, color = c.muted)
        }
    }
}
