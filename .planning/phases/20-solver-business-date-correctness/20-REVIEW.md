---
phase: 20-solver-business-date-correctness
reviewed: 2026-10-02T04:21:13Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java
  - src/main/java/com/wfm/controller/TimeslotController.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
  - src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
  - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
  - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
  - src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java
  - src/test/resources/bday-join-guard.md
findings:
  critical: 1
  warning: 1
  info: 1
  total: 3
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-10-02T04:21:13Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

This incremental review covers plans 20-09/20-10/20-11, which close three gaps left by
`20-VERIFICATION.md`: the sub-minute-precision refusal on `DeskService.setDayStart`, the
weekday-eligibility/count-lookup fix in `SolverService.expandMinimumStaffingSeats`, and the
period-coverage/end-time fix in `SolverService.runPreSolveValidation`. All three changes are
narrow, well-targeted, and match their own extensive in-code documentation; every new test does
what its javadoc claims, the anchor-vs-midnight control pattern is applied consistently, and the
production code paths they touch (`DeskService.setDayStart`, `TimeslotController.generateTimeslots`,
`SolverService.runPreSolveValidation`, `SolverService.expandMinimumStaffingSeats`) now correctly
read `Timeslot.getBusinessDate()` where the project's own `bday-join-guard.md` says they should.

The diff itself is clean. The problem is what sits immediately upstream of it, still unfixed:
`SolverService.startSolve` loads the `timeslots` and `staffingRequirements` problem facts that
`runPreSolveValidation` and `expandMinimumStaffingSeats` both consume via two repository calls that
filter on **calendar** date using the schedule's **business-date** period bounds. For any desk with
a non-midnight day start — which this phase's own plan 20-08/20-09 work makes a legitimately
configurable, supported state — those two queries silently drop the last business day's
post-midnight-rollover rows before either fixed method ever sees them. This is the identical defect
class the whole phase exists to eliminate, in the same file, one call away from the lines this
review's diff touches, and it is not mentioned anywhere in `bday-join-guard.md`'s otherwise
exhaustive "known scope boundaries" and "measured blind spots" sections — it falls outside even
that guard's widened textual scope because it is a derived repository-method name, not a
`Timeslot`-receiver `.getDate()` call or a join-key verb. Filed below as CR-01.

## Critical Issues

### CR-01: Problem-fact repository fetches in `startSolve` filter on calendar date using business-date period bounds, silently truncating the last business day of any non-midnight-anchored solve

**File:** `src/main/java/com/wfm/service/SolverService.java:180-185`

**Issue:** `startSolve` builds `schedule` from the raw request (`buildSchedule`, :635-660;
`schedule.getPeriodStartDate()`/`getPeriodEndDate()` are set verbatim from
`request.periodStartDate()`/`periodEndDate()` with no adjustment), then loads the solve's two
`Timeslot`-keyed problem facts with these two calls:

```java
List<Timeslot> timeslots = timeslotRepository
        .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
List<StaffingRequirement> staffingRequirements = staffingRequirementRepository
        .findLiveByDeskAndDateRange(tenantId, deskId,
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
```

Both derived-query methods filter on `Timeslot.date` — the **calendar** date column
(`TimeslotRepository.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc`
binds Spring Data's `DateBetween` token to the entity's `date` field;
`StaffingRequirementRepository.findLiveByDeskAndDateRange`'s JPQL is explicitly
`t.date BETWEEN :from AND :to`). But the bounds passed in, `schedule.getPeriodStartDate()` and
`getPeriodEndDate()`, are **business** dates — this is not a guess, it is asserted by this very
phase's own new comment two call sites later, in the method this review's diff touches
(`SolverService.java:1097-1102`): *"schedule.getPeriodStartDate()/getPeriodEndDate() are BUSINESS
dates (18-CONTEXT.md D-22)."* `18-03-PLAN.md` P-12 independently documents the identical defect
shape in `TimeslotGeneratorService.generateTimeslots`'s closing read-back and fixed it there by
widening the calendar query to `periodEnd.plusDays(1)` and filtering/sorting in memory by derived
business date. No equivalent fix exists for these two call sites.

Concretely: for a desk anchored at 21:00 with a one-day schedule (`periodStartDate ==
periodEndDate == businessMonday`), business-Monday's 24 timeslots split into 3 rows carrying
calendar date `businessMonday` (21:00-23:59) and 21 rows carrying calendar date
`businessMonday.plusDays(1)` (00:00-20:59). The `DateBetween(businessMonday, businessMonday)`
filter admits only the first 3; the 21 post-midnight rows are never fetched. The same truncation
hits exactly the last business day of any multi-day period on a non-midnight-anchored desk (every
earlier day's post-midnight tail still falls inside the period's calendar range, so only the final
day is affected) — and it hits `staffingRequirements` identically, via the same `t.date BETWEEN`
pattern.

This silently defeats the very fix this phase just finished making correct:
- `runPreSolveValidation`'s check 2 (period coverage) can still pass falsely, because the
  truncated set still contains *some* row whose `getBusinessDate()` equals the last business day —
  just not the 21 missing hours' worth. The check has no way to see what the query already dropped.
