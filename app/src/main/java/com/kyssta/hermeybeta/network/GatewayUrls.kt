package com.kyssta.hermeybeta.network

import java.net.URLEncoder

/**
 * Pure URL helpers — port of desktop electron/connection-config.ts
 * (normalizeRemoteBaseUrl / buildGatewayWsUrl / buildGatewayWsUrlWithTicket).
 * JVM-only so unit tests exercise the exact strings the app dials.
 */

/** Strip hash/query/trailing slashes; require http(s). Throws on misuse. */
fun normalizeRemoteBaseUrl(rawUrl: String): String {
    val value = rawUrl.trim()
    if (value.isEmpty()) throw IllegalArgumentException("Remote gateway URL is required.")
    val noFragment = value.substringBefore("#").substringBefore("?")
    val schemeEnd = noFragment.indexOf("://")
    if (schemeEnd < 0) throw IllegalArgumentException("Remote gateway URL is not valid: $rawUrl")
    val scheme = noFragment.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") {
        throw IllegalArgumentException("Remote gateway URL must be http:// or https://, got $scheme://")
    }
    return "$scheme://${noFragment.substring(schemeEnd + 3).trimEnd('/')}"
}

private fun wsBase(baseUrl: String): Pair<String, String> {
    val norm = normalizeRemoteBaseUrl(baseUrl)
    val rest = norm.substringAfter("://")
    val scheme = if (norm.startsWith("https://")) "wss" else "ws"
    val slash = rest.indexOf('/')
    val host = if (slash < 0) rest else rest.substring(0, slash)
    val prefix = if (slash < 0) "" else rest.substring(slash).trimEnd('/')
    return scheme to "$host$prefix"
}

/** ws(s)://host/prefix/api/ws?ticket=… — OAuth/session-cookie mode. */
fun buildGatewayWsUrlWithTicket(baseUrl: String, ticket: String): String {
    val (scheme, hostPrefix) = wsBase(baseUrl)
    return "$scheme://$hostPrefix/api/ws?ticket=${URLEncoder.encode(ticket, "UTF-8")}"
}

/** ws(s)://host/prefix/api/ws?token=… — static-token mode. */
fun buildGatewayWsUrl(baseUrl: String, token: String): String {
    val (scheme, hostPrefix) = wsBase(baseUrl)
    return "$scheme://$hostPrefix/api/ws?token=${URLEncoder.encode(token, "UTF-8")}"
}

fun buildUrl(baseUrl: String, path: String, query: Map<String, String?> = emptyMap()): String {
    val base = normalizeRemoteBaseUrl(baseUrl)
    val cleanPath = "/" + path.trim().trimStart('/')
    val params = query.entries
        .filter { it.value != null }
        .joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    return if (params.isNotBlank()) "$base$cleanPath?$params" else "$base$cleanPath"
}

/** Encode one URL path segment (mirrors encodeURIComponent at call sites). */
fun enc(segment: String): String = URLEncoder.encode(segment, "UTF-8")

/** Cloud gateway default: the Nous-hosted Hermes endpoint family. */
const val DEFAULT_CLOUD_BASE_URL = "https://hermes.get-hermes.ai"
