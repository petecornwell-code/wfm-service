---
phase: 19-daywindow-re-anchoring
plan: 04
subsystem: solver
tags: [daywindow, bday-04, shift-templates, break-bands, net-hours, timefold]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-03's DayWindow.anchoredAt(LocalTime) bound-instance factory and the nine anchored* instance methods (anchoredContains, anchoredOverlaps, anchoredPlusWithinDay, anchoredDurationMinutes, etc.) this plan's call sites consume"
provides:
  - "ShiftTemplateBreakBand.getBreakStartTime(ShiftTemplate, DayWindow) / getBreakEndTime(ShiftTemplate, DayWindow) -- the break-band geometry helpers now take an explicit anchor, no single-argument form survives"
  - "ShiftTemplateService.dayWindowFor(UUID) -- the anchor source ShiftTemplateController reaches through, so the controller holds no DeskRepository of its own (P-03)"
  - "ShiftTemplate.getNetHours(int, DayWindow) -- net hours computed against an explicit anchor; no single-argument form survives"
  - "ScheduleConstraintProvider.breakClustering consuming the real desk anchor via ScheduleConfig.dayStart(), the second of the two constraints whose stream carries it (the tracer's shiftEnvelopeCompliance is the first)"
  - "ShiftLibraryValidationService.covers/isTemplateAligned/netHoursForBands and the four advisory finders all take a bound DayWindow, resolved once per public method from the desk the class already loads"
  - "DeskAgentService's two public read methods and four public write methods each bind one window per call from the desk they already load through DeskRepository"
  - "ShiftLibraryGenerationService.generateSuggestion binds its window from the same most-recently-created Schedule already loaded for break-duration config, propagated through candidate enumeration, greedy cover, and band suggestion"
affects: [19-05-daywindow-reanchoring, 19-06-daywindow-reanchoring, 19-07-daywindow-reanchoring, 19-08-daywindow-reanchoring]

actuals:
  tokens: 16278
  tasks: 2
  commits: 2
  plan_head_before: 408718fbec7bf453a4019ac10cbbea302c74644f

tech-stack:
  added: []
  patterns:
    - "Window bound once per public method, threaded as a parameter through every private helper that needs it -- not re-resolved per call. Every service in this plan (ShiftTemplateService, ShiftLibraryValidationService, DeskAgentService, ShiftLibraryGenerationService) follows this shape: bind DayWindow at the top of the public method from whatever desk/schedule source the class already holds, then pass the DayWindow object (never a raw LocalTime) down into every model helper."
    - "A DayWindow local variable is named dayWindow (not window) in any method that already uses `window` for a demand-window loop variable (ShiftLibraryValidationService.Window, ShiftLibraryGenerationService.Window) -- avoids a same-scope name collision between the two distinct 'window' concepts this phase's vocabulary now carries."
    - "Resolve-only-if-needed: a window (or any anchor lookup) that could throw on a not-found desk is deferred until the code path that actually needs it, preserving a pre-existing all-cases-handled graceful path (see Deviation 2) rather than moving the failure earlier."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/controller/ShiftTemplateController.java
    - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/main/java/com/wfm/model/ShiftTemplate.java
    - src/main/java/com/wfm/model/ShiftBandPair.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java

