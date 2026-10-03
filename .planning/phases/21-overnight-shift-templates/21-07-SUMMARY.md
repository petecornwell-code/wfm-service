---
phase: 21-overnight-shift-templates
plan: 07
subsystem: api
tags: [java, poi, excel-export, day-window, allocation-sheet, roster-sheet]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates
    provides: "21-03's ViolationDetail.businessDate()/startTime() structured fields and the business-date-keyed unfilledSeatsByDateAndSlot map this plan's shortfall-column test reads through unchanged"
provides:
  - "ScheduleExportService.shiftCode (Roster sheet) discloses an overnight agent-day's span as 'Sun 22:00-Mon 06:00' -- both calendar dates, spelled out as short weekday abbreviations -- while every same-day cell on every desk stays byte-identical"
  - "The Roster legend restructured from one horizontal row of eight cells to one code/meaning pair per row, five rows, carrying the new locked crossing-day row"
  - "writeAllocationSheet's slot columns ordered by window.anchoredStartMinute instead of clock time, so an overnight shift renders as one contiguous run of filled cells on the per-date Allocation sheet"
  - "writeAgentAllocation's agent-day rows grouped by an anchored (start-minute, end-minute) key instead of a clock-ordered string, so a night desk's day begins with its own first shift rather than wherever its clock time falls"
affects: [21-08, 21-10, 21-11]

# Actuals (#2632)
actuals:
  tokens: 7954
  tasks: 3
  commits: 6
  plan_head_before: c1be7f6b677a04ab97adcc3e9b3a07ad468b2097
  plan_head_after: f2c9da2786e29331e3dd1ed474ededcc9f31ed3c

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A calendar-crossing disclosure (OVNT-07/D-12) is derived by comparing DayWindow.calendarDateAtDayStartOffset at the envelope's two anchored offsets, clamping an END offset of exactly MINUTES_PER_DAY down to MINUTES_PER_DAY-1 for that lookup only (the helper's documented argument range), and falling back to the plain 'start-end' text when the two dates are equal -- the same pattern now used for both the envelope branch and the no-envelope worked-span fallback in shiftCode"
    - "Packing a two-level anchored sort key (start minute, then end minute) into one comparable Long via start * (MINUTES_PER_DAY + 1) + end, so a single Comparator.naturalOrder() reproduces a two-field comparison without an array or a custom Comparator chain"
    - "Keeping a to-be-superseded sort-key producer (shiftSortKey) alive specifically because a sibling function (shiftLabel) still needs its STRING form for display text, while introducing a new, separate key purely for ORDERING -- avoids coupling the two concerns through one function when they diverge"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ScheduleExportService.java
    - src/test/java/com/wfm/service/ScheduleRosterExportTest.java
    - src/test/java/com/wfm/service/ScheduleAllocationExportTest.java

key-decisions:
  - "OVNT-06's literal text ('a continuation indicator on the morning-after cell') was not implemented on the Roster sheet -- confirmed void per the plan's own framing (D-11): that sheet is keyed by business date, so an overnight shift already occupies exactly one cell, and there is no morning-after cell to annotate. No code change was made to chase the literal requirement text; OVNT-07's disclosure and the Allocation sheet's column/row ordering are the real fixes."
  - "shiftCode's raw day start is threaded as an explicit third parameter from writeRoster (which already holds detail), not read via DayWindow's existing dayStart() accessor from inside shiftCode -- matches the plan's explicit action item to thread the value already in scope rather than add a second read path."
  - "P-01 (planner-surfaced, not in any requirement or decision): writeAgentAllocation's row grouping used the same clock-ordered string key (shiftSortKey) that D-13 found and fixed for the Allocation sheet's COLUMNS. Found by reading the method during Task 3's read_first, not inherited as a known item. Fixed in Task 3 by introducing anchoredShiftSortKey, a packed-Long anchored key, while leaving shiftSortKey itself alone (shiftLabel still needs its string form)."

requirements-completed: [OVNT-06, OVNT-07]

