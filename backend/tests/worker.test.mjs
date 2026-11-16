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

function createEnv({ session = null, allowedOrigins = "*", auditRows = [] } = {}) {
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
          return null;
        },
        async all() {
          if (sql.includes("FROM audit_logs")) return { results: auditRows };
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

function createDeviceRegistrationEnv({ challenge, license, session = customerSession }) {
  const queries = [];
  const DB = {
    prepare(sql) {
      queries.push(sql);
      return {
        values: [],
        bind(...values) {
          this.values = values;
          return this;
        },
        async first() {
          if (sql.includes("FROM sessions s JOIN users u")) return session;
          if (sql.includes("FROM device_enrollment_challenges")) return challenge;
          if (sql.includes("FROM licenses")) return license;
          if (sql.includes("SELECT id, status FROM devices")) return null;
          if (sql.includes("SELECT COUNT(*) AS active_count FROM devices")) return { active_count: 0 };
          return null;
        },
        async all() {
          return { results: [] };
        },
        async run() {
          return { meta: { changes: 1 } };
        }
      };
    },
    async batch() {
      return [];
    }
  };
  return { env: { DB }, queries };
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
        languageVersion: "0.3",
        source
      })
    }),
    env
  );
  const body = await json(response);
  const versionInsert = statements.find(statement => statement.sql.includes("INSERT INTO script_package_versions"));

  assert.equal(response.status, 201);
  assert.equal(body.published, true);
  assert.ok(versionInsert);
  assert.notEqual(versionInsert.values[4], source);
  assert.ok(versionInsert.values[4].length > source.length);
  assert.equal(versionInsert.values[8], "V1");
  assert.equal(versionInsert.values.includes(source), false);
});

