package com.dixa.messenger

import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example tests showing how to drive the SDK with [FakeTransport].
 *
 * These are a starting point to build on. They cover only the happy path —
 * extend them to cover reconnect and malformed-input handling.
 *
 * The SDK's internal collectors are launched on the [CoroutineScope] passed
 * into [Messenger]. Building that scope on an [UnconfinedTestDispatcher] makes
 * those collectors run eagerly (rather than being queued behind the test
 * scheduler), so delivery through the hot [kotlinx.coroutines.flow.SharedFlow]
 * is deterministic without any `advanceUntilIdle()` timing games.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDeliveryTest {

    @Test
    fun `a message frame reaches the listener`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = Messenger(
            config = MessengerConfig.Builder().build(),
            transport = transport,
            scope = scope,
        )
        messenger.setListener(listener)

        messenger.connect()

        transport.pushInbound(
            """{"type":"message","id":"m-1","author":"agent","text":"Hi there","sentAt":"2026-07-14T10:00:00Z"}"""
        )

        verify(exactly = 1) {
            listener.onMessage(
                match { it.id == "m-1" && it.author == Author.AGENT && it.text == "Hi there" }
            )
        }

        scope.cancel()
    }

    @Test
    fun `an outgoing message is forwarded as a sendMessage frame`() = runTest {
        val transport = FakeTransport()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = Messenger(
            config = MessengerConfig.Builder().build(),
            transport = transport,
            scope = scope,
        )

        // Turbine makes the connection lifecycle easy to assert on.
        transport.connectionState.test {
            messenger.connect()
            assertEquals(ConnectionState.Connecting, awaitItem())
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        messenger.send("Hello agent")

        assertEquals(1, transport.sentFrames.size)
        val frame = Json.parseToJsonElement(transport.sentFrames.single()).jsonObject
        assertEquals("sendMessage", frame["type"]?.jsonPrimitive?.content)
        assertEquals("Hello agent", frame["text"]?.jsonPrimitive?.content)

        scope.cancel()
    }
}
