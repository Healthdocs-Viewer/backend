-- Normaliza los roles persistidos antes de restringir los valores admitidos.
UPDATE app_user
SET role = CASE role
    WHEN 'paciente' THEN 'patient'
    WHEN 'estudiante' THEN 'researcher'
    WHEN 'profesor' THEN 'coordinator'
    ELSE role
END
WHERE role IN ('paciente', 'estudiante', 'profesor');

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_role
        CHECK (role IN ('patient', 'researcher', 'coordinator', 'admin'));
