package com.garbageguard.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garbageguard.app.R
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.notify.Sounds
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.ggBackground
import com.garbageguard.app.ui.components.logoPainter
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.theme.Gg
import com.garbageguard.app.ui.theme.GgColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * A small hidden game: hold the logo for three seconds and it becomes a
 * blade. Bottles, bags and containers are tossed up from the water; slash
 * each one before it falls back. Miss one and the round is over. Nothing is
 * saved. It is only here for fun.
 */

private enum class Phase { READY, PLAYING, OVER }

private class Flying(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
)

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
    val leftHalf: Boolean, var life: Float,
)

private class TrailPoint(val at: Offset, var age: Float)

/** Everything that moves. Plain fields, stepped once a frame; [frame] tells Compose to redraw. */
private class SlashState(private val density: Float) {
    var width = 0f
    var height = 0f
    val items = ArrayList<Flying>()
    val pieces = ArrayList<Piece>()
    val trail = ArrayList<TrailPoint>()
    var finger: Offset? = null
    var heading = -90f
    var counts = IntArray(3)
    var missed = false
    private var untilSpawn = 0.6f

    val total get() = counts.sum()

    fun reset() {
        items.clear(); pieces.clear(); trail.clear()
        counts = IntArray(3)
        missed = false
        untilSpawn = 0.6f
        finger = null
    }

    private fun toss() {
        val size = (62f + Random.nextFloat() * 14f) * density
        val gravity = GRAVITY * density
        val peak = height * (0.5f + Random.nextFloat() * 0.3f)
        val x = width * (0.14f + Random.nextFloat() * 0.72f)
        val towardCentre = (width / 2f - x) / width
        items += Flying(
            x = x, y = height + size,
            vx = (towardCentre * 0.9f + (Random.nextFloat() - 0.5f) * 0.35f) * width,
            vy = -sqrt(2f * gravity * (peak + size)),
            angle = Random.nextFloat() * 360f, spin = (Random.nextFloat() - 0.5f) * 420f,
            kind = Random.nextInt(3), size = size,
        )
    }

    /** Advances the world by [dt] seconds. Returns true the moment an item is missed. */
    fun step(dt: Float): Boolean {
        if (width == 0f) return false
        val gravity = GRAVITY * density
        untilSpawn -= dt
        if (untilSpawn <= 0f) {
            // The pace climbs with the score: shorter gaps, then pairs and triples.
            val extra = if (total > 24 && Random.nextFloat() < 0.35f) 2 else if (total > 8 && Random.nextFloat() < 0.4f) 1 else 0
            repeat(1 + extra) { toss() }
            untilSpawn = (1.15f - total * 0.022f).coerceAtLeast(0.42f)
        }
        var miss = false
        for (it in items) {
            it.vy += gravity * dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.angle += it.spin * dt
            if (it.vy > 0f && it.y > height + it.size) miss = true
        }
        val gone = pieces.iterator()
        while (gone.hasNext()) {
            val p = gone.next()
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.life -= dt
            if (p.life <= 0f || p.y > height + p.size * 2) gone.remove()
        }
        val old = trail.iterator()
        while (old.hasNext()) {
            val t = old.next()
            t.age += dt
            if (t.age > TRAIL_SECONDS) old.remove()
        }
        if (miss) missed = true
        return miss
    }

    /** Moves the blade and cuts whatever the stroke crosses. Returns how many were cut. */
    fun slash(to: Offset): Int {
        val from = finger
        finger = to
        trail += TrailPoint(to, 0f)
        if (from == null) return 0
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (hypot(dx, dy) < 3f * density) return 0
        heading = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        var cut = 0
        val hit = items.iterator()
        while (hit.hasNext()) {
            val it = hit.next()
            if (distanceToSegment(it.x, it.y, from, to) < it.size * 0.52f) {
                hit.remove()
                counts[it.kind]++
                cut++
                // The two halves fly apart across the line of the cut.
                val nx = -dy / hypot(dx, dy)
                val ny = dx / hypot(dx, dy)
                val push = 190f * density
                pieces += Piece(it.x, it.y, it.vx + nx * push, it.vy + ny * push - 120f * density, it.angle, it.spin - 260f, it.kind, it.size, true, 1.1f)
                pieces += Piece(it.x, it.y, it.vx - nx * push, it.vy - ny * push - 120f * density, it.angle, it.spin + 260f, it.kind, it.size, false, 1.1f)
            }
        }
        return cut
    }

    fun lift() {
        finger = null
    }

    private fun distanceToSegment(px: Float, py: Float, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }

