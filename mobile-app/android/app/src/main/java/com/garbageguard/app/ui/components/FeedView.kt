package com.garbageguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.RoiPoint
import com.garbageguard.app.ui.theme.Gg

// Fake detection boxes from the mockup, in a 160 x 100 frame: x, y, w, h.
private val BOXES = listOf(
    floatArrayOf(14f, 50f, 16f, 13f), floatArrayOf(40f, 38f, 14f, 16f), floatArrayOf(66f, 56f, 17f, 12f),
    floatArrayOf(98f, 44f, 15f, 15f), floatArrayOf(124f, 60f, 14f, 14f), floatArrayOf(30f, 24f, 13f, 12f),
    floatArrayOf(84f, 26f, 16f, 13f), floatArrayOf(112f, 24f, 14f, 14f), floatArrayOf(54f, 70f, 15f, 12f),
    floatArrayOf(132f, 40f, 13f, 15f), floatArrayOf(8f, 70f, 14f, 12f), floatArrayOf(104f, 70f, 16f, 12f),
)

/** The area the mockup uses for snapshots that carry no outline of their own. */
val DEFAULT_ROI = listOf(
    RoiPoint(0.10f, 0.18f), RoiPoint(0.82f, 0.12f), RoiPoint(0.94f, 0.78f),
    RoiPoint(0.55f, 0.92f), RoiPoint(0.08f, 0.82f),
)

private val COL_BOTTLE = Color(0xFF3B9CFF)
private val COL_BAG = Color(0xFFC65CF0)
private val COL_CONT = Color(0xFF3FD06E)
private val COL_OUT = Color(0xFF8C8C8C)
private val COL_ROI = Color(0xFFF0B429)
private val COL_ITEM = Color(0xFFD8D2C4)
val FEED_BLACK = Color(0xFF0B0A09)

