# Security policy

## Reporting a vulnerability

Do not publish credentials, exploit details, personal information, or an unpatched vulnerability in a public Issue or Discussion.

Use GitHub private vulnerability reporting:
1. Open the repository's Security tab.
2. Select Report a vulnerability.
3. Include the affected commit/version, reproduction steps, impact, and a safe proof of concept.

Do not send signing keystores, passwords, Cloudflare API tokens, Worker secrets, Google OAuth credentials, customer data, or session tokens in a report.

## Supported code

Security fixes are applied to the main branch. The public repository is not a confidentiality boundary; source, Git history, workflow files and issue content must be treated as public.

## Current backend status

- The Worker source includes approximate per-IP rate limits for the unauthenticated Google challenge and login endpoints. Cloudflare evaluates these counters separately by location, so this reduces abuse but is not a strict global limit; shared mobile carrier IPs can affect multiple legitimate users.
- The connected Cloudflare account was checked on 19 November 2026: no `autu-lio-auth-api` Worker was deployed and D1 `autu_lio_auth` had zero tables. The code and local migration tests must not be described as a running production backend until Worker deployment, D1 migrations, secrets and live endpoint tests are completed.
- `OWNER_GOOGLE_SUB`, `GOOGLE_CLIENT_ID`, and `PACKAGE_MASTER_KEY_V1` are runtime configuration values. They must exist only as Cloudflare Worker secrets, never in Git or the APK. `OWNER_GOOGLE_SUB` is the verified Google OpenID Connect `sub` claim; email is not an owner credential.

## Additional API protections

- API responses use `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a restrictive Content Security Policy, same-origin resource policy, a restrictive Permissions Policy and HSTS.
- Browser CORS accepts only exact configured origins. A wildcard origin is not accepted.
- Invitation preview tokens are no longer accepted in URL query strings. Invitation acceptance remains a signed-in POST operation so bearer-like invitation tokens are not placed in browser history or URL logs.
- The Android API client refuses non-HTTPS base URLs and does not follow HTTP redirects, preventing bearer session headers from being forwarded to a redirected host.

## Security boundaries

- Authentication, owner role, permissions, licenses and device enrollment are enforced by the Cloudflare Worker, not by Android UI state.
- Never commit .dev.vars, Wrangler local state, API tokens, Google OAuth secrets, master encryption keys, release keystores, or signing passwords.
- Do not store production customer data, sessions, device private keys, or unpublished packages in Git.
- Treat every public APK as inspectable. R8 and package encryption reduce exposure but cannot guarantee secrecy after code is running on a device.
- A change in source is not a production security fix until the Worker, D1 migrations, secrets and Android release have been deployed and verified.
