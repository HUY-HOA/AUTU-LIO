# AUTU-LIO Cloudflare API

This service is the server-side authority for Google sign-in, sessions, collaborator invitations and roles. It does not use Firebase.

## Required Cloudflare resources

1. One Cloudflare Worker named `autu-lio-api`.
2. One D1 database named `autu-lio-production`.
3. A private R2 bucket will be added later for released script packages.

The Worker is not deployed by this repository change. The D1 database and its ID must be created in the intended Cloudflare account before deployment.

## Configuration

1. Create the D1 database and replace `REPLACE_WITH_D1_DATABASE_ID` in `wrangler.jsonc`.
2. Set `GOOGLE_WEB_CLIENT_ID` to the Google OAuth **Web application client ID**. This is a public client identifier, not a client secret.
3. Configure `OWNER_BOOTSTRAP_EMAIL` with Wrangler Secret before the first owner login:
   `npx wrangler secret put OWNER_BOOTSTRAP_EMAIL`
4. From this folder install the pinned CLI and generate Worker binding types:
   `npm install`
   `npm run types`
5. Apply the D1 migration:
   `npx wrangler d1 migrations apply autu-lio-production --remote`
6. Verify locally before deployment:
   `npm run dev`
7. Deploy only after the Google OAuth client, D1 ID, owner bootstrap email, allowed web origins and migration are verified:
   `npm run deploy`

Do not commit `.dev.vars`, Cloudflare API tokens, Google client secrets, private signing keys or customer data.

## First owner

The first Google account whose verified email matches the secret `OWNER_BOOTSTRAP_EMAIL` becomes the sole active OWNER. The bootstrap email is only used when no active owner exists. After owner creation, database role assignments are authoritative.

If the bootstrap secret is missing or incorrect, sign-in will create a CUSTOMER account; it will not grant Admin access by guessing an email.

## Authentication endpoints

- `POST /v1/auth/google`: exchange a Google ID token for an AUTU-LIO session. Optional `invitationCode` accepts a pending collaborator invitation.
- `GET /v1/me`: return the current user's role and profile.
- `POST /v1/auth/logout`: revoke the current session.
- `GET /v1/admin/collaborators`: OWNER only.
- `POST /v1/admin/invitations`: OWNER only; creates an invitation for DEVELOPER, DESIGNER or REVIEWER. The returned code is displayed once so the owner can share it securely.
- `GET /v1/admin/invitations`: OWNER only.
- `PATCH /v1/admin/collaborators/:id/role`: OWNER only; cannot create another OWNER.
- `DELETE /v1/admin/collaborators/:id`: OWNER only; revokes the collaborator and their sessions.
- `DELETE /v1/admin/invitations/:id`: OWNER only; revokes an unused invitation.

## Security boundaries

- Google ID tokens are validated on the Worker: signature, issuer, audience, expiry, issue time and verified email.
- The Google `sub` claim is the durable identity key. Email is used only for owner bootstrap and invitation matching.
- Google ID tokens are consumed once by token hash. AUTU-LIO uses its own random opaque session token; only its SHA-256 hash is stored in D1.
- Current role and account status are read from D1 for each authenticated request. Revocation therefore takes effect server-side without trusting an app-supplied role.
- All SQL values are bound parameters.
- The app hiding an Admin button is not a security control; the Worker authorizes every privileged endpoint.
- CORS is restrictive for browser origins. Native Android clients do not rely on browser CORS.
- The implementation does not yet include rate limiting, email delivery, refresh tokens, device/license APIs, or R2 package distribution. Add and test those before production use.

## Operational status

Source and schema are prepared only. No Worker or D1 resource has been created or deployed by this commit. Do not describe Google login or collaborator management as live until a deployment and end-to-end test are successful.
