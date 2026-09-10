package com.kyssta.hermeybeta.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * JSON-RPC over /api/ws — the gateway chat transport.
 *
 * Frames:
 *   client -> {"jsonrpc":"2.0","id":N,"method":"...","params":{...}}
 *   server -> {"jsonrpc":"2.0","id":N,"result":{...}}              (RPC replies)
 *   server -> {"jsonrpc":"2.0","method":"event",
 *              "params":{"type":"message.delta","payload":{...}}}  (stream events)
 */
sealed interface WsFrame {
    data class Reply(val id: Int, val result: JSONObject?, val error: String?) : WsFrame
    data class Event(val type: String, val payload: JSONObject) : WsFrame
    data class Failure(val message: String) : WsFrame
}

class GatewayWs private constructor(val url: String) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // sockets never time out on read
        .pingInterval(30, TimeUnit.SECONDS)
        .cookieJar(GatewayCookieJar)
        .build()

    private val ids = AtomicInteger(1)
    private var ws: WebSocket? = null

    private val _frames = MutableSharedFlow<WsFrame>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<WsFrame> = _frames.asSharedFlow()

    fun nextId(): Int = ids.getAndIncrement()

    fun connect(onOpen: () -> Unit = {}) {
        ws = client.newWebSocket(
            Request.Builder().url(url).header("Origin", url.substringBefore("/api")).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = onOpen()
                override fun onMessage(webSocket: WebSocket, text: String) {
                    decode(text)?.let { _frames.tryEmit(it) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val msg = response?.let { "HTTP ${it.code}" } ?: (t.message ?: "connection failed")
                    _frames.tryEmit(WsFrame.Failure(msg))
                }
            },
        )
    }

    fun rpc(id: Int, method: String, params: JSONObject = JSONObject()): Boolean {
        val frame = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        return ws?.send(frame.toString()) ?: false
    }

    fun close() {
        ws?.close(1000, "bye")
        ws = null
    }

    companion object {
        fun withTicket(baseUrl: String, ticket: String): GatewayWs =
            GatewayWs(buildGatewayWsUrlWithTicket(baseUrl, ticket))

        fun withToken(baseUrl: String, token: String): GatewayWs =
            GatewayWs(buildGatewayWsUrl(baseUrl, token))

        internal fun decode(text: String): WsFrame? = try {
            val root = JSONObject(text)
            if (root.optString("method") == "event") {
                val params = root.optJSONObject("params") ?: return null
                val type = params.optString("type").takeUnless { it.isBlank() } ?: return null
                WsFrame.Event(type, params.optJSONObject("payload") ?: JSONObject())
            } else if (root.has("id")) {
                val err = root.optJSONObject("error")?.optString("message")
                WsFrame.Reply(root.optInt("id"), root.optJSONObject("result"), err)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
