---
phase: 14-shift-library-scheduling-mode
plan: 03
subsystem: api
tags: [jpa, spring-boot, validation, tdd, shift-template]

# Dependency graph
requires:
  - phase: 14 (Plan 01)
    provides: shift_template table, ShiftTemplate entity/repository/service/controller/DTOs (create+list only), the D-11 checkpoint decision (app-level non-overlap enforcement)
provides:
  - "ShiftTemplateService.updateShiftTemplate — the single edit/retire path (P-11); no delete method exists"
  - "Full save-time validation on both create and update, sharing one validate(...) path (T-14-11): name, time ordering, break-within-envelope, non-empty weekday set, effective-range ordering, D-02 grid alignment, D-11 identity + same-name non-overlap"
  - "ShiftTemplateResponse.eraStatus (CURRENT/UPCOMING/PAST, P-13) computed server-side in the controller"
  - "ShiftTemplateService.listShiftTemplates ordered name-ascending then effectiveFrom-descending (P-14), stable across reads"
  - "PUT /api/v1/desks/{deskId}/shift-templates/{id} — single edit/retire endpoint; no DeleteMapping"
affects: [15-shift-envelope-coupling, 16-usual-shift-storage]

actuals:
  tokens: 45000
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Shared private validate(...) path called identically from create and update so the two entry points cannot drift (T-14-11) — mirrors the codebase's existing manual-validation-in-service idiom (SpecializationService), extended with a package-private static alignment helper (isAligned) for the one D-02 grid rule"
    - "In-memory LocalDate.MAX sentinel for open-ended (effectiveTo == null) range-overlap comparison — computed per-call, never persisted, avoiding the two-fields-that-can-disagree trap this project has been burned by twice (audit NEW-1, audit I-1)"
    - "TimeslotGeneratorService supplied to ShiftTemplateServiceTest as @MockitoBean (first use of Spring Boot 3.4's org.springframework.test.context.bean.override.mockito.MockitoBean in this codebase) since getLiveBounds runs a Postgres-only EXTRACT(EPOCH FROM ...) native query that cannot execute under H2"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/controller/ShiftTemplateController.java
    - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
    - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
    - src/test/java/com/wfm/service/ShiftTemplateTracerTest.java

key-decisions:
  - "Range-overlap conflict message uses the literal word 'present' for an open-ended other range's end (e.g. \"...covering 2026-01-01 to present\") — the plan's message template names {from} and {to} but does not specify null-{to} text; 'present' matches 14-UI-SPEC.md's own '{to or \"Present\"}' convention for the same concept, lowercased to read naturally mid-sentence."
  - "ShiftTemplateTracerTest's @Import extended to include TimeslotGeneratorService.class (Rule 3 — blocking fix), required because ShiftTemplateService's constructor now takes TimeslotGeneratorService and @DataJpaTest does not auto-wire @Service beans outside explicit @Import; the real bean resolves fine since its own dependencies (TimeslotRepository, StaffingRequirementRepository, ScheduleRepository, EntityManager) are already available under @DataJpaTest, and getLiveBounds correctly returns Optional.empty() for every desk the tracer test creates (no timeslots), so grid checking is a no-op there (P-10)."
  - "listShiftTemplates sorting deliberately withheld from Task 1's implementation and added only in Task 2 — kept the ordering behavior's RED state real (repository insertion order, not yet sorted) rather than accidentally already-green when Task 2's tests were written, per the plan's own task boundary."

patterns-established:
  - "Package-private static alignment predicate (isAligned) as the single grid-rule implementation, directly unit-testable and reused for all four boundary checks (start/end/breakStart/breakEnd) rather than four inline copies — a pattern any future per-boundary-grid-rule validator in this codebase should copy."

requirements-completed: [SHLB-01, SHLB-02, SHLB-03, SHLB-04]

