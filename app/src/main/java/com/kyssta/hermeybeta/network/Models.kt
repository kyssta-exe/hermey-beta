package com.kyssta.hermeybeta.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gateway model shapes. Every field is optional and every parser is tolerant:
 * unknown fields are ignored, missing fields become null/empty — the server
 * may add or rename fields without breaking this client.
 */

data class AuthProvider(
    val name: String? = null,
    val displayName: String? = null,
    val supportsPassword: Boolean? = null,
)

data class SessionSummary(
    val id: String? = null,
    val resolvedId: String? = null,
    val title: String? = null,
    val preview: String? = null,
    val startedAt: Double? = null,
    val lastActive: Double? = null,
    val messageCount: Int? = null,
    val model: String? = null,
    val source: String? = null,
    val archived: Boolean? = null,
    val pinned: Boolean? = null,
) {
    val stableId: String get() = resolvedId ?: id ?: ""
}

data class ChatMessage(
    val role: String? = null,
    val content: String? = null,
    val timestamp: Double? = null,
    val messageId: Long? = null,
    val toolName: String? = null,
)

data class CronJob(
    val id: String? = null,
    val name: String? = null,
    val prompt: String? = null,
    val schedule: String = "manual",
    val state: String? = null,
    val enabled: Boolean? = null,
    val pausedAt: Double? = null,
    val nextRunAt: Double? = null,
    val lastRunAt: Double? = null,
    val lastStatus: String? = null,
) {
    fun isPaused(): Boolean = pausedAt != null || enabled == false
}

data class SkillInfo(
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val category: String? = null,
    val usage: Int? = null,
)

data class ModelInfo(
    val model: String? = null,
    val provider: String? = null,
    val raw: JSONObject? = null,
)

private fun JSONObject.optD(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.optL(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun JSONObject.optI(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optS(key: String): String? =
    if (isNull(key)) null else optString(key).takeUnless { it == "null" }

private fun JSONObject.optB(key: String): Boolean? =
    if (isNull(key)) null else optBoolean(key)

fun parseAuthProviders(body: String): List<AuthProvider> {
    val root = JSONObject(body)
    val arr: JSONArray = root.optJSONArray("providers") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        AuthProvider(
            name = o.optS("name"),
            displayName = o.optS("display_name"),
            supportsPassword = o.optB("supports_password"),
        )
    }
}

fun parseSessions(body: String): List<SessionSummary> {
    val root = JSONObject(body)
    val arr: JSONArray = root.optJSONArray("sessions") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        SessionSummary(
            id = o.optS("id"),
            resolvedId = o.optS("resolved_id"),
            title = o.optS("title"),
            preview = o.optS("preview"),
            startedAt = o.optD("started_at"),
            lastActive = o.optD("last_active"),
            messageCount = o.optI("message_count"),
            model = o.optS("model"),
            source = o.optS("source"),
            archived = o.optB("archived"),
            pinned = o.optB("pinned"),
        )
    }
}

fun parseMessages(body: String): List<ChatMessage> {
    val root = JSONObject(body)
    val arr: JSONArray = root.optJSONArray("messages") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        ChatMessage(
            role = o.optS("role"),
            content = o.optS("content") ?: o.optS("text"),
            timestamp = o.optD("timestamp"),
            messageId = o.optL("id"),
            toolName = o.optS("tool_name"),
        )
    }
}

fun parseCrons(body: String): List<CronJob> {
    val trimmed = body.trim()
    val arr: JSONArray = if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
    } else {
        JSONObject(trimmed).optJSONArray("jobs") ?: return emptyList()
    }
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        val schedule = when (val s = o.opt("schedule")) {
            is JSONObject -> s.optS("display") ?: s.optS("expr") ?: "manual"
            is String -> s
            else -> o.optS("schedule_display") ?: "manual"
        }
        CronJob(
            id = o.optS("id"),
            name = o.optS("name"),
            prompt = o.optS("prompt"),
            schedule = schedule,
            state = o.optS("state"),
            enabled = o.optB("enabled"),
            pausedAt = o.optD("paused_at"),
            nextRunAt = o.optD("next_run_at"),
            lastRunAt = o.optD("last_run_at"),
            lastStatus = o.optS("last_status"),
        )
    }
}

