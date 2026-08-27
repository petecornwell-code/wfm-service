-- Phase 15 Plan 03: Shift Envelope, Breaks & Library Generation -- the second planning entity
-- (D-04/D-05) and the hard-constraint weight that couples it to seat assignment (P-16).
--
-- agent_shift_assignment holds one row per working agent-day on a shift-scheduled desk (D-05):
-- SolverService.buildShiftAssignments emits a row for every AgentDayConfig entry whose
-- effectiveHours > 0 -- the SAME fact the entity-level value range filter reads, so entity
-- creation and the filter can never disagree by construction. Every LIVE row leaves
-- template_name/shift_start_time/shift_end_time/band_offset_minutes/band_duration_minutes/
-- source_template_id NULL -- they are populated only at accept time (D-07), denormalising the
-- resolved envelope so a later edit to the live shift_template can never rewrite what history
-- says an agent actually worked.
--
-- VARCHAR, never CHAR, for template_name -- the exact V39 mismatch that applied cleanly and then
-- failed application boot under ddl-auto=validate with a fully green 402-test suite (UAT gap
-- G-14-1). The index mirrors how agent_assignment is queried (tenant_id, desk_id, schedule_id).
CREATE TABLE agent_shift_assignment (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    desk_id UUID NOT NULL,
    schedule_id UUID NOT NULL,
    agent_id UUID NOT NULL REFERENCES agent(id),
    date DATE NOT NULL,
    template_name VARCHAR(255),
    shift_start_time TIME,
    shift_end_time TIME,
    band_offset_minutes INTEGER,
    band_duration_minutes INTEGER,
    source_template_id UUID
);

CREATE INDEX idx_agent_shift_assignment_tenant_desk_schedule
    ON agent_shift_assignment (tenant_id, desk_id, schedule_id);

-- shift_envelope_compliance_weight: hard by default -- an agent seated outside their chosen
-- envelope is an illegal schedule, not a preference, mirroring the reasoning V37/V38 both record
-- for their own weight columns. ConstraintWeights is a @ConstraintConfiguration, so hard-vs-soft
-- is this column's value rather than a code decision.
ALTER TABLE constraint_weights
    ADD COLUMN shift_envelope_compliance_weight VARCHAR(50) NOT NULL DEFAULT '1hard/0soft';
