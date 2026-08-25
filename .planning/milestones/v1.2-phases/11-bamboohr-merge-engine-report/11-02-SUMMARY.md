---
phase: 11-bamboohr-merge-engine-report
plan: 02
subsystem: api
tags: [spring-boot, jpa, timefold-solver, merge-engine, react]

requires:
  - phase: 11-bamboohr-merge-engine-report
    plan: 01
    provides: AgentMergeService, MergeReportEntry, DeskAssignmentUploadResult.mergeReport, BambooHRSyncFailedException, fresh-sync-before-transaction upload restructure
provides:
  - Agent.workingDaysSource (V36 migration + WorkingDaysSource enum) — persisted provenance marker distinguishing a sheet-supplied working-days pattern from a BambooHR-derived one
  - BambooRefreshService.shouldDowngradeWorkingDaysKnown — refresh-side guard that never downgrades a spreadsheet-sourced pattern (D-15)
  - DeskAssignmentUploadResult.newlyEligibleAgents + Upload Results modal eligibility callout (D-14)
  - SolverService.arbitratePtoAgainstBambooWindow — D-09 window arbitration for recurring spreadsheet PTO
  - SolverService.unblockSheetWorkedDays — D-05 un-blocking of stale BambooHR MANDATORY rows
  - AgentMergeService.mergeWorkingPattern + FIELD_WORKING_PATTERN / OUTCOME_PATTERN_REPLACED — merge-report visibility for the D-05 week replacement
affects: []

actuals:
  tokens: 18200
  tasks: 4
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Persisted provenance marker + one-directional guard: Agent.workingDaysSource is set only by the upload path; BambooRefreshService reads it (shouldDowngradeWorkingDaysKnown) but never writes it, so BambooHR can never reclaim ownership of a week an operator corrected"
    - "Solve-time arbitration over in-memory lists: both SolverService.arbitratePtoAgainstBambooWindow and unblockSheetWorkedDays run on the allDaysOff list assembled in startSolve, before buildAgentDaysOffMap/runPreSolveValidation/setAgentDaysOff, adding no repository dependency and persisting nothing (D-10) — re-derived on every solve"
    - "Report-only merge helper mirroring mergeIdentityFields: AgentMergeService.mergeWorkingPattern decides nothing about what is persisted (the sheet's day group is already authoritative and already written by the agent_day_hours loop); it only appends a MergeReportEntry on gap-fill or genuine divergence, silent on agreement (D-11)"

key-files:
  created:
    - src/main/resources/db/migration/V36__add_agent_working_days_source.sql
    - src/main/java/com/wfm/model/WorkingDaysSource.java
    - src/test/java/com/wfm/service/WorkingDaysKnownTest.java
    - src/test/java/com/wfm/integration/WorkingDaysSourceGuardTest.java
    - src/test/java/com/wfm/service/PtoArbitrationTest.java
    - src/test/java/com/wfm/service/SheetPatternUnblockTest.java
    - src/test/java/com/wfm/integration/WorkingPatternMergeTest.java
  modified:
    - src/main/java/com/wfm/model/Agent.java
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/main/java/com/wfm/integration/BambooRefreshService.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/integration/AgentMergeService.java
    - frontend/src/api/client.ts
    - frontend/src/pages/ClientManagement.tsx
    - src/test/java/com/wfm/integration/MergeReportTest.java

key-decisions:
  - "Checkpoint resolved: PTO/pattern arbitration runs at solve time in SolverService (in-memory, re-derived every solve), not at merge-write time. Selected explicitly by the human operator, with the exact mechanism sketch (arbitratePtoAgainstBambooWindow + unblockSheetWorkedDays, no AgentDayOffRepository dependency added to the upload path) attached to the selection. This is a one-way door: D-05 (see 11-02-PLAN.md checkpoint) — reverting to Phase 10's union rule later would silently re-block days operators had already corrected, with no signal."
  - "SolverService.bambooLookaheadWeeks/bambooLookbackWeeks are @Value field injections, not constructor parameters — matches BambooRefreshService's own window fields exactly and keeps every existing SolverService test (which constructs the service with its current parameter list, or calls the static helpers directly) unchanged"
  - "unblockSheetWorkedDays runs against the persisted AgentDayOff list ONLY, before any recurring fact from agent_day_hours is added — so a spreadsheet-sourced recurring MANDATORY fact can never be mistaken for a BambooHR-sourced one and un-blocked against itself"
  - "MergeReportTest fixture deviation (Rule 3): employee()/employeeNoDept() helpers set customWorkingdays=\"Mon-Sun\" so the parsed BambooHR set matches the paired workbook fixture's implied full week (every day cell has dayOffType==null, including the Sat/Sun \"0\" cells, which count as worked per the 2026-08-18 operator revision) — keeping the pre-existing identity-only assertions green now that mergeWorkingPattern also inspects customWorkingdays"

