CREATE SCHEMA IF NOT EXISTS receta;

-- episode_id and patient_id are references by value: other modules own those tables.
CREATE TABLE receta.prescription (
    id                 UUID                     NOT NULL,
    episode_id         UUID                     NOT NULL,
    patient_id         UUID                     NOT NULL,
    prescriber_subject UUID                     NOT NULL,
    content            VARCHAR(4000)            NOT NULL,
    status             VARCHAR(16)              NOT NULL,
    content_hash       VARCHAR(64),
    signed_at          TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_prescription_signed CHECK (status = 'DRAFT' OR (content_hash IS NOT NULL AND signed_at IS NOT NULL))
);
