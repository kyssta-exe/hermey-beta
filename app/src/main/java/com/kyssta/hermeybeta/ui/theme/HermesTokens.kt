package com.kyssta.hermeybeta.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Desktop design seeds, ported 1:1 from apps/desktop/src/styles.css
 * (`:root` light seeds + `:root.dark` overrides).
 *
 * The desktop derives every surface from these seeds via color-mix in CSS;
 * [hermesPalette] performs the same derivation in Kotlin so mobile surfaces
 * equal the desktop ones for the same mode.
 */
object HermesSeeds {
    const val BASE_LIGHT = "#17171a"
    const val BASE_DARK = "#f2f2f4"
    const val PRIMARY = "#0053fd" // --theme-primary + --theme-midground
    const val WARM = "#cf806d"

    const val BG_SEED = "#f8faff"
    const val SIDEBAR_SEED = "#f3f7ff"
    const val CARD_SEED = "#ffffff"
    const val ELEVATED_SEED = "#ffffff"

    const val NEUTRAL_CHROME_DARK = "#0d0d0e"
    const val NEUTRAL_SIDEBAR_DARK = "#0a0a0b"
    const val NEUTRAL_CARD_LIGHT = "#fcfcfc"
    const val NEUTRAL_CARD_DARK = "#161618"

    // --ui-* status colors (light; dark overrides where the desktop has them).
    const val RED_LIGHT = "#cf2d56"
    const val RED_DARK = "#e75e78"
    const val ORANGE = "#db704b"
    const val YELLOW = "#c08532"
    const val GREEN_LIGHT = "#1f8a65"
    const val GREEN_DARK = "#55a583"
    const val CYAN_LIGHT = "#4c7f8c"
    const val CYAN_DARK = "#6f9ba6"
    const val PURPLE = "#9e94d5"
}

fun String.toColor(): Color {
    val rgb = hexToRgb(this) ?: Triple(0, 0, 0)
    return Color(rgb.first, rgb.second, rgb.third)
}

/** One computed palette. [dark] selects the `:root.dark` seed overrides. */
data class HermesPalette(
    val base: Color,
    val accent: Color,
    val background: Color,
    val sidebar: Color,
    val card: Color,
    val elevated: Color,
    val bubble: Color,
    val input: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val strokePrimary: Color,
    val strokeSecondary: Color,
    /** Default in-panel divider / list hairline (--ui-stroke-tertiary). */
    val strokeTertiary: Color,
    val strokeQuaternary: Color,
    /** Overlay hairline paired with the floating shadow (--stroke-nous). */
    val strokeNous: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val cyan: Color,
    val blue: Color,
    val purple: Color,
    val warm: Color,
    val onAccent: Color,
    val dark: Boolean,
)

/**
 * Computes the full palette from the cinematic mobile scheme (DESIGN v1.0 §01).
 *
 * The approved mobile look is dark-first and fixed: deep blue-black canvas
 * #0A0F17, card surface #121826, accent #3B82F6, hairlines #1F2A3A. Both modes
 * share those surfaces so the app always renders the approved scheme; light
 * mode only lifts body text readability. Mirrors styles.css in shape so every
 * existing call site keeps working.
 */
fun hermesPalette(dark: Boolean): HermesPalette {
    // Cinematic mobile scheme — fixed surfaces in both modes.
    val bg = "#0A0F17".toColor()
    val surface = "#121826".toColor()
    val accent = "#3B82F6".toColor()
    val bright = "#E6ECF3".toColor()
    val muted = "#8B98A7".toColor()
    val hairline = "#1F2A3A".toColor()
    val onAccent = Color.White
    // ponytail: single dark-first scheme; per-mode derivation removed —
    // reintroduce only if a light scheme is ever approved.
    return HermesPalette(
        base = bright,
        accent = accent,
        background = bg,
        sidebar = bg,
        card = surface,
        elevated = surface,
        bubble = "#18233A".toColor(),
        input = surface,
        textPrimary = bright,
        textSecondary = muted,
        textTertiary = muted.copy(alpha = 0.7f),
        textQuaternary = muted.copy(alpha = 0.5f),
        strokePrimary = hairline,
        strokeSecondary = hairline,
        strokeTertiary = hairline.copy(alpha = 0.7f),
        strokeQuaternary = hairline.copy(alpha = 0.5f),
        strokeNous = hairline.copy(alpha = 0.5f),
        red = "#F87171".toColor(),
        orange = HermesSeeds.ORANGE.toColor(),
        yellow = "#FBBF24".toColor(),
        green = "#34D399".toColor(),
        cyan = "#22D3EE".toColor(),
        blue = accent,
        purple = "#A78BFA".toColor(),
        warm = HermesSeeds.WARM.toColor(),
        onAccent = onAccent,
        dark = dark,
    )
}

/** Layout constants — cinematic mobile scheme (DESIGN v1.0 §05). */
object HermesLayout {
    /** PAGE_INSET_X: page side padding. */
    const val PAGE_INSET_X = 16
    /** Card corner radius (dp). */
    const val CARD_RADIUS = 12
    /** Control corner radius (dp). */
    const val CONTROL_RADIUS = 8
    /** Borderless overlay elevation (shadow-nous equivalent, dp). */
    const val OVERLAY_ELEVATION = 12
    /** Menu/popover elevation (shadow-md equivalent, dp). */
    const val MENU_ELEVATION = 6
    /** Icon buttons share a 4px radius; controls use 8px. */
    const val ICON_RADIUS = 4
    /** Quick functional transitions (~100ms on controls). */
    const val MOTION_MS = 100
}
