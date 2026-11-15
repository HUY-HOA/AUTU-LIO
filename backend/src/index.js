const encoder = new TextEncoder();
const OWNER_ROLES = new Set(["DEVELOPER", "DESIGNER", "REVIEWER"]);
const ASSIGNABLE_ROLES = new Set(["CUSTOMER", ...OWNER_ROLES]);
const ROLE_PERMISSIONS = {
  OWNER: ["*"],
  DEVELOPER: ["VIEW_ADMIN_WORKSPACE", "EDIT_SCRIPTS", "TEST_SCRIPTS", "SUBMIT_RELEASE"],
  DESIGNER: ["VIEW_ADMIN_WORKSPACE", "EDIT_UI_DESIGN", "PREVIEW_UI", "SUBMIT_RELEASE"],
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
  DESIGNER: ["EDIT_UI_DESIGN", "PREVIEW_UI", "SUBMIT_RELEASE"],
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
    headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
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

async function readBody(request, maxChars = 24_000) {
  const text = await request.text();
  if (text.length > maxChars) throw new HttpError(413, "body_too_large", "Request body is too large.");
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


const PROJECT_ID_PATTERN = /^[0-9a-fA-F-]{36}$/;
const MAX_PROJECT_BODY_CHARS = 90_000;
const MAX_DRAFT_SOURCE_CHARS = 64_000;
const MAX_UI_DESIGN_CHARS = 64_000;

async function requireProjectAccess(env, session, projectId) {
  if (session.role !== "OWNER" && !["DEVELOPER", "DESIGNER", "REVIEWER"].includes(session.role)) {
    throw new HttpError(404, "project_not_found", "Project was not found.");
  }
  if (!PROJECT_ID_PATTERN.test(projectId)) {
    throw new HttpError(400, "invalid_project_id", "Project ID is invalid.");
  }
  const project = await env.DB.prepare(
    "SELECT id, name, description, status, created_by, created_at, updated_at FROM projects WHERE id = ?"
  ).bind(projectId).first();
  if (!project || project.status !== "ACTIVE") {
    throw new HttpError(404, "project_not_found", "Project was not found.");
  }
  if (session.role !== "OWNER") {
    const membership = await env.DB.prepare(
      "SELECT 1 AS allowed FROM project_memberships WHERE project_id = ? AND user_id = ?"
    ).bind(projectId, session.user_id).first();
    if (!membership) throw new HttpError(404, "project_not_found", "Project was not found.");
  }
  return project;
}

async function listProjects(request, env, session) {
  const query = session.role === "OWNER"
    ? env.DB.prepare(
      "SELECT p.id, p.name, p.description, p.status, p.created_at, p.updated_at, 'OWNER' AS access_role " +
      "FROM projects p WHERE p.status = 'ACTIVE' ORDER BY p.updated_at DESC"
    )
    : env.DB.prepare(
      "SELECT p.id, p.name, p.description, p.status, p.created_at, p.updated_at, u.role AS access_role " +
      "FROM projects p JOIN project_memberships m ON m.project_id = p.id " +
      "JOIN users u ON u.id = m.user_id WHERE m.user_id = ? AND p.status = 'ACTIVE' " +
      "AND u.role IN ('DEVELOPER','DESIGNER','REVIEWER') ORDER BY p.updated_at DESC"
    ).bind(session.user_id);
  const result = await query.all();
  return response(request, { projects: result.results || [] }, 200, env);
}

async function createProject(request, env, session) {
  await requireRole(session, ["OWNER"]);
  const body = await readBody(request);
  const name = typeof body.name === "string" ? body.name.trim() : "";
  const description = typeof body.description === "string" ? body.description.trim() : "";
  if (name.length < 2 || name.length > 80) {
    throw new HttpError(400, "invalid_project_name", "Project name must contain 2 to 80 characters.");
  }
  if (description.length > 500) {
    throw new HttpError(400, "invalid_project_description", "Project description is too long.");
  }
  const now = Math.floor(Date.now() / 1000);
  const projectId = crypto.randomUUID();
  await env.DB.prepare(
    "INSERT INTO projects (id, name, description, status, created_by, created_at, updated_at) " +
    "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)"
  ).bind(projectId, name, description, session.user_id, now, now).run();
  await audit(env, session.user_id, "project.created", "project", projectId, { name });
  return response(request, { created: true, project: { id: projectId, name, description, status: "ACTIVE" } }, 201, env);
}

async function listProjectMembers(request, env, session, projectId) {
  await requireRole(session, ["OWNER"]);
  await requireProjectAccess(env, session, projectId);
  const result = await env.DB.prepare(
    "SELECT u.id, u.email, u.display_name, u.role, u.status, m.created_at AS added_at " +
    "FROM project_memberships m JOIN users u ON u.id = m.user_id " +
    "WHERE m.project_id = ? ORDER BY m.created_at ASC"
  ).bind(projectId).all();
  return response(request, { members: result.results || [] }, 200, env);
}

async function addProjectMember(request, env, session, projectId) {
  await requireRole(session, ["OWNER"]);
  await requireProjectAccess(env, session, projectId);
  const body = await readBody(request);
  const userId = typeof body.userId === "string" ? body.userId : "";
  if (!PROJECT_ID_PATTERN.test(userId)) {
    throw new HttpError(400, "invalid_user_id", "User ID is invalid.");
  }
  const target = await env.DB.prepare(
    "SELECT id, email, role, status FROM users WHERE id = ?"
  ).bind(userId).first();
  if (!target || target.status !== "ACTIVE" || !["DEVELOPER", "DESIGNER", "REVIEWER"].includes(target.role)) {
    throw new HttpError(400, "invalid_project_member", "Only active Developer, Designer or Reviewer accounts can join a project.");
  }
  const now = Math.floor(Date.now() / 1000);
  try {
    await env.DB.prepare(
      "INSERT INTO project_memberships (project_id, user_id, added_by, created_at) VALUES (?, ?, ?, ?)"
    ).bind(projectId, userId, session.user_id, now).run();
  } catch (error) {
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "project_member_exists", "This collaborator is already in the project.");
    }
    throw error;
  }
  await audit(env, session.user_id, "project.member_added", "project", projectId, { userId, role: target.role });
  return response(request, { added: true, projectId, userId, email: target.email, role: target.role }, 201, env);
}

