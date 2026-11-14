import { createRemoteJWKSet, jwtVerify } from "jose";

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const SESSION_LIFETIME_SECONDS = 60 * 60 * 24 * 30;
const INVITATION_LIFETIME_SECONDS = 60 * 60 * 24 * 7;
const COLLABORATOR_ROLES = new Set(["DEVELOPER", "DESIGNER", "REVIEWER"]);
const ASSIGNABLE_ROLES = new Set(["DEVELOPER", "DESIGNER", "REVIEWER", "CUSTOMER"]);

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin");
    const allowedOrigins = (env.ALLOWED_ORIGINS || "")
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean);

    if (origin && !allowedOrigins.includes(origin)) {
      return json({ error: "origin_not_allowed" }, 403);
    }

    const cors = origin
      ? {
          "Access-Control-Allow-Origin": origin,
          "Access-Control-Allow-Methods": "GET,POST,PATCH,DELETE,OPTIONS",
          "Access-Control-Allow-Headers": "Authorization,Content-Type",
          "Access-Control-Max-Age": "86400",
          "Vary": "Origin"
        }
      : {};

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    const url = new URL(request.url);

    try {
      let response;
      if (url.pathname === "/health" && request.method === "GET") {
        response = json({ ok: true, service: "autu-lio-api" }, 200);
      } else if (url.pathname === "/v1/auth/google" && request.method === "POST") {
        response = await googleLogin(request, env);
      } else if (url.pathname === "/v1/me" && request.method === "GET") {
        const user = await requireUser(request, env);
        response = user ? json({ user: publicUser(user) }) : unauthorized();
      } else if (url.pathname === "/v1/auth/logout" && request.method === "POST") {
        response = await logout(request, env);
      } else if (url.pathname === "/v1/admin/collaborators" && request.method === "GET") {
        response = await listCollaborators(request, env);
      } else if (url.pathname === "/v1/admin/invitations" && request.method === "GET") {
        response = await listInvitations(request, env);
      } else if (url.pathname === "/v1/admin/invitations" && request.method === "POST") {
        response = await createInvitation(request, env);
      } else if (url.pathname.startsWith("/v1/admin/collaborators/") && url.pathname.endsWith("/role") && request.method === "PATCH") {
        const id = url.pathname.slice("/v1/admin/collaborators/".length, -"/role".length);
        response = await updateCollaboratorRole(request, env, id);
      } else if (url.pathname.startsWith("/v1/admin/collaborators/") && request.method === "DELETE") {
        const id = url.pathname.slice("/v1/admin/collaborators/".length);
        response = await revokeCollaborator(request, env, id);
      } else if (url.pathname.startsWith("/v1/admin/invitations/") && request.method === "DELETE") {
        const id = url.pathname.slice("/v1/admin/invitations/".length);
        response = await revokeInvitation(request, env, id);
      } else {
        response = json({ error: "not_found" }, 404);
      }

      return withHeaders(response, cors);
    } catch (error) {
      console.error(JSON.stringify({
        message: "request_failed",
        path: url.pathname,
        error: error instanceof Error ? error.message : "unknown"
      }));
      return withHeaders(json({ error: "internal_error" }, 500), cors);
    }
  }
};

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

function withHeaders(response, extraHeaders) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(extraHeaders)) headers.set(key, value);
  return new Response(response.body, { status: response.status, headers });
}

function unauthorized() {
  return json({ error: "unauthorized" }, 401);
}

function forbidden() {
  return json({ error: "forbidden" }, 403);
}

