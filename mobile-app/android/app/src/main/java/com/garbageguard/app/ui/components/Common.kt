package com.garbageguard.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garbageguard.app.R
import com.garbageguard.app.data.Battery
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.ui.Tab
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.ui.theme.Gg
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput

/** The mockup sizes everything in em on a 14 px base. */
fun em(x: Float): TextUnit = (14f * x).sp

/** Text in the mockup's type scale. Color follows the surface it sits on. */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 1f,
    weight: FontWeight = FontWeight.ExtraBold,
    color: Color = LocalContentColor.current,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    tnum: Boolean = false,
    spacing: Float = 0f,
    lineHeight: Float = 1.25f,
    /** Keep to one line by stepping the size down until it fits, so no word is ever cut off. */
    fit: Boolean = false,
) {
    // Keyed on length, not the text itself, so a ticking countdown does not re-shrink every second.
    var scale by remember(text.length, size) { mutableFloatStateOf(1f) }
    val shown = if (fit) size * scale else size
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = align,
        maxLines = if (fit) 1 else maxLines,
        softWrap = !fit,
        overflow = if (fit && scale > FIT_FLOOR) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { if (fit && it.didOverflowWidth && scale > FIT_FLOOR) scale = (scale * 0.94f).coerceAtLeast(FIT_FLOOR) },
        style = TextStyle(
            fontSize = em(shown),
            fontWeight = weight,
            lineHeight = em(shown * lineHeight),
            letterSpacing = (spacing * shown * 14f).sp,
            fontFeatureSettings = if (tnum) "tnum" else null,
        ),
    )
}

private const val FIT_FLOOR = 0.6f

/** Swallows touches silently, for a surface that must not let taps fall through to what is behind it. */
@Composable
fun Modifier.blockTouches(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

/** A press with no ripple, a tick under the finger and a soft sound. Switch-like things use [toggle]. */
@Composable
fun Modifier.pressable(toggle: Boolean = false, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val view = LocalView.current
    return clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
    ) {
        if (toggle) Feel.toggle(view) else Feel.tap(view)
        onClick()
    }
}

@Composable
fun Ico(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

// ---------- motion, the keyframes from the mockup ----------

/** 0.45..1 opacity and 0.84..1 scale, like @keyframes pulse. */
@Composable
fun Modifier.pulse(on: Boolean = true): Modifier {
    if (!on) return this
    val t = rememberInfiniteTransition(label = "pulse")
    val f by t.animateFloat(
        1f, 0f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    return graphicsLayer {
        alpha = 0.45f + 0.55f * f
        val s = 0.84f + 0.16f * f
        scaleX = s
        scaleY = s
    }
}

/** The bell swing, @keyframes ring. */
@Composable
fun Modifier.ring(on: Boolean = true): Modifier {
    if (!on) return this
    val t = rememberInfiniteTransition(label = "ring")
    val r by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 900
                0f at 0
                13f at 225
                -13f at 675
                0f at 900
            }
        ),
        label = "ring",
    )
    return graphicsLayer { rotationZ = r }
}

/** A short sideways shake every few seconds, @keyframes shake. */
@Composable
fun Modifier.shake(on: Boolean = true): Modifier {
    if (!on) return this
    val t = rememberInfiniteTransition(label = "shake")
    val x by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                0f at 0
                -2f at 520
                2f at 1040
                -2f at 1560
                2f at 2080
                0f at 2600
            }
        ),
        label = "shake",
    )
    return graphicsLayer { translationX = x * density }
}

@Composable
fun Modifier.spin(): Modifier {
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin",
    )
    return graphicsLayer { rotationZ = r }
}

/** 0..1..0, used for the alarm border and skeleton blocks. */
@Composable
fun breathe(millis: Int): Float {
    val t = rememberInfiniteTransition(label = "breathe")
    val f by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(millis / 2), RepeatMode.Reverse), label = "breathe",
    )
    return f
}

// ---------- buttons ----------

enum class BtnKind { PLAIN, TEAL, INK, DANGER }

