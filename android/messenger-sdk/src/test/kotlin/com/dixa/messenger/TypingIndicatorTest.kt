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
 * The typing-indicator feature: `typing` frames surface to the host app through
 * [MessengerListener.onAgentTypingChanged], and a connection drop while the
 * agent is typing resets the indicator so it can never go stale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorTest {

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
    fun `a typing frame fires onAgentTypingChanged with true`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound("""{"type":"typing","isTyping":true}""")

        verify(exactly = 1) { listener.onAgentTypingChanged(true) }

        scope.cancel()
    }

    @Test
    fun `a typing frame fires onAgentTypingChanged with false`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound("""{"type":"typing","isTyping":true}""")
        transport.pushInbound("""{"type":"typing","isTyping":false}""")

        verify(exactly = 1) { listener.onAgentTypingChanged(true) }
        verify(exactly = 1) { listener.onAgentTypingChanged(false) }

        scope.cancel()
    }

    @Test
    fun `a disconnect while the agent is typing resets the indicator to false`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound("""{"type":"typing","isTyping":true}""")
        transport.simulateDisconnect()

        // The reset is delivered as a regular typing-stopped callback.
        verify(exactly = 1) { listener.onAgentTypingChanged(true) }
        verify(exactly = 1) { listener.onAgentTypingChanged(false) }

        scope.cancel()
    }

    @Test
    fun `a disconnect while the agent is not typing fires no typing callback`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.simulateDisconnect()

        verify(exactly = 0) { listener.onAgentTypingChanged(any()) }

        scope.cancel()
    }
}