async function readJson(request) {
  try {
    const value = await request.json();
    return value && typeof value === "object" && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

function encodeBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\\+/g, "-").replace(/\\//g, "_");
}

function randomToken(byteLength = 32) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return encodeBase64Url(bytes);
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function verifyGoogleIdToken(idToken, env, expectedNonce) {
  if (!env.GOOGLE_WEB_CLIENT_ID || env.GOOGLE_WEB_CLIENT_ID === "SET_GOOGLE_WEB_CLIENT_ID") {
    throw new Error("google_client_id_not_configured");
  }

  const verified = await jwtVerify(idToken, GOOGLE_JWKS, {
    issuer: ["https://accounts.google.com", "accounts.google.com"],
    audience: env.GOOGLE_WEB_CLIENT_ID,
    algorithms: ["RS256"],
    clockTolerance: 60
  });
  const claims = verified.payload;
  const now = Math.floor(Date.now() / 1000);

  if (Array.isArray(claims.aud) && claims.aud.length > 1 && claims.azp !== env.GOOGLE_WEB_CLIENT_ID) {
    throw new Error("invalid_authorized_party");
  }
  if (typeof claims.sub !== "string" || !claims.sub) throw new Error("missing_google_subject");
  if (typeof claims.email !== "string" || !claims.email) throw new Error("missing_email");
  if (claims.email_verified !== true && claims.email_verified !== "true") {
    throw new Error("email_not_verified");
  }
  if (typeof expectedNonce !== "string" || expectedNonce.length < 20 || claims.nonce !== expectedNonce) {
    throw new Error("invalid_nonce");
  }
  if (typeof claims.iat !== "number" || claims.iat > now + 60) {
    throw new Error("invalid_issue_time");
  }

  return claims;
}

async function googleLogin(request, env) {
  const body = await readJson(request);
  const idToken = body?.idToken;
  const invitationCode = typeof body?.invitationCode === "string" ? body.invitationCode.trim() : "";
  const nonce = typeof body?.nonce === "string" ? body.nonce : "";

  if (typeof idToken !== "string" || idToken.length < 100 || idToken.length > 12000 || nonce.length < 20 || nonce.length > 200) {
    return json({ error: "invalid_request" }, 400);
  }
  if (invitationCode.length > 200) return json({ error: "invalid_invitation_code" }, 400);

  let claims;
  try {
    claims = await verifyGoogleIdToken(idToken, env, nonce);
  } catch (error) {
    const reason = error instanceof Error ? error.message : "invalid_id_token";
    const status = reason === "google_client_id_not_configured" ? 503 : 401;
    return json({ error: status === 503 ? "auth_not_configured" : "invalid_google_token" }, status);
  }

  const now = Math.floor(Date.now() / 1000);
  const tokenHash = await sha256Hex(idToken);
  await env.DB.prepare("DELETE FROM consumed_google_tokens WHERE expires_at < ?").bind(now).run();
  const consumed = await env.DB.prepare(
    "INSERT OR IGNORE INTO consumed_google_tokens (token_hash, expires_at, consumed_at) VALUES (?, ?, ?)"
  ).bind(tokenHash, claims.exp, now).run();

  if (consumed.meta?.changes !== 1) {
    return json({ error: "google_token_already_used", retry: "sign_in_again" }, 409);
  }

  const googleSub = claims.sub;
  const email = claims.email.trim().toLowerCase();
  const displayName = typeof claims.name === "string" ? claims.name.slice(0, 160) : "";
  let user = await env.DB.prepare(
    "SELECT id, google_sub, email, display_name, role, status FROM users WHERE google_sub = ? LIMIT 1"
  ).bind(googleSub).first();

  if (user?.status === "REVOKED") return forbidden();

  if (user && invitationCode) {
    const accepted = await acceptInvitationForExistingUser(env, user, email, invitationCode, now);
    if (!accepted) return json({ error: "invitation_invalid_or_expired" }, 400);
    user = await env.DB.prepare(
      "SELECT id, google_sub, email, display_name, role, status FROM users WHERE id = ? LIMIT 1"
    ).bind(user.id).first();
  }

  if (!user) {
    let role = "CUSTOMER";
    let invitation = null;

    if (invitationCode) {
      const invitationHash = await sha256Hex(invitationCode);
      invitation = await env.DB.prepare(
        "SELECT id, email, role, expires_at FROM collaborator_invitations WHERE token_hash = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ? LIMIT 1"
      ).bind(invitationHash, now).first();

      if (!invitation || invitation.email.toLowerCase() !== email) {
        return json({ error: "invitation_invalid_or_expired" }, 400);
      }
      role = invitation.role;
    } else {
      const owner = await env.DB.prepare(
        "SELECT id FROM users WHERE role = 'OWNER' AND status = 'ACTIVE' LIMIT 1"
      ).first();
      const bootstrapEmail = (env.OWNER_BOOTSTRAP_EMAIL || "").trim().toLowerCase();
      if (!owner && bootstrapEmail && email === bootstrapEmail) role = "OWNER";
    }

    const userId = crypto.randomUUID();
    if (invitation) {
      const invitationHash = await sha256Hex(invitationCode);
      const batch = await env.DB.batch([
        env.DB.prepare(
          "UPDATE collaborator_invitations SET used_at = ?, used_by_user_id = ? WHERE id = ? AND token_hash = ? AND email = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ?"
        ).bind(now, userId, invitation.id, invitationHash, email, now),
        env.DB.prepare(
          "INSERT INTO users (id, google_sub, email, display_name, role, status, created_at, updated_at) SELECT ?, ?, ?, ?, ?, 'ACTIVE', ?, ? WHERE EXISTS (SELECT 1 FROM collaborator_invitations WHERE id = ? AND used_by_user_id = ?)"
        ).bind(userId, googleSub, email, displayName, role, now, now, invitation.id, userId)
      ]);
      if (batch[0].meta?.changes !== 1 || batch[1].meta?.changes !== 1) {
        return json({ error: "invitation_invalid_or_expired" }, 409);
      }
    } else {
      try {
        await env.DB.prepare(
          "INSERT INTO users (id, google_sub, email, display_name, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)"
        ).bind(userId, googleSub, email, displayName, role, now, now).run();
      } catch (error) {
        const existing = await env.DB.prepare(
          "SELECT id, google_sub, email, display_name, role, status FROM users WHERE google_sub = ? LIMIT 1"
        ).bind(googleSub).first();
        if (existing) {
          user = existing;
        } else if (role === "OWNER") {
          await env.DB.prepare(
            "INSERT INTO users (id, google_sub, email, display_name, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'CUSTOMER', 'ACTIVE', ?, ?)"
          ).bind(userId, googleSub, email, displayName, now, now).run();
        } else {
          throw error;
        }
      }
    }

    user = user || await env.DB.prepare(
      "SELECT id, google_sub, email, display_name, role, status FROM users WHERE google_sub = ? LIMIT 1"
    ).bind(googleSub).first();
  }

  if (!user || user.status !== "ACTIVE") return forbidden();

  if (user.role !== "OWNER") {
    const owner = await env.DB.prepare(
      "SELECT id FROM users WHERE role = 'OWNER' AND status = 'ACTIVE' LIMIT 1"
    ).first();
    const bootstrapEmail = (env.OWNER_BOOTSTRAP_EMAIL || "").trim().toLowerCase();
    if (!owner && bootstrapEmail && email === bootstrapEmail) {
      await env.DB.prepare("UPDATE users SET role = 'OWNER', updated_at = ? WHERE id = ? AND status = 'ACTIVE'")
        .bind(now, user.id).run();
      user.role = "OWNER";
    }
  }

  const sessionToken = randomToken(32);
  const sessionHash = await sha256Hex(sessionToken);
  const sessionExpiresAt = now + SESSION_LIFETIME_SECONDS;
  await env.DB.prepare(
    "INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)"
  ).bind(sessionHash, user.id, now, sessionExpiresAt).run();

  return json({
    sessionToken,
    expiresAt: sessionExpiresAt,
    user: publicUser(user)
  });
}

async function acceptInvitationForExistingUser(env, user, email, code, now) {
  if (user.role === "OWNER") return false;
  const hash = await sha256Hex(code);
  const invitation = await env.DB.prepare(
    "SELECT id, role FROM collaborator_invitations WHERE token_hash = ? AND email = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ? LIMIT 1"
  ).bind(hash, email, now).first();

  if (!invitation || !COLLABORATOR_ROLES.has(invitation.role)) return false;

  const batch = await env.DB.batch([
    env.DB.prepare(
      "UPDATE collaborator_invitations SET used_at = ?, used_by_user_id = ? WHERE id = ? AND token_hash = ? AND email = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > ?"
    ).bind(now, user.id, invitation.id, hash, email, now),
    env.DB.prepare(
      "UPDATE users SET role = ?, updated_at = ? WHERE id = ? AND status = 'ACTIVE' AND EXISTS (SELECT 1 FROM collaborator_invitations WHERE id = ? AND used_by_user_id = ?)"
    ).bind(invitation.role, now, user.id, invitation.id, user.id)
  ]);

  return batch[0].meta?.changes === 1 && batch[1].meta?.changes === 1;
}

function publicUser(user) {
  return {
    id: user.id,
    email: user.email,
    displayName: user.display_name,
    role: user.role
  };
}

async function requireUser(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+([A-Za-z0-9_-]{32,})$/);
  if (!match) return null;

  const now = Math.floor(Date.now() / 1000);
  const tokenHash = await sha256Hex(match[1]);
  return env.DB.prepare(
    "SELECT u.id, u.email, u.display_name, u.role, u.status FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND u.status = 'ACTIVE' LIMIT 1"
  ).bind(tokenHash, now).first();
}

