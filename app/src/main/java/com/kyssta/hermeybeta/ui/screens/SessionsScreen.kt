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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.SessionSummary
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    private var generation = -1

    fun load(conn: com.kyssta.hermeybeta.auth.ServerConnection, force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && sessions.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = sessions.isEmpty()
            error = null
            try {
                sessions = SessionRepository.apiFor(conn).sessions()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun refresh(conn: com.kyssta.hermeybeta.auth.ServerConnection) {
        refreshing = true
        generation = -1
        load(conn, force = true)
    }

    fun togglePin(conn: com.kyssta.hermeybeta.auth.ServerConnection, s: SessionSummary) {
        val id = s.stableId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).updateSession(id, JSONObject().put("pinned", s.pinned != true))
                load(conn, force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun archive(conn: com.kyssta.hermeybeta.auth.ServerConnection, s: SessionSummary, archived: Boolean) {
        val id = s.stableId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).updateSession(id, JSONObject().put("archived", archived))
                load(conn, force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun delete(conn: com.kyssta.hermeybeta.auth.ServerConnection, s: SessionSummary) {
        val id = s.stableId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).deleteSession(id)
                load(conn, force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }
}

/** Session list — the mobile form of the desktop chat sidebar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(onOpenChat: (String) -> Unit, onNewChat: () -> Unit) {
    val vm: SessionsViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { conn?.let { vm.load(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                actions = {
                    HermesButton("New", onClick = onNewChat, variant = HermesVariant.Ghost, size = HermesSize.Sm)
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
            // Empty lists hide their search field (desktop rule).
            if (vm.sessions.isNotEmpty()) {
                SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search sessions")
            }
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null -> ErrorState(
                    title = "Could not load sessions",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { conn?.let { vm.refresh(it) } },
                )
                else -> {
                    val rows = vm.sessions.filter {
                        vm.query.isBlank() ||
                            (it.title ?: "").contains(vm.query, ignoreCase = true) ||
                            (it.preview ?: "").contains(vm.query, ignoreCase = true)
                    }
                    if (rows.isEmpty()) {
                        EmptyState(
                            title = if (vm.query.isBlank()) "No sessions yet" else "No matches",
                            description = if (vm.query.isBlank()) "Start a new chat to begin." else null,
                            actionLabel = if (vm.query.isBlank()) "New chat" else null,
                            onAction = if (vm.query.isBlank()) onNewChat else null,
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { conn?.let { vm.refresh(it) } },
                        ) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(rows, key = { it.stableId.ifBlank { it.title ?: "?" } }) { s ->
                                    SessionRow(
                                        s = s,
                                        onClick = { onOpenChat(s.stableId) },
                                        onPin = { conn?.let { vm.togglePin(it, s) } },
                                        onArchive = { conn?.let { vm.archive(it, s, s.archived != true) } },
                                        onDelete = { conn?.let { vm.delete(it, s) } },
                                    )
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

@Composable
private fun SessionRow(
    s: SessionSummary,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = Hermes
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (s.title ?: s.preview ?: "Untitled").take(120),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = p.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            HermesButton(
                if (expanded) "Hide" else "More",
                onClick = { expanded = !expanded },
                variant = HermesVariant.Text,
                size = HermesSize.Sm,
            )
        }
        val sub = listOfNotNull(
            s.model,
            s.messageCount?.let { "$it msgs" },
            if (s.pinned == true) "pinned" else null,
            if (s.archived == true) "archived" else null,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = p.textTertiary)
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HermesButton(if (s.pinned == true) "Unpin" else "Pin", onClick = onPin, variant = HermesVariant.Text, size = HermesSize.Sm)
                HermesButton(
                    if (s.archived == true) "Unarchive" else "Archive",
                    onClick = onArchive,
                    variant = HermesVariant.Text,
                    size = HermesSize.Sm,
                )
                HermesButton("Delete", onClick = onDelete, variant = HermesVariant.Text, size = HermesSize.Sm)
            }
        }
    }
}
