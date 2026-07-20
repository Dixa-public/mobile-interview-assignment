# Written Note

## Adopting the typing indicator

Host apps that already implement `MessengerListener` don't have to change
anything to keep building. To show the indicator, they override the new
`onTypingChanged(isTyping: Boolean)` method and
update their UI accordingly — e.g. toggling a "typing…" bubble's visibility,
as done in `sample-app`'s `ChatActivity`. Like the other callbacks, it can
fire off the main thread, so UI updates must be wrapped with
`runOnUiThread` (or the host app's equivalent).

## Version bump: minor

This is a **minor** bump, not a major one. A new
method was added to `MessengerListener`,
so existing host-app implementations of that interface keep compiling and
behaving exactly as before without touching their code. No existing method
signature, class, or behavior changed. If the new method had been added
without a default body, every host app would have been forced to implement it
to keep compiling, which would have made it a breaking (major) change.

## Backend contract drift

**Before this change:** the SDK had a single code path for parsing every
inbound frame, and an unrecognized `type` or a `message` missing an expected
field (e.g. `sentAt`) threw a deserialization exception. Depending on where
that exception surfaced, it could crash the app or, if caught too broadly
somewhere, silently tear down the whole WebSocket collection loop — so one
malformed or unfamiliar frame could cost the host app *every* subsequent
frame on that connection, not just the bad one.

**After this change:** `MessageParser.parse()` treats parse failures as
per-frame, not fatal. An unknown `type` (like `presence`) returns `null` and
is skipped. A recognized `type` with a missing/malformed field also returns
`null` via a caught `SerializationException`, rather than propagating.
`RealtimeClient` only emits an event when parsing succeeds, so the collector
that reads `transport.inboundMessages` never exits and keeps processing every
frame that arrives afterward. In short: unknown or malformed frames are now
dropped (and logged, if `loggingEnabled`) individually, and the connection —
and every future frame on it — is unaffected. This is proven in
`ContractDriftTest` (unknown type, missing field, and malformed JSON cases).

## Reconnect robustness

Also fixed as part of this work: `RealtimeClient` used to resubscribe to the
transport's shared inbound flow on every `Connected` transition, stacking a
new collector on top of the still-running old one each time — so after N
reconnects, a single incoming frame fired the listener N+1 times. The fix
subscribes exactly once, for the life of the client, so every event —
messages, typing changes, connection-state transitions — reaches the host app
exactly once per occurrence regardless of how many reconnects happened in
between. Proven in `ReconnectTest`.
