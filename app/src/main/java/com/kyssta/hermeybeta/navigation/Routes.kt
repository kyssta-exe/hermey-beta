package com.kyssta.hermeybeta.navigation

/**
 * Route table — mirrors desktop src/app/routes.ts APP_ROUTES, adapted to a
 * mobile nav graph. Chat is the home surface; settings/profiles/overlays are
 * full screens (no floating OverlayView cards on mobile).
 */
object Routes {
    const val CONNECT = "connect"
    const val CHAT = "chat?sessionId={sessionId}"
    const val SESSIONS = "sessions"
    const val SKILLS = "skills"
    const val MESSAGING = "messaging"
    const val ARTIFACTS = "artifacts"
    const val CRON = "cron"
    const val PROFILES = "profiles"
    const val AGENTS = "agents"
    const val STARMAP = "starmap"
    const val SETTINGS = "settings"

    fun chat(sessionId: String = "") = if (sessionId.isBlank()) "chat?sessionId=" else "chat?sessionId=$sessionId"
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
    PROFILES(Routes.PROFILES, "Gateways", "Remote and cloud connections"),
    AGENTS(Routes.AGENTS, "Agents", "Subagents and runs"),
    STARMAP(Routes.STARMAP, "Starmap", "Session graph"),
    SETTINGS(Routes.SETTINGS, "Settings", "Models, appearance, connection"),
}
