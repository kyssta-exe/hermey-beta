package com.kyssta.hermeybeta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.kyssta.hermey.network.GatewayCookieJar
import com.kyssta.hermey.navigation.AppNavigation
import com.kyssta.hermey.ui.theme.HermeyBetaTheme
import com.kyssta.hermey.ui.theme.ThemeMode
import com.kyssta.hermey.ui.theme.ThemeModeStore
import com.kyssta.hermey.ui.theme.ThemeModeStorage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GatewayCookieJar.init(this)
        enableEdgeToEdge()

        // Restore persisted theme mode once at cold start.
        lifecycleScope.launch {
            ThemeModeStorage.load(this@MainActivity).collect { mode ->
                ThemeModeStore.setMode(mode)
            }
        }

        setContent {
            HermeyBetaTheme { AppNavigation() }
        }
    }
}
