CREATE SCHEMA IF NOT EXISTS interconsulta;

-- booking_id, episode_id and patient_id are references by value: other modules own those tables.
CREATE TABLE interconsulta.consult (
    id            UUID                     NOT NULL,
    episode_id    UUID                     NOT NULL,
    patient_id    UUID                     NOT NULL,
    requested_by  UUID                     NOT NULL,
    specialty     VARCHAR(100)             NOT NULL,
    center_id     VARCHAR(64),
    status        VARCHAR(16)              NOT NULL,
    booking_id    UUID,
    specialist_id UUID,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT                   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_consult_status CHECK (status IN ('REQUESTED', 'SCHEDULED', 'ATTENDED', 'CLOSED'))
);

CREATE TABLE interconsulta.alert (
    id                  UUID                     NOT NULL,
    consult_id          UUID                     NOT NULL,
    recipient_subject   UUID,
    recipient_specialty VARCHAR(100),
    kind                VARCHAR(16)              NOT NULL,
    message             VARCHAR(500)             NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_consult FOREIGN KEY (consult_id) REFERENCES interconsulta.consult (id),
    CONSTRAINT ck_alert_recipient CHECK (recipient_subject IS NOT NULL OR recipient_specialty IS NOT NULL)
);
CREATE INDEX idx_alert_subject ON interconsulta.alert (recipient_subject, created_at);
CREATE INDEX idx_alert_specialty ON interconsulta.alert (recipient_specialty, created_at);
