// Lightweight Redis wrapper for device connection locks
// Uses ioredis for reliability with Bun and reconnection handling
import Redis from "ioredis";

let redis = null;
let pub = null;
let sub = null;

// Keys/Channels
const LIVE_SET_KEY = "ws:live"; // contains deviceIds of currently connected devices
const PUSH_CHANNEL = "ws:push"; // pub/sub channel for push-by-deviceId
const CONN_INFO_PREFIX = "ws:conn:"; // per-device hash with conn metadata
const KICK_CHANNEL = "ws:kick"; // request workers to close a device connection

export function isRedisEnabled() {
  return !!redis;
}

export async function initRedis(url) {
  if (!url) return null;
  redis = new Redis(url, {
    lazyConnect: true,
    maxRetriesPerRequest: 2,
    enableAutoPipelining: true
  });
  redis.on("error", (e) => console.error("[Redis]", e?.message || e));
  await redis.connect();
  console.log("[Redis] connected");

  // pub/sub connections (duplicated for isolation)
  pub = redis.duplicate();
  sub = redis.duplicate();
  pub.on("error", (e) => console.error("[Redis pub]", e?.message || e));
  sub.on("error", (e) => console.error("[Redis sub]", e?.message || e));
  await Promise.all([pub.connect(), sub.connect()]);
  return redis;
}

// Try to acquire a device lock with TTL (seconds)
// key: ws:dev:{appId}:{deviceId}
export async function acquireDeviceLock(key, value, ttlSec) {
  if (!redis) return true; // if disabled, allow
  try {
    const ok = await redis.set(key, value, "EX", Math.max(1, ttlSec | 0), "NX");
    return ok === "OK";
  } catch (e) {
    console.error("[Redis] acquire error", e?.message || e);
    // Fail-open by default to avoid accidental outage; flip via env if you want fail-close
    return true;
  }
}

// Release the device lock only if value matches (atomic with Lua)
export async function releaseDeviceLock(key, value) {
  if (!redis) return true;
  const lua = `if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end`;
  try {
    const res = await redis.eval(lua, 1, key, value);
    return res === 1;
  } catch (e) {
    console.error("[Redis] release error", e?.message || e);
    return false;
  }
}

export function buildDeviceKey(appId, deviceId) {
  return `ws:dev:${appId || "na"}:${deviceId || "na"}`;
}

// ----------------- Live registry helpers -----------------
export async function addLiveDevice(deviceId) {
  if (!redis || !deviceId) return 0;
  try {
    return await redis.sadd(LIVE_SET_KEY, String(deviceId));
  } catch (e) {
    console.error("[Redis] addLiveDevice", e?.message || e);
    return 0;
  }
}

export async function removeLiveDevice(deviceId) {
  if (!redis || !deviceId) return 0;
  try {
    return await redis.srem(LIVE_SET_KEY, String(deviceId));
  } catch (e) {
    console.error("[Redis] removeLiveDevice", e?.message || e);
    return 0;
  }
}

export async function getLiveCount() {
  if (!redis) return 0;
  try {
    return await redis.scard(LIVE_SET_KEY);
  } catch (e) {
    console.error("[Redis] getLiveCount", e?.message || e);
    return 0;
  }
}

export async function isDeviceLive(deviceId) {
  if (!redis || !deviceId) return false;
  try {
    const n = await redis.sismember(LIVE_SET_KEY, String(deviceId));
    return n === 1;
  } catch (e) {
    return false;
  }
}

// Sample up to 'limit' members from the live set (for debug endpoints)
export async function sampleLive(limit = 50) {
  if (!redis) return [];
  let cursor = "0";
  const out = [];
  try {
    do {
      const [nextCursor, members] = await redis.sscan(
        LIVE_SET_KEY,
        cursor,
        "COUNT",
        Math.max(limit * 2, 10)
      );
      out.push(...members);
      cursor = nextCursor;
    } while (cursor !== "0" && out.length < limit);
  } catch (e) {
    console.error("[Redis] sampleLive", e?.message || e);
  }
  return out.slice(0, limit);
}

