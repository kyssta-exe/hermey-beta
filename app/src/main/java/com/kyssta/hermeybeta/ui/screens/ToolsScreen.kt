package com.kyssta.hermeybeta.ui.screens

import android.app.Application
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
import androidx.compose.runtime.setValue
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
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Tolerant name/description list from tools-style RPC envelopes. */
fun parseToolEntries(root: JSONObject, vararg keys: String): List<Pair<String, String>> {
    var arr: JSONArray? = null
    for (k in keys) {
        arr = root.optJSONArray(k)
        if (arr != null) break
    }
    arr = arr ?: root.optJSONArray("items") ?: return emptyList()
    return List(arr.length()) { i ->
        val item = arr.opt(i)
        when (item) {
            is JSONObject -> (item.optString("name").ifBlank { item.optString("id") }) to
                item.optString("description").ifBlank { item.optString("summary") }
            is String -> item to ""
            else -> "" to ""
        }
    }.filter { it.first.isNotBlank() }
}

class ToolsViewModel(app: Application) : AndroidViewModel(app) {
    var tools by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var toolsets by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && (tools.isNotEmpty() || toolsets.isNotEmpty())) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = tools.isEmpty() && toolsets.isEmpty()
            error = null
            try {
                // Both methods tolerate an empty session_id (global catalog).
                tools = parseToolEntries(WsRpc.call("tools.list", JSONObject().put("session_id", "")), "tools")
                toolsets = parseToolEntries(WsRpc.call("toolsets.list", JSONObject().put("session_id", "")), "toolsets")
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** Tools — tool + toolset catalogs (desktop toolset config, mobile form). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onMenu: () -> Unit = {}) {
    val vm: ToolsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools", color = p.textPrimary) },
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
                vm.error != null && vm.tools.isEmpty() && vm.toolsets.isEmpty() -> ErrorState(
                    title = "Could not load tools",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (vm.toolsets.isNotEmpty()) {
                            item { Text("Toolsets", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(vm.toolsets, key = { "ts:" + it.first }) { (name, desc) ->
                                ToolRow(name, desc)
                            }
                        }
                        if (vm.tools.isNotEmpty()) {
                            item { Text("Tools (${vm.tools.size})", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(vm.tools, key = { "t:" + it.first }) { (name, desc) ->
                                ToolRow(name, desc)
                            }
                        }
                        if (vm.tools.isEmpty() && vm.toolsets.isEmpty()) {
                            item { EmptyState(title = "No tools reported") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(name: String, desc: String) {
    val p = Hermes
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
        if (desc.isNotBlank()) Text(desc, fontSize = 13.sp, color = p.textSecondary, maxLines = 2)
    }
    HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
}
