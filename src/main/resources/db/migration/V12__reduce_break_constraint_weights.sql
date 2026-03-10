-- Reduce break geometry constraint weights from 1000hard to 10hard.
-- V6 set these to 1000hard to match unassigned_assignment_weight (1000hard),
-- but V9 changed unassigned to soft. At 1000hard, a single break violation
-- outweighs the contracted-hours benefit of assigning an agent (800hard for
-- 8 slots × 100), causing the CH to prefer null for every step.
-- At 10hard, break violations are still enforced but don't block CH progress.
-- The no_overlap_weight stays at 1000hard — overlaps are always invalid.
UPDATE constraint_weights
   SET exactly_one_break_weight    = '10hard/0soft',
       break_duration_weight       = '10hard/0soft',
       break_blocked_window_weight = '10hard/0soft',
       break_alignment_weight      = '10hard/0soft';
