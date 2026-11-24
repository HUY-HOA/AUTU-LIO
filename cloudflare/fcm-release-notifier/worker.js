const APP = Object.freeze({
  id: "autu-lio",
  name: "AUTU-LIO",
  repository: "HUY-HOA/AUTU-LIO",
  firebaseProjectId: "autu-lio-515207",
  topic: "autu-lio-announcements",
  notificationChannelId: "autu_lio_push_updates"
});

const RELEASE_KEY = "latest-release-tag";
const TOKEN_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
let cachedAccessToken = "";
let cachedAccessTokenExpiresAt = 0;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    }
  });
}

function base64Url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function privateKeyBytes(pem) {
  const match = String(pem || "").match(/-----BEGIN PRIVATE KEY-----([\s\S]+?)-----END PRIVATE KEY-----/);
  if (!match) throw new Error("firebase_private_key_invalid");
  const encoded = match[1].replace(/\s/g, "");
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function readServiceAccount(env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT) throw new Error("firebase_service_account_not_configured");
  let account;
  try {
    account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  } catch {
    throw new Error("firebase_service_account_json_invalid");
  }
  if (account.project_id !== APP.firebaseProjectId) throw new Error("firebase_project_id_mismatch");
  if (!account.client_email || !account.private_key) throw new Error("firebase_service_account_fields_missing");
  return account;
}

async function getGoogleAccessToken(serviceAccount) {
  const nowMs = Date.now();
  if (cachedAccessToken && cachedAccessTokenExpiresAt > nowMs + 60000) return cachedAccessToken;

  const now = Math.floor(nowMs / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: TOKEN_SCOPE,
    aud: serviceAccount.token_uri || TOKEN_URL,
    iat: now,
    exp: now + 3600
  }));
  const unsignedJwt = header + "." + claim;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    privateKeyBytes(serviceAccount.private_key.replace(/\\n/g, "\n")).buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedJwt)
  );
  const assertion = unsignedJwt + "." + base64Url(signature);
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion
  });
  const response = await fetch(serviceAccount.token_uri || TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.access_token) throw new Error("google_oauth_token_failed");
  cachedAccessToken = result.access_token;
  cachedAccessTokenExpiresAt = nowMs + Math.max(60, Number(result.expires_in) || 3600) * 1000;
  return cachedAccessToken;
}

async function sendReleaseNotification(env, release) {
  const serviceAccount = readServiceAccount(env);
  const accessToken = await getGoogleAccessToken(serviceAccount);
  const version = String(release.tag_name || "").replace(/^v/i, "");
  const title = APP.name + " có bản cập nhật mới";
  const body = "Phiên bản " + version + " đã sẵn sàng. Mở " + APP.name + " để kiểm tra và cập nhật.";
  const message = {
    message: {
      topic: APP.topic,
      notification: { title, body },
      data: {
        appId: APP.id,
        event: "app_update",
        releaseTag: String(release.tag_name || ""),
        versionName: version
      },
      android: {
        priority: "HIGH",
        notification: {
          channel_id: APP.notificationChannelId,
          sound: "default"
        }
      }
    }
  };
  const response = await fetch(
    "https://fcm.googleapis.com/v1/projects/" + APP.firebaseProjectId + "/messages:send",
    {
      method: "POST",
      headers: {
        authorization: "Bearer " + accessToken,
        "content-type": "application/json"
      },
      body: JSON.stringify(message)
    }
  );
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result.name) {
    console.error("FCM send failed", response.status, result.error?.status || "unknown");
    throw new Error("fcm_send_failed");
  }
  return result.name;
}

async function checkForNewRelease(env) {
  if (!env.STATE) throw new Error("release_state_binding_missing");
  const response = await fetch(
    "https://api.github.com/repos/" + APP.repository + "/releases/latest",
    {
      headers: {
        accept: "application/vnd.github+json",
        "user-agent": "LIO-FCM-Release-Notifier"
      },
      cf: { cacheTtl: 0, cacheEverything: false }
    }
  );
  if (response.status === 404) {
    console.log("No published GitHub release found for", APP.id);
    return;
  }
  if (!response.ok) throw new Error("github_release_lookup_failed");
  const release = await response.json();
  const tag = String(release.tag_name || "").trim();
  if (!tag) throw new Error("github_release_tag_missing");

  const previousTag = await env.STATE.get(RELEASE_KEY);
  if (previousTag === null) {
    await env.STATE.put(RELEASE_KEY, tag);
    console.log("Initialized release baseline for", APP.id, tag);
    return;
  }
  if (previousTag === tag) return;

  await sendReleaseNotification(env, release);
  await env.STATE.put(RELEASE_KEY, tag);
  console.log("Release notification sent for", APP.id, tag);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      const latestTag = env.STATE ? await env.STATE.get(RELEASE_KEY) : null;
      return json({
        ok: true,
        appId: APP.id,
        service: "fcm-release-notifier",
        firebaseConfigured: Boolean(env.FIREBASE_SERVICE_ACCOUNT),
        stateBindingConfigured: Boolean(env.STATE),
        latestReleaseTag: latestTag
      });
    }
    return json({ error: "not_found" }, 404);
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil(
      checkForNewRelease(env).catch(error => {
        console.error("Release check failed for", APP.id, String(error));
      })
    );
  }
};
