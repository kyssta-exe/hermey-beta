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
 * UI face — the system sans stack, exactly like desktop (Segoe/SF/system-ui;
 * Roboto on Android). No bundled file; desktop ships no sans webfont either.
 */
val HermesSans: FontFamily = FontFamily.SansSerif

/**
 * Hero/brand face — Collapse Bold, the face desktop bundles
 * (@nous-research/ui, woff2 converted to ttf; OFL-licensed). Desktop renders
 * the HERMES AGENT wordmark in Collapse 700 — never a serif.
 */
val HermesDisplay = FontFamily(
    Font(R.font.collapse_bold, FontWeight.Bold),
)
