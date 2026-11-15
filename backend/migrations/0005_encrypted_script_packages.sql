PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS script_packages (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  latest_version INTEGER NOT NULL DEFAULT 1 CHECK (latest_version > 0),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(name COLLATE NOCASE)
);
CREATE INDEX IF NOT EXISTS idx_script_packages_status_updated
ON script_packages(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS script_package_versions (
  id TEXT PRIMARY KEY,
  package_id TEXT NOT NULL REFERENCES script_packages(id) ON DELETE CASCADE,
  version INTEGER NOT NULL CHECK (version > 0),
  language_version TEXT NOT NULL,
  ciphertext TEXT NOT NULL,
  content_iv TEXT NOT NULL,
  encrypted_data_key TEXT NOT NULL,
  key_iv TEXT NOT NULL,
  source_sha256 TEXT NOT NULL,
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  UNIQUE(package_id, version)
);
CREATE INDEX IF NOT EXISTS idx_script_package_versions_package_version
ON script_package_versions(package_id, version DESC);
