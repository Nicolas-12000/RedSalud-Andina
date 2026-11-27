CREATE SCHEMA IF NOT EXISTS identidad;

-- A professional (rural doctor or specialist) may read a patient only while a relationship exists.
CREATE TABLE identidad.care_relationship (
    id                   UUID                     NOT NULL,
    professional_subject UUID                     NOT NULL,
    patient_id           UUID                     NOT NULL,
    granted_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_care_relationship UNIQUE (professional_subject, patient_id)
);
