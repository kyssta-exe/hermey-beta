package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.kyssta.hermeybeta.network.ArtifactRecord
import com.kyssta.hermeybeta.network.artifactKindFor
import com.kyssta.hermeybeta.network.artifactLabelFor
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

private val URL_FINDER = Regex("https?://[^\\s)\"']+")
private val FILE_FINDER = Regex("(^|[\\s:])((/[\\w.\\-]+)+\\.(png|jpe?g|gif|webp|pdf|txt|md|json|csv|log|mp4|mp3))", RegexOption.IGNORE_CASE)

class ArtifactsViewModel(app: Application) : AndroidViewModel(app) {
    var records by mutableStateOf<List<ArtifactRecord>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var filter by mutableStateOf("all")
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && records.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = records.isEmpty()
            error = null
            try {
                val conn = SessionRepository.connection.value ?: throw IllegalStateException("No gateway selected")
                val api = SessionRepository.apiFor(conn)
                val out = mutableListOf<ArtifactRecord>()
                val sessions = api.sessions()
                // ponytail: sequential scan caps gateway load; parallel fan-out if artifact scan gets slow.
                for (s in sessions.take(60)) {
                    val sid = s.stableId
                    if (sid.isBlank()) continue
                    val msgs = try { api.messages(sid, 100) } catch (_: Exception) { continue }
                    for (m in msgs) {
                        val text = m.content ?: continue
                        for (url in URL_FINDER.findAll(text)) {
                            val v = url.value.trimEnd('.', ',', ')')
                            val kind = artifactKindFor("url", v) ?: "link"
                            out += ArtifactRecord(sid, s.title, kind, artifactLabelFor(v), v, m.timestamp)
                        }
                        for (f in FILE_FINDER.findAll(text)) {
                            val v = f.groupValues[2]
                            val kind = artifactKindFor("path", v) ?: continue
                            out += ArtifactRecord(sid, s.title, kind, artifactLabelFor(v), v, m.timestamp)
                        }
                        // Tool outputs shaped as JSON: {"file_path": "..."} etc.
                        if (text.trimStart().startsWith("{")) {
                            try {
                                val o = JSONObject(text)
                                for (k in o.keys()) {
                                    val v = o.optString(k)
                                    if (v.isBlank()) continue
                                    val kind = artifactKindFor(k, v) ?: continue
                                    out += ArtifactRecord(sid, s.title, kind, artifactLabelFor(v), v, m.timestamp)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
                // Dedupe on href, newest first — mirrors desktop history behavior.
                records = out.distinctBy { it.href }.sortedByDescending { it.timestamp ?: 0.0 }
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** Artifacts — desktop parity: aggregated files/images/links across sessions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactsScreen(onOpenSession: (String) -> Unit) {
    val vm: ArtifactsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    val visible = remember(vm.records, vm.query, vm.filter) {
        vm.records.filter {
            (vm.filter == "all" || it.kind == vm.filter) &&
                (vm.query.isBlank() || "${it.label} ${it.href} ${it.sessionTitle}".contains(vm.query, true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifacts", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                val artifactOptions = listOf("all", "image", "file", "link")
                SegmentedControl(
                    options = artifactOptions,
                    selected = artifactOptions.indexOf(vm.filter).coerceAtLeast(0),
                    onSelect = { vm.filter = artifactOptions[it] },
                )
            }
            SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search artifacts")
            when {
                vm.loading -> Loader(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                vm.error != null && vm.records.isEmpty() -> ErrorState(
                    title = "Could not load artifacts",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                visible.isEmpty() -> EmptyState(
                    title = if (vm.records.isEmpty()) "No artifacts yet" else "No matches",
                    description = "Files, images and links the agent produced appear here.",
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible.take(300), key = { it.href }) { a ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onOpenSession(a.sessionId) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(a.label.ifBlank { a.href }, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${a.kind} · ${a.sessionTitle ?: a.sessionId.take(8)}",
                                    color = p.textTertiary, fontSize = 12.sp,
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
