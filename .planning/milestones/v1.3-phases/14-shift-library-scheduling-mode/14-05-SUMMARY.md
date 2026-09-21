---
phase: 14-shift-library-scheduling-mode
plan: 05
subsystem: api
tags: [jpa, spring-boot, validation, tdd, scheduling-mode, mode-switch]

# Dependency graph
requires:
  - phase: 14 (Plan 01)
    provides: V39 migration, SchedulingMode enum, Desk.schedulingMode (SLOT default)
  - phase: 14 (Plan 04)
    provides: ShiftLibraryValidationService.requireShiftModeReady(deskId) — the shared coverage/hours gate, D-08's first caller
provides:
  - "DeskService.switchSchedulingMode(UUID, SchedulingMode) — the 409 in-flight guard (symmetric, P-22), the SLOT-to-SHIFT-only coverage gate (D-08's second caller), the SLOT-to-SLOT/SHIFT-to-SHIFT no-op (P-23), and the single-column write that is MODE-04's whole proof"
  - "PUT /api/v1/desks/{deskId}/scheduling-mode — the MODE-02 operator-facing switch surface"
  - "DeskResponse.schedulingMode — every desk response (list, create, get, update) now carries the mode (XCUT-01)"
affects: [14-06-shift-library-ui, 15-shift-envelope-coupling]

actuals:
  tokens: 6558
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "P-21's 409 idiom: ConflictException (already 409 CONFLICT) reading InMemoryScheduleStore.getByDeskId — zero new exception class, zero new ConcurrentHashMap, zero new GlobalExceptionHandler mapping. BambooRefreshService's RefreshInProgressException/ConcurrentHashMap idiom exists for a genuinely different reason (nothing else tracks a refresh in flight) and was deliberately not copied."
    - "P-22: the RUNNING guard is symmetric (applies to both switch directions) even though the coverage gate is one-way (SLOT-to-SHIFT only) — D-13's hazard (an unauditable accept into a desk now flagged the other model) is symmetric even though D-12's 'ungated' refers only to the coverage validation."
    - "P-23: switching to the current mode is an early-return no-op — no guard, no validation, no write. Proven behaviorally (not just by absence of a call) by registering a RUNNING solve in the store and confirming a same-mode switch still succeeds, since consulting the store would have thrown."
    - "P-24: guard evaluated first (cheaper, more specific refusal), then the coverage gate (only for SHIFT), then the single-column write, all inside one @Transactional method — the narrowed-not-eliminated TOCTOU window T-14-21 records honestly."

key-files:
  created:
    - src/main/java/com/wfm/dto/SchedulingModeRequest.java
    - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
  modified:
    - src/main/java/com/wfm/service/DeskService.java
    - src/main/java/com/wfm/controller/DeskController.java
    - src/main/java/com/wfm/dto/DeskResponse.java

key-decisions:
  - "P-21 adopted verbatim: D-13's 409 is ConflictException reading InMemoryScheduleStore, not a new RefreshInProgressException plus a new ConcurrentHashMap. DeskService already constructor-injects InMemoryScheduleStore (verified: used in deleteDesk) and already throws ConflictException, so this is zero new surface area."
  - "P-22 adopted verbatim: the in-flight guard applies to BOTH switch directions, while the coverage gate applies only SLOT-to-SHIFT. Recorded explicitly per the plan's own instruction, since D-12 ('ungated') and D-13 ('409 guard') read as if they conflict and a later reader will ask — they don't: D-12 is about the coverage validation only."
  - "P-23 adopted verbatim: switching to the current mode returns immediately with no guard, no validation, no write — refusing an idempotent request would make the UI's optimistic toggle behave surprisingly on a double click."
  - "P-24 adopted verbatim: guard, then coverage gate (SHIFT only), then the single-column write, all in one @Transactional method — the coverage gate's own DB reads necessarily sit between the guard and the write for the SHIFT path (the plan's own action steps specify this order), so T-14-21's 'no I/O between them' framing is accurate for the SLOT path and a narrowed-not-eliminated window for the SHIFT path, exactly as the threat register itself concedes."
  - "P-25 adopted verbatim: DeskResponse gains schedulingMode as a trailing fifth component. Confirmed (grep) that DeskController.toResponse is the only production or test call site constructing DeskResponse, so this was a one-site change with no positional-argument breakage elsewhere."

