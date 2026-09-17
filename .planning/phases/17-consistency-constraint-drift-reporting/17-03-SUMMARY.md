---
phase: 17-consistency-constraint-drift-reporting
plan: 03
subsystem: reporting
tags: [poi-excel, junit5, mockito, spring-data-jpa]

requires:
  - phase: 17-consistency-constraint-drift-reporting
    provides: "17-01's ScheduleOutputService.buildDriftReport (List.of() popularity stub), ScheduleDetailResponse.DriftReport/ShiftPopularityEntry DTO family, SolverService.resolveUsualShiftTargets, and the ushf-05-write-paths.md write-path record extended for the solver's new read path"
provides:
  - "ScheduleOutputService.buildDriftReport's popularity ranking (DRFT-04, D-13) -- distinct-agent counts per resolved shift template, sorted descending by count then ascending by name, read from the same tenant-and-desk-scoped allUsualShifts fetch already used for drift entries"
  - "ScheduleService's drift date-filter block (DRFT-01) -- narrows entries to the filtered date and RECOMPUTES the DriftSummary from that narrowed set, unlike PreferenceReport's whole-schedule summary"
  - "ScheduleExportService.writeDriftReport -- the Drift Report Excel sheet (XCUT-01, D-14), byte-identical headers to the tab, popularity section below the main table"
  - "SolverUsualShiftWritePathGuardTest -- behavioural + structural proof that SolverService.resolveUsualShiftTargets never writes agent_usual_shift (T-17-01/XCUT-02)"
affects: [17-04-benchmark-and-defaults, 17-05-frontend]

actuals:
  tokens: 13343
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Popularity computed from the SAME allUsualShifts fetch already loaded for drift entries -- never a second repository read (T-17-05)"
    - "Excel sheet writer modelled line-for-line on writePreferenceReport's guard-then-header-only pattern, extended with a second, independently-gated section (the popularity block) below the main table"
    - "Write-path guard proven two ways per row (behavioural mock-interaction proof + structural comment-stripped source scan), mirroring UsualShiftWritePathTest's existing precedent for the solver's other two rows"

key-files:
  created:
    - src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java
  modified:
    - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/service/ScheduleExportService.java
    - src/test/java/com/wfm/service/DriftReportTest.java
    - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
    - src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java
    - src/test/resources/ushf-05-write-paths.md

key-decisions:
  - "Popularity is resolved at LocalDate.now() ('today'), mirroring DeskAgentService.toResponse's existing isLive-check precedent for 'the usual shift the operator currently sees' -- an era-renamed template ranks under its current name, not a stale pointer's name."
  - "The Excel popularity block is written whenever the DriftReport itself is non-null, regardless of whether the main entry list is empty -- it answers an independent question (D-13, read from stored usual shifts) that a date-filtered or otherwise-empty entry list must not suppress. Only a null DriftReport (a SLOT-scheduled desk) produces the header-only sheet."
  - "Every Drift Report sheet cell is a plain-text value, including the popularity count (String.valueOf(agentCount)) -- matching the sheet's own all-string convention (UI E4 truth) rather than writePreferenceReport-style mixed numeric/string cells used elsewhere in the file."
  - "ScheduleService's date-filter tests instantiate ScheduleService directly with plain Mockito mocks for every repository dependency (a real InMemoryScheduleStore, since it is a plain POJO) rather than a @DataJpaTest slice -- the filtering/recompute logic needs no persistence, only a mocked ScheduleOutputService returning a controlled DriftReport fixture."

patterns-established:
  - "A second, independently-gated writer section within one Excel sheet (main table + popularity block), each following writePreferenceReport's own guard shape rather than sharing one guard"

requirements-completed: [DRFT-01, DRFT-04, XCUT-01, XCUT-02]