coverage:
  - id: D1
    description: "An overnight Roster cell spells out both weekdays (Sun 22:00-Mon 06:00); every same-day cell, on every desk, is byte-identical to today"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ScheduleRosterExportTest#overnightEnvelopeCellSpellsOutBothWeekdays"
        status: pass
      - kind: unit
        ref: "ScheduleRosterExportTest#sameDayEnvelopeCellOnAnAnchoredDeskStaysByteIdentical"
        status: pass
      - kind: unit
        ref: "ScheduleRosterExportTest#sameDayEnvelopeCellOnAMidnightDeskStaysByteIdentical"
        status: pass
      - kind: unit
        ref: "ScheduleRosterExportTest#envelopeEndingExactlyAtTheAnchorRendersWithoutThrowing"
        status: pass
      - kind: unit
        ref: "ScheduleRosterExportTest#noEnvelopeFallbackAndBlankCellsAreUnchanged"
        status: pass
    human_judgment: false
  - id: D2
    description: "The Roster legend is vertical, one code/meaning pair per row, five rows, carrying the new locked 'shift crossing into the next calendar day' row; date columns widened to 22 POI character units so the cell does not truncate"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ScheduleRosterExportTest#legendIsVerticalFiveRowsWithTheLockedCrossingRow"
        status: pass
      - kind: unit
        ref: "ScheduleRosterExportTest#dateColumnsAreWidenedToFitTheOvernightCell"
        status: pass
      - kind: other
        ref: "grep -cE 'shift crossing into the next calendar day' ScheduleExportService.java (prints 2 -- the legend write site and the before-picture reproduced in the roster-legend comparison test)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The Allocation sheet's slot columns are ordered by offset from the desk's day start, so an overnight shift renders as one contiguous run of filled cells; column count and a midnight-anchored desk's output are unchanged"
    requirement: "OVNT-06"
    verification:
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredSlotColumnOrder#contiguousRunFromTheAnchor"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredSlotColumnOrder#midnightAnchorSequenceUnchanged"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredSlotColumnOrder#columnCountUnchanged"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredSlotColumnOrder#shortfallOnlySlotKeepsItsAnchoredPosition"
        status: pass
    human_judgment: false
  - id: D4
    description: "The Allocation sheet's agent rows are grouped in anchored envelope order (planner item P-01): a night desk's first shift appears first; the no-envelope bucket still sorts last; a midnight-anchored desk's row order is unchanged; agents sharing an envelope still tie-break by name"
    requirement: "OVNT-06"
    verification:
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredRowGrouping#anchoredEnvelopeOrderPutsNightShiftFirst"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredRowGrouping#midnightAnchorRowOrderUnchanged"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredRowGrouping#offRosterSortsLastOnAnAnchoredDesk"
        status: pass
      - kind: unit
        ref: "ScheduleAllocationExportTest$AnchoredRowGrouping#tieBreakByNameWithinASharedEnvelope"
        status: pass
    human_judgment: false
  - id: D5
    description: "The restructured vertical legend does not collide with the agent rows above it, and the frozen top row and first column still behave with the taller legend block"
    requirement: "OVNT-07"
    verification:
      - kind: manual_procedural
        ref: "Generated a real 21:00-anchored-desk workbook and read its Roster sheet row-by-row via POI: row 0 header, row 1 the single agent (cell text 'Sun 22:00-Mon 06:00' in its own date column), row 2 blank, rows 3-8 'Legend' title then the five pair rows in order, rows 9+ empty. PaneInformation confirmed verticalSplitLeftColumn=1, horizontalSplitTopRow=1 -- createFreezePane(1,1), unchanged."
        status: pass
    human_judgment: true
    rationale: "The plan's own <verify> designates this a human-check backstop -- a generated workbook's visual layout in Excel is not fully assertable from cell values and frozen-pane coordinates alone. The executor performed the closest automatable proxy (byte-for-byte row/column inspection of a real exported workbook plus the frozen-pane coordinates) and it confirms no collision and an unchanged freeze pane; a human opening the file in Excel is still the authoritative check this backstop exists for."

duration: ~70 min
completed: 2026-10-02
status: complete
---

# Phase 21 Plan 07: Overnight Excel Export Disclosure and Anchored Ordering Summary

**The Roster sheet's overnight cell now spells out both weekdays (`Sun 22:00-Mon 06:00`) with a vertical five-row legend, and the per-date Allocation sheet orders both its slot columns and its agent rows from the desk's own day start instead of clock time, so an overnight shift reads as one contiguous block in both dimensions.**

