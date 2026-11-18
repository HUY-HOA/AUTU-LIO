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

## Security boundaries

- Authentication, owner role, permissions, licenses and device enrollment are enforced by the Cloudflare Worker, not by Android UI state.
- Never commit .dev.vars, Wrangler local state, API tokens, Google OAuth secrets, master encryption keys, release keystores, or signing passwords.
- Do not store production customer data, sessions, device private keys, or unpublished packages in Git.
- Treat every public APK as inspectable. R8 and package encryption reduce exposure but cannot guarantee secrecy after code is running on a device.
- A change in source is not a production security fix until the Worker, D1 migrations, secrets and Android release have been deployed and verified.