fun pointInPolygon(x: Float, y: Float, poly: List<RoiPoint>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** What one frame shows. Later the picture comes from GET /frame.jpg. */
data class FeedFrame(
    val showBoxes: Boolean,
    val total: Int,
    val bottle: Int,
    val bag: Int,
    val container: Int,
    val roi: List<RoiPoint>,
    /** Draw the dashed outline of the saved area. */
    val outline: Boolean,
)

/**
 * Stand-in for the camera picture, the same drawing as feedSVG() in the
 * mockup. [cover] crops to fill (the card), otherwise the frame is fitted
 * whole (fullscreen and drawing). When [drawPoints] is not null, taps call
 * [onTap] with normalised 0..1 coordinates, the format POST /api/roi expects.
 */
@Composable
fun FeedCanvas(
    frame: FeedFrame,
    modifier: Modifier = Modifier,
    cover: Boolean = true,
    drawPoints: List<RoiPoint>? = null,
    onTap: (RoiPoint) -> Unit = {},
    image: ImageBitmap? = null,
) {
    // The drawing space is 160 wide. Its height follows the real picture
    // when there is one, so taps land on the same spot of the camera view.
    val fh = if (image != null && image.width > 0) FW * image.height / image.width else 100f
    Canvas(
        modifier = modifier.then(
            if (drawPoints != null) Modifier.pointerInput(cover, fh) {
                detectTapGestures { p ->
                    val s = fitScale(size.width.toFloat(), size.height.toFloat(), fh, cover)
                    val ox = (size.width - FW * s) / 2f
                    val oy = (size.height - fh * s) / 2f
                    val x = (p.x - ox) / (FW * s)
                    val y = (p.y - oy) / (fh * s)
                    if (x in 0f..1f && y in 0f..1f) onTap(RoiPoint(x, y))
                }
            } else Modifier
        ),
    ) {
        val s = fitScale(size.width, size.height, fh, cover)
        val ox = (size.width - FW * s) / 2f
        val oy = (size.height - fh * s) / 2f
        if (image != null) {
            // The Pi's frame already carries its boxes and the area outline.
            drawImage(
                image,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize((FW * s).roundToInt(), (fh * s).roundToInt()),
            )
        }
        withTransform({
            translate(ox, oy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            if (image == null) drawFrame(frame)
            drawAreaInProgress(drawPoints, fh)
        }
    }
}

private const val FW = 160f

private fun fitScale(w: Float, h: Float, fh: Float, cover: Boolean): Float {
    val a = w / FW
    val b = h / fh
    return if (cover) maxOf(a, b) else minOf(a, b)
}

private fun DrawScope.drawAreaInProgress(drawPoints: List<RoiPoint>?, fh: Float) {
    if (drawPoints.isNullOrEmpty()) return
    val closed = drawPoints.size >= 3
    val path = polygon(drawPoints, close = closed, fh = fh)
    if (closed) drawPath(path, COL_ROI.copy(alpha = 0.22f))
    drawPath(path, COL_ROI, style = Stroke(width = 1.4f))
    drawPoints.forEach {
        val c = Offset(it.x * FW, it.y * fh)
        drawCircle(COL_ROI, radius = 3f, center = c)
        drawCircle(Color(0xFF16130F), radius = 3f, center = c, style = Stroke(width = 1f))
    }
}

private fun DrawScope.drawFrame(f: FeedFrame) {
    val frame = Size(160f, 100f)
    // Water.
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF46667A), Color(0xFF1C2F3A)), startY = 0f, endY = 100f),
        size = frame,
    )
    var y = 14f
    while (y < 100f) {
        val wave = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = true
            while (x < 160f) {
                relativeQuadraticTo(10f, if (up) -3f else 3f, 20f, 0f)
                up = !up
                x += 20f
            }
        }
        drawPath(wave, Color.White.copy(alpha = 0.07f), style = Stroke(width = 1f))
        y += 9f
    }
    drawRect(Color.Black.copy(alpha = 0.25f), size = Size(160f, 9f))

    if (f.showBoxes) {
        val roi = if (f.roi.size >= 3) f.roi else null
        val n = minOf(BOXES.size, f.total)
        val sum = maxOf(1, f.bottle + f.bag + f.container)
        for (i in 0 until n) {
            val r = i.toFloat() / n * sum
            val cls = when {
                r < f.bottle -> COL_BOTTLE
                r < f.bottle + f.bag -> COL_BAG
                else -> COL_CONT
            }
            val b = BOXES[i]
            val cx = (b[0] + b[2] / 2f) / 160f
            val cy = (b[1] + b[3] / 2f) / 100f
            val out = roi != null && !pointInPolygon(cx, cy, roi)
            val col = if (out) COL_OUT else cls
            val tl = Offset(b[0], b[1])
            val sz = Size(b[2], b[3])
            drawRect(col.copy(alpha = 0.2f), tl, sz)
            drawRect(col, tl, sz, style = Stroke(width = 1.4f))
            drawRoundRect(
                COL_ITEM.copy(alpha = if (out) 0.35f else 0.7f),
                topLeft = Offset(b[0] + 3f, b[1] + 3f),
                size = Size(b[2] - 6f, b[3] - 6f),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
        if (roi != null && f.outline) {
            drawPath(
                polygon(roi, close = true), COL_ROI.copy(alpha = 0.55f),
                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))),
            )
        }
    }
}

private fun polygon(points: List<RoiPoint>, close: Boolean, fh: Float = 100f) = Path().apply {
    moveTo(points[0].x * FW, points[0].y * fh)
    points.drop(1).forEach { lineTo(it.x * FW, it.y * fh) }
    if (close) close()
}

/** .scan, the faint TV scan lines. */
fun Modifier.scanLines(): Modifier = drawBehind {
    val step = 3.dp.toPx()
    val line = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(Color.White.copy(alpha = 0.045f), topLeft = Offset(0f, y), size = Size(size.width, line))
        y += step
    }
}

