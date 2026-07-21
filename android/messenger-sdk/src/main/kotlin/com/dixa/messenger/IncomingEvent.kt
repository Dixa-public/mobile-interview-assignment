package com.dixa.messenger

import kotlinx.serialization.json.Json
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
 * (see protocol/PROTOCOL.md).
 *
 * The backend evolves independently of installed SDK versions, so frames this
 * SDK cannot understand are expected, not exceptional. [parse] therefore never
 * throws: it returns `null` for any frame that cannot be turned into a known
 * event, and the caller decides how to log/skip it.
 */
internal class MessageParser {

    // `ignoreUnknownKeys` lets the extra `type` discriminator key (and any
    // fields a newer backend adds) be skipped when deserializing the concrete
    // payload. Missing required fields (like `sentAt`) still throw inside
    // `decodeFromString` — the catch below turns that into a `null`.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): IncomingEvent? {
        return try {
            val type = json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content
            when (type) {
                "message" ->
                    // Decode the concrete `Message` payload.
                    IncomingEvent.MessageReceived(json.decodeFromString(Message.serializer(), raw))
                "typing" ->
                    IncomingEvent.TypingChanged(
                        json.decodeFromString(TypingFrame.serializer(), raw).isTyping
                    )
                else ->
                    // Unknown `type`: a newer backend talking to an older SDK.
                    // Ignore the frame; the connection stays healthy.
                    null
            }
        } catch (e: Exception) {
            // Malformed JSON, a non-['/object frame, or a known type missing a
            // required field. `parse` never suspends, so no risk of swallowing
            // a CancellationException here.
            null
        }
    }
}
