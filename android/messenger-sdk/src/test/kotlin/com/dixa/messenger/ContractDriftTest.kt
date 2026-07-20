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
 * Proves the SDK degrades gracefully when the backend sends something this
 * SDK version doesn't fully understand: it drops the offending frame (and
 * would log it) instead of crashing or ending the inbound collection, so
 * every later, well-formed frame still gets through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContractDriftTest {

    private fun newMessenger(listener: MessengerListener, scope: CoroutineScope, transport: FakeTransport): Messenger {
        val messenger = Messenger(
            config = MessengerConfig.Builder().build(),
            transport = transport,
            scope = scope,
        )
        messenger.setListener(listener)
        return messenger
    }

    @Test
    fun `an unknown frame type is ignored and does not stop later delivery`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = newMessenger(listener, scope, transport)

        messenger.connect()

        // A type this SDK has never seen.
        transport.pushInbound("""{"type":"presence","agentId":"a-7","status":"online"}""")
        // A normal message right after — proves the collector is still alive.
        transport.pushInbound(
            """{"type":"message","id":"m-1","author":"agent","text":"still works","sentAt":"2026-07-14T10:00:00Z"}"""
        )

        verify(exactly = 1) { listener.onMessage(match { it.id == "m-1" }) }

        scope.cancel()
    }

    @Test
    fun `a message missing a required field is ignored and does not stop later delivery`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = newMessenger(listener, scope, transport)

        messenger.connect()

        // `message` frame missing the `sentAt` field the parser expects.
        transport.pushInbound("""{"type":"message","id":"m-3","author":"agent","text":"no timestamp on this one"}""")
        transport.pushInbound(
            """{"type":"message","id":"m-4","author":"agent","text":"back to normal","sentAt":"2026-07-14T10:00:05Z"}"""
        )

        verify(exactly = 0) { listener.onMessage(match { it.id == "m-3" }) }
        verify(exactly = 1) { listener.onMessage(match { it.id == "m-4" }) }

        scope.cancel()
    }

    @Test
    fun `malformed JSON is ignored and does not crash the collector`() = runTest {
        val transport = FakeTransport()
        val listener = mockk<MessengerListener>(relaxed = true)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messenger = newMessenger(listener, scope, transport)

        messenger.connect()

        transport.pushInbound("this is not json")
        transport.pushInbound(
            """{"type":"message","id":"m-5","author":"agent","text":"still fine","sentAt":"2026-07-14T10:00:00Z"}"""
        )

        verify(exactly = 1) { listener.onMessage(match { it.id == "m-5" }) }

        scope.cancel()
    }
}
