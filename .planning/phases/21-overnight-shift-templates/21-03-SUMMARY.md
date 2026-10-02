---
phase: 21-overnight-shift-templates
plan: 03
subsystem: api
tags: [java, spring, timefold-adjacent, excel-export, day-window, violation-reporting]

# Dependency graph
requires:
  - phase: 19-daywindow-re-anchoring
    provides: "DayWindow anchored interval arithmetic and Timeslot.getBusinessDate()/getDate() split, which this plan's structured fields read directly"
  - phase: 20-solver-business-date-correctness
    provides: "ScheduleOutputService's existing business-date-keyed grouping (SOLV-07/D-10) that this plan's new fields and the export's re-keyed map now agree with"
provides:
  - "ViolationDetail (ScheduleDetailResponse) carries four new typed fields -- businessDate, calendarDate, startTime, endTime -- populated at both backend construction sites from the Timeslot already in scope"
  - "ScheduleExportService.unfilledSeatsByDateAndSlot reads the structured businessDate/startTime fields directly, with indexOf(' ')/LocalTime.parse removed entirely, and keys its result map on the BUSINESS date instead of the calendar date the label displays"
  - "The before-picture of timeslotLabel's shape, pinned for 21-10: `{calendarDate} {startTime}-{endTime}`, e.g. \"2026-09-21 21:00-22:00\" -- Java's default LocalDate/LocalTime toString(), unchanged by this plan"
affects: [21-08, 21-10]

