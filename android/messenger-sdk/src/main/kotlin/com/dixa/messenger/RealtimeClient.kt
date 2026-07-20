package com.dixa.messenger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class RealtimeClient(
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val loggingEnabled: Boolean = false,
) {

    private val parser = MessageParser()

    private val eventsFlow = MutableSharedFlow<IncomingEvent>(replay = 0, extraBufferCapacity = 64)
    private val stateFlow = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 8)

    val events: SharedFlow<IncomingEvent> = eventsFlow.asSharedFlow()

    val connectionState: SharedFlow<ConnectionState> = stateFlow.asSharedFlow()

    fun start() {
        scope.launch {
            transport.connectionState.collect { state -> stateFlow.emit(state) }
        }
        subscribeToInbound()
        transport.connect()
    }

    private fun subscribeToInbound() {
        scope.launch {
            transport.inboundMessages.collect { raw ->
                val event = try {
                    parser.parse(raw)
                } catch (e: Exception) {
                    log("dropping unparseable frame: ${e.message}")
                    null
                }
                if (event != null) eventsFlow.emit(event)
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

@Serializable
internal data class OutgoingMessage(
    val type: String,
    val text: String,
)
