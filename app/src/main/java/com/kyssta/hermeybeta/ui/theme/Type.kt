package com.kyssta.hermeybeta.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kyssta.hermeybeta.R

/**
 * Terminal/code face — JetBrains Mono, the same family the desktop bundles
 * (converted from its woff2 to ttf; OFL-licensed). Used for logs, commands,
 * and other mono surfaces — never for body text.
 */
val HermesMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * UI face — Inter (variable, OFL). One file covers every weight; Compose
 * picks the instance closest to the requested [FontWeight].
 */
val HermesSans = FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

/**
 * Hero/brand face — Playfair Display (variable, OFL). Display Black for the
 * HERMES AGENT wordmark and editorial moments only; never body text.
 */
val HermesDisplay = FontFamily(
    Font(R.font.playfair_display, FontWeight.Black),
)
