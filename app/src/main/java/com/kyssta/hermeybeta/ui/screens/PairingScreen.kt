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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.PairUser
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parsePairing
import com.kyssta.hermeybeta.session.SessionRepository
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

class PairingViewModel(app: Application) : AndroidViewModel(app) {
    var pending by mutableStateOf<List<PairUser>>(emptyList())
    var approved by mutableStateOf<List<PairUser>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busyId by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && (pending.isNotEmpty() || approved.isNotEmpty())) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = pending.isEmpty() && approved.isEmpty()
            error = null
            try {
                val (p, a) = parsePairing(SessionRepository.apiFor(conn).pairing())
                pending = p
                approved = a
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun approve(user: PairUser) {
        val code = user.code ?: return
        busyId = user.userId
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).approvePairing(user.platform, code)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busyId = null
            }
        }
    }

    fun revoke(user: PairUser) {
        busyId = user.userId
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).revokePairing(user.platform, user.userId)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busyId = null
            }
        }
    }

    fun clearPending() {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).clearPendingPairing()
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }
}

/** Pairing — approve pending device/channel pairing requests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onMenu: () -> Unit = {}) {
    val vm: PairingViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pairing", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                actions = {
                    if (vm.pending.isNotEmpty()) {
                        HermesButton("Clear", onClick = { vm.clearPending() }, variant = HermesVariant.Text, size = HermesSize.Sm)
                    }
                },
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
                vm.error != null && vm.pending.isEmpty() && vm.approved.isEmpty() -> ErrorState(
                    title = "Could not load pairing",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                vm.pending.isEmpty() && vm.approved.isEmpty() -> EmptyState(
                    title = "Nothing to pair",
                    description = "New device requests appear here.",
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (vm.pending.isNotEmpty()) {
                            item {
                                Text("Pending", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.padding(vertical = 8.dp))
                            }
                            items(vm.pending, key = { it.platform + it.userId }) { u ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(u.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                        val sub = listOfNotNull(
                                            u.platform.takeIf { it.isNotBlank() },
                                            u.code?.let { "code $it" },
                                            u.ageMinutes?.let { "$it min ago" },
                                        ).joinToString(" · ")
                                        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = p.textTertiary)
                                    }
                                    if (u.code != null) {
                                        HermesButton(
                                            if (vm.busyId == u.userId) "…" else "Approve",
                                            onClick = { vm.approve(u) },
                                            variant = HermesVariant.Secondary,
                                            size = HermesSize.Sm,
                                            enabled = vm.busyId == null,
                                        )
                                    }
                                }
                                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                            }
                        }
                        if (vm.approved.isNotEmpty()) {
                            item {
                                Text("Approved", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.padding(vertical = 8.dp))
                            }
                            items(vm.approved, key = { "a" + it.platform + it.userId }) { u ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(u.label, fontSize = 15.sp, color = p.textPrimary)
                                        if (u.platform.isNotBlank()) Text(u.platform, fontSize = 12.sp, color = p.textTertiary)
                                    }
                                    HermesButton(
                                        "Revoke",
                                        onClick = { vm.revoke(u) },
                                        variant = HermesVariant.Text,
                                        size = HermesSize.Sm,
                                        enabled = vm.busyId == null,
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