## Performance

- **Duration:** ~70 min
- **Tasks:** 3 of 3
- **Files modified:** 3

## Accomplishments
- `ScheduleExportService.shiftCode` (Roster sheet) now discloses the calendar dates an agent-day's span touches: when the start and end land on different calendar days it renders `{startAbbrev} {start}-{endAbbrev} {end}` (e.g. `Sun 22:00-Mon 06:00`), using the same `TextStyle.SHORT`/`Locale.ENGLISH` weekday abbreviation the column header already uses. When the two dates are equal -- every same-day cell, on every desk -- the cell is byte-identical to today's plain `start-end` text. Applied uniformly to both the assigned-envelope branch and the no-envelope worked-span fallback.
- An envelope ending exactly at the anchor (anchored end minute 1440, `DayWindow.calendarDateAtDayStartOffset`'s documented exclusive upper bound) is handled by clamping to minute 1439 for that one lookup, so the function never throws at that edge.
- The Roster legend is restructured from one horizontal row of eight cells into five rows, one code/meaning pair per row, carrying the new locked `"Sun 22:00-Mon 06:00    shift crossing into the next calendar day"` row. The four pre-existing pairs' text and styles are unchanged. Date columns widened from 16 to 22 POI character units so the 19-character overnight cell does not truncate.
- `writeAllocationSheet`'s slot-column list is now sorted by `window.anchoredStartMinute` instead of left in the `TreeSet<LocalTime>`'s natural clock order, so an overnight agent-day's filled cells form one contiguous run (e.g. `22:00` through `05:00` on a `21:00`-anchored desk) instead of two runs split across the midnight boundary. Column count, `slotStarts`, `incrementMinutes` and `SLOT_COL` are all unchanged; at a `00:00` anchor the emitted sequence is identical to today's.
- `writeAgentAllocation`'s row comparator now sorts by a new `anchoredShiftSortKey` -- the envelope's anchored start minute, then its anchored end minute, packed into one comparable `Long` -- instead of the clock-ordered string `shiftSortKey`. A night desk's first shift (e.g. `22:00`) now sorts before a daytime one (`08:00`) rather than wherever its clock time falls. `shiftLabel`'s displayed text is untouched; the no-envelope bucket still sorts last; agents sharing an envelope still tie-break by name case-insensitively.
- No continuation marker, plus-one suffix, or morning-after annotation was added anywhere on the Roster sheet -- OVNT-06's literal "continuation indicator on the morning-after cell" text was confirmed void for that sheet per the plan's own D-11 framing, since the sheet is already business-day-keyed and an overnight shift already occupies exactly one cell there.

## Task Commits

Each task followed RED -> GREEN TDD discipline, one commit pair per task:

1. **Task 1: Spell out both weekdays in an overnight Roster cell**
   - `1ce847d` (test/RED) -- 7 new tests added to `ScheduleRosterExportTest`. Confirmed RED: 6/7 failed on assertion (the still-clock-formatted cell text, the still-16-wide column, a `NullPointerException` on the not-yet-restructured legend rows); the 7th (midnight-desk no-op control) passed already since it asserts unchanged behavior.
   - `f2629af` (feat/GREEN) -- `shiftCode` widened to a 3-arg signature threading the raw day start; new `crossingAwareCode` helper; legend restructured to 5 vertical rows; column width raised to `22 * 256`. All 13 tests in the class green. A test-authoring bug surfaced during GREEN verification (4 new tests indexed the wrong roster column -- corrected to a named `SUN_COL` constant; no production code was at fault) -- see Deviations.
2. **Task 2: Order the Allocation sheet's slot columns from the desk's day start**
   - `3e1a82d` (test/RED) -- 4 new tests added in a new `ScheduleAllocationExportTest$AnchoredSlotColumnOrder` nested class. Confirmed RED: the contiguity test and the shortfall-position test failed on the real assertion (clock order put `00:00`-`05:00` before `22:00`-`23:00`); the no-op control and column-count tests passed already.
   - `ac63934` (feat/GREEN) -- `writeAllocationSheet`'s slot list sorted by `window.anchoredStartMinute`. All 28 tests in the class green after also correcting a pre-existing 21-03 test (`postMidnightShortfallLandsOnBusinessDaySheet`) whose column-order assertions encoded the clock-order behavior this task intentionally changes -- see Deviations.
3. **Task 3: Group the Allocation sheet's agent rows in anchored envelope order**
   - `ef1aca8` (test/RED) -- 4 new tests added in a new `ScheduleAllocationExportTest$AnchoredRowGrouping` nested class. Confirmed RED: only the night-shift-first test failed, on the real assertion (the `08:00` group sorted first by clock-ordered string comparison); the other three passed already (null handling and tie-break are anchor-independent).
   - `f2c9da2` (feat/GREEN) -- new `anchoredShiftSortKey` (packed-`Long` anchored key) replaces the clock-ordered string key for row ordering; `shiftSortKey` kept alive only because `shiftLabel` still needs its string form. All 32 tests in the class green.

**Plan metadata:** this SUMMARY commit (docs: complete plan)

_TDD discipline: every RED commit was confirmed to fail intentionally -- a genuine assertion failure on the exact planned behavior (or, for Task 1's test-authoring slip, an assertion failure that correctly surfaced a test bug rather than a production one) -- before its GREEN commit landed. No RED was a compile error, a vacuous failure, or an unrelated breakage._

## Files Created/Modified
- `src/main/java/com/wfm/service/ScheduleExportService.java` -- `shiftCode`/`crossingAwareCode` (Task 1: overnight cell disclosure, vertical legend, widened date columns); `writeAllocationSheet`'s slot-list sort (Task 2: anchored column order); `anchoredShiftSortKey` and `writeAgentAllocation`'s comparator (Task 3: anchored row order)
- `src/test/java/com/wfm/service/ScheduleRosterExportTest.java` -- 7 new tests for the overnight cell, legend restructure, and column width (Task 1)
- `src/test/java/com/wfm/service/ScheduleAllocationExportTest.java` -- 2 new `@Nested` classes (`AnchoredSlotColumnOrder`, 4 tests; `AnchoredRowGrouping`, 4 tests), plus a correction to 21-03's `UnfilledSeatAttribution#postMidnightShortfallLandsOnBusinessDaySheet` (column-order assertions updated to the new anchored order)

## Decisions Made
- OVNT-06's literal requirement text was not chased on the Roster sheet -- see `key-decisions` above (D-11's void premise). The Allocation sheet's column (Task 2) and row (Task 3) ordering are where the real overnight-fragmentation defect OVNT-06 describes actually lived, and both are fixed.
- `shiftCode`'s new third parameter threads `detail.getDayStart()` from `writeRoster` rather than reading it via `DayWindow.dayStart()` (which already exists from 21-01) from inside `shiftCode` -- the plan's explicit instruction, matched literally, since the caller already holds the raw value.
- P-01 (planner-surfaced during Task 3's `read_first`, in no requirement or decision): `writeAgentAllocation`'s row grouping shared the same clock-ordered-string defect D-13 already fixed for the Allocation sheet's columns. Fixed here as Task 3, using a packed-`Long` anchored key rather than touching `shiftSortKey` itself (which `shiftLabel` still needs for its string text).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug, test-only] Four new Task 1 tests indexed the wrong roster column**
- **Found during:** Task 1, GREEN verification
- **Issue:** `overnightEnvelopeCellSpellsOutBothWeekdays`, `sameDayEnvelopeCellOnAnAnchoredDeskStaysByteIdentical`, `envelopeEndingExactlyAtTheAnchorRendersWithoutThrowing` and `noEnvelopeFallbackAndBlankCellsAreUnchanged` all used the SUN business date for their fixture's single agent-day, but asserted against roster column 1 (the period's first day, MON) instead of column 7 (SUN's own column -- the period fills all 7 days regardless of which carry data). This produced an empty-string assertion failure that looked like a production bug but was a test-authoring slip.
- **Fix:** introduced a named `SUN_COL = 7` constant and corrected all four assertions to read that column.
- **Verification:** `./gradlew test --tests "com.wfm.service.ScheduleRosterExportTest"` green (13/13).
- **Committed in:** `f2629af` (Task 1 GREEN commit, since RED had already locked in the correct behavior expectation and only the column index needed fixing before GREEN could prove it)

**2. [Rule 1 - Bug, test-only] A pre-existing 21-03 test's column-order assertions encoded the behavior Task 2 intentionally changes**
- **Found during:** Task 2, GREEN verification
- **Issue:** `UnfilledSeatAttribution#postMidnightShortfallLandsOnBusinessDaySheet` (added by plan 21-03, on a `21:00`-anchored desk) asserted `header.getCell(3)` = `"02:00"` and `header.getCell(4)` = `"22:00"` -- clock order. Task 2's anchored re-ordering makes `22:00` (anchored minute 60) legitimately precede `02:00` (anchored minute 300) on that desk, so this pre-existing assertion became wrong the moment the fix landed. The read_first note scoping "the byte-identical regression surface at a 00:00 anchor" to that anchor explicitly excludes this fixture.
- **Fix:** updated the header-cell and unfilled-row-cell assertions to the new anchored order (`22:00` at column 3, `02:00` at column 4; the shortfall marker moves from column 3 to column 4 accordingly).
- **Verification:** `./gradlew test --tests "com.wfm.service.ScheduleAllocationExportTest"` green (28/28) after the fix.
- **Committed in:** `ac63934` (Task 2 GREEN commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1, both test-only corrections; no production code was at fault in either case)
**Impact on plan:** Neither deviation touched production logic or added scope. Both were necessary for the test suite to accurately assert the plan's own specified behavior. No scope creep.

## Issues Encountered

None beyond the two deviations above. All three tasks' RED commits were confirmed to fail for the planned reason before their GREEN commits landed, and the full unfiltered `./gradlew test` run was green throughout.

## User Setup Required

None - no external service configuration required.

## Verification

1. `./gradlew compileJava compileTestJava` -- clean.
2. `./gradlew test --tests "com.wfm.service.ScheduleRosterExportTest" --tests "com.wfm.service.ScheduleAllocationExportTest" --tests "com.wfm.service.ScheduleExportServiceTest"` -- green.
3. `./gradlew test` (full suite, run unfiltered after all filtered runs, never read as an aggregate right after one): **1281 tests, 0 failures, 0 errors, 4 pre-existing skips, 201 classes** (baseline after 21-06 was 1266 tests / 199 classes; +15 tests across this plan's three tasks, +2 classes for the two new `@Nested` test classes).
4. Backstop legend-layout check: performed by generating a real exported workbook for a `21:00`-anchored desk and reading its Roster sheet row-by-row via POI -- see coverage entry D5 above for the result (no collision, freeze pane unchanged).
5. `src/test/resources/midnight-time-arithmetic.md`: confirmed unmodified across all six of this plan's commits (`git diff` against the pre-plan HEAD shows no changes to this file).

## Next Phase Readiness

- The Excel export's overnight disclosure (Roster cell + legend) and anchored ordering (Allocation columns + rows) are both complete and tested. Nothing in this plan touches `timeslotLabel`'s text or the frontend parser -- both remain exactly as 21-03 left them, for 21-08 (frontend parser) and 21-10 (label rewrite) to build on in their own planned order.
- `requirements-completed` lists `[OVNT-06, OVNT-07]` per this plan's own frontmatter, but neither is marked complete in REQUIREMENTS.md yet: both IDs are shared with sibling plans (OVNT-06 also declared by 21-10/21-11; OVNT-07 also declared by 21-03/21-10/21-11), and 21-10/21-11 have no SUMMARY yet. This is expected shared-ID gating (#2388), not a gap -- confirmed via `requirements.ready-ids`.
- No blockers for 21-08, 21-10 or 21-11. This plan's changes are additive to `ScheduleExportService`'s existing surface (a widened private-method signature and two new private helpers); nothing it touches is a dependency those plans need reworked.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-02*

## Self-Check: PASSED

All 3 modified source/test files confirmed present on disk; all 6 plan commits (`1ce847d`,
`f2629af`, `3e1a82d`, `ac63934`, `ef1aca8`, `f2c9da2`) confirmed present in `git log --oneline
--all`. Full unfiltered `./gradlew test` run: 1281 tests, 0 failures, 0 errors, 4 pre-existing
skips, across 201 test classes.
