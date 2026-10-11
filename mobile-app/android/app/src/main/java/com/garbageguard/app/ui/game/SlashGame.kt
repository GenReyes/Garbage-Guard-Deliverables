package com.garbageguard.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.garbageguard.app.ui.components.neuWell
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
 * each one before it falls back.
 *
 *  - Three lives. A missed item or a cut bomb costs one.
 *  - The combo counts every cut in a row and grows what each cut is worth.
 *  - At 50 points, then 100, 200, 300 and so on, a boss arrives: a big heap
 *    that stays put while ordinary items keep coming. Slash it down, then
 *    one last cut splits it along the line of that cut and flips the whole
 *    app between light and dark.
 *  - The water answers the blade, and a bomb throws up a splash.
 *
 * Nothing is saved. It is only here for fun.
 */

private enum class Phase { READY, PLAYING, OVER }

/** Things the frame loop tells the screen about, so it can play a sound or flip the theme. */
private enum class Ev { CUT, COMBO, LIFE_LOST, BOMB, BOSS_IN, BOSS_HIT, BOSS_READY, BOSS_DOWN, OVER }

private const val BOMB = 3
private const val WATER = 4
private const val HEAP = 5

private class Flying(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
)

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
    val leftHalf: Boolean, var life: Float, val full: Float,
)

private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, var life: Float)

private class Popup(val text: String, val x: Float, var y: Float, var age: Float, val kind: Int)

private class Ripple(val x: Float, var age: Float, val strength: Float)

private class TrailPoint(val at: Offset, var age: Float)

/** The heap that arrives at each milestone. It does not move; it has to be worn down. */
private class Boss(val level: Int, val maxHp: Int, val seconds: Float) {
    var hp = maxHp
    var timeLeft = seconds
    var flash = 0f
    var cooldown = 0f
    var age = 0f

    /** Health gone: one more cut finishes it. */
    val ready get() = hp <= 0
}

/** The cut that finished a boss, drawn across the whole screen while the theme flips. */
private class StyleCut(val through: Offset, val dir: Offset, var age: Float)

/** Everything that moves. Plain fields, stepped once a frame; a frame counter tells Compose to redraw. */
private class SlashState(private val density: Float) {
    var width = 0f
    var height = 0f
    val items = ArrayList<Flying>()
    val pieces = ArrayList<Piece>()
    val drops = ArrayList<Drop>()
    val popups = ArrayList<Popup>()
    val ripples = ArrayList<Ripple>()
    val trail = ArrayList<TrailPoint>()
    val events = ArrayList<Ev>()
    var finger: Offset? = null
    var heading = -90f
    var counts = IntArray(3)
    var score = 0
    var lives = LIVES
    var combo = 0
    var bestCombo = 0
    var bossesDown = 0
    var boss: Boss? = null
    var styleCut: StyleCut? = null
    var flash = 0f
    var shake = 0f
    var surge = 0f
    private var nextBossAt = FIRST_BOSS
    private var untilSpawn = 0.6f
    private var sinceRipple = 9f

    val waterTop get() = height * 0.86f
    val bossAt get() = Offset(width / 2f, height * 0.31f)
    val bossRadius get() = 76f * density

    fun reset() {
        items.clear(); pieces.clear(); drops.clear(); popups.clear(); ripples.clear(); trail.clear(); events.clear()
        counts = IntArray(3)
        score = 0
        lives = LIVES
        combo = 0
        bestCombo = 0
        bossesDown = 0
        boss = null
        styleCut = null
        flash = 0f
        shake = 0f
        surge = 0f
        nextBossAt = FIRST_BOSS
        untilSpawn = 0.6f
        finger = null
    }

