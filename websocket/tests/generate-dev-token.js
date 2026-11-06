// Simple HS256 token generator for local testing
// Usage: node tests/generate-dev-token.js -AppId app.local -DeviceId phone-001 -Ip 127.0.0.1 -Ttl 3600
import jwt from "jsonwebtoken";

function arg(name, def) {
  const i = process.argv.findIndex((s) => s.toLowerCase() === ("-" + name.toLowerCase()));
  if (i >= 0 && process.argv[i + 1]) return process.argv[i + 1];
  return def;
}

const appId = arg("AppId", "app.local");
const deviceId = arg("DeviceId", "dev-1");
const ip = arg("Ip", "127.0.0.1");
const ttl = Number(arg("Ttl", "3600"));

const now = Math.floor(Date.now() / 1000);
const payload = {
  sub: appId,
  appId,
  deviceId,
  ip,
  iat: now,
  exp: now + ttl
};

const secret = process.env.AUTH_PUBLIC_PEM || "devsecret";
const token = jwt.sign(payload, secret, { algorithm: "HS256" });
console.log(token);
