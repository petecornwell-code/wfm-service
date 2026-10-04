---
phase: 22-minimum-rest
plan: 02
subsystem: solver
tags: [timefold, constraint-streams, minimum-rest, slot-mode]

requires:
  - phase: 22-minimum-rest
    provides: "RestSpan (gapMinutes, ofShift), ScheduleConfig's 15th component (minimumRestMinutes), ConstraintWeights.minimumRestShiftWeight, the minimumRestShift constraint (plan 22-01)"
provides:
  - "RestSpan.ofSlots -- D-02's SLOT-mode span definition: the agent's whole assigned span on a business date, first slot start to last slot end, with the intra-day break gap deliberately ignored"
  - "ConstraintWeights.minimumRestSlotWeight (ofHard(1000), identical to minimumRestShiftWeight)"
  - "ScheduleConstraintProvider.minimumRestSlot -- the 28th registered constraint"
  - "RestGapMatch -- the shared record both mode-gated rest constraints now map through, so RestSpan.gapMinutes is called exactly once per candidate pair"
affects: [22-03-waiver, 22-05, 22-06, 22-07, 22-08, 22-10]

actuals:
  tokens: 9412
  tasks: 2
  commits: 2
  plan_head_before: 9e9c1c048ea1190fd353855a8f086aba625aecde
  plan_head_after: e9ae5d0305fc5989042bb94c892252b493693eff

tech-stack:
  added: []
  patterns:
    - "A per-mode span derivation (RestSpan.ofSlots) ordered by ANCHORED minute, mirroring ScheduleConstraintProvider.countContiguousGaps's existing idiom, rather than clock-time ordering"
    - "A single-use-per-candidate-pair measurement record (RestGapMatch) so a shared gap implementation is called exactly once per tuple rather than once per filter and once per penalty -- extends D-08's 'one shared computation' argument to the stream shape itself"

key-files:
  created:
    - src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java
  modified:
    - src/main/java/com/wfm/model/RestSpan.java
    - src/main/java/com/wfm/model/ConstraintWeights.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
    - src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java
    - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
    - src/main/java/com/wfm/service/ConstraintWeightsService.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java

key-decisions:
  - "Refactored both mode-gated rest constraints (minimumRestShift and minimumRestSlot) to map into a shared RestGapMatch(cfg, prev, next, gapMinutes) record so RestSpan.gapMinutes is called exactly once per candidate pair -- required to satisfy the plan's literal 'exactly two non-comment occurrences of RestSpan.gapMinutes' acceptance criterion, which the as-shipped 22-01 shift constraint (filter + penalizeConfigurable each calling it) would otherwise have already failed on its own. Pure internal refactor, zero behavior change -- MinimumRestShiftConstraintTest's 13 tests are unchanged and green."

requirements-completed: [REST-02, REST-04]

coverage:
  - id: D1
    description: "A SLOT desk with a configured minimum rest scores a hard penalty for an agent whose assigned-span gap across adjacent business dates is short, measured over the whole assigned span (first slot start to last slot end), at the exact-boundary edge, at a 15:00 anchor, and sharing one gap implementation with the SHIFT constraint"
    requirement: "REST-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java#11 tests (RestSpan.ofSlots span derivation x4, shortfall penalty, exact boundary, NULL/SHIFT-mode/zero-rows inertness, the paired zero/one-match D-02 regression)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#13 tests (unchanged, re-verified green after the shared RestGapMatch refactor)"
        status: pass
    human_judgment: false
  - id: D2
    description: "A genuinely exactlyOneBreak-compliant SLOT agent-day (one gap of exactly the break duration) does not trip the rest constraint on its own mandated break, against itself or against the previous day -- the D-02 false-positive trap this plan exists to defuse -- and the constraint is proven live, not dead, by a paired raised-minimum test on the identical fixture"
    requirement: "REST-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java#twoCompliantConsecutiveDays_minimum660_zeroMatches and #twoCompliantConsecutiveDays_raisedMinimum_oneMatch"
        status: pass
    human_judgment: false
  - id: D3
    description: "A SLOT-mode desk with no configured minimum rest, or a SHIFT-mode desk, builds zero tuples for 'Minimum rest (slot)' at the first stream node -- proven structurally (zero row in the match-count baseline, mode-inertness tests), never on the strength of a zero score alone -- and both mode-gated constraints share one gap implementation and one hard weight value"
    requirement: "REST-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java#midnightBaseline_everyRegisteredConstraintMatchesItsLiteralExpectedCount (Minimum rest (slot) row = 0)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java (7 tests, all three derivations agree at 28 constraints)"
        status: pass
      - kind: unit
        ref: "grep -c 'RestSpan.gapMinutes' over ScheduleConstraintProvider.java (non-comment) = 2; grep -c 'public static' over RestSpan.java = 3"
        status: pass
      - kind: unit
        ref: "./gradlew test (full suite after ./gradlew --stop): 1343 tests, 0 failures, 0 errors"
        status: pass

