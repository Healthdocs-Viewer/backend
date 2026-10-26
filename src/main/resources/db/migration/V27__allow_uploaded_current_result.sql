-- An uploaded result is the current version while it awaits coordinator review.
-- Keep the current-result constraint aligned with CaseResultEntity.reserveVersion().
ALTER TABLE case_result
    DROP CONSTRAINT chk_case_result_current_status;

ALTER TABLE case_result
    ADD CONSTRAINT chk_case_result_current_status CHECK (
        NOT is_current OR status IN ('UPLOADED', 'AVAILABLE', 'CORRECTION_REQUIRED')
    );