requirements-completed: [MODE-02, MODE-03, MODE-04]

coverage:
  - id: D1
    description: "Switching a desk to shift-scheduled mode is refused, with the coverage validator's PreSolveValidationException propagated unchanged (details array intact), whenever the shared validator finds the library cannot cover demand — the switch is D-08's second caller, never a reimplementation."
    requirement: MODE-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_slotToShift_validatorThrows_propagatesUnchangedAndLeavesModeAtSlot"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_slotToShift_validatorPasses_persistsShiftAndReturnsShift"
        status: pass
    human_judgment: false
  - id: D2
    description: "A mode switch writes exactly one column (desk.scheduling_mode) and touches no Schedule/Timeslot/StaffingRequirement/AgentAssignment row: an ACCEPTED Schedule row and its snapshot Timeslot/StaffingRequirement rows, plus untouched live rows, are byte-identical field-by-field before and after a SLOT-SHIFT-SLOT round trip."
    requirement: MODE-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_roundTrip_leavesAcceptedScheduleAndSnapshotRowsExactlyUnchanged"
        status: pass
      - kind: other
        ref: "grep -vE comment-stripped 'catch (PreSolveValidationException' src/main/java/com/wfm/service/DeskService.java -> 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "SHIFT to SLOT is freely reversible and ungated: no coverage validation runs, no confirmation dialog exists at this layer, and the validator mock is never invoked on the way out — proven even when the validator is stubbed to throw."
    requirement: MODE-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_shiftToSlot_validatorStubbedToThrow_succeedsAndValidatorNeverCalled"
        status: pass
    human_judgment: false
  - id: D4
    description: "A mode switch in either direction is refused with 409 (ConflictException, verbatim in-flight sentence) while the desk has a RUNNING solve; a COMPLETED/STOPPED/FAILED/ACCEPTED solve never blocks; the guard fires before the coverage gate is ever consulted; no remove/terminateEarly/stopSolve/cancel call was added to the method."
    requirement: MODE-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_runningSchedule_slotToShift_throwsConflictWithVerbatimSentence_modeUnchanged"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_runningSchedule_shiftToSlot_alsoThrowsConflict"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_nonRunningSchedule_switchAllowed"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_runningGuardFires_validatorNeverCalled"
        status: pass
      - kind: other
        ref: "grep -Ec 'inMemoryScheduleStore\\.remove|terminateEarly|stopSolve|cancel' src/main/java/com/wfm/service/DeskService.java -> 1 (unchanged, the pre-existing deleteDesk call site)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Switching a desk to the mode it is already in is a no-op that changes nothing and refuses nothing, even with a RUNNING solve registered for the desk."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_slotToSlot_isNoOp_validatorNotCalled_storeNotConsulted_rowUnchanged"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_shiftToShift_isNoOp"
        status: pass
    human_judgment: false
  - id: D6
    description: "The desk list and desk detail responses carry schedulingMode (XCUT-01): the mode-switch endpoint returns a DeskResponse with the new mode, and listDesks carries schedulingMode for every desk with no second request."
    requirement: MODE-02
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#controller_switchSchedulingMode_shiftOnPassingDesk_returnsResponseWithShiftMode"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#controller_listDesks_responsesCarrySchedulingModeForEveryDesk"
        status: pass
    human_judgment: false
  - id: D7
    description: "Switching a desk id belonging to another tenant throws EntityNotFoundException — the desk is loaded through findByIdAndTenantId, never a bare findById."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java#switchSchedulingMode_crossTenantDeskId_throwsEntityNotFound"
        status: pass
    human_judgment: false
  - id: D8
    description: "No production file under src/main/java/com/wfm/solver/, SolverService.java, or ScheduleService.java was touched by this plan, and the full pre-existing backend suite (373 tests) passes unchanged."
    verification:
      - kind: other
        ref: "git diff --name-only -- src/main/java/com/wfm/solver/ src/main/java/com/wfm/service/SolverService.java src/main/java/com/wfm/service/ScheduleService.java (empty)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full existing suite, run twice, both green)"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-08-25
