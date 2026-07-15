import Foundation

/// Fluent configuration builder for `DixaMessenger`, e.g.:
/// ```
/// let config = DixaConfiguration()
///     .setServerURL(URL(string: "ws://localhost:8080")!)
///     .setLoggingEnabled(true)
/// ```
public final class DixaConfiguration {

    public private(set) var serverURL: URL
    public private(set) var enableLogging: Bool

    public init() {
        // Defaults to the shared local mock server used by the sample app.
        self.serverURL = URL(string: "ws://localhost:8080")!
        self.enableLogging = false
    }

    @discardableResult
    public func setServerURL(_ url: URL) -> DixaConfiguration {
        self.serverURL = url
        return self
    }

    @discardableResult
    public func setLoggingEnabled(_ enabled: Bool) -> DixaConfiguration {
        self.enableLogging = enabled
        return self
    }
}
