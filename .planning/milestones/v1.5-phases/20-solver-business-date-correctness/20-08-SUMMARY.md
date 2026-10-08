---
phase: 20-solver-business-date-correctness
plan: 08
subsystem: solver
tags: [business-date, day-start, timeslot-generation, desk-configuration, gate-removal]

# Dependency graph
requires:
  - phase: 20-01
    provides: "DayWindow re-anchored to take an explicit day-start anchor, and MidnightBoundaryFixture/MidnightBoundaryRegressionTest proving non-midnight anchors are safe at the model layer"
  - phase: 20-05
    provides: "ScheduleConstraintProvider's joins and anchors fully migrated off the PENDING_DESK_ANCHOR placeholder and guarded by BusinessDateJoinGuardTest/MidnightTimeArithmeticGuardTest"
  - phase: 20-06
    provides: "ScheduleOutputService and ShiftLibraryGenerationService migrated to business-date key positions, with the join guard's headline set-equality test GREEN"
  - phase: 20-07
    provides: "StaffingRequirementService's destructive demand-upload delete migrated to business-date range derivation, closing the guard's last documented structural blind spot"
provides:
  - "DeskService.setDayStart accepts any 15-minute boundary, refusing anything else by name at save time, as one visible increment-independent condition -- the midnight-only gate is gone"
  - "The generation-time tiling refusal (TimeslotGeneratorService.requireDayStartTiles), previously written but unreachable, is now reachable through the real save-then-generate path and PROVEN to fire, with a positive control"
  - "The desk-configuration day-start cell (DeskManagement.tsx) states the desk's anchor and the accepted 15-minute range in both branches, truthfully and before the gate removal, and stays read-only"
affects: [21-overnight-template]

# Actuals (#2632)
actuals:
  tokens: 3873
  tasks: 3
  commits: 3
  plan_head_before: 82fa58e

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A gate-removal plan's disclosure copy is corrected BEFORE the gate itself is deleted, and the gate deletion is landed last and alone (D-01) -- the window in which the interface could promise a wider range than the backend actually accepted never exists at any commit"
    - "A refusal written and unit-tested against a static method in an earlier phase is proven REACHABLE by a dedicated test driving the real caller chain end-to-end (save, then generate), with a positive control alongside the negative case so 'fires on everything' cannot be mistaken for 'fires correctly'"
    - "A save-time validation stays increment-INDEPENDENT (a fixed 15-minute modulus) precisely because the thing it would need to validate against (the generation increment) is not desk state -- it arrives per call, inferred from an uploaded spreadsheet -- so a correctness check can only be increment-aware at generation time, never at save time"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskDayStartGenerationReachabilityTest.java
  modified:
    - frontend/src/pages/DeskManagement.tsx
    - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
    - src/main/java/com/wfm/service/DeskService.java

key-decisions:
  - "The plan's read_first note described a comment block preceding the day-start cell in BOTH the edit and display branches, but the file as found only had one (above the edit branch) -- the display branch's cell had no preceding comment at all. Since the plan's acceptance criteria explicitly required 'both comment blocks are rewritten', a matching comment block was added above the display-branch cell too, duplicating the edit branch's (rewritten) reasoning, rather than leaving the display branch undocumented. This is a minor interpretation choice to satisfy the acceptance criteria as written, not a correctness fix."
  - "The two former gate test cases were renamed rather than kept under their old (now-inaccurate) names, since the six cases the plan requires to stay byte-identical are identified by name in the plan's own verify step -- renaming the two that changed behavior, rather than reusing stale names for new behavior, keeps that identity check meaningful."
  - "The reachability test reuses DeskServiceDayStartTest's exact @DataJpaTest/@MockitoBean wiring shape (ShiftLibraryValidationService mocked, DeskService and InMemoryScheduleStore imported) plus TimeslotGeneratorService.class, mirroring the precedent already established by ShiftTemplateTracerTest and MidnightTimeslotPostgresTest for importing TimeslotGeneratorService directly into a @DataJpaTest context rather than inventing a second wiring style."
  - "21:15 (1275 minutes from midnight) and 21:30 (1290 minutes) were chosen as the negative/positive control pair for the reachability test because they are a genuine non-tiling/tiling pair against a 30-minute increment (1275 is not a whole multiple of 30; 1290 is), exactly as the plan specifies, rather than a contrived pair that would pass for the wrong reason."

patterns-established:
  - "A gate-removal's final commit carries the gate change alone (verified by `git log --name-only` on that commit), so a future revert of exactly that commit restores the gate without touching any guard, test, or disclosure-copy work that came before it."

