import test from "node:test";
import assert from "node:assert/strict";
import worker, { isConfiguredOwnerIdentity } from "../src/index.js";
import { gzipSync, gunzipSync } from "node:zlib";

const customerSession = {
  session_id: "session-customer",
  user_id: "customer-user",
  expires_at: Math.floor(Date.now() / 1000) + 3600,
  email: "customer@example.com",
  display_name: "Customer",
  role: "CUSTOMER",
  status: "ACTIVE",
  email_verified: 1,
  google_sub: "google-sub-customer-123",
  created_at: 1794000000,
  last_login_at: 1795000000
};

const ownerSession = {
  session_id: "session-owner",
  user_id: "owner-user",
  expires_at: Math.floor(Date.now() / 1000) + 3600,
  email: "owner@example.com",
  display_name: "Owner",
  role: "OWNER",
  status: "ACTIVE",
  email_verified: 1
};

function createEnv({
  session = null,
  allowedOrigins = "",
  auditRows = [],
  challengeLimitSuccess = true,
  loginLimitSuccess = true,
  accountLicense = null,
  accountDevices = [],
  latestSignInRow = null
} = {}) {
  const queries = [];
  const DB = {
    prepare(sql) {
      queries.push(sql);
      const statement = {
        sql,
        values: [],
        bind(...values) {
          this.values = values;
          return this;
        },
        async first() {
          if (sql.includes("FROM sessions s JOIN users u")) return session;
          if (sql.includes("SELECT metadata_json, created_at FROM audit_logs")) return latestSignInRow;
          if (sql.includes("FROM licenses") && sql.includes("ORDER BY created_at DESC")) return accountLicense;
          return null;
        },
        async all() {
          if (sql.includes("FROM audit_logs")) return { results: auditRows };
          if (sql.includes("FROM devices WHERE user_id = ? ORDER BY registered_at DESC")) {
            return { results: accountDevices };
          }
          return { results: [] };
        },
        async run() {
          return { meta: { changes: 1 } };
        }
      };
      return statement;
    },
    async batch() {
      return [];
    }
  };
  return {
    env: {
      DB,
      ALLOWED_ORIGINS: allowedOrigins,
      AUTH_CHALLENGE_LIMITER: { limit: async () => ({ success: challengeLimitSuccess }) },
      AUTH_LOGIN_LIMITER: { limit: async () => ({ success: loginLimitSuccess }) }
    },
    queries
  };
}

function createDeviceRegistrationEnv({
  challenge,
  license,
  session = customerSession,
  existingDevice = null,
  activeDeviceCount = 0
}) {
  const queries = [];
  const operations = [];
  const DB = {
    prepare(sql) {
      queries.push(sql);
      return {
        sql,
        values: [],
        bind(...values) {
          this.values = values;
          return this;
        },
        async first() {
          if (sql.includes("FROM sessions s JOIN users u")) return session;
          if (sql.includes("FROM device_enrollment_challenges")) return challenge;
          if (sql.includes("FROM licenses")) return license;
          if (sql.includes("SELECT id, status, revoked_reason FROM devices")) return existingDevice;
          return null;
        },
        async all() {
          return { results: [] };
        },
        async run() {
          operations.push({ sql, values: this.values });
          if (sql.includes("UPDATE devices SET status = 'ACTIVE'") &&
              activeDeviceCount >= Number(this.values[this.values.length - 1])) {
            return { meta: { changes: 0 } };
          }
          return { meta: { changes: 1 } };
        }
      };
    },
    async batch() {
      return [];
    }
  };
  return { env: { DB }, queries, operations };
}

async function makeSignedDeviceEnrollment() {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 3072,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256"
    },
    true,
    ["sign", "verify"]
  );
  const publicKeyBytes = new Uint8Array(await crypto.subtle.exportKey("spki", keyPair.publicKey));
  const publicKeySpki = btoa(String.fromCharCode(...publicKeyBytes));
  const challengeId = crypto.randomUUID();
  const nonceBytes = crypto.getRandomValues(new Uint8Array(32));
  const nonce = btoa(String.fromCharCode(...nonceBytes))
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const nonceDigest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(nonce));
  const nonceHash = Array.from(new Uint8Array(nonceDigest), value => value.toString(16).padStart(2, "0")).join("");
  const signatureBytes = new Uint8Array(await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    keyPair.privateKey,
    new TextEncoder().encode(challengeId + "." + nonce)
  ));
  return {
    challengeId,
    nonce,
    nonceHash,
    publicKeySpki,
    signature: btoa(String.fromCharCode(...signatureBytes))
  };
}

function makeRequest(path, options = {}) {
  return new Request(`https://autu-lio.test${path}`, options);
}

async function json(response) {
  return response.json();
}

test("owner bootstrap matches the verified Google sub, not the email address", () => {
  const env = { OWNER_GOOGLE_SUB: "google-sub-owner-123" };

  assert.equal(
    isConfiguredOwnerIdentity(
      { sub: "google-sub-owner-123", email: "different@example.com" },
      env
    ),
    true
  );
  assert.equal(
    isConfiguredOwnerIdentity(
      { sub: "another-google-sub-456", email: "owner@example.com" },
      env
    ),
    false
  );
});

test("missing OWNER_GOOGLE_SUB never grants OWNER even when an owner email exists", () => {
  const identity = { sub: "google-sub-owner-123", email: "owner@example.com" };

  assert.equal(isConfiguredOwnerIdentity(identity, {}), false);
  assert.equal(
    isConfiguredOwnerIdentity(identity, { OWNER_GOOGLE_SUB: "   " }),
    false
  );
});

test("public health endpoint exposes only minimal service status", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(makeRequest("/health"), env);
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.deepEqual(body, { ok: true, service: "AUTU-LIO API" });
  assert.equal(JSON.stringify(body).includes("owner@example.com"), false);
  assert.equal(JSON.stringify(body).includes("GOOGLE_CLIENT_ID"), false);
});

