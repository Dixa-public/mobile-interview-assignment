package com.dixa.messenger

/**
 * Fluent configuration for [Messenger], built via [Builder], e.g.:
 * ```
 * val config = MessengerConfig.Builder()
 *     .serverUrl("ws://localhost:8080")
 *     .loggingEnabled(true)
 *     .build()
 * ```
 */
class MessengerConfig private constructor(
    val serverUrl: String,
    val loggingEnabled: Boolean,
) {
    class Builder {
        // Defaults to the shared local mock server used by the sample app.
        private var serverUrl: String = "ws://localhost:8080"
        private var loggingEnabled: Boolean = false

        fun serverUrl(url: String): Builder = apply { this.serverUrl = url }

        fun loggingEnabled(enabled: Boolean): Builder = apply { this.loggingEnabled = enabled }

        fun build(): MessengerConfig = MessengerConfig(serverUrl, loggingEnabled)
    }
}
