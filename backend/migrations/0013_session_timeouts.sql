-- Preserve existing active sessions through rollout, then enforce bounded lifetime in Worker code.
-- New sessions expire after 90 days; authenticated requests also enforce 30 days of inactivity.
UPDATE sessions
SET expires_at = CAST(strftime('%s', 'now') AS INTEGER) + 90 * 24 * 60 * 60,
    last_seen_at = CAST(strftime('%s', 'now') AS INTEGER)
WHERE revoked_at IS NULL;