test("customer package download decrypts the server envelope and wraps the data key to the registered RSA device", async () => {
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
    makeRequest("/api/customer/packages/" + packageInfo.id + "/download?deviceFingerprint=" + fingerprint, {
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
    Buffer.from(packageEnvelope.ciphertext, "base64url")
  );
  const decryptedSource = new TextDecoder().decode(plainBytes);
  const digest = Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(decryptedSource))).toString("hex");

  assert.equal(decryptedSource, source);
  assert.equal(digest, packageEnvelope.sourceSha256);
  assert.equal(packageEnvelope.version, 1);
  assert.equal(queries.some(sql => sql.includes("public_key_spki")), true);
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


const PROJECT_TEST_ID = "11111111-1111-4111-8111-111111111111";

function createProjectDraftEnv({ session = ownerSession } = {}) {
  const scripts = [];
  const submissions = [];
  const queries = [];
  const project = {
    id: PROJECT_TEST_ID,
    name: "Protected project",
    description: "",
    status: "ACTIVE",
    created_by: ownerSession.user_id,
    created_at: 1,
    updated_at: 1
  };
  const key = crypto.getRandomValues(new Uint8Array(32));
  const masterKey = Buffer.from(key).toString("base64url");
  key.fill(0);

  const DB = {
    prepare(sql) {
      queries.push(sql);
      const statement = {
        values: [],
        bind(...values) {
          this.values = values;
          return this;
        },
        async first() {
          if (sql.includes("FROM sessions s JOIN users u")) return session;
          if (sql.includes("FROM projects WHERE id = ?")) return project;
          if (sql.includes("FROM project_memberships")) return { allowed: 1 };
          if (sql.includes("FROM permission_overrides")) return null;
          if (sql.includes("COUNT(*) AS total") && sql.includes("FROM script_drafts")) {
            return { total: scripts.filter(item => item.source && !item.source_ciphertext).length };
          }
          if (sql.includes("COUNT(*) AS total") && sql.includes("FROM change_submissions")) {
            return { total: submissions.filter(item => item.submitted_snapshot && !item.snapshot_ciphertext).length };
          }
          if (sql.includes("FROM script_drafts WHERE id = ?")) {
            return scripts.find(item => item.id === this.values[0]) || null;
          }
          return null;
        },
        async all() {
          if (sql.includes("FROM audit_logs")) return { results: [] };
          if (sql.includes("FROM script_drafts") && sql.includes("WHERE source <> ''")) {
            return { results: scripts.filter(item => item.source && !item.source_ciphertext).map(item => ({ ...item })) };
          }
          if (sql.includes("FROM script_drafts")) {
            if (sql.includes("source_ciphertext")) {
              return { results: scripts.map(item => ({ ...item })) };
            }
            return {
              results: scripts.map(item => ({
                id: item.id,
                name: item.name,
                language_version: item.language_version,
                revision: item.revision,
                created_by: item.created_by,
                updated_by: item.updated_by,
                created_at: item.created_at,
                updated_at: item.updated_at
              }))
            };
          }
          if (sql.includes("FROM change_submissions") && sql.includes("WHERE submitted_snapshot <> ''")) {
            return { results: submissions.filter(item => item.submitted_snapshot && !item.snapshot_ciphertext).map(item => ({ ...item })) };
          }
          if (sql.includes("FROM change_submissions s JOIN users u")) {
            return {
              results: submissions.map(item => ({
                ...item,
                submitter_email: ownerSession.email
              }))
            };
          }
          return { results: [] };
        },
        async run() {
          if (sql.startsWith("INSERT INTO script_drafts")) {
            const [
              id, projectId, name, languageVersion, createdBy, updatedBy,
              createdAt, updatedAt, sourceCiphertext, sourceIv, sourceEncryptedDataKey, sourceKeyIv, sourceKeyVersion
            ] = this.values;
            scripts.push({
              id,
              project_id: projectId,
              name,
              source: "",
              language_version: languageVersion,
              revision: 1,
              created_by: createdBy,
              updated_by: updatedBy,
              created_at: createdAt,
              updated_at: updatedAt,
              source_ciphertext: sourceCiphertext,
              source_iv: sourceIv,
              source_encrypted_data_key: sourceEncryptedDataKey,
              source_key_iv: sourceKeyIv,
              source_key_version: sourceKeyVersion
            });
          } else if (sql.startsWith("INSERT INTO change_submissions")) {
            const [
              id, projectId, kind, itemId, revision, submittedBy,
              createdAt, snapshotCiphertext, snapshotIv, snapshotEncryptedDataKey, snapshotKeyIv, snapshotKeyVersion
            ] = this.values;
            submissions.push({
              id,
              project_id: projectId,
              kind,
              item_id: itemId,
              revision,
              submitted_snapshot: "",
              submitted_by: submittedBy,
              status: "PENDING",
              review_note: "",
              reviewed_by: null,
              created_at: createdAt,
              reviewed_at: null,
              snapshot_ciphertext: snapshotCiphertext,
              snapshot_iv: snapshotIv,
              snapshot_encrypted_data_key: snapshotEncryptedDataKey,
              snapshot_key_iv: snapshotKeyIv,
              snapshot_key_version: snapshotKeyVersion
            });
          } else if (sql.startsWith("UPDATE script_drafts SET source = ''")) {
            const [ciphertext, iv, encryptedDataKey, keyIv, keyVersion, id, projectId] = this.values;
            const draft = scripts.find(item => item.id === id && item.project_id === projectId);
            if (draft && draft.source && !draft.source_ciphertext) {
              draft.source = "";
              draft.source_ciphertext = ciphertext;
              draft.source_iv = iv;
              draft.source_encrypted_data_key = encryptedDataKey;
              draft.source_key_iv = keyIv;
              draft.source_key_version = keyVersion;
            }
          } else if (sql.startsWith("UPDATE change_submissions SET submitted_snapshot = ''")) {
            const [ciphertext, iv, encryptedDataKey, keyIv, keyVersion, id, projectId] = this.values;
            const submission = submissions.find(item => item.id === id && item.project_id === projectId);
            if (submission && submission.submitted_snapshot && !submission.snapshot_ciphertext) {
              submission.submitted_snapshot = "";
              submission.snapshot_ciphertext = ciphertext;
              submission.snapshot_iv = iv;
              submission.snapshot_encrypted_data_key = encryptedDataKey;
              submission.snapshot_key_iv = keyIv;
              submission.snapshot_key_version = keyVersion;
            }
          }
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
      PROJECT_DRAFT_MASTER_KEY_VERSION: "V1",
      PROJECT_DRAFT_MASTER_KEY_V1: masterKey
    },
    session,
    scripts,
    submissions,
    queries
  };
}
test("shared project script source is encrypted in D1 and returned only after permission checks", async () => {
  const { env, scripts } = createProjectDraftEnv();
  const headers = {
    Authorization: `Bearer ${"P".repeat(48)}`,
    "Content-Type": "application/json"
  };
  const source = "tap(321, 654)\nwait(500)";
  const created = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "Protected script", source, languageVersion: "0.1" })
    }),
    env
  );
  const createdBody = await json(created);

  assert.equal(created.status, 201);
  assert.equal(createdBody.script.source, source);
  assert.equal(scripts.length, 1);
  assert.equal(scripts[0].source, "");
  assert.ok(scripts[0].source_ciphertext);
  assert.ok(scripts[0].source_encrypted_data_key);
  assert.ok(scripts[0].source_key_iv);
  assert.notEqual(scripts[0].source_ciphertext, source);
  assert.equal(scripts[0].source_ciphertext.includes("tap(321"), false);

  const listed = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, { headers }),
    env
  );
  const listedBody = await json(listed);

  assert.equal(listed.status, 200);
  assert.equal(listedBody.scripts[0].source, source);
  assert.equal(Object.hasOwn(listedBody.scripts[0], "source_ciphertext"), false);
});