fun parseSkills(body: String): List<SkillInfo> {
    val trimmed = body.trim()
    val arr: JSONArray = if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
    } else {
        JSONObject(trimmed).optJSONArray("skills") ?: return emptyList()
    }
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        SkillInfo(
            name = o.optS("name"),
            description = o.optS("description"),
            enabled = o.optB("enabled"),
            category = o.optS("category"),
            usage = o.optI("usage"),
        )
    }
}

fun parseModelInfo(body: String): ModelInfo {
    val root = JSONObject(body)
    return ModelInfo(
        model = root.optS("model") ?: root.optS("current_model"),
        provider = root.optS("provider"),
        raw = root,
    )
}

fun parseModelOptions(body: String): List<Pair<String, List<String>>> {
    // Shape varies by server version; accept {providers:[{name,models:[]}]},
    // {options:{provider:[models]}}, or a flat {provider:[models]} map.
    val root = try {
        JSONObject(body)
    } catch (_: Exception) {
        return emptyList()
    }
    root.optJSONArray("providers")?.let { arr ->
        return List(arr.length()) { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            val name = o.optS("name") ?: o.optS("id") ?: "unknown"
            val models = o.optJSONArray("models")?.let { m ->
                List(m.length()) { j -> m.optString(j) }
            } ?: emptyList()
            name to models
        }
    }
    val out = mutableListOf<Pair<String, List<String>>>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        if (k == "options") {
            val inner = root.optJSONObject("options") ?: continue
            val ik = inner.keys()
            while (ik.hasNext()) {
                val p = ik.next()
                out += p to inner.optJSONArray(p)?.let { m -> List(m.length()) { j -> m.optString(j) } }.orEmpty()
            }
        } else {
            root.optJSONArray(k)?.let { m ->
                out += k to List(m.length()) { j -> m.optString(j) }
            }
        }
    }
    return out
}

// ─── Files ──────────────────────────────────────────────────────────────────
data class FileEntry(
    val name: String = "",
    val path: String = "",
    val isDirectory: Boolean = false,
    val size: Long? = null,
    val mime: String? = null,
)

data class FileListing(
    val path: String = "",
    val parent: String? = null,
    val entries: List<FileEntry> = emptyList(),
)

fun parseFileListing(root: JSONObject): FileListing {
    val arr = root.optJSONArray("entries") ?: JSONArray()
    return FileListing(
        path = root.optS("path") ?: "",
        parent = root.optS("parent"),
        entries = List(arr.length()) { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            FileEntry(
                name = o.optS("name") ?: "",
                path = o.optS("path") ?: "",
                isDirectory = o.optBoolean("is_directory", false),
                size = o.optL("size"),
                mime = o.optS("mime_type"),
            )
        },
    )
}

// ─── Messaging ──────────────────────────────────────────────────────────────
data class MsgPlatform(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val state: String = "",
    val errorMessage: String? = null,
)

fun parseMsgPlatforms(root: JSONObject): List<MsgPlatform> {
    val arr = root.optJSONArray("platforms") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        MsgPlatform(
            id = o.optS("id") ?: "",
            name = o.optS("name") ?: o.optS("id") ?: "",
            description = o.optS("description"),
            enabled = o.optBoolean("enabled", false),
            configured = o.optBoolean("configured", false),
            state = o.optS("state") ?: "",
            errorMessage = o.optS("error_message"),
        )
    }
}

// ─── Pairing ────────────────────────────────────────────────────────────────
data class PairUser(
    val platform: String = "",
    val userId: String = "",
    val userName: String? = null,
    val code: String? = null,
    val ageMinutes: Int? = null,
) {
    val label: String get() = userName ?: userId
}