async function removeProjectMember(request, env, session, projectId, userId) {
  await requireRole(session, ["OWNER"]);
  await requireProjectAccess(env, session, projectId);
  if (!PROJECT_ID_PATTERN.test(userId)) {
    throw new HttpError(400, "invalid_user_id", "User ID is invalid.");
  }
  const result = await env.DB.prepare(
    "DELETE FROM project_memberships WHERE project_id = ? AND user_id = ?"
  ).bind(projectId, userId).run();
  if (result.meta?.changes !== 1) {
    throw new HttpError(404, "project_member_not_found", "Project member was not found.");
  }
  await audit(env, session.user_id, "project.member_removed", "project", projectId, { userId });
  return response(request, { removed: true, projectId, userId }, 200, env);
}

async function requireProjectPermission(env, session, projectId, permission) {
  const project = await requireProjectAccess(env, session, projectId);
  await requirePermission(env, session, permission);
  return project;
}

async function listProjectScripts(request, env, session, projectId) {
  await requireProjectAccess(env, session, projectId);
  const canEdit = await hasPermission(env, session, "EDIT_SCRIPTS");
  const canReview = await hasPermission(env, session, "REVIEW_RELEASE");
  if (!canEdit && !canReview) {
    throw new HttpError(403, "insufficient_permissions", "This account cannot view script drafts.");
  }
  const result = await env.DB.prepare(
    "SELECT id, name, language_version, revision, created_by, updated_by, created_at, updated_at" +
    (canEdit ? ", source" : "") +
    " FROM script_drafts WHERE project_id = ? ORDER BY updated_at DESC"
  ).bind(projectId).all();
  return response(request, { scripts: result.results || [] }, 200, env);
}

async function createProjectScript(request, env, session, projectId) {
  await requireProjectPermission(env, session, projectId, "EDIT_SCRIPTS");
  const body = await readBody(request, MAX_PROJECT_BODY_CHARS);
  const name = typeof body.name === "string" ? body.name.trim() : "";
  const source = typeof body.source === "string" ? body.source : "";
  const languageVersion = typeof body.languageVersion === "string" ? body.languageVersion.trim() : "0.1";
  if (name.length < 1 || name.length > 100) {
    throw new HttpError(400, "invalid_script_name", "Script name must contain 1 to 100 characters.");
  }
  if (source.length > MAX_DRAFT_SOURCE_CHARS || languageVersion.length > 30) {
    throw new HttpError(413, "script_draft_too_large", "Script draft exceeds the allowed size.");
  }
  const now = Math.floor(Date.now() / 1000);
  const scriptId = crypto.randomUUID();
  try {
    await env.DB.prepare(
      "INSERT INTO script_drafts (id, project_id, name, source, language_version, revision, created_by, updated_by, created_at, updated_at) " +
      "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)"
    ).bind(scriptId, projectId, name, source, languageVersion || "0.1", session.user_id, session.user_id, now, now).run();
  } catch (error) {
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "script_name_exists", "A script with this name already exists in the project.");
    }
    throw error;
  }
  await env.DB.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").bind(now, projectId).run();
  await audit(env, session.user_id, "project.script_created", "script_draft", scriptId, { projectId, revision: 1 });
  return response(request, {
    created: true,
    script: { id: scriptId, name, source, languageVersion: languageVersion || "0.1", revision: 1, updatedAt: now }
  }, 201, env);
}

