-- Allow multiple result rows per case while preserving every existing row and file.
ALTER TABLE case_result
    DROP CONSTRAINT IF EXISTS case_result_case_id_key;

ALTER TABLE case_result
    ADD COLUMN version_number INTEGER,
    ADD COLUMN publication_type VARCHAR(30),
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE;

-- Published legacy results are the first and currently published version.
UPDATE case_result
SET version_number = 1,
    publication_type = 'INITIAL',
    is_current = TRUE
WHERE status = 'AVAILABLE';

-- UPLOADED rows stay pending. Their publication transaction will assign the next
-- version number and INITIAL/CORRECTED type when the row is actually published.

ALTER TABLE case_result
    ADD CONSTRAINT chk_case_result_version_metadata CHECK (
        (version_number IS NULL AND publication_type IS NULL)
        OR (version_number IS NOT NULL AND version_number > 0
            AND publication_type IS NOT NULL
            AND publication_type IN ('INITIAL', 'CORRECTED'))
    ),
    ADD CONSTRAINT chk_case_result_current_is_published CHECK (
        NOT is_current OR (status = 'AVAILABLE' AND version_number IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_case_result_case_version
    ON case_result(case_id, version_number)
    WHERE version_number IS NOT NULL;

CREATE UNIQUE INDEX uq_case_result_one_current_per_case
    ON case_result(case_id)
    WHERE is_current;

CREATE INDEX idx_case_result_case_current
    ON case_result(case_id, is_current);
