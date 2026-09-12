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
import com.kyssta.hermeybeta.network.SkillInfo
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
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

class SkillsViewModel(app: Application) : AndroidViewModel(app) {
    var skills by mutableStateOf<List<SkillInfo>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    private var generation = -1
    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && skills.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = skills.isEmpty()
            error = null
            try {
                skills = SessionRepository.apiFor(conn).skills()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun toggle(skill: SkillInfo) {
        val name = skill.name ?: return
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).toggleSkill(name, skill.enabled != true)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    // ── Hub (search / install / reload via skills.manage) ────────────────
    var tabIdx by mutableIntStateOf(0)
    var hubQuery by mutableStateOf("")
    var hubResults by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var hubBusy by mutableStateOf(false)
    var hubError by mutableStateOf<String?>(null)
    var installedNote by mutableStateOf<String?>(null)

    fun hubSearch() {
        if (hubQuery.isBlank() || hubBusy) return
        hubBusy = true
        hubError = null
        installedNote = null
        viewModelScope.launch {
            try {
                val res = WsRpc.call(
                    "skills.manage",
                    JSONObject().put("action", "search").put("query", hubQuery),
                    timeoutMs = 30_000,
                )
                val arr = res.optJSONArray("results")
                hubResults = List(arr?.length() ?: 0) { i ->
                    val o = arr.getJSONObject(i)
                    o.optString("name") to o.optString("description")
                }.filter { it.first.isNotBlank() }
                if (hubResults.isEmpty()) hubError = "No matches"
            } catch (e: Exception) {
                hubError = gatewayErrorMessage(e)
            } finally {
                hubBusy = false
            }
        }
    }

    fun hubInstall(name: String) {
        hubBusy = true
        viewModelScope.launch {
            try {
                WsRpc.call(
                    "skills.manage",
                    JSONObject().put("action", "install").put("query", name),
                    timeoutMs = 120_000,
                )
                installedNote = "Installed $name"
                load(force = true)
            } catch (e: Exception) {
                hubError = gatewayErrorMessage(e)
            } finally {
                hubBusy = false
            }
        }
    }

    fun hubReload() {
        viewModelScope.launch {
            try {
                WsRpc.call("skills.reload", timeoutMs = 30_000)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }
}

/** Skills hub — mirrors the desktop skills page (list + toggles). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(onMenu: () -> Unit = {}) {
    val vm: SkillsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills", color = p.textPrimary) },
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
            SegmentedControl(
                options = listOf("Installed", "Hub"),
                selected = vm.tabIdx,
                onSelect = { vm.tabIdx = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            if (vm.tabIdx == 1) {
                HubSection(vm)
            } else {
            if (vm.skills.isNotEmpty()) {
                SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search skills")
            }
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null && vm.skills.isEmpty() -> ErrorState(
                    title = "Could not load skills",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> {
                    val rows = vm.skills.filter {
                        vm.query.isBlank() || (it.name ?: "").contains(vm.query, ignoreCase = true)
                    }
                    if (rows.isEmpty()) {
                        EmptyState(title = "No skills", description = "The gateway reported no skills.")
                    } else {
                        PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.refreshing = true; vm.load(force = true) },
                        ) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(rows, key = { it.name ?: "?" }) { s ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.name ?: "?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                            s.description?.let { Text(it, fontSize = 13.sp, color = p.textSecondary, maxLines = 2) }
                                            s.category?.let { Text(it, fontSize = 12.sp, color = p.textTertiary) }
                                        }
                                        Switch(
                                            checked = s.enabled == true,
                                            onCheckedChange = { vm.toggle(s) },
                                        )
                                    }
                                    HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun HubSection(vm: SkillsViewModel) {
    val p = Hermes
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = vm.hubQuery,
                onValueChange = { vm.hubQuery = it },
                placeholder = "Search the hub",
                modifier = Modifier.weight(1f),
            )
            HermesButton(
                "Go",
                onClick = { vm.hubSearch() },
                variant = HermesVariant.Secondary,
                size = HermesSize.Sm,
                enabled = vm.hubQuery.isNotBlank() && !vm.hubBusy,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HermesButton("Reload skills", onClick = { vm.hubReload() }, variant = HermesVariant.Text, size = HermesSize.Sm)
            if (vm.hubBusy) Loader()
        }
        vm.installedNote?.let { Text(it, fontSize = 13.sp, color = p.green) }
        vm.hubError?.let { Text(it, fontSize = 13.sp, color = p.textSecondary) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.hubResults, key = { it.first }) { (name, desc) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                        if (desc.isNotBlank()) Text(desc, fontSize = 13.sp, color = p.textSecondary, maxLines = 2)
                    }
                    HermesButton(
                        "Install",
                        onClick = { vm.hubInstall(name) },
                        variant = HermesVariant.Secondary,
                        size = HermesSize.Sm,
                        enabled = !vm.hubBusy,
                    )
                }
                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            }
        }
    }
}