async function updateProjectScript(request, env, session, projectId, scriptId) {
  await requireProjectPermission(env, session, projectId, "EDIT_SCRIPTS");
  if (!PROJECT_ID_PATTERN.test(scriptId)) {
    throw new HttpError(400, "invalid_script_id", "Script ID is invalid.");
  }
  const body = await readBody(request, MAX_PROJECT_BODY_CHARS);
  const name = typeof body.name === "string" ? body.name.trim() : "";
  const source = typeof body.source === "string" ? body.source : "";
  const languageVersion = typeof body.languageVersion === "string" ? body.languageVersion.trim() : "0.1";
  const expectedRevision = Number(body.expectedRevision);
  if (name.length < 1 || name.length > 100) {
    throw new HttpError(400, "invalid_script_name", "Script name must contain 1 to 100 characters.");
  }
  if (source.length > MAX_DRAFT_SOURCE_CHARS || languageVersion.length > 30) {
    throw new HttpError(413, "script_draft_too_large", "Script draft exceeds the allowed size.");
  }
  if (!Number.isInteger(expectedRevision) || expectedRevision < 1) {
    throw new HttpError(400, "invalid_revision", "A valid expected revision is required.");
  }
  const current = await env.DB.prepare(
    "SELECT id, revision FROM script_drafts WHERE id = ? AND project_id = ?"
  ).bind(scriptId, projectId).first();
  if (!current) throw new HttpError(404, "script_not_found", "Script draft was not found.");
  if (current.revision !== expectedRevision) {
    throw new HttpError(409, "revision_conflict", "This draft was changed by another collaborator. Reload before saving.");
  }
  const now = Math.floor(Date.now() / 1000);
  try {
    const result = await env.DB.prepare(
      "UPDATE script_drafts SET name = ?, source = ?, language_version = ?, revision = revision + 1, updated_by = ?, updated_at = ? " +
      "WHERE id = ? AND project_id = ? AND revision = ?"
    ).bind(name, source, languageVersion || "0.1", session.user_id, now, scriptId, projectId, expectedRevision).run();
    if (result.meta?.changes !== 1) {
      throw new HttpError(409, "revision_conflict", "This draft was changed by another collaborator. Reload before saving.");
    }
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (String(error?.message || "").toLowerCase().includes("unique")) {
      throw new HttpError(409, "script_name_exists", "A script with this name already exists in the project.");
    }
    throw error;
  }
  await env.DB.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").bind(now, projectId).run();
  await audit(env, session.user_id, "project.script_updated", "script_draft", scriptId, {
    projectId, fromRevision: expectedRevision, toRevision: expectedRevision + 1
  });
  return response(request, {
    updated: true,
    scriptId,
    revision: expectedRevision + 1,
    updatedAt: now
  }, 200, env);
}

async function deleteProjectScript(request, env, session, projectId, scriptId) {
  await requireProjectPermission(env, session, projectId, "EDIT_SCRIPTS");
  if (!PROJECT_ID_PATTERN.test(scriptId)) throw new HttpError(400, "invalid_script_id", "Script ID is invalid.");
  const result = await env.DB.prepare(
    "DELETE FROM script_drafts WHERE id = ? AND project_id = ?"
  ).bind(scriptId, projectId).run();
  if (result.meta?.changes !== 1) throw new HttpError(404, "script_not_found", "Script draft was not found.");
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").bind(now, projectId).run();
  await audit(env, session.user_id, "project.script_deleted", "script_draft", scriptId, { projectId });
  return response(request, { deleted: true, scriptId }, 200, env);
}

