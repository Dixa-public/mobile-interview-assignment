'use strict';

// Local mock WebSocket server for the messenger SDK.
// Speaks the protocol in ../PROTOCOL.md. For OPTIONAL manual/visual testing —
// the unit tests use each kit's in-memory fake transport instead.
//
// Usage:
//   node mock-server.js [--scenario=basic|typing|drift|reconnect] [--port=8080]
//   node mock-server.js --selftest        # print scripted frames and exit (no server)

const WebSocket = require('ws');

function parseArgs(argv) {
  const args = { scenario: 'basic', port: 8080, selftest: false };
  for (const raw of argv.slice(2)) {
    if (raw === '--selftest') args.selftest = true;
    else if (raw.startsWith('--scenario=')) args.scenario = raw.split('=')[1];
    else if (raw.startsWith('--port=')) args.port = parseInt(raw.split('=')[1], 10);
  }
  return args;
}

// Each scenario is an ordered list of steps. A step is either:
//   { send: <frame object> }              — send one JSON frame
//   { wait: <ms> }                         — pause
//   { drop: true }                         — close the socket (client should reconnect)
//
// `conn` is the zero-based index of the current client connection. Only the
// `reconnect` scenario varies by it: because a dropped socket forces the client
// to open a NEW connection, the script must advance per connection — otherwise
// it would just replay connection 0's steps forever. Each reconnect delivers a
// distinct new message so fresh content is easy to tell apart from redelivered
// content.
function scriptFor(scenario, conn = 0) {
  const hello = { type: 'message', id: 'm-1', author: 'agent', text: 'Hi, how can I help?', sentAt: '2026-07-14T10:00:00Z' };
  switch (scenario) {
    case 'typing':
      return [
        { send: hello },
        { wait: 800 },
        { send: { type: 'typing', isTyping: true } },
        { wait: 1500 },
        { send: { type: 'typing', isTyping: false } },
        { wait: 300 },
        { send: { type: 'message', id: 'm-2', author: 'agent', text: 'Here you go.', sentAt: '2026-07-14T10:00:05Z' } },
      ];
    case 'drift':
      return [
        { send: hello },
        { wait: 600 },
        // Unknown type the installed SDK has never seen — must be ignored, not crash.
        { send: { type: 'presence', agentId: 'a-7', status: 'online' } },
        { wait: 600 },
        // A 'message' missing the 'sentAt' field the parser assumes is present.
        { send: { type: 'message', id: 'm-3', author: 'agent', text: 'No timestamp on this one.' } },
      ];
    case 'reconnect':
      // Connection 0: greet, then drop so the client reconnects.
      if (conn === 0) {
        return [
          { send: hello },
          { wait: 1000 },
          { drop: true },
        ];
      }
      // Connection 1 (first reconnect): a NEW message, then drop again.
      if (conn === 1) {
        return [
          { send: { type: 'message', id: 'm-4', author: 'agent', text: 'Back again after reconnect #1.', sentAt: '2026-07-14T10:00:10Z' } },
          { wait: 1000 },
          { drop: true },
        ];
      }
      // Connection 2+ (later reconnects): a final distinct message; stay connected.
      return [
        { send: { type: 'message', id: 'm-5', author: 'agent', text: `Reconnected again (#${conn}) — should arrive once per healthy subscription.`, sentAt: '2026-07-14T10:00:20Z' } },
      ];
    case 'basic':
    default:
      return [
        { send: hello },
        { wait: 1000 },
        { send: { type: 'message', id: 'm-2', author: 'user', text: 'Thanks!', sentAt: '2026-07-14T10:00:02Z' } },
      ];
  }
}

function runSelftest(scenario) {
  console.log(`# selftest scenario=${scenario}`);
  // `reconnect` varies per connection, so show the first three connections.
  const conns = scenario === 'reconnect' ? [0, 1, 2] : [0];
  for (const conn of conns) {
    if (scenario === 'reconnect') console.log(`## connection ${conn}`);
    for (const step of scriptFor(scenario, conn)) {
      if (step.send) console.log('SEND ' + JSON.stringify(step.send));
      else if (step.wait) console.log(`WAIT ${step.wait}ms`);
      else if (step.drop) console.log('DROP connection');
    }
  }
}

async function playScript(ws, script) {
  for (const step of script) {
    if (ws.readyState !== WebSocket.OPEN) return;
    if (step.send) ws.send(JSON.stringify(step.send));
    else if (step.wait) await new Promise((r) => setTimeout(r, step.wait));
    else if (step.drop) { ws.close(); return; }
  }
}

function main() {
  const args = parseArgs(process.argv);
  if (args.selftest) { runSelftest(args.scenario); return; }

  const wss = new WebSocket.Server({ port: args.port });
  wss.on('error', (e) => {
    if (e && e.code === 'EADDRINUSE') {
      console.error(`port ${args.port} is already in use — try --port=<other>`);
      process.exit(1);
    }
    throw e;
  });
  console.log(`mock server listening on ws://localhost:${args.port} (scenario=${args.scenario})`);
  console.log('press Ctrl+C to stop');

  let connectionIndex = 0;
  wss.on('connection', (ws) => {
    const conn = connectionIndex++;
    console.log(`client connected (#${conn})`);
    ws.on('message', (data) => {
      // Echo the user's outgoing message back as an agent reply so the UI shows life.
      let parsed;
      try { parsed = JSON.parse(data.toString()); } catch { return; }
      if (parsed && parsed.type === 'sendMessage') {
        ws.send(JSON.stringify({ type: 'message', id: 'echo-' + Date.now(), author: 'agent', text: 'You said: ' + parsed.text, sentAt: new Date().toISOString() }));
      }
    });
    ws.on('close', () => console.log(`client #${conn} disconnected`));
    playScript(ws, scriptFor(args.scenario, conn));
  });
}

main();
