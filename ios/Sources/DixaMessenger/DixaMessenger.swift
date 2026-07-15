import Foundation
import Combine

/// The public entry point a host app embeds. Configure it with a
/// `DixaConfiguration`, set the callback closures you care about, then
/// `connect()`.
///
/// Callbacks fire on whatever thread the transport delivers on (the real
/// transport uses a background queue). Host apps that update UI should marshal
/// to the main thread themselves — the SampleApp shows how.
public final class DixaMessenger {

    private let configuration: DixaConfiguration
    private let client: RealtimeClient
    private var cancellables = Set<AnyCancellable>()
    private var hasConnected = false

    // MARK: - Host-app callbacks

    /// Called for every chat `message` received from the backend.
    public var onMessage: ((Message) -> Void)?

    /// Called on every connection-state transition.
    public var onConnectionStateChanged: ((ConnectionState) -> Void)?

    // MARK: - Init

    public convenience init(configuration: DixaConfiguration) {
        self.init(
            configuration: configuration,
            transport: WebSocketTransport(url: configuration.serverURL)
        )
    }

    /// Test seam: inject a custom `Transport` (e.g. `FakeTransport`).
    init(configuration: DixaConfiguration, transport: Transport) {
        self.configuration = configuration
        self.client = RealtimeClient(
            transport: transport,
            loggingEnabled: configuration.enableLogging
        )
    }

    // MARK: - Lifecycle

    public func connect() {
        // Wire the SDK's callbacks exactly once; calling connect() again is a no-op
        // rather than stacking duplicate subscriptions.
        guard !hasConnected else { return }
        hasConnected = true

        client.events
            .sink { [weak self] event in
                guard let self = self else { return }
                switch event {
                case .message(let message):
                    self.onMessage?(message)
                }
            }
            .store(in: &cancellables)

        client.connectionState
            .sink { [weak self] state in
                self?.onConnectionStateChanged?(state)
            }
            .store(in: &cancellables)

        client.start()
    }

    public func send(text: String) {
        client.send(text: text)
    }

    public func disconnect() {
        client.disconnect()
    }
}
