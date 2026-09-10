package com.kyssta.hermeybeta.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Exact port of apps/desktop/src/themes/color.ts.
 *
 * Everything works in 6-digit `#rrggbb`. Same naive sRGB-space math as the
 * desktop so a token computed here equals the token in the desktop app.
 */

fun hexToRgb(hex: String): Triple<Int, Int, Int>? {
    val clean = hex.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(clean)) return null
    return Triple(
        clean.substring(0, 2).toInt(16),
        clean.substring(2, 4).toInt(16),
        clean.substring(4, 6).toInt(16),
    )
}

fun rgbToHex(r: Int, g: Int, b: Int): String {
    fun clamp(n: Int) = n.coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${clamp(r)}${clamp(g)}${clamp(b)}"
}

fun mix(a: String, b: String, amount: Double): String {
    val ar = hexToRgb(a)
    val br = hexToRgb(b)
    if (ar == null || br == null) return a
    // Same rounding as the TS original (Math.round, half up).
    fun lerp(x: Int, y: Int) = (x + (y - x) * amount).roundToInt()
    return rgbToHex(lerp(ar.first, br.first), lerp(ar.second, br.second), lerp(ar.third, br.third))
}

private fun linearize(channel: Double): Double =
    if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

/** WCAG relative luminance (gamma-corrected), 0..1. */
fun relativeLuminance(hex: String): Double {
    val rgb = hexToRgb(hex) ?: return 0.0
    val (r, g, b) = listOf(rgb.first, rgb.second, rgb.third).map { linearize(it / 255.0) }
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio (1..21) between two hex colors. */
fun contrastRatio(a: String, b: String): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return if (la >= lb) (la + 0.05) / (lb + 0.05) else (lb + 0.05) / (la + 0.05)
}

/** Returns a readable foreground (#161616 or #ffffff) for a background hex. */
fun readableOn(hex: String): String =
    if (relativeLuminance(hex) > 0.58) "#161616" else "#ffffff"

/**
 * Guarantee [color] reads against [bg]: below [min] contrast it is mixed
 * toward white (dark bg) or black (light bg) until it clears.
 */
fun ensureContrast(color: String, bg: String, min: Double): String {
    if (contrastRatio(color, bg) >= min) return color
    val towards = if (relativeLuminance(bg) < 0.5) "#ffffff" else "#000000"
    var best = color
    var amount = 0.2
    while (amount <= 1.0001) {
        best = mix(color, towards, minOf(amount, 1.0))
        if (contrastRatio(best, bg) >= min) return best
        amount += 0.2
    }
    return best
}