@Composable
fun GgButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.PLAIN,
    small: Boolean = false,
    enabled: Boolean = true,
    square: Boolean = false,
    accent: Color? = null,
) {
    val c = Gg.colors
    // Every key carries a symbol. Common labels get theirs here, so no button is text alone.
    val icon = icon ?: text?.let { defaultIcon(it) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hard = if (small) 2.dp else 3.dp
    val radius = if (small) 11.dp else 12.dp
    val shape = RoundedCornerShape(radius)
    val fill: Brush
    val fg: Color
    when (kind) {
        BtnKind.PLAIN -> { fill = raisedBrush(c); fg = c.text }
        BtnKind.TEAL -> { fill = faceBrush(c.tealLight, c.teal, 0.62f); fg = c.onTeal }
        BtnKind.INK -> { fill = faceBrush(c.inkTop, c.text, 0.62f); fg = c.onInk }
        BtnKind.DANGER -> { fill = faceBrush(Color(0xFFFF8A7E), c.bad, 0.62f); fg = c.onBad }
    }
    val down = pressed && enabled
    val view = LocalView.current
    val sizeMod = if (square) Modifier.size(44.dp) else Modifier.defaultMinSize(minHeight = if (small) 40.dp else 44.dp)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .offset { if (down) IntOffset(hard.roundToPx(), hard.roundToPx()) else IntOffset.Zero }
            .then(if (down) Modifier else Modifier.neuShadow(c, radius, hard, Soft.SM))
            .clip(shape)
            .then(if (down) Modifier.background(c.sunk).neuInset(c, radius) else Modifier.background(fill))
            .border(2.dp, c.edge, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                // A small tick, so keys feel like the physical buttons they are drawn as.
                Feel.tap(view)
                onClick()
            }
            .then(sizeMod)
            .padding(horizontal = if (square) 0.dp else if (small) 10.dp else 13.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (down) c.text else fg
        CompositionLocalProvider(LocalContentColor provides tint) {
            // On a plain key the symbol takes its own colour; on a coloured key it stays the key's ink.
            val symbol = if (kind == BtnKind.PLAIN && !down && icon != null) accent ?: accentOf(icon) else tint
            if (icon != null) Ico(icon, size = if (small) 14.dp else 16.dp, tint = symbol)
            if (text != null) T(text.uppercase(), size = if (small) 0.74f else 0.84f, spacing = 0.05f, maxLines = 1)
        }
    }
}

private fun defaultIcon(label: String): ImageVector? = when (label.lowercase()) {
    "cancel", "close" -> GgIcons.X
    "not now", "later", "load older" -> GgIcons.Clock
    "allow alerts", "turn on" -> GgIcons.Bell
    "reset" -> GgIcons.Undo
    "change" -> GgIcons.Edit
    "details" -> GgIcons.Signal
    "fix" -> GgIcons.Wrench
    "restart", "working" -> GgIcons.Refresh
    else -> null
}

/**
 * One colour per symbol, the same wherever it appears: red for alerts and
 * anything that removes, amber for mute and caution, teal for the camera and
 * confirmation, blue for links and editing, purple for time and appearance.
 */
@Composable
fun accentOf(icon: ImageVector): Color {
    val c = Gg.colors
    return when (icon.name) {
        "bell", "trash", "x", "eraser", "warn", "nolink" -> c.bad
        "eyeoff", "bellauto", "wrench", "undo", "sun", "bolt", "gauge", "poly" -> c.warnText
        "shutter", "cam", "eye", "check", "ok", "refresh", "gear", "pen", "save", "shield", "target" -> c.teal
        "link", "edit", "signal", "filter", "layers", "bottle", "expand", "shrink" -> c.bottle
        "clock", "moon", "sunmoon", "phone", "cal", "bag", "activity", "mail" -> c.bag
        "cloud", "key", "power" -> c.cloud
        "battery", "plug", "container", "up" -> c.container
        else -> c.text
    }
}

/** The plus and minus keys inside a stepper well. */
@Composable
fun StepKey(label: String, onClick: () -> Unit) {
    val c = Gg.colors
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(if (pressed) Modifier else Modifier.neuShadow(c, 11.dp, 0.dp, Soft.SM))
            .clip(shape)
            .then(if (pressed) Modifier.background(c.sunk).neuInset(c, 11.dp) else Modifier.background(raisedBrush(c)))
            .border(2.dp, c.edge, shape)
            .clickable(interactionSource = src, indication = null) {
                Feel.tap(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { T(label, size = 1.15f, weight = FontWeight.Normal, color = c.text) }
}

// ---------- surfaces ----------

/** .card */
@Composable
fun GgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Gg.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .neuRaised(c, 16.dp, hard = 4.dp, soft = Soft.LG)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** .hd, the icon and title row at the top of a card. */
@Composable
fun Hd(
    icon: ImageVector,
    title: String,
    titleSize: Float = 1.05f,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Ico(icon, size = 18.dp, tint = accentOf(icon))
        T(title.uppercase(), Modifier.weight(1f), size = titleSize * 0.92f, weight = FontWeight.Black, spacing = 0.03f)
        trailing()
    }
}

/** .hint */
@Composable
fun Hint(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    size: Float = 0.78f,
    /** Let the text run over several lines, justified so both edges are straight. */
    wrap: Boolean = false,
) {
    val c = Gg.colors
    Row(
        modifier = modifier,
        verticalAlignment = if (wrap) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        if (icon != null) Ico(icon, Modifier.padding(top = if (wrap) 2.dp else 0.dp), size = 12.dp, tint = c.muted)
        T(
            text, Modifier.weight(1f, fill = !center),
            size = size, weight = FontWeight.Normal, color = c.muted, lineHeight = 1.3f,
            align = if (wrap) TextAlign.Justify else if (center) TextAlign.Center else null,
            fit = !wrap,
        )
    }
}

enum class Tone { OK, WARN, BAD }

/** .tag */
@Composable
fun Tag(text: String, tone: Tone, icon: ImageVector? = null) {
    val c = Gg.colors
    val bg = when (tone) { Tone.OK -> c.okTint; Tone.WARN -> c.warnTint; Tone.BAD -> c.badTint }
    val ink = when (tone) { Tone.OK -> c.onlineText; Tone.WARN -> c.warnText; Tone.BAD -> c.bad }
    val symbol = icon ?: when (tone) { Tone.OK -> GgIcons.Check; Tone.WARN -> GgIcons.Warn; Tone.BAD -> GgIcons.X }
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, c.edge, CircleShape)
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico(symbol, size = 10.dp, tint = ink)
        T(text.uppercase(), size = 0.66f, weight = FontWeight.Black, color = c.text, spacing = 0.05f, maxLines = 1)
    }
}

