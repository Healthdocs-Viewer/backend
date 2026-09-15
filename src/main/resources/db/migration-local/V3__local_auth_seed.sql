INSERT INTO app_user (id, external_subject, email, role, password_hash, enabled)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'local-paciente', 'paciente@healthdocs.local', 'paciente',
     '$2b$10$qbfNcwONjE0V2IeTyWJl6.4ZW6ebeBui8vKR54dFrgNsYMYUOEEAC', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'local-estudiante', 'estudiante@healthdocs.local', 'estudiante',
     '$2b$10$PHkVQWrTe1EX/QG.t6vrxO33mheZ2a5rIIDLw4inUl7L1bcWXtL1y', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'local-profesor', 'profesor@healthdocs.local', 'profesor',
     '$2b$10$yo1ZMw4DiodPSwDx5ngVLu05MD4mjYoUu66Tlbfw5YwIGCh.CzQUy', TRUE);
