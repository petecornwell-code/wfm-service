-- Phase 15 Plan 06: Shift Envelope, Breaks & Library Generation -- the weight column for the new
-- "Band capacity" hard constraint (ENVL-08/D-03).
--
-- D-03 makes a shift_template_break_band's capacity a hard cap only when set; a blank/null
-- capacity is unlimited and never produces a tuple for this constraint to penalise. Hard by
-- default -- an over-capacity agent-day on a band is an illegal schedule, not a preference --
-- mirroring V37 (min_staffing_weight), V38 (consistent_start_weight) and V41
-- (shift_envelope_compliance_weight)'s shared reasoning that hard-vs-soft is this column's
-- value, never a code decision, since ConstraintWeights is a @ConstraintConfiguration.
ALTER TABLE constraint_weights
    ADD COLUMN band_capacity_weight VARCHAR(50) NOT NULL DEFAULT '1hard/0soft';
