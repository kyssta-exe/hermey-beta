package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.BuildConfig
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.network.AppRelease
import com.kyssta.hermeybeta.network.AppUpdates
import com.kyssta.hermeybeta.network.GatewayApi
import com.kyssta.hermeybeta.network.GatewayCookieJar
import com.kyssta.hermeybeta.network.ToolsetInfo
import com.kyssta.hermeybeta.network.ModelInfo
import com.kyssta.hermeybeta.network.AuxSlot
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.parseAuxSlots
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.CineCard
import com.kyssta.hermeybeta.ui.components.CineSub
import com.kyssta.hermeybeta.ui.components.CineTopBar
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesDialog
import com.kyssta.hermeybeta.ui.components.HermesSheet
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.LogView
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    var info by mutableStateOf<ModelInfo?>(null)
    var profileName by mutableStateOf<String?>(null)
    var options by mutableStateOf<List<Pair<String, List<String>>>>(emptyList())
    var slots by mutableStateOf<List<AuxSlot>>(emptyList())
    var pendingModel by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var auxError by mutableStateOf<String?>(null)

    fun load() {
        val conn = SessionRepository.connection.value ?: return
        loading = true
        error = null
        auxError = null
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                // Independent rungs: one failing endpoint must not blank the rest.
                try {
                    val mi = api.modelInfo()
                    info = mi
                    pendingModel = mi.model
                } catch (e: Exception) {
                    error = gatewayErrorMessage(e)
                }
                try {
                    options = api.modelOptions()
                } catch (e: Exception) {
                    if (error == null) error = gatewayErrorMessage(e)
                }
                try {
                    slots = parseAuxSlots(api.auxiliaryModels())
                } catch (e: Exception) {
                    slots = emptyList()
                    auxError = gatewayErrorMessage(e)
                }
                profileName = runCatching { api.activeProfile().optString("active").takeUnless { it.isBlank() } }.getOrNull()
            } finally {
                loading = false
            }
        }
    }

    fun saveMain(model: String) {
        val conn = SessionRepository.connection.value ?: return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                api.setModelAssignment(model, info?.provider ?: "auto", "main", "")
                info = api.modelInfo()
                pendingModel = info?.model
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }

    fun saveAux(slot: AuxSlot, model: String) {
        val conn = SessionRepository.connection.value ?: return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                api.setModelAssignment(model, slot.provider ?: "auto", "auxiliary", slot.task)
                slots = parseAuxSlots(api.auxiliaryModels())
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }
}

private fun auxIcon(task: String): ImageVector {
    val t = task.lowercase()
    return when {
        "vision" in t -> Icons.Outlined.Visibility
        "compress" in t -> Icons.Outlined.Compress
        "skill" in t -> Icons.Outlined.Extension
        "approv" in t -> Icons.Outlined.VerifiedUser
        "mcp" in t -> Icons.Outlined.Link
        else -> Icons.Outlined.Extension
    }
}

