---
phase: 21-overnight-shift-templates
reviewed: 2026-10-03T12:23:27Z
depth: standard
files_reviewed: 44
files_reviewed_list:
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskManagement.tsx
  - frontend/src/pages/ScheduleResults.tsx
  - frontend/src/utils/dayWindow.ts
  - src/main/java/com/wfm/controller/DeskController.java
  - src/main/java/com/wfm/controller/ScheduleController.java
  - src/main/java/com/wfm/dto/DeskResponse.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/dto/ScheduleSummary.java
  - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
  - src/main/java/com/wfm/repository/ScheduleRepository.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/service/FteUploadService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftTemplateService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/java/com/wfm/util/DayWindow.java
  - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
  - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
  - src/test/java/com/wfm/service/DriftReportTest.java
  - src/test/java/com/wfm/service/FteUploadServiceAnchoredStartTest.java
  - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
  - src/test/java/com/wfm/service/ScheduleAllocationExportTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ScheduleRosterExportTest.java
  - src/test/java/com/wfm/service/ScheduleSummaryReadTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
  - src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java
  - src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java
  - src/test/java/com/wfm/service/UsualShiftWritePathTest.java
  - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
  - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
  - src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java
  - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
  - src/test/resources/bday-join-guard.md
  - src/test/resources/midnight-boundary-scenarios.md
  - src/test/resources/midnight-time-arithmetic.md
findings:
  critical: 1
  warning: 3
  info: 1
  total: 5
status: issues_found
---

# Phase 21: Code Review Report

**Reviewed:** 2026-10-03T12:23:27Z
**Depth:** standard
**Files Reviewed:** 44
**Status:** issues_found

## Summary

This phase converts a large, well-disciplined surface area (solver, Excel export, shift-library
generation, FTE upload, and the React results grid) from midnight-implicit time arithmetic to
anchor-aware `DayWindow`/`dayWindow.ts` arithmetic. The overall craftsmanship is high: the two
remaining raw-comparison allowlist entries match the current source exactly, the four permitted
midnight-anchor bindings are all still exactly where documented, `ScheduleConstraintProvider`'s
`honourPreferredStartTime` widening to a real `ScheduleConfig` join is provably a no-op join
(singleton problem fact, never filtered), and `ScheduleOutputService`/`ScheduleExportService`/
`StaffingRequirementService` consistently key their per-date maps and deletes by
`Timeslot.getBusinessDate()`, not `getDate()`.

Against that high bar, one of the twelve plans' sibling conversions was missed: `FteUploadService`'s
second pass builds its date→time→Timeslot lookup keyed by **calendar** date but queries it with the
uploaded sheet's **business** date, which is exactly the "map written under one key and read under
the other" defect class this phase exists to close elsewhere. On any desk whose day start is not
`00:00` — the exact desks this phase was written to enable — every FTE value in a sheet's
post-midnight columns silently fails to resolve its timeslot and is dropped into `skippedDetails`,
rather than saved. No existing test (including the new `FteUploadServiceAnchoredStartTest`, which
only covers the first-pass min/max start-time tracking) exercises this path on an anchored desk with
data rows, so the full green suite does not contradict this finding.

Three further issues are below blocker severity: a validation helper (`ShiftTemplateService
.isAligned`) carries a correctness assumption that is false whenever a desk's live timeslot grid
starts later than its day-start anchor; `DeskService.setDayStart`'s stranding check does not
distinguish retired shift-template eras from live ones, which can produce a false refusal; and the
results grid's envelope-reached check has no defensive bound against the one case (`DayWindow`'s
`[0, MINUTES_PER_DAY]` contract) that throws, with no error boundary anywhere in the frontend to
catch it.

## Critical Issues

### CR-01: FTE upload silently drops post-midnight demand on any desk anchored away from midnight

**File:** `src/main/java/com/wfm/service/FteUploadService.java:165-169, 190, 213-218`

