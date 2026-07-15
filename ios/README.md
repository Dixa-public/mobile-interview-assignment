# DixaMessenger (iOS)

A miniature messenger SDK in Swift, built to mirror the shape of the real Dixa
iOS SDK (Combine, a fluent `DixaConfiguration` builder, closure callbacks, and a
`URLSessionWebSocketTask` transport).

**Read [`../docs/ASSIGNMENT.md`](../docs/ASSIGNMENT.md) first.** It
describes the task. This README only covers how to build, test, and run the kit
and where the code lives.

## Requirements

- Xcode 15+ / Swift 5.9+
- The package targets **iOS 14+ / macOS 11+** and uses only Foundation +
  Combine, with no third-party dependencies. (The SampleApp's SwiftUI lifecycle
  requires iOS 14; the SDK code itself is otherwise Combine/Foundation-only.)

## Build

```bash
cd ios
swift build
```

`swift build` compiles the SDK library and its tests against the host macOS SDK.
The runnable sample app is a separate Xcode project (see below).

## Run the tests

```bash
cd ios
swift test
```

Tests drive the SDK through an in-memory `FakeTransport`, so no network and no
mock server are required. The two example tests in `MessageDeliveryTests` show how the
harness works; extend them to cover more scenarios (reconnects, malformed input,
etc.).

Run a subset with, e.g.:

```bash
swift test --filter MessageDeliveryTests
```

## Run the sample app (optional, visual)

Open the sample app project in Xcode, pick an iPhone simulator, and press Run:

```bash
cd ios
open SampleApp.xcodeproj
```

(`SampleApp.xcodeproj` is a small app target that depends on the local
`DixaMessenger` package. It is generated from `project.yml` via
[XcodeGen](https://github.com/yonaskolb/XcodeGen); you don't need XcodeGen to
open or run it.)

The app connects to `ws://localhost:8080`. Start the shared mock server first
(see [`../protocol/mock-server/README.md`](../protocol/mock-server/README.md)),
for example:

```bash
cd ../protocol/mock-server
npm install
node mock-server.js --scenario=typing
```

Useful scenarios: `basic`, `typing`, `drift`, `reconnect`.

## Where things live

| Path | What it is |
|---|---|
| `Sources/DixaMessenger/Message.swift` | Domain models (`Message`, `Author`). |
| `Sources/DixaMessenger/IncomingEvent.swift` | Inbound event type + `EventDecoder`. |
| `Sources/DixaMessenger/Transport.swift` | `Transport` protocol + `ConnectionState`. |
| `Sources/DixaMessenger/WebSocketTransport.swift` | Real `URLSessionWebSocketTask` transport. |
| `Sources/DixaMessenger/RealtimeClient.swift` | Subscription/state layer over a `Transport`. |
| `Sources/DixaMessenger/DixaConfiguration.swift` | Fluent config builder. |
| `Sources/DixaMessenger/DixaMessenger.swift` | Public facade + host-app callbacks. |
| `SampleApp/` | SwiftUI host app sources (chat UI + typing-bubble area). |
| `SampleApp.xcodeproj` / `project.yml` | The runnable sample-app project (open this in Xcode). |
| `Tests/DixaMessengerTests/FakeTransport.swift` | In-memory transport for deterministic tests. |
| `Tests/DixaMessengerTests/MessageDeliveryTests.swift` | Example tests to build on. |

## The wire protocol

The SDK speaks the JSON-over-WebSocket protocol in
[`../protocol/PROTOCOL.md`](../protocol/PROTOCOL.md). Every frame is a UTF-8 JSON
text frame with a string `type` field.
