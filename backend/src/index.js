import { gzipSync, gunzipSync } from "node:zlib";

const encoder = new TextEncoder();
const ID_PATTERN = /^[0-9a-fA-F-]{36}$/;

class HttpError extends Error {
  constructor(status, code, message, details = null) {
    super(message);
    this.status = status;
    this.code = code;
    if (details && typeof details === "object") this.details = details;
  }
}

function configuredOrigins(env) {
  return String(env.ALLOWED_ORIGINS || "")
    .split(",")
    .map(value => value.trim())
    .filter(Boolean);
}

function response(request, data, status = 200, env = {}) {
  const origin = request.headers.get("Origin");
  const allowed = configuredOrigins(env);
  const originAllowed = !origin || allowed.includes(origin);
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Cross-Origin-Resource-Policy": "same-origin",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Strict-Transport-Security": "max-age=31536000",
    "Vary": "Origin"
  };
  if (origin && originAllowed) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type";
    headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    headers["Access-Control-Max-Age"] = "600";
  }
  return new Response(JSON.stringify(data), { status, headers });
}

async function enforceRateLimit(request, env, bindingName) {
  const limiter = env[bindingName];
  if (!limiter || typeof limiter.limit !== "function") {
    if (env.RUNTIME_ENV === "production") {
      throw new HttpError(503, "rate_limit_not_configured", "Authentication rate limiting is not configured.");
    }
    return;
  }

  const clientIp = request.headers.get("CF-Connecting-IP");
  if (!clientIp) {
    throw new HttpError(503, "rate_limit_client_unavailable", "The client network identifier is unavailable.");
  }

  let result;
  try {
    result = await limiter.limit({ key: `ip:${clientIp}` });
  } catch {
    throw new HttpError(503, "rate_limit_unavailable", "Authentication rate limiting is temporarily unavailable.");
  }
  if (!result?.success) {
    throw new HttpError(429, "rate_limited", "Too many sign-in requests. Try again shortly.");
  }
}

function base64UrlEncode(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64UrlDecode(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

function randomToken(size = 32) {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

async function sha256(value) {
  const bytes = encoder.encode(value);
  try {
    return await sha256Bytes(bytes);
  } finally {
    bytes.fill(0);
  }
}

async function sha256Bytes(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}

function parseJson(value) {
  try {
    return JSON.parse(value);
  } catch {
    throw new HttpError(400, "invalid_json", "Invalid JSON body.");
  }
}

async function readBody(request, maxChars = 24_000) {
  const text = await request.text();
  if (text.length > maxChars) throw new HttpError(413, "body_too_large", "Request body is too large.");
  return text ? parseJson(text) : {};
}

export function isConfiguredOwnerIdentity(identity, env) {
  const configuredSub = typeof env?.OWNER_GOOGLE_SUB === "string"
    ? env.OWNER_GOOGLE_SUB.trim()
    : "";
  return configuredSub.length > 0
    && typeof identity?.sub === "string"
    && identity.sub.length > 0
    && identity.sub === configuredSub;
}

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

async function verifyGoogleIdToken(idToken, expectedNonce, env) {
  if (!env.GOOGLE_CLIENT_ID) {
    throw new HttpError(503, "google_auth_not_configured", "Google Sign-In is not configured.");
  }
  if (typeof idToken !== "string" || idToken.length > 16000) {
    throw new HttpError(400, "invalid_id_token", "A valid Google ID token is required.");
  }

  const parts = idToken.split(".");
  if (parts.length !== 3) throw new HttpError(401, "invalid_id_token", "Invalid Google ID token.");
  let header;
  let claims;
  try {
    header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
  } catch {
    throw new HttpError(401, "invalid_id_token", "Invalid Google ID token.");
  }
  if (header.alg !== "RS256" || typeof header.kid !== "string") {
    throw new HttpError(401, "invalid_id_token", "Unsupported Google ID token signature.");
  }

  const keyResponse = await fetch("https://www.googleapis.com/oauth2/v3/certs", {
    headers: { "Accept": "application/json" },
    cf: { cacheTtl: 300, cacheEverything: true }
  });
  if (!keyResponse.ok) {
    throw new HttpError(503, "google_keys_unavailable", "Google signing keys are temporarily unavailable.");
  }
  const keySet = await keyResponse.json();
  const jwk = (keySet.keys || []).find(key => key.kid === header.kid && key.kty === "RSA" && key.use === "sig");
  if (!jwk) throw new HttpError(401, "unknown_signing_key", "Google signing key was not recognized.");

  const publicKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const validSignature = await crypto.subtle.verify(
    { name: "RSASSA-PKCS1-v1_5" },
    publicKey,
    base64UrlDecode(parts[2]),
    encoder.encode(parts[0] + "." + parts[1])
  );
  if (!validSignature) throw new HttpError(401, "invalid_signature", "Google ID token signature is invalid.");

  const now = Math.floor(Date.now() / 1000);
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  const issuerValid = claims.iss === "https://accounts.google.com" || claims.iss === "accounts.google.com";
  if (!issuerValid || !audiences.includes(env.GOOGLE_CLIENT_ID)) {
    throw new HttpError(401, "invalid_token_audience", "Google ID token issuer or audience is invalid.");
  }
  if (claims.azp) {
    const allowedAuthorizedParties = new Set([
      env.GOOGLE_CLIENT_ID,
      ...(env.GOOGLE_ALLOWED_AZP || "")
        .split(",")
        .map(value => value.trim())
        .filter(Boolean)
    ]);
    if (!allowedAuthorizedParties.has(claims.azp)) {
      throw new HttpError(401, "invalid_authorized_party", "Google ID token authorized party is invalid.");
    }
  }
  if (!Number.isFinite(claims.exp) || claims.exp <= now ||
      !Number.isFinite(claims.iat) || claims.iat > now + 60) {
    throw new HttpError(401, "expired_id_token", "Google ID token has expired or is not yet valid.");
  }
  if (claims.nonce !== expectedNonce) {
    throw new HttpError(401, "nonce_mismatch", "Google ID token nonce does not match this sign-in attempt.");
  }
  if (typeof claims.sub !== "string" || !claims.sub ||
      typeof claims.email !== "string" ||
      !(claims.email_verified === true || claims.email_verified === "true")) {
    throw new HttpError(401, "unverified_google_account", "Google account email must be verified.");
  }
  const optionalText = (key, maxLength) =>
    typeof claims[key] === "string" ? claims[key].trim().slice(0, maxLength) || null : null;
  const optionalHttpsUrl = (key) => {
    const value = optionalText(key, 2048);
    if (!value) return null;
    try {
      const parsed = new URL(value);
      return parsed.protocol === "https:" ? parsed.toString() : null;
    } catch {
      return null;
    }
  };
  return {
    sub: claims.sub,
    email: normalizeEmail(claims.email),
    emailVerified: true,
    name: optionalText("name", 120),
    givenName: optionalText("given_name", 120),
    familyName: optionalText("family_name", 120),
    pictureUrl: optionalHttpsUrl("picture"),
    profileUrl: optionalHttpsUrl("profile"),
    locale: optionalText("locale", 40),
    hostedDomain: optionalText("hd", 253)
  };
}

async function getSession(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer ([A-Za-z0-9_-]{40,100})$/);
  if (!match) throw new HttpError(401, "authentication_required", "Sign in is required.");
  const tokenHash = await sha256(match[1]);
  const now = Math.floor(Date.now() / 1000);
  const session = await env.DB.prepare(
    "SELECT s.id AS session_id, s.user_id, s.expires_at, u.email, u.display_name, u.role, u.status, u.email_verified, u.google_sub, u.created_at, u.last_login_at " +
    "FROM sessions s JOIN users u ON u.id = s.user_id " +
    "WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?"
  ).bind(tokenHash, now).first();
  if (!session || session.status !== "ACTIVE" || session.email_verified !== 1) {
    throw new HttpError(401, "invalid_session", "Session is expired or no longer active.");
  }
  return session;
}

async function requireRole(session, roles) {
  if (!roles.includes(session.role)) {
    throw new HttpError(403, "insufficient_permissions", "This account cannot perform this action.");
  }
}

async function audit(env, actorId, action, targetType = null, targetId = null, metadata = {}) {
  await env.DB.prepare(
    "INSERT INTO audit_logs (id, actor_user_id, action, target_type, target_id, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).bind(
    crypto.randomUUID(), actorId, action, targetType, targetId,
    JSON.stringify(metadata), Math.floor(Date.now() / 1000)
  ).run();
}

function toLatestSignInDetails(metadataJson, createdAt) {
  if (typeof metadataJson !== "string" || !metadataJson.trim()) return null;
  let metadata;
  try {
    metadata = JSON.parse(metadataJson);
  } catch {
    return null;
  }
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) return null;
  return {
    createdAt: Number.isSafeInteger(Number(createdAt)) ? Number(createdAt) : null,
    deviceLabel: typeof metadata.deviceLabel === "string" ? metadata.deviceLabel.slice(0, 100) : null,
    googleProfile: metadata.googleProfile && typeof metadata.googleProfile === "object"
      ? metadata.googleProfile : null,
    deviceInfo: metadata.deviceInfo && typeof metadata.deviceInfo === "object"
      ? metadata.deviceInfo : null
  };
}

async function latestSignInDetails(env, userId) {
  const row = await env.DB.prepare(
    "SELECT metadata_json, created_at FROM audit_logs " +
    "WHERE actor_user_id = ? AND action = 'auth.google_sign_in' " +
    "ORDER BY created_at DESC LIMIT 1"
  ).bind(userId).first();
  return row ? toLatestSignInDetails(row.metadata_json, row.created_at) : null;
}

async function createChallenge(request, env) {
  if (!env.GOOGLE_CLIENT_ID) {
    throw new HttpError(503, "google_auth_not_configured", "Google Sign-In is not configured yet.");
  }
  await enforceRateLimit(request, env, "AUTH_CHALLENGE_LIMITER");
  const id = crypto.randomUUID();
  const nonce = randomToken(32);
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare(
    "INSERT INTO auth_challenges (id, nonce_hash, expires_at, created_at) VALUES (?, ?, ?, ?)"
  ).bind(id, await sha256(nonce), now + 300, now).run();
  await env.DB.prepare("DELETE FROM auth_challenges WHERE expires_at < ? OR consumed_at IS NOT NULL").bind(now - 300).run();
  return response(request, { challengeId: id, nonce, expiresInSeconds: 300 }, 200, env);
}

async function googleLogin(request, env) {
  await enforceRateLimit(request, env, "AUTH_LOGIN_LIMITER");
  const body = await readBody(request);
  if (typeof body.challengeId !== "string" || typeof body.idToken !== "string") {
    throw new HttpError(400, "missing_sign_in_fields", "challengeId and idToken are required.");
  }
  const now = Math.floor(Date.now() / 1000);
  const challenge = await env.DB.prepare(
    "SELECT id, nonce_hash, expires_at, consumed_at FROM auth_challenges WHERE id = ?"
  ).bind(body.challengeId).first();
  if (!challenge || challenge.consumed_at || challenge.expires_at <= now) {
    throw new HttpError(401, "invalid_challenge", "Sign-in challenge is invalid or expired.");
  }

  const nonce = typeof body.nonce === "string" ? body.nonce : "";
  if (!nonce || (await sha256(nonce)) !== challenge.nonce_hash) {
    throw new HttpError(401, "invalid_challenge_nonce", "Sign-in challenge nonce is invalid.");
  }
  const identity = await verifyGoogleIdToken(body.idToken, nonce, env);
  const consumed = await env.DB.prepare(
    "UPDATE auth_challenges SET consumed_at = ? WHERE id = ? AND nonce_hash = ? AND consumed_at IS NULL AND expires_at > ?"
  ).bind(now, challenge.id, challenge.nonce_hash, now).run();
  if (consumed.meta?.changes !== 1) {
    throw new HttpError(409, "challenge_already_used", "This sign-in attempt has already been used.");
  }

  let user = await env.DB.prepare(
    "SELECT id, email, display_name, role, status, email_verified FROM users WHERE google_sub = ?"
  ).bind(identity.sub).first();

  if (!user) {
    const otherUser = await env.DB.prepare("SELECT id FROM users WHERE lower(email) = ?").bind(identity.email).first();
    if (otherUser) throw new HttpError(409, "google_identity_conflict", "This email is already linked to a different Google identity.");
    const userId = crypto.randomUUID();
    const isConfiguredOwner = isConfiguredOwnerIdentity(identity, env);
    const existingOwner = isConfiguredOwner
      ? await env.DB.prepare("SELECT id FROM users WHERE role = 'OWNER' LIMIT 1").first()
      : null;
    if (isConfiguredOwner && existingOwner) {
      throw new HttpError(409, "owner_already_provisioned", "The owner account has already been configured.");
    }
    const role = isConfiguredOwner ? "OWNER" : "CUSTOMER";
    await env.DB.prepare(
      "INSERT INTO users (id, google_sub, email, email_verified, display_name, role, status, created_at, updated_at, last_login_at) " +
      "VALUES (?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?, ?)"
    ).bind(userId, identity.sub, identity.email, identity.name, role, now, now, now).run();
    user = { id: userId, email: identity.email, display_name: identity.name, role, status: "ACTIVE", email_verified: 1 };
  } else {
    if (user.status !== "ACTIVE") throw new HttpError(403, "account_inactive", "This account has been disabled.");
    if (isConfiguredOwnerIdentity(identity, env) && user.role !== "OWNER") {
      const existingOwner = await env.DB.prepare("SELECT id FROM users WHERE role = 'OWNER' LIMIT 1").first();
      if (!existingOwner) {
        await env.DB.prepare("UPDATE users SET role = 'OWNER' WHERE id = ?").bind(user.id).run();
        user.role = "OWNER";
      }
    }
    await env.DB.prepare(
      "UPDATE users SET email = ?, email_verified = 1, display_name = ?, updated_at = ?, last_login_at = ? WHERE id = ?"
    ).bind(identity.email, identity.name, now, now, user.id).run();
  }

  const rawSession = randomToken(48);
  const ttl = Math.min(2592000, Math.max(300, Number(env.SESSION_TTL_SECONDS || 604800)));
  const expiresAt = now + ttl;
  const sessionId = crypto.randomUUID();
  const deviceLabel = typeof body.deviceLabel === "string" ? body.deviceLabel.trim().slice(0, 100) : null;
  const deviceInfo = sanitizeClientTechnicalInfo(body.deviceInfo);
  await env.DB.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at, last_seen_at, device_label) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).bind(sessionId, user.id, await sha256(rawSession), now, expiresAt, now, deviceLabel).run();
  const googleProfile = {
    givenName: identity.givenName,
    familyName: identity.familyName,
    pictureUrl: identity.pictureUrl,
    profileUrl: identity.profileUrl,
    locale: identity.locale,
    hostedDomain: identity.hostedDomain
  };
  await audit(env, user.id, "auth.google_sign_in", "session", sessionId, {
    deviceLabel,
    googleProfile,
    deviceInfo
  });
  const latestSignIn = { createdAt: now, deviceLabel, googleProfile, deviceInfo };
  const responseUser = {
    id: user.id,
    email: user.email,
    displayName: user.display_name,
    role: user.role
  };
  Object.assign(responseUser, {
    googleSub: identity.sub,
    emailVerified: identity.emailVerified,
    latestSignIn
  });
  return response(request, {
    accessToken: rawSession,
    tokenType: "Bearer",
    expiresAt,
    user: responseUser
  }, 200, env);
}