private fun JSONObject.toPairUser(): PairUser = PairUser(
    platform = optS("platform") ?: "",
    userId = optS("user_id") ?: "",
    userName = optS("user_name"),
    code = optS("code"),
    ageMinutes = optI("age_minutes"),
)

fun parsePairing(root: JSONObject): Pair<List<PairUser>, List<PairUser>> {
    fun list(key: String): List<PairUser> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i -> (arr.optJSONObject(i) ?: JSONObject()).toPairUser() }
    }
    return list("pending") to list("approved")
}

// ─── MCP ────────────────────────────────────────────────────────────────────
data class McpServer(
    val name: String = "",
    val transport: String = "",
    val enabled: Boolean = false,
    val tools: List<String> = emptyList(),
)

fun parseMcpServers(root: JSONObject): List<McpServer> {
    val arr = root.optJSONArray("servers") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        McpServer(
            name = o.optS("name") ?: "",
            transport = o.optS("transport") ?: "",
            enabled = o.optBoolean("enabled", false),
            tools = o.optJSONArray("tools")?.let { t -> List(t.length()) { j -> t.optString(j) } }.orEmpty(),
        )
    }
}

// ─── Analytics ──────────────────────────────────────────────────────────────
data class ModelStat(
    val model: String = "",
    val sessions: Int = 0,
    val calls: Int = 0,
    val input: Long = 0,
    val output: Long = 0,
    val cost: Double = 0.0,
)

fun parseModelsAnalytics(root: JSONObject): Pair<List<ModelStat>, JSONObject?> {
    val arr = root.optJSONArray("models") ?: return emptyList<ModelStat>() to root.optJSONObject("totals")
    val models = List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        ModelStat(
            model = o.optS("model") ?: o.optS("name") ?: "",
            sessions = o.optI("sessions") ?: o.optI("total_sessions") ?: 0,
            calls = o.optI("calls") ?: o.optI("total_api_calls") ?: 0,
            input = o.optL("input") ?: o.optL("total_input") ?: 0,
            output = o.optL("output") ?: o.optL("total_output") ?: 0,
            cost = o.optD("cost") ?: o.optD("total_actual_cost") ?: o.optD("total_estimated_cost") ?: 0.0,
        )
    }
    return models to root.optJSONObject("totals")
}

// ─── Server-side profiles ───────────────────────────────────────────────────
data class ServerProfile(
    val name: String = "",
    val isDefault: Boolean = false,
    val model: String? = null,
    val provider: String? = null,
    val skillCount: Int = 0,
    val gatewayRunning: Boolean = false,
    val description: String = "",
)

fun parseProfiles(root: JSONObject): List<ServerProfile> {
    val arr = root.optJSONArray("profiles") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        ServerProfile(
            name = o.optS("name") ?: "",
            isDefault = o.optBoolean("is_default", false),
            model = o.optS("model"),
            provider = o.optS("provider"),
            skillCount = o.optI("skill_count") ?: 0,
            gatewayRunning = o.optBoolean("gateway_running", false),
            description = o.optS("description") ?: "",
        )
    }
}

// ─── Spawn tree (starmap data) ──────────────────────────────────────────────
data class SpawnSnapshot(
    val sessionId: String = "",
    val startedAt: Double? = null,
    val finishedAt: Double? = null,
    val subagentCount: Int = 0,
    val path: String? = null,
)

fun parseSpawnEntries(root: JSONObject): List<SpawnSnapshot> {
    val arr = when {
        root.has("entries") -> root.optJSONArray("entries")
        root.has("snapshots") -> root.optJSONArray("snapshots")
        else -> null
    } ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        SpawnSnapshot(
            sessionId = o.optS("session_id") ?: "",
            startedAt = o.optD("started_at"),
            finishedAt = o.optD("finished_at"),
            subagentCount = o.optJSONArray("subagents")?.length() ?: 0,
            path = o.optS("path"),
        )
    }
}