status: complete
---

# Phase 14 Plan 05: Scheduling Mode Switch Summary

**`DeskService.switchSchedulingMode` — a `PUT /api/v1/desks/{deskId}/scheduling-mode` endpoint that gates SLOT-to-SHIFT with the shared `ShiftLibraryValidationService` coverage gate (D-08's second caller), leaves SHIFT-to-SLOT freely reversible (D-12), refuses both directions with 409 while a solve is RUNNING by reusing the existing `ConflictException`/`InMemoryScheduleStore` idiom (D-13, P-21), and writes exactly one column — proven field-by-field never to touch an accepted schedule (MODE-04).**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-25T20:55:57-04:00
- **Completed:** 2026-08-25T21:17:30-04:00
- **Tasks:** 2 (Task 1 `tdd="true"` RED/GREEN pair; Task 2 `auto`)
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- `DeskService.switchSchedulingMode(UUID deskId, SchedulingMode target)`: rejects a null target, loads the desk via `findByIdAndTenantId` (cross-tenant safe), returns immediately on a same-mode switch (P-23), refuses with 409 `ConflictException` while a `RUNNING` schedule is registered for the desk in either direction (D-13, P-22, evaluated first per P-24), calls `ShiftLibraryValidationService.requireShiftModeReady(deskId)` only for SLOT-to-SHIFT and lets `PreSolveValidationException` propagate untouched, then writes exactly one column.
- `ShiftLibraryValidationService` added as a constructor dependency of `DeskService`, appended to the existing parameter list — the current parameter order and every existing `DeskService` test stayed untouched.
- `SchedulingModeRequest` — a plain record, no bean-validation annotations, matching the codebase's existing DTO idiom.
- `DeskResponse.schedulingMode` (P-25, trailing fifth component) populated in `DeskController.toResponse` — every desk response (list, create, get, update) now tells the truth about the desk's mode (XCUT-01).
- `PUT /api/v1/desks/{deskId}/scheduling-mode` (MODE-02) on `DeskController`, delegating directly to the service method.
- 17 tests across default/happy paths, the no-op self-switch (proven behaviorally against a registered RUNNING solve), the 409 guard in both directions with a parameterized non-RUNNING-status pass-through, the coverage-gate refusal and its propagation, tenant isolation, and the MODE-04 accepted-schedule/live-row round-trip invariant, plus two controller-level cases.

## Task Commits

Each task was committed atomically:

1. **Task 1: `DeskService.switchSchedulingMode` — the 409 guard, the coverage gate, and the single-column write** (`tdd="true"`)
   - `90e0dee` (test) — 15 failing tests for `DeskServiceSchedulingModeTest`; confirmed RED via genuine compile failure (`switchSchedulingMode` does not exist on `DeskService`)
   - `584e34d` (feat) — implemented `switchSchedulingMode`, appended `ShiftLibraryValidationService` as a constructor dependency; all 15 tests green, full 373-test suite green (7m 37s)
2. **Task 2: The scheduling-mode endpoint and mode on every desk response** (`auto`)
   - `b7f5b3b` (feat) — `SchedulingModeRequest`, `DeskResponse.schedulingMode`, `PUT /{deskId}/scheduling-mode`, two controller-level test additions; 17 tests total, full suite re-run green (7m 54s)

**Plan metadata:** committed alongside this SUMMARY

_Note: Task 1 is TDD (test → feat); Task 2 is a single `auto` commit extending the same test file._

## Files Created/Modified

- `src/main/java/com/wfm/dto/SchedulingModeRequest.java` — the mode-switch request DTO (created)
- `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java` — 17 tests covering both tasks' `<behavior>` blocks (created)
- `src/main/java/com/wfm/service/DeskService.java` — `switchSchedulingMode` and the `ShiftLibraryValidationService` constructor dependency (modified)
- `src/main/java/com/wfm/controller/DeskController.java` — the mode-switch endpoint and `toResponse`'s new argument (modified)
- `src/main/java/com/wfm/dto/DeskResponse.java` — `schedulingMode` trailing component (modified)

## Decisions Made

- **P-21's chosen 409 idiom, applied exactly as planned:** `ConflictException` (already mapped to 409 `CONFLICT`) reading `InMemoryScheduleStore.getByDeskId(deskId).map(Schedule::getStatus)`. Zero new exception class, zero new `ConcurrentHashMap`, zero new `GlobalExceptionHandler` mapping — confirmed by the comment-stripped grep gate (`ConcurrentHashMap|RefreshInProgressException` count 0).
- **P-22's symmetric-guard reading, applied exactly as planned:** the RUNNING guard fires for both SLOT-to-SHIFT and SHIFT-to-SLOT, while the coverage gate (`requireShiftModeReady`) fires only for SLOT-to-SHIFT. These two decisions read as contradictory on a first pass — D-12 says "ungated," D-13 says "409-guarded" — but they answer different questions: D-12's "ungated" is specifically about the coverage *validation*, and D-13's hazard (accepting a schedule produced under one model into a desk now flagged the other) is symmetric regardless of validation direction. Recorded explicitly per the plan's own instruction, since a later reader will ask.
- **P-24's ordering means the guard-to-write window is not literally I/O-free for the SHIFT path.** The plan's own action steps place the coverage gate (which reads templates, demand, and hours from the database) between the in-flight guard and the final `save`. The threat register's T-14-21 language ("no I/O between them") is accurate for the SLOT path only; for the SHIFT path this is a narrowed-not-eliminated TOCTOU window, exactly as the threat register itself concedes in its "Residual risk accepted and recorded" clause. No deviation from the plan — implemented per its explicit action-step ordering.

## Deviations from Plan

None - plan executed exactly as written. All five `planner_decisions` (P-21 through P-25) were adopted verbatim; no Rule 1–4 auto-fixes were needed.

## Issues Encountered

None — both `./gradlew test` runs (once after Task 1's GREEN commit, once after Task 2's commit) completed `BUILD SUCCESSFUL` on the first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- MODE-02, MODE-03, and MODE-04 are fully implemented and tested: the switch endpoint exists, the coverage gate delegates to 14-04's shared validator with no reimplementation, the 409 guard is symmetric and never stops an in-flight solve, and the accepted-schedule invariant is proven field-by-field across a round trip.
- `DeskResponse.schedulingMode` (XCUT-01) is live in every desk response — `frontend/src/pages/DeskManagement.tsx`'s read-only mode column and `ShiftLibrary.tsx`'s mode toggle (14-CONTEXT.md D-14, not built in this plan) can consume it directly with no new backend work.
- `PUT /api/v1/desks/{deskId}/scheduling-mode` is ready for the frontend to call — a 400 response carries `PreSolveValidationException`'s populated `details` array (coverage/grid/demand/contractedHours keys, per 14-04-SUMMARY.md), a 409 carries the verbatim in-flight sentence from 14-UI-SPEC.md's Copywriting Contract.
- No blockers. Frontend work (`ShiftLibrary.tsx`, the mode toggle UI, `DeskManagement.tsx`'s read-only column) is explicitly out of scope for this plan per `artifacts_this_phase_produces` ("no new frontend symbols") and belongs to a later plan in this phase.

---
*Phase: 14-shift-library-scheduling-mode*
*Completed: 2026-08-25*

## Self-Check: PASSED

All 5 created/modified files confirmed present on disk; all 3 task commits (`90e0dee`, `584e34d`,
`b7f5b3b`) confirmed present in `git log`. Re-ran the plan's `<verification>` block:
`./gradlew test --tests 'com.wfm.service.DeskServiceSchedulingModeTest'` — PASS (17/17);
`./gradlew test` (full suite) — PASS, run twice, `BUILD SUCCESSFUL` in 7m 37s and 7m 54s;
solver/SolverService/ScheduleService diff — empty both times.