test("API responses include no-store and browser security headers", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(makeRequest("/health"), env);

  assert.equal(response.headers.get("Cache-Control"), "no-store");
  assert.equal(response.headers.get("X-Content-Type-Options"), "nosniff");
  assert.equal(response.headers.get("X-Frame-Options"), "DENY");
  assert.equal(response.headers.get("Referrer-Policy"), "no-referrer");
  assert.equal(response.headers.get("Cross-Origin-Resource-Policy"), "same-origin");
  assert.match(response.headers.get("Content-Security-Policy") || "", /default-src 'none'/);
  assert.match(response.headers.get("Strict-Transport-Security") || "", /max-age=31536000/);
});

test("browser CORS is denied by default when no web origins are configured", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "OPTIONS",
      headers: { Origin: "https://untrusted.example" }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "origin_not_allowed");
});

test("CORS never treats wildcard as an allowed origin", async () => {
  const { env } = createEnv({ allowedOrigins: "*" });
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "OPTIONS",
      headers: { Origin: "https://untrusted.example" }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "origin_not_allowed");
});

test("legacy collaboration and invitation routes are removed", async () => {
  const { env, queries } = createEnv({ session: ownerSession });
  const paths = [
    ["/api/admin/collaborators", "GET"],
    ["/api/admin/invitations", "POST"],
    ["/api/invitations/accept", "POST"],
    ["/api/projects", "GET"]
  ];
  for (const [path, method] of paths) {
    const response = await worker.fetch(
      makeRequest(path, {
        method,
        headers: { Authorization: `Bearer ${"C".repeat(48)}` }
      }),
      env
    );
    assert.equal(response.status, 404, path);
    assert.equal((await json(response)).error, "route_not_found", path);
  }
  assert.equal(queries.some(sql =>
    /invitations|permission_overrides|projects|project_memberships|script_drafts|ui_drafts|change_submissions/i.test(sql)
  ), false);
});

test("auth challenge requests are rate limited before creating D1 rows", async () => {
  const { env, queries } = createEnv({ challengeLimitSuccess: false });
  env.GOOGLE_CLIENT_ID = "autu-test-client.apps.googleusercontent.com";
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.10" }
    }),
    env
  );

  assert.equal(response.status, 429);
  assert.equal((await json(response)).error, "rate_limited");
  assert.equal(queries.length, 0);
});

test("Google login requests are rate limited before parsing or verifying tokens", async () => {
  const { env, queries } = createEnv({ loginLimitSuccess: false });
  const response = await worker.fetch(
    makeRequest("/api/auth/google", {
      method: "POST",
      headers: {
        "CF-Connecting-IP": "203.0.113.11",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ challengeId: "x", nonce: "y", idToken: "z" })
    }),
    env
  );

  assert.equal(response.status, 429);
  assert.equal((await json(response)).error, "rate_limited");
  assert.equal(queries.length, 0);
});

test("production authentication fails closed when its rate-limit binding is missing", async () => {
  const { env, queries } = createEnv();
  env.RUNTIME_ENV = "production";
  delete env.AUTH_CHALLENGE_LIMITER;
  env.GOOGLE_CLIENT_ID = "autu-test-client.apps.googleusercontent.com";
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "POST",
      headers: { "CF-Connecting-IP": "203.0.113.12" }
    }),
    env
  );

  assert.equal(response.status, 503);
  assert.equal((await json(response)).error, "rate_limit_not_configured");
  assert.equal(queries.length, 0);
});

test("Google challenge is disabled until the server client ID is configured", async () => {
  const { env, queries } = createEnv();
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", { method: "POST" }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 503);
  assert.equal(body.error, "google_auth_not_configured");
  assert.equal(queries.length, 0);
});

test("protected routes reject requests without a bearer session", async () => {
  const { env, queries } = createEnv();
  const response = await worker.fetch(makeRequest("/api/admin/audit-logs"), env);
  const body = await json(response);

  assert.equal(response.status, 401);
  assert.equal(body.error, "authentication_required");
  assert.equal(queries.length, 0);
});

