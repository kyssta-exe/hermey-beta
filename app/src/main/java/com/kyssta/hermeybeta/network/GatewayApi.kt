package com.kyssta.hermeybeta.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Gateway session cookies survive across requests for the life of the process. */
object GatewayCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            list.removeAll { existing -> cookies.any { it.name == existing.name } }
            list.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        synchronized(list) {
            list.removeAll { it.expiresAt < System.currentTimeMillis() && it.persistent }
            return list.toList()
        }
    }

    fun clear() = store.clear()
}

class GatewayHttpException(val code: Int, val detail: String?) : IOException("HTTP $code${detail?.let { ": $it" } ?: ""}")

/**
 * REST client for the Hermes gateway dashboard surface. Endpoints verified
 * against a live gateway (contract test in the previous project):
 *   GET  /api/auth/providers
 *   POST /auth/password-login {provider, username, password} -> {ok} + cookie
 *   POST /api/auth/ws-ticket -> {ticket}
 *   GET  /api/auth/me
 *   GET  /api/sessions (+ PATCH/DELETE /api/sessions/{id})
 *   GET  /api/sessions/{id}/messages
 *   GET  /api/cron/jobs (+ POST .../pause|resume)
 *   GET  /api/skills (+ PUT /api/skills/toggle)
 *   GET  /api/memory
 *   GET  /api/model/info, /api/model/options; POST /api/model/set
 */
class GatewayApi(baseUrl: String, private val onUnauthorized: () -> Unit = {}) {
    private val base = normalizeRemoteBaseUrl(baseUrl)
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(GatewayCookieJar)
        .build()

