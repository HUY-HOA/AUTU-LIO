ALTER TABLE sessions ADD COLUMN device_public_key_spki TEXT;

UPDATE sessions
SET revoked_at = COALESCE(revoked_at, CAST(strftime('%s', 'now') AS INTEGER))
WHERE device_public_key_spki IS NULL AND revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS session_request_nonces (
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  nonce TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (session_id, nonce)
);

CREATE INDEX IF NOT EXISTS idx_session_request_nonces_expiry
ON session_request_nonces(expires_at);