async function listProjectUiDrafts(request, env, session, projectId) {
  await requireProjectAccess(env, session, projectId);
  const canEdit = await hasPermission(env, session, "EDIT_UI_DESIGN");
  const canPreview = await hasPermission(env, session, "PREVIEW_UI");
  if (!canEdit && !canPreview) {
    throw new HttpError(403, "insufficient_permissions", "This account cannot view UI drafts.");
  }
  const result = await env.DB.prepare(
    "SELECT id, screen_key, design_json, revision, created_by, updated_by, created_at, updated_at " +
    "FROM ui_drafts WHERE project_id = ? ORDER BY updated_at DESC"
  ).bind(projectId).all();
  return response(request, { designs: result.results || [] }, 200, env);
}

async function saveProjectUiDraft(request, env, session, projectId, screenKey) {
  await requireProjectPermission(env, session, projectId, "EDIT_UI_DESIGN");
  if (!/^[A-Za-z0-9._-]{1,80}$/.test(screenKey)) {
    throw new HttpError(400, "invalid_screen_key", "Screen key may contain only letters, numbers, dot, underscore and hyphen.");
  }
  const body = await readBody(request, MAX_PROJECT_BODY_CHARS);
  if (!body.design || typeof body.design !== "object" || Array.isArray(body.design)) {
    throw new HttpError(400, "invalid_ui_design", "A JSON object is required for the UI design.");
  }
  const designJson = JSON.stringify(body.design);
  if (designJson.length > MAX_UI_DESIGN_CHARS) {
    throw new HttpError(413, "ui_design_too_large", "UI design exceeds the allowed size.");
  }
  const expectedRevision = body.expectedRevision == null ? 0 : Number(body.expectedRevision);
  if (!Number.isInteger(expectedRevision) || expectedRevision < 0) {
    throw new HttpError(400, "invalid_revision", "Expected revision must be zero or a positive integer.");
  }
  const now = Math.floor(Date.now() / 1000);
  const current = await env.DB.prepare(
    "SELECT id, revision FROM ui_drafts WHERE project_id = ? AND screen_key = ?"
  ).bind(projectId, screenKey).first();
  let designId;
  let revision;
  if (!current) {
    if (expectedRevision !== 0) {
      throw new HttpError(409, "revision_conflict", "This UI draft does not exist at the expected revision.");
    }
    designId = crypto.randomUUID();
    revision = 1;
    try {
      await env.DB.prepare(
        "INSERT INTO ui_drafts (id, project_id, screen_key, design_json, revision, created_by, updated_by, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?)"
      ).bind(designId, projectId, screenKey, designJson, session.user_id, session.user_id, now, now).run();
    } catch (error) {
      if (String(error?.message || "").toLowerCase().includes("unique")) {
        throw new HttpError(409, "revision_conflict", "This UI draft was created by another collaborator. Reload before saving.");
      }
      throw error;
    }
  } else {
    if (current.revision !== expectedRevision) {
      throw new HttpError(409, "revision_conflict", "This UI draft was changed by another collaborator. Reload before saving.");
    }
    designId = current.id;
    revision = expectedRevision + 1;
    const result = await env.DB.prepare(
      "UPDATE ui_drafts SET design_json = ?, revision = revision + 1, updated_by = ?, updated_at = ? " +
      "WHERE id = ? AND project_id = ? AND revision = ?"
    ).bind(designJson, session.user_id, now, designId, projectId, expectedRevision).run();
    if (result.meta?.changes !== 1) {
      throw new HttpError(409, "revision_conflict", "This UI draft was changed by another collaborator. Reload before saving.");
    }
  }
  await env.DB.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").bind(now, projectId).run();
  await audit(env, session.user_id, "project.ui_draft_saved", "ui_draft", designId, {
    projectId, screenKey, revision
  });
  return response(request, { saved: true, designId, screenKey, revision, updatedAt: now }, current ? 200 : 201, env);
}

