package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import android.util.Base64
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.network.FileEntry
import com.kyssta.hermeybeta.network.FileListing
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseFileListing
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.LogView
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch

class WorkspaceViewModel(app: Application) : AndroidViewModel(app) {
    var path: String? by mutableStateOf(null)
    var listing by mutableStateOf<FileListing?>(null)
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var viewing by mutableStateOf<Pair<String, String>?>(null)
    var showMkdir by mutableStateOf(false)
    private var generation = -1

    fun load(force: Boolean = false) {
        val conn = SessionRepository.connection.value ?: return
        if (!force && generation == SessionRepository.generation.value && listing != null) return
        generation = SessionRepository.generation.value
        viewModelScope.launch {
            loading = listing == null
            error = null
            try {
                listing = parseFileListing(SessionRepository.apiFor(conn).files(path))
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            path = entry.path
            listing = null
            query = ""
            load(force = true)
            return
        }
        val conn = SessionRepository.connection.value ?: return
        viewModelScope.launch {
            try {
                val res = SessionRepository.apiFor(conn).readFile(entry.path)
                val dataUrl = res.optString("data_url")
                val mime = res.optString("mime_type")
                val size = res.optLong("size", 0)
                viewing = entry.name to decodeTextPreview(dataUrl, mime, size)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun goUp() {
        path = listing?.parent
        listing = null
        load(force = true)
    }

    fun mkdir(name: String) {
        val base = path?.trimEnd('/') ?: ""
        val full = "$base/$name".trimStart('/')
        val conn = SessionRepository.connection.value ?: return
        viewModelScope.launch {
            try {
                SessionRepository.apiFor(conn).mkdir(full)
                load(force = true)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }
}

/** Decode small text previews; binary/large files get an honest message. */
fun decodeTextPreview(dataUrl: String, mime: String, size: Long): String {
    if (dataUrl.isBlank()) return "(empty file)"
    if (size > 200 * 1024) return "(file too large to preview)"
    if (mime.isNotBlank() && !mime.startsWith("text/") && !mime.contains("json") && !mime.contains("javascript")) {
        return "(binary file — no preview)"
    }
    return try {
        val b64 = dataUrl.substringAfter(",", dataUrl)
        String(Base64.decode(b64, Base64.DEFAULT))
    } catch (_: Exception) {
        "(could not decode preview)"
    }
}

/** Workspace — server file browser (desktop files pane, mobile form). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen() {
    val vm: WorkspaceViewModel = viewModel()
    val conn by SessionRepository.connection.collectAsState()
    val p = Hermes

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.listing?.path?.takeLast(32) ?: "Workspace", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                navigationIcon = {
                    if (vm.listing?.parent != null || vm.path != null) {
                        HermesButton("Up", onClick = { vm.goUp() }, variant = HermesVariant.Text, size = HermesSize.Sm)
                    }
                },
                actions = {
                    HermesButton("New folder", onClick = { vm.showMkdir = true }, variant = HermesVariant.Text, size = HermesSize.Sm)
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
            val entries = vm.listing?.entries.orEmpty()
            if (entries.isNotEmpty()) {
                SearchField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search files")
            }
            when {
                vm.loading -> Loader(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp))
                vm.error != null && entries.isEmpty() -> ErrorState(
                    title = "Could not list files",
                    description = vm.error,
                    retryLabel = "Retry",
                    onRetry = { vm.load(force = true) },
                )
                else -> {
                    val rows = entries.filter {
                        vm.query.isBlank() || it.name.contains(vm.query, ignoreCase = true)
                    }
                    if (rows.isEmpty()) {
                        EmptyState(title = "Empty folder")
                    } else {
                        PullToRefreshBox(
                            isRefreshing = vm.refreshing,
                            onRefresh = { vm.refreshing = true; vm.load(force = true) },
                        ) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(rows, key = { it.path.ifBlank { it.name } }) { e ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { vm.open(e) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            if (e.isDirectory) "[${e.name}]" else e.name,
                                            fontSize = 15.sp,
                                            fontWeight = if (e.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                            color = p.textPrimary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                        )
                                        if (!e.isDirectory && e.size != null) {
                                            Text(formatSize(e.size), fontSize = 12.sp, color = p.textTertiary)
                                        }
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

    vm.viewing?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { vm.viewing = null },
            title = { Text(name) },
            text = { LogView(content.take(8000)) },
            confirmButton = {
                HermesButton("Close", onClick = { vm.viewing = null })
            },
        )
    }
    if (vm.showMkdir) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.showMkdir = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                HermesButton("Create", onClick = { vm.showMkdir = false; vm.mkdir(name) }, enabled = name.isNotBlank())
            },
            dismissButton = {
                HermesButton("Cancel", onClick = { vm.showMkdir = false }, variant = HermesVariant.Text)
            },
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1fM".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0fk".format(bytes / 1024.0)
    else -> "${bytes}B"
}
