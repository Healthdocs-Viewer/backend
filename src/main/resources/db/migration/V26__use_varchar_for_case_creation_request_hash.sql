ALTER TABLE case_creation_idempotency
    ALTER COLUMN request_hash TYPE VARCHAR(64)
    USING request_hash::VARCHAR(64);
