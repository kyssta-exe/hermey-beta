package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.GatewayHttpException
import com.kyssta.hermeybeta.network.KanbanColumn
import com.kyssta.hermeybeta.network.KanbanDetail
import com.kyssta.hermeybeta.network.KanbanTask
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.lane
import com.kyssta.hermeybeta.network.parseKanbanBoard
import com.kyssta.hermeybeta.network.parseKanbanDetail
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.CineCard
import com.kyssta.hermeybeta.ui.components.CineTopBar
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesDialog
import com.kyssta.hermeybeta.ui.components.HermesSheet
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.LaneHeader
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesSans
import kotlinx.coroutines.launch

class TasksViewModel(app: Application) : AndroidViewModel(app) {
    var columns by mutableStateOf<List<KanbanColumn>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var notEnabled by mutableStateOf(false)
    var tabIdx by mutableStateOf(0)
    var query by mutableStateOf("")
    var searchVisible by mutableStateOf(false)
    var showAdd by mutableStateOf(false)
    var selected by mutableStateOf<KanbanTask?>(null)
    var busy by mutableStateOf(false)
    private var generation = -1

    fun laneTasks(lane: String): List<KanbanTask> =
        columns.filter { it.lane() == lane }.flatMap { it.tasks }

    private fun matches(t: KanbanTask): Boolean {
        if (query.isBlank()) return true
        return t.title.contains(query, ignoreCase = true) ||
            (t.body?.contains(query, ignoreCase = true) == true)
    }

    fun visibleTasks(lane: String, mineOnly: Boolean): List<KanbanTask> =
        laneTasks(lane).filter { (!mineOnly || it.assignee != null) && matches(it) }

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && columns.isNotEmpty()) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = columns.isEmpty()
            error = null
            notEnabled = false
            try {
                columns = parseKanbanBoard(SessionRepository.apiFor(conn).kanbanBoard())
            } catch (e: GatewayHttpException) {
                if (e.code == 404) notEnabled = true else error = gatewayErrorMessage(e)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun create(title: String, body: String) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                busy = true
                SessionRepository.apiFor(conn).createKanbanTask(title, body.ifBlank { null })
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun move(task: KanbanTask, status: String) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                busy = true
                SessionRepository.apiFor(conn).moveKanbanTask(task.id, status)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun comment(task: KanbanTask, body: String) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                busy = true
                SessionRepository.apiFor(conn).kanbanComment(task.id, body)
                load(force = true)
                detail(task.id, silent = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    var detail by mutableStateOf<KanbanDetail?>(null)
    var detailLoading by mutableStateOf(false)

    fun detail(taskId: String, silent: Boolean = false) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                if (!silent) { detailLoading = true; detail = null }
                detail = parseKanbanDetail(SessionRepository.apiFor(conn).kanbanTask(taskId))
            } catch (e: Exception) {
                if (!silent) error = gatewayErrorMessage(e)
            } finally {
                detailLoading = false
            }
        }
    }

    fun clearDetail() { detail = null; detailLoading = false }

    fun edit(task: KanbanTask, title: String, body: String?) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                busy = true
                SessionRepository.apiFor(conn).updateKanbanTask(
                    task.id,
                    org.json.JSONObject().put("title", title).apply { body?.let { put("body", it) } },
                )
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun delete(task: KanbanTask, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val conn = SessionRepository.connection.value ?: return@launch
                busy = true
                SessionRepository.apiFor(conn).deleteKanbanTask(task.id)
                selected = null
                clearDetail()
                load(force = true)
                onDone()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }
}

