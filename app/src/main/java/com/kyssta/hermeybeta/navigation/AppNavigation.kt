package com.kyssta.hermeybeta.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.remember
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
import com.kyssta.hermeybeta.ui.screens.CloudSignInScreen
import com.kyssta.hermeybeta.ui.screens.ConnectScreen
import com.kyssta.hermeybeta.ui.screens.CronScreen
import com.kyssta.hermeybeta.ui.screens.GatewaysScreen
import com.kyssta.hermeybeta.ui.screens.InsightsScreen
import com.kyssta.hermeybeta.ui.screens.AgentsScreen
import com.kyssta.hermeybeta.ui.screens.ArtifactsScreen
import com.kyssta.hermeybeta.ui.screens.CommandCenterScreen
import com.kyssta.hermeybeta.ui.screens.McpScreen
import com.kyssta.hermeybeta.ui.screens.MemoryScreen
import com.kyssta.hermeybeta.ui.screens.MessagingScreen
import com.kyssta.hermeybeta.ui.screens.OAuthLoginScreen
import com.kyssta.hermeybeta.ui.screens.PairingScreen
import com.kyssta.hermeybeta.ui.screens.SessionsScreen
import com.kyssta.hermeybeta.ui.screens.SettingsScreen
import com.kyssta.hermeybeta.ui.screens.SkillsScreen
import com.kyssta.hermeybeta.ui.screens.StarmapScreen
import com.kyssta.hermeybeta.ui.screens.TasksScreen
import com.kyssta.hermeybeta.ui.screens.ToolsScreen
import com.kyssta.hermeybeta.ui.screens.WebhooksScreen
import com.kyssta.hermeybeta.ui.screens.SessionImportScreen
import com.kyssta.hermeybeta.ui.screens.WorkspaceScreen
import com.kyssta.hermeybeta.ui.theme.Hermes
import kotlinx.coroutines.launch

private data class Tab(val item: TopLevel, val icon: ImageVector)