async function createProjectSubmission(request, env, session, projectId) {
  await requireProjectPermission(env, session, projectId, "SUBMIT_RELEASE");
  const body = await readBody(request, MAX_PROJECT_BODY_CHARS);
  const kind = typeof body.kind === "string" ? body.kind.toUpperCase() : "";
  const itemId = typeof body.itemId === "string" ? body.itemId : "";
  const revision = Number(body.revision);
  if (!["SCRIPT", "UI"].includes(kind) || !Number.isInteger(revision) || revision < 1) {
    throw new HttpError(400, "invalid_submission", "A valid submission kind and revision are required.");
  }
  await requirePermission(env, session, kind === "SCRIPT" ? "EDIT_SCRIPTS" : "EDIT_UI_DESIGN");
  let snapshot;
  if (kind === "SCRIPT") {
    if (!PROJECT_ID_PATTERN.test(itemId)) throw new HttpError(400, "invalid_item_id", "Script ID is invalid.");
    snapshot = await env.DB.prepare(
      "SELECT id, name, source, language_version AS languageVersion, revision FROM script_drafts WHERE id = ? AND project_id = ?"
    ).bind(itemId, projectId).first();
  } else {
    snapshot = await env.DB.prepare(
      "SELECT id, screen_key AS screenKey, design_json AS designJson, revision FROM ui_drafts WHERE id = ? AND project_id = ?"
    ).bind(itemId, projectId).first();
  }
  if (!snapshot) throw new HttpError(404, "draft_not_found", "The draft to submit was not found.");
  if (snapshot.revision !== revision) {
    throw new HttpError(409, "revision_conflict", "The draft changed before it could be submitted. Reload and submit the latest revision.");
  }
  if (kind === "UI") {
    snapshot.design = JSON.parse(snapshot.designJson);
    delete snapshot.designJson;
  }
  const submissionId = crypto.randomUUID();
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare(
    "INSERT INTO change_submissions (id, project_id, kind, item_id, revision, submitted_snapshot, submitted_by, status, created_at) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)"
  ).bind(submissionId, projectId, kind, itemId, revision, JSON.stringify(snapshot), session.user_id, now).run();
  await audit(env, session.user_id, "project.change_submitted", "submission", submissionId, {
    projectId, kind, itemId, revision
  });
  return response(request, { submitted: true, submissionId, kind, itemId, revision, status: "PENDING" }, 201, env);
}

async function listProjectSubmissions(request, env, session, projectId) {
  await requireProjectAccess(env, session, projectId);
  await requirePermission(env, session, "REVIEW_RELEASE");
  const result = await env.DB.prepare(
    "SELECT s.id, s.kind, s.item_id, s.revision, s.submitted_snapshot, s.submitted_by, " +
    "u.email AS submitter_email, s.status, s.review_note, s.reviewed_by, s.created_at, s.reviewed_at " +
    "FROM change_submissions s JOIN users u ON u.id = s.submitted_by " +
    "WHERE s.project_id = ? ORDER BY CASE s.status WHEN 'PENDING' THEN 0 ELSE 1 END, s.created_at DESC"
  ).bind(projectId).all();
  const submissions = (result.results || []).map(item => ({
    ...item,
    snapshot: JSON.parse(item.submitted_snapshot)
  }));
  for (const item of submissions) delete item.submitted_snapshot;
  return response(request, { submissions }, 200, env);
}

async function reviewProjectSubmission(request, env, session, projectId, submissionId) {
  await requireProjectPermission(env, session, projectId, "REVIEW_RELEASE");
  if (!PROJECT_ID_PATTERN.test(submissionId)) {
    throw new HttpError(400, "invalid_submission_id", "Submission ID is invalid.");
  }
  const body = await readBody(request);
  const status = typeof body.status === "string" ? body.status.toUpperCase() : "";
  const note = typeof body.note === "string" ? body.note.trim() : "";
  if (!["APPROVED", "REJECTED"].includes(status) || note.length > 1000) {
    throw new HttpError(400, "invalid_review", "Review status or note is invalid.");
  }
  const submission = await env.DB.prepare(
    "SELECT id, submitted_by, status FROM change_submissions WHERE id = ? AND project_id = ?"
  ).bind(submissionId, projectId).first();
  if (!submission) throw new HttpError(404, "submission_not_found", "Submission was not found.");
  if (submission.status !== "PENDING") {
    throw new HttpError(409, "submission_already_reviewed", "This submission has already been reviewed.");
  }
  if (submission.submitted_by === session.user_id) {
    throw new HttpError(403, "self_review_not_allowed", "A collaborator cannot review their own submission.");
  }
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "UPDATE change_submissions SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ? " +
    "WHERE id = ? AND project_id = ? AND status = 'PENDING'"
  ).bind(status, note, session.user_id, now, submissionId, projectId).run();
  if (result.meta?.changes !== 1) {
    throw new HttpError(409, "submission_already_reviewed", "This submission has already been reviewed.");
  }
  await audit(env, session.user_id, "project.submission_reviewed", "submission", submissionId, {
    projectId, status
  });
  return response(request, { reviewed: true, submissionId, status, reviewedAt: now }, 200, env);
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
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  const result = await env.DB.prepare(
    "SELECT u.id, u.email, u.display_name, u.status, u.created_at, u.last_login_at, " +
    "l.id AS license_id, l.plan_code, l.status AS license_status, l.starts_at, l.expires_at, l.max_devices, " +
    "(SELECT COUNT(*) FROM devices d WHERE d.user_id = u.id AND d.status = 'ACTIVE') AS active_device_count " +
    "FROM users u LEFT JOIN licenses l ON l.user_id = u.id AND l.status = 'ACTIVE' " +
    "WHERE u.role = 'CUSTOMER' ORDER BY u.created_at DESC"
  ).all();
  return response(request, { customers: result.results || [] }, 200, env);
}