coverage:
  - id: D1
    description: "Every invalid shift template shape (blank name, inverted times, negative/over-envelope break, empty weekday set, missing/inverted effective range) is rejected at save with a specific, operator-readable, verbatim-asserted message, applied identically on create and update."
    requirement: SHLB-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_startTimeEqualsEndTime_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_negativeBreakValues_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_breakExceedsEnvelope_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_nullWeekdaySet_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_effectiveToBeforeEffectiveFrom_rejected"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-02 grid alignment: a template whose start, end, or break boundaries do not land on the desk's live timeslot grid is rejected with a PreSolveValidationException naming the misaligned field(s); when the desk has no live timeslots (Optional.empty()), the check is skipped entirely rather than failing the save (P-10)."
    requirement: SHLB-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_offGridStartTime_rejectedWithStartTimeDetail"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_offGridBreakStart_rejectedWithBreakStartTimeDetail"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_onGridWithBreak_accepted"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_boundsAbsent_offGridAccepted"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-11 identity + non-overlap: same (name, effectiveFrom) collides; touching eras (one ends the day before the next starts) coexist; overlapping-by-one-day eras collide; an open-ended era blocks any later same-name era; names differing only by case are distinct identities; updating a row to its own existing identity never collides with itself."
    requirement: SHLB-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_duplicateNameAndEffectiveFrom_throwsConflictException"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_touchingEras_bothSaveSuccessfully"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_overlappingErasByOneDay_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_openEndedEraBlocksLaterEra_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_namesDifferingByCase_areDistinct"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#update_toOwnExistingIdentity_doesNotCollideWithItself"
        status: pass
    human_judgment: false
  - id: D4
    description: "Retirement is an effective_to edit through updateShiftTemplate: the row still exists and is still returned by the repository afterward — no delete or retire method exists on the service or controller."
    requirement: SHLB-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#update_effectiveToToToday_retiresTemplate_rowStillExists"
        status: pass
      - kind: other
        ref: "grep -vE '^\\s*(//|\\*|/\\*)' ShiftTemplateService.java | grep -Ec 'public void delete|public void retire' -> 0"
        status: pass
      - kind: other
        ref: "grep -c '@DeleteMapping' ShiftTemplateController.java -> 0"
        status: pass
    human_judgment: false
  - id: D5
    description: "Cross-tenant isolation on the update path: a template belonging to tenant A is invisible to a list call made under tenant B, and updating it from tenant B throws EntityNotFoundException (never a bare findById)."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#crossTenant_invisibleToListing_updateThrowsEntityNotFound"
        status: pass
    human_judgment: false
  - id: D6
    description: "List ordering is specified and stable: rows are grouped by name ascending, then effectiveFrom descending within a name, and repeated calls on unchanged data return the identical id sequence."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#list_ordersByNameAscendingThenEffectiveFromDescending"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#list_twoCallsSameSequence"
        status: pass
    human_judgment: false
  - id: D7
    description: "eraStatus (CURRENT/UPCOMING/PAST) is computed server-side and agrees with the non-overlap invariant: exactly one row per name reports CURRENT for any given day, and an open-ended range (effectiveTo null) never reports PAST."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#eraStatus_exactlyOneCurrentAcrossErasForGivenDay"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#eraStatus_openEndedRange_isCurrentAndNeverExpires"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#eraStatus_upcoming_forFutureEffectiveFrom"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#eraStatus_past_forExpiredRange"
        status: pass
    human_judgment: false
  - id: D8
    description: "An operator can edit or retire a template through PUT /api/v1/desks/{deskId}/shift-templates/{id} and see the change reflected on a subsequent list call."
    requirement: SHLB-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#controllerUpdate_returnsUpdatedResponse_andListReflectsChange"
        status: pass
    human_judgment: false
  - id: D9
    description: "No production solver file is touched by this plan (git diff over src/main/java/com/wfm/solver/, SolverService.java, ScheduleService.java is empty), and the full pre-existing backend suite passes unchanged."
    verification:
      - kind: other
        ref: "git diff --name-only -- src/main/java/com/wfm/solver/ src/main/java/com/wfm/service/SolverService.java src/main/java/com/wfm/service/ScheduleService.java (empty)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full existing suite, run clean and solo after the plan's changes)"
        status: pass
    human_judgment: false

duration: 24min
completed: 2026-08-25
status: complete
---

# Phase 14 Plan 03: Shift Template Full Validation & Lifecycle Summary

**`ShiftTemplateService` gains full save-time validation (name/time/break/weekday/effective-range checks, D-02 grid alignment, D-11 identity + non-overlap), `updateShiftTemplate` as the sole edit/retire path, server-computed `eraStatus`, and a name-grouped era-descending list order — every reject now carries a specific, asserted, operator-readable message.**

## Performance

