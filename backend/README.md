# AUTU-LIO Auth API

Cloudflare Worker + D1 backend for one Android APK with server-verified account roles. Firebase is not used.

**Account model:** only OWNER and CUSTOMER roles are supported. Invitations, collaborator roles, shared projects and review workflows have been removed.

## Current implementation

- Google ID token signature validation against Google's published signing keys.
- Issuer, audience, expiry, verified-email and one-time nonce checks.
- Short-lived sign-in challenge and hashed bearer sessions.
- Server-side role lookup on every protected request.
- One-owner bootstrap by the exact Google OpenID Connect `sub` claim configured as the `OWNER_GOOGLE_SUB` Worker secret; email is not used to grant OWNER.
- D1 schema migrations and local migration validation.
- Owner-only publication of immutable AUTU package versions.
- Package metadata accepts legacy AUTU Script versions 0.2, 0.3, 1.19 and 1.20, plus current Android compiler version 1.22. The Android app publishes the compiler's exact version and checks that version again before executing a downloaded package.
- AES-256-GCM encryption with a fresh random 256-bit data key and 96-bit nonce for every published version; only ciphertext is stored in D1.
- Versioned Worker master keys protect package data keys and support controlled key rotation.
- Customer package listing and download require an active license and the requesting device's registered RSA-3072 public key.
- At download time, the Worker re-wraps the package data key to that device; the private key remains in Android Keystore.

Only OWNER can manage customers, licenses, devices and AUTU packages; CUSTOMER accounts are limited to their own licensed devices and packages.

## D1

Database name: `autu_lio_auth`

Database ID: `ade35e39-c894-4389-b6c9-31d9f35ec6a2`

Primary location: APAC.

Migrations `0001`–`0006` were previously applied to the remote `autu_lio_auth` D1 database. Migration `0007_device_revocation_reasons.sql` must be applied before deploying the customer/device lifecycle fix; it adds revocation reasons and labels existing revoked devices as `LEGACY_REVOKED`. After all migrations, 10 application tables remain (excluding SQLite/D1 internal tables). The seven collaboration-related tables have been dropped. The Worker `autu-lio-auth-api` latest owner/customer-only version was deployed on 19 November 2026 at 21:52 UTC with 100% traffic and its workers.dev endpoint is `https://autu-lio-auth-api.coc-lio-data.workers.dev`. The D1 binding and authentication rate-limit bindings are configured on the Worker. The Google Web Client ID, `OWNER_GOOGLE_SUB` and package master key are configured as Worker secrets; the Android OAuth Client ID is configured in `GOOGLE_ALLOWED_AZP`. The owner account has completed Google sign-in and is stored as the single active OWNER in D1. Keep every SQL migration in source control as the schema history.

## Required Worker secrets

Set these only after the values have been confirmed:

- `GOOGLE_CLIENT_ID`: Google OAuth Web/server client ID used as the expected ID-token audience.
- `OWNER_GOOGLE_SUB`: exact `sub` claim from the owner's Google ID token after the Worker verifies its signature, issuer, audience, expiry and nonce. This value, not the email address, selects the one account eligible for OWNER.
- `PACKAGE_MASTER_KEY_V1`: base64-encoded cryptographically random 32-byte key. Generate it securely and set it as a Worker secret; never commit it, send it through chat or put it in the APK.

Worker variables:
- `PACKAGE_MASTER_KEY_VERSION`: active package master-key version, set to `V1` in `wrangler.jsonc`. The matching secret is `PACKAGE_MASTER_KEY_V1`.
- `GOOGLE_ALLOWED_AZP`: optional comma-separated allowlist of Android OAuth Client IDs that may appear in the Google ID token `azp` (authorized presenter) claim. The `aud` claim must still exactly match `GOOGLE_CLIENT_ID`; an arbitrary `azp` is rejected.

For key rotation, add `PACKAGE_MASTER_KEY_V2`, change `PACKAGE_MASTER_KEY_VERSION` to `V2`, and keep the previous versioned secret available while package versions still reference it. Each encrypted package version records the key version used. Do not delete an older master key until every package encrypted with it has been re-encrypted and verified.

Do not place either value in Git. Never place a client secret or Cloudflare API token in the Android APK. Android Google Sign-In should use Credential Manager and request an ID token for the server client ID.

