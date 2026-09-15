CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medical_case (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES app_user(id),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stored_file (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES medical_case(id),
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    file_kind VARCHAR(50) NOT NULL,
    original_filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE case_result (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL UNIQUE REFERENCES medical_case(id),
    file_id UUID NOT NULL REFERENCES stored_file(id),
    status VARCHAR(50) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_medical_case_patient_id ON medical_case(patient_id);
CREATE INDEX idx_stored_file_case_id ON stored_file(case_id);
