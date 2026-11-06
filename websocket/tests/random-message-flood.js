// Start N clients, randomly send messages, verify ping->pong latency
// Usage: node tests/random-message-flood.js ws://localhost:8080?token=... 500 200
import WebSocket from "ws";

const url = process.argv[2] || "ws://localhost:8080?token=dev";
const clients = Number(process.argv[3] || 500);
const durationSec = Number(process.argv[4] || 120);

const sockets = [];
let open = 0, pongs = 0;

for (let i = 0; i < clients; i++) {
  const ws = new WebSocket(url, { perMessageDeflate: false });
  ws.on("open", () => { open++; });
  ws.on("message", (buf) => {
    try {
      const m = JSON.parse(buf.toString());
      if (m.type === "pong" && m.t) pongs++;
    } catch {}
  });
  ws.on("error", () => {});
  sockets.push(ws);
}

function rndInt(n) { return Math.floor(Math.random() * n); }

const start = Date.now();
const sender = setInterval(() => {
  // send 5% of clients a ping
  const sendCount = Math.max(1, Math.floor(clients * 0.05));
  for (let k = 0; k < sendCount; k++) {
    const i = rndInt(clients);
    const ws = sockets[i];
    if (ws && ws.readyState === 1) {
      ws.send(JSON.stringify({ type: "ping", t: Date.now() }));
    }
  }

  const elapsed = (Date.now() - start) / 1000;
  if (elapsed > durationSec) {
    clearInterval(sender);
    console.log(JSON.stringify({ open, pongs, durationSec }));
    process.exit(0);
  }
}, 250);