If `OWNER_GOOGLE_SUB` is absent or does not exactly match the verified token `sub`, newly authenticated accounts receive CUSTOMER. A matching existing non-owner account is promoted only when D1 contains no other OWNER. The app never promotes itself to OWNER based on a local email check or a Debug preview selection. A unique D1 index prevents more than one OWNER account.

## Configure and deploy

From this directory:

1. Install Wrangler locally as a development dependency using the official current Wrangler release.
2. Run `npx wrangler types` after reviewing the D1 binding in `wrangler.jsonc`.
3. Run `npm run check`.
4. Confirm the Production secrets `GOOGLE_CLIENT_ID` and `OWNER_GOOGLE_SUB` are present. Never use an email address as the OWNER identifier. Do not rotate or replace the owner `sub` without an explicit owner-account migration plan.
5. Confirm `PACKAGE_MASTER_KEY_V1` is present. Do not replace it while published packages still depend on it; follow the key-rotation procedure below.
6. Run npx wrangler d1 migrations list autu_lio_auth and apply every pending migration with npx wrangler d1 migrations apply autu_lio_auth --remote after reviewing the output.
7. Deploy only after OAuth configuration, Android package/SHA fingerprints, migrations and secrets have been reviewed.

The Worker source is deployed and all six D1 migrations are applied. Production secrets and OAuth allowlist are configured; the owner has signed in successfully and D1 confirms exactly one active OWNER. Other Google accounts are assigned CUSTOMER by default. The Worker URL is `https://autu-lio-auth-api.coc-lio-data.workers.dev`.

## API

### Authentication

- `GET /health`
- `POST /api/auth/challenge`
- `POST /api/auth/google`
- `GET /api/auth/me`
- `POST /api/auth/logout`

### Customer licenses, devices and AUTU packages

- `GET /api/customer/account` — CUSTOMER account, license and registered devices.
- `POST /api/customer/devices/challenge` — create a one-time device enrollment challenge.
- `POST /api/customer/devices` — register the RSA-3072 Android Keystore public key.
- `GET /api/customer/packages?deviceFingerprint=...` — list published packages for an active license and matching active device.
- `GET /api/customer/packages/:packageId/download?deviceFingerprint=...` — retrieve ciphertext and a data key wrapped to the registered device.
- `GET /api/admin/customers`, `GET /api/admin/licenses`, `GET /api/admin/devices` — OWNER customer and device directories.
- `POST /api/admin/licenses`, `PATCH /api/admin/licenses/:licenseId`, `PATCH /api/admin/devices/:deviceId` — OWNER license and device controls.

### Published AUTU packages

- `GET /api/admin/packages` — OWNER lists packages and latest versions.
- `POST /api/admin/packages` — OWNER publishes version 1 of a package.
- `POST /api/admin/packages/:packageId/versions` — OWNER publishes the next immutable version.
- `PATCH /api/admin/packages/:packageId` — OWNER activates or archives a package.

Package source is encrypted by the Worker before D1 writes. The package-version row stores ciphertext, content nonce, a server-encrypted data key, its nonce, the master-key version and a source SHA-256 hash; it does not store plaintext source. Every customer download checks a live license and active registered device. The data key is wrapped to that device's RSA-3072 public key, then Android unwraps it with the non-exportable private key in Android Keystore, decrypts AES-GCM and verifies SHA-256 before compiling or running.

RSA-OAEP SHA-1/MGF1-SHA1 is used only to wrap the 32-byte AES data key for compatibility with Android Keystore devices exposing the legacy OAEP MGF1 default. AUTU source is encrypted with AES-256-GCM. A future protocol revision can negotiate RSA-OAEP-SHA256 where device support is available.

## Local validation

`npm run check` checks Worker JavaScript syntax, runs the Node.js API behavior test suite with a mocked D1 binding, and executes every SQL migration against an in-memory SQLite database. The API tests cover health/config reporting, the unconfigured Google challenge gate, unauthenticated access denial, removed collaboration routes and OWNER-only package operations, missing package encryption secrets, package encryption/decryption round-trip, license/device enforcement, malformed/oversized request bodies and CORS origin rules. Migration validation checks the single-owner constraint, foreign keys, immutable package versions and encrypted-only package columns. These are source-level tests; they do not deploy the Worker, call the live D1 database or validate a live Google OAuth client.
