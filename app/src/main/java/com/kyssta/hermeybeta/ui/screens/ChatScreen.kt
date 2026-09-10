package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.ChatMessage
import com.kyssta.hermeybeta.network.GatewayWs
import com.kyssta.hermeybeta.network.ModelInfo
import com.kyssta.hermeybeta.network.WsFrame
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Transcript rows. Assistant text streams into one row until finalized. */
sealed interface UiMsg {
    data class User(val text: String) : UiMsg
    data class Assistant(var text: String, var done: Boolean) : UiMsg
    data class Tool(val name: String, var summary: String, var done: Boolean) : UiMsg
}

data class ClarifyState(val id: String, val question: String, val options: List<String>)
data class ApprovalState(val id: String, val command: String, val detail: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val messages = mutableStateListOf<UiMsg>()
    val toolRows = mutableStateMapOf<String, UiMsg.Tool>()
    var sessionId by mutableStateOf("")
    var input by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var socketState by mutableStateOf("connecting")
    var error by mutableStateOf<String?>(null)
    var model: ModelInfo? by mutableStateOf(null)
    var modelOptions by mutableStateOf<List<Pair<String, List<String>>>>(emptyList())
    var clarify by mutableStateOf<ClarifyState?>(null)
    var approval by mutableStateOf<ApprovalState?>(null)

    private var ws: GatewayWs? = null
    private var loop: Job? = null
    private var pendingSubmitId = -1
    private var generation = -1

    fun start(initialSessionId: String) {
        val conn = SessionRepository.connection.value ?: run {
            error = "No gateway selected"
            return
        }
        if (generation == SessionRepository.generation.value && ws != null) return
        generation = SessionRepository.generation.value
        stop()
        sessionId = initialSessionId
        socketState = "connecting"
        error = null
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                try {
                    model = api.modelInfo()
                } catch (_: Exception) {
                }
                try {
                    modelOptions = api.modelOptions()
                } catch (_: Exception) {
                }
                if (sessionId.isNotBlank()) {
                    api.messages(sessionId).forEach { m -> addHistory(m) }
                }
                val ticket = api.wsTicket()
                val socket = GatewayWs.withTicket(conn.baseUrl, ticket)
                ws = socket
                SessionRepository.attachSocket(socket)
                loop = viewModelScope.launch { eventLoop(socket) }
                socket.connect {}
                socketState = "open"
                if (sessionId.isBlank()) {
                    val id = socket.nextId()
                    socket.rpc(id, "session.create")
                    awaitSessionId()?.let { sessionId = it }
                }
            } catch (e: Exception) {
                socketState = "closed"
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun send() {
        val text = input.trim()
        val socket = ws
        if (text.isBlank() || socket == null || streaming || sessionId.isBlank()) return
        input = ""
        error = null
        messages += UiMsg.User(text)
        val row = UiMsg.Assistant("", done = false)
        messages += row
        streaming = true
        val id = socket.nextId()
        pendingSubmitId = id
        socket.rpc(
            id, "prompt.submit",
            JSONObject().put("message", text).put("session_id", sessionId),
        )
    }

    fun interrupt() {
        val socket = ws ?: return
        socket.rpc(socket.nextId(), "session.interrupt", JSONObject().put("session_id", sessionId))
    }

    fun answerClarify(response: String) {
        val c = clarify ?: return
        clarify = null
        ws?.rpc(
            ws!!.nextId(), "clarify.respond",
            JSONObject().put("id", c.id).put("response", response),
        )
    }

    fun answerApproval(approved: Boolean) {
        val a = approval ?: return
        approval = null
        ws?.rpc(
            ws!!.nextId(), "approval.respond",
            JSONObject().put("id", a.id).put("approved", approved),
        )
    }

    fun setModel(modelName: String) {
        val conn = SessionRepository.connection.value ?: return
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).setModel(modelName)
                model = SessionRepository.apiFor(conn).modelInfo()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        ws?.close()
        ws = null
        streaming = false
    }

    private fun addHistory(m: ChatMessage) {
        val text = m.content ?: return
        when (m.role) {
            "user" -> messages += UiMsg.User(text)
            "assistant" -> messages += UiMsg.Assistant(text, done = true)
            "tool" -> messages += UiMsg.Tool(m.toolName ?: "tool", text.take(200), done = true)
            else -> messages += UiMsg.Assistant(text, done = true)
        }
    }

    private suspend fun eventLoop(socket: GatewayWs) {
        socket.frames.collect { frame ->
            when (frame) {
                is WsFrame.Event -> onEvent(frame.type, frame.payload)
                is WsFrame.Reply -> {
                    frame.error?.let { error = it }
                    if (frame.id == pendingSubmitId) {
                        // The RPC may resolve before message.complete fires —
                        // finalize leftovers after a grace window.
                        pendingSubmitId = -1
                        viewModelScope.launch {
                            delay(500)
                            if (streaming) finalizeStream()
                        }
                    }
                }
                is WsFrame.Failure -> {
                    socketState = "closed"
                    if (streaming) finalizeStream()
                    error = frame.message
                }
            }
        }
    }

    private fun onEvent(type: String, p: JSONObject) {
        when (type) {
            "message.delta" -> {
                val chunk = p.optString("text")
                    .ifBlank { p.optString("content") }
                    .ifBlank { p.optString("delta") }
                if (chunk.isNotBlank()) {
                    val row = messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant
                    if (row != null) row.text += chunk
                    else messages += UiMsg.Assistant(chunk, done = false)
                    streaming = true
                }
            }
            "message.complete" -> finalizeStream()
            "tool.start" -> {
                val id = p.optString("id").ifBlank { p.optString("tool_id") }.ifBlank { "t${toolRows.size}" }
                val name = p.optString("name").ifBlank { p.optString("tool") }.ifBlank { "tool" }
                val row = UiMsg.Tool(name, "running…", done = false)
                toolRows[id] = row
                messages += row
            }
            "tool.progress" -> {
                val id = p.optString("id").ifBlank { p.optString("tool_id") }
                val update = p.optString("summary").ifBlank { p.optString("text") }
                if (update.isNotBlank()) toolRows[id]?.summary = update
            }
            "tool.complete" -> {
                val id = p.optString("id").ifBlank { p.optString("tool_id") }
                val result = p.optString("summary").ifBlank { p.optString("result") }
                toolRows[id]?.let {
                    it.done = true
                    if (result.isNotBlank()) it.summary = result.take(300)
                }
            }
            "clarify.request" -> {
                val opts = p.optJSONArray("options")?.let { a -> List(a.length()) { i -> a.optString(i) } }.orEmpty()
                clarify = ClarifyState(
                    id = p.optString("id").ifBlank { "clarify" },
                    question = p.optString("question").ifBlank { p.optString("prompt") }.ifBlank { p.optString("text") },
                    options = opts,
                )
            }
            "approval.request" -> {
                approval = ApprovalState(
                    id = p.optString("id").ifBlank { "approval" },
                    command = p.optString("command").ifBlank { p.optString("tool") }.ifBlank { "command" },
                    detail = p.optString("description").ifBlank { p.optString("detail") },
                )
            }
            "gateway.ready" -> socketState = "open"
            else -> Unit // unknown future events are ignored, never crash
        }
    }

    private fun finalizeStream() {
        streaming = false
        (messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant)?.done = true
    }

    /** After session.create, resolve the new id via the session list. */
    private suspend fun awaitSessionId(): String? {
        repeat(50) {
            delay(300)
            try {
                val conn = SessionRepository.connection.value ?: return null
                val id = SessionRepository.apiFor(conn).sessions().firstOrNull()?.stableId
                if (!id.isNullOrBlank()) return id
            } catch (_: Exception) {
            }
        }
        return null
    }
}

