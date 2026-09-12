package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.session.WsRpc
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AgentProcess(
    val sessionId: String = "",
    val command: String = "",
    val status: String = "",
    val uptimeSeconds: Long = 0,
)

fun parseAgentProcesses(root: JSONObject): List<AgentProcess> {
    val arr = root.optJSONArray("processes") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        AgentProcess(
            sessionId = o.optString("session_id"),
            command = o.optString("command"),
            status = o.optString("status"),
            uptimeSeconds = o.optLong("uptime", 0),
        )
    }
}

fun formatUptime(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
}

class AgentsViewModel(app: Application) : AndroidViewModel(app) {
    var processes by mutableStateOf<List<AgentProcess>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && processes.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = processes.isEmpty()
            error = null
            try {
                processes = parseAgentProcesses(WsRpc.call("agents.list"))
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** Agents — live gateway processes (desktop agents surface, mobile form). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(onOpenSession: (String) -> Unit,
    onMenu: () -> Unit = {}) {
    val vm: AgentsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
        ) {
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null && vm.processes.isEmpty() -> ErrorState(
                    title = "Could not load agents",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                vm.processes.isEmpty() -> EmptyState(
                    title = "No running agents",
                    description = "Background runs and subagents appear here.",
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.processes, key = { it.sessionId.ifBlank { it.command } }) { proc ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        proc.command.ifBlank { proc.sessionId.take(8) },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = p.textPrimary,
                                        maxLines = 1,
                                    )
                                    val sub = listOfNotNull(
                                        proc.status.takeIf { it.isNotBlank() },
                                        "up ${formatUptime(proc.uptimeSeconds)}",
                                    ).joinToString(" · ")
                                    Text(sub, fontSize = 12.sp, color = p.textTertiary)
                                }
                                if (proc.sessionId.isNotBlank()) {
                                    HermesButton(
                                        "Open",
                                        onClick = { onOpenSession(proc.sessionId) },
                                        variant = HermesVariant.Text,
                                        size = HermesSize.Sm,
                                    )
                                }
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
