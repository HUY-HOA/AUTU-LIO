import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const customerSession = {
  session_id: "session-customer",
  user_id: "customer-user",
  expires_at: Math.floor(Date.now() / 1000) + 3600,
  email: "customer@example.com",
  display_name: "Customer",
  role: "CUSTOMER",
  status: "ACTIVE",
  email_verified: 1
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

function createEnv({ session = null, allowedOrigins = "*" } = {}) {
  const queries = [];
  const DB = {
    prepare(sql) {
      queries.push(sql);
      const statement = {
        bind(...values) {
          this.values = values;
          return this;
        },
        async first() {
          if (sql.includes("FROM sessions s JOIN users u")) return session;
          return null;
        },
        async all() {
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
  return { env: { DB, ALLOWED_ORIGINS: allowedOrigins }, queries };
}

function makeRequest(path, options = {}) {
  return new Request(`https://autu-lio.test${path}`, options);
}

async function json(response) {
  return response.json();
}

test("health endpoint reports configuration state without exposing secrets", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(makeRequest("/health"), env);
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.databaseConfigured, true);
  assert.equal(body.googleSignInConfigured, false);
  assert.equal(body.ownerBootstrapConfigured, false);
  assert.equal(JSON.stringify(body).includes("owner@example.com"), false);
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
  const response = await worker.fetch(makeRequest("/api/admin/collaborators"), env);
  const body = await json(response);

  assert.equal(response.status, 401);
  assert.equal(body.error, "authentication_required");
  assert.equal(queries.length, 0);
});

test("Developer cannot access the owner-only collaborator directory", async () => {
  const developer = { ...ownerSession, role: "DEVELOPER", user_id: "developer-user" };
  const { env } = createEnv({ session: developer });
  const response = await worker.fetch(
    makeRequest("/api/admin/collaborators", {
      headers: { Authorization: `Bearer ${"A".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 403);
  assert.equal(body.error, "insufficient_permissions");
});

test("Reviewer cannot access the owner-only collaborator directory", async () => {
  const reviewer = { ...ownerSession, role: "REVIEWER", user_id: "reviewer-user" };
  const { env } = createEnv({ session: reviewer });
  const response = await worker.fetch(
    makeRequest("/api/admin/collaborators", {
      headers: { Authorization: `Bearer ${"B".repeat(48)}` }
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "insufficient_permissions");
});

test("Owner can open the collaborator directory", async () => {
  const { env } = createEnv({ session: ownerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/collaborators", {
      headers: { Authorization: `Bearer ${"C".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.deepEqual(body.collaborators, []);
  assert.deepEqual(body.pendingInvitations, []);
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
  assert.equal(body.license, null);
  assert.deepEqual(body.devices, []);
  assert.equal(body.canRun, false);
  assert.equal(body.canRegisterDevice, false);
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

test("device registration rejects a valid public key until the account has an active license", async () => {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: "RSA-OAEP",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256"
    },
    true,
    ["encrypt", "decrypt"]
  );
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", keyPair.publicKey));
  let binary = "";
  for (const byte of spki) binary += String.fromCharCode(byte);
  const publicKeySpki = btoa(binary);

  const { env } = createEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/customer/devices", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"G".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ publicKeySpki, label: "My Android" })
    }),
    env
  );

  assert.equal(response.status, 403);
  assert.equal((await json(response)).error, "active_license_required");
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

test("malformed Google sign-in JSON is rejected", async () => {
  const { env } = createEnv();
  const response = await worker.fetch(
    makeRequest("/api/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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
      headers: { "Content-Type": "application/json" },
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
