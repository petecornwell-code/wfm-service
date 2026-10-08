---
phase: 20-solver-business-date-correctness
plan: 09
subsystem: scheduling/timeslot-generation
tags: [timeslot-generation, desk-anchor, tenant-scoping, validation, tdd]

# Dependency graph
requires:
  - phase: 20-solver-business-date-correctness (plan 20-08)
    provides: "DeskService.setDayStart's 15-minute-boundary save gate, replacing the 00:00-only gate"
provides:
  - "The live generate-timeslots REST endpoint reads the desk's own stored day start through a tenant-scoped lookup, instead of a hardcoded midnight literal"
  - "A test proving the Phase 18 generation-time tiling refusal fires through the real controller method, not just the generator behind it"
  - "A save-time refusal of any day start carrying a nonzero second or sub-second component, closing the precision hole the 15-minute modulus silently ignored"
affects: [20-10, 20-11]

# Actuals (#2632) -- chars/4 over the realized diff (git diff, not whole-file size)
actuals:
  tokens: 4006
  tasks: 2
  commits: 4
  plan_head_before: 57d4bb463f61810b84182d19e0dabbf5978df8eb

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Controller resolves the tenant-scoped owning entity (DeskService.getDesk) BEFORE calling into a service whose own validation fires before any tenant check of its own -- ordering the desk read first is what makes a downstream refusal (TimeslotGeneratorService.requireDayStartTiles) reachable without leaking it to an unauthorized caller"
    - "A save-time gate composed of multiple independently-worded refusal conditions, each naming its own reason, ordered so the more specific/discriminating check runs before a coarser one that could report the wrong reason for the same bad value"

key-files:
  created:
    - src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java
  modified:
    - src/main/java/com/wfm/controller/TimeslotController.java
    - src/main/java/com/wfm/service/DeskService.java
    - src/test/java/com/wfm/service/DeskServiceDayStartTest.java

key-decisions:
  - "Resolved the desk via DeskService.getDesk (tenant-scoped, throws EntityNotFoundException), never DeskRepository.findById, per the plan's own threat model (T-20-09-01) -- a bare findById would let a caller generate against another tenant's anchor"
  - "The sub-minute precision refusal is ordered BEFORE the 15-minute modulus check so a value failing both (e.g. 06:07:01) is refused for the correct reason -- the modulus discards seconds and would otherwise name the wrong defect"
  - "Kept the precision refusal as its own separately-worded IllegalArgumentException rather than folding it into the modulus message, per 20-CONTEXT.md D-02's one-visible-reviewable-condition intent"
  - "Left TimeslotGeneratorService.requireDayStartTiles's own sub-minute blind spot unchanged (recorded non-change in the plan): after Task 2, no desk row can hold a sub-minute day start, so that generator check can never receive one through the save-then-generate path"

patterns-established:
  - "Desk-anchor reachability tests that drive the REST controller method directly (plain Spring-context instantiation via @DataJpaTest + @Import of the controller itself), rather than the service behind it, when the gap being closed is specifically about the entry point's own wiring"

requirements-completed: [SOLV-01]

coverage:
  - id: D1
    description: "The generate-timeslots endpoint resolves the desk's OWN stored day start through a tenant-scoped lookup and passes it to the generator"
    requirement: "SOLV-01"
    verification:
      - kind: integration
        ref: "src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java#generate_2100AnchoredDesk_oneBusinessDay_returns24RowsAcrossTwoCalendarDates"
        status: pass
    human_judgment: false
  - id: D2
    description: "Phase 18's generation-time tiling refusal fires through the live REST entry point (controller method itself), with a positive control proving it does not fire indiscriminately"
    requirement: "SOLV-01"
    verification:
      - kind: integration
        ref: "src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java#generate_2115AnchoredDesk_30MinuteIncrement_refusedNamingDayStartIncrementAndTile"
        status: pass
      - kind: integration
        ref: "src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java#generate_2130AnchoredDesk_30MinuteIncrement_succeeds_positiveControl"
        status: pass
    human_judgment: false
  - id: D3
    description: "A desk id belonging to a different tenant is refused with not-found before any generation work, and no row is persisted for it"
    requirement: "SOLV-01"
    verification:
      - kind: integration
        ref: "src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java#generate_crossTenantDeskId_refusedNotFound_noRowPersisted"
        status: pass
    human_judgment: false
  - id: D4
    description: "DeskService.setDayStart refuses a day start carrying a nonzero second or sub-second component, by name, ordered before the 15-minute modulus"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_060001_refusedNamingRejectedValue_persistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_nanosecondComponent_refusedNamingRejectedValue_persistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_060701_refusedNamingSubMinuteReason_notBoundaryReason_persistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_060000_accepted_positiveControl"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-02