- **Duration:** 24 min
- **Started:** 2026-08-25T23:57:10Z
- **Completed:** 2026-08-26T00:21:18Z
- **Tasks:** 2 (both `tdd="true"`, RED/GREEN commit pairs)
- **Files modified:** 5 (0 created)

## Accomplishments

- `ShiftTemplateService.validate(...)`: one shared private path, called identically from `createShiftTemplate` and the new `updateShiftTemplate`, so the two entry points cannot drift (T-14-11) — covers blank name, inverted/equal start-end times, negative or envelope-exceeding break values, empty weekday set, missing/inverted effective range, each with an exact operator-facing message
- D-02 grid alignment against `TimeslotGeneratorService.getLiveBounds(deskId)`: a package-private static `isAligned` helper checks start/end/break-start/break-end against the desk's live increment, accumulating one `ErrorDetail` per misaligned field into a single `PreSolveValidationException`; skipped entirely when the desk has no live timeslots yet (P-10), never failing the save
- D-11 identity + non-overlap: `validateIdentityAndNonOverlap` rejects a same-`(name, effectiveFrom)` collision and any same-name range overlap (both ends inclusive, `LocalDate.MAX` used only as an in-memory sentinel for a null `effectiveTo`, never persisted) — touching eras (one ends the day before the next starts) are proven to coexist, one-day overlaps are proven to collide
- `updateShiftTemplate(UUID deskId, UUID id, ShiftTemplateRequest request)`: loads through `findByIdAndTenantIdAndDeskId` (never a bare `findById`, T-14-10), runs the same `validate(...)`, then applies every field including `effectiveTo` — this is the entire retirement mechanism (P-11); there is no delete or retire method anywhere in the service or controller
- `ShiftTemplateResponse.eraStatus` (`CURRENT`/`UPCOMING`/`PAST`, P-13) computed server-side in the controller's `toResponse` from `LocalDate.now()`, using the same inclusive-both-ends predicate the non-overlap check uses, so the two can never disagree about which era owns a date
- `ShiftTemplateService.listShiftTemplates` now sorts name-ascending then `effectiveFrom`-descending (P-14) with a stable comparator — repeated calls on unchanged data return the identical id sequence
- `PUT /api/v1/desks/{deskId}/shift-templates/{id}` added to `ShiftTemplateController`, mirroring `SpecializationController.updateSpecialization`'s shape — the single endpoint that serves both editing and retiring; no `@DeleteMapping` exists or was added

## Task Commits

Each task followed RED/GREEN (tdd="true"):

1. **Task 1: Full save-time validation, identity and non-overlap invariants**
   - `9171247` (test) — failing tests for validation/identity/grid/retirement/tenancy; confirmed RED via a genuine compile failure (`updateShiftTemplate` did not exist yet)
   - `20b12be` (feat) — implemented `validate(...)`, `updateShiftTemplate`, grid check, identity+overlap; fixed `ShiftTemplateTracerTest`'s `@Import` to include the new `TimeslotGeneratorService` constructor dependency (Rule 3)
2. **Task 2: Era-aware list ordering, eraStatus, and the update endpoint**
   - `4f20410` (test) — failing tests for ordering/eraStatus/controller update; confirmed RED via compile failure (`eraStatus()`, `controller.updateShiftTemplate(...)` did not exist yet)
   - `65a2ef5` (feat) — added `eraStatus` to `ShiftTemplateResponse`, computed it in the controller, added the sort to `listShiftTemplates`, added the `PUT` mapping

**Plan metadata:** committed alongside this SUMMARY

## Files Created/Modified