async function listAdminLicenses(request, env, session) {
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  const result = await env.DB.prepare(
    "SELECT l.id, l.user_id, u.email, u.display_name, l.plan_code, l.status, l.starts_at, l.expires_at, " +
    "l.max_devices, l.created_at, l.updated_at, " +
    "(SELECT COUNT(*) FROM devices d WHERE d.user_id = l.user_id AND d.status = 'ACTIVE') AS active_device_count " +
    "FROM licenses l JOIN users u ON u.id = l.user_id ORDER BY l.created_at DESC"
  ).all();
  return response(request, { licenses: result.results || [] }, 200, env);
}

async function createCustomerLicense(request, env, session) {
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  const body = await readBody(request);
  const userId = typeof body.userId === "string" ? body.userId.trim() : "";
  const planCode = typeof body.planCode === "string" ? body.planCode.trim().toUpperCase() : "";
  const now = Math.floor(Date.now() / 1000);
  const startsAt = body.startsAt == null ? now : Number(body.startsAt);
  const expiresAt = Number(body.expiresAt);
  const maxDevices = Number(body.maxDevices ?? 1);

  if (!PROJECT_ID_PATTERN.test(userId)) {
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
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  if (!PROJECT_ID_PATTERN.test(licenseId)) throw new HttpError(400, "invalid_license_id", "License ID is invalid.");
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

  if (status === "REVOKED") {
    await env.DB.prepare(
      "UPDATE devices SET status = 'REVOKED', revoked_at = ? WHERE user_id = ? AND status = 'ACTIVE'"
    ).bind(now, current.user_id).run();
  }

  await audit(env, session.user_id, "license.updated", "license", licenseId, {
    status, planCode, startsAt, expiresAt, maxDevices
  });
  return response(request, { updated: true, licenseId, status }, 200, env);
}

async function listAdminDevices(request, env, session) {
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  const result = await env.DB.prepare(
    "SELECT d.id, d.user_id, u.email, u.display_name, d.label, d.manufacturer, d.model, " +
    "d.android_version, d.app_version, d.status, d.registered_at, d.last_seen_at, d.revoked_at " +
    "FROM devices d JOIN users u ON u.id = d.user_id ORDER BY d.last_seen_at DESC"
  ).all();
  return response(request, { devices: result.results || [] }, 200, env);
}

async function revokeCustomerDevice(request, env, session, deviceId) {
  await requirePermission(env, session, "MANAGE_CUSTOMERS_AND_LICENSES");
  if (!PROJECT_ID_PATTERN.test(deviceId)) throw new HttpError(400, "invalid_device_id", "Device ID is invalid.");
  const device = await env.DB.prepare(
    "SELECT id, user_id, status FROM devices WHERE id = ?"
  ).bind(deviceId).first();
  if (!device) throw new HttpError(404, "device_not_found", "Device was not found.");
  if (device.status === "REVOKED") return response(request, { revoked: true, deviceId }, 200, env);
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare(
    "UPDATE devices SET status = 'REVOKED', revoked_at = ? WHERE id = ? AND status = 'ACTIVE'"
  ).bind(now, deviceId).run();
  await audit(env, session.user_id, "device.revoked_by_owner", "device", deviceId, { customerId: device.user_id });
  return response(request, { revoked: true, deviceId }, 200, env);
}

async function getCustomerAccount(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const now = Math.floor(Date.now() / 1000);
  const license = await env.DB.prepare(
    "SELECT id, plan_code, status, starts_at, expires_at, max_devices FROM licenses " +
    "WHERE user_id = ? ORDER BY created_at DESC LIMIT 1"
  ).bind(session.user_id).first();
  const result = await env.DB.prepare(
    "SELECT id, label, manufacturer, model, android_version, app_version, status, registered_at, last_seen_at, revoked_at " +
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
    account: { id: session.user_id, email: session.email, displayName: session.display_name },
    license: license ? { ...license, effective_status: licenseState } : null,
    devices: result.results || [],
    activeDeviceCount,
    canRegisterDevice: licenseState === "ACTIVE" && activeDeviceCount < (license?.max_devices || 0),
    canRun: licenseState === "ACTIVE"
  }, 200, env);
}