**Issue:** The second pass builds a lookup map keyed by `Timeslot.getDate()` (the **calendar** date,
which `TimeslotGeneratorService` documents as diverging from the business date for every
post-midnight slot on an anchored desk — "A 21:00-anchored desk's post-midnight rows carry the
FOLLOWING calendar date but the ORIGINAL business date"):

```java
Map<LocalDate, Map<LocalTime, Timeslot>> timeslotLookup = new HashMap<>();
for (Timeslot ts : timeslots) {
    timeslotLookup.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
            .put(ts.getStartTime(), ts);
}
```

but is then queried by `sheetDate` — the business date parsed from the uploaded sheet's name, which
is the SAME business day for every column in that sheet (confirmed by
`FteUploadServiceAnchoredStartTest`'s own fixture, whose single sheet named `2026-01-05` carries
columns `22:00, 23:00, 06:00, 07:00` spanning one 21:00-anchored business day across two calendar
dates):

```java
Map<LocalTime, Timeslot> daySlots = timeslotLookup.getOrDefault(sheetDate, Map.of());
...
Timeslot ts = daySlots.get(slotStart);
if (ts == null) {
    skipped.add("Sheet '" + sheetName + "' row " + (r + 1)
            + ": no timeslot for " + slotStart.format(TIME_FMT));
    continue;
}
```

On a `00:00`-anchored desk `ts.getDate()` always equals the business date, so this is invisible
today — but on exactly the overnight-anchored desks this phase exists to support, every column whose
clock time is past the anchor (e.g. the `06:00`/`07:00` columns in the test fixture above) lives
under calendar date `sheetDate + 1` in the map, and `timeslotLookup.get(sheetDate)` never finds it.
Every such FTE value is routed to `skipped` instead of being saved as a `StaffingRequirement` — a
correctness regression on the exact feature this phase ships, not merely a missed edge case. This is
distinct from, and not excused by, `StaffingRequirementRepository`'s documented decision to keep
`deleteLiveByDeskAndDateRange`'s calendar-date semantics for this caller's *delete* query — that note
is about which date system bounds the clear, not about how the per-sheet lookup should key
individual timeslots.

Every other per-date map or delete in this phase's scope (`ScheduleOutputService`,
`ScheduleExportService`, `StaffingRequirementService.saveRequirements`/`calculateErlangC`/
`calculateErlangX`) keys by `getBusinessDate()`; this is the one outlier, and the new test added for
this class (`FteUploadServiceAnchoredStartTest`) only covers the unrelated first-pass min/max
start-time tracking, with no data rows, so it never exercises this map.

**Fix:**
```java
Map<LocalDate, Map<LocalTime, Timeslot>> timeslotLookup = new HashMap<>();
for (Timeslot ts : timeslots) {
    timeslotLookup.computeIfAbsent(ts.getBusinessDate(), k -> new HashMap<>())
            .put(ts.getStartTime(), ts);
}
```
This is byte-identical on every desk that exists today (business date equals calendar date at a
`00:00` anchor) and resolves correctly on an anchored desk, matching the keying convention every
sibling consumer in this phase already uses.

## Warnings

### WR-01: `ShiftTemplateService.isAligned`'s END-position reinterpretation is unsound when the live grid starts later than the desk's anchor

**File:** `src/main/java/com/wfm/service/ShiftTemplateService.java:412-424`

**Issue:** `isAligned` always reads `candidate` in an END position, justified by this comment:

```java
// The candidate is read in an END position (00:00 -> 1440). That is safe for candidates
// in a START position too: the only start that could be 00:00 sits on a desk whose grid
// also starts at 00:00, and 1440 is divisible by every permitted increment (15/30/60), so
// the verdict is "aligned" either way.
long diffMinutes = window.anchoredEndMinute(candidate) - window.anchoredStartMinute(gridStart);
return diffMinutes >= 0 && diffMinutes % incrementMinutes == 0;
```

The justification assumes `gridStart` equals the desk's anchor whenever `candidate` equals the
anchor — but the method's own javadoc says the opposite: `gridStart` is "the desk's live timeslot
grid start, not necessarily the desk's day-start anchor." `TimeslotGeneratorService.getLiveBounds`
confirms `gridStart` is the MIN start time among the desk's actually-generated timeslots, which can
legitimately be later than the desk's anchor (e.g. an operator generates timeslots only for
`06:00`–`22:00` on a `00:00`-anchored desk). In that case, a shift template whose `startTime` equals
the anchor (`00:00`) is misread as `anchoredEndMinute(00:00) = 1440`, producing
`diffMinutes = 1440 - anchoredStartMinute(06:00) = 1080`, which is divisible by every permitted
increment and reports "aligned" — even though a start of `00:00` is six hours before the grid
actually begins and should fail alignment. The template would still likely be rejected later by the
envelope/operating-window checks if it crosses calendar midnight, but a same-day template
(`00:00`–`02:00`, which never crosses calendar midnight) would sail through both the grid-alignment
check and the midnight-gated operating-window check, producing a saved template outside the desk's
real operating hours with no refusal naming the actual problem.

