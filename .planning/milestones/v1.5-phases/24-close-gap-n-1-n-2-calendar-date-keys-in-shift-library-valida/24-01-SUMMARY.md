---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
plan: 01
subsystem: shift-library-validation
tags: [java, spring, business-date, overnight-shifts, structural-guard, junit5]

requires:
  - phase: 18-20
    provides: "Timeslot.businessDate (BDAY-02), DayWindow day-start vocabulary, BusinessDateJoinGuardTest technique"
provides:
  - "ShiftLibraryValidationService keys every demand window on Timeslot.getBusinessDate(), the key SolverService reads"
  - "Window.describe(DayWindow): one shared operator-facing label, calendar date disclosed only when it differs from the business date"
  - "BusinessDateJoinGuardTest widened with a verb-free scan over an explicit four-file list"
affects: [24-02, 24-03, shift-library, schedule-envelope-repair, shift-start-mix]

requirements-completed: [OVNT-05, OVNT-07, SOLV-07, BDAY-02, BDAY-05]

actuals:
  tokens: 8912
  tasks: 3
  commits: 7
plan_head_before: a5d895c5d6e93189319c753519fdd8761f212eed
plan_head_after: 01401e82e5045903a55a58efa2385f481b11e5dc

tech-stack:
  added: []
  patterns:
    - "Widen a structural guard first, commit it red against the unmigrated tree, then migrate; never allowlist"
    - "Explicit-oracle fixtures: saveDemandAnchored takes the business date as an independent value and asserts it against DayWindow.businessDateOf"
    - "One label method (Window.describe) shared by validator and generator so operator-facing lists cannot drift"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
    - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
    - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java

key-decisions:
  - "Window component renamed date -> businessDate; covers() stays the single coverage predicate and is not forked"
  - "describe() puts the business date first and appends ' (Dow)' and ' [calendar d]' only when the calendar date differs, so every 00:00 string is byte-identical"
  - "uncoveredWindows orders by business date, then anchored start minute, then anchored end minute (deterministic for ties)"
  - "PeakShortfallAdvisory.date now carries the business date; ShiftLibrary.tsx and the DTO are untouched (operator decision 3)"

patterns-established:
  - "Verb-free widened guard for files that should hold no legitimate calendar-date Timeslot read"

duration: 74min
completed: 2026-10-07
status: complete

coverage:
  - id: D1
    description: "A Monday-only overnight template on a 06:00 desk covers its own post-midnight hours; validate reports no uncovered window or unsatisfiable weekday and requireShiftModeReady does not throw"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#requireShiftModeReady_mondayOnlyOvernightTemplate_acceptsItsOwnPostMidnightHours"
        status: pass
    human_judgment: false
  - id: D2
    description: "Business-Sunday's post-midnight window is not credited to the calendar-Monday template, and isEffectiveOn is judged on the business date"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_previousBusinessDaysPostMidnightWindow_isNotCreditedToTheCalendarWeekdaysTemplate"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_effectiveFromIsJudgedOnTheBusinessDate"
        status: pass
    human_judgment: false
  - id: D3
    description: "Operator-facing validator and generator strings name the business date and disclose the calendar date only when it differs; 00:00 strings byte-identical; ordering from the day start; adjacency at the anchor"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_uncoveredPostMidnightWindow_namesTheBusinessDayAndDisclosesTheCalendarDate"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_windowStartingExactlyAtTheDayStart_belongsToTheNewBusinessDay_andIsNotDecorated"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_midnightDesk_uncoveredStringsStayByteIdentical"
        status: pass
    human_judgment: false
  - id: D4
    description: "Peak shortfall is found and reported against its business date and weekday, with the 00:00 message asserted byte-identical"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_peakShortfallOnAPostMidnightHour_bucketsOnTheBusinessWeekday"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_hourNeedingMoreAgentsThanExist_isReportedWithTheShortfall"
        status: pass
    human_judgment: false
  - id: D5
    description: "BusinessDateJoinGuardTest widened to the validator and the three repair/start-mix services, committed red on 14 sites before any production edit; now red only on the 11 sites plan 24-02 owns"
    requirement: "BDAY-05"
    verification:
      - kind: unit
        ref: "BusinessDateJoinGuardTest#theWidenedMatcherCatchesVerbFreeTimeslotDateReadsTheVerbScanMisses"
        status: pass
    human_judgment: false
  - id: D6
    description: "The PeakShortfallPanel now shows the business date, matching the schedule grid headings"
    requirement: "OVNT-07"
    verification: []
    human_judgment: true
    rationale: "A UI-visible meaning change of an existing field (operator decision 3); no frontend test asserts it and the panel was deliberately not edited"