async function registerCustomerDevice(request, env, session) {
  await requireRole(session, ["CUSTOMER"]);
  const body = await readBody(request, 12_000);
  const publicKeySpki = typeof body.publicKeySpki === "string" ? body.publicKeySpki.trim() : "";
  if (publicKeySpki.length < 200 || publicKeySpki.length > 8192 ||
      !/^[A-Za-z0-9+/=_-]+$/.test(publicKeySpki)) {
    throw new HttpError(400, "invalid_device_public_key", "A valid Android Keystore public key is required.");
  }

  let publicKeyBytes;
  let publicKey;
  try {
    publicKeyBytes = base64UrlDecode(publicKeySpki);
    publicKey = await crypto.subtle.importKey(
      "spki",
      publicKeyBytes,
      { name: "RSA-OAEP", hash: "SHA-256" },
      false,
      ["encrypt"]
    );
  } catch {
    throw new HttpError(400, "invalid_device_public_key", "The device public key could not be verified.");
  }
  const modulusLength = publicKey.algorithm?.modulusLength;
  if (!Number.isInteger(modulusLength) || modulusLength < 2048 || modulusLength > 4096) {
    throw new HttpError(400, "weak_device_public_key", "The device key must use RSA 2048 to 4096 bits.");
  }

  const now = Math.floor(Date.now() / 1000);
  const license = await getActiveLicense(env, session.user_id, now);
  if (!license) throw new HttpError(403, "active_license_required", "An active license is required to register this device.");

  const publicKeyCanonical = base64UrlEncode(publicKeyBytes);
  const digest = await crypto.subtle.digest("SHA-256", publicKeyBytes);
  const fingerprint = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  const existing = await env.DB.prepare(
    "SELECT id, status FROM devices WHERE user_id = ? AND public_key_fingerprint = ?"
  ).bind(session.user_id, fingerprint).first();
  if (existing?.status === "REVOKED") {
    throw new HttpError(403, "device_revoked", "This device was revoked by the application owner.");
  }

  const metadata = {
    label: cleanDeviceText(body.label, 100),
    manufacturer: cleanDeviceText(body.manufacturer, 80),
    model: cleanDeviceText(body.model, 100),
    androidVersion: cleanDeviceText(body.androidVersion, 40),
    appVersion: cleanDeviceText(body.appVersion, 40)
  };

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

  const count = await env.DB.prepare(
    "SELECT COUNT(*) AS active_count FROM devices WHERE user_id = ? AND status = 'ACTIVE'"
  ).bind(session.user_id).first();
  if ((count?.active_count || 0) >= license.max_devices) {
    throw new HttpError(409, "device_limit_reached", "The license has reached its active device limit.");
  }

  const id = crypto.randomUUID();
  await env.DB.prepare(
    "INSERT INTO devices (id, user_id, public_key_spki, public_key_fingerprint, label, manufacturer, model, android_version, app_version, status, registered_at, last_seen_at) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)"
  ).bind(
    id, session.user_id, publicKeyCanonical, fingerprint,
    metadata.label, metadata.manufacturer, metadata.model, metadata.androidVersion, metadata.appVersion, now, now
  ).run();
  await audit(env, session.user_id, "device.registered", "device", id, { fingerprint });
  return response(request, {
    registered: true,
    alreadyRegistered: false,
    device: { id, ...metadata, status: "ACTIVE", registeredAt: now, lastSeenAt: now }
  }, 201, env);
}

