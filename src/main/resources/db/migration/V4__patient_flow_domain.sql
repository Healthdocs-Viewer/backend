ALTER TABLE medical_case
    ADD COLUMN reference_code VARCHAR(32);

ALTER TABLE medical_case
    ADD COLUMN study_type VARCHAR(100);

UPDATE medical_case
SET reference_code = 'LEGACY-' || id,
    study_type = 'UNKNOWN'
WHERE reference_code IS NULL;

ALTER TABLE medical_case
    ALTER COLUMN reference_code SET NOT NULL;

ALTER TABLE medical_case
    ALTER COLUMN study_type SET NOT NULL;

ALTER TABLE medical_case
    ADD CONSTRAINT uk_medical_case_reference_code UNIQUE (reference_code);

ALTER TABLE stored_file
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE case_consent (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES medical_case(id),
    patient_id UUID NOT NULL REFERENCES app_user(id),
    terms_version VARCHAR(50) NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_case_consent_case_terms UNIQUE (case_id, terms_version)
);

CREATE INDEX idx_case_consent_case_id ON case_consent(case_id);
CREATE INDEX idx_case_consent_patient_id ON case_consent(patient_id);
