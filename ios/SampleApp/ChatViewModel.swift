import Foundation
import Combine
import DixaMessenger

/// Bridges the SDK's callbacks into observable UI state. Marshals SDK callbacks
/// (which may arrive off the main thread) onto the main queue.
final class ChatViewModel: ObservableObject {

    @Published var messages: [Message] = []
    @Published var connectionState: ConnectionState = .disconnected
    @Published var draft: String = ""

    private let messenger: DixaMessenger

    init() {
        let configuration = DixaConfiguration()
            .setServerURL(URL(string: "ws://localhost:8080")!)
            .setLoggingEnabled(true)
        self.messenger = DixaMessenger(configuration: configuration)
        configureCallbacks()
    }

    private func configureCallbacks() {
        messenger.onMessage = { [weak self] message in
            DispatchQueue.main.async { self?.messages.append(message) }
        }
        messenger.onConnectionStateChanged = { [weak self] state in
            DispatchQueue.main.async { self?.connectionState = state }
        }
    }

    func connect() {
        messenger.connect()
    }

    func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messenger.send(text: text)
        draft = ""
    }
}
