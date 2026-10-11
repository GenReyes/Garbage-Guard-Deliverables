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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * A small hidden game: hold the logo for three seconds and it becomes a
 * blade. The whole screen is water. Bottles, bags and containers are
 * tossed up through it; slash each one before it falls back.
 *
 *  - Three lives. A missed item or a cut bomb costs one.
 *  - The combo counts every cut in a row and grows what each cut is worth.
 *  - The water answers everything: it parts along the line of a cut, and a
 *    bomb bursts under it and sends a shock wave across the screen.
 *  - At 50 points, then 100, 200, 300 and so on, a boss arrives, one of
 *    three picked at random, and each stirs the water its own way:
 *      Warrior  turning shields; only a cut through a gap counts. It pounds
 *               the water into rings.
 *      Rogue    fast, and it darts away when the blade comes close. It
 *               leaves the water torn into wakes behind it.
 *      Mage     warded while its wisps live; cut those first. It winds the
 *               water into a whirlpool and blinks from place to place.
 *    Wear it down, then one last cut splits it along the line of that cut,
 *    flips the whole app between light and dark, and gives one life back.
 *
 * Nothing is saved. It is only here for fun.
 */

private enum class Phase { READY, PLAYING, OVER }

/** Things the frame loop tells the screen about, so it can play a sound or flip the theme. */
private enum class Ev { CUT, COMBO, LIFE_LOST, BOMB, BOSS_IN, BOSS_HIT, BOSS_BLOCK, BOSS_DODGE, BOSS_CAST, WARD_DOWN, BOSS_READY, BOSS_DOWN, OVER }

private enum class BossKind(val title: String, val hint: String) {
    WARRIOR("WARRIOR", "CUT THROUGH THE GAPS"),
    ROGUE("ROGUE", "CATCH IT WHEN IT STOPS"),
    MAGE("MAGE", "CUT THE WISPS FIRST"),
}

private const val BOMB = 3
private const val WATER = 4
private const val HEAP = 5
private const val WISP = 6

private class Flying(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
)

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val kind: Int, val size: Float,
    val leftHalf: Boolean, var life: Float, val full: Float, val boss: BossKind? = null,
)

/** A fleck of spray, or with [rise] a bubble that floats up. */
private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, var life: Float, val rise: Boolean = false)

private class Popup(val text: String, val x: Float, var y: Float, var age: Float, val kind: Int)

/** A line where the water has been parted, by the blade or by something moving fast. */
private class Wake(val a: Offset, val b: Offset, var age: Float, val power: Float)

/** A shock wave spreading through the water from one point. */
private class Shock(val x: Float, val y: Float, var age: Float, val power: Float, val kind: Int)

private class TrailPoint(val at: Offset, var age: Float)

/** One of the mage's wisps, at [angle] degrees around it. */
private class Wisp(val angle: Float)

/** The boss that arrives at each milestone. It has to be worn down, then finished with one cut. */
private class Boss(val kind: BossKind, val level: Int, val maxHp: Int, val seconds: Float, var x: Float, var y: Float) {
    var hp = maxHp
    var timeLeft = seconds
    var flash = 0f
    var blockFlash = 0f
    var cooldown = 0f
    var age = 0f

    // Warrior: plates that turn around it, and the beat of its pounding.
    val plates = if (level >= 3) 3 else 2
    val cover = (0.5f + 0.04f * level).coerceAtMost(0.68f)
    var shieldAngle = Random.nextFloat() * 360f
    var spin = 55f + 12f * level
    var turnIn = 2.5f
    var pulseIn = 1.2f

    // Rogue: where it is heading, how long it rests, and when it can next dart away.
    var tx = x
    var ty = y
    var pause = 0.5f
    var dashing = 0f
    var dodgeIn = 0.8f
    var markX = x
    var markY = y
    var sinceMark = 0f

    // Mage: how long the ward stays down, and the turning of its wisps.
    var wardDown = 0.7f
    var casts = 0
    var orbit = 0f

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
    val wakes = ArrayList<Wake>()
    val shocks = ArrayList<Shock>()
    val wisps = ArrayList<Wisp>()
    val ghosts = ArrayList<TrailPoint>()
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
    var clock = 0f

    /** Where [displace] last moved a point of the water to. */
    var movedX = 0f
    var movedY = 0f
    private var nextBossAt = FIRST_BOSS
    private var lastKind: BossKind? = null
    private var untilSpawn = 0.6f
    private var wakeFrom: Offset? = null

    fun dp(v: Float) = v * density

    fun radiusOf(b: Boss) = dp(76f) * when (b.kind) {
        BossKind.WARRIOR -> 1.05f
        BossKind.ROGUE -> 0.72f
        BossKind.MAGE -> 0.9f
    }