/** .flag */
@Composable
fun BoxScope.AlertFlag() {
    val c = Gg.colors
    val f = breathe(1200)
    Row(
        Modifier
            .align(Alignment.TopEnd)
            .padding(7.dp)
            .drawBehind {
                // @keyframes alarm: a tint ring that swells and fades.
                val grow = 4.dp.toPx() * f
                val r = 9.dp.toPx() + grow
                drawRoundRect(
                    c.badTint.copy(alpha = f), topLeft = Offset(-grow, -grow),
                    size = Size(size.width + 2 * grow, size.height + 2 * grow), cornerRadius = CornerRadius(r, r),
                )
            }
            .neuShadow(c, 9.dp, 3.dp, Soft.NONE)
            .clip(RoundedCornerShape(9.dp))
            .background(c.bad)
            .border(2.dp, c.edge, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Ico(GgIcons.Bell, Modifier.ring(), size = 13.dp, tint = c.onBad)
        T("Accumulation alert", size = 0.8f, weight = FontWeight.Black, color = c.onBad, maxLines = 1)
    }
}

/** .offp */
@Composable
fun BoxScope.OfflinePanel(noPi: Boolean) {
    val c = Gg.colors
    Row(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(9.dp))
            .background(c.raised)
            .border(2.dp, c.edge, RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Ico(GgIcons.NoLink, Modifier.shake(), size = 14.dp, tint = c.bad)
        T(if (noPi) "No link to Pi" else "No camera signal", size = 0.82f, weight = FontWeight.Black, color = c.text, maxLines = 1)
    }
}

/** .led */
@Composable
fun Led(alive: Boolean) {
    val c = Gg.colors
    val col = if (alive) c.ok else c.bad
    val f = if (alive) breathe(2600) else 1f
    Box(
        Modifier
            .size(7.dp)
            .alpha(if (alive) 0.25f + 0.75f * f else 1f)
            .drawBehind { drawCircle(col.copy(alpha = 0.45f), radius = size.minDimension) }
            .clip(CircleShape)
            .background(col),
    )
}

private val BEZEL_INK = Color(0xFF0E0C0A)

/**
 * The casing colours, taken from the dashboard's --mon-* and --hw-* values.
 * Light theme keeps the dark monitor; dark theme turns the casing pale, so
 * the set always stands out from the page. The glass stays black in both.
 */
private class Casing(
    val top: Color, val mid: Color, val bottom: Color,
    val engraved: Color, val hint: Color, val fps: Color,
    val keyTop: Color, val keyBottom: Color, val keyInk: Color,
    val lipLight: Float, val lipDark: Float,
)

private val DarkCasing = Casing(
    top = Color(0xFF4E4740), mid = Color(0xFF2C2621), bottom = Color(0xFF1F1A16),
    engraved = Color(0xFFC9BFB2), hint = Color(0xFFBDB3A7), fps = Color(0xFF8FE0CF),
    keyTop = Color(0xFF5C554D), keyBottom = Color(0xFF36302A), keyInk = Color(0xFFEDE6DC),
    lipLight = 0.16f, lipDark = 0.35f,
)

private val PaleCasing = Casing(
    top = Color(0xFFF4EFE7), mid = Color(0xFFD9D1C5), bottom = Color(0xFFBDB3A5),
    engraved = Color(0xFF3B342D), hint = Color(0xFF5A5148), fps = Color(0xFF8FE0CF),
    keyTop = Color(0xFFFFFDF9), keyBottom = Color(0xFFDCD4C8), keyInk = Color(0xFF1E1A16),
    lipLight = 0.85f, lipDark = 0.18f,
)

/** The current casing, cross-fading when the theme changes. */
@Composable
private fun casing(): Casing {
    val to = if (Gg.colors.isDark) PaleCasing else DarkCasing
    val spec = tween<Color>(350)
    val top by animateColorAsState(to.top, spec, label = "c1")
    val mid by animateColorAsState(to.mid, spec, label = "c2")
    val bottom by animateColorAsState(to.bottom, spec, label = "c3")
    val engraved by animateColorAsState(to.engraved, spec, label = "c4")
    val hint by animateColorAsState(to.hint, spec, label = "c5")
    val keyTop by animateColorAsState(to.keyTop, spec, label = "c6")
    val keyBottom by animateColorAsState(to.keyBottom, spec, label = "c7")
    val keyInk by animateColorAsState(to.keyInk, spec, label = "c8")
    return Casing(top, mid, bottom, engraved, hint, to.fps, keyTop, keyBottom, keyInk, to.lipLight, to.lipDark)
}

/** A small lit readout set into the bezel. */
@Composable
private fun Lcd(text: String) {
    // Black glass in both themes, like the screen.
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF14201A))
            .border(2.dp, BEZEL_INK, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { T(text, size = 10f / 14f, weight = FontWeight.Black, color = Color(0xFF8FE0CF), tnum = true, spacing = 0.06f, maxLines = 1) }
}