---

# Phase 24 Plan 01: Business-date keys in shift-library validation Summary

**ShiftLibraryValidationService now keys demand windows, weekday bucketing and peak shortfalls on Timeslot.getBusinessDate() with one shared Window.describe label, and a widened verb-free BusinessDateJoinGuardTest was committed red on 14 sites before any fix.**

## Performance

- **Duration:** 74 min (includes a halt on the protected-branch guard, resolved by the operator setting `git.allow_default_branch_commits`)
- **Started:** 2026-10-07T16:33:04Z
- **Completed:** 2026-10-07T17:47:31Z
- **Tasks:** 3
- **Files modified:** 4 (`git diff --stat`: 333 insertions, 36 deletions)

## Accomplishments

- A Monday-only 21:00-06:00 template on a 06:00 desk now passes `requireShiftModeReady` for its own calendar-Tuesday hours (audit flow F-2 unblocked), and is no longer credited with business Sunday's 01:00 window, nor judged effective by the calendar date.
- `findUnsatisfiableWeekdays` and `findPeakShortfalls` bucket on the business weekday. A 3-FTE post-midnight peak against one Monday agent now yields exactly one advisory dated 2026-10-05 ("short by 2"); before, it produced none.
- `Window.describe(DayWindow)` is the single label for the validator's `uncoveredWindows`, the `requireShiftModeReady` coverage detail, the peak-shortfall message prefix and the generator's coverage detail. A post-midnight window renders `2026-10-04 (Sun) 01:00-02:00 [calendar 2026-10-05]`; every same-day window and every 00:00 window is byte-identical to before.
- `uncoveredWindows` orders by business date, then anchored start minute, then anchored end minute.
- The widened guard scans an explicit four-file list with no verb requirement and no allowlist entry. It is red on exactly the 11 sites plan 24-02 owns.

## Guard RED evidence (Step 2, before any production edit)

`./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest" --rerun-tasks` exited non-zero. Only the headline test failed (1 of 7). STALE set empty. NEW set was exactly 14:

```
ShiftLibraryValidationService   .map(sr -> new Window(sr.getTimeslot().getDate(),
ShiftLibraryValidationService   .map(sr -> sr.getTimeslot().getDate())
ShiftLibraryValidationService   LocalDate date = sr.getTimeslot().getDate();
ScheduleEnvelopeRepairService   .comparing((AgentAssignment a) -> a.getTimeslot().getDate())
ScheduleEnvelopeRepairService   LocalDate date = violation.getTimeslot().getDate();
ScheduleEnvelopeRepairService   AgentDay key = new AgentDay(a.getAgent().getId(), a.getTimeslot().getDate());
ScheduleEnvelopeRepairService   LocalDate date = move.from().getTimeslot().getDate();
ScheduleEnvelopeRepairService   violation.getAgent().getId(), violation.getTimeslot().getDate(),
ScheduleEnvelopeRepairService   LocalDate date = from.getTimeslot().getDate();
ScheduleEnvelopeRepairService   freeByDate.computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>()).add(a);
ScheduleConsistencyRepairService .computeIfAbsent(a.getTimeslot().getDate(), k -> new HashMap<>())
ShiftStartMixTargetService      reqByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
ShiftStartMixTargetService      seatsByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
ShiftStartMixTargetService      slotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);
```

That is 3 + 7 + 1 + 3 = 14. After this plan the same run reports 7 + 3 + 1 = 11 with no validator entry.

## RED evidence per test group

- Task 1 (`ecc95e8`): the three tests failed for the planned reasons: uncovered windows non-empty on a 06:00 desk (false refusal), and `Expected size: 1 but was: 0` twice (false coverage).
- Task 2 (`e6ea7cb`): exactly the two plan-predicted tests failed (`..._namesTheBusinessDayAndDisclosesTheCalendarDate`, `..._areOrderedFromTheDayStart`). The adjacency test and the 00:00 control were green before and after, as the plan specified.
- Task 3 (`ca0752f`): `validate_peakShortfallOnAPostMidnightHour_bucketsOnTheBusinessWeekday` failed; the added 00:00 control assertion passed before and after.

## Task Commits

1. **Task 1: widened guard + business-date window key** (tracer)
   - `d763d82` test: widen business-date join guard, red on 14 unmigrated sites
   - `ecc95e8` test: red, N-1 false refusal and false coverage
   - `623258f` fix: key shift-library validation windows on the business date
