package com.kyssta.hermey.ui.theme

import androidx.compose.runtime.MutableState

/**
 * Appearance setting — mirrors the desktop color-mode SegmentedControl
 * (system / light / dark).
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeModeStore {
    /** Backing atom, written by the DataStore loader at startup & by Settings. */
    var mode: MutableState<ThemeMode> = androidx.compose.runtime.mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Called from Settings (and MainActivity boot) to persist + react. */
    fun setMode(newMode: ThemeMode) {
        mode.value = newMode
    }
}
