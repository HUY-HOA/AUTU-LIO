CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('announcement', 'community', 'update', 'account', 'system')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '{}',
    created_at INTEGER NOT NULL,
    read_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_created
ON notifications(user_id, read_at, created_at ASC);