test("tampered encrypted project source fails integrity verification", async () => {
  const { env, scripts } = createProjectDraftEnv();
  const headers = {
    Authorization: `Bearer ${"Q".repeat(48)}`,
    "Content-Type": "application/json"
  };
  const created = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "Tamper test", source: "tap(1, 2)", languageVersion: "0.1" })
    }),
    env
  );
  assert.equal(created.status, 201);
  scripts[0].source_ciphertext = (scripts[0].source_ciphertext[0] === "A" ? "B" : "A") + scripts[0].source_ciphertext.slice(1);

  const listed = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, { headers }),
    env
  );
  const body = await json(listed);

  assert.equal(listed.status, 500);
  assert.equal(body.error, "project_data_integrity_failed");
});

test("customer cannot enumerate or read shared project script drafts", async () => {
  const { env } = createProjectDraftEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      headers: { Authorization: `Bearer ${"R".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 404);
  assert.equal(body.error, "project_not_found");
  assert.equal(JSON.stringify(body).includes("source"), false);
});

test("reviewer can list script metadata without receiving draft source", async () => {
  const reviewer = { ...ownerSession, role: "REVIEWER", user_id: "reviewer-user" };
  const { env, scripts, queries } = createProjectDraftEnv({ session: reviewer });
  const response = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      headers: { Authorization: `Bearer ${"S".repeat(48)}` }
    }),
    env
  );

  assert.equal(response.status, 200);
  const body = await json(response);
  assert.equal(JSON.stringify(body).includes("source"), false);
  assert.equal(queries.some(sql => sql.includes("source_ciphertext")), false);
  assert.equal(scripts.length, 0);
});

test("customer cannot trigger owner-only legacy project encryption migration", async () => {
  const { env } = createProjectDraftEnv({ session: customerSession });
  const response = await worker.fetch(
    makeRequest("/api/admin/security/migrate-project-data", {
      method: "POST",
      headers: { Authorization: `Bearer ${"T".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 403);
  assert.equal(body.error, "insufficient_permissions");
});


test("review submission snapshots are encrypted at rest and decrypted only for reviewers", async () => {
  const { env, scripts, submissions } = createProjectDraftEnv();
  const headers = {
    Authorization: `Bearer ${"U".repeat(48)}`,
    "Content-Type": "application/json"
  };
  const source = "tap(12, 34)\nwait(250)";
  const created = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "Reviewable script", source, languageVersion: "0.1" })
    }),
    env
  );
  const createdBody = await json(created);
  assert.equal(created.status, 201);

  const submitted = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        kind: "SCRIPT",
        itemId: createdBody.script.id,
        revision: 1
      })
    }),
    env
  );
  const submittedBody = await json(submitted);

  assert.equal(submitted.status, 201);
  assert.equal(submittedBody.submitted, true);
  assert.equal(submissions.length, 1);
  assert.equal(submissions[0].submitted_snapshot, "");
  assert.ok(submissions[0].snapshot_ciphertext);
  assert.ok(submissions[0].snapshot_encrypted_data_key);
  assert.ok(submissions[0].snapshot_key_iv);
  assert.equal(submissions[0].snapshot_ciphertext.includes("tap(12"), false);

  const listed = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      headers: { Authorization: `Bearer ${"V".repeat(48)}` }
    }),
    env
  );
  const listedBody = await json(listed);

  assert.equal(listed.status, 200);
  assert.equal(listedBody.submissions[0].snapshot.source, source);
  assert.equal(Object.hasOwn(listedBody.submissions[0], "submitted_snapshot"), false);
});

test("tampered encrypted review submission fails integrity verification", async () => {
  const { env, scripts, submissions } = createProjectDraftEnv();
  const headers = {
    Authorization: `Bearer ${"W".repeat(48)}`,
    "Content-Type": "application/json"
  };
  const created = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "Tampered submission", source: "tap(2, 3)", languageVersion: "0.1" })
    }),
    env
  );
  const createdBody = await json(created);
  assert.equal(created.status, 201);

  const submitted = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      method: "POST",
      headers,
      body: JSON.stringify({ kind: "SCRIPT", itemId: createdBody.script.id, revision: 1 })
    }),
    env
  );
  assert.equal(submitted.status, 201);
  submissions[0].snapshot_ciphertext =
    (submissions[0].snapshot_ciphertext[0] === "A" ? "B" : "A") +
    submissions[0].snapshot_ciphertext.slice(1);

  const listed = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      headers: { Authorization: `Bearer ${"X".repeat(48)}` }
    }),
    env
  );
  const body = await json(listed);

  assert.equal(listed.status, 500);
  assert.equal(body.error, "project_data_integrity_failed");
});


