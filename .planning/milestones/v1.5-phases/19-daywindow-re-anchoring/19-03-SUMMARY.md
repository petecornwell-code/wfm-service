---
phase: 19-daywindow-re-anchoring
plan: 03
subsystem: solver
tags: [daywindow, bday-04, timefold, constraint-stream, bound-instance]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-01's ScheduleConfig.dayStart additive channel (commit 47649f1) — the value this plan's tracer consumes"
provides:
  - "DayWindow.anchoredAt(LocalTime) — a bound-instance factory, plus nine new public instance methods composing the five Phase-18 anchored primitives against the bound anchor"
  - "ShiftBandPair.covers(Timeslot, DayWindow) and the 7-argument static covers(..., DayWindow) — the anchor-aware coverage predicate, one implementation, four call shapes"
  - "ScheduleConstraintProvider.shiftEnvelopeCompliance consuming the real desk anchor via ScheduleConfig.dayStart() — the first solver constraint in the milestone whose verdict changes at a non-midnight anchor"
  - "The first re-anchoring commit (3d45274) — the exclusive lower bound plan 19-08's criterion 5 commit-range check anchors on, immediately after plan 19-01's additive commit (47649f1)"
affects: [19-04-daywindow-reanchoring, 19-05-daywindow-reanchoring, 19-06-daywindow-reanchoring, 19-07-daywindow-reanchoring, 19-08-daywindow-reanchoring]

actuals:
  tokens: 8833
  tasks: 1
  commits: 1
  plan_head_before: 6833ea3ce9694ff56e9fcffaa7416e5313411afc

tech-stack:
  added: []
  patterns:
    - "Static-utility-to-bound-instance conversion (D-04): a private final field plus a private constructor plus a public static factory (anchoredAt), with the formerly-static composite functions becoming instance methods that compose the five already-anchored primitives. No analog existed anywhere else in this codebase before this plan (19-PATTERNS.md Shape A)."
    - "Disambiguated transitional naming for an unavoidable static/instance name collision: when a public static and a public instance method would otherwise need the identical name and parameter list in the same class — which does not compile — the new instance methods carry a distinguishing prefix (anchored*) rather than the bare name, so the pre-existing deprecated statics keep compiling for every call site not yet migrated."
    - "Four-form single-implementation discipline, extended: a model method keeps exactly one real implementation and every other arity/shape (1-arg instance, 6-arg static, 2-arg instance, 7-arg static) delegates to it — the same discipline ShiftBandPair.covers already followed for two forms now covers four."

key-files:
  created:
    - src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java
    - src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java
  modified:
    - src/main/java/com/wfm/util/DayWindow.java
    - src/main/java/com/wfm/model/ShiftBandPair.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java

key-decisions:
  - "The nine new DayWindow instance methods are named anchoredStartMinute, anchoredEndMinute, anchoredDurationMinutes, anchoredIsForwardWithinDay, anchoredOverlaps, anchoredContains, anchoredStartsBefore, anchoredToLocalTime and anchoredPlusWithinDay — NOT the bare midnight-implicit names (startMinute, durationMinutes, etc.) the plan text describes verbatim. Verified by direct javac compilation that a public static and a public instance method cannot share an identical name and parameter list in the same class, and the phase's own execution constraints require the nine statics to stay public and unchanged through wave 19-07 for call sites not yet migrated (confirmed: DayWindowTest.java alone calls all nine statics directly ~100+ times and must not be touched). Plans 19-04 through 19-08 should use these exact names wherever their text says 'window.startMinute(...)' etc. Plan 19-08 is free to rename the private demoted statics internally if it wants to reclaim the bare names for the public instance API once nothing calls them publicly — not required, and this plan recommends against the extra churn."
  - "shiftEnvelopeCompliance falls back to LocalTime.MIDNIGHT when ScheduleConfig.dayStart() is null, rather than propagating anchoredAt's null-rejection into the constraint. Production never supplies null (SolverService.buildSchedule always copies Desk.dayStart, which defaults to MIDNIGHT per Desk.java:37), so this only affects hand-built test Schedules (and any Schedule persisted before plan 19-01's V54 migration) that never call setDayStart — exactly the same desk population the midnight-implicit covers() always served. Found via the full-suite run after the first implementation attempt (see Deviations)."
  - "ShiftBandPair's one-argument covers(Timeslot) and six-argument static covers(...) forms are kept, unchanged in call shape, each now delegating through DayWindow.anchoredAt(LocalTime.MIDNIGHT) to the new anchor-aware form — not altered in place. ScheduleOutputService (lines 837, 873) and MidnightWindowSeamTest (line 125) call the six-argument static form directly today; changing its arity in place rather than adding an overload would have broken both, neither of which plan 19-03 is scoped to touch."

