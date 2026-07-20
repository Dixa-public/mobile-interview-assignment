package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface MessengerListener {
    fun onMessage(message: Message)

    fun onConnectionStateChanged(state: ConnectionState)

    fun onTypingChanged(isTyping: Boolean) {}
}

class Messenger internal constructor(
    private val config: MessengerConfig,
    private val transport: Transport,
    private val scope: CoroutineScope,
) {

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
        if (hasConnected) return
        hasConnected = true

        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is IncomingEvent.MessageReceived -> listener?.onMessage(event.message)
                    is IncomingEvent.TypingChanged -> listener?.onTypingChanged(event.isTyping)
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
