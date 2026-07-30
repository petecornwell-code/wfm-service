---
phase: 09-agent-data-model-foundation
plan: 03
subsystem: solver
tags: [timefold, java, spring-boot, jpa, bigdecimal]

# Dependency graph
requires:
  - phase: 09-agent-data-model-foundation (plan 02)
    provides: AgentDayHours child entity + AgentDayHoursRepository (findByTenantIdAndDeskId, deleteByAgent_Id)
provides:
  - "SolverService.resolveEffectiveHours static resolver: exception > per-day > schedule-default precedence (D-03/D-04)"
  - "All 3 getEffectiveHours call sites (computeAgentDayConfigs + both runPreSolveValidation checks) migrated to resolveEffectiveHours"
  - "AgentDayHoursRepository constructor-injected into SolverService"
  - "Behaviour-equivalence proof (Success Criterion 4) via unit test"
affects: [10-enriched-upload-parsing, 11-bamboohr-merge-engine]

# Tech tracking
tech-stack:
  added: []
  patterns: [package-private static extraction for solver-logic unit testing, containsKey-based precedence (never null-check)]

key-files:
  created:
    - src/test/java/com/wfm/service/SolverServiceEffectiveHoursResolutionTest.java
  modified:
    - src/main/java/com/wfm/service/SolverService.java

key-decisions:
  - "Old private getEffectiveHours(Agent, LocalDate, Map, Schedule) instance method deleted entirely — agent.getContractedHoursPerDay() is no longer consulted anywhere in the resolution path, per MDL-02"
  - "agentDayHoursMap is built twice (main solve flow + inside runPreSolveValidation), mirroring the existing agentExceptionMap duplication pattern — validation cannot diverge from the actual solve (RESEARCH Pitfall 1)"

patterns-established:
  - "Pattern: package-private static resolver methods threaded through multiple call sites via locally-built Map<UUID, Map<K,V>> lookups, never JPA navigation — same idiom as buildAgentDaysOffMap"

requirements-completed: [MDL-02, MDL-03]

# Metrics
duration: 25min
completed: 2026-07-30
---

# Phase 9 Plan 3: Effective Hours Resolution Summary

**Extracted `SolverService.resolveEffectiveHours` static resolver implementing exception-over-per-day-over-schedule-default precedence, threaded through all 3 former `getEffectiveHours` call sites, with a behaviour-equivalence unit test pinning Success Criterion 4.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-30T14:32:56Z (approx, per STATE.md phase start)
- **Completed:** 2026-07-30T14:53:17Z
- **Tasks:** 2
- **Files modified:** 2 (1 new test, 1 modified source)

## Accomplishments
- Package-private static `resolveEffectiveHours(exceptionMap, dayHoursMap, date, scheduleDefaultHours)` implementing D-03/D-04 precedence, extracted following the `buildAgentDaysOffMap` testability precedent
- Full TDD RED→GREEN cycle: failing test committed first (compile error), then implementation made it pass
- `AgentDayHoursRepository` constructor-injected into `SolverService`; per-desk `agentDayHours` bulk-loaded alongside existing `exceptions` load
- `agentDayHoursMap` built at both required sites (main solve flow + inside `runPreSolveValidation`), mirroring the existing `agentExceptionMap` duplication — closing the validation/solve divergence risk called out as the highest-risk item in RESEARCH.md
- All 3 `getEffectiveHours` call sites migrated to `resolveEffectiveHours`; old instance method deleted — agent scalar `contractedHoursPerDay` is no longer consulted by the resolution path
- Equivalence test proves a uniform 7-weekday map produces identical `effectiveHours` to the pre-migration scalar-only logic for every `DayOfWeek`

## Task Commits

Each task was committed atomically (TDD RED/GREEN split for Task 1):

1. **Task 1 (RED): add failing test for resolveEffectiveHours** - `a96bdc2` (test)
2. **Task 1 (GREEN): extract package-private static resolveEffectiveHours** - `09b14b4` (feat)
3. **Task 2: thread agentDayHoursMap through all 3 call sites** - `d44e941` (feat)

_TDD task had 2 commits (test → feat) per the RED/GREEN cycle; Task 2 was not TDD-flagged in the plan and was committed as a single feat commit._

## Files Created/Modified
- `src/test/java/com/wfm/service/SolverServiceEffectiveHoursResolutionTest.java` - Unit tests for precedence, zero-day, absent-day, and behaviour-equivalence (Success Criterion 4), no Spring context
- `src/main/java/com/wfm/service/SolverService.java` - Added `resolveEffectiveHours` static resolver; injected `AgentDayHoursRepository`; bulk-loads `agentDayHours` per desk; builds `agentDayHoursMap` at 2 sites; migrated all 3 call sites; deleted old `getEffectiveHours` instance method

## Decisions Made
- Deleted the old `getEffectiveHours` instance method outright rather than leaving it as dead code, since the plan explicitly calls for the scalar to no longer be consulted anywhere in the resolution path
- Threaded the raw `List<AgentDayHours>` into `runPreSolveValidation` (rather than passing the pre-built map) to match the existing convention where `runPreSolveValidation` builds its own local copies of `agentDaysOffMap`/`agentExceptionMap` from raw lists — keeps the "two independent builds" pattern consistent across all three child-table maps

## Deviations from Plan

None - plan executed exactly as written. Task 1 followed TDD (test="true"); Task 2 was plain `type="auto"` per plan frontmatter and was implemented and verified as a single unit, matching the plan's own task-type declaration.

## Issues Encountered

Worktree HEAD was found detached from the expected base commit (`d1791274fd90a0f831e0ef3cbbe5497ca97d8f71`, phase-09 tracking-update commit) at execution start — the worktree's HEAD (`f173c51`) was an ancestor of the expected base, not a descendant, so `.planning/phases/09-agent-data-model-foundation/` and other phase-09 files were missing. Corrected via `git reset --hard d1791274fd90a0f831e0ef3cbbe5497ca97d8f71` per the worktree_branch_check protocol before any file reads or edits. No commits were lost (worktree branch `worktree-agent-a47876ff36516e59d` had no prior commits beyond the base).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `resolveEffectiveHours` and the fully-threaded `agentDayHoursMap` are ready for Plan 09-04+ (DeskAgentService fan-out writes, BambooRefreshService/DeskAssignmentUploadService name-split call sites, and the migration) to build on
- `./gradlew test` (full suite) passes with 0 failures after this plan's changes — no regressions introduced in solver, persistence, or integration tests
- No blockers for subsequent phase-09 plans

---
*Phase: 09-agent-data-model-foundation*
*Completed: 2026-07-30*
