# Mock WebSocket server

Optional helper for **manual/visual** testing. The unit tests do not need it;
they use the in-memory fake transport in your platform's kit. Use this only if
you want to watch the SDK against a real `ws://` socket.

## Run

```bash
cd protocol/mock-server
npm install
node mock-server.js --scenario=typing
```

Then launch your platform's sample app; it connects to `ws://localhost:8080`.

## Scenarios (`--scenario=`)

- `basic`: a couple of chat messages (default).
- `typing`: sends `typing` on/off frames (exercise your new feature).
- `drift`: sends an unknown `presence` type and a `message` missing `sentAt`
  (exercise backend contract-drift resilience).
- `reconnect`: drops the connection after each of the first two connections and
  delivers a new message on every reconnect (exercise reconnect behavior; watch
  for those post-reconnect messages arriving duplicated).

The `reconnect` scenario advances per connection over the server's lifetime, so
the opening greeting is sent only to the very first connection. **Restart the
server to replay a scenario from the beginning.**

## No Node?

You don't need this server to run the unit tests; they're covered entirely by
the fake-transport tests.
