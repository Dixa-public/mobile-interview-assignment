package com.dixa.messenger

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Covers the `typing` frame surfacing through `MessengerListener.onTypingChanged`. */
@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorTest {

    @Test
    fun `a typing frame surfaces as onTypingChanged`() = runTest {
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

        transport.pushInbound("""{"type":"typing","isTyping":true}""")
        verify(exactly = 1) { listener.onTypingChanged(true) }

        transport.pushInbound("""{"type":"typing","isTyping":false}""")
        verify(exactly = 1) { listener.onTypingChanged(false) }

        scope.cancel()
    }
}
