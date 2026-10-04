---
phase: 22-minimum-rest
reviewed: 2026-10-04T04:31:15Z
depth: standard
files_reviewed: 47
files_reviewed_list:
  - src/main/resources/db/migration/V55__add_minimum_rest.sql
  - src/main/java/com/wfm/model/RestSpan.java
  - src/main/java/com/wfm/dto/MinimumRestRequest.java
  - src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java
  - src/test/java/com/wfm/service/DeskServiceMinimumRestTest.java
  - src/main/java/com/wfm/model/Desk.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/model/ConstraintWeights.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/controller/DeskController.java
  - src/main/java/com/wfm/dto/DeskResponse.java
  - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
  - src/main/java/com/wfm/service/ConstraintWeightsService.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
  - src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java
  - src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java
  - src/main/java/com/wfm/model/AgentRestWaiver.java
  - src/main/java/com/wfm/repository/AgentRestWaiverRepository.java
  - src/main/java/com/wfm/service/RestWaiverService.java
  - src/main/java/com/wfm/dto/RestWaiverResponse.java
  - src/test/java/com/wfm/service/RestWaiverServiceTest.java
  - src/main/java/com/wfm/controller/DeskAgentController.java
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskManagement.tsx
  - src/main/java/com/wfm/model/RestWaiverLookup.java
  - src/test/resources/rest-waiver-predicate-guard.md
  - src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java
  - src/main/java/com/wfm/service/RestPredecessorService.java
  - src/test/java/com/wfm/service/RestPredecessorServiceTest.java
  - src/test/java/com/wfm/solver/RestHorizonEdgeTest.java
  - src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java
  - src/main/java/com/wfm/repository/AgentAssignmentRepository.java
  - src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java
  - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/dto/ScheduleSummary.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/controller/ScheduleController.java
  - src/test/java/com/wfm/service/ScheduleSummaryReadTest.java
  - src/test/java/com/wfm/service/DriftReportTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - frontend/src/pages/AgentExceptions.tsx
  - frontend/src/pages/ScheduleResults.tsx
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase 22: Code Review Report

**Reviewed:** 2026-10-04T04:31:15Z
**Depth:** standard
**Files Reviewed:** 47
**Status:** issues_found

## Summary

This phase adds a configurable per-desk minimum rest period, enforced via two mode-gated hard
constraints (`minimumRestShift`, `minimumRestSlot`), a pre-solve structural-impossibility refusal
(`requireRestFeasibility`), and a per-agent/per-date waiver mechanism, all built on one shared
gap-arithmetic primitive (`RestSpan.gapMinutes`) and one shared waived-pair predicate
(`RestWaiverLookup`), both enforced by structural guard tests
(`MidnightTimeArithmeticGuardTest`, `RestWaiverPredicateGuardTest`).

I traced the constraint-stream shapes in `ScheduleConstraintProvider` (structural inertness on
NULL/wrong-mode via the leading filtered `forEach(ScheduleConfig.class)`, the indexed self-join
over `RestSpan`, the pre-horizon `concat` applied one-directionally to the predecessor side only),
the pre-solve refusal and horizon-edge handling in `SolverService`, the pre-horizon resolution in
`RestPredecessorService`, and tenant/desk scoping on every new repository method and every new
`/rest-waivers` and `/minimum-rest` endpoint. All of it is correctly and defensively scoped by
`tenantId` + `deskId` with no bare `findById` exposed anywhere in the new surface, and the
constraint arithmetic correctly routes through `DayWindow`/`RestSpan` rather than raw
`LocalTime`/`Duration` comparisons. I did not find a security gap (no missing tenant scoping, no
injection, no auth bypass) and did not find a correctness bug in the gap arithmetic or constraint
stream shapes themselves — the test suites for both are unusually thorough, including exact
boundary cases (gap == minimum, gap == minimum - 1, the midnight-boundary zero-gap case, the
waiver-direction (D-06) cases, and the horizon-edge with/without-pair pairs).

The issues I did find are both robustness/consistency gaps rather than reachable-today defects:
one inconsistent null-handling convention in a new read path, and one case of the same derived
value being computed by two independently-maintained call sites with only a single test asserting
their parity (versus the structural guard this exact phase uses elsewhere for an analogous risk).
I also confirm, but do not re-report per the review brief, the already-known false-0/0
`appliedRestWaiverCount`/`unusedRestWaiverCount` on an ACCEPTED schedule reached through the
DB-fallback path in `listSchedules`/`getScheduleSummary` (`ScheduleService.toSummary` /
`ScheduleController.toSummary`, both of which skip `loadSnapshotData` and therefore read the
schedule's default-empty transient `agentRestWaivers`/`shiftAssignments`/`assignments`
collections) — I did not find a second instance of it beyond the two call sites already named in
the phase brief.

## Warnings

### WR-01: `buildRestWaiverDisclosure` reads `schedule.getDayStart()` without the codebase's established null-safe anchor fallback

**File:** `src/main/java/com/wfm/service/ScheduleOutputService.java:958` (used at lines 973-974 and 987)
**Issue:** Every other anchor-consuming call site that reaches `DayWindow`/`RestSpan` in this
phase goes through a null-coalescing helper before binding the anchor —
`ScheduleConstraintProvider.resolveAnchor` defaults a null `dayStart` to `LocalTime.MIDNIGHT`
specifically because, per that method's own javadoc, "every `Schedule` built before BDAY-04 and
every hand-built test fixture that never calls `setDayStart`" can carry a null value, and a raw
`DayWindow.anchoredAt(null)` throws `IllegalArgumentException` (`DayWindow.java:94-96`).
`buildRestWaiverDisclosure` does not follow that convention:

