ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE case_assignment (
    case_id UUID NOT NULL REFERENCES medical_case(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, user_id)
);

CREATE INDEX idx_case_assignment_user_id ON case_assignment(user_id);
