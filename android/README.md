# Android Starter Kit (`messenger-sdk`)

A miniature messenger SDK in Kotlin, built to mirror the shape of the real Dixa
Android SDK (coroutines/Flow, a fluent `MessengerConfig.Builder`, a
`MessengerListener` callback interface, and an OkHttp WebSocket transport).

**Read [`../docs/ASSIGNMENT.md`](../docs/ASSIGNMENT.md) first.** It
describes the task. This README only covers how to build, test, and run the kit
and where the code lives.

## Requirements

- JDK 17
- Android SDK with `compileSdk 34` installed
- Gradle wrapper is included (`./gradlew`), pinned to Gradle 8.7 / AGP 8.5.2 /
  Kotlin 1.9.24. The SDK library uses coroutines, OkHttp, and kotlinx.serialization;
  the module targets `minSdk 24`.

## Build

```bash
cd android
./gradlew :messenger-sdk:assembleDebug
```

## Run the unit tests

```bash
cd android
./gradlew :messenger-sdk:testDebugUnitTest
```

Tests drive the SDK through an in-memory `FakeTransport`, so no network and no
mock server are required. The two example tests in `MessageDeliveryTest` show how the
harness works (`runTest` with an `UnconfinedTestDispatcher`-based scope, plus
MockK and Turbine available); extend them to cover more scenarios (reconnects,
malformed input, etc.).

Run a single class with, e.g.:

```bash
./gradlew :messenger-sdk:testDebugUnitTest --tests "com.dixa.messenger.MessageDeliveryTest"
```

The HTML test report is written to
`messenger-sdk/build/reports/tests/testDebugUnitTest/index.html`.

## Run the sample app (optional, visual)

Build and install the sample app on a running emulator/device:

```bash
cd android
./gradlew :sample-app:installDebug
```

The app connects to `ws://localhost:8080`. Start the shared mock server first
(see [`../protocol/mock-server/README.md`](../protocol/mock-server/README.md)),
for example:

```bash
cd ../protocol/mock-server
npm install
node mock-server.js --scenario=typing
```

Useful scenarios: `basic`, `typing`, `drift`, `reconnect`. (On the Android
emulator, `localhost` refers to the emulator itself; use `adb reverse tcp:8080
tcp:8080` to reach a mock server running on your host machine.)

## Where things live

| Path | What it is |
|---|---|
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/Message.kt` | Domain models (`Message`, `Author`). |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/IncomingEvent.kt` | Inbound event type + `MessageParser`. |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/Transport.kt` | `Transport` interface + `ConnectionState`. |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/OkHttpWebSocketTransport.kt` | Real OkHttp WebSocket transport. |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/RealtimeClient.kt` | Subscription/state layer over a `Transport`. |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/MessengerConfig.kt` | Fluent config builder. |
| `messenger-sdk/src/main/kotlin/com/dixa/messenger/Messenger.kt` | Public facade + `MessengerListener`. |
| `sample-app/src/main/` | Views/XML host app (chat UI + typing-bubble area). |
| `messenger-sdk/src/test/kotlin/com/dixa/messenger/FakeTransport.kt` | In-memory transport for deterministic tests. |
| `messenger-sdk/src/test/kotlin/com/dixa/messenger/MessageDeliveryTest.kt` | Example tests to build on. |

## The wire protocol

The SDK speaks the JSON-over-WebSocket protocol in
[`../protocol/PROTOCOL.md`](../protocol/PROTOCOL.md). Every frame is a UTF-8 JSON
text frame with a string `type` field.
