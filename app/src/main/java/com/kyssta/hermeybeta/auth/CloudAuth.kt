package com.kyssta.hermeybeta.auth

import com.kyssta.hermeybeta.network.GatewayCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/**
 * Hermes Cloud (Nous portal) auth — mirrors the desktop cloud flow:
 * portal sign-in once (Privy session), GET {portal}/api/agents discovery,
 * then a silent per-agent cascade that lands each gateway's own session
 * cookies. The phone's WebView is the isolated cookie surface (the
 * desktop's OAuth partition); cookies are harvested into the shared
 * OkHttp jar so REST + WS use them like any other login.
 */

// Canonical portal base. Mirrors desktop DEFAULT_NOUS_PORTAL_URL.
const val NOUS_PORTAL_BASE_URL = "https://portal.nousresearch.com"

// Cookie variants mirror desktop connection-config.ts.
val PRIVY_COOKIES = listOf("__Host-privy-token", "__Secure-privy-token", "privy-token", "privy-session")
val GATEWAY_SESSION_COOKIES = listOf(
    "__Host-hermes_session_at", "__Secure-hermes_session_at", "hermes_session_at",
    "__Host-hermes_session_rt", "__Secure-hermes_session_rt", "hermes_session_rt",
)

private fun cookieMap(header: String?): Map<String, String> {
    if (header.isNullOrBlank()) return emptyMap()
    return header.split(";").mapNotNull {
        val kv = it.trim().split("=", limit = 2)
        if (kv.size == 2 && kv[0].isNotBlank() && kv[1].isNotBlank()) kv[0] to kv[1] else null
    }.toMap()
}

fun cookieHeaderHas(header: String?, names: List<String>): Boolean {
    val present = cookieMap(header)
    return names.any { (present[it] ?: "").isNotBlank() }
}

/** Portal liveness: a non-empty privy-token (or variant) cookie. */
fun hasPrivySession(header: String?) = cookieHeaderHas(header, PRIVY_COOKIES)

/** Gateway liveness: a non-empty hermes_session access or refresh cookie. */
fun hasGatewaySession(header: String?) = cookieHeaderHas(header, GATEWAY_SESSION_COOKIES)

data class CloudAgent(
    val id: String = "",
    val name: String = "",
    val status: String = "unknown",
    val dashboardUrl: String? = null,
)

fun parseCloudAgents(root: JSONObject): List<CloudAgent> {
    val arr = root.optJSONArray("agents") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        CloudAgent(
            id = o.optString("id"),
            name = o.optString("name").ifBlank { o.optString("id") },
            status = o.optString("status").ifBlank { "unknown" },
            dashboardUrl = o.optString("dashboardUrl")
                .ifBlank { o.optString("dashboard_url") }
                .ifBlank { null },
        )
    }.filter { it.id.isNotBlank() }
}

data class CloudOrg(val id: String = "", val slug: String? = null, val name: String = "")

fun parseCloudOrgs(root: JSONObject): List<CloudOrg> {
    val arr = root.optJSONArray("orgs") ?: return emptyList()
    return List(arr.length()) { i ->
        val o = arr.optJSONObject(i) ?: JSONObject()
        CloudOrg(
            id = o.optString("id"),
            slug = o.optString("slug").ifBlank { null },
            name = o.optString("name").ifBlank { o.optString("id") },
        )
    }.filter { it.id.isNotBlank() }
}

/** Expire every cookie for one host (per-gateway forget without nuking portal login). */
fun clearHostCookies(baseUrl: String) {
    val url = try {
        baseUrl.toHttpUrl()
    } catch (_: Exception) {
        return
    }
    val header = try {
        android.webkit.CookieManager.getInstance().getCookie(baseUrl) ?: return
    } catch (_: Exception) {
        return
    }
    header.split(";").forEach { part ->
        val name = part.trim().substringBefore("=").trim()
        if (name.isNotBlank() && !name.startsWith("$")) {
            try {
                android.webkit.CookieManager.getInstance()
                    .setCookie(baseUrl, "$name=; Max-Age=0; Path=/")
            } catch (_: Exception) {
            }
        }
    }
}

/** Move WebView-harvested cookies into the shared OkHttp jar. */
fun injectCookies(baseUrl: String, cookieHeader: String) {
    val url = baseUrl.toHttpUrl()
    val cookies = cookieHeader.split(";").mapNotNull { part ->
        val kv = part.trim().split("=", limit = 2)
        if (kv.size != 2 || kv[0].isBlank() || kv[1].isBlank() || kv[0].startsWith("$")) {
            return@mapNotNull null
        }
        Cookie.Builder().name(kv[0]).value(kv[1]).hostOnlyDomain(url.host).path("/").apply {
            if (url.isHttps) secure()
            httpOnly()
        }.build()
    }
    if (cookies.isNotEmpty()) GatewayCookieJar.saveFromResponse(url, cookies)
}
