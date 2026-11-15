PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS licenses (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_code TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','REVOKED')),
  starts_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  max_devices INTEGER NOT NULL DEFAULT 1 CHECK (max_devices BETWEEN 1 AND 20),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  CHECK (expires_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_licenses_user_created
ON licenses(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_licenses_status_expiry
ON licenses(status, expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_licenses_single_active_user
ON licenses(user_id)
WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  public_key_spki TEXT NOT NULL,
  public_key_fingerprint TEXT NOT NULL UNIQUE,
  label TEXT NOT NULL DEFAULT '',
  manufacturer TEXT NOT NULL DEFAULT '',
  model TEXT NOT NULL DEFAULT '',
  android_version TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
  registered_at INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL,
  revoked_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_devices_user_status
ON devices(user_id, status, registered_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen
ON devices(last_seen_at DESC);

CREATE TABLE IF NOT EXISTS device_enrollment_challenges (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  nonce_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_device_enrollment_challenges_expiry
ON device_enrollment_challenges(expires_at);
CREATE INDEX IF NOT EXISTS idx_device_enrollment_challenges_user
ON device_enrollment_challenges(user_id, created_at DESC);
