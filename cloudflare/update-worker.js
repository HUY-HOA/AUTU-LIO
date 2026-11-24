// Cloudflare Worker source for AUTU-LIO automatic APK update delivery.
// Release package IDs are checked against app/build.gradle.kts at the exact release tag.
// Keep GITHUB_TOKEN configured as a Cloudflare secret binding; never commit the token.

const APP = {
  appId: "autu-lio",
  appName: "AUTU-LIO",
  applicationId: "com.autu.lio",
  repository: "HUY-HOA/AUTU-LIO"
};
const API_ROOT = "https://api.github.com/repos/" + APP.repository;
const UPDATE_POLICY_URL = "https://autu-lio-auth-api.coc-lio-data.workers.dev/api/app-update-policy";
const API_HEADERS = { accept: "application/vnd.github+json", "x-github-api-version": "2022-11-28", "user-agent": "LIO-App-Update-Proxy" };
function githubHeaders(env, accept) {
  const headers = new Headers(API_HEADERS);
  if (accept) headers.set("accept", accept);
  const token = typeof env?.GITHUB_TOKEN === "string" ? env.GITHUB_TOKEN.trim() : "";
  if (token) headers.set("authorization", "Bearer " + token);
  return headers;
}
function json(data, status = 200, cache = "public, max-age=60", head = false) {
  const body = JSON.stringify(data);
  return new Response(head ? null : body, { status, headers: {
    "content-type": "application/json; charset=utf-8",
    "content-length": String(new TextEncoder().encode(body).byteLength),
    "cache-control": cache, "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, HEAD, OPTIONS",
    "access-control-allow-headers": "content-type, range, if-range",
    "access-control-expose-headers": "content-length, content-range, accept-ranges",
    "x-content-type-options": "nosniff"
  }});
}
async function verifyReleasePackage(tag, env) {
  const sourceResponse = await fetch(
    API_ROOT + "/contents/app/build.gradle.kts?ref=" + encodeURIComponent(tag),
    { headers: githubHeaders(env), cf: { cacheTtl: 60, cacheEverything: true } }
  );
  if (!sourceResponse.ok) throw new Error("release_source_lookup_failed");
  const file = await sourceResponse.json();
  if (typeof file.content !== "string" || file.encoding !== "base64") {
    throw new Error("release_source_content_missing");
  }
  const compactBase64 = file.content.split("\n").join("").split("\r").join("");
  const binary = atob(compactBase64);
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  const gradleSource = new TextDecoder().decode(bytes);
  const packageLine = gradleSource.split("\n").find(line => line.trim().startsWith("applicationId"));
  const firstQuote = packageLine ? packageLine.indexOf('"') : -1;
  const secondQuote = firstQuote >= 0 ? packageLine.indexOf('"', firstQuote + 1) : -1;
  const releasePackageId = firstQuote >= 0 && secondQuote > firstQuote
    ? packageLine.slice(firstQuote + 1, secondQuote)
    : "";
  if (!releasePackageId || releasePackageId !== APP.applicationId) {
    throw new Error("release_package_id_mismatch");
  }
}
async function getRelease(tag, env) {
  const path = tag ? "/releases/tags/" + encodeURIComponent(tag) : "/releases/latest";
  const response = await fetch(API_ROOT + path, { headers: githubHeaders(env), cf: { cacheTtl: 60, cacheEverything: true } });
  if (!response.ok) throw new Error("release_lookup_failed");
  const release = await response.json();
  const releaseTag = String(release.tag_name || tag || "");
 if (!releaseTag) throw new Error("release_tag_missing");
 await verifyReleasePackage(releaseTag, env);
 const asset = (release.assets || []).find(item => typeof item.name === "string" && item.name.toLowerCase().endsWith(".apk") && item.browser_download_url && item.url);
  if (!asset) throw new Error("apk_asset_not_found");
  return { release, asset };
}
function versionOf(tag) { return String(tag || "").replace(/^v/i, ""); }
async function readUpdatePolicy(latestVersion) {
    try {
      const response = await fetch(UPDATE_POLICY_URL, {
        method: "GET",
        headers: { accept: "application/json", "cache-control": "no-store" },
        redirect: "error"
      });
      if (!response.ok) return { mandatory: false, requiredVersion: null };
      const policy = await response.json();
      const requiredVersion = typeof policy.requiredVersion === "string" ? policy.requiredVersion.trim() : "";
      const parts = value => String(value || "").split(".").map(part => Number.parseInt(part, 10));
      const current = parts(latestVersion);
      const required = parts(requiredVersion);
      let comparison = -1;
      if (current.length >= 2 && required.length >= 2 &&
          current.every(Number.isSafeInteger) && required.every(Number.isSafeInteger)) {
        comparison = 0;
        for (let index = 0; index < Math.max(current.length, required.length); index++) {
          const a = current[index] || 0;
          const b = required[index] || 0;
          if (a !== b) { comparison = a > b ? 1 : -1; break; }
        }
      }
      const mandatory = policy.mandatory === true && requiredVersion.length > 0 && comparison >= 0;
      return { mandatory, requiredVersion: mandatory ? requiredVersion : null };
    } catch {
      return { mandatory: false, requiredVersion: null };
    }
  }
