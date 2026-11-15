from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
database = sqlite3.connect(":memory:")
database.execute("PRAGMA foreign_keys = ON")

for migration in sorted((root / "migrations").glob("*.sql")):
    database.executescript(migration.read_text(encoding="utf-8"))

required_tables = {
    "users",
    "auth_challenges",
    "sessions",
    "invitations",
    "permission_overrides",
    "audit_logs",
    "app_settings",
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

database.execute(
    "INSERT INTO users (id, google_sub, email, role, status, created_at, updated_at) "
    "VALUES ('owner-1', 'google-1', 'owner@example.com', 'OWNER', 'ACTIVE', 1, 1)"
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

print(f"OK: {len(required_tables)} required tables and single-owner constraint")
