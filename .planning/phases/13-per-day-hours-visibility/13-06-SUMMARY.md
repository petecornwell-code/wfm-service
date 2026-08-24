---
phase: 13-per-day-hours-visibility
plan: 06
subsystem: api
tags: [spring-boot, jpa, mockito, exception-handling, transactional-rollback]

requires:
  - phase: 13-02
    provides: setContractedHours bulk fan-out, setDayHours per-cell write path, GlobalExceptionHandler baseline
provides:
  - Inclusive 0-24 rejection in DeskAgentService.setContractedHours, matching setDayHours' existing bound
  - GlobalExceptionHandler.handleTypeMismatch mapping MethodArgumentTypeMismatchException to a non-leaking 400
  - A failure-injection test proving the bulk fan-out's transactional rollback with a genuine mid-loop failure
affects: [13-per-day-hours-visibility, future-desk-agent-endpoints]

actuals:
  tokens: 21000
  tasks: 3
  commits: 6

tech-stack:
  added: []
  patterns:
    - "@MockitoSpyBean + argument-matched doThrow() stubbing to inject a genuine mid-loop repository failure, instead of reflecting on @Transactional's presence"
    - "@Transactional(propagation = Propagation.NOT_SUPPORTED) on a single @DataJpaTest method to suppress the rollback-only test wrapper so the service's own transaction genuinely commits/rolls back"

key-files:
  created:
    - src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java
    - src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java
  modified:
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
    - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java
    - .planning/phases/13-per-day-hours-visibility/13-02-PLAN.md

key-decisions:
  - "Adopted planner decisions P-16/P-17/P-18/P-19 verbatim: MockitoSpyBean + NOT_SUPPORTED + argument-matched stubbing is feasible without a new dependency; the WR-02 proof is a direct handler unit test rather than a new MockMvc harness; the bulk range message names the field; exactly one new exception type is intercepted"
  - "Added Mockito.clearInvocations(agentDayHoursRepository) immediately before stubbing the THURSDAY failure, so the atLeast(4) save-count verification isolates the failing call's own invocations from the 7 baseline-seeding saves in the same test method"
  - "Routed DeskAgentServiceBulkRollbackTest's @AfterEach cleanup through DeskAgentService.setContractedHours(..., null) instead of calling the @MockitoSpyBean-wrapped AgentDayHoursRepository.deleteByAgent_Id directly — the spy's delegate does not carry Spring Data's self-transactional proxy behaviour, so a direct write call with no ambient transaction throws jakarta.persistence.TransactionRequiredException (Rule 3 blocking fix)"

requirements-completed: [MDL-02, UPL-03, UPL-04, UPL-05]

coverage:
  - id: D1
    description: "Bulk contracted-hours value above 24 is rejected server-side with an inclusive 0-24 rule matching setDayHours, and a rejected/mid-way-failed edit persists nothing"
    requirement: "UPL-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_above24_isRejectedAndPersistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_above24_leavesExistingRowsAndLabelsUntouched"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java#setContractedHours_exactly24_isAccepted"
        status: pass
    human_judgment: false
  - id: D2
    description: "A malformed {day} path segment maps to a clean 400 whose body names only the controller's own parameter, never the rejected value or internal type"
    requirement: "MDL-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java#handleTypeMismatch_returns400WithParameterNameOnly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java#preExistingMappings_stillReturnTheirOriginalStatuses"
        status: pass
    human_judgment: true
    rationale: "The unit test proves the response GlobalExceptionHandler.handleTypeMismatch builds. It does not prove Spring's dispatch from an actual malformed URL to that handler over HTTP (no MockMvc/@WebMvcTest harness exists for this codebase's web layer, P-17). Tracked as WINDOWS.md item 7 and the plan's own declared residual gap — the task 2 <human-check> block was not run, no live backend available in this executor session."
  - id: D3
    description: "The bulk fan-out's rollback is proven by a genuine mid-loop failure (THURSDAY row, 4th of 7) rather than annotation reflection: zero of the seven new rows persist, all seven original rows survive with original ids and hours, and the Agent scalar rolls back too"
    requirement: "UPL-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java#setContractedHours_failureOnTheFourthOfSevenRowWrites_persistsNothing"
        status: pass
    human_judgment: false

