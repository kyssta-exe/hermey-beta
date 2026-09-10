package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.BuildConfig
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.network.GatewayCookieJar
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.ThemeMode
import com.kyssta.hermeybeta.ui.theme.ThemeModeStore
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    var modelLabel by mutableStateOf<String?>(null)
    var memoryLabel by mutableStateOf<String?>(null)

    fun load() {
        val conn = SessionRepository.connection.value ?: return
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                modelLabel = api.modelInfo().model ?: "unknown"
            } catch (_: Exception) {
            }
            try {
                val api = SessionRepository.apiFor(conn)
                val mem = api.memory()
                memoryLabel = mem.optString("active").ifBlank { "on" }
            } catch (_: Exception) {
            }
        }
    }
}

/** Settings — mirrors the desktop settings overlay (models, appearance, gateway). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignOut: () -> Unit, onManageGateways: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val ctx = LocalContext.current.applicationContext
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Gateway", color = p.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            ListRow(
                label = conn?.displayName ?: "None",
                description = conn?.baseUrl,
                action = {
                    HermesButton("Manage", onClick = onManageGateways, variant = HermesVariant.Text, size = HermesSize.Sm)
                },
            )
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            Text("Model", color = p.textTertiary, fontSize = 12.sp)
            ListRow(label = "Active model", description = vm.modelLabel ?: "…")
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            Text("Memory", color = p.textTertiary, fontSize = 12.sp)
            ListRow(label = "Agent memory", description = vm.memoryLabel ?: "…")
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            Text("Appearance", color = p.textTertiary, fontSize = 12.sp)
            SegmentedControl(
                options = listOf("System", "Light", "Dark"),
                selected = when (ThemeModeStore.mode.value) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
                onSelect = {
                    ThemeModeStore.mode.value = when (it) {
                        1 -> ThemeMode.LIGHT
                        2 -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            Text("About", color = p.textTertiary, fontSize = 12.sp)
            ListRow(label = "Hermey Beta", description = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · thin gateway client")
            HermesButton(
                "Sign out",
                onClick = {
                    GatewayCookieJar.clear()
                    SessionRepository.deactivate()
                    ConnectionStore(ctx).setActive(null)
                    onSignOut()
                },
                variant = HermesVariant.Destructive,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}
