package com.garbageguard.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.notify.Sfx
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

/**
 * Outline and drop colour for anything drawn on the black screen. The page's
 * ink is near black, so a border or hard shadow in it would vanish there.
 */
val GLASS_EDGE = Color(0xFFF2EBE1)

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
    alignTop: Boolean = false,
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
        val oy = if (alignTop) 0f else (size.height - fh * s) / 2f
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
            .neuShadow(c, 9.dp, 3.dp, Soft.NONE, hardColor = GLASS_EDGE)
            .clip(RoundedCornerShape(9.dp))
            .background(c.bad)
            .border(2.dp, GLASS_EDGE, RoundedCornerShape(9.dp))
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
            .neuShadow(c, 9.dp, 3.dp, Soft.NONE, hardColor = GLASS_EDGE)
            .clip(RoundedCornerShape(9.dp))
            .background(c.raised)
            .border(2.dp, if (c.isDark) GLASS_EDGE else c.edge, RoundedCornerShape(9.dp))
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
    /** A thin line around keys and readouts so their dark outline shows on the casing. */
    val rim: Color = Color.Transparent,
)

/** Draws the casing's rim just outside a key or readout. */
private fun Modifier.casingRim(rim: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    if (rim.alpha > 0f) {
        val o = 1.5.dp.toPx()
        val r = radius.toPx() + o
        drawRoundRect(
            rim, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

private val DarkCasing = Casing(
    top = Color(0xFF4E4740), mid = Color(0xFF2C2621), bottom = Color(0xFF1F1A16),
    engraved = Color(0xFFC9BFB2), hint = Color(0xFFBDB3A7), fps = Color(0xFF8FE0CF),
    keyTop = Color(0xFF5C554D), keyBottom = Color(0xFF36302A), keyInk = Color(0xFFEDE6DC),
    lipLight = 0.16f, lipDark = 0.35f,
    rim = Color(0x59FFFFFF),
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
    val rim by animateColorAsState(to.rim, spec, label = "c9")
    return Casing(top, mid, bottom, engraved, hint, to.fps, keyTop, keyBottom, keyInk, to.lipLight, to.lipDark, rim)
}

/** A small lit readout set into the bezel. */
@Composable
private fun Lcd(text: String, rim: Color = Color.Transparent) {
    // Black glass in both themes, like the screen.
    Box(
        Modifier
            .casingRim(rim, 6.dp)
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
    spinning: Boolean = false,
    lock: String? = null,
    sound: Sfx = Sfx.TAP,
    onClick: () -> Unit,
) {
    val c = Gg.colors
    val look = lockLook(lock)
    val enabled = enabled && look?.waiting != true
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val down = (pressed && enabled) || look?.waiting == true
    val shape = RoundedCornerShape(10.dp)
    val fill = if (teal) Brush.verticalGradient(listOf(c.tealLight, c.teal))
    else Brush.verticalGradient(listOf(casing.keyTop, casing.keyBottom))
    val ink = if (teal) Color.White else casing.keyInk
    Row(
        Modifier
            .alpha(if (enabled || look != null) 1f else 0.4f)
            .offset { if (down) IntOffset(3.dp.roundToPx(), 3.dp.roundToPx()) else IntOffset.Zero }
            .then(if (down) Modifier else Modifier.neuShadow(c, 10.dp, 3.dp, Soft.NONE, hardColor = Color.Black).casingRim(casing.rim, 10.dp))
            .clip(shape)
            .background(fill)
            .lockRing(look, 10.dp, c)
            .border(2.dp, BEZEL_INK, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                Feel.play(view, sound)
                onClick()
            }
            .height(42.dp)
            .padding(horizontal = if (label == null) 12.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ico(icon, if (spinning) Modifier.spin() else Modifier, size = 17.dp, tint = ink)
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
    onReconnect: () -> Unit = {},
    reconnecting: Boolean = false,
    /** Through Firebase: a still picture every few seconds, not video. */
    remote: Boolean = false,
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
            if (remote) Ico(GgIcons.Cloud, size = 13.dp, tint = c.cloud)
            T(if (remote) "REMOTE VIEW" else "LIVE FEED", size = 0.78f, weight = FontWeight.Black, color = k.engraved, spacing = 0.1f, maxLines = 1)
            T("CAM 01", size = 0.66f, color = k.hint, spacing = 0.1f, maxLines = 1)
            Box(Modifier.weight(1f))
            Lcd(fpsLabel, k.rim)
        }

        // The glass takes the camera's own shape, so the whole frame shows
        // with nothing cropped off the sides or the top and bottom.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(feedAspect(image))
                .clip(RoundedCornerShape(9.dp))
                .background(FEED_BLACK),
        ) {
            // Set inside the glass outline, so the outline never covers the edge of the frame.
            FeedCanvas(frame, Modifier.fillMaxSize().padding(GLASS_RIM), cover = image == null, image = image)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Ico(GgIcons.Clock, size = 10.dp, tint = k.hint)
                    T("LATENCY", size = 0.58f, color = k.hint, spacing = 0.12f, maxLines = 1)
                }
                Lcd(latencyLabel, k.rim)
            }
            // Reconnects the camera; the arrows turn until the picture is back.
            TvKey(GgIcons.Refresh, null, teal = false, enabled = !noPi && !reconnecting, casing = k, spinning = reconnecting, onClick = onReconnect)
            TvKey(GgIcons.Shutter, "CAPTURE", teal = true, enabled = online, casing = k, lock = "capture", onClick = onCapture)
            TvKey(GgIcons.Expand, null, teal = false, enabled = true, casing = k, sound = Sfx.OPEN, onClick = onFullscreen)
        }

        T(
            caption, Modifier.fillMaxWidth().padding(horizontal = 4.dp), size = 0.68f, weight = FontWeight.Medium,
            color = k.hint, lineHeight = 1.3f, fit = true,
        )
    }
}
/** Width over height of the camera picture, or the stand-in drawing's 16:10 when there is none yet. */
fun feedAspect(image: ImageBitmap?): Float =
    if (image != null && image.width > 0 && image.height > 0) (image.width.toFloat() / image.height).coerceIn(1f, 2.4f)
    else 16f / 10f

/** The glass outline is this thick; pictures are set inside it so their edges are never covered. */
val GLASS_RIM = 3.dp

private val RAIL_MIN = 78.dp
private val RAIL_ITEM = 96.dp

/**
 * The colours of the keys and readouts beside the monitor. They belong to
 * the page, not the set, so they follow the light or dark theme while the
 * monitor between them stays reversed.
 */
@Composable
private fun railCasing(): Casing {
    val c = Gg.colors
    return Casing(c.base, c.base, c.base2, c.text, c.muted, c.teal, c.raised, c.base2, c.text, 0f, 0f)
}

/**
 * The fullscreen monitor. The picture keeps the camera's own shape and takes
 * the full height of the phone, inside a slim reversed casing. Either side
 * of it stands one panel in the page's theme, the same width and height as
 * its twin: readings on the left, keys on the right. With [rails] off the
 * panels go and the picture has all the room there is.
 */
@Composable
fun WideMonitor(
    aspect: Float,
    rails: Boolean,
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    screen: @Composable BoxScope.() -> Unit,
) {
    val k = casing()
    val c = Gg.colors
    val glass = RoundedCornerShape(9.dp)
    val set = RoundedCornerShape(13.dp)
    // The monitor itself: the reversed casing as a slim bezel round the glass.
    val monitor: @Composable (Modifier) -> Unit = { place ->
        Box(
            place
                .neuShadow(c, 13.dp, 3.dp, Soft.SM)
                .clip(set)
                .background(angledBrush(172f, 0f to k.top, 0.46f to k.mid, 1f to k.bottom))
                .border(2.dp, c.edge, set)
                .padding(5.dp),
        ) {
            Box(Modifier.fillMaxSize().clip(glass).background(FEED_BLACK).border(2.dp, Color.Black, glass).padding(GLASS_RIM), content = screen)
        }
    }
    // A camera cut-out on one side is matched by the same margin on the
    // other, so the picture stays in the middle and the panels stay twins.
    val cut = WindowInsets.displayCutout
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cutLeft = with(density) { cut.getLeft(this, dir).toDp() }
    val cutRight = with(density) { cut.getRight(this, dir).toDp() }
    val even = maxOf(cutLeft, cutRight)

    BoxWithConstraints(
        modifier
            .ggBackground(c)
            .windowInsetsPadding(cut)
            .padding(start = even - cutLeft + 6.dp, end = even - cutRight + 6.dp, top = 6.dp, bottom = 8.dp),
    ) {
        if (maxHeight > maxWidth) {
            // An upright window, such as a tablet that does not turn: the
            // picture takes the full width and the two panels share the space beneath it.
            val across = maxWidth
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = if (rails) Arrangement.spacedBy(8.dp) else Arrangement.Center,
            ) {
                monitor(Modifier.size(across, (across - (5.dp + GLASS_RIM) * 2) / aspect + (5.dp + GLASS_RIM) * 2))
                if (rails) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RailPanel(Modifier.weight(1f).fillMaxHeight(), left)
                    RailPanel(Modifier.weight(1f).fillMaxHeight(), right)
                }
            }
            return@BoxWithConstraints
        }
        val gap = 7.dp
        // The casing and the glass rim take a fixed margin round the picture,
        // so the set is sized from the picture inward and no black bars are left.
        val frame = (5.dp + GLASS_RIM) * 2
        var h = maxHeight
        var w = (h - frame) * aspect + frame
        val room = maxWidth - if (rails) (RAIL_MIN + gap) * 2 else 0.dp
        if (w > room) {
            w = room
            h = (w - frame) / aspect + frame
        }
        val pw by animateDpAsState(w, tween(220), label = "pw")
        val ph by animateDpAsState(h, tween(220), label = "ph")
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (rails) RailPanel(Modifier.weight(1f).height(ph).padding(end = gap), left) else Box(Modifier.weight(1f))
            monitor(Modifier.size(pw, ph))
            if (rails) RailPanel(Modifier.weight(1f).height(ph).padding(start = gap), right) else Box(Modifier.weight(1f))
        }
    }
}

