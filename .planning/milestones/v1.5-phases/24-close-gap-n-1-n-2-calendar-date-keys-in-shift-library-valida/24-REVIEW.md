---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
reviewed: 2026-10-07T00:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - frontend/src/api/client.ts
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/controller/StaffingRequirementController.java
  - src/main/java/com/wfm/dto/StaffingRequirementResponse.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java
  - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java
  - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
  - src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java
  - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
  - src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java
  - src/test/resources/bday-join-guard-offender-widened/OffendingSample.java
  - src/test/resources/bday-join-guard.md
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 24: Code Review Report

**Reviewed:** 2026-10-07
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

The phase re-keys the shift-library validator, the envelope and usual-shift repair services, the start-mix target service and the allocation-row demand fetch from a Timeslot's calendar date to its stored business date. I traced every migrated site against the key it is read with: the `AgentDay` keys, `freeByDate`, `occupied`, `seatsByDateAgent`, `reqByDate`, `seatsByDate`, `slotsByDate` and the `Window` record. All of them now agree with the business-date key of `AgentShiftAssignment.getDate()`. I found no remaining calendar-date `Timeslot` read in the four widened files.

The new business-date list endpoint is sound:
- The validation order is correct.
- The keyset predicate and sort are identical to the calendar twin, so cursors are interchangeable.
- `timeslot.business_date` is `NOT NULL` (V53), so there is no null-key risk.
- The tests assert by row id and cover paging, tenant scope and the 00:00 control.

I found no blockers. The defects are residual clock-order sorting in the same files that the phase explicitly moved to anchored order elsewhere, one cursor-handling hole in the new query path, and a DTO contract change that is not documented.

## Warnings

### WR-01: Generator sorts demand windows in clock order while the validator sorts them in anchored order, so the two "shared" coverage lists drift

**File:** `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:239-240`
**Issue:** `Window.describe` is documented as "the one operator-facing label ... shared by the validator and the generator so their coverage lists cannot drift". Ordering is not shared.
- `ShiftLibraryValidationService.findUncoveredWindows` now sorts by `businessDate`, then `anchoredStartMinute`, then `anchoredEndMinute`. `validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart` pins this.
- The generator's `windows` list is sorted by `Window::businessDate` then `Window::startTime`, which is clock order. This list feeds `computeUncoveredDetails`, which emits one `ErrorDetail("coverage", ...)` per window in list order.
- On a 06:00 desk the generator therefore lists business Monday's `01:00` window before its `21:00` window. The validator lists them the other way round.
- The code comment above the sort says the migration here was done deliberately. The ordering fix was applied to one of the two sorts only.
- At a 00:00 anchor the two orders coincide, so no existing test notices.

**Fix:**
```java
.sorted(Comparator.comparing(ShiftLibraryValidationService.Window::businessDate)
        .thenComparingInt(w -> dayWindow.anchoredStartMinute(w.startTime()))
        .thenComparingInt(w -> dayWindow.anchoredEndMinute(w.endTime())))
```
Use whichever `DayWindow` the enclosing method already holds (`window` in `enumerateCandidates`). Add a generator-side test mirroring `validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart`.

### WR-02: Peak-shortfall advisories tie-break on clock start time, contradicting the phase's own anchored-order rule

**File:** `src/main/java/com/wfm/service/ShiftLibraryValidationService.java:616-618`
**Issue:** The advisory sort is `shortfall` descending, then `date`, then `startTime`. `date` is now the business date, but `startTime` is still natural `LocalTime` order. On a 06:00 desk, two equal-shortfall advisories on one business day list the `01:00` hour (calendar next day) before the `21:00` hour. That is the ordering defect the phase fixes in `findUncoveredWindows` and in the repair service's violation sort. The same pattern remains in two other places where the sort key was left in clock order after the date key moved to business date:
- `ShiftStartMixTargetService.java:177` sorts `slotsByDate` with `Comparator.comparing(Timeslot::getStartTime)`. This is behaviourally harmless today, since `solveDate` treats slots as an indexed set.
- `ScheduleEnvelopeRepairService.java:315` uses `.thenComparing(f -> f.getTimeslot().getStartTime())` as the candidate tie-break. Its sibling violation sort a few lines earlier (line 170) was moved to `anchoredStartMinute`, so the file is now internally inconsistent about what "earlier" means. The tie-break still picks deterministically, but it prefers a late-business-day seat (`01:00`) over an early one (`21:00`) on a shifted desk, which the comment at line 165 says the file does not intend.