function sanitizeClientTechnicalInfo(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const text = (key, maxLength) => cleanDeviceText(value[key], maxLength);
  const integer = (key, maxValue) => {
    const number = Number(value[key]);
    return Number.isSafeInteger(number) && number >= 0 && number <= maxValue ? number : null;
  };
  const allowedNetworkTypes = new Set(["NONE", "VPN", "WIFI", "CELLULAR", "ETHERNET", "OTHER"]);
  const allowedNetworkStates = new Set(["CONNECTED", "OFFLINE"]);
  const networkType = text("networkType", 20);
  const networkStatus = text("networkStatus", 20);

  return {
    manufacturer: text("manufacturer", 80),
    model: text("model", 100),
    brand: text("brand", 80),
    product: text("product", 100),
    deviceCodeName: text("deviceCodeName", 100),
    supportedAbis: text("supportedAbis", 300),
    androidVersion: text("androidVersion", 40),
    sdkInt: integer("sdkInt", 1000),
    appVersionName: text("appVersionName", 40),
    appVersionCode: integer("appVersionCode", 2147483647),
    localeTag: text("localeTag", 40),
    timeZoneId: text("timeZoneId", 80),
    screenWidthPx: integer("screenWidthPx", 20000),
    screenHeightPx: integer("screenHeightPx", 20000),
    densityDpi: integer("densityDpi", 2000),
    networkType: allowedNetworkTypes.has(networkType) ? networkType : null,
    networkStatus: allowedNetworkStates.has(networkStatus) ? networkStatus : null,
    networkValidated: typeof value.networkValidated === "boolean" ? value.networkValidated : null
  };
}

function cleanDeviceText(value, maxLength) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

async function getActiveLicense(env, userId, now = Math.floor(Date.now() / 1000)) {
  return env.DB.prepare(
    "SELECT id, user_id, plan_code, status, starts_at, expires_at, max_devices " +
    "FROM licenses WHERE user_id = ? AND status = 'ACTIVE' AND starts_at <= ? AND expires_at > ? " +
    "ORDER BY created_at DESC LIMIT 1"
  ).bind(userId, now, now).first();
}

