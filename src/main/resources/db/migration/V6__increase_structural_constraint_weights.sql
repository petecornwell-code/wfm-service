-- Increase structural integrity constraint weights from 1hard to 1000hard.
-- These constraints (overlap, break geometry) must match the unassigned
-- assignment weight (1000hard) so the solver never prefers double-booking
-- an agent or breaking break geometry over leaving a seat empty.
-- Without this, the solver's local search degrades the initial solution
-- by creating overlaps to fill seats (cost 1 << benefit 1000).
UPDATE constraint_weights
   SET no_overlap_weight = '1000hard/0soft',
       exactly_one_break_weight = '1000hard/0soft',
       break_duration_weight = '1000hard/0soft',
       break_blocked_window_weight = '1000hard/0soft',
       break_alignment_weight = '1000hard/0soft';
