package com.garbageguard.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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

/**
 * .tv, the live feed in its television bezel with the FPS tag, the alert
 * flag, the offline panel and the fullscreen key.
 */
@Composable
fun TvFeed(
    frame: FeedFrame,
    online: Boolean,
    noPi: Boolean,
    fpsLabel: String,
    showFlag: Boolean,
    onFullscreen: () -> Unit,
    image: ImageBitmap? = null,
) {
    val c = Gg.colors
    val tvShape = RoundedCornerShape(14.dp)
    val glare = rememberInfiniteTransition(label = "glare")
    val gx by glare.animateFloat(
        -1.2f, 3.2f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing)), label = "glare",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .neuShadow(c, 14.dp, 3.dp, Soft.NONE)
            .clip(tvShape)
            .background(
                angledBrush(170f, 0f to Color(0xFF4A443D), 0.6f to Color(0xFF241F1B), 1f to Color(0xFF241F1B))
            )
            .drawBehind {
                // inset 0 2px 0 rgba(255,255,255,.18), the lit top lip of the bezel
                drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
            }
            .border(2.dp, c.edge, tvShape),
    ) {
        Column(Modifier.padding(start = 7.dp, end = 7.dp, top = 7.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(7.dp))
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
                Box(Modifier.fillMaxSize().border(2.dp, Color.Black, RoundedCornerShape(7.dp)))
                if (showFlag) AlertFlag()
                if (!online) OfflinePanel(noPi)
            }
            Row(
                Modifier.height(15.dp).padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Led(online)
                T("CAM 01 · LIVE", size = 9f / 14f, color = Color(0xFFC9BFB2), spacing = 0.09f, maxLines = 1)
            }
        }
        // .fps-tag
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(7.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xB80A0806))
                .border(1.5.dp, c.teal, RoundedCornerShape(7.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) { T(fpsLabel, size = 9f / 14f, weight = FontWeight.Black, color = c.tealLight, tnum = true, spacing = 0.06f, maxLines = 1) }
        // .fsb
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val keyShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 9.dp, bottom = 8.dp)
                .offset { if (pressed) IntOffset(3.dp.roundToPx(), 3.dp.roundToPx()) else IntOffset.Zero }
                .size(44.dp)
                .alpha(0.85f)
                .then(if (pressed) Modifier else Modifier.neuShadow(c, 12.dp, 3.dp, Soft.NONE))
                .clip(keyShape)
                .background(if (c.isDark) Color(0xC71A1714) else Color(0xD1F5F0E8))
                .border(2.dp, c.edge, keyShape)
                .clickable(interactionSource = src, indication = null, onClick = onFullscreen),
            contentAlignment = Alignment.Center,
        ) { Ico(GgIcons.Expand, size = 20.dp, tint = if (c.isDark) c.text else Color(0xFF16130F)) }
    }
}
