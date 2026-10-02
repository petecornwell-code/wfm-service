---
phase: 20-solver-business-date-correctness
plan: 10
subsystem: solver
tags: [timefold, business-date, minimum-staffing, seat-expansion, tdd, javadoc]

# Dependency graph
requires:
  - phase: 20-solver-business-date-correctness (plan 20-09)
    provides: "The live generate-timeslots endpoint and DeskService.setDayStart's precision gate, which made a non-midnight desk fully reachable through the API"
provides:
  - "SolverService.expandMinimumStaffingSeats' two date-sensitive reads inside its SHIFT-mode branch (shift-template weekday eligibility, and the workingAgentDaysByDate count lookup) resolve the timeslot's business date, matching the sibling function requireShiftEnvelopeSeatSupply's key system"
  - "A 21:00-anchored proof (MinimumStaffingSeatsBusinessDateTest) separating the weekday-eligibility and count-lookup halves of the defect, each against a named non-zero expected count, with a midnight-anchored control and two anchor-adjacency cases"
  - "The fixture gap that would otherwise make the new business-date read fail open on a null value is closed in ShiftModeMinimumStaffingSeatSupplyTest and ZeroDemandTimeslotCeilingTest"
  - "The stale present-tense javadoc (and two sibling present-tense passages) in MidnightBoundaryRegressionTest's NonMidnightAnchor class now describe the pre-migration solver in the past tense"
affects: [20-11]

# Actuals (#2632) -- chars/4 over the realized diff (git diff, not whole-file size)
actuals:
  tokens: 5881
  tasks: 2
  commits: 4
  plan_head_before: 620f65c1d2e8168293b025e34a711aaeadc451a0

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "An envelope whose start and end both equal the desk's anchor spans the entire business day (DayWindow's end-equals-anchor-means-end-of-day rule applied to a template with no break), used here to isolate a seat-expansion test fixture to the weekday-eligibility filter and the count lookup without needing to also reason about envelope-coverage boundaries"
    - "A fixture proving a date-resolution defect at a non-midnight anchor must set BOTH the calendar date and the business date explicitly and independently per timeslot -- deriving one from the other, or defaulting either, defeats the proof"

key-files:
  created:
    - src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
    - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
    - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java

key-decisions:
  - "Isolated the new proof's coverage check to a single full-day envelope template (start == end == anchor) rather than reusing the existing analog fixtures' partial-day shapes, so every scenario tests only the weekday-eligibility filter and the count lookup -- never an incidental envelope-boundary interaction"
  - "Designed the two adjacency cases ('starts exactly at anchor' / 'ends exactly at anchor') from DayWindow.businessDateOf's own documented rule (time >= anchor -> same calendar date; time < anchor -> calendar date minus one day), not from an assumption -- confirmed against the real method before writing the fixture"
  - "Widened Task 2 beyond the plan's literal single-javadoc scope to three more present-tense passages in the same NonMidnightAnchor class that the plan's own grep-based verify gate could not pass otherwise -- recorded as a deviation below, not silently absorbed into the 'one javadoc block' framing"

patterns-established:
  - "A 21:00-anchored javadoc-stated proof class separates weekday-eligibility and count-lookup failure modes into independent test methods, each asserting a named non-zero count derived from the fixture's own stated shape, never from an observed run"

requirements-completed: [SOLV-01, SOLV-05]

