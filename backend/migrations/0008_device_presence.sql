-- Separate real app/runtime presence from device authorization state.
-- Timestamps are updated only by authenticated app foreground or active runner heartbeats.
ALTER TABLE devices ADD COLUMN presence_seen_at INTEGER;
ALTER TABLE devices ADD COLUMN app_foreground_at INTEGER;
ALTER TABLE devices ADD COLUMN runtime_state TEXT NOT NULL DEFAULT 'UNKNOWN'
  CHECK (runtime_state IN ('UNKNOWN', 'RUNNING', 'PAUSED', 'STOPPED'));

CREATE INDEX IF NOT EXISTS idx_devices_presence_seen
ON devices(presence_seen_at DESC);
