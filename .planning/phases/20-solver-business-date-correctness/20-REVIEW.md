---
phase: 20-solver-business-date-correctness
reviewed: 2026-10-02T00:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - src/main/java/com/wfm/service/BusinessDayPeriodLoader.java
  - src/test/java/com/wfm/service/BusinessDayPeriodLoaderTest.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/resources/bday-join-guard.md
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-10-02T00:00:00Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

This is a gap-closure round fixing CR-01 and WR-01 from the previous `20-REVIEW.md` round (commit
`a302a46`): four call sites (`SolverService.startSolve`'s two problem-fact loads,
`ScheduleService.acceptSchedule`'s two snapshot loads) fed BUSINESS-date period bounds into
repository finders that filter the CALENDAR `date` column, silently truncating a non-midnight-
anchored desk's last business day. The fix is a new shared loader, `BusinessDayPeriodLoader`,
applying a widen-the-calendar-upper-bound-by-one-day-then-filter-on-derived-business-date strategy
mirroring `TimeslotGeneratorService`'s existing BDAY-03 read-back.

Verified independently against `DayWindow.businessDateOf`'s actual implementation (`businessDate =
calendarDate` if `timeOfDay >= dayStart`, else `calendarDate.minusDays(1)`) rather than assumed: the
derivation can only ever shift a row's business date backward by at most one day relative to its
calendar date, for every possible anchor value (confirmed via `calendarDateAtDayStartOffset`'s own
`daysForward = (startMinute(dayStart) + minutesFromDayStart) / 1440` arithmetic, which is bounded to
`{0, 1}` for any `dayStart` and any `minutesFromDayStart` in `[0, 1440)`). This means:

- Widening the calendar **upper** bound by exactly one day is both necessary and sufficient to catch
  every row that could belong to `periodEnd`'s business day — there is no anchor value for which more
  than one extra calendar day could ever be needed.
- The calendar **lower** bound never needs widening: a row contributing to `periodStart`'s business
  date can only live on calendar date `periodStart` or `periodStart + 1`, both already inside the
  un-widened range. The two-sided filter's `isBefore(periodStart)` check still does real work, though
  — it correctly excludes rows physically stored on calendar date `periodStart` whose time-of-day is
  before the anchor and therefore derive to `periodStart - 1` (exercised by `BusinessDayPeriodLoaderTest`
  Test 3's `headRow`).

The ordering contract (`loadLiveTimeslots` sorts explicitly; `loadLiveStaffingRequirements`
deliberately does not, per P-02) was checked against every consumer. `SolverService`'s `lastOnDay`
reduce (IN-01, carried over from the prior round) is safe: `timeslots` flows from `loadLiveTimeslots`
straight into `runPreSolveValidation` and `schedule.setTimeslots(new ArrayList<>(timeslots))` with no
intervening re-sort or re-order, so the "last stream element matching this business date is the
chronologically last timeslot of the day" assumption the reduce depends on continues to hold. Every
other `staffingRequirements` consumer in `SolverService` builds maps/sums (`groupingBy`, `.sum()`),
none of which are order-sensitive, consistent with the no-sort contract.

The JOIN-FETCH safety claim was independently verified against the repository source: the 4-argument
`findLiveByDeskAndDateRange(long, UUID, LocalDate, LocalDate)` overload the loader actually calls
(distinct from the 5-argument, `Pageable`-taking overload of the same name) does carry `JOIN FETCH
sr.timeslot t JOIN FETCH sr.specialization s`, and `StaffingRequirement.timeslot` is in fact
`@ManyToOne(fetch = FetchType.LAZY)` — so this fetch join is load-bearing, not decorative, and the
loader correctly resolves to the fetch-joined overload by argument count.

A repo-wide grep confirms all four sites migrated and no other production call site now feeds a
business-date bound into either calendar-date finder; the three remaining direct uses
(`TimeslotGeneratorService`'s own generation/read-back, `StaffingRequirementService.listRequirements`)
are legitimately calendar-date-scoped operator-facing paths, not business-date-fed, and are already
recorded as deliberate in `bday-join-guard.md`.

One coverage gap remains, filed below as a Warning: no test exercising the real `ScheduleService.
acceptSchedule` path at a 21:00 anchor would fail if `BusinessDayPeriodLoader`'s derived-business-date
filter were deleted entirely (i.e. if the loader just returned the widened fetch unfiltered).

## Warnings

### WR-01: No end-to-end (real accept-path) test proves the derived-business-date filter actually excludes an out-of-range row at a non-midnight anchor

**File:** `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java:305-424`

**Issue:** Tests A, B and C (`acceptSchedule_21_00Anchor_snapshotsAllTwentyFourTimeslotsOfTheLastBusinessDay`,
`...snapshotsAllTwentyFourStaffingRequirementsOfTheLastBusinessDay`, and
`...derivesBusinessDateRatherThanTrustingTheStoredColumn`) each persist exactly the 24 rows whose
calendar dates fall in `[MONDAY, MONDAY.plusDays(1)]` — i.e., exactly the widened fetch's own calendar
range, with no row outside it. If `BusinessDayPeriodLoader.loadLiveTimeslots`/
`loadLiveStaffingRequirements` were changed to skip the business-date filter entirely and return the
widened fetch verbatim, all three tests would still pass unchanged (same 24 rows either way), because
none of their fixtures include a row inside the widened calendar range but outside the business-date
range.

The only test in this file with an excluded decoy row is Test D
(`acceptSchedule_midnightAnchor_snapshotIsUnchangedAndDecoyDayIsExcluded`), but it runs at a
`LocalTime.MIDNIGHT` anchor, where derived business date and calendar date are always identical — so
it cannot distinguish "the filter correctly derives and excludes a business-date-out-of-range row"
from "the filter (or an un-widened calendar fetch) excludes a calendar-date-out-of-range row"; both
produce the same result at midnight.

The scenario IS proven correct at the unit level — `BusinessDayPeriodLoaderTest`'s Test 3
(`loadLiveTimeslots_filtersBothBelowAndAboveTheRequestedRange`) builds exactly this case (a row inside
the widened calendar range but outside the derived business-date range, at a 21:00 anchor) against a
mocked repository and asserts it is excluded. But that test exercises the loader directly with a
hand-stubbed repository return value, not the real JPA/H2 round-trip through
`ScheduleService.acceptSchedule`. A defect in how the loader is wired to the real repository call (as
opposed to the loader's own filtering logic, which Test 3 already covers) would go undetected by this
file's test suite.

**Fix:** Add a fifth test to `ScheduleServiceShiftSnapshotTest`, anchored at 21:00, that persists one
extra live timeslot (and matching staffing requirement) whose calendar date falls inside
`[periodStart, periodEnd.plusDays(1)]` but whose derived business date falls outside
`[periodStart, periodEnd]` — e.g. a row on calendar `MONDAY` at `20:00` (before the 21:00 anchor,
deriving to `MONDAY.minusDays(1)`), mirroring `BusinessDayPeriodLoaderTest`'s `headRow` fixture — and
assert the accepted snapshot excludes it. This closes the gap between "the loader's filter logic is
correct" (already proven) and "the loader is correctly wired into the real accept path" (not yet
proven end-to-end).

---

_Reviewed: 2026-10-02T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