requirements-completed: [SOLV-01]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "The desk-configuration day-start cell (DeskManagement.tsx, both edit and display branches) states the desk's anchor and the accepted 15-minute range, with the stale overnight-scheduling-restriction claim removed, before the backend gate is deleted"
    requirement: "SOLV-01"
    verification:
      - kind: other
        ref: "grep -c 'only 00:00 is supported until overnight scheduling lands' DeskManagement.tsx -> 0"
        status: pass
      - kind: other
        ref: "grep -c '15-minute' DeskManagement.tsx -> 6 (both cells plus both rewritten comment blocks)"
        status: pass
      - kind: other
        ref: "npm --prefix frontend run build -> exit 0"
        status: pass
      - kind: other
        ref: "git diff frontend/src/api/client.ts -> empty (file unchanged)"
        status: pass
    human_judgment: false
  - id: D2
    description: "DeskServiceDayStartTest and the new DeskDayStartGenerationReachabilityTest assert the post-change 15-minute-boundary contract and the generation-time reachability contract, both written and committed RED against the still-midnight-only gate"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "com.wfm.service.DeskServiceDayStartTest (observed RED at commit 60b6866: 7 failed / 16 total)"
        status: pass
      - kind: unit
        ref: "com.wfm.service.DeskDayStartGenerationReachabilityTest (observed RED at commit 60b6866: 2 failed / 2 total)"
        status: pass
      - kind: other
        ref: "grep -cE for the six untouched case names in DeskServiceDayStartTest.java -> 6"
        status: pass
    human_judgment: false
  - id: D3
    description: "DeskService.setDayStart's midnight-only refusal is replaced by a 15-minute-boundary refusal naming the rejected value, as one visible increment-independent condition; the null check, tenant-scoped lookup, equal-value early return, and accepted-schedule refusal (no bypass) are byte-identical; this lands as the phase's final commit, carrying only this file"
    requirement: "SOLV-01"
    verification:
      - kind: other
        ref: "grep -c 'Day start other than 00:00 is not yet supported' DeskService.java -> 0"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.DeskServiceDayStartTest --tests com.wfm.service.DeskDayStartGenerationReachabilityTest -> BUILD SUCCESSFUL (both GREEN)"
        status: pass
      - kind: other
        ref: "awk over setDayStart method body | grep -cE 'ConflictException|findByIdAndTenantId|findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc' -> 3"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.DeskServiceSchedulingModeTest --tests com.wfm.service.TimeslotGeneratorServiceTest --tests com.wfm.service.TimeslotGeneratorBusinessDateTest --tests com.wfm.service.MidnightTimeArithmeticGuardTest --tests com.wfm.service.BusinessDateJoinGuardTest --tests com.wfm.service.AgentDayDerivationGuardTest -> BUILD SUCCESSFUL"
        status: pass
      - kind: other
        ref: "git log --oneline -1 --name-only on the final commit -> src/main/java/com/wfm/service/DeskService.java (only this file)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The full unfiltered suite is fully green at the phase's final commit, and no desk row's stored day start was changed by this phase"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "./gradlew test (unfiltered) -> BUILD SUCCESSFUL in 10m31s; 1187 tests, 4 skipped, 0 failures, 0 errors across 193 JUnit XML files (baseline before this plan: 1180 tests / 192 files / 0 failures-errors; the +7 tests / +1 file are this plan's own new and expanded cases)"
        status: pass
      - kind: other
        ref: "No task in this plan writes a desk row outside a test fixture; git diff of the final commit touches only DeskService.java's validation logic, not any migration or seed data"
        status: pass
    human_judgment: false
---

# Phase 20 Plan 08: Open Day Start to Any 15-Minute Boundary Summary