requirements-completed: [BDAY-04]

coverage:
  - id: D1
    description: "DayWindow.anchoredAt(LocalTime) returns a bound instance exposing nine public instance methods, each keeping its midnight-implicit counterpart's exact parameter list; the nine old statics stay public and @Deprecated; anchoredAt(null) throws; the two equal-endpoint duration cases and the removed non-forward throw behave exactly as specified."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java#AnchoredAtNullCheck.anchoredAtNullThrows"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java#MidnightAnchorAgreement (6 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java#AnchoredBehaviorAt2100 (2 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java#EqualEndpointCases (2 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java#OverlapsSymmetry.overlapsIsSymmetricOverBoundaryAdjacentQuadruples"
        status: pass
      - kind: other
        ref: "grep -cE '^[[:space:]]*@Deprecated' src/main/java/com/wfm/util/DayWindow.java == 9"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftBandPair.covers reaches one implementation across all four forms it now carries (1-arg instance, 2-arg instance, 6-arg static, 7-arg static), including the transitional midnight-implicit forms plan 19-05 removes."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java (pre-existing, re-run green against the 1-arg transitional delegate)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/MidnightWindowSeamTest.java (pre-existing, re-run green against the unchanged 6-arg static)"
        status: pass
    human_judgment: false
  - id: D3
    description: "A desk's day-start anchor reaches ScheduleConstraintProvider.shiftEnvelopeCompliance through SolverService.buildSchedule -> Schedule.getScheduleConfig() -> cfg.dayStart(), and changes the constraint's verdict for the same overnight envelope and slot: not penalised at a 21:00 anchor, penalised at a 00:00 anchor."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java#deskAnchorChangesTheConstraintVerdict"
        status: pass
    human_judgment: false
  - id: D4
    description: "The full suite is green, including the eight Phase 18 guard tests (MidnightBoundaryRegressionTest, MidnightBoundaryPropertyTest, BusinessDateWritePathGuardTest, TimeslotGeneratorBusinessDateTest, MidnightGapScanTest, MidnightWindowSeamTest, MidnightTimeArithmeticGuardTest, DayWindowTest), and nothing outside the one wired path moved."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "./gradlew test (full, unfiltered) — 1134+ tests, 0 failures, 0 errors after the Rule 2 fix"
        status: pass
    human_judgment: false

duration: 40min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 03: Prove The Tracer — One Desk Anchor Reaches One Constraint Summary

**`DayWindow.anchoredAt(LocalTime)` binds a desk's business-day anchor into a reusable instance; `ShiftBandPair.covers` and `ScheduleConstraintProvider.shiftEnvelopeCompliance` consume it end to end, and a 21:00 anchor now legalises an overnight seat a 00:00 anchor forbids — the first proof in this milestone that a desk anchor changes a solver decision.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-10-01T00:46:07Z
- **Completed:** 2026-10-01T01:24:46Z
- **Tasks:** 1
- **Files modified:** 5 (3 main, 2 new test files)

## Accomplishments
- `DayWindow` gains a `private final LocalTime dayStart` field, a private constructor, and `public static DayWindow anchoredAt(LocalTime dayStart)` — null-checked exactly like every other anchor parameter in the class, never binding an implicit midnight.
- Nine new public instance methods (`anchoredStartMinute`, `anchoredEndMinute`, `anchoredDurationMinutes`, `anchoredIsForwardWithinDay`, `anchoredOverlaps`, `anchoredContains`, `anchoredStartsBefore`, `anchoredToLocalTime`, `anchoredPlusWithinDay`) each compose the five Phase-18 anchored primitives against the bound anchor — zero new interval arithmetic written, per the plan's prohibition. `anchoredDurationMinutes` no longer throws on a non-forward interval; that condition now wraps forward a full day (criterion 2), with the zero-length-vs-whole-day boundary handled explicitly (a non-anchor instant equal to itself is 0 minutes; the anchor equal to itself is 1440).
- The nine old midnight-implicit public statics are **completely unchanged** — same names, same bodies, same `@Deprecated` javadoc — so every one of the ~83 call sites not yet migrated (TimeslotGeneratorService, ScheduleOutputService, ShiftTemplateService, etc.) keeps compiling exactly as it did before this plan.
- `ShiftBandPair` gains `covers(Timeslot, DayWindow)` and a 7-argument static `covers(..., DayWindow)`. The one implementation lives in the 7-argument static form; the pre-existing 1-argument instance form and 6-argument static form are kept as `@Deprecated` transitional delegates to `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`, breaking none of their ten-plus existing callers (`ScheduleOutputService`, `SolverService`, `ScheduleEnvelopeRepairService`, `ShiftStartMixTargetService`, and the `MidnightWindowSeamTest`/`ZeroDemandTimeslotCeilingTest`/etc. test callers).
- `ScheduleConstraintProvider.shiftEnvelopeCompliance` — the only constraint this plan touches — binds a `DayWindow` from `ScheduleConfig`'s anchor field inside its final `.filter` (where `cfg` is already a tuple member) and calls the new two-argument `covers`. No other constraint moved.
- `DayWindowAnchorBindingTest` (14 tests) and `DeskAnchorReachesConstraintTest` (1 test, walking the real `buildSchedule -> Schedule.getScheduleConfig() -> shiftEnvelopeCompliance` chain via `ConstraintVerifier`) prove every `<behavior>` item in the plan, including the headline case: a `22:00`–`02:00` envelope against a `23:00`–`00:00` slot is legal at a `21:00` anchor and illegal at a `00:00` anchor.
- Full unfiltered `./gradlew test` is green: 1134+ tests, 0 failures, 0 errors (confirmed after the Rule 2 fix below; see Deviations for the one regression this plan introduced and caught before committing).

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end "a desk's day start decides whether a seat is legal" — one path only** - `3d45274` (feat)