coverage:
  - id: D1
    description: "Popularity ranking counts distinct agents per shift template, sorted descending by count then ascending by template name, ranking under the currently-effective template name"
    requirement: DRFT-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#popularityRanking_fourAgentsEarlyTwoAgentsLate_sortedDescendingByCount"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#popularityRanking_tiedCounts_sortedAscendingByTemplateName"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#popularityRanking_agentWithSameUsualShiftOnThreeWeekdays_countsOnce"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#popularityRanking_templateNoAgentHolds_doesNotAppearInTheList"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#popularityRanking_noStoredUsualShifts_returnsEmptyNotNull"
        status: pass
    human_judgment: false
  - id: D2
    description: "The popularity ranking is scoped to the schedule's own tenant and desk, read through the tenant-and-desk-scoped finder, never an unscoped repository read"
    requirement: DRFT-04
    verification:
      - kind: other
        ref: "grep -q 'findByTenantIdAndDeskId' src/main/java/com/wfm/service/ScheduleOutputService.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "A date filter on the schedule detail narrows drift entries to that date and recomputes the summary counts from the narrowed set; popularity is unaffected by the date filter"
    requirement: DRFT-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#dateFilter_narrowsDriftEntriesToThatDateOnly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#dateFilter_recomputesSummaryFromTheFilteredEntries"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#dateFilter_popularityListUnchangedFromTheUnfilteredResponse"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DriftReportTest.java#dateFilter_malformedDate_stillThrowsIllegalArgumentException"
        status: pass
    human_judgment: false
  - id: D4
    description: "A solve never writes to agent_usual_shift: the pre-solve resolution performs zero mutating interactions with AgentUsualShiftRepository, proven by a guard test rather than asserted in prose"
    requirement: XCUT-02
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java#resolveUsualShiftTargets_zeroMutatingInteractionsOnTheRepository"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java#solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java#stripCommentsAndJavadoc_removesACommentLineThatWouldOtherwiseMatch"
        status: pass
    human_judgment: false
  - id: D5
    description: "The Excel schedule export carries a Drift Report sheet whose column headers are byte-identical to the Drift Report tab's headers"
    requirement: XCUT-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_driftReport_mainHeadersMatchTheInterfaceLiteralsInOrder"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_driftReport_popularityHeadingAndHeadersMatch"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_driftReport_popularityRowsAppearInTheSuppliedOrder"
        status: pass
      - kind: other
        ref: "grep -c 'createSheet(\"Drift Report\")' src/main/java/com/wfm/service/ScheduleExportService.java"
        status: pass
    human_judgment: false
  - id: D6
    description: "A null DriftReport (SLOT-scheduled desk) writes the header row and nothing else; status is mapped to the three operator-facing labels, never the raw enum name; a NO_USUAL_SHIFT row leaves Usual Start/Delta blank while Actual Start is populated"
    requirement: XCUT-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_nullDriftReport_sheetHasOnlyTheMainHeaderRow"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_driftReport_mixedStatusRows_writeTheThreeOperatorFacingLabels"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_driftReport_noUsualShiftRow_leavesUsualStartAndDeltaBlankButActualStartPopulated"
        status: pass
    human_judgment: false

duration: 55min
completed: 2026-09-17
status: complete
---

# Phase 17 Plan 03: Popularity Ranking, Drift Date-Filter Summary & Excel Export Summary

Fills DRFT-04's over-subscription ranking into `buildDriftReport`, makes `ScheduleService`'s date
filter recompute the drift summary so the four numbers on screen always agree with the rows beneath
them, writes the same drift data into a new Excel `Drift Report` sheet, and proves — for the first
time with a structural test rather than prose — that the solver's pre-solve usual-shift resolution
never writes `agent_usual_shift`.

## Performance

