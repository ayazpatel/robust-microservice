/* uWebSockets.js WebSocket server
 * - Auth before upgrade (JWT 24h)
 * - Schema-less JSON receive → publish to RabbitMQ
 * - Backpressure-safe sends with per-socket queue
 * - 24h+ idle/busy supported (idleTimeout + client pings)
 * - Ready for PM2 cluster (multiple processes on same port)
 */
import "dotenv/config";
import uWS from "uWebSockets.js";
import fs from "node:fs";
import { verifyTokenSync } from "./auth.js";
import { initRabbit, publishJSON } from "./rabbit.js";
import { makeSender } from "./backpressure.js";
import {
  initRedis,
  acquireDeviceLock,
  releaseDeviceLock,
  buildDeviceKey,
  isRedisEnabled,
  addLiveDevice,
  removeLiveDevice,
  getLiveCount,
  publishPush,
  subscribePush,
  isDeviceLive,
  sampleLive,
  setDeviceInfo,
  removeDeviceInfo,
  resetRegistry,
  getDeviceInfo,
  publishKick,
  subscribeKick,
} from "./redis.js";

/* ----------------------- ENV / CONFIG ----------------------- */
const PORT = Number(process.env.PORT || 8080);
const USE_SSL = !!(process.env.SSL_KEY && process.env.SSL_CERT);

function normalizePem(pem) {
  if (!pem) return "";
  // Convert escaped newlines ("\\n") from .env into real newlines
  if (pem.includes("\\n") && !pem.includes("\n")) {
    try {
      return pem.replace(/\\n/g, "\n");
    } catch {}
  }
  return pem;
}

function loadPublicKey() {
  // 1) Prefer file path
  const filePath = process.env.AUTH_PUBLIC_PEM_FILE;
  if (filePath && filePath.length) {
    try {
      if (fs.existsSync(filePath)) {
        return fs.readFileSync(filePath, "utf8");
      }
    } catch {}
  }
  // 2) Support base64-encoded PEM
  const b64 = process.env.AUTH_PUBLIC_PEM_B64;
  if (b64 && b64.length) {
    try {
      return Buffer.from(b64, "base64").toString("utf8");
    } catch {}
  }
  // 3) Inline (with \n escapes handled)
  const inline = process.env.AUTH_PUBLIC_PEM;
  if (inline && inline.length) return normalizePem(inline);
  return "";
}

const AUTH_PUBLIC_PEM = loadPublicKey();
const RABBIT_URL = process.env.RABBIT_URL;
const EXCHANGE = process.env.RABBIT_EXCHANGE || "ws_ingress";
const ROUTING_KEY = process.env.RABBIT_ROUTING_KEY || "client.event";
const REDIS_URL = process.env.REDIS_URL || ""; // optional

const IDLE_TIMEOUT = Number(process.env.IDLE_TIMEOUT || 120);
const MAX_PAYLOAD = Number(process.env.MAX_PAYLOAD || 1024 * 1024);
const MAX_BACKPRESSURE = Number(process.env.MAX_BACKPRESSURE || 64 * 1024);
const WS_COMPRESSION = (process.env.WS_COMPRESSION || "none").toLowerCase(); // none|shared
const WS_WELCOME =
  String(process.env.WS_WELCOME || "false").toLowerCase() === "true";
const WS_ACK = String(process.env.WS_ACK || "false").toLowerCase() === "true";
const DEBUG_WS =
  String(process.env.DEBUG_WS || "true").toLowerCase() === "true"; // temporary debug
const AUTH_DEV_MODE =
  String(process.env.AUTH_DEV_MODE || "false").toLowerCase() === "true"; // bypass JWT for quick tests

if (!AUTH_PUBLIC_PEM)
  throw new Error(
    "AUTH_PUBLIC_PEM missing (set AUTH_PUBLIC_PEM_FILE or AUTH_PUBLIC_PEM_B64 or AUTH_PUBLIC_PEM with \\n escapes)"
  );