async function listAdminCustomers(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "SELECT u.id, u.google_sub, u.email, u.email_verified, u.display_name, u.status, u.created_at, u.last_login_at, " +
    "l.id AS license_id, l.plan_code, l.status AS license_status, l.starts_at, l.expires_at, l.max_devices, " +
    "CASE WHEN l.id IS NULL THEN NULL WHEN l.status <> 'ACTIVE' THEN l.status WHEN l.starts_at > ? THEN 'NOT_STARTED' WHEN l.expires_at <= ? THEN 'EXPIRED' ELSE l.status END AS effective_license_status, " +
    "(SELECT COUNT(*) FROM devices d WHERE d.user_id = u.id AND d.status = 'ACTIVE') AS active_device_count, " +
    "(SELECT metadata_json FROM audit_logs a WHERE a.actor_user_id = u.id AND a.action = 'auth.google_sign_in' ORDER BY a.created_at DESC LIMIT 1) AS latest_sign_in_metadata_json, " +
    "(SELECT created_at FROM audit_logs a WHERE a.actor_user_id = u.id AND a.action = 'auth.google_sign_in' ORDER BY a.created_at DESC LIMIT 1) AS latest_sign_in_at " +
    "FROM users u LEFT JOIN licenses l ON l.id = (SELECT latest.id FROM licenses latest WHERE latest.user_id = u.id ORDER BY latest.created_at DESC, latest.rowid DESC LIMIT 1) " +
    "WHERE u.role = 'CUSTOMER' ORDER BY u.created_at DESC"
  ).bind(now, now).all();
  const customers = (result.results || []).map(row => {
    const {
      latest_sign_in_metadata_json: metadataJson,
      latest_sign_in_at: signInAt,
      email_verified: emailVerified,
      ...customer
    } = row;
    return {
      ...customer,
      email_verified: Number(emailVerified) === 1,
      latestSignIn: toLatestSignInDetails(metadataJson, signInAt)
    };
  });
  return response(request, { customers }, 200, env);
}


async function updateAdminCustomer(request, env, session, customerId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(customerId)) {
    throw new HttpError(400, "invalid_customer_id", "Customer ID is invalid.");
  }
  const body = await readBody(request);
  const status = typeof body.status === "string" ? body.status.trim().toUpperCase() : "";
  if (!["ACTIVE", "SUSPENDED", "REVOKED"].includes(status)) {
    throw new HttpError(400, "invalid_customer_status", "Customer status must be ACTIVE, SUSPENDED or REVOKED.");
  }

  const customer = await env.DB.prepare(
    "SELECT id, role, status FROM users WHERE id = ?"
  ).bind(customerId).first();
  if (!customer || customer.role !== "CUSTOMER") {
    throw new HttpError(404, "customer_not_found", "Customer was not found.");
  }
  if (customer.status === status) {
    return response(request, { updated: true, customerId, status, unchanged: true }, 200, env);
  }

  const now = Math.floor(Date.now() / 1000);
  const statements = [
    env.DB.prepare(
      "UPDATE users SET status = ?, updated_at = ? WHERE id = ? AND role = 'CUSTOMER'"
    ).bind(status, now, customerId),
    env.DB.prepare(
      "UPDATE sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL"
    ).bind(now, customerId)
  ];

  if (status === "REVOKED") {
    statements.push(
      env.DB.prepare(
        "UPDATE licenses SET status = 'REVOKED', updated_at = ? WHERE user_id = ? AND status <> 'REVOKED'"
      ).bind(now, customerId),
      env.DB.prepare(
        "UPDATE devices SET status = 'REVOKED', revoked_at = ?, revoked_reason = 'ACCOUNT_REVOKED' WHERE user_id = ? AND status = 'ACTIVE'"
      ).bind(now, customerId)
    );
  }

  await env.DB.batch(statements);
  await audit(env, session.user_id, status === "REVOKED" ? "customer.revoked" : "customer.status_updated", "customer", customerId, {
    previousStatus: customer.status,
    status,
    sessionsRevoked: true,
    dependentAccessRevoked: status === "REVOKED"
  });
  return response(request, { updated: true, customerId, status }, 200, env);
}

async function listAdminLicenses(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "SELECT l.id, l.user_id, u.email, u.display_name, l.plan_code, l.status, " +
    "CASE WHEN l.status <> 'ACTIVE' THEN l.status WHEN l.starts_at > ? THEN 'NOT_STARTED' WHEN l.expires_at <= ? THEN 'EXPIRED' ELSE l.status END AS effective_status, " +
    "l.starts_at, l.expires_at, l.max_devices, l.created_at, l.updated_at, " +
    "(SELECT COUNT(*) FROM devices d WHERE d.user_id = l.user_id AND d.status = 'ACTIVE') AS active_device_count " +
    "FROM licenses l JOIN users u ON u.id = l.user_id ORDER BY l.created_at DESC"
  ).bind(now, now).all();
  return response(request, { licenses: result.results || [] }, 200, env);
}

async function createCustomerLicense(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request);
  const userId = typeof body.userId === "string" ? body.userId.trim() : "";
  const planCode = typeof body.planCode === "string" ? body.planCode.trim().toUpperCase() : "";
  const now = Math.floor(Date.now() / 1000);
  const startsAt = body.startsAt == null ? now : Number(body.startsAt);
  const expiresAt = Number(body.expiresAt);
  const maxDevices = Number(body.maxDevices ?? 1);

  if (!ID_PATTERN.test(userId)) {
    throw new HttpError(400, "invalid_customer_id", "A valid customer ID is required.");
  }
  if (!/^[A-Z0-9_-]{2,32}$/.test(planCode)) {
    throw new HttpError(400, "invalid_plan_code", "Plan code must contain 2 to 32 letters, numbers, underscores or hyphens.");
  }
  if (!Number.isSafeInteger(startsAt) || !Number.isSafeInteger(expiresAt) ||
      startsAt < now - 300 || expiresAt <= startsAt || expiresAt > now + 10 * 366 * 86400) {
    throw new HttpError(400, "invalid_license_period", "The license start and expiry times are invalid.");
  }
  if (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 20) {
    throw new HttpError(400, "invalid_device_limit", "The device limit must be from 1 to 20.");
  }

  const customer = await env.DB.prepare(
    "SELECT id, email, role, status FROM users WHERE id = ?"
  ).bind(userId).first();
  if (!customer || customer.role !== "CUSTOMER") {
    throw new HttpError(404, "customer_not_found", "The customer account was not found.");
  }
  if (customer.status !== "ACTIVE") {
    throw new HttpError(409, "customer_inactive", "A license cannot be issued to an inactive account.");
  }
  const enrolled = await env.DB.prepare(
    "SELECT COUNT(*) AS active_count FROM devices WHERE user_id = ? AND status = 'ACTIVE'"
  ).bind(userId).first();
  if (Number(enrolled?.active_count || 0) > maxDevices) {
    throw new HttpError(409, "device_limit_below_existing", "The new license device limit is lower than the customer's current active device count. Revoke extra devices or increase the limit.");
  }

  await env.DB.prepare(
    "UPDATE licenses SET status = 'PAUSED', updated_at = ? " +
    "WHERE user_id = ? AND status = 'ACTIVE' AND expires_at <= ?"
  ).bind(now, userId, now).run();

  const id = crypto.randomUUID();
  try {
    await env.DB.prepare(
      "INSERT INTO licenses (id, user_id, plan_code, status, starts_at, expires_at, max_devices, created_by, created_at, updated_at) " +
      "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)"
    ).bind(id, userId, planCode, startsAt, expiresAt, maxDevices, session.user_id, now, now).run();
  } catch (error) {
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "active_license_exists", "This customer already has an active license. Update or revoke it before issuing another.");
    }
    throw error;
  }

  await audit(env, session.user_id, "license.created", "license", id, {
    customerId: userId, planCode, startsAt, expiresAt, maxDevices
  });
  return response(request, {
    created: true,
    license: { id, user_id: userId, email: customer.email, plan_code: planCode, status: "ACTIVE", starts_at: startsAt, expires_at: expiresAt, max_devices: maxDevices }
  }, 201, env);
}

