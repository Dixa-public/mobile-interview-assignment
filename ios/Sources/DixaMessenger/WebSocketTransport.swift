import Foundation
import Combine

/// Real transport backed by `URLSessionWebSocketTask` (completion-handler based
/// — no async/await). On an unexpected drop it emits `.disconnected` and then
/// automatically re-opens the socket, re-emitting `.connected`.
///
/// This backs only the optional/visual path (the SampleApp against the local
/// mock server). Unit tests exercise the SDK entirely through the in-memory
/// `FakeTransport`, so there's normally no need to modify this file. It is
/// intentionally simple and skips production concerns (handshake confirmation,
/// thread-safety, send-error handling).
public final class WebSocketTransport: Transport {

    private let url: URL
    private let session: URLSession
    private let reconnectDelay: TimeInterval

    private var task: URLSessionWebSocketTask?
    private var isManuallyClosed = false

    private let inboundSubject = PassthroughSubject<String, Never>()
    private let stateSubject = CurrentValueSubject<ConnectionState, Never>(.disconnected)

    public var inboundMessages: AnyPublisher<String, Never> {
        inboundSubject.eraseToAnyPublisher()
    }

    public var connectionState: AnyPublisher<ConnectionState, Never> {
        stateSubject.eraseToAnyPublisher()
    }

    public init(url: URL, session: URLSession = .shared, reconnectDelay: TimeInterval = 1.0) {
        self.url = url
        self.session = session
        self.reconnectDelay = reconnectDelay
    }

    public func connect() {
        isManuallyClosed = false
        openTask()
    }

    public func disconnect() {
        isManuallyClosed = true
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        stateSubject.send(.disconnected)
    }

    public func send(_ text: String) {
        task?.send(.string(text)) { _ in }
    }

    private func openTask() {
        stateSubject.send(.connecting)
        let task = session.webSocketTask(with: url)
        self.task = task
        task.resume()
        stateSubject.send(.connected)
        receiveNext()
    }

    private func receiveNext() {
        task?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.inboundSubject.send(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.inboundSubject.send(text)
                    }
                @unknown default:
                    break
                }
                self.receiveNext()
            case .failure:
                self.handleDrop()
            }
        }
    }

    private func handleDrop() {
        guard !isManuallyClosed else { return }
        stateSubject.send(.disconnected)
        DispatchQueue.main.asyncAfter(deadline: .now() + reconnectDelay) { [weak self] in
            guard let self = self, !self.isManuallyClosed else { return }
            self.openTask()
        }
    }
}
