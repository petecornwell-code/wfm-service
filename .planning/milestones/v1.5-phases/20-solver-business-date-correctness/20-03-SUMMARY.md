---
phase: 20-solver-business-date-correctness
plan: 03
subsystem: solver
tags: [timefold, constraint-streams, business-date, midnight-boundary, test-fixture, drift-guard]

# Dependency graph
requires:
  - phase: 20-01
    provides: "MidnightBoundaryFixture's three 21:00-anchored scenario pairs (ninePmCoverageScenario, ninePmBreakBandFlushToEnvelopeEndScenario) this plan's anchor-invariance assertion compares against their 00:00 counterparts"
provides:
  - "ConstraintMatchCountNonVacuityTest — a reflectively-complete, 26-row per-constraint match-count table over the 00:00 coverage scenario, plus an anchor-invariance assertion that names exactly which constraints a non-join defeats (SOLV-06)"
  - "PhilUsShapedDriftGuardTest — a constructed (never captured) 48-agent, 44.6-FTE, 00:00-anchored drift guard with a literal pre-migration baseline, green before and after plan 20-05's migration (BDAY-07, D-14)"
  - "A documented, empirically-confirmed Timefold behaviour: a @ConstraintWeight-ZERO constraint (Shift start mix, shipped inert) is elided entirely from explain()'s match-total map rather than reported at zero — both new test classes normalise this weight before reading any count"
affects: [20-05-join-and-anchor-migration]

# Actuals (#2632)
actuals:
  tokens: 14470
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Completeness inherited by copying ScheduleConstraintClassificationTest's two reflective derivations verbatim into a new test class, rather than re-deriving or hand-typing a parallel constraint-name list"
    - "A per-constraint match-count table's 'zero' row distinguishes a satisfied constraint from an unregistered one only when the read helper asserts exactly one match total found before reading the count — ported from MidnightBoundaryRegressionTest#requireConstraint"
    - "Anchor-invariance proven by direct count comparison between two live-computed scenario pairs, never by comparing against a second literal table — only the 00:00 baseline needs committed literals; the 21:00 side is read and diffed at test time"
    - "Cohort-derived fixture construction: demand at every timeslot computed directly from which agents' chosen shift envelope covers that hour (non-break), giving an exact, deterministic, envelope-respecting agent-to-seat pinning with no round-robin seat/envelope mismatch"

key-files:
  created:
    - src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java
    - src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java
  modified: []

key-decisions:
  - "A @ConstraintWeight-ZERO constraint (shiftStartMixWeight, shipped inert per its own javadoc) is elided entirely from SolutionManager.explain()'s constraintMatchTotalMap — not reported at a zero count. Confirmed empirically against this tree (both with and without a weight override) before either test class was written. Both new classes normalise this one weight to a non-zero value before reading any count, which changes no match (no ShiftStartMixTarget fact exists in either fixture) but keeps the 'exactly one match total found' completeness invariant holding without exception for all 26 constraints."
  - "The literal baseline table is committed ONLY for the 00:00 scenario in each plan — the anchor-invariance assertion compares the 00:00 and 21:00 sides directly (live-computed on both sides) rather than committing a second literal table for the 21:00 anchor. This matches the plan's <behavior> wording literally and avoids a second baseline that could itself drift."
  - "PhilUsShapedDriftGuardTest's 48-agent composition splits 44 full-time (8.00h/day, FTE 1.0) and 4 part-time (1.20h/day, FTE 0.15) — the unique two-tier split where full-time count is maximised (44, the largest integer ≤44.6) and the remaining 4 agents share the residual 0.6 FTE evenly. Part-time agents intentionally match no shift template's net hours and so hold no eligible shift, producing exactly 4 deterministic 'Contracted hours (under, zero)' violations — a legitimate, reproducible literal, not a defect to avoid."
  - "Demand in PhilUsShapedDriftGuardTest is derived FROM cohort membership (which agents' chosen template covers a given non-break hour), not from an independently-authored demand curve — this guarantees an exact 1:1 seat-to-agent match with zero accidental shiftEnvelopeCompliance or contracted-hours drift for the 44 full-time agents, isolating the fixture's only two violation sources (part-time under-allocation, staggered-break clustering) to ones the author chose deliberately."
  - "Removed the BambooHR-id field entirely from PhilUsShapedDriftGuardTest's agent() factory helper (Rule 1 fix, caught before commit) — calling Agent.setBamboohrId(...) literally matched this plan's own T-20-03-01 grep gate ('bamboo' case-insensitive), the same pattern every precedent fixture in this package uses harmlessly but which THIS plan's new verify step specifically forbids on the new file."

