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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.ModelStat
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseModelsAnalytics
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.session.WsRpc
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

class InsightsViewModel(app: Application) : AndroidViewModel(app) {
    var sessions by mutableStateOf(-1)
    var messages by mutableStateOf(-1)
    var models by mutableStateOf<List<ModelStat>>(emptyList())
    var totals by mutableStateOf<JSONObject?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var daysIdx by mutableIntStateOf(1)
    private var generation = -1

    val days: Int get() = intArrayOf(7, 30, 90)[daysIdx]

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && sessions >= 0) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = sessions < 0
            error = null
            try {
                val res = WsRpc.call("insights.get", JSONObject().put("days", days))
                sessions = res.optInt("sessions", 0)
                messages = res.optInt("messages", 0)
                val api = SessionRepository.apiFor(conn)
                val (m, t) = parseModelsAnalytics(api.analyticsModels(days))
                models = m
                totals = t
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }
}

/** Insights — usage analytics (desktop analytics + web dashboard data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onMenu: () -> Unit = {}) {
    val vm: InsightsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", color = p.textPrimary) },
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
            SegmentedControl(
                options = listOf("7 days", "30 days", "90 days"),
                selected = vm.daysIdx,
                onSelect = { vm.daysIdx = it; vm.sessions = -1; vm.load(force = true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null && vm.sessions < 0 -> ErrorState(
                    title = "Could not load insights",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatCard("Sessions", vm.sessions.toString(), Modifier.weight(1f))
                        StatCard("Messages", vm.messages.toString(), Modifier.weight(1f))
                    }
                    vm.totals?.let { t ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatCard("API calls", t.optInt("total_api_calls", 0).toString(), Modifier.weight(1f))
                            StatCard(
                                "Est. cost",
                                "$" + "%.2f".format(t.optDouble("total_estimated_cost", 0.0)),
                                Modifier.weight(1f),
                            )
                        }
                    }
                    if (vm.models.isNotEmpty()) {
                        Text("By model", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.padding(top = 8.dp))
                        vm.models.forEach { m ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(m.model.ifBlank { "unknown" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                Text(
                                    "${m.sessions} sessions · ${m.calls} calls · ${m.input + m.output} tokens",
                                    fontSize = 12.sp,
                                    color = p.textTertiary,
                                )
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    } else if (vm.sessions == 0) {
                        EmptyState(title = "No activity", description = "No sessions in the last ${vm.days} days.")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val p = Hermes
    Column(modifier.padding(vertical = 4.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
        Text(label, fontSize = 12.sp, color = p.textTertiary)
    }
}
