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
import com.kyssta.hermeybeta.network.CronJob
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch

class CronViewModel(app: Application) : AndroidViewModel(app) {
    var jobs by mutableStateOf<List<CronJob>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var generation = -1

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && jobs.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = jobs.isEmpty()
            error = null
            try {
                jobs = SessionRepository.apiFor(conn).crons()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun setPaused(job: CronJob, paused: Boolean) {
        val id = job.id ?: return
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                if (paused) SessionRepository.apiFor(conn).pauseCron(id)
                else SessionRepository.apiFor(conn).resumeCron(id)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }
}

/** Cron — mirrors the desktop cron overlay (jobs, pause/resume). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronScreen() {
    val vm: CronViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cron", color = p.textPrimary) },
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
                vm.error != null && vm.jobs.isEmpty() -> ErrorState(
                    title = "Could not load jobs",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                vm.jobs.isEmpty() -> EmptyState(title = "No scheduled jobs", description = "Create one from the desktop or web dashboard.")
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.jobs, key = { it.id ?: "?" }) { job ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(job.name ?: "?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                        Text(job.schedule, fontSize = 12.sp, color = p.textTertiary)
                                    }
                                    if (job.isPaused()) {
                                        Text("paused", fontSize = 12.sp, color = p.yellow)
                                    }
                                    HermesButton(
                                        if (job.isPaused()) "Resume" else "Pause",
                                        onClick = { vm.setPaused(job, !job.isPaused()) },
                                        variant = HermesVariant.Text,
                                        size = HermesSize.Sm,
                                    )
                                }
                                job.prompt?.let {
                                    Text(it, fontSize = 13.sp, color = p.textSecondary, maxLines = 2)
                                }
                                val meta = listOfNotNull(
                                    job.lastStatus?.let { "last: $it" },
                                    job.state?.let { "state: $it" },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = p.textTertiary)
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