/** Age like 6d / 4h / 12m from a created-at epoch (seconds or ms). */
private fun taskAge(createdAt: Long?): String? {
    if (createdAt == null) return null
    val ms = if (createdAt > 1_000_000_000_000L) createdAt else createdAt * 1000
    val diffMin = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when {
        diffMin < 60 -> "${diffMin}m"
        diffMin < 60 * 24 -> "${diffMin / 60}h"
        else -> "${diffMin / (60 * 24)}d"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onMenu: () -> Unit = {}) {
    val vm: TasksViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            CineTopBar(
                title = "Tasks",
                onMenu = onMenu,
                onSearch = { vm.searchVisible = !vm.searchVisible },
                onAdd = { vm.showAdd = true },
            )
        },
        containerColor = p.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
        ) {
            SegmentedControl(
                options = listOf("Kanban", "My Tasks", "Done"),
                selected = vm.tabIdx,
                onSelect = { vm.tabIdx = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            if (vm.searchVisible) {
                SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search tasks")
            }
            when {
                vm.loading -> Loader(modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
                vm.notEnabled -> EmptyState(
                    title = "Kanban not enabled",
                    description = "The kanban plugin is not enabled on this gateway.",
                )
                vm.error != null && vm.columns.isEmpty() -> ErrorState(
                    title = "Could not load tasks",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.refreshing = true; vm.load(force = true) },
                ) {
                    when (vm.tabIdx) {
                        2 -> {
                            val done = vm.visibleTasks("done", mineOnly = false)
                            if (done.isEmpty()) {
                                EmptyState(title = "Nothing done yet", description = "Completed tasks will appear here.")
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(done, key = { it.id }) { t ->
                                        TaskCard(t, onClick = { vm.selected = t })
                                    }
                                }
                            }
                        }
                        else -> {
                            val mineOnly = vm.tabIdx == 1
                            val lanes = listOf(
                                // ponytail: teal has no Hermes token; spec mandates #2DD4BF for the TODO dot.
                                Triple("TODO", Color(0xFF2DD4BF), vm.visibleTasks("todo", mineOnly)),
                                Triple("IN PROGRESS", p.purple, vm.visibleTasks("progress", mineOnly)),
                                Triple("DONE", p.green, vm.visibleTasks("done", mineOnly)),
                            )
                            if (lanes.all { it.third.isEmpty() }) {
                                EmptyState(
                                    title = if (mineOnly) "No tasks assigned to you" else "No tasks",
                                    description = if (mineOnly) "Tasks with an assignee appear here." else "Create one with the + button.",
                                )
                            } else {
                                Row(
                                    Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    lanes.forEach { (name, dot, tasks) ->
                                        Column(Modifier.width(280.dp)) {
                                            LaneHeader(dot = dot, name = name, count = tasks.size, modifier = Modifier.padding(vertical = 8.dp))
                                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                items(tasks, key = { it.id }) { t ->
                                                    TaskCard(t, onClick = { vm.selected = t })
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
        }
    }

    if (vm.showAdd) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        HermesDialog(
            onDismissRequest = { vm.showAdd = false },
            title = { Text("New task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Details") }, minLines = 2)
                }
            },
            confirmButton = {
                HermesButton(
                    "Create",
                    onClick = { vm.showAdd = false; vm.create(title.trim(), body.trim()) },
                    enabled = title.isNotBlank() && !vm.busy,
                )
            },
            dismissButton = {
                HermesButton("Cancel", onClick = { vm.showAdd = false }, variant = HermesVariant.Text)
            },
        )
    }

    vm.selected?.let { task ->
        // Refresh the selection from the reloaded board so counts stay current.
        val live = vm.laneTasks("todo").plus(vm.laneTasks("progress")).plus(vm.laneTasks("done"))
            .firstOrNull { it.id == task.id } ?: task
        LaunchedEffect(live.id) { vm.detail(live.id) }
        HermesSheet(onDismissRequest = { vm.selected = null; vm.clearDetail() }) {
            TaskDetail(
                task = live,
                busy = vm.busy,
                detail = vm.detail,
                detailLoading = vm.detailLoading,
                onMove = { status -> vm.move(live, status) },
                onComment = { body -> vm.comment(live, body) },
                onEdit = { title, body -> vm.edit(live, title, body) },
                onDelete = { vm.delete(live) {} },
            )
        }
    }
}

@Composable
private fun TaskCard(task: KanbanTask, onClick: () -> Unit) {
    val p = Hermes
    CineCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            task.title.ifBlank { "Untitled" },
            color = p.textPrimary,
            fontFamily = HermesSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.body != null) {
            Text(
                task.body,
                color = p.textSecondary,
                fontFamily = HermesSans,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (task.assignee != null) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(p.elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        task.assignee.first().uppercase(),
                        color = p.textPrimary,
                        fontFamily = HermesSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
            if (task.commentCount > 0) {
                Text("${task.commentCount} comments", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
            }
            if (task.progressDone != null && task.progressTotal != null) {
                Text("${task.progressDone}/${task.progressTotal}", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
            }
            taskAge(task.createdAt)?.let {
                Text(it, color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TaskDetail(
    task: KanbanTask,
    busy: Boolean,
    detail: KanbanDetail?,
    detailLoading: Boolean,
    onMove: (String) -> Unit,
    onComment: (String) -> Unit,
    onEdit: (String, String?) -> Unit,
    onDelete: () -> Unit,
) {
    val p = Hermes
    var draft by remember(task.id) { mutableStateOf("") }
    var editing by remember(task.id) { mutableStateOf(false) }
    var editTitle by remember(task.id) { mutableStateOf(task.title) }
    var editBody by remember(task.id) { mutableStateOf(task.body ?: "") }
    var confirmDelete by remember(task.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!editing) {
            Text(task.title.ifBlank { "Untitled" }, color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (task.body != null) {
                Text(task.body, color = p.textSecondary, fontFamily = HermesSans, fontSize = 14.sp, lineHeight = 20.sp)
            }
        } else {
            OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = editBody, onValueChange = { editBody = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesButton("Save", onClick = { onEdit(editTitle.trim(), editBody.trim().takeUnless { it.isBlank() }); editing = false },
                    enabled = editTitle.isNotBlank() && !busy, size = HermesSize.Sm)
                HermesButton("Cancel", onClick = { editing = false; editTitle = task.title; editBody = task.body ?: "" },
                    variant = HermesVariant.Text, size = HermesSize.Sm)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (task.assignee != null) Text("Assignee: ${task.assignee}", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
            if (task.priority != 0) Text("P${task.priority}", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
            if (detail != null && detail.runCount > 0) Text("${detail.runCount} runs", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
        }
        // Full desktop status set: todo / running / blocked / review / done.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HermesButton("To Do", onClick = { onMove("todo") }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !busy)
            HermesButton("Running", onClick = { onMove("running") }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !busy)
            HermesButton("Blocked", onClick = { onMove("blocked") }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !busy)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HermesButton("Review", onClick = { onMove("review") }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !busy)
            HermesButton("Done", onClick = { onMove("done") }, variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !busy)
            if (!editing) HermesButton("Edit", onClick = { editing = true }, variant = HermesVariant.Text, size = HermesSize.Sm)
            HermesButton("Delete", onClick = { confirmDelete = true }, variant = HermesVariant.Destructive, size = HermesSize.Sm, enabled = !busy)
        }
        if (detailLoading) {
            Loader(modifier = Modifier.fillMaxWidth())
        } else if (detail != null) {
            if (detail.comments.isNotEmpty()) {
                Text("Comments (${detail.comments.size})", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                detail.comments.takeLast(20).forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        if (c.author != null) Text(c.author, color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
                        Text(c.body, color = p.textSecondary, fontFamily = HermesSans, fontSize = 13.sp)
                    }
                }
            }
            if (detail.events.isNotEmpty()) {
                Text("History", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                detail.events.takeLast(10).forEach { e ->
                    Text("${e.kind}${e.detail?.let { ": $it" } ?: ""}", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
                }
            }
        } else if (task.commentCount > 0) {
            Text("${task.commentCount} comments", color = p.textTertiary, fontFamily = HermesSans, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Add a comment") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        HermesButton(
            "Send",
            onClick = { onComment(draft.trim()); draft = "" },
            enabled = draft.isNotBlank() && !busy,
            modifier = Modifier.align(Alignment.End),
        )
    }
    if (confirmDelete) {
        HermesDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("Delete \"${task.title.ifBlank { task.id }}\"? This cannot be undone.") },
            confirmButton = { HermesButton("Delete", onClick = { confirmDelete = false; onDelete() }, variant = HermesVariant.Destructive) },
            dismissButton = { HermesButton("Cancel", onClick = { confirmDelete = false }, variant = HermesVariant.Text) },
        )
    }
}
