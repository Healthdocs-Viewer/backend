ALTER TABLE auth_identity
    ADD COLUMN IF NOT EXISTS linked_by VARCHAR(255);

ALTER TABLE auth_identity
    ADD COLUMN IF NOT EXISTS verification_reference VARCHAR(255);