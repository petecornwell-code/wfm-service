---
phase: 20-solver-business-date-correctness
plan: 11
subsystem: solver
tags: [timefold, business-date, pre-solve-validation, audit, tdd, documentation]

# Dependency graph
requires:
  - phase: 20-solver-business-date-correctness (plan 20-10)
    provides: "expandMinimumStaffingSeats' two migrated reads and MinimumStaffingSeatsBusinessDateTest's 21:00-anchored fixture idiom, which this plan's proof mirrors"
provides:
  - "runPreSolveValidation's two remaining Timeslot-receiver calendar-date reads (check 2's period-coverage set, check 3's same-day filter feeding the end-time comparison) resolve the business date, not the calendar date"
  - "runPreSolveValidation converted from private instance method to package-private static, taking ShiftLibraryValidationService as its first parameter -- directly unit-testable without a Spring context, mirroring appendBandCapacityErrors' precedent"
  - "The complete SolverService Timeslot-date-read enumeration (all four sites, two per migrating plan) recorded in bday-join-guard.md's 'Known scope boundaries' section, with the per-site verb measurement proving BusinessDateJoinGuardTest structurally cannot see any of them"
  - "Advisory 1 of 20-VERIFICATION.md is discharged: 'is CR-02 the only remaining instance, or one of several?' -- answered 'no, there were two more', both now fixed and audited"
affects: []

# Actuals (#2632) -- chars/4 over the realized diff (git diff, not whole-file size)
actuals:
  tokens: 5599
  tasks: 2
  commits: 4
  plan_head_before: bf9ce74571a08adbaa59cd4c241eb8c2c89532f0

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A method with no other instance state is converted private-instance -> package-private-static with its collaborator as a leading parameter, as its own behaviour-free commit, before any test or production-logic change -- the same shape appendBandCapacityErrors already established in this file, now applied a second time to runPreSolveValidation"
    - "A guard's own documentation file ('Known scope boundaries') is the durable home for an audit's enumeration and per-site verb measurement, rather than a planning document, because the guard parses that file at test time and a future reader meets it beside the contract it qualifies"

key-files:
  created:
    - src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/resources/bday-join-guard.md

key-decisions:
  - "Made isAligned (a pure helper called only from runPreSolveValidation) static alongside the visibility-change commit, as the one compile-time consequence of converting its caller to static -- kept inside the same 'no behaviour change' commit since it is a visibility change with no logic edit, not a second production-logic commit"
  - "Built the RED proof's fixtures with every collaborator list empty except timeslots (allAgents, staffingRequirements, eligibleAgents, daysOff, exceptions, agentDayHours, preferences all List.of()), filtering raised ErrorDetails by field name rather than asserting no-throw -- several of the thirteen checks fire on an empty-collection fixture regardless (e.g. check 6, no staffing requirements), which is expected and does not interfere with the two field-scoped assertions this proof needs"
  - "Placed the new 'Known scope boundaries' entry for SolverService first in that section's bulleted list, ahead of the pre-existing ScheduleOutputService/StaffingRequirementService/ShiftLibraryGenerationService entries -- it is the most recently audited and the largest (four sites across two migrating plans), and plan 20-10's advisory_dispositions already named it as the entry this plan would add"

patterns-established:
  - "A guard-scope 'Known scope boundaries' entry states, for each audited site: the region and what it does, which verb token the guard's predicate would need and what the line actually holds, how correctness was established instead (named test classes), and what would have to change about the guard's own design to close the blind spot -- the four-part shape this plan's entry follows from the file's pre-existing precedent entries"

requirements-completed: [SOLV-01, SOLV-02]

coverage:
  - id: D1
    description: "runPreSolveValidation's check 2 (period-coverage set) resolves a timeslot's business date, not its calendar date"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java#periodCoverage_resolvesBusinessDate_not21_00AnchoredCalendarDate"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java#periodCoverage_falsificationControl_equalCalendarAndBusinessDate_stillRaises"
        status: pass
    human_judgment: false
  - id: D2
    description: "runPreSolveValidation's check 3 (same-day filter feeding the end-time comparison) resolves a timeslot's business date, not its calendar date"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java#endTime_resolvesBusinessDate_not21_00AnchoredCalendarDate"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java#endTime_falsificationControl_equalCalendarAndBusinessDate_stillRaises"
        status: pass
    human_judgment: false
  - id: D3
    description: "The fix is a no-op on a midnight-anchored desk, where calendar date and business date always coincide"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java#midnightAnchor_calendarAndBusinessDateCoincide_fixIsANoOp"
        status: pass
    human_judgment: false
  - id: D4
    description: "runPreSolveValidation is directly unit-testable (package-private static, ShiftLibraryValidationService as a parameter) with no behaviour change from the conversion itself"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "./gradlew test (full suite, 1200 tests, 0 failures, 0 errors -- re-run immediately after the visibility-only commit, before any test or logic change)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every Timeslot-receiver calendar-date read in SolverService is enumerated (all four sites, two per migrating plan) and recorded in bday-join-guard.md with the per-site verb measurement proving the guard cannot see any of them"
    requirement: "SOLV-02"
    verification:
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest (6/6, unaffected by the documentation-only change)"
        status: pass
    human_judgment: false

