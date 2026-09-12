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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import com.kyssta.hermeybeta.network.WebhookSub
import com.kyssta.hermeybeta.network.WebhooksState
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseWebhooks
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.MenuNavButton
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch
import org.json.JSONObject

private val DELIVER_OPTIONS = listOf("log", "telegram", "discord", "slack", "email", "github_comment")

class WebhooksViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf(WebhooksState())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var busy by mutableStateOf(false)
    private var generation = -1

    fun load(force: Boolean = false) {
        if (!force && generation == SessionRepository.generation.value && !loading) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val conn = SessionRepository.connection.value ?: throw IllegalStateException("No gateway selected")
                state = parseWebhooks(SessionRepository.apiFor(conn).webhooks())
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun toggle(sub: WebhookSub, onError: (String) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).setWebhookEnabled(sub.name, !sub.enabled)
                // Optimistic paint, backend wins on reload — desktop parity.
                state = state.copy(subscriptions = state.subscriptions.map {
                    if (it.name == sub.name) it.copy(enabled = !sub.enabled) else it
                })
                load(force = true)
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { busy = false }
        }
    }

    fun delete(name: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).deleteWebhook(name)
                load(force = true)
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { busy = false }
        }
    }

    fun create(body: JSONObject, onDone: (url: String?, secret: String?) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                val res = SessionRepository.apiFor(conn).createWebhook(body)
                onDone(res.optString("url").takeUnless { it.isBlank() }, res.optString("secret").takeUnless { it.isBlank() })
                load(force = true)
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { busy = false }
        }
    }

    fun enable(onError: (String) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                SessionRepository.apiFor(conn).enableWebhooks()
                load(force = true)
            } catch (e: Exception) { onError(gatewayErrorMessage(e)) }
            finally { busy = false }
        }
    }
}

/** Webhooks — desktop parity: receiver enable + subscription CRUD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreen(onMenu: () -> Unit = {}) {
    val vm: WebhooksViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes
    var toast by remember { mutableStateOf<String?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var createdUrl by remember { mutableStateOf<String?>(null) }
    var createdSecret by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conn) { vm.load(force = true) }

    val visible = remember(vm.state, vm.query) {
        vm.state.subscriptions.filter {
            vm.query.isBlank() || "${it.name} ${it.description} ${it.deliver}".contains(vm.query, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Webhooks", color = p.textPrimary) },
                navigationIcon = { MenuNavButton(onMenu) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                actions = {
                    HermesButton("New", onClick = { createdUrl = null; createdSecret = null; createOpen = true },
                        variant = HermesVariant.Secondary, size = HermesSize.Sm)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = HermesLayout.PAGE_INSET_X.dp)) {
            if (toast != null) {
                Text(toast!!, color = p.red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (!vm.state.enabled && !vm.loading) {
                ListRow(
                    label = "Receiver disabled",
                    description = "Enable the gateway webhook receiver to use subscriptions.",
                    action = { HermesButton("Enable", onClick = { vm.enable { toast = it } }, variant = HermesVariant.Secondary, size = HermesSize.Sm) },
                )
            }
            SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search webhooks")
            when {
                vm.loading -> Loader(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                vm.error != null && visible.isEmpty() -> ErrorState(
                    title = "Could not load webhooks", description = vm.error,
                    retryLabel = "Retry", onRetry = { vm.load(force = true) },
                )
                visible.isEmpty() -> EmptyState(
                    title = "No webhooks",
                    description = "Subscriptions hot-reload on the gateway.",
                    actionLabel = "New webhook",
                    onAction = { createOpen = true },
                )
                else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refreshing = true; vm.load(force = true) }) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.name }) { sub ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                                    .padding(vertical = 8.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sub.name, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(if (sub.enabled) "on" else "off", color = if (sub.enabled) p.green else p.textTertiary, fontSize = 12.sp)
                                }
                                if (sub.description != null) Text(sub.description, color = p.textSecondary, fontSize = 13.sp)
                                Text(
                                    listOfNotNull(sub.deliver, sub.events.takeUnless { it.isEmpty() }?.joinToString(",")).joinToString(" · "),
                                    color = p.textTertiary, fontSize = 12.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    HermesButton(if (sub.enabled) "Disable" else "Enable",
                                        onClick = { vm.toggle(sub) { toast = it } },
                                        variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !vm.busy)
                                    HermesButton("Delete",
                                        onClick = { pendingDelete = sub.name },
                                        variant = HermesVariant.Destructive, size = HermesSize.Sm, enabled = !vm.busy)
                                }
                            }
                            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    if (createOpen) CreateWebhookDialog(
        busy = vm.busy,
        createdUrl = createdUrl,
        createdSecret = createdSecret,
        onDismiss = { createOpen = false },
        onCreate = { body -> vm.create(body, onDone = { u, s -> createdUrl = u; createdSecret = s }, onError = { toast = it }) },
    )

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete webhook?") },
            text = { Text("Delete \"$name\"? This cannot be undone.") },
            confirmButton = { HermesButton("Delete", onClick = { vm.delete(name) { toast = it }; pendingDelete = null }, variant = HermesVariant.Destructive, size = HermesSize.Sm) },
            dismissButton = { HermesButton("Cancel", onClick = { pendingDelete = null }, variant = HermesVariant.Text, size = HermesSize.Sm) },
        )
    }
}

@Composable
private fun CreateWebhookDialog(
    busy: Boolean,
    createdUrl: String?,
    createdSecret: String?,
    onDismiss: () -> Unit,
    onCreate: (JSONObject) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var events by remember { mutableStateOf("") }
    var deliver by remember { mutableStateOf("log") }
    var prompt by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New webhook") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (err != null) Text(err!!, color = Hermes.red, fontSize = 13.sp)
                if (createdUrl != null || createdSecret != null) {
                    if (createdUrl != null) Text("URL: $createdUrl", fontSize = 13.sp, color = Hermes.textPrimary)
                    if (createdSecret != null) Text("Secret: $createdSecret", fontSize = 13.sp, color = Hermes.textPrimary)
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(events, { events = it }, label = { Text("Events (comma separated)") }, singleLine = true)
                OutlinedTextField(deliver, { deliver = it }, label = { Text("Deliver (${DELIVER_OPTIONS.joinToString("/")})") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt") })
                OutlinedTextField(skills, { skills = it }, label = { Text("Skills (comma separated)") }, singleLine = true)
            }
        },
        confirmButton = {
            HermesButton("Create", enabled = !busy, onClick = {
                if (name.isBlank()) { err = "Name is required"; return@HermesButton }
                onCreate(JSONObject()
                    .put("name", name.trim())
                    .put("description", description.trim().takeUnless { it.isBlank() } ?: JSONObject.NULL)
                    .put("events", events.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null } ?: JSONObject.NULL)
                    .put("deliver", deliver.trim().ifBlank { "log" })
                    .put("prompt", prompt.trim().takeUnless { it.isBlank() } ?: JSONObject.NULL)
                    .put("skills", skills.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null } ?: JSONObject.NULL))
            }, variant = HermesVariant.Default, size = HermesSize.Sm)
        },
        dismissButton = { HermesButton("Close", onClick = onDismiss, variant = HermesVariant.Text, size = HermesSize.Sm) },
    )
}
