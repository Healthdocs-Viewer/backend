-- Existing INPUT files remain current. Files uploaded into an input-change
-- proposal are created with input_active = false until coordinator approval.
ALTER TABLE stored_file
    ADD COLUMN input_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_stored_file_current_input
    ON stored_file(case_id)
    WHERE file_kind = 'INPUT' AND input_active = TRUE;
