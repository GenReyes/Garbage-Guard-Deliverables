package com.garbageguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.garbageguard.app.notify.Sfx

/**
 * Pinch zoom for a picture on the black screen. [scale] runs from 1 (the
 * whole picture) to [MAX], and [pan] is where the zoomed picture's top left
 * corner sits, in the canvas's own pixels. A point on screen maps back to
 * the unzoomed canvas with [toCanvas], so a tap made while zoomed in still
 * lands on the right spot of the camera frame.
 */
@Stable
class FeedZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set

    val zoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        pan = Offset.Zero
    }

    fun toCanvas(p: Offset): Offset = (p - pan) / scale

    /** Two fingers: [factor] is the change in their spread, [move] the change in their middle. */
    fun pinch(centroid: Offset, factor: Float, move: Offset, size: IntSize) {
        val next = (scale * factor).coerceIn(1f, MAX)
        // The point under the fingers stays under the fingers.
        val under = toCanvas(centroid)
        pan = clamp(centroid + move - under * next, next, size)
        scale = next
    }

    /** One finger, only while zoomed in and not drawing. */
    fun drag(move: Offset, size: IntSize) {
        pan = clamp(pan + move, scale, size)
    }

    /** The zoomed picture always covers the canvas: no black gap opens at an edge. */
    private fun clamp(p: Offset, s: Float, size: IntSize): Offset = Offset(
        p.x.coerceIn(size.width * (1f - s), 0f),
        p.y.coerceIn(size.height * (1f - s), 0f),
    )

    companion object {
        const val MAX = 5f
    }
}

@Composable
fun rememberFeedZoom(): FeedZoom = remember { FeedZoom() }

/**
 * One gesture handler for the picture. Two fingers zoom and pan. One finger
 * that does not move is a tap, passed on in screen pixels. One finger that
 * moves pans the zoomed picture, except while drawing, where only taps count
 * so a point is never placed by accident.
 */
internal suspend fun PointerInputScope.feedGestures(
    zoom: FeedZoom?,
    drawing: () -> Boolean,
    onTap: (Offset) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var multi = false
    var moved = false
    var travel = Offset.Zero
    var event: PointerEvent
    do {
        event = awaitPointerEvent()
        val fingers = event.changes.count { it.pressed }
        if (zoom != null && fingers >= 2) {
            multi = true
            zoom.pinch(event.calculateCentroid(useCurrent = true), event.calculateZoom(), event.calculatePan(), size)
            event.changes.forEach { it.consume() }
        } else if (!multi) {
            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
            val step = change.positionChange()
            travel += step
            if (travel.getDistance() > viewConfiguration.touchSlop) moved = true
            if (moved && zoom != null && zoom.zoomed && !drawing()) {
                zoom.drag(step, size)
                change.consume()
            }
        }
    } while (event.changes.any { it.pressed })
    // A pinch that ends with one finger still down is not a tap.
    if (!multi && !moved) onTap(down.position)
}

/** The zoom level and a key back to the whole picture, shown only while zoomed in. */
@Composable
fun BoxScope.ZoomReset(zoom: FeedZoom, modifier: Modifier = Modifier) {
    if (!zoom.zoomed) return
    Row(
        modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .border(2.dp, GLASS_EDGE, CircleShape)
            .pressable(sound = Sfx.BACK) { zoom.reset() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ico(GgIcons.Shrink, size = 14.dp, tint = GLASS_EDGE)
        T("%.1fx".format(zoom.scale), size = 0.72f, weight = FontWeight.Black, color = GLASS_EDGE, tnum = true, maxLines = 1)
        T("RESET ZOOM", size = 0.66f, weight = FontWeight.Black, color = GLASS_EDGE, spacing = 0.05f, maxLines = 1)
    }
}
