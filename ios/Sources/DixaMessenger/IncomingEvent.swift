import Foundation

/// A decoded inbound event the SDK understands.
enum IncomingEvent: Equatable {
    case message(Message)
}

/// Decodes a single UTF-8 JSON text frame into an `IncomingEvent`.
///
/// The frame's string `type` field selects the shape of the rest of the object
/// (see protocol/PROTOCOL.md).
struct EventDecoder {

    /// Only the discriminator is read first, so we know which concrete shape to
    /// decode next.
    private struct TypeEnvelope: Decodable {
        let type: String
    }

    private let json: JSONDecoder = {
        let decoder = JSONDecoder()
        // `sentAt` is an ISO-8601 string on the wire; without this strategy even
        // a well-formed timestamp fails to decode. (The real SDK once shipped
        // without it — a genuine production incident.)
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    func decode(_ raw: String) throws -> IncomingEvent {
        let data = Data(raw.utf8)
        let envelope = try json.decode(TypeEnvelope.self, from: data)
        switch envelope.type {
        case "message":
            // Decode the concrete `Message` shape.
            let message = try json.decode(Message.self, from: data)
            return .message(message)
        default:
            throw DecodingError.dataCorrupted(
                DecodingError.Context(
                    codingPath: [],
                    debugDescription: "Unrecognised event type: \(envelope.type)"
                )
            )
        }
    }
}
