CREATE TABLE job_title_config (
    id               UUID PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    job_title        VARCHAR(255) NOT NULL,
    non_schedulable  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, job_title)
);

CREATE INDEX idx_job_title_config_tenant ON job_title_config(tenant_id);