if (!RABBIT_URL) throw new Error("RABBIT_URL missing");

/* ----------------------- BOOT RABBIT ------------------------ */
await initRabbit(RABBIT_URL, EXCHANGE);
if (REDIS_URL) {
  try {
    await initRedis(REDIS_URL);
    // Reset registry on boot (clean stale entries from previous runs)
    await resetRegistry();
    // subscribe to push bus to deliver to locally-connected sockets
    await subscribePush(({ deviceId, payload }) => {
      const ws = connectionsByDevice.get(String(deviceId));
      if (ws && ws.alive) {
        ws.sender = ws.sender || makeSender();
        ws.sender.enqueue(ws, Buffer.from(JSON.stringify(payload)));
      }
    });
    // subscribe to kick bus to close local socket if connected
    await subscribeKick(({ deviceId, reason }) => {
      const ws = connectionsByDevice.get(String(deviceId));
      if (ws && ws.alive) {
        // If this worker initiated a takeover for this device very recently,
        // ignore the broadcast kick to avoid self-closing the new connection.
        if (ws._ignoreKickUntil && Date.now() < ws._ignoreKickUntil) return;
        try {
          ws.end(4401, reason || "takeover");
        } catch {}
      }
    });
  } catch (e) {
    console.error("[Redis] init failed", e?.message || e);
  }
}

/* ------------------- WS BEHAVIOR & HELPERS ------------------ */
function safeJSON(u8) {
  try {
    return JSON.parse(Buffer.from(u8).toString("utf8"));
  } catch {
    return null;
  }
}

function getParamCI(searchParams, name) {
  const needle = String(name).toLowerCase();
  for (const [k, v] of searchParams) {
    if (String(k).toLowerCase() === needle) return v;
  }
  return null;
}

// Active connection registries
const connectionsByDevice = new Map(); // deviceId -> ws
const connectionsById = new Map(); // connId -> ws

function json(res, code, obj) {
  res.cork(() => {
    res
      .writeStatus(String(code))
      .writeHeader("content-type", "application/json")
      .end(JSON.stringify(obj));
  });
}

function readJsonBody(res, onBody) {
  let buf = Buffer.alloc(0);
  res.onData((ab, isLast) => {
    const chunk = Buffer.from(ab);
    buf = Buffer.concat([buf, chunk]);
    if (isLast) {
      try {
        onBody(null, JSON.parse(buf.toString("utf8")));
      } catch (e) {
        onBody(e);
      }
    }
  });
  res.onAborted(() => {});
}

