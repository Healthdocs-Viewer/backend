CREATE TABLE auth_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_identity_provider_issuer_subject
        UNIQUE (provider, issuer, subject)
);

CREATE INDEX idx_auth_identity_user_id ON auth_identity(user_id);