/** A key on the set's control deck: hard black drop, pushes in when pressed. */
@Composable
private fun TvKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    teal: Boolean,
    enabled: Boolean,
    casing: Casing,
    onClick: () -> Unit,
) {
    val c = Gg.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val down = pressed && enabled
    val shape = RoundedCornerShape(10.dp)
    val fill = if (teal) Brush.verticalGradient(listOf(c.tealLight, c.teal))
    else Brush.verticalGradient(listOf(casing.keyTop, casing.keyBottom))
    val ink = if (teal) Color.White else casing.keyInk
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .offset { if (down) IntOffset(3.dp.roundToPx(), 3.dp.roundToPx()) else IntOffset.Zero }
            .then(if (down) Modifier else Modifier.neuShadow(c, 10.dp, 3.dp, Soft.NONE, hardColor = BEZEL_INK))
            .clip(shape)
            .background(fill)
            .border(2.dp, BEZEL_INK, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .height(42.dp)
            .padding(horizontal = if (label == null) 12.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ico(icon, size = 17.dp, tint = ink)
        if (label != null) T(label, size = 0.76f, color = ink, spacing = 0.05f, maxLines = 1)
    }
}

/**
 * The television. Top strip: power light, name, frame rate. Then the
 * picture. Then the control deck with the latency readout, Capture and
 * the fullscreen key, and a line of small print along the bottom edge.
 */
@Composable
fun TvFeed(
    frame: FeedFrame,
    online: Boolean,
    noPi: Boolean,
    fpsLabel: String,
    latencyLabel: String,
    caption: String,
    showFlag: Boolean,
    onCapture: () -> Unit,
    onFullscreen: () -> Unit,
    image: ImageBitmap? = null,
) {
    val c = Gg.colors
    val k = casing()
    val tvShape = RoundedCornerShape(18.dp)
    val glare = rememberInfiniteTransition(label = "glare")
    val gx by glare.animateFloat(
        -1.2f, 4.2f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing)), label = "glare",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .neuShadow(c, 18.dp, 4.dp, Soft.LG)
            .clip(tvShape)
            .background(angledBrush(172f, 0f to k.top, 0.46f to k.mid, 1f to k.bottom))
            .drawBehind {
                // The moulded rim: a lit lip along the top, a shaded one along the bottom.
                val lip = 2.dp.toPx()
                drawRect(Color.White.copy(alpha = k.lipLight), topLeft = Offset(0f, lip), size = Size(size.width, lip))
                drawRect(Color.Black.copy(alpha = k.lipDark), topLeft = Offset(0f, size.height - lip - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
            }
            .border(2.dp, c.edge, tvShape)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Led(online)
            T("LIVE FEED", size = 0.78f, weight = FontWeight.Black, color = k.engraved, spacing = 0.1f, maxLines = 1)
            T("CAM 01", size = 0.66f, color = k.hint, spacing = 0.1f, maxLines = 1)
            Box(Modifier.weight(1f))
            Lcd(fpsLabel)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(9.dp))
                .background(FEED_BLACK),
        ) {
            FeedCanvas(frame, Modifier.fillMaxSize(), image = image)
            Box(Modifier.fillMaxSize().scanLines())
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width * 0.26f
                    val x = gx * w
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent),
                            startX = x, endX = x + w,
                        ),
                        topLeft = Offset(x, 0f), size = Size(w, size.height),
                    )
                }
            )
            Box(Modifier.fillMaxSize().border(2.dp, Color.Black, RoundedCornerShape(9.dp)))
            if (showFlag) AlertFlag()
            if (!online) OfflinePanel(noPi)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T("LATENCY", size = 0.58f, color = k.hint, spacing = 0.12f, maxLines = 1)
                Lcd(latencyLabel)
            }
            TvKey(GgIcons.Shutter, "CAPTURE", teal = true, enabled = online, casing = k, onClick = onCapture)
            TvKey(GgIcons.Expand, null, teal = false, enabled = true, casing = k, onClick = onFullscreen)
        }

        T(
            caption, Modifier.padding(horizontal = 4.dp), size = 0.68f, weight = FontWeight.Medium,
            color = k.hint, lineHeight = 1.3f,
        )
    }
}