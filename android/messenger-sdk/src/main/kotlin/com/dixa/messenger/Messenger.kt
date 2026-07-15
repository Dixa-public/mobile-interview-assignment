package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    }

    fun disconnect() {
        client.disconnect()
    }
}