```java
LocalTime dayStart = schedule.getDayStart();
...
RestSpan span = new RestSpan(sa.getAgent().getId(), sa.getDate(),
        descriptor.startTime(), descriptor.endTime(), dayStart);
...
inHorizonSpans.put(key, RestSpan.ofSlots(key.agentId(), key.date(), entry.getValue(), dayStart));
```

`RestSpan.ofSlots` calls `DayWindow.anchoredAt(dayStart)` internally, and `RestSpan.gapMinutes`
does the same — both throw `IllegalArgumentException` on a null anchor. This method is reached
from `GET /desks/{deskId}/schedules/{scheduleId}` and the summary endpoint whenever
`schedule.getMinimumRestMinutes() != null`, so a schedule row that somehow carries a non-null
`minimumRestMinutes` alongside a null `dayStart` would 500 here instead of degrading. In today's
schema this specific combination cannot arise through any normal write path (the `dayStart`
column predates `minimum_rest_minutes` by one migration, V54 vs V55, and
`SolverService.buildSchedule` always copies both from the same non-null-defaulting `Desk`), so
this is not reachable today — but every test in `RestWaiverDisclosureTest` passes
`LocalTime.MIDNIGHT` explicitly and none exercises a null `dayStart`, so a future change that
introduces such a row (a manual data fix, a migration that nulls the column, a new construction
path) would regress silently until it reaches this exact method.
**Fix:** Route through the same null-coalescing convention used elsewhere, e.g.
`LocalTime dayStart = schedule.getDayStart() != null ? schedule.getDayStart() : LocalTime.MIDNIGHT;`
(or extract `ScheduleConstraintProvider.resolveAnchor`'s one-line fallback into a shared utility
both classes call), and add a `RestWaiverDisclosureTest` case with a null `dayStart` schedule
proving it degrades rather than throws.

### WR-02: Rest-waiver summary counts are computed by two independently-maintained call sites, unlike the waived-pair predicate this same phase structurally guards

**File:** `src/main/java/com/wfm/service/ScheduleService.java:705-711` and
`src/main/java/com/wfm/controller/ScheduleController.java:154-160`
**Issue:** `ScheduleService.toSummary` and `ScheduleController.toSummary` each independently
re-implement the identical four lines:

```java
Integer appliedRestWaiverCount = null;
Integer unusedRestWaiverCount = null;
if (s.getMinimumRestMinutes() != null) {
    var disclosure = scheduleOutputService.buildRestWaiverDisclosure(s, s.getStatus() == ScheduleStatus.ACCEPTED);
    appliedRestWaiverCount = disclosure.applied().size();
    unusedRestWaiverCount = disclosure.unused().size();
}
```

Both sites' own comments acknowledge the duplication ("the sibling construction site... so the two
construction sites can never disagree") and rely on exactly one behavioural test
(`RestWaiverDisclosureTest#summaryCounts_bothConstructionSitesAgree`) to catch drift. That is a
materially weaker guarantee than the structural guard (`RestWaiverPredicateGuardTest` +
`rest-waiver-predicate-guard.md`) this same phase builds for the analogous "two things must agree
about one meaning" risk in the waived-pair predicate — there, a second implementation anywhere
else in `src/main/java` fails the build by construction; here, a future edit to one call site
(e.g. changing the ACCEPTED-status check, or adding a third consumer) can silently diverge from
the other and only a single, specific test would notice.
**Fix:** Extract the four lines into one shared static helper (e.g.
`ScheduleOutputService.restWaiverCounts(Schedule)` returning a small record/pair, or a package-
private static method both classes call) so there is exactly one place this is computed, matching
the "exactly one implementation" discipline the phase already applies to `RestWaiverLookup`.

## Info

### IN-01: Minimum-rest hours input can silently clear the desk's configured value on an invalid intermediate state

**File:** `frontend/src/pages/DeskManagement.tsx:21-25` (`hoursStringToMinutes`) and `:122-127`
(`handleUpdate`)
**Issue:** `hoursStringToMinutes` does not guard against `Number(trimmed)` returning `NaN` for a
non-empty-but-unparseable string, and the inline range check at lines 219-222 only tests
`parsed < 0 || parsed >= 24`, which is `false` for `NaN` (so no warning renders). If
`editedMinimumRestMinutes` evaluates to `NaN`, `desks.setMinimumRest(editingId, NaN)` is still
called; `JSON.stringify({ minimumRestMinutes: NaN })` serialises `NaN` as `null`, so the request
silently clears the desk's minimum rest instead of being rejected or ignored. In practice the
`<input type="number">` element sanitises most invalid keystrokes to an empty string before
`onChange` fires, which maps to the intentional "clear" path (`hoursStringToMinutes("")` → `null`)
rather than `NaN`, so this is unlikely to be reachable through normal browser typing — but it is
not guarded in code, and clearing a safety-relevant rest floor silently (no toast, no confirmation)
is a higher-consequence failure mode than most "harmless NaN" cases.
**Fix:** Treat a non-empty, non-finite parse result as an error rather than silently forwarding it,
e.g. `if (trimmed === '') return null; const n = Number(trimmed); if (!Number.isFinite(n)) throw new Error(...)`
(or skip the save and show a toast), so a malformed value can never reach the "clear the setting"
code path.

---

_Reviewed: 2026-10-04T04:31:15Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