requirements-completed: []  # SOLV-06 and BDAY-07 are also declared by plan 20-05 (the migration plan that consumes these guards) — the shared-ID gate (#2388) correctly defers marking them complete until 20-05 lands too.

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "ConstraintMatchCountNonVacuityTest: reflectively-complete 26-row expected-count table for the 00:00 coverage scenario, plus an anchor-invariance assertion across both of plan 20-01's scenario pairs that names which constraints a calendar-date join silently drops"
    requirement: "SOLV-06"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest#completeness_tableKeySetExactlyEqualsTheReflectedConstraintWeightNames"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest#completeness_tableSizeExactlyEqualsTheReflectedBuilderMethodCount"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest#midnightBaseline_everyRegisteredConstraintMatchesItsLiteralExpectedCount"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest#anchorInvariance_coveragePair_everyConstraintCountMatchesAcrossTheAnchor"
        status: fail
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest#anchorInvariance_bandFlushPair_everyConstraintCountMatchesAcrossTheAnchor"
        status: fail
    human_judgment: true
    rationale: "The two anchor-invariance tests are EXPECTED RED until plan 20-05's migration lands (D-11/D-12) — a human must confirm this is the designed RED state (three completeness/baseline tests pass in the same run, confirming the instrument itself is sound), since an automated pass/fail check cannot distinguish an intentional red guard from a broken one."
  - id: D2
    description: "PhilUsShapedDriftGuardTest: 48-agent, 44.6-FTE, 00:00-anchored constructed drift guard — literal per-constraint match-count table and hard/soft score, green before and (required to stay) after the migration"
    requirement: "BDAY-07"
    verification:
      - kind: unit
        ref: "com.wfm.solver.PhilUsShapedDriftGuardTest#composition_fixtureMatchesItsOwnDeclaredShape"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.PhilUsShapedDriftGuardTest#driftGuard_everyConstraintMatchCountAndTheScoreMatchTheLiteralBaseline"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.PhilUsShapedDriftGuardTest$NoSearchDuringEvaluation#scoringMutatesNoPlanningVariable"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite, after both tasks): 1167 tests, 6 failed, 0 errors"
        status: pass
    human_judgment: false

duration: 32min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 3: Per-Constraint Match-Count Instrument & Phil-US Drift Guard Summary

**A reflectively-complete 26-constraint match-count table with an anchor-invariance assertion that names exactly which three constraints a calendar-date join silently drops (RED today, by design), plus a 48-agent constructed Phil-US-shaped drift guard with a literal pre-migration baseline (GREEN today, required to stay green through the migration).**

## Performance

- **Duration:** 32 min
- **Started:** 2026-10-01T18:55:00Z
- **Completed:** 2026-10-01T19:27:31Z
- **Tasks:** 2 completed
- **Files modified:** 2 (2 created, both test-scope)

## Accomplishments

- `ConstraintMatchCountNonVacuityTest` inherits SOLV-06's completeness property by copying
  `ScheduleConstraintClassificationTest`'s two reflective derivations verbatim — the expected-count
  table's key set and size are checked against the live `@ConstraintWeight` annotation set and the
  live builder-method count on every run, so a 27th constraint fails the build until a row is added.