key-decisions:
  - "ShiftLibraryGenerationService's window is bound in resolveBreakConfig from the SAME most-recently-created Schedule query already run there for break-duration/threshold config, not a second DeskRepository lookup -- P-02 rule 5 ('no DeskRepository and no Schedule parameter of its own') is satisfied by reusing a load the class already performs, falling back to a midnight anchor exactly like that method's existing break-duration defaults do when the desk has no persisted schedule yet. Plan 19-06/19-07 should bind at this same place (BreakConfig.window()) rather than introducing a second anchor source in this class."
  - "ShiftLibraryValidationService's dayWindowFor and DeskAgentService's dayWindowFor both fall back to DayWindow.anchoredAt(LocalTime.MIDNIGHT) on a missing desk, rather than throwing EntityNotFoundException -- mirroring each class's own PRE-EXISTING graceful-default convention for the identical DeskRepository.findByIdAndTenantId call (ShiftLibraryValidationService.loadHoursByWeekday already does this for defaultContractedHoursPerDay; DeskAgentService.resolveScheduleDefault already does this for the schedule default). ShiftTemplateService.dayWindowFor, by contrast, throws EntityNotFoundException on a missing desk -- matching ITS class's own established convention (every other method on that class throws on a missing desk/template). Three classes, two different existing conventions, each one kept rather than homogenized onto a single new rule."
  - "ShiftBandPair.netHours() is kept as a transitional @Deprecated delegate binding DayWindow.anchoredAt(LocalTime.MIDNIGHT), not migrated to take a window parameter -- it is a direct caller of the now-two-argument ShiftTemplate.getNetHours but its own remaining callers (AgentShiftAssignment, ShiftLibraryGenerationService's Candidate/EmittedRow report accessors, and several tests) are out of this plan's file scope. This exactly mirrors plan 19-03's own precedent for ShiftBandPair.covers()'s transitional one- and six-argument forms."

requirements-completed: [BDAY-04]

coverage:
  - id: D1
    description: "ShiftTemplateBreakBand.getBreakStartTime/getBreakEndTime, ShiftLibraryValidationService.covers/isTemplateAligned, and ScheduleConstraintProvider.breakClustering each take a DayWindow and neither resolves an anchor on its own; no one-argument form of the break-band methods survives."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -cE 'getBreakStartTime\\(ShiftTemplate [a-z]+\\)|getBreakEndTime\\(ShiftTemplate [a-z]+\\)' src/main/java/com/wfm/model/ShiftTemplateBreakBand.java == 0"
        status: pass
      - kind: other
        ref: "grep -c 'cfg.dayStart()' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java == 2"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.MidnightWindowSeamTest (full class, all 5 nested groups)"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftTemplateController reaches its anchor through ShiftTemplateService.dayWindowFor(deskId), never through a DeskRepository or LocalTime of its own, and still takes exactly two constructor arguments."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -c 'DeskRepository\\|LocalTime' src/main/java/com/wfm/controller/ShiftTemplateController.java == 0"
        status: pass
      - kind: other
        ref: "grep -c 'dayWindowFor(deskId)' src/main/java/com/wfm/controller/ShiftTemplateController.java == 3"
        status: pass
    human_judgment: false
  - id: D3
    description: "ShiftTemplate.getNetHours and every production/test caller take a DayWindow; no single-argument form survives; a template whose envelope crosses the anchor yields a positive value instead of throwing."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -cE 'getNetHours\\((0|[0-9]+|[a-zA-Z.()]+)\\)$' src/main/java/com/wfm/model/ShiftTemplate.java == 0"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.MidnightWindowSeamTest --tests com.wfm.service.MidnightBoundaryPropertyTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full unfiltered suite green after both tasks, including every Phase 18 guard test, with MidnightWindowSeamTest's and MidnightBoundaryPropertyTest's call-shape-only diffs (no changed assertion)."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "./gradlew test (full, unfiltered) -- 1134 tests, 0 failures, 0 errors, 3 separate runs across the Task 1 intermediate state, and the final combined state"
        status: pass
      - kind: other
        ref: "git diff -- src/test/java/com/wfm/service/MidnightWindowSeamTest.java src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java (manually reviewed: call-shape only)"
        status: pass
    human_judgment: false

duration: 100min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 04: Break-Band Times And Net Hours Take The Window Summary

**`ShiftTemplateBreakBand`'s break-geometry helpers, `ShiftTemplate.getNetHours`, and `ScheduleConstraintProvider.breakClustering` all now consume an explicit `DayWindow` instead of a midnight-implicit static — `ShiftTemplateController` reaches its anchor through a new `ShiftTemplateService.dayWindowFor(UUID)` seam rather than growing its own `DeskRepository`, and every one of the four services calling these helpers (including `ShiftLibraryGenerationService`, compiler-forced into scope) binds its window once per public method from a real source.**

## Performance

