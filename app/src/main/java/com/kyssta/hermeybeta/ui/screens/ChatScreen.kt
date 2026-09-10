package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.ui.theme.HermesMono
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.ChatMessage
import com.kyssta.hermeybeta.network.GatewayWs
import com.kyssta.hermeybeta.network.ModelInfo
import com.kyssta.hermeybeta.network.WsFrame
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.CineCard
import com.kyssta.hermeybeta.ui.components.CineHero
import com.kyssta.hermeybeta.ui.components.CineSub
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesDialog
import com.kyssta.hermeybeta.ui.components.HermesSheet
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesSans
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Transcript rows. Assistant text streams into one row until finalized. */
sealed interface UiMsg {
    data class User(val text: String) : UiMsg
    data class Assistant(var text: String, var done: Boolean) : UiMsg
    data class Tool(val name: String, var summary: String, var done: Boolean) : UiMsg
}

data class ClarifyState(val requestId: String, val question: String, val options: List<String>)
data class ApprovalState(val command: String, val detail: String, val choices: List<String>)
data class SlashItem(val text: String, val display: String)

/** Compact "12 calls · 45.2k tokens" label for the chat header. */
fun formatUsage(calls: Int, totalTokens: Long): String {
    val t = when {
        totalTokens >= 1_000_000 -> "%.1fm".format(totalTokens / 1_000_000.0)
        totalTokens >= 1_000 -> "%.1fk".format(totalTokens / 1_000.0)
        else -> totalTokens.toString()
    }
    return "$calls calls · $t tokens"
}

