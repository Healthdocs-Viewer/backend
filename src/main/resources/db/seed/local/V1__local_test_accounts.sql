-- Datos exclusivos de los perfiles local y dev. Flyway nunca ejecuta este archivo.
-- Claves públicas de desarrollo: paciente123, estudiante123 y profesor123.
INSERT INTO app_user (id, external_subject, email, role, password_hash, enabled, authorization_status)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'local:paciente', 'paciente@healthdocs.local', 'patient', '$2a$10$INChNFGqHJcLejiZ6Wag5uZ/XK8D4UI/QKShxUk.PwRNogcBKQ23G', TRUE, NULL),
    ('22222222-2222-2222-2222-222222222222', 'local:estudiante', 'estudiante@healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'AUTHORIZED'),
    ('33333333-3333-3333-3333-333333333333', 'local:profesor', 'profesor@healthdocs.local', 'coordinator', '$2a$10$VQ2NkRv2MyScru0Q5XW5WauMBGLuV5uC9l30IJdw9Zj9WFA7RYKCDy', TRUE, NULL)
ON CONFLICT (email) DO UPDATE
SET external_subject = EXCLUDED.external_subject,
    role = EXCLUDED.role,
    password_hash = EXCLUDED.password_hash,
    enabled = EXCLUDED.enabled,
    authorization_status = CASE
        WHEN EXCLUDED.role = 'researcher'
            THEN COALESCE(app_user.authorization_status, EXCLUDED.authorization_status)
        ELSE NULL
    END;
