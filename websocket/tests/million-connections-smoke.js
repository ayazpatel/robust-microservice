// Parameterized high-conn opener (beware OS limits). Not truly 1M on a laptop.
// Usage: node tests/million-connections-smoke.js ws://host:port?token=... 200000 100
// args: url, totalClients, batchSize
import WebSocket from "ws";

const url = process.argv[2] || "ws://localhost:8080?token=dev";
const total = Number(process.argv[3] || 200000);
const batch = Number(process.argv[4] || 500);
const sockets = new Array(total);
let opened = 0, failed = 0;

function openBatch(start, count) {
  for (let i = start; i < Math.min(total, start + count); i++) {
    const ws = new WebSocket(url, { perMessageDeflate: false });
    ws.on("open", () => { opened++; if (opened % 1000 === 0) console.log("open", opened); });
    ws.on("error", () => { failed++; });
    ws.on("close", () => {});
    sockets[i] = ws;
  }
}

let idx = 0;
const timer = setInterval(() => {
  if (idx >= total) return clearInterval(timer);
  openBatch(idx, batch);
  idx += batch;
}, 10);

process.on("SIGINT", () => {
  console.log("Opened:", opened, "Failed:", failed);
  process.exit(0);
});