function downloadHeaders(asset) {
  return new Headers({
    "content-type": "application/vnd.android.package-archive",
    "content-disposition": "attachment; filename=\"" + String(asset.name).replace(/[^A-Za-z0-9._-]/g, "_") + "\"",
    "content-length": String(asset.size), "accept-ranges": "bytes",
    "cache-control": "private, no-store", "x-content-type-options": "nosniff"
  });
}
async function streamAsset(request, env, asset) {
  const range = request.headers.get("range");
  const ifRange = request.headers.get("if-range");
  const token = typeof env?.GITHUB_TOKEN === "string" ? env.GITHUB_TOKEN.trim() : "";
  let upstream;
  if (token) {
    const headers = githubHeaders(env, "application/octet-stream");
    if (range) headers.set("range", range);
    if (ifRange) headers.set("if-range", ifRange);
    upstream = await fetch(asset.url, { method: "GET", headers, redirect: "manual" });
    if ([301, 302, 303, 307, 308].includes(upstream.status)) {
      const location = upstream.headers.get("location");
      if (!location) throw new Error("missing_asset_redirect");
      const fileHeaders = new Headers();
      if (range) fileHeaders.set("range", range);
      if (ifRange) fileHeaders.set("if-range", ifRange);
      upstream = await fetch(location, { method: "GET", headers: fileHeaders, redirect: "follow" });
    }
  } else {
    const headers = new Headers();
    if (range) headers.set("range", range);
    if (ifRange) headers.set("if-range", ifRange);
    upstream = await fetch(asset.browser_download_url, { method: "GET", headers, redirect: "follow" });
  }
  if (!upstream.ok && upstream.status !== 206) {
    try { await upstream.body?.cancel(); } catch {}
    throw new Error("apk_download_unavailable");
  }
  const headers = downloadHeaders(asset);
  for (const name of ["content-length", "content-range", "accept-ranges"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status === 206 ? 206 : 200, headers });
}
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const head = method === "HEAD";
    if (method === "OPTIONS") return new Response(null, { status: 204, headers: {
      "access-control-allow-origin": "*", "access-control-allow-methods": "GET, HEAD, OPTIONS",
      "access-control-allow-headers": "content-type, range, if-range", "access-control-max-age": "86400"
    }});
    if (method !== "GET" && !head) return json({ error: "method_not_allowed" }, 405, "no-store");
    if (url.pathname === "/health") return json({ ok: true, appId: APP.appId, service: "cloudflare-download-proxy" }, 200, "no-store", head);
    if (url.pathname === "/v1/latest") {
      try {
        const { release, asset } = await getRelease(null, env);
        const tag = String(release.tag_name || "");
        if (!tag) return json({ error: "release_tag_missing" }, 502, "no-store", head);
        return json({
          appId: APP.appId, appName: APP.appName, applicationId: APP.applicationId,
          versionName: versionOf(tag), tag, downloadUrl: url.origin + "/v1/download/" + encodeURIComponent(tag),
          sha256: asset.digest && asset.digest.startsWith("sha256:") ? asset.digest.slice(7) : null,
          publishedAt: release.published_at || release.created_at || null,
          releaseNotes: release.body || "", prerelease: !!release.prerelease, ...(await readUpdatePolicy(versionOf(tag)))
        }, 200, "no-store", head);
      } catch { return json({ error: "update_metadata_unavailable" }, 502, "no-store", head); }
    }
    const match = url.pathname.match(/^\/v1\/download\/([A-Za-z0-9._-]{1,100})$/);
    if (match) {
      try {
        const { asset } = await getRelease(match[1], env);
        if (head) return new Response(null, { status: 200, headers: downloadHeaders(asset) });
        return await streamAsset(request, env, asset);
      } catch { return json({ error: "apk_download_unavailable" }, 502, "no-store"); }
    }
    return json({ error: "not_found" }, 404, "no-store", head);
  }
};
