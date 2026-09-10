package com.kyssta.hermeybeta.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Saved gateway connections. Remote (self-hosted) and cloud (Nous-hosted)
 * only — mobile never runs Hermes itself, so there is no local mode.
 * Secrets live in the encrypted prefs; session cookies live in
 * [com.kyssta.hermeybeta.network.GatewayCookieJar] for the process lifetime.
 */
enum class GatewayMode { REMOTE, CLOUD }

data class ServerConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val mode: GatewayMode = GatewayMode.REMOTE,
    val baseUrl: String = "",
    val username: String? = null,
) {
    val displayName: String get() = name.ifBlank { baseUrl.substringAfter("://").substringBefore("/") }
}

class ConnectionStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _connections = MutableStateFlow(load())
    val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString("active_id", null))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun active(): ServerConnection? = _connections.value.find { it.id == _activeId.value }

    fun save(conn: ServerConnection): ServerConnection {
        val list = _connections.value.toMutableList()
        val i = list.indexOfFirst { it.id == conn.id }
        if (i >= 0) list[i] = conn else list += conn
        persist(list)
        _connections.value = list
        return conn
    }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit { putString("active_id", id) }
    }

    fun remove(id: String) {
        val list = _connections.value.filterNot { it.id == id }
        persist(list)
        _connections.value = list
        if (_activeId.value == id) setActive(list.firstOrNull()?.id)
    }

    private fun load(): List<ServerConnection> {
        val raw = prefs.getString("servers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.optJSONObject(i) ?: JSONObject()
                ServerConnection(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    mode = try {
                        GatewayMode.valueOf(o.optString("mode").ifBlank { "REMOTE" })
                    } catch (_: Exception) {
                        GatewayMode.REMOTE
                    },
                    baseUrl = o.optString("baseUrl"),
                    username = o.optString("username").takeUnless { it.isBlank() },
                )
            }.filter { it.baseUrl.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<ServerConnection>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("mode", it.mode.name)
                    .put("baseUrl", it.baseUrl)
                    .put("username", it.username),
            )
        }
        prefs.edit { putString("servers", arr.toString()) }
    }
}
