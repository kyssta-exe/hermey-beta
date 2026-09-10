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

/** Computes the full palette from the desktop seeds. Mirrors styles.css. */
fun hermesPalette(dark: Boolean): HermesPalette {
    val baseHex = if (dark) HermesSeeds.BASE_DARK else HermesSeeds.BASE_LIGHT
    val base = baseHex.toColor()
    val accent = HermesSeeds.PRIMARY.toColor()

    // --theme-mix-* knobs per mode (chrome 92/74, card 22/38, elevated 28/46).
    val cardMix = if (dark) 0.38 else 0.22
    val elevatedMix = if (dark) 0.46 else 0.28
    val neutralCard = if (dark) HermesSeeds.NEUTRAL_CARD_DARK else HermesSeeds.NEUTRAL_CARD_LIGHT

    // --ui-bg-card / --ui-bg-elevated: seed mixed over the neutral card.
    val card = mix(HermesSeeds.CARD_SEED, neutralCard, 1 - cardMix).toColor()
    val elevated = mix(HermesSeeds.ELEVATED_SEED, neutralCard, 1 - elevatedMix).toColor()
    // --theme-bubble-seed: accent 6% over white (light); dark mixes toward card.
    val bubble = if (dark) {
        val accentSoft = mix(HermesSeeds.PRIMARY, "#ffffff", 0.9)
        mix(accentSoft, neutralCard, 0.54).toColor()
    } else {
        mix(HermesSeeds.PRIMARY, "#ffffff", 0.94).toColor()
    }

    // Text hierarchy: base at 94 / 74 / 54 / 36%.
    fun text(alpha: Float) = base.copy(alpha = alpha)

    // Strokes: accent-tinted base hairlines (light 24/10, 16/7, 10/5, 6/3).
    fun stroke(accentMix: Double, alpha: Float): Color {
        val solid = mix(HermesSeeds.PRIMARY, baseHex, 1 - accentMix).toColor()
        return solid.copy(alpha = alpha)
    }

    return HermesPalette(
        base = base,
        accent = accent,
        background = if (dark) HermesSeeds.NEUTRAL_CHROME_DARK.toColor() else HermesSeeds.BG_SEED.toColor(),
        sidebar = if (dark) HermesSeeds.NEUTRAL_SIDEBAR_DARK.toColor() else HermesSeeds.SIDEBAR_SEED.toColor(),
        card = card,
        elevated = elevated,
        bubble = bubble,
        input = neutralCard.toColor(),
        textPrimary = text(0.94f),
        textSecondary = text(0.74f),
        textTertiary = text(0.54f),
        textQuaternary = text(0.36f),
        strokePrimary = stroke(0.24, 0.35f),
        strokeSecondary = stroke(0.16, 0.28f),
        strokeTertiary = stroke(0.10, 0.22f),
        strokeQuaternary = stroke(0.06, 0.16f),
        // --stroke-nous is currentColor at 3%; lifted to 8% as the mobile
        // visibility floor (1dp hairlines alias to invisible at 3%).
        strokeNous = base.copy(alpha = 0.08f),
        red = (if (dark) HermesSeeds.RED_DARK else HermesSeeds.RED_LIGHT).toColor(),
        orange = HermesSeeds.ORANGE.toColor(),
        yellow = HermesSeeds.YELLOW.toColor(),
        green = (if (dark) HermesSeeds.GREEN_DARK else HermesSeeds.GREEN_LIGHT).toColor(),
        cyan = (if (dark) HermesSeeds.CYAN_DARK else HermesSeeds.CYAN_LIGHT).toColor(),
        blue = HermesSeeds.PRIMARY.toColor(),
        purple = HermesSeeds.PURPLE.toColor(),
        warm = HermesSeeds.WARM.toColor(),
        onAccent = readableOn(HermesSeeds.PRIMARY).toColor(),
        dark = dark,
    )
}

/** Layout constants — mirrors apps/desktop/src/app/layout-constants.ts. */
object HermesLayout {
    /** PAGE_INSET_X: page side padding. */
    const val PAGE_INSET_X = 16
    /** Borderless overlay elevation (shadow-nous equivalent, dp). */
    const val OVERLAY_ELEVATION = 12
    /** Menu/popover elevation (shadow-md equivalent, dp). */
    const val MENU_ELEVATION = 6
    /** Icon buttons share a 4px radius; controls use 8px. */
    const val ICON_RADIUS = 4
    const val CONTROL_RADIUS = 8
    /** Quick functional transitions (~100ms on controls). */
    const val MOTION_MS = 100
}