test("current customer receives only their own verified Google profile and latest sign-in data", async () => {
  const latestSignInRow = {
    metadata_json: JSON.stringify({
      deviceLabel: "Google Pixel",
      googleProfile: {
        givenName: "Customer",
        pictureUrl: "https://lh3.googleusercontent.com/a/customer-photo",
        locale: "vi"
      },
      deviceInfo: {
        manufacturer: "Google",
        model: "Pixel",
        androidVersion: "16"
      }
    }),
    created_at: 1795000000
  };
  const { env } = createEnv({ session: customerSession, latestSignInRow });
  const response = await worker.fetch(
    makeRequest("/api/auth/me", {
      headers: { Authorization: `Bearer ${"D".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.user.id, customerSession.user_id);
  assert.equal(body.user.googleSub, customerSession.google_sub);
  assert.equal(body.user.emailVerified, true);
  assert.equal(body.user.latestSignIn.googleProfile.pictureUrl, "https://lh3.googleusercontent.com/a/customer-photo");
  assert.equal(body.user.latestSignIn.deviceLabel, "Google Pixel");
  assert.equal(Object.hasOwn(body.user, "createdAt"), false);
});

test("customer account endpoint returns the signed-in account and empty license/device state", async () => {
  const { env } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/customer/account", {
      headers: { Authorization: `Bearer ${"D".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.account.id, customerSession.user_id);
  assert.equal(body.account.email, customerSession.email);
  assert.equal(Object.hasOwn(body.account, "googleSub"), false);
  assert.equal(Object.hasOwn(body.account, "emailVerified"), false);
  assert.equal(Object.hasOwn(body.account, "latestSignIn"), false);
  assert.equal(body.license, null);
  assert.deepEqual(body.devices, []);
  assert.equal(body.canRun, false);
  assert.equal(body.canRegisterDevice, false);
});

test("customer run eligibility requires an active license and at least one active device", async () => {
  const now = Math.floor(Date.now() / 1000);
  const accountLicense = {
    id: "license-123",
    plan_code: "MONTHLY",
    status: "ACTIVE",
    starts_at: now - 60,
    expires_at: now + 3600,
    max_devices: 1
  };

  const noDevice = createEnv({ session: customerSession, accountLicense });
  const noDeviceResponse = await worker.fetch(
    makeRequest("/api/customer/account", {
      headers: { Authorization: `Bearer ${"N".repeat(48)}` }
    }),
    noDevice.env
  );
  const noDeviceBody = await json(noDeviceResponse);
  assert.equal(noDeviceResponse.status, 200);
  assert.equal(noDeviceBody.canRun, false);
  assert.equal(noDeviceBody.canRegisterDevice, true);

  const activeDevice = {
    id: "device-123",
    label: "Android",
    status: "ACTIVE",
    registered_at: now - 30,
    last_seen_at: now - 10
  };
  const withDevice = createEnv({
    session: customerSession,
    accountLicense,
    accountDevices: [activeDevice]
  });
  const withDeviceResponse = await worker.fetch(
    makeRequest("/api/customer/account", {
      headers: { Authorization: `Bearer ${"A".repeat(48)}` }
    }),
    withDevice.env
  );
  const withDeviceBody = await json(withDeviceResponse);
  assert.equal(withDeviceResponse.status, 200);
  assert.equal(withDeviceBody.canRun, true);
  assert.equal(withDeviceBody.canRegisterDevice, false);
});

test("owner can read live customer, license and device directories", async () => {
  const { env } = createEnv({ session: ownerSession });
  const headers = { Authorization: `Bearer ${"E".repeat(48)}` };

  const customers = await worker.fetch(makeRequest("/api/admin/customers", { headers }), env);
  const licenses = await worker.fetch(makeRequest("/api/admin/licenses", { headers }), env);
  const devices = await worker.fetch(makeRequest("/api/admin/devices", { headers }), env);

  assert.equal(customers.status, 200);
  assert.deepEqual((await json(customers)).customers, []);
  assert.equal(licenses.status, 200);
  assert.deepEqual((await json(licenses)).licenses, []);
  assert.equal(devices.status, 200);
  assert.deepEqual((await json(devices)).devices, []);
});

test("non-owner cannot read customer license administration", async () => {
  const developer = { ...ownerSession, role: "DEVELOPER", user_id: "developer-user" };
  const { env } = createEnv({ session: developer });
  const response = await worker.fetch(
    makeRequest("/api/admin/licenses", {
      headers: { Authorization: `Bearer ${"F".repeat(48)}` }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
});


test("owner can suspend a customer and revoke its existing sessions", async () => {
  const { env } = createEnv({ session: ownerSession });
  const statementsSeen = [];
  const originalPrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = originalPrepare(sql);
    if (sql.includes("SELECT id, role, status FROM users WHERE id = ?")) {
      statement.first = async () => ({ id: "12345678-1234-1234-1234-123456789012", role: "CUSTOMER", status: "ACTIVE" });
    }
    return statement;
  };
  env.DB.batch = async statements => {
    statementsSeen.push(...statements.map(item => item.sql));
    return statements.map(() => ({ meta: { changes: 1 } }));
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/customers/12345678-1234-1234-1234-123456789012", {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${"E".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "SUSPENDED" })
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.status, "SUSPENDED");
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE users SET status")));
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE sessions SET revoked_at")));
  assert.equal(statementsSeen.some(sql => sql.includes("UPDATE licenses")), false);
  assert.equal(statementsSeen.some(sql => sql.includes("UPDATE devices")), false);
});

test("revoking a customer revokes its licenses, devices and sessions", async () => {
  const { env } = createEnv({ session: ownerSession });
  const statementsSeen = [];
  const originalPrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = originalPrepare(sql);
    if (sql.includes("SELECT id, role, status FROM users WHERE id = ?")) {
      statement.first = async () => ({ id: "12345678-1234-1234-1234-123456789012", role: "CUSTOMER", status: "ACTIVE" });
    }
    return statement;
  };
  env.DB.batch = async statements => {
    statementsSeen.push(...statements.map(item => item.sql));
    return statements.map(() => ({ meta: { changes: 1 } }));
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/customers/12345678-1234-1234-1234-123456789012", {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${"E".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "REVOKED" })
    }),
    env
  );

  assert.equal(response.status, 200);
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE users SET status")));
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE sessions SET revoked_at")));
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE licenses SET status = 'REVOKED'")));
  assert.ok(statementsSeen.some(sql => sql.includes("UPDATE devices SET status = 'REVOKED'")));
});

test("non-owner cannot suspend a customer", async () => {
  const customer = { ...customerSession, user_id: "customer-user" };
  const { env, queries } = createEnv({ session: customer });
  const response = await worker.fetch(
    makeRequest("/api/admin/customers/12345678-1234-1234-1234-123456789012", {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${"D".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "SUSPENDED" })
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
  assert.equal(queries.some(sql => sql.includes("SELECT id, role, status FROM users")), false);
});

test("device enrollment challenge requires an active customer license", async () => {
  const { env } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/customer/devices/challenge", {
      method: "POST",
      headers: { Authorization: `Bearer ${"G".repeat(48)}` }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "active_license_required");
});

test("device registration rejects unsigned or missing one-time proof", async () => {
  const { env } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/customer/devices", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"G".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ publicKeySpki: "A".repeat(500), label: "My Android" })
    }),
    env
  );

  assert.equal(response.status, 400);
  assert.equal((await json(response)).error, "invalid_device_proof");
});


test("device enrollment accepts a valid RSA-3072 proof of private-key ownership", async () => {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 3072,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256"
    },
    true,
    ["sign", "verify"]
  );
  const publicKeyBytes = new Uint8Array(await crypto.subtle.exportKey("spki", keyPair.publicKey));
  const publicKeySpki = btoa(String.fromCharCode(...publicKeyBytes));
  const challengeId = crypto.randomUUID();
  const nonceBytes = crypto.getRandomValues(new Uint8Array(32));
  const nonce = btoa(String.fromCharCode(...nonceBytes)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const nonceDigest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(nonce));
  const nonceHash = Array.from(new Uint8Array(nonceDigest), value => value.toString(16).padStart(2, "0")).join("");
  const signatureBytes = new Uint8Array(
    await crypto.subtle.sign(
      { name: "RSASSA-PKCS1-v1_5" },
      keyPair.privateKey,
      new TextEncoder().encode(challengeId + "." + nonce)
    )
  );
  const signature = btoa(String.fromCharCode(...signatureBytes));
  const now = Math.floor(Date.now() / 1000);
  const { env } = createDeviceRegistrationEnv({
    challenge: { id: challengeId, nonce_hash: nonceHash, expires_at: now + 300, consumed_at: null },
    license: { id: "license-1", user_id: customerSession.user_id, max_devices: 2, status: "ACTIVE", starts_at: now - 60, expires_at: now + 86400 }
  });

  const response = await worker.fetch(
    makeRequest("/api/customer/devices", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"J".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        publicKeySpki,
        challengeId,
        nonce,
        signature,
        label: "My Android",
        manufacturer: "Test",
        model: "Phone",
        androidVersion: "16",
        appVersion: "0.1.0"
      })
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 201);
  assert.equal(body.registered, true);
  assert.equal(body.device.status, "ACTIVE");
  assert.equal(body.device.label, "My Android");
  assert.equal(JSON.stringify(body).includes("privateKey"), false);
});

