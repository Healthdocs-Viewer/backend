ALTER TABLE app_user
    ADD COLUMN full_name VARCHAR(200),
    ADD COLUMN researcher_code VARCHAR(50),
    ADD COLUMN authorization_status VARCHAR(20);

-- Legacy identity/profile data is absent, so do not invent names or codes.
-- Every existing researcher (including roles normalized by V12) is authorized by D-01.
UPDATE app_user
SET authorization_status = 'AUTHORIZED'
WHERE role = 'researcher';

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_authorization_status
        CHECK (authorization_status IS NULL OR authorization_status IN ('AUTHORIZED', 'RESTRICTED')),
    ADD CONSTRAINT chk_app_user_researcher_authorization
        CHECK ((role = 'researcher' AND authorization_status IS NOT NULL)
            OR (role <> 'researcher' AND authorization_status IS NULL));

CREATE UNIQUE INDEX uq_app_user_researcher_code
    ON app_user (UPPER(researcher_code))
    WHERE researcher_code IS NOT NULL;

CREATE INDEX idx_app_user_researcher_directory
    ON app_user (role, authorization_status, email);