- A literal, hand-written 26-row table of every registered constraint's match count on
  `MidnightBoundaryFixture.midnightCoverageScenario()` (the 00:00 baseline) — every count is zero,
  captured by running the pre-migration tree directly, never guessed.
- Two anchor-invariance assertions compare every registered constraint's match count between
  plan 20-01's 00:00/21:00 scenario pairs directly (live-computed on both sides, no second literal
  table needed). Both are EXPECTED RED today: the coverage pair differs on "Agent not working that
  day" (0 vs 2) and "Contracted hours (under, zero)" (0 vs 2); the band-flush pair differs on
  "Agent not working that day" (0 vs 8), "Break clustering" (1 vs 0) and "Contracted hours (under,
  zero)" (0 vs 1) — the migration's own work order, independently derived from SOLV-06's instrument
  rather than copied from plan 20-02's join-guard list.
- `PhilUsShapedDriftGuardTest` constructs — never captures — a 48-agent, 44.6-total-contracted-FTE,
  SHIFT-mode, `00:00`-anchored fixture to the Phil-US desk's composition shape only. 44 full-time
  agents (8.00h/day, FTE 1.0) are round-robin assigned across four main shift templates (net 8h
  each, staggered 1h breaks); 4 part-time agents (1.20h/day, FTE 0.15) match no template's net
  hours and hold no shift. A fifth "Flex" template (net 9h vs the 8h contract) exists purely as the
  required slack-template composition property.
- A class-load validator asserts agent count (48), total FTE (44.60), the slack-template property,
  and every band's edge margin — fails loudly, naming the lost property, if a later edit weakens
  any of them. Re-asserted by an explicit `@Test` as well, not only the static initializer.
- Demand at every hourly timeslot is derived directly from cohort membership (which agents' chosen
  envelope covers that hour, excluding their own break), giving an exact 1:1 seat-to-agent pinning
  with zero accidental envelope-compliance or contracted-hours drift for the 44 full-time agents.
  The literal baseline is therefore clean apart from two deliberate, fully-understood sources: 4
  "Contracted hours (under, zero)" (the part-timers' unreachable one slot) and 4 "Break clustering"
  (each main template's own break window puts 11 of 33 then-assigned agents on break, 33.3% against
  the shipped 20% cluster threshold) — score `-400hard/-40soft` exactly.
- `NoSearchDuringEvaluation` proves no planning variable moves across the explanation read, mirroring
  `MidnightBoundaryRegressionTest`'s own precedent.
- Both new classes independently discovered and documented the same Timefold behaviour: a
  `@ConstraintWeight`-ZERO constraint ("Shift start mix", shipped inert) is elided entirely from
  `explain()`'s match-total map rather than reported at a zero count — confirmed empirically (with
  and without a weight override) before either test was written. Both classes normalise this one
  weight before reading any count, changing no actual match (neither fixture carries any
  `ShiftStartMixTarget` fact) but keeping "exactly one match total found" holding without exception.
- Full unfiltered suite after both tasks: **1167 tests, 6 failed, 0 errors** — failures confined to
  exactly the three expected classes (`MidnightBoundaryRegressionTest$NonMidnightAnchor` x3,
  `BusinessDateJoinGuardTest` x1, `ConstraintMatchCountNonVacuityTest` x2), matching the wave-close
  gate's inverted expectation precisely.

## Task Commits

Each task was committed atomically:

1. **Task 1: ConstraintMatchCountNonVacuityTest — complete by reflection, invariant across the anchor** - `7ba954d` (test)
2. **Task 2: PhilUsShapedDriftGuardTest — 48 agents, constructed to a shape, never captured** - `726799b` (test)

**Plan metadata:** pending (this commit)

## Files Created/Modified

- `src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java` — SOLV-06's per-constraint match-count table, reflective completeness, and the anchor-invariance diagnosis
- `src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java` — BDAY-07's 48-agent Phil-US-shaped constructed drift guard with a literal pre-migration baseline

## Decisions Made

