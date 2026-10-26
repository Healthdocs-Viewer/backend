CREATE TABLE input_change_file_cleanup (
    file_id UUID PRIMARY KEY REFERENCES stored_file(id),
    request_id UUID NOT NULL REFERENCES input_change_request(id),
    eligible_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error VARCHAR(500)
);

CREATE INDEX idx_input_change_file_cleanup_due
    ON input_change_file_cleanup(eligible_at)
    WHERE processed_at IS NULL;
