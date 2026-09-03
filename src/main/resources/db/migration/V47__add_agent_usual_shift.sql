-- Phase 16: Usual Shift Storage (plan 16-01, tracer slice).
--
-- New table `agent_usual_shift` -- the per-weekday TARGET an agent is scheduled towards (D-01),
-- distinct from `agent_shift_assignment` (the solved RESULT, Phase 15). Stores a real FK to
-- shift_template; there is deliberately no denormalized template-name column here (P-01) --
-- AgentUsualShift is a live target that must always reflect current truth, unlike
-- AgentShiftAssignment's frozen-history templateName/sourceTemplateId pair.
--
-- day_of_week is declared as a variable-length 9-character string below, never a fixed-length
-- blank-padded type: the entity maps a @Enumerated(STRING) DayOfWeek with @Column(length = 9),
-- matching agent_day_hours' own V29 column. V39's migration header records the production
-- incident this codebase already had once (G-14-1) from a fixed-length declaration disagreeing
-- with a variable-length entity mapping -- a blank-padded declaration is forbidden here for the
-- same reason.
--
-- Both foreign keys below cascade on delete (P-02). agent_id cascades to match agent_day_hours'
-- own V29 behaviour. shift_template_id cascades because DeskService.deleteDesk relies on
-- shift_template.desk_id's own delete cascade (V39) to clean up a desk's templates -- a
-- non-cascading reference from this table would turn every desk deletion on a desk with stored
-- usual shifts into a foreign-key violation (T-16-03).

CREATE TABLE agent_usual_shift (
    id                UUID PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    agent_id          UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    day_of_week       VARCHAR(9) NOT NULL,
    shift_template_id UUID NOT NULL REFERENCES shift_template(id) ON DELETE CASCADE,
    UNIQUE (agent_id, day_of_week)
);