private val TABS = listOf(
    Tab(TopLevel.CHAT, Icons.Outlined.ChatBubbleOutline),
    Tab(TopLevel.TASKS, Icons.Outlined.Dashboard),
    Tab(TopLevel.SKILLS, Icons.Outlined.Extension),
    Tab(TopLevel.SETTINGS, Icons.Outlined.Settings),
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
    val store = remember { ConnectionStore(ctx) }
    val conn by SessionRepository.connection.collectAsState()
    val reauth by SessionRepository.reauthNeeded.collectAsState()

    // Cold start: resume the saved gateway without blocking on its cookie.
    LaunchedEffect(Unit) {
        if (SessionRepository.connection.value == null) {
            store.active()?.let { SessionRepository.activate(it) }
        }
    }

    if (conn == null || reauth) {
        val authNav = rememberNavController()
        NavHost(navController = authNav, startDestination = Routes.CONNECT) {
            AuthGraph(
                nav = authNav,
                onAuthed = { SessionRepository.clearReauth() },
            )
        }
        return
    }

    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val p = Hermes
    val openDrawer: () -> Unit = { scope.launch { drawer.open() }; Unit }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.CHAT

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = p.sidebar) {
                DrawerHeader(
                    host = conn?.baseUrl,
                    route = route,
                    onHome = {
                        scope.launch { drawer.close() }
                        nav.navigate(Routes.chat()) { launchSingleTop = true }
                    },
                )
                DrawerGroups(
                    route = route,
                    onPick = { target ->
                        scope.launch { drawer.close() }
                        nav.navigate(target) { launchSingleTop = true }
                    },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = p.background,
            bottomBar = {
                NavigationBar(containerColor = p.background) {
                    TABS.forEach { tab ->
                        val selected = when (tab.item) {
                            TopLevel.CHAT -> route.startsWith("chat")
                            TopLevel.TASKS -> route == Routes.TASKS
                            TopLevel.SKILLS -> route == Routes.SKILLS
                            TopLevel.SETTINGS -> route == Routes.SETTINGS
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (tab.item) {
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
                            icon = { Icon(tab.icon, contentDescription = tab.item.label, tint = if (selected) p.accent else p.textSecondary) },
                            label = { Text(tab.item.label, color = if (selected) p.accent else p.textSecondary) },
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
                        onMenu = openDrawer,
                        onBranched = { nid -> nav.navigate(Routes.chat(nid)) },
                        onSessionClosed = { nav.navigate(Routes.SESSIONS) { popUpTo(nav.graph.startDestinationId) } },
                        onNewChat = { nav.navigate(Routes.chat()) },
                        onOpenSessions = { nav.navigate(Routes.SESSIONS) { popUpTo(nav.graph.startDestinationId) } },
                    )
                }
                composable(Routes.TASKS) { TasksScreen(onMenu = openDrawer) }
                composable(Routes.SESSIONS) {
                    SessionsScreen(
                        onMenu = openDrawer,
                        onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                        onNewChat = { nav.navigate(Routes.chat()) },
                    )
                }
                composable(Routes.CRON) { CronScreen(onMenu = openDrawer) }
                composable(Routes.SKILLS) { SkillsScreen(onMenu = openDrawer) }
                composable(Routes.ARTIFACTS) {
                    ArtifactsScreen(onMenu = openDrawer, onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.MESSAGING) { MessagingScreen(onMenu = openDrawer) }
                composable(Routes.WORKSPACE) { WorkspaceScreen(onMenu = openDrawer) }
                composable(Routes.PROFILES) {
                    GatewaysScreen(onMenu = openDrawer, onAddGateway = { nav.navigate(Routes.CONNECT) })
                }
                composable(Routes.AGENTS) {
                    AgentsScreen(onMenu = openDrawer, onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.STARMAP) {
                    StarmapScreen(onMenu = openDrawer, onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.INSIGHTS) { InsightsScreen(onMenu = openDrawer) }
                composable(Routes.MEMORY) { MemoryScreen(onMenu = openDrawer) }
                composable(Routes.PAIRING) { PairingScreen(onMenu = openDrawer) }
                composable(Routes.MCP) { McpScreen(onMenu = openDrawer) }
                composable(Routes.TOOLS) { ToolsScreen(onMenu = openDrawer) }
                composable(Routes.WEBHOOKS) { WebhooksScreen(onMenu = openDrawer) }
                composable(Routes.COMMAND_CENTER) {
                    CommandCenterScreen(onMenu = openDrawer, onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.SESSION_IMPORT) {
                    SessionImportScreen(onMenu = openDrawer, onOpenSession = { id -> nav.navigate(Routes.chat(id)) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onMenu = openDrawer,
                        onSignOut = {},
                        onManageGateways = { nav.navigate(Routes.PROFILES) },
                    )
                }
                AuthGraph(
                    nav = nav,
                    onAuthed = { nav.popBackStack() },
                )
            }
        }
    }
}

/** Auth destinations, shared by the logged-out shell and the add-gateway flow. */
private fun androidx.navigation.NavGraphBuilder.AuthGraph(
    nav: androidx.navigation.NavHostController,
    onAuthed: () -> Unit,
) {
    composable(Routes.CONNECT) {
        ConnectScreen(
            onConnected = { onAuthed() },
            onCloudSignIn = { nav.navigate(Routes.CLOUD_SIGNIN) },
            onRemoteOAuth = { base -> nav.navigate(Routes.oauthLogin(base)) },
        )
    }
    composable(Routes.CLOUD_SIGNIN) {
        CloudSignInScreen(
            onDone = {
                // Fresh login lands home; drop the whole back stack.
                while (nav.popBackStack()) {
                }
            },
            onCancel = { nav.popBackStack() },
        )
    }
    composable(
        Routes.OAUTH_LOGIN,
        arguments = listOf(navArgument("base") {
            type = NavType.StringType
            defaultValue = ""
        }),
    ) { entry ->
        OAuthLoginScreen(
            baseUrl = decodeBase(entry.arguments?.getString("base") ?: ""),
            onDone = {
                while (nav.popBackStack()) {
                }
            },
            onCancel = { nav.popBackStack() },
        )
    }
}

private fun decodeBase(raw: String): String = try {
    java.net.URLDecoder.decode(raw, "UTF-8")
} catch (_: Exception) {
    raw
}
