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
import com.kyssta.hermeybeta.network.MsgPlatform
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseMsgPlatforms
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch

class MessagingViewModel(app: Application) : AndroidViewModel(app) {
    var platforms by mutableStateOf<List<MsgPlatform>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && platforms.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = platforms.isEmpty()
            error = null
            try {
                platforms = parseMsgPlatforms(SessionRepository.apiFor(conn).messagingPlatforms())
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** Messaging — channel adapters configured on the gateway. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(onMenu: () -> Unit = {}) {
    val vm: MessagingViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messaging", color = p.textPrimary) },
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
                vm.error != null && vm.platforms.isEmpty() -> ErrorState(
                    title = "Could not load channels",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                vm.platforms.isEmpty() -> EmptyState(
                    title = "No channels",
                    description = "Connect Telegram, Discord or WhatsApp from the desktop or web dashboard.",
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.platforms, key = { it.id }) { pl ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pl.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                        pl.description?.let { Text(it, fontSize = 13.sp, color = p.textSecondary, maxLines = 2) }
                                    }
                                    Text(
                                        pl.state.ifBlank { if (pl.enabled) "on" else "off" },
                                        fontSize = 12.sp,
                                        color = when (pl.state) {
                                            "connected" -> p.green
                                            "disabled", "not_configured", "gateway_stopped" -> p.textTertiary
                                            "pending_restart" -> p.yellow
                                            else -> if (pl.enabled) p.green else p.textTertiary
                                        },
                                    )
                                }
                                val sub = listOfNotNull(
                                    if (pl.configured) "configured" else "not configured",
                                    pl.errorMessage,
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = p.red.takeIf { pl.errorMessage != null } ?: p.textTertiary)
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
