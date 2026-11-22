-- Track why a device was revoked so customer self-removal can be reversed
-- with a fresh proof, while owner/account revocations remain owner-controlled.
ALTER TABLE devices ADD COLUMN revoked_reason TEXT;

-- Existing records predate reason tracking. Treat them conservatively: only
-- the owner may explicitly restore a legacy-revoked device.
UPDATE devices
SET revoked_reason = 'LEGACY_REVOKED'
WHERE status = 'REVOKED' AND revoked_reason IS NULL;
