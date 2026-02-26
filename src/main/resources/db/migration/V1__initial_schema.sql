-- WFM Service initial schema
-- All tenant-owned tables have tenant_id BIGINT NOT NULL
-- Desk-scoped tables additionally have desk_id UUID NOT NULL

-- ============================================================
-- Tenant-level tables
-- ============================================================

CREATE TABLE desk (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    default_contracted_hours_per_day NUMERIC(5,2) DEFAULT 8.00,
    UNIQUE (tenant_id, name)
);

CREATE TABLE agent (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    bamboohr_id     VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    department      VARCHAR(255),
    job_title       VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    last_refreshed_at TIMESTAMPTZ,
    UNIQUE (tenant_id, bamboohr_id)
);

CREATE TABLE agent_day_off (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    agent_id        UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    type            VARCHAR(20) NOT NULL,
    UNIQUE (agent_id, date)
);

CREATE INDEX idx_agent_day_off_tenant ON agent_day_off(tenant_id);
CREATE INDEX idx_agent_day_off_agent_date ON agent_day_off(agent_id, date);

-- ============================================================
-- Desk-scoped tables
-- ============================================================

CREATE TABLE specialization (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    desk_id         UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    UNIQUE (tenant_id, desk_id, name)
);

CREATE TABLE desk_agent (
    id                          UUID PRIMARY KEY,
    tenant_id                   BIGINT NOT NULL,
    desk_id                     UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    agent_id                    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    primary_specialization_id   UUID REFERENCES specialization(id) ON DELETE SET NULL,
    contracted_hours_per_day    NUMERIC(5,2),
    UNIQUE (tenant_id, desk_id, agent_id),
    UNIQUE (tenant_id, agent_id)
);

CREATE TABLE desk_agent_secondary_specialization (
    desk_agent_id       UUID NOT NULL REFERENCES desk_agent(id) ON DELETE CASCADE,
    specialization_id   UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    PRIMARY KEY (desk_agent_id, specialization_id)
);

CREATE TABLE agent_preference (
    id                      UUID PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    desk_id                 UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    agent_id                UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    day_of_week             VARCHAR(10) NOT NULL,
    date                    DATE,
    is_standing             BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_start_time    TIME,
    preferred_break_time    TIME
);

-- Standing: unique per (desk, agent, day_of_week) where is_standing = true
CREATE UNIQUE INDEX idx_agent_preference_standing
    ON agent_preference(tenant_id, desk_id, agent_id, day_of_week)
    WHERE is_standing = TRUE;

-- Weekly: unique per (desk, agent, date) where is_standing = false
CREATE UNIQUE INDEX idx_agent_preference_weekly
    ON agent_preference(tenant_id, desk_id, agent_id, date)
    WHERE is_standing = FALSE;

CREATE TABLE agent_exception (
    id                          UUID PRIMARY KEY,
    tenant_id                   BIGINT NOT NULL,
    desk_id                     UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    agent_id                    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    date                        DATE NOT NULL,
    contracted_hours_override   NUMERIC(5,2) NOT NULL,
    reason                      TEXT NOT NULL,
    UNIQUE (tenant_id, desk_id, agent_id, date)
);

CREATE TABLE constraint_weights (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    desk_id         UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,

    -- Each weight stored as two int columns (hard_score, soft_score)
    agent_day_off_weight_hard_score         INT NOT NULL DEFAULT 1,
    agent_day_off_weight_soft_score         INT NOT NULL DEFAULT 0,
    spec_match_weight_hard_score            INT NOT NULL DEFAULT 1,
    spec_match_weight_soft_score            INT NOT NULL DEFAULT 0,
    no_overlap_weight_hard_score            INT NOT NULL DEFAULT 1,
    no_overlap_weight_soft_score            INT NOT NULL DEFAULT 0,
    exactly_one_break_weight_hard_score     INT NOT NULL DEFAULT 1,
    exactly_one_break_weight_soft_score     INT NOT NULL DEFAULT 0,
    break_duration_weight_hard_score        INT NOT NULL DEFAULT 1,
    break_duration_weight_soft_score        INT NOT NULL DEFAULT 0,
    break_blocked_window_weight_hard_score  INT NOT NULL DEFAULT 1,
    break_blocked_window_weight_soft_score  INT NOT NULL DEFAULT 0,
    break_alignment_weight_hard_score       INT NOT NULL DEFAULT 1,
    break_alignment_weight_soft_score       INT NOT NULL DEFAULT 0,
    prefer_primary_weight_hard_score        INT NOT NULL DEFAULT 0,
    prefer_primary_weight_soft_score        INT NOT NULL DEFAULT 1,
    honour_start_time_weight_hard_score     INT NOT NULL DEFAULT 0,
    honour_start_time_weight_soft_score     INT NOT NULL DEFAULT 1,
    honour_break_time_weight_hard_score     INT NOT NULL DEFAULT 0,
    honour_break_time_weight_soft_score     INT NOT NULL DEFAULT 1,
    break_clustering_weight_hard_score      INT NOT NULL DEFAULT 0,
    break_clustering_weight_soft_score      INT NOT NULL DEFAULT 2,
    contracted_hours_weight_hard_score      INT NOT NULL DEFAULT 1,
    contracted_hours_weight_soft_score      INT NOT NULL DEFAULT 0,
    bulk_overallocation_limit_weight_hard_score  INT NOT NULL DEFAULT 1,
    bulk_overallocation_limit_weight_soft_score  INT NOT NULL DEFAULT 0,
    bulk_underallocation_soft_weight_hard_score  INT NOT NULL DEFAULT 0,
    bulk_underallocation_soft_weight_soft_score  INT NOT NULL DEFAULT 1,
    bulk_underallocation_hard_weight_hard_score  INT NOT NULL DEFAULT 1,
    bulk_underallocation_hard_weight_soft_score  INT NOT NULL DEFAULT 0,

    UNIQUE (tenant_id, desk_id)
);

