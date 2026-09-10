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
