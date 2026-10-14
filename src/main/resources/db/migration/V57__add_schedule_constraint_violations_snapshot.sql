-- Accepted schedules lost their constraint-violation report: the accepted path cannot re-explain
-- the score (problem facts are not reconstituted from the DB), so it reported nothing for every
-- constraint except shift-envelope compliance, while the persisted score still said
-- NOT FEASIBLE. acceptSchedule now explains the score once, from the live in-memory solution
-- where every fact is present, and stores the resulting report here as JSON.
--
-- NULLable with no default: NULL means "accepted before this column existed (or the accept-time
-- explanation failed)", and the reader falls back to the envelope-only derivation in that case.
ALTER TABLE schedule ADD COLUMN constraint_violations_snapshot TEXT;
