---
phase: 15-shift-envelope-breaks-library-generation
plan: 07
subsystem: api
tags: [spring-boot, jpa, react, typescript, scheduling]

# Dependency graph
requires:
  - phase: 15-03
    provides: AgentShiftAssignment (second @PlanningEntity), ShiftBandPair problem fact, solverConfig.xml two-phase CH
  - phase: 15-05
    provides: frontend/src/api/client.ts ShiftTemplate bands array, ShiftLibrarySuggestion types
provides:
  - AgentShiftAssignmentRepository — tenant/desk-scoped reads of accepted shift rows
  - ScheduleService.acceptSchedule denormalised accept-time shift snapshot (D-07), immune to later template edits
  - ScheduleService.loadSnapshotData reload of an accepted schedule's shift rows, and derived schedulingMode (P-32)
  - ScheduleDetailResponse.schedulingMode and AgentScheduleEntry.shift (ShiftDescriptor), populated by one builder
  - ScheduleResults.tsx AgentAllocationTab shift-grouped rendering for SHIFT-mode desks, byte-identical SLOT rendering
affects: [15-08-ch-ordering-benchmark, phase-15-verification, phase-15-uat]

actuals:
  tokens: 15700
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Accept-time denormalisation (D-07) — the accepted AgentShiftAssignment row copies scalar values (template name, start, end, band offset/duration) rather than an FK to the live template, mirroring the control flow (not the field shape) of the existing Timeslot/StaffingRequirement snapshot-and-remap block"
    - "schedulingMode derived from accept-time snapshot presence, never a live desk read (P-32) — an accepted schedule has no persisted mode column, so loadSnapshotData infers SHIFT/SLOT from whether it just loaded any shift rows (D-05's one-row-per-working-agent-day guarantee makes this unambiguous)"
    - "One builder, two paths — ScheduleOutputService.buildAgentSchedule.resolveShiftDescriptor reads the transient shiftBandPair when present (in-memory) and falls back to the denormalised scalar columns (accepted), so the frontend cannot distinguish an in-memory schedule from an accepted one"
    - "Frontend mode branch placed after the pre-existing shared per-date computations (dayEntries/slots/sortedEntries/agentsPerSlot/unfilledPerSlot), not before them — mirrors the backend's own 'first operation after the existing joins' mode-gating discipline (RESEARCH.md Pitfall 3) so the slots column set is computed once per date and shared across every group, never duplicated"

key-files:
  created:
    - src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  modified:
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
    - frontend/src/api/client.ts
    - frontend/src/pages/ScheduleResults.tsx

key-decisions:
  - "schedulingMode has no persisted column — Schedule's own transient field (already set by SolverService for in-memory schedules) is reused; for an accepted schedule, loadSnapshotData derives it from whether the just-loaded shift-row snapshot is empty or not, since D-05 guarantees exactly one row per working agent-day on a SHIFT-mode accept and zero on a SLOT-mode accept"
  - "Grouping key is sourceTemplateId falling back to templateName (P-31), matching the plan's explicit instruction over the UI-SPEC's simpler templateName-only prose — two templates with identical envelope times but different ids render as two distinct groups"
  - "The SLOT-mode render branch is placed after the pre-existing shared per-date computations rather than as the literal first statement of the per-date block, so the slots column set stays computed once per date (Component Spec §4's own requirement) instead of being duplicated per branch — this reading is corroborated by RESEARCH.md's own backend guidance to add the mode filter 'as the very first operation AFTER the existing joins'"
  - "The grouped variant's per-agent row rendering is fully duplicated rather than extracted into a shared helper function, to keep the SLOT branch's JSX literally byte-for-byte unchanged and avoid any ambiguity about 'no restructuring whatsoever' for that branch"

patterns-established:
  - "resolveShiftDescriptor(AgentShiftAssignment) — the single conversion point from either representation of an assigned shift (transient pair or denormalised scalars) to the response DTO shape; any future reader of shift data should call this rather than re-deriving it"

requirements-completed: [ENVL-10, XCUT-01]

