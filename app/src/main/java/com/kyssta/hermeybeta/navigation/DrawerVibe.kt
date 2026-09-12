package com.kyssta.hermeybeta.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.ui.theme.Hermes

/**
 * Drawer chrome — Hermes vibe, not default Material: sidebar fill, brand
 * header with the live gateway host, grouped destinations with descriptions.
 * One home for drawer styling; screens only call onMenu.
 */
@Composable
fun DrawerHeader(host: String?, route: String, onHome: () -> Unit) {
    val p = Hermes
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onHome() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = p.accent,
                shape = CircleShape,
                modifier = Modifier.size(32.dp),
            ) {
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Hermey",
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                )
                Text(
                    host ?: "no gateway",
                    color = p.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (route.startsWith("chat")) {
            Text(
                "Chat is home — pick a destination below.",
                color = p.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
}

private data class DrawerGroup(val title: String, val items: List<MoreScreen>)

private val DRAWER_GROUPS = listOf(
    DrawerGroup(
        "Conversations",
        listOf(
            MoreScreen.SESSIONS,
            MoreScreen.COMMAND_CENTER,
            MoreScreen.SESSION_IMPORT,
        ),
    ),
    DrawerGroup(
        "Capabilities",
        listOf(
            MoreScreen.MCP,
            MoreScreen.TOOLS,
            MoreScreen.CRON,
            MoreScreen.ARTIFACTS,
        ),
    ),
    DrawerGroup(
        "Activity",
        listOf(
            MoreScreen.AGENTS,
            MoreScreen.STARMAP,
            MoreScreen.INSIGHTS,
        ),
    ),
    DrawerGroup(
        "Connections",
        listOf(
            MoreScreen.MESSAGING,
            MoreScreen.WEBHOOKS,
            MoreScreen.WORKSPACE,
            MoreScreen.MEMORY,
            MoreScreen.PAIRING,
            MoreScreen.PROFILES,
        ),
    ),
    DrawerGroup(
        "System",
        listOf(MoreScreen.SETTINGS),
    ),
)

@Composable
fun DrawerGroups(route: String, onPick: (String) -> Unit) {
    val p = Hermes
    val colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = p.elevated,
        unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedTextColor = p.accent,
        unselectedTextColor = p.textSecondary,
    )
    DRAWER_GROUPS.forEach { group ->
        Text(
            group.title.uppercase(),
            color = p.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        group.items.forEach { m ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(m.label, fontSize = 14.sp)
                        Text(m.description, fontSize = 12.sp, color = p.textTertiary, maxLines = 1)
                    }
                },
                selected = route == m.route,
                colors = colors,
                onClick = { onPick(m.route) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}
