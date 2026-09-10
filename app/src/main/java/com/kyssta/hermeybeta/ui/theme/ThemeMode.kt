package com.kyssta.hermeybeta.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Appearance setting — mirrors the desktop color-mode SegmentedControl
 * (system / light / dark). Persisted beside the atom that owns it.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeModeStore {
    val mode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM)
}
