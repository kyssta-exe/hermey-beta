package com.kyssta.hermeybeta.session

import com.kyssta.hermeybeta.auth.ServerConnection
import com.kyssta.hermeybeta.network.GatewayApi
import com.kyssta.hermeybeta.network.GatewayWs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide gateway session. Owns the active REST client and at most one
 * live chat socket — the mobile equivalent of the desktop's primary gateway.
 * Connection/mode switches re-home the workspace (stores cleared by their
 * ViewModels observing [generation]); the shell stays mounted.
 */
object SessionRepository {
    private val _connection = MutableStateFlow<ServerConnection?>(null)
    val connection: StateFlow<ServerConnection?> = _connection.asStateFlow()

    /** Bumped on every connection/mode switch so screens drop stale rows. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private val apis = mutableMapOf<String, GatewayApi>()
    var ws: GatewayWs? = null
        private set

    fun apiFor(conn: ServerConnection): GatewayApi =
        apis.getOrPut(conn.id) { GatewayApi(conn.baseUrl) }

    fun activate(conn: ServerConnection) {
        closeSocket()
        _connection.value = conn
        _generation.value += 1
    }

    fun attachSocket(socket: GatewayWs) {
        closeSocket()
        ws = socket
    }

    fun closeSocket() {
        ws?.close()
        ws = null
    }

    fun deactivate() {
        closeSocket()
        _connection.value = null
        _generation.value += 1
    }
}
