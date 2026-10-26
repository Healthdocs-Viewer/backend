ALTER TABLE stored_file
    ADD COLUMN upload_expires_at TIMESTAMPTZ;

-- Older upload intents used the default 15-minute storage URL lifetime but did
-- not persist it. Backdate those expirations so they do not block new uploads.
UPDATE stored_file
SET upload_expires_at = created_at + INTERVAL '15 minutes'
WHERE upload_expires_at IS NULL
  AND file_kind = 'INPUT'
  AND confirmed_at IS NULL;

CREATE INDEX idx_stored_file_active_upload_intent
    ON stored_file (case_id, file_kind, upload_expires_at)
    WHERE confirmed_at IS NULL;
