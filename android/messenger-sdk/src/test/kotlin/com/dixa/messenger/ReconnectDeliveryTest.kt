package com.dixa.messenger

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Exactly-once delivery across disconnect-reconnect cycles.
 *
 * The real-time connection drops and re-establishes often; the host app must
 * still be notified exactly once per event. These tests fail against a client
 * that stacks a new inbound collector on every reconnect (each accumulated
 * collector re-delivers every subsequent frame).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectDeliveryTest {

    /** Builds a connected [Messenger] on an eager test scope; caller cancels the scope. */
    private fun TestScope.startMessenger(
        transport: FakeTransport,
        listener: MessengerListener,
    ): CoroutineScope {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = Messenger(
            config = MessengerConfig.Builder().build(),
            transport = transport,
            scope = scope,
        )
        messenger.setListener(listener)
        messenger.connect()
        return scope
    }

    @Test
    fun `messages are delivered exactly once across multiple reconnect cycles`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound(
            """{"type":"message","id":"m-1","author":"agent","text":"Before the drop","sentAt":"2026-07-14T10:00:00Z"}"""
        )

        // Two full drop/re-establish cycles, as happens with bad signal or backgrounding.
        transport.simulateDisconnect()
        transport.simulateReconnect()
        transport.simulateDisconnect()
        transport.simulateReconnect()

        transport.pushInbound(
            """{"type":"message","id":"m-2","author":"agent","text":"After two reconnects","sentAt":"2026-07-14T10:00:20Z"}"""
        )

        // Exactly one callback per message — no duplicates from the reconnects.
        verify(exactly = 1) { listener.onMessage(match { it.id == "m-1" }) }
        verify(exactly = 1) { listener.onMessage(match { it.id == "m-2" }) }

        scope.cancel()
    }

    @Test
    fun `a typing event after a reconnect is delivered exactly once`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.simulateDisconnect()
        transport.simulateReconnect()

        transport.pushInbound("""{"type":"typing","isTyping":true}""")

        verify(exactly = 1) { listener.onAgentTypingChanged(true) }

        scope.cancel()
    }
}
