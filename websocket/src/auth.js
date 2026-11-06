// Fast, sync JWT verification using a provided PEM (AUTH_PUBLIC_PEM).
// No network calls in the upgrade path (VERY important for uWS).
import jwt from "jsonwebtoken";

/**
 * verifyTokenSync
 * @param {string} token - JWT from client
 * @param {string} pem - RSA/HS public key (PEM) from env AUTH_PUBLIC_PEM
 * @param {number} clockSkewSec - allowed clock skew
 * @returns {object} decoded payload { sub, exp, ... }
 * @throws on invalid/expired token
 */
export function verifyTokenSync(token, pem, clockSkewSec = 60) {
  if (!pem) throw new Error("Missing AUTH_PUBLIC_PEM");
  const payload = jwt.verify(token, pem, {
    algorithms: ["RS256"],
    clockTolerance: clockSkewSec
  });
  return payload;
}
