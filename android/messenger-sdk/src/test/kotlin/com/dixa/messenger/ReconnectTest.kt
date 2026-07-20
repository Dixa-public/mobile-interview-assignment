package com.dixa.messenger

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Proves the host app is notified exactly once per event across
 * disconnect/reconnect cycles.
 *
 * `RealtimeClient` used to resubscribe to `transport.inboundMessages` on every
 * `Connected` transition without cancelling the previous subscription. Since
 * that flow is shared across the transport's whole lifetime (including
 * reconnects), each reconnect stacked one more live collector on top of the
 * still-running old ones, so a frame arriving after N reconnects was
 * delivered to the listener N+1 times. These tests fail against that bug and
 * pass once `subscribeToInbound()` is only ever active once per client.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTest {

    @Test
    fun `a message after a single reconnect is delivered exactly once`() = runTest {
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
        transport.simulateDisconnect()
        transport.simulateReconnect()

        transport.pushInbound(
            """{"type":"message","id":"m-1","author":"agent","text":"Back after reconnect","sentAt":"2026-07-14T10:00:00Z"}"""
        )

        verify(exactly = 1) {
            listener.onMessage(match { it.id == "m-1" })
        }

        scope.cancel()
    }

    @Test
    fun `typing events survive multiple reconnects and still fire exactly once each`() = runTest {
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

        // Three drop/reconnect cycles — with the old bug this would leave four
        // stacked collectors on the shared inbound flow.
        repeat(3) {
            transport.simulateDisconnect()
            transport.simulateReconnect()
        }

        transport.pushInbound("""{"type":"typing","isTyping":true}""")
        transport.pushInbound("""{"type":"typing","isTyping":false}""")

        verify(exactly = 1) { listener.onTypingChanged(true) }
        verify(exactly = 1) { listener.onTypingChanged(false) }

        scope.cancel()
    }

    @Test
    fun `connection state transitions are still reported through reconnects`() = runTest {
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
        transport.simulateDisconnect()
        transport.simulateReconnect()

        verify(exactly = 1) { listener.onConnectionStateChanged(ConnectionState.Disconnected) }
        // Connecting -> Connected fires once at connect() and once at the manual reconnect.
        verify(exactly = 2) { listener.onConnectionStateChanged(ConnectionState.Connected) }

        scope.cancel()
    }
}
