ALTER TABLE script_drafts ADD COLUMN source_ciphertext TEXT;
ALTER TABLE script_drafts ADD COLUMN source_iv TEXT;
ALTER TABLE script_drafts ADD COLUMN source_key_version TEXT;

ALTER TABLE change_submissions ADD COLUMN snapshot_ciphertext TEXT;
ALTER TABLE change_submissions ADD COLUMN snapshot_iv TEXT;
ALTER TABLE change_submissions ADD COLUMN snapshot_key_version TEXT;
