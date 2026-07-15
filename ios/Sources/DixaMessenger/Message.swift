import Foundation

/// Who authored a chat message. Mirrors the `author` field of a `message` frame.
public enum Author: String, Decodable, Equatable {
    case agent
    case user
}

/// A single chat message decoded from a `message` wire frame.
///
/// Wire shape (see protocol/PROTOCOL.md):
/// `{ "type":"message", "id":<string>, "author":"agent"|"user", "text":<string>, "sentAt":<ISO-8601 string> }`
public struct Message: Decodable, Equatable {
    public let id: String
    public let author: Author
    public let text: String

    /// When the message was sent, decoded from the wire's ISO-8601 string via
    /// `EventDecoder`'s date strategy.
    public let sentAt: Date

    public init(id: String, author: Author, text: String, sentAt: Date) {
        self.id = id
        self.author = author
        self.text = text
        self.sentAt = sentAt
    }
}
