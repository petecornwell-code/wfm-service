-- BDAY-04: carry the desk's day start into the solved schedule, additively.
--
-- The default is exact for every existing row because DeskService still refuses any desk anchor
-- other than 00:00 -- no desk in this database has ever held a different value. A schedule row
-- already snapshots its solve inputs the same way start_time and scheduling_mode already do; this
-- is not a second copy of desk state, it is the established shape for a point-in-time solve input.
-- Nothing reads this column yet -- SolverService.buildSchedule is the sole writer, and no
-- production consumer exists until the re-anchoring phase that follows this one.
ALTER TABLE schedule ADD COLUMN day_start TIME NOT NULL DEFAULT '00:00';
