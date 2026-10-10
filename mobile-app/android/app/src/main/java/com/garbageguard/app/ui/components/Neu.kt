package com.garbageguard.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garbageguard.app.ui.theme.GgColors

/*
 * The look of the mockup is two shadow systems stacked on one surface.
 * The soft pair (dark below right, light above left) is the Neumorphism
 * depth. The hard offset block and the 2 dp border are the Neo-Brutalism
 * part. These modifiers draw both the way the CSS variables do.
 */

/** Matches --soft and --soft-sm. */
enum class Soft(val dark: Dp, val darkBlur: Dp, val light: Dp, val lightBlur: Dp) {
    NONE(0.dp, 0.dp, 0.dp, 0.dp),
    SM(3.dp, 6.dp, 2.dp, 5.dp),
    LG(6.dp, 12.dp, 4.dp, 9.dp),
}

// A CSS blur of b px is a Gaussian with sigma b / 2. BlurMaskFilter takes a
// radius where sigma is about 0.577 * radius, so the factor is close to 0.85.
private const val BLUR_K = 0.85f

private fun blurPaint(color: Color, blurPx: Float, stroke: Float = 0f) =
    Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        if (stroke > 0f) {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
        }
        if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }

/** Outer shadows: soft pair first, hard offset block on top of them. */
fun Modifier.neuShadow(
    c: GgColors,
    radius: Dp,
    hard: Dp = 3.dp,
    soft: Soft = Soft.SM,
    hardColor: Color = c.edge,
): Modifier = drawWithCache {
    val r = radius.toPx()
    val h = hard.toPx()
    val dOff = soft.dark.toPx()
    val lOff = soft.light.toPx()
    val darkPaint = if (soft != Soft.NONE) blurPaint(c.shade, soft.darkBlur.toPx() * BLUR_K) else null
    val lightPaint = if (soft != Soft.NONE) blurPaint(c.lite, soft.lightBlur.toPx() * BLUR_K) else null
    val hardPaint = if (h > 0f) blurPaint(hardColor, 0f) else null
    onDrawBehind {
        drawIntoCanvas { canvas ->
            val n = canvas.nativeCanvas
            val w = size.width
            val ht = size.height
            if (darkPaint != null) n.drawRoundRect(dOff, dOff, w + dOff, ht + dOff, r, r, darkPaint)
            if (lightPaint != null) n.drawRoundRect(-lOff, -lOff, w - lOff, ht - lOff, r, r, lightPaint)
            if (hardPaint != null) n.drawRoundRect(h, h, w + h, ht + h, r, r, hardPaint)
        }
    }
}

/** Inner shadows, --well and --press. Put it after the background. */
fun Modifier.neuInset(c: GgColors, radius: Dp): Modifier = drawWithCache {
    val r = radius.toPx()
    val dOff = 5.dp.toPx()
    val lOff = 3.dp.toPx()
    val clip = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
    val darkPaint = blurPaint(c.shade, 9.dp.toPx() * BLUR_K, stroke = dOff * 2)
    val lightPaint = blurPaint(c.lite, 7.dp.toPx() * BLUR_K, stroke = lOff * 2)
    onDrawBehind {
        clipPath(clip) {
            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                val w = size.width
                val h = size.height
                n.drawRoundRect(-lOff, -lOff, w - lOff, h - lOff, r, r, lightPaint)
                n.drawRoundRect(dOff, dOff, w + dOff, h + dOff, r, r, darkPaint)
            }
        }
    }
}

/** linear-gradient(162deg, top 0%, bottom 58%), the raised button face. */
fun faceBrush(top: Color, bottom: Color, stop: Float = 0.58f): Brush =
    angledBrush(162f, 0f to top, stop to bottom, 1f to bottom)

/** A CSS linear-gradient(<deg>, ...): the line runs through the centre and reaches the corners. */
fun angledBrush(deg: Float, vararg stops: Pair<Float, Color>): Brush = AngledBrush(deg, stops.toList())

private class AngledBrush(private val deg: Float, private val stops: List<Pair<Float, Color>>) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(deg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * half, cy - dy * half),
            to = Offset(cx + dx * half, cy + dy * half),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }
}

fun raisedBrush(c: GgColors): Brush = faceBrush(c.lite, c.raised)

/** A raised surface: shadows, fill, 2 dp edge. */
fun Modifier.neuRaised(
    c: GgColors,
    radius: Dp,
    hard: Dp = 3.dp,
    soft: Soft = Soft.SM,
    fill: Brush? = null,
    fillColor: Color = c.raised,
    edge: Color = c.edge,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .neuShadow(c, radius, hard, soft)
        .clip(shape)
        .then(if (fill != null) Modifier.background(fill) else Modifier.background(fillColor))
        .border(2.dp, edge, shape)
}

/** A sunk surface: the well that inputs, steppers and chips sit in. */
fun Modifier.neuWell(c: GgColors, radius: Dp, fillColor: Color = c.sunk): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(fillColor)
        .neuInset(c, radius)
        .border(2.dp, c.edge, shape)
}

/** The page background: two soft blooms of base2 over base. */
fun Modifier.ggBackground(c: GgColors): Modifier = drawBehind {
    drawRect(c.base)
    val clear = c.base2.copy(alpha = 0f)
    drawRect(
        Brush.radialGradient(
            0f to c.base2, 0.6f to clear,
            center = Offset(size.width * 0.08f, 0f),
            radius = size.height * 0.9f,
        )
    )
    drawRect(
        Brush.radialGradient(
            0f to c.base2, 0.55f to clear,
            center = Offset(size.width, size.height),
            radius = size.height * 0.8f,
        )
    )
}