async function updateCustomerLicense(request, env, session, licenseId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(licenseId)) throw new HttpError(400, "invalid_license_id", "License ID is invalid.");
  const body = await readBody(request);
  const current = await env.DB.prepare(
    "SELECT id, user_id, plan_code, status, starts_at, expires_at, max_devices FROM licenses WHERE id = ?"
  ).bind(licenseId).first();
  if (!current) throw new HttpError(404, "license_not_found", "License was not found.");

  const status = body.status == null ? current.status : String(body.status).toUpperCase();
  const planCode = body.planCode == null ? current.plan_code : String(body.planCode).trim().toUpperCase();
  const startsAt = body.startsAt == null ? current.starts_at : Number(body.startsAt);
  const expiresAt = body.expiresAt == null ? current.expires_at : Number(body.expiresAt);
  const maxDevices = body.maxDevices == null ? current.max_devices : Number(body.maxDevices);
  const now = Math.floor(Date.now() / 1000);

  if (!["ACTIVE", "PAUSED", "REVOKED"].includes(status)) {
    throw new HttpError(400, "invalid_license_status", "License status must be ACTIVE, PAUSED or REVOKED.");
  }
  if (!/^[A-Z0-9_-]{2,32}$/.test(planCode)) {
    throw new HttpError(400, "invalid_plan_code", "Plan code is invalid.");
  }
  if (!Number.isSafeInteger(startsAt) || !Number.isSafeInteger(expiresAt) ||
      startsAt < 0 || expiresAt <= startsAt || expiresAt > now + 10 * 366 * 86400) {
    throw new HttpError(400, "invalid_license_period", "The license start and expiry times are invalid.");
  }
  if (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 20) {
    throw new HttpError(400, "invalid_device_limit", "The device limit must be from 1 to 20.");
  }
  if (status === "ACTIVE" && (startsAt > now || expiresAt <= now)) {
    throw new HttpError(400, "license_not_current", "An active license must have a start time in the past and an expiry time in the future.");
  }
  if (status === "ACTIVE") {
    const enrolled = await env.DB.prepare(
      "SELECT COUNT(*) AS active_count FROM devices WHERE user_id = ? AND status = 'ACTIVE'"
    ).bind(current.user_id).first();
    if (Number(enrolled?.active_count || 0) > maxDevices) {
      throw new HttpError(409, "device_limit_below_existing", "The new license device limit is lower than the customer's current active device count. Revoke extra devices or increase the limit.");
    }
  }

  try {
    const result = await env.DB.prepare(
      "UPDATE licenses SET plan_code = ?, status = ?, starts_at = ?, expires_at = ?, max_devices = ?, updated_at = ? WHERE id = ?"
    ).bind(planCode, status, startsAt, expiresAt, maxDevices, now, licenseId).run();
    if (result.meta?.changes !== 1) throw new HttpError(409, "license_update_conflict", "License was changed before this update completed.");
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "active_license_exists", "The customer already has another active license.");
    }
    throw error;
  }

  // License entitlement is independent from device enrollment. Keep device
  // registrations intact so a newly issued license can restore access without
  // forcing the customer to replace an already-trusted Android Keystore key.
  await audit(env, session.user_id, "license.updated", "license", licenseId, {
    status, planCode, startsAt, expiresAt, maxDevices
  });
  return response(request, { updated: true, licenseId, status }, 200, env);
}

async function listAdminDevices(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "SELECT d.id, d.user_id, u.email, u.display_name, u.status AS customer_status, " +
    "d.label, d.manufacturer, d.model, d.android_version, d.app_version, d.status, d.revoked_reason, " +
    "d.registered_at, d.last_seen_at, d.revoked_at, l.status AS raw_license_status, l.starts_at AS license_starts_at, " +
    "l.expires_at AS license_expires_at, l.max_devices, " +
    "CASE WHEN l.id IS NULL THEN NULL WHEN l.status <> 'ACTIVE' THEN l.status WHEN l.starts_at > ? THEN 'NOT_STARTED' WHEN l.expires_at <= ? THEN 'EXPIRED' ELSE l.status END AS license_status, " +
    "(SELECT COUNT(*) FROM devices active WHERE active.user_id = d.user_id AND active.status = 'ACTIVE') AS active_device_count " +
    "FROM devices d JOIN users u ON u.id = d.user_id " +
    "LEFT JOIN licenses l ON l.id = (SELECT latest.id FROM licenses latest WHERE latest.user_id = u.id ORDER BY latest.created_at DESC, latest.rowid DESC LIMIT 1) " +
    "ORDER BY d.last_seen_at DESC"
  ).bind(now, now).all();
  return response(request, { devices: result.results || [] }, 200, env);
}

async function updateAdminDeviceStatus(request, env, session, deviceId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(deviceId)) throw new HttpError(400, "invalid_device_id", "Device ID is invalid.");
  const body = await readBody(request);
  const status = typeof body.status === "string" ? body.status.trim().toUpperCase() : "";
  if (!["ACTIVE", "REVOKED"].includes(status)) {
    throw new HttpError(400, "invalid_device_status", "Device status must be ACTIVE or REVOKED.");
  }
  const device = await env.DB.prepare(
    "SELECT id, user_id, status, revoked_reason FROM devices WHERE id = ?"
  ).bind(deviceId).first();
  if (!device) throw new HttpError(404, "device_not_found", "Device was not found.");
  if (device.status === status) {
    return response(request, { updated: true, deviceId, status, unchanged: true }, 200, env);
  }

  const now = Math.floor(Date.now() / 1000);
  if (status === "ACTIVE") {
    const customer = await env.DB.prepare(
      "SELECT status FROM users WHERE id = ? AND role = 'CUSTOMER'"
    ).bind(device.user_id).first();
    if (!customer || customer.status !== "ACTIVE") {
      throw new HttpError(409, "customer_inactive", "Activate the customer account before restoring its device.");
    }
    const license = await getActiveLicense(env, device.user_id, now);
    if (!license) {
      throw new HttpError(409, "active_license_required", "Issue or activate a current license before restoring this device.");
    }
    const restored = await env.DB.prepare(
      "UPDATE devices SET status = 'ACTIVE', revoked_at = NULL, revoked_reason = NULL, last_seen_at = ? " +
      "WHERE id = ? AND user_id = ? AND status = 'REVOKED' " +
      "AND (SELECT COUNT(*) FROM devices active WHERE active.user_id = ? AND active.status = 'ACTIVE') < ?"
    ).bind(now, deviceId, device.user_id, device.user_id, license.max_devices).run();
    if (restored.meta?.changes !== 1) {
      throw new HttpError(409, "device_limit_reached", "The license has no free device slot. Revoke another active device or increase the device limit first.");
    }
    await audit(env, session.user_id, "device.reactivated_by_owner", "device", deviceId, {
      customerId: device.user_id,
      previousRevocationReason: device.revoked_reason
    });
    return response(request, { updated: true, deviceId, status, reactivated: true }, 200, env);
  }

  await env.DB.prepare(
    "UPDATE devices SET status = 'REVOKED', revoked_at = ?, revoked_reason = 'OWNER_REVOKED' " +
    "WHERE id = ? AND status = 'ACTIVE'"
  ).bind(now, deviceId).run();
  await audit(env, session.user_id, "device.revoked_by_owner", "device", deviceId, { customerId: device.user_id });
  return response(request, { updated: true, deviceId, status }, 200, env);
}

async function getCustomerAccount(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const now = Math.floor(Date.now() / 1000);
  const license = await env.DB.prepare(
    "SELECT id, plan_code, status, starts_at, expires_at, max_devices FROM licenses " +
    "WHERE user_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1"
  ).bind(session.user_id).first();
  const result = await env.DB.prepare(
    "SELECT id, status, revoked_reason " +
    "FROM devices WHERE user_id = ? ORDER BY registered_at DESC"
  ).bind(session.user_id).all();

  let licenseState = null;
  if (license) {
    licenseState = license.status;
    if (license.status === "ACTIVE" && license.starts_at > now) licenseState = "NOT_STARTED";
    else if (license.status === "ACTIVE" && license.expires_at <= now) licenseState = "EXPIRED";
  }
  const activeDeviceCount = (result.results || []).filter(device => device.status === "ACTIVE").length;
  return response(request, {
    account: {
      id: session.user_id,
      email: session.email,
      displayName: session.display_name
    },
    license: license ? { ...license, effective_status: licenseState } : null,
    devices: result.results || [],
    activeDeviceCount,
    canRegisterDevice: licenseState === "ACTIVE" && activeDeviceCount < (license?.max_devices || 0),
    canRun: licenseState === "ACTIVE" && activeDeviceCount > 0
  }, 200, env);
}

async function createCustomerDeviceChallenge(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const now = Math.floor(Date.now() / 1000);
  const license = await getActiveLicense(env, session.user_id, now);
  if (!license) {
    throw new HttpError(403, "active_license_required", "An active license is required to register this device.");
  }
  const id = crypto.randomUUID();
  const nonce = randomToken(32);
  await env.DB.prepare(
    "INSERT INTO device_enrollment_challenges (id, user_id, nonce_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)"
  ).bind(id, session.user_id, await sha256(nonce), now + 300, now).run();
  await env.DB.prepare(
    "DELETE FROM device_enrollment_challenges WHERE expires_at <= ? OR consumed_at IS NOT NULL"
  ).bind(now - 300).run();
  return response(request, { challengeId: id, nonce, expiresInSeconds: 300 }, 200, env);
}

