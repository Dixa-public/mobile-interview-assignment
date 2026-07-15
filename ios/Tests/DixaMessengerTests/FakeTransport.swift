import Foundation
import Combine
@testable import DixaMessenger

/// In-memory `Transport` for tests. No real socket, no timers — you drive it by
/// hand so tests are deterministic.
///
/// Typical use:
/// ```
/// let transport = FakeTransport()
/// let messenger = DixaMessenger(configuration: DixaConfiguration(), transport: transport)
/// messenger.onMessage = { ... }
/// messenger.connect()                 // FakeTransport emits .connecting then .connected
/// transport.pushInbound(#"{"type":"message", ... }"#)
/// ```
final class FakeTransport: Transport {

    private let inboundSubject = PassthroughSubject<String, Never>()
    private let stateSubject = PassthroughSubject<ConnectionState, Never>()

    /// Frames handed to `send(_:)`, in order — assert your outbound wire format here.
    private(set) var sentFrames: [String] = []
    /// How many times `connect()` was called.
    private(set) var connectCallCount = 0

    var inboundMessages: AnyPublisher<String, Never> {
        inboundSubject.eraseToAnyPublisher()
    }

    var connectionState: AnyPublisher<ConnectionState, Never> {
        stateSubject.eraseToAnyPublisher()
    }

    func connect() {
        connectCallCount += 1
        stateSubject.send(.connecting)
        stateSubject.send(.connected)
    }

    func disconnect() {
        stateSubject.send(.disconnected)
    }

    func send(_ text: String) {
        sentFrames.append(text)
    }

    // MARK: - Test controls

    /// Deliver a raw inbound JSON frame to the SDK.
    func pushInbound(_ rawJSON: String) {
        inboundSubject.send(rawJSON)
    }

    /// Simulate the socket dropping (bad signal, backgrounded, etc.).
    func simulateDisconnect() {
        stateSubject.send(.disconnected)
    }

    /// Simulate the socket coming back after a drop.
    func simulateReconnect() {
        stateSubject.send(.connecting)
        stateSubject.send(.connected)
    }
}
