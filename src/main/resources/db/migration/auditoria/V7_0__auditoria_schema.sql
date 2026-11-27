CREATE SCHEMA IF NOT EXISTS auditoria;

-- Append-only: the application only inserts and selects. Never UPDATE or DELETE here.
CREATE TABLE auditoria.audit_record (
    id              UUID                     NOT NULL,
    kind            VARCHAR(32)              NOT NULL,
    actor           UUID                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_type   VARCHAR(64)              NOT NULL,
    resource_id     VARCHAR(128)             NOT NULL,
    reason          VARCHAR(500),
    field_name      VARCHAR(64),
    kept_value      VARCHAR(4000),
    discarded_value VARCHAR(4000),
    PRIMARY KEY (id)
);
CREATE INDEX idx_audit_resource ON auditoria.audit_record (resource_type, resource_id, occurred_at);