- `runPreSolveValidation`'s check 3 (end-time match) will very likely now raise where the fixture
  tests (which build `timeslots` in memory, bypassing this query entirely) show it should not: the
  real `lastOnDay` for the business day is one of the excluded rows, so the method compares against
  whatever calendar-truncated row happens to survive instead.
- `expandMinimumStaffingSeats` (and the solver itself) simply never sees the missing hours as
  problem facts at all — no timeslot, no staffing requirement, no constraint violation, no warning.
  The schedule silently solves as if the desk's final business day were only 3 hours long.

None of the new tests in this diff exercise this path, because
`MinimumStaffingSeatsBusinessDateTest` and `PreSolveValidationBusinessDateTest` both call
`SolverService.runPreSolveValidation`/`expandMinimumStaffingSeats` directly with a hand-built
`timeslots` list, never through `startSolve` and the real repository query — so the fixes they
prove are real, but they are proven downstream of a problem-fact load that, in production, has
already lost the data.

**Fix:** Add business-date-filtering twins of both repository methods, following the precedent
already set by `StaffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange` (added in plan
20-07 for the identical reason), and point both `startSolve` call sites at them:

```java
// TimeslotRepository
List<Timeslot> findByTenantIdAndDeskIdAndScheduleIdIsNullAndBusinessDateBetweenOrderByBusinessDateAscStartTimeAsc(
        long tenantId, UUID deskId, LocalDate from, LocalDate to);

// StaffingRequirementRepository
@Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
       "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
       "AND t.businessDate BETWEEN :from AND :to")
List<StaffingRequirement> findLiveByDeskAndBusinessDateRange(
        long tenantId, UUID deskId, LocalDate from, LocalDate to);
```

```java
// SolverService.startSolve
List<Timeslot> timeslots = timeslotRepository
        .findByTenantIdAndDeskIdAndScheduleIdIsNullAndBusinessDateBetweenOrderByBusinessDateAscStartTimeAsc(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
List<StaffingRequirement> staffingRequirements = staffingRequirementRepository
        .findLiveByDeskAndBusinessDateRange(tenantId, deskId,
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
```

At a midnight anchor `date == businessDate` for every row, so this is a no-op for every desk that
does not use SOLV-01's widened range — the same invariance property every other fix in this phase
is held to. A regression test exercising `startSolve` itself (not the two already-fixed methods in
isolation) at a 21:00 anchor, asserting the full 24-timeslot set and all staffing requirements for
the schedule's last business day are present in what gets passed to the solver, would close this
gap and would have caught it RED before this fix.

## Warnings

### WR-01: `bday-join-guard.md`'s audited scope does not, and structurally cannot, cover repository derived-query method names

**File:** `src/test/resources/bday-join-guard.md`

**Issue:** The guard (and the plan 20-11 manual audit layered on top of it) is scoped to
`Timeslot`-receiver `.getDate()` calls and the four join-key verbs. CR-01's defect is a Spring Data
derived-query method name (`...DateBetween...`) and a JPQL literal (`t.date BETWEEN`) fed
business-date-typed arguments — a third shape, alongside the `.min()`/`.max()` and
`.distinct()`/`TreeSet` shapes the file already documents as blind spots. The file's own "Known
scope boundaries" section is commendably thorough about blind spots it already knows about, but
this one is absent, which means the next reader has no signal that repository-level range queries
are a candidate for the same defect class.

**Fix:** Add a "Known scope boundaries" entry naming `TimeslotRepository`'s and
`StaffingRequirementRepository`'s plain (non-business-date) `...DateBetween...` finder methods as a
structurally-invisible-to-this-guard shape, once CR-01 is fixed, so a future regression here is at
least documented as a named risk rather than rediscovered.

## Info

### IN-01: `runPreSolveValidation`'s check 3 "last timeslot of the business day" selection is correct only because the caller's sort order happens to coincide with business-day order

**File:** `src/main/java/com/wfm/service/SolverService.java:1126-1128`

**Issue:** `lastOnDay` is selected via `timeslots.stream().filter(t ->
t.getBusinessDate().equals(first.getBusinessDate())).reduce((a, b) -> b).orElse(first)` — this
relies on `timeslots` already being ordered such that the last stream element matching the business
date is also the chronologically last one. That holds today only because the list arrives sorted
`OrderByDateAscStartTimeAsc` (calendar date, then start time) and a business day's calendar-date
tail is always chronologically later than its calendar-date head for any anchor value currently
reachable in this codebase (0 <= anchor <= 24h). It is correct, but silently depends on an ordering
guarantee from a caller two frames away that nothing in this method asserts or documents.

**Fix:** No action required now; if `timeslots` is ever built from a different source (e.g. once
CR-01's business-date-ordered finder lands), re-verify that "sorted by business date, then start
time" still makes the last-matching-element-in-stream-order selection equivalent to "chronologically
last timeslot of the day" — it does, but a one-line comment at :1126 stating the ordering dependency
would save the next reader from re-deriving it.

---

_Reviewed: 2026-10-02T04:21:13Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