test("revoking a license keeps already-enrolled devices registered", async () => {
  const { env } = createEnv({ session: ownerSession });
  const statements = [];
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    if (sql.includes("FROM licenses WHERE id = ?")) {
      statement.first = async () => ({
        id: "123e4567-e89b-42d3-a456-426614174000",
        user_id: customerSession.user_id,
        plan_code: "MONTHLY",
        status: "ACTIVE",
        starts_at: Math.floor(Date.now() / 1000) - 60,
        expires_at: Math.floor(Date.now() / 1000) + 86400,
        max_devices: 1
      });
    }
    const originalRun = statement.run.bind(statement);
    statement.run = async () => {
      statements.push(sql);
      return originalRun();
    };
    return statement;
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/licenses/123e4567-e89b-42d3-a456-426614174000", {
      method: "PATCH",
      headers: {
        Authorization: "Bearer " + "E".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "REVOKED" })
    }),
    env
  );

  assert.equal(response.status, 200);
  assert.equal((await json(response)).status, "REVOKED");
  assert.equal(statements.some(sql => sql.includes("UPDATE devices")), false);
});

test("customer self-removal records a reversible customer-removal reason", async () => {
  const { env } = createEnv({ session: customerSession });
  const statements = [];
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    const originalRun = statement.run.bind(statement);
    statement.run = async () => {
      statements.push(sql);
      return originalRun();
    };
    return statement;
  };

  const response = await worker.fetch(
    makeRequest("/api/customer/devices/123e4567-e89b-42d3-a456-426614174005", {
      method: "DELETE",
      headers: { Authorization: "Bearer " + "D".repeat(48) }
    }),
    env
  );

  assert.equal(response.status, 200);
  assert.equal((await json(response)).removed, true);
  assert.ok(statements.some(sql =>
    sql.includes("revoked_reason = 'CUSTOMER_REMOVED'") && sql.includes("status = 'REVOKED'")
  ));
});

for (const revokedReason of ["CUSTOMER_REMOVED", "ACCOUNT_REVOKED"]) {
test(`customer can re-enroll a device revoked with ${revokedReason}`, async () => {
  const proof = await makeSignedDeviceEnrollment();
  const now = Math.floor(Date.now() / 1000);
  const { env, operations } = createDeviceRegistrationEnv({
    challenge: {
      id: proof.challengeId,
      nonce_hash: proof.nonceHash,
      expires_at: now + 300,
      consumed_at: null
    },
    license: {
      id: "license-1",
      user_id: customerSession.user_id,
      max_devices: 2,
      status: "ACTIVE",
      starts_at: now - 60,
      expires_at: now + 86400
    },
    existingDevice: {
      id: "device-previous",
      status: "REVOKED",
      revoked_reason: revokedReason
    }
  });

  const response = await worker.fetch(
    makeRequest("/api/customer/devices", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "J".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        publicKeySpki: proof.publicKeySpki,
        challengeId: proof.challengeId,
        nonce: proof.nonce,
        signature: proof.signature,
        label: "My Android",
        manufacturer: "Test",
        model: "Phone",
        androidVersion: "16",
        appVersion: "0.1.0"
      })
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.registered, true);
  assert.equal(body.reactivated, true);
  assert.equal(body.device.id, "device-previous");
  assert.ok(operations.some(operation =>
    operation.sql.includes("revoked_reason IN ('CUSTOMER_REMOVED', 'ACCOUNT_REVOKED')") &&
    operation.sql.includes("status = 'ACTIVE'")
  ));
});
}

test("customer cannot self-reactivate a device revoked by the owner", async () => {
  const proof = await makeSignedDeviceEnrollment();
  const now = Math.floor(Date.now() / 1000);
  const { env } = createDeviceRegistrationEnv({
    challenge: {
      id: proof.challengeId,
      nonce_hash: proof.nonceHash,
      expires_at: now + 300,
      consumed_at: null
    },
    license: {
      id: "license-1",
      user_id: customerSession.user_id,
      max_devices: 2,
      status: "ACTIVE",
      starts_at: now - 60,
      expires_at: now + 86400
    },
    existingDevice: {
      id: "device-blocked",
      status: "REVOKED",
      revoked_reason: "OWNER_REVOKED"
    }
  });

  const response = await worker.fetch(
    makeRequest("/api/customer/devices", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "K".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        publicKeySpki: proof.publicKeySpki,
        challengeId: proof.challengeId,
        nonce: proof.nonce,
        signature: proof.signature,
        label: "My Android",
        manufacturer: "Test",
        model: "Phone",
        androidVersion: "16",
        appVersion: "0.1.0"
      })
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "device_revoked");
});

