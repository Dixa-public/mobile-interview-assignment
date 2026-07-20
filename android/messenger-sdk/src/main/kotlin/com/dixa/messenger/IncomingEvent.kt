package com.dixa.messenger

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A decoded inbound event the SDK understands.
 */
internal sealed interface IncomingEvent {
    data class MessageReceived(val message: Message) : IncomingEvent
    data class TypingChanged(val isTyping: Boolean) : IncomingEvent
}

/**
 * Parses a single UTF-8 JSON text frame into an [IncomingEvent].
 *
 * The frame's string `type` field selects the shape of the rest of the object
 * (see protocol/PROTOCOL.md). The backend evolves independently of installed
 * SDK versions, so this must degrade gracefully instead of throwing: a `type`
 * this SDK doesn't recognise, or a recognised `type` with an unexpected shape
 * (missing/malformed fields), returns `null` rather than raising — the caller
 * skips (and may log) those frames instead of tearing down the connection.
 */
internal class MessageParser {

    // `ignoreUnknownKeys` lets unexpected extra fields (including the `type`
    // discriminator) be skipped when deserializing a concrete payload.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): IncomingEvent? = try {
        val root = json.parseToJsonElement(raw).jsonObject
        when (root["type"]?.jsonPrimitive?.content) {
            "message" -> IncomingEvent.MessageReceived(json.decodeFromString(Message.serializer(), raw))
            "typing" -> root["isTyping"]?.jsonPrimitive?.boolean?.let { IncomingEvent.TypingChanged(it) }
            // Unknown/unsupported type (e.g. `presence`) — the installed SDK
            // version has never seen it. Ignore rather than fail.
            else -> null
        }
    } catch (e: SerializationException) {
        // Known `type` but the payload doesn't match the expected shape
        // (e.g. a `message` missing `sentAt`).
        null
    } catch (e: IllegalArgumentException) {
        // Malformed JSON, or the `type` field itself is missing/not a string.
        null
    }
}
