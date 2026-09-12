package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.SpawnSnapshot
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseSpawnEntries
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.session.WsRpc
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StarmapViewModel(app: Application) : AndroidViewModel(app) {
    var snapshots by mutableStateOf<List<SpawnSnapshot>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && snapshots.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = snapshots.isEmpty()
            error = null
            try {
                val res = WsRpc.call(
                    "spawn_tree.list",
                    JSONObject().put("cross_session", true).put("limit", 100),
                )
                snapshots = parseSpawnEntries(res).sortedByDescending { it.startedAt ?: 0.0 }
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** Starmap — cross-session run history (spawn snapshots, mobile form). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarmapScreen(onOpenSession: (String) -> Unit,
    onMenu: () -> Unit = {}) {
    val vm: StarmapViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starmap", color = p.textPrimary) },
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
                vm.error != null && vm.snapshots.isEmpty() -> ErrorState(
                    title = "Could not load runs",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                vm.snapshots.isEmpty() -> EmptyState(
                    title = "No runs yet",
                    description = "Subagent runs across sessions appear here.",
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.snapshots, key = { it.path ?: it.sessionId + it.startedAt.toString() }) { s ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = s.sessionId.isNotBlank(),
                                    ) { onOpenSession(s.sessionId) }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    s.sessionId.take(8).ifBlank { "run" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = p.textPrimary,
                                )
                                val sub = listOfNotNull(
                                    s.startedAt?.let { formatTime(it) },
                                    "${s.subagentCount} subagents",
                                    if (s.finishedAt == null) "running" else null,
                                ).joinToString(" · ")
                                Text(sub, fontSize = 12.sp, color = p.textTertiary)
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(epochSeconds: Double): String = try {
    SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))
} catch (_: Exception) {
    ""
}
