---
phase: 21-overnight-shift-templates
plan: 02
subsystem: api
tags: [java, spring, jpa, desk-anchor, shift-templates, typescript]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-01)
    provides: "ShiftTemplateService.validate's forward-interval refusal and DayWindow.dayStart() accessor, which this plan's D-03 refusal reuses rather than duplicating"
  - phase: 20-solver-business-date-correctness
    provides: "DeskService.setDayStart's 15-minute-boundary save gate and its unconditional ACCEPTED-schedule refusal, which this plan extends with a fifth refusal"
provides:
  - "DeskService.setDayStart's fifth refusal (OVNT-01/D-03): rejects a proposed day start that would strand an already-stored shift template, naming the template and its effectiveFrom"
  - "DeskService.dayStartTilingWarning (OVNT-01/D-05): non-blocking advisory, called only after a successful setDayStart, naming the proposed value and the desk's live generation increment when they do not tile"
  - "DeskService.dayStartLocksByDeskId: one batch query resolving every desk's permanent ACCEPTED-schedule lock for the whole tenant"
  - "DeskResponse carries dayStartLockedByScheduleId/dayStartLockedPeriodStart/dayStartLockedPeriodEnd/dayStartTilingWarning; DeskController discloses them through every response path without a second ScheduleRepository access point"
  - "The Desk TypeScript interface carries the same four fields, optional, ready for 21-08 to consume"
affects: [21-08]

# Actuals (#2632)
actuals:
  tokens: 7584
  tasks: 3
  commits: 5
  plan_head_before: d162d4431da3bdc4d083fb626234cf3f1753c843
  plan_head_after: 2d886cfea6e1c81bc50b84e5e63a61004395771e

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A refusal and an advisory that must never disagree with another rule share that rule's own predicate call (anchoredIsForwardWithinDay, getLiveBounds) rather than a hand-written re-implementation — same discipline as 21-01's D-02 and the pre-existing D-08 pattern in ShiftLibraryValidationService"
    - "A batch per-tenant finder (findByTenantIdAndStatusOrderByCreatedAtDesc) feeding a first-encountered-per-key map, reused by every single-desk call site instead of adding a second per-desk finder shape — avoids an N+1 query pattern across listDesks while keeping getDesk/createDesk/updateDesk/switchSchedulingMode uniform"
    - "A read-only advisory method is called by the controller strictly AFTER the write it advises on succeeds, never from inside the write path itself, which is what makes 'the save happened, the advisory never blocks' true by construction"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/DeskService.java
    - src/main/java/com/wfm/dto/DeskResponse.java
    - src/main/java/com/wfm/controller/DeskController.java
    - src/main/java/com/wfm/repository/ScheduleRepository.java
    - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
    - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
    - src/test/java/com/wfm/service/UsualShiftWritePathTest.java
    - frontend/src/api/client.ts

key-decisions:
  - "D-03's stranded-template refusal and D-05's tiling advisory both reuse existing predicates (DayWindow.anchoredIsForwardWithinDay, TimeslotGeneratorService.getLiveBounds) rather than hand-written comparisons, so the save path and this plan's new checks can never drift apart"
  - "dayStartLocksByDeskId resolves one query for the WHOLE tenant and keeps the first (latest createdAt) Schedule per deskId; DeskController's single-desk endpoints call it too (never a second per-desk finder shape), accepting one extra per-request query there in exchange for one shared code path"
  - "dayStartTilingWarning is called by the controller strictly after setDayStart returns successfully, never from inside DeskService.setDayStart itself — enforced by its own method boundary, not by caller discipline"
  - "Every @DataJpaTest context that @Imports DeskService (DeskServiceSchedulingModeTest, UsualShiftWritePathTest) also now imports TimeslotGeneratorService, since DeskService's constructor grew that dependency"

patterns-established:
  - "The day-start lock disclosure fields are additive-only on DeskResponse and the Desk TypeScript interface, following the same lands-now-consumed-later sequencing Phases 18-20 used for similar DTO widenings"

requirements-completed: [OVNT-01]

