# AUTU-LIO Auth API

Cloudflare Worker + D1 backend for one Android APK with server-verified account roles. Firebase is not used.

## Current implementation

- Google ID token signature validation against Google's published signing keys.
- Issuer, audience, expiry, verified-email and one-time nonce checks.
- Short-lived sign-in challenge and hashed bearer sessions.
- Server-side role lookup on every protected request.
- One-owner bootstrap by an exact, verified Google email configured as a Worker secret.
- Owner-only invitation creation, invitation preview and single-use acceptance tied to the signed-in Google email.
- Owner-only collaborator listing, role changes and access revocation, including active sessions.
- Role-based permission defaults plus owner-managed permission overrides for Developer, Designer and Reviewer.
- Permission changes are audited; changing a collaborator role resets overrides to that role's defaults.
- Minimal audit records for authentication and collaborator changes.
- D1 schema migrations and local migration validation.

Permission overrides are restricted to each role's allowed set. Owner-only controls, such as managing collaborators and licenses or final publishing, cannot be granted to collaborators.

## D1

Database name: `autu_lio_auth`

Database ID: `ade35e39-c894-4389-b6c9-31d9f35ec6a2`

Primary location: APAC.

The initial schema is already applied to the connected Cloudflare account. Keep the SQL migration files in source control as the schema history. Apply future migrations with Wrangler after reviewing the SQL.

## Required Worker secrets

Set these only after the values have been confirmed:

- `GOOGLE_CLIENT_ID`: Google OAuth Web/server client ID used as the expected ID-token audience.
- `OWNER_EMAIL`: exact Google email that is allowed to become the first OWNER after Google verifies the account.

Optional Worker variable `GOOGLE_ALLOWED_AZP`: comma-separated allowlist of Android OAuth Client IDs that may appear in the Google ID token `azp` (authorized presenter) claim. The `aud` claim must still exactly match `GOOGLE_CLIENT_ID`; an arbitrary `azp` is rejected. Configure the Android OAuth Client ID here after obtaining it from Google Cloud.

Do not place either value in Git. Never place a client secret or Cloudflare API token in the Android APK. Android Google Sign-In should use Credential Manager and request an ID token for the server client ID.

If `OWNER_EMAIL` is absent, newly authenticated accounts receive CUSTOMER. The app never promotes itself to OWNER based on a local email check or a Debug preview selection. A unique D1 index prevents more than one OWNER account.

## Configure and deploy

From this directory:

1. Install Wrangler locally as a development dependency using the official current Wrangler release.
2. Run `npx wrangler types` after reviewing the D1 binding in `wrangler.jsonc`.
3. Run `npm run check`.
4. Set the two secrets using `npx wrangler secret put GOOGLE_CLIENT_ID` and `npx wrangler secret put OWNER_EMAIL`.
5. Deploy only after Google Cloud OAuth configuration and Android package/SHA fingerprints have been prepared and reviewed.

This repository currently contains the Worker source and D1 binding configuration. The Worker has not been deployed, Google OAuth has not been configured, and no client login is active yet.

## API

### Authentication

- `GET /health`
- `POST /api/auth/challenge`
- `POST /api/auth/google`
- `GET /api/auth/me`
- `POST /api/auth/logout`

### Invitations and collaborators

- `GET /api/invitations/preview?token=...`
- `POST /api/invitations/accept`
- `GET /api/admin/collaborators` — OWNER
- `POST /api/admin/invitations` — OWNER
- `DELETE /api/admin/invitations/:invitationId` — OWNER
- `PATCH /api/admin/collaborators/:userId` — OWNER
- `DELETE /api/admin/collaborators/:userId` — OWNER

### Granular permissions

- `GET /api/admin/collaborators/:userId/permissions` — OWNER
- `PUT /api/admin/collaborators/:userId/permissions` — OWNER

The permission API returns only permissions configurable for the selected role. The Worker rejects permissions outside that role's allowlist. A role change clears previous overrides to avoid carrying permissions into an incompatible role.

The Worker returns a one-time invitation token for the owner to copy and share manually. The owner can list and revoke pending invitations; the Worker does not send email. Invitation tokens are stored only as SHA-256 hashes; access tokens are also stored hashed. All protected actions require a live server session.

## Local validation

`npm run check` checks JavaScript syntax and executes every SQL migration against an in-memory SQLite database, including the single-owner constraint. It does not deploy the Worker or validate a live Google OAuth client.