test("owner can restore a revoked device after the account and license are active", async () => {
  const { env } = createEnv({ session: ownerSession });
  const statements = [];
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    if (sql.includes("SELECT id, user_id, status, revoked_reason FROM devices WHERE id = ?")) {
      statement.first = async () => ({
        id: "123e4567-e89b-42d3-a456-426614174002",
        user_id: customerSession.user_id,
        status: "REVOKED",
        revoked_reason: "LEGACY_REVOKED"
      });
    } else if (sql.includes("SELECT status FROM users WHERE id = ? AND role = 'CUSTOMER'")) {
      statement.first = async () => ({ status: "ACTIVE" });
    } else if (sql.includes("FROM licenses") && sql.includes("status = 'ACTIVE'")) {
      statement.first = async () => ({
        id: "license-active",
        user_id: customerSession.user_id,
        max_devices: 2,
        status: "ACTIVE",
        starts_at: Math.floor(Date.now() / 1000) - 60,
        expires_at: Math.floor(Date.now() / 1000) + 86400
      });
    }
    const originalRun = statement.run.bind(statement);
    statement.run = async () => {
      statements.push(sql);
      return originalRun();
    };
    return statement;
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/devices/123e4567-e89b-42d3-a456-426614174002", {
      method: "PATCH",
      headers: {
        Authorization: "Bearer " + "E".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "ACTIVE" })
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.reactivated, true);
  assert.ok(statements.some(sql => sql.includes("revoked_at = NULL") && sql.includes("COUNT(*)")));
  assert.ok(statements.some(sql => sql.includes("INSERT INTO audit_logs")));
});

test("owner cannot restore a device when the customer has no active license", async () => {
  const { env } = createEnv({ session: ownerSession });
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    if (sql.includes("SELECT id, user_id, status, revoked_reason FROM devices WHERE id = ?")) {
      statement.first = async () => ({
        id: "123e4567-e89b-42d3-a456-426614174003",
        user_id: customerSession.user_id,
        status: "REVOKED",
        revoked_reason: "ACCOUNT_REVOKED"
      });
    } else if (sql.includes("SELECT status FROM users WHERE id = ? AND role = 'CUSTOMER'")) {
      statement.first = async () => ({ status: "ACTIVE" });
    } else if (sql.includes("FROM licenses") && sql.includes("status = 'ACTIVE'")) {
      statement.first = async () => null;
    }
    return statement;
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/devices/123e4567-e89b-42d3-a456-426614174003", {
      method: "PATCH",
      headers: {
        Authorization: "Bearer " + "E".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ status: "ACTIVE" })
    }),
    env
  );

  assert.equal(response.status, 409);
  assert.equal((await json(response)).error, "active_license_required");
});

test("owner cannot issue a license with fewer device slots than currently active devices", async () => {
  const { env, queries } = createEnv({ session: ownerSession });
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    if (sql.includes("SELECT id, email, role, status FROM users WHERE id = ?")) {
      statement.first = async () => ({
        id: "123e4567-e89b-42d3-a456-426614174004",
        email: customerSession.email,
        role: "CUSTOMER",
        status: "ACTIVE"
      });
    } else if (sql.includes("SELECT COUNT(*) AS active_count FROM devices")) {
      statement.first = async () => ({ active_count: 2 });
    }
    return statement;
  };

  const response = await worker.fetch(
    makeRequest("/api/admin/licenses", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "E".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        userId: "123e4567-e89b-42d3-a456-426614174004",
        planCode: "MONTHLY",
        expiresAt: Math.floor(Date.now() / 1000) + 30 * 86400,
        maxDevices: 1
      })
    }),
    env
  );

  assert.equal(response.status, 409);
  assert.equal((await json(response)).error, "device_limit_below_existing");
  assert.equal(queries.some(sql => sql.includes("INSERT INTO licenses")), false);
});

test("owner cannot create a license for an invalid customer identifier", async () => {
  const { env } = createEnv({ session: ownerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/licenses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"H".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        userId: "not-a-uuid",
        planCode: "MONTHLY",
        expiresAt: Math.floor(Date.now() / 1000) + 30 * 86400,
        maxDevices: 1
      })
    }),
    env
  );

  assert.equal(response.status, 400);
  assert.equal((await json(response)).error, "invalid_customer_id");
});


test("owner can inspect original source and authenticated encrypted envelope", async () => {
  const masterKey = Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url");
  const { env } = createEnv({ session: ownerSession });
  env.PACKAGE_MASTER_KEY_V1 = masterKey;
  const packageId = "123e4567-e89b-42d3-a456-426614174000";
  const source = "ĐẶT soLan = 100\\nLẶP(soLan) {\\n  CHẠM(10, 20)\\n  CHỜ(5)\\n}";
  const sourceBytes = new TextEncoder().encode(source);
  const compressed = new Uint8Array(gzipSync(sourceBytes, { level: 9 }));
  const dataKeyBytes = crypto.getRandomValues(new Uint8Array(32));
  const contentIv = crypto.getRandomValues(new Uint8Array(12));
  const keyIv = crypto.getRandomValues(new Uint8Array(12));
  const context = "AUTU-LIO|PACKAGE|" + packageId + "|v1";
  const master = await crypto.subtle.importKey("raw", Buffer.from(masterKey, "base64url"), {name:"AES-GCM"}, false, ["encrypt"]);
  const contentKey = await crypto.subtle.importKey("raw", dataKeyBytes, {name:"AES-GCM"}, false, ["encrypt"]);
  const ciphertext = await crypto.subtle.encrypt(
    {name:"AES-GCM",iv:contentIv,additionalData:new TextEncoder().encode(context),tagLength:128},
    contentKey,
    compressed
  );
  const encryptedDataKey = await crypto.subtle.encrypt(
    {name:"AES-GCM",iv:keyIv,additionalData:new TextEncoder().encode(context+"|DATA_KEY"),tagLength:128},
    master,
    dataKeyBytes
  );
  const sourceSha256 = Buffer.from(await crypto.subtle.digest("SHA-256", sourceBytes)).toString("hex");
  const packageRow = {
    version: 1,
    language_version: "1.22",
    ciphertext: "GZ1." + Buffer.from(ciphertext).toString("base64url"),
    content_iv: Buffer.from(contentIv).toString("base64url"),
    encrypted_data_key: Buffer.from(encryptedDataKey).toString("base64url"),
    key_iv: Buffer.from(keyIv).toString("base64url"),
    key_version: "V1",
    source_sha256: sourceSha256
  };
  const basePrepare = env.DB.prepare.bind(env.DB);
  env.DB.prepare = sql => {
    const statement = basePrepare(sql);
    if (sql.includes("FROM script_packages WHERE id = ?")) {
      statement.first = async () => ({
        id: packageId,
        name: "Owner preview",
        description: "",
        status: "ACTIVE",
        latest_version: 1
      });
    }
    if (sql.includes("FROM script_package_versions WHERE package_id = ? AND version = ?")) {
      statement.first = async () => packageRow;
    }
    return statement;
  };
  const response = await worker.fetch(
    makeRequest("/api/admin/packages/" + packageId + "/source", {
      headers: { Authorization: "Bearer " + "E".repeat(48) }
    }),
    env
  );
  const body = await json(response);
  assert.equal(response.status, 200);
  assert.equal(body.package.source, source);
  assert.equal(body.package.encrypted.algorithm, "AES-256-GCM");
  assert.equal(body.package.encrypted.keyWrapping, "RSA-3072-OAEP-SHA256 (wrapped per registered device at download)");
  assert.equal(body.package.encrypted.compression, "GZIP");
  assert.equal(body.package.encrypted.ciphertext.startsWith("GZ1."), true);
  assert.equal(JSON.stringify(body.package.encrypted).includes(source), false);
});

