const encoder = new TextEncoder();
const OWNER_ROLES = new Set(["DEVELOPER", "DESIGNER", "REVIEWER"]);
const ASSIGNABLE_ROLES = new Set(["CUSTOMER", ...OWNER_ROLES]);
const ROLE_PERMISSIONS = {
  OWNER: ["*"],
  DEVELOPER: ["VIEW_ADMIN_WORKSPACE", "EDIT_SCRIPTS", "TEST_SCRIPTS", "SUBMIT_RELEASE"],
  DESIGNER: ["VIEW_ADMIN_WORKSPACE", "EDIT_UI_DESIGN", "PREVIEW_UI"],
  REVIEWER: ["VIEW_ADMIN_WORKSPACE", "TEST_SCRIPTS", "REVIEW_RELEASE"],
  CUSTOMER: ["RUN_PUBLISHED_SCRIPTS", "VIEW_OWN_ACCOUNT"]
};

const ALL_PERMISSION_NAMES = [
  "VIEW_ADMIN_WORKSPACE",
  "MANAGE_COLLABORATORS",
  "MANAGE_CUSTOMERS_AND_LICENSES",
  "EDIT_SCRIPTS",
  "TEST_SCRIPTS",
  "EDIT_UI_DESIGN",
  "PREVIEW_UI",
  "SUBMIT_RELEASE",
  "REVIEW_RELEASE",
  "PUBLISH_RELEASE",
  "RUN_PUBLISHED_SCRIPTS",
  "VIEW_OWN_ACCOUNT"
];

const ROLE_CONFIGURABLE_PERMISSIONS = {
  DEVELOPER: ["EDIT_SCRIPTS", "TEST_SCRIPTS", "SUBMIT_RELEASE"],
  DESIGNER: ["EDIT_UI_DESIGN", "PREVIEW_UI"],
  REVIEWER: ["TEST_SCRIPTS", "REVIEW_RELEASE"],
  CUSTOMER: []
};

class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function response(request, data, status = 200, env = {}) {
  const origin = request.headers.get("Origin");
  const allowed = (env.ALLOWED_ORIGINS || "*").split(",").map(x => x.trim());
  const originAllowed = !origin || allowed.includes("*") || allowed.includes(origin);
  const headers = {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Vary": "Origin"
  };
  if (originAllowed) {
    headers["Access-Control-Allow-Origin"] = allowed.includes("*") ? "*" : origin || "null";
    headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type";
    headers["Access-Control-Allow-Methods"] = "GET, POST, PATCH, DELETE, OPTIONS";
    headers["Access-Control-Max-Age"] = "600";
  }
  return new Response(JSON.stringify(data), { status, headers });
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
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}

function parseJson(value) {
  try {
    return JSON.parse(value);
  } catch {
    throw new HttpError(400, "invalid_json", "Invalid JSON body.");
  }
}

async function readBody(request) {
  const text = await request.text();
  if (text.length > 24_000) throw new HttpError(413, "body_too_large", "Request body is too large.");
  return text ? parseJson(text) : {};
}

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function validEmail(value) {
  return value.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
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
  return {
    sub: claims.sub,
    email: normalizeEmail(claims.email),
    name: typeof claims.name === "string" ? claims.name.slice(0, 120) : null
  };
}