private fun String?.ifNullOrBlank(fallback: () -> String?): String? =
    if (this.isNullOrBlank()) fallback() else this

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
    var slashItems by mutableStateOf<List<SlashItem>>(emptyList())
    var usageLabel by mutableStateOf<String?>(null)
    var notFound by mutableStateOf(false)
    var thinking by mutableStateOf(false)

    private var ws: GatewayWs? = null
    private var loop: Job? = null
    private var slashJob: Job? = null
    private var pendingSubmitId = -1
    private var generation = -1
    private val pendingReplies = mutableMapOf<Int, CompletableDeferred<JSONObject?>>()

    /** Request/response over the shared event flow. Null on timeout. */
    private suspend fun awaitReply(id: Int, timeoutMs: Long = 10_000): JSONObject? {
        val d = CompletableDeferred<JSONObject?>()
        pendingReplies[id] = d
        try {
            return withTimeoutOrNull(timeoutMs) { d.await() }
        } finally {
            pendingReplies.remove(id)
        }
    }

    private fun call(method: String, params: JSONObject, timeoutMs: Long = 10_000, onReply: (JSONObject?) -> Unit = {}) {
        val socket = ws ?: return
        val id = socket.nextId()
        if (!socket.rpc(id, method, params)) return
        viewModelScope.launch {
            onReply(awaitReply(id, timeoutMs))
        }
    }

    fun start(initialSessionId: String) {
        val conn = SessionRepository.connection.value ?: run {
            error = "No gateway selected"
            return
        }
        // Another screen may have attached a fresh socket; adopt it instead of
        // talking on a closed one.
        if (ws != null && SessionRepository.ws !== ws) stop()
        if (generation == SessionRepository.generation.value && ws != null && initialSessionId == sessionId) return
        generation = SessionRepository.generation.value
        stop()
        messages.clear()
        toolRows.clear()
        clarify = null
        approval = null
        slashItems = emptyList()
        usageLabel = null
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
                } else {
                    openSession(socket, sessionId)
                }
                refreshUsage()
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
        slashItems = emptyList()
        error = null
        thinking = false
        if (text.startsWith("/")) {
            sendSlash(text)
            return
        }
        messages += UiMsg.User(text)
        val row = UiMsg.Assistant("", done = false)
        messages += row
        streaming = true
        val id = socket.nextId()
        pendingSubmitId = id
        socket.rpc(
            id, "prompt.submit",
            JSONObject().put("text", text).put("session_id", sessionId),
        )
    }

    /** Slash command via the gateway registry (same as desktop command palette). */
    private fun sendSlash(command: String) {
        messages += UiMsg.User(command)
        val row = UiMsg.Assistant("", done = false)
        messages += row
        streaming = true
        call(
            "slash.exec",
            JSONObject().put("command", command).put("session_id", sessionId),
            timeoutMs = 60_000,
        ) { res ->
            row.text = res?.optString("text").ifNullOrBlank { res?.toString()?.take(2000) } ?: "(no output)"
            row.done = true
            streaming = false
            refreshUsage()
        }
    }

    /** Debounced server-side slash completion while typing "/…". */
    fun onInputChanged(v: String) {
        input = v
        slashJob?.cancel()
        val socket = ws
        if (!v.startsWith("/") || v.contains(" ") || v.contains("\n") || socket == null) {
            slashItems = emptyList()
            return
        }
        slashJob = viewModelScope.launch {
            delay(250)
            val id = socket.nextId()
            if (!socket.rpc(id, "complete.slash", JSONObject().put("text", v))) return@launch
            val res = awaitReply(id, 5_000) ?: return@launch
            val arr = res.optJSONArray("items") ?: return@launch
            slashItems = List(arr.length()) { i ->
                val o = arr.optJSONObject(i) ?: JSONObject()
                val t = o.optString("text")
                SlashItem(t, o.optString("display").ifBlank { t })
            }.filter { it.text.isNotBlank() }.take(8)
        }
    }

    /** Attach gallery/file bytes via the remote-client upload path. */
    fun attachFile(name: String, mime: String, bytes: ByteArray) {
        val socket = ws
        if (socket == null || sessionId.isBlank()) {
            error = "Connect first, then attach"
            return
        }
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val isImage = mime.startsWith("image/")
        val method = if (isImage) "image.attach_bytes" else "file.attach"
        val params = JSONObject().put("session_id", sessionId).put("filename", name).put("name", name)
        if (isImage) params.put("content_base64", b64)
        else params.put("data_url", "data:$mime;base64,$b64")
        messages += UiMsg.Tool("attach", "uploading $name…", done = false)
        val row = messages.last() as UiMsg.Tool
        call(method, params, timeoutMs = 60_000) { res ->
            row.done = true
            row.summary = if (res != null) "attached $name" else "upload failed"
            if (res == null) error = "Attachment upload timed out"
        }
    }

    /** Context meter for the header (desktop context-usage panel, compact). */
    fun refreshUsage() {
        if (sessionId.isBlank()) return
        call("session.usage", JSONObject().put("session_id", sessionId)) { res ->
            if (res == null) return@call
            val calls = res.optInt("calls", -1)
            val total = res.optLong("total", -1)
            usageLabel = if (calls >= 0 && total >= 0) formatUsage(calls, total) else null
        }
    }

    fun renameSession(title: String) {
        if (sessionId.isBlank() || title.isBlank()) return
        call("session.title", JSONObject().put("session_id", sessionId).put("title", title))
    }

    fun compressSession() {
        if (sessionId.isBlank()) return
        messages += UiMsg.Tool("compress", "compressing context…", done = false)
        val row = messages.last() as UiMsg.Tool
        call("session.compress", JSONObject().put("session_id", sessionId), timeoutMs = 120_000) { res ->
            row.done = true
            row.summary = if (res != null) "context compressed" else "compress failed"
            refreshUsage()
        }
    }

    fun branchSession(name: String, onBranched: (String) -> Unit) {
        if (sessionId.isBlank()) return
        call("session.branch", JSONObject().put("session_id", sessionId).put("name", name)) { res ->
            val nid = res?.optString("session_id").ifNullOrBlank { res?.optString("id") }
            if (!nid.isNullOrBlank()) onBranched(nid)
            else error = "Branch failed"
        }
    }

    fun closeSession(onClosed: () -> Unit) {
        if (sessionId.isBlank()) {
            onClosed()
            return
        }
        call("session.close", JSONObject().put("session_id", sessionId)) { onClosed() }
    }

    fun interrupt() {
        val socket = ws ?: return
        socket.rpc(socket.nextId(), "session.interrupt", JSONObject().put("session_id", sessionId))
    }

    fun answerClarify(response: String) {
        val c = clarify ?: return
        clarify = null
        val socket = ws ?: return
        // Verified: clarify.respond takes {request_id, answer}.
        socket.rpc(
            socket.nextId(), "clarify.respond",
            JSONObject().put("request_id", c.requestId).put("answer", response),
        )
    }

    fun answerApproval(choice: String) {
        approval = null
        val socket = ws ?: return
        // Verified: approval.respond is session-scoped and takes {choice, all}.
        socket.rpc(
            socket.nextId(), "approval.respond",
            JSONObject().put("choice", choice).put("all", false),
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

    /**
     * Authoritative open: session.resume binds the live session (cold resume,
     * compression chains) and returns history. REST transcript is the fallback
     * rung; both missing means the session is gone (not a retry loop).
     */
    private suspend fun openSession(socket: GatewayWs, id: String) {
        val rid = socket.nextId()
        if (socket.rpc(rid, "session.resume", JSONObject().put("session_id", id))) {
            val res = awaitReply(rid, 30_000)
            if (res != null) {
                res.optString("session_id").takeUnless { it.isBlank() }?.let { sessionId = it }
                val msgs = res.optJSONArray("messages")
                if (msgs != null) {
                    for (i in 0 until msgs.length()) {
                        addHistoryJson(msgs.optJSONObject(i) ?: JSONObject())
                    }
                    return
                }
            }
        }
        // Fallback rung: REST transcript.
        try {
            val conn = SessionRepository.connection.value ?: return
            SessionRepository.apiFor(conn).messages(id).forEach { m -> addHistory(m) }
        } catch (e: Exception) {
            if (e is com.kyssta.hermeybeta.network.GatewayHttpException && e.code == 404) {
                notFound = true
            } else {
                error = gatewayErrorMessage(e)
            }
        }
    }

    private fun addHistoryJson(o: JSONObject) {
        val text = o.optString("content").ifBlank { o.optString("text") }
        if (text.isBlank()) return
        val role = o.optString("role")
        when (role) {
            "user" -> messages += UiMsg.User(text)
            "assistant" -> messages += UiMsg.Assistant(text, done = true)
            "tool" -> messages += UiMsg.Tool(o.optString("tool_name").ifBlank { "tool" }, text.take(200), done = true)
            else -> messages += UiMsg.Assistant(text, done = true)
        }
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
                    // Tracked request/response pairs handle their own errors;
                    // only untracked replies (prompt.submit path) surface here.
                    val tracked = pendingReplies.remove(frame.id)?.complete(frame.result) != null
                    if (!tracked) frame.error?.let { error = it }
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
                    thinking = false
                    val row = messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant
                    if (row != null) row.text += chunk
                    else messages += UiMsg.Assistant(chunk, done = false)
                    streaming = true
                }
            }
            "message.start" -> streaming = true
            "thinking.delta", "reasoning.delta", "reasoning.available" -> {
                if (p.optString("text").isNotBlank()) thinking = true
                streaming = true
            }
            "error" -> {
                val detail = p.optString("message").ifBlank { "turn failed" }
                failTurn(detail.take(500))
            }
            "message.complete" -> {
                val text = p.optString("text")
                val row = messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant
                if (row != null && row.text.isBlank() && text.isNotBlank()) row.text = text
                finalizeStream()
            }
            "tool.start" -> {
                val id = p.optString("id").ifBlank { p.optString("tool_id") }.ifBlank { "t${toolRows.size}" }
                val name = p.optString("name").ifBlank { p.optString("tool") }.ifBlank { "tool" }
                val preview = p.optString("preview").ifBlank { p.optString("text") }
                val row = UiMsg.Tool(name, preview.ifBlank { "running…" }, done = false)
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
                    .map { it.toString() }.filter { it.isNotBlank() }
                clarify = ClarifyState(
                    requestId = p.optString("request_id"),
                    question = p.optString("question").ifBlank { p.optString("prompt") }.ifBlank { p.optString("text") },
                    options = opts,
                )
            }
            "approval.request" -> {
                val choices = p.optJSONArray("choices")?.let { a -> List(a.length()) { i -> a.optString(i) } }.orEmpty()
                    .map { it.toString() }.filter { it.isNotBlank() }
                approval = ApprovalState(
                    command = p.optString("command").ifBlank { p.optString("tool") }.ifBlank { "command" },
                    detail = p.optString("description").ifBlank { p.optString("detail") },
                    choices = choices,
                )
            }
            "gateway.ready" -> socketState = "open"
            else -> Unit // unknown future events are ignored, never crash
        }
    }

    private fun finalizeStream() {
        streaming = false
        thinking = false
        (messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant)?.done = true
        refreshUsage()
    }

    /** Terminal turn failure — never strand an eternal "…". */
    private fun failTurn(detail: String) {
        thinking = false
        val row = messages.lastOrNull { it is UiMsg.Assistant && !it.done } as? UiMsg.Assistant
        if (row != null) {
            if (row.text.isBlank()) row.text = "Error: $detail"
            row.done = true
        } else {
            error = detail
        }
        streaming = false
        refreshUsage()
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
fun ChatScreen(
    sessionId: String,
    onNavigateBack: (() -> Unit)? = null,
    onSessionClosed: (() -> Unit)? = null,
    onBranched: ((String) -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    onOpenSessions: (() -> Unit)? = null,
) {
    val vm: ChatViewModel = viewModel()
    val p = Hermes
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var showModels by mutableStateOf(false)
    var showSessionMenu by mutableStateOf(false)

    // System pickers need no new dependency (androidx.activity result APIs).
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val name = "image-${System.currentTimeMillis()}.jpg"
                vm.attachFile(name, "image/jpeg", bytes)
            }
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.size <= 10 * 1024 * 1024) {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfter(':') ?: "file"
                vm.attachFile(name, ctx.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
            }
        }
    }
    // Voice input via the system recognizer — no dependency, no audio stored.
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) vm.onInputChanged((vm.input + " " + heard).trim())
    }

    LaunchedEffect(sessionId) { vm.start(sessionId) }
    DisposableEffect(Unit) { onDispose { /* socket persists per desktop panes */ } }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showModels = true },
                    ) {
                        Text(
                            vm.model?.model ?: "Chat",
                            color = p.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            vm.usageLabel ?: when (vm.socketState) {
                                "open" -> when {
                                    vm.thinking -> "thinking…"
                                    vm.streaming -> "responding…"
                                    else -> "connected"
                                }
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
                    HermesButton("Session", onClick = { showSessionMenu = true }, variant = HermesVariant.Text, size = HermesSize.Sm)
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
                vm.notFound -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Session not found", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                        Text(
                            "It may have been deleted or rotated elsewhere.",
                            fontSize = 13.sp,
                            color = p.textSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onNewChat != null) {
                                HermesButton("New chat", onClick = onNewChat, variant = HermesVariant.Secondary, size = HermesSize.Sm)
                            }
                            if (onOpenSessions != null) {
                                HermesButton("Sessions", onClick = onOpenSessions, variant = HermesVariant.Text, size = HermesSize.Sm)
                            }
                        }
                    }
                }
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
                vm.messages.isEmpty() -> NewChatEmptyState()
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
            // The composer only shows when a message could actually send.
            if (!vm.notFound && !(vm.messages.isEmpty() && vm.error != null)) {
            HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            // Server-side slash completion (desktop "/" menu, compact).
            if (vm.slashItems.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
                ) {
                    vm.slashItems.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    vm.onInputChanged(item.text + " ")
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(item.display, fontSize = 14.sp, color = p.textPrimary)
                        }
                        HorizontalDivider(color = p.strokeQuaternary, thickness = 0.5.dp)
                    }
                }
                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HermesLayout.PAGE_INSET_X.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.card)
                    .border(1.dp, p.strokePrimary, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = { vm.onInputChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("What are we building?") },
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HermesButton(
                        "+ Photo",
                        onClick = {
                            pickImage.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        variant = HermesVariant.Text,
                        size = HermesSize.Sm,
                    )
                    HermesButton(
                        "File",
                        onClick = { pickFile.launch(arrayOf("*/*")) },
                        variant = HermesVariant.Text,
                        size = HermesSize.Sm,
                    )
                    HermesButton(
                        "Voice",
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            voiceInput.launch(intent)
                        },
                        variant = HermesVariant.Text,
                        size = HermesSize.Sm,
                    )
                }
            }
            }
        }
    }

    vm.clarify?.let { c ->
        var customAnswer by remember(c.requestId) { mutableStateOf("") }
        HermesDialog(
            onDismissRequest = {},
            title = { Text("Hermes needs input") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.question)
                    c.options.forEach { opt ->
                        HermesButton(opt, onClick = { vm.answerClarify(opt) }, variant = HermesVariant.Secondary, size = HermesSize.Sm, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = customAnswer,
                        onValueChange = { customAnswer = it },
                        label = { Text("Or type an answer") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                HermesButton("Send", onClick = { vm.answerClarify(customAnswer.ifBlank { "proceed" }) }, enabled = true)
            },
        )
    }
    vm.approval?.let { a ->
        HermesDialog(
            onDismissRequest = {},
            title = { Text("Approve command?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.command, fontFamily = HermesMono)
                    if (a.detail.isNotBlank()) Text(a.detail)
                    val choices = a.choices.ifEmpty { listOf("once", "deny") }
                    choices.forEach { choice ->
                        HermesButton(
                            choice.replaceFirstChar { it.uppercase() },
                            onClick = { vm.answerApproval(choice) },
                            variant = if (choice == "deny") HermesVariant.Destructive else HermesVariant.Secondary,
                            size = HermesSize.Sm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
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
    if (showSessionMenu) {
        SessionMenuSheet(
            onRename = { vm.renameSession(it); showSessionMenu = false },
            onCompress = { vm.compressSession(); showSessionMenu = false },
            onBranch = { name ->
                vm.branchSession(name) { nid ->
                    showSessionMenu = false
                    onBranched?.invoke(nid)
                }
            },
            onClose = { vm.closeSession { onSessionClosed?.invoke() }; showSessionMenu = false },
            onDismiss = { showSessionMenu = false },
        )
    }
}

/** DESIGN 01 home: hero wordmark, muted standfirst, three equal cards. */
@Composable
private fun ColumnScope.NewChatEmptyState() {
    val p = Hermes
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HermesLayout.PAGE_INSET_X.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CineHero()
        Spacer(Modifier.height(12.dp))
        CineSub("Describe the task in your own words. I will pick the right tools, explain my plan, and check in before risky steps.")
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CineCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Build, contentDescription = null, tint = p.blue, modifier = Modifier.padding(bottom = 8.dp))
                Text("Build", color = p.textPrimary, fontFamily = HermesSans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Turn ideas into working software", color = p.textSecondary, fontFamily = HermesSans, fontSize = 12.sp, lineHeight = 16.sp)
            }
            CineCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = p.blue, modifier = Modifier.padding(bottom = 8.dp))
                Text("Research", color = p.textPrimary, fontFamily = HermesSans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Find, analyze and synthesize", color = p.textSecondary, fontFamily = HermesSans, fontSize = 12.sp, lineHeight = 16.sp)
            }
            CineCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = p.blue, modifier = Modifier.padding(bottom = 8.dp))
                Text("Automate", color = p.textPrimary, fontFamily = HermesSans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Use tools, run jobs and get results", color = p.textSecondary, fontFamily = HermesSans, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
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
private fun SessionMenuSheet(
    onRename: (String) -> Unit,
    onCompress: () -> Unit,
    onBranch: (String) -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Hermes
    var name by mutableStateOf("")
    HermesSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = HermesLayout.PAGE_INSET_X.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Session", fontWeight = FontWeight.SemiBold, color = p.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (rename / branch)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HermesButton("Rename", onClick = { onRename(name) }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = name.isNotBlank())
                HermesButton("Branch", onClick = { onBranch(name.ifBlank { "branch" }) }, variant = HermesVariant.Secondary, size = HermesSize.Sm)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HermesButton("Compress context", onClick = onCompress, variant = HermesVariant.Secondary, size = HermesSize.Sm)
                HermesButton("Close session", onClick = onClose, variant = HermesVariant.Text, size = HermesSize.Sm)
            }
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
    var query by remember { mutableStateOf("") }
    HermesSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = HermesLayout.PAGE_INSET_X.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Model", fontWeight = FontWeight.SemiBold, color = p.textPrimary)
            if (options.sumOf { it.second.size } > 6) {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search models")
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                options.forEach { (provider, models) ->
                    val rows = models.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                    if (rows.isNotEmpty()) {
                        item(key = "h:$provider") {
                            Text(
                                "$provider · ${models.size}",
                                fontSize = 12.sp,
                                color = p.textTertiary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(rows, key = { "m:$provider:$it" }) { m ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onPick(m) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    m,
                                    color = p.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (m == current) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                if (m == current) {
                                    Text("current", fontSize = 12.sp, color = p.green)
                                }
                            }
                            HorizontalDivider(color = p.strokeQuaternary, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
