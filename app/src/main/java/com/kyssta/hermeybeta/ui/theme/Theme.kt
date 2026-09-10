package com.kyssta.hermeybeta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

private val LocalPalette = compositionLocalOf { hermesPalette(dark = true) }

/** Current Hermes palette inside [HermeyBetaTheme]. */
val Hermes: HermesPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalPalette.current

/**
 * App theme. Exact desktop port: fixed light/dark seeds, NO dynamic color —
 * the desktop applies user/VS Code themes deterministically, never the OS
 * wallpaper. Follows the system dark setting like the desktop's auto mode.
 */
@Composable
fun HermeyBetaTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = darkTheme ?: when (ThemeModeStore.mode.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val palette = hermesPalette(useDark)
    val scheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.cyan,
            tertiary = palette.purple,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.strokeSecondary,
            outlineVariant = palette.strokeTertiary,
            error = palette.red,
            onError = palette.onAccent,
            surfaceContainer = palette.elevated,
            surfaceContainerLow = palette.card,
            surfaceContainerLowest = palette.background,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.cyan,
            tertiary = palette.purple,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.card,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.strokeSecondary,
            outlineVariant = palette.strokeTertiary,
            error = palette.red,
            onError = palette.onAccent,
            surfaceContainer = palette.elevated,
            surfaceContainerLow = palette.card,
            surfaceContainerLowest = palette.background,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
