---
phase: 15-shift-envelope-breaks-library-generation
reviewed: 2026-08-27T11:57:34Z
depth: standard
files_reviewed: 52
files_reviewed_list:
  - build.gradle
  - frontend/src/api/client.ts
  - frontend/src/pages/ScheduleResults.tsx
  - frontend/src/pages/ShiftLibrary.tsx
  - src/main/java/com/wfm/controller/ShiftLibraryValidationController.java
  - src/main/java/com/wfm/controller/ShiftTemplateController.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/dto/ShiftLibrarySuggestionResponse.java
  - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
  - src/main/java/com/wfm/dto/ShiftTemplateRequest.java
  - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
  - src/main/java/com/wfm/model/AgentShiftAssignment.java
  - src/main/java/com/wfm/model/ConstraintWeights.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/model/ShiftTemplate.java
  - src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
  - src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java
  - src/main/java/com/wfm/repository/ShiftTemplateBreakBandRepository.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftTemplateService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/resources/db/migration/V40__shift_template_break_bands.sql
  - src/main/resources/db/migration/V41__agent_shift_assignment.sql
  - src/main/resources/db/migration/V42__add_band_capacity_weight.sql
  - src/main/resources/solverConfig.xml
  - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateBreakBandServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateTracerTest.java
  - src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java
  - src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java
  - src/test/java/com/wfm/solver/BandCapacityConstraintTest.java
  - src/test/java/com/wfm/solver/BreakAwareConstructionTest.java
  - src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java
  - src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java
  - src/test/java/com/wfm/solver/ShiftModeFixtures.java
  - src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java
  - src/test/java/com/wfm/solver/SolverConfigBuildTest.java
  - src/test/java/com/wfm/solver/TestConstructionHeuristicPhases.java
findings:
  critical: 3
  warning: 1
  info: 0
  total: 4
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-08-27T11:57:34Z
**Depth:** standard
**Files Reviewed:** 52
**Status:** issues_found

## Summary

Phase 15 promotes a shift template's single break offset into N break bands, introduces
`AgentShiftAssignment` as a second Timefold planning entity, and adds suggested-library
generation and accept-time snapshotting. The areas flagged as risk-prone in the phase context
were verified directly:

