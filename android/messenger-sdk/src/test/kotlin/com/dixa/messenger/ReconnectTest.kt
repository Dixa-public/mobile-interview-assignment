package com.dixa.messenger

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
        verify(exactly = 2) { listener.onConnectionStateChanged(ConnectionState.Connected) }

        scope.cancel()
    }
}
