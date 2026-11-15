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
- Project creation and explicit membership management, with project data isolated by membership checks.
- Owner-managed project roster; only active Developer, Designer and Reviewer accounts can be added.
- UI design drafts store title, subtitle, button label, accent color and selected components as JSON; Android provides a form editor and live preview.
- Server-backed script drafts and UI design drafts with revision checks to prevent silent overwrites.
- Immutable snapshots for review submissions, review decisions and audit records.
- D1 schema migrations and local migration validation.

Permission overrides are restricted to each role's allowed set. Owner-only controls, such as managing collaborators and licenses or final publishing, cannot be granted to collaborators.

## D1

Database name: `autu_lio_auth`

Database ID: `ade35e39-c894-4389-b6c9-31d9f35ec6a2`

Primary location: APAC.

Migrations `0001_identity_and_collaborators.sql`, `0002_single_owner.sql`, and `0003_shared_project_workspaces.sql` have been applied to the connected D1 database and recorded in `d1_migrations`. The five workspace tables are present and `PRAGMA foreign_key_check` returned no errors. Keep every SQL migration in source control as the schema history.

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

The repository contains Worker source and D1 binding configuration. D1 migrations `0001`–`0003` are applied, but the Worker has not been deployed and Google OAuth has not been configured. No client login or shared project API is active from the app yet.

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

The Worker returns a one-time invitation token for the owner to copy and share manually. The owner can list and revoke pending invitations; the Worker does not send email. Invitation tokens are stored only as SHA-256 hashes; access tokens are also stored hashed. All protected actions require a live server session.

## Local validation

`npm run check` checks Worker JavaScript syntax, runs 10 Node.js API behavior tests with a mocked D1 binding, and executes every SQL migration against an in-memory SQLite database. The API tests cover health/config reporting, the unconfigured Google challenge gate, unauthenticated access denial, OWNER-only collaborator access, malformed/oversized request bodies and CORS origin rules. Migration validation checks the single-owner constraint, foreign keys, project membership, case-insensitive script-name uniqueness, UI draft storage and allowed submission kinds. These are source-level tests; they do not deploy the Worker, call the live D1 database or validate a live Google OAuth client.
