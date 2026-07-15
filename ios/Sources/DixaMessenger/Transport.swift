import Foundation
import Combine

/// High-level connection state of the realtime transport.
public enum ConnectionState: Equatable {
    case connecting
    case connected
    case disconnected
}

/// The realtime socket seam. The SDK talks only to this interface, so tests can
/// inject an in-memory `FakeTransport` instead of a real WebSocket.
///
/// - `inboundMessages` emits each raw UTF-8 JSON text frame received from the
///   server, exactly as received (decoding happens above this layer).
/// - `connectionState` emits transitions; a reconnect re-emits `.connected`.
public protocol Transport: AnyObject {
    var inboundMessages: AnyPublisher<String, Never> { get }
    var connectionState: AnyPublisher<ConnectionState, Never> { get }
    func connect()
    func disconnect()
    func send(_ text: String)
}
