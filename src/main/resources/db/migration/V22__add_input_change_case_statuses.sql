-- Add input-change workflow states without rewriting existing case rows.
-- NOT VALID avoids a table rewrite/update; VALIDATE checks the existing values
-- before the migration completes and enforces the constraint for future writes.
ALTER TABLE medical_case
    ADD CONSTRAINT chk_medical_case_status CHECK (
        status IN (
            'CONSENT_PENDING',
            'FILE_PENDING',
            'CONFIRMATION_PENDING',
            'FILES_RECEIVED',
            'INPUT_CHANGE_DRAFT',
            'INPUT_CHANGE_REVIEW',
            'ENABLED_FOR_PROCESSING',
            'RESULT_UPLOADED',
            'RESULT_AVAILABLE'
        )
    ) NOT VALID;

ALTER TABLE medical_case
    VALIDATE CONSTRAINT chk_medical_case_status;
