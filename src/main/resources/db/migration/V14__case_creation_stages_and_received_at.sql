ALTER TABLE medical_case
    ADD COLUMN creation_stage VARCHAR(40),
    ADD COLUMN received_at TIMESTAMP WITH TIME ZONE;


ALTER TABLE stored_file
    ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;

-- Existing CREATED cases have persisted study information. Consent is the next
-- required step unless it already exists, in which case file upload is next.
UPDATE medical_case c
SET creation_stage = CASE
    WHEN EXISTS (SELECT 1 FROM case_consent cc WHERE cc.case_id = c.id) THEN 'FILE_PENDING'
    ELSE 'CONSENT_PENDING'
END
WHERE c.status = 'CREATED';

-- Advanced legacy cases have completed initial registration. Their actual receipt
-- timestamp cannot be reconstructed from updated_at or stored_file.updated_at.
UPDATE medical_case
SET creation_stage = NULL, received_at = NULL
WHERE status <> 'CREATED';

ALTER TABLE medical_case
    ADD CONSTRAINT chk_medical_case_creation_stage CHECK (
        creation_stage IS NULL OR creation_stage IN (
            'INFORMATION_PENDING', 'CONSENT_PENDING', 'FILE_PENDING', 'CONFIRMATION_PENDING'
        )
    );

CREATE INDEX idx_medical_case_patient_received_at ON medical_case(patient_id, received_at);
CREATE INDEX idx_stored_file_case_confirmation ON stored_file(case_id, file_kind, confirmed_at);
