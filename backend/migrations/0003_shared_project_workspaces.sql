PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS projects (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_projects_status_updated
ON projects(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS project_memberships (
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  added_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  PRIMARY KEY(project_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_project_memberships_user
ON project_memberships(user_id, project_id);

CREATE TABLE IF NOT EXISTS script_drafts (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT '',
  language_version TEXT NOT NULL DEFAULT '0.1',
  revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
  created_by TEXT NOT NULL REFERENCES users(id),
  updated_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(project_id, name COLLATE NOCASE)
);
CREATE INDEX IF NOT EXISTS idx_script_drafts_project_updated
ON script_drafts(project_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ui_drafts (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  screen_key TEXT NOT NULL,
  design_json TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
  created_by TEXT NOT NULL REFERENCES users(id),
  updated_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(project_id, screen_key)
);
CREATE INDEX IF NOT EXISTS idx_ui_drafts_project_updated
ON ui_drafts(project_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS change_submissions (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('SCRIPT','UI')),
  item_id TEXT NOT NULL,
  revision INTEGER NOT NULL CHECK (revision > 0),
  submitted_snapshot TEXT NOT NULL,
  submitted_by TEXT NOT NULL REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  review_note TEXT NOT NULL DEFAULT '',
  reviewed_by TEXT REFERENCES users(id),
  created_at INTEGER NOT NULL,
  reviewed_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_change_submissions_project_status
ON change_submissions(project_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_submissions_item
ON change_submissions(project_id, kind, item_id, revision);
