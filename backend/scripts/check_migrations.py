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
    "(id, project_id, name, source, language_version, revision, created_by, updated_by, created_at, updated_at) "
    "VALUES ('script-1', 'project-1', 'Example', 'tap(10, 20)', '0.1', 1, 'developer-1', 'developer-1', 1, 1)"
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
    "(id, project_id, kind, item_id, revision, submitted_snapshot, submitted_by, status, created_at) "
    "VALUES ('submission-1', 'project-1', 'SCRIPT', 'script-1', 1, '{\"name\":\"Example\"}', 'developer-1', 'PENDING', 1)"
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

foreign_key_errors = database.execute("PRAGMA foreign_key_check").fetchall()
if foreign_key_errors:
    raise SystemExit(f"Foreign key violations: {foreign_key_errors}")

print(
    f"OK: {len(required_tables)} required tables, {len(migrations)} migrations, "
    "single-owner, project membership, draft uniqueness and submission constraints"
)
