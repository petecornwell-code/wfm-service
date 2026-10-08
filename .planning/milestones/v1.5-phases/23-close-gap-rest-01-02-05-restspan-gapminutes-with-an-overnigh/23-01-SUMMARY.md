---
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
plan: 01
subsystem: scheduling-solver
tags: [timefold, rest-constraint, day-window, interval-arithmetic, junit5]

# Dependency graph
requires:
  - phase: 22-minimum-rest
    provides: RestSpan, DayWindow anchored accessors, requireRestFeasibility's SHIFT/SLOT branches, the minimumRestShift/minimumRestSlot constraints
provides:
  - DayWindow.anchoredWrappedEndMinute(LocalTime, LocalTime) -- the single wrap-aware end-offset primitive
  - RestSpan.gapMinutes corrected to measure the true gap against an overnight predecessor
  - requireRestFeasibility's SLOT pre-horizon branch corrected to refuse against a wrapping historical predecessor
affects: [23-02 (remaining fixture coverage across four more test classes), 23-03 (structural guard against a third occurrence)]

actuals:
  tokens: 4770
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Wrap-aware end-offset composed from already-correct primitives (anchoredStartMinute + anchoredDurationMinutes), never inlined a second time at a call site"
    - "One named DayWindow instance method shared by both production call sites, closing a documented single-implementation discipline (D-08) that had already drifted once"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/util/DayWindow.java
    - src/main/java/com/wfm/model/RestSpan.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/util/DayWindowTest.java
    - src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java
    - src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java

key-decisions:
  - "anchoredWrappedEndMinute lands as a public INSTANCE method on DayWindow (never static), placed immediately after anchoredDurationMinutes, preserving DayWindowTest.NoPublicStaticTakesABareSchedulingTime's frozen ANCHORED set with no edit"
  - "Only the remainingInPrevDay line in RestSpan.gapMinutes changes; the successor-side elapsedIntoNextDay line and the dayStart guard clause are untouched, per Pitfall 3's explicit instruction"
  - "SolverService.requireRestFeasibility's SLOT pre-horizon branch is the only line changed in that file; the synthetic in-horizon estimate and the D-08 'must never drift' comment are both left in place, now true rather than aspirational"

requirements-completed: [REST-01, REST-02, REST-05, OVNT-01]

coverage:
  - id: D1
    description: "An overnight predecessor (22:00-06:00) measures a true 60-minute gap against a 07:00 successor, not the pre-fix 1500, and penalises 600 under a 660-minute minimum"
    requirement: "REST-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#overnightPredecessor_trueGapMeasuredCorrectly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#gapMinutes_overnightPredecessor_matchesTrueGap"
        status: pass
    human_judgment: false
  - id: D2
    description: "anchoredWrappedEndMinute is pinned directly at two anchors (00:00 and 21:00), both sides of the wrap threshold (1440/1441), and the successor side of the gap formula is proven numerically unchanged"
    requirement: "REST-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#WrappedEndMinute (8 test methods)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#overnightPredecessor_gapExactlyAtMinimum_notPenalised"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#overnightPredecessor_gapOneMinuteShortOfMinimum_penalisedByOne"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java#overnightSuccessorWithNonWrappingPredecessor_unchangedByTheFix"
        status: pass
    human_judgment: false
  - id: D3
    description: "requireRestFeasibility's SLOT pre-horizon branch refuses an agent-day whose historical predecessor wrapped (best achievable gap 360 against 660), where it previously computed 1800 and refused nothing"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java#slot_preHorizonOvernightSpan_refusedOnTheTrueWrappedEnd"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java#shift_preHorizonOvernightSpan_refusedOnTheTrueGap"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java#slot_preHorizonEdge_usesAcceptedActualEnd_refusedWithExactFigure (pre-existing 1020 figure, confirmed unchanged)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Constraint classification and non-vacuity tables are provably untouched -- this phase adds no constraint"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-04
status: complete
---

# Phase 23 Plan 1: End-to-end overnight-predecessor rest gap fix Summary

**Corrected `RestSpan.gapMinutes` and `requireRestFeasibility`'s SLOT pre-horizon branch to both read a predecessor's true wrapped end through one new shared `DayWindow.anchoredWrappedEndMinute` primitive, closing the audit's G-1 gap (1500-minute overstatement collapsing to the true 60) and its previously-unnamed sibling defect at the SLOT pre-horizon call site.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-10-04 (session start)
- **Completed:** 2026-10-04T20:08:45Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- Added `DayWindow.anchoredWrappedEndMinute(LocalTime, LocalTime)` — the single wrap-aware end-offset primitive, composed from the already-correct `anchoredStartMinute` + `anchoredDurationMinutes`, collapsing onto `anchoredEndMinute` exactly in the non-wrapping case
- `RestSpan.gapMinutes` now measures an overnight-predecessor pair (22:00–06:00 into 07:00) as a true 60-minute gap, not the pre-fix 1500, penalising 600 under a 660-minute minimum
- `SolverService.requireRestFeasibility`'s SLOT pre-horizon branch now refuses a wrapping historical predecessor (best achievable gap 360 against 660), where it previously computed 1800 and refused nothing
- Both production call sites now read a wrapped end offset from the one shared primitive — no third spelling of the composition exists anywhere, restoring the D-08 "one and only gap-minutes implementation" discipline

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end "an overnight predecessor is a hard rest violation"** - `563e1e8` (fix)
2. **Task 2: Pin the primitive directly and prove the successor side did not move** - `cce0b2b` (test)
3. **Task 3: Close the sibling defect — requireRestFeasibility's SLOT pre-horizon branch** - `a6c9210` (fix)