duration: 14min
completed: 2026-08-24
status: complete
---

# Phase 13 Plan 06: Bulk Hours Range Validation, Malformed-Path 400 Mapping, and Rollback Proof Summary

**Inclusive 0-24 upper bound on the bulk contracted-hours endpoint, a `MethodArgumentTypeMismatchException` handler for malformed path segments, and a `@MockitoSpyBean`-injected mid-loop failure test proving the bulk fan-out's transactional rollback.**

## Performance

- **Duration:** 14 min (active execution) + a full `./gradlew test --rerun-tasks` verification run (~7.5 min)
- **Started:** 2026-08-24T12:39:46Z
- **Completed:** 2026-08-24T12:53:58Z
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- `DeskAgentService.setContractedHours` now rejects any value outside `[0, 24]`, matching `setDayHours`' existing reject-not-clamp rule (closes WR-01/WR-03 and the `coincidental_reliance_items` entry in `13-VERIFICATION.md`)
- `GlobalExceptionHandler.handleTypeMismatch` maps `MethodArgumentTypeMismatchException` to a clean 400 that names only the declared parameter — never the rejected token or the internal target type (closes WR-02; T-13-25/26)
- `DeskAgentServiceBulkRollbackTest` injects a genuine failure on the fourth of seven row writes and proves zero partial state persists — closes both `behavior_unverified_items` entries in `13-VERIFICATION.md`, replacing the annotation-reflection-only evidence
- `13-02-PLAN.md`'s Task 2 `<read_first>` note corrected so the planning record no longer claims no new exception handler was needed

## Task Commits

Each task was committed atomically (TDD RED/GREEN pairs for tasks 1-2, single commit for task 3 per its plan type):

1. **Task 1 RED: failing upper-bound tests** - `6cc76f7` (test)
2. **Task 1 GREEN: reject bulk hours above 24** - `ab0af9d` (feat)
3. **Task 2 RED: failing MethodArgumentTypeMismatchException test** - `6325203` (test)
4. **Task 2 GREEN: handleTypeMismatch + 13-02-PLAN.md correction** - `534e9ed` (feat)
5. **Task 3: bulk rollback failure-injection test** - `dcc6e06` (test)

**Plan metadata:** (this commit)

## Files Created/Modified

- `src/main/java/com/wfm/service/DeskAgentService.java` - `setContractedHours` guard now rejects `> 24` in addition to `< 0`, throwing "Contracted hours per day must be between 0 and 24"
- `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` - new `handleTypeMismatch(MethodArgumentTypeMismatchException)` returning a 400 that names only the parameter
- `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` - 3 new tests: `setContractedHours_above24_isRejectedAndPersistsNothing`, `setContractedHours_above24_leavesExistingRowsAndLabelsUntouched`, `setContractedHours_exactly24_isAccepted`
- `src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java` - new file, first test in the `com.wfm.controller` package; direct instantiation, no Spring context
- `src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java` - new file; `@MockitoSpyBean` + `Propagation.NOT_SUPPORTED` failure-injection proof
- `.planning/phases/13-per-day-hours-visibility/13-02-PLAN.md` - Task 2 `<read_first>` note corrected with `corrected by 13-06: WR-02` marker

## Decisions Made