status: complete
---

# Phase 20 Plan 09: Generate-Endpoint Desk Anchor & Sub-Minute Precision Gate Summary

**The live `POST /api/v1/desks/{deskId}/timeslots/generate` endpoint now reads the desk's own stored day start through a tenant-scoped lookup instead of a hardcoded midnight literal, and `DeskService.setDayStart` refuses any value carrying a nonzero second or sub-second component.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-10-02T02:35:00Z (approx)
- **Completed:** 2026-10-02T02:59:15Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- `TimeslotController.generateTimeslots` resolves the desk via `DeskService.getDesk` (tenant-scoped, 404 on an invisible desk) and passes `desk.getDayStart()` to the generator, closing GAP 1 of `20-VERIFICATION.md` — the one live REST caller that `FteUploadService` had already been fixed to match.
- A new test class, `TimeslotControllerDeskAnchorTest`, drives the controller method directly (not the generator behind it) and proves: a 21:00-anchored desk's one-business-day window generates 24 rows across two calendar dates; a 21:15-anchored desk refuses a 30-minute generation call naming the day start, the increment and the word "tile"; a 21:30-anchored desk succeeds (positive control); and a cross-tenant desk id is refused with not-found before any row is persisted.
- `DeskService.setDayStart` gained a second refusal, ordered before the 15-minute modulus check, that rejects any day start with a nonzero second or nanosecond component — closing a precision hole where the modulus silently discarded sub-minute information and accepted values like `06:00:01` that would behave differently than their truncated equivalent wherever minute-of-day arithmetic reads them back.
- The stale comment in `TimeslotController` claiming "the 00:00-only gate makes any other anchor unreachable through this endpoint" — a premise plan 20-08 already invalidated — was deleted along with the now-unused `LocalTime` import.

## Task Commits

Each task followed RED → GREEN (no REFACTOR needed in either case):

1. **Task 1 (tracer): generate-endpoint anchors on the desk's own day start**
   - `f78c0fe` — `test(20-09): add failing test for generate-endpoint desk anchor (SOLV-01)` (RED)
   - `d53522c` — `feat(20-09): anchor generate-timeslots endpoint on desk's own day start` (GREEN)
2. **Task 2: save-time gate refuses sub-minute precision**
   - `2935463` — `test(20-09): add failing tests for day-start sub-minute precision (SOLV-01/WR-01)` (RED)
   - `a1314c7` — `feat(20-09): refuse sub-minute precision in desk day-start save (SOLV-01/WR-01)` (GREEN)

**Plan metadata:** committed alongside this SUMMARY.

## Files Created/Modified