/**
 * The auto-mute control: a small key of its own, with the bell-and-clock
 * symbol, a caps label and the switch. It lights teal when armed.
 */
@Composable
fun AutoSwitch(checked: Boolean, onToggle: () -> Unit, label: String? = "AUTO-MUTE", icon: ImageVector? = GgIcons.BellAuto) {
    val c = Gg.colors
    val view = LocalView.current
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "sw")
    val trackW = 46.dp
    val trackH = 26.dp
    val knob = 20.dp
    // The switch stands on its own, with no key round it. On, the groove
    // fills with teal and a soft teal light spreads round it.
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Feel.toggle(view)
                onToggle()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) Ico(icon, size = 17.dp, tint = if (checked) c.teal else accentOf(icon))
        if (label != null) T(label, size = 0.68f, weight = FontWeight.Black, color = if (checked) c.teal else c.text, spacing = 0.06f, maxLines = 1)
        Box(
            Modifier
                .size(width = trackW, height = trackH)
                .drawBehind {
                    if (t > 0f) {
                        val g = 4.dp.toPx()
                        val r = (size.height + 2 * g) / 2f
                        drawRoundRect(
                            c.teal.copy(alpha = 0.26f * t), topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g), cornerRadius = CornerRadius(r, r),
                        )
                    }
                }
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.lerp(c.sunk, c.teal, t))
                .drawBehind {
                    // The groove's shade is drawn in plain dark, fading down from the
                    // top. It reads as sunk on the pale groove and on the teal one, in
                    // both themes, where the page's light and dark shadow pair did not.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.62f,
                        )
                    )
                }
                .border(2.dp, c.edge, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = 3.dp + (trackW - knob - 6.dp) * t)
                    .size(knob)
                    .drawBehind {
                        // The knob's own small shadow on the floor of the groove.
                        drawCircle(Color.Black.copy(alpha = 0.32f), radius = size.minDimension / 2f, center = center + Offset(1.dp.toPx(), 1.6.dp.toPx()))
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF8), Color(0xFFD6CEC2))))
                    .border(1.5.dp, c.edge, CircleShape),
            )
        }
    }
}

