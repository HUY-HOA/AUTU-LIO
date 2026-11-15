CREATE UNIQUE INDEX IF NOT EXISTS idx_users_single_owner
ON users(role)
WHERE role = 'OWNER';