async function registerCustomerDevice(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const body = await readBody(request, 12_000);
  const publicKeySpki = typeof body.publicKeySpki === "string" ? body.publicKeySpki.trim() : "";
  const challengeId = typeof body.challengeId === "string" ? body.challengeId.trim() : "";
  const nonce = typeof body.nonce === "string" ? body.nonce : "";
  const signatureText = typeof body.signature === "string" ? body.signature.trim() : "";
  if (publicKeySpki.length < 300 || publicKeySpki.length > 8192 ||
      !/^[A-Za-z0-9+/=_-]+$/.test(publicKeySpki)) {
    throw new HttpError(400, "invalid_device_public_key", "A valid Android Keystore public key is required.");
  }
  if (!ID_PATTERN.test(challengeId) || nonce.length < 32 || nonce.length > 128 ||
      signatureText.length < 300 || signatureText.length > 600 ||
      !/^[A-Za-z0-9_+\\/=-]+$/.test(signatureText)) {
    throw new HttpError(400, "invalid_device_proof", "A valid one-time device enrollment proof is required.");
  }

  const now = Math.floor(Date.now() / 1000);
  const challenge = await env.DB.prepare(
    "SELECT id, nonce_hash, expires_at, consumed_at FROM device_enrollment_challenges " +
    "WHERE id = ? AND user_id = ?"
  ).bind(challengeId, session.user_id).first();
  if (!challenge || challenge.consumed_at || challenge.expires_at <= now ||
      (await sha256(nonce)) !== challenge.nonce_hash) {
    throw new HttpError(401, "invalid_device_challenge", "Device enrollment challenge is invalid, expired or already used.");
  }

  const license = await getActiveLicense(env, session.user_id, now);
  if (!license) throw new HttpError(403, "active_license_required", "An active license is required to register this device.");

  let publicKeyBytes;
  let publicKey;
  let signature;
  try {
    publicKeyBytes = base64UrlDecode(publicKeySpki);
    publicKey = await crypto.subtle.importKey(
      "spki",
      publicKeyBytes,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    signature = base64UrlDecode(signatureText);
  } catch {
    throw new HttpError(400, "invalid_device_public_key", "The device key or signature format is invalid.");
  }
  const modulusLength = publicKey.algorithm?.modulusLength;
  if (!Number.isInteger(modulusLength) || modulusLength < 3072 || modulusLength > 4096) {
    throw new HttpError(400, "weak_device_public_key", "The device key must use RSA 3072 to 4096 bits.");
  }
  const proofMessage = encoder.encode(challengeId + "." + nonce);
  const proofValid = await crypto.subtle.verify(
    { name: "RSASSA-PKCS1-v1_5" },
    publicKey,
    signature,
    proofMessage
  );
  if (!proofValid) throw new HttpError(401, "invalid_device_signature", "The device did not prove ownership of its private key.");

  const consumed = await env.DB.prepare(
    "UPDATE device_enrollment_challenges SET consumed_at = ? " +
    "WHERE id = ? AND user_id = ? AND nonce_hash = ? AND consumed_at IS NULL AND expires_at > ?"
  ).bind(now, challengeId, session.user_id, challenge.nonce_hash, now).run();
  if (consumed.meta?.changes !== 1) {
    throw new HttpError(409, "device_challenge_already_used", "This device enrollment challenge has already been used.");
  }

  const publicKeyCanonical = base64UrlEncode(publicKeyBytes);
  const digest = await crypto.subtle.digest("SHA-256", publicKeyBytes);
  const fingerprint = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  const existing = await env.DB.prepare(
    "SELECT id, status, revoked_reason FROM devices WHERE user_id = ? AND public_key_fingerprint = ?"
  ).bind(session.user_id, fingerprint).first();

  const metadata = {
    label: cleanDeviceText(body.label, 100),
    manufacturer: cleanDeviceText(body.manufacturer, 80),
    model: cleanDeviceText(body.model, 100),
    androidVersion: cleanDeviceText(body.androidVersion, 40),
    appVersion: cleanDeviceText(body.appVersion, 40)
  };

  // A customer may re-enroll a device they removed, or a device that was
  // disabled only because the whole account was revoked. A fresh one-time
  // challenge signed by the device's private key is still required, and the
  // account must now have a current license with a free device slot.
  const customerReenrollmentReasons = new Set(["CUSTOMER_REMOVED", "ACCOUNT_REVOKED"]);
  if (existing?.status === "REVOKED" && !customerReenrollmentReasons.has(existing.revoked_reason)) {
    throw new HttpError(403, "device_revoked", "This device was explicitly revoked by the application owner. Ask the owner to reactivate it.");
  }

  if (existing?.status === "REVOKED") {
    const restored = await env.DB.prepare(
      "UPDATE devices SET status = 'ACTIVE', revoked_at = NULL, revoked_reason = NULL, " +
      "label = ?, manufacturer = ?, model = ?, android_version = ?, app_version = ?, last_seen_at = ? " +
      "WHERE id = ? AND user_id = ? AND status = 'REVOKED' " +
      "AND revoked_reason IN ('CUSTOMER_REMOVED', 'ACCOUNT_REVOKED') " +
      "AND (SELECT COUNT(*) FROM devices WHERE user_id = ? AND status = 'ACTIVE') < ?"
    ).bind(
      metadata.label, metadata.manufacturer, metadata.model, metadata.androidVersion, metadata.appVersion,
      now, existing.id, session.user_id, session.user_id, license.max_devices
    ).run();
    if (restored.meta?.changes !== 1) {
      throw new HttpError(409, "device_limit_reached", "The license has reached its active device limit or the device state changed. Refresh and try again.");
    }
    await audit(env, session.user_id, "device.re_registered", "device", existing.id, {
      fingerprint,
      previousRevocationReason: existing.revoked_reason
    });
    return response(request, {
      registered: true,
      alreadyRegistered: true,
      reactivated: true,
      device: { id: existing.id, ...metadata, status: "ACTIVE", lastSeenAt: now }
    }, 200, env);
  }

  if (existing) {
    await env.DB.prepare(
      "UPDATE devices SET label = ?, manufacturer = ?, model = ?, android_version = ?, app_version = ?, last_seen_at = ? " +
      "WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"
    ).bind(metadata.label, metadata.manufacturer, metadata.model, metadata.androidVersion, metadata.appVersion, now, existing.id, session.user_id).run();
    return response(request, {
      registered: true,
      alreadyRegistered: true,
      device: { id: existing.id, ...metadata, status: "ACTIVE", lastSeenAt: now }
    }, 200, env);
  }

  const id = crypto.randomUUID();
  try {
    const inserted = await env.DB.prepare(
      "INSERT INTO devices (id, user_id, public_key_spki, public_key_fingerprint, label, manufacturer, model, android_version, app_version, status, registered_at, last_seen_at) " +
      "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ? " +
      "WHERE (SELECT COUNT(*) FROM devices WHERE user_id = ? AND status = 'ACTIVE') < ?"
    ).bind(
      id, session.user_id, publicKeyCanonical, fingerprint,
      metadata.label, metadata.manufacturer, metadata.model, metadata.androidVersion, metadata.appVersion, now, now,
      session.user_id, license.max_devices
    ).run();
    if (inserted.meta?.changes !== 1) {
      throw new HttpError(409, "device_limit_reached", "The license has reached its active device limit.");
    }
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "device_key_already_registered", "This device key is already registered to another account.");
    }
    throw error;
  }
  await audit(env, session.user_id, "device.registered", "device", id, { fingerprint });
  return response(request, {
    registered: true,
    alreadyRegistered: false,
    device: { id, ...metadata, status: "ACTIVE", registeredAt: now, lastSeenAt: now }
  }, 201, env);
}

async function removeOwnDevice(request, env, session, deviceId) {
  await requireRole(session, ["CUSTOMER"]);
  if (!ID_PATTERN.test(deviceId)) throw new HttpError(400, "invalid_device_id", "Device ID is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "UPDATE devices SET status = 'REVOKED', revoked_at = ?, revoked_reason = 'CUSTOMER_REMOVED' " +
    "WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"
  ).bind(now, deviceId, session.user_id).run();
  if (result.meta?.changes !== 1) throw new HttpError(404, "device_not_found", "Active device was not found on this account.");
  await audit(env, session.user_id, "device.removed_by_customer", "device", deviceId);
  return response(request, { removed: true, deviceId }, 200, env);
}

async function packageMasterKey(env, keyVersion = "V1") {
  if (!/^V[1-9][0-9]*$/.test(keyVersion)) {
    throw new HttpError(503, "package_encryption_key_version_invalid", "The package encryption key version is invalid.");
  }
  const encoded = env[`PACKAGE_MASTER_KEY_${keyVersion}`];
  if (typeof encoded !== "string" || !encoded.trim()) {
    throw new HttpError(503, "package_encryption_not_configured", "Encrypted package delivery is not configured.");
  }
  let raw;
  try {
    raw = base64UrlDecode(encoded.trim());
  } catch {
    throw new HttpError(503, "package_encryption_key_invalid", "The package encryption key is invalid.");
  }
  if (raw.length !== 32) {
    throw new HttpError(503, "package_encryption_key_invalid", "The package encryption key must be exactly 32 bytes.");
  }
  try {
    return await crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
  } finally {
    raw.fill(0);
  }
}

function randomBytes(size) {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return bytes;
}

function packageContext(packageId, version) {
  return `AUTU-LIO|PACKAGE|${packageId}|v${version}`;
}