coverage:
  - id: D1
    description: "A re-anchor that would strand a stored template is refused by name; one that would not is allowed; a desk with zero stored templates accepts any valid change"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "DeskServiceDayStartTest#setDayStart_wouldStrandStoredTemplate_throwsConflictNamingTemplate"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#setDayStart_templateStillForwardAtProposedAnchor_persists"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#setDayStart_noStoredTemplates_acceptsAnyValidChange"
        status: pass
    human_judgment: false
  - id: D2
    description: "The ACCEPTED-schedule refusal still wins when a desk fails both rules, naming the blocking schedule rather than the stranded template"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "DeskServiceDayStartTest#setDayStart_failsBothAcceptedScheduleAndStrandedTemplate_refusedForAcceptedSchedule"
        status: pass
    human_judgment: false
  - id: D3
    description: "A non-tiling day start saves and returns an advisory; a tiling one and an ungenerated desk return none; the save happens regardless"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "DeskServiceDayStartTest#dayStartTilingWarning_doesNotTileLiveThirtyMinuteIncrement_returnsAdvisory_saveStillHappened"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#dayStartTilingWarning_tilesLiveThirtyMinuteIncrement_returnsEmpty"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#dayStartTilingWarning_deskHasNoLiveTimeslots_returnsEmptyForAnyValue"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every desk response discloses its permanent lock (id, period) when it has one and null when it does not; the disclosed id matches the id the setDayStart refusal names for the same desk; the write response carries the tiling advisory"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "DeskServiceDayStartTest#listDesks_oneLockedOneNot_lockFieldsPopulatedOnlyOnLockedDesk"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#listDesks_disclosedLockId_matchesTheIdNamedByTheSetDayStartRefusal"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#controller_setDayStart_nonTilingValue_returns200WithAdvisoryAndSubmittedValue"
        status: pass
      - kind: unit
        ref: "DeskServiceDayStartTest#controller_setDayStart_tilingValue_returns200WithNullAdvisory"
        status: pass
    human_judgment: false
  - id: D5
    description: "No lock is resolvable outside the caller's tenant: another tenant's desk never appears in GET /desks, and no other tenant's schedule id can appear in any lock field"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "DeskServiceDayStartTest#listDesks_tenantScoped_otherTenantDeskAndScheduleNeverDisclosed"
        status: pass
    human_judgment: false
  - id: D6
    description: "The supported 15/30/60-minute increment set is byte-identical to today; the Desk TypeScript contract carries the four new fields and type-checks clean; the existing day-start mutation call is untouched"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "grep 'incrementMinutes must be 15, 30, or 60' TimeslotGeneratorService.java (count 1, unchanged)"
        status: pass
      - kind: other
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
    human_judgment: false

duration: 65 min
completed: 2026-10-02
status: complete
---

# Phase 21 Plan 02: Day-Start Reachability Backend Support Summary

**A desk's day-start control can now be made fully editable without silently breaking a stored template, missing a tiling warning, or leaving the frontend unable to render the permanent accepted-schedule lock honestly.**

## Performance

- **Duration:** 65 min
- **Started:** 2026-10-02T21:49:00Z (approx.)
- **Completed:** 2026-10-02T22:05:00Z (approx.)
- **Tasks:** 3 of 3
- **Files modified:** 8

## Accomplishments
- `DeskService.setDayStart` gained a fifth, named refusal (D-03): changing a desk's day start is rejected when the proposed anchor would leave an already-stored shift template no longer running forward against it, naming the stranded template(s) by name and effective-from date. The refusal shares `DayWindow.anchoredIsForwardWithinDay` — the exact predicate `ShiftTemplateService.validate` uses on save — so the two paths can never disagree about which templates survive a given anchor.
- The pre-existing ACCEPTED-schedule refusal still wins when a desk fails both rules: it is ordered first and is permanent, so an operator failing both is told about the cause they cannot work around.
- `DeskService.dayStartTilingWarning` (D-05) is a new read-only, non-blocking advisory: called only *after* a successful `setDayStart`, it reports when the saved value will not tile the desk's existing live generation increment — never a second gate, matching the operator's explicit ruling that the 15/30/60-minute increment allowlist and the save-time 15-minute modulus both stay untouched.
- `DeskService.dayStartLocksByDeskId` resolves every desk's permanent ACCEPTED-schedule lock in one batch query per tenant; `DeskResponse` now carries that lock's id and period (null when none exists) plus the write-response-only tiling advisory, and `DeskController` discloses them through every response path (`listDesks`, `getDesk`, `createDesk`, `updateDesk`, `switchSchedulingMode`, `setDayStart`) while holding no direct `ScheduleRepository` reference — tenant scoping stays in the one layer (`DeskService`) that owns it.
- The `Desk` TypeScript interface now carries the same four fields, optional, so 21-08 can consume them without another backend round-trip; `desks.setDayStart`'s call shape is unchanged.

## Task Commits

Each task followed RED -> GREEN TDD discipline (Tasks 1 and 2); Task 3 was `type="auto"` with no TDD cycle:

1. **Task 1: Refuse a re-anchor that would strand a stored template, and advise when it will not tile**
   - `ed3eee7` (test/RED) — failing tests for D-03's refusal/ordering/empty cases and D-05's fires/tiles/empty cases; compile-fails on the missing `dayStartTilingWarning` method
   - `54bd248` (feat/GREEN) — the fifth refusal, `dayStartTilingWarning`, `ShiftTemplateRepository`/`TimeslotGeneratorService` injected into `DeskService`, sibling `@DataJpaTest` contexts updated for the new dependency
2. **Task 2: Disclose the permanent day-start lock and the advisory on the desk response**
   - `931ada7` (test/RED) — failing tests for the lock disclosure, the id-matches-the-refusal invariant, the write-response advisory, and tenant scoping; widens `DeskResponse` and adds `ScheduleRepository`'s batch finder as inert data-shape scaffolding; compile-fails at `DeskController`'s now-short constructor call
   - `5d4148c` (feat/GREEN) — `dayStartLocksByDeskId`, the widened `toResponse(Desk, Schedule, String)` mapper, and every call site threaded through it
