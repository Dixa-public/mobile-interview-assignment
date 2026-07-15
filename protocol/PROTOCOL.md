# Wire Protocol

The SDK talks to the backend over a single WebSocket connection. Every frame is
a UTF-8 **JSON text frame**: one JSON object per frame. Every object has a
string `type` field that selects the shape of the rest of the object.

This protocol is **frozen**. Both the Android and iOS kits implement it, and the
mock server (`protocol/mock-server/`) speaks it.

## Server → SDK frames

### `message`
A chat message from an agent or the end user.
```json
{ "type": "message", "id": "m-1", "author": "agent", "text": "Hello!", "sentAt": "2026-07-14T10:00:00Z" }
```
- `author` is `"agent"` or `"user"`.
- `sentAt` is an ISO-8601 timestamp.

### `typing`
Whether the agent is currently typing. **This SDK does not yet surface this
type to host apps.**
```json
{ "type": "typing", "isTyping": true }
```

### `presence`
Agent presence/status. **The starter kit is not expected to handle this type.**
It exists so you can observe how the SDK behaves when the backend sends a frame
the installed SDK version does not recognize.
```json
{ "type": "presence", "agentId": "a-7", "status": "online" }
```

## SDK → server frames

### `sendMessage`
```json
{ "type": "sendMessage", "text": "Hi there" }
```

## Notes on backend evolution

The backend evolves independently of installed SDK versions. In production the
backend may:
- introduce **new `type` values** the installed SDK has never seen (like
  `presence` above);
- **add or remove fields** on existing types (e.g. a `message` that arrives
  without `sentAt`).

A resilient SDK must not crash or drop the whole connection when this happens.
