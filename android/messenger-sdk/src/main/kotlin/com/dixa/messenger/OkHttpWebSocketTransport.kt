package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Real transport backed by OkHttp's WebSocket. On an unexpected drop it emits
 * [ConnectionState.Disconnected] and then automatically re-opens the socket,
 * re-emitting [ConnectionState.Connecting] then [ConnectionState.Connected].
 *
 * OkHttp rewrites a `ws://`/`wss://` URL to `http`/`https` internally, so the
 * `ws://localhost:8080` default from [MessengerConfig] works directly.
 *
 * This backs only the optional/visual path (the sample app against the local
 * mock server). Unit tests exercise the SDK entirely through the in-memory
 * `FakeTransport`, so there's normally no need to modify this file. It is
 * intentionally simple and skips production concerns (thread-safe socket
 * access, reconnect backoff, send-error handling).
 */
class OkHttpWebSocketTransport(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val reconnectDelayMs: Long = 1000L,
) : Transport {

    private val inbound = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    private val state = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 8)

    override val inboundMessages: SharedFlow<String> = inbound.asSharedFlow()
    override val connectionState: SharedFlow<ConnectionState> = state.asSharedFlow()

    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var webSocket: WebSocket? = null
    @Volatile
    private var manuallyClosed = false

    override fun connect() {
        manuallyClosed = false
        openSocket()
    }

    override fun disconnect() {
        manuallyClosed = true
        webSocket?.close(NORMAL_CLOSURE, null)
        webSocket = null
        state.tryEmit(ConnectionState.Disconnected)
    }

    override fun send(text: String) {
        webSocket?.send(text)
    }

    private fun openSocket() {
        state.tryEmit(ConnectionState.Connecting)
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                state.tryEmit(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                inbound.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDrop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDrop()
            }
        })
    }

    private fun handleDrop() {
        if (manuallyClosed) return
        state.tryEmit(ConnectionState.Disconnected)
        // Naive auto-reconnect: after a delay, re-open and re-emit Connected. This
        // is what triggers RealtimeClient's re-subscribe path.
        reconnectScope.launch {
            delay(reconnectDelayMs)
            if (!manuallyClosed) openSocket()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
