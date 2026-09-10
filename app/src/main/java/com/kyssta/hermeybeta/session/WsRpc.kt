package com.kyssta.hermeybeta.session

import com.kyssta.hermeybeta.network.GatewayWs
import com.kyssta.hermeybeta.network.WsFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Request/response RPC for screens outside chat. Shares the session socket:
 * attaches (minting a fresh single-use ticket) only when no live socket
 * exists. Replies correlate by id; timeouts throw [IOException].
 */
object WsRpc {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject?>>()
    private var collecting: GatewayWs? = null

    suspend fun call(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 20_000,
    ): JSONObject {
        val conn = SessionRepository.connection.value ?: throw IOException("No gateway selected")
        var socket = SessionRepository.ws
        if (socket == null) {
            val api = SessionRepository.apiFor(conn)
            socket = GatewayWs.withTicket(conn.baseUrl, api.wsTicket())
            SessionRepository.attachSocket(socket)
            socket.connect {}
            // ponytail: fixed handshake grace; upgrade to onOpen-gated dial when reconnect logic lands.
            delay(800)
        }
        val s = socket
        synchronized(this) {
            if (collecting !== s) {
                collecting = s
                scope.launch { collectLoop(s) }
            }
        }
        val id = s.nextId()
        val d = CompletableDeferred<JSONObject?>()
        pending[id] = d
        try {
            if (!s.rpc(id, method, params)) throw IOException("Socket not connected")
            return withTimeoutOrNull(timeoutMs) { d.await() }
                ?: throw IOException("Request timed out: $method")
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun collectLoop(socket: GatewayWs) {
        try {
            socket.frames.collect { frame ->
                when (frame) {
                    is WsFrame.Reply -> pending.remove(frame.id)?.complete(frame.result)
                    is WsFrame.Failure -> {
                        pending.entries.forEach { it.value.completeExceptionally(IOException(frame.message)) }
                        pending.clear()
                    }
                    else -> Unit // stream events belong to chat's own collector
                }
            }
        } catch (_: Exception) {
        }
    }
}