/**
 * One of the two panels beside the monitor: a raised card as tall as the
 * picture, with its groups spread evenly from top to bottom.
 */
@Composable
private fun RailPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Gg.colors
    Column(
        modifier.neuRaised(c, 14.dp, hard = 3.dp, soft = Soft.LG).padding(horizontal = 7.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

/** Things that belong together in a panel, kept close while the groups spread apart. */
@Composable
fun RailGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.widthIn(max = RAIL_ITEM).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A key beside the monitor: symbol over its name, hard drop, pushes in when pressed. Without a name it is a square symbol key. */
@Composable
fun RailKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    enabled: Boolean = true,
    toggle: Boolean = false,
    lock: String? = null,
    sound: Sfx? = null,
) {
    val c = Gg.colors
    val k = railCasing()
    val look = lockLook(lock)
    val enabled = enabled && look?.waiting != true
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val view = LocalView.current
    val down = (pressed && enabled) || look?.waiting == true
    val shape = RoundedCornerShape(10.dp)
    // Raised when at rest, with the soft pair of shadows; pressed, it sinks into a well.
    val fill = when {
        down -> Brush.verticalGradient(listOf(c.sunk, c.sunk))
        lit -> faceBrush(c.tealLight, c.teal)
        else -> raisedBrush(c)
    }
    val ink = if (lit && !down) Color.White else k.keyInk
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled || look != null) 1f else 0.4f)
            .offset { if (down) IntOffset(2.dp.roundToPx(), 2.dp.roundToPx()) else IntOffset.Zero }
            .then(if (down) Modifier else Modifier.neuShadow(c, 10.dp, 2.dp, Soft.SM))
            .clip(shape)
            .background(fill)
            .then(if (down) Modifier.neuInset(c, 10.dp) else Modifier.neuBevel(c, 10.dp))
            .lockRing(look, 10.dp, c)
            .border(2.dp, c.edge, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                Feel.play(view, sound ?: if (toggle) Sfx.CHOICE else Sfx.TAP)
                onClick()
            }
            .padding(horizontal = 3.dp, vertical = if (label == null) 8.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Ico(icon, size = 18.dp, tint = ink)
        if (label != null) T(label, Modifier.fillMaxWidth(), size = 0.58f, color = ink, spacing = 0.04f, align = TextAlign.Center, fit = true)
    }
}

