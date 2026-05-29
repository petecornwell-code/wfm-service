CREATE TABLE bamboo_sync_event (
    id                   UUID PRIMARY KEY,
    tenant_id            BIGINT NOT NULL,
    desk_id              UUID NULL,
    started_at           TIMESTAMPTZ NOT NULL,
    finished_at          TIMESTAMPTZ NULL,
    success              BOOLEAN NOT NULL,
    error_message        TEXT NULL,
    agents_synced        INTEGER NULL,
    time_off_pulled      INTEGER NULL,
    retry_after_seconds  INTEGER NULL
);

CREATE INDEX idx_bamboo_sync_event_tenant_started ON bamboo_sync_event(tenant_id, started_at DESC);
