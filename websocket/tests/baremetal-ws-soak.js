// Large soak test (increase step-by-step).
// Usage: node tests/baremetal-ws-soak.js wss://YOUR_HOST:443?token=PROD 20000
import WebSocket from "ws";

const url = process.argv[2] || "wss://localhost:443?token=prod";
const clients = Number(process.argv[3] || 20000);

const sockets = new Array(clients);
let open = 0;

function spawn(i) {
  const ws = new WebSocket(url, { perMessageDeflate: true });
  ws.on("open", () => {
    open++;
    if (open % 1000 === 0) console.log("open", open);
  });
  ws.on("close", () => setTimeout(() => spawn(i), 1000 + Math.random() * 2000));
  ws.on("message", () => {});
  ws.on("error", () => {});
  sockets[i] = ws;
}

for (let i = 0; i < clients; i++) spawn(i);

setInterval(() => {
  for (let i = 0; i < sockets.length; i++) {
    const ws = sockets[i];
    if (ws && ws.readyState === 1) {
      ws.send(JSON.stringify({ type: "beat", i, t: Date.now() }));
    }
  }
}, 5000);
