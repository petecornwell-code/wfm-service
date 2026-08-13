-- Revert V33: put the contracted-hours UNDER constraints back to hard.
--
-- V33 made them soft so an over-staffed desk could still produce a feasible schedule. That worked
-- — schedules became feasible — but it did not deliver usable rosters: shifts came out at ~3.75h
-- with no breaks, because the solver cannot construct a full shift WITH a correctly formed break
-- incrementally (every intermediate state violates the hard break rule). Softening under-allocation
-- also destabilised solver convergence, and one construction-heuristic test began failing
-- deterministically.
--
-- Reverting to the previously known-good behaviour while the underlying problem — the solver needs
-- an atomic "assign full shift with break" move — is scoped as its own piece of work.
--
-- NOTE: V33 is intentionally left in place rather than deleted. It has already been applied to the
-- live database, and Flyway validates applied migrations against the files present
-- (validateOnMigrate defaults to true) — removing it would fail startup. Forward-only correction.
UPDATE constraint_weights
   SET contracted_hours_under_weight = '100hard/0soft'
 WHERE contracted_hours_under_weight = '0hard/100soft';

UPDATE constraint_weights
   SET contracted_hours_under_zero_weight = '100hard/0soft'
 WHERE contracted_hours_under_zero_weight = '0hard/100soft';
