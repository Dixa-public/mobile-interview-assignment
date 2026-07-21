package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Host-app callbacks. Register one via [Messenger.setListener].
 *
 * Callbacks fire on whatever thread the SDK's coroutine scope delivers on (the
 * real transport uses `Dispatchers.Default`). Host apps that update UI must
 * marshal to the main thread themselves — the sample app shows how.
 */
interface MessengerListener {
    /** Called for every chat `message` received from the backend. */
    fun onMessage(message: Message)

    /** Called on every connection-state transition. */
    fun onConnectionStateChanged(state: ConnectionState)

    /**
     * Called when the agent starts (`true`) or stops (`false`) typing.
     *
     * Also called with `false` if the connection drops while the agent was
     * typing, so a host app never shows a stale indicator.
     *
     * Default no-op so existing listener implementations keep compiling —
     * override it to show an "Agent is typing…" indicator.
     */
    fun onAgentTypingChanged(isTyping: Boolean) {}
}

/**
 * The public entry point a host app embeds. Configure it with a [MessengerConfig],
 * register a [MessengerListener], then [connect].
 */
class Messenger internal constructor(
    private val config: MessengerConfig,
    private val transport: Transport,
    private val scope: CoroutineScope,
) {

    /** Public constructor: builds a real WebSocket transport and a default scope. */
    constructor(config: MessengerConfig) : this(
        config = config,
        transport = OkHttpWebSocketTransport(config.serverUrl),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val client = RealtimeClient(
        transport = transport,
        scope = scope,
        loggingEnabled = config.loggingEnabled,
    )

    private var listener: MessengerListener? = null
    private var hasConnected = false

    // ISO-8601 UTC stamp for locally-echoed sent messages. SimpleDateFormat
    // (not java.time) so it works on the SDK's minSdk 24 without desugaring.
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun setListener(listener: MessengerListener) {
        this.listener = listener
    }

    fun connect() {
        // Wire the SDK's callbacks exactly once; calling connect() again is a
        // no-op rather than stacking duplicate collectors.
        if (hasConnected) return
        hasConnected = true

        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is IncomingEvent.MessageReceived -> listener?.onMessage(event.message)
                    is IncomingEvent.TypingChanged -> listener?.onAgentTypingChanged(event.isTyping)
                }
            }
        }
        scope.launch {
            client.connectionState.collect { state ->
                listener?.onConnectionStateChanged(state)
            }
        }
        client.start()
    }

    fun send(text: String) {
        client.send(text)
        // Optimistic local echo: the backend never sends the user's own message
        // back as a `user` frame, so the SDK surfaces it to the host immediately.
        // Delivered on the same listener path as inbound messages, so host apps
        // render sent and received messages identically without special-casing.
        listener?.onMessage(
            Message(
                id = "local-${System.currentTimeMillis()}",
                author = Author.USER,
                text = text,
                sentAt = isoFormat.format(Date()),
            )
        )
    }

    fun disconnect() {
        client.disconnect()
    }
}