/** Chat — the home surface. Transcript + composer stay primary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(sessionId: String, onNavigateBack: (() -> Unit)? = null) {
    val vm: ChatViewModel = viewModel()
    val p = Hermes
    val listState = rememberLazyListState()
    var showModels by mutableStateOf(false)

    LaunchedEffect(sessionId) { vm.start(sessionId) }
    DisposableEffect(Unit) { onDispose { /* socket persists per desktop panes */ } }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            vm.model?.model ?: "Chat",
                            color = p.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            when (vm.socketState) {
                                "open" -> if (vm.streaming) "responding…" else "connected"
                                else -> vm.socketState
                            },
                            color = p.textTertiary,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        HermesButton("Back", onClick = onNavigateBack, variant = HermesVariant.Text, size = HermesSize.Sm)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                actions = {
                    if (vm.streaming) {
                        HermesButton("Stop", onClick = { vm.interrupt() }, variant = HermesVariant.Text, size = HermesSize.Sm)
                    }
                    if (vm.modelOptions.isNotEmpty()) {
                        HermesButton("Model", onClick = { showModels = true }, variant = HermesVariant.Text, size = HermesSize.Sm)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                vm.error != null && vm.messages.isEmpty() -> ErrorState(
                    title = "Could not open chat",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.start(sessionId) },
                )
                vm.messages.isEmpty() && vm.socketState == "connecting" -> Loader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                )
                vm.messages.isEmpty() -> EmptyState(
                    title = "New chat",
                    description = "Ask anything — tools, files and memory live on the gateway.",
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
                ) {
                    // Index keys: assistant rows mutate in place while streaming.
                    items(vm.messages.size) { i -> MessageRow(vm.messages[i]) }
                }
            }
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HermesLayout.PAGE_INSET_X.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Hermes…") },
                    minLines = 1,
                    maxLines = 6,
                )
                HermesButton(
                    "Send",
                    onClick = { vm.send() },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = vm.input.isNotBlank() && !vm.streaming && vm.sessionId.isNotBlank(),
                )
            }
        }
    }

    vm.clarify?.let { c ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Hermes needs input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.question)
                    c.options.forEach { opt ->
                        HermesButton(opt, onClick = { vm.answerClarify(opt) }, variant = HermesVariant.Secondary, size = HermesSize.Sm, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (c.options.isEmpty()) HermesButton("OK", onClick = { vm.answerClarify("proceed") })
            },
        )
    }
    vm.approval?.let { a ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Approve command?") },
            text = {
                Column {
                    Text(a.command, fontFamily = FontFamily.Monospace)
                    if (a.detail.isNotBlank()) Text(a.detail)
                }
            },
            confirmButton = { HermesButton("Approve", onClick = { vm.answerApproval(true) }) },
            dismissButton = {
                HermesButton("Deny", onClick = { vm.answerApproval(false) }, variant = HermesVariant.Destructive)
            },
        )
    }
    if (showModels) {
        ModelSheet(
            current = vm.model?.model,
            options = vm.modelOptions,
            onPick = { vm.setModel(it); showModels = false },
            onDismiss = { showModels = false },
        )
    }
}

@Composable
private fun MessageRow(m: UiMsg) {
    val p = Hermes
    when (m) {
        is UiMsg.User -> Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = p.accent, shape = RoundedCornerShape(16.dp)) {
                Text(m.text, color = p.onAccent, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        is UiMsg.Assistant -> Text(
            m.text.ifBlank { "…" },
            color = p.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        is UiMsg.Tool -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("▸ ${m.name}", color = p.purple, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(m.summary, color = p.textTertiary, fontSize = 13.sp, maxLines = 4)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    current: String?,
    options: List<Pair<String, List<String>>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Hermes
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.padding(horizontal = HermesLayout.PAGE_INSET_X.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Model", fontWeight = FontWeight.SemiBold, color = p.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
            options.forEach { (provider, models) ->
                Text(provider, fontSize = 12.sp, color = p.textTertiary)
                models.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            m,
                            color = p.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (m == current) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (m != current) {
                            HermesButton("Use", onClick = { onPick(m) }, variant = HermesVariant.Text, size = HermesSize.Sm)
                        }
                    }
                }
            }
        }
    }
}