- **Duration:** ~55 min
- **Started:** 2026-09-17T13:15:00Z (approx, continuing from 17-02's completion)
- **Completed:** 2026-09-17T18:06:15Z
- **Tasks:** 3 completed
- **Files modified:** 9 (1 created, 8 modified)

## Accomplishments

- `ScheduleOutputService.buildDriftReport`'s `popularity` list is filled: counts DISTINCT agents
  per resolved shift-template name from the same tenant-and-desk-scoped `allUsualShifts` fetch
  already used for drift entries (no second repository read, T-17-05), resolved through
  `UsualShiftResolutionService.resolve` at "today" so an era-renamed template ranks under its
  current name, sorted descending by count then ascending by template name.
- `ScheduleService`'s date-filter block now narrows `driftReport` entries to the filtered date and
  **recomputes** the `DriftSummary` from that narrowed set — a deliberate divergence from
  `PreferenceReport`'s whole-schedule summary carried through unfiltered — so the four summary
  numbers on the Drift Report tab always agree with the rows on screen (DRFT-01). Popularity is
  carried through untouched, since it answers an independent question.
- `ScheduleExportService.writeDriftReport` adds a `Drift Report` sheet to the Excel export,
  modelled on `writePreferenceReport`'s guard-then-header-only pattern, with byte-identical
  headers to the on-screen tab (XCUT-01, D-14) and a `Most-Subscribed Usual Shifts` section below
  the main table.
- `SolverUsualShiftWritePathGuardTest` proves XCUT-02 by test, not prose: a behavioural proof
  (zero mutating interactions on a mocked `AgentUsualShiftRepository`, one explicit never-invoked
  assertion per mutating method the interface exposes) and a structural proof (comment-stripped
  source scan of `SolverService.java` finds no mutating call on the field anywhere in the class).
  `ushf-05-write-paths.md` gained a tenth row naming this test as evidence.

## Task Commits

Each task was committed atomically (Task 1 carried `tdd="true"` — a genuine RED/GREEN cycle):

1. **Task 1: Over-subscription ranking and a date filter whose summary tells the truth (TDD)**
   - `88ae0ce` (test) — RED: 6 of 19 tests in `DriftReportTest` fail (popularity stub still empty,
     date-filter block doesn't yet touch `driftReport`)
   - `e247ea6` (feat) — GREEN: popularity filled, date-filter recompute added, all 19 pass
2. **Task 2: Drift Report sheet in the Excel export** - `c5f6ae6` (feat)
3. **Task 3: Prove the solver never writes the usual-shift target** - `6bf6d14` (test)

**Plan metadata:** (this commit)

_TDD Gate Compliance: Task 1's RED commit (`88ae0ce`) strictly precedes its GREEN commit
(`e247ea6`). RED evidence was captured and verified via `gsd_run check tdd-red-evidence` — the
production files (`ScheduleOutputService.java`, `ScheduleService.java`) were reverted via
`git checkout --` immediately before the RED run and restored via `git apply` immediately after,
so the recorded failure is against the genuine pre-implementation tree, not a fixture. A real
`./gradlew test --tests "com.wfm.service.DriftReportTest"` run produced exit code 1 with 19 tests
completed, 6 failed, 13 passed; that exact result was hand-transcribed into a TAP-shaped record for
the checker (Gradle's console output is not TAP-format — the same tooling gap 17-01 documented and
worked around identically), verdict `RED_EVIDENCE_OK`. No REFACTOR commit was needed. Tasks 2 and 3
were `type="auto"` (non-TDD) and each produced one clean commit on the first implementation attempt._

## Files Created/Modified

- `src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java` - new, XCUT-02's
  behavioural + structural write-path guard, 3 tests
- `src/main/java/com/wfm/service/ScheduleOutputService.java` - popularity ranking added to
  `buildDriftReport`
- `src/main/java/com/wfm/service/ScheduleService.java` - drift date-filter narrow + summary
  recompute block
- `src/main/java/com/wfm/service/ScheduleExportService.java` - `writeDriftReport`, called from
  `exportToExcel`
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` - corrected `ShiftPopularityEntry`'s
  stale javadoc (still described the 17-01 `List.of()` stub)
- `src/test/java/com/wfm/service/DriftReportTest.java` - extended, 11 new tests (5 popularity, 5
  date-filter, +1 already covered by existing structure)
- `src/test/java/com/wfm/service/ScheduleExportServiceTest.java` - extended, 6 new tests for the
  Drift Report sheet
- `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` - updated hardcoded row-count
  assertion (see Deviations)
- `src/test/resources/ushf-05-write-paths.md` - tenth row appended (solver read-path guard)

## Decisions Made

- Popularity resolved at `LocalDate.now()` — mirrors `DeskAgentService.toResponse`'s existing
  `isLive`-check precedent for "the usual shift the operator currently sees."
- Excel popularity block written whenever `DriftReport` is non-null, independent of whether the
  main entry list is empty — it answers a different question (D-13) that an empty/date-filtered
  entry list must not suppress.
- Every Drift Report sheet cell is a plain-text value (`String.valueOf(agentCount)` for the
  popularity count), matching the sheet's own all-string convention (a UI E4 must-have truth).
- `ScheduleService`'s date-filter tests instantiate `ScheduleService` directly with plain Mockito
  mocks (real `InMemoryScheduleStore`, since it's a plain POJO) rather than a `@DataJpaTest` slice
  — the filtering/recompute logic needs no persistence.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking issue] `UsualShiftWritePathGuardTest`'s hardcoded row-count assertion updated (not in `files_modified`)**
- **Found during:** Task 3, running the full suite after appending the tenth `ushf-05-write-paths.md` row
- **Issue:** `theTableHasExactlyNineDataRows_withNoBlankRequiredCells` asserted the USHF-05 table
  has exactly 9 data rows. This plan's required tenth row (naming
  `SolverUsualShiftWritePathGuardTest`) made the real count 10, failing this pre-existing guard —
  the same class of collision 17-01 hit with `ScheduleConstraintClassificationTest`'s hardcoded
  `MODE_GATED` count.
- **Fix:** Updated the assertion to `hasSize(10)`, the message text, and renamed the test
  (`...ExactlyNineDataRows...` → `...ExactlyTenDataRows...`) to match, following 17-01's own
  precedent for this exact failure mode.
- **Files modified:** `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.service.UsualShiftWritePathGuardTest"` exits 0
- **Committed in:** `6bf6d14`

---

**Total deviations:** 1 auto-fixed (Rule 3 — a pre-existing test's hardcoded completeness count
required a same-commit update).
**Impact on plan:** Necessary for the plan's own acceptance criteria (`./gradlew test` exits 0 for
the whole suite) to hold. No scope creep — no new architecture, no functionality beyond what the
plan specified.

## Issues Encountered

None beyond the RED-evidence tooling gap already documented and worked around identically to
17-01 (see TDD Gate Compliance note above).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 17-04 (benchmark, redone per-agent-day arithmetic, human-gated V49 default weights) and plan
17-05 (frontend) can both proceed. `DRFT-01`, `DRFT-04`, and `XCUT-01` are also declared by plan
17-05's frontmatter (shared IDs) — per the shared-ID gate, they will not flip to `Complete` in
`REQUIREMENTS.md` until 17-05 also finishes; only `XCUT-02` (unique to this plan) is expected to
mark complete immediately. `ScheduleDetailResponse.DriftReport`/`ShiftPopularityEntry` and the six
literal header/label strings this plan wrote into the Excel export are exactly what
`17-UI-SPEC.md`'s frontend contract names — confirmed byte-for-byte against this plan's own
`<interfaces>` block, which 17-05 also consumes. No blockers.

## Self-Check: PASSED

- `src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java` verified present on disk.
- All 4 commit hashes (`88ae0ce`, `e247ea6`, `c5f6ae6`, `6bf6d14`) verified present via `git log`.
- All task-level `<acceptance_criteria>` re-run and passing: popularity ordering/tie-break/dedup/
  empty-list assertions, `findByTenantIdAndDeskId` grep, `setDriftReport` occurrence count (2),
  Excel header/label/null-report/blank-cell assertions, `createSheet("Drift Report")` grep (1),
  guard test's per-method never-invoked assertions plus `verifyNoMoreInteractions`, stripper
  self-test, `ushf-05-write-paths.md` exactly one new row with additions-only diff.
- Plan-level `<verification>` re-run: `./gradlew test` green (full suite, 781 tests after this
  plan's additions, 0 failures); a date-filtered schedule detail returns only that date's drift
  entries with summary counts summing to the filtered count; the exported workbook contains a
  `Drift Report` sheet whose headers match the tab's headers exactly.