    companion object {
        const val GRAVITY = 1150f
        const val TRAIL_SECONDS = 0.16f
    }
}

@Composable
fun SlashGame(onExit: () -> Unit) {
    val c = Gg.colors
    val density = LocalDensity.current.density
    val view = LocalView.current
    val state = remember { SlashState(density) }
    var phase by remember { mutableStateOf(Phase.READY) }
    var frame by remember { mutableIntStateOf(0) }

    val painters = listOf(
        rememberVectorPainter(GgIcons.Bottle),
        rememberVectorPainter(GgIcons.Bag),
        rememberVectorPainter(GgIcons.Container),
    )
    val tints = listOf(c.bottle, c.bag, c.container)
    val logo = logoPainter()

    BackHandler(onBack = onExit)

    LaunchedEffect(phase) {
        if (phase != Phase.PLAYING) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.034f)
                last = now
                if (state.step(dt)) {
                    Sounds.play(R.raw.gg_miss)
                    Feel.thump(view)
                    phase = Phase.OVER
                }
                frame++
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .ggBackground(c)
            .onSizeChanged {
                state.width = it.width.toFloat()
                state.height = it.height.toFloat()
            }
            .pointerInput(phase) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (phase != Phase.PLAYING) return@awaitEachGesture
                    state.slash(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        if (state.slash(change.position) > 0) {
                            Sounds.play(R.raw.gg_slice, 0.8f)
                            Feel.tick(view)
                        }
                        change.consume()
                    }
                    state.lift()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame // read so every frame redraws
            drawWater(c)
            state.pieces.forEach { drawToken(it.x, it.y, it.angle, it.size, painters[it.kind], tints[it.kind], c, (it.life / 1.1f).coerceIn(0f, 1f), it.leftHalf) }
            state.items.forEach { drawToken(it.x, it.y, it.angle, it.size, painters[it.kind], tints[it.kind], c, 1f, null) }
            drawTrail(state.trail, c)
            // The blade: the logo is the guard, the steel points the way the hand is moving.
            val at = state.finger ?: Offset(size.width / 2f, size.height - 150.dp.toPx())
            if (phase == Phase.PLAYING) drawBlade(at, if (state.finger == null) -90f else state.heading, c)
            val guard = 46.dp.toPx()
            withTransform({ translate(at.x - guard / 2f, at.y - guard / 2f) }) {
                with(logo) { draw(Size(guard, guard)) }
            }
        }

        // ---- score ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            Row(
                Modifier.neuRaised(c, 14.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    T("SLASHED", size = 0.6f, color = c.muted, spacing = 0.08f)
                    T("${state.total}", size = 1.7f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f)
                }
                listOf(GgIcons.Bottle, GgIcons.Bag, GgIcons.Container).forEachIndexed { i, icon ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Ico(icon, size = 15.dp, tint = tints[i])
                        T("${state.counts[i]}", size = 0.9f, weight = FontWeight.Black, color = c.text, tnum = true)
                    }
                }
            }
            Box(Modifier.weight(1f))
            GgButton(null, onExit, icon = GgIcons.X, small = true, square = true)
        }

        // ---- start and end cards ----
        if (phase != Phase.PLAYING) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 26.dp)
                    .widthIn(max = 360.dp)
                    .neuRaised(c, 18.dp, hard = 5.dp, soft = Soft.LG)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(logo, contentDescription = null, modifier = Modifier.size(84.dp))
                if (phase == Phase.READY) {
                    T("GARBAGE SLASH", size = 1.5f, weight = FontWeight.Black, color = c.text, spacing = 0.04f)
                    T(
                        "You found it. Swipe to slash every bottle, bag and container before it falls back into the water. Miss one and the round is over.",
                        size = 0.86f, weight = FontWeight.Normal, color = c.muted, align = TextAlign.Center, lineHeight = 1.35f,
                    )
                } else {
                    T("GAME OVER", size = 1.5f, weight = FontWeight.Black, color = c.bad, spacing = 0.04f)
                    T("${state.total}", size = 3.2f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f)
                    T("ITEMS CLEARED FROM THE WATER", size = 0.66f, color = c.muted, spacing = 0.07f)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(GgIcons.Bottle, GgIcons.Bag, GgIcons.Container).forEachIndexed { i, icon ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Ico(icon, size = 18.dp, tint = tints[i])
                                T("${state.counts[i]}", size = 1.1f, weight = FontWeight.Black, color = c.text, tnum = true)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GgButton("Exit", onExit, Modifier.weight(1f), icon = GgIcons.X)
                    GgButton(
                        if (phase == Phase.READY) "Start" else "Retry",
                        {
                            state.reset()
                            phase = Phase.PLAYING
                        },
                        Modifier.weight(1f),
                        icon = if (phase == Phase.READY) GgIcons.Check else GgIcons.Refresh, kind = BtnKind.TEAL,
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** A band of water along the bottom, where everything is thrown from and falls back to. */
private fun DrawScope.drawWater(c: GgColors) {
    val top = size.height * 0.86f
    drawRect(
        Brush.verticalGradient(listOf(c.teal.copy(alpha = 0f), c.teal.copy(alpha = 0.32f)), startY = top - 60.dp.toPx(), endY = size.height),
        topLeft = Offset(0f, top - 60.dp.toPx()), size = Size(size.width, size.height - top + 60.dp.toPx()),
    )
    for (row in 0..2) {
        val y = top + row * 22.dp.toPx()
        val wave = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = row % 2 == 0
            val step = 34.dp.toPx()
            while (x < size.width) {
                relativeQuadraticTo(step / 2f, if (up) -7.dp.toPx() else 7.dp.toPx(), step, 0f)
                up = !up
                x += step
            }
        }
        drawPath(wave, c.teal.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * One item, drawn as a raised disc in the app's style with its class icon.
 * When [leftHalf] is set only that half is drawn, for the pieces after a cut.
 */
private fun DrawScope.drawToken(
    x: Float, y: Float, angle: Float, size: Float,
    icon: VectorPainter, tint: Color, c: GgColors, alpha: Float, leftHalf: Boolean?,
) {
    val r = size / 2f
    withTransform({
        translate(x, y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        val clip = when (leftHalf) {
            true -> Rect(-r * 1.4f, -r * 1.4f, 0f, r * 1.4f)
            false -> Rect(0f, -r * 1.4f, r * 1.4f, r * 1.4f)
            null -> Rect(-r * 1.4f, -r * 1.4f, r * 1.4f, r * 1.4f)
        }
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val drop = 4.dp.toPx()
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset(drop, drop))
            drawCircle(c.raised.copy(alpha = alpha), radius = r, center = Offset.Zero)
            drawCircle(tint.copy(alpha = 0.16f * alpha), radius = r, center = Offset.Zero)
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset.Zero, style = Stroke(width = 2.5.dp.toPx()))
            val s = size * 0.58f
            withTransform({ translate(-s / 2f, -s / 2f) }) {
                with(icon) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(tint)) }
            }
        }
    }
}

/** The fading streak behind the blade. */
private fun DrawScope.drawTrail(trail: List<TrailPoint>, c: GgColors) {
    for (i in 1 until trail.size) {
        val a = trail[i - 1]
        val b = trail[i]
        val fresh = 1f - (b.age / SlashState.TRAIL_SECONDS).coerceIn(0f, 1f)
        drawLine(c.edge.copy(alpha = 0.5f * fresh), a.at, b.at, strokeWidth = (11.dp.toPx() * fresh + 2f), cap = StrokeCap.Round)
        drawLine(c.tealLight.copy(alpha = fresh), a.at, b.at, strokeWidth = (7.dp.toPx() * fresh + 1f), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.9f * fresh), a.at, b.at, strokeWidth = (2.5.dp.toPx() * fresh), cap = StrokeCap.Round)
    }
}

/** The steel: a long tapered blade from the guard, pointing along [heading] degrees. */
private fun DrawScope.drawBlade(at: Offset, heading: Float, c: GgColors) {
    val rad = Math.toRadians(heading.toDouble())
    val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
    val side = Offset(-dir.y, dir.x)
    val length = 104.dp.toPx()
    val half = 9.dp.toPx()
    val start = at + dir * 16.dp.toPx()
    val tip = at + dir * length
    val blade = Path().apply {
        moveTo(start.x + side.x * half, start.y + side.y * half)
        lineTo(tip.x, tip.y)
        lineTo(start.x - side.x * half, start.y - side.y * half)
        close()
    }
    val drop = Offset(3.dp.toPx(), 3.dp.toPx())
    withTransform({ translate(drop.x, drop.y) }) { drawPath(blade, c.edge) }
    drawPath(blade, Brush.linearGradient(listOf(Color.White, c.tealLight), start = start, end = tip))
    drawPath(blade, c.edge, style = Stroke(width = 2.dp.toPx()))
    drawLine(c.edge.copy(alpha = 0.35f), start, tip, strokeWidth = 1.5.dp.toPx())
}
