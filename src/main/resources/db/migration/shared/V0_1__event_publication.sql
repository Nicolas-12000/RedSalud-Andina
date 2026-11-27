-- Spring Modulith event publication registry (outbox). Owned by the platform, not by a business module.
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(4000)            NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(32),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
