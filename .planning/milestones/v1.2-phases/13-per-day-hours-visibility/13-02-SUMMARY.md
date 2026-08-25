---
phase: 13-per-day-hours-visibility
plan: 02
subsystem: api
tags: [spring-boot, jpa, agent-day-hours, per-cell-edit]

# Dependency graph
requires:
  - phase: 13-per-day-hours-visibility (plan 01)
    provides: "DeskAgentResponse.dayHours map, toResponse/resolveScheduleDefault read-path helpers"
provides:
  - "DeskAgentService.setDayHours(deskId, agentId, day, hours, dayOffType, clearRow) — single-row upsert/delete, D-05"
  - "AgentDayHoursRepository.findByAgent_IdAndDayOfWeek(agentId, dayOfWeek) — single-weekday finder"
  - "PUT /api/v1/desks/{deskId}/agents/{agentId}/day-hours/{day} — per-cell edit endpoint, distinct from the bulk fan-out (P-05)"
  - "setContractedHours pinned as transactional and label-destructive, with a deterministic post-write flush before the response re-read"
affects: [13-per-day-hours-visibility-plan-03]

# Actuals (#2632)
actuals:
  tokens: 5957
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Single-row upsert via findByAgent_IdAndDayOfWeek().orElseGet(new)/save() — reuses an existing row for the weekday if present, never a second row (unique constraint backstop)"
    - "Reject-not-clamp validation for a single-cell edit, deliberately divergent from the bulk upload parser's silent clamp-at-24 (P-04)"
    - "Explicit flush() immediately after a repository write block, before a derived-query re-read that feeds the response — makes read-after-write ordering deterministic rather than relying on Hibernate's implicit auto-flush"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java
    - src/main/java/com/wfm/dto/SetDayHoursRequest.java
  modified:
    - src/main/java/com/wfm/repository/AgentDayHoursRepository.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/controller/DeskAgentController.java
    - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java

key-decisions:
  - "Adopted PLAN.md's P-04/P-05/P-06/P-07 planner decisions verbatim (reject not clamp; two distinct endpoints not a discriminated one; setDayHours leaves Agent.contractedHoursPerDay untouched; D-07 warning computed client-side from data already sent)"
  - "Added an explicit agentDayHoursRepository.flush() after setContractedHours' recreate loop (previously only flushed before the delete's recreate, relying on auto-flush before the read query) — makes the read-after-write ordering the plan's Task 3 <action> describes literally true rather than implicit"

requirements-completed: [UPL-03, UPL-04, UPL-05]

