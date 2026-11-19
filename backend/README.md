# AUTU-LIO Auth API

Cloudflare Worker + D1 backend for one Android APK with server-verified account roles. Firebase is not used.

**Current rollout scope:** only OWNER and CUSTOMER accounts are being used. The collaboration roles and invitation/project workflow remain in the source for a later phase; do not onboard AUTU writers yet.

## Current implementation

- Google ID token signature validation against Google's published signing keys.
- Issuer, audience, expiry, verified-email and one-time nonce checks.
- Short-lived sign-in challenge and hashed bearer sessions.
- Server-side role lookup on every protected request.
- One-owner bootstrap by the exact Google OpenID Connect `sub` claim configured as the `OWNER_GOOGLE_SUB` Worker secret; email is not used to grant OWNER.
- Owner-only invitation creation, invitation preview and single-use acceptance tied to the signed-in Google email.
- Owner-only collaborator listing, role changes and access revocation, including active sessions.
- Role-based permission defaults plus owner-managed permission overrides for Developer, Designer and Reviewer.
- Permission changes are audited; changing a collaborator role resets overrides to that role's defaults.
- Project creation and explicit membership management, with project data isolated by membership checks.
- Owner-managed project roster; only active Developer, Designer and Reviewer accounts can be added.
- UI design drafts store title, subtitle, button label, accent color and selected components as JSON; Android provides a form editor and live preview.
- Server-backed script drafts and UI design drafts with revision checks to prevent silent overwrites.
- Immutable snapshots for review submissions, review decisions and audit records.
- D1 schema migrations and local migration validation.
- Owner-only publication of immutable AUTU package versions.
- AES-256-GCM encryption with a fresh random 256-bit data key and 96-bit nonce for every published version; only ciphertext is stored in D1.
- Versioned Worker master keys protect package data keys and support controlled key rotation.
- Customer package listing and download require an active license and the requesting device's registered RSA-3072 public key.
- At download time, the Worker re-wraps the package data key to that device; the private key remains in Android Keystore.

Permission overrides are restricted to each role's allowed set. Owner-only controls, such as managing collaborators and licenses or final publishing, cannot be granted to collaborators.

## Gemini Live

Gemini Live uses the authenticated Worker as a token broker. The permanent Gemini API key is stored only as the Cloudflare Worker secret `GEMINI_API_KEY`; Android requests a single-use ephemeral token from `POST /api/ai/live-token` using its existing AUTU bearer session. The Worker pins the model to `models/gemini-3.8-live` and locks the documented audio response and session-resumption fields in the ephemeral token. The Android setup frame supplies the AUTU-LIO system instruction and screen-control function declarations; the app enforces the allowed tools and owner-only AUTU script authoring locally. The Worker rate-limits token minting and never returns the permanent API key. Ephemeral tokens are single-use for creating a new session, allow a 30-minute session lifetime, and authorize session resumption; the new-session window is limited to 60 seconds.

The Gemini Developer API ephemeral-token broker and Android `BidiGenerateContentConstrained` WebSocket both use API version `v1alpha`, as required by the current Google GenAI SDK's ephemeral-token path. This is distinct from the regular API-key Live WebSocket documented under `v1beta`. The token request uses the `bidiGenerateContentSetup` field and the client connects with the `access_token` query parameter.

Configure the secret in the Worker environment before deploying:

```sh
npx wrangler secret put GEMINI_API_KEY
```

The app requires Android 11 or newer and the AUTU-LIO Accessibility Service for screen capture and gestures, plus runtime microphone permission for voice conversations. Screen images are compressed JPEG frames, sent at approximately one frame per 1.2 seconds while the user keeps the Live session active. Audio is sent as 16 kHz mono PCM and Gemini audio is played as 24 kHz mono PCM. Session context compression and resumption are enabled: the app stores the latest resumable handle in memory and automatically reconnects when Gemini sends `goAway` or the WebSocket drops, with up to three retry attempts. Accessible screen labels and pixel bounds can be read on demand; editable and password nodes are omitted. `tap_text` can activate a matching visible, non-editable label when the Android app exposes it. A microphone foreground service keeps the session active when the Live activity moves to the background; the user can stop it in the app or from its persistent notification.

The user must separately enable the “Cho phép AI điều khiển” switch before function calls can tap, swipe, long-press, or press Back. The assistant is instructed not to handle passwords, OTPs, payments, purchases, destructive actions, or message submission. This is a model instruction plus a narrow local tool allow-list, not a guarantee that the model always interprets every screen correctly; users should supervise all automation. Android secure windows can block screenshot capture.

The API key secret and the Worker route must be configured/deployed for the feature to connect. The Android source change alone does not deploy the Worker. Gemini Live usage is billed or rate-limited according to the selected Google AI API plan and model policy.

## D1

Database name: `autu_lio_auth`

Database ID: `ade35e39-c894-4389-b6c9-31d9f35ec6a2`

Primary location: APAC.

Migrations `0001`–`0005` are applied to the remote `autu_lio_auth` D1 database (20 application tables). The Worker `autu-lio-auth-api` was deployed on 19 November 2026 and its workers.dev endpoint is `https://autu-lio-auth-api.coc-lio-data.workers.dev`. The D1 binding and authentication rate-limit bindings are configured on the Worker. The Google Web Client ID, `OWNER_GOOGLE_SUB` and package master key are configured as Worker secrets; the Android OAuth Client ID is configured in `GOOGLE_ALLOWED_AZP`. The owner account has completed Google sign-in and is stored as the single active OWNER in D1. Keep every SQL migration in source control as the schema history.

