// Cloudflare Worker for signed APK delivery and optional LIO delta updates.
// Delta files are created from adjacent signed GitHub Releases. APKs remain full,
// signed packages; clients reconstruct and SHA-256 verify the complete target APK.
const APP = {
  appId: "autu-lio",
  appName: "AUTU-LIO",
  applicationId: "com.autu.lio",
  repository: "HUY-HOA/AUTU-LIO"
};
const UPDATE_POLICY_URL = "https://autu-lio-auth-api.coc-lio-data.workers.dev/api/app-update-policy";
const API_ROOT = "https://api.github.com/repos/" + APP.repository;
const APK_MIME = "application/vnd.android.package-archive";
const DELTA_MIME = "application/gzip";
const API_HEADERS = {
  accept: "application/vnd.github+json",
  "x-github-api-version": "2022-11-28",
  "user-agent": "LIO-App-Update-Proxy"
};

function githubHeaders(env, accept) {
  const headers = new Headers(API_HEADERS);
  if (accept) headers.set("accept", accept);
  const token = typeof env?.GITHUB_TOKEN === "string" ? env.GITHUB_TOKEN.trim() : "";
  if (token) headers.set("authorization", "Bearer " + token);
  return headers;
}

function json(data, status = 200, cache = "no-store", head = false) {
  const body = JSON.stringify(data);
  return new Response(head ? null : body, {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "content-length": String(new TextEncoder().encode(body).byteLength),
      "cache-control": cache,
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, HEAD, OPTIONS",
      "access-control-allow-headers": "content-type, range, if-range",
      "access-control-expose-headers": "content-length, content-range, accept-ranges",
      "x-content-type-options": "nosniff"
    }
  });
}

function versionOf(tag) {
  return String(tag || "").replace(/^v/i, "");
}

function validVersion(value) {
  return /^\d+(?:\.\d+){1,3}$/.test(String(value || ""));
}

function shaOf(asset) {
  const digest = typeof asset?.digest === "string" ? asset.digest : "";
  return /^sha256:[a-f0-9]{64}$/i.test(digest) ? digest.slice(7).toLowerCase() : null;
}

async function verifyReleasePackage(tag, env) {
  const response = await fetch(
    API_ROOT + "/contents/app/build.gradle.kts?ref=" + encodeURIComponent(tag),
    { headers: githubHeaders(env), cf: { cacheTtl: 60, cacheEverything: true } }
  );
  if (!response.ok) throw new Error("release_source_lookup_failed");
  const file = await response.json();
  if (typeof file.content !== "string" || file.encoding !== "base64") {
    throw new Error("release_source_content_missing");
  }
  const compactBase64 = file.content.replace(/[\r\n]/g, "");
  const binary = atob(compactBase64);
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  const source = new TextDecoder().decode(bytes);
  const line = source.split("\n").find(item => item.trim().startsWith("applicationId"));
  const firstQuote = line ? line.indexOf('"') : -1;
  const secondQuote = firstQuote >= 0 ? line.indexOf('"', firstQuote + 1) : -1;
  const packageId = firstQuote >= 0 && secondQuote > firstQuote
    ? line.slice(firstQuote + 1, secondQuote)
    : "";
  if (packageId !== APP.applicationId) throw new Error("release_package_id_mismatch");
}

async function getRelease(tag, env) {
  const path = tag ? "/releases/tags/" + encodeURIComponent(tag) : "/releases/latest";
  const response = await fetch(API_ROOT + path, {
    headers: githubHeaders(env),
    cf: { cacheTtl: 60, cacheEverything: true }
  });
  if (!response.ok) throw new Error("release_lookup_failed");
  const release = await response.json();
  const releaseTag = String(release.tag_name || tag || "");
  if (!releaseTag) throw new Error("release_tag_missing");
  await verifyReleasePackage(releaseTag, env);
  const apk = (release.assets || []).find(item =>
    typeof item.name === "string" &&
    item.name.toLowerCase().endsWith(".apk") &&
    item.browser_download_url && item.url
  );
  if (!apk) throw new Error("apk_asset_not_found");
  return { release, apk };
}

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

function deltaAssetName(fromVersion) {
  return APP.appName + "-delta-from-" + fromVersion + ".lio.gz";
}

