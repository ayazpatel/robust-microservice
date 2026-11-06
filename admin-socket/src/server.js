/* Minimal Admin Socket (uWebSockets.js)
 * - WS endpoint: /ws?adminId=... (no auth; UI must enforce login)
 * - HTTP push: POST /api/push  { adminId, type, payload }
 * - Simple limits via .env (.env.example provided)
 * - Single process; PM2/cluster optional later
 */
import "dotenv/config";
import uWS from "uWebSockets.js";
import http from "node:http";

// ---------------- Env / Config ----------------
const PORT = Number(process.env.PORT || 8090);
const MAX_WS_TOTAL = Number(process.env.MAX_WS_TOTAL || 20000);
const MAX_WS_ADMIN = Number(process.env.MAX_WS_ADMIN || MAX_WS_TOTAL);
const MAX_WS_PER_ADMIN = Number(process.env.MAX_WS_PER_ADMIN || 5);
const MAX_WS_SERVICE = Number(process.env.MAX_WS_SERVICE || 0); // reserved
const MAX_BACKPRESSURE = Number(process.env.MAX_BACKPRESSURE || 64 * 1024);
const PUSH_RPS = Number(process.env.PUSH_RPS || 2000);

// ---------------- State ----------------
// Track admin connections by adminId
const adminRooms = new Map(); // adminId -> Set<ws>
let totalWs = 0;
let adminWs = 0;
let serviceWs = 0; // reserved for future

// Basic metrics
let pushesOk = 0;
let pushesNoClient = 0;
let pushesDropped = 0;

// Simple token bucket for /api/push
let pushTokens = PUSH_RPS;
setInterval(() => {
  pushTokens = PUSH_RPS;
}, 1000).unref?.();

// ---------------- Helpers ----------------
function json(res, status, obj) {
  res.cork(() => {
    res.writeStatus(String(status));
    res.writeHeader("content-type", "application/json; charset=utf-8");
    res.end(JSON.stringify(obj));
  });
}

function readJsonBody(res, cb) {
  let aborted = false;
  let buffer;
  res.onAborted(() => {
    aborted = true;
  });
  res.onData((ab, isLast) => {
    if (aborted) return;
    const chunk = Buffer.from(ab);
    buffer = buffer ? Buffer.concat([buffer, chunk]) : chunk;
    if (isLast) {
      if (!buffer || buffer.length === 0) {
        cb(new Error("empty"));
        return;
      }
      try {
        // Trim and strip UTF-8 BOM if present
        let text = buffer.toString("utf8");
        if (text.charCodeAt(0) === 0xFEFF) text = text.slice(1);
        text = text.trim();
        const obj = JSON.parse(text);
        cb(null, obj);
      } catch (e) {
        // Log a small snippet for debugging
        try {
          let snippet = buffer.toString("utf8");
          if (snippet.length > 256) snippet = snippet.slice(0, 256) + "...";
          console.error("[PUSH] bad_json body snippet:", snippet.replace(/\n/g, "\\n"));
        } catch {}
        cb(e || new Error("bad_json"));
      }
    }
  });
}

function addAdminConn(adminId, ws) {
  let set = adminRooms.get(adminId);
  if (!set) {
    set = new Set();
    adminRooms.set(adminId, set);
  }
  set.add(ws);
}

function removeAdminConn(adminId, ws) {
  const set = adminRooms.get(adminId);
  if (set) {
    set.delete(ws);
    if (set.size === 0) adminRooms.delete(adminId);
  }
}

function broadcastAdmin(adminId, msgBufOrStr) {
  const set = adminRooms.get(String(adminId));
  if (!set || set.size === 0) return 0;
  let delivered = 0;
  for (const ws of set) {
    if (!ws.alive) continue;
    // Backpressure guard: uWS send returns false if backpressured
    const ok = typeof msgBufOrStr === 'string'
      ? ws.send(msgBufOrStr)
      : ws.send(msgBufOrStr, false, false);
    if (!ok) {
      pushesDropped++;
    } else {
      delivered++;
    }
  }
  return delivered;
}