test("customer cannot inspect owner-only package source preview", async () => {
  const { env, queries } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/packages/123e4567-e89b-42d3-a456-426614174000/source", {
      headers: { Authorization: "Bearer " + "P".repeat(48) }
    }),
    env
  );
  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
  assert.equal(queries.some(sql => sql.includes("FROM script_package_versions")), false);
});

test("malformed Google sign-in JSON is rejected", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(
    makeRequest("/api/auth/google", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "CF-Connecting-IP": "203.0.113.18"
      },
      body: "{"
    }),
    env
  );

  assert.equal(response.status, 400);
  assert.equal((await json(response)).error, "invalid_json");
});

test("oversized Google sign-in request is rejected before parsing", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(
    makeRequest("/api/auth/google", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "CF-Connecting-IP": "203.0.113.19"
      },
      body: " ".repeat(24_001)
    }),
    env
  );

  assert.equal(response.status, 413);
  assert.equal((await json(response)).error, "body_too_large");
});

test("CORS preflight rejects an unlisted web origin", async () => {
  const { env } = createEnv({ allowedOrigins: "https://trusted.example" });
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "OPTIONS",
      headers: { Origin: "https://untrusted.example" }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "origin_not_allowed");
});

test("CORS preflight allows an explicitly configured origin", async () => {
  const { env } = createEnv({ allowedOrigins: "https://trusted.example" });
  const response = await worker.fetch(
    makeRequest("/api/auth/challenge", {
      method: "OPTIONS",
      headers: { Origin: "https://trusted.example" }
    }),
    env
  );

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Access-Control-Allow-Origin"), "https://trusted.example");
});

test("customers cannot publish owner script packages", async () => {
  const { env } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "K".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Restricted package",
        description: "",
        languageVersion: "0.3",
        source: "CHẠM(10, 20)"
      })
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
});

test("package publishing rejects unsupported AUTU language versions", async () => {
  const { env } = createEnv({ session: ownerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "U".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Unsupported language",
        description: "",
        languageVersion: "9.9",
        source: "CHẠM(10, 20)"
      })
    }),
    env
  );

  assert.equal(response.status, 400);
  assert.equal((await json(response)).error, "unsupported_language_version");
});

test("package publishing fails closed when the versioned master encryption key is absent", async () => {
  const { env } = createEnv({ session: ownerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "L".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Secure package",
        description: "Encrypted package",
        languageVersion: "0.3",
        source: "CHẠM(10, 20)"
      })
    }),
    env
  );

  assert.equal(response.status, 503);
  assert.equal((await json(response)).error, "package_encryption_not_configured");
});

test("package publishing stores AES-GCM ciphertext and master-key version, not plain source", async () => {
  const { env } = createEnv({ session: ownerSession });
  env.PACKAGE_MASTER_KEY_V1 = Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url");
  let statements = [];
  env.DB.batch = async batch => {
    statements = batch;
    return batch.map(() => ({ meta: { changes: 1 } }));
  };
  const source = "ĐẶT soLan = 3\nLẶP(soLan) { CHẠM(10, 20) }";
  const response = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "M".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Encrypted package",
        description: "Package test",
        languageVersion: "1.22",
        source
      })
    }),
    env
  );
  const body = await json(response);
  const versionInsert = statements.find(statement => statement.sql.includes("INSERT INTO script_package_versions"));

  assert.equal(response.status, 201);
  assert.equal(body.published, true);
  assert.equal(body.package.languageVersion, "1.22");
  assert.ok(versionInsert);
  assert.equal(versionInsert.values[3], "1.22");
  assert.notEqual(versionInsert.values[4], source);
  assert.ok(versionInsert.values[4].length > source.length);
  assert.equal(versionInsert.values[8], "V1");
  assert.equal(versionInsert.values.includes(source), false);
});

