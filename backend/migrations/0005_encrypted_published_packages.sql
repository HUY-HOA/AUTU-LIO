ALTER TABLE devices
ADD COLUMN oaep_hash TEXT NOT NULL DEFAULT 'SHA-1'
CHECK (oaep_hash IN ('SHA-1', 'SHA-256'));

CREATE TABLE IF NOT EXISTS autu_packages (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  current_version_id TEXT,
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_autu_packages_name
ON autu_packages(name COLLATE NOCASE);
CREATE INDEX IF NOT EXISTS idx_autu_packages_status_updated
ON autu_packages(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS autu_package_versions (
  id TEXT PRIMARY KEY,
  package_id TEXT NOT NULL REFERENCES autu_packages(id) ON DELETE CASCADE,
  version_code INTEGER NOT NULL CHECK (version_code > 0),
  version_name TEXT NOT NULL,
  language_version TEXT NOT NULL,
  object_key TEXT NOT NULL UNIQUE,
  content_nonce TEXT NOT NULL,
  encrypted_content_key TEXT NOT NULL,
  content_key_nonce TEXT NOT NULL,
  source_sha256 TEXT NOT NULL,
  plaintext_size INTEGER NOT NULL CHECK (plaintext_size BETWEEN 1 AND 262144),
  status TEXT NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED', 'REVOKED')),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  UNIQUE(package_id, version_code)
);
CREATE INDEX IF NOT EXISTS idx_autu_package_versions_current
ON autu_package_versions(package_id, status, version_code DESC);
CREATE INDEX IF NOT EXISTS idx_autu_package_versions_created
ON autu_package_versions(created_at DESC);
