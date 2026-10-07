ALTER TABLE auth_identity
    ADD COLUMN linked_by VARCHAR(255),
    ADD COLUMN verification_reference VARCHAR(255);