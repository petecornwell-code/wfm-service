-- App-level configuration per tenant (BambooHR server, API key, etc.)
CREATE TABLE app_configuration (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    config_key      VARCHAR(255) NOT NULL,
    config_value    TEXT NOT NULL DEFAULT '',
    UNIQUE (tenant_id, config_key)
);