/**
 * .seg, a choice of a few options. The tray is sunk into the page and the
 * chosen option is a raised key standing in it, with its own rounded shape
 * and shadow, so what is chosen is pushed out and the rest stay pushed in.
 * With empty labels it is a row of symbols.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, icons: List<ImageVector> = emptyList(), onSelect: (Int) -> Unit) {
    val c = Gg.colors
    val view = LocalView.current
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().neuWell(c, 14.dp))
        Row(
            // A little more room on the right and bottom, where the raised key's hard shadow falls.
            Modifier.fillMaxWidth().padding(start = 5.dp, top = 5.dp, end = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .then(if (on) Modifier.neuRaised(c, 10.dp, hard = 2.dp, soft = Soft.SM, fill = raisedBrush(c)) else Modifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!on) Feel.toggle(view)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        icons.getOrNull(i)?.let {
                            val tint = accentOf(it)
                            Ico(it, size = if (label.isEmpty()) 18.dp else 14.dp, tint = if (on) tint else tint.copy(alpha = 0.55f))
                        }
                        if (label.isNotEmpty()) T(label.uppercase(), size = 0.78f, color = if (on) c.text else c.muted, spacing = 0.05f, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** .well with a stepper in it. */
@Composable
fun Stepper(value: Int, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Gg.colors
    Row(
        Modifier.fillMaxWidth().neuWell(c, 12.dp).padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepKey("−", onMinus)
        T("$value", Modifier.weight(1f), size = 1.35f, weight = FontWeight.Black, align = TextAlign.Center, tnum = true)
        T(unit, size = 0.8f, color = c.muted)
        StepKey("+", onPlus)
    }
}

/** .inp */
@Composable
fun GgInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    /** Shows dots instead of the characters. */
    secret: Boolean = false,
) {
    val c = Gg.colors
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .neuWell(c, 12.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) T(placeholder, size = 1.05f, color = c.muted.copy(alpha = 0.7f), maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = em(1.05f), fontWeight = FontWeight.ExtraBold),
            cursorBrush = SolidColor(c.teal),
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** .sk, the pulsing placeholder shown while loading. */
@Composable
fun Skeleton(height: Dp, modifier: Modifier = Modifier) {
    val c = Gg.colors
    val f = breathe(1200)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(0.5f + 0.5f * f)
            .clip(RoundedCornerShape(12.dp))
            .background(c.sunk),
    )
}

// ---------- brand, header, navigation ----------

@Composable
fun logoPainter() = painterResource(if (Gg.colors.isDark) R.drawable.gg_logo_dark else R.drawable.gg_logo_light)

/** The badge alone. It carries the name and has a light and a dark drawing. */
@Composable
fun BrandLogo(size: Dp, modifier: Modifier = Modifier, onLongHold: (() -> Unit)? = null) {
    val view = LocalView.current
    val hold = if (onLongHold == null) Modifier else Modifier.pointerInput(onLongHold) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // Null means three seconds passed with the finger still down.
            val released = withTimeoutOrNull(3000) {
                waitForUpOrCancellation()
                true
            }
            if (released == null) {
                Feel.thump(view)
                onLongHold()
                waitForUpOrCancellation()
            }
        }
    }
    Image(logoPainter(), contentDescription = "Garbage-Guard", modifier = modifier.size(size).then(hold))
}

/** A number that rolls to its new value instead of jumping. */
@Composable
fun RollingText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
        },
        label = "roll",
    ) { shown ->
        T(shown, size = size, weight = FontWeight.Black, color = color, tnum = true, spacing = spacing, lineHeight = 1.05f, maxLines = 1)
    }
}

/** .pillc, the connection pill. */
@Composable
fun ConnPill(conn: ConnState, modifier: Modifier = Modifier, onlineLabel: String = "Online", cloud: Boolean = false) {
    val c = Gg.colors
    val online = conn == ConnState.ONLINE
    Row(
        modifier
            .shake(!online)
            .neuRaised(c, 17.dp, fill = raisedBrush(c))
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (online && cloud) {
            // Through Firebase: the cloud, in the same orange the Pi's screen uses.
            Ico(GgIcons.Cloud, Modifier.pulse(), size = 15.dp, tint = c.cloud)
            T("Cloud", size = 0.82f, color = c.cloud, maxLines = 1)
        } else if (online) {
            Ico(GgIcons.Signal, Modifier.pulse(), size = 14.dp, tint = c.ok)
            T(onlineLabel, size = 0.82f, color = c.onlineText, maxLines = 1)
        } else {
            Ico(GgIcons.NoLink, size = 14.dp, tint = c.bad)
            T(if (conn == ConnState.NO_PI) "No link" else "No camera", size = 0.82f, color = c.bad, maxLines = 1)
        }
    }
}

