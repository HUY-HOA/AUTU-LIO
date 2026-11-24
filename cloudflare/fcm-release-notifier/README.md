# AUTU-LIO FCM release notifier

This Cloudflare Worker checks the public GitHub Releases feed every 15 minutes and sends a Firebase Cloud Messaging topic notification when a new stable release tag appears.

## Configuration

- Worker: `lio-fcm-autu`
- Cloudflare account: `Phunghuyhohaikba`
- KV namespace: `lio-fcm-autu-lio-release-state`
- KV namespace ID: `c82d358c5eab468c802ceebac869dc1b`
- Firebase project: `autu-lio-515207`
- FCM topic: `autu-lio-announcements`
- Android notification channel: `autu_lio_push_updates`
- Required Worker secret: `FIREBASE_SERVICE_ACCOUNT` (the complete service-account JSON)

The first scheduled run stores the currently published release tag as a baseline and does not send a notification. Later tag changes trigger one push notification. The tag is only updated after FCM accepts the message, so a failed send will be retried on a later run.

## Required one-time Firebase setup

1. In Firebase Console, open project `autu-lio-515207`.
2. Open Project settings → Service accounts.
3. Generate a new private key for a service account that has permission to send FCM messages (Firebase Cloud Messaging API Admin).
4. In the matching Cloudflare account, open Workers & Pages → `lio-fcm-autu` → Settings → Variables and Secrets.
5. Add a **Secret** named `FIREBASE_SERVICE_ACCOUNT`; paste the entire downloaded JSON file as its value. Do not place the JSON in GitHub or app source.

The public `/health` endpoint reports whether the secret is configured and which release tag has been recorded. It never returns credential values.

## Local configuration identifiers

- GitHub repository: `HUY-HOA/AUTU-LIO`
- FCM topic used by the Android app: `autu-lio-announcements`

This worker does not build APKs and does not modify app authentication or download workers.