- **Duration:** ~100 min
- **Completed:** 2026-10-01T02:35:12Z
- **Tasks:** 2
- **Files modified:** 14 (9 main, 5 test)

## Accomplishments
- `ShiftTemplateBreakBand.getBreakStartTime`/`getBreakEndTime` take a trailing `DayWindow` and route through `window.anchoredPlusWithinDay` instead of the deprecated `DayWindow.plusWithinDay` static — no field, setter, or local default for an anchor exists on the class.
- `ShiftTemplateService.dayWindowFor(UUID deskId)` is the new anchor source `ShiftTemplateController` reaches through — the controller's constructor still takes exactly two arguments and the class contains no occurrence of `DeskRepository` or `LocalTime`.
- `ShiftTemplate.getNetHours(int, DayWindow)` routes through `window.anchoredDurationMinutes`, which no longer throws on a non-forward interval (it wraps forward instead) — satisfying this plan's `<behavior>` requirement that a template crossing the anchor yields a positive net-hours value.
- `ScheduleConstraintProvider.breakClustering`'s `isOnBreak` helper now binds a `DayWindow` from `cfg.dayStart()` inside the `(sa, cfg, ts)` filter tuple, falling back to `MIDNIGHT` for the same unmigrated-fixture reason `shiftEnvelopeCompliance` already established in plan 19-03 — `cfg.dayStart()` appears exactly twice in the file, the only two constraints whose stream carries it.
- `ShiftLibraryValidationService.covers`, `isTemplateAligned`, and `netHoursForBands` (plus the four advisory-finding methods that call it) all take a bound `DayWindow`, resolved once per `validate(UUID)` call via a new `dayWindowFor(long, UUID)` helper that falls back to midnight on a missing desk — matching the class's own pre-existing graceful-default convention.
- `DeskAgentService`'s two public read methods (`listDeskAgentResponses`, `getDeskAgentResponse`) and four public write methods (`assignAgents`, `setSpecializations`, `setContractedHours`, `setDayHours`) each bind one window per call from the desk they already load through `DeskRepository`, threaded through `toResponse` → `usualShiftEntry` → `hoursAdvisory`.
- `ShiftLibraryGenerationService.generateSuggestion` binds its window inside `resolveBreakConfig` (reusing the Schedule query already run there for break-duration config, not a second repository call), propagated through `enumerateCandidates`/`addCandidateIfAdmissible` (for `getNetHours`) and `greedyCover`/`buildResponse`/`computeUncoveredDetails`/`suggestedBands` (for `covers`, compiler-forced into this plan's scope — see Deviations).
- Full unfiltered `./gradlew test` is green at every checkpoint: the Task 1 intermediate state (1134 tests, 0 failures, 0 errors), and the final combined state (1134 tests, 0 failures, 0 errors) — run three separate times across this plan's execution.

## Task Commits

Each task was committed atomically:

1. **Task 1: Break-band times take the window, and the controller gets an anchor it can reach** - `7296d3d` (feat)
2. **Task 2: Net hours takes the window, across every caller** - `a2c76fa` (feat)

**Plan metadata:** (this commit, docs(19-04): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` - `getBreakStartTime`/`getBreakEndTime(ShiftTemplate, DayWindow)`
- `src/main/java/com/wfm/service/ShiftTemplateService.java` - new `dayWindowFor(UUID)`
- `src/main/java/com/wfm/controller/ShiftTemplateController.java` - `toResponse(ShiftTemplate, DayWindow)`; `listShiftTemplates` resolves the window only when the template list is non-empty (Deviation 2)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `breakClustering`/`isOnBreak` bind and consume a `DayWindow` from `cfg.dayStart()`
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` - `covers(ShiftTemplate, List, Window, DayWindow)`, `isTemplateAligned`, `netHoursForBands`, and the four advisory finders all take a bound window; new private `dayWindowFor(long, UUID)`
- `src/main/java/com/wfm/model/ShiftTemplate.java` - `getNetHours(int, DayWindow)`
- `src/main/java/com/wfm/model/ShiftBandPair.java` - `netHours()` kept as a transitional `@Deprecated` delegate at a midnight anchor (Deviation 3)
- `src/main/java/com/wfm/service/DeskAgentService.java` - new private `dayWindowFor(long, UUID)`; window threaded through `toResponse`/`usualShiftEntry`/`hoursAdvisory` and all six public entry points
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` - `BreakConfig` gains a `window` field bound in `resolveBreakConfig`; threaded through candidate enumeration, greedy cover, and response building (Deviation 1)
- `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`, `MidnightBoundaryPropertyTest.java`, `ShiftLibraryValidationServiceTest.java`, `ShiftTemplateServiceTest.java`, `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` - every call site updated to pass `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`, no asserted value changed

## Decisions Made

See `key-decisions` in the frontmatter for the full reasoning. In brief:
- `ShiftLibraryGenerationService`'s window reuses the existing `resolveBreakConfig` Schedule load rather than adding a second repository dependency.
- Three classes' `dayWindowFor` helpers follow THREE different existing conventions for a missing desk (throw in `ShiftTemplateService`, fall back to midnight in `ShiftLibraryValidationService` and `DeskAgentService`) — each matches its own class's pre-existing behavior for the identical repository call, rather than being homogenized onto one new rule.
- `ShiftBandPair.netHours()` stays a transitional midnight-anchor delegate, mirroring plan 19-03's precedent for `covers()`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking, compiler-forced] `ShiftLibraryGenerationService` is not in either task's file list, but is a direct caller of `ShiftLibraryValidationService.covers(...)` at five call sites and `ShiftTemplate.getNetHours` at one**
- **Found during:** Task 1 (compile step, after `covers()`'s signature changed) and Task 2 (compile step, after `getNetHours`'s signature changed)
- **Issue:** The plan's `<files>` lists for both tasks name `ShiftLibraryValidationService.java` but not `ShiftLibraryGenerationService.java`, even though the latter calls `shiftLibraryValidationService.covers(template, bands, window)` in `greedyCover` (twice), `computeUncoveredDetails`, and `suggestedBands` (twice), and `template.getNetHours(duration)` once in `addCandidateIfAdmissible`. Changing either signature without updating this file is a compile failure, not an optional improvement.
- **Fix:** Threaded a `DayWindow` through this file exactly as D-08 prescribes for a class with "neither a `DeskRepository` nor a `Schedule` parameter of its own" (the anchor-source table's own description of this file) — bound once in `resolveBreakConfig` from the same most-recently-created `Schedule` already loaded there for break-duration config (added a `window` field to the existing `BreakConfig` record), falling back to `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` exactly like that method's existing break-duration/threshold defaults. Propagated through `enumerateCandidates`/`addCandidateIfAdmissible` and `greedyCover`/`buildResponse`/`computeUncoveredDetails`/`suggestedBands`.
- **Files modified:** `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; full `./gradlew test` green (1134 tests, 0 failures) at both the Task 1 intermediate state and the final state.
- **Committed in:** `7296d3d` (Task 1, the `covers()` threading) and `a2c76fa` (Task 2, the `getNetHours` threading)

**2. [Rule 1 - Bug, caught by this task's own full-suite run] `listShiftTemplates` bound the window unconditionally, breaking a cross-tenant tenant-isolation test**
- **Found during:** Task 1's mandated full `./gradlew test` run
- **Issue:** The first implementation of `ShiftTemplateController.listShiftTemplates` called `shiftTemplateService.dayWindowFor(deskId)` before checking whether the (already tenant-scoped) template list was empty. `ShiftTemplateTracerTest.list_crossTenant_returnsEmpty` creates a desk under `TENANT_A`, switches `TenantContext` to `TENANT_B`, and asserts the endpoint returns an empty list (T-14-15) — `shiftTemplateService.listShiftTemplates(deskId)` already returns empty for a cross-tenant `deskId` (its query is tenant-scoped), but `dayWindowFor` looks up the desk under the NEW tenant and throws `EntityNotFoundException` instead, since the desk genuinely belongs to a different tenant.
- **Fix:** `listShiftTemplates` now checks `templates.isEmpty()` first and returns `List.of()` immediately in that case, resolving the window only when there is at least one template to map — the window lookup never runs for a case that needed no window.
- **Files modified:** `src/main/java/com/wfm/controller/ShiftTemplateController.java`
- **Verification:** `./gradlew test --tests com.wfm.service.ShiftTemplateTracerTest` passes; full `./gradlew test` green afterward (1134 tests, 0 failures, 0 errors).
- **Committed in:** `7296d3d` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 compiler-forced file addition spanning both tasks, 1 Rule 1 bug caught and fixed before either task's commit)
**Impact on plan:** The `ShiftLibraryGenerationService` addition is a necessary consequence of the plan's own anchor-source table (which explicitly names this file and its resolution rule) that the file-scope lists simply omitted — no scope creep beyond what the signature changes compiler-force. The tenant-isolation fix restores exactly the pre-existing security behavior; it does not change the window-binding behavior for any in-tenant request.

## Issues Encountered
None beyond the two deviations above, both resolved before their respective task commits.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Both commits (`7296d3d`, `a2c76fa`) land cleanly after plan 19-03's `3d45274`, each independently compiling and passing the full suite — `7296d3d` alone (Task 1 only, before `ShiftTemplate.getNetHours` gained its `DayWindow` parameter) was verified compilable and green in isolation before Task 2 was reapplied on top.
- `ShiftLibraryGenerationService`'s window-binding pattern (`BreakConfig.window()`, bound in `resolveBreakConfig`) is now established and should be reused rather than re-derived by any later plan touching this file (noted in `key-decisions`).
- `ShiftBandPair.netHours()`'s remaining callers (`AgentShiftAssignment`, `ShiftLibraryGenerationService`'s `Candidate`/`EmittedRow` accessors, and their tests) are still on the transitional midnight-anchor delegate — whichever plan migrates `ShiftBandPair`'s remaining callers should also migrate this method's call shape.
- No blockers. Full unfiltered `./gradlew test` is green (1134 tests, 0 failures, 0 errors) at every checkpoint in this plan's execution.

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java`
- FOUND: `src/main/java/com/wfm/service/ShiftTemplateService.java`
- FOUND: `src/main/java/com/wfm/controller/ShiftTemplateController.java`
- FOUND: `src/main/java/com/wfm/service/ShiftLibraryValidationService.java`
- FOUND: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- FOUND: `src/main/java/com/wfm/model/ShiftTemplate.java`
- FOUND: `src/main/java/com/wfm/model/ShiftBandPair.java`
- FOUND: `src/main/java/com/wfm/service/DeskAgentService.java`
- FOUND: `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`
- FOUND commit `7296d3d` (Task 1) in `git log --oneline --all`
- FOUND commit `a2c76fa` (Task 2) in `git log --oneline --all`
- CONFIRMED: `grep -cE 'getBreakStartTime\(ShiftTemplate [a-z]+\)|getBreakEndTime\(ShiftTemplate [a-z]+\)' src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` reports `0`
- CONFIRMED: `grep -c 'DeskRepository\|LocalTime' src/main/java/com/wfm/controller/ShiftTemplateController.java` reports `0`
- CONFIRMED: `grep -c 'dayWindowFor(deskId)' src/main/java/com/wfm/controller/ShiftTemplateController.java` reports `3`
- CONFIRMED: `grep -c 'cfg.dayStart()' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` reports `2`
- CONFIRMED: `grep -cE 'getNetHours\((0|[0-9]+|[a-zA-Z.()]+)\)$' src/main/java/com/wfm/model/ShiftTemplate.java` reports `0`
- CONFIRMED: `grep -cE '^[[:space:]]*@Deprecated' src/main/java/com/wfm/util/DayWindow.java` reports `9` (unchanged)
- CONFIRMED: full, unfiltered `./gradlew test` — 1134 tests, 0 failures, 0 errors (run 3 times: once before the tenant-isolation fix found 1 failure, once for the Task 1 intermediate state, once for the final combined state)
- CONFIRMED: `git diff --stat 408718f..HEAD` touches exactly the 14 files listed above

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