duration: 48min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 02: SLOT-Mode Minimum Rest Summary

**SLOT-mode minimum-rest hard constraint (the 28th registered constraint) sharing RestSpan's gap implementation and weight tier with its SHIFT-mode sibling, proven not to false-positive on a compliant agent-day's own mandated break.**

## Performance

- **Duration:** 48 min
- **Started:** 2026-10-04T00:19:00Z (approx)
- **Completed:** 2026-10-04T00:29:30Z (approx, including the ~11 min full-suite verification run)
- **Tasks:** 2
- **Files modified:** 9 (1 created, 8 modified)

## Accomplishments

- `RestSpan.ofSlots(UUID, LocalDate, List<AgentAssignment>, LocalTime)`: D-02's SLOT-mode span definition — the agent's whole assigned span on a business date, first slot start to last slot end, with the intra-day break gap deliberately ignored. Ordered by ANCHORED minute (never clock time), mirroring `countContiguousGaps`'s existing idiom. Throws `IllegalArgumentException` on an empty slot list rather than returning a degenerate span.
- `ConstraintWeights.minimumRestSlotWeight` — `@ConstraintWeight("Minimum rest (slot)")`, `ofHard(1000)`, deliberately identical to `minimumRestShiftWeight` (D-08's "severity must never disagree").
- `ScheduleConstraintProvider.minimumRestSlot` — the 28th registered constraint: leads with a filtered `forEach(ScheduleConfig.class)` singleton (non-null minimum rest, `schedulingMode() != SHIFT`) so an unset or SHIFT-mode desk produces zero tuples structurally, then an indexed self-join over `RestSpan.ofSlots` spans on `(agentId, businessDate -> businessDate.plusDays(1))`, penalising a short gap by its shortfall in minutes.
- **Deviation (Rule 1, discovered mid-Task-1):** refactored both `minimumRestShift` and `minimumRestSlot` to map their join results into a shared `RestGapMatch(cfg, prev, next, gapMinutes)` record, computing `RestSpan.gapMinutes` exactly once per candidate pair instead of once in the filter and again in the penalty. This was required to satisfy the plan's own literal acceptance criterion ("ScheduleConstraintProvider.java contains exactly two non-comment occurrences of `RestSpan.gapMinutes`") — the as-shipped 22-01 shift constraint already called it twice on its own (filter + `penalizeConfigurable`), so adding the slot constraint in the same shape would have produced four occurrences, not two. Pure internal refactor with zero behavior change, confirmed by `MinimumRestShiftConstraintTest`'s unchanged 13/13 green result.
- `MinimumRestSlotConstraintTest` — 11 tests: four direct `RestSpan.ofSlots` proofs (compliant-day span, fragmented two-gap span, 15:00-anchor ordering by anchored minute, empty-list throw) and seven full-pipeline proofs (shortfall penalty, exact-boundary non-penalty, the load-bearing paired zero-match/raised-minimum D-02 regression, NULL/SHIFT-mode/zero-assigned-rows inertness).
- Registry rows added: `ScheduleConstraintClassification` ("Minimum rest (slot)", `MODE_GATED`) and `ConstraintMatchCountNonVacuityTest`'s expected-count table (0, with the same REST-04 structural-zero comment as the shift row).

## Task Commits

Each task was committed atomically:

1. **Task 1: A SLOT desk's minimum rest is a hard violation over the whole assigned span** - `f25ec32` (test)
2. **Task 2: Prove a compliant SLOT agent-day does not trip the rest constraint** - `e9ae5d0` (test)

_Note: both tasks carried `tdd="true"`; following plan 22-01's established precedent for this phase, each task's test-and-production-code changes landed as one commit (not split into three separate RED/GREEN/REFACTOR commits) since `workflow.tdd_mode`'s stricter gate enforcement is not configured for this project._

## Files Created/Modified

- `src/main/java/com/wfm/model/RestSpan.java` - `ofSlots`, the SLOT-mode span derivation
- `src/main/java/com/wfm/model/ConstraintWeights.java` - `minimumRestSlotWeight` (`ofHard(1000)`)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `minimumRestSlot` constraint, `RestGapMatch` shared record, refactored `minimumRestShift`
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` / `ConstraintMatchCountNonVacuityTest.java` - registry rows for the 28th constraint
- `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` - 11 tests covering the full behavior block
- `src/main/java/com/wfm/dto/ConstraintWeightsDto.java` / `service/ConstraintWeightsService.java` - weight exposed on the existing Constraint Weights API (deviation, see below)
- `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` - hardcoded `MODE_GATED` expected set updated to sixteen (deviation, see below)

## Decisions Made

- Shared `RestGapMatch` record introduced so both mode-gated rest constraints compute their gap exactly once per candidate pair — see Deviations below for why this was necessary, not optional polish.
- Followed 22-01's established per-task single-commit precedent for `tdd="true"` tasks in this phase (no `workflow.tdd_mode` gate configured for this project).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Refactored both rest constraints to share a `RestGapMatch` record, reducing `RestSpan.gapMinutes` call sites from 4 to 2**
- **Found during:** Task 1, while verifying the plan's own acceptance criterion (`grep -c 'RestSpan.gapMinutes'` must equal 2)
- **Issue:** The plan's Task 1 action explicitly instructed building `minimumRestSlot` "in the same order as the shift constraint" (filter on gap, then penalize by shortfall), which calls `RestSpan.gapMinutes` twice per constraint. The pre-existing `minimumRestShift` (shipped in plan 22-01) already called it twice on its own (filter + `penalizeConfigurable`), so mirroring that shape for the new constraint would have produced 4 non-comment occurrences total, not the plan's literal expected 2 — a planning arithmetic mismatch, not a code defect introduced by this task.
- **Fix:** Introduced `RestGapMatch(ScheduleConfig cfg, RestSpan prev, RestSpan next, int gapMinutes)` and had both constraints `.map(...)` their join results through it once, so the filter and the penalty both read the pre-computed `gapMinutes()` field instead of recomputing it. This satisfies the literal grep count (2 total, one per constraint) while also being a minor performance improvement (the gap is now computed once per tuple, not twice).
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `grep -c 'RestSpan.gapMinutes'` (non-comment) now reports 2. `MinimumRestShiftConstraintTest` re-run green at 13/13 (unchanged behavior). Full suite green at 1343/1343.
- **Commit:** `f25ec32` (Task 1 commit)

**2. [Rule 1 - Bug] Wired `minimumRestSlotWeight` into `ConstraintWeightsDto`/`ConstraintWeightsService`**
- **Found during:** Task 1, by anticipation from plan 22-01's identical deviation (adding a 28th `@ConstraintWeight` field breaks `ConstraintWeightDtoParityTest`'s precondition that every weight is reachable through the Constraint Weights API)
- **Issue:** `ConstraintWeightDtoParityTest` requires every `@ConstraintWeight` on `ConstraintWeights` to have a matching field on `ConstraintWeightsDto`, mapped in both directions in `ConstraintWeightsService`.
- **Fix:** Added `minimumRestSlotWeight` to `ConstraintWeightsDto` (getter/setter) and both mapping directions (`updateWeights`'s partial-update block, `toDto`) in `ConstraintWeightsService`, mirroring `minimumRestShiftWeight`'s 22-01 precedent exactly.
- **Files modified:** `src/main/java/com/wfm/dto/ConstraintWeightsDto.java`, `src/main/java/com/wfm/service/ConstraintWeightsService.java`
- **Verification:** Full suite green (includes `ConstraintWeightDtoParityTest`).
- **Commit:** `f25ec32` (Task 1 commit)

**3. [Rule 1 - Bug] Updated `ScheduleConstraintClassificationTest`'s hardcoded `MODE_GATED` expected set**
- **Found during:** Task 1, by anticipation from plan 22-01's identical deviation
- **Issue:** Classifying the new constraint `MODE_GATED` in `ScheduleConstraintClassification.classifications()` made the pre-existing `thePhase15ModeGatedSetIsExactlyTheExpectedRows` test's hardcoded expected set stale (still named only the prior fifteen rows), failing the build.
- **Fix:** Added `"Minimum rest (slot)"` to the expected `Set.of(...)` with an inline comment, and updated the assertion's message text from "fifteen" to "sixteen" constraints.
- **Files modified:** `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java`
- **Verification:** `ScheduleConstraintClassificationTest` green (7/7).
- **Commit:** `f25ec32` (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 — one a plan-arithmetic correction required to satisfy the plan's own literal acceptance criterion without weakening it, two pre-existing structural guard tests that a correctly-added 28th constraint/weight necessarily touches, following 22-01's exact precedent).
**Impact on plan:** All three fixes were necessary for correctness and for the plan's own acceptance criteria to be literally satisfiable. No scope creep — every change stays within the Constraint Weights / classification registry surface and the rest-constraint stream shape this plan's new constraint necessarily intersects.

## Issues Encountered

- Same sandbox-specific pre-commit protected-branch guard false positive recorded in 22-01-SUMMARY.md: this sequential executor's branch (`claude/create-system-specification-451ge`) resolves as `origin/HEAD` in this sandbox. Treated as a known false positive per that plan's precedent, not a halt condition.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Both mode-gated rest constraints (SHIFT and SLOT) are now live, structurally inert on an unset or wrong-mode desk, sharing one gap implementation (`RestSpan.gapMinutes`, now called exactly once per candidate pair in both) and one hard weight tier.
- Plan 22-03 (waiver) can proceed: the `agent_rest_waiver` table landed in 22-01's V55 migration is still unused until then; D-08's "one shared predicate, three consumers" obligation (the two constraints plus the pre-solve refusal) now has both constraints in place to extend.
- No blockers.

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*

## Self-Check: PASSED

- FOUND: src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java
- FOUND: src/main/java/com/wfm/model/RestSpan.java (ofSlots present)
- FOUND: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java (minimumRestSlot, RestGapMatch present)
- FOUND commit f25ec32 (Task 1)
- FOUND commit e9ae5d0 (Task 2)
- Re-ran plan-level `<verification>`: `./gradlew test --tests "ScheduleConstraintClassificationTest"` green (7/7, 28 constraints); `./gradlew test --tests "ConstraintMatchCountNonVacuityTest"` green (5/5); `./gradlew test --tests "MidnightTimeArithmeticGuardTest"` green (12/12, allowlist unmodified at 10 entries); `grep -c 'RestSpan.gapMinutes'` (non-comment) = 2; `./gradlew --stop` then `./gradlew test` (full suite) green — 1343 tests, 0 failures, 0 errors.
- All `<acceptance_criteria>` for both tasks re-verified passing (see per-task grep/test commands above).