coverage:
  - id: D1
    description: "A single-weekday numeric edit touches exactly one agent_day_hours row and leaves the other six byte-identical (id/hours/dayOffType) — the structural closure of audit finding I-3 (D-05)"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_numeric_touchesExactlyOneRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_createsRowWhenNoneExists"
        status: pass
    human_judgment: false
  - id: D2
    description: "Setting a weekday to MANDATORY or PTO stores hours=0.00 with that label, matching the upload parser's own encoding"
    requirement: "UPL-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_mandatory_storesZeroWithLabel"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_pto_storesZeroWithLabel"
        status: pass
    human_judgment: false
  - id: D3
    description: "Setting a weekday to 'not set' deletes that weekday's row entirely rather than writing a zero row (absent is not the same as 0); clearing an already-absent weekday is a no-op"
    requirement: "UPL-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_notSet_deletesOnlyThatRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_notSet_onAbsentRow_isANoOp"
        status: pass
    human_judgment: false
  - id: D4
    description: "Out-of-range hours (negative, >24), a normalization edge case, and an all-null request body are rejected with IllegalArgumentException (400) and persist nothing — reject, not clamp (P-04)"
    requirement: "UPL-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_negative_isRejectedAndPersistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_above24_isRejectedAndPersistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_normalizesToScaleTwo"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_noValueAndNoLabelAndNoClear_isRejected"
        status: pass
    human_judgment: false
  - id: D5
    description: "A per-cell edit for an agent outside the caller's tenant/desk is rejected (EntityNotFoundException) before any AgentDayHoursRepository call, closing threat T-13-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_foreignTenantAgent_throwsEntityNotFound"
        status: pass
    human_judgment: false
  - id: D6
    description: "The response returned by a per-cell edit reflects the row just written, not a pre-write snapshot; the scalar Agent.contractedHoursPerDay is left untouched by a per-cell edit (P-06)"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_responseReflectsTheJustWrittenRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java#setDayHours_leavesScalarUntouched"
        status: pass
    human_judgment: false
  - id: D7
    description: "The bulk seven-row fan-out's response reflects the seven rows just written (both a set and a null/revert call), stays a single @Transactional operation, and is pinned as label-destructive (overwrites MANDATORY/PTO) — the fact the frontend's D-07 overwrite warning reads is visible in the roster response before the edit"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_responseCarriesTheSevenJustWrittenRows"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_null_responseShowsAllSevenAsNotSet"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_overwritesExistingLabels_soTheClientCanWarnFirst"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_labelPresenceIsVisibleInTheResponseBeforeTheEdit"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_isTransactional"
        status: pass
      - kind: unit
        ref: "gradlew test (full backend suite)"
        status: pass
    human_judgment: false
  - id: D8
    description: "PUT /api/v1/desks/{deskId}/agents/{agentId}/day-hours/{day} is reachable over HTTP, structurally distinct from the surviving bulk contracted-hours endpoint, delegates to setDayHours, and returns a clean 400 (not 500) on invalid input"
    requirement: "UPL-03"
    verification:
      - kind: other
        ref: "./gradlew compileJava; grep -q 'day-hours/{day}' DeskAgentController.java"
        status: pass
    human_judgment: true
    rationale: "No controller/integration test exercises the actual HTTP call in this codebase (no @WebMvcTest or MockMvc harness exists for DeskAgentController) — verified only by compilation and structural grep checks against the source, matching this project's existing test-coverage convention (DeskAgentServiceContractedHoursTest et al. are @DataJpaTest service-level, not controller-level). Recorded as unrun-verify entry #4 in .planning/WINDOWS.md."

duration: 14min
completed: 2026-08-22
status: complete
---

# Phase 13 Plan 02: Per-Weekday Edit Path Summary

