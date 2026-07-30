---
phase: 09-agent-data-model-foundation
plan: 04
subsystem: api
tags: [spring-boot, jpa, bamboohr-integration, upload]

# Dependency graph
requires:
  - phase: 09-01
    provides: "AgentNameSplitter utility + Agent.firstName/lastName scalar fields"
  - phase: 09-02
    provides: "AgentDayHours entity + AgentDayHoursRepository.deleteByAgent_Id"
provides:
  - "BambooHR refresh write-path populates firstName/lastName via AgentNameSplitter (D-07)"
  - "Desk-upload write-path populates firstName/lastName at both name-setting sites (D-11)"
  - "Desk-upload clearDesk deletes agent_day_hours rows so re-assigned agents inherit no stale per-day hours (D-10 clear side)"
affects: [09-05, 09-06, 10-enriched-upload-parsing, 11-bamboohr-merge-engine]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Live write-paths call AgentNameSplitter.split(...) immediately after the existing setName(...) call, never replacing it"
    - "clearDesk-style per-agent cleanup loops call the new child-repository deleteByAgent_Id alongside existing preference/exception deletes"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/integration/BambooRefreshService.java
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java

key-decisions:
  - "BambooRefreshService only has one setName call site (existing-agent refresh loop); no new-agent creation branch exists in that file, so only one split call was needed there"
  - "DeskAssignmentUploadService constructor gained a new AgentDayHoursRepository parameter, injected in the same explicit field-injection style as agentExceptionRepository/agentPreferenceRepository"

requirements-completed: [MDL-01, MDL-02]

# Metrics
duration: 15min
completed: 2026-07-30
---

# Phase 09 Plan 04: Live Write-Path Consistency Summary

**BambooHR refresh and desk-upload now populate firstName/lastName via the shared AgentNameSplitter, and desk-clear deletes stale per-day-hours rows — keeping the new agent data model coherent across every live write-path, not just the one-time migration.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-07-30T14:35:00Z (approx)
- **Completed:** 2026-07-30T14:50:00Z (approx)
- **Tasks:** 2
- **Files modified:** 5 (2 source, 3 test)

## Accomplishments
- `BambooRefreshService` splits `emp.displayName()` into `firstName`/`lastName` via `AgentNameSplitter` at the existing-agent refresh loop's `setName` site, leaving the searchable `name` column untouched (D-07).
- `DeskAssignmentUploadService` splits both name sources — the BambooHR-cache-derived new-agent name and the spreadsheet-override name — at their respective `setName` sites (D-11).
- `DeskAssignmentUploadService.clearDesk` now deletes each cleared agent's `agent_day_hours` rows via the newly-injected `AgentDayHoursRepository`, so a re-assigned agent inherits no stale per-day hours (D-10 clear side).

## Task Commits

Each task was committed atomically:

1. **Task 1: Split displayName into first/last on BambooHR refresh (D-07)** - `73c7cc9` (feat)
2. **Task 2: Upload-path name split (D-11) + per-day-hours delete on clear (D-10 clear side)** - `8209d97` (feat)

_Note: Task 2's commit also includes the Rule-3 test-file fixes required by the constructor signature change (see Deviations below)._

## Files Created/Modified
- `src/main/java/com/wfm/integration/BambooRefreshService.java` - added `AgentNameSplitter` import and split call at the existing-agent refresh `setName` site
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` - added `AgentNameSplitter`/`AgentDayHoursRepository` imports, constructor-injected `agentDayHoursRepository` field, split calls at both name-setting sites, and `deleteByAgent_Id` call in `clearDesk`
- `src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java` - updated constructor call + added mock for new `AgentDayHoursRepository` parameter
- `src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java` - same
- `src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java` - same

## Decisions Made
- None beyond what the plan specified — implementation followed the pattern map's exact call-site guidance verbatim.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated three existing unit tests for the changed constructor signature**
- **Found during:** Task 2 (Upload-path name split + per-day-hours delete on clear)
- **Issue:** Adding a new constructor parameter (`AgentDayHoursRepository agentDayHoursRepository`) to `DeskAssignmentUploadService` broke compilation of three existing tests (`DeskAssignmentUploadLegacyShapeTest`, `DeskAssignmentUploadNonSchedulableRejectTest`, `DeskAssignmentUploadEnrichedShapeTest`) that constructed the service directly with the old argument list.
- **Fix:** Added an `AgentDayHoursRepository agentDayHoursRepository` mock field to each test's setup and passed it through the constructor call in the correct position, matching the field-injection order in the modified service.
- **Files modified:** `src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java`, `src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java`, `src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java`
- **Verification:** `./gradlew compileJava compileTestJava` and `./gradlew test --tests "com.wfm.service.*DeskAssignmentUpload*"` both succeed.
- **Committed in:** `8209d97` (part of Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to keep the build compiling after the constructor signature change; no scope creep beyond the plan's own file list intent.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Both live agent write-paths (BambooHR refresh, desk upload) now stay consistent with the new firstName/lastName and per-day-hours model on every future run, not just at migration time.
- `agent_day_hours` rows no longer leak stale values across desk re-assignment, which Phase 10/11 (enriched upload parsing, BambooHR merge engine) can build on without needing their own cleanup logic.
- No blockers identified for subsequent phase 09 plans or downstream phases.

---
*Phase: 09-agent-data-model-foundation*
*Completed: 2026-07-30*