CREATE TABLE schedule (
    id                              UUID PRIMARY KEY,
    tenant_id                       BIGINT NOT NULL,
    desk_id                         UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    status                          VARCHAR(20) NOT NULL,
    increment_minutes               INT NOT NULL,
    start_time                      TIME NOT NULL,
    end_time                        TIME NOT NULL,
    period_start_date               DATE NOT NULL,
    period_end_date                 DATE NOT NULL,
    break_blocked_hours             NUMERIC(5,2) DEFAULT 1.00,
    break_duration_minutes          INT DEFAULT 60,
    break_min_shift_hours           NUMERIC(5,2) DEFAULT 4.00,
    break_start_alignment           VARCHAR(20) DEFAULT 'ON_HOUR',
    break_cluster_threshold_pct     INT DEFAULT 20,
    default_contracted_hours_per_day NUMERIC(5,2) DEFAULT 8.00,
    overallocation_hard_limit_pct   INT DEFAULT 130,
    underallocation_hard_limit_pct  INT DEFAULT 70,
    hard_score                      INT,
    soft_score                      INT,
    error_message                   TEXT,
    created_at                      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_schedule_desk ON schedule(tenant_id, desk_id);

CREATE TABLE timeslot (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    desk_id         UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    schedule_id     UUID REFERENCES schedule(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL
);

-- Live timeslots: unique per desk+date+time where schedule_id IS NULL
CREATE UNIQUE INDEX idx_timeslot_live
    ON timeslot(tenant_id, desk_id, date, start_time, end_time)
    WHERE schedule_id IS NULL;

CREATE INDEX idx_timeslot_desk_date ON timeslot(tenant_id, desk_id, date);
CREATE INDEX idx_timeslot_schedule ON timeslot(schedule_id) WHERE schedule_id IS NOT NULL;

CREATE TABLE staffing_requirement (
    id                  UUID PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    desk_id             UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    schedule_id         UUID REFERENCES schedule(id) ON DELETE CASCADE,
    timeslot_id         UUID NOT NULL REFERENCES timeslot(id) ON DELETE CASCADE,
    specialization_id   UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    required_agents     INT NOT NULL,
    source              VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
);

-- Live staffing requirements: unique per desk+timeslot+specialization where schedule_id IS NULL
CREATE UNIQUE INDEX idx_staffing_requirement_live
    ON staffing_requirement(tenant_id, desk_id, timeslot_id, specialization_id)
    WHERE schedule_id IS NULL;

CREATE INDEX idx_staffing_requirement_schedule ON staffing_requirement(schedule_id) WHERE schedule_id IS NOT NULL;

CREATE TABLE agent_assignment (
    id                  UUID PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    desk_id             UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    schedule_id         UUID NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    timeslot_id         UUID NOT NULL REFERENCES timeslot(id) ON DELETE CASCADE,
    specialization_id   UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    desk_agent_id       UUID REFERENCES desk_agent(id) ON DELETE SET NULL,
    agent_id            UUID REFERENCES agent(id) ON DELETE SET NULL
);

CREATE INDEX idx_agent_assignment_schedule ON agent_assignment(schedule_id);
CREATE INDEX idx_agent_assignment_timeslot ON agent_assignment(timeslot_id);