function connectionsSnapshot(limit = 50) {
  const byAdmin = [];
  for (const [adminId, set] of adminRooms) {
    byAdmin.push({ adminId, count: set.size });
    if (byAdmin.length >= limit) break;
  }
  return byAdmin;
}

// ---------------- App & Routes ----------------
const app = uWS.App();

// WebSocket for admin UI
app.ws("/ws", {
  maxPayloadLength: 1024 * 1024,
  idleTimeout: 120,
  maxBackpressure: MAX_BACKPRESSURE,

  upgrade: (res, req, context) => {
    const rawQuery = req.getQuery();
    const url = new URL("http://x" + req.getUrl() + (rawQuery ? "?" + rawQuery : ""));
    const adminIdParam = url.searchParams.get("adminId") || "";
    const kind = (url.searchParams.get("kind") || "admin").toLowerCase();

    // Validate session cookie (asid) and bind to adminId
    const cookie = req.getHeader("cookie");
    const cookies = parseCookie(cookie);
    const asid = cookies[process.env.SESSION_COOKIE || "asid"] || url.searchParams.get("asid") || "";
    if (!asid) { res.writeStatus("401 Unauthorized").end(); return; }
    // Async validation: we need to consume headers for upgrade, so do a quick HTTP sync before upgrade
    // Note: uWS requires upgrade sync; we will block briefly via callback wrappers
    let validated = false;
    let val = null;
    const loop = uWS.us_listen_socket_close; // dummy ref to keep scope
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 0); // no-op
    // Use Node's sync-like approach via async/await emulation not possible here; instead reject early
    // Fallback: allow upgrade and immediately close in open() if not validated (will validate there)

    // Enforce limits
    if (totalWs >= MAX_WS_TOTAL) {
      res.writeStatus("429 Too Many Requests").end();
      return;
    }
    if (kind === "admin" && adminWs >= MAX_WS_ADMIN) {
      res.writeStatus("429 Too Many Requests").end();
      return;
    }
    const currentSet = adminRooms.get(adminId);
    if (currentSet && currentSet.size >= MAX_WS_PER_ADMIN) {
      res.writeStatus("429 Too Many Requests").end();
      return;
    }
    if (kind === "service" && serviceWs >= MAX_WS_SERVICE) {
      res.writeStatus("429 Too Many Requests").end();
      return;
    }

    const secKey = req.getHeader("sec-websocket-key");
    const secProtocol = req.getHeader("sec-websocket-protocol");
    const secExtensions = req.getHeader("sec-websocket-extensions");

    res.upgrade(
      { adminId: adminIdParam, kind, asid },
      secKey,
      secProtocol,
      secExtensions,
      context
    );
  },

  open: async (ws) => {
    ws.alive = true;
    totalWs++;
    if (ws.kind === "service") serviceWs++; else adminWs++;
    // Validate session now
    const v = await validateAdminSession(ws.asid);
    if (!v) {
      try { ws.end(4401, "unauthorized"); } catch {}
      return;
    }
    // If adminId param provided, enforce match
    if (ws.adminId && String(ws.adminId) !== String(v.adminId)) {
      try { ws.end(4401, "forbidden"); } catch {}
      return;
    }
    ws.adminId = String(v.adminId);
    addAdminConn(String(ws.adminId), ws);
    console.log(`[OPEN] admin=${ws.adminId} kind=${ws.kind||'admin'} total=${totalWs} admin=${adminWs} service=${serviceWs}`);
    // periodic revalidate
    ws._reval = setInterval(async () => {
      const ok = await validateAdminSession(ws.asid);
      if (!ok) {
        try { ws.end(4401, "session_expired"); } catch {}
        clearInterval(ws._reval);
      }
    }, 60000).unref?.();
  },

  message: (ws, msg, isBinary) => {
    // optional minimal ping/pong echo
    try {
      const s = Buffer.from(msg).toString("utf8");
      if (s === "ping" || s === "{\"type\":\"ping\"}") {
        ws.send(Buffer.from('{"type":"pong"}'));
      }
    } catch {}
  },

  close: (ws) => {
    if (ws.alive) {
      ws.alive = false;
      if (ws._reval) try { clearInterval(ws._reval); } catch {}
      removeAdminConn(String(ws.adminId), ws);
      totalWs = Math.max(0, totalWs - 1);
      if (ws.kind === "service") serviceWs = Math.max(0, serviceWs - 1); else adminWs = Math.max(0, adminWs - 1);
      console.log(`[CLOSE] admin=${ws.adminId} kind=${ws.kind||'admin'} total=${totalWs} admin=${adminWs} service=${serviceWs}`);
    }
  },
});