coverage:
  - id: D1
    description: "An accepted shift-mode schedule denormalises the resolved envelope (template name, start, end, band offset/duration, nullable sourceTemplateId) onto the accepted AgentShiftAssignment row, immune to a later template edit"
    requirement: "XCUT-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#acceptSchedule_shiftMode_recordedEnvelopeSurvivesALaterTemplateEdit"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#acceptSchedule_shiftMode_persistedRowsCarryTheSavedScheduleIdNotTheInMemoryOne"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#acceptSchedule_slotMode_writesZeroShiftRowsAndLeavesAssignmentSnapshotUnchanged"
        status: pass
    human_judgment: false
  - id: D2
    description: "ScheduleOutputService.buildAgentSchedule is the single builder populating schedulingMode-consistent shift descriptors for both the in-memory path (transient shiftBandPair) and the accepted path (denormalised columns), and returns null shift on every entry for a SLOT-mode schedule"
    requirement: "XCUT-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#buildAgentSchedule_shiftModeInMemory_returnsShiftDescriptorOnEveryWorkingAgentDay"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#buildAgentSchedule_afterAcceptAndReload_returnsIdenticalDescriptorReadFromDenormalisedRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#buildAgentSchedule_slotMode_returnsNullShiftOnEveryEntryAndOtherFieldsUnchanged"
        status: pass
    human_judgment: false
  - id: D3
    description: "ScheduleDetailResponse.schedulingMode reports SHIFT/SLOT correctly for both in-memory and accepted schedules, derived from the schedule's own recorded mode, never a live desk read"
    requirement: "XCUT-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#getScheduleDetail_inMemoryShiftModeSchedule_reportsSchedulingModeShift"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#getScheduleDetail_inMemorySlotModeSchedule_reportsSchedulingModeSlot"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#getScheduleDetail_acceptedShiftModeSchedule_derivesSchedulingModeFromShiftRowPresence"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java#getScheduleDetail_acceptedSlotModeSchedule_reportsSchedulingModeSlot"
        status: pass
    human_judgment: false
  - id: D4
    description: "The Agent Allocation view groups agents under their assigned shift on a shift-scheduled desk (grouped by template identity, sorted by start time then name, null bucket last, header naming the shift/times/headcount), while a slot-scheduled desk renders exactly as it did before this phase"
    requirement: "ENVL-10"
    verification:
      - kind: other
        ref: "cd frontend && npm run build"
        status: pass
    human_judgment: true
    rationale: "This repo has no frontend test framework (standing project decision since Phase 13). The build proves the code compiles and the branch/partition/sort logic type-checks; the actual visual grouping, header content, and byte-identical slot-mode rendering require a running app against a real solved shift-mode and slot-mode desk — this plan's own human-check block, routed to end-of-phase UAT."
  - id: D5
    description: "An accepted shift-mode schedule reopened after a template edit still shows the group headers naming the envelope the agents actually worked, not the edited template"
    requirement: "ENVL-10"
    verification: []
    human_judgment: true
    rationale: "Backend proof (D1) covers the persisted data; the end-to-end visual claim (reopen an accepted schedule in the browser and read the group header) needs a live app and is this plan's own human-check, routed to end-of-phase UAT."

duration: ~50min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 7: Accepted-Schedule Shift Snapshot & Agent Allocation Grouping Summary

**An accepted shift-mode schedule denormalises what each agent actually worked onto the accepted row (immune to later template edits), one builder exposes that envelope through `schedulingMode` and a per-entry `ShiftDescriptor` on the schedule detail response, and the Agent Allocation view groups agents under their assigned shift while a slot-scheduled desk renders byte-identical to before.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 3
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments

- `AgentShiftAssignmentRepository` — tenant/desk-scoped reads mirroring `AgentAssignmentRepository`'s naming convention (T-15-26)
- `ScheduleService.acceptSchedule` denormalises each accepted shift row's resolved envelope (template name, start, end, band offset/duration, nullable `sourceTemplateId`) rather than copying or FK-ing the live template (D-07) — discharging Phase 14's D-09 accept-time snapshot obligation for the new entity
- `ScheduleService.loadSnapshotData` reloads an accepted schedule's shift rows through the new repository and derives `schedulingMode` from their presence, never from a live desk read (P-32)
- `ScheduleDetailResponse.schedulingMode` (non-nullable) and `AgentScheduleEntry.shift` (nullable `ShiftDescriptor`), populated by the one existing `ScheduleOutputService.buildAgentSchedule` builder for both the in-memory and accepted paths
- `ScheduleResults.tsx` `AgentAllocationTab` groups agents under their assigned shift on shift-scheduled desks (header bar naming the shift, its times and headcount; groups sorted by start time then template name; "No shift assigned" bucket always last; Total/Unfilled rows render exactly once, after all groups) while a slot-scheduled desk's rendering is untouched
- Full backend suite green: 469 tests, 0 failures. Frontend build green with `noUnusedLocals` enabled.