    private suspend fun get(path: String, query: Map<String, String?> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(buildUrl(base, path, query)).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    if (resp.code == 401) runCatching { onUnauthorized() }
                    throw GatewayHttpException(resp.code, extractDetail(body))
                }
                body
            }
        }

    private suspend fun post(path: String, json: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(buildUrl(base, path))
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    if (resp.code == 401) runCatching { onUnauthorized() }
                    throw GatewayHttpException(resp.code, extractDetail(body))
                }
                body
            }
        }

    private suspend fun patch(path: String, json: JSONObject): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(buildUrl(base, path))
                .patch(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    if (resp.code == 401) runCatching { onUnauthorized() }
                    throw GatewayHttpException(resp.code, extractDetail(body))
                }
                body
            }
        }

    private suspend fun delete(path: String): String =
        deleteJson(path, null)

    private suspend fun deleteJson(path: String, json: JSONObject?): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(buildUrl(base, path))
            if (json != null) builder.delete(json.toString().toRequestBody("application/json".toMediaType()))
            else builder.delete()
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    if (resp.code == 401) runCatching { onUnauthorized() }
                    throw GatewayHttpException(resp.code, extractDetail(body))
                }
                body
            }
        }

    private suspend fun put(path: String, json: JSONObject): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(buildUrl(base, path))
                .put(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    if (resp.code == 401) runCatching { onUnauthorized() }
                    throw GatewayHttpException(resp.code, extractDetail(body))
                }
                body
            }
        }

    private fun extractDetail(body: String): String? = try {
        JSONObject(body).optString("detail").takeUnless { it.isBlank() }
    } catch (_: Exception) {
        null
    }

    // ── Auth ──────────────────────────────────────────────────────────────
    suspend fun authProviders(): List<AuthProvider> = parseAuthProviders(get("/api/auth/providers"))

    /** Returns true when the session cookie authenticates. */
    suspend fun passwordLogin(provider: String, username: String, password: String): Boolean {
        val body = post(
            "/auth/password-login",
            JSONObject()
                .put("provider", provider)
                .put("username", username)
                .put("password", password),
        )
        val ok = try {
            JSONObject(body).optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
        if (!ok) return false
        return try {
            authMe()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun authMe(): JSONObject = JSONObject(get("/api/auth/me"))

    /** Mint a single-use ~30s ticket for the /api/ws socket. */
    suspend fun wsTicket(): String {
        val body = post("/api/auth/ws-ticket")
        return JSONObject(body).optString("ticket")
            .takeUnless { it.isBlank() }
            ?: throw IOException("Server did not mint a WebSocket ticket")
    }

    /** Raw http URL host for display; validates the base parses. */
    fun host(): String = base.toHttpUrl().host

    // ── Sessions ──────────────────────────────────────────────────────────
    suspend fun sessions(): List<SessionSummary> = parseSessions(get("/api/sessions"))

    suspend fun messages(sessionId: String, limit: Int? = null): List<ChatMessage> =
        parseMessages(get("/api/sessions/${enc(sessionId)}/messages", mapOf("limit" to limit?.toString())))

    suspend fun updateSession(sessionId: String, patch: JSONObject): JSONObject =
        JSONObject(patch("/api/sessions/${enc(sessionId)}", patch))

    suspend fun deleteSession(sessionId: String) {
        delete("/api/sessions/${enc(sessionId)}")
    }

    // ── Cron ──────────────────────────────────────────────────────────────
    suspend fun crons(): List<CronJob> = parseCrons(get("/api/cron/jobs"))

    suspend fun pauseCron(jobId: String) {
        post("/api/cron/jobs/$jobId/pause")
    }

    suspend fun resumeCron(jobId: String) {
        post("/api/cron/jobs/$jobId/resume")
    }

    // ── Skills ────────────────────────────────────────────────────────────
    suspend fun skills(): List<SkillInfo> = parseSkills(get("/api/skills"))

    suspend fun toggleSkill(name: String, enabled: Boolean) {
        put("/api/skills/toggle", JSONObject().put("name", name).put("enabled", enabled))
    }

    // ── Memory / models ───────────────────────────────────────────────────
    suspend fun memory(): JSONObject = JSONObject(get("/api/memory"))

    suspend fun resetMemory(target: String = "all"): JSONObject =
        JSONObject(post("/api/memory/reset", JSONObject().put("target", target)))

    suspend fun modelInfo(): ModelInfo = parseModelInfo(get("/api/model/info"))

    suspend fun modelOptions(): List<Pair<String, List<String>>> =
        parseModelOptions(get("/api/model/options"))

    suspend fun setModel(model: String) {
        post("/api/model/set", JSONObject().put("model", model))
    }

    // ── Files (workspace browser) ─────────────────────────────────────────
    suspend fun files(path: String? = null): JSONObject =
        JSONObject(get("/api/files", mapOf("path" to path)))

    suspend fun readFile(path: String): JSONObject =
        JSONObject(get("/api/files/read", mapOf("path" to path)))

    suspend fun mkdir(path: String): JSONObject =
        JSONObject(post("/api/files/mkdir", JSONObject().put("path", path)))

    // ── Messaging ─────────────────────────────────────────────────────────
    suspend fun messagingPlatforms(): JSONObject =
        JSONObject(get("/api/messaging/platforms"))

    // ── Pairing ───────────────────────────────────────────────────────────
    suspend fun pairing(): JSONObject = JSONObject(get("/api/pairing"))

    suspend fun approvePairing(platform: String, code: String): JSONObject =
        JSONObject(
            post(
                "/api/pairing/approve",
                JSONObject().put("platform", platform).put("code", code),
            ),
        )

    suspend fun revokePairing(platform: String, userId: String) {
        post(
            "/api/pairing/revoke",
            JSONObject().put("platform", platform).put("user_id", userId),
        )
    }

    suspend fun clearPendingPairing() {
        post("/api/pairing/clear-pending")
    }

    // ── MCP ───────────────────────────────────────────────────────────────
    suspend fun mcpServers(): JSONObject = JSONObject(get("/api/mcp/servers"))

    suspend fun mcpCatalog(): JSONObject = JSONObject(get("/api/mcp/catalog"))

    suspend fun setMcpEnabled(name: String, enabled: Boolean) {
        put("/api/mcp/servers/${enc(name)}/enabled", JSONObject().put("enabled", enabled))
    }

    suspend fun deleteMcpServer(name: String) {
        delete("/api/mcp/servers/${enc(name)}")
    }

    suspend fun testMcpServer(name: String): JSONObject =
        JSONObject(post("/api/mcp/servers/${enc(name)}/test"))

    suspend fun installMcp(name: String, env: JSONObject = JSONObject(), enable: Boolean = true) {
        post(
            "/api/mcp/catalog/install",
            JSONObject().put("name", name).put("env", env).put("enable", enable),
        )
    }

    // ── Analytics ─────────────────────────────────────────────────────────
    suspend fun analyticsUsage(days: Int): JSONObject =
        JSONObject(get("/api/analytics/usage", mapOf("days" to days.toString())))

    suspend fun analyticsModels(days: Int): JSONObject =
        JSONObject(get("/api/analytics/models", mapOf("days" to days.toString())))

    // ── Server-side profiles ──────────────────────────────────────────────
    suspend fun profiles(): JSONObject = JSONObject(get("/api/profiles"))

    suspend fun activeProfile(): JSONObject = JSONObject(get("/api/profiles/active"))

    suspend fun switchProfile(name: String): JSONObject =
        JSONObject(post("/api/profiles/active", JSONObject().put("name", name)))

    // ── Kanban (tasks board plugin) ───────────────────────────────────────
    suspend fun kanbanBoard(): JSONObject = JSONObject(get("/api/plugins/kanban/board"))

    suspend fun createKanbanTask(title: String, body: String? = null): JSONObject =
        JSONObject(
            post(
                "/api/plugins/kanban/tasks",
                JSONObject().put("title", title).apply { body?.let { put("body", it) } },
            ),
        )

    suspend fun updateKanbanTask(taskId: String, patch: JSONObject): JSONObject =
        JSONObject(patch("/api/plugins/kanban/tasks/${enc(taskId)}", patch))

    suspend fun moveKanbanTask(taskId: String, status: String): JSONObject =
        updateKanbanTask(taskId, JSONObject().put("status", status))

    suspend fun kanbanComment(taskId: String, body: String): JSONObject =
        JSONObject(
            post("/api/plugins/kanban/tasks/${enc(taskId)}/comments", JSONObject().put("body", body)),
        )

    suspend fun kanbanTask(taskId: String): JSONObject =
        JSONObject(get("/api/plugins/kanban/tasks/${enc(taskId)}"))

    suspend fun deleteKanbanTask(taskId: String) {
        delete("/api/plugins/kanban/tasks/${enc(taskId)}")
    }

    // ── Auxiliary models ────────────────────────────────────────────────
    suspend fun auxiliaryModels(): JSONObject = JSONObject(get("/api/model/auxiliary"))

    /** `task` is optional in the desktop contract — main-scope callers must not send it. */
    suspend fun setModelAssignment(model: String, provider: String, scope: String, task: String): JSONObject =
        JSONObject(
            post(
                "/api/model/set",
                JSONObject().put("model", model).put("provider", provider).put("scope", scope)
                    .apply { if (task.isNotBlank()) put("task", task) },
            ),
        )

    // ── Server config (Settings → Workspace; PUT deep-merges, partial OK) ───
    suspend fun serverConfig(): JSONObject = JSONObject(get("/api/config"))

    suspend fun saveServerConfig(patch: JSONObject) {
        post("/api/config", JSONObject().put("config", patch))
    }

    // ── Toolsets (Settings → Tools; mirrors web_server.py) ──────────────────
    suspend fun toolsets(): List<ToolsetInfo> = parseToolsets(get("/api/tools/toolsets"))

    suspend fun setToolsetEnabled(name: String, enabled: Boolean) {
        put("/api/tools/toolsets/${enc(name)}", JSONObject().put("enabled", enabled))
    }

    suspend fun toolsetConfig(name: String): JSONObject =
        JSONObject(get("/api/tools/toolsets/${enc(name)}/config"))

    suspend fun setToolsetProvider(name: String, provider: String) {
        put("/api/tools/toolsets/${enc(name)}/provider", JSONObject().put("provider", provider))
    }

    /** Returns the server's {saved, skipped, is_set} report. */
    suspend fun setToolsetEnv(name: String, env: JSONObject): JSONObject =
        JSONObject(put("/api/tools/toolsets/${enc(name)}/env", JSONObject().put("env", env)))

    suspend fun revealEnv(key: String): String =
        JSONObject(post("/api/env/reveal", JSONObject().put("key", key))).optString("value")

    suspend fun clearEnv(key: String) {
        deleteJson("/api/env", JSONObject().put("key", key))
    }

    suspend fun toolsetModels(name: String, provider: String? = null): JSONObject =
        JSONObject(get("/api/tools/toolsets/${enc(name)}/models", mapOf("provider" to provider)))

    suspend fun setToolsetModel(name: String, model: String, provider: String? = null) {
        put(
            "/api/tools/toolsets/${enc(name)}/model",
            JSONObject().put("model", model).apply { provider?.let { put("provider", it) } },
        )
    }

    /** Returns {ok, name(action), ...} — poll [actionStatus] with name. */
    suspend fun runToolsetPostSetup(name: String, key: String): JSONObject =
        JSONObject(post("/api/tools/toolsets/${enc(name)}/post-setup", JSONObject().put("key", key)))

    suspend fun actionStatus(name: String, lines: Int = 60): JSONObject =
        JSONObject(get("/api/actions/${enc(name)}/status", mapOf("lines" to lines.toString())))

    // ── System (Command Center → System; mirrors desktop api/config.ts) ─────
    suspend fun status(): JSONObject = JSONObject(get("/api/status"))

    suspend fun logs(
        file: String? = null,
        lines: Int = 200,
        level: String? = null,
        search: String? = null,
    ): JSONObject = JSONObject(
        get(
            "/api/logs",
            mapOf(
                "file" to file,
                "lines" to lines.toString(),
                "level" to level?.takeUnless { it == "ALL" },
                "search" to search?.takeUnless { it.isBlank() },
            ),
        ),
    )

    // ── Starmap graph (REST contract stays /api/learning/* — see desktop) ───
    suspend fun starmapGraph(): JSONObject = JSONObject(get("/api/learning/graph"))

    // ── Webhooks (mirrors desktop api/messaging.ts) ─────────────────────────
    suspend fun webhooks(): JSONObject = JSONObject(get("/api/webhooks"))

    suspend fun enableWebhooks(): JSONObject = JSONObject(post("/api/webhooks/enable"))

    suspend fun createWebhook(body: JSONObject): JSONObject =
        JSONObject(post("/api/webhooks", body))

    suspend fun deleteWebhook(name: String) {
        delete("/api/webhooks/${enc(name)}")
    }

    suspend fun setWebhookEnabled(name: String, enabled: Boolean): JSONObject =
        JSONObject(put("/api/webhooks/${enc(name)}/enabled", JSONObject().put("enabled", enabled)))
}

fun gatewayErrorMessage(e: Throwable): String = when (e) {
    is GatewayHttpException -> when (e.code) {
        401 -> e.detail ?: "Invalid username or password"
        in 500..599 -> "Server error (${e.code})"
        else -> e.detail ?: "HTTP ${e.code}"
    }
    is IllegalArgumentException -> e.message ?: "Bad request"
    is IOException -> "Cannot reach server — check the URL and your connection"
    else -> e.message ?: "Unexpected error"
}