- `src/main/java/com/wfm/controller/TimeslotController.java` — added a `DeskService` constructor dependency; `generateTimeslots` resolves the desk first and passes its stored `dayStart` to the generator; deleted the stale comment and the now-unused `LocalTime` import.
- `src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java` — new: 4 tests driving the real controller method on a re-anchored desk (business-day case, tiling refusal, positive control, cross-tenant refusal).
- `src/main/java/com/wfm/service/DeskService.java` — `setDayStart` gains a second-and-nanosecond refusal ordered before the 15-minute modulus check; javadoc extended with the rationale.
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` — 4 new cases: `06:00:00` accepted (positive control), `06:00:01` refused, a nonzero-nanosecond value refused, and `06:07:01` refused naming the sub-minute reason rather than the boundary reason. All 16 pre-existing cases pass unchanged.

## RED Evidence (observed, not claimed)

**Task 1**, before the production change, `TimeslotControllerDeskAnchorTest` (4 tests, 3 failed):
- `generate_2100AnchoredDesk_oneBusinessDay_returns24RowsAcrossTwoCalendarDates` → `java.lang.IllegalArgumentException: Time range must be positive and evenly divisible by incrementMinutes` (the controller still hardcoded `LocalTime.MIDNIGHT`, so a 21:00-to-21:00 window measured zero minutes).
- `generate_2115AnchoredDesk_30MinuteIncrement_refusedNamingDayStartIncrementAndTile` → `AssertionError: Expecting code to raise a throwable` (no tiling refusal fired against the hardcoded midnight anchor).
- `generate_crossTenantDeskId_refusedNotFound_noRowPersisted` → `AssertionError: Expecting code to raise a throwable` (no tenant-scoped desk read existed yet, so a cross-tenant id was never rejected).
- `generate_2130AnchoredDesk_30MinuteIncrement_succeeds_positiveControl` passed trivially both before and after (expected for a positive control).

**Task 2**, before the production change, `DeskServiceDayStartTest`'s 4 new cases (3 failed):
- `setDayStart_060001_refusedNamingRejectedValue_persistsNothing` → `AssertionError: Expecting code to raise a throwable` (`06:00:01`'s minute-of-day is 360, `360 % 15 == 0`, so the modulus passed it silently).
- `setDayStart_nanosecondComponent_refusedNamingRejectedValue_persistsNothing` → same failure mode (`21:15` plus 500ns has minute-of-day 1275, `1275 % 15 == 0`).
- `setDayStart_060701_refusedNamingSubMinuteReason_notBoundaryReason_persistsNothing` → the call DID throw, but with `"Desk day start 06:07:01 is not a 15-minute boundary"` — the wrong reason, since `06:07:01`'s actual defect is its trailing second, not its 7-minute offset from a quarter-hour.
- `setDayStart_060000_accepted_positiveControl` passed trivially both before and after.

## Decisions Made

- Resolved the desk via `DeskService.getDesk` rather than `DeskRepository.findById`, per the plan's threat model (T-20-09-01): a bare `findById` would let a caller generate against another tenant's anchor, and `getDesk`'s tenant-scoped `EntityNotFoundException` is what `FteUploadService` already does for the same reason.
- Ordered the new precision refusal before the existing 15-minute modulus check so a value failing both conditions is refused for its real defect, not a coincidentally-true but misleading one.
- Kept the two `setDayStart` refusals separately worded rather than merging them into one message, matching `20-CONTEXT.md` D-02's one-visible-reviewable-condition intent carried forward from `18-CONTEXT.md` D-07.
- Deliberately left `TimeslotGeneratorService.requireDayStartTiles`'s identical sub-minute blind spot unchanged (recorded as a non-change in the plan itself): after Task 2 no desk row can hold a sub-minute day start, so that generator-side check can never receive one through the save-then-generate path, and widening a second refusal here would add untested surface with no reachable caller.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- GAP 1 of `20-VERIFICATION.md` (success criterion 6) is closed: all four `missing:` items hold (desk-anchored generation, stale comment removed, controller-path proof with positive control, sub-minute precision refusal covered by tests).
- `./gradlew test` is green end to end (full suite, not just the filtered classes this plan touched).
- Plans 20-10 and 20-11 (the phase goal and advisory 1 gap closures) are unblocked and can proceed; neither depends on anything this plan left open.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-02*

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/controller/TimeslotController.java`
- FOUND: `src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java`
- FOUND: `src/main/java/com/wfm/service/DeskService.java`
- FOUND: `src/test/java/com/wfm/service/DeskServiceDayStartTest.java`
- FOUND: `.planning/phases/20-solver-business-date-correctness/20-09-SUMMARY.md`
- FOUND commits: `f78c0fe`, `d53522c`, `2935463`, `a1314c7`, `27c98a1`
- Re-ran all plan-level `<verification>` commands: `./gradlew test` green (full suite); `desk.getDayStart()` count 1; `LocalTime` count 0 in `TimeslotController.java`; `TimeslotControllerDeskAnchorTest` reports 4 passing tests with no direct generator call; `getSecond`/`getNano` each present in `DeskService.java`; `is not a 15-minute boundary` present exactly once.