patterns-established:
  - "Provenance-gated downgrade: a refresh-time guard function (shouldDowngradeWorkingDaysKnown) reads a persisted ownership marker before mutating a derived flag, rather than the marker itself ever being written outside its single owning code path"

requirements-completed: [MRG-03, MRG-06]

coverage:
  - id: D6
    description: "An agent whose BambooHR customWorkingdays is blank/Variable but whose uploaded sheet supplied all seven day cells becomes workingDaysKnown=true, survives SolverService.filterEligible, and stays true across a later blank/Variable refresh because the provenance marker is never written back to BAMBOOHR by the refresh path"
    requirement: MRG-06
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/WorkingDaysKnownTest.java"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/integration/WorkingDaysSourceGuardTest.java"
        status: pass
    human_judgment: true
    rationale: "The eligibility callout's visual rendering (green panel, singular/plural copy, wrapping inside the 760px modal) is not covered by an automated frontend test in this repo — confirmed by code review, grep-based acceptance checks, and npm build only; not visually verified in a browser."
  - id: D7
    description: "BambooHR's dated PTO governs every date inside its synced window (inclusive both boundaries); the spreadsheet's recurring weekly PTO applies only outside it; MANDATORY facts are never touched by this arbitration"
    requirement: MRG-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/PtoArbitrationTest.java"
        status: pass
    human_judgment: false
  - id: D8
    description: "A weekday the spreadsheet marks as worked un-blocks a stale BambooHR field-4517 MANDATORY row for that weekday; a persisted BambooHR PTO row is never un-blocked; an agent with no agent_day_hours rows is untouched"
    requirement: MRG-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/SheetPatternUnblockTest.java"
        status: pass
    human_judgment: false
  - id: D9
    description: "The D-05 week replacement is visible in the merge report — gap-fill when BambooHR's working-days field is blank/Variable, replacement when the two sets differ, silent on agreement, rendered Mon-Sun regardless of source ordering"
    requirement: MRG-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/integration/WorkingPatternMergeTest.java"
        status: pass
    human_judgment: false

duration: "~40min (continuation session: Tasks 3-4 + verification; Task 1 + checkpoint ran in a prior session)"
completed: 2026-08-19
status: complete
---

# Phase 11 Plan 02: MRG-03 PTO Arbitration & MRG-06 Solver Eligibility Summary

**A spreadsheet-supplied working-days pattern now makes its agent permanently solver-eligible
(a persisted provenance marker stops any future BambooHR refresh from clawing it back), and
BambooHR's dated PTO governs every date inside its synced window while the sheet's day group
un-blocks a stale BambooHR MANDATORY day — both arbitrated entirely at solve time, per the
human-approved one-way-door checkpoint decision, with nothing new persisted.**

## Performance

- **Duration:** ~40 min for this continuation (Task 3, Task 4, verification, SUMMARY); Task 1
  and the checkpoint ran in a prior session (see `Phase 11 P01`-adjacent context in STATE.md)
- **Tasks:** 4 (Task 1, checkpoint, Task 3, Task 4) — 3 code tasks committed
- **Files modified:** 15 distinct files (7 created, 8 modified) across the whole plan

## Checkpoint Decision

**Where D-05/D-09 arbitration executes — RESOLVED: solve-time.**

The human operator selected `solve-time` explicitly, attaching this mechanism sketch to the
selection:

```
SolverService.solve()
  allDaysOff = assemble(...)          // as today
  allDaysOff = arbitratePtoAgainstBambooWindow(allDaysOff, window)
  allDaysOff = unblockSheetWorkedDays(allDaysOff, dayGroup)
  -> solve

Persisted rows: UNCHANGED
Upload path deps: UNCHANGED (no AgentDayOffRepository)
Revert = code change + re-solve
```

This is a **one-way door** (D-05): once operators rely on the sheet to correct a wrong BambooHR
week, reverting to Phase 10's union rule would silently re-block days they had already fixed,
with no signal that it happened. Task 3 was executed exactly against this mechanism — no
re-planning occurred, matching the plan's authorization ("execute Task 3 as written").

## Accomplishments

- `Agent.workingDaysSource` (V36 migration, `WorkingDaysSource` enum) persists whether an
  agent's working-days pattern is BambooHR- or spreadsheet-owned; the upload sets it, the
  refresh path never does — `BambooRefreshService.shouldDowngradeWorkingDaysKnown` guards the
  existing downgrade so a routine refresh can never silently reclaim a sheet-corrected agent
  out of the solve (MRG-06, D-15, closing the UAT 2026-08-12 hazard)