    private fun toss(bomb: Boolean) {
        val size = (62f + Random.nextFloat() * 14f) * density
        val gravity = GRAVITY * density
        // While a boss is up, items stay low so they are not hidden behind it.
        val peak = height * (if (boss != null) 0.3f + Random.nextFloat() * 0.15f else 0.5f + Random.nextFloat() * 0.3f)
        val x = width * (0.14f + Random.nextFloat() * 0.72f)
        val towardCentre = (width / 2f - x) / width
        items += Flying(
            x = x, y = height + size,
            vx = (towardCentre * 0.9f + (Random.nextFloat() - 0.5f) * 0.35f) * width,
            vy = -sqrt(2f * gravity * (peak + size)),
            angle = Random.nextFloat() * 360f, spin = (Random.nextFloat() - 0.5f) * 420f,
            kind = if (bomb) BOMB else Random.nextInt(3), size = size,
        )
    }

    private fun loseLife(label: String, x: Float, y: Float) {
        lives--
        combo = 0
        popups += Popup(label, x, y, 0f, BOMB)
        events += if (lives <= 0) Ev.OVER else Ev.LIFE_LOST
    }

    private fun splash(x: Float, big: Boolean) {
        ripples += Ripple(x, 0f, if (big) 2.4f else 1f)
        val n = if (big) 14 else 3
        for (i in 0 until n) {
            val a = -1.57f + (Random.nextFloat() - 0.5f) * (if (big) 2.2f else 1.2f)
            val v = (0.4f + Random.nextFloat() * 0.6f) * (if (big) 760f else 300f) * density
            drops += Drop(x + (Random.nextFloat() - 0.5f) * 30f * density, waterTop, cos(a) * v, sin(a) * v, WATER, 0.5f + Random.nextFloat() * 0.5f)
        }
    }

