---
phase: 16-usual-shift-storage
plan: 03
subsystem: scheduling
tags: [apache-poi, excel-data-validation, spring-boot, upload-parser, shift-templates]

# Dependency graph
requires:
  - phase: 16-usual-shift-storage
    provides: "plan 16-01's EnrichedColumnLayout.usualShiftHeader/DAY_ORDER, UsualShiftService.clearUsualShifts; plan 16-02's UsualShiftService as a proven clearUsualShifts caller (removeDeskAgent) and DeskAgentResponse.usualShift as the pre-fill's data source"
provides:
  - "DeskAssignmentTemplateService pre-fills the seven Usual Shift cells (D-09) and attaches a
    sheet-scoped Excel dropdown of the desk's live template names (D-10), degrading gracefully at
    the 255-char Excel data-validation limit or on a comma/double-quote in a template name (P-14)"
  - "DeskAssignmentUploadService requires the seven Usual Shift headers on every enriched-shape
    sheet (P-11), reads them with D-07/D-08/D-03/P-12 cell semantics, and writes AgentUsualShift
    rows directly through the repository (P-13)"
  - "DeskAssignmentUploadService.clearDesk wipes usual shifts through UsualShiftService.clearUsualShifts
    (D-11) -- the SAME implementation DeskAgentService.removeDeskAgent already calls (plan 16-02),
    now proven with two production callers"
  - "The load-bearing D-09/D-11 pair discharged together: a download-then-re-upload of a per-desk
    template is a no-op for stored usual shifts (DeskAssignmentUploadUsualShiftTest#downloadThenReupload_isANoOp_forStoredUsualShifts)"
affects: [16-04-write-path-structural-guard, 16-05-roster-ui-usual-shift, 17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 27814
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sheet-scoped Excel explicit-list data validation (org.apache.poi DataValidationHelper/CellRangeAddressList), genuinely new to this codebase -- guarded by a pre-build length/character check rather than a try/catch, since POI itself never rejects an oversized formula1 string at write time (16-RESEARCH.md Pitfall 5)"
    - "Per-sheet normalized-name index with a separate collision set, built once before the row loop (never per row) -- an ambiguous key is reported explicitly rather than one candidate silently winning"
    - "Direct-repository bulk write beside a choke-point service (P-13) -- the upload parser writes AgentUsualShift the same way it already writes AgentDayHours, deliberately bypassing UsualShiftService.setUsualShift's reject-with-400 contract in favor of skip-and-warn"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceUsualShiftTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadUsualShiftTest.java
  modified:
    - src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateFilterTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadAllowlistTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
    - src/test/java/com/wfm/service/UploadSyncFailureTest.java
    - src/test/java/com/wfm/service/WorkingDaysKnownTest.java
    - src/test/java/com/wfm/integration/WorkingPatternMergeTest.java
    - src/test/java/com/wfm/integration/UploadFreshSyncTest.java
    - src/test/java/com/wfm/integration/MergePrecedenceTest.java
    - src/test/java/com/wfm/integration/MergeReportTest.java

key-decisions:
  - "Adopted P-11..P-15 verbatim: seven Usual Shift headers required (not optional) on an enriched-shape sheet; trim+lowercase name matching with an explicit ambiguity path; direct-repository parser write bypassing the choke-point service; three-condition graceful dropdown degradation; template column placement at indices 14-20 with specialty moved to 21-22"
  - "Task order (D-09 pre-fill before D-11 wipe) enforced exactly as planned -- Task 1 committed and its precondition grep verified green before Task 2's clearDesk change was written, so the wipe was never live without its re-supply path"
  - "The round-trip test (case 9) fills in the template's still-blank day-hours cells before re-uploading, since the template leaves those blank by Phase 10 design -- an unmodified generate-then-reupload would fail every row on blank day cells for reasons unrelated to this phase's own claim, so the test isolates the Usual Shift round trip specifically"

requirements-completed: [USHF-02]
# USHF-05 and XCUT-02 are also declared here, but both are ALSO declared by plan 16-02, which
# finished first and already marked them complete (16-02-SUMMARY.md requirements-completed).
# The shared-ID gate (#2388) is satisfied — this plan's own USHF-05/XCUT-02 work (clearDesk's
# UsualShiftService.clearUsualShifts call, the second of the "one implementation, two callers"
# pair) is the LAST piece; nothing further blocks on this plan for those two IDs.

coverage:
  - id: D1
    description: "The generated per-desk template pre-fills each agent's stored usual shift with the raw stored name and attaches a sheet-scoped dropdown of the desk's live template names to each of the seven Usual Shift columns"
    requirement: USHF-02
    verification:
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#preFillsStoredUsualShift_blankWhereNotStored"
        status: pass
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_happyPath_sevenValidationsWithLiveTemplateNames"
        status: pass
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_adjacency_deskBValidationsExcludeDeskANames"
        status: pass
    human_judgment: false
  - id: D2
    description: "The dropdown degrades gracefully (skips itself, keeps headers/pre-fill) at the 255-char Excel data-validation limit and for a comma/double-quote in a template name, pinned at both sides of the 255/256 boundary"
    requirement: USHF-02
    verification:
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_boundaryAt255_stillAttachesValidations"
        status: pass
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_boundaryAt256_skipsValidationsButKeepsHeadersAndPreFill"
        status: pass
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_templateNameContainsComma_skipsValidations"
        status: pass
      - kind: unit
        ref: "DeskAssignmentTemplateServiceUsualShiftTest#dropdown_templateNameContainsDoubleQuote_skipsValidations"
        status: pass
    human_judgment: false
  - id: D3
    description: "The upload parser reads the seven Usual Shift columns with D-07 (blank valid, no warning), D-08 (unresolvable name skips only the cell, warns, row still imports), D-03 (weekday-mask violation is a cell-level skip, not a 400), and P-12 (trim+lowercase matching with an explicit ambiguity path) semantics"
    requirement: USHF-02
    verification:
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#happyPath_resolvedName_writesOneRowForThatWeekday"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#blankCell_writesNoRow_noWarning_restOfRowImports"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#unresolvedName_skipsCellOnly_warnsAndRestOfRowImports"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#weekdayMaskExcludesDay_skipsCellOnly_warnsAndRestImports"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#lowercaseSpacePaddedName_resolvesViaNormalize"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#ambiguousNormalizedName_skipsCellOnly_warnsAmbiguity_neitherTemplateWritten"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#numericTypedCell_matchingAllDigitsTemplateName_resolves"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#warningsOnMultipleWeekdays_appearInDayOrderSequence"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-09 and D-11 are load-bearing for each other: clearDesk wipes usual shifts, and a download-then-re-upload of a per-desk template is a no-op because the template pre-fills exactly what the wipe clears"
    requirement: USHF-02
    verification:
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#downloadThenReupload_isANoOp_forStoredUsualShifts"
        status: pass
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#allUsualShiftCellsBlank_clearDeskWipesEveryCurrentDeskAgentsUsualShifts"
        status: pass
    human_judgment: false
  - id: D5
    description: "P-11: a sheet missing the seven Usual Shift headers is skipped with a specific notice and the desk is NOT cleared (CR-01 property), preventing a pre-Phase-16 workbook from silently wiping every stored usual shift with nothing to re-supply"
    requirement: USHF-02
    verification:
      - kind: unit
        ref: "DeskAssignmentUploadUsualShiftTest#sheetMissingUsualShiftHeaders_reportedInSkippedSheets_deskNotCleared"
        status: pass
    human_judgment: false
  - id: D6
    description: "Full backend suite stays green at or above the 688-test baseline after this plan's changes to clearDesk and the enriched-shape header contract"
    verification:
      - kind: integration
        ref: "./gradlew test (709 tests, 0 failures, 2 pre-existing ignored benchmark tests)"
        status: pass
    human_judgment: false
  - id: D7
    description: "A generated per-desk template opens in real Excel with no repair prompt, and clicking a Usual Shift cell shows a working dropdown of that desk's live template names"
    verification: []
    human_judgment: true
    rationale: "16-RESEARCH.md Pitfall 5 / the plan's own backstop must_haves entry: a POI round-trip test re-reads the file with the library that wrote it and structurally cannot detect Excel-side corruption from the 255-char explicit-list limit or verify the dropdown actually renders in real Excel. This is the one claim in this plan that only a manual Excel open-and-inspect can prove."

# Metrics
duration: 35min
completed: 2026-09-03
status: complete
---

# Phase 16 Plan 03: Upload Template Usual Shift Summary

**The per-desk upload template now pre-fills and shows a dropdown of each agent's stored usual shift (D-09/D-10), the parser reads all seven columns back with cell-level skip-and-warn semantics (D-07/D-08/D-03/P-12), and `clearDesk` wipes usual shifts the same way `removeDeskAgent` already does (D-11) — proven together as a download-then-re-upload no-op.**

## Performance

- **Duration:** 35 min
- **Started:** 2026-09-03T16:40:34Z
- **Completed:** 2026-09-03T17:15:23Z
- **Tasks:** 2
- **Files modified:** 20 (2 created, 18 modified)

## Accomplishments
- `DeskAssignmentTemplateService` writes seven pre-filled Usual Shift cells at template indices 14-20 (P-15), reading the same raw stored-name value function `DeskAgentExportService.writeUsualShiftCells` already uses — one derivation, not two (D-09).
- `DeskAssignmentTemplateService` attaches a sheet-scoped Excel explicit-list data-validation dropdown of the desk's live template names to each Usual Shift column (D-10), degrading gracefully — dropdown skipped, headers/pre-fill kept, parser validation still applies — when a desk has zero live templates, the comma-joined name list exceeds Excel's 255-character validation-text limit, or a name contains a comma or double-quote (P-14, pinned at both sides of the 255/256 boundary by dedicated tests).
- `DeskAssignmentUploadService` now requires the seven Usual Shift headers on every enriched-shape sheet (P-11) — a sheet missing them is skipped with the existing CR-01 notice and the desk is untouched, exactly the outcome a pre-Phase-16 workbook should get.
- The parser reads each Usual Shift cell with a per-sheet normalized-name index (P-12): blank means no usual shift and is valid (D-07); an unresolvable name skips only that cell and warns, never the row (D-08); a weekday-mask violation is a cell-level skip and warn (D-03), never the inline path's 400; a normalized-key collision across two live templates is reported as an explicit ambiguity rather than silently resolved. Writes land directly through `AgentUsualShiftRepository`, mirroring the existing `agent_day_hours` loop rather than routing through the roster's reject-on-mismatch choke point (P-13).
- `clearDesk` calls `UsualShiftService.clearUsualShifts` beside its existing `agentDayHoursRepository` wipe (D-11) — the identical implementation `DeskAgentService.removeDeskAgent` calls (plan 16-02), now proven with two production callers.
- The D-09/D-11 load-bearing pair is proven together, not separately: a download-then-immediate-re-upload of a per-desk template changes zero `agent_usual_shift` rows.
- Two new test classes: `DeskAssignmentTemplateServiceUsualShiftTest` (9 methods) and `DeskAssignmentUploadUsualShiftTest` (11 methods).

## Task Commits

Each task was committed atomically:

1. **Task 1: The generated template round-trips stored usual shifts (D-09, D-10)** - `f43abc1` (feat)
2. **Task 2: The upload reads the seven columns and clearDesk wipes usual shifts (D-06, D-07, D-08, D-03, D-11)** - `81d21c4` (feat)

_Note: both tasks' compile-ripple test-file fixes are committed as part of their own task commit, since they were required for that same commit's own verification to run and pass._

## Files Created/Modified
- `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` — D-09 pre-fill, D-10 dropdown with P-14 degradation, `ShiftTemplateRepository` injected, SLF4J logger added
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` — P-11 required-header check, P-12 per-sheet template index, D-06/D-07/D-08/D-03 row loop, D-11 `clearDesk` call, `AgentUsualShiftRepository`/`ShiftTemplateRepository`/`UsualShiftService` injected
- `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceUsualShiftTest.java` — new, 9 methods
- `src/test/java/com/wfm/service/DeskAssignmentUploadUsualShiftTest.java` — new, 11 methods
- `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java`, `DeskAssignmentTemplateFilterTest.java` — updated header positions/count and constructor call for the new `ShiftTemplateRepository` parameter
- 16 upload-service test files across `com.wfm.service` and `com.wfm.integration` — constructor call updated for the 3 new parameters; the enriched-shape header-building helper in the files that actually exercise the `missingHeaders` check extended with the seven Usual Shift headers (deviation, see below)

## Decisions Made
- Adopted P-11 through P-15 verbatim (see PLAN.md `<planner_decisions>`): required (not optional) Usual Shift headers; trim+lowercase name matching with an explicit ambiguity path over Phase 15's UsualShiftResolutionService's exact-match convention; direct-repository parser write bypassing the choke-point service; three-condition graceful dropdown degradation; template column placement 14-20 with specialty moved to 21-22.
- Task ordering honored exactly: Task 1 (D-09 pre-fill) was committed and its precondition grep verified `1` before Task 2's `clearDesk` change was written — the wipe was never live in this codebase's history without its re-supply path already present.
- The round-trip test (`downloadThenReupload_isANoOp_forStoredUsualShifts`) fills in the still-blank day-hours cells the generated template deliberately leaves blank (Phase 10 scope, unchanged by this phase) before re-uploading — an unmodified generate-then-reupload would fail every row on blank day cells, which is a pre-existing, out-of-scope behavior, not something this test should be measuring.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `DeskAssignmentTemplateFilterTest` needed the new `ShiftTemplateRepository` constructor parameter**
- **Found during:** Task 1 (adding `ShiftTemplateRepository` as `DeskAssignmentTemplateService`'s 4th constructor parameter)
- **Issue:** `DeskAssignmentTemplateFilterTest` (not listed in Task 1's `<files>`) directly constructs `DeskAssignmentTemplateService` and failed to compile once the constructor gained a 4th parameter.
- **Fix:** Added a `ShiftTemplateRepository` mock (stubbed to return an empty list) and passed it to the constructor.
- **Files modified:** `src/test/java/com/wfm/service/DeskAssignmentTemplateFilterTest.java`.
- **Verification:** `./gradlew test --tests DeskAssignmentTemplateFilterTest` green; part of the Task 1 scoped verify command.
- **Committed in:** `f43abc1` (Task 1 commit).

**2. [Rule 3 - Blocking] 6 upload-service test files outside Task 2's listed 7 needed the same 3-parameter constructor and (where they exercise the header check) header-loop fix**
- **Found during:** Task 2 (adding `AgentUsualShiftRepository`, `ShiftTemplateRepository`, `UsualShiftService` as `DeskAssignmentUploadService`'s 3 new constructor parameters, and making the seven Usual Shift headers required)
- **Issue:** `WorkingPatternMergeTest`, `UploadFreshSyncTest`, `WorkingDaysKnownTest` (`com.wfm.integration`/`com.wfm.service`), `MergePrecedenceTest`, `MergeReportTest` (`com.wfm.integration`), and the three retired/legacy/enriched-shape shape-classification tests all construct `DeskAssignmentUploadService` directly. The first five also build a full enriched-shape workbook via a `newShapeHeaders()`-style helper and exercise the real upload row-write path, so P-11's new required-header rule would have skipped every row in those suites as "missing required column(s)" without the header-loop fix; the three shape-classification tests only needed the constructor fix, since their fixtures are rejected before reaching the enriched-shape `missingHeaders` block (matching the plan's own note about those three files).
- **Fix:** Added the 3 new mocks (`AgentUsualShiftRepository`, `ShiftTemplateRepository` stubbed to return an empty template list, `UsualShiftService`) and the 3 new constructor arguments to all 8 files; extended the `newShapeHeaders()`/`identityAndDayHeaders()` helper with a second `DAY_ORDER` loop over `EnrichedColumnLayout.usualShiftHeader(d)` in the 5 files whose fixtures reach the header-validation block.
- **Files modified:** `src/test/java/com/wfm/integration/WorkingPatternMergeTest.java`, `UploadFreshSyncTest.java`, `MergePrecedenceTest.java`, `MergeReportTest.java`, `src/test/java/com/wfm/service/WorkingDaysKnownTest.java`, `DeskAssignmentUploadLegacyShapeTest.java`, `DeskAssignmentUploadRetiredShapeTest.java`, `DeskAssignmentUploadEnrichedShapeTest.java`.
- **Verification:** `./gradlew compileTestJava` clean; all 8 files' own suites pass individually; full suite green (709 tests, 0 failures).
- **Committed in:** `81d21c4` (Task 2 commit — required for that commit's own verification, `./gradlew test`, to compile and pass).

**3. [Rule 3 - Blocking] `DeskAssignmentUploadSpecialtyTest`'s row-value list needed blank placeholders to keep column position aligned with its own headers**
- **Found during:** Task 2, updating `DeskAssignmentUploadSpecialtyTest` (one of the plan's own listed 5 "identity + day header" files)
- **Issue:** This file appends its `Specialty N` headers AFTER the day-hours group in the header row, then writes specialty cell values at the column index immediately following the day-hours values in the row-value list. Appending the seven new Usual Shift headers to the shared `identityAndDayHeaders()` helper (per the plan's literal instruction) inserted them between the day-hours and specialty header columns without a matching change to the row-value list, which would have misaligned every specialty value by 7 columns.
- **Fix:** Inserted 7 blank placeholder values into the row-value list between the day-hours values and the specialty values, matching the header row's new column order.
- **Files modified:** `src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java`.
- **Verification:** `./gradlew test --tests DeskAssignmentUploadSpecialtyTest` green (both specialty-parsing assertions still pass with correct values at the correct columns).
- **Committed in:** `81d21c4` (Task 2 commit).

---

**Total deviations:** 3 auto-fixed (all Rule 3 — blocking compile/correctness ripple from the two constructor-signature changes and the new required-header rule). **Impact:** No behavior changes beyond what the plan specified; all three fixes were necessary for the plan's own verification gates (`./gradlew compileTestJava`, the scoped test commands, and the mandatory full `./gradlew test`) to run and pass. No scope creep — the additional 6 files outside Task 2's listed 7 are the same class of compile/correctness ripple the plan's own file list already anticipated for 7 sibling files, just an incomplete enumeration of the full caller set.

## Issues Encountered
None beyond the three deviations above (all resolved).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 16-04 (write-path structural guard, D-14) can now enumerate `DeskAssignmentUploadService`'s parser row loop and `clearDesk` as two proven `AgentUsualShiftRepository` write paths alongside `UsualShiftService.setUsualShift` (plan 16-01) and `DeskAgentService.removeDeskAgent` (plan 16-02).
- The template/parser/export shape is now fully consistent: exactly four main-source files reference `EnrichedColumnLayout.usualShiftHeader` (`EnrichedColumnLayout` itself, `DeskAgentExportService`, `DeskAssignmentTemplateService`, `DeskAssignmentUploadService`), confirmed by acceptance-criteria grep.
- No blockers. One item requires human verification before `/gsd-verify-work` closes this plan: the manual Excel open-and-inspect (D7 in the coverage block above) — a POI round-trip test structurally cannot prove the dropdown renders correctly or that the workbook opens without a repair prompt in real Excel (16-RESEARCH.md Pitfall 5).

## Self-Check: PASSED

Both created test files verified present on disk (`DeskAssignmentTemplateServiceUsualShiftTest.java`,
`DeskAssignmentUploadUsualShiftTest.java`); both commit hashes (`f43abc1`, `81d21c4`) verified present
in `git log --oneline`; plan-level `<verification>` commands re-run green after all edits
(`./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*" --tests "com.wfm.service.DeskAssignmentTemplate*" --tests UploadSyncFailureTest`
and the mandatory full `./gradlew test`, 709 tests, 0 failures, 2 pre-existing ignored).

---
*Phase: 16-usual-shift-storage*
*Completed: 2026-09-03*
