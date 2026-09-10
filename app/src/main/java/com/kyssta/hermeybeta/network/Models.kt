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
