package com.dixa.messenger

import kotlinx.coroutines.flow.SharedFlow

/** High-level connection state of the realtime transport. */
enum class ConnectionState {
    Connecting,
    Connected,
    Disconnected,
}

/**
 * The realtime socket seam. The SDK talks only to this interface, so tests can
 * inject an in-memory `FakeTransport` instead of a real WebSocket.
 *
 * - [inboundMessages] emits each raw UTF-8 JSON text frame received from the
 *   server, exactly as received (parsing happens above this layer). It is a hot
 *   `replay = 0` flow: every currently-collecting coroutine receives every frame.
 * - [connectionState] emits transitions; a reconnect re-emits [ConnectionState.Connected].
 *   It replays the latest value to new collectors (`replay = 1`).
 */
interface Transport {
    val inboundMessages: SharedFlow<String>
    val connectionState: SharedFlow<ConnectionState>
    fun connect()
    fun disconnect()
    fun send(text: String)
}