const behavior = {
  compression:
    WS_COMPRESSION === "shared" ? uWS.SHARED_COMPRESSOR : uWS.DISABLED,
  maxPayloadLength: MAX_PAYLOAD,
  idleTimeout: IDLE_TIMEOUT,
  maxBackpressure: MAX_BACKPRESSURE,
  upgrade: (res, req, context) => {
    const proto = req.getHeader("sec-websocket-protocol");
    const authz = req.getHeader("authorization");
    const rawQuery = req.getQuery();
    const url = new URL(
      "http://x" + req.getUrl() + (rawQuery ? "?" + rawQuery : "")
    );

    const token =
      (proto && proto.trim()) ||
      (authz && authz.replace(/^Bearer\s+/i, "")) ||
      url.searchParams.get("token");

    if (!token) {
      if (DEBUG_WS) console.error("[UPGRADE-FAIL] missing token");
      res.writeStatus("401 Unauthorized").end();
      return;
    }

    let payload;
    if (AUTH_DEV_MODE) {
      // Fast path for local tests
      payload = { sub: "dev", exp: Math.floor(Date.now() / 1000) + 3600 };
    } else {
      try {
        payload = verifyTokenSync(token, AUTH_PUBLIC_PEM, 60);
      } catch (e) {
        if (DEBUG_WS)
          console.error("[UPGRADE-FAIL] jwt verify:", e?.message || e);
        res.writeStatus("401 Unauthorized").end();
        return;
      }
    }

    // Enforce deviceId from JWT only
    const deviceId = payload.deviceId || payload.did;
    const appId = payload.appId || payload.aid || payload.sub || "unknown";
    const boundIp = payload.ip;
    if (!deviceId) {
      if (DEBUG_WS)
        console.error("[UPGRADE-FAIL] missing deviceId (query or JWT)");
      res.writeStatus("401 Unauthorized").end();
      return;
    }

    const ipStr = getClientIp(req);
    if (boundIp) {
      if (ipStr && ipStr !== boundIp) {
        if (DEBUG_WS)
          console.error(`
          [UPGRADE-FAIL] ip mismatch token_ip=${boundIp} client_ip=${ipStr}
        `);
        res.writeStatus("401 Unauthorized").end();
        return;
      }
    }

    const secKey = req.getHeader("sec-websocket-key");
    const secProtocol = proto || "";
    const secExtensions = req.getHeader("sec-websocket-extensions");
    const connId = cryptoRandom();

    if (DEBUG_WS)
      console.log(`[UPGRADE] dev=${deviceId} app=${appId} ip=${ipStr}`);

    res.upgrade(
      {
        userId: payload.sub || payload.userId || "unknown",
        exp: payload.exp || 0,
        appId,
        deviceId,
        // Prefer JWT ip if present (already validated to match client), else use client IP
        ip: boundIp || ipStr || null,
        connId,
        deviceKey: buildDeviceKey(appId, deviceId),
      },
      secKey,
      secProtocol,
      secExtensions,
      context
    );
  },

  open: (ws) => {
    ws.alive = true;
    ws._openedTs = Date.now();
    // Lazy sender: create on first use to reduce per-conn footprint
    ws.sender = null;
    // Defer local/Redis registration until after we acquire the device lock
    ws._registered = false;
    const registerConnection = () => {
      if (ws._registered) return;
      ws._registered = true;
      if (ws.deviceId) {
        connectionsByDevice.set(ws.deviceId, ws);
        if (isRedisEnabled()) {
          if (DEBUG_WS)
            console.log(
              `[OPEN] dev=${ws.deviceId} conn=${ws.connId} ip=${ws.ip}`
            );
          addLiveDevice(ws.deviceId).catch(() => {});
          setDeviceInfo(ws.deviceId, {
            appId: ws.appId || "",
            connId: ws.connId || "",
            ip: ws.ip || "",
            ts: Date.now(),
          }).catch(() => {});
        }
      }
      if (ws.connId) connectionsById.set(ws.connId, ws);
    };
    if (WS_WELCOME) {
      ws.sender = ws.sender || makeSender();
      ws.sender.enqueue(
        ws,
        Buffer.from(
          JSON.stringify({
            type: "welcome",
            userId: ws.userId,
            deviceId: ws.deviceId,
            connId: ws.connId,
          })
        )
      );
    }

    // If Redis is disabled, register immediately (no global locking)
    if (!isRedisEnabled()) {
      registerConnection();
    }

    // Enforce single active connection per device via Redis lock with takeover
    // Acquire lock right after upgrade (uWS forbids async during upgrade)
    const ttlSec = Math.max(1, (ws.exp || 0) - Math.floor(Date.now() / 1000));
    if (isRedisEnabled() && ws.deviceKey && ttlSec > 0) {
      const attemptAcquire = async (retries = 1) => {
        const val = cryptoRandom();
        ws.lockVal = val;
        const ok = await acquireDeviceLock(ws.deviceKey, val, ttlSec);
        if (ok) return true;
        // Someone else holds the lock → kick and retry once
        try {
          // kick locally and broadcast to other workers
          const old = connectionsByDevice.get(ws.deviceId);
          if (old && old !== ws) {
            try {
              old.end(4401, "takeover");
            } catch {}
          }
          // Broadcast kick to other workers; guard against self-kick locally
          ws._ignoreKickUntil = Date.now() + 500;
          await publishKick({ deviceId: ws.deviceId, reason: "takeover" });
        } catch {}
        if (retries > 0) {
          await sleep(150);
          return attemptAcquire(retries - 1);
        }
        return false;
      };
      attemptAcquire(2)
        .then((acquired) => {
          if (!acquired) {
            try {
              ws.sender = ws.sender || makeSender();
              ws.sender.enqueue(
                ws,
                Buffer.from(
                  JSON.stringify({
                    type: "error",
                    error: "duplicate_connection",
                  })
                )
              );
            } finally {
              ws.end(1013, "dup");
            }
          } else {
            // Lock acquired: now safe to register locally/redis without self-kicking
            registerConnection();
          }
        })
        .catch(() => {});
    }

    // Optional: auto-close slightly after token expiry to enforce refresh
    if (ws.exp) {
      const ms = Math.max(1000, ws.exp * 1000 - Date.now() + 1500);
      ws._expiryTimer = setTimeout(() => {
        try {
          ws.end(1000, "exp");
        } catch {}
      }, ms);
    }
  },

  message: (ws, msg /* ArrayBuffer */, isBinary) => {
    const obj = safeJSON(msg);
    if (!obj) {
      ws.sender = ws.sender || makeSender();
      ws.sender.enqueue(
        ws,
        Buffer.from(JSON.stringify({ type: "error", error: "bad_json" }))
      );
      return;
    }
    // Normalize topic & payload
    const topic =
      typeof obj.topic === "string" && obj.topic ? obj.topic : ROUTING_KEY;
    const data = Object.prototype.hasOwnProperty.call(obj, "data")
      ? obj.data
      : obj;

    // Envelope with sender metadata
    const envelope = {
      topic,
      data,
      sender: {
        kind: "android",
        appId: ws.appId,
        deviceId: ws.deviceId,
        ip: ws.ip || null,
        connId: ws.connId,
      },
      ts: Date.now(),
    };

    const published = publishJSON(EXCHANGE, topic, envelope);
    // Test-friendly echo:
    if (obj.type === "ping") {
      ws.sender = ws.sender || makeSender();
      ws.sender.enqueue(
        ws,
        Buffer.from(JSON.stringify({ type: "pong", t: Date.now() }))
      );
      return;
    }
    // Optional ACK (env-controlled): only after publish ok
    if (WS_ACK) {
      ws.sender = ws.sender || makeSender();
      if (published) {
        ws.sender.enqueue(
          ws,
          Buffer.from(JSON.stringify({ type: "ack", id: obj.id || null }))
        );
      } else {
        ws.sender.enqueue(
          ws,
          Buffer.from(
            JSON.stringify({ type: "error", error: "publish_failed", id: obj.id || null })
          )
        );
      }
    }
  },

  drain: (ws) => {
    ws.sender?.flush(ws);
  },

  close: (ws, code, msg) => {
    ws.alive = false;
    if (ws.sender) ws.sender.q.length = 0;
    if (ws._expiryTimer) clearTimeout(ws._expiryTimer);
    if (ws.deviceKey && ws.lockVal && isRedisEnabled()) {
      // fire and forget
      releaseDeviceLock(ws.deviceKey, ws.lockVal).catch(() => {});
    }
    if (ws.deviceId) {
      connectionsByDevice.delete(ws.deviceId);
      if (isRedisEnabled()) {
        if (DEBUG_WS)
          console.log(`[CLOSE] dev=${ws.deviceId} conn=${ws.connId}`);
        removeLiveDevice(ws.deviceId).catch(() => {});
        removeDeviceInfo(ws.deviceId).catch(() => {});
      }
    }
    if (ws.connId) connectionsById.delete(ws.connId);
  },
};

