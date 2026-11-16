from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
database = sqlite3.connect(":memory:")
database.execute("PRAGMA foreign_keys = ON")

migrations = sorted((root / "migrations").glob("*.sql"))
if not migrations:
    raise SystemExit("No D1 migrations were found")

for migration in migrations:
    database.executescript(migration.read_text(encoding="utf-8"))

required_tables = {
    "users",
    "auth_challenges",
    "sessions",
    "invitations",
    "permission_overrides",
    "audit_logs",
    "app_settings",
    "projects",
    "project_memberships",
    "script_drafts",
    "ui_drafts",
    "change_submissions",
    "licenses",
    "devices",
    "device_enrollment_challenges",
    "script_packages",
    "script_package_versions",
}
actual_tables = {
    row[0]
    for row in database.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table'"
    )
}
missing = sorted(required_tables - actual_tables)
if missing:
    raise SystemExit(f"Missing tables: {', '.join(missing)}")

owner_index = database.execute(
    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_users_single_owner'"
).fetchone()
if owner_index is None:
    raise SystemExit("Missing unique single-owner index")

users = [
    ("owner-1", "google-owner", "owner@example.com", "OWNER"),
    ("developer-1", "google-developer", "developer@example.com", "DEVELOPER"),
    ("designer-1", "google-designer", "designer@example.com", "DESIGNER"),
    ("reviewer-1", "google-reviewer", "reviewer@example.com", "REVIEWER"),
]
for user_id, google_sub, email, role in users:
    database.execute(
        "INSERT INTO users (id, google_sub, email, role, status, created_at, updated_at) "
        "VALUES (?, ?, ?, ?, 'ACTIVE', 1, 1)",
        (user_id, google_sub, email, role),
    )

try:
    database.execute(
        "INSERT INTO users (id, google_sub, email, role, status, created_at, updated_at) "
        "VALUES ('owner-2', 'google-2', 'other@example.com', 'OWNER', 'ACTIVE', 1, 1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Database accepted more than one OWNER")

database.execute(
    "INSERT INTO projects (id, name, description, created_by, created_at, updated_at) "
    "VALUES ('project-1', 'Main project', '', 'owner-1', 1, 1)"
)
database.execute(
    "INSERT INTO project_memberships (project_id, user_id, added_by, created_at) "
    "VALUES ('project-1', 'developer-1', 'owner-1', 1)"
)

try:
    database.execute(
        "INSERT INTO project_memberships (project_id, user_id, added_by, created_at) "
        "VALUES ('project-1', 'missing-user', 'owner-1', 1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Project membership accepted a user that does not exist")

database.execute(
    "INSERT INTO script_drafts "
    "(id, project_id, name, source, language_version, revision, created_by, updated_by, created_at, updated_at, source_ciphertext, source_iv, source_key_version) "
    "VALUES ('script-1', 'project-1', 'Example', '', '0.1', 1, 'developer-1', 'developer-1', 1, 1, 'encrypted-script', 'random-iv', 'V1')"
)
try:
    database.execute(
        "INSERT INTO script_drafts "
        "(id, project_id, name, source, language_version, revision, created_by, updated_by, created_at, updated_at) "
        "VALUES ('script-2', 'project-1', 'example', '', '0.1', 1, 'developer-1', 'developer-1', 1, 1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Script names are not unique within a project (case-insensitive)")

database.execute(
    "INSERT INTO ui_drafts "
    "(id, project_id, screen_key, design_json, revision, created_by, updated_by, created_at, updated_at) "
    "VALUES ('ui-1', 'project-1', 'home', '{\"layout\":\"column\"}', 1, 'designer-1', 'designer-1', 1, 1)"
)
database.execute(
    "INSERT INTO change_submissions "
    "(id, project_id, kind, item_id, revision, submitted_snapshot, submitted_by, status, created_at, snapshot_ciphertext, snapshot_iv, snapshot_key_version) "
    "VALUES ('submission-1', 'project-1', 'SCRIPT', 'script-1', 1, '', 'developer-1', 'PENDING', 1, 'encrypted-snapshot', 'random-iv', 'V1')"
)

try:
    database.execute(
        "INSERT INTO change_submissions "
        "(id, project_id, kind, item_id, revision, submitted_snapshot, submitted_by, status, created_at) "
        "VALUES ('submission-2', 'project-1', 'INVALID', 'script-1', 1, '{}', 'developer-1', 'PENDING', 1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Submission accepted an unsupported change kind")

database.execute(
    "INSERT INTO licenses "
    "(id, user_id, plan_code, status, starts_at, expires_at, max_devices, created_by, created_at, updated_at) "
    "VALUES ('license-1', 'owner-1', 'PRO', 'ACTIVE', 100, 200, 2, 'owner-1', 100, 100)"
)
try:
    database.execute(
        "INSERT INTO licenses "
        "(id, user_id, plan_code, status, starts_at, expires_at, max_devices, created_by, created_at, updated_at) "
        "VALUES ('license-2', 'owner-1', 'PRO', 'ACTIVE', 100, 300, 2, 'owner-1', 100, 100)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Database accepted more than one active license for a user")