1. **A zero-weight constraint is elided from `explain()`, not reported at zero.** Discovered and
   confirmed empirically (a throwaway scratch test, removed before commit) before writing either
   literal baseline table: `ConstraintWeights.shiftStartMixWeight` ships at `HardSoftScore.ZERO` by
   design, and Timefold does not include a zero-weight constraint in
   `getConstraintMatchTotalMap()` at all — the map holds 25 entries, not 26, under default weights.
   Both new classes normalise this one weight to non-zero before reading any count (a pure
   presence-in-the-map change; the underlying join predicate, and therefore every actual match
   count, is unaffected).
2. **Only the 00:00 scenario needs a committed literal table; anchor-invariance compares live
   values on both sides.** The plan's `<behavior>` scopes the literal baseline requirement to "the
   00:00 coverage scenario" specifically; the anchor-invariance assertion reads both the 00:00 and
   21:00 sides at test time and diffs them directly, so no second literal table exists to drift out
   of sync with the first.
3. **PhilUsShapedDriftGuardTest's demand is derived from cohort membership, not authored
   independently.** Rather than hand-crafting a demand curve and hoping a generic round-robin
   seat-pinning rule happens to respect each agent's chosen envelope (the approach that would make
   violations unpredictable and the literal baseline fragile to re-derive), demand at every hour is
   computed directly from which of the 44 full-time agents' chosen templates cover that hour,
   non-break. This makes the fixture's only two violation sources exactly the two the author chose
   deliberately (part-time under-allocation, staggered-break clustering), both independently
   verified by direct constraint-body reading before the literal numbers were captured.
4. **The 44/4 full-time/part-time split is the unique maximal-full-time two-tier decomposition.**
   44 is the largest integer FTE count not exceeding 44.6 FTE under FTE-1.0 full-time agents, which
   forces the remaining 4 agents to share exactly 0.6 FTE (0.15 each) — not an arbitrary choice, the
   simplest integer-count split reachable at all under a two-tier model.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed BambooHR-id field population from PhilUsShapedDriftGuardTest's agent() helper**
- **Found during:** Task 2, running the plan's own `<verify>` tenant-identifier grep gate
- **Issue:** `grep -ciE 'bamboo|@helpware|@gmail|philus-[0-9a-f]{8}'` printed 3, not the required 0.
  The matches were a javadoc mention of "BambooHR-id-shaped field", a parameter named `bambooId`,
  and a call to `Agent.setBamboohrId(bambooId)` — the exact same harmless pattern every precedent
  fixture in this package uses (`LiveShapeShiftDeskFixture`, `MidnightBoundaryFixture` both call
  `setBamboohrId`), but this plan's own T-20-03-01 mitigation specifically grep-gates the NEW file
  against it, and the literal string "bamboo" matched regardless of context.
- **Fix:** Dropped the BambooHR-id parameter and field population from the `agent()` factory helper
  entirely — it is not read by any constraint this fixture exercises, so no behaviour changed.
  Reworded the class javadoc to describe the naming discipline without the literal substring.
- **Files modified:** `src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java`
- **Verification:** `grep -ciE 'bamboo|@helpware|@gmail|philus-[0-9a-f]{8}'` now prints 0; full test
  class re-run confirms zero behaviour change (same 26-row literal table, same score).
- **Committed in:** `726799b` (Task 2 commit — caught before the commit, not a follow-up fix)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug, caught and fixed before the commit it belongs to).
**Impact on plan:** None — a self-inflicted grep-gate trip on documentation/variable-naming only;
no constraint behaviour or literal count was ever affected.

## Issues Encountered

**Predicting PhilUsShapedDriftGuardTest's literal score required one iteration.** The first-draft
literal (`0hard/-20soft`) under-counted both terms: `contractedHoursUnderZeroWeight`'s shipped
`ofHard(100)` was not multiplied through (4 part-time agents x -100 = -400hard, not the 0hard first
guessed), and `breakClusteringWeight`'s shipped `ofSoft(2)` was likewise not applied to the per-match
base penalty of 5 (4 matches x 5 x 2 = -40soft, not -20soft). The MATCH COUNTS in the first draft
were already exactly correct — only the SCORE magnitude needed correcting, confirmed by one
`./gradlew test` run against the actual tree before committing. Recorded here per this project's
established pattern of distinguishing a narrative/first-draft miss from a real defect.