    /** Advances the world by [dt] seconds. What happened is left in [events]. */
    fun step(dt: Float) {
        if (width == 0f) return
        val gravity = GRAVITY * density
        sinceRipple += dt

        // A boss arrives when the score crosses the next milestone: 50, 100, 200, 300 ...
        if (boss == null && styleCut == null && score >= nextBossAt) {
            val level = bossesDown + 1
            boss = Boss(level, maxHp = 26 + level * 10, seconds = 15f)
            nextBossAt = if (nextBossAt < 100) 100 else nextBossAt + 100
            popups += Popup("BOSS", width / 2f, height * 0.5f, 0f, HEAP)
            events += Ev.BOSS_IN
        }
        boss?.let { b ->
            b.age += dt
            if (b.flash > 0f) b.flash = (b.flash - dt * 6f).coerceAtLeast(0f)
            if (b.cooldown > 0f) b.cooldown -= dt
            if (!b.ready) {
                b.timeLeft -= dt
                if (b.timeLeft <= 0f) {
                    // Too slow: the heap sinks back and takes a life with it.
                    boss = null
                    splash(width / 2f, big = true)
                    loseLife("TOO SLOW", width / 2f, height * 0.4f)
                }
            }
        }

        untilSpawn -= dt
        if (untilSpawn <= 0f) {
            if (boss != null) {
                // A few ordinary ones, enough to keep an eye on while cutting the boss down.
                toss(bomb = Random.nextFloat() < 0.12f)
                untilSpawn = 1.5f + Random.nextFloat() * 0.5f
            } else {
                val extra = if (score > 40 && Random.nextFloat() < 0.4f) 2 else if (score > 8 && Random.nextFloat() < 0.45f) 1 else 0
                repeat(1 + extra) { toss(bomb = false) }
                if (score >= 5 && Random.nextFloat() < 0.22f) toss(bomb = true)
                untilSpawn = (1.2f - counts.sum() * 0.012f).coerceAtLeast(0.55f)
            }
        }

        val fall = items.iterator()
        while (fall.hasNext()) {
            val it = fall.next()
            it.vy += gravity * dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.angle += it.spin * dt
            if (it.vy > 0f && it.y > height + it.size) {
                fall.remove()
                // A bomb that falls back is a bomb avoided. Anything else costs a life.
                if (it.kind != BOMB) {
                    splash(it.x.coerceIn(0f, width), big = false)
                    loseLife("MISS", it.x.coerceIn(60f * density, width - 60f * density), waterTop - 60f * density)
                }
            }
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
        val dry = drops.iterator()
        while (dry.hasNext()) {
            val d = dry.next()
            d.vy += gravity * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.life -= dt
            if (d.life <= 0f) dry.remove()
        }
        val faded = popups.iterator()
        while (faded.hasNext()) {
            val p = faded.next()
            p.age += dt
            p.y -= 46f * density * dt
            if (p.age > POPUP_SECONDS) faded.remove()
        }
        val calm = ripples.iterator()
        while (calm.hasNext()) {
            val r = calm.next()
            r.age += dt
            if (r.age > RIPPLE_SECONDS) calm.remove()
        }
        val old = trail.iterator()
        while (old.hasNext()) {
            val t = old.next()
            t.age += dt
            if (t.age > TRAIL_SECONDS) old.remove()
        }
        styleCut?.let {
            it.age += dt
            if (it.age > STYLE_SECONDS) styleCut = null
        }
        if (flash > 0f) flash = (flash - dt * 2.2f).coerceAtLeast(0f)
        if (shake > 0f) shake = (shake - dt * 2.4f).coerceAtLeast(0f)
        if (surge > 0f) surge = (surge - dt * 1.3f).coerceAtLeast(0f)
    }

    /** Moves the blade and cuts whatever the stroke crosses. */
    fun slash(to: Offset) {
        val from = finger
        finger = to
        trail += TrailPoint(to, 0f)
        // The blade drawn through the water leaves a ripple behind it.
        if (to.y > waterTop && sinceRipple > 0.07f) {
            sinceRipple = 0f
            splash(to.x, big = false)
        }
        if (from == null) return
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = hypot(dx, dy)
        if (len < 3f * density) return
        heading = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val nx = -dy / len
        val ny = dx / len

        val hit = items.iterator()
        while (hit.hasNext()) {
            val it = hit.next()
            if (distanceToSegment(it.x, it.y, from, to) >= it.size * 0.52f) continue
            hit.remove()
            if (it.kind == BOMB) {
                flash = 1f
                shake = 1f
                surge = 1f
                popups += Popup("BOOM", it.x, it.y, 0f, BOMB)
                for (n in 0 until 18) burst(it.x, it.y, BOMB, 520f)
                // The blast throws the water up right across the screen.
                for (n in 0..5) splash(width * (0.08f + n * 0.168f), big = true)
                events += Ev.BOMB
                loseLife("-1", it.x, it.y + 40f * density)
                continue
            }
            counts[it.kind]++
            combo++
            if (combo > bestCombo) bestCombo = combo
            // Every ten cuts in a row make each cut worth one more.
            val worth = 1 + combo / 10
            score += worth
            if (combo % 5 == 0) {
                popups += Popup("COMBO x$combo", it.x, it.y - it.size, 0f, WATER)
                events += Ev.COMBO
            } else {
                if (worth > 1) popups += Popup("+$worth", it.x, it.y - it.size * 0.6f, 0f, it.kind)
                events += Ev.CUT
            }
            // The two halves fly apart across the line of the cut, with a little spray.
            val push = 190f * density
            pieces += Piece(it.x, it.y, it.vx + nx * push, it.vy + ny * push - 120f * density, it.angle, it.spin - 260f, it.kind, it.size, true, 1.1f, 1.1f)
            pieces += Piece(it.x, it.y, it.vx - nx * push, it.vy - ny * push - 120f * density, it.angle, it.spin + 260f, it.kind, it.size, false, 1.1f, 1.1f)
            for (n in 0 until 7) burst(it.x, it.y, it.kind, 300f)
        }

        val b = boss ?: return
        val c = bossAt
        if (distanceToSegment(c.x, c.y, from, to) >= bossRadius * 0.95f || len < 5f * density) return
        if (!b.ready) {
            if (b.cooldown > 0f) return
            b.cooldown = 0.045f
            b.hp--
            b.flash = 1f
            for (n in 0 until 3) burst(c.x + (Random.nextFloat() - 0.5f) * bossRadius, c.y + (Random.nextFloat() - 0.5f) * bossRadius, Random.nextInt(3), 340f)
            if (b.ready) {
                popups += Popup("FINISH IT", c.x, c.y - bossRadius - 50f * density, 0f, HEAP)
                events += Ev.BOSS_READY
            } else events += Ev.BOSS_HIT
            return
        }
        // The finishing cut. The heap splits along the line the hand chose:
        // each half is turned so its flat side lies on that line, then pushed off it.
        val turn = Math.toDegrees(atan2(-dx, dy).toDouble()).toFloat()
        val size = bossRadius * 2f
        val push = 260f * density
        pieces += Piece(c.x, c.y, -nx * push, -ny * push - 60f * density, turn, -40f, HEAP, size, true, 1.6f, 1.6f)
        pieces += Piece(c.x, c.y, nx * push, ny * push - 60f * density, turn, 40f, HEAP, size, false, 1.6f, 1.6f)
        for (n in 0 until 34) burst(c.x, c.y, Random.nextInt(3), 620f)
        styleCut = StyleCut(c, Offset(dx / len, dy / len), 0f)
        val bonus = 10 * b.level
        score += bonus
        bossesDown++
        boss = null
        shake = 0.7f
        popups += Popup("STYLE SWITCH  +$bonus", c.x, c.y, 0f, WATER)
        events += Ev.BOSS_DOWN
    }

    private fun burst(x: Float, y: Float, kind: Int, speed: Float) {
        val a = Random.nextFloat() * 6.283f
        val v = (0.35f + Random.nextFloat() * 0.65f) * speed * density
        drops += Drop(x, y, cos(a) * v, sin(a) * v - 160f * density, kind, 0.45f + Random.nextFloat() * 0.35f)
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
        const val LIVES = 3
        const val FIRST_BOSS = 50
        const val GRAVITY = 1150f
        const val TRAIL_SECONDS = 0.16f
        const val POPUP_SECONDS = 1.1f
        const val RIPPLE_SECONDS = 0.9f
        const val STYLE_SECONDS = 0.7f
    }
}

@Composable
fun SlashGame(onExit: () -> Unit, onToggleTheme: () -> Unit) {
    val c = Gg.colors
    val density = LocalDensity.current.density
    val view = LocalView.current
    val state = remember { SlashState(density) }
    var phase by remember { mutableStateOf(Phase.READY) }
    var frame by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val flipTheme by rememberUpdatedState(onToggleTheme)

    val painters = listOf(
        rememberVectorPainter(GgIcons.Bottle),
        rememberVectorPainter(GgIcons.Bag),
        rememberVectorPainter(GgIcons.Container),
        rememberVectorPainter(GgIcons.Warn),
    )
    // Class colours, the bomb's red, the water's teal, and the boss in the page's ink. All follow the theme.
    val tints = listOf(c.bottle, c.bag, c.container, c.bad, c.teal, c.text)
    val logo = logoPainter()

    BackHandler(onBack = onExit)

    /** Turns what just happened into sound, a tick, and for a beaten boss the change of theme. */
    fun react(): Boolean {
        var over = false
        var cut = false
        for (e in state.events) when (e) {
            Ev.CUT, Ev.BOSS_HIT -> cut = true
            Ev.COMBO -> { Sounds.play(R.raw.gg_combo, 0.8f); Feel.thump(view) }
            Ev.LIFE_LOST -> { Sounds.play(R.raw.gg_miss); Feel.thump(view) }
            Ev.BOMB -> { Sounds.play(R.raw.gg_bomb); Feel.thump(view) }
            Ev.BOSS_IN -> { Sounds.play(R.raw.gg_boss); Feel.thump(view) }
            Ev.BOSS_READY -> { Sounds.play(R.raw.gg_combo); Feel.thump(view) }
            Ev.BOSS_DOWN -> {
                Sounds.play(R.raw.gg_win)
                Feel.thump(view)
                // The finishing cut flips the whole app between light and dark.
                flipTheme()
            }
            Ev.OVER -> { Sounds.play(R.raw.gg_miss); Feel.thump(view); over = true }
        }
        if (cut) {
            Sounds.play(R.raw.gg_slice, 0.8f)
            Feel.tick(view)
        }
        state.events.clear()
        return over
    }

    LaunchedEffect(phase) {
        if (phase != Phase.PLAYING) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.034f)
                last = now
                state.step(dt)
                if (react()) phase = Phase.OVER
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
                        state.slash(change.position)
                        if (react()) phase = Phase.OVER
                        change.consume()
                    }
                    state.lift()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame // read so every frame redraws
            // A bomb shakes the whole scene.
            val jolt = state.shake * 9.dp.toPx()
            withTransform({ translate((Random.nextFloat() - 0.5f) * jolt, (Random.nextFloat() - 0.5f) * jolt) }) {
                drawWater(c, state.waterTop, state.surge, state.ripples)
                state.drops.forEach {
                    drawCircle(
                        tints[it.kind].copy(alpha = (it.life * 2f).coerceIn(0f, 1f)),
                        radius = (if (it.kind == WATER) 5 else 4).dp.toPx(), center = Offset(it.x, it.y),
                    )
                }
                state.boss?.let { drawBoss(it, state.bossAt, state.bossRadius, painters, tints, c) }
                state.pieces.forEach {
                    val fade = (it.life / it.full).coerceIn(0f, 1f)
                    if (it.kind == HEAP) drawHeap(Offset(it.x, it.y), it.size / 2f, it.angle, painters, tints, c, fade, it.leftHalf, 0f)
                    else drawToken(it.x, it.y, it.angle, it.size, painters[it.kind], tints[it.kind], c, fade, it.leftHalf, false)
                }
                state.items.forEach { drawToken(it.x, it.y, it.angle, it.size, painters[it.kind], tints[it.kind], c, 1f, null, it.kind == BOMB) }
                drawTrail(state.trail, c)
                // The blade: the logo is the guard, the steel points the way the hand is moving.
                val at = state.finger ?: Offset(size.width / 2f, size.height - 150.dp.toPx())
                if (phase == Phase.PLAYING) drawBlade(at, if (state.finger == null) -90f else state.heading, c)
                val guard = 46.dp.toPx()
                withTransform({ translate(at.x - guard / 2f, at.y - guard / 2f) }) {
                    with(logo) { draw(Size(guard, guard)) }
                }
                state.styleCut?.let { drawStyleCut(it, c) }
                state.popups.forEach { drawPopup(it, measurer, tints, c) }
            }
            if (state.flash > 0f) drawRect(c.bad.copy(alpha = 0.5f * state.flash))
        }

        // ---- score, lives, combo, boss ----
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.neuRaised(c, 14.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Ico(GgIcons.Target, size = 10.dp, tint = c.teal)
                            T("SCORE", size = 0.6f, color = c.muted, spacing = 0.08f)
                        }
                        T("${state.score}", size = 1.7f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f)
                    }
                    listOf(GgIcons.Bottle, GgIcons.Bag, GgIcons.Container).forEachIndexed { i, icon ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Ico(icon, size = 14.dp, tint = tints[i])
                            T("${state.counts[i]}", size = 0.85f, weight = FontWeight.Black, color = c.text, tnum = true)
                        }
                    }
                }
                Box(Modifier.weight(1f))
                GgButton(null, onToggleTheme, icon = if (c.isDark) GgIcons.Sun else GgIcons.Moon, small = true, square = true)
                GgButton(null, onExit, icon = GgIcons.X, small = true, square = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Three lives, as three shields.
                Row(
                    Modifier.neuRaised(c, 12.dp, hard = 2.dp).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (i in 0 until SlashState.LIVES) {
                        val alive = i < state.lives
                        Ico(if (alive) GgIcons.Shield else GgIcons.X, size = 16.dp, tint = if (alive) c.teal else c.muted)
                    }
                }
                if (state.combo >= 2) {
                    Row(
                        Modifier.neuRaised(c, 12.dp, hard = 2.dp, fillColor = c.tealTint).padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Ico(GgIcons.Up, size = 13.dp, tint = c.teal)
                        T("COMBO x${state.combo}", size = 0.74f, weight = FontWeight.Black, color = c.text, tnum = true, spacing = 0.04f, maxLines = 1)
                    }
                }
            }
            state.boss?.let { b ->
                // The boss's health, and how long is left to bring it down.
                Row(
                    Modifier.fillMaxWidth().neuRaised(c, 12.dp, hard = 2.dp, edge = c.bad).padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Ico(GgIcons.Warn, size = 14.dp, tint = c.bad)
                    T(if (b.ready) "FINISH IT" else "BOSS ${b.level}", size = 0.72f, weight = FontWeight.Black, color = c.bad, spacing = 0.05f, maxLines = 1)
                    Row(Modifier.weight(1f).height(14.dp).neuWell(c, 7.dp)) {
                        val frac = (b.hp.toFloat() / b.maxHp).coerceIn(0f, 1f)
                        if (frac > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(if (frac > 0.5f) c.bad else c.warn))
                    }
                    Ico(GgIcons.Clock, size = 13.dp, tint = c.bag)
                    T(if (b.ready) "--" else "${b.timeLeft.toInt() + 1}", size = 0.8f, weight = FontWeight.Black, color = c.text, tnum = true, maxLines = 1)
                }
            }
        }

        // ---- start and end cards ----
        if (phase != Phase.PLAYING) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 380.dp)
                    .neuRaised(c, 18.dp, hard = 5.dp, soft = Soft.LG)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Image(logo, contentDescription = null, modifier = Modifier.size(78.dp))
                if (phase == Phase.READY) {
                    T("GARBAGE SLASH", size = 1.5f, weight = FontWeight.Black, color = c.text, spacing = 0.04f)
                    Rule(GgIcons.Check, c.teal, "Slash every bottle, bag and container.")
                    Rule(GgIcons.Shield, c.bottle, "Three lives. A miss costs one.")
                    Rule(GgIcons.Warn, c.bad, "A bomb costs one too. Let it fall.")
                    Rule(GgIcons.Up, c.container, "Cuts in a row build your combo.")
                    Rule(GgIcons.Target, c.cloud, "At 50 points a boss arrives.")
                    Rule(GgIcons.SunMoon, c.bag, "Finish it to flip light and dark.")
                } else {
                    T("GAME OVER", size = 1.5f, weight = FontWeight.Black, color = c.bad, spacing = 0.04f)
                    T("${state.score}", size = 3.2f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f)
                    T("POINTS", size = 0.66f, color = c.muted, spacing = 0.07f)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(GgIcons.Bottle, GgIcons.Bag, GgIcons.Container).forEachIndexed { i, icon ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Ico(icon, size = 18.dp, tint = tints[i])
                                T("${state.counts[i]}", size = 1.1f, weight = FontWeight.Black, color = c.text, tnum = true)
                            }
                        }
                    }
                    T(
                        "BEST COMBO x${state.bestCombo}  ·  BOSSES ${state.bossesDown}",
                        Modifier.fillMaxWidth(), size = 0.66f, color = c.muted, spacing = 0.05f, align = TextAlign.Center, fit = true,
                    )
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
    }
}