async function getSession(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer ([A-Za-z0-9_-]{40,100})$/);
  if (!match) throw new HttpError(401, "authentication_required", "Sign in is required.");
  const tokenHash = await sha256(match[1]);
  const now = Math.floor(Date.now() / 1000);
  const session = await env.DB.prepare(
    "SELECT s.id AS session_id, s.user_id, s.expires_at, u.email, u.display_name, u.role, u.status, u.email_verified " +
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

async function hasPermission(env, session, permission) {
  if (session.role === "OWNER") return true;
  const override = await env.DB.prepare(
    "SELECT allowed FROM permission_overrides WHERE user_id = ? AND permission = ? AND scope = 'global'"
  ).bind(session.user_id, permission).first();
  if (override) return override.allowed === 1;
  return (ROLE_PERMISSIONS[session.role] || []).includes(permission);
}

async function requirePermission(env, session, permission) {
  if (!(await hasPermission(env, session, permission))) {
    throw new HttpError(403, "insufficient_permissions", "This account does not have the required permission.");
  }
}

async function effectivePermissions(env, userId, role) {
  if (role === "OWNER") return ALL_PERMISSION_NAMES;
  const permissions = new Set(ROLE_PERMISSIONS[role] || []);
  const result = await env.DB.prepare(
    "SELECT permission, allowed FROM permission_overrides WHERE user_id = ? AND scope = 'global'"
  ).bind(userId).all();
  for (const override of result.results || []) {
    if (override.allowed === 1) permissions.add(override.permission);
    else permissions.delete(override.permission);
  }
  return [...permissions];
}

async function audit(env, actorId, action, targetType = null, targetId = null, metadata = {}) {
  await env.DB.prepare(
    "INSERT INTO audit_logs (id, actor_user_id, action, target_type, target_id, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).bind(
    crypto.randomUUID(), actorId, action, targetType, targetId,
    JSON.stringify(metadata), Math.floor(Date.now() / 1000)
  ).run();
}

async function createChallenge(request, env) {
  if (!env.GOOGLE_CLIENT_ID) {
    throw new HttpError(503, "google_auth_not_configured", "Google Sign-In is not configured yet.");
  }
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
    const isConfiguredOwner = env.OWNER_EMAIL && identity.email === normalizeEmail(env.OWNER_EMAIL);
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
    if (env.OWNER_EMAIL && identity.email === normalizeEmail(env.OWNER_EMAIL) && user.role !== "OWNER") {
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
  await env.DB.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at, last_seen_at, device_label) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).bind(sessionId, user.id, await sha256(rawSession), now, expiresAt, now, deviceLabel).run();
  await audit(env, user.id, "auth.google_sign_in", "session", sessionId);
  return response(request, {
    accessToken: rawSession,
    tokenType: "Bearer",
    expiresAt,
    user: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      role: user.role,
      permissions: await effectivePermissions(env, user.id, user.role)
    }
  }, 200, env);
}

async function listCollaborators(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const users = await env.DB.prepare(
    "SELECT id, email, display_name, role, status, created_at, last_login_at FROM users WHERE role <> 'OWNER' ORDER BY created_at DESC"
  ).all();
  const invitations = await env.DB.prepare(
    "SELECT id, email, role, created_at, expires_at FROM invitations WHERE accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ? ORDER BY created_at DESC"
  ).bind(Math.floor(Date.now() / 1000)).all();
  return response(request, { collaborators: users.results || [], pendingInvitations: invitations.results || [] }, 200, env);
}

async function createInvitation(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request);
  const email = normalizeEmail(body.email);
  const role = String(body.role || "").toUpperCase();
  if (!validEmail(email)) throw new HttpError(400, "invalid_email", "A valid Google account email is required.");
  if (!OWNER_ROLES.has(role)) throw new HttpError(400, "invalid_role", "Only Developer, Designer or Reviewer can be invited.");
  const now = Math.floor(Date.now() / 1000);
  const days = Math.min(30, Math.max(1, Number(body.expiresInDays || 7)));
  const expiresAt = now + days * 86400;
  const invitationId = crypto.randomUUID();
  const token = randomToken(32);
  const tokenHash = await sha256(token);
  await env.DB.batch([
    env.DB.prepare(
      "UPDATE invitations SET revoked_at = ? WHERE lower(email) = ? AND accepted_at IS NULL AND revoked_at IS NULL"
    ).bind(now, email),
    env.DB.prepare(
      "INSERT INTO invitations (id, email, role, token_hash, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
    ).bind(invitationId, email, role, tokenHash, session.user_id, now, expiresAt)
  ]);
  await audit(env, session.user_id, "collaborator.invite_created", "invitation", invitationId, { email, role, expiresAt });
  return response(request, {
    invitationId,
    email,
    role,
    expiresAt,
    invitationToken: token,
    delivery: "copy_and_share_manually"
  }, 201, env);
}