duration: ~95min
completed: 2026-10-02
status: complete
---

# Phase 20 Plan 11: Pre-Solve Validation Resolves the Business Day, SolverService Audit Recorded Summary

**`runPreSolveValidation`'s two remaining calendar-date reads (period-coverage set, same-day end-time filter) now resolve the business date, converted to package-private static for direct unit testing like its sibling `appendBandCapacityErrors`, with the complete four-site `SolverService` audit recorded in `bday-join-guard.md` discharging advisory 1 of `20-VERIFICATION.md`.**

## Performance

- **Duration:** ~95 min (dominated by four full-suite `./gradlew test` runs at ~13-16 min each, per this project's "never read a suite aggregate after a filtered run" discipline)
- **Started:** 2026-10-01T23:15:00Z (approx)
- **Completed:** 2026-10-02T04:09:59Z
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `SolverService.runPreSolveValidation`'s check 2 (the period-coverage `Set<LocalDate>`) now builds from `Timeslot::getBusinessDate` instead of `Timeslot::getDate`, matching the business-date period bounds (`periodStartDate`/`periodEndDate`, 18-CONTEXT.md D-22) it is compared against. Closes the first of the two remaining instances advisory 1 of `20-VERIFICATION.md` asked about.
- Check 3's same-day filter (selecting "the last timeslot on the same day as the first" to compare against the schedule's end time) now compares `getBusinessDate()` instead of `getDate()`, so a business day ending after midnight is measured against its actual last timeslot rather than the last one still sharing the first row's calendar date. Closes the second remaining instance.
- `runPreSolveValidation` converted from a `private` instance method to package-private `static`, taking `ShiftLibraryValidationService` as its first parameter — the identical conversion `appendBandCapacityErrors` already underwent in this file, for the identical reason: direct unit-testability with a mocked collaborator and no Spring context. Its own commit changes no behaviour (full suite confirmed green immediately before and after, with no test file touched).
- `PreSolveValidationBusinessDateTest` (new, 5 tests) proves both fixes at a 21:00 anchor: a period-coverage case (a business Monday whose timeslots sit entirely on calendar Tuesday is not refused), an end-time case (a business day ending at 02:00 on the following calendar date is not refused), a falsification control for each (the same fixture with business date deliberately set equal to calendar date must still raise, proving the assertions read the production field), and a midnight-anchored control (the fix is a provable no-op where the two dates coincide).
- `src/test/resources/bday-join-guard.md` gains a "Known scope boundaries" entry naming `SolverService`: the decision that it is deliberately not one of `BusinessDateJoinGuardTest`'s four scanned files, the enumeration of all four `Timeslot`-receiver calendar-date reads the audit found (two in `runPreSolveValidation`, migrated here; two in `expandMinimumStaffingSeats`, migrated by plan 20-10), the per-site verb measurement proving none of the four satisfies the guard's four-verb predicate, and how correctness was actually established at each site (direct reading plus `MinimumStaffingSeatsBusinessDateTest`/`PreSolveValidationBusinessDateTest`, never by the guard turning green). The `### Allowlist` fenced block and `BusinessDateJoinGuardTest.java` are both untouched; the guard still passes 6/6.
- Advisory 1 of `20-VERIFICATION.md` — "is CR-02 the only remaining instance, or one of several?" — is discharged with a direct answer recorded in the codebase: **it was not the only instance; two more existed, and all four are now enumerated, fixed, and documented.**

## Task Commits

Each task followed the plan's TDD discipline:

1. **Task 1 (tracer, tdd): pre-solve validation stops refusing a legal desk at a 21:00 anchor**
   - `4531844` — `refactor(20-11): make runPreSolveValidation directly unit-testable (SOLV-01)` (visibility change only — no behaviour, no test diff; full suite confirmed green before and after: 1200 tests, 0 failures, 0 errors)
   - `c07abdd` — `test(20-11): add failing proof for pre-solve validation business-date reads (SOLV-01)` (RED)
   - `92d463c` — `feat(20-11): resolve business date in pre-solve period-coverage and end-time checks (SOLV-01)` (GREEN)
2. **Task 2: record the SolverService audit and the guard-scope decision**
   - `0d6bee9` — `docs(20-11): record the SolverService audit in the join-guard's scope notes (SOLV-02)` (documentation only — `git show --name-only` confirms exactly `src/test/resources/bday-join-guard.md`)

**Plan metadata:** committed alongside this SUMMARY.

## RED Evidence (observed, not claimed)

Before the production change (commit `92d463c`), `PreSolveValidationBusinessDateTest` reported **2 of 5 tests failing** (verified via `build/test-results/test/TEST-com.wfm.service.PreSolveValidationBusinessDateTest.xml` after the targeted run):

