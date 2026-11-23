CREATE TABLE IF NOT EXISTS app_update_policy (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  mandatory INTEGER NOT NULL DEFAULT 0 CHECK (mandatory IN (0, 1)),
  required_version TEXT,
  updated_at INTEGER NOT NULL DEFAULT 0,
  updated_by_user_id TEXT
);

INSERT OR IGNORE INTO app_update_policy (id, mandatory, required_version, updated_at)
VALUES (1, 0, NULL, 0);