No blockers, no auth gates, no unresolved ambiguity requiring a checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-04** (`SlotModeOvernightContractedHoursTest`, SOLV-04). This plan's two
guards are now live instruments for the rest of this phase:

- `ConstraintMatchCountNonVacuityTest`'s anchor-invariance assertion is RED with a committed,
  independently-derived differing-constraint list (below) — plan 20-05's migration must turn it
  green without changing any OTHER constraint's count, and the failure message itself will continue
  naming any constraint that still disagrees.
- `PhilUsShapedDriftGuardTest` is GREEN and must stay green through every later plan in this
  phase — a red run after plan 20-05's migration means the migration moved something on a
  realistic 48-agent `00:00`-anchored desk, which it must not, and the failure will name which
  constraint moved.

**The anchor-invariance failure's differing-constraint list, verbatim (SOLV-06's diagnosis, D-12 —
independently derived from plan 20-02's join-guard list, not copied from it):**

```
Coverage pair (midnightCoverageScenario vs ninePmCoverageScenario):
  "Agent not working that day": 0 (00:00) vs 2 (21:00)
  "Contracted hours (under, zero)": 0 (00:00) vs 2 (21:00)

Band-flush pair (breakBandFlushToEnvelopeEndScenario vs ninePmBreakBandFlushToEnvelopeEndScenario):
  "Agent not working that day": 0 (00:00) vs 8 (21:00)
  "Break clustering": 1 (00:00) vs 0 (21:00)
  "Contracted hours (under, zero)": 0 (00:00) vs 1 (21:00)
```

**The two literal baseline tables, by reference to their committed location (the only drift evidence
this milestone ships, per D-14):**

- `ConstraintMatchCountNonVacuityTest.EXPECTED_MIDNIGHT_COVERAGE_COUNTS` (26 rows, all zero) —
  `src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java`
- `PhilUsShapedDriftGuardTest.EXPECTED_MATCH_COUNTS` (26 rows) and `EXPECTED_SCORE`
  (`-400hard/-40soft`) — `src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java`

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java
- FOUND: src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java
- FOUND: commit 7ba954d (Task 1)
- FOUND: commit 726799b (Task 2)
- Acceptance criteria re-verified: `grep -c 'getConstraintName()'` → 2 (Task 1 file); `grep -c
  '\.solve('` → 0 (both files); `grep -c 'getConstraintMatchTotalMap'` → 1 (Task 2 file); `grep
  -ciE 'bamboo|@helpware|@gmail|philus-[0-9a-f]{8}'` → 0 (Task 2 file).
- Plan-level `<verification>` re-run: `./gradlew compileTestJava` → BUILD SUCCESSFUL; `./gradlew
  test --tests "com.wfm.solver.PhilUsShapedDriftGuardTest"` → BUILD SUCCESSFUL (3/3 pass);
  `./gradlew test --tests "com.wfm.solver.ConstraintMatchCountNonVacuityTest"` → BUILD FAILED,
  failing only the two anchor-invariance tests (3/5 pass); `./gradlew test --tests
  "com.wfm.solver.ScheduleConstraintClassificationTest" --tests
  "com.wfm.solver.ShiftDeskEndToEndRegressionTest" --tests "com.wfm.solver.SolverQualityGuardTest"`
  → BUILD SUCCESSFUL; unfiltered `./gradlew test` → 1167 tests, 6 failed, 0 errors, failures
  confined to exactly `MidnightBoundaryRegressionTest$NonMidnightAnchor` (3),
  `BusinessDateJoinGuardTest` (1) and `ConstraintMatchCountNonVacuityTest` (2) — no other class
  regressed.