**New `PUT .../day-hours/{day}` endpoint and `DeskAgentService.setDayHours` upsert a single `agent_day_hours` row at a time — provably leaving the other six untouched — closing audit finding I-3 by construction, while the surviving seven-row bulk fan-out (`setContractedHours`) is pinned as transactional and explicitly label-destructive.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-08-22T00:23:25Z
- **Completed:** 2026-08-22T00:37:39Z
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- `AgentDayHoursRepository.findByAgent_IdAndDayOfWeek` — single-weekday finder, not tenant-scoped by signature (matches the file's existing `deleteByAgent_Id` convention); callers must resolve tenant scope first
- `DeskAgentService.setDayHours` — a `@Transactional` single-row upsert/delete covering all five combo states (numeric, MANDATORY, PTO, not-set/clear, and the rejected empty-body case), with mandatory tenant+desk-scoped agent resolution before any per-day repository call (closes T-13-05), reject-not-clamp range validation (0–24 inclusive), and a response that reflects the row just written
- New `PUT /api/v1/desks/{deskId}/agents/{agentId}/day-hours/{day}` endpoint and `SetDayHoursRequest` DTO, structurally distinct from the surviving `PUT .../contracted-hours` bulk endpoint (P-05)
- `DeskAgentServiceDayHoursTest` (13 tests) proves the D-05 structural property — a numeric edit captures and re-asserts the other six rows' `id`/`hours`/`dayOffType` are byte-identical — plus label encoding, not-set-deletes-the-row, range rejection, cross-tenant rejection, response freshness, and scalar non-interference
- `DeskAgentServiceContractedHoursTest` extended with 5 new tests pinning the bulk fan-out's transactional, label-destructive, and fresh-response contract; `setContractedHours` gained an explicit `flush()` after its recreate loop so the read-after-write ordering is deterministic rather than implicit
- Full `./gradlew test` backend suite green (7m32s)

## Task Commits

Each task was committed atomically (Task 1 and Task 3 used TDD: test → feat/test):

1. **Task 1: Single-weekday upsert in DeskAgentService** (tdd)
   - `ad93c9b` test(13-02): add failing test for per-weekday setDayHours upsert
   - `294d62e` feat(13-02): single-weekday upsert setDayHours (D-05)
2. **Task 2: PUT day-hours endpoint and request DTO** (auto)
   - `12e1a3d` feat(13-02): PUT day-hours/{day} endpoint and SetDayHoursRequest DTO
3. **Task 3: Bulk fan-out returns a fresh seven-row response** (tdd)
   - `5b06802` test(13-02): pin bulk fan-out's transactional and label-destructive contract

**Plan metadata:** commit pending (this SUMMARY + STATE/ROADMAP/REQUIREMENTS update)

_TDD gate check: Task 1's RED (`ad93c9b`) precedes GREEN (`294d62e`) — confirmed by compiling the test against the pre-change service (`./gradlew compileTestJava`), observing 14 "cannot find symbol: setDayHours" errors, then implementing. Task 3's five new tests passed without any production-code change (the read-after-write ordering was already correct from Plan 01) — investigated per the fail-fast rule and confirmed as a genuine "feature already exists" case, not a test bug; the explicit `flush()` addition (committed alongside the tests) makes that ordering guarantee deterministic rather than dependent on Hibernate's implicit auto-flush before a derived-query read._

## Files Created/Modified
- `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` - adds `findByAgent_IdAndDayOfWeek` single-weekday finder
- `src/main/java/com/wfm/service/DeskAgentService.java` - new `setDayHours` method + `upsertDayHoursRow` helper; `setContractedHours` gains an explicit post-write `flush()`
- `src/main/java/com/wfm/dto/SetDayHoursRequest.java` - new record: `hours`, `dayOffType`, `clearRow`, all nullable
- `src/main/java/com/wfm/controller/DeskAgentController.java` - new `PUT .../day-hours/{day}` endpoint delegating to `setDayHours`
- `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` - new 13-test regression suite for `setDayHours`
- `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` - 5 new tests pinning the bulk fan-out's transactional/label-destructive/fresh-response contract

## Decisions Made
- Adopted PLAN.md's P-04/P-05/P-06/P-07 planner decisions verbatim (reject not clamp; two distinct endpoints; scalar left untouched by per-cell edits; D-07 warning computed client-side from data already sent — see 13-03)
- Added an explicit `agentDayHoursRepository.flush()` after `setContractedHours`' recreate loop (previously the method's only flush sat before the delete's recreate, relying on Hibernate's auto-flush-before-query to make the response fresh) — makes the plan's Task 3 `<action>` wording ("the re-read must sit after the delete-and-recreate block and after `flush()`") literally true

## Deviations from Plan

None - plan executed exactly as written. (Task 3's tests passing without a required behavior change is documented above as a TDD gate note, not a deviation — the plan's own `<action>` explicitly scoped the change to "only as far as the tests require.")

## Issues Encountered
- Task 2's two HTTP-behavior acceptance criteria (`PUT .../day-hours/WEDNESDAY` returning 200/400) were not exercised by an actual HTTP call — this codebase has no controller/integration test harness for `DeskAgentController` (no `@WebMvcTest`/`MockMvc` setup exists for it). Verified only via `./gradlew compileJava` and structural `grep` checks, consistent with the plan's own `<verify>` block for this task. Recorded as `unrun-verify` entry in `.planning/WINDOWS.md` (entry #4).
- Full `./gradlew test` run took ~7.5 minutes (includes solver test suite) — ran in background, confirmed `BUILD SUCCESSFUL`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `PUT .../day-hours/{day}` and the pinned `setContractedHours` response contract are both available for plan 13-03 (frontend per-cell combo UI + D-07 overwrite warning) to build against directly
- Recommend a human UAT pass exercising the new endpoint against a live desk before shipping, to close the growing set of `unrun-verify` items in `.planning/WINDOWS.md` (4 open as of this plan)

## Self-Check: PASSED

- All 6 key files confirmed present on disk (`[ -f ]`)
- All 4 task commit hashes confirmed present in `git log --oneline --all`
- `./gradlew test --tests "com.wfm.service.DeskAgentServiceDayHoursTest"` — 13/13 pass
- `./gradlew test --tests "com.wfm.service.DeskAgentServiceContractedHoursTest"` — 10/10 pass
- `./gradlew test` (full backend suite) — BUILD SUCCESSFUL
- Region-scoped gate: `setDayHours` method body contains no `deleteByAgent_Id` call — confirmed clean

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-22*