## Required Worker secrets

Set these only after the values have been confirmed:

- `GOOGLE_CLIENT_ID`: Google OAuth Web/server client ID used as the expected ID-token audience.
- `OWNER_GOOGLE_SUB`: exact `sub` claim from the owner's Google ID token after the Worker verifies its signature, issuer, audience, expiry and nonce. This value, not the email address, selects the one account eligible for OWNER.
- `PACKAGE_MASTER_KEY_V1`: base64-encoded cryptographically random 32-byte key. Generate it securely and set it as a Worker secret; never commit it, send it through chat or put it in the APK.
- `GEMINI_API_KEY`: Google Gemini Developer API key used only by the Worker to mint single-use Live ephemeral tokens. Do not store it in Android or Git.

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

The Worker source is deployed and the five D1 migrations are applied. Production secrets and OAuth allowlist are configured; the owner has signed in successfully and D1 confirms exactly one active OWNER. Other Google accounts are assigned CUSTOMER by default. The Worker URL is `https://autu-lio-auth-api.coc-lio-data.workers.dev`.

## API

### Gemini Live

- `POST /api/ai/live-token` — OWNER or CUSTOMER with a valid session; mints one short-lived Gemini Live ephemeral token. Token requests are rate-limited.

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

### Invitations and collaborators

- Invitation preview through URL query tokens is disabled to avoid leaking invitation tokens through URL history/logs. Accept invitations only after sign-in with `POST /api/invitations/accept`.
- `POST /api/invitations/accept`
- `GET /api/admin/collaborators` — OWNER
- `POST /api/admin/invitations` — OWNER
- `DELETE /api/admin/invitations/:invitationId` — OWNER
- `PATCH /api/admin/collaborators/:userId` — OWNER
- `DELETE /api/admin/collaborators/:userId` — OWNER

### Granular permissions

- `GET /api/admin/collaborators/:userId/permissions` — OWNER
- `PUT /api/admin/collaborators/:userId/permissions` — OWNER

### Shared projects and workspaces

- `GET /api/projects` — list active projects available to the signed-in account
- `POST /api/admin/projects` — OWNER creates a project
- `GET /api/projects/:projectId` — project member or OWNER
- `GET /api/admin/projects/:projectId/members` — OWNER lists project members
- `POST /api/admin/projects/:projectId/members` — OWNER adds an active Developer, Designer or Reviewer
- `DELETE /api/admin/projects/:projectId/members/:userId` — OWNER removes a member
- `GET /api/projects/:projectId/scripts` — script editor or reviewer access
- `POST /api/projects/:projectId/scripts` — create a shared script draft; requires `EDIT_SCRIPTS`
- `PUT /api/projects/:projectId/scripts/:scriptId` — revision-checked script update; requires `EDIT_SCRIPTS`
- `DELETE /api/projects/:projectId/scripts/:scriptId` — delete a draft; requires `EDIT_SCRIPTS`
- `GET /api/projects/:projectId/ui-drafts` — UI designer or preview access
- `PUT /api/projects/:projectId/ui-drafts/:screenKey` — revision-checked UI design save; requires `EDIT_UI_DESIGN`
- `POST /api/projects/:projectId/submissions` — submit a current script/UI revision for review; requires `SUBMIT_RELEASE`
- `GET /api/projects/:projectId/submissions` — list submissions; requires `REVIEW_RELEASE`
- `PATCH /api/projects/:projectId/submissions/:submissionId` — approve/reject a pending submission; requires `REVIEW_RELEASE` and forbids self-review

Script source and UI JSON are size-limited. Every project API checks project membership and the relevant account permission. Script and UI updates use expected revisions, returning HTTP 409 when another collaborator has saved a newer revision. Submission records keep the submitted snapshot even if the draft is edited later.

The permission API returns only permissions configurable for the selected role. The Worker rejects permissions outside that role's allowlist. A role change clears previous overrides to avoid carrying permissions into an incompatible role.

The Worker returns a one-time invitation token for the owner to copy and share manually. The owner can list and revoke pending invitations; the Worker does not send email. Invitation acceptance requires a signed-in POST request matching the invited Google email. There is no public invitation-preview endpoint, so invitation tokens are not carried in URL query strings. Invitation tokens are stored only as SHA-256 hashes; access tokens are also stored hashed. All protected actions require a live server session.

## Local validation

`npm run check` checks Worker JavaScript syntax, runs the Node.js API behavior test suite with a mocked D1 binding, and executes every SQL migration against an in-memory SQLite database. The API tests cover health/config reporting, the unconfigured Google challenge gate, unauthenticated access denial, OWNER-only collaborator and package operations, missing package encryption secrets, package encryption/decryption round-trip, license/device enforcement, malformed/oversized request bodies and CORS origin rules. Migration validation checks the single-owner constraint, foreign keys, project membership, case-insensitive script/package-name uniqueness, UI draft storage, allowed submission kinds, immutable package versions and encrypted-only package columns. These are source-level tests; they do not deploy the Worker, call the live D1 database or validate a live Google OAuth client.