try:
    database.execute(
        "INSERT INTO licenses "
        "(id, user_id, plan_code, status, starts_at, expires_at, max_devices, created_by, created_at, updated_at) "
        "VALUES ('license-invalid', 'developer-1', 'PRO', 'ACTIVE', 100, 200, 21, 'owner-1', 100, 100)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Database accepted a license device limit outside 1..20")

database.execute(
    "INSERT INTO devices "
    "(id, user_id, public_key_spki, public_key_fingerprint, label, status, registered_at, last_seen_at) "
    "VALUES ('device-1', 'owner-1', 'public-key', 'fingerprint-1', 'Test device', 'ACTIVE', 100, 100)"
)
try:
    database.execute(
        "INSERT INTO devices "
        "(id, user_id, public_key_spki, public_key_fingerprint, label, status, registered_at, last_seen_at) "
        "VALUES ('device-2', 'developer-1', 'public-key', 'fingerprint-1', 'Duplicate key', 'ACTIVE', 100, 100)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Database accepted a duplicate device public-key fingerprint")

database.execute(
    "INSERT INTO script_packages "
    "(id, name, description, status, latest_version, created_by, created_at, updated_at) "
    "VALUES ('package-1', 'AUTU Test', '', 'ACTIVE', 1, 'owner-1', 100, 100)"
)
database.execute(
    "INSERT INTO script_package_versions "
    "(id, package_id, version, language_version, ciphertext, content_iv, encrypted_data_key, key_iv, key_version, source_sha256, created_by, created_at) "
    "VALUES ('package-version-1', 'package-1', 1, '0.3', 'encrypted-payload', 'content-iv', 'encrypted-key', 'key-iv', 'V1', 'hash', 'owner-1', 100)"
)
try:
    database.execute(
        "INSERT INTO script_package_versions "
        "(id, package_id, version, language_version, ciphertext, content_iv, encrypted_data_key, key_iv, key_version, source_sha256, created_by, created_at) "
        "VALUES ('package-version-duplicate', 'package-1', 1, '0.3', 'ciphertext', 'iv', 'wrapped', 'iv', 'V1', 'hash', 'owner-1', 100)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Database accepted a duplicate package version")

project_draft_columns = {
    row[1]
    for row in database.execute("PRAGMA table_info(script_drafts)")
}
required_project_draft_columns = {"source_ciphertext", "source_iv", "source_key_version"}
if not required_project_draft_columns.issubset(project_draft_columns):
    raise SystemExit("Encrypted project script draft columns are incomplete")
encrypted_script = database.execute(
    "SELECT source, source_ciphertext, source_iv, source_key_version FROM script_drafts WHERE id = 'script-1'"
).fetchone()
if encrypted_script != ("", "encrypted-script", "random-iv", "V1"):
    raise SystemExit("New project script drafts must not store plaintext source")

submission_columns = {
    row[1]
    for row in database.execute("PRAGMA table_info(change_submissions)")
}
required_submission_columns = {"snapshot_ciphertext", "snapshot_iv", "snapshot_key_version"}
if not required_submission_columns.issubset(submission_columns):
    raise SystemExit("Encrypted project submission columns are incomplete")
encrypted_submission = database.execute(
    "SELECT submitted_snapshot, snapshot_ciphertext, snapshot_iv, snapshot_key_version FROM change_submissions WHERE id = 'submission-1'"
).fetchone()
if encrypted_submission != ("", "encrypted-snapshot", "random-iv", "V1"):
    raise SystemExit("New review submissions must not store plaintext snapshots")

package_version_columns = {
    row[1]
    for row in database.execute("PRAGMA table_info(script_package_versions)")
}
required_encrypted_columns = {
    "ciphertext", "content_iv", "encrypted_data_key", "key_iv", "key_version", "source_sha256"
}
if not required_encrypted_columns.issubset(package_version_columns):
    raise SystemExit("Encrypted package version columns are incomplete")
if "source" in package_version_columns:
    raise SystemExit("Plain script source must never be stored in the package version table")

try:
    database.execute(
        "INSERT INTO script_packages "
        "(id, name, description, status, latest_version, created_by, created_at, updated_at) "
        "VALUES ('package-2', 'autu test', '', 'ACTIVE', 1, 'owner-1', 100, 100)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Package names are not unique (case-insensitive)")

foreign_key_errors = database.execute("PRAGMA foreign_key_check").fetchall()
if foreign_key_errors:
    raise SystemExit(f"Foreign key violations: {foreign_key_errors}")

print(
    f"OK: {len(required_tables)} required tables, {len(migrations)} migrations, "
    "single-owner, project membership, draft uniqueness, submissions, licenses, device limits, unique device keys, encrypted package schema and immutable versions"
)
