package com.kyssta.hermeybeta.navigation

/**
 * Route table — mirrors desktop src/app/routes.ts APP_ROUTES, adapted to a
 * mobile nav graph. Chat is the home surface; settings/profiles/overlays are
 * full screens (no floating OverlayView cards on mobile).
 */
object Routes {
    const val CONNECT = "connect"
    const val CLOUD_SIGNIN = "cloud-signin"
    const val OAUTH_LOGIN = "oauth-login?base={base}"
    const val CHAT = "chat?sessionId={sessionId}"
    const val SESSIONS = "sessions"
    const val SKILLS = "skills"
    const val MESSAGING = "messaging"
    const val WORKSPACE = "workspace"
    const val ARTIFACTS = "artifacts"
    const val CRON = "cron"
    const val PROFILES = "profiles"
    const val AGENTS = "agents"
    const val STARMAP = "starmap"
    const val INSIGHTS = "insights"
    const val MEMORY = "memory"
    const val PAIRING = "pairing"
    const val MCP = "mcp"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    fun chat(sessionId: String = "") = if (sessionId.isBlank()) "chat?sessionId=" else "chat?sessionId=$sessionId"

    fun oauthLogin(baseUrl: String) =
        "oauth-login?base=${java.net.URLEncoder.encode(baseUrl, "UTF-8")}"
}

enum class TopLevel(val route: String, val label: String) {
    CHAT(Routes.CHAT, "Chat"),
    SESSIONS(Routes.SESSIONS, "Sessions"),
    CRON(Routes.CRON, "Cron"),
    SKILLS(Routes.SKILLS, "Skills"),
    MORE("more", "More"),
}

/** Drawer destinations behind "More" (desktop pages without a bottom tab). */
enum class MoreScreen(val route: String, val label: String, val description: String) {
    ARTIFACTS(Routes.ARTIFACTS, "Artifacts", "Previews and generated files"),
    MESSAGING(Routes.MESSAGING, "Messaging", "Connected channels"),
    WORKSPACE(Routes.WORKSPACE, "Workspace", "Server files"),
    PROFILES(Routes.PROFILES, "Profiles", "Connections and server profiles"),
    AGENTS(Routes.AGENTS, "Agents", "Live runs and processes"),
    STARMAP(Routes.STARMAP, "Starmap", "Run history"),
    INSIGHTS(Routes.INSIGHTS, "Insights", "Usage analytics"),
    MEMORY(Routes.MEMORY, "Memory", "Agent memory"),
    PAIRING(Routes.PAIRING, "Pairing", "Device approvals"),
    MCP(Routes.MCP, "MCP", "Model context servers"),
    TOOLS(Routes.TOOLS, "Tools", "Tool catalog"),
    SETTINGS(Routes.SETTINGS, "Settings", "Models, appearance, connection"),
}
