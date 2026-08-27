---
phase: 15-shift-envelope-breaks-library-generation
reviewed: 2026-08-27T18:28:00Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/java/com/wfm/model/AgentDayConfig.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - frontend/src/api/client.ts
  - frontend/src/pages/ScheduleResults.tsx
  - src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java
  - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
  - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
  - src/test/java/com/wfm/service/SolverSeatExpansionAccess.java
  - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
  - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
  - src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java
  - src/test/java/com/wfm/solver/ShiftModeFixtures.java
  - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
findings:
  critical: 1
  warning: 2
  info: 0
  total: 3
status: issues_found
---

# Phase 15: Code Review Report (gap-closure scope + carried-forward findings)

**Reviewed:** 2026-08-27T18:28:00Z
**Depth:** standard
**Files Reviewed:** 22 (of the 23 listed in scope — `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` sampled at reduced depth due to length; no defects found in the portion read)
**Status:** issues_found

## Summary

**Scope note:** this report covers two things, kept in separate sections below. First, the
**gap-closure change set** for G-15-10 — plans 15-09 through 15-13 (commits `b1a905b..be3765c`,
base `55c9ae7`) — reviewed fresh against the 23 files the workflow supplied. Second, the four
**carried-forward findings** from the prior `15-01..15-08` review (committed at `66bc0e8`), each
re-verified against the code currently on disk, since this file overwrites that earlier report and
must not silently drop its record. Readers should not treat this as a narrower, gap-closure-only
review — the carried-forward section is a first-class part of it.

**Gap-closure assessment.** The core claims of plans 15-09/15-10/15-11 hold up under direct
inspection: `SolverService.expandMinimumStaffingSeats` is genuinely mode-aware and SLOT-mode output
is provably untouched (dedicated invariance tests, and the SLOT branch of the frontend grid is
never touched by the new grouped-rendering code path); `AgentDayConfig.expectedWorkSlots()` is a
byte-identical extraction of the arithmetic `ScheduleConstraintProvider` used inline before (diff
confirmed); `requireShiftEnvelopeSeatSupply` is a real precondition gate with well-targeted tests
(shortfall, no-false-refusal, hours-mismatch, wholly-retired library, non-blocking advisory); and
CR-01 from the prior review (UPCOMING templates assignable immediately) is genuinely fixed by the
combination of `SolverService.filterLiveShiftTemplates` (coarse, period-overlap pre-filter) and
`AgentShiftAssignment.getEligibleShiftBandPairs()`'s new per-row `ShiftTemplate.isEffectiveOn(date)`
check.

However, one new **BLOCKER** was found in the plan 15-10 report-layer work: `ScheduleOutputService
.buildAgentSchedule` mutates the schedule's own `warnings` list every time it is called, with no
deduplication — and it is called on every `GET /schedules/{id}` request, including the frontend's
2-second poll while a solve is `RUNNING`. For a SHIFT-mode schedule with any shift-envelope
divergence, this appends a duplicate warning string on every single poll/view/export, growing the
list unboundedly on the live, shared, cached `Schedule` object held in `InMemoryScheduleStore` for
the entire pre-accept lifetime of the schedule. This is a genuine, provable regression, not a
theoretical one — see CR-04 below for the mechanism and evidence.

