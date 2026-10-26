-- Dataset local de demostración. No forma parte de Flyway y puede ejecutarse repetidamente.
-- Los IDs y perfiles corresponden a academic-students.json; researcher_code conserva
-- los códigos usados por los mocks por compatibilidad con su contrato vigente.
INSERT INTO app_user (
    id, external_subject, email, role, password_hash, enabled,
    full_name, researcher_code, authorization_status
)
VALUES
    ('a0000001-0000-4000-8000-000000000001', 'local:demo:researcher:EST-1187', 'investigador.est-1187@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Ana Ruiz', 'EST-1187', 'AUTHORIZED'),
    ('a0000002-0000-4000-8000-000000000002', 'local:demo:researcher:EST-1042', 'investigador.est-1042@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Laura Gómez', 'EST-1042', 'AUTHORIZED'),
    ('a0000003-0000-4000-8000-000000000003', 'local:demo:researcher:EST-1205', 'investigador.est-1205@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Mateo Sánchez', 'EST-1205', 'AUTHORIZED'),
    ('a0000004-0000-4000-8000-000000000004', 'local:demo:researcher:EST-0998', 'investigador.est-0998@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Camila Vega', 'EST-0998', 'AUTHORIZED'),
    ('a0000005-0000-4000-8000-000000000005', 'local:demo:researcher:EST-1076', 'investigador.est-1076@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Daniel Ramírez', 'EST-1076', 'AUTHORIZED'),
    ('a0000006-0000-4000-8000-000000000006', 'local:demo:researcher:EST-1033', 'investigador.est-1033@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Paula Torres', 'EST-1033', 'AUTHORIZED'),
    ('a0000007-0000-4000-8000-000000000007', 'local:demo:researcher:EST-1101', 'investigador.est-1101@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Sofía Herrera', 'EST-1101', 'RESTRICTED'),
    ('a0000008-0000-4000-8000-000000000008', 'local:demo:researcher:EST-1250', 'investigador.est-1250@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Juan Castillo', 'EST-1250', 'AUTHORIZED'),
    ('a0000009-0000-4000-8000-000000000009', 'local:demo:researcher:EST-1190', 'investigador.est-1190@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Mariana León', 'EST-1190', 'AUTHORIZED'),
    ('a0000010-0000-4000-8000-000000000010', 'local:demo:researcher:EST-1021', 'investigador.est-1021@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Felipe Moreno', 'EST-1021', 'AUTHORIZED'),
    ('a0000011-0000-4000-8000-000000000011', 'local:demo:researcher:EST-1114', 'investigador.est-1114@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Valentina Díaz', 'EST-1114', 'AUTHORIZED'),
    ('a0000012-0000-4000-8000-000000000012', 'local:demo:researcher:EST-1128', 'investigador.est-1128@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Andrés Castro', 'EST-1128', 'AUTHORIZED'),
    ('a0000013-0000-4000-8000-000000000013', 'local:demo:researcher:EST-1136', 'investigador.est-1136@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Sara Rojas', 'EST-1136', 'AUTHORIZED'),
    ('a0000014-0000-4000-8000-000000000014', 'local:demo:researcher:EST-1144', 'investigador.est-1144@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Nicolás Molina', 'EST-1144', 'AUTHORIZED'),
    ('a0000015-0000-4000-8000-000000000015', 'local:demo:researcher:EST-1152', 'investigador.est-1152@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Elena Navarro', 'EST-1152', 'AUTHORIZED'),
    ('a0000016-0000-4000-8000-000000000016', 'local:demo:researcher:EST-1160', 'investigador.est-1160@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Tomás Salazar', 'EST-1160', 'AUTHORIZED'),
    ('a0000017-0000-4000-8000-000000000017', 'local:demo:researcher:EST-1168', 'investigador.est-1168@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Isabel Méndez', 'EST-1168', 'AUTHORIZED'),
    ('a0000018-0000-4000-8000-000000000018', 'local:demo:researcher:EST-1176', 'investigador.est-1176@demo.healthdocs.local', 'researcher', '$2a$10$Dfdzsh/QW8y6bdleqyMVVO6Rm9yigjKc1QaY8Mrs5FV7p0nTzXnu.', TRUE, 'Óscar Pardo', 'EST-1176', 'AUTHORIZED')
ON CONFLICT (id) DO UPDATE
SET full_name = EXCLUDED.full_name,
    researcher_code = EXCLUDED.researcher_code,
    authorization_status = CASE
        WHEN app_user.authorization_status = 'RESTRICTED' THEN 'RESTRICTED'
        ELSE EXCLUDED.authorization_status
    END
WHERE app_user.external_subject = EXCLUDED.external_subject
  AND app_user.email = EXCLUDED.email
  AND app_user.role = 'researcher';

-- Backfill ficticio solo para la base local/dev y solo donde falten metadatos.
-- Producción no carga este seed y conserva NULL en casos históricos.
UPDATE medical_case
SET origin_institution = COALESCE(origin_institution, 'Centro Médico de Prueba'),
    study_date = COALESCE(study_date, (created_at AT TIME ZONE 'UTC')::date),
    additional_reference = COALESCE(additional_reference, 'Caso de prueba'),
    notes = COALESCE(notes, 'Datos de prueba para visualización local.')
WHERE origin_institution IS NULL OR study_date IS NULL
   OR additional_reference IS NULL OR notes IS NULL;
