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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch

class MemoryViewModel(app: Application) : AndroidViewModel(app) {
    var active by mutableStateOf<String?>(null)
    var detail by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var targetIdx by mutableIntStateOf(0)
    var resetting by mutableStateOf(false)
    private var generation = -1

    val target: String get() = arrayOf("memory", "user", "all")[targetIdx]

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && active != null) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = active == null
            error = null
            try {
                val mem = SessionRepository.apiFor(conn).memory()
                active = mem.optString("active").ifBlank { "on" }
                val files = mem.optJSONObject("builtin_files")
                val providers = mem.optJSONArray("providers")
                detail = listOfNotNull(
                    files?.let { "${it.length()} files" },
                    providers?.let { "${it.length()} providers" },
                ).joinToString(" · ").ifBlank { null }
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    fun reset() {
        val conn = SessionRepository.connection.value ?: return
        resetting = true
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).resetMemory(target)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                resetting = false
            }
        }
    }
}

/** Memory — provider status + reset (desktop memory surface, mobile form). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onMenu: () -> Unit = {}) {
    val vm: MemoryViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
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
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null && vm.active == null -> ErrorState(
                    title = "Could not load memory",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> {
                    ListRow(label = "Agent memory", description = listOfNotNull(vm.active, vm.detail).joinToString(" · "))
                    HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                    Text("Reset scope", color = p.textTertiary, fontSize = 12.sp)
                    SegmentedControl(
                        options = listOf("Memory", "User", "All"),
                        selected = vm.targetIdx,
                        onSelect = { vm.targetIdx = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    HermesButton(
                        if (vm.resetting) "Resetting…" else "Reset ${vm.target} memory",
                        onClick = { vm.reset() },
                        variant = HermesVariant.Destructive,
                        enabled = !vm.resetting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    if (vm.error != null) {
                        Text(vm.error ?: "", color = p.red, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
