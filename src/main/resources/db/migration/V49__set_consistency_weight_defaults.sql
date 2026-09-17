-- Phase 17 (plan 17-04, D-06/XCUT-04): sets the benchmark-derived, human-decided shipped
-- defaults for both "Usual shift consistency" (consistent_start_weight) and "Preferred start
-- (shift mode)" (preferred_start_shift_mode_weight). Values chosen at the 17-04-PLAN.md Task 3
-- checkpoint (decision: "proposed"); the seeded A/B run, the explain() constraint-match
-- breakdown, and the redone sizing arithmetic below all live in
-- .planning/phases/17-consistency-constraint-drift-reporting/17-BENCHMARK.md.
--
-- V38 (add_consistent_start_weight) sized consistent_start_weight at 0hard/2soft with
-- arithmetic written for a PER-AGENT penalty: "on the live desk's 28 CSRs a four-increment
-- spread each is 28 x 4 x 2 = 224 soft -- enough to break ties between equal-coverage
-- schedules, not enough to outbid bulk under-allocation or minimum staffing." Phase 17's
-- usualShiftConsistency constraint charges PER-AGENT-DAY (D-02) -- a model V38 predates
-- entirely, so that arithmetic could never have accounted for it. 17-BENCHMARK.md redoes the
-- sizing for the per-agent-day model: worst case (every one of the 28 agents drifted on every
-- one of the 5 working days in a schedule period) is `28 x 5 x 4 x weight = 560 x weight` soft.
-- At weight 2 that totals 1,120 soft -- 12% OVER minStaffingWeight's 1000-soft ceiling.
--
-- That overshoot is a KNOWN, ACCEPTED residual risk, not an oversight: no positive-integer
-- weight pair can satisfy both the ceiling and D-08's ordering invariant (preference strictly
-- below consistency, both nonzero) at once. Weight 1 clears the ceiling on its own (560 < 1000)
-- but leaves no positive integer below it for a nonzero preference weight; weight 2 is the
-- smallest consistency value that can carry a nonzero, strictly-lower preference weight at all.
-- The worst case requires the ENTIRE roster to drift on EVERY working day simultaneously, which
-- reflects a data-quality breakdown (e.g. a mass template retirement invalidating most stored
-- usual shifts at once), not normal operation. The illustrative typical case -- an explicit,
-- stated judgement (~20% of the roster drifting on a given day), not a measurement, since no
-- production telemetry on actual drift rates exists yet -- projects to ~240 soft, comfortably
-- under the ceiling. Full working, including where this projection agrees and disagrees with
-- this run's own explain() measurement, is in 17-BENCHMARK.md's Sizing Arithmetic section.
--
-- Both chosen values are IDENTICAL to what is already live: V38 shipped
-- consistent_start_weight DEFAULT '0hard/2soft' and V48 shipped
-- preferred_start_shift_mode_weight DEFAULT '0hard/1soft'. This migration's UPDATE statements
-- therefore CONFIRM the incumbent values rather than change behaviour for any desk -- the most
-- important property of this migration, and the one that makes it safe to run against live
-- tenant rows on the environment that serves production traffic (there is no separate
-- production tier; "dev" is that environment). Flyway is forward-only, so this write is a
-- one-way door (see 17-04-PLAN.md's reversibility rating); undoing a wrong value here would
-- need another migration, not an edit.
--
-- Preference strictly below consistency (1 < 2, both nonzero) is CONS-06's stated precedence
-- invariant, separately enforced at save time by ConstraintWeightsService (D-08) -- this
-- migration ships exactly the two starting values that invariant already requires.
--
-- T-17-06: every UPDATE below is predicated on the row still holding its prior shipped
-- default, so a per-desk value an operator has already tuned is left untouched. The
-- ALTER COLUMN ... SET DEFAULT clauses apply separately, to rows created after this migration.
--
-- Note: because both chosen values above are identical to what V38/V48 already shipped, each
-- UPDATE's SET and WHERE clauses currently name the same literal -- the two statements below are
-- self-referential no-ops by construction today; they exist to document/prove the predicate shape
-- (and are exercised as such by ConstraintWeightsMigrationTest), not to change any row's data.
-- They would only start doing real work if a future migration changed the chosen default away
-- from the value shipped here, at which point the SET value and the WHERE value would diverge.
ALTER TABLE constraint_weights
    ALTER COLUMN consistent_start_weight SET DEFAULT '0hard/2soft';

ALTER TABLE constraint_weights
    ALTER COLUMN preferred_start_shift_mode_weight SET DEFAULT '0hard/1soft';

UPDATE constraint_weights
    SET consistent_start_weight = '0hard/2soft'
    WHERE consistent_start_weight = '0hard/2soft';

UPDATE constraint_weights
    SET preferred_start_shift_mode_weight = '0hard/1soft'
    WHERE preferred_start_shift_mode_weight = '0hard/1soft';
