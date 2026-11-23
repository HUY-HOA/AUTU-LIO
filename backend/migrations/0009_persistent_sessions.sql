-- Existing active AUTU-LIO authentication sessions remain valid until logout or explicit revocation.
-- Customer license expiry is stored separately in licenses.expires_at and is intentionally unchanged.
-- sessions.expires_at remains only as a schema-compatibility field, not an authorization deadline.
UPDATE sessions
SET expires_at = 253402300799
WHERE revoked_at IS NULL;
