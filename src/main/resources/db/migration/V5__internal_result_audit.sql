ALTER TABLE app_user ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE case_result ADD COLUMN IF NOT EXISTS uploaded_by UUID REFERENCES app_user(id);
ALTER TABLE case_result ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_case_assignment_user_case ON case_assignment(user_id, case_id);
CREATE INDEX IF NOT EXISTS idx_stored_file_case_kind ON stored_file(case_id, file_kind);
