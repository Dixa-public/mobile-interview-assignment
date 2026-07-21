package com.dixa.messenger

import kotlinx.serialization.Serializable

/** Wire shape of a `typing` frame: `{ "type":"typing", "isTyping":<bool> }`. */
@Serializable
internal data class TypingFrame(
    val isTyping: Boolean,
)