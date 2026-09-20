ALTER TABLE case_result ADD COLUMN IF NOT EXISTS available_by UUID REFERENCES app_user(id);