- `DeskAssignmentUploadResult.newlyEligibleAgents` + a green "Newly eligible for scheduling"
  callout in the Upload Results modal names every agent whose `workingDaysKnown` flipped
  false-to-true this upload, rendered above the Merge Report section (MRG-06, D-14)
- `SolverService.arbitratePtoAgainstBambooWindow` drops a recurring spreadsheet PTO fact for
  any date inside the BambooHR sync window (inclusive both boundaries); dates outside the
  window keep the recurring pattern; MANDATORY facts are never touched (MRG-03, D-09)
- `SolverService.unblockSheetWorkedDays` removes a persisted BambooHR MANDATORY row for a
  weekday the sheet states as worked; persisted PTO rows are never un-blocked; an agent with no
  `agent_day_hours` rows is untouched (MRG-03, D-05)
- Both new `SolverService` helpers are pure, package-private statics operating on in-memory
  lists — no repository dependency added, nothing persisted, fully re-derived every solve (D-10)
- `AgentMergeService.mergeWorkingPattern` (+ `FIELD_WORKING_PATTERN`, `OUTCOME_PATTERN_REPLACED`
  constants) reports the D-05 week replacement in the Merge Report: gap-fill when BambooHR's
  field 4517 is blank/`Variable`, replacement when the parsed BambooHR week differs from the
  sheet's, nothing on silent agreement — rendered as a neutral pill using only already-declared
  palette values (`#f9fafb`/`#e5e7eb`)

## Task Commits

1. **Task 1: MRG-06 — spreadsheet-sourced week makes an agent solver-eligible, permanently** -
   `b23a3de` (feat, tdd) — completed in the prior session
2. **Checkpoint: where D-05/D-09 arbitration executes — a one-way door** - resolved by the human
   operator (`solve-time`); no commit (decision only)
3. **Task 3: MRG-03 — BambooHR's dated PTO governs its window; the sheet's week un-blocks
   outside it** - `b71a082` (feat, tdd)
4. **Task 4: Make the D-05 week replacement visible in the merge report** - `df44f1e` (feat, tdd)

## Files Created/Modified

- `src/main/resources/db/migration/V36__add_agent_working_days_source.sql` - D-15 provenance
  column, default `BAMBOOHR`
- `src/main/java/com/wfm/model/WorkingDaysSource.java` - two-constant provenance enum
- `src/main/java/com/wfm/model/Agent.java` - `workingDaysSource` field + accessors
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` - `newlyEligibleAgents`
  accumulator + `workingDaysSource` assignment (Task 1); `mergeWorkingPattern` call wired
  between the identity merge and the `agent_day_hours` write loop (Task 4)
- `src/main/java/com/wfm/integration/BambooRefreshService.java` -
  `shouldDowngradeWorkingDaysKnown` guard on the existing downgrade-and-save pair
- `src/main/java/com/wfm/service/SolverService.java` - `bambooLookaheadWeeks`/
  `bambooLookbackWeeks` `@Value` fields, `arbitratePtoAgainstBambooWindow`,
  `unblockSheetWorkedDays`, both wired into `startSolve` before `buildAgentDaysOffMap`
- `src/main/java/com/wfm/integration/AgentMergeService.java` - `FIELD_WORKING_PATTERN`,
  `OUTCOME_PATTERN_REPLACED`, `mergeWorkingPattern`, `renderDays` helper
- `frontend/src/api/client.ts` - `newlyEligibleAgents: string[]` on `DeskAssignmentUploadResult`
- `frontend/src/pages/ClientManagement.tsx` - eligibility callout (Task 1); neutral Outcome pill
  for the new "Replaced by spreadsheet" value (Task 4)
- `src/test/java/com/wfm/service/WorkingDaysKnownTest.java` - MRG-06 upload-side coverage
- `src/test/java/com/wfm/integration/WorkingDaysSourceGuardTest.java` - D-15 refresh-side
  coverage
- `src/test/java/com/wfm/service/PtoArbitrationTest.java` - D-09 window arbitration, both
  boundary dates and one date inside/outside each bound
- `src/test/java/com/wfm/service/SheetPatternUnblockTest.java` - D-05 un-block coverage
- `src/test/java/com/wfm/integration/WorkingPatternMergeTest.java` - merge-report visibility
  for gap-fill, replacement, silent agreement, Mon-Sun ordering, determinism
- `src/test/java/com/wfm/integration/MergeReportTest.java` - fixture fix (see Deviations)

## Decisions Made

- **Checkpoint resolved to solve-time arbitration** (see above) — binding for all of Task 3's
  implementation; the upload path gained no `AgentDayOffRepository` dependency (verified by an
  unchanged `grep -c 'Repository'` count on `SolverService.java` before/after).
- **`@Value` field injection over constructor parameters for the BambooHR window fields** — the
  plan called for this explicitly so every existing `SolverService` test keeps working
  untouched; followed as specified.
- **`unblockSheetWorkedDays` ordering**: called against the persisted-only `AgentDayOff` list,
  strictly before any recurring fact is added to `allDaysOff` — otherwise a spreadsheet-sourced
  recurring MANDATORY fact could be mistaken for a BambooHR-sourced one and un-block itself.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking issue] `MergeReportTest` fixtures broke once `mergeWorkingPattern`
started consulting `customWorkingdays`**
- **Found during:** Task 4 verification (`./gradlew test --tests "com.wfm.integration.Merge*Test"`)
- **Issue:** `MergeReportTest`'s shared `employee()`/`employeeNoDept()` helpers (written by plan
  11-01, before MRG-03/D-05 existed) left `customWorkingdays` as `null` — a BambooHR data gap.
  The paired workbook fixture states an implied full Mon-Sun week (every day cell parses to
  `dayOffType == null`, including the Sat/Sun `"0"` cells, which the 2026-08-18 operator
  revision counts as worked). Once `mergeWorkingPattern` ran against that gap, it unconditionally
  appended a gap-filled "Working pattern" merge-report row, breaking 3 of the suite's
  identity-field-only assertions (`agreementAndBlankFields_emitNoEntries`,
  `divergentFields_emitOneEntryEach_inFixedFieldOrder`, `gapFilledField_emitsGapFilledOutcome_notOverride`).
- **Fix:** Set `customWorkingdays = "Mon-Sun"` on both fixtures so the parsed BambooHR working-day
  set matches the workbook fixture's implied full week exactly, restoring silent agreement (D-11)
  and zero new pattern rows — no assertion in the suite was changed.
- **Files modified:** `src/test/java/com/wfm/integration/MergeReportTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.integration.WorkingPatternMergeTest" --tests "com.wfm.integration.Merge*Test" --tests "com.wfm.integration.WorkingDaysParserTest" --tests "com.wfm.service.DeskAssignmentUpload*Test"` passes (61 tests, 0 failures)
- **Committed in:** `df44f1e` (Task 4 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking issue)
**Impact on plan:** Necessary for Task 4's own acceptance criteria (pre-existing report suites
must stay green); no scope creep — the fix is entirely inside a test fixture, no production code
or assertion changed.

