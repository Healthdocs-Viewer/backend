-- Move cases created by the legacy CREATED + creation_stage model to the
-- single status model defined by OpenAPI 0.7.0.
UPDATE medical_case c
SET status = CASE
    WHEN NOT EXISTS (
        SELECT 1 FROM case_consent cc WHERE cc.case_id = c.id
    ) THEN 'CONSENT_PENDING'
    WHEN c.creation_stage = 'CONFIRMATION_PENDING'
      OR EXISTS (
          SELECT 1 FROM stored_file sf
          WHERE sf.case_id = c.id
            AND sf.file_kind = 'INPUT'
            AND sf.confirmed_at IS NOT NULL
      ) THEN 'CONFIRMATION_PENDING'
    ELSE 'FILE_PENDING'
END
WHERE c.status = 'CREATED';

ALTER TABLE medical_case
    DROP CONSTRAINT chk_medical_case_creation_stage,
    DROP COLUMN creation_stage;
