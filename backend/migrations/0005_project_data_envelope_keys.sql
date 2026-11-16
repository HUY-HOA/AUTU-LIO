ALTER TABLE script_drafts ADD COLUMN source_encrypted_data_key TEXT;
ALTER TABLE script_drafts ADD COLUMN source_key_iv TEXT;

ALTER TABLE change_submissions ADD COLUMN snapshot_encrypted_data_key TEXT;
ALTER TABLE change_submissions ADD COLUMN snapshot_key_iv TEXT;
