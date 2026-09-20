package com.kyssta.hermey.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeModeStorage {
    private val SYSTEM_KEY = stringPreferencesKey("theme_mode")

    fun load(context: Context): Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[SYSTEM_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun save(context: Context, mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[SYSTEM_KEY] = when (mode) {
                ThemeMode.SYSTEM -> "SYSTEM"
                ThemeMode.LIGHT -> "LIGHT"
                ThemeMode.DARK -> "DARK"
            }
        }
    }
}
