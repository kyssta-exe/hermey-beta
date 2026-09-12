package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.SessionSummary
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.LogView
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesMono
import kotlinx.coroutines.launch

class CommandCenterViewModel(app: Application) : AndroidViewModel(app) {
    var section by mutableStateOf("sessions")
    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
    var query by mutableStateOf("")
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var statusText by mutableStateOf<String?>(null)
    var logsText by mutableStateOf<String?>(null)
    var logFile by mutableStateOf("agent")
    var logLevel by mutableStateOf("ALL")
    var usageText by mutableStateOf<String?>(null)
    var usagePeriod by mutableStateOf(30)
    var busy by mutableStateOf(false)
    var pendingDelete by mutableStateOf<SessionSummary?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && sessions.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = sessions.isEmpty()
            error = null
            try {
                val conn = SessionRepository.connection.value ?: throw IllegalStateException("No gateway selected")
                sessions = SessionRepository.apiFor(conn).sessions()
            } catch (e: Exception) { error = gatewayErrorMessage(e) }
            finally { loading = false; refreshing = false }
        }
    }

    fun loadSystem() {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                val api = SessionRepository.apiFor(conn)
                statusText = api.status().toString(2)
                logsText = api.logs(file = logFile, level = logLevel).toString(2).take(12000)
            } catch (e: Exception) { statusText = gatewayErrorMessage(e) }
            finally { busy = false }
        }
    }

    fun loadUsage() {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                val api = SessionRepository.apiFor(conn)
                val usage = api.analyticsUsage(usagePeriod)
                val models = api.analyticsModels(usagePeriod)
                usageText = "Usage (${usagePeriod}d): ${usage.toString(2).take(4000)}\n\nModels: ${models.toString(2).take(4000)}"
            } catch (e: Exception) { usageText = gatewayErrorMessage(e) }
            finally { busy = false }
        }
    }

    fun delete(s: SessionSummary, onError: (String) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).deleteSession(s.stableId)
                sessions = sessions.filter { it.stableId != s.stableId }
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { busy = false }
        }
    }
}

/** Command Center — desktop parity: sessions / system / usage maintenance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(onOpenSession: (String) -> Unit,
    onMenu: () -> Unit = {}) {
    val vm: CommandCenterViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conn) { vm.load() }
    LaunchedEffect(vm.section) {
        if (vm.section == "system" && vm.statusText == null) vm.loadSystem()
        if (vm.section == "usage" && vm.usageText == null) vm.loadUsage()
    }

    val visibleSessions = remember(vm.sessions, vm.query) {
        val q = vm.query.trim()
        vm.sessions.filter { q.isBlank() || "${it.title} ${it.preview} ${it.id}".contains(q, true) }
            .sortedByDescending { it.lastActive ?: it.startedAt ?: 0.0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Command Center", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = HermesLayout.PAGE_INSET_X.dp)) {
            if (toast != null) Text(toast!!, color = p.red, fontSize = 13.sp)
            Row(Modifier.padding(vertical = 8.dp)) {
                val ccOptions = listOf("sessions", "system", "usage")
                SegmentedControl(
                    options = ccOptions,
                    selected = ccOptions.indexOf(vm.section).coerceAtLeast(0),
                    onSelect = { vm.section = ccOptions[it] },
                )
            }
            when (vm.section) {
                "sessions" -> {
                    SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search sessions")
                    when {
                        vm.loading -> Loader(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                        vm.error != null && vm.sessions.isEmpty() -> ErrorState(
                            title = "Could not load sessions", description = vm.error,
                            retryLabel = "Retry", onRetry = { vm.load(force = true) },
                        )
                        visibleSessions.isEmpty() -> EmptyState(title = "No sessions", description = "Nothing matches.")
                        else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refreshing = true; vm.load(force = true) }) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(visibleSessions.take(200), key = { it.stableId }) { s ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                        Text(s.title ?: s.stableId.take(8), color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("${s.messageCount ?: 0} msgs · ${s.model ?: ""}", color = p.textTertiary, fontSize = 12.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            HermesButton("Open", onClick = { onOpenSession(s.stableId) }, variant = HermesVariant.Secondary, size = HermesSize.Sm)
                                            HermesButton("Delete", onClick = { vm.pendingDelete = s }, variant = HermesVariant.Destructive, size = HermesSize.Sm, enabled = !vm.busy)
                                        }
                                    }
                                    HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
                "system" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        HermesButton("Refresh", onClick = { vm.loadSystem() }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !vm.busy)
                    }
                    if (vm.statusText != null) {
                        Text("Status", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        LogView(log = vm.statusText!!)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf("agent", "errors", "gateway").forEach { f ->
                            HermesButton(f, onClick = { vm.logFile = f; vm.loadSystem() },
                                variant = if (vm.logFile == f) HermesVariant.Default else HermesVariant.Secondary, size = HermesSize.Sm)
                        }
                    }
                    if (vm.logsText != null) {
                        Text("Logs (${vm.logFile})", color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        LogView(log = vm.logsText!!)
                    }
                    if (vm.statusText == null && vm.busy) Loader(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                }
                "usage" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf(7, 30, 90).forEach { d ->
                            HermesButton("${d}d", onClick = { vm.usagePeriod = d; vm.loadUsage() },
                                variant = if (vm.usagePeriod == d) HermesVariant.Default else HermesVariant.Secondary, size = HermesSize.Sm)
                        }
                    }
                    if (vm.usageText != null) LogView(log = vm.usageText!!)
                    else if (vm.busy) Loader(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                    else EmptyState(title = "No usage yet", description = "Analytics appear after the agent runs.")
                }
            }
        }
    }

    vm.pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { vm.pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("Delete \"${s.title ?: s.stableId}\"? This cannot be undone.") },
            confirmButton = {
                HermesButton("Delete", onClick = { vm.delete(s) { toast = it }; vm.pendingDelete = null },
                    variant = HermesVariant.Destructive, size = HermesSize.Sm)
            },
            dismissButton = { HermesButton("Cancel", onClick = { vm.pendingDelete = null }, variant = HermesVariant.Text, size = HermesSize.Sm) },
        )
    }
}
