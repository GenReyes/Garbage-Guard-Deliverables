package com.garbageguard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garbageguard.app.ui.theme.GgColors

/*
 * A command to the Pi can take seconds to be answered, above all through
 * Firebase. While one is on its way, the control that sent it is locked so
 * that it cannot be sent twice, and it shows what is happening:
 *
 *   ARMING   a number field was just changed; it will send itself when the
 *            line round it has filled, and each new press starts the line again
 *   WAITING  sent, not yet answered; greyed, with a line running down as
 *            the time allowed runs out
 *   OK       the Pi confirmed; one teal flash
 *   FAIL     no answer in time, or the Pi refused; one red flash
 */

enum class LockPhase { ARMING, WAITING, OK, FAIL }

/** One control's lock: its phase, when that phase began, and how long it lasts, in milliseconds. */
data class Lock(val phase: LockPhase, val since: Long, val span: Long)

/** Every lock now in force, by the name of the control or group it belongs to. */
val LocalLocks = compositionLocalOf<Map<String, Lock>> { emptyMap() }

/** A lock as a control draws it: the phase, and how far through it is, 0 to 1. */
class LockLook(val phase: LockPhase, val fraction: Float) {
    val waiting get() = phase == LockPhase.WAITING
}

/** The lock on [key], kept moving frame by frame while there is one. Null when the control is free. */
@Composable
fun lockLook(key: String?): LockLook? {
    val lock = (if (key == null) null else LocalLocks.current[key]) ?: return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lock) {
        while (true) withFrameMillis { now = System.currentTimeMillis() }
    }
    return LockLook(lock.phase, ((now - lock.since).toFloat() / lock.span.coerceAtLeast(1)).coerceIn(0f, 1f))
}

/**
 * Draws the lock over a control of the given corner [radius]: the grey
 * veil and the running line while waiting, the filling line while arming,
 * and the flash at the end.
 */
fun Modifier.lockRing(look: LockLook?, radius: Dp, c: GgColors): Modifier = if (look == null) this else drawWithContent {
    drawContent()
    val w = 3.dp.toPx()
    val r = (radius.toPx() - w / 2f).coerceAtLeast(0f)
    val corner = CornerRadius(r, r)
    val inset = Offset(w / 2f, w / 2f)
    val box = Size(size.width - w, size.height - w)
    fun line(from: Float, to: Float, color: androidx.compose.ui.graphics.Color) {
        val whole = Path().apply { addRoundRect(RoundRect(inset.x, inset.y, inset.x + box.width, inset.y + box.height, corner)) }
        val measure = PathMeasure().apply { setPath(whole, false) }
        val part = Path()
        measure.getSegment(measure.length * from, measure.length * to, part, true)
        drawPath(part, color, style = Stroke(width = w, cap = StrokeCap.Round))
    }
    when (look.phase) {
        LockPhase.ARMING -> line(0f, look.fraction, c.warn)
        LockPhase.WAITING -> {
            drawRoundRect(c.base.copy(alpha = 0.5f), cornerRadius = CornerRadius(radius.toPx(), radius.toPx()))
            line(0f, 1f - look.fraction, c.text)
        }
        LockPhase.OK, LockPhase.FAIL -> {
            val tone = if (look.phase == LockPhase.OK) c.teal else c.bad
            val fade = 1f - look.fraction
            drawRoundRect(tone.copy(alpha = 0.3f * fade), cornerRadius = CornerRadius(radius.toPx(), radius.toPx()))
            drawRoundRect(tone.copy(alpha = fade), topLeft = inset, size = box, cornerRadius = corner, style = Stroke(width = w))
        }
    }
}
