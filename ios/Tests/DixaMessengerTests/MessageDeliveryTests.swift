import XCTest
import Combine
@testable import DixaMessenger

/// Example tests showing how to drive the SDK with `FakeTransport`.
///
/// These are a starting point to build on. They cover only the happy path —
/// extend them to cover reconnect and malformed-input handling.
final class MessageDeliveryTests: XCTestCase {

    func testValidMessageFrameIsDeliveredToHostCallback() {
        let transport = FakeTransport()
        let messenger = DixaMessenger(
            configuration: DixaConfiguration(),
            transport: transport
        )

        var received: [Message] = []
        messenger.onMessage = { received.append($0) }
        messenger.connect()

        transport.pushInbound(
            #"{"type":"message","id":"m-1","author":"agent","text":"Hi there","sentAt":"2026-07-14T10:00:00Z"}"#
        )

        XCTAssertEqual(received.count, 1)
        XCTAssertEqual(received.first?.id, "m-1")
        XCTAssertEqual(received.first?.author, .agent)
        XCTAssertEqual(received.first?.text, "Hi there")
    }

    func testOutgoingMessageIsForwardedToTransport() throws {
        let transport = FakeTransport()
        let messenger = DixaMessenger(
            configuration: DixaConfiguration(),
            transport: transport
        )
        messenger.connect()

        messenger.send(text: "Hello agent")

        XCTAssertEqual(transport.sentFrames.count, 1)
        let frame = try XCTUnwrap(transport.sentFrames.first)
        let object = try JSONSerialization.jsonObject(with: Data(frame.utf8)) as? [String: Any]
        XCTAssertEqual(object?["type"] as? String, "sendMessage")
        XCTAssertEqual(object?["text"] as? String, "Hello agent")
    }
}
