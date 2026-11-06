// Small local test: opens N clients and sends periodic messages.
// Usage: node tests/local-ws-burst.js ws://localhost:8080?token=DEV 500
import WebSocket from "ws";

const url = process.argv[2] || "ws://localhost:8080?token=dev";
const clients = Number(process.argv[3] || 200);
const arr = [];
let opened = 0;

for (let i = 0; i < clients; i++) {
  const ws = new WebSocket(url, { perMessageDeflate: false });
  ws.on("open", () => {
    opened++;
    if (opened % 50 === 0) console.log("opened", opened);
    ws.send(JSON.stringify({ type: "hello", i }));
  });
  ws.on("message", () => {});
  ws.on("error", () => {});
  arr.push(ws);
}

setInterval(() => {
  for (const [i, ws] of arr.entries()) {
    if (ws.readyState === 1) {
      ws.send(JSON.stringify({ type: "telemetry", i, t: Date.now() }));
    }
  }
}, 2000);