- `periodCoverage_resolvesBusinessDate_not21_00AnchoredCalendarDate` — expected no `timeslots`-field `ErrorDetail`; **observed** `ErrorDetail[field=timeslots, message=No timeslots found for date 2026-01-05, value=null]`. The period-coverage set, built from the six timeslots' calendar dates (all 2026-01-06), never contained the schedule's one business-date period day (2026-01-05), even though every timeslot carries business date 2026-01-05.
- `endTime_resolvesBusinessDate_not21_00AnchoredCalendarDate` — expected no `endTime`-field `ErrorDetail`; **observed** `ErrorDetail[field=endTime, message=Schedule endTime 02:00 does not match timeslot end 00:00, value=02:00]`. The same-day filter, run against the first timeslot's calendar date (2026-01-05), stopped at the last timeslot still carrying that calendar date (ending 00:00) instead of the business day's actual last timeslot (ending 02:00, one calendar day later).

The two falsification controls and the midnight-anchored control all passed before the fix too, as designed — each is built so the error legitimately fires (or legitimately does not) regardless of which accessor the code reads, which is what makes them controls rather than a fourth instance of the defect.

After the production change, all 5 tests pass; the full suite is 1205 tests (1200 + 5 new), 0 failures, 0 errors, 4 skipped.

## Files Created/Modified

- `src/main/java/com/wfm/service/SolverService.java` — `runPreSolveValidation` converted to package-private `static` with `ShiftLibraryValidationService` as a leading parameter (and `isAligned`, its one pure helper, demoted to `static` as the compile-time consequence); check 2's period-coverage set and check 3's same-day filter now read `getBusinessDate()`; a short comment above each explains why, mirroring `requireShiftEnvelopeSeatSupply`'s comment discipline. No other check (1, 4-13) touched; the accumulate-then-throw-once shape is unchanged.
- `src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java` — new, 5 tests, package `com.wfm.service`, calls `SolverService.runPreSolveValidation` directly with a Mockito-mocked `ShiftLibraryValidationService`, no Spring context, no database.
- `src/test/resources/bday-join-guard.md` — new "Known scope boundaries" entry for `SolverService`, placed first in that section, recording the complete four-site audit and the guard-scope decision. The `### Allowlist` fenced block is byte-identical to before this plan.

## Decisions Made

- Kept `isAligned`'s static conversion inside the visibility-change commit rather than splitting it into a third sub-commit: it is a pure function whose only caller (`runPreSolveValidation`) just became static, so making it static too is a mechanical consequence of the same refactor with zero logic change, not a second behavioural edit.
- Built the RED proof's fixtures with minimal, mostly-empty collaborator lists (per the plan's own instruction) rather than fully-populated fixtures satisfying all thirteen checks — several checks (6, 7, 12 in particular) fire on an empty-collection fixture by construction, which is expected and does not interfere with the two field-scoped assertions (`timeslots`, `endTime`) this proof targets.
- Placed the new `bday-join-guard.md` entry at the top of "Known scope boundaries" (before the pre-existing `ScheduleOutputService` entry) since it is the most recently completed audit and the one plan 20-10's own `advisory_dispositions` section named as "what this run does instead" for the following plan.

## Deviations from Plan

None — plan executed exactly as written. The plan's own prohibitions (no `SolverService` added to `BusinessDateJoinGuardTest.TARGET_FILES`, no widening of the guard's verb set, no undocumented survivor) were honored throughout; both `Timeslot`-receiver date reads this plan found were migrated (no site was found to be deliberately calendar-correct, so the "leave it and document why" branch of Task 1's action was not needed).

## Issues Encountered

None. The only unplanned compile-time consequence (making `isAligned` static) was a one-line, zero-behavior-change fix resolved immediately as part of the same visibility-change commit it was caused by — not logged as a separate deviation since it carries no Rule 1-4 classification of its own (a direct, mechanical compile requirement of the planned change, not a bug, missing feature, blocker, or architectural choice).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Advisory 1 of `20-VERIFICATION.md` is fully discharged: the enumeration of every `Timeslot`-receiver calendar-date read in `SolverService` is complete (4 sites, all 4 migrated across plans 20-10 and 20-11), and the reasoned decision to keep `SolverService` outside `BusinessDateJoinGuardTest`'s four-file scope is recorded in `bday-join-guard.md` with the measurement that justifies it.
- `./gradlew test` (full suite, independently re-run after all four commits): **1205 tests, 0 failures, 0 errors, 4 skipped** — green.
- `grep -cF 'first.getBusinessDate()' src/main/java/com/wfm/service/SolverService.java` prints 1; with `//`/`*` lines stripped, `Timeslot::getDate` appears 0 times in the file.
- `BusinessDateJoinGuardTest` passes 6/6, unaffected by the documentation-only change; no commit in this plan names `BusinessDateJoinGuardTest.java`.
- This was the final plan of Phase 20 (wave 3, `depends_on: ["20-10"]`); both gaps identified in `20-VERIFICATION.md`'s gap-closure set (SC6/CR-01, already closed by an earlier wave, and the phase goal's "every solver join" promise / CR-02 and its siblings) are now closed, and the phase's own advisory items are dispositioned.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-02*
