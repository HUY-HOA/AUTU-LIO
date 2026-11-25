# AUTU-LIO Auth API

Cloudflare Worker + D1 backend for one Android APK with server-verified account roles. Authentication, account data, package delivery and update publishing are handled by Cloudflare. AUTU-LIO does not use Firebase SDKs or Firebase services. Login, account data, licenses, devices, update publishing and application APIs remain on Cloudflare.

**Account model:** only OWNER and CUSTOMER roles are supported. Invitations, collaborator roles, shared projects and review workflows have been removed.

## Current implementation

- Google ID token signature validation against Google's published signing keys.
- Issuer, audience, expiry, verified-email and one-time nonce checks.
- Five-minute, one-time sign-in challenge and hashed bearer sessions stored persistently on the Android client. The Worker enforces a 30-day idle timeout and a 90-day absolute lifetime; sign-in with Google creates a new session after expiry.
- Server-side role lookup on every protected request.
- One-owner bootstrap by the exact Google OpenID Connect `sub` claim configured as the `OWNER_GOOGLE_SUB` Worker secret; email is not used to grant OWNER.
- D1 schema migrations and local migration validation.
- Owner-only publication of immutable AUTU package versions.
- Package metadata accepts legacy AUTU Script versions 0.2, 0.3, 1.19 and 1.20, plus current Android compiler version 1.22. The Android app publishes the compiler's exact version and checks that version again before executing a downloaded package.
- AES-256-GCM encryption with a fresh random 256-bit data key and 96-bit nonce for every published version; only ciphertext is stored in D1.
- Versioned Worker master keys protect package data keys and support controlled key rotation.
- Customer package listing and download require an active license, the requesting device's registered RSA-3072 public key and the package distribution policy. Existing/new packages default to `ALL_LICENSED` for backward compatibility; OWNER can restrict one package to explicit CUSTOMER accounts with `SELECTED_CUSTOMERS`. Direct downloads enforce the same policy as package listing.
- At download time, the Worker re-wraps the package data key to that device; the private key remains in Android Keystore.

Only OWNER can manage customers, licenses, devices and AUTU packages; CUSTOMER accounts are limited to their own licensed devices and packages.

When an account is explicitly revoked, its active devices are marked `ACCOUNT_REVOKED` along with its licenses. After the owner reactivates the account and issues a current license, the customer may re-enroll one of those same devices by signing a fresh one-time challenge with its existing Android Keystore private key, subject to the current device limit. A device individually revoked by the owner (`OWNER_REVOKED`) or marked as a legacy revocation still requires owner-controlled restoration.

## D1

Database name: `autu_lio_auth`

Database ID: `ade35e39-c894-4389-b6c9-31d9f35ec6a2`

Primary location: APAC.

Migrations `0001`–`0013` are applied to the remote `autu_lio_auth` D1 database. Migration `0007_device_revocation_reasons.sql` records why a device was revoked; migration `0008_device_presence.sql` adds app-presence and AUTU runtime-state timestamps; `0009_persistent_sessions.sql` originally retained login sessions until explicit revocation; `0010_package_customer_distribution.sql` adds customer targeting for each published package; `0013_session_timeouts.sql` applies a 90-day absolute session lifetime while preserving each session's last-activity timestamp. The Worker also rejects sessions idle for more than 30 days and updates `last_seen_at` on authenticated requests. `licenses.expires_at` remains unchanged and still controls customer license validity. All 13 migrations are recorded in `d1_migrations`; keep every SQL migration in source control as schema history.

## Required Worker secrets and variables

Set these only after the values have been confirmed:

- `OWNER_GOOGLE_SUB`: exact `sub` claim from the owner's Google ID token after the Worker verifies its signature, issuer, audience, expiry and nonce. This value, not the email address, selects the one account eligible for OWNER.
- `PACKAGE_MASTER_KEY_V1`: base64-encoded cryptographically random 32-byte key. Generate it securely and set it as a Worker secret; never commit it, send it through chat or put it in the APK.

Worker variables:
- `GOOGLE_CLIENT_ID`: public Google OAuth Web/server Client ID used as the expected ID-token audience. Its value is declared in `backend/wrangler.jsonc` and is not a Secret.
- `PACKAGE_MASTER_KEY_VERSION`: active package master-key version, set to `V1` in `wrangler.jsonc`. The matching secret is `PACKAGE_MASTER_KEY_V1`.
- `GOOGLE_ALLOWED_AZP`: optional comma-separated allowlist of Android OAuth Client IDs that may appear in the Google ID token `azp` (authorized presenter) claim. The `aud` claim must still exactly match `GOOGLE_CLIENT_ID`; an arbitrary `azp` is rejected.

For key rotation, add `PACKAGE_MASTER_KEY_V2`, change `PACKAGE_MASTER_KEY_VERSION` to `V2`, and keep the previous versioned secret available while package versions still reference it. Each encrypted package version records the key version used. Do not delete an older master key until every package encrypted with it has been re-encrypted and verified.

Do not place `OWNER_GOOGLE_SUB` or `PACKAGE_MASTER_KEY_V1` in Git. `GOOGLE_CLIENT_ID` is a public identifier declared in `wrangler.jsonc`. Never place a Google OAuth client secret or Cloudflare API token in the Android APK. Android Google Sign-In should use Credential Manager and request an ID token for the server client ID.

If `OWNER_GOOGLE_SUB` is absent or does not exactly match the verified token `sub`, newly authenticated accounts receive CUSTOMER. A matching existing non-owner account is promoted only when D1 contains no other OWNER. The app never promotes itself to OWNER based on a local email check or a Debug preview selection. A unique D1 index prevents more than one OWNER account.

## Configure and deploy

From this directory:

1. Install Wrangler locally as a development dependency using the official current Wrangler release.
2. Run `npx wrangler types` after reviewing the D1 binding in `wrangler.jsonc`.
3. Run `npm run check`.
4. Confirm the production variable `GOOGLE_CLIENT_ID` and Secret `OWNER_GOOGLE_SUB` are present. Never use an email address as the OWNER identifier. Do not rotate or replace the owner `sub` without an explicit owner-account migration plan.
5. Confirm `PACKAGE_MASTER_KEY_V1` is present. Do not replace it while published packages still depend on it; follow the key-rotation procedure below.
6. Run npx wrangler d1 migrations list autu_lio_auth and apply every pending migration with npx wrangler d1 migrations apply autu_lio_auth --remote after reviewing the output.
7. Deploy only after OAuth configuration, Android package/SHA fingerprints, migrations and secrets have been reviewed.

The Worker source is deployed and all ten D1 migrations are applied. Production secrets and OAuth allowlist are configured; the owner has signed in successfully and D1 confirms exactly one active OWNER. Other Google accounts are assigned CUSTOMER by default. The Worker URL is `https://autu-lio-auth-api.coc-lio-data.workers.dev`. Presence reports are accepted at `POST /api/customer/presence`.

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
- `POST /api/customer/presence` — report app foreground and AUTU runtime presence for the authenticated active device.
- `GET /api/customer/packages?deviceFingerprint=...` — list published packages for an active license and matching active device.
- `GET /api/customer/packages/:packageId/download?deviceFingerprint=...` — retrieve ciphertext and a data key wrapped to the registered device.
- `GET /api/admin/customers`, `GET /api/admin/licenses`, `GET /api/admin/devices` — OWNER customer, license and device directories.
- `PATCH /api/admin/customers/:customerId` — change account login status.
- `POST /api/admin/customers/:customerId/sessions/revoke` — revoke all current login sessions without changing account status, licenses or devices.
- `POST /api/admin/licenses`, `PATCH /api/admin/licenses/:licenseId`, `DELETE /api/admin/licenses/:licenseId` — create, update or delete one license.
- `PATCH /api/admin/devices/:deviceId` — grant or revoke one registered device.
 
### Independent access controls

These controls are deliberately separate:

- **Login session only:** `POST /api/admin/customers/:customerId/sessions/revoke` signs the customer out of existing sessions. The account remains active and can sign in again; licenses and device registrations are unchanged.
- **Block/revoke account login:** setting the account to `SUSPENDED` or `REVOKED` blocks new sign-ins and revokes its existing sessions. It does not pause/revoke/delete licenses or change registered devices.
- **Pause/revoke a license:** changing one license to `PAUSED` or `REVOKED` blocks AUTU package listing/download for that customer. It does not sign the customer out, change account status or revoke registered devices.
- **Delete a license:** `DELETE` removes only that license record. The deletion is recorded in audit logs; login sessions, account status and devices are unchanged.
- **Revoke a device:** changes only that device's registration status. It does not revoke login sessions or change the license.


### Published AUTU packages

- `GET /api/admin/packages` — OWNER lists packages and latest versions.
- `POST /api/admin/packages` — OWNER publishes version 1 of a package.
- `POST /api/admin/packages/:packageId/versions` — OWNER publishes the next immutable version.
- `PATCH /api/admin/packages/:packageId` — OWNER activates or archives a package.
- `GET /api/admin/packages/:packageId/distribution` — OWNER reads package recipients and the current distribution mode.
- `PUT /api/admin/packages/:packageId/distribution` — OWNER sets `ALL_LICENSED` or `SELECTED_CUSTOMERS` and supplies selected CUSTOMER IDs.

Package source is encrypted by the Worker before D1 writes. The package-version row stores ciphertext, content nonce, a server-encrypted data key, its nonce, the master-key version and a source SHA-256 hash; it does not store plaintext source. Every customer download checks a live license and active registered device. The data key is wrapped to that device's RSA-3072 public key, then Android unwraps it with the non-exportable private key in Android Keystore, decrypts AES-GCM and verifies SHA-256 before compiling or running.

RSA-OAEP SHA-1/MGF1-SHA1 is used only to wrap the 32-byte AES data key for compatibility with Android Keystore devices exposing the legacy OAEP MGF1 default. AUTU source is encrypted with AES-256-GCM. A future protocol revision can negotiate RSA-OAEP-SHA256 where device support is available.

## Local validation

`npm run check` checks Worker JavaScript syntax, runs the Node.js API behavior test suite with a mocked D1 binding, and executes every SQL migration against an in-memory SQLite database. The API tests cover health/config reporting, the unconfigured Google challenge gate, unauthenticated access denial, removed collaboration routes and OWNER-only package operations, missing package encryption secrets, package encryption/decryption round-trip, license/device enforcement, malformed/oversized request bodies and CORS origin rules. Migration validation checks the single-owner constraint, foreign keys, immutable package versions and encrypted-only package columns. These are source-level tests; they do not deploy the Worker, call the live D1 database or validate a live Google OAuth client.

## GitHub Actions deployment

The production Worker and D1 backend are deployed automatically by `.github/workflows/worker-deploy.yml` when files under `backend/` change on `main`, or when the workflow is run manually. The workflow installs the pinned Wrangler dependency, runs `npm run check`, applies pending migrations with `npm run migrate:remote`, and deploys the Worker with `npm run deploy`. It requires repository Actions secrets `CLOUDFLARE_API_TOKEN` (scoped to the AUTU-LIO Cloudflare account with Workers Scripts Edit and D1 Read/Edit permissions) and `CLOUDFLARE_ACCOUNT_ID` (`b1e6af9f0ef491fb7557747974a863f9`). These deployment secrets are separate from the four Android APK signing secrets. This workflow does not build an Android APK.