**Fix:** Pass the candidate's own interval position (START vs END) into `isAligned` rather than
always reading it as END, or special-case the comparison so a START-position candidate uses
`anchoredStartMinute` against `gridStart`'s own start position:
```java
long diffMinutes = (position == END ? window.anchoredEndMinute(candidate) : window.anchoredStartMinute(candidate))
        - window.anchoredStartMinute(gridStart);
```

### WR-02: `DeskService.setDayStart`'s stranding check does not exclude retired shift-template eras

**File:** `src/main/java/com/wfm/service/DeskService.java:298-321`

**Issue:** The refusal that blocks a day-start change when it would strand a stored shift template
reads every template on the desk via `shiftTemplateRepository.findByTenantIdAndDeskId`, with no
filter on era (`effectiveTo` in the past / `eraStatus == PAST`):

```java
List<ShiftTemplate> stranded = shiftTemplateRepository.findByTenantIdAndDeskId(tenantId, deskId)
        .stream()
        .filter(t -> !proposed.anchoredIsForwardWithinDay(t.getStartTime(), t.getEndTime()))
        .toList();
```

A template that is already retired (its `effectiveTo` is in the past, and it can never be scheduled
again) can still block a legitimate day-start change today, with a refusal message naming a template
nobody will ever use. This is a false-refusal risk, not a correctness bug, but it works against the
very purpose of the check (protecting templates that could still be used).

**Fix:** Filter to current/upcoming eras before checking stranding, e.g.
`.filter(t -> t.getEffectiveTo() == null || !t.getEffectiveTo().isBefore(LocalDate.now()))`, mirroring
the era filters `ShiftLibraryValidationService` already applies elsewhere in this phase.

### WR-03: `AgentAllocationTab`'s envelope-reached check can throw past the end of the business day, with no error boundary to catch it

**File:** `frontend/src/pages/ScheduleResults.tsx:695-698`

**Issue:**
```javascript
const isEnvelopeReached = (slot: string) =>
  envelopeSpans.some(([s, e]) =>
    dayWindow.anchoredContains(s, e, slot, dayWindow.anchoredPlusWithinDay(slot, schedule.incrementMinutes))
  )
```
`anchoredPlusWithinDay` throws a `RangeError` when `slot`'s anchored offset plus
`schedule.incrementMinutes` exceeds `MINUTES_PER_DAY` (per `dayWindow.ts`'s documented contract on
`timeAtDayStartOffset`). `slot` is drawn from `shiftSlots`, a union of a regenerated, grid-aligned
set and the raw set of times pulled directly off `assignments`/`breaks`/unfilled-slot violations —
data that is not independently guaranteed to be increment-aligned to `schedule.startTime`. In the
normal case every such time is grid-aligned and this never fires, but there is no bounds check or
try/catch here, and this repository's frontend has no `ErrorBoundary` anywhere
(`grep -rl ErrorBoundary frontend/src` is empty), so any row that does hit this throws during render
and blanks the entire Schedule Results page rather than degrading one cell.

**Fix:** Guard the addition before calling into `DayWindow`, e.g.
```javascript
const end = Math.min(window.anchoredStartMinute(slot) + schedule.incrementMinutes, MINUTES_PER_DAY)
```
or wrap the per-slot check in a try/catch that treats a thrown error as "not reached," and consider
adding a top-level `ErrorBoundary` around route content generally so one bad row degrades gracefully
instead of blanking the page.

## Info

### IN-01: `DeskManagement.tsx`'s Cancel button stays enabled while a day-start save is in flight

**File:** `frontend/src/pages/DeskManagement.tsx:109, 172-175`

**Issue:** `savingDayStart` disables the Save button while `handleUpdate`'s two awaited calls
(`desks.update` then, conditionally, `desks.setDayStart`) are in flight, but the adjacent Cancel
button has no such guard:
```jsx
<button className="primary" onClick={handleUpdate} disabled={savingDayStart}>Save</button>
<button onClick={() => setEditingId(null)}>Cancel</button>
```
Clicking Cancel mid-save does not abort the in-flight request; when it resolves, `setDeskList`
still updates the matching row by id even though the row has left edit mode, which is harmless here
but is an inconsistency with the Save button's own guard and could confuse an operator who clicked
Cancel expecting the in-flight change to be discarded.

**Fix:** Disable Cancel alongside Save while `savingDayStart` is true, or make `handleUpdate` check a
cancellation flag before applying its result.

---

_Reviewed: 2026-10-03T12:23:27Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