async function requireOwner(request, env) {
  const user = await requireUser(request, env);
  if (!user) return { error: unauthorized() };
  if (user.role !== "OWNER" || user.status !== "ACTIVE") return { error: forbidden() };
  return { user };
}

async function writeAudit(env, actorId, action, targetId = null) {
  await env.DB.prepare(
    "INSERT INTO audit_log (id, actor_user_id, action, target_id, created_at) VALUES (?, ?, ?, ?, ?)"
  ).bind(crypto.randomUUID(), actorId, action, targetId, Math.floor(Date.now() / 1000)).run();
}

async function listCollaborators(request, env) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;
  const result = await env.DB.prepare(
    "SELECT id, email, display_name, role, status, created_at FROM users WHERE role != 'OWNER' ORDER BY created_at DESC LIMIT 500"
  ).all();
  return json({ collaborators: result.results || [] });
}

async function listInvitations(request, env) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;
  const result = await env.DB.prepare(
    "SELECT id, email, role, created_at, expires_at, used_at, revoked_at FROM collaborator_invitations ORDER BY created_at DESC LIMIT 500"
  ).all();
  return json({ invitations: result.results || [] });
}

async function createInvitation(request, env) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;

  const body = await readJson(request);
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  const role = typeof body?.role === "string" ? body.role.toUpperCase() : "";

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
    return json({ error: "invalid_email" }, 400);
  }
  if (!COLLABORATOR_ROLES.has(role)) return json({ error: "invalid_collaborator_role" }, 400);

  const id = crypto.randomUUID();
  const invitationCode = randomToken(32);
  const tokenHash = await sha256Hex(invitationCode);
  const now = Math.floor(Date.now() / 1000);
  const expiresAt = now + INVITATION_LIFETIME_SECONDS;

  await env.DB.prepare(
    "INSERT INTO collaborator_invitations (id, email, role, token_hash, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).bind(id, email, role, tokenHash, access.user.id, now, expiresAt).run();

  await writeAudit(env, access.user.id, "COLLABORATOR_INVITED", id);
  return json({
    invitation: { id, email, role, createdAt: now, expiresAt },
    invitationCode,
    note: "Copy and share this code once. It will not be shown again."
  }, 201);
}