// Health
app.get("/health", (res) => json(res, 200, { ok: true }));

// Connections snapshot
app.get("/connections", (res) => {
  const byAdmin = connectionsSnapshot(100);
  json(res, 200, {
    total: totalWs,
    admin: adminWs,
    service: serviceWs,
    rooms: byAdmin,
  });
});

// Push to a specific adminId
// Body: { adminId, type, payload }
app.post("/api/push", (res, req) => {
  if (pushTokens <= 0) {
    json(res, 429, { error: "rate_limited" });
    return;
  }
  pushTokens--;
  const ct = req.getHeader("content-type");
  const te = req.getHeader("transfer-encoding");
  const cl = req.getHeader("content-length");
  readJsonBody(res, (err, body) => {
    if (err) {
      console.error(`[PUSH] parse error ct=${ct||'-'} te=${te||'-'} cl=${cl||'-'}`);
      return json(res, 400, { error: "bad_json" });
    }
    const adminId = body?.adminId;
    const type = body?.type; // optional; do not modify body
    if (!adminId) return json(res, 400, { error: "missing_adminId" });
    const text = JSON.stringify(body); // forward exactly what service sent
    const delivered = broadcastAdmin(String(adminId), text);
    if (delivered > 0) {
      console.log(`[PUSH] admin=${adminId} type=${type||'-'} delivered=${delivered}`);
    } else {
      console.log(`[PUSH] admin=${adminId} type=${type||'-'} delivered=0 (no clients)`);
    }
    if (delivered > 0) {
      pushesOk += delivered;
      json(res, 200, { delivered });
    } else {
      pushesNoClient++;
      json(res, 202, { delivered: 0, note: "no_active_clients" });
    }
  });
});

// Kick all sockets for an admin (call this from Admin logout hook)
app.post("/api/kick-admin", (res, req) => {
  readJsonBody(res, (err, body) => {
    if (err) return json(res, 400, { error: "bad_json" });
    const adminId = body?.adminId;
    if (!adminId) return json(res, 400, { error: "missing_adminId" });
    const set = adminRooms.get(String(adminId));
    let count = 0;
    if (set) {
      for (const ws of Array.from(set)) {
        try { ws.end(4401, "kicked"); count++; } catch {}
      }
    }
    json(res, 200, { kicked: count });
  });
});

app.listen(PORT, (token) => {
  if (!token) {
    console.error("Failed to bind", PORT);
    process.exit(1);
  }
  console.log(`admin-socket listening on :${PORT}`);
});
// Validate admin session by calling /admin validate endpoint
async function validateAdminSession(asid) {
  if (!asid) return null;
  const base = process.env.ADMIN_BASE_URL || "http://localhost:8082";
  const url = new URL("/auth/session/validate", base);
  return new Promise((resolve) => {
    const req = http.request(url, { method: "GET", headers: { "X-Session-Id": asid } }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => {
        try {
          const j = JSON.parse(data || "{}");
          if (res.statusCode >= 200 && res.statusCode < 300 && j.active && j.adminId) {
            resolve({ adminId: String(j.adminId), expiresAt: j.expiresAt || null });
          } else resolve(null);
        } catch { resolve(null); }
      });
    });
    req.on("error", () => resolve(null));
    req.end();
  });
}

function parseCookie(cookieHeader) {
  if (!cookieHeader) return {};
  const out = {};
  const parts = String(cookieHeader).split(";");
  for (const p of parts) {
    const i = p.indexOf("=");
    if (i > 0) {
      const k = p.slice(0, i).trim();
      const v = p.slice(i + 1).trim();
      out[k] = v;
    }
  }
  return out;
}