test("customer package download unwraps the data key with Android Keystore-compatible RSA-OAEP SHA-1", async () => {
  const masterBytes = crypto.getRandomValues(new Uint8Array(32));
  const masterKey = Buffer.from(masterBytes).toString("base64url");
  const { env: ownerEnv } = createEnv({ session: ownerSession });
  ownerEnv.PACKAGE_MASTER_KEY_V1 = masterKey;
  let statements = [];
  ownerEnv.DB.batch = async batch => {
    statements = batch;
    return batch.map(() => ({ meta: { changes: 1 } }));
  };
  const source = "CHỜ_ĐẾN_KHI(CÓ_MẠNG(), 5000, 250)\nCHẠM(5, 6)";
  const published = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + "N".repeat(48),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Download test",
        description: "Round-trip",
        languageVersion: "0.3",
        source
      })
    }),
    ownerEnv
  );
  assert.equal(published.status, 201);
  const packageInfo = (await json(published)).package;
  const versionInsert = statements.find(statement => statement.sql.includes("INSERT INTO script_package_versions"));
  const values = versionInsert.values;
  const deviceKeys = await crypto.subtle.generateKey(
    {
      name: "RSA-OAEP",
      modulusLength: 3072,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-1"
    },
    true,
    ["encrypt", "decrypt"]
  );
  const publicKeyBytes = new Uint8Array(await crypto.subtle.exportKey("spki", deviceKeys.publicKey));
  const publicKeySpki = Buffer.from(publicKeyBytes).toString("base64url");
  const fingerprint = Buffer.from(await crypto.subtle.digest("SHA-256", publicKeyBytes)).toString("hex");
  const packageRow = {
    id: packageInfo.id,
    name: packageInfo.name,
    description: packageInfo.description,
    status: "ACTIVE",
    version: values[2],
    language_version: values[3],
    ciphertext: values[4],
    content_iv: values[5],
    encrypted_data_key: values[6],
    key_iv: values[7],
    key_version: values[8],
    source_sha256: values[9]
  };
  const now = Math.floor(Date.now() / 1000);
  const license = {
    id: "license-1",
    user_id: customerSession.user_id,
    plan_code: "MONTHLY",
    status: "ACTIVE",
    starts_at: now - 60,
    expires_at: now + 86400,
    max_devices: 1
  };
  const queries = [];
  const customerEnv = {
    PACKAGE_MASTER_KEY_V1: masterKey,
    DB: {
      prepare(sql) {
        queries.push(sql);
        return {
          values: [],
          bind(...bound) {
            this.values = bound;
            return this;
          },
          async first() {
            if (sql.includes("FROM sessions s JOIN users u")) return customerSession;
            if (sql.includes("FROM licenses WHERE user_id = ? AND status = 'ACTIVE'")) return license;
            if (sql.includes("FROM devices WHERE user_id = ? AND public_key_fingerprint = ?")) {
              return { id: "device-1", public_key_spki: publicKeySpki };
            }
            if (sql.includes("FROM script_packages p JOIN script_package_versions")) return packageRow;
            return null;
          },
          async all() { return { results: [] }; },
          async run() { return { meta: { changes: 1 } }; }
        };
      },
      async batch() { return []; }
    }
  };
  const downloaded = await worker.fetch(
    makeRequest("/api/customer/packages/" + packageInfo.id + "/download?deviceFingerprint=" + fingerprint + "&keyWrapping=RSA-OAEP-SHA1&compression=GZIP", {
      headers: { Authorization: "Bearer " + "O".repeat(48) }
    }),
    customerEnv
  );
  const downloadBody = await json(downloaded);
  assert.equal(downloaded.status, 200);

  const packageEnvelope = downloadBody.package;
  const wrappedBytes = Buffer.from(packageEnvelope.wrappedKey, "base64url");
  const dataKeyBytes = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, deviceKeys.privateKey, wrappedBytes);
  const dataKey = await crypto.subtle.importKey("raw", dataKeyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
  const context = new TextEncoder().encode("AUTU-LIO|PACKAGE|" + packageInfo.id + "|v1");
  const plainBytes = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: Buffer.from(packageEnvelope.contentIv, "base64url"),
      additionalData: context,
      tagLength: 128
    },
    dataKey,
    Buffer.from(packageEnvelope.ciphertext.startsWith("GZ1.") ? packageEnvelope.ciphertext.slice(4) : packageEnvelope.ciphertext, "base64url")
  );
  const decryptedPayload = Buffer.from(plainBytes);
  const sourcePayload = packageEnvelope.ciphertext.startsWith("GZ1.")
    ? gunzipSync(decryptedPayload)
    : decryptedPayload;
  const decryptedSource = new TextDecoder().decode(sourcePayload);
  const digest = Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(decryptedSource))).toString("hex");

  assert.equal(decryptedSource, source);
  assert.equal(digest, packageEnvelope.sourceSha256);
  assert.equal(packageEnvelope.version, 1);
  assert.equal(queries.some(sql => sql.includes("public_key_spki")), true);
});



