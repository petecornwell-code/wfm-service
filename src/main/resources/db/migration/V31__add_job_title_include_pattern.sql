-- Tenant-configurable job-title allowlist for desk-assignment template + upload.
--
-- Semantics (deliberately fail-open on empty): when a tenant has ZERO patterns the
-- allowlist is INACTIVE and every job title passes, so existing tenants keep their
-- current behaviour after this migration. Once at least one pattern exists the
-- allowlist is ACTIVE and a job title must contain one of the patterns
-- (case-insensitive substring) to be included.
--
-- This is an allowlist and complements the existing job_title_config.non_schedulable
-- denylist; both are applied, and either can exclude an agent.
CREATE TABLE job_title_include_pattern (
    id          UUID PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    pattern     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, pattern)
);

CREATE INDEX idx_job_title_include_pattern_tenant ON job_title_include_pattern(tenant_id);
