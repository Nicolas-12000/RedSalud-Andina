CREATE SCHEMA IF NOT EXISTS analitica;

-- Fed only by events from other modules (an export): indicators never read transactional tables.
-- No raw patient identifier is stored: only a keyed hash.
CREATE TABLE analitica.episode_fact (
    episode_id        UUID                     NOT NULL,
    center_id         VARCHAR(64)              NOT NULL,
    patient_pseudonym VARCHAR(64)              NOT NULL,
    opened_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (episode_id)
);
CREATE INDEX idx_episode_fact_center ON analitica.episode_fact (center_id);

CREATE TABLE analitica.consult_fact (
    consult_id   UUID                     NOT NULL,
    center_id    VARCHAR(64)              NOT NULL,
    specialty    VARCHAR(100)             NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE,
    attended_at  TIMESTAMP WITH TIME ZONE,
    closed_at    TIMESTAMP WITH TIME ZONE,
    wait_seconds BIGINT,
    version      BIGINT                   NOT NULL,
    PRIMARY KEY (consult_id)
);
CREATE INDEX idx_consult_fact_center ON analitica.consult_fact (center_id);