async function previewInvitation(request, env) {
  const token = new URL(request.url).searchParams.get("token") || "";
  if (!/^[A-Za-z0-9_-]{40,100}$/.test(token)) throw new HttpError(400, "invalid_invitation", "Invitation token is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const invite = await env.DB.prepare(
    "SELECT email, role, expires_at FROM invitations WHERE token_hash = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ?"
  ).bind(await sha256(token), now).first();
  if (!invite) throw new HttpError(404, "invitation_not_found", "Invitation was not found or has expired.");
  return response(request, { email: invite.email, role: invite.role, expiresAt: invite.expires_at }, 200, env);
}

async function acceptInvitation(request, env, session) {
  const body = await readBody(request);
  const token = typeof body.invitationToken === "string" ? body.invitationToken : "";
  if (!/^[A-Za-z0-9_-]{40,100}$/.test(token)) throw new HttpError(400, "invalid_invitation", "Invitation token is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const invite = await env.DB.prepare(
    "SELECT id, email, role FROM invitations WHERE token_hash = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ?"
  ).bind(await sha256(token), now).first();
  if (!invite) throw new HttpError(404, "invitation_not_found", "Invitation was not found or has expired.");
  if (normalizeEmail(invite.email) !== normalizeEmail(session.email)) {
    throw new HttpError(403, "invitation_email_mismatch", "Sign in with the Google account that received this invitation.");
  }
  const accepted = await env.DB.batch([
    env.DB.prepare(
      "UPDATE invitations SET accepted_at = ?, accepted_by = ? WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL AND expires_at > ?"
    ).bind(now, session.user_id, invite.id, now),
    env.DB.prepare(
      "UPDATE users SET role = ?, updated_at = ? WHERE id = ? AND role <> 'OWNER' AND status = 'ACTIVE' " +
      "AND lower(email) = ? AND EXISTS (SELECT 1 FROM invitations WHERE id = ? AND accepted_by = ? AND accepted_at = ?)"
    ).bind(invite.role, now, session.user_id, normalizeEmail(session.email), invite.id, session.user_id, now)
  ]);
  if (accepted[0]?.meta?.changes !== 1 || accepted[1]?.meta?.changes !== 1) {
    throw new HttpError(409, "invitation_acceptance_conflict", "Invitation could not be accepted.");
  }
  const updated = await env.DB.prepare(
    "SELECT role FROM users WHERE id = ?"
  ).bind(session.user_id).first();
  await audit(env, session.user_id, "collaborator.invite_accepted", "invitation", invite.id, { role: invite.role });
  return response(request, { accepted: true, role: updated.role }, 200, env);
}

async function revokeInvitation(request, env, session, invitationId) {
  await requireRole(session, ["OWNER"]);
  const invitation = await env.DB.prepare(
    "SELECT id, email, role, accepted_at, revoked_at FROM invitations WHERE id = ?"
  ).bind(invitationId).first();
  if (!invitation || invitation.accepted_at || invitation.revoked_at) {
    throw new HttpError(404, "invitation_not_found", "Pending invitation was not found.");
  }
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "UPDATE invitations SET revoked_at = ? WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL"
  ).bind(now, invitationId).run();
  if (result.meta?.changes !== 1) {
    throw new HttpError(409, "invitation_revoke_conflict", "Invitation could not be revoked.");
  }
  await audit(env, session.user_id, "collaborator.invitation_revoked", "invitation", invitationId, {
    email: invitation.email,
    role: invitation.role
  });
  return response(request, { revoked: true, invitationId }, 200, env);
}

