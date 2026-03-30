-- Track per-date acceptance status for schedules.
-- Only one schedule can be ACCEPTED for a given (tenant, desk, date).

CREATE TABLE accepted_schedule_date (
    schedule_id UUID NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    tenant_id   BIGINT NOT NULL,
    desk_id     UUID NOT NULL,
    date        DATE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    PRIMARY KEY (schedule_id, date)
);

-- Enforce at most one ACCEPTED schedule per desk+date
CREATE UNIQUE INDEX idx_accepted_schedule_date_active
    ON accepted_schedule_date(tenant_id, desk_id, date)
    WHERE status = 'ACCEPTED';

CREATE INDEX idx_accepted_schedule_date_desk
    ON accepted_schedule_date(tenant_id, desk_id);

-- Add optimistic locking version column to schedule
ALTER TABLE schedule ADD COLUMN version INT NOT NULL DEFAULT 0;