## Issues Encountered

- Same long-pole issue plan 11-01 documented: a full unfiltered `./gradlew test` run includes
  real Timefold constraint-satisfaction solves (`com.wfm.solver.*Test`, `SolverService*Test`)
  that make a full-suite run impractically slow for a single session. Every test class this plan
  created or modified was run explicitly via targeted `--tests` filters after each task and is
  confirmed green with zero failures (`WorkingDaysKnownTest`, `WorkingDaysSourceGuardTest`,
  `BambooRefreshServiceTest`, `DeskAssignmentUpload*Test`, `PtoArbitrationTest`,
  `SheetPatternUnblockTest`, `SolverService*Test`, `AgentDayOffRecurringExpansionTest`,
  `ResolvePreferencesPtoFilterTest`, `WorkingPatternMergeTest`, `Merge*Test`,
  `WorkingDaysParserTest`). `./gradlew compileJava` and `npm --prefix frontend run build` both
  pass cleanly. A full unfiltered `./gradlew test` was started in the background during this
  session for extra confidence but had not completed by the time this plan's work concluded;
  nothing in its scope touches code paths this plan didn't already verify via targeted runs.

## Next Phase Readiness

- MRG-03 and MRG-06 are both closed; Phase 11's full requirement set (MRG-01 through MRG-07) is
  complete across plans 11-01 and 11-02.
- **Follow-up owed before phase seal** (recorded in `11-02-PLAN.md` flagged assumption A-02-4):
  `COVERAGE.md` for the BambooHR API surface does not exist for this phase. The seal-time gate
  reads `${PHASE_DIR}/COVERAGE.md`; Phase 11 re-uses existing `listEmployees`/`listTimeOff`
  methods and adds no new BambooHR endpoint, but a matrix or reasoned declaration is still owed.
- A-02-3 (the `Replaced by spreadsheet` outcome label/pill is not yet UI-checker-confirmed) and
  the eligibility callout's visual rendering both remain open for `/gsd-verify-work` /
  `/gsd-ui-review` — not blocking, but not yet human-verified in a browser.

---
*Phase: 11-bamboohr-merge-engine-report*
*Completed: 2026-08-19*

## Self-Check: PASSED

All 7 created source/test files and this SUMMARY.md verified present on disk. All 3 commit
hashes (`b23a3de`, `b71a082`, `df44f1e`) verified present in git log.
