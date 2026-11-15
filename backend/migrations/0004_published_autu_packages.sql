PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS autu_products (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL COLLATE NOCASE UNIQUE,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','SUSPENDED','REVOKED')),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_autu_products_status_name
ON autu_products(status, name COLLATE NOCASE);

CREATE TABLE IF NOT EXISTS autu_releases (
  id TEXT PRIMARY KEY,
  autu_id TEXT NOT NULL REFERENCES autu_products(id) ON DELETE CASCADE,
  version TEXT NOT NULL,
  language_version TEXT NOT NULL,
  source_ciphertext TEXT NOT NULL,
  source_iv TEXT NOT NULL,
  wrapped_data_key TEXT NOT NULL,
  wrapped_key_iv TEXT NOT NULL,
  source_sha256 TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','REVOKED')),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  UNIQUE(autu_id, version)
);
CREATE INDEX IF NOT EXISTS idx_autu_releases_latest
ON autu_releases(autu_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS autu_licenses (
  id TEXT PRIMARY KEY,
  autu_id TEXT NOT NULL REFERENCES autu_products(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
  starts_at INTEGER NOT NULL,
  expires_at INTEGER,
  max_devices INTEGER NOT NULL DEFAULT 1 CHECK (max_devices BETWEEN 1 AND 10),
  created_by TEXT NOT NULL REFERENCES users(id),
  updated_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(autu_id, user_id),
  CHECK (expires_at IS NULL OR expires_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_autu_licenses_user_status
ON autu_licenses(user_id, status, expires_at);

CREATE TABLE IF NOT EXISTS autu_license_devices (
  id TEXT PRIMARY KEY,
  license_id TEXT NOT NULL REFERENCES autu_licenses(id) ON DELETE CASCADE,
  install_id TEXT NOT NULL,
  device_label TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
  registered_at INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL,
  UNIQUE(license_id, install_id)
);
CREATE INDEX IF NOT EXISTS idx_autu_license_devices_license
ON autu_license_devices(license_id, status, last_seen_at DESC);