/** Settings/Models (DESIGN 03) — model picker, auxiliary overrides, placeholders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onMenu: () -> Unit = {}, onSignOut: () -> Unit, onManageGateways: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val ctx = LocalContext.current.applicationContext
    val conn by SessionRepository.connection.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var auxTarget by remember { mutableStateOf<AuxSlot?>(null) }

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = { CineTopBar(title = "Settings", onMenu = onMenu) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
        ) {
            SegmentedControl(
                options = listOf("Model", "Workspace", "Tools", "About"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            when (tab) {
                0 -> ModelTab(
                    vm = vm,
                    onPickMain = { auxTarget = null; sheetOpen = true },
                    onPickAux = { auxTarget = it; sheetOpen = true },
                )
                1 -> WorkspaceTab()
                2 -> ToolsTab()
                else -> AboutTab(connName = conn?.displayName, connUrl = conn?.baseUrl, onManageGateways = onManageGateways, onSignOut = {
                    GatewayCookieJar.clear()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    SessionRepository.deactivate()
                    ConnectionStore(ctx).setActive(null)
                    onSignOut()
                })
            }
        }
    }

    if (sheetOpen) {
        ModelSheet(
            title = if (auxTarget == null) "Choose model" else "Override ${auxTarget?.label ?: auxTarget?.task}",
            options = vm.options,
            onDismiss = { sheetOpen = false },
            onPick = { model ->
                sheetOpen = false
                val target = auxTarget
                if (target == null) vm.pendingModel = model else vm.saveAux(target, model)
            },
        )
    }
}

@Composable
private fun ModelTab(vm: SettingsViewModel, onPickMain: () -> Unit, onPickAux: (AuxSlot) -> Unit) {
    val p = Hermes
    if (vm.loading) {
        Loader(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        return
    }
    if (vm.error != null && vm.info == null) {
        ErrorState(title = "Could not load models", description = vm.error, retryLabel = "Retry", onRetry = { vm.load() })
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Active model", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        CineSub("Choose the model for new sessions.")
        Spacer(Modifier.height(8.dp))
        CineCard(modifier = Modifier.fillMaxWidth(), onClick = onPickMain) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = p.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.pendingModel ?: vm.info?.model ?: "Unknown", color = p.textPrimary, fontFamily = HermesSans, fontSize = 15.sp)
                    Text(
                        vm.profileName ?: vm.info?.provider ?: "Default profile",
                        color = p.textSecondary, fontFamily = HermesSans, fontSize = 13.sp,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = p.textTertiary)
            }
        }
        Spacer(Modifier.height(8.dp))
        CineCard(modifier = Modifier.fillMaxWidth()) {
            ListRow(label = "Provider", description = "Auto (recommended)")
        }
        Spacer(Modifier.height(12.dp))
        val dirty = vm.pendingModel != null && vm.pendingModel != vm.info?.model
        HermesButton(
            if (vm.saving) "Applying…" else "Apply",
            onClick = { vm.pendingModel?.let { vm.saveMain(it) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = dirty && !vm.saving,
        )
        if (vm.error != null) {
            Text(vm.error ?: "", color = p.red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Auxiliary models", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        CineSub("Helper tasks run on the default model. Assign a dedicated model to any task to override.")
        Spacer(Modifier.height(8.dp))
        if (vm.slots.isEmpty()) {
            CineSub(vm.auxError ?: "No auxiliary tasks reported by this gateway.")
        } else {
            vm.slots.forEach { slot ->
                CineCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { onPickAux(slot) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(auxIcon(slot.task), contentDescription = null, tint = p.textSecondary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(slot.label ?: slot.task, color = p.textPrimary, fontFamily = HermesSans, fontSize = 15.sp)
                                Surface(color = p.elevated, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        slot.task, color = p.textSecondary, fontFamily = HermesSans, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                slot.model ?: "auto - use main model",
                                color = p.textSecondary, fontFamily = HermesSans, fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = p.textTertiary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AboutTab(connName: String?, connUrl: String?, onManageGateways: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<AppRelease?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    fun check() {
        scope.launch {
            checking = true
            try {
                val rel = AppUpdates.latest()
                pending = rel?.takeIf { AppUpdates.isNewer(it.tag, BuildConfig.VERSION_NAME) }
                if (pending != null) showUpdate = true
                note = when {
                    pending != null -> null // dialog carries the news
                    rel != null -> "Up to date (${BuildConfig.VERSION_NAME})"
                    else -> "Check failed — tap Check to retry"
                }
            } catch (_: Exception) {
                pending = null
                note = "Check failed — tap Check to retry"
            } finally {
                checking = false
            }
        }
    }
    LaunchedEffect(Unit) { check() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        EmptyState(
            title = "About",
            description = "Hermey Beta v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · thin gateway client",
            modifier = Modifier.fillMaxWidth(),
        )
        ListRow(
            label = connName ?: "None",
            description = connUrl,
            action = {
                HermesButton("Manage", onClick = onManageGateways, variant = HermesVariant.Text, size = HermesSize.Sm)
            },
        )
        val rel = pending
        ListRow(
            label = "App updates",
            description = when {
                checking -> "Checking…"
                downloading && rel != null -> "Downloading ${rel.tag} — tap the finished notification to install"
                rel != null -> "${rel.tag} available · ${"%.1f MB".format(rel.apkSize / 1048576.0)}"
                else -> note
            },
            action = {
                HermesButton(
                    when {
                        checking -> "…"
                        rel != null && !downloading -> "Update"
                        else -> "Check"
                    },
                    onClick = { if (rel != null && !downloading) showUpdate = true else check() },
                    variant = HermesVariant.Text,
                    size = HermesSize.Sm,
                )
            },
        )
        HermesButton(
            "Sign out",
            onClick = onSignOut,
            variant = HermesVariant.Destructive,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
    val offer = pending
    if (showUpdate && offer != null) {
        HermesDialog(
            onDismissRequest = { showUpdate = false },
            title = { Text("Update available") },
            text = { Text("${offer.tag} is ready · ${"%.1f MB".format(offer.apkSize / 1048576.0)}. Download now and tap the finished notification to install.") },
            confirmButton = {
                HermesButton(
                    "Download & install",
                    onClick = {
                        enqueueApk(ctx, offer)
                        downloading = true
                        showUpdate = false
                    },
                )
            },
            dismissButton = {
                HermesButton("Later", onClick = { showUpdate = false }, variant = HermesVariant.Text, size = HermesSize.Sm)
            },
        )
    }
}

/** System DownloadManager fetch — its completion notification installs the APK, no new dep. */
private fun enqueueApk(ctx: android.content.Context, rel: AppRelease) {
    val dm = ctx.getSystemService(android.app.DownloadManager::class.java)
    dm.enqueue(
        android.app.DownloadManager.Request(android.net.Uri.parse(rel.apkUrl))
            .setTitle("Hermey Beta ${rel.tag}")
            .setDescription("Downloading update")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(ctx, android.os.Environment.DIRECTORY_DOWNLOADS, "hermey-beta-${rel.tag}.apk"),
    )
}