/** .apph */
@Composable
fun AppHeader(conn: ConnState, battery: Battery, onBattery: () -> Unit, onToggleTheme: () -> Unit, onPillLongPress: () -> Unit, onLogoHold: () -> Unit = {}, cloud: Boolean = false) {
    val c = Gg.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandLogo(58.dp, onLongHold = onLogoHold)
        BatteryChip(battery, onBattery)
        Spacer(Modifier.weight(1f))
        ConnPill(conn, Modifier.longPress(onPillLongPress), cloud = cloud)
        GgButton(
            null, onToggleTheme, icon = if (c.isDark) GgIcons.Sun else GgIcons.Moon,
            small = true, square = true,
        )
    }
}

/** .bnav */
@Composable
fun BottomNav(tab: Tab, unread: Int, onSelect: (Tab) -> Unit) {
    val c = Gg.colors
    Column(Modifier.fillMaxWidth().background(c.raised)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.edge))
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NavItem(GgIcons.Eye, "Monitor", tab == Tab.MONITOR, 0) { onSelect(Tab.MONITOR) }
            NavItem(GgIcons.Clock, "History", tab == Tab.HISTORY, unread) { onSelect(Tab.HISTORY) }
            NavItem(GgIcons.Gear, "Settings", tab == Tab.SETTINGS, 0) { onSelect(Tab.SETTINGS) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val c = Gg.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .then(
                if (selected) Modifier.neuRaised(c, 14.dp, fill = raisedBrush(c))
                else Modifier.clip(shape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!selected) Feel.toggle(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Ico(icon, size = 18.dp, tint = if (selected) accentOf(icon) else c.muted)
            T(label.uppercase(), size = 0.7f, color = if (selected) c.text else c.muted, spacing = 0.06f, maxLines = 1)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 17.dp, y = 2.dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(c.bad)
                    .border(2.dp, c.edge, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { T(if (badge > 99) "99+" else "$badge", size = 0.62f, weight = FontWeight.Black, color = c.onBad, maxLines = 1) }
        }
    }
}

// ---------- overlays ----------

/** .scrim and .sheet */
@Composable
fun GgSheet(onDismiss: () -> Unit, dismissable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Gg.colors
    val slide = remember { Animatable(1f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(180)) }
    LaunchedEffect(Unit) { slide.animateTo(0f, tween(220)) }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(fade.value)
                .background(Color(0x8C0A0806))
                .pressable(enabled = dismissable, onClick = onDismiss),
        )
        val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = slide.value * size.height }
                .drawBehind {
                    // box-shadow: 0 -4px 0 edge
                    val r = 22.dp.toPx()
                    drawRoundRect(
                        c.edge, topLeft = Offset(0f, -4.dp.toPx()),
                        size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r),
                    )
                }
                .clip(shape)
                .background(c.raised)
                .border(2.dp, c.edge, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(c.shade),
            )
            CompositionLocalProvider(LocalContentColor provides c.text) { content() }
        }
    }
}

@Composable
fun SheetTitle(text: String, center: Boolean = false) {
    T(
        text, if (center) Modifier.fillMaxWidth() else Modifier, size = 1.1f, weight = FontWeight.Black,
        align = if (center) TextAlign.Center else null,
    )
}

@Composable
fun SheetText(text: String, center: Boolean = false, mutedSmall: Boolean = false) {
    val c = Gg.colors
    T(
        text, if (center) Modifier.fillMaxWidth() else Modifier,
        size = if (mutedSmall) 0.82f else 0.9f, weight = FontWeight.Normal,
        color = if (mutedSmall) c.muted else c.text, align = if (center) TextAlign.Center else TextAlign.Justify,
        lineHeight = 1.35f,
    )
}

/** .acts, two buttons sharing the row. */
@Composable
fun Acts(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** .toast */
@Composable
fun BoxScope.GgToast(message: String, bottom: Dp) {
    val c = Gg.colors
    val rise = remember(message) { Animatable(0f) }
    LaunchedEffect(message) { rise.animateTo(1f, tween(200)) }
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = bottom, start = 16.dp, end = 16.dp)
            .graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 6.dp.toPx()
            }
            .neuShadow(c, 12.dp, 4.dp, Soft.NONE, hardColor = if (c.isDark) c.teal else c.edge)
            .clip(RoundedCornerShape(12.dp))
            .background(c.text)
            .border(2.dp, c.edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Ico(GgIcons.Ok, size = 16.dp, tint = c.base2)
        T(message, size = 0.88f, color = c.base2)
    }
}

/** Long press without a ripple, used for the demo connection switch. */
fun Modifier.longPress(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