## Task Commits

1. **Task 1: An accepted shift survives a template edit — the D-07 denormalised snapshot, end to end** - `6da0299` (feat)
2. **Task 2: The shift reaches the response — schedulingMode and a per-entry shift descriptor** - `0dab787` (feat)
3. **Task 3: Agent Allocation groups by shift, and slot desks render byte-identically** - `924df7f` (feat)

## Files Created/Modified

- `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` - tenant/desk-scoped reads, relations-fetching variant for the output path
- `src/main/java/com/wfm/service/ScheduleService.java` - `acceptSchedule` accept-time shift snapshot write; `loadSnapshotData` shift-row reload + `schedulingMode` derivation; `buildDetailResponse` sets `schedulingMode`
- `src/main/java/com/wfm/service/ScheduleOutputService.java` - `buildAgentSchedule` builds a `(agentId, date) → ShiftDescriptor` lookup via `resolveShiftDescriptor`, covering both the in-memory and accepted representations
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` - `schedulingMode` field; `ShiftDescriptor` record; `AgentScheduleEntry.shift` component
- `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` - 13 tests across accept-time snapshot survival, schedule-id remapping, SLOT-mode zero-rows, `buildAgentSchedule` descriptor resolution (in-memory / accepted / SLOT), and `schedulingMode` reporting (in-memory and accepted, both modes)
- `frontend/src/api/client.ts` - `ShiftDescriptor` interface; `ScheduleDetail.schedulingMode`; `AgentScheduleEntry.shift`
- `frontend/src/pages/ScheduleResults.tsx` - `AgentAllocationTab` mode branch and grouped-variant rendering

## Decisions Made

See `key-decisions` in frontmatter — `schedulingMode`'s derive-from-snapshot-presence mechanism (no new persisted column), the `sourceTemplateId`-falling-back-to-`templateName` grouping key (P-31), the mode branch's placement after the shared per-date computations (preserving "slots computed once per date"), and the deliberate row-rendering duplication in the grouped branch to keep the SLOT branch's JSX untouched.

## Deviations from Plan

None - plan executed exactly as written. `schedulingMode`'s lack of a persisted column required a design decision (derive from shift-row presence at reload) since the plan's own `files_modified` list carries no migration file for this plan; this is a direct, non-architectural implementation of P-32's stated constraint ("the schedule's own recorded mode... never a live desk read"), not a deviation from it.

## Issues Encountered

None. The full `./gradlew test` run needed ~8.5 minutes (469 tests including the pre-existing solver benchmark/diagnostic suite); no failures.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ScheduleDetailResponse.schedulingMode` and `AgentScheduleEntry.shift` are stable response fields any later plan (Phase 16/17 reporting, drift report) can read without re-deriving shift data.
- The Agent Allocation grouping (ENVL-10) and accept-time snapshot (D-07/XCUT-01) human-check items are routed to end-of-phase UAT, per this plan's own `<verification>` section — no frontend test framework exists in this repo (standing decision since Phase 13).
- `resolveShiftDescriptor` is available as the one place any future backend surface should read an assigned shift from, rather than re-deriving the in-memory-vs-accepted branch.

## Self-Check: PASSED

- All created files verified present on disk: `AgentShiftAssignmentRepository.java`, `ScheduleServiceShiftSnapshotTest.java`.
- All three task commits verified present in `git log`: `6da0299`, `0dab787`, `924df7f`.
- Plan `<verification>` re-run: `./gradlew test` green (469 tests, 0 failures, 0 errors across all `build/test-results/test/*.xml`); `cd frontend && npm run build` succeeds with `noUnusedLocals` enabled (confirmed in `frontend/tsconfig.json`, unchanged this plan).
- `must_haves.artifacts` confirmed: `AgentShiftAssignmentRepository.java` contains `findByTenantIdAndDeskIdAndScheduleId`; `ScheduleServiceShiftSnapshotTest.java` is 553 lines (min_lines: 70).
- `must_haves.key_links` confirmed: `ScheduleService.java` constructs `AgentShiftAssignment` in `acceptSchedule`; `ScheduleOutputService.java` sets `AgentScheduleEntry.shift`; `ScheduleResults.tsx` reads `schedule.schedulingMode` and `entry.shift`.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
