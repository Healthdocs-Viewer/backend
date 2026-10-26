ALTER TABLE stored_file
    ADD COLUMN upload_initiated_by UUID REFERENCES app_user(id);

-- Existing INPUT intents belong to the case owner. Result intents that already
-- became case_result records retain their recorded uploader when available.
UPDATE stored_file f
SET upload_initiated_by = (
    SELECT c.patient_id FROM medical_case c WHERE c.id = f.case_id
)
WHERE f.file_kind = 'INPUT';

UPDATE stored_file f
SET upload_initiated_by = (
    SELECT r.uploaded_by FROM case_result r WHERE r.file_id = f.id
)
WHERE f.file_kind = 'RESULT'
  AND EXISTS (SELECT 1 FROM case_result r WHERE r.file_id = f.id AND r.uploaded_by IS NOT NULL);

-- Unconfirmed legacy RESULT intents have no persisted actor. Backfill only when
-- exactly one researcher was assigned to the case; ambiguous intents remain
-- unclaimable and can be replaced with a new upload intent.
UPDATE stored_file f
SET upload_initiated_by = (
    SELECT a.user_id
    FROM case_assignment a
    JOIN app_user u ON u.id = a.user_id
    WHERE a.case_id = f.case_id
      AND u.role = 'researcher'
)
WHERE f.file_kind = 'RESULT'
  AND f.upload_initiated_by IS NULL
  AND (
      SELECT count(*)
      FROM case_assignment a
      JOIN app_user u ON u.id = a.user_id
      WHERE a.case_id = f.case_id
        AND u.role = 'researcher'
  ) = 1;