    fun wispAt(b: Boss, w: Wisp): Offset {
        val a = Math.toRadians((w.angle + b.orbit).toDouble())
        val r = radiusOf(b) * 2f
        return Offset(b.x + cos(a).toFloat() * r, b.y + sin(a).toFloat() * r)
    }

    fun reset() {
        items.clear(); pieces.clear(); drops.clear(); popups.clear(); wakes.clear(); shocks.clear()
        wisps.clear(); ghosts.clear(); trail.clear(); events.clear()
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
        lastKind = null
        untilSpawn = 0.6f
        finger = null
        wakeFrom = null
    }

    private fun toss(bomb: Boolean) {
        val size = dp(62f + Random.nextFloat() * 14f)
        val gravity = dp(GRAVITY)
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

    /** Starts a shock wave in the water. A negative start age delays it. */
    private fun shock(x: Float, y: Float, power: Float, kind: Int = WATER, delay: Float = 0f) {
        if (shocks.size >= 10) shocks.removeAt(0)
        shocks += Shock(x, y, -delay, power, kind)
    }

    private fun bubbles(x: Float, y: Float, count: Int, spread: Float) {
        for (n in 0 until count) {
            val a = Random.nextFloat() * 6.283f
            val r = Random.nextFloat() * dp(spread)
            drops += Drop(
                x + cos(a) * r, y + sin(a) * r, (Random.nextFloat() - 0.5f) * dp(60f), -dp(40f + Random.nextFloat() * 120f),
                WATER, 0.6f + Random.nextFloat() * 0.7f, rise = true,
            )
        }
    }

    private fun burst(x: Float, y: Float, kind: Int, speed: Float) {
        val a = Random.nextFloat() * 6.283f
        val v = (0.35f + Random.nextFloat() * 0.65f) * dp(speed)
        drops += Drop(x, y, cos(a) * v, sin(a) * v - dp(160f), kind, 0.45f + Random.nextFloat() * 0.35f)
    }

    private fun addWake(a: Offset, b: Offset, power: Float) {
        if (wakes.size >= 18) wakes.removeAt(0)
        wakes += Wake(a, b, 0f, power)
    }

    /**
     * Where a point of the water sits this frame: wound round a mage,
     * pushed aside by every fresh cut, and lifted by every shock wave.
     * The answer is left in [movedX] and [movedY].
     */
    fun displace(px: Float, py: Float) {
        var x = px
        var y = py
        val b = boss
        if (b != null && b.kind == BossKind.MAGE) {
            val reach = radiusOf(b) * 3.6f
            val ox = x - b.x
            val oy = y - b.y
            val d = hypot(ox, oy)
            if (d < reach && d > 1f) {
                val k = 1f - d / reach
                val turn = k * k * (1.1f + 0.3f * sin(clock * 2.2f)) * min(1f, b.age / 0.6f)
                val cs = cos(turn)
                val sn = sin(turn)
                x = b.x + ox * cs - oy * sn
                y = b.y + ox * sn + oy * cs
            }
        }
        for (i in wakes.indices) {
            val w = wakes[i]
            val sx = w.b.x - w.a.x
            val sy = w.b.y - w.a.y
            val len2 = sx * sx + sy * sy
            val t = if (len2 == 0f) 0f else (((px - w.a.x) * sx + (py - w.a.y) * sy) / len2).coerceIn(0f, 1f)
            val qx = w.a.x + t * sx
            val qy = w.a.y + t * sy
            val d = hypot(px - qx, py - qy)
            val reach = dp(38f) * w.power
            if (d < reach) {
                val push = (1f - d / reach) * dp(16f) * w.power * (1f - w.age / WAKE_SECONDS)
                if (d > 0.5f) {
                    x += (px - qx) / d * push
                    y += (py - qy) / d * push
                } else y += push
            }
        }
        for (i in shocks.indices) {
            val s = shocks[i]
            if (s.age < 0f) continue
            val ox = px - s.x
            val oy = py - s.y
            val d = hypot(ox, oy)
            val k = (d - dp(SHOCK_SPEED) * s.age) / dp(52f)
            if (k > -1f && k < 1f && d > 0.5f) {
                val lift = (1f - abs(k)) * dp(17f) * s.power * (1f - s.age / SHOCK_SECONDS)
                x += ox / d * lift
                y += oy / d * lift
            }
        }
        movedX = x
        movedY = y
    }

    private fun retarget(b: Boss, awayFrom: Offset?) {
        var bestX = b.x
        var bestY = b.y
        var best = -1f
        for (n in 0 until if (awayFrom == null) 1 else 6) {
            val x = width * (0.18f + Random.nextFloat() * 0.64f)
            val y = height * (0.16f + Random.nextFloat() * 0.34f)
            val score = if (awayFrom == null) 1f else hypot(x - awayFrom.x, y - awayFrom.y)
            if (score > best) {
                best = score
                bestX = x
                bestY = y
            }
        }
        b.tx = bestX
        b.ty = bestY
    }

    private fun summon(b: Boss) {
        if (b.casts > 0) {
            // It blinks to a new place first, and the water marks both ends of the jump.
            shock(b.x, b.y, 0.9f, WISP)
            b.x = width * (0.25f + Random.nextFloat() * 0.5f)
            b.y = height * (0.2f + Random.nextFloat() * 0.22f)
            shock(b.x, b.y, 1.2f, WISP)
        }
        b.casts++
        val n = (2 + b.level).coerceAtMost(5)
        for (i in 0 until n) wisps += Wisp(i * 360f / n)
        for (w in wisps) {
            val at = wispAt(b, w)
            shock(at.x, at.y, 0.45f, WISP, delay = 0.1f)
        }
        popups += Popup("SUMMON", b.x, b.y - radiusOf(b) - dp(54f), 0f, WISP)
        events += Ev.BOSS_CAST
    }

    private fun stepBoss(b: Boss, dt: Float) {
        b.age += dt
        if (b.flash > 0f) b.flash = (b.flash - dt * 6f).coerceAtLeast(0f)
        if (b.blockFlash > 0f) b.blockFlash = (b.blockFlash - dt * 4f).coerceAtLeast(0f)
        if (b.cooldown > 0f) b.cooldown -= dt
        if (b.ready) return
        when (b.kind) {
            BossKind.WARRIOR -> {
                b.turnIn -= dt
                if (b.turnIn <= 0f) {
                    b.spin = -b.spin
                    b.turnIn = 2.2f + Random.nextFloat() * 1.8f
                }
                b.shieldAngle += b.spin * dt
                // It pounds the water on a slow beat.
                b.pulseIn -= dt
                if (b.pulseIn <= 0f) {
                    shock(b.x, b.y, 1.5f, 0)
                    b.pulseIn = 2.4f
                }
            }
            BossKind.ROGUE -> {
                b.dodgeIn -= dt
                if (b.dashing > 0f) b.dashing -= dt
                val f = finger
                if (f != null && b.dodgeIn <= 0f && hypot(f.x - b.x, f.y - b.y) < radiusOf(b) * 2.1f) {
                    retarget(b, f)
                    b.dashing = 0.28f
                    b.pause = 0f
                    b.dodgeIn = (1.5f - 0.1f * b.level).coerceAtLeast(0.75f)
                    popups += Popup("DODGE", b.x, b.y - radiusOf(b) - dp(40f), 0f, 2)
                    events += Ev.BOSS_DODGE
                }
                if (b.pause > 0f) b.pause -= dt
                else {
                    val dx = b.tx - b.x
                    val dy = b.ty - b.y
                    val d = hypot(dx, dy)
                    val v = dp(430f + 50f * b.level) * (if (b.dashing > 0f) 2.6f else 1f) * dt
                    if (d <= v) {
                        b.x = b.tx
                        b.y = b.ty
                        b.pause = 0.6f
                        retarget(b, null)
                    } else {
                        b.x += dx / d * v
                        b.y += dy / d * v
                    }
                    // Its speed tears the water open behind it.
                    b.sinceMark += v
                    if (b.sinceMark > dp(46f)) {
                        addWake(Offset(b.markX, b.markY), Offset(b.x, b.y), 1.4f)
                        ghosts += TrailPoint(Offset(b.x, b.y), 0f)
                        b.markX = b.x
                        b.markY = b.y
                        b.sinceMark = 0f
                    }
                }
            }
            BossKind.MAGE -> {
                b.orbit += dt * (70f + 12f * b.level)
                if (wisps.isEmpty()) {
                    b.wardDown -= dt
                    if (b.wardDown <= 0f) summon(b)
                }
            }
        }
        b.timeLeft -= dt
        if (b.timeLeft <= 0f) {
            // Too slow: it sinks back and takes a life with it.
            boss = null
            wisps.clear()
            shock(b.x, b.y, 2f)
            bubbles(b.x, b.y, 16, 70f)
            loseLife("TOO SLOW", b.x, b.y)
        }
    }

    /** Advances the world by [dt] seconds. What happened is left in [events]. */
    fun step(dt: Float) {
        if (width == 0f) return
        val gravity = dp(GRAVITY)
        clock += dt

        // A boss arrives when the score crosses the next milestone: 50, 100, 200, 300 ...
        if (boss == null && styleCut == null && score >= nextBossAt) {
            val level = bossesDown + 1
            val kind = BossKind.entries.filter { it != lastKind }.random()
            lastKind = kind
            val cx = width / 2f
            val cy = height * 0.31f
            val b = when (kind) {
                BossKind.WARRIOR -> Boss(kind, level, 7 + level * 3, 18f, cx, cy)
                BossKind.ROGUE -> Boss(kind, level, 14 + level * 5, 17f, cx, cy)
                BossKind.MAGE -> Boss(kind, level, 12 + level * 4, 20f, cx, cy)
            }
            boss = b
            nextBossAt = if (nextBossAt < 100) 100 else nextBossAt + 100
            shock(cx, cy, 1.6f, HEAP)
            popups += Popup(kind.title, cx, height * 0.52f, 0f, HEAP)
            popups += Popup(kind.hint, cx, height * 0.52f + dp(38f), 0f, WATER)
            events += Ev.BOSS_IN
        }
        boss?.let { stepBoss(it, dt) }

        untilSpawn -= dt
        if (untilSpawn <= 0f) {
            if (boss != null) {
                // A few ordinary ones, enough to keep an eye on while cutting the boss down.
                toss(bomb = Random.nextFloat() < 0.12f)
                untilSpawn = 1.6f + Random.nextFloat() * 0.6f
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
                    val x = it.x.coerceIn(0f, width)
                    shock(x, height, 1f, BOMB)
                    bubbles(x, height - dp(20f), 6, 30f)
                    loseLife("MISS", x.coerceIn(dp(60f), width - dp(60f)), height - dp(150f))
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
            d.vy += (if (d.rise) -gravity * 0.3f else gravity) * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.life -= dt
            if (d.life <= 0f) dry.remove()
        }
        val faded = popups.iterator()
        while (faded.hasNext()) {
            val p = faded.next()
            p.age += dt
            p.y -= dp(46f) * dt
            if (p.age > POPUP_SECONDS) faded.remove()
        }
        val closed = wakes.iterator()
        while (closed.hasNext()) {
            val w = closed.next()
            w.age += dt
            if (w.age > WAKE_SECONDS) closed.remove()
        }
        val calm = shocks.iterator()
        while (calm.hasNext()) {
            val s = calm.next()
            s.age += dt
            if (s.age > SHOCK_SECONDS) calm.remove()
        }
        val shade = ghosts.iterator()
        while (shade.hasNext()) {
            val g = shade.next()
            g.age += dt
            if (g.age > GHOST_SECONDS) shade.remove()
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
        if (surge > 0f) surge = (surge - dt * 1.1f).coerceAtLeast(0f)
    }

    private fun shielded(b: Boss, angle: Float): Boolean {
        val each = 360f / b.plates
        val half = each * b.cover / 2f
        for (i in 0 until b.plates) {
            val centre = b.shieldAngle + i * each
            val off = ((angle - centre) % 360f + 540f) % 360f - 180f
            if (abs(off) < half) return true
        }
        return false
    }

    private fun wound(b: Boss) {
        b.hp--
        b.flash = 1f
        val r = radiusOf(b)
        for (n in 0 until 3) burst(b.x + (Random.nextFloat() - 0.5f) * r, b.y + (Random.nextFloat() - 0.5f) * r, Random.nextInt(3), 340f)
        if (b.kind == BossKind.WARRIOR) shock(b.x, b.y, 0.6f, 0)
        if (b.ready) {
            wisps.clear()
            popups += Popup("FINISH IT", b.x, b.y - r - dp(50f), 0f, HEAP)
            events += Ev.BOSS_READY
        } else events += Ev.BOSS_HIT
    }

    /** Moves the blade and cuts whatever the stroke crosses. */
    fun slash(to: Offset) {
        val from = finger
        finger = to
        trail += TrailPoint(to, 0f)
        if (from == null) {
            wakeFrom = to
            return
        }
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = hypot(dx, dy)
        if (len < dp(3f)) return
        heading = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val nx = -dy / len
        val ny = dx / len

        // The blade parts the water wherever it goes.
        val start = wakeFrom ?: from
        if (hypot(to.x - start.x, to.y - start.y) >= dp(30f)) {
            addWake(start, to, 1f)
            wakeFrom = to
        }

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
                // It bursts under the water: a dome of white, then two waves that cross the whole screen.
                shock(it.x, it.y, 2.8f, BOMB)
                shock(it.x, it.y, 2f, WATER, delay = 0.14f)
                bubbles(it.x, it.y, 26, 90f)
                events += Ev.BOMB
                loseLife("-1", it.x, it.y + dp(40f))
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
            val push = dp(190f)
            pieces += Piece(it.x, it.y, it.vx + nx * push, it.vy + ny * push - dp(120f), it.angle, it.spin - 260f, it.kind, it.size, true, 1.1f, 1.1f)
            pieces += Piece(it.x, it.y, it.vx - nx * push, it.vy - ny * push - dp(120f), it.angle, it.spin + 260f, it.kind, it.size, false, 1.1f, 1.1f)
            for (n in 0 until 7) burst(it.x, it.y, it.kind, 300f)
            shock(it.x, it.y, 0.4f, it.kind)
        }

        val b = boss ?: return
        val r = radiusOf(b)

        // The mage's wisps: cut them all and its ward falls for a few seconds.
        if (b.kind == BossKind.MAGE && wisps.isNotEmpty()) {
            val each = wisps.iterator()
            while (each.hasNext()) {
                val at = wispAt(b, each.next())
                if (distanceToSegment(at.x, at.y, from, to) >= dp(26f)) continue
                each.remove()
                for (n in 0 until 8) burst(at.x, at.y, WISP, 320f)
                shock(at.x, at.y, 0.6f, WISP)
                events += Ev.CUT
            }
            if (wisps.isEmpty()) {
                b.wardDown = 4f
                popups += Popup("WARD DOWN", b.x, b.y - r - dp(50f), 0f, WATER)
                events += Ev.WARD_DOWN
            }
        }

        if (distanceToSegment(b.x, b.y, from, to) >= r * 0.95f || len < dp(5f)) return
        if (b.ready) {
            finish(b, dx / len, dy / len, nx, ny)
            return
        }
        when (b.kind) {
            BossKind.WARRIOR -> {
                // Only the way in counts, so a cut has to come through a gap between the plates.
                if (hypot(from.x - b.x, from.y - b.y) < r * 0.95f) return
                val angle = Math.toDegrees(atan2(from.y - b.y, from.x - b.x).toDouble()).toFloat()
                if (shielded(b, angle)) {
                    if (b.cooldown <= 0f) {
                        b.cooldown = 0.3f
                        b.blockFlash = 1f
                        for (n in 0 until 6) burst(from.x, from.y, 0, 380f)
                        shock(b.x, b.y, 0.8f, 0)
                        popups += Popup("BLOCKED", from.x, from.y - dp(30f), 0f, 0)
                        events += Ev.BOSS_BLOCK
                    }
                    return
                }
                wound(b)
            }
            BossKind.ROGUE -> {
                if (b.cooldown > 0f) return
                b.cooldown = 0.045f
                wound(b)
            }
            BossKind.MAGE -> {
                if (wisps.isNotEmpty()) {
                    if (b.cooldown <= 0f) {
                        b.cooldown = 0.4f
                        b.blockFlash = 1f
                        popups += Popup("WARDED", b.x, b.y - r - dp(30f), 0f, WISP)
                        events += Ev.BOSS_BLOCK
                    }
                    return
                }
                if (b.cooldown > 0f) return
                b.cooldown = 0.045f
                wound(b)
            }
        }
    }

    /**
     * The finishing cut. The boss splits along the line the hand chose:
     * each half is turned so its flat side lies on that line, then pushed off it.
     */
    private fun finish(b: Boss, ux: Float, uy: Float, nx: Float, ny: Float) {
        val turn = Math.toDegrees(atan2(-ux, uy).toDouble()).toFloat()
        val size = radiusOf(b) * 2f
        val push = dp(260f)
        pieces += Piece(b.x, b.y, -nx * push, -ny * push - dp(60f), turn, -40f, HEAP, size, true, 1.6f, 1.6f, b.kind)
        pieces += Piece(b.x, b.y, nx * push, ny * push - dp(60f), turn, 40f, HEAP, size, false, 1.6f, 1.6f, b.kind)
        for (n in 0 until 34) burst(b.x, b.y, Random.nextInt(3), 620f)
        shock(b.x, b.y, 2.4f, HEAP)
        bubbles(b.x, b.y, 18, 80f)
        val far = hypot(width, height)
        addWake(Offset(b.x - ux * far, b.y - uy * far), Offset(b.x + ux * far, b.y + uy * far), 1.8f)
        styleCut = StyleCut(Offset(b.x, b.y), Offset(ux, uy), 0f)
        val bonus = 10 * b.level
        score += bonus
        bossesDown++
        boss = null
        wisps.clear()
        ghosts.clear()
        shake = 0.7f
        popups += Popup("STYLE SWITCH  +$bonus", b.x, b.y, 0f, WATER)
        // Beating a boss gives one life back, up to the full three.
        if (lives < LIVES) {
            lives++
            popups += Popup("+1 LIFE", b.x, b.y + dp(44f), 0f, 2)
        }
        events += Ev.BOSS_DOWN
    }

    fun lift() {
        finger = null
        wakeFrom = null
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
        const val WAKE_SECONDS = 0.95f
        const val SHOCK_SECONDS = 1.3f
        const val SHOCK_SPEED = 620f
        const val GHOST_SECONDS = 0.32f
        const val STYLE_SECONDS = 0.7f
    }
}

private fun bossTint(kind: BossKind, c: GgColors): Color = when (kind) {
    BossKind.WARRIOR -> c.bottle
    BossKind.ROGUE -> c.container
    BossKind.MAGE -> c.bag
}

private fun bossIcon(kind: BossKind): ImageVector = when (kind) {
    BossKind.WARRIOR -> GgIcons.Shield
    BossKind.ROGUE -> GgIcons.Bolt
    BossKind.MAGE -> GgIcons.Target
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

    // Items first, then the bomb, then one emblem for each boss in the order of BossKind.
    val painters = listOf(
        rememberVectorPainter(GgIcons.Bottle),
        rememberVectorPainter(GgIcons.Bag),
        rememberVectorPainter(GgIcons.Container),
        rememberVectorPainter(GgIcons.Warn),
        rememberVectorPainter(GgIcons.Shield),
        rememberVectorPainter(GgIcons.Bolt),
        rememberVectorPainter(GgIcons.Target),
    )
    // Class colours, the bomb's red, the water's teal, the boss in the page's ink, and the wisps' purple. All follow the theme.
    val tints = listOf(c.bottle, c.bag, c.container, c.bad, c.teal, c.text, c.bag)
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
            Ev.BOSS_BLOCK -> { Sounds.play(R.raw.gg_block, 0.8f); Feel.thump(view) }
            Ev.BOSS_DODGE -> Sounds.play(R.raw.gg_dodge, 0.7f)
            Ev.BOSS_CAST -> { Sounds.play(R.raw.gg_cast, 0.8f); Feel.tick(view) }
            Ev.WARD_DOWN, Ev.BOSS_READY -> { Sounds.play(R.raw.gg_combo); Feel.thump(view) }
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
                drawSea(state, tints, c)
                state.drops.forEach {
                    val a = (it.life * 2f).coerceIn(0f, 1f)
                    if (it.rise) drawCircle(c.tealLight.copy(alpha = 0.8f * a), radius = 4.dp.toPx(), center = Offset(it.x, it.y), style = Stroke(width = 1.5.dp.toPx()))
                    else drawCircle(tints[it.kind].copy(alpha = a), radius = (if (it.kind == WATER) 5 else 4).dp.toPx(), center = Offset(it.x, it.y))
                }
                state.boss?.let { drawBoss(state, it, painters, tints, c) }
                state.pieces.forEach {
                    val fade = (it.life / it.full).coerceIn(0f, 1f)
                    if (it.boss != null) drawHeap(Offset(it.x, it.y), it.size / 2f, it.angle, it.boss, painters, tints, c, fade, it.leftHalf, 0f)
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
                // Which boss it is, its health, and how long is left to bring it down.
                val ink = bossTint(b.kind, c)
                Row(
                    Modifier.fillMaxWidth().neuRaised(c, 12.dp, hard = 2.dp, edge = ink).padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Ico(bossIcon(b.kind), size = 15.dp, tint = ink)
                    T(if (b.ready) "FINISH IT" else "${b.kind.title} ${b.level}", size = 0.72f, weight = FontWeight.Black, color = ink, spacing = 0.05f, maxLines = 1)
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(logo, contentDescription = null, modifier = Modifier.size(70.dp))
                if (phase == Phase.READY) {
                    T("GARBAGE SLASH", size = 1.5f, weight = FontWeight.Black, color = c.text, spacing = 0.04f)
                    Rule(GgIcons.Check, c.teal, "Slash every bottle, bag and container.")
                    Rule(GgIcons.Layers, c.cloud, "Three lives. A miss or a bomb costs one.")
                    Rule(GgIcons.Up, c.ok, "Cuts in a row build your combo.")
                    Rule(GgIcons.Shield, c.bottle, "Warrior: cut through the shield gaps.")
                    Rule(GgIcons.Bolt, c.container, "Rogue: catch it when it stops.")
                    Rule(GgIcons.Target, c.bag, "Mage: cut its wisps first.")
                    Rule(GgIcons.SunMoon, c.warn, "Beat a boss: one life back, theme flips.")
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
private fun Rule(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ico(icon, size = 15.dp, tint = tint)
        T(text, Modifier.weight(1f), size = 0.82f, weight = FontWeight.Bold, color = Gg.colors.text, fit = true)
    }
}

/**
 * The water, over the whole screen: a wash of colour and rows of waves.
 * Every point of every wave is passed through [SlashState.displace], so the
 * rows part along a cut, bulge as a shock wave passes, and wind round a
 * mage. The colour leans toward whichever boss is in it, and a rogue makes
 * the waves race.
 */
private fun DrawScope.drawSea(s: SlashState, tints: List<Color>, c: GgColors) {
    val b = s.boss
    val mood = if (b == null) c.teal else lerp(c.teal, bossTint(b.kind, c), 0.6f * min(1f, b.age / 0.5f))
    drawRect(Brush.verticalGradient(listOf(mood.copy(alpha = 0.10f + 0.12f * s.surge), mood.copy(alpha = 0.34f + 0.2f * s.surge))))

    val gap = 40.dp.toPx()
    val stepX = 26.dp.toPx()
    val swell = (5f + 9f * s.surge).dp.toPx()
    val rush = if (b != null && b.kind == BossKind.ROGUE && !b.ready) 5f else 1.3f
    val line = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    val ink = mood.copy(alpha = 0.32f + 0.3f * s.surge)
    val path = Path()
    var row = 0
    var y0 = gap * 0.5f
    while (y0 < size.height + gap) {
        path.reset()
        val way = if (row % 2 == 0) 1f else -1f
        var x = -stepX
        var lastX = 0f
        var lastY = 0f
        var first = true
        while (x <= size.width + stepX * 2) {
            val y = y0 + sin(x / stepX * 1.25f + s.clock * rush * way + row * 1.3f) * swell
            s.displace(x, y)
            if (first) {
                path.moveTo(s.movedX, s.movedY)
                first = false
            } else path.quadraticTo(lastX, lastY, (lastX + s.movedX) / 2f, (lastY + s.movedY) / 2f)
            lastX = s.movedX
            lastY = s.movedY
            x += stepX
        }
        drawPath(path, ink, style = line)
        y0 += gap
        row++
    }

    // The seam where the water was parted: a dark gap with foam along it, closing as it fades.
    s.wakes.forEach { w ->
        val fresh = 1f - (w.age / SlashState.WAKE_SECONDS).coerceIn(0f, 1f)
        drawLine(c.edge.copy(alpha = 0.2f * fresh), w.a, w.b, strokeWidth = (12f * w.power * fresh + 1f).dp.toPx(), cap = StrokeCap.Round)
        drawLine(c.tealLight.copy(alpha = 0.75f * fresh), w.a, w.b, strokeWidth = (3f * fresh + 0.5f).dp.toPx(), cap = StrokeCap.Round)
    }
    // Shock waves: two rings running outward, and for a big one a white dome at the start.
    s.shocks.forEach { k ->
        if (k.age < 0f) return@forEach
        val fade = 1f - (k.age / SlashState.SHOCK_SECONDS).coerceIn(0f, 1f)
        val r = s.dp(SlashState.SHOCK_SPEED) * k.age
        val at = Offset(k.x, k.y)
        val tint = tints[k.kind]
        if (k.power >= 2f && k.age < 0.3f) {
            val early = 1f - k.age / 0.3f
            drawCircle(Color.White.copy(alpha = 0.6f * early), radius = r + 20.dp.toPx(), center = at)
            drawCircle(tint.copy(alpha = 0.45f * early), radius = (r + 20.dp.toPx()) * 0.6f, center = at)
        }
        drawCircle(tint.copy(alpha = 0.7f * fade), radius = r, center = at, style = Stroke(width = (5f * k.power * fade + 1f).dp.toPx()))
        drawCircle(c.tealLight.copy(alpha = 0.4f * fade), radius = r * 0.66f, center = at, style = Stroke(width = (2.5f * k.power * fade + 0.5f).dp.toPx()))
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
 * A boss's body: a big heap of all three kinds round its own emblem, heavy
 * outline, hard drop, washed in its colour. Also used for its two halves
 * once it has been cut.
 */
private fun DrawScope.drawHeap(
    at: Offset, r: Float, angle: Float, kind: BossKind, painters: List<VectorPainter>, tints: List<Color>,
    c: GgColors, alpha: Float, leftHalf: Boolean?, flash: Float,
) {
    val accent = bossTint(kind, c)
    withTransform({
        translate(at.x, at.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        val clip = halfClip(r, leftHalf)
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val drop = 7.dp.toPx()
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset(drop, drop))
            drawCircle(c.raised.copy(alpha = alpha), radius = r, center = Offset.Zero)
            drawCircle(accent.copy(alpha = 0.3f * alpha), radius = r, center = Offset.Zero)
            drawCircle(c.sunk.copy(alpha = alpha), radius = r * 0.7f, center = Offset.Zero)
            drawCircle(c.edge.copy(alpha = alpha), radius = r * 0.7f, center = Offset.Zero, style = Stroke(width = 2.dp.toPx()))
            drawCircle(c.edge.copy(alpha = alpha), radius = r, center = Offset.Zero, style = Stroke(width = 4.dp.toPx()))
            // The three kinds piled round the emblem.
            val s = r * 0.36f
            for (i in 0..2) {
                val a = Math.toRadians(-90.0 + i * 120.0)
                val cx = cos(a).toFloat() * r * 0.43f
                val cy = sin(a).toFloat() * r * 0.43f
                withTransform({ translate(cx - s / 2f, cy - s / 2f) }) {
                    with(painters[i]) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(tints[i])) }
                }
            }
            val e = r * 0.36f
            withTransform({ translate(-e / 2f, -e / 2f) }) {
                with(painters[4 + kind.ordinal]) { draw(Size(e, e), alpha = alpha, colorFilter = ColorFilter.tint(accent)) }
            }
            if (flash > 0f) drawCircle(Color.White.copy(alpha = 0.6f * flash), radius = r, center = Offset.Zero)
        }
    }
}

/**
 * The boss in play, with what makes each kind itself: the warrior's turning
 * plates, the rogue's after-images, the mage's ward and wisps. All three
 * carry a ring of remaining time, and a guide line once they can be finished.
 */
private fun DrawScope.drawBoss(s: SlashState, b: Boss, painters: List<VectorPainter>, tints: List<Color>, c: GgColors) {
    val accent = bossTint(b.kind, c)
    val r = s.radiusOf(b)
    val arrive = (b.age / 0.35f).coerceIn(0f, 1f)
    val bob = if (b.kind == BossKind.ROGUE) 0f else sin(b.age * 2.2f) * 5.dp.toPx()
    val jolt = b.flash * 5.dp.toPx()
    val at = Offset(b.x + (Random.nextFloat() - 0.5f) * jolt, b.y + bob + (Random.nextFloat() - 0.5f) * jolt)
    val pulse = if (b.ready) 1f + 0.05f * sin(b.age * 14f) else 1f
    val radius = r * arrive * pulse

    if (b.kind == BossKind.ROGUE) s.ghosts.forEach { g ->
        val fade = 1f - (g.age / SlashState.GHOST_SECONDS).coerceIn(0f, 1f)
        drawCircle(accent.copy(alpha = 0.3f * fade), radius = radius * (0.6f + 0.4f * fade), center = g.at)
    }
    if (b.kind == BossKind.MAGE && !b.ready) {
        if (s.wisps.isNotEmpty()) {
            // The ward: a shell round the mage, fed by a thread from every living wisp.
            drawCircle(accent.copy(alpha = 0.16f + 0.2f * b.blockFlash), radius = radius + 14.dp.toPx(), center = at)
            drawCircle(
                accent, radius = radius + 14.dp.toPx(), center = at,
                style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()), -b.age * 50f)),
            )
        }
        s.wisps.forEach { w ->
            val p = s.wispAt(b, w)
            drawLine(accent.copy(alpha = 0.4f), at, p, strokeWidth = 1.5.dp.toPx())
            val wr = 17.dp.toPx() * (1f + 0.1f * sin(b.age * 9f + w.angle))
            drawCircle(c.edge, radius = wr, center = p + Offset(3.dp.toPx(), 3.dp.toPx()))
            drawCircle(accent, radius = wr, center = p)
            drawCircle(Color.White.copy(alpha = 0.75f), radius = wr * 0.42f, center = p)
            drawCircle(c.edge, radius = wr, center = p, style = Stroke(width = 2.dp.toPx()))
        }
    }

    drawHeap(at, radius, 0f, b.kind, painters, tints, c, 1f, null, b.flash)

    if (b.kind == BossKind.WARRIOR && !b.ready) {
        // The plates: thick arcs that turn round it. A cut has to come in through a gap.
        val ring = radius + 15.dp.toPx()
        val each = 360f / b.plates
        val sweep = each * b.cover
        val steel = lerp(accent, Color.White, b.blockFlash)
        for (i in 0 until b.plates) {
            val start = b.shieldAngle + i * each - sweep / 2f
            drawArc(
                c.edge, start, sweep, useCenter = false, topLeft = Offset(at.x - ring, at.y - ring), size = Size(ring * 2, ring * 2),
                style = Stroke(width = 17.dp.toPx(), cap = StrokeCap.Butt),
            )
            drawArc(
                steel, start + 1.5f, sweep - 3f, useCenter = false, topLeft = Offset(at.x - ring, at.y - ring), size = Size(ring * 2, ring * 2),
                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Butt),
            )
        }
    }

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
        val ring = radius + (if (b.kind == BossKind.WARRIOR) 30 else 9).dp.toPx()
        drawArc(
            if (left > 0.35f) c.warn else c.bad, startAngle = -90f, sweepAngle = 360f * left, useCenter = false,
            topLeft = Offset(at.x - ring, at.y - ring), size = Size(ring * 2, ring * 2),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
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
    val style = TextStyle(fontSize = ((if (small) 17f else 21f) * (0.7f + 0.3f * grow)).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    val layout = measurer.measure(p.text, style)
    val x = (p.x - layout.size.width / 2f).coerceIn(8.dp.toPx(), (size.width - layout.size.width - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
    val y = p.y.coerceAtLeast(150.dp.toPx())
    val d = 2.5.dp.toPx()
    val ink = if (p.kind == HEAP) c.cloud else tints[p.kind]
    drawText(layout, color = c.edge.copy(alpha = fade), topLeft = Offset(x + d, y + d))
    drawText(layout, color = ink.copy(alpha = fade), topLeft = Offset(x, y))
}