async function encryptPackageSource(env, packageId, version, source) {
  const keyVersion = String(env.PACKAGE_MASTER_KEY_VERSION || "V1").trim().toUpperCase();
  const masterKey = await packageMasterKey(env, keyVersion);
  const dataKeyBytes = randomBytes(32);
  const contentIv = randomBytes(12);
  const keyIv = randomBytes(12);
  const context = packageContext(packageId, version);
  const sourceBytes = encoder.encode(source);
  let compressedBytes;
  try {
    compressedBytes = new Uint8Array(gzipSync(sourceBytes, { level: 9 }));
    const useCompression = compressedBytes.length < sourceBytes.length;
    const payloadBytes = useCompression ? compressedBytes : sourceBytes;
    const contentKey = await crypto.subtle.importKey(
      "raw",
      dataKeyBytes,
      { name: "AES-GCM" },
      false,
      ["encrypt"]
    );
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: contentIv, additionalData: encoder.encode(context), tagLength: 128 },
      contentKey,
      payloadBytes
    );
    const encryptedDataKey = await crypto.subtle.encrypt(
      {
        name: "AES-GCM",
        iv: keyIv,
        additionalData: encoder.encode(context + "|DATA_KEY"),
        tagLength: 128
      },
      masterKey,
      dataKeyBytes
    );
    return {
      ciphertext: (useCompression ? "GZ1." : "") + base64UrlEncode(new Uint8Array(ciphertext)),
      contentIv: base64UrlEncode(contentIv),
      encryptedDataKey: base64UrlEncode(new Uint8Array(encryptedDataKey)),
      keyIv: base64UrlEncode(new Uint8Array(keyIv)),
      sourceSha256: await sha256Bytes(sourceBytes),
      keyVersion
    };
  } finally {
    sourceBytes.fill(0);
    compressedBytes?.fill(0);
    dataKeyBytes.fill(0);
  }
}


async function wrapPackageKeyForDevice(
  env, packageId, version, encryptedDataKey, keyIv, keyVersion, publicKeySpki,
  hashAlgorithm = "SHA-1"
) {
  if (!["SHA-1", "SHA-256"].includes(hashAlgorithm)) {
    throw new HttpError(400, "invalid_key_wrapping", "The requested device key wrapping algorithm is not supported.");
  }
  const masterKey = await packageMasterKey(env, keyVersion);
  const context = packageContext(packageId, version);
  let dataKeyBytes;
  try {
    dataKeyBytes = new Uint8Array(await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(keyIv), additionalData: encoder.encode(context + "|DATA_KEY"), tagLength: 128 },
      masterKey,
      base64UrlDecode(encryptedDataKey)
    ));
  } catch {
    throw new HttpError(503, "package_key_wrap_failed", "The package key could not be prepared for this device.");
  }
  try {
    const publicKey = await crypto.subtle.importKey(
      "spki", base64UrlDecode(publicKeySpki), { name: "RSA-OAEP", hash: hashAlgorithm }, false, ["encrypt"]
    );
    if (dataKeyBytes.length !== 32 || !Number.isInteger(publicKey.algorithm?.modulusLength) ||
        publicKey.algorithm.modulusLength < 3072) {
      throw new HttpError(403, "device_key_not_supported", "This registered device does not have a supported RSA-3072 package key.");
    }
    const wrappedKey = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, publicKey, dataKeyBytes);
    return base64UrlEncode(new Uint8Array(wrappedKey));
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(503, "package_key_wrap_failed", "The package key could not be wrapped for this device.");
  } finally {
    dataKeyBytes.fill(0);
  }
}

async function createLegacyPackagePayload(env, item) {
  if (!String(item.ciphertext).startsWith("GZ1.")) {
    return { ciphertext: item.ciphertext, contentIv: item.content_iv };
  }
  const context = packageContext(item.id, item.version);
  const masterKey = await packageMasterKey(env, item.key_version);
  let dataKeyBytes;
  let compressedBytes;
  let sourceBytes;
  let legacyCiphertextBytes;
  const legacyIv = crypto.getRandomValues(new Uint8Array(12));
  try {
    dataKeyBytes = new Uint8Array(await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(item.key_iv), additionalData: encoder.encode(context + "|DATA_KEY"), tagLength: 128 },
      masterKey,
      base64UrlDecode(item.encrypted_data_key)
    ));
    const contentKey = await crypto.subtle.importKey("raw", dataKeyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
    compressedBytes = new Uint8Array(await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(item.content_iv), additionalData: encoder.encode(context), tagLength: 128 },
      contentKey,
      base64UrlDecode(String(item.ciphertext).slice(4))
    ));
    sourceBytes = new Uint8Array(gunzipSync(compressedBytes, { maxOutputLength: 262144 }));
    const digest = await sha256Bytes(sourceBytes);
    if (digest.toLowerCase() !== String(item.source_sha256).toLowerCase()) {
      throw new HttpError(500, "package_integrity_failed", "The AUTU source hash does not match.");
    }
    const legacyKey = await crypto.subtle.importKey("raw", dataKeyBytes, { name: "AES-GCM" }, false, ["encrypt"]);
    legacyCiphertextBytes = new Uint8Array(await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: legacyIv, additionalData: encoder.encode(context), tagLength: 128 },
      legacyKey,
      sourceBytes
    ));
    return { ciphertext: base64UrlEncode(legacyCiphertextBytes), contentIv: base64UrlEncode(legacyIv) };
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(500, "package_compatibility_conversion_failed", "The legacy AUTU package could not be prepared.");
  } finally {
    dataKeyBytes?.fill(0);
    compressedBytes?.fill(0);
    sourceBytes?.fill(0);
    legacyCiphertextBytes?.fill(0);
    legacyIv.fill(0);
  }
}


function validatePackageMetadata(body) {
  const name = typeof body.name === "string" ? body.name.trim() : "";
  const description = typeof body.description === "string" ? body.description.trim() : "";
  const source = typeof body.source === "string" ? body.source : "";
  const languageVersion = typeof body.languageVersion === "string" ? body.languageVersion.trim() : "";
  if (!name || name.length > 100) throw new HttpError(400, "invalid_package_name", "Package name must contain 1 to 100 characters.");
  if (description.length > 1_000) throw new HttpError(400, "invalid_package_description", "Package description cannot exceed 1,000 characters.");
  if (!source.trim() || source.length > 32_768) throw new HttpError(400, "invalid_package_source", "Package source must contain 1 to 32,768 characters.");
  // Keep legacy packages publishable while accepting the exact Android compiler
  // versions currently used by AUTU-LIO.
  if (!["0.2", "0.3", "1.19", "1.20", "1.21", "1.22"].includes(languageVersion)) {
    throw new HttpError(
      400,
      "unsupported_language_version",
      "Supported AUTU Script versions are 0.2, 0.3, 1.19, 1.20, 1.21 and 1.22."
    );
  }
  return { name, description, source, languageVersion };
}

async function listAdminAuditLogs(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const url = new URL(request.url);
  const requestedLimit = url.searchParams.has("limit") ? Number(url.searchParams.get("limit")) : 50;
  if (!Number.isInteger(requestedLimit) || requestedLimit < 1 || requestedLimit > 100) {
    throw new HttpError(400, "invalid_audit_limit", "Audit log limit must be an integer from 1 to 100.");
  }
  const result = await env.DB.prepare(
    "SELECT l.id, l.action, l.target_type, l.target_id, l.metadata_json, l.created_at, " +
    "u.email AS actor_email, u.display_name AS actor_display_name " +
    "FROM audit_logs l LEFT JOIN users u ON u.id = l.actor_user_id " +
    "ORDER BY l.created_at DESC, l.id DESC LIMIT ?"
  ).bind(requestedLimit).all();

  const logs = (result.results || []).map(row => {
    let metadata = {};
    try {
      const parsed = JSON.parse(row.metadata_json || "{}");
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) metadata = parsed;
    } catch {
      metadata = {};
    }
    return {
      id: String(row.id || ""),
      action: String(row.action || ""),
      targetType: row.target_type || null,
      targetId: row.target_id || null,
      actorEmail: row.actor_email || "",
      actorDisplayName: row.actor_display_name || null,
      metadata,
      createdAt: Number(row.created_at) || 0
    };
  }).filter(item => item.id && item.action);

  return response(request, { logs, limit: requestedLimit }, 200, env);
}

async function listAdminPackages(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const result = await env.DB.prepare(
    "SELECT p.id, p.name, p.description, p.status, p.latest_version, p.created_at, p.updated_at, " +
    "v.language_version, v.created_at AS published_at " +
    "FROM script_packages p LEFT JOIN script_package_versions v " +
    "ON v.package_id = p.id AND v.version = p.latest_version " +
    "ORDER BY p.updated_at DESC"
  ).all();
  return response(request, { packages: result.results || [] }, 200, env);
}

async function createAdminPackage(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request, 128_000);
  const metadata = validatePackageMetadata(body);
  const id = crypto.randomUUID();
  const version = 1;
  const now = Math.floor(Date.now() / 1000);
  const encrypted = await encryptPackageSource(env, id, version, metadata.source);
  try {
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO script_packages (id, name, description, status, latest_version, created_by, created_at, updated_at) " +
        "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)"
      ).bind(id, metadata.name, metadata.description, version, session.user_id, now, now),
      env.DB.prepare(
        "INSERT INTO script_package_versions " +
        "(id, package_id, version, language_version, ciphertext, content_iv, encrypted_data_key, key_iv, key_version, source_sha256, created_by, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      ).bind(
        crypto.randomUUID(), id, version, metadata.languageVersion,
        encrypted.ciphertext, encrypted.contentIv, encrypted.encryptedDataKey,
        encrypted.keyIv, encrypted.keyVersion, encrypted.sourceSha256, session.user_id, now
      )
    ]);
  } catch (error) {
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "package_name_exists", "A package with this name already exists.");
    }
    throw error;
  }
  await audit(env, session.user_id, "package.published", "script_package", id, {
    version, languageVersion: metadata.languageVersion, sourceSha256: encrypted.sourceSha256
  });
  return response(request, {
    published: true,
    package: { id, name: metadata.name, description: metadata.description, status: "ACTIVE", latestVersion: version, languageVersion: metadata.languageVersion, createdAt: now }
  }, 201, env);
}