/** One line of the rules, each with its own symbol. */
@Composable
private fun Rule(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ico(icon, size = 15.dp, tint = tint)
        T(text, Modifier.weight(1f), size = 0.82f, weight = FontWeight.Bold, color = Gg.colors.text, fit = true)
    }
}

/**
 * The water along the bottom, where everything is thrown from and falls back
 * to. It lifts after a bomb ([surge]) and carries a ring wherever the blade
 * or a falling item touched it.
 */
private fun DrawScope.drawWater(c: GgColors, waterTop: Float, surge: Float, ripples: List<Ripple>) {
    val lift = surge * 26.dp.toPx()
    val top = waterTop - lift
    drawRect(
        Brush.verticalGradient(listOf(c.teal.copy(alpha = 0f), c.teal.copy(alpha = 0.32f + 0.2f * surge)), startY = top - 60.dp.toPx(), endY = size.height),
        topLeft = Offset(0f, top - 60.dp.toPx()), size = Size(size.width, size.height - top + 60.dp.toPx()),
    )
    for (row in 0..2) {
        val y = top + row * 22.dp.toPx()
        val swell = (7f + 9f * surge).dp.toPx()
        val wave = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = row % 2 == 0
            val step = 34.dp.toPx()
            while (x < size.width) {
                relativeQuadraticTo(step / 2f, if (up) -swell else swell, step, 0f)
                up = !up
                x += step
            }
        }
        drawPath(wave, c.teal.copy(alpha = 0.35f + 0.3f * surge), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
    ripples.forEach { r ->
        val t = (r.age / SlashState.RIPPLE_SECONDS).coerceIn(0f, 1f)
        val w = (30f + 150f * t).dp.toPx() * r.strength
        val h = w * 0.22f
        drawOval(
            c.tealLight.copy(alpha = (1f - t) * 0.85f),
            topLeft = Offset(r.x - w / 2f, top + 6.dp.toPx() - h / 2f), size = Size(w, h),
            style = Stroke(width = (3f * (1f - t) + 1f).dp.toPx()),
        )
    }
}

/**
 * One item, drawn as a raised disc in the app's style with its symbol.
 * When [leftHalf] is set only that half is drawn, for the pieces after a
 * cut. A bomb is the same disc in dark ink with a red edge.
 */
private fun DrawScope.drawToken(
    x: Float, y: Float, angle: Float, size: Float,
    icon: VectorPainter, tint: Color, c: GgColors, alpha: Float, leftHalf: Boolean?, bomb: Boolean,
) {
    val r = size / 2f
    withTransform({
        translate(x, y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        val clip = halfClip(r, leftHalf)
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val drop = 4.dp.toPx()
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset(drop, drop))
            if (bomb) {
                drawCircle(Color(0xFF16130F), radius = r, center = Offset.Zero)
                drawCircle(tint, radius = r, center = Offset.Zero, style = Stroke(width = 3.5.dp.toPx()))
            } else {
                drawCircle(c.raised.copy(alpha = alpha), radius = r, center = Offset.Zero)
                drawCircle(tint.copy(alpha = 0.16f * alpha), radius = r, center = Offset.Zero)
                drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset.Zero, style = Stroke(width = 2.5.dp.toPx()))
            }
            val s = size * 0.58f
            withTransform({ translate(-s / 2f, -s / 2f) }) {
                with(icon) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(tint)) }
            }
        }
    }
}