- **Constraint stream ordering** (`ScheduleConstraintProvider`): all Phase-15-added constraints
  (`shiftEnvelopeCompliance`, `bandCapacity`, `breakClustering`'s on-break side) correctly lead
  with the empty-in-SLOT-mode `AgentShiftAssignment` stream and gate `SchedulingMode.SHIFT`
  before joining `AgentAssignment`/`Timeslot`. No regression of the previously-fixed ordering
  defect was found.
- **Migration/entity agreement**: `V40`/`V41`/`V42` are genuinely covered by
  `MigrationEntityConsistencyTest` (all three tables appear in `DECLARED_TABLES`, including the
  V40-dropped-columns assertion). `V40`'s data fan-out is safe — `break_duration_minutes` is
  `NOT NULL DEFAULT 0` since V39, so the `WHERE break_duration_minutes > 0` predicate cannot
  silently drop a row via a NULL comparison.
- **`build.gradle`**'s system-property passthrough forwards exactly `wfm.benchmark`, not the
  whole `-D` set — confirmed safe.

However, direct tracing of the shift-envelope lifecycle mechanism (the effective-date range,
which `ShiftTemplate`'s own javadoc calls "the template's ENTIRE lifecycle mechanism") surfaced
a genuine correctness gap that is not covered by any test, plus two data-integrity gaps in the
accept/delete lifecycle of `AgentShiftAssignment`. These are detailed below.

## Critical Issues

### CR-01: `SolverService.startSolve` never excludes UPCOMING shift templates, so a not-yet-effective template is assignable immediately

**File:** `src/main/java/com/wfm/service/SolverService.java:278-282`
**Issue:**

```java
List<ShiftTemplate> liveShiftTemplates = desk.getSchedulingMode() == SchedulingMode.SHIFT
        ? shiftTemplateRepository.findByTenantIdAndDeskId(tenantId, deskId).stream()
                .filter(t -> t.getEffectiveTo() == null || !t.getEffectiveTo().isBefore(LocalDate.now()))
                .toList()
        : List.of();
```

This filter only excludes **retired** templates (`effectiveTo` before today). It does not
exclude **UPCOMING** templates — those whose `effectiveFrom` is in the future. `ShiftTemplate`'s
own class javadoc states the effective date range is "the template's ENTIRE lifecycle
mechanism," and `ShiftTemplateController.eraStatus` (and the frontend `ShiftLibrary.tsx`, which
renders an "Upcoming" badge) confirm UPCOMING is a first-class, expected state — operators are
explicitly invited to create a template that becomes effective later.

Nothing downstream ever checks the effective range against a specific agent-day either:
`ShiftBandPair.covers(Timeslot ts)` (`ShiftBandPair.java:31`) takes no date argument and checks
only envelope/break times; `AgentShiftAssignment.getEligibleShiftBandPairs()`
(`AgentShiftAssignment.java:149-161`) filters candidate pairs purely by net-hours equality, not
by date; and no constraint in `ScheduleConstraintProvider` reads `effectiveFrom`/`effectiveTo`.
So a template created today with `effectiveFrom` three months out is treated as fully live for
every agent-day in the current solve the moment it is saved, and — more generally — a schedule
period that straddles a template's `effectiveFrom`/`effectiveTo` boundary can assign that
template's shift on dates outside its declared era. `SolverServiceShiftAssignmentTest` confirms
this: it exercises `buildShiftBandPairs`/`buildShiftAssignments` directly and never asserts any
effective-range exclusion, because the (missing) filtering would have to happen one level up, in
`startSolve`, which has no test coverage for this case at all.

**Fix:** Filter `liveShiftTemplates` (or, better, filter per agent-day when building
`ShiftBandPair`s / `AgentShiftAssignment`s) by both ends of the effective range against the
relevant date, e.g.:

```java
.filter(t -> !t.getEffectiveFrom().isAfter(schedule.getPeriodEndDate())
        && (t.getEffectiveTo() == null || !t.getEffectiveTo().isBefore(schedule.getPeriodStartDate())))
```

and, since a single schedule period can still straddle a template's boundary, thread the
per-date check into `ShiftBandPair.covers` or `AgentShiftAssignment.getEligibleShiftBandPairs()`
so an agent-day outside a template's effective range can never select it, mirroring
`ShiftLibraryValidationService.withinEffectiveRange`, which already implements exactly this
predicate for the validation/suggestion paths.

### CR-02: Accepted-schedule `schedulingMode` is inferred from an empty shift snapshot, permanently mislabeling a SHIFT-mode schedule that assigned no shifts

**File:** `src/main/java/com/wfm/service/ScheduleService.java:410-418`
**Issue:**

```java
schedule.setSchedulingMode(shiftAssignments.isEmpty() ? SchedulingMode.SLOT : SchedulingMode.SHIFT);
```

`loadSnapshotData` derives the mode of an *accepted* (DB-persisted) schedule solely from whether
any `agent_shift_assignment` rows exist for it — there is no persisted mode column (by design,
per the comment). But `ScheduleService.acceptSchedule` (`ScheduleService.java:310-330`) only
writes a row per `AgentShiftAssignment` **whose `shiftBandPair` is non-null**:

```java
for (AgentShiftAssignment shiftAssignment : schedule.getShiftAssignments()) {
    ShiftBandPair pair = shiftAssignment.getShiftBandPair();
    if (pair == null) continue;
    ...
}
```

`acceptSchedule` is reachable for any schedule in `COMPLETED` or `STOPPED` status
(`ScheduleService.java:210-214`) — it does not require feasibility, nor that any shift was
actually assigned. A SHIFT-mode solve that is stopped early (before the construction heuristic
assigns any `shiftBandPair`), or one where no live shift template's net hours match any agent's
contracted hours, can legitimately reach `COMPLETED`/`STOPPED` with **zero** shift-band
assignments. Accepting such a schedule persists zero `agent_shift_assignment` rows, and on every
subsequent load `loadSnapshotData` will report `schedulingMode = SLOT` for what was actually a
SHIFT-mode solve. This corrupts an otherwise-immutable historical record and is not merely
cosmetic: `ScheduleDetailResponse.schedulingMode` (`ScheduleService.java:528`) feeds directly
into `frontend/src/pages/ScheduleResults.tsx:422` (`if (schedule.schedulingMode !== 'SHIFT')`),
which selects an entirely different table-rendering branch (ungrouped vs. shift-grouped), so the
mislabeling is user-visible, not just an internal bookkeeping detail.

**Fix:** Persist the mode explicitly rather than inferring it. The simplest fix is a
`scheduling_mode` column on `agent_assignment`/a new snapshot table, or a `scheduling_mode`
column on `schedule` itself written at accept time from `schedule.getSchedulingMode()` (already
available in-memory, per `Schedule.getScheduleConfig()`). Inferring state from a collection that
can legitimately be empty for the state it's supposed to signal is the same "two facts that can
disagree" shape this project's own commit history (D-01's migration note) says it has already
been burned by twice.

### CR-03: `ScheduleService.deleteSchedule` never deletes the accepted schedule's `AgentShiftAssignment` rows — orphaned data on every SHIFT-mode delete

**File:** `src/main/java/com/wfm/service/ScheduleService.java:346-362`
**Issue:**

```java
@Transactional
public void deleteSchedule(UUID deskId, UUID scheduleId) {
    ...
    agentAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
    staffingRequirementRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
    timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
    scheduleRepository.delete(schedule);
}
```

This deletes `agent_assignment`, `staffing_requirement`, and `timeslot` rows for the schedule,
then the `schedule` row itself — but never touches `agent_shift_assignment`. Confirmed by reading
`AgentShiftAssignmentRepository` in full (`AgentShiftAssignmentRepository.java:1-35`): it exposes
only two `find*` methods, no `deleteBy*` method exists at all. `agent_shift_assignment.schedule_id`
carries no foreign key to `schedule` (`V41__agent_shift_assignment.sql:16-29`), so deleting the
`schedule` row does not cascade and does not error — it simply leaves every shift-envelope row for
that schedule permanently orphaned in the database, unreachable by any query that joins through a
live `Schedule` row. This is a genuine, silent data-integrity bug: deleting an accepted
SHIFT-mode schedule is supposed to remove that schedule's data, and it partially does not.

**Fix:** Add a tenant/desk/schedule-scoped delete method to `AgentShiftAssignmentRepository`,
mirroring `AgentAssignmentRepository`'s, and call it from `deleteSchedule`:

```java
// AgentShiftAssignmentRepository
void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

// ScheduleService.deleteSchedule
agentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
```

## Warnings

### WR-01: `ShiftTemplateBreakBandRepository.deleteByShiftTemplate_Id` is the only unscoped write in an otherwise fully tenant-scoped repository

**File:** `src/main/java/com/wfm/repository/ShiftTemplateBreakBandRepository.java:26-28`
**Issue:** Every other method on this repository (and, per its own class javadoc, every
repository in the codebase) takes `tenantId` explicitly. `deleteByShiftTemplate_Id(UUID)` does
not, relying entirely on the caller (`ShiftTemplateService.replaceBands`) having already resolved
the template through a tenant-scoped read before calling it. That caller does so correctly today
(`createShiftTemplate`/`updateShiftTemplate` both load/save the template through a
tenant-verified path first), so this is not currently exploitable — but it is a footgun: any
future caller of `deleteByShiftTemplate_Id` that skips the tenant check would silently delete
another tenant's break bands given only a guessed/enumerated template UUID, with no compiler or
repository-layer signal that a tenant check is required.
**Fix:** Add a tenant-scoped overload (`deleteByTenantIdAndShiftTemplate_Id`) and route the
existing caller through it, so the unscoped method can be removed entirely and the "every method
takes tenantId" invariant this repository's own javadoc claims becomes actually true rather than
true-by-caller-discipline.

---

_Reviewed: 2026-08-27T11:57:34Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