- Adopted P-16/P-17/P-18/P-19 verbatim from the plan's `<planner_decisions>` — see frontmatter `key-decisions` for the full list
- Added `Mockito.clearInvocations(agentDayHoursRepository)` right before stubbing the THURSDAY failure so the `atLeast(4)` save-count verification measures only the failing call's own invocations, not the 7 baseline-seeding saves earlier in the same test method — this was necessary to make the assertion mean what the plan's action text says ("save was invoked at least four times across the failing call")
- Routed the rollback test's `@AfterEach` cleanup through `DeskAgentService.setContractedHours(desk.getId(), agent.getId(), null)` rather than calling `agentDayHoursRepository.deleteByAgent_Id` (the spy) directly — see Deviations below

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `@MockitoSpyBean`'s delegate does not carry Spring Data's self-transactional proxy behaviour, breaking direct-call cleanup in `@AfterEach`**
- **Found during:** Task 3 (bulk rollback failure-injection test)
- **Issue:** The plan's `<action>` text described an `@AfterEach` that "deletes the committed fixtures in dependency order — day-hours rows, then agents, then desks" by calling the repositories directly. When `agentDayHoursRepository.deleteByAgent_Id(agent.getId())` was called directly from `@AfterEach` (with `Propagation.NOT_SUPPORTED` on the test method meaning no ambient transaction exists for the whole test lifecycle, including `@AfterEach`), it threw `org.springframework.dao.InvalidDataAccessApiUsageException` caused by `jakarta.persistence.TransactionRequiredException`. Investigation showed this is specific to `agentDayHoursRepository` being a `@MockitoSpyBean`: a plain (non-spied) Spring Data repository bean is self-transactional (each direct call opens its own transaction if none is ambient), but the spy's delegate does not preserve that behaviour for a write call made with no ambient transaction. `agentRepository`/`deskRepository` are plain `@Autowired` beans (not spies), so their direct `deleteById` calls in the same `@AfterEach` worked without incident.
- **Fix:** Routed the day-hours cleanup through `deskAgentService.setContractedHours(desk.getId(), agent.getId(), null)` instead, which opens its own real `@Transactional` boundary (DeskAgentService is not spied) and clears the rows via the same `deleteByAgent_Id` call, but now within an ambient transaction. `agentRepository.deleteById`/`deskRepository.deleteById` remained as direct calls since those beans are unaffected.
- **Files modified:** `src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.service.DeskAgentServiceBulkRollbackTest"` passes cleanly; re-running the class twice in a row (implying prior teardown succeeded and left no leaked fixtures) also passes
- **Committed in:** `dcc6e06` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to make the plan's own described `@AfterEach` cleanup strategy actually work against this specific harness detail (`@MockitoSpyBean` over a Spring Data repository). No scope creep — the fix stays entirely within the new test file the plan already scoped to this task.

## Issues Encountered

None beyond the deviation documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Every write path into `agent_day_hours` (parser, per-cell endpoint, bulk fan-out) now obeys one inclusive 0-24 rule
- Both `behavior_unverified_items` entries in `13-VERIFICATION.md` are closed with genuine failure-injection evidence, not annotation reflection
- The malformed-path-segment 500 (WR-02) is fixed with a non-leaking 400, though its end-to-end HTTP dispatch remains an open human-check (WINDOWS.md item 7) — no MockMvc harness exists for this codebase's web layer, by deliberate P-17 scope decision
- Full backend suite: 315 tests, 0 failures, 0 errors, 0 skipped (up from the 309-test baseline) — `./gradlew test --rerun-tasks`
- Frontend build unaffected: `npm --prefix frontend run build` exits 0 (no frontend files touched this plan)
- `git diff --quiet build.gradle` confirms no new dependency was added
- 6 open items remain in `.planning/WINDOWS.md` for phase 13, all pre-existing `unrun-verify`/`deviation` entries requiring a live BambooHR-configured desk, plus the new item 7 recorded by this plan for the task 2 HTTP-level `<human-check>`

## Self-Check: PASSED

- `[ -f src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java ]` → FOUND
- `[ -f src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java ]` → FOUND
- `git log --oneline --all --grep="13-06"` → 5 matching commits found (6cc76f7, ab0af9d, 6325203, 534e9ed, dcc6e06)
- All plan-level `<verification>` commands re-run clean: `./gradlew test --rerun-tasks` → 315 tests, 0 failures; `npm --prefix frontend run build` → exit 0; `git diff --quiet build.gradle` → exit 0

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-24*
