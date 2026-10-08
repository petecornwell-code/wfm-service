-- quick-261008-f51 (OD-3): the inputs behind a persisted Erlang C / Erlang X calculation, saved
-- per desk, timeslot and specialization so reopening a business date reloads what produced it.
--
-- Design choices:
--
-- 1. Keyed by timeslot, which carries exactly one business date, with NO business_date column.
--    A copy of the date here would be a second writer that can drift from timeslot.business_date,
--    and it would need a setBusinessDate writer that the BDAY-02 write-path guard pins.
--
-- 2. ON DELETE CASCADE from timeslot. A call volume is the number of contacts IN that slot and is
--    meaningless under a regenerated geometry, for example a 15-to-30-minute change, and the live
--    staffing_requirement rows these inputs produced cascade on the same FK. Keying by start time
--    instead would resurrect volumes against slots of a different length.
--
-- 3. Units. service_level_target and retry_rate are percentages (80 means 80%). shrinkage and
--    max_occupancy are fractions, as the adjustments DTO carries them. NULL means off, or not
--    applicable to the model (patience and retry_rate are NULL for Erlang C). model holds
--    ERLANG_C or ERLANG_X as a plain string with no CHECK, matching staffing_requirement.source.
CREATE TABLE erlang_demand_input (
    id                      UUID PRIMARY KEY,
    tenant_id               BIGINT NOT NULL,
    desk_id                 UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    timeslot_id             UUID NOT NULL REFERENCES timeslot(id) ON DELETE CASCADE,
    specialization_id       UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    model                   VARCHAR(20) NOT NULL,
    call_volume             INTEGER NOT NULL,
    aht                     DOUBLE PRECISION NOT NULL,
    service_level_target    DOUBLE PRECISION NOT NULL,
    service_level_threshold INTEGER NOT NULL,
    patience                DOUBLE PRECISION,
    retry_rate              DOUBLE PRECISION,
    shrinkage               DOUBLE PRECISION,
    max_occupancy           DOUBLE PRECISION,
    concurrency             DOUBLE PRECISION,
    UNIQUE (tenant_id, desk_id, timeslot_id, specialization_id)
);

-- The cascade lookups from timeslot deletes need this: the unique index leads with tenant_id and
-- cannot serve a lookup by timeslot_id alone.
CREATE INDEX idx_erlang_demand_input_timeslot ON erlang_demand_input(timeslot_id);
