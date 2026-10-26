ALTER TABLE medical_case
    ADD COLUMN additional_reference VARCHAR(100),
    ADD COLUMN origin_institution VARCHAR(200),
    ADD COLUMN study_date DATE,
    ADD COLUMN notes VARCHAR(500);

ALTER TABLE medical_case
    ADD CONSTRAINT chk_medical_case_additional_reference
        CHECK (additional_reference IS NULL OR (length(btrim(additional_reference)) BETWEEN 1 AND 100)),
    ADD CONSTRAINT chk_medical_case_origin_institution
        CHECK (origin_institution IS NULL OR (length(btrim(origin_institution)) BETWEEN 1 AND 200)),
    ADD CONSTRAINT chk_medical_case_notes
        CHECK (notes IS NULL OR length(notes) <= 500);
