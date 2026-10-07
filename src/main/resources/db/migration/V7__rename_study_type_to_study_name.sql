ALTER TABLE medical_case
    RENAME COLUMN study_type TO study_name;

ALTER TABLE medical_case
    ALTER COLUMN study_name TYPE VARCHAR(255);

UPDATE medical_case
SET study_name = CASE study_name
    WHEN 'RADIOGRAFIA_TORAX' THEN 'Radiografía de tórax'
    WHEN 'RESONANCIA_CEREBRAL' THEN 'Resonancia cerebral'
    WHEN 'TOMOGRAFIA_ABDOMINAL' THEN 'Tomografía abdominal'
    WHEN 'ECOGRAFIA_GENERAL' THEN 'Ecografía general'
    WHEN 'OTRO_ESTUDIO' THEN 'Otro estudio'
    WHEN 'UNKNOWN' THEN 'Estudio no especificado'
    ELSE study_name
END
WHERE study_name IN (
    'RADIOGRAFIA_TORAX',
    'RESONANCIA_CEREBRAL',
    'TOMOGRAFIA_ABDOMINAL',
    'ECOGRAFIA_GENERAL',
    'OTRO_ESTUDIO',
    'UNKNOWN'
);