async function updateCollaborator(request, env, session, userId) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request);
  const role = String(body.role || "").toUpperCase();
  if (!ASSIGNABLE_ROLES.has(role)) throw new HttpError(400, "invalid_role", "Requested role is not assignable.");
  const target = await env.DB.prepare("SELECT id, role FROM users WHERE id = ?").bind(userId).first();
  if (!target || target.role === "OWNER") throw new HttpError(404, "collaborator_not_found", "Collaborator was not found.");
  const now = Math.floor(Date.now() / 1000);
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET role = ?, updated_at = ? WHERE id = ?").bind(role, now, userId),
    env.DB.prepare("DELETE FROM permission_overrides WHERE user_id = ? AND scope = 'global'").bind(userId)
  ]);
  await audit(env, session.user_id, "collaborator.role_changed", "user", userId, { from: target.role, to: role });
  return response(request, { updated: true, userId, role }, 200, env);
}

async function getCollaboratorPermissions(request, env, session, userId) {
  await requireRole(session, ["OWNER"]);
  const target = await env.DB.prepare("SELECT id, role FROM users WHERE id = ?").bind(userId).first();
  if (!target || target.role === "OWNER") {
    throw new HttpError(404, "collaborator_not_found", "Collaborator was not found.");
  }
  const availablePermissions = ROLE_CONFIGURABLE_PERMISSIONS[target.role] || [];
  const permissions = await effectivePermissions(env, target.id, target.role);
  return response(request, {
    userId,
    role: target.role,
    permissions,
    configurablePermissions: availablePermissions,
    selectedPermissions: permissions.filter(permission => availablePermissions.includes(permission))
  }, 200, env);
}

async function updateCollaboratorPermissions(request, env, session, userId) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request);
  const requested = body.permissions;
  if (!Array.isArray(requested) || requested.length > 8 ||
      requested.some(permission => typeof permission !== "string")) {
    throw new HttpError(400, "invalid_permissions", "A valid permissions list is required.");
  }
  const target = await env.DB.prepare("SELECT id, role FROM users WHERE id = ?").bind(userId).first();
  if (!target || target.role === "OWNER") {
    throw new HttpError(404, "collaborator_not_found", "Collaborator was not found.");
  }
  const available = ROLE_CONFIGURABLE_PERMISSIONS[target.role] || [];
  const selected = new Set(requested);
  if (selected.size !== requested.length || [...selected].some(permission => !available.includes(permission))) {
    throw new HttpError(400, "invalid_permissions", "One or more permissions cannot be assigned to this role.");
  }

  const defaults = new Set(ROLE_PERMISSIONS[target.role] || []);
  const statements = [
    env.DB.prepare("DELETE FROM permission_overrides WHERE user_id = ? AND scope = 'global'").bind(userId)
  ];
  for (const permission of available) {
    const allowed = selected.has(permission);
    if (allowed !== defaults.has(permission)) {
      statements.push(env.DB.prepare(
        "INSERT INTO permission_overrides (id, user_id, permission, scope, allowed, granted_by, created_at) VALUES (?, ?, ?, 'global', ?, ?, ?)"
      ).bind(crypto.randomUUID(), userId, permission, allowed ? 1 : 0, session.user_id, Math.floor(Date.now() / 1000)));
    }
  }
  await env.DB.batch(statements);
  await audit(env, session.user_id, "collaborator.permissions_updated", "user", userId, {
    role: target.role,
    permissions: [...selected]
  });
  return response(request, {
    updated: true,
    userId,
    role: target.role,
    permissions: await effectivePermissions(env, userId, target.role)
  }, 200, env);
}