**Plan metadata:** (this commit, docs(19-03): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/util/DayWindow.java` - `anchoredAt(LocalTime)`, the bound `dayStart` field, nine `anchored*` instance methods, and a "Naming note" javadoc paragraph documenting the deviation below
- `src/main/java/com/wfm/model/ShiftBandPair.java` - `covers(Timeslot, DayWindow)`, the 7-argument static `covers`; the 1-argument and 6-argument forms retired to `@Deprecated` transitional delegates
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `shiftEnvelopeCompliance` binds a `DayWindow` from `cfg.dayStart()` (falling back to `MIDNIGHT` when unset) and calls the new 2-argument `covers`
- `src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java` - new; 14 tests covering every `<behavior>` item that concerns `DayWindow` alone
- `src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java` - new; the tracer's single end-to-end proof via `ConstraintVerifier` and the real `buildSchedule` path

## Decisions Made
- **Instance-method naming (see key-decisions above for the full reasoning):** chosen names `anchoredStartMinute`/`anchoredEndMinute`/`anchoredDurationMinutes`/`anchoredIsForwardWithinDay`/`anchoredOverlaps`/`anchoredContains`/`anchoredStartsBefore`/`anchoredToLocalTime`/`anchoredPlusWithinDay`, not the bare midnight-implicit names. This is the single most consequential decision in this plan for downstream executors — **read it before starting 19-04 through 19-08**.
- `shiftEnvelopeCompliance` treats a `null` `cfg.dayStart()` as `MIDNIGHT` rather than propagating `anchoredAt`'s null-rejection — a defensive default matching exactly what the midnight-implicit `covers()` always did, needed only because this plan's own fix, not because production ever supplies null.
- `ShiftBandPair`'s 1-arg and 6-arg forms are kept as separate overloads (not rewritten in place), since two existing callers of the 6-arg static form (`ScheduleOutputService`, `MidnightWindowSeamTest`) are outside this plan's scope and would otherwise break.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug, discovered via compiler] The plan's literal instance-method names do not compile alongside the retained statics**
- **Found during:** Task 1 (before writing any code — confirmed by direct `javac` experiment)
- **Issue:** The plan's `<action>` and `<acceptance_criteria>` describe nine new public instance methods named identically to the nine existing public static methods (`startMinute`, `durationMinutes`, etc.), with the same parameter lists. A public static method and a public instance method cannot share an identical name and parameter list in the same Java class — proven by a minimal `javac` compile (`method bar(int) is already defined in class Foo`) — and the phase's own execution constraints require the nine statics to stay public, unchanged, and under their current names through wave 19-07, because `DayWindowTest.java` alone (explicitly forbidden to touch) calls all nine directly roughly 100+ times, and ~83 production call sites across 16 files are not migrated until plans 19-04–19-07.
- **Fix:** Implemented the nine new instance methods with an `anchored` prefix (`anchoredStartMinute`, etc.) instead of the bare names, keeping every other requirement (same parameter lists, same behavior, composing the five Phase-18 anchored primitives) exactly as specified. Documented the naming choice in `DayWindow`'s class javadoc ("Naming note") and in this SUMMARY's `key-decisions` for 19-04 through 19-08 to consume.
- **Files modified:** `src/main/java/com/wfm/util/DayWindow.java`, `src/main/java/com/wfm/model/ShiftBandPair.java`, `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`, both new test files
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; all nine `anchored*` methods exist with the required parameter lists (verified by targeted grep); `grep -cE '^[[:space:]]*@Deprecated' DayWindow.java` reports 9 unchanged.
- **Committed in:** `3d45274` (Task 1 commit)

**2. [Rule 1 - Bug] Null `ScheduleConfig.dayStart()` crashed `shiftEnvelopeCompliance` for every pre-existing SHIFT-mode test fixture**
- **Found during:** Task 1 (the plan-mandated full unfiltered `./gradlew test` run, after the targeted tests passed)
- **Issue:** 29 pre-existing tests across 9 classes (`MidnightBoundaryRegressionTest`, `ConstraintPrecedenceObservabilityTest`, `SeatSupplyDistributionAnalysisTest`, `ShiftDeskEndToEndRegressionTest`, `ShiftEnvelopeGroundTruthTest`, `ShiftEnvelopeSupplyInvariantTest`, `ShiftModeBreakGatingTest`, `ShiftStartMixSteerTest`, `SolverQualityGuardTest`) hand-build a `Schedule` via `new Schedule()` and never call `setDayStart` — a genuinely null anchor per plan 19-01's own "unsetDayStartSurfacesAsNullNotMidnight" design. The first implementation called `DayWindow.anchoredAt(cfg.dayStart())` directly, which threw `IllegalArgumentException: dayStart must not be null` the instant `shiftEnvelopeCompliance` evaluated any SHIFT-mode match, failing all 29 tests with the identical stack trace.
- **Fix:** `shiftEnvelopeCompliance`'s filter now reads `cfg.dayStart()` into a local once, and binds `DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT)` — falling back to the same midnight default every pre-existing fixture implicitly relied on, and that `Desk.dayStart` itself defaults to in production. `grep -c 'cfg.dayStart()'` in the file still reports exactly 1 (the local-variable read), satisfying the plan's literal verify check.
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** Re-ran the 9 previously-failing classes plus a full unfiltered `./gradlew test` — 1134+ tests, 0 failures, 0 errors.
- **Committed in:** `3d45274` (Task 1 commit; caught and fixed before committing, so no separate fix-up commit was needed)

---

**Total deviations:** 2 auto-fixed (1 Rule 1 naming-impossibility bug in the plan text, 1 Rule 1 null-handling regression caught by this plan's own mandated full-suite run before committing)
**Impact on plan:** The naming deviation changes what future plans must call (`window.anchoredDurationMinutes(...)` etc., not `window.durationMinutes(...)`) — a real adaptation 19-04 through 19-08 must make, documented clearly above. The null-handling fix is purely defensive and restores exactly the pre-existing behavior for every un-migrated fixture; it does not touch any `DayWindow` call site's production semantics.

## Issues Encountered
None beyond the two deviations above, both resolved before the task commit.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The bound-instance architecture is proven end-to-end and committed (`3d45274`, immediately after plan 19-01's additive `47649f1` — the two-commit range plan 19-08's criterion 5 check spans).
- **Naming is the one thing every remaining plan in this phase must read before writing code:** the nine new `DayWindow` instance methods are `anchored`-prefixed, not bare-named. Plans 19-04 through 19-07 should call `window.anchoredContains(...)`, `window.anchoredPlusWithinDay(...)`, `window.anchoredDurationMinutes(...)`, etc. — not the plan text's literal `window.contains(...)`/`window.plusWithinDay(...)`/`window.durationMinutes(...)`.
- `ShiftBandPair.covers`'s four-form shape is ready for plan 19-05's removal of the transitional 1-argument and 6-argument midnight-implicit forms once their remaining callers (`ScheduleOutputService`, `SolverService`, `ScheduleEnvelopeRepairService`, `ShiftStartMixTargetService`) are migrated.
- No blockers. Full unfiltered `./gradlew test` is green (0 failures, 0 errors) both before and after this plan's edits.

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/util/DayWindow.java` contains `public static DayWindow anchoredAt(LocalTime`
- FOUND: `src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java`
- FOUND: `src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java`
- FOUND: `.planning/phases/19-daywindow-re-anchoring/19-03-SUMMARY.md`
- FOUND commit `3d45274` (task commit) in `git log --oneline --all`
- CONFIRMED: `grep -cE '^[[:space:]]*@Deprecated' src/main/java/com/wfm/util/DayWindow.java` reports `9`
- CONFIRMED: `grep -c 'public static DayWindow anchoredAt' src/main/java/com/wfm/util/DayWindow.java` reports `1`
- CONFIRMED: `grep -c 'cfg.dayStart()' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` reports `1`
- CONFIRMED: `./gradlew test --tests "com.wfm.util.DayWindowAnchorBindingTest" --tests "com.wfm.solver.DeskAnchorReachesConstraintTest"` — 15 tests, 0 failures, 0 errors
- CONFIRMED: full, unfiltered `./gradlew test` — 1134+ tests, 0 failures, 0 errors
- CONFIRMED: `git diff --stat 6833ea3ce9694ff56e9fcffaa7416e5313411afc..3d45274` touches only the five files listed above

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
