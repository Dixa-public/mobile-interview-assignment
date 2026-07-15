import Foundation
import Combine

/// Subscription/state layer over a `Transport`. Decodes inbound frames into
/// `IncomingEvent`s and re-publishes connection state.
final class RealtimeClient {

    private let transport: Transport
    private let decoder = EventDecoder()
    private let loggingEnabled: Bool

    /// Decoded inbound events for the facade above.
    let events = PassthroughSubject<IncomingEvent, Never>()
    /// Connection state re-published for the facade above.
    let connectionState = PassthroughSubject<ConnectionState, Never>()

    private var stateCancellable: AnyCancellable?

    /// Cancellables for the inbound-frame subscription.
    private var inboundCancellables = Set<AnyCancellable>()

    init(transport: Transport, loggingEnabled: Bool = false) {
        self.transport = transport
        self.loggingEnabled = loggingEnabled
    }

    func start() {
        stateCancellable = transport.connectionState
            .sink { [weak self] state in
                guard let self = self else { return }
                self.connectionState.send(state)
                if state == .connected {
                    self.subscribeToInbound()
                }
            }
        transport.connect()
    }

    private func subscribeToInbound() {
        // Subscribe to inbound frames from the transport.
        transport.inboundMessages
            .tryMap { raw in try self.decoder.decode(raw) }
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        self.log("inbound stream ended: \(error)")
                    }
                },
                receiveValue: { event in
                    self.events.send(event)
                }
            )
            .store(in: &inboundCancellables)
    }

    func send(text: String) {
        let frame = OutgoingMessage(type: "sendMessage", text: text)
        guard let data = try? JSONEncoder().encode(frame),
              let json = String(data: data, encoding: .utf8) else { return }
        transport.send(json)
    }

    func disconnect() {
        transport.disconnect()
    }

    private func log(_ message: String) {
        if loggingEnabled { print("[DixaMessenger] \(message)") }
    }
}

/// The single outbound frame the SDK sends: `{ "type":"sendMessage", "text":<string> }`.
struct OutgoingMessage: Encodable {
    let type: String
    let text: String
}
