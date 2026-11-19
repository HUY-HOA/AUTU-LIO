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
    "audit_logs",
    "app_settings",
    "licenses",
    "devices",
    "device_enrollment_challenges",
    "script_packages",
    "script_package_versions",
}
removed_tables = {
    "invitations",
    "permission_overrides",
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

leftover = sorted(removed_tables & actual_tables)
if leftover:
    raise SystemExit(f"Collaboration tables remain: {', '.join(leftover)}")

owner_index = database.execute(
    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_users_single_owner'"
).fetchone()
if owner_index is None:
    raise SystemExit("Missing unique single-owner index")

users = [
    ("owner-1", "google-owner", "owner@example.com", "OWNER"),
    ("customer-1", "google-customer", "customer@example.com", "CUSTOMER"),
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
    "single-owner, removed collaboration schema, licenses, device limits, unique device keys, encrypted package schema and immutable versions"
)