async function updateCollaboratorRole(request, env, collaboratorId) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;
  if (!collaboratorId || collaboratorId === access.user.id) return json({ error: "invalid_target" }, 400);

  const body = await readJson(request);
  const role = typeof body?.role === "string" ? body.role.toUpperCase() : "";
  if (!ASSIGNABLE_ROLES.has(role)) return json({ error: "invalid_role" }, 400);

  const target = await env.DB.prepare(
    "SELECT id, role FROM users WHERE id = ? AND status = 'ACTIVE' LIMIT 1"
  ).bind(collaboratorId).first();
  if (!target || target.role === "OWNER") return json({ error: "collaborator_not_found" }, 404);

  await env.DB.prepare(
    "UPDATE users SET role = ?, updated_at = ? WHERE id = ? AND role != 'OWNER'"
  ).bind(role, Math.floor(Date.now() / 1000), collaboratorId).run();
  await writeAudit(env, access.user.id, "COLLABORATOR_ROLE_CHANGED", collaboratorId);
  return json({ ok: true, collaboratorId, role });
}

async function revokeCollaborator(request, env, collaboratorId) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;
  if (!collaboratorId || collaboratorId === access.user.id) return json({ error: "invalid_target" }, 400);

  const target = await env.DB.prepare(
    "SELECT id, role FROM users WHERE id = ? AND status = 'ACTIVE' LIMIT 1"
  ).bind(collaboratorId).first();
  if (!target || target.role === "OWNER") return json({ error: "collaborator_not_found" }, 404);

  const now = Math.floor(Date.now() / 1000);
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET status = 'REVOKED', updated_at = ? WHERE id = ? AND role != 'OWNER'").bind(now, collaboratorId),
    env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").bind(now, collaboratorId)
  ]);
  await writeAudit(env, access.user.id, "COLLABORATOR_REVOKED", collaboratorId);
  return json({ ok: true });
}

async function revokeInvitation(request, env, invitationId) {
  const access = await requireOwner(request, env);
  if (access.error) return access.error;
  if (!invitationId) return json({ error: "invalid_invitation" }, 400);

  const now = Math.floor(Date.now() / 1000);
  const result = await env.DB.prepare(
    "UPDATE collaborator_invitations SET revoked_at = ? WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL"
  ).bind(now, invitationId).run();
  if (result.meta?.changes !== 1) return json({ error: "invitation_not_found" }, 404);

  await writeAudit(env, access.user.id, "INVITATION_REVOKED", invitationId);
  return json({ ok: true });
}

async function logout(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+([A-Za-z0-9_-]{32,})$/);
  if (!match) return json({ ok: true });

  const tokenHash = await sha256Hex(match[1]);
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare(
    "UPDATE sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL"
  ).bind(now, tokenHash).run();
  return json({ ok: true });
}