async function getDeltaPair(toTag, fromVersion, env) {
  if (!validVersion(fromVersion)) throw new Error("invalid_base_version");
  const expectedToVersion = versionOf(toTag);
  if (!validVersion(expectedToVersion)) throw new Error("invalid_target_version");
  const fromTag = "v" + fromVersion;
  const [target, base] = await Promise.all([
    getRelease(toTag, env),
    getRelease(fromTag, env)
  ]);
  if (versionOf(target.release.tag_name) !== expectedToVersion ||
      versionOf(base.release.tag_name) !== fromVersion) {
    throw new Error("release_version_mismatch");
  }
  const targetSha = shaOf(target.apk);
  const baseSha = shaOf(base.apk);
  if (!targetSha || !baseSha || !Number.isSafeInteger(target.apk.size) ||
      !Number.isSafeInteger(base.apk.size)) {
    throw new Error("release_digest_missing");
  }
  const expectedName = deltaAssetName(fromVersion);
  const patch = (target.release.assets || []).find(item =>
    item.name === expectedName && item.browser_download_url && item.url
  );
  if (!patch || !shaOf(patch) || !Number.isSafeInteger(patch.size) ||
      patch.size <= 0 || patch.size >= target.apk.size * 0.9) {
    throw new Error("delta_not_available");
  }
  return { target, base, patch, targetSha, baseSha, patchSha: shaOf(patch) };
}

function downloadHeaders(asset, contentType = APK_MIME) {
  return new Headers({
    "content-type": contentType,
    "content-disposition": "attachment; filename=\"" + String(asset.name).replace(/[^A-Za-z0-9._-]/g, "_") + "\"",
    "content-length": String(asset.size),
    "accept-ranges": "bytes",
    "cache-control": "private, no-store",
    "x-content-type-options": "nosniff"
  });
}

async function streamAsset(request, env, asset, contentType = APK_MIME) {
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
    throw new Error("asset_download_unavailable");
  }
  const headers = downloadHeaders(asset, contentType);
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
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, HEAD, OPTIONS",
      "access-control-allow-headers": "content-type, range, if-range",
      "access-control-max-age": "86400"
    }});
    if (method !== "GET" && !head) return json({ error: "method_not_allowed" }, 405);
    if (url.pathname === "/health") {
      return json({ ok: true, appId: APP.appId, service: "cloudflare-download-proxy", delta: "lio-delta-v1" }, 200, "no-store", head);
    }
    if (url.pathname === "/v1/latest") {
      try {
        const { release, apk } = await getRelease(null, env);
        const tag = String(release.tag_name || "");
        if (!tag) return json({ error: "release_tag_missing" }, 502, "no-store", head);
        return json({
          appId: APP.appId,
          appName: APP.appName,
          applicationId: APP.applicationId,
          versionName: versionOf(tag),
          tag,
          downloadUrl: url.origin + "/v1/download/" + encodeURIComponent(tag),
          sha256: shaOf(apk),
          publishedAt: release.published_at || release.created_at || null,
          releaseNotes: release.body || "",
          prerelease: !!release.prerelease,
          ...(await readUpdatePolicy(versionOf(tag)))
        }, 200, "no-store", head);
      } catch {
        return json({ error: "update_metadata_unavailable" }, 502, "no-store", head);
      }
    }

    const deltaManifestMatch = url.pathname.match(/^\/v1\/delta\/([A-Za-z0-9._-]{1,100})$/);
    if (deltaManifestMatch) {
      const fromVersion = (url.searchParams.get("from") || "").trim();
      try {
        const pair = await getDeltaPair(deltaManifestMatch[1], fromVersion, env);
        return json({
          appId: APP.appId,
          applicationId: APP.applicationId,
          algorithm: "lio-delta-v1",
          fromVersion,
          toVersion: versionOf(pair.target.release.tag_name),
          fromSha256: pair.baseSha,
          targetSha256: pair.targetSha,
          targetSize: pair.target.apk.size,
          patchSize: pair.patch.size,
          patchSha256: pair.patchSha,
          patchUrl: url.origin + "/v1/delta-download/" + encodeURIComponent(deltaManifestMatch[1]) +
            "?from=" + encodeURIComponent(fromVersion)
        }, 200, "no-store", head);
      } catch {
        return json({ error: "delta_not_available" }, 404, "no-store", head);
      }
    }

    const deltaDownloadMatch = url.pathname.match(/^\/v1\/delta-download\/([A-Za-z0-9._-]{1,100})$/);
    if (deltaDownloadMatch) {
      const fromVersion = (url.searchParams.get("from") || "").trim();
      try {
        const pair = await getDeltaPair(deltaDownloadMatch[1], fromVersion, env);
        if (head) return new Response(null, {
          status: 200,
          headers: downloadHeaders(pair.patch, DELTA_MIME)
        });
        return await streamAsset(request, env, pair.patch, DELTA_MIME);
      } catch {
        return json({ error: "delta_download_unavailable" }, 404, "no-store", head);
      }
    }

    const apkMatch = url.pathname.match(/^\/v1\/download\/([A-Za-z0-9._-]{1,100})$/);
    if (apkMatch) {
      try {
        const { apk } = await getRelease(apkMatch[1], env);
        if (head) return new Response(null, { status: 200, headers: downloadHeaders(apk) });
        return await streamAsset(request, env, apk, APK_MIME);
      } catch {
        return json({ error: "apk_download_unavailable" }, 502, "no-store", head);
      }
    }
    return json({ error: "not_found" }, 404, "no-store", head);
  }
};