/** Small lettering in a panel, with an optional symbol. */
@Composable
fun RailLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color? = null) {
    val k = railCasing()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (icon != null) Ico(icon, size = 10.dp, tint = tint ?: k.hint)
        T(text, size = 0.54f, color = k.hint, spacing = 0.1f, maxLines = 1)
    }
}

/** A line or two of small print in a panel. */
@Composable
fun RailNote(text: String) {
    T(text, Modifier.fillMaxWidth(), size = 0.56f, weight = FontWeight.Bold, color = railCasing().hint, align = TextAlign.Center, lineHeight = 1.2f)
}

/** A reading in a sunk well with its name beneath. */
@Composable
fun RailReadout(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color? = null) {
    val c = Gg.colors
    val shape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.fillMaxWidth().neuWell(c, 8.dp).padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) { T(value, size = 0.82f, weight = FontWeight.Black, color = c.teal, tnum = true, spacing = 0.03f, fit = true) }
        RailLabel(label, icon, tint)
    }
}

/** One class count in a sunk well: its symbol in its own colour, then the number. */
@Composable
fun RailChip(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, value: String) {
    val c = Gg.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().neuWell(c, 8.dp).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Ico(icon, size = 15.dp, tint = tint)
        T(value, size = 0.95f, weight = FontWeight.Black, color = c.text, tnum = true, maxLines = 1)
    }
}