2. **Task 2: shared business-date label**
   - `e6ea7cb` test: red, label with calendar disclosure and day-start order
   - `3f3d065` feat: one shared business-date label with calendar disclosure
3. **Task 3: peak shortfall on the business day**
   - `ca0752f` test: red, post-midnight peak shortfall bucketed on business weekday
   - `01401e8` fix: bucket peak shortfall on the business day with calendar disclosure

The guard commit `d763d82` precedes the validator fix `623258f`, as acceptance requires.

## Files Created/Modified

- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` - `Window(businessDate, ...)` with `describe`, business-date reads at all four sites, new ordering, `peakShortfallMessage(Window, DayWindow, ...)`
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` - accessor rename ripple and coverage detail via `window.describe(dayWindow)`; `distinctSortedWindows` order untouched
- `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` - `WIDENED_TARGET_FILES`, `isWidenedTimeslotDateRead`, predicate-parameterised `scanFiles`, union headline scan, `allGuardedFilesExist`, widened matcher proof
- `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` - `ANCHOR_0600`/`BIZ_*` constants, `saveDemandAnchored`, eight new tests, one added 00:00 control assertion

## Decisions Made

- `covers` was renamed-through, not forked (D-01). The generator calls it unchanged.
- **Operator decision 3 consequence:** `PeakShortfallAdvisory.date` now carries the business date, so `ShiftLibrary.tsx`'s `PeakShortfallPanel` shows the business date, matching the schedule grid headings. No second date field was added and neither the panel nor the DTO changed (verified by `git diff` against the plan-add commit).
- No allowlist entry was added to `bday-join-guard.md`; `midnight-time-arithmetic.md` is also unmodified (both verified by `git diff` against the plan-add commit).

## Deviations from Plan

### Auto-fixed Issues

None to production code. Two process notes:

**1. Halt on the protected-branch guard, resolved externally**
- **Found during:** first commit (Task 1 Step 2)
- **Issue:** the pre-commit HEAD safety assertion treats `claude/create-system-specification-451ge` as the default branch (origin/HEAD points to it), and `git.allow_default_branch_commits` was unset.
- **Resolution:** halted without committing. The operator set `git.allow_default_branch_commits: true` in `.planning/config.json`, and the orchestrator committed that file, not this plan. Not a code deviation.

**2. Commit type on the Task 1 and Task 3 GREEN commits is `fix(...)`, not `feat(...)`**
- The plan's own commit messages specify `fix(24-01): ...` for these two bug fixes, and the plan frontmatter is `type: execute`, not `type: tdd`, so the `## TDD Gate Compliance` RED-then-`feat` sequence check does not apply. Every GREEN commit is preceded by its own `test(24-01)` RED commit. Task 2 is a `feat`.

**Total deviations:** 0 auto-fixed. **Impact on plan:** none.

## Issues Encountered

- `BusinessDateJoinGuardTest` is red at the end of this plan by design (11 sites, owned by 24-02). Per the plan, no full-suite gate runs until 24-03. A filtered `--tests` run deletes other classes' JUnit XML, so no suite-wide aggregate was read.
- `.planning/STATE.md` carried an uncommitted pre-existing "Phase 24 execution started" edit from the orchestrator before this plan began; it is committed together with this plan's position update.

## Known Stubs

None.

## Threat Flags

None. No new endpoint, parameter, query or persisted field; the added `[calendar ...]` suffix is a date from the caller's own tenant's demand row (T-24-01, accepted). T-24-02 is mitigated by tests (b) and (b2).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 24-02 can migrate the 11 remaining sites (7 `ScheduleEnvelopeRepairService`, 1 `ScheduleConsistencyRepairService`, 3 `ShiftStartMixTargetService`) and the guard turns green with no allowlist change.
- **Do not push.** `.github/workflows/deploy.yml` deploys to the live `dev` system on every push to this branch, and the guard is red until 24-02 completes.

## Self-Check: PASSED

Files verified present: the four modified source and test files and this SUMMARY.
Commits verified as ancestors of HEAD: `d763d82`, `ecc95e8`, `623258f`, `e6ea7cb`, `3f3d065`, `ca0752f`, `01401e8`.
Final test runs: `ShiftLibraryValidationServiceTest` 67/0 failures, `ShiftLibraryGenerationServiceTest` 31/0, `MidnightWindowSeamTest` 17/0, `MidnightTimeArithmeticGuardTest` 12/0. `BusinessDateJoinGuardTest` 6 of 7 pass; the headline is red on the 11 sites 24-02 owns, as intended.

---
*Phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida*
*Completed: 2026-10-07*
