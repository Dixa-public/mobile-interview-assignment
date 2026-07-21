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
 * Backend contract-drift resilience: the backend evolves independently of
 * installed SDK versions, so unknown types, missing fields, and outright
 * garbage must be ignored/logged — never crash the SDK, never tear down the
 * connection, and never stall delivery of the frames that follow.
 *
 * Each test pushes a bad frame and then proves the pipeline is still alive by
 * delivering a valid one after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContractDriftTest {

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

    private val validMessage =
        """{"type":"message","id":"m-ok","author":"agent","text":"Still here","sentAt":"2026-07-14T10:00:00Z"}"""

    @Test
    fun `an unknown event type is ignored and later messages still arrive`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        // A frame this SDK version has never seen (see protocol drift scenario).
        transport.pushInbound("""{"type":"presence","agentId":"a-7","status":"online"}""")
        transport.pushInbound(validMessage)

        verify(exactly = 1) { listener.onMessage(match { it.id == "m-ok" }) }
        // The connection was not torn down by the unknown frame.
        verify(exactly = 0) { listener.onConnectionStateChanged(ConnectionState.Disconnected) }

        scope.cancel()
    }

    @Test
    fun `a message missing a required field is dropped and later messages still arrive`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        // A `message` without the `sentAt` field the parser expects.
        transport.pushInbound("""{"type":"message","id":"m-3","author":"agent","text":"No timestamp on this one."}""")
        transport.pushInbound(validMessage)

        verify(exactly = 0) { listener.onMessage(match { it.id == "m-3" }) }
        verify(exactly = 1) { listener.onMessage(match { it.id == "m-ok" }) }

        scope.cancel()
    }

    @Test
    fun `non-JSON garbage is ignored and later messages still arrive`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound("this is not json {{{")
        transport.pushInbound(validMessage)

        verify(exactly = 1) { listener.onMessage(match { it.id == "m-ok" }) }

        scope.cancel()
    }

    @Test
    fun `a typing frame missing isTyping is ignored and later frames still arrive`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = startMessenger(transport, listener)

        transport.pushInbound("""{"type":"typing"}""")
        transport.pushInbound("""{"type":"typing","isTyping":true}""")

        // Only the well-formed frame produced a callback.
        verify(exactly = 1) { listener.onAgentTypingChanged(any()) }
        verify(exactly = 1) { listener.onAgentTypingChanged(true) }

        scope.cancel()
    }
}
