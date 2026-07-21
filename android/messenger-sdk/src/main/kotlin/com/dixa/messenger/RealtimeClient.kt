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

    /** Whether the last typing event delivered to the host said the agent is typing. */
    private var agentIsTyping = false

    fun start() {
        // Single long-lived collector of inbound frames. The transport's
        // `inboundMessages` flow outlives individual socket connections (the
        // transport reconnects behind it), so the subscription's lifetime is
        // the client's, not the connection's. Exactly one collector means each
        // frame is delivered to the host exactly once — re-subscribing on every
        // `Connected` transition is what used to stack duplicate collectors.
        scope.launch {
            transport.inboundMessages.collect { raw ->
                val event = parser.parse(raw)
                if (event == null) {
                    // Contract drift (unknown type / missing field / bad JSON):
                    // skip this frame only; delivery of later frames continues.
                    log("ignored unrecognised or malformed frame: $raw")
                } else {
                    if (event is IncomingEvent.TypingChanged) {
                        agentIsTyping = event.isTyping
                    }
                    eventsFlow.emit(event)
                }
            }
        }
        // Single long-lived collector of connection-state transitions.
        scope.launch {
            transport.connectionState.collect { state ->
                if (state == ConnectionState.Disconnected && agentIsTyping) {
                    // Typing is ephemeral presence state: after a drop we can no
                    // longer know the agent is still typing, so tell the host it
                    // stopped rather than leave a stale indicator on screen.
                    agentIsTyping = false
                    eventsFlow.emit(IncomingEvent.TypingChanged(isTyping = false))
                }
                stateFlow.emit(state)
            }
        }
        transport.connect()
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