**Deleted the midnight-only day-start gate as the phase's final commit, replacing it with an increment-independent 15-minute-boundary refusal, after proving the generation-time tiling refusal now fires through the real save-then-generate path.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-10-01 (session start)
- **Completed:** 2026-10-01T20:44:14-04:00
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- Corrected the desk-configuration cell's day-start disclosure copy in both branches of `DeskManagement.tsx`, before the gate removal made the old claim false, and rewrote both comment blocks to cite SOLV-01 rather than phase numbers.
- Rewrote `DeskServiceDayStartTest`'s two gate cases from "non-midnight is refused" to "a 21:00 day start is accepted, persists, and reads back" (service and controller levels), added a parameterised case for the accepted boundaries 00:15/06:30/21:15/23:45, and added a case asserting 21:07 is refused by name -- all committed RED.
- Created `DeskDayStartGenerationReachabilityTest`, proving the generation-time tiling refusal (`TimeslotGeneratorService.requireDayStartTiles`) is reachable through the real save-then-generate path for the first time (21:15 refused against a 30-minute increment; 21:30 succeeds as a positive control) -- also committed RED.
- Replaced `DeskService.setDayStart`'s midnight-only refusal with one visible 15-minute-boundary condition naming the rejected value, rewrote the method's javadoc, and left the null check, tenant-scoped lookup, equal-value no-op, and accepted-schedule refusal byte-identical -- landed alone as the phase's final commit.

## Task Commits

Each task was committed atomically:

1. **Task 1: Correct the day-start disclosure copy** - `9c075f7` (docs)
2. **Task 2: The two refusals' expectations, written red** - `60b6866` (test)
3. **Task 3: Delete the gate, add the 15-minute refusal (phase's FINAL commit)** - `1d34d5f` (feat)

_No separate plan-metadata commit shown above; see below for this SUMMARY's own commit._

## Files Created/Modified
- `frontend/src/pages/DeskManagement.tsx` - Day-start cell copy corrected in both branches (15-minute-boundary statement, not an overnight-scheduling restriction); comment blocks rewritten citing SOLV-01; cell stays read-only, no input added.
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` - Two gate cases rewritten to the post-change contract; parameterised accepted-boundary case and rejected-value case added; six other cases left byte-identical.
- `src/test/java/com/wfm/service/DeskDayStartGenerationReachabilityTest.java` - New: proves the generation-time tiling refusal fires through the real save-then-generate path, with a positive control.
- `src/main/java/com/wfm/service/DeskService.java` - Midnight-only refusal replaced by a 15-minute-boundary refusal naming the rejected value; javadoc rewritten; everything else in the method byte-identical.

## Decisions Made
- The plan's read_first note for Task 1 described a comment block preceding the day-start cell in both branches of `DeskManagement.tsx`; only the edit branch actually had one. Since the acceptance criteria required both comment blocks to be rewritten, a matching comment was added above the display-branch cell too, duplicating the edit branch's rewritten reasoning rather than leaving that branch undocumented.
- The two rewritten gate test cases were given new names (`setDayStart_2100_acceptedPersistedAndReadsBackFromDeskRow`, `controller_setDayStart_2100_returnsResponseCarrying2100`) rather than reusing their old, now-inaccurate names, since the plan's own verify step identifies the six untouched cases by name -- keeping the renamed two visibly distinct from that list keeps the identity check meaningful.
- 21:15 and 21:30 against a 30-minute increment were chosen as the reachability test's negative/positive control pair because they are a genuine non-tiling/tiling pair (1275 and 1290 minutes from midnight, respectively), per the plan's own worked arithmetic.

## Deviations from Plan

None - plan executed exactly as written. (The one documentation discrepancy found -- the Task 1 read_first describing two comment blocks where the file held one -- was resolved per the acceptance criteria and is recorded above under Decisions Made, not as a Rule 1-4 deviation: nothing was broken, missing, or blocking; it was a judgment call about how literally to satisfy a stated acceptance criterion.)

## Issues Encountered
- The foreground `./gradlew test` run for the phase-close unfiltered suite exceeded the 10-minute tool timeout and was moved to background automatically; waited for completion (10m31s total) rather than treating the timeout as a failure, per this plan's own test-discipline guidance (run unfiltered, in the foreground, with a generous budget).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 20 (Solver Business-Date Correctness) is now complete: all 8 plans executed, SOLV-01 satisfied, and the day-start range opened as the phase's deliberately-last commit.
- Ready for `/gsd-verify-work 20` and phase close.
- Deferred, by scoping decision (not oversight): an editable day-start control (time picker plus error surfacing for both refusals) belongs to the overnight-template phase (Phase 21), per `20-CONTEXT.md`'s Deferred Ideas. No live desk's day start was changed by this phase; the live-desk migration to a non-midnight anchor is also deferred to Phase 21/beyond this milestone.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- All 4 key files found on disk (1 created, 3 modified).
- All 3 task commits (`9c075f7`, `60b6866`, `1d34d5f`) found in `git log --oneline --all`.
- Re-ran acceptance-criteria greps: stale disclosure phrase = 0, `15-minute` count = 6, midnight-only refusal = 0 -- all match the SUMMARY's claims.
