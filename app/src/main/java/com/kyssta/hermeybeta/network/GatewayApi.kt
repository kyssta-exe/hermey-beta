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
class GatewayApi(baseUrl: String) {
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
                if (!resp.isSuccessful) throw GatewayHttpException(resp.code, extractDetail(body))
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
                if (!resp.isSuccessful) throw GatewayHttpException(resp.code, extractDetail(body))
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
                if (!resp.isSuccessful) throw GatewayHttpException(resp.code, extractDetail(body))
                body
            }
        }

    private suspend fun delete(path: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(buildUrl(base, path)).delete().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw GatewayHttpException(resp.code, extractDetail(body))
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
                if (!resp.isSuccessful) throw GatewayHttpException(resp.code, extractDetail(body))
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
        parseMessages(get("/api/sessions/$sessionId/messages", mapOf("limit" to limit?.toString())))

    suspend fun updateSession(sessionId: String, patch: JSONObject): JSONObject =
        JSONObject(patch("/api/sessions/$sessionId", patch))

    suspend fun deleteSession(sessionId: String) {
        delete("/api/sessions/$sessionId")
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

    suspend fun modelInfo(): ModelInfo = parseModelInfo(get("/api/model/info"))

    suspend fun modelOptions(): List<Pair<String, List<String>>> =
        parseModelOptions(get("/api/model/options"))

    suspend fun setModel(model: String) {
        post("/api/model/set", JSONObject().put("model", model))
    }
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
