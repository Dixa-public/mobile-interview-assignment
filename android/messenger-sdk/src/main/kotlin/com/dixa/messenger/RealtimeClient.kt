package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Subscription/state layer over a [Transport]. Parses inbound frames into
 * [IncomingEvent]s and re-publishes connection state, all on the injected [scope]
 * (production passes a `Dispatchers.Default` scope; tests pass `backgroundScope`).
 */
internal class RealtimeClient(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val loggingEnabled: Boolean = false,
) {

    private val parser = MessageParser()

    private val eventsFlow = MutableSharedFlow<IncomingEvent>(replay = 0, extraBufferCapacity = 64)
    private val stateFlow = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 8)

    /** Parsed inbound events for the facade above. */
    val events: SharedFlow<IncomingEvent> = eventsFlow.asSharedFlow()

    /** Connection state re-published for the facade above. */
    val connectionState: SharedFlow<ConnectionState> = stateFlow.asSharedFlow()

    fun start() {
        // Single long-lived collector of connection-state transitions.
        scope.launch {
            transport.connectionState.collect { state ->
                stateFlow.emit(state)
                if (state == ConnectionState.Connected) {
                    subscribeToInbound()
                }
            }
        }
        transport.connect()
    }

    private fun subscribeToInbound() {
        // Collect inbound frames from the transport.
        scope.launch {
            try {
                transport.inboundMessages.collect { raw ->
                    eventsFlow.emit(parser.parse(raw))
                }
            } catch (e: Exception) {
                // A parse failure ends this collection; the surrounding scope keeps running.
                log("inbound collection ended: ${e.message}")
            }
        }
    }

    fun send(text: String) {
        val frame = OutgoingMessage(type = "sendMessage", text = text)
        transport.send(Json.encodeToString(OutgoingMessage.serializer(), frame))
    }

    fun disconnect() {
        transport.disconnect()
    }

    private fun log(message: String) {
        if (loggingEnabled) println("[Messenger] $message")
    }
}

/** The single outbound frame the SDK sends: `{ "type":"sendMessage", "text":<string> }`. */
@Serializable
internal data class OutgoingMessage(
    val type: String,
    val text: String,
)