**Fix:**
```java
// ShiftLibraryValidationService.java
.thenComparing(PeakShortfallAdvisory::date)
.thenComparingInt(a -> dayWindow.anchoredStartMinute(a.startTime()))
// ScheduleEnvelopeRepairService.java (candidatesFor already has `window`)
.thenComparingInt(f -> window.anchoredStartMinute(f.getTimeslot().getStartTime()))
```

### WR-03: Business-range query path trusts the cursor payload and turns a malformed one into a 500

**File:** `src/main/java/com/wfm/service/StaffingRequirementService.java:122-130` (new branch; same shape at 136 and 147)
**Issue:** The phase hardens the new `businessFrom`/`businessTo` parameters so that every bad value becomes an `IllegalArgumentException` (400). The new `hasBusinessRange && hasCursor` branch still does `LocalDate.parse(cursorValues.get("date"))`, `LocalTime.parse(...)` and `UUID.fromString(...)` on a client-supplied cursor with no guard.
- A well-formed Base64 cursor whose JSON lacks a key makes `cursorValues.get(...)` return null, so `LocalDate.parse(null)` throws `NullPointerException`.
- A cursor with a non-date value throws `DateTimeParseException` (not an `IllegalArgumentException`).
- `UUID.fromString` throws `IllegalArgumentException`, which maps correctly.
- The first two surface as HTTP 500, and the class comment itself says unmapped parse exceptions do exactly that.
- The calendar twin has the same hole, but it is unchanged and pre-existing. The new branch copies it verbatim, and the new tests do not exercise it.

**Fix:** Decode the cursor once into a small validated record before branching. Throw `IllegalArgumentException("Invalid cursor")` on a missing key or a parse failure, for example:
```java
private record Keyset(LocalDate date, LocalTime startTime, String specName, UUID id) {}
private static Keyset parseKeyset(Map<String, String> c) {
    try {
        return new Keyset(LocalDate.parse(c.get("date")), LocalTime.parse(c.get("startTime")),
                Objects.requireNonNull(c.get("specName")), UUID.fromString(c.get("id")));
    } catch (RuntimeException e) {
        throw new IllegalArgumentException("Invalid cursor", e);
    }
}
```
Add a test with a cursor missing a key and one with a non-date value.

## Info

### IN-01: `PeakShortfallAdvisory.date` silently changed meaning with no contract note

**File:** `src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java:137-145`, set at `src/main/java/com/wfm/service/ShiftLibraryValidationService.java:578-579`
**Issue:** `date` now carries the business date, where it carried the calendar date before. Together with the unchanged `startTime`, the pair no longer identifies a calendar instant for post-midnight hours. The `StaffingRequirementResponse.Item` change documents exactly this distinction in its javadoc. This record's javadoc does not, so an API consumer that reads `date` + `startTime` as "when does this happen" will mis-date every post-midnight advisory. The `message` string does disclose the calendar date.
**Fix:** Add a line to the record javadoc, for example: "`date` is the BUSINESS date; for a post-midnight hour on a desk whose day start is not 00:00 the calendar date is disclosed only in `message`." Alternatively add a `calendarDate` component.

### IN-02: Join-guard receiver heuristic misses `Timeslot` locals not named `ts`, and the widened scan relies on that gap being empty

**File:** `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java:122-125, 435-451`
**Issue:** The widened scan is the only defence against regressions in the four widened files. It recognises only `.getTimeslot().getDate()`, a bare `ts.getDate()` and `Timeslot::getDate`. A local such as `Timeslot t = sr.getTimeslot(); ... t.getDate()` or `slot.getDate()` is invisible to it. The class javadoc acknowledges this as accepted risk, and the guard is green today. `StaffingRequirementService.toResponseItem` already uses `t.getDate()` (the intentional calendar read), which shows that the name `t` is in use in this codebase. The guard's silence is therefore weaker evidence than the markdown's "GREEN with this Allowlist block empty" suggests.
**Fix:** No change is required for this phase. If the guard is relied on further, either widen `BARE_TIMESLOT_VARIABLE` to a small set (`ts`, `t`, `slot`, `timeslot`) within the four widened files only, where no legitimate calendar read remains, or add a type-aware check (a declared `Timeslot <name>` in the file feeds the receiver set).

---

_Reviewed: 2026-10-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