/** Mirrors desktop Settings → Workspace (config:workspace section). */
@Composable
private fun WorkspaceTab() {
    val p = Hermes
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cwd by remember { mutableStateOf("") }
    var execMode by remember { mutableStateOf("project") }
    var persistentShell by remember { mutableStateOf(true) }
    var envPassthrough by remember { mutableStateOf("") }
    var fileReadLimit by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf("") }
    fun current() = listOf(cwd, execMode, persistentShell.toString(), envPassthrough, fileReadLimit).joinToString("")
    fun api(): GatewayApi? = conn?.let { SessionRepository.apiFor(it) }
    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val cfg = api()?.serverConfig() ?: throw IllegalStateException("No gateway selected")
                val t = cfg.optJSONObject("terminal") ?: org.json.JSONObject()
                cwd = t.optString("cwd", ".")
                execMode = cfg.optJSONObject("code_execution")?.optString("mode", "project")?.takeUnless { it.isBlank() } ?: "project"
                persistentShell = t.optBoolean("persistent_shell", true)
                envPassthrough = t.optJSONArray("env_passthrough")?.let { a ->
                    List(a.length()) { i -> a.optString(i) }.filter { it.isNotBlank() }.joinToString(", ")
                } ?: ""
                fileReadLimit = cfg.opt("file_read_max_chars")?.toString() ?: ""
                snapshot = current()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(conn) { load() }
    if (loading) {
        Loader(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        return
    }
    if (error != null && snapshot.isEmpty()) {
        ErrorState(title = "Could not load workspace", description = error, retryLabel = "Retry", onRetry = { load() })
        return
    }
    val dirty = current() != snapshot
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Workspace", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        CineSub("Working directory, execution sandbox and file limits — same server config as desktop Settings.")
        Spacer(Modifier.height(8.dp))
        Text("Working Directory", color = p.textPrimary, fontFamily = HermesSans, fontSize = 14.sp)
        OutlinedTextField(
            value = cwd,
            onValueChange = { cwd = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
        )
        Text("Code Execution Mode", color = p.textPrimary, fontFamily = HermesSans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        SegmentedControl(
            options = listOf("project", "strict"),
            selected = if (execMode == "strict") 1 else 0,
            onSelect = { execMode = if (it == 1) "strict" else "project" },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        CineSub(if (execMode == "strict") "Isolated temp dir, own python. Maximum isolation." else "Session dir with project env. Deps and relative paths resolve.")
        ListRow(
            label = "Persistent Shell",
            description = "Keep one shell across commands",
            action = {
                Switch(
                    checked = persistentShell,
                    onCheckedChange = { persistentShell = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = p.accent),
                )
            },
        )
        Text("Environment Passthrough", color = p.textPrimary, fontFamily = HermesSans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = envPassthrough,
            onValueChange = { envPassthrough = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            placeholder = { Text("comma, separated, VARS") },
            singleLine = true,
        )
        Text("File Read Limit", color = p.textPrimary, fontFamily = HermesSans, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = fileReadLimit,
            onValueChange = { fileReadLimit = it.filter(Char::isDigit).take(9) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            placeholder = { Text("100000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        HermesButton(
            if (saving) "Saving…" else "Save",
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    try {
                        val a = api() ?: throw IllegalStateException("No gateway selected")
                        val envArr = org.json.JSONArray(
                            envPassthrough.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        )
                        val patch = org.json.JSONObject()
                            .put(
                                "terminal",
                                org.json.JSONObject()
                                    .put("cwd", cwd.trim().ifBlank { "." })
                                    .put("persistent_shell", persistentShell)
                                    .put("env_passthrough", envArr),
                            )
                            .put("code_execution", org.json.JSONObject().put("mode", execMode))
                        fileReadLimit.trim().toIntOrNull()?.let { patch.put("file_read_max_chars", it) }
                        a.saveServerConfig(patch)
                        snapshot = current()
                    } catch (e: Exception) {
                        error = gatewayErrorMessage(e)
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = dirty && !saving,
        )
        if (error != null) {
            Text(error ?: "", color = p.red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Desktop hides platform-restricted toolsets client-side; match it. */
private val HIDDEN_TOOLSETS = setOf("discord", "discord_admin", "yuanbao", "context_engine", "moa")

/** Mirrors desktop Settings → Tools (toolset-config-panel). */
@Composable
private fun ToolsTab() {
    val p = Hermes
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<ToolsetInfo>>(emptyList()) }
    var toggling by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<ToolsetInfo?>(null) }
    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val a = conn?.let { SessionRepository.apiFor(it) } ?: throw IllegalStateException("No gateway selected")
                rows = a.toolsets().filter { it.name !in HIDDEN_TOOLSETS }
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(conn) { load() }
    if (loading) {
        Loader(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        return
    }
    if (error != null && rows.isEmpty()) {
        ErrorState(title = "Could not load tools", description = error, retryLabel = "Retry", onRetry = { load() })
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Tools", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        CineSub("Enable toolsets and configure providers and keys — same panel as desktop Settings → Tools.")
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            CineSub("No configurable toolsets reported by this gateway.")
        }
        rows.forEach { ts ->
            CineCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { selected = ts }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ts.label ?: ts.name, color = p.textPrimary, fontFamily = HermesSans, fontSize = 15.sp)
                            if (ts.configured) {
                                Surface(color = p.elevated, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "Configured", color = p.green, fontFamily = HermesSans, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (!ts.description.isNullOrBlank()) {
                            Text(ts.description, color = p.textSecondary, fontFamily = HermesSans, fontSize = 13.sp)
                        }
                    }
                    Switch(
                        checked = ts.enabled,
                        enabled = ts.name !in toggling,
                        onCheckedChange = { want ->
                            scope.launch {
                                toggling = toggling + ts.name
                                try {
                                    val a = conn?.let { SessionRepository.apiFor(it) }
                                        ?: throw IllegalStateException("No gateway selected")
                                    a.setToolsetEnabled(ts.name, want)
                                    rows = a.toolsets().filter { it.name !in HIDDEN_TOOLSETS }
                                } catch (e: Exception) {
                                    error = gatewayErrorMessage(e)
                                } finally {
                                    toggling = toggling - ts.name
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.accent),
                    )
                }
            }
        }
        if (error != null) {
            Text(error ?: "", color = p.red, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
    selected?.let { ts ->
        ToolsetSheet(info = ts, onChanged = { load() }, onDismiss = { selected = null })
    }
}

@Composable
private fun ToolsetSheet(info: ToolsetInfo, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    var cfg by remember(info.name) { mutableStateOf<org.json.JSONObject?>(null) }
    var loading by remember(info.name) { mutableStateOf(true) }
    var error by remember(info.name) { mutableStateOf<String?>(null) }
    var pickingProvider by remember { mutableStateOf(false) }
    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                val a = conn?.let { SessionRepository.apiFor(it) } ?: throw IllegalStateException("No gateway selected")
                cfg = a.toolsetConfig(info.name)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(info.name) { refresh() }
    HermesSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Text(info.label ?: info.name, fontWeight = FontWeight.SemiBold, color = Hermes.textPrimary, fontFamily = HermesSans, fontSize = 16.sp)
            if (!info.description.isNullOrBlank()) CineSub(info.description)
            Spacer(Modifier.height(8.dp))
            val c = cfg
            when {
                loading -> Loader(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                c == null -> ErrorState(title = "Could not load config", description = error, retryLabel = "Retry", onRetry = { refresh() })
                else -> {
                    val active = c.optString("active_provider")
                    val providers = c.optJSONArray("providers")
                    Text("Provider", fontWeight = FontWeight.SemiBold, color = Hermes.textPrimary, fontFamily = HermesSans, fontSize = 14.sp)
                    if (providers == null || providers.length() == 0) {
                        CineSub("No providers for this toolset.")
                    } else {
                        for (i in 0 until providers.length()) {
                            val prov = providers.optJSONObject(i) ?: continue
                            val pname = prov.optString("name")
                            if (pname.isBlank()) continue
                            val isActive = prov.optBoolean("is_active", false) || pname == active
                            ListRow(
                                label = pname + (prov.optString("badge").takeUnless { it.isBlank() }?.let { " · $it" } ?: ""),
                                description = prov.optString("tag").takeUnless { it.isBlank() }
                                    ?: prov.optString("requires_nous_auth").takeIf { it == "true" }?.let { "Requires Nous auth" },
                                action = {
                                    if (isActive) Text("Active", color = Hermes.green, fontFamily = HermesSans, fontSize = 13.sp)
                                },
                                onClick = {
                                    if (!isActive && !pickingProvider) {
                                        scope.launch {
                                            pickingProvider = true
                                            try {
                                                val a = conn?.let { SessionRepository.apiFor(it) }
                                                    ?: throw IllegalStateException("No gateway selected")
                                                a.setToolsetProvider(info.name, pname)
                                                refresh()
                                                onChanged()
                                            } catch (e: Exception) {
                                                error = gatewayErrorMessage(e)
                                            } finally {
                                                pickingProvider = false
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        // Detail blocks for the active provider only (desktop parity, compact).
                        val prov = (0 until providers.length())
                            .map { providers.optJSONObject(it) ?: org.json.JSONObject() }
                            .firstOrNull { it.optBoolean("is_active", false) || it.optString("name") == active }
                            ?: providers.optJSONObject(0) ?: org.json.JSONObject()
                        val envVars = prov.optJSONArray("env_vars")
                        if (envVars != null && envVars.length() > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("Keys", fontWeight = FontWeight.SemiBold, color = Hermes.textPrimary, fontFamily = HermesSans, fontSize = 14.sp)
                            for (i in 0 until envVars.length()) {
                                val ev = envVars.optJSONObject(i) ?: continue
                                EnvKeyField(toolset = info.name, ev = ev)
                            }
                        }
                        prov.optString("post_setup").takeUnless { it.isBlank() }?.let { key ->
                            Spacer(Modifier.height(8.dp))
                            PostSetupRunner(toolset = info.name, setupKey = key, onComplete = { refresh(); onChanged() })
                        }
                        if (info.name == "image_gen" || info.name == "video_gen") {
                            Spacer(Modifier.height(8.dp))
                            ModelCatalog(toolset = info.name, provider = prov.optString("name").takeUnless { it.isBlank() })
                        }
                    }
                    if (error != null) {
                        Text(error ?: "", color = Hermes.red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvKeyField(toolset: String, ev: org.json.JSONObject) {
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    val key = ev.optString("key")
    if (key.isBlank()) return
    var input by remember(key) { mutableStateOf("") }
    var isSet by remember(key) { mutableStateOf(ev.optBoolean("is_set", false)) }
    var revealed by remember(key) { mutableStateOf<String?>(null) }
    var busy by remember(key) { mutableStateOf(false) }
    var armedClear by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    fun api(): GatewayApi = conn?.let { SessionRepository.apiFor(it) } ?: throw IllegalStateException("No gateway selected")
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                ev.optString("prompt").takeUnless { it.isBlank() } ?: key,
                color = Hermes.textPrimary, fontFamily = HermesSans, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            Surface(color = Hermes.elevated, shape = RoundedCornerShape(6.dp)) {
                Text(
                    if (isSet) "Set" else "Not set", color = if (isSet) Hermes.green else Hermes.textTertiary,
                    fontFamily = HermesSans, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (!ev.optString("url").isNullOrBlank()) CineSub(ev.optString("url"))
        if (revealed != null) {
            OutlinedTextField(
                value = revealed ?: "", onValueChange = {}, readOnly = true,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; armedClear = false },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isSet) "•••••• (enter new value)" else (ev.optString("default").takeUnless { it.isBlank() } ?: "Enter value")) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HermesButton(
                "Save", onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val rep = api().setToolsetEnv(toolset, org.json.JSONObject().put(key, if (revealed != null) revealed else input))
                            isSet = rep.optJSONObject("is_set")?.optBoolean(key, true) ?: true
                            input = ""
                            revealed = null
                        } catch (e: Exception) {
                            error = gatewayErrorMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                },
                variant = HermesVariant.Secondary, size = HermesSize.Sm,
                enabled = !busy && (revealed != null || input.isNotBlank()),
            )
            if (isSet) {
                if (revealed != null) {
                    HermesButton("Hide", onClick = { revealed = null }, variant = HermesVariant.Text, size = HermesSize.Sm)
                } else {
                    HermesButton("Reveal", onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                revealed = api().revealEnv(key)
                            } catch (e: Exception) {
                                error = gatewayErrorMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }, variant = HermesVariant.Text, size = HermesSize.Sm, enabled = !busy)
                }
                HermesButton(if (armedClear) "Tap again to confirm" else "Clear", onClick = {
                    if (!armedClear) {
                        armedClear = true
                        return@HermesButton
                    }
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            api().clearEnv(key)
                            isSet = false
                            revealed = null
                            input = ""
                        } catch (e: Exception) {
                            error = gatewayErrorMessage(e)
                        } finally {
                            busy = false
                            armedClear = false
                        }
                    }
                }, variant = HermesVariant.Text, size = HermesSize.Sm, enabled = !busy)
            }
        }
        if (error != null) Text(error ?: "", color = Hermes.red, fontSize = 12.sp)
    }
}

@Composable
private fun ModelCatalog(toolset: String, provider: String?) {
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    var catalog by remember(toolset, provider) { mutableStateOf<org.json.JSONObject?>(null) }
    var loading by remember(toolset, provider) { mutableStateOf(true) }
    var error by remember(toolset, provider) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val a = conn?.let { SessionRepository.apiFor(it) } ?: throw IllegalStateException("No gateway selected")
                catalog = a.toolsetModels(toolset, provider)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(toolset, provider) { load() }
    Text("Model", fontWeight = FontWeight.SemiBold, color = Hermes.textPrimary, fontFamily = HermesSans, fontSize = 14.sp)
    val c = catalog
    when {
        loading -> Loader(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        c == null || error != null -> ErrorState(title = "Could not load models", description = error, retryLabel = "Retry", onRetry = { load() })
        !c.optBoolean("has_models", false) -> CineSub("No model catalog for this backend.")
        else -> {
            val current = c.optString("current").takeUnless { it.isBlank() }
            val default = c.optString("default").takeUnless { it.isBlank() }
            val models = c.optJSONArray("models")
            if (models != null) {
                for (i in 0 until models.length()) {
                    val m = models.optJSONObject(i) ?: continue
                    val id = m.optString("id")
                    if (id.isBlank()) continue
                    val meta = listOf(m.optString("speed"), m.optString("price")).filter { it.isNotBlank() }.joinToString(" · ")
                    ListRow(
                        label = m.optString("display").takeUnless { it.isBlank() } ?: id,
                        description = (m.optString("strengths").takeUnless { it.isBlank() }?.let { "$it" } ?: "") +
                            (meta.takeUnless { it.isBlank() }?.let { " ($it)" } ?: ""),
                        action = {
                            when (id) {
                                current -> Text("In use", color = Hermes.green, fontFamily = HermesSans, fontSize = 13.sp)
                                default -> Text("Default", color = Hermes.textTertiary, fontFamily = HermesSans, fontSize = 13.sp)
                                else -> if (picking == id) Text("…", color = Hermes.textTertiary, fontSize = 13.sp) else null
                            }
                        },
                        onClick = {
                            if (id != current && picking == null) {
                                scope.launch {
                                    picking = id
                                    try {
                                        val a = conn?.let { SessionRepository.apiFor(it) }
                                            ?: throw IllegalStateException("No gateway selected")
                                        a.setToolsetModel(toolset, id, provider)
                                        load()
                                    } catch (e: Exception) {
                                        error = gatewayErrorMessage(e)
                                    } finally {
                                        picking = null
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostSetupRunner(toolset: String, setupKey: String, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val conn by SessionRepository.connection.collectAsState()
    var running by remember(toolset, setupKey) { mutableStateOf(false) }
    var log by remember(toolset, setupKey) { mutableStateOf("") }
    var error by remember(toolset, setupKey) { mutableStateOf<String?>(null) }
    var runId by remember(toolset, setupKey) { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Needs install ($setupKey)", color = Hermes.textPrimary, fontFamily = HermesSans,
                fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            HermesButton(
                if (running) "Running…" else "Run",
                onClick = {
                    scope.launch {
                        val id = runId + 1
                        runId = id
                        running = true
                        error = null
                        log = ""
                        try {
                            val a = conn?.let { SessionRepository.apiFor(it) }
                                ?: throw IllegalStateException("No gateway selected")
                            val started = a.runToolsetPostSetup(toolset, setupKey)
                            if (!started.optBoolean("ok", false)) {
                                error = started.optString("detail").takeUnless { it.isBlank() } ?: "Install failed to start"
                                return@launch
                            }
                            val action = started.optString("name")
                            repeat(150) {
                                delay(1200)
                                if (runId != id) return@launch
                                val st = a.actionStatus(action, 60)
                                val lines = st.optJSONArray("lines")
                                log = List(lines?.length() ?: 0) { i -> lines?.optString(i) ?: "" }.takeLast(60).joinToString("\n")
                                if (!st.optBoolean("running", false)) {
                                    if (st.opt("exit_code") != null && st.optInt("exit_code", -1) != 0) {
                                        error = "Install exited with code ${st.optInt("exit_code")}"
                                    }
                                    onComplete()
                                    return@launch
                                }
                            }
                            error = "Still running — reopen to check again"
                        } catch (e: Exception) {
                            if (runId == id) error = gatewayErrorMessage(e)
                        } finally {
                            if (runId == id) running = false
                        }
                    }
                },
                variant = HermesVariant.Secondary, size = HermesSize.Sm, enabled = !running,
            )
        }
        if (log.isNotBlank()) LogView(log)
        if (error != null) Text(error ?: "", color = Hermes.red, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(title: String, options: List<Pair<String, List<String>>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = Hermes
    var query by remember { mutableStateOf("") }
    HermesSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            if (options.sumOf { it.second.size } == 0) {
                CineSub("No models reported by this gateway.")
            } else {
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search models")
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (provider, models) ->
                    val filtered = if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }
                    if (filtered.isNotEmpty()) {
                        item(key = "h-$provider") {
                            Text(
                                provider, color = p.textTertiary, fontFamily = HermesSans,
                                fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(filtered, key = { "$provider/$it" }) { model ->
                            ListRow(label = model, onClick = { onPick(model) })
                        }
                    }
                }
            }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