- `src/main/java/com/wfm/service/ShiftTemplateService.java` — full validation, `updateShiftTemplate`, grid check, identity+overlap, sorted list
- `src/main/java/com/wfm/controller/ShiftTemplateController.java` — `PUT /{id}`, `eraStatus` computation in `toResponse`
- `src/main/java/com/wfm/dto/ShiftTemplateResponse.java` — `+eraStatus` component (positioned last)
- `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` — new file (created in this plan's RED commit), 30 test methods covering both tasks' `<behavior>` blocks
- `src/test/java/com/wfm/service/ShiftTemplateTracerTest.java` — `@Import` extended with `TimeslotGeneratorService.class` (Rule 3 fix, required for the tracer's context to still load)

## Decisions Made

- **Open-ended range-overlap message text:** the plan's conflict message template names `{from}` and `{to}` but does not specify text for a null `{to}`. Used the word `"present"` (lowercase, matches sentence flow), consistent with 14-UI-SPEC.md's own `{to or "Present"}` convention for the identical concept.
- **Tracer test `@Import` fix (Rule 3 — blocking):** `ShiftTemplateService`'s constructor now requires `TimeslotGeneratorService`. `@DataJpaTest` does not auto-wire `@Service` beans outside an explicit `@Import`, so `ShiftTemplateTracerTest`'s existing `@Import({ShiftTemplateService.class, ShiftTemplateController.class})` would fail to build the context. Added `TimeslotGeneratorService.class` to that `@Import` — its own dependencies (`TimeslotRepository`, `StaffingRequirementRepository`, `ScheduleRepository`, `EntityManager`) are already available under `@DataJpaTest`, and `getLiveBounds` correctly returns `Optional.empty()` for every desk the tracer test creates (no timeslots exist), so grid checking remains a no-op there (P-10) and none of the tracer's existing assertions change.
- **Sorting deliberately withheld from Task 1:** `listShiftTemplates` stayed unsorted after Task 1's implementation (repository insertion order), so Task 2's ordering tests had a real RED state to fail against, rather than passing by accident before the sort was written.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Extended `ShiftTemplateTracerTest`'s `@Import` to include `TimeslotGeneratorService`**
- **Found during:** Task 1 (implementing `ShiftTemplateService`'s new `TimeslotGeneratorService` constructor dependency)
- **Issue:** `ShiftTemplateService`'s constructor signature changed to require `TimeslotGeneratorService`, but the pre-existing `ShiftTemplateTracerTest`'s `@DataJpaTest` context (`@Import({ShiftTemplateService.class, ShiftTemplateController.class})`) had no bean for it, which would fail Spring context creation and break the whole existing tracer test.
- **Fix:** Added `TimeslotGeneratorService.class` to the `@Import` list. Its own dependencies are already available under `@DataJpaTest` (repositories + `EntityManager`), so no further wiring was needed.
- **Files modified:** `src/test/java/com/wfm/service/ShiftTemplateTracerTest.java`
- **Verification:** `./gradlew test --tests 'com.wfm.service.ShiftTemplateTracerTest'` passes unchanged; full suite green.
- **Committed in:** `20b12be` (Task 1 GREEN commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to keep the pre-existing tracer test's Spring context loadable after adding a required constructor dependency to the service it imports. No behavioral change to the tracer test's own assertions. No scope creep.

## Issues Encountered

- An earlier `./gradlew test` invocation collided with a second concurrent invocation against the same `build/test-results/test/` directory, producing a spurious `Could not write XML test results for ...` failure with no actual test failures listed. Re-ran a single, solo `./gradlew test` after confirming no other Gradle test process was running — that run was `BUILD SUCCESSFUL` in 7m 46s with `EXIT:0`. Not a real regression; an artifact of overlapping local invocations during this session.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- SHLB-01 through SHLB-04 are now fully implemented and tested: every reject is a named, operator-readable message; D-11's identity + non-overlap invariants together guarantee exactly one era of a given name applies to any given date; retirement deletes nothing; the list order is specified and stable.
- Remaining Phase 14 plans (SHLB-05/06 coverage + contracted-hours validation, MODE-01..05 scheduling-mode switch, the `ShiftLibrary.tsx` mode toggle/coverage panel) are unaffected by this plan's scope and can proceed against this validated `ShiftTemplateService`/`ShiftTemplateController` foundation.
- Phase 15/16 can build against `updateShiftTemplate` as the one mutation path for edits and retirements — no delete endpoint exists to guard against, per D-10/P-11.
- No blockers.

---
*Phase: 14-shift-library-scheduling-mode*
*Completed: 2026-08-25*

## Self-Check: PASSED

All 5 modified files confirmed present on disk; all 4 task commits (`9171247`, `20b12be`,
`4f20410`, `65a2ef5`) confirmed present in `git log`. Re-ran the plan's `<verification>` block:
`./gradlew test --tests 'com.wfm.service.ShiftTemplateServiceTest'` — PASS; `./gradlew test`
(full suite, solo) — PASS, `BUILD SUCCESSFUL` in 7m 46s; solver-file diff — empty.