3. **Task 3: Land the TypeScript Desk contract the frontend control will consume**
   - `2d886cf` (feat) — the four optional fields on the `Desk` interface

**Plan metadata:** this SUMMARY commit (docs: complete plan)

_TDD discipline: each RED commit was confirmed to fail intentionally before its GREEN commit landed. Tasks 1 and 2's RED failures were compile errors naming the exact missing method/argument-list for the planned API surface (`dayStartTilingWarning` not found; `DeskResponse` constructor arity mismatch) rather than a passing or vacuous suite — the same class of RED this phase's 21-01 plan and 20-09's reachability tests already established as this codebase's convention for adding a new method/record shape._

## Files Created/Modified
- `src/main/java/com/wfm/service/DeskService.java` — the fifth `setDayStart` refusal (D-03), `dayStartTilingWarning` (D-05), `dayStartLocksByDeskId` (D-04); two new constructor dependencies (`ShiftTemplateRepository`, `TimeslotGeneratorService`)
- `src/main/java/com/wfm/dto/DeskResponse.java` — four new nullable components: `dayStartLockedByScheduleId`, `dayStartLockedPeriodStart`, `dayStartLockedPeriodEnd`, `dayStartTilingWarning`
- `src/main/java/com/wfm/controller/DeskController.java` — `toResponse` widened to `(Desk, Schedule, String)`; every endpoint threaded through it; `lockFor` helper reusing the batch finder
- `src/main/java/com/wfm/repository/ScheduleRepository.java` — `findByTenantIdAndStatusOrderByCreatedAtDesc`, the batch twin of the existing per-desk ordered ACCEPTED finder
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` — 12 new tests covering D-03, D-05, D-04 and tenant scoping; new helpers `saveShiftTemplate`, `generateLiveTimeslots`, `saveAcceptedScheduleForTenant`
- `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java` — `@Import` widened to include `TimeslotGeneratorService` (Rule 3, see Deviations)
- `src/test/java/com/wfm/service/UsualShiftWritePathTest.java` — same `@Import` widening (Rule 3, see Deviations)
- `frontend/src/api/client.ts` — `Desk` interface gains four optional fields matching the DTO's new component names exactly

## Decisions Made
- D-03's refusal and D-05's advisory both route through existing predicates (`anchoredIsForwardWithinDay`, `getLiveBounds`) rather than new hand-written logic, so they can never disagree with the save path or generation.
- `dayStartLocksByDeskId` is one tenant-wide batch query, reused by single-desk controller call sites rather than adding a second per-desk finder — one extra query per single-desk request, in exchange for one code path that can never drift from `listDesks`'s.
- `dayStartTilingWarning` is called by the controller only after `setDayStart` returns, never from inside it — enforced structurally, not by discipline.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Widened two sibling `@DataJpaTest` contexts to import `TimeslotGeneratorService`**
- **Found during:** Task 1, GREEN phase (compile step)
- **Issue:** `DeskService`'s constructor grew a new `TimeslotGeneratorService` dependency. `DeskServiceSchedulingModeTest` and `UsualShiftWritePathTest` both `@Import({DeskService.class, ...})` without it, which Spring cannot satisfy — a missing-bean failure, not a logic bug, but one this plan's own change caused.
- **Fix:** added `TimeslotGeneratorService.class` to both `@Import` lists, matching the existing pattern `TimeslotControllerDeskAnchorTest` and `DeskDayStartGenerationReachabilityTest` already used.
- **Files modified:** `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java`, `src/test/java/com/wfm/service/UsualShiftWritePathTest.java`
- **Verification:** both suites green after the change; confirmed again in the full unfiltered `./gradlew test` run.
- **Committed in:** `54bd248` (Task 1 GREEN commit)

---

**Total deviations:** 1 auto-fixed (blocking, scope-consistent with Task 1's own dependency change)
**Impact on plan:** Necessary for the build to compile at all after widening `DeskService`'s constructor. No scope creep — confined to `@Import` lists, no behavioral change to either test file.

## Issues Encountered

None. Both TDD tasks' RED commits were confirmed to fail for the planned reason (compile errors naming the exact missing API surface) before their GREEN commits landed; Task 3 required no TDD cycle per the plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The backend refusal/advisory/disclosure surface this plan adds is complete and tested; `DeskController` now exposes everything `DeskManagement.tsx`'s eventual editable day-start control (21-08) needs to render honestly: the permanent lock, the stranded-template refusal (surfaced via the existing generic error path), and the non-blocking tiling advisory.
- `frontend/src/api/client.ts`'s `Desk` interface already carries the four fields 21-08 will consume — no backend round-trip needed when that plan lands the UI.
- No blockers. The full unfiltered `./gradlew test` run (1237 tests, 0 failures, 0 errors across 197 classes) and `npm --prefix frontend exec -- tsc -b frontend --force` (clean) both passed after all three tasks landed.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-02*

## Self-Check: PASSED