/* ----------------------- APP & UPGRADE ---------------------- */
const app = USE_SSL
  ? uWS.SSLApp({
      key_file_name: process.env.SSL_KEY,
      cert_file_name: process.env.SSL_CERT,
    })
  : uWS.App();

// health (plain text)
app.get("/health", (res) => {
  res.cork(() => res.end("ok"));
});

// Register WS behavior early
app.ws("/*", behavior);

// debug: redis view
app.get("/debug/redis", (res, req) => {
  const requestIp = getClientIp(req);
  if (!isRedisEnabled()) {
    json(res, 200, { enabled: false, requestIp });
    return;
  }
  let aborted = false;
  res.onAborted(() => {
    aborted = true;
  });
  Promise.all([getLiveCount(), sampleLive(20)])
    .then(async ([count, ids]) => {
      if (aborted) return;
      const sample = [];
      for (const id of ids) {
        const info = await getDeviceInfo(id);
        sample.push({
          deviceId: id,
          appId: info.appId || "",
          connId: info.connId || "",
          ip: info.ip || "",
          ts: Number(info.ts || 0),
          online: true,
        });
      }
      json(res, 200, { enabled: true, count, sample, requestIp });
    })
    .catch((e) => {
      if (!aborted)
        json(res, 500, { error: String(e?.message || e), requestIp });
    });
});

