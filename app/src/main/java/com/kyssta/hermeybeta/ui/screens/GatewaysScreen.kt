package com.kyssta.hermeybeta.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.auth.GatewayMode
import com.kyssta.hermeybeta.network.GatewayCookieJar
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.ListRow
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout

/**
 * Gateways — the mobile form of the desktop profiles/gateway switch.
 * Remote and cloud connections only; switching re-homes the workspace
 * (live socket closed, lists cleared by generation bump).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewaysScreen(onAddGateway: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    // Re-create the store handle against the current context (same encrypted prefs).
    val store = ConnectionStore(ctx)
    val connections by store.connections.collectAsState()
    val activeId by store.activeId.collectAsState()
    val p = Hermes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gateways", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                actions = {
                    HermesButton("Add", onClick = onAddGateway, variant = HermesVariant.Ghost, size = HermesSize.Sm)
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
            if (connections.isEmpty()) {
                EmptyState(
                    title = "No gateways",
                    description = "Add your remote or cloud Hermes gateway.",
                    actionLabel = "Add gateway",
                    onAction = onAddGateway,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(connections, key = { it.id }) { conn ->
                        val isActive = conn.id == activeId
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(conn.displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                    Text(
                                        (if (conn.mode == GatewayMode.REMOTE) "Remote" else "Cloud") + " · " + conn.baseUrl,
                                        fontSize = 12.sp,
                                        color = p.textTertiary,
                                    )
                                }
                                if (isActive) Text("active", fontSize = 12.sp, color = p.green)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isActive) {
                                    HermesButton(
                                        "Switch",
                                        onClick = {
                                            store.setActive(conn.id)
                                            SessionRepository.activate(conn)
                                        },
                                        variant = HermesVariant.Text,
                                        size = HermesSize.Sm,
                                    )
                                }
                                HermesButton(
                                    "Forget",
                                    onClick = {
                                        if (isActive) {
                                            GatewayCookieJar.clear()
                                            SessionRepository.deactivate()
                                        }
                                        store.remove(conn.id)
                                    },
                                    variant = HermesVariant.Text,
                                    size = HermesSize.Sm,
                                )
                            }
                        }
                        HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/** Placeholder for desktop surfaces with no mobile gateway API yet. */
@Composable
fun ComingFromDesktopScreen(title: String, description: String) {
    val p = Hermes
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = HermesLayout.PAGE_INSET_X.dp, vertical = 24.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
        EmptyState(title = "Desktop / web surface", description = "$description Manage it from the Hermes desktop or web dashboard; it appears here once the gateway exposes it.")
        ListRow(label = "Gateway", description = SessionRepository.connection.collectAsState().value?.baseUrl ?: "none")
    }
}
