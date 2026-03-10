-- Split contracted_hours_weight into asymmetric over/under weights.
-- Over-assignment (agent works MORE than contracted) keeps the high 1001hard
-- weight to prevent agents being assigned extra slots just to fill seats.
-- Under-assignment (agent works FEWER than contracted) uses 1hard so the
-- solver prefers filling seats with partial shifts over leaving them empty.
-- This resolves the deadlock where contracted_hours_weight at 1001hard
-- dominated unassigned_assignment_weight at 1000hard, making it impossible
-- to assign a second agent to fill remaining demand slots.

ALTER TABLE constraint_weights
    ADD COLUMN contracted_hours_over_weight  VARCHAR(255) DEFAULT '1001hard/0soft',
    ADD COLUMN contracted_hours_under_weight VARCHAR(255) DEFAULT '1hard/0soft';

-- Migrate existing data: over-assignment keeps the old weight, under uses 1hard
UPDATE constraint_weights
   SET contracted_hours_over_weight  = contracted_hours_weight,
       contracted_hours_under_weight = '1hard/0soft';

ALTER TABLE constraint_weights DROP COLUMN contracted_hours_weight;
