# Notes on the typing-indicator release

## Adopting the typing indicator

`MessengerListener` has one new callback:

```kotlin
override fun onAgentTypingChanged(isTyping: Boolean) { /* show/hide indicator */ }
```

Override it on your existing listener — that's the whole integration. It has a
default no-op body, so host apps that don't want the feature need **zero
changes** to compile or run against this release. Threading is identical to the
existing callbacks (may arrive off the main thread; marshal to UI yourself —
the sample app shows the pattern). The SDK also delivers `false` if the
connection drops while the agent was typing, so you never render a stale
"typing…" bubble.

## Version bump: minor

- New public API (`onAgentTypingChanged`), added backward-compatibly via a
  default interface method → not a patch.
- Nothing existing was removed or changed incompatibly → not a major. The
  reconnect and contract-drift fixes do change observable behaviour, but only
  where the old behaviour was a defect (duplicate callbacks; delivery silently
  stopping after a bad frame).

## Unknown backend messages: before and after

**Before:** the parser threw on any frame it didn't recognise — an unknown
`type` or a known type missing a required field. The exception escaped inside
the inbound `collect` loop, which terminated the whole subscription: the socket
stayed open but **no further messages were delivered** until the next
reconnect. One drifted frame silently killed the chat.

**After:** the parser never throws. Frames it can't understand (unknown type,
missing required field, malformed JSON) return `null`, are logged, and are
skipped — delivery of every subsequent frame continues and the connection is
untouched. Tolerating unknown types is now the parser's explicit
forward-compatibility contract, so the backend can keep evolving ahead of
installed SDK versions.

The reconnect fix is related but distinct: the client used to launch an
*additional* inbound collector on every `Connected` transition without
cancelling the old one, so after N reconnects each frame was delivered N times.
The inbound subscription is now created once, for the client's lifetime — the
transport's frame flow already outlives individual socket connections, so
exactly-once follows by construction rather than by deduplication.

## Deliberately left out (and why)

- **Message-id dedup set** — the duplication was caused by stacked collectors,
  not re-sent frames; an id set would have masked the bug and grown unboundedly.
  If the protocol ever gains replay-on-resume, dedup should arrive with it.
- **Reconnect backoff/jitter in the transport** — the starter transport
  documents this as out of scope for the exercise.
- **Making `Message.sentAt` optional** to accept the no-timestamp drift frame —
  that changes the public `Message` type (a breaking change) and pushes partial
  data onto every host app; ignore-and-log is safer.

**Time spent:** ~X hours. <!-- fill in -->