# Actuals (#2632)
actuals:
  tokens: 5233
  tasks: 2
  commits: 4
  plan_head_before: 4d345255f08c4bbe1db51e3e462dbe234796e4fe
  plan_head_after: 16ee1d560273efeb98cfa8fd109129f4685b0bd9

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A response-record widening that is compiler-forced across every construction and consumption site (ViolationDetail's four new components) is exercised as RED by committing the test-only changes first and confirming the build fails naming exactly the missing constructor arity/accessors, then restoring the source change as GREEN -- same convention 21-01/21-02 established for API-shape TDD tasks in this phase"
    - "When a coverage/attribution predicate depends only on time-of-day (ShiftBandPair.covers compares LocalTime values, never LocalDate), a test can vary businessDate/calendarDate independently of the anchor-sensitive coverage check by relocating one seat's Timeslot to a hand-built instance with the two dates set apart -- the relocateSeat trap 21-03's own red-proof test already documents, reused rather than re-invented for the new behavior tests"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleExportService.java
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/java/com/wfm/service/ScheduleAllocationExportTest.java

key-decisions:
  - "The three new ScheduleOutputServiceShiftReportingTest behavior tests (Task 1) exercise construction site 2 (buildAcceptedConstraintViolations) via the file's existing acceptedScheduleWithEnvelope + relocateSeat fixture pattern -- the practical, already-established fixture style in this file. Construction site 1 (the live solutionManager.explain() path) is covered structurally: the widened constructor is compiler-forced there too, and the pre-existing buildConstraintViolations_liveUnaccepted_nullWeightsGuardStaysScopedToLivePath test still passes unmodified against it."
  - "Site 1 (the live-path loop) now captures the indicted Timeslot in a local variable (`Timeslot ts = aa.getTimeslot();`) per the plan's explicit action item, which incidentally makes its label-concatenation expression textually identical to site 2's -- not required by the plan's literal-text acceptance check (which only pins site 2), but a natural consequence of following the stated refactor."
  - "unfilledSeatsByDateAndSlot's outer map key changed from the label's parsed calendar-date string to businessDate.toString() -- the actual fix, since writeAgentAllocation's lookup key (AgentScheduleEntry.date()) is always the business date (confirmed by reading buildAgentSchedule's own grouping, which keys on getTimeslot().getBusinessDate())."

requirements-completed: [OVNT-02, OVNT-07]

coverage:
  - id: D1
    description: "ViolationDetail is widened with businessDate, calendarDate, startTime and endTime, populated at both construction sites (the live-path explain() loop and the accepted-path buildAcceptedConstraintViolations) from the Timeslot already in scope"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_21_00AnchoredDesk_violationCarriesDistinctBusinessAndCalendarDates"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_00_00AnchoredDesk_businessAndCalendarDatesAreEqual"
        status: pass
      - kind: integration
        ref: "./gradlew compileJava compileTestJava (widened constructor compiler-forced across all four call sites: two in ScheduleOutputService, two hand-built instances in ScheduleAllocationExportTest)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The operator-facing timeslotLabel text is byte-identical to today -- calendar date, one space, start and end times joined by a hyphen -- even though its business date and calendar date now diverge on an anchored desk"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_anchoredDesk_labelTextStaysCalendarDateSpaceStartHyphenEnd"
        status: pass
      - kind: unit
        ref: "grep -cF 'ts.getDate() + \" \" + ts.getStartTime() + \"-\" + ts.getEndTime()' ScheduleOutputService.java (prints 1, the plan's own prohibition gate)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Unfilled-seat attribution reads the violation's structured businessDate/startTime fields directly -- indexOf(' ') and LocalTime.parse removed entirely from unfilledSeatsByDateAndSlot -- and keys its result on the BUSINESS date, closing the mismatch against writeAgentAllocation's business-date lookup"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "ScheduleAllocationExportTest$UnfilledSeatAttribution#postMidnightShortfallLandsOnBusinessDaySheet"
        status: pass
      - kind: unit
        ref: "sed-scoped grep for indexOf(' ') (prints 0) and businessDate() (prints 1) inside the method body"
        status: pass
    human_judgment: false
  - id: D4
    description: "A midnight-anchored desk's export is unchanged from today -- every pre-existing unfilled-seat assertion in ScheduleAllocationExportTest passes unmodified, since business date equals calendar date at that anchor"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "ScheduleAllocationExportTest$Totals#unfilledRow and #noUnfilledRowWhenFullyCovered (both unmodified, both pass)"
        status: pass
    human_judgment: false
  - id: D5
    description: "A violation with null structured businessDate/startTime fields produces no marker and no exception -- the pre-existing report-not-fail contract preserved under the new read path"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "ScheduleAllocationExportTest$UnfilledSeatAttribution#nullStructuredFieldsSkippedWithoutException"
        status: pass
    human_judgment: false

duration: ~40 min
completed: 2026-10-02
status: complete
---

# Phase 21 Plan 03: Structured Violation Date/Time Fields Summary

**ViolationDetail now carries typed businessDate/calendarDate/startTime/endTime fields alongside its unchanged timeslotLabel string, and the Excel export's unfilled-seat attribution reads those typed fields and keys by business date, closing a bug where a post-midnight shortfall on an anchored desk rendered on no sheet at all.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-10-02T18:00:00-04:00 (approx.)
- **Completed:** 2026-10-02T18:30:31-04:00
- **Tasks:** 2 of 2
- **Files modified:** 5

## Accomplishments
- `ScheduleDetailResponse.ViolationDetail` widened from a 5-component to a 9-component record: `businessDate`, `calendarDate`, `startTime` and `endTime` added after `timeslotId` and before the unchanged `timeslotLabel`. Both backend construction sites (`ScheduleOutputService.buildConstraintViolations`'s live-path `explain()` loop and `buildAcceptedConstraintViolations`) populate all four from the `Timeslot` already in scope.
- The label concatenation expression and its explanatory comments at both sites are untouched byte-for-byte in behavior — pinned by a dedicated control test and by the plan's own literal-text verify gate (`grep -cF` for the exact expression).
- `ScheduleExportService.unfilledSeatsByDateAndSlot` no longer parses `timeslotLabel` at all: `indexOf(' ')` and `LocalTime.parse` (with its surrounding try/catch) are gone, replaced by direct reads of `v.businessDate()` and `v.startTime()`.
- The real defect this closes: the method's result map is now keyed on the **business date**, not the calendar date the label displayed. `writeAgentAllocation` looks the map up by `AgentScheduleEntry.date()`, which is always the business date (confirmed in `buildAgentSchedule`'s own grouping) — so a post-midnight shortfall on a `21:00`-anchored desk used to be stored under a key no sheet ever requested, rendering on no sheet at all. A dedicated regression test proves the marker now lands on the business day's sheet.
- A `00:00`-anchored desk's export is provably unchanged: every pre-existing `ScheduleAllocationExportTest` assertion (`unfilledRow`, `noUnfilledRowWhenFullyCovered`, and all `Layout`/`Colours`/`Violations`/`ShiftGrouping` tests) passes unmodified, since business date equals calendar date at that anchor.
- A violation with null structured fields still produces no marker and no exception — the method's pre-existing "losing one shortfall marker beats failing the whole export" contract survives the rewrite.

## Task Commits

Each task followed RED -> GREEN TDD discipline, one commit per gate:

1. **Task 1: Add structured business date, calendar date and times to the violation payload**
   - `defcf05` (test/RED) — `ScheduleAllocationExportTest`'s two hand-built `ViolationDetail` instances widened to the 9-arg constructor; three new behavior tests added to `ScheduleOutputServiceShiftReportingTest`. Confirmed RED: `./gradlew compileTestJava` failed naming the exact missing constructor arity (`required: UUID,String,UUID,String,String / found: ...9 args...`) and the four missing accessor methods — a compile-forced RED, the same convention 21-01/21-02 established for API-shape TDD tasks.
   - `0872414` (feat/GREEN) — `ViolationDetail` widened; both construction sites populate the four fields; site 1 captures the `Timeslot` in a local (`ts`) per the plan's action item. All 20 tests in `ScheduleOutputServiceShiftReportingTest` and 2 in `ScheduleAllocationExportTest` green; both plan-level literal-text verify gates (`grep -c 'LocalDate businessDate'` and `grep -cF` the label expression) pass.
2. **Task 2: Attribute unfilled seats from the structured fields, on the business date**
   - `3aa49bb` (test/RED) — two new tests added to a new `ScheduleAllocationExportTest$UnfilledSeatAttribution` nested class. Confirmed RED: the post-midnight-shortfall test failed on a real assertion (`header.getCell(3)` read `"22:00"`, expected `"02:00"`) — the correct failure mode, since the pre-fix implementation keys unfilled seats under the label's calendar date and no sheet for that date exists in the fixture.
   - `16ee1d5` (feat/GREEN) — `unfilledSeatsByDateAndSlot` rewritten to read structured fields and key on `businessDate`; javadoc rewritten to describe the new read path instead of the retired label format. All tests green, including the new nested class (2/2) and the full unfiltered suite (1242 tests, 0 failures, 0 errors, 10m31s).

**Plan metadata:** this SUMMARY commit (docs: complete plan)

_TDD discipline: each RED commit was confirmed to fail intentionally — Task 1's RED was a compiler-forced failure naming the exact missing API surface (constructor arity, accessor methods); Task 2's RED was a genuine assertion failure on the exact behavior under test, not a vacuous or unrelated failure — before its GREEN commit landed._

## Files Created/Modified
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` — `ViolationDetail` widened with `businessDate`, `calendarDate`, `startTime`, `endTime`; javadoc documents the structured channel these replace and cites OVNT-07/D-14
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — both `ViolationDetail` construction sites populate the four new fields from the `Timeslot` in scope; site 1 (`buildConstraintViolations`'s live-path loop) now captures the timeslot in a local `ts` instead of calling the accessor five times
- `src/main/java/com/wfm/service/ScheduleExportService.java` — `unfilledSeatsByDateAndSlot` rewritten to read `v.businessDate()`/`v.startTime()` directly and key its result map on the business date; `indexOf(' ')`/`LocalTime.parse` removed; javadoc rewritten
- `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` — three new tests (differing business/calendar dates on a `21:00`-anchored desk, equal dates on a `00:00`-anchored desk, and the label-unchanged control), plus a shared `singleRelocatedViolation` fixture helper
- `src/test/java/com/wfm/service/ScheduleAllocationExportTest.java` — the two pre-existing hand-built `ViolationDetail` instances widened to the new constructor; a new `UnfilledSeatAttribution` nested class with the post-midnight-shortfall regression proof and the null-fields robustness proof

## Decisions Made
- The three new `ScheduleOutputServiceShiftReportingTest` tests exercise construction site 2 (`buildAcceptedConstraintViolations`) via the file's existing `acceptedScheduleWithEnvelope` + relocated-`Timeslot` fixture pattern, rather than building a full live-solver fixture (`ScheduleConfig`/`TimeslotDemandConfig`/real `explain()`) for construction site 1. Site 1 is still covered: the widened constructor is compiler-forced there identically, and the pre-existing `buildConstraintViolations_liveUnaccepted_nullWeightsGuardStaysScopedToLivePath` test continues to pass against it unmodified.
- Site 1's refactor (`Timeslot ts = aa.getTimeslot();`) incidentally makes its label-concatenation expression textually identical to site 2's pinned expression — not required by the plan's literal-text check (which only pins site 2's existing text), but a natural, harmless consequence of following the plan's own action item to capture the timeslot in a local.
- `unfilledSeatsByDateAndSlot`'s outer map key changed from the label's parsed calendar-date string to `businessDate.toString()`, confirmed correct by reading `buildAgentSchedule`'s own grouping (`AgentScheduleEntry.date()` is always keyed from `getTimeslot().getBusinessDate()`), so the export's lookup and the violation's attribution now agree by construction.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' RED commits were confirmed to fail for the planned reason before their GREEN commits landed; no out-of-scope files were touched, no new dependencies were added, and `slotStarts`/`incrementMinutes`/slot-column ordering were left untouched as the plan requires (21-07's territory).

## Issues Encountered

None. Both tasks followed their TDD cycle without unexpected GREEN, unrelated test breakage, or scope expansion. The `./gradlew --stop` reset before the first compile, and the full unfiltered `./gradlew test` run (10m31s, run once, after all filtered runs) confirmed the whole suite green: 1242 tests across 198 classes, 0 failures, 0 errors, 4 pre-existing skips unrelated to this plan's files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ViolationDetail`'s four new typed fields are landed and populated at both backend sites, ready for 21-08 to move the frontend parser (`ScheduleResults.tsx`) onto them instead of splitting `timeslotLabel`.
- The label text itself is deliberately unchanged — this SUMMARY records its exact before-picture shape (`{calendarDate} {startTime}-{endTime}`, Java's default `LocalDate`/`LocalTime` `toString()`, e.g. `"2026-09-21 21:00-22:00"`) for 21-10, which rewrites it only after 21-08 has moved the frontend parser.
- `requirements-completed` lists `[OVNT-02, OVNT-07]` per this plan's own frontmatter, but neither is marked complete in REQUIREMENTS.md yet: both IDs are shared with sibling plans in this phase (OVNT-02 also declared by 21-04/21-06/21-09/21-10/21-12; OVNT-07 also declared by 21-07/21-10/21-11), none of which have a SUMMARY yet. `requirements.ready-ids` confirmed 0/2 ready — this is expected shared-ID gating (#2388), not a gap.
- No blockers for any sibling plan. The export's business-date keying fix (Task 2) is a pure bug fix with no API surface change beyond what Task 1 already added, so no downstream plan needs rework because of it.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-02*

## Self-Check: PASSED

All 5 modified files confirmed present on disk; all 4 plan commits (`defcf05`, `0872414`, `3aa49bb`, `16ee1d5`) confirmed present in `git log --oneline --all`. Full unfiltered `./gradlew test` run: 1242 tests, 0 failures, 0 errors, 4 pre-existing skips, across 198 test classes.
