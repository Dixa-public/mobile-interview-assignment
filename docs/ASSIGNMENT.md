# Take-Home Assignment: Ship a Typing Indicator

**Time:** Think of 2-4 hours as a ballpark, not a limit. It's untimed, so spend a
bit more if you're enjoying it - just tell us roughly how long you spent. We're
not looking for a production-grade result in a few hours, and we know that's not
realistic. What we really care about is the decisions you made and the reasoning
behind them, so don't sweat the polish.

**Pick one platform:** Android (Kotlin) or iOS (Swift), whichever you're
strongest in. Work only in that platform's folder (`android/` or `ios/`).

## The scenario

You're the new maintainer of this messenger SDK. Customer apps ("host apps")
embed it to add live chat. Product wants an **"Agent is typing…"** indicator.

Your job: ship it. And because the real-time layer is the most bug-prone part
of this SDK, leave it more robust than you found it.

## What to build

1. **The feature.** When the backend sends a `typing` frame (see
   `protocol/PROTOCOL.md`), the SDK should surface the agent's typing state to
   the host app through the public API. How you shape that API is up to you.
2. **Reconnect robustness.** The real-time connection drops and re-establishes
   often (bad signal, app backgrounded, etc.). Make sure the host app is notified
   exactly once per event, even across reconnects.
3. **Backend contract-drift resilience.** The backend evolves independently of
   installed SDK versions. It may send message types the SDK has never seen, or
   messages missing fields the SDK expects. The SDK must degrade gracefully
   (ignore/log) instead of crashing or dropping the whole connection.

Your platform folder's `README.md` tells you where the relevant code lives and
how to build/test/run it.

## What to hand in

1. Your code changes.
2. **Tests** proving:
   - the typing callback fires **exactly once** per event across
     disconnect-reconnect cycles (no duplicate notifications);
   - an unexpected/unknown/malformed incoming message does not crash and does
     not tear down the connection.
   The starter kit ships an in-memory **fake transport** and 1-2 example tests to
   build on; you don't need any network or the mock server to write these.
3. A short **written note** (~half a page, plain English) covering:
   - what a host-app developer has to do to adopt the typing indicator;
   - whether this is a patch / minor / major version bump, and why;
   - what happens today if the backend sends a message this SDK version has never
     seen, and how your change makes that safe.

## Testing your work

- **Graded path (unit tests):** use the fake transport. See your platform README.
- **Optional, see it live:** run the mock server (`protocol/mock-server/`,
  needs Node) and launch the sample app. Scenarios: `typing`, `drift`,
  `reconnect` (see the mock-server README).

## How we evaluate

We look at correctness, how you handle the reconnect and contract-drift cases
(symptom vs. root cause), your tests, and the clarity of your written note. We'll
then talk through your solution together in a short follow-up call. It's fine to
leave things out for time; just tell us what you'd do with more.