coverage:
  - id: D1
    description: "expandMinimumStaffingSeats' weekday-eligibility filter resolves a timeslot's business date, not its calendar date"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java#weekdayEligibility_evaluatedAgainstBusinessDate_notCalendarDate"
        status: pass
    human_judgment: false
  - id: D2
    description: "expandMinimumStaffingSeats' workingAgentDaysByDate count lookup resolves a timeslot's business date, not its calendar date"
    requirement: "SOLV-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java#workingAgentDayCount_lookedUpByBusinessDate_notCalendarDate"
        status: pass
    human_judgment: false
  - id: D3
    description: "The fix is a no-op on a midnight-anchored desk, where calendar date and business date always coincide"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java#midnightAnchor_calendarAndBusinessDateCoincide_fixIsANoOp"
        status: pass
      - kind: integration
        ref: "com.wfm.solver.PhilUsShapedDriftGuardTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "A timeslot exactly at the anchor boundary attributes to exactly one business day, never both and never neither"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java#timeslotStartingExactlyAtAnchor_opensTheNewBusinessDay"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java#timeslotEndingExactlyAtAnchor_closesThePreviousBusinessDay"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every fixture driving the SHIFT-mode branch (ShiftModeMinimumStaffingSeatSupplyTest, ZeroDemandTimeslotCeilingTest) supplies a business date, so the new read cannot fail open on null"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest (6 tests)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ZeroDemandTimeslotCeilingTest (6 tests)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Advisory 2 (stale present-tense javadoc) is dispositioned: MidnightBoundaryRegressionTest's NonMidnightAnchor class no longer describes the pre-migration solver in the present tense"
    verification:
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest (full suite, same test count)"
        status: pass
    human_judgment: false

duration: 35min
completed: 2026-10-02
status: complete
---

# Phase 20 Plan 10: Minimum-Staffing Seat Expansion Resolves the Business Day Summary

**`expandMinimumStaffingSeats`' two date-sensitive reads now resolve a timeslot's business date instead of its calendar date, closing the identical key-system mismatch its sibling function was already fixed for — proven on a 21:00-anchored desk that separates weekday eligibility from the count lookup, each against a named non-zero expected count.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-10-01T23:00:00Z (approx)
- **Completed:** 2026-10-01T23:35:00Z (approx)
- **Tasks:** 2
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments

