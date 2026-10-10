package com.garbageguard.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Tokens copied from the .ph[data-th] blocks of garbage-guard-app-mockup v3.html.

@Immutable
data class GgColors(
    val base: Color,
    val base2: Color,
    val raised: Color,
    val sunk: Color,
    /** Dark side of the soft shadow. */
    val shade: Color,
    /** Light side of the soft shadow, and the top of raised gradients. */
    val lite: Color,
    val text: Color,
    val muted: Color,
    /** Border and hard offset shadow. */
    val edge: Color,
    val teal: Color,
    val tealLight: Color,
    val tealTint: Color,
    val onTeal: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val okTint: Color,
    val warnTint: Color,
    val badTint: Color,
    val onBad: Color,
    /** Hard shadow under the GUARD chip. */
    val chipShadow: Color,
    val inkTop: Color,
    val onInk: Color,
    val warnText: Color,
    val onlineText: Color,
    val bottle: Color,
    val bag: Color,
    val container: Color,
    val isDark: Boolean,
)

val LightGg = GgColors(
    base = Color(0xFFDED4C8),
    base2 = Color(0xFFE9E1D6),
    raised = Color(0xFFEFE8DE),
    sunk = Color(0xFFD0C4B5),
    shade = Color(0xFFB3A592),
    lite = Color(0xFFFFFFFF),
    text = Color(0xFF16130F),
    muted = Color(0xFF6A6054),
    edge = Color(0xFF16130F),
    teal = Color(0xFF128C84),
    tealLight = Color(0xFF2FB3A8),
    tealTint = Color(0xFFBFE3DF),
    onTeal = Color(0xFFFFFFFF),
    ok = Color(0xFF2F9E44),
    warn = Color(0xFFE8A317),
    bad = Color(0xFFD92D20),
    okTint = Color(0xFFCDE9D4),
    warnTint = Color(0xFFF7E3B8),
    badTint = Color(0xFFF4C8C3),
    onBad = Color(0xFFFFFFFF),
    chipShadow = Color(0xFF128C84),
    inkTop = Color(0xFF45413B),
    onInk = Color(0xFFDED4C8),
    warnText = Color(0xFF8A6100),
    onlineText = Color(0xFF15703A),
    bottle = Color(0xFF0F7BD4),
    bag = Color(0xFFA32BC9),
    container = Color(0xFF1E9E4A),
    isDark = false,
)

val DarkGg = GgColors(
    base = Color(0xFF1F1C19),
    base2 = Color(0xFF2A2622),
    raised = Color(0xFF332E29),
    sunk = Color(0xFF1A1714),
    shade = Color(0xFF0D0B09),
    lite = Color(0xFF504840),
    text = Color(0xFFF2EBE1),
    muted = Color(0xFFA79B8C),
    edge = Color(0xFF090807),
    teal = Color(0xFF16A79C),
    tealLight = Color(0xFF3ED0C4),
    tealTint = Color(0xFF1C3A37),
    onTeal = Color(0xFF08201E),
    ok = Color(0xFF4ECB71),
    warn = Color(0xFFF0B429),
    bad = Color(0xFFFF6B5E),
    okTint = Color(0xFF1E3B27),
    warnTint = Color(0xFF453213),
    badTint = Color(0xFF4A211C),
    onBad = Color(0xFF1A0805),
    chipShadow = Color(0xFF090807),
    inkTop = Color(0xFFFFFFFF),
    onInk = Color(0xFF16130F),
    warnText = Color(0xFFF0B429),
    onlineText = Color(0xFF4ECB71),
    bottle = Color(0xFF4DA3F0),
    bag = Color(0xFFD17DF0),
    container = Color(0xFF4ECB71),
    isDark = true,
)

val LocalGgColors = staticCompositionLocalOf { LightGg }
