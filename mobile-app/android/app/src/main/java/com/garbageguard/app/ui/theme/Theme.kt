package com.garbageguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { AUTO, LIGHT, DARK }

private fun schemeFor(c: GgColors) =
    if (c.isDark) darkColorScheme(
        primary = c.teal, onPrimary = c.onTeal,
        primaryContainer = c.tealTint, onPrimaryContainer = c.text,
        background = c.base, onBackground = c.text,
        surface = c.raised, onSurface = c.text,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted,
        surfaceContainer = c.raised, surfaceContainerHigh = c.raised,
        error = c.bad, outline = c.muted, outlineVariant = c.shade,
    ) else lightColorScheme(
        primary = c.teal, onPrimary = c.onTeal,
        primaryContainer = c.tealTint, onPrimaryContainer = c.text,
        background = c.base, onBackground = c.text,
        surface = c.raised, onSurface = c.text,
        surfaceVariant = c.sunk, onSurfaceVariant = c.muted,
        surfaceContainer = c.raised, surfaceContainerHigh = c.raised,
        error = c.bad, outline = c.muted, outlineVariant = c.shade,
    )

/** Use Gg.colors.ok etc. for the colors Material has no slot for. */
object Gg {
    val colors: GgColors
        @Composable get() = LocalGgColors.current
}

@Composable
fun GarbageGuardTheme(
    mode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkGg else LightGg
    CompositionLocalProvider(LocalGgColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFor(colors),
            typography = GgTypography,
            content = content,
        )
    }
}