async function createAdminPackageVersion(request, env, session, packageId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(packageId)) throw new HttpError(400, "invalid_package_id", "Package ID is invalid.");
  const body = await readBody(request, 128_000);
  const metadata = validatePackageMetadata(body);
  const current = await env.DB.prepare(
    "SELECT id, name, description, status, latest_version FROM script_packages WHERE id = ?"
  ).bind(packageId).first();
  if (!current) throw new HttpError(404, "package_not_found", "Package was not found.");
  if (current.status !== "ACTIVE") throw new HttpError(409, "package_archived", "An archived package cannot receive a new version.");
  if (metadata.name.toLocaleLowerCase() !== current.name.toLocaleLowerCase()) {
    throw new HttpError(400, "package_name_immutable", "A package name cannot be changed when publishing a new version.");
  }
  const version = Number(current.latest_version) + 1;
  if (!Number.isSafeInteger(version) || version < 2) throw new HttpError(409, "invalid_package_version", "The next package version is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const encrypted = await encryptPackageSource(env, packageId, version, metadata.source);
  const batchResults = await env.DB.batch([
    env.DB.prepare(
      "INSERT INTO script_package_versions " +
      "(id, package_id, version, language_version, ciphertext, content_iv, encrypted_data_key, key_iv, key_version, source_sha256, created_by, created_at) " +
      "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? " +
      "WHERE EXISTS (SELECT 1 FROM script_packages WHERE id = ? AND latest_version = ? AND status = 'ACTIVE')"
    ).bind(
      crypto.randomUUID(), packageId, version, metadata.languageVersion,
      encrypted.ciphertext, encrypted.contentIv, encrypted.encryptedDataKey,
      encrypted.keyIv, encrypted.keyVersion, encrypted.sourceSha256, session.user_id, now,
      packageId, current.latest_version
    ),
    env.DB.prepare(
      "UPDATE script_packages SET description = ?, latest_version = ?, updated_at = ? WHERE id = ? AND latest_version = ? AND status = 'ACTIVE'"
    ).bind(metadata.description, version, now, packageId, current.latest_version)
  ]);
  if (batchResults?.length !== 2 || batchResults.some(result => result.meta?.changes !== 1)) {
    throw new HttpError(409, "package_version_conflict", "The package changed while this version was being published. Refresh and try again.");
  }
  await audit(env, session.user_id, "package.version_published", "script_package", packageId, {
    version, languageVersion: metadata.languageVersion, sourceSha256: encrypted.sourceSha256
  });
  return response(request, {
    published: true,
    package: { id: packageId, name: current.name, description: metadata.description, status: "ACTIVE", latestVersion: version, languageVersion: metadata.languageVersion, updatedAt: now }
  }, 201, env);
}

async function decryptPackageSourceForOwner(request, env, session, packageId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(packageId)) throw new HttpError(400, "invalid_package_id", "Package ID is invalid.");
  const packageInfo = await env.DB.prepare(
    "SELECT id, name, description, status, latest_version FROM script_packages WHERE id = ?"
  ).bind(packageId).first();
  if (!packageInfo) throw new HttpError(404, "package_not_found", "Package was not found.");
  const url = new URL(request.url);
  const requestedVersion = url.searchParams.has("version") ? Number(url.searchParams.get("version")) : packageInfo.latest_version;
  if (!Number.isSafeInteger(requestedVersion) || requestedVersion < 1 || requestedVersion > packageInfo.latest_version) {
    throw new HttpError(400, "invalid_package_version", "The requested package version is invalid.");
  }
  const item = await env.DB.prepare(
    "SELECT version, language_version, ciphertext, content_iv, encrypted_data_key, key_iv, key_version, source_sha256 " +
    "FROM script_package_versions WHERE package_id = ? AND version = ?"
  ).bind(packageId, requestedVersion).first();
  if (!item) throw new HttpError(404, "package_version_not_found", "The requested AUTU package version was not found.");
  const context = packageContext(packageInfo.id, item.version);
  const masterKey = await packageMasterKey(env, item.key_version);
  let dataKeyBytes;
  let encryptedPayloadBytes;
  let sourceBytes;
  try {
    dataKeyBytes = new Uint8Array(await crypto.subtle.decrypt(
      {
        name: "AES-GCM",
        iv: base64UrlDecode(item.key_iv),
        additionalData: encoder.encode(context + "|DATA_KEY"),
        tagLength: 128
      },
      masterKey,
      base64UrlDecode(item.encrypted_data_key)
    ));
    if (dataKeyBytes.length !== 32) throw new HttpError(500, "package_key_invalid", "The package data key is invalid.");
    const compressed = String(item.ciphertext).startsWith("GZ1.");
    const encodedCiphertext = compressed ? String(item.ciphertext).slice(4) : String(item.ciphertext);
    const contentKey = await crypto.subtle.importKey("raw", dataKeyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
    encryptedPayloadBytes = new Uint8Array(await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(item.content_iv), additionalData: encoder.encode(context), tagLength: 128 },
      contentKey,
      base64UrlDecode(encodedCiphertext)
    ));
    sourceBytes = compressed
      ? new Uint8Array(gunzipSync(encryptedPayloadBytes, { maxOutputLength: 262144 }))
      : encryptedPayloadBytes;
    const digest = await sha256Bytes(sourceBytes);
    if (digest.toLowerCase() !== String(item.source_sha256).toLowerCase()) {
      throw new HttpError(500, "package_integrity_failed", "The AUTU source hash does not match.");
    }
    const sourceCode = new TextDecoder("utf-8", { fatal: true }).decode(sourceBytes);
    await audit(env, session.user_id, "package.source_viewed", "script_package", packageId, { version: requestedVersion, sourceSha256: item.source_sha256 });
    return response(request, {
      package: {
        id: packageInfo.id,
        name: packageInfo.name,
        version: requestedVersion,
        languageVersion: item.language_version,
        source: sourceCode,
        encrypted: {
          algorithm: "AES-256-GCM",
          keyWrapping: "RSA-3072-OAEP-SHA256 (wrapped per registered device at download)",
          compression: compressed ? "GZIP" : "NONE",
          ciphertext: item.ciphertext,
          contentIv: item.content_iv,
          encryptedDataKey: item.encrypted_data_key,
          keyIv: item.key_iv,
          keyVersion: item.key_version,
          sourceSha256: item.source_sha256
        }
      }
    }, 200, env);
  } catch (error) {
    if (error instanceof HttpError) throw error;
    throw new HttpError(500, "package_decryption_failed", "The owner preview could not decrypt or verify this AUTU package.");
  } finally {
    dataKeyBytes?.fill(0);
    encryptedPayloadBytes?.fill(0);
    if (sourceBytes !== encryptedPayloadBytes) sourceBytes?.fill(0);
  }
}

async function updateAdminPackage(request, env, session, packageId) {
  await requireRole(session, ["OWNER"]);
  if (!ID_PATTERN.test(packageId)) throw new HttpError(400, "invalid_package_id", "Package ID is invalid.");
  const body = await readBody(request, 4_000);
  const status = String(body.status || "").toUpperCase();
  if (!["ACTIVE", "ARCHIVED"].includes(status)) throw new HttpError(400, "invalid_package_status", "Package status must be ACTIVE or ARCHIVED.");
  const result = await env.DB.prepare(
    "UPDATE script_packages SET status = ?, updated_at = ? WHERE id = ?"
  ).bind(status, Math.floor(Date.now() / 1000), packageId).run();
  if (result.meta?.changes !== 1) throw new HttpError(404, "package_not_found", "Package was not found.");
  await audit(env, session.user_id, "package.status_changed", "script_package", packageId, { status });
  return response(request, { updated: true, packageId, status }, 200, env);
}