async function removeOwnDevice(request, env, session, deviceId) {
  await requireRole(session, ["CUSTOMER"]);
  if (!PROJECT_ID_PATTERN.test(deviceId)) throw new HttpError(400, "invalid_device_id", "Device ID is invalid.");
  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "UPDATE devices SET status = 'REVOKED', revoked_at = ? WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"
  ).bind(now, deviceId, session.user_id).run();
  if (result.meta?.changes !== 1) throw new HttpError(404, "device_not_found", "Active device was not found on this account.");
  await audit(env, session.user_id, "device.removed_by_customer", "device", deviceId);
  return response(request, { removed: true, deviceId }, 200, env);
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


  if (method === "GET" && path === "/api/customer/account") {
    return await getCustomerAccount(request, env, session);
  }
  if (method === "POST" && path === "/api/customer/devices") {
    return await registerCustomerDevice(request, env, session);
  }
  const ownDeviceMatch = path.match(/^\/api\/customer\/devices\/([0-9a-f-]{36})$/i);
  if (ownDeviceMatch && method === "DELETE") {
    return await removeOwnDevice(request, env, session, ownDeviceMatch[1]);
  }

  if (method === "GET" && path === "/api/admin/customers") return await listAdminCustomers(request, env, session);
  if (method === "GET" && path === "/api/admin/licenses") return await listAdminLicenses(request, env, session);
  if (method === "POST" && path === "/api/admin/licenses") return await createCustomerLicense(request, env, session);
  const licenseMatch = path.match(/^\/api\/admin\/licenses\/([0-9a-f-]{36})$/i);
  if (licenseMatch && method === "PATCH") return await updateCustomerLicense(request, env, session, licenseMatch[1]);
  if (method === "GET" && path === "/api/admin/devices") return await listAdminDevices(request, env, session);
  const deviceMatch = path.match(/^\/api\/admin\/devices\/([0-9a-f-]{36})$/i);
  if (deviceMatch && method === "PATCH") return await revokeCustomerDevice(request, env, session, deviceMatch[1]);

  if (method === "POST" && path === "/api/auth/logout") {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").bind(now, session.session_id).run();
    await audit(env, session.user_id, "auth.logout", "session", session.session_id);
    return response(request, { loggedOut: true }, 200, env);
  }


  if (method === "GET" && path === "/api/projects") return await listProjects(request, env, session);
  if (method === "POST" && path === "/api/admin/projects") return await createProject(request, env, session);

  const projectMemberMatch = path.match(/^\/api\/admin\/projects\/([0-9a-f-]{36})\/members$/i);
  if (projectMemberMatch && method === "GET") {
    return await listProjectMembers(request, env, session, projectMemberMatch[1]);
  }
  if (projectMemberMatch && method === "POST") {
    return await addProjectMember(request, env, session, projectMemberMatch[1]);
  }
  const projectMemberDeleteMatch = path.match(/^\/api\/admin\/projects\/([0-9a-f-]{36})\/members\/([0-9a-f-]{36})$/i);
  if (projectMemberDeleteMatch && method === "DELETE") {
    return await removeProjectMember(request, env, session, projectMemberDeleteMatch[1], projectMemberDeleteMatch[2]);
  }

  const projectScriptsMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/scripts$/i);
  if (projectScriptsMatch && method === "GET") {
    return await listProjectScripts(request, env, session, projectScriptsMatch[1]);
  }
  if (projectScriptsMatch && method === "POST") {
    return await createProjectScript(request, env, session, projectScriptsMatch[1]);
  }
  const projectScriptMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/scripts\/([0-9a-f-]{36})$/i);
  if (projectScriptMatch && method === "PUT") {
    return await updateProjectScript(request, env, session, projectScriptMatch[1], projectScriptMatch[2]);
  }
  if (projectScriptMatch && method === "DELETE") {
    return await deleteProjectScript(request, env, session, projectScriptMatch[1], projectScriptMatch[2]);
  }

  const projectUiDraftsMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/ui-drafts$/i);
  if (projectUiDraftsMatch && method === "GET") {
    return await listProjectUiDrafts(request, env, session, projectUiDraftsMatch[1]);
  }
  const projectUiDraftMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/ui-drafts\/([A-Za-z0-9._-]{1,80})$/i);
  if (projectUiDraftMatch && method === "PUT") {
    return await saveProjectUiDraft(request, env, session, projectUiDraftMatch[1], projectUiDraftMatch[2]);
  }

  const projectSubmissionsMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/submissions$/i);
  if (projectSubmissionsMatch && method === "GET") {
    return await listProjectSubmissions(request, env, session, projectSubmissionsMatch[1]);
  }
  if (projectSubmissionsMatch && method === "POST") {
    return await createProjectSubmission(request, env, session, projectSubmissionsMatch[1]);
  }
  const projectSubmissionMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})\/submissions\/([0-9a-f-]{36})$/i);
  if (projectSubmissionMatch && method === "PATCH") {
    return await reviewProjectSubmission(request, env, session, projectSubmissionMatch[1], projectSubmissionMatch[2]);
  }

  const projectMatch = path.match(/^\/api\/projects\/([0-9a-f-]{36})$/i);
  if (projectMatch && method === "GET") {
    const project = await requireProjectAccess(env, session, projectMatch[1]);
    return response(request, { project }, 200, env);
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