// Inspect a single device registry entry
app.get("/debug/conn/:id", (res, req) => {
  const requestIp = getClientIp(req);
  const id = req.getParameter(0);
  if (!isRedisEnabled()) {
    json(res, 200, { enabled: false, requestIp });
    return;
  }
  let aborted = false;
  res.onAborted(() => {
    aborted = true;
  });
  Promise.all([isDeviceLive(id), getDeviceInfo(id)])
    .then(([online, info]) => {
      if (!aborted) json(res, 200, { id, online, info, requestIp });
    })
    .catch((e) => {
      if (!aborted)
        json(res, 500, { error: String(e?.message || e), requestIp });
    });
});

// List connections (sample up to 10k)
app.get("/connections", (res, req) => {
  const sample = [];
  let i = 0;
  for (const [deviceId, ws] of connectionsByDevice) {
    sample.push({
      deviceId,
      appId: ws.appId,
      connId: ws.connId,
      ip: ws.ip || "",
      ts: ws._openedTs || 0,
      online: true,
    });
    if (++i >= 10000) break;
  }
  if (isRedisEnabled()) {
    let aborted = false;
    res.onAborted(() => {
      aborted = true;
    });
    Promise.all([getLiveCount(), sampleLive(50)])
      .then(async ([count, ids]) => {
        if (aborted) return;
        const detailed = [];
        for (const id of ids) {
          const info = await getDeviceInfo(id);
          detailed.push({
            deviceId: id,
            appId: info.appId || "",
            connId: info.connId || "",
            ip: info.ip || "",
            ts: Number(info.ts || 0),
            online: true,
          });
        }
        json(res, 200, { count, sample: detailed });
      })
      .catch(() => {
        if (!aborted) json(res, 500, { error: "redis_error" });
      });
    return;
  }
  json(res, 200, { count: connectionsByDevice.size, sample });
});

// Push message to a specific device
// POST /push/device { deviceId, payload }
app.post("/push/device", (res, req) => {
  const requestIp = getClientIp(req);
  readJsonBody(res, async (err, body) => {
    if (err) return json(res, 400, { error: "bad_json" });
    const deviceId = body?.deviceId;
    const payload = body?.payload;
    if (!deviceId || !payload)
      return json(res, 400, { error: "missing_fields" });

    // If Redis enabled, publish to bus so proper worker delivers it
    if (isRedisEnabled()) {
      await publishPush({ deviceId: String(deviceId), payload });
      const live = await isDeviceLive(String(deviceId));
      json(res, 200, { accepted: true, online: live, requestIp });
      return;
    }

    // Fallback: single-process/local delivery
    const ws = connectionsByDevice.get(String(deviceId));
    if (!ws || !ws.alive) return json(res, 404, { error: "not_connected" });
    ws.sender = ws.sender || makeSender();
    ws.sender.enqueue(ws, Buffer.from(JSON.stringify(payload)));
    json(res, 200, { delivered: true, requestIp });
  });
});

