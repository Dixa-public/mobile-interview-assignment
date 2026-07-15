package com.dixa.messenger

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory [Transport] for tests. No real socket, no timers — you drive it by
 * hand so tests are deterministic under the coroutine test scheduler.
 *
 * Typical use:
 * ```
 * val transport = FakeTransport()
 * val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
 * val messenger = Messenger(MessengerConfig.Builder().build(), transport, scope)
 * messenger.setListener(listener)
 * messenger.connect()                 // FakeTransport emits Connecting then Connected;
 *                                      // the unconfined dispatcher runs collectors eagerly
 * transport.pushInbound("""{"type":"message", ... }""")
 * ```
 */
class FakeTransport : Transport {

    private val inbound = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    private val state = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 8)

    override val inboundMessages: SharedFlow<String> = inbound.asSharedFlow()
    override val connectionState: SharedFlow<ConnectionState> = state.asSharedFlow()

    /** Frames handed to [send], in order — assert your outbound wire format here. */
    val sentFrames: MutableList<String> = mutableListOf()

    /** How many times [connect] was called. */
    var connectCallCount: Int = 0
        private set

    override fun connect() {
        connectCallCount++
        state.tryEmit(ConnectionState.Connecting)
        state.tryEmit(ConnectionState.Connected)
    }

    override fun disconnect() {
        state.tryEmit(ConnectionState.Disconnected)
    }

    override fun send(text: String) {
        sentFrames.add(text)
    }

    // ---- Test controls ----

    /** Deliver a raw inbound JSON frame to every live collector. */
    fun pushInbound(rawJson: String) {
        inbound.tryEmit(rawJson)
    }

    /** Simulate the socket dropping (bad signal, app backgrounded, etc.). */
    fun simulateDisconnect() {
        state.tryEmit(ConnectionState.Disconnected)
    }

    /** Simulate the socket coming back after a drop. */
    fun simulateReconnect() {
        state.tryEmit(ConnectionState.Connecting)
        state.tryEmit(ConnectionState.Connected)
    }
}
