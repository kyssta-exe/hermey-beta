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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.kyssta.hermeybeta.network.McpServer
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseMcpServers
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.session.WsRpc
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesDialog
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

data class McpCatalogEntry(
    val name: String = "",
    val description: String = "",
    val transport: String = "",
    val requiredEnv: List<Pair<String, String>> = emptyList(),
)

fun parseMcpCatalog(root: JSONObject): List<McpCatalogEntry> {
    val arr = root.optJSONArray("entries")
        ?: root.optJSONArray("catalog")
        ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        McpCatalogEntry(
            name = o.optString("name"),
            description = o.optString("description"),
            transport = o.optString("transport"),
            requiredEnv = o.optJSONArray("required_env")?.let { e ->
                List(e.length()) { j ->
                    val f = e.optJSONObject(j) ?: JSONObject()
                    f.optString("name") to f.optString("prompt").ifBlank { f.optString("name") }
                }
            }.orEmpty().filter { it.first.isNotBlank() },
        )
    }
}

class McpViewModel(app: Application) : AndroidViewModel(app) {
    var tabIdx by mutableIntStateOf(0)
    var servers by mutableStateOf<List<McpServer>>(emptyList())
    var catalog by mutableStateOf<List<McpCatalogEntry>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var catalogError by mutableStateOf<String?>(null)
    var testResult by mutableStateOf<Pair<String, String>?>(null)
    var installing by mutableStateOf<McpCatalogEntry?>(null)
    var busy by mutableStateOf(false)
    private var generation = -1

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && servers.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = servers.isEmpty()
            error = null
            try {
                servers = parseMcpServers(SessionRepository.apiFor(conn).mcpServers())
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadCatalog() {
        val conn = SessionRepository.connection.value ?: return
        viewModelScope.launch {
            catalogError = null
            try {
                catalog = parseMcpCatalog(SessionRepository.apiFor(conn).mcpCatalog())
            } catch (e: Exception) {
                catalogError = gatewayErrorMessage(e)
            }
        }
    }

    fun toggle(server: McpServer) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).setMcpEnabled(server.name, !server.enabled)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun test(server: McpServer) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                val res = SessionRepository.apiFor(conn).testMcpServer(server.name)
                testResult = server.name to (res.optString("detail").ifBlank { res.toString().take(300) })
            } catch (e: Exception) {
                testResult = server.name to gatewayErrorMessage(e)
            }
        }
    }

    fun delete(server: McpServer) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).deleteMcpServer(server.name)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun install(entry: McpCatalogEntry, env: Map<String, String>) {
        busy = true
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                val envJson = JSONObject()
                env.forEach { (k, v) -> envJson.put(k, v) }
                SessionRepository.apiFor(conn).installMcp(entry.name, envJson, true)
                installing = null
                load(force = true)
                tabIdx = 0
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }
}

/** MCP — servers (toggle/test/delete) + catalog install with env prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen() {
    val vm: McpViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }
    LaunchedEffect(vm.tabIdx) { if (vm.tabIdx == 1 && vm.catalog.isEmpty()) vm.loadCatalog() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP", color = p.textPrimary) },
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
            SegmentedControl(
                options = listOf("Installed", "Catalog"),
                selected = vm.tabIdx,
                onSelect = { vm.tabIdx = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            if (vm.tabIdx == 0) {
                when {
                    vm.loading -> Loader(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp))
                    vm.error != null && vm.servers.isEmpty() -> ErrorState(
                        title = "Could not load MCP servers",
                        description = vm.error,
                        retryLabel = "Retry",
                        onRetry = { vm.load(force = true) },
                    )
                    vm.servers.isEmpty() -> EmptyState(title = "No MCP servers", description = "Install one from the Catalog tab.")
                    else -> PullToRefreshBox(
                        isRefreshing = vm.refreshing,
                        onRefresh = { vm.refreshing = true; vm.load(force = true) },
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(vm.servers, key = { it.name }) { s ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                            val sub = listOfNotNull(
                                                s.transport.takeIf { it.isNotBlank() },
                                                if (s.tools.isNotEmpty()) "${s.tools.size} tools" else null,
                                            ).joinToString(" · ")
                                            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = p.textTertiary)
                                        }
                                        Switch(checked = s.enabled, onCheckedChange = { vm.toggle(s) })
                                    }
                                    vm.testResult?.takeIf { it.first == s.name }?.let { (_, out) ->
                                        Text(out, fontSize = 12.sp, color = p.textSecondary, maxLines = 4)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        HermesButton("Test", onClick = { vm.test(s) }, variant = HermesVariant.Text, size = HermesSize.Sm)
                                        HermesButton("Delete", onClick = { vm.delete(s) }, variant = HermesVariant.Text, size = HermesSize.Sm)
                                    }
                                }
                                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            } else {
                when {
                    vm.catalogError != null && vm.catalog.isEmpty() -> ErrorState(
                        title = "Could not load catalog",
                        description = vm.catalogError,
                        retryLabel = "Retry",
                        onRetry = { vm.loadCatalog() },
                    )
                    vm.catalog.isEmpty() -> Loader(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.catalog, key = { it.name }) { e ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                    if (e.description.isNotBlank()) Text(e.description, fontSize = 13.sp, color = p.textSecondary, maxLines = 2)
                                    if (e.requiredEnv.isNotEmpty()) Text("needs ${e.requiredEnv.size} secret(s)", fontSize = 12.sp, color = p.yellow)
                                }
                                HermesButton("Install", onClick = { vm.installing = e }, variant = HermesVariant.Secondary, size = HermesSize.Sm)
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    vm.installing?.let { entry ->
        val envValues = androidx.compose.runtime.remember(entry.name) {
            mutableStateMapOf<String, String>().apply { entry.requiredEnv.forEach { put(it.first, "") } }
        }
        HermesDialog(
            onDismissRequest = { vm.installing = null },
            title = { Text("Install ${entry.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.requiredEnv.isEmpty()) {
                        Text("No secrets required.")
                    } else {
                        entry.requiredEnv.forEach { (key, prompt) ->
                            OutlinedTextField(
                                value = envValues[key] ?: "",
                                onValueChange = { envValues[key] = it },
                                label = { Text(prompt) },
                                singleLine = true,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                HermesButton(
                    if (vm.busy) "…" else "Install",
                    onClick = { vm.install(entry, envValues.toMap()) },
                    enabled = !vm.busy && entry.requiredEnv.all { (envValues[it.first] ?: "").isNotBlank() },
                )
            },
            dismissButton = {
                HermesButton("Cancel", onClick = { vm.installing = null }, variant = HermesVariant.Text)
            },
        )
    }
}
