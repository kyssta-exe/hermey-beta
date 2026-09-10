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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.BuildConfig
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.network.AppRelease
import com.kyssta.hermeybeta.network.AppUpdates
import com.kyssta.hermeybeta.network.GatewayCookieJar
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
import com.kyssta.hermeybeta.ui.components.SearchField
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesSans
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

    fun load() {
        val conn = SessionRepository.connection.value ?: return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val api = SessionRepository.apiFor(conn)
                val mi = api.modelInfo()
                info = mi
                pendingModel = mi.model
                options = api.modelOptions()
                slots = parseAuxSlots(api.auxiliaryModels())
                profileName = runCatching { api.activeProfile().optString("active").takeUnless { it.isBlank() } }.getOrNull()
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
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
fun SettingsScreen(onSignOut: () -> Unit, onManageGateways: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val ctx = LocalContext.current.applicationContext
    val conn by SessionRepository.connection.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var auxTarget by remember { mutableStateOf<AuxSlot?>(null) }

    LaunchedEffect(conn) { vm.load() }

    Scaffold(
        topBar = { CineTopBar(title = "Settings", onMenu = onManageGateways) },
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
                1 -> EmptyState(
                    title = "Workspace",
                    description = "Workspace settings are not available on mobile yet.",
                    modifier = Modifier.fillMaxWidth(),
                )
                2 -> EmptyState(
                    title = "Tools",
                    description = "Tool settings are not available on mobile yet.",
                    modifier = Modifier.fillMaxWidth(),
                )
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
            CineSub("No auxiliary tasks reported by this gateway.")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(title: String, options: List<Pair<String, List<String>>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = Hermes
    var query by remember { mutableStateOf("") }
    HermesSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(title, color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(16.dp))
        }
    }
}