private fun halfClip(r: Float, leftHalf: Boolean?): Rect = when (leftHalf) {
    true -> Rect(-r * 1.5f, -r * 1.5f, 0f, r * 1.5f)
    false -> Rect(0f, -r * 1.5f, r * 1.5f, r * 1.5f)
    null -> Rect(-r * 1.5f, -r * 1.5f, r * 1.5f, r * 1.5f)
}

/**
 * The boss's body: a big heap of all three kinds, heavy outline, hard drop.
 * Also used for its two halves once it has been cut.
 */
private fun DrawScope.drawHeap(
    at: Offset, r: Float, angle: Float, painters: List<VectorPainter>, tints: List<Color>,
    c: GgColors, alpha: Float, leftHalf: Boolean?, flash: Float,
) {
    withTransform({
        translate(at.x, at.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        val clip = halfClip(r, leftHalf)
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val drop = 7.dp.toPx()
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset(drop, drop))
            drawCircle(c.raised.copy(alpha = alpha), radius = r, center = Offset.Zero)
            drawCircle(c.bad.copy(alpha = 0.2f * alpha), radius = r, center = Offset.Zero)
            drawCircle(c.sunk.copy(alpha = alpha), radius = r * 0.62f, center = Offset.Zero)
            drawCircle(c.edge.copy(alpha = alpha), radius = r * 0.62f, center = Offset.Zero, style = Stroke(width = 2.dp.toPx()))
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset.Zero, style = Stroke(width = 4.dp.toPx()))
            // The three kinds piled together.
            val s = r * 0.52f
            for (i in 0..2) {
                val a = Math.toRadians(-90.0 + i * 120.0)
                val cx = cos(a).toFloat() * r * 0.33f
                val cy = sin(a).toFloat() * r * 0.33f
                withTransform({ translate(cx - s / 2f, cy - s / 2f) }) {
                    with(painters[i]) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(tints[i])) }
                }
            }
            if (flash > 0f) drawCircle(Color.White.copy(alpha = 0.6f * flash), radius = r, center = Offset.Zero)
        }
    }
}

