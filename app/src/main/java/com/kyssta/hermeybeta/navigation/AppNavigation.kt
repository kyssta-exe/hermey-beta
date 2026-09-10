package com.kyssta.hermeybeta.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.screens.ChatScreen
import com.kyssta.hermeybeta.ui.screens.ComingFromDesktopScreen
import com.kyssta.hermeybeta.ui.screens.ConnectScreen
import com.kyssta.hermeybeta.ui.screens.CronScreen
import com.kyssta.hermeybeta.ui.screens.GatewaysScreen
import com.kyssta.hermeybeta.ui.screens.InsightsScreen
import com.kyssta.hermeybeta.ui.screens.AgentsScreen
import com.kyssta.hermeybeta.ui.screens.MemoryScreen
import com.kyssta.hermeybeta.ui.screens.MessagingScreen
import com.kyssta.hermeybeta.ui.screens.PairingScreen
import com.kyssta.hermeybeta.ui.screens.SessionsScreen
import com.kyssta.hermeybeta.ui.screens.SettingsScreen
import com.kyssta.hermeybeta.ui.screens.SkillsScreen
import com.kyssta.hermeybeta.ui.screens.StarmapScreen
import com.kyssta.hermeybeta.ui.screens.WorkspaceScreen
import com.kyssta.hermeybeta.ui.theme.Hermes
import kotlinx.coroutines.launch

private data class Tab(val item: TopLevel, val icon: ImageVector)

private val TABS = listOf(
    Tab(TopLevel.CHAT, Icons.Filled.ChatBubbleOutline),
    Tab(TopLevel.SESSIONS, Icons.Filled.Groups),
    Tab(TopLevel.CRON, Icons.Filled.Schedule),
    Tab(TopLevel.SKILLS, Icons.Filled.School),
    Tab(TopLevel.MORE, Icons.Filled.Menu),
)

/**
 * Shell — mirrors the desktop AppShell: chat is home, pages are durable
 * destinations, everything else hangs off More. The shell stays mounted
 * across gateway switches; only gateway-bound lists reload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val ctx = LocalContext.current.applicationContext
    val store = androidx.compose.runtime.remember { ConnectionStore(ctx) }
    val conn by SessionRepository.connection.collectAsState()

    // Cold start: resume the saved gateway without blocking on its cookie.
    LaunchedEffect(Unit) {
        if (SessionRepository.connection.value == null) {
            store.active()?.let { SessionRepository.activate(it) }
        }
    }

    if (conn == null) {
        ConnectScreen(onConnected = {})
        return
    }

    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val p = Hermes
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.CHAT

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("More", color = p.textTertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                MoreScreen.entries.forEach { m ->
                    NavigationDrawerItem(
                        label = { Text(m.label) },
                        selected = route == m.route,
                        onClick = {
                            scope.launch { drawer.close() }
                            nav.navigate(m.route) { launchSingleTop = true }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = p.sidebar) {
                    TABS.forEach { tab ->
                        val selected = when (tab.item) {
                            TopLevel.CHAT -> route.startsWith("chat")
                            TopLevel.SESSIONS -> route == Routes.SESSIONS
                            TopLevel.CRON -> route == Routes.CRON
                            TopLevel.SKILLS -> route == Routes.SKILLS
                            TopLevel.MORE -> MoreScreen.entries.any { it.route == route } || route == Routes.SETTINGS || route == Routes.PROFILES
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (tab.item) {
                                    TopLevel.MORE -> scope.launch { drawer.open() }
                                    TopLevel.CHAT -> nav.navigate(Routes.chat()) {
                                        popUpTo(nav.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                    else -> nav.navigate(tab.item.route) {
                                        popUpTo(nav.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.item.label) },
                            label = { Text(tab.item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.CHAT,
                modifier = Modifier.padding(padding),
            ) {
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("sessionId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }),
                ) { entry ->
                    val sid = entry.arguments?.getString("sessionId") ?: ""
                    ChatScreen(
                        sessionId = sid,
                        onBranched = { nid -> nav.navigate(Routes.chat(nid)) },
                        onSessionClosed = { nav.navigate(Routes.SESSIONS) { popUpTo(nav.graph.startDestinationId) } },
                    )
                }
                composable(Routes.SESSIONS) {
                    SessionsScreen(
                        onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                        onNewChat = { nav.navigate(Routes.chat()) },
                    )
                }
                composable(Routes.CRON) { CronScreen() }
                composable(Routes.SKILLS) { SkillsScreen() }
                composable(Routes.ARTIFACTS) {
                    ComingFromDesktopScreen("Artifacts", "Previews, files, review and terminal panes attach to the current task on desktop.")
                }
                composable(Routes.MESSAGING) { MessagingScreen() }
                composable(Routes.WORKSPACE) { WorkspaceScreen() }
                composable(Routes.PROFILES) {
                    GatewaysScreen(onAddGateway = { nav.navigate(Routes.CONNECT) })
                }
                composable(Routes.AGENTS) {
                    AgentsScreen(onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.STARMAP) {
                    StarmapScreen(onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.INSIGHTS) { InsightsScreen() }
                composable(Routes.MEMORY) { MemoryScreen() }
                composable(Routes.PAIRING) { PairingScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onSignOut = {},
                        onManageGateways = { nav.navigate(Routes.PROFILES) },
                    )
                }
                composable(Routes.CONNECT) {
                    ConnectScreen(onConnected = { nav.popBackStack() })
                }
            }
        }
    }
}