// ----------------- Per-device info CRUD -----------------
export async function setDeviceInfo(deviceId, info = {}) {
  if (!redis || !deviceId) return 0;
  try {
    const key = CONN_INFO_PREFIX + String(deviceId);
    if (!info || typeof info !== "object") return 0;
    // Flatten object to field/value pairs
    const entries = [];
    for (const [k, v] of Object.entries(info)) entries.push(k, String(v));
    if (entries.length === 0) return 0;
    return await redis.hset(key, ...entries);
  } catch (e) {
    console.error("[Redis] setDeviceInfo", e?.message || e);
    return 0;
  }
}

export async function removeDeviceInfo(deviceId) {
  if (!redis || !deviceId) return 0;
  try {
    const key = CONN_INFO_PREFIX + String(deviceId);
    return await redis.del(key);
  } catch (e) {
    console.error("[Redis] removeDeviceInfo", e?.message || e);
    return 0;
  }
}

export async function getDeviceInfo(deviceId) {
  if (!redis || !deviceId) return {};
  try {
    const key = CONN_INFO_PREFIX + String(deviceId);
    return await redis.hgetall(key);
  } catch (e) {
    return {};
  }
}

// Remove old registry entries on boot
export async function resetRegistry() {
  if (!redis) return { deleted: 0 };
  let deleted = 0;
  try {
    deleted += await redis.del(LIVE_SET_KEY);
  } catch (e) {
    console.error("[Redis] reset del set", e?.message || e);
  }
  // Scan and delete ws:conn:* keys in batches
  try {
    let cursor = "0";
    do {
      const [next, keys] = await redis.scan(cursor, "MATCH", CONN_INFO_PREFIX + "*", "COUNT", 1000);
      cursor = next;
      if (keys && keys.length) {
        const pipe = redis.pipeline();
        for (const k of keys) pipe.del(k);
        const res = await pipe.exec();
        for (const [err, val] of res) if (!err) deleted += (val || 0);
      }
    } while (cursor !== "0");
  } catch (e) {
    console.error("[Redis] reset scan", e?.message || e);
  }
  // Also purge any stale device locks ws:dev:*
  try {
    let cursor = "0";
    do {
      const [next, keys] = await redis.scan(cursor, "MATCH", "ws:dev:*", "COUNT", 1000);
      cursor = next;
      if (keys && keys.length) {
        const pipe = redis.pipeline();
        for (const k of keys) pipe.del(k);
        const res = await pipe.exec();
        for (const [err, val] of res) if (!err) deleted += (val || 0);
      }
    } while (cursor !== "0");
  } catch (e) {
    console.error("[Redis] reset locks", e?.message || e);
  }
  return { deleted };
}

// ----------------- Push pub/sub helpers -----------------
export async function publishPush({ deviceId, payload }) {
  if (!pub) return 0;
  try {
    const msg = JSON.stringify({ deviceId, payload });
    return await pub.publish(PUSH_CHANNEL, msg);
  } catch (e) {
    console.error("[Redis] publishPush", e?.message || e);
    return 0;
  }
}

export async function subscribePush(handler) {
  if (!sub) return false;
  try {
    await sub.subscribe(PUSH_CHANNEL);
    sub.on("message", (channel, message) => {
      if (channel !== PUSH_CHANNEL) return;
      try {
        const obj = JSON.parse(message);
        handler?.(obj);
      } catch {}
    });
    return true;
  } catch (e) {
    console.error("[Redis] subscribePush", e?.message || e);
    return false;
  }
}

// ----------------- Kick pub/sub helpers -----------------
export async function publishKick({ deviceId, reason = "takeover" }) {
  if (!pub) return 0;
  try {
    return await pub.publish(KICK_CHANNEL, JSON.stringify({ deviceId: String(deviceId), reason }));
  } catch (e) {
    console.error("[Redis] publishKick", e?.message || e);
    return 0;
  }
}

export async function subscribeKick(handler) {
  if (!sub) return false;
  try {
    await sub.subscribe(KICK_CHANNEL);
    sub.on("message", (channel, message) => {
      if (channel !== KICK_CHANNEL) return;
      try {
        const obj = JSON.parse(message);
        handler?.(obj);
      } catch {}
    });
    return true;
  } catch (e) {
    console.error("[Redis] subscribeKick", e?.message || e);
    return false;
  }
}