// Minimal HTML UI listing connections (served from server to avoid CORS)
app.get("/ui/connections", (res, req) => {
  const html = `<!DOCTYPE html>
  <html lang="en"><head><meta charset="utf-8" />
  <title>Connections</title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <style>
    body{font-family:Segoe UI,Roboto,Arial,sans-serif;background:#0f111a;color:#e0e0e0;margin:0;padding:24px}
    h2{color:#00ffc8;text-shadow:0 0 8px #00ffc8;margin:0 0 16px}
    .bar{display:flex;gap:8px;align-items:center;margin-bottom:12px}
    button{background:#25273a;color:#e0e0e0;border:none;border-radius:8px;padding:8px 12px;font-weight:600;cursor:pointer}
    button:hover{background:#00ffc8;color:#0f111a}
    table{width:100%;border-collapse:collapse;background:#1b1d2a;border-radius:10px;overflow:hidden}
    th,td{padding:8px 10px;border-bottom:1px solid #2e324d;font-size:13px}
    th{background:#141625;text-align:left;color:#a9b2c3}
    tr:hover{background:#202338}
    .muted{color:#a9b2c3}
  </style></head>
  <body>
  <h2>Connected Devices</h2>
  <div class="bar"><button onclick="loadNow()">Refresh</button><span class="muted" id="meta"></span></div>
  <table><thead><tr><th>Device ID</th><th>App ID</th><th>Conn ID</th><th>IP</th><th>TS</th><th>Online</th></tr></thead>
  <tbody id="rows"><tr><td class="muted" colspan="6">Loading...</td></tr></tbody></table>
  <script>
    async function loadNow(){
      const res = await fetch('/connections');
      const j = await res.json().catch(() => ({}));
      const rows = document.getElementById('rows');
      rows.innerHTML='';
  document.getElementById('meta').textContent = 'count='+(j.count||0);
      const sample = j.sample||[];
      if(!sample.length){ rows.innerHTML = '<tr><td class="muted" colspan="6">No connections</td></tr>'; return; }
      for(const it of sample){
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>' + (it.deviceId||'') + '</td>'
          + '<td>' + (it.appId||'') + '</td>'
          + '<td>' + (it.connId||'') + '</td>'
          + '<td>' + (it.ip||'') + '</td>'
          + '<td>' + (it.ts||'') + '</td>'
          + '<td>' + (it.online? 'yes':'no') + '</td>';
        rows.appendChild(tr);
      }
    }
    loadNow();
    setInterval(loadNow, 3000);
  </script>
  </body></html>`;
  res.cork(() => {
    res.writeHeader("content-type", "text/html; charset=utf-8");
    res.end(html);
  });
});

// (manual app.any upgrade removed; handled by behavior.upgrade)

app.listen(PORT, (token) => {
  if (!token) {
    console.error("Failed to bind", PORT);
    process.exit(1);
  }
  console.log(`uWS listening on :${PORT} ${USE_SSL ? "(TLS)" : ""}`);
});

function getClientIp(req) {
  const xff = req.getHeader("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  try {
    const u8 = req.getRemoteAddressAsText();
    return Buffer.from(u8).toString();
  } catch {
    return "";
  }
}

// Helper for Redis lock value
function cryptoRandom() {
  const arr = new Uint8Array(16);
  for (let i = 0; i < arr.length; i++) arr[i] = (Math.random() * 256) | 0;
  return Buffer.from(arr).toString("hex");
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}