async function revokeCollaborator(request, env, session, userId) {
  await requireRole(session, ["OWNER"]);
  const target = await env.DB.prepare("SELECT id, role FROM users WHERE id = ?").bind(userId).first();
  if (!target || target.role === "OWNER") throw new HttpError(404, "collaborator_not_found", "Collaborator was not found.");
  const now = Math.floor(Date.now() / 1000);
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET status = 'REVOKED', updated_at = ? WHERE id = ?").bind(now, userId),
    env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").bind(now, userId)
  ]);
  await audit(env, session.user_id, "collaborator.access_revoked", "user", userId, { previousRole: target.role });
  return response(request, { revoked: true, userId }, 200, env);
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();

  if (method === "OPTIONS") {
    const origin = request.headers.get("Origin");
    const allowed = (env.ALLOWED_ORIGINS || "*").split(",").map(x => x.trim());
    if (origin && !allowed.includes("*") && !allowed.includes(origin)) {
      return response(request, { error: "origin_not_allowed" }, 403, env);
    }
    return response(request, { ok: true }, 200, env);
  }

  if (method === "GET" && path === "/health") {
    return response(request, {
      ok: true,
      service: "AUTU-LIO Auth API",
      databaseConfigured: Boolean(env.DB),
      googleSignInConfigured: Boolean(env.GOOGLE_CLIENT_ID),
      ownerBootstrapConfigured: Boolean(env.OWNER_EMAIL)
    }, 200, env);
  }

  if (method === "POST" && path === "/api/auth/challenge") return await createChallenge(request, env);
  if (method === "POST" && path === "/api/auth/google") return await googleLogin(request, env);

  if (method === "GET" && path === "/api/invitations/preview") return await previewInvitation(request, env);

  const session = await getSession(request, env);

  if (method === "GET" && path === "/api/auth/me") {
    return response(request, {
      user: {
        id: session.user_id,
        email: session.email,
        displayName: session.display_name,
        role: session.role,
        permissions: await effectivePermissions(env, session.user_id, session.role)
      },
      session: { expiresAt: session.expires_at }
    }, 200, env);
  }

  if (method === "POST" && path === "/api/auth/logout") {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").bind(now, session.session_id).run();
    await audit(env, session.user_id, "auth.logout", "session", session.session_id);
    return response(request, { loggedOut: true }, 200, env);
  }

  if (method === "POST" && path === "/api/invitations/accept") return await acceptInvitation(request, env, session);
  if (method === "GET" && path === "/api/admin/collaborators") return await listCollaborators(request, env, session);
  if (method === "POST" && path === "/api/admin/invitations") return await createInvitation(request, env, session);

  const invitationMatch = path.match(/^\/api\/admin\/invitations\/([0-9a-f-]{36})$/i);
  if (invitationMatch && method === "DELETE") return await revokeInvitation(request, env, session, invitationMatch[1]);

  const collaboratorPermissionsMatch = path.match(/^\/api\/admin\/collaborators\/([0-9a-f-]{36})\/permissions$/i);
  if (collaboratorPermissionsMatch && method === "GET") {
    return await getCollaboratorPermissions(request, env, session, collaboratorPermissionsMatch[1]);
  }
  if (collaboratorPermissionsMatch && method === "PUT") {
    return await updateCollaboratorPermissions(request, env, session, collaboratorPermissionsMatch[1]);
  }

  const collaboratorMatch = path.match(/^\/api\/admin\/collaborators\/([0-9a-f-]{36})$/i);
  if (collaboratorMatch && method === "PATCH") return await updateCollaborator(request, env, session, collaboratorMatch[1]);
  if (collaboratorMatch && method === "DELETE") return await revokeCollaborator(request, env, session, collaboratorMatch[1]);

  throw new HttpError(404, "route_not_found", "API route was not found.");
}

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env);
    } catch (error) {
      if (error instanceof HttpError) {
        return response(request, { error: error.code, message: error.message }, error.status, env);
      }
      console.error(JSON.stringify({
        message: "AUTU-LIO API request failed",
        path: new URL(request.url).pathname,
        error: error instanceof Error ? error.message : "Unknown error"
      }));
      return response(request, { error: "internal_error", message: "An unexpected server error occurred." }, 500, env);
    }
  }
};