Two WARNING-level findings round out the gap-closure review: WR-01 (unscoped repository write) is
carried forward unchanged from the prior review, and a new WR-02 documents a frontend display
question (the "unstaffed by design" grid header treatment reflects only that day's actually-assigned
shifts, not the desk's full live shift library).

## Structural Findings (fallow)

None provided for this review — no `<structural_findings>` block was supplied.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-04: `buildAgentSchedule` appends a duplicate divergence warning on every call, permanently growing the shared in-memory schedule's warnings list

**File:** `src/main/java/com/wfm/service/ScheduleOutputService.java:225-239`
**Introduced:** commit `1bd953b` (plan 15-10, Task 2) — within this review's scope.

**Issue:** At the end of `buildAgentSchedule`, the method rolls the per-entry divergence counts up
into the schedule's own warnings collection:

```java
if (outOfEnvelopeSeatCount > 0 && schedule.getWarnings() != null) {
    schedule.getWarnings().add(outOfEnvelopeSeatCount
            + " agent-day seat(s) fall outside their assigned shift envelope; "
            + unworkedLegalSlotCount + " legal envelope slot(s) went unworked.");
}
```

This has no guard against being called more than once on the same `Schedule` instance, and no
deduplication of the message it appends. `Schedule.warnings` defaults to a live, non-null
`ArrayList` (`Schedule.java:167`), so the `!= null` guard is never false in practice — every call
with `outOfEnvelopeSeatCount > 0` appends another copy of the same string.

`buildAgentSchedule` is called from `ScheduleService.getScheduleDetail` (`ScheduleService.java:155`),
which is the handler behind both `GET /desks/{deskId}/schedules/{id}` and
`GET /desks/{deskId}/schedules/{id}/export` (`ScheduleController.java:56-61,89-92`). For a
**pre-accept** schedule (`RUNNING`/`COMPLETED`/`STOPPED`/`FAILED`), `getScheduleDetail` fetches the
schedule via `inMemoryStore.get(scheduleId)`, and `InMemoryScheduleStore.get()` returns the exact
object reference stored in its `ConcurrentHashMap` — not a copy (`InMemoryScheduleStore.java:31-33`).
The frontend polls this exact endpoint every 2 seconds while `status === 'RUNNING'`
(`ScheduleResults.tsx:27-45`), and continues to allow repeated views/exports after completion, up
until accept or reject. Each such call mutates the one live `Schedule` object's `warnings` list in
place, permanently, for as long as that schedule survives in memory (there is no transactional
rollback for this path, unlike the accepted/DB path where the mutation happens inside a
`readOnly=true` transaction and is discarded).

Concretely: a SHIFT-mode desk whose accepted output has any out-of-envelope seat (a real,
documented, reachable state per `ScheduleOutputServiceShiftReportingTest`'s SCATTERED fixture) will
accumulate one duplicate warning message per 2-second poll for the full duration of the solve, plus
one more every time the results/export page is subsequently viewed before accept/reject. A
multi-minute solve alone produces dozens of identical warning entries; leaving the results tab open
or re-visiting it multiplies that further. This is user-visible (the warnings panel/list in the UI)
and is a genuine behavioural regression introduced by this gap-closure round, not a pre-existing
condition — no equivalent call site existed before plan 15-10 added this block.

**Fix:** Do not mutate the schedule's warnings as a side effect of a read/report-building method.
Either (a) return the divergence-summary message as part of `buildAgentSchedule`'s own return value
(e.g., alongside the entries, or via a small result wrapper) and let the caller (`getScheduleDetail`)
decide once whether to fold it into the response's warnings, or (b) make the append idempotent by
checking for the exact message (or a stable prefix) before adding it:

```java
if (outOfEnvelopeSeatCount > 0 && schedule.getWarnings() != null) {
    String message = outOfEnvelopeSeatCount
            + " agent-day seat(s) fall outside their assigned shift envelope; "
            + unworkedLegalSlotCount + " legal envelope slot(s) went unworked.";
    if (!schedule.getWarnings().contains(message)) {
        schedule.getWarnings().add(message);
    }
}
```

Option (a) is preferable — a report-building method should not have a side effect on the input it
was handed at all, idempotent or not; a caller that invokes it more than once (as this codebase
demonstrably does) should not need to reason about repeat-call safety.

## Warnings

### WR-01 (carried forward from 15-01..15-08 review): `ShiftTemplateBreakBandRepository.deleteByShiftTemplate_Id` is the only unscoped write in an otherwise fully tenant-scoped repository

**Status:** Still present, unchanged.

**File:** `src/main/java/com/wfm/repository/ShiftTemplateBreakBandRepository.java:28`
**Issue:** Every other method on this repository takes `tenantId` explicitly and filters on it (the
class javadoc says as much: "Every method takes `tenantId` explicitly and filters on it"), but
`deleteByShiftTemplate_Id(UUID shiftTemplateId)` takes no tenant parameter at all. Its only caller,
`ShiftTemplateService.replaceBands` (`ShiftTemplateService.java:118-123`), does resolve the template
via `shiftTemplateRepository.findByIdAndTenantIdAndDeskId` first, so the current call site is safe
in practice — but the repository method itself carries no defense-in-depth: a future caller that
skips that resolution step (or a refactor that reorders it) would delete bands for a
`shiftTemplateId` without any tenant check, silently breaking this codebase's otherwise-universal
convention (and the explicit mitigation this same file's own comment claims for T-15-01).

**Fix:** Add a tenant-scoped overload (`deleteByTenantIdAndShiftTemplate_Id(long tenantId, UUID
shiftTemplateId)`) and switch the one caller to it, matching every other method on this interface.
If the unscoped method is kept for some other reason, its javadoc should make the caller obligation
a load-bearing contract (e.g., package-private visibility plus a comment) rather than a comment
alone.

### WR-02 (new): the Agent Allocation grid's "unstaffed by design" header treatment reflects only that day's actually-assigned shifts, not the desk's live shift library

**File:** `frontend/src/pages/ScheduleResults.tsx:616-623` (envelope-span computation),
`frontend/src/pages/ScheduleResults.tsx:634-648` (header rendering and tooltip text)
**Issue:** `envelopeSpans` — the set of intervals used to decide whether a grid column header gets
the grey/italic "no shift covers this hour" treatment and the tooltip text "No shift in this desk's
library covers this hour — unstaffed by design" — is derived from `shiftGroups`, which in turn is
built by partitioning `sortedEntries` (that date's already-filtered `AgentScheduleEntry` rows) by
shift identity:

```javascript
const envelopeSpans = shiftGroups
  .filter(g => g.key !== null)
  .map(g => [toHHMM(g.startTime), toHHMM(g.endTime)] as const)
```

This only ever contains the envelopes of shifts some agent was actually assigned to **on this
specific date**. It is not derived from the desk's shift template list at all — the component has
no such list in scope. If the desk's live library contains a template whose envelope reaches a given
hour, but on this particular date no agent ended up on that template (e.g., a solver choice, thin
demand at that template's hours, or a partial-supply day), the header for that hour is marked
"unstaffed by design" even though the library does, in fact, reach it — the tooltip's specific claim
("the library covers this hour" being false) is then inaccurate for that day. This is a display
correctness issue, not a scheduling-logic one, and does not affect solver behaviour or the divergence
computation itself — but the tooltip asserts a specific, checkable fact about the shift library that
the code does not actually verify, and could mislead an operator investigating an unexpectedly
under-staffed hour into believing the library was never intended to reach it.

**Fix:** Either (a) soften the tooltip text to describe what is actually being shown ("no agent
worked this hour" rather than "no shift in this desk's library covers this hour"), or (b) thread the
desk's live shift template envelopes (already available via `shiftTemplates.list(deskId)` elsewhere
in this codebase) into this component so `isEnvelopeReached` can test against the true library
rather than against that day's realized assignments.

## Resolved since previous review

### CR-01 (carried forward, RESOLVED): `SolverService.startSolve` now excludes UPCOMING/retired shift templates on a per-agent-day basis

**Original finding:** `SolverService.startSolve` never excluded UPCOMING shift templates, so a
not-yet-effective template was assignable immediately.

**Evidence of fix:** Two layers now enforce this, added in this gap-closure round (plan 15-09/15-11
region, explicitly labelled "CR-01 gap closure" in the source):

1. `SolverService.filterLiveShiftTemplates` (`SolverService.java:680-689`) — a coarse, desk-level
   pre-filter against the *schedule's period*, keeping a template only if
   `!effectiveFrom.isAfter(periodEndDate) && (effectiveTo == null || !effectiveTo.isBefore(periodStartDate))`.
   This deliberately does not use `LocalDate.now()`.
2. `AgentShiftAssignment.getEligibleShiftBandPairs()` (`AgentShiftAssignment.java:165-178`) — the
   precise, per-row filter, which in addition to the net-hours-match test now also requires
   `p.template().isEffectiveOn(date)` for THIS row's own date, via `ShiftTemplate.isEffectiveOn`
   (`ShiftTemplate.java:153-158`), which correctly checks both `effectiveFrom.isAfter(date)` (excludes
   UPCOMING) and `effectiveTo.isBefore(date)` (excludes retired).

Both layers call the same `isEffectiveOn` predicate, so they cannot disagree about what "live on this
date" means. `ShiftEnvelopeSupplyGateTest#refusesWhollyRetiredLibraryDistinctFromShortfall` and
`ShiftEnvelopeSupplyInvariantTest`'s CASE tests exercise this path directly. This finding is
genuinely closed.

### CR-02 (carried forward, RESOLVED — prior to this gap-closure round): accepted-schedule `schedulingMode` is no longer inferred from the shift snapshot

**Original finding:** Accepted-schedule `schedulingMode` was inferred from an empty shift snapshot,
permanently mislabeling a SHIFT-mode schedule that assigned no shifts.

**Evidence of fix:** `schedulingMode` is now a mapped, persisted column (V43) written once at accept
time from the in-memory schedule's own recorded mode (`ScheduleService.java:237-240`, `420-427`,
`533-538`, all marked "CR-02 gap closure" in the source comments). This fix (commit `75349d8`)
predates this gap-closure round's base commit (`55c9ae7`) — it is not part of the 23 files under
review here, but the review instructions require re-verifying it against current code, and it is
confirmed still in place and unchanged. This finding is genuinely closed.

### CR-03 (carried forward, RESOLVED — prior to this gap-closure round): `ScheduleService.deleteSchedule` now deletes the accepted schedule's `AgentShiftAssignment` rows

**Original finding:** `ScheduleService.deleteSchedule` never deleted the accepted schedule's
`AgentShiftAssignment` rows, orphaning data on every SHIFT-mode delete.

**Evidence of fix:** `deleteSchedule` now calls
`agentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId)`
explicitly before deleting the schedule row (`ScheduleService.java:363-370`, marked "CR-03 gap
closure"). This fix (commit `6065fd6`) also predates this round's base commit but is confirmed
still in place and unchanged. This finding is genuinely closed.

---

_Reviewed: 2026-08-27T18:28:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