/** The state of the water, as a lamp. It pulses while an alert is sounding. */
@Composable
fun RailState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String?,
    fill: Color,
    ink: Color,
    pulse: Boolean,
) {
    val c = Gg.colors
    val shape = RoundedCornerShape(9.dp)
    val a = if (pulse) 0.6f + 0.4f * breathe(1200) else 1f
    Column(
        Modifier.fillMaxWidth().neuWell(c, 9.dp, fill.copy(alpha = a)).padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Ico(icon, size = 15.dp, tint = ink)
        T(label.uppercase(), Modifier.fillMaxWidth(), size = 0.58f, weight = FontWeight.Black, color = ink, spacing = 0.04f, align = TextAlign.Center, fit = true)
        if (sub != null) T(sub.uppercase(), Modifier.fillMaxWidth(), size = 0.54f, color = ink, tnum = true, align = TextAlign.Center, fit = true)
    }
}

/**
 * The items gauge: a ring that fills as the count nears the limit, with the
 * count in the middle and the limit beneath.
 */
@Composable
fun RailGauge(fraction: Float, color: Color, count: String, limit: String) {
    val c = Gg.colors
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "gauge")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        // A sunk dial, the ring running round it, and a raised hub in the middle that carries the count.
        Box(
            Modifier.size(66.dp).neuShadow(c, 33.dp, 0.dp, Soft.SM).clip(CircleShape).background(c.sunk).neuInset(c, 33.dp).border(2.dp, c.edge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val w = 7.dp.toPx()
                val inset = w / 2f
                val box = Size(size.width - inset * 2, size.height - inset * 2)
                val at = Offset(inset, inset)
                drawArc(c.edge.copy(alpha = 0.16f), 135f, 270f, false, topLeft = at, size = box, style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                if (f > 0.01f) drawArc(color, 135f, 270f * f, false, topLeft = at, size = box, style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Box(
                Modifier.size(36.dp).neuShadow(c, 18.dp, 0.dp, Soft.SM).clip(CircleShape).background(raisedBrush(c)).neuBevel(c, 18.dp).border(2.dp, c.edge, CircleShape),
                contentAlignment = Alignment.Center,
            ) { T(count, size = 1.15f, weight = FontWeight.Black, color = c.text, tnum = true, maxLines = 1, lineHeight = 1f) }
        }
        RailLabel(limit, GgIcons.Gauge, c.warn)
    }
}
