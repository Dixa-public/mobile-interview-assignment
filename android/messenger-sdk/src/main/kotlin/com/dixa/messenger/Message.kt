package com.dixa.messenger

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who authored a chat message. Mirrors the `author` field of a `message` frame. */
@Serializable
enum class Author {
    @SerialName("agent")
    AGENT,

    @SerialName("user")
    USER,
}

/**
 * A single chat message decoded from a `message` wire frame.
 *
 * Wire shape (see protocol/PROTOCOL.md):
 * `{ "type":"message", "id":<string>, "author":"agent"|"user", "text":<string>, "sentAt":<ISO-8601 string> }`
 *
 * `sentAt` is kept as the raw ISO-8601 string (no date parsing) to keep the kit
 * dependency-light — the format is never inspected here.
 */
@Serializable
data class Message(
    val id: String,
    val author: Author,
    val text: String,
    val sentAt: String,
)
