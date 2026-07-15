package com.dixa.messenger

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A decoded inbound event the SDK understands.
 */
internal sealed interface IncomingEvent {
    data class MessageReceived(val message: Message) : IncomingEvent
}

/**
 * Parses a single UTF-8 JSON text frame into an [IncomingEvent].
 *
 * The frame's string `type` field selects the shape of the rest of the object
 * (see protocol/PROTOCOL.md).
 */
internal class MessageParser {

    // `ignoreUnknownKeys` lets the extra `type` discriminator key be skipped when
    // deserializing the concrete `Message`. It does NOT make missing required
    // fields (like `sentAt`) tolerated — those still throw.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): IncomingEvent {
        val type = json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content
        return when (type) {
            "message" ->
                // Decode the concrete `Message` payload.
                IncomingEvent.MessageReceived(json.decodeFromString(Message.serializer(), raw))
            else ->
                throw IllegalArgumentException("Unrecognised event type: $type")
        }
    }
}