test("legacy APK download receives raw AES-GCM payload and RSA-OAEP SHA-1 wrapping", async () => {
  const masterKey = Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url");
  const { env: ownerEnv } = createEnv({ session: ownerSession });
  ownerEnv.PACKAGE_MASTER_KEY_V1 = masterKey;
  let inserts = [];
  ownerEnv.DB.batch = async batch => {
    inserts = batch;
    return batch.map(() => ({ meta: { changes: 1 } }));
  };
  const source = "ĐẶT legacy = 1\\nCHẠM(10, 20)";
  const published = await worker.fetch(
    makeRequest("/api/admin/packages", {
      method: "POST",
      headers: { Authorization: "Bearer " + "V".repeat(48), "Content-Type": "application/json" },
      body: JSON.stringify({ name: "Legacy fallback", description: "", languageVersion: "1.22", source })
    }),
    ownerEnv
  );
  assert.equal(published.status, 201);
  const packageInfo = (await json(published)).package;
  const values = inserts.find(statement => statement.sql.includes("INSERT INTO script_package_versions")).values;
  const deviceKeys = await crypto.subtle.generateKey(
    { name: "RSA-OAEP", modulusLength: 3072, publicExponent: new Uint8Array([1,0,1]), hash: "SHA-1" },
    true,
    ["encrypt", "decrypt"]
  );
  const publicKeyBytes = new Uint8Array(await crypto.subtle.exportKey("spki", deviceKeys.publicKey));
  const publicKeySpki = Buffer.from(publicKeyBytes).toString("base64url");
  const fingerprint = Buffer.from(await crypto.subtle.digest("SHA-256", publicKeyBytes)).toString("hex");
  const now = Math.floor(Date.now() / 1000);
  const license = { id: "legacy-license", user_id: customerSession.user_id, plan_code: "MONTHLY", status: "ACTIVE", starts_at: now - 60, expires_at: now + 86400, max_devices: 1 };
  const item = {
    id: packageInfo.id, name: packageInfo.name, description: "", status: "ACTIVE",
    version: values[2], language_version: values[3], ciphertext: values[4], content_iv: values[5],
    encrypted_data_key: values[6], key_iv: values[7], key_version: values[8], source_sha256: values[9]
  };
  const legacyEnv = {
    PACKAGE_MASTER_KEY_V1: masterKey,
    DB: {
      prepare(sql) {
        return {
          bind() { return this; },
          async first() {
            if (sql.includes("FROM sessions s JOIN users u")) return customerSession;
            if (sql.includes("FROM licenses WHERE user_id = ? AND status = 'ACTIVE'")) return license;
            if (sql.includes("FROM devices WHERE user_id = ? AND public_key_fingerprint = ?")) return { id: "legacy-device", public_key_spki: publicKeySpki };
            if (sql.includes("FROM script_packages p JOIN script_package_versions")) return item;
            return null;
          },
          async run() { return { meta: { changes: 1 } }; },
          async all() { return { results: [] }; }
        };
      }
    }
  };
  const downloaded = await worker.fetch(
    makeRequest("/api/customer/packages/" + packageInfo.id + "/download?deviceFingerprint=" + fingerprint, {
      headers: { Authorization: "Bearer " + "V".repeat(48) }
    }),
    legacyEnv
  );
  const body = await json(downloaded);
  assert.equal(downloaded.status, 200);
  assert.equal(body.package.keyWrapping, "RSA-OAEP-SHA1");
  assert.equal(body.package.ciphertext.startsWith("GZ1."), false);
  const wrappedDataKey = await crypto.subtle.decrypt(
    { name: "RSA-OAEP" }, deviceKeys.privateKey, Buffer.from(body.package.wrappedKey, "base64url")
  );
  const contentKey = await crypto.subtle.importKey("raw", wrappedDataKey, { name: "AES-GCM" }, false, ["decrypt"]);
  const plain = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: Buffer.from(body.package.contentIv, "base64url"),
      additionalData: new TextEncoder().encode("AUTU-LIO|PACKAGE|" + packageInfo.id + "|v1"),
      tagLength: 128
    },
    contentKey,
    Buffer.from(body.package.ciphertext, "base64url")
  );
  assert.equal(new TextDecoder().decode(plain), source);
});

test("customer without an active license cannot list published AUTUs", async () => {
  const { env, queries } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/customer/packages?deviceFingerprint=" + "a".repeat(64), {
      headers: { Authorization: "Bearer " + "P".repeat(48) }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "active_license_required");
  assert.equal(queries.some(sql => sql.includes("FROM script_packages")), false);
});

test("customer without an active registered device cannot download a published AUTU", async () => {
  const now = Math.floor(Date.now() / 1000);
  const license = {
    id: "license-1",
    user_id: customerSession.user_id,
    plan_code: "MONTHLY",
    status: "ACTIVE",
    starts_at: now - 60,
    expires_at: now + 86400,
    max_devices: 1
  };
  const queries = [];
  const env = {
    DB: {
      prepare(sql) {
        queries.push(sql);
        return {
          bind() { return this; },
          async first() {
            if (sql.includes("FROM sessions s JOIN users u")) return customerSession;
            if (sql.includes("FROM licenses WHERE user_id = ? AND status = 'ACTIVE'")) return license;
            if (sql.includes("FROM devices WHERE user_id = ? AND public_key_fingerprint = ? AND status = 'ACTIVE'")) return null;
            return null;
          },
          async all() { return { results: [] }; },
          async run() { return { meta: { changes: 1 } }; }
        };
      },
      async batch() { return []; }
    }
  };
  const packageId = "123e4567-e89b-42d3-a456-426614174000";
  const response = await worker.fetch(
    makeRequest("/api/customer/packages/" + packageId + "/download?deviceFingerprint=" + "b".repeat(64), {
      headers: { Authorization: "Bearer " + "Q".repeat(48) }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "active_device_required");
  assert.equal(queries.some(sql => sql.includes("FROM script_packages")), false);
});



test("owner can read newest audit logs with parsed metadata and actor details", async () => {
  const auditRows = [{
    id: "audit-1",
    action: "license.created",
    target_type: "license",
    target_id: "license-1",
    metadata_json: JSON.stringify({ planCode: "MONTHLY", maxDevices: 1 }),
    created_at: 1794770000,
    actor_email: "owner@example.com",
    actor_display_name: "Owner"
  }];
  const { env, queries } = createEnv({ session: ownerSession, auditRows });
  const response = await worker.fetch(
    makeRequest("/api/admin/audit-logs?limit=25", {
      headers: { Authorization: "Bearer " + "R".repeat(48) }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.limit, 25);
  assert.equal(body.logs.length, 1);
  assert.equal(body.logs[0].action, "license.created");
  assert.equal(body.logs[0].actorEmail, "owner@example.com");
  assert.deepEqual(body.logs[0].metadata, { planCode: "MONTHLY", maxDevices: 1 });
  assert.equal(queries.some(sql => sql.includes("FROM audit_logs l LEFT JOIN users")), true);
});

test("customers cannot read owner audit logs", async () => {
  const { env, queries } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/audit-logs", {
      headers: { Authorization: "Bearer " + "S".repeat(48) }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
  assert.equal(queries.some(sql => sql.includes("FROM audit_logs")), false);
});

test("audit log endpoint rejects an invalid or excessive limit", async () => {
  const { env, queries } = createEnv({ session: ownerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/audit-logs?limit=101", {
      headers: { Authorization: "Bearer " + "T".repeat(48) }
    }),
    env
  );

  assert.equal(response.status, 400);
  assert.equal((await json(response)).error, "invalid_audit_limit");
  assert.equal(queries.some(sql => sql.includes("FROM audit_logs")), false);
});