**Plan metadata:** (pending — docs commit below)

## Files Created/Modified
- `src/main/java/com/wfm/util/DayWindow.java` - adds `anchoredWrappedEndMinute(start, end)`, a public instance method
- `src/main/java/com/wfm/model/RestSpan.java` - `gapMinutes` derives `remainingInPrevDay` from the predecessor's wrapped end
- `src/main/java/com/wfm/service/SolverService.java` - `requireRestFeasibility`'s SLOT pre-horizon branch reads the historical span's wrapped end through the same primitive
- `src/test/java/com/wfm/util/DayWindowTest.java` - new `WrappedEndMinute` nested class (8 tests) pinning the primitive at two anchors
- `src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java` - 5 new tests: the overnight-predecessor proof, the direct `gapMinutes` unit case, the exact-equality and one-minute-short boundaries, and the overnight-successor symmetry case
- `src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java` - 2 new tests: Finding 2's SLOT proof case and its SHIFT sibling

## Decisions Made
- `anchoredWrappedEndMinute` placed immediately after `anchoredDurationMinutes` as a public instance method (never static), per the plan's explicit instruction protecting `NoPublicStaticTakesABareSchedulingTime`'s frozen set
- No structural guard (`RestGapArithmeticGuardTest`) was built in this plan — that is explicitly plan 23-03's scope per the phase's artifact ownership table; this plan's deliverable is the fix plus its direct fixture-level proof only
- Followed the plan's fixture numbers exactly (60/600/360/1800 etc.) rather than re-deriving from scratch, since RESEARCH.md's hand-derivation was independently verified against the live source during task execution

## Deviations from Plan

None — plan executed exactly as written. One verification-command anomaly was found and is documented rather than silently worked around:

### Verification anomaly (not a deviation, not auto-fixed)

Task 3's acceptance criterion `grep -c 'the two must never drift' src/main/java/com/wfm/service/SolverService.java` is specified to print `1`, confirming the D-08 comment was kept. The live comment at `SolverService.java:2045-2046` reads `// Same structural shape as RestSpan.gapMinutes (D-08: the two must never\n                    // drift) -- ...` — the phrase wraps across two source lines (confirmed via `git show 9ebc81b:...` to predate this entire phase, from Phase 22 commit `db44048`). A literal single-line grep for `'the two must never drift'` therefore prints `0` regardless of whether the comment is present, deleted, or reworded, because the matched substring never appears on one line. This is a pre-existing plan-verification-command limitation, not a defect introduced by this plan: `git diff -- src/main/java/com/wfm/service/SolverService.java` confirms those exact two lines are byte-for-byte untouched by this plan's edit (only the `predecessorEndMinute` assignment line and its preceding comment, several lines above, were changed). The underlying property the criterion checks — the D-08 comment was kept, not deleted — holds; the grep pattern itself cannot observe it due to the comment's pre-existing line wrap. No source change was made to artificially satisfy the literal grep, since reformatting a comment purely to pass a brittle check was not requested and risks being read as an unauthorized edit to code the plan explicitly says to leave alone ("Do not delete it as stale").

**Total deviations:** 0 auto-fixed. One verification-command limitation documented above (pre-existing, not introduced by this plan).
**Impact on plan:** None on correctness or scope. All six acceptance-criteria greps that depend on single-line content matched exactly as specified; only this one pre-wrapped multi-line comment's grep is structurally unable to match.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 23-02 can proceed: it expands fixture coverage to `MinimumRestSlotConstraintTest`, `RestPredecessorServiceTest`, and `RestWaiverDisclosureTest`, all of which now call into the corrected `RestSpan.gapMinutes` and will exercise the same `anchoredWrappedEndMinute` primitive this plan introduced.
- Plan 23-03 can proceed: the structural guard (`rest-gap-arithmetic-guard.md` + `RestGapArithmeticGuardTest`) targets exactly the two call sites this plan fixed (`RestSpan.gapMinutes`, `requireRestFeasibility`'s SLOT branch) plus `DayWindow.anchoredWrappedEndMinute` itself as the sanctioned implementation set.
- No blockers. All targeted test classes pass; `src/test/resources/midnight-time-arithmetic.md` is unmodified; no full-suite run was attempted in this plan (by design — plan 23-03 owns the single full-suite gate per the plan's own verification notes).

## Self-Check: PASSED

All 7 created/modified files confirmed present on disk; all 3 task commits (`563e1e8`, `cce0b2b`, `a6c9210`) confirmed in `git log`; all plan-level `<acceptance_criteria>` and `<verify>` commands re-run fresh and passing (24/24 `MinimumRestShiftConstraintTest`, 20/20 `RestFeasibilityRefusalTest`, 8/8 `DayWindowTest$WrappedEndMinute`, 7/7 `ScheduleConstraintClassificationTest`, 5/5 `ConstraintMatchCountNonVacuityTest`); `midnight-time-arithmetic.md` byte-identical.

---
*Phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh*
*Completed: 2026-10-04*