test("Owner migration encrypts existing plaintext project source and submission snapshots", async () => {
  const { env, scripts, submissions } = createProjectDraftEnv();
  const legacySource = "tap(555, 666)\nwait(100)";
  const legacySnapshot = JSON.stringify({
    id: "script-legacy",
    name: "Legacy",
    source: legacySource,
    languageVersion: "0.1",
    revision: 2
  });
  scripts.push({
    id: "script-legacy",
    project_id: PROJECT_TEST_ID,
    name: "Legacy",
    source: legacySource,
    language_version: "0.1",
    revision: 2,
    created_by: ownerSession.user_id,
    updated_by: ownerSession.user_id,
    created_at: 1,
    updated_at: 1,
    source_ciphertext: null,
    source_iv: null,
    source_key_version: null
  });
  submissions.push({
    id: "submission-legacy",
    project_id: PROJECT_TEST_ID,
    kind: "SCRIPT",
    item_id: "script-legacy",
    revision: 2,
    submitted_snapshot: legacySnapshot,
    submitted_by: ownerSession.user_id,
    status: "PENDING",
    review_note: "",
    reviewed_by: null,
    created_at: 1,
    reviewed_at: null,
    snapshot_ciphertext: null,
    snapshot_iv: null,
    snapshot_key_version: null
  });

  const response = await worker.fetch(
    makeRequest("/api/admin/security/migrate-project-data", {
      method: "POST",
      headers: { Authorization: `Bearer ${"Y".repeat(48)}` }
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 200);
  assert.equal(body.migratedDrafts, 1);
  assert.equal(body.migratedSubmissions, 1);
  assert.equal(body.remainingDrafts, 0);
  assert.equal(body.remainingSubmissions, 0);
  assert.equal(body.complete, true);
  assert.equal(scripts[0].source, "");
  assert.ok(scripts[0].source_ciphertext);
  assert.ok(scripts[0].source_encrypted_data_key);
  assert.ok(scripts[0].source_key_iv);
  assert.equal(submissions[0].submitted_snapshot, "");
  assert.ok(submissions[0].snapshot_ciphertext);
  assert.ok(submissions[0].snapshot_encrypted_data_key);
  assert.ok(submissions[0].snapshot_key_iv);
});


test("project draft writes fail closed when the project encryption secret is missing", async () => {
  const { env, scripts } = createProjectDraftEnv();
  delete env.PROJECT_DRAFT_MASTER_KEY_V1;
  const response = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${"Z".repeat(48)}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        name: "Must stay protected",
        source: "tap(1, 1)",
        languageVersion: "0.1"
      })
    }),
    env
  );
  const body = await json(response);

  assert.equal(response.status, 503);
  assert.equal(body.error, "project_encryption_not_configured");
  assert.equal(scripts.length, 0);
});


test("reviewer can review script submission metadata without receiving AUTU source", async () => {
  const reviewerSession = { ...ownerSession };
  const { env, session, scripts, submissions } = createProjectDraftEnv({ session: reviewerSession });
  const headers = {
    Authorization: `Bearer ${"M".repeat(48)}`,
    "Content-Type": "application/json"
  };
  const source = "tap(998, 776)\nwait(350)";
  const created = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/scripts`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "Reviewer visibility", source, languageVersion: "0.1" })
    }),
    env
  );
  const createdBody = await json(created);
  assert.equal(created.status, 201);

  const submitted = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      method: "POST",
      headers,
      body: JSON.stringify({ kind: "SCRIPT", itemId: createdBody.script.id, revision: 1 })
    }),
    env
  );
  assert.equal(submitted.status, 201);
  assert.equal(submissions[0].submitted_snapshot, "");

  session.role = "REVIEWER";
  session.user_id = "reviewer-user";

  const listed = await worker.fetch(
    makeRequest(`/api/projects/${PROJECT_TEST_ID}/submissions`, {
      headers: { Authorization: `Bearer ${"N".repeat(48)}` }
    }),
    env
  );
  const body = await json(listed);

  assert.equal(listed.status, 200);
  assert.equal(body.submissions.length, 1);
  assert.equal(body.submissions[0].snapshot.name, "Reviewer visibility");
  assert.equal(body.submissions[0].snapshot.revision, 1);
  assert.equal(Object.hasOwn(body.submissions[0].snapshot, "source"), false);
});