async function listCustomerPackages(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const now = Math.floor(Date.now() / 1000);
  const license = await getActiveLicense(env, session.user_id, now);
  if (!license) throw new HttpError(403, "active_license_required", "An active license is required to access published AUTUs.");
  const url = new URL(request.url);
  const fingerprint = (url.searchParams.get("deviceFingerprint") || "").toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(fingerprint)) throw new HttpError(400, "invalid_device_fingerprint", "A registered device fingerprint is required.");
  const device = await env.DB.prepare(
    "SELECT id FROM devices WHERE user_id = ? AND public_key_fingerprint = ? AND status = 'ACTIVE'"
  ).bind(session.user_id, fingerprint).first();
  if (!device) throw new HttpError(403, "active_device_required", "Register this device before accessing published AUTUs.");
  const result = await env.DB.prepare(
    "SELECT p.id, p.name, p.description, p.latest_version, v.language_version, v.created_at AS published_at " +
    "FROM script_packages p JOIN script_package_versions v ON v.package_id = p.id AND v.version = p.latest_version " +
    "WHERE p.status = 'ACTIVE' ORDER BY p.updated_at DESC"
  ).all();
  await env.DB.prepare(
    "UPDATE devices SET last_seen_at = ? WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"
  ).bind(now, device.id, session.user_id).run();
  return response(request, { deviceId: device.id, packages: result.results || [] }, 200, env);
}

async function downloadCustomerPackage(request, env, session, packageId) {
  await requireRole(session, ["CUSTOMER"]);
  if (!ID_PATTERN.test(packageId)) throw new HttpError(400, "invalid_package_id", "Package ID is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const license = await getActiveLicense(env, session.user_id, now);
  if (!license) throw new HttpError(403, "active_license_required", "An active license is required to download AUTUs.");
  const url = new URL(request.url);
  const fingerprint = (url.searchParams.get("deviceFingerprint") || "").toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(fingerprint)) throw new HttpError(400, "invalid_device_fingerprint", "A registered device fingerprint is required.");
  const device = await env.DB.prepare(
    "SELECT id, public_key_spki FROM devices WHERE user_id = ? AND public_key_fingerprint = ? AND status = 'ACTIVE'"
  ).bind(session.user_id, fingerprint).first();
  if (!device) throw new HttpError(403, "active_device_required", "This device is not registered or has been revoked.");
  const item = await env.DB.prepare(
    "SELECT p.id, p.name, p.description, p.status, v.version, v.language_version, v.ciphertext, v.content_iv, " +
    "v.encrypted_data_key, v.key_iv, v.key_version, v.source_sha256 " +
    "FROM script_packages p JOIN script_package_versions v ON v.package_id = p.id AND v.version = p.latest_version " +
    "WHERE p.id = ? AND p.status = 'ACTIVE'"
  ).bind(packageId).first();
  if (!item) throw new HttpError(404, "package_not_found", "Published AUTU package was not found.");
  const requestedWrapping = url.searchParams.get("keyWrapping") || "RSA-OAEP-SHA1";
  if (!["RSA-OAEP-SHA1", "RSA-OAEP-SHA256"].includes(requestedWrapping)) {
    throw new HttpError(400, "invalid_key_wrapping", "The requested key wrapping mode is not supported.");
  }
  const useCompressedPayload = url.searchParams.get("compression") === "GZIP";
  const keyHash = requestedWrapping === "RSA-OAEP-SHA256" ? "SHA-256" : "SHA-1";
  const payload = useCompressedPayload
    ? { ciphertext: item.ciphertext, contentIv: item.content_iv }
    : await createLegacyPackagePayload(env, item);
  const wrappedKey = await wrapPackageKeyForDevice(
    env, item.id, item.version, item.encrypted_data_key, item.key_iv, item.key_version, device.public_key_spki, keyHash
  );
  await env.DB.prepare(
    "UPDATE devices SET last_seen_at = ? WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"
  ).bind(now, device.id, session.user_id).run();
  await audit(env, session.user_id, "package.downloaded", "script_package", packageId, {
    version: item.version, deviceId: device.id
  });
  return response(request, {
    package: {
      id: item.id,
      name: item.name,
      description: item.description,
      version: item.version,
      languageVersion: item.language_version,
      ciphertext: payload.ciphertext,
      contentIv: payload.contentIv,
      wrappedKey,
      keyWrapping: requestedWrapping,
      sourceSha256: item.source_sha256
    }
  }, 200, env);
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();

  if (method === "OPTIONS") {
    const origin = request.headers.get("Origin");
    const allowed = configuredOrigins(env);
    if (origin && !allowed.includes(origin)) {
      return response(request, { error: "origin_not_allowed" }, 403, env);
    }
    return response(request, { ok: true }, 200, env);
  }

  if (method === "GET" && path === "/health") {
    return response(request, {
      ok: true,
      service: "AUTU-LIO API"
    }, 200, env);
  }

  if (method === "POST" && path === "/api/auth/challenge") return await createChallenge(request, env);
  if (method === "POST" && path === "/api/auth/google") return await googleLogin(request, env);

  const session = await getSession(request, env);

  if (method === "GET" && path === "/api/auth/me") {
    const user = {
      id: session.user_id,
      email: session.email,
      displayName: session.display_name,
      role: session.role
    };
    Object.assign(user, {
      googleSub: session.google_sub,
      emailVerified: Number(session.email_verified) === 1,
      latestSignIn: await latestSignInDetails(env, session.user_id)
    });
    if (session.role === "OWNER") {
      Object.assign(user, {
        createdAt: session.created_at,
        lastLoginAt: session.last_login_at
      });
    }
    return response(request, {
      user,
      session: { expiresAt: session.expires_at }
    }, 200, env);
  }


  if (method === "GET" && path === "/api/customer/account") {
    return await getCustomerAccount(request, env, session);
  }
  if (method === "GET" && path === "/api/customer/packages") {
    return await listCustomerPackages(request, env, session);
  }
  const customerPackageDownloadMatch = path.match(/^\/api\/customer\/packages\/([0-9a-f-]{36})\/download$/i);
  if (customerPackageDownloadMatch && method === "GET") {
    return await downloadCustomerPackage(request, env, session, customerPackageDownloadMatch[1]);
  }
  if (method === "POST" && path === "/api/customer/devices/challenge") {
    return await createCustomerDeviceChallenge(request, env, session);
  }
  if (method === "POST" && path === "/api/customer/devices") {
    return await registerCustomerDevice(request, env, session);
  }
  const ownDeviceMatch = path.match(/^\/api\/customer\/devices\/([0-9a-f-]{36})$/i);
  if (ownDeviceMatch && method === "DELETE") {
    return await removeOwnDevice(request, env, session, ownDeviceMatch[1]);
  }

  if (method === "GET" && path === "/api/admin/audit-logs") return await listAdminAuditLogs(request, env, session);
  if (method === "GET" && path === "/api/admin/packages") return await listAdminPackages(request, env, session);
  if (method === "POST" && path === "/api/admin/packages") return await createAdminPackage(request, env, session);
  const adminPackageSourceMatch = path.match(/^\/api\/admin\/packages\/([0-9a-f-]{36})\/source$/i);
  if (adminPackageSourceMatch && method === "GET") return await decryptPackageSourceForOwner(request, env, session, adminPackageSourceMatch[1]);
  const adminPackageVersionMatch = path.match(/^\/api\/admin\/packages\/([0-9a-f-]{36})\/versions$/i);
  if (adminPackageVersionMatch && method === "POST") return await createAdminPackageVersion(request, env, session, adminPackageVersionMatch[1]);
  const adminPackageMatch = path.match(/^\/api\/admin\/packages\/([0-9a-f-]{36})$/i);
  if (adminPackageMatch && method === "PATCH") return await updateAdminPackage(request, env, session, adminPackageMatch[1]);

  if (method === "GET" && path === "/api/admin/customers") return await listAdminCustomers(request, env, session);
  const adminCustomerMatch = path.match(/^\/api\/admin\/customers\/([0-9a-f-]{36})$/i);
  if (adminCustomerMatch && method === "PATCH") return await updateAdminCustomer(request, env, session, adminCustomerMatch[1]);
  if (method === "GET" && path === "/api/admin/licenses") return await listAdminLicenses(request, env, session);
  if (method === "POST" && path === "/api/admin/licenses") return await createCustomerLicense(request, env, session);
  const licenseMatch = path.match(/^\/api\/admin\/licenses\/([0-9a-f-]{36})$/i);
  if (licenseMatch && method === "PATCH") return await updateCustomerLicense(request, env, session, licenseMatch[1]);
  if (method === "GET" && path === "/api/admin/devices") return await listAdminDevices(request, env, session);
  const deviceMatch = path.match(/^\/api\/admin\/devices\/([0-9a-f-]{36})$/i);
  if (deviceMatch && method === "PATCH") return await updateAdminDeviceStatus(request, env, session, deviceMatch[1]);

  if (method === "POST" && path === "/api/auth/logout") {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").bind(now, session.session_id).run();
    await audit(env, session.user_id, "auth.logout", "session", session.session_id);
    return response(request, { loggedOut: true }, 200, env);
  }


  throw new HttpError(404, "route_not_found", "API route was not found.");
}

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env);
    } catch (error) {
      if (error instanceof HttpError) {
        return response(request, {
          error: error.code,
          message: error.message,
          ...(error.details || {})
        }, error.status, env);
      }
      console.error(JSON.stringify({
        message: "AUTU-LIO API request failed",
        path: new URL(request.url).pathname,
        errorType: error instanceof Error ? error.name : "UnknownError"
      }));
      return response(request, { error: "internal_error", message: "An unexpected server error occurred." }, 500, env);
    }
  }
};
