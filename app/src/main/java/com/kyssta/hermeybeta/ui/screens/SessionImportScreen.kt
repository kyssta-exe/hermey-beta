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
import com.kyssta.hermeybeta.network.ForeignSession
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseForeignSessions
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.session.WsRpc
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

class SessionImportViewModel(app: Application) : AndroidViewModel(app) {
    var source by mutableStateOf("all")
    var query by mutableStateOf("")
    var rows by mutableStateOf<List<ForeignSession>>(emptyList())
    var host by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var importing by mutableStateOf(false)
    var preview by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && rows.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = rows.isEmpty()
            error = null
            try {
                val params = JSONObject().put("offset", 0)
                if (source != "all") params.put("source", source)
                val res = WsRpc.call("session.foreign.list", params, 60_000)
                val (list, h) = parseForeignSessions(res)
                rows = list
                host = h
            } catch (e: Exception) { error = gatewayErrorMessage(e) }
            finally { loading = false; refreshing = false }
        }
    }

    fun previewRow(id: String) {
        viewModelScope.launch {
            preview = "Loading…"
            try {
                val res = WsRpc.call("session.foreign.preview", JSONObject().put("id", id), 60_000)
                val msgs = res.optJSONArray("messages")
                val sb = StringBuilder()
                if (msgs != null) for (i in 0 until minOf(msgs.length(), 6)) {
                    val m = msgs.optJSONObject(i) ?: continue
                    sb.append(m.optString("role")).append(": ")
                        .append(m.optString("content").take(300)).append("\n\n")
                }
                preview = sb.toString().take(2000).ifBlank { "No preview available." }
            } catch (e: Exception) { preview = gatewayErrorMessage(e) }
        }
    }

    fun importRow(id: String, onOpen: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            importing = true
            try {
                val res = WsRpc.call("session.foreign.import", JSONObject().put("id", id), 60_000)
                val sid = res.optString("session_id").ifBlank { res.optString("sessionId") }
                if (sid.isBlank()) onError("Import returned no session id")
                else onOpen(sid)
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { importing = false }
        }
    }
}

/** Session Import — desktop parity: Claude/Codex foreign sessions → import. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionImportScreen(onOpenSession: (String) -> Unit,
    onMenu: () -> Unit = {}) {
    val vm: SessionImportViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conn, vm.source) { vm.load(force = true) }

    val visible = remember(vm.rows, vm.query) {
        vm.rows.filter {
            vm.query.isBlank() || "${it.title} ${it.cwd} ${it.excerpt}".contains(vm.query, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import sessions", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = HermesLayout.PAGE_INSET_X.dp)) {
            if (toast != null) Text(toast!!, color = p.red, fontSize = 13.sp)
            Text(
                "From ${vm.host ?: "connected computer"} — continue a Claude or Codex session in Hermes.",
                color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(Modifier.padding(vertical = 8.dp)) {
                val srcOptions = listOf("all", "claude", "codex")
                SegmentedControl(
                    options = srcOptions,
                    selected = srcOptions.indexOf(vm.source).coerceAtLeast(0),
                    onSelect = { vm.source = srcOptions[it] },
                )
            }
            SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search foreign sessions")
            if (vm.preview != null) {
                Text(vm.preview!!, color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            when {
                vm.loading -> Loader(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                vm.error != null && vm.rows.isEmpty() -> ErrorState(
                    title = "Could not list sessions", description = vm.error,
                    retryLabel = "Retry", onRetry = { vm.load(force = true) },
                )
                visible.isEmpty() -> EmptyState(title = "Nothing to import", description = "No Claude or Codex sessions found.")
                else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refreshing = true; vm.load(force = true) }) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible.take(200), key = { it.id }) { row ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(row.title.ifBlank { row.id }, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${row.source} · ${row.turnCount} turns${row.cwd?.let { " · $it" } ?: ""}",
                                    color = p.textTertiary, fontSize = 12.sp,
                                )
                                if (row.excerpt.isNotBlank()) Text(row.excerpt.take(160), color = p.textSecondary, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    HermesButton("Preview", onClick = { vm.previewRow(row.id) },
                                        variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !vm.importing)
                                    HermesButton("Import", onClick = { vm.importRow(row.id, onOpenSession) { toast = it } },
                                        variant = HermesVariant.Default, size = HermesSize.Sm, enabled = !vm.importing)
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