/** The boss in place: a slow bob, a jolt when hit, a ring of remaining time, and a guide line when it can be finished. */
private fun DrawScope.drawBoss(b: Boss, centre: Offset, r: Float, painters: List<VectorPainter>, tints: List<Color>, c: GgColors) {
    val arrive = (b.age / 0.35f).coerceIn(0f, 1f)
    val bob = sin(b.age * 2.2f) * 5.dp.toPx()
    val jolt = b.flash * 5.dp.toPx()
    val at = Offset(centre.x + (Random.nextFloat() - 0.5f) * jolt, centre.y + bob + (Random.nextFloat() - 0.5f) * jolt)
    val pulse = if (b.ready) 1f + 0.05f * sin(b.age * 14f) else 1f
    val radius = r * arrive * pulse
    drawHeap(at, radius, 0f, painters, tints, c, 1f, null, b.flash)
    if (b.ready) {
        // Health is gone. A dashed ring and a turning line invite the last cut.
        drawCircle(
            c.tealLight, radius = radius + 12.dp.toPx(), center = at,
            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()), b.age * 60f)),
        )
        val a = b.age * 1.6f
        val d = Offset(cos(a), sin(a)) * (radius + 26.dp.toPx())
        val dashes = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 10.dp.toPx()))
        drawLine(c.edge, at - d, at + d, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes)
        drawLine(Color.White, at - d, at + d, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes)
    } else {
        // Time left, as a ring that empties.
        val left = (b.timeLeft / b.seconds).coerceIn(0f, 1f)
        val ring = radius + 10.dp.toPx()
        drawArc(
            if (left > 0.35f) c.warn else c.bad, startAngle = -90f, sweepAngle = 360f * left, useCenter = false,
            topLeft = Offset(at.x - ring, at.y - ring), size = Size(ring * 2, ring * 2),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * The finishing cut, carried right across the screen: one side washes
 * bright for a moment and a line of light runs along the cut, while the
 * theme changes underneath.
 */
private fun DrawScope.drawStyleCut(s: StyleCut, c: GgColors) {
    val t = (s.age / SlashState.STYLE_SECONDS).coerceIn(0f, 1f)
    val far = hypot(size.width, size.height) * 1.5f
    val a = s.through - s.dir * far
    val b = s.through + s.dir * far
    val n = Offset(-s.dir.y, s.dir.x)
    val wash = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(b.x + n.x * far, b.y + n.y * far)
        lineTo(a.x + n.x * far, a.y + n.y * far)
        close()
    }
    drawPath(wash, c.text.copy(alpha = 0.34f * (1f - t)))
    val w = (30f * (1f - t) + 2f).dp.toPx()
    drawLine(c.teal.copy(alpha = 1f - t), a, b, strokeWidth = w * 1.8f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 1f - t * 0.6f), a, b, strokeWidth = w * 0.6f, cap = StrokeCap.Round)
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

/** Floating words: ink drop first, then the colour, so they read on any background. */
private fun DrawScope.drawPopup(p: Popup, measurer: TextMeasurer, tints: List<Color>, c: GgColors) {
    val fade = (1f - p.age / SlashState.POPUP_SECONDS).coerceIn(0f, 1f)
    val grow = (p.age / 0.12f).coerceIn(0f, 1f)
    val small = p.text.startsWith("+") || p.text.startsWith("-")
    val style = TextStyle(fontSize = ((if (small) 17f else 23f) * (0.7f + 0.3f * grow)).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    val layout = measurer.measure(p.text, style)
    val x = (p.x - layout.size.width / 2f).coerceIn(8.dp.toPx(), (size.width - layout.size.width - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
    val y = p.y.coerceAtLeast(150.dp.toPx())
    val d = 2.5.dp.toPx()
    val ink = if (p.kind == HEAP) c.cloud else tints[p.kind]
    drawText(layout, color = c.edge.copy(alpha = fade), topLeft = Offset(x + d, y + d))
    drawText(layout, color = ink.copy(alpha = fade), topLeft = Offset(x, y))
}