- `SolverService.expandMinimumStaffingSeats`' SHIFT-mode branch re-keys both of its date-sensitive reads — the shift-template weekday-eligibility filter and the `workingAgentDaysByDate` count lookup — from `ts.getDate()` (calendar date) to `ts.getBusinessDate()`, mirroring the already-committed fix in the sibling function `requireShiftEnvelopeSeatSupply` earlier in the same file. Closes GAP 2 of `20-VERIFICATION.md` (CR-02).
- `MinimumStaffingSeatsBusinessDateTest` (new, 5 tests) proves the fix on a 21:00-anchored desk: a weekday-eligibility case (a Monday-only template must seat a post-midnight Tuesday-calendar timeslot because its business day is Monday), a count-lookup case (an exact 3-seat count must be read on the business date, not defaulted to the 1-seat floor on the calendar date), a midnight-anchored control (the fix is a provable no-op where the two dates coincide), and two anchor-adjacency cases (a timeslot starting exactly at the 21:00 anchor opens the new business day; one ending exactly at the anchor closes the previous one).
- The fixture gap that would otherwise make the new business-date read NPE (via `ShiftTemplate.isEffectiveOn` dereferencing a null date) or fail open is closed: `ShiftModeMinimumStaffingSeatSupplyTest`'s `timeslot(...)` factory and its Saturday-copy site, and `ZeroDemandTimeslotCeilingTest`'s `timeslot(...)` factory, now set the business date alongside the calendar date they already set (both midnight-anchored fixtures, where the two coincide).
- Advisory 2 of `20-VERIFICATION.md` is dispositioned: `MidnightBoundaryRegressionTest`'s `NonMidnightAnchor` nested class's javadoc — and, per a documented scope widening below, two sibling present-tense passages in the same class — no longer claim in the present tense that the solver "still runs every interval" on the retired placeholder anchor or "still joins" on calendar date. All now explicitly frame that as the pre-plan-20-05-migration state, with the falsifiability argument (assertions argued against the migrated solver's required output, never copied from observed output) kept intact.

## Task Commits

Each task followed the TDD discipline the plan specified:

1. **Task 1 (tracer, tdd): seat expansion resolves the business day on a 21:00 desk**
   - `b6a2b19` — `test(20-10): set business date in SHIFT-branch timeslot fixtures (SOLV-01)` (fixture repair — no production diff, no assertion change; both repaired classes stayed green before and after)
   - `47794d6` — `test(20-10): add failing proof for seat-expansion business-date reads (SOLV-01/SOLV-05)` (RED)
   - `05a98ac` — `feat(20-10): resolve business date in minimum-staffing seat expansion (SOLV-01/SOLV-05)` (GREEN)
2. **Task 2: the non-midnight-anchor javadoc stops asserting a pre-migration solver in the present tense**
   - `7474266` — `docs(20-10): correct stale present-tense javadoc in NonMidnightAnchor (SOLV-01/SOLV-03)`

**Plan metadata:** committed alongside this SUMMARY.

## RED Evidence (observed, not claimed)

Before the production change (commit `05a98ac`), `MinimumStaffingSeatsBusinessDateTest` reported **3 of 5 tests failing**:

- `weekdayEligibility_evaluatedAgainstBusinessDate_notCalendarDate` — expected 4 seats on the post-midnight timeslot, **observed 0**. The weekday filter, reading `ts.getDate()` (calendar Tuesday), rejected the only template on the desk (valid Monday only) even though the timeslot's business day is Monday.
- `workingAgentDayCount_lookedUpByBusinessDate_notCalendarDate` — expected 3 seats, **observed 1** (the `MIN_AGENTS_PER_TIMESLOT` floor). The count lookup, keyed on `ts.getDate()` (calendar Tuesday, absent from the map), missed its key and silently defaulted to zero.
- `timeslotEndingExactlyAtAnchor_closesThePreviousBusinessDay` (incidental third failure, not required by the plan's acceptance criteria but recorded here for completeness) — expected 9 seats, **observed 1**, same lookup defect triggered by this adjacency scenario (calendar date Monday vs. business date Sunday).

The midnight-anchored control and the "starts exactly at anchor" adjacency case both passed before the fix, as expected: in both, calendar date and business date coincide, so neither accessor choice is distinguishable there.

After the production change, all 5 tests pass.

## Files Created/Modified

- `src/main/java/com/wfm/service/SolverService.java` — `expandMinimumStaffingSeats`' two date-sensitive reads inside the SHIFT-mode branch now call `ts.getBusinessDate()` instead of `ts.getDate()`; a comment above the eligibility filter explains why, mirroring the sibling function's comment discipline. No other change to the method (SLOT-mode short-circuit, already-seated short-circuit, specialization cycle, and ordering all textually unchanged — confirmed by `git show --format=` over the commit).
- `src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java` — new, 5 tests, package `com.wfm.service`, calls `SolverService.expandMinimumStaffingSeats` directly.
- `src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java` — `timeslot(...)` factory and the Saturday-copy site in `weekdayOnlyTemplateDoesNotCoverAWeekendHour` now set `businessDate` alongside the calendar `date` they already set.
- `src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java` — `timeslot(...)` factory now sets `businessDate` alongside `date`.
- `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` — the `NonMidnightAnchor` nested class's javadoc, plus two sibling present-tense passages inside that same class's test bodies, rewritten in the past tense. See Deviations below.

## Decisions Made

- Built the new proof's templates as a single full-day envelope (`startTime == endTime == anchor`, no band) so every scenario is isolated to the weekday-eligibility filter and the count lookup, never an incidental envelope-coverage boundary interaction — `ShiftBandPair.covers` with a null band reduces to `window.anchoredContains`, which a `[0, 1440]`-offset envelope always satisfies regardless of the timeslot's clock time.
- Derived the two adjacency fixtures directly from `DayWindow.businessDateOf`'s documented rule (`time >= anchor` → same calendar date; `time < anchor` → calendar date minus one day) rather than assuming the shape, confirming it against the real method before writing the test.
- `MinimumStaffingSeatsTest` needed no change: confirmed by reading it that every call constructs `SchedulingMode.SLOT`, which returns from `expandMinimumStaffingSeats` before either migrated read.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug/plan inconsistency] Task 2's "confined to one javadoc block, at most 15 lines" criterion conflicted with its own grep-based verify gate**
- **Found during:** Task 2
- **Issue:** The plan scoped Task 2's action and read_first strictly to the `NonMidnightAnchor` class's javadoc at `:268-275`. But the plan's own `<verify>` step runs `grep -cF 'still joins'` and `grep -cF 'still runs every interval'` over the **whole file** and requires both to print 0. Two additional instances of the identical stale "today's solver still joins ... on calendar date" claim existed inside the same `NonMidnightAnchor` class but outside the javadoc — one in an assertion's `.as(...)` description string, one in a `//` comment inside a different test method — plus one adjacent "today's calendar-date join" phrase in the same method as the second (left half-stale if not also corrected, since it directly follows the fixed comment).
- **Fix:** Corrected the wording (tense only) in all three additional locations, applying the identical past-tense/pre-migration framing used in the javadoc fix. No assertion's expected VALUE changed, no test method renamed, no `@DisplayName` changed, no import touched — confirmed by `git show --name-only` over the production file and Task 1's proof file (empty output) and by re-running the class (same test count, all green).
- **Files modified:** `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java`
- **Verification:** `grep -cF 'still joins'` and `grep -cF 'still runs every interval'` both print 0; `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest"` passes with the same test count as before; `./gradlew test` (full suite) is green.
- **Committed in:** `7474266`
- **Impact:** The commit's total diff is 34 lines (19 insertions, 15 deletions), not the `<=15` the single-javadoc framing assumed. This is the one acceptance-criteria bullet not literally satisfied as written; the two grep-based bullets (the actually mechanically-enforced contract) and the "no production file / no Task 1 test file touched" bullet are all satisfied. No test behavior, assertion outcome, or test identity changed.

---

**Total deviations:** 1 auto-fixed (plan-inconsistency / scope widening, Rule 1).
**Impact on plan:** The widening was necessary to satisfy the plan's own automated verification gate; it is wording-only, confined to the same nested test class the plan already named, and does not touch any production file or Task 1's proof.

## Issues Encountered

None beyond the deviation above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- GAP 2 of `20-VERIFICATION.md` (the phase goal's "every solver join resolves business date" promise, CR-02) is closed: both `expandMinimumStaffingSeats` reads now resolve the business date, proven on a non-midnight-anchored desk with a midnight-anchored control.
- Advisory 2 (stale present-tense javadoc) is dispositioned and done.
- `./gradlew test` (full suite, independently re-run by this executor after all four commits): **1200 tests, 0 failures, 0 errors, 4 skipped** — green.
- `grep -cF 'ts.getBusinessDate()' src/main/java/com/wfm/service/SolverService.java` prints 2; `grep -cF 'ts.getDate()'` over the same file prints 0.
- `PhilUsShapedDriftGuardTest` passes 2/2 — nothing drifted on a midnight-anchored desk.
- Plan 20-11 (the remaining enumerated `SolverService` calendar-date reads, per Advisory 1's disposition) is unblocked: this plan touched only the two reads inside `expandMinimumStaffingSeats`' SHIFT-mode branch and the fixtures that feed it, leaving the four other calendar-date reads `20-11-PLAN.md` owns untouched.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-02*

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/service/SolverService.java`
- FOUND: `src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java`
- FOUND: `src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java`
- FOUND: `src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java`
- FOUND: `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java`
- FOUND: `.planning/phases/20-solver-business-date-correctness/20-10-SUMMARY.md`
- FOUND commits: `b6a2b19`, `47794d6`, `05a98ac`, `7474266`
- Re-ran all plan-level `<verification>` commands: `./gradlew test` green (full suite, 1200 tests, 0 failures, 0 errors, 4 skipped); `ts.getBusinessDate()` count 2 and `ts.getDate()` count 0 in `SolverService.java`; `MinimumStaffingSeatsBusinessDateTest` reports 5 passing tests with named non-zero counts; `setBusinessDate` present in both repaired fixture files; `PhilUsShapedDriftGuardTest` passes 2/2; `still joins` and `still runs every interval` both print 0 in `MidnightBoundaryRegressionTest.java`.
