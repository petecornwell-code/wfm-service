---
status: partial
phase: 16-usual-shift-storage
source: 16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md, 16-04-SUMMARY.md, 16-05-SUMMARY.md
started: 2026-09-03T21:30:59Z
updated: 2026-09-04T00:05:00Z
---

## Current Test

[testing paused — 1 item outstanding: test 3 blocked on real Microsoft Excel]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server/service. Clear ephemeral state (temp DBs, caches, lock files). Start the application from scratch. Flyway migration V47__add_agent_usual_shift.sql applies cleanly, Hibernate ddl-auto=validate passes against the new agent_usual_shift table, the service boots with no errors, and a primary query (roster GET for a desk) returns live data.
result: pass
source: automated (live-environment verification, 2026-09-03)
evidence: |
  Deploy run 33804010801 booted the backend cold in ECS against the live Postgres.
  Flyway applied V47__add_agent_usual_shift.sql and Hibernate ddl-auto=validate (confirmed
  set in application.yml:13) was satisfied — a mismatched entity/migration aborts boot, so a
  successful boot IS the validate proof. /actuator/health returns UP with db UP
  (PostgreSQL, isValid()). A primary query — GET /desks/{id}/agents — returns live data
  including the usualShift block for all seven weekdays. Independently, the local suite drops
  and recreates agent_usual_shift each run, and AgentUsualShiftPostgresTest runs real Flyway
  from empty against a fresh Postgres container.

### 2. Backend Suite Baseline and the Flaky-Test Reading
expected: The full backend suite (./gradlew test) is green at or above the 720-test baseline. The one failure recorded during 16-02 (MultiDayConstraintDiagnosticTest, solver package) was ruled flaky and unrelated by an isolated re-run — a human confirms that reading rather than the executor self-certifying its own root-cause analysis of a test it did not write.
result: pass
source: human confirmation on independent full-suite evidence
evidence: |
  Independent full-suite re-run 2026-09-03: ./gradlew test -> BUILD SUCCESSFUL in 10m 7s,
  720 tests, 0 failures, 0 errors, 2 skipped (the two pre-existing ignored benchmarks).
  MultiDayConstraintDiagnosticTest PASSED in this run — the 16-02 failure did not reproduce.
  Operator ruled the flaky reading sound: the solver is timing/heuristic-sensitive so
  intermittent failure there is plausible, and phase 16 is proven not to touch the solver
  both structurally (UsualShiftWritePathTest#solverPackageAndSolverService_declareNo
  AgentUsualShiftReference_structural) and behaviourally (a real solve leaves stored rows
  byte-identical). Confirmed by a human, not self-certified by the executor.

### 3. Excel Open-and-Inspect of a Generated Per-Desk Template
expected: Download a per-desk template for a desk with a live shift library. It opens in real Microsoft Excel (not LibreOffice alone) with no repair prompt, and clicking a Usual Shift cell shows a working dropdown of that desk's live template names.
result: blocked
blocked_by: third-party
reason: "Requires real Microsoft Excel, which is not available in this session. Operator elected to leave it blocked rather than open it now."
structural_evidence: |
  Everything reachable without Excel was verified against the LIVE deployed template
  (GET /api/v1/client-management/desk-assignments/template, 2026-09-03):
    - 7 dataValidation blocks, one per Usual Shift column, sqref O2:U1048576
    - headers "Usual Shift Monday".."Usual Shift Sunday" at columns O-U
    - each formula1 is 104 chars: "Daytime,Early,Late,Mid,Morning,Weekend Closing,
      Weekend Early,Weekend Flex,Weekend Late,Weekend Opening" — 151 chars of headroom
      under the 255-char explicit-list limit that is the actual failure mode
    - the retired "Weekend Morning" (effectiveTo 2026-01-01) is correctly EXCLUDED,
      confirming the dropdown carries live template names only
    - pre-fill works: with Mon/Tue set on Adaeze Dawari, cols O and P carried "Early"
      and Q-U were blank, so the D-11 download-then-reupload no-op property holds
  What remains unproven is ONLY whether Microsoft Excel itself opens the file without a
  repair prompt and renders the dropdowns interactively. The 255-char limit — the failure
  mode this test exists to catch — is measured and clear.
  Downloaded copy retained at ~/Downloads/wfm-desk-assignment-template-2026-09-03.xlsx
  (sha256 919fa98dbfdb988a82df9b893eb4a73d33bc0f6c46499e2102fd4f3b4c0eccce).

### 4. Roster Tile Three-State Visual QA
expected: On a desk with a live shift library, all three D-16 states render as a distinguishable second line inside each day tile — never-set (light gray en dash), live (accent-blue bold), stored-but-not-in-effect (italic muted gray). The states differ by colour and slant without reading the text, and a clipped long name shows the full value on hover.
result: pass
source: automated (live browser, computed styles read from the DOM)
evidence: |
  Drove the live app to produce all three states on ONE agent row (Adaeze Dawari), then read
  getComputedStyle from the live DOM rather than eyeballing a screenshot:
    State A (never-set, Wed/Thu/Fri/Sat/Sun): text "-", rgb(209,213,219)=#d1d5db, weight 400, upright
    State B (LIVE, Tue):                      text "Early", rgb(59,130,246)=#3b82f6, weight 600, upright
    State C (STORED_INACTIVE, Mon/MANDATORY): text "Early - not worked", rgb(156,163,175)=#9ca3af, weight 400, ITALIC
  Every token matches 16-UI-SPEC.md Component Specifications S1. The three states differ by
  colour AND weight AND slant, so they are distinguishable without reading the text.
  Clipping/hover: the State C line has scrollWidth 96 > clientWidth 90 (genuinely clipped) and
  carries title="Early - not worked on Monday", so hover surfaces the full value.
  Tile geometry: all seven tiles measure exactly 90px wide at a 98px pitch = 7x90 + 6x8 = 678px,
  confirming feff8e4 live.
caveat: |
  The usual-shift line still sits 1px lower on MAND days (top 464 vs 463) on the DEPLOYED bundle.
  This is the known residual: b01329d (the 28px fix) is committed but not yet deployed — the last
  deploy was dabcb8a. Not a new defect; an 8px error already reduced to 1px.

### 5. Inline Usual-Shift Picker Behaviour
expected: The inline native <select> lets an operator set, change and clear an agent's usual shift with an explicit "— none —" option. It offers only the desk's live CURRENT-era templates valid for that weekday, disables retired options, shows an inline error on a D-03 rejection, and never raises a confirmation dialog. Setting a value inline then exporting the desk shows the same template name in that weekday's Usual Shift export column.
result: pass
source: automated (live browser + live API + live export)
evidence: |
  Set:    Tuesday -> "Early" via the inline native <select>; saved on selection, no dialog raised.
  Set:    Monday -> "Early" (a MANDATORY day) — stored and inert, rendering as State C (D-04).
  Clear:  both cleared through the explicit "- none -" option; server re-read confirms every
          agent back to NOT_SET, restoring the desk to its pre-QA state.
  Options: the Tuesday picker offered exactly "- none -", Daytime, Early, Late, Mid, Morning —
          5 of the library's 11 templates. Correctly excluded: the 5 Weekend * templates
          (validWeekdays excludes Tuesday) AND "Weekend Morning" (effectiveTo 2026-01-01, i.e.
          retired, so not CURRENT-era). "- none -" first, remainder alphabetical, per D-17.
  D-03 rejection: forced by injecting the Saturday-only "Weekend Early" id into the Monday
          picker. The server refused and the UI rendered an INLINE error
          "Shift template 'Weekend Early' is not valid on MONDAY" in #92400e — no dialog,
          no navigation, editor stayed open.
  Export trace: with Mon+Tue set, GET /agents/export put the raw stored name "Early" in
          col U "Usual Shift Monday" and col V "Usual Shift Tuesday", blanks in W-AA, day-hours
          col N still "MANDATORY", First/Last Name at AB/AC — exactly the D6 layout.
partial: |
  "disables retired options" was NOT observed live. That branch (usualShiftOptions,
  DeskAgents.tsx:98-100) renders a disabled "{name} (retired)" entry only when the entry ALREADY
  stored is STORED_INACTIVE/RETIRED — unreachable through the UI without first retiring a
  template that an agent already uses, which would mutate the live shift library. Verified by
  code inspection; the underlying state is covered by DeskAgentServiceUsualShiftTest.

### 6. [16-01 D1] An operator can store an agent's usual shift for Monday through the choke-point PUT endpoint and see it in the same request's response body
expected: An operator can store an agent's usual shift for Monday through the choke-point PUT endpoint and see it in the same request's response body
result: pass
source: automated
coverage_id: D1
covered_by: integration: UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd

### 7. [16-01 D2] The stored shift is visible on the roster GET (listDeskAgentResponses) and in the Excel export, at the correct column indices, with no mocked layer between store and read
expected: The stored shift is visible on the roster GET (listDeskAgentResponses) and in the Excel export, at the correct column indices, with no mocked layer between store and read
result: pass
source: automated
coverage_id: D2
covered_by: integration: UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd

### 8. [16-01 D3] An agent with no stored usual shift resolves to NOT_SET with a null name, never a substitute value
expected: An agent with no stored usual shift resolves to NOT_SET with a null name, never a substitute value
result: pass
source: automated
coverage_id: D3
covered_by: integration: UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd

### 9. [16-01 D4] Setting a usual shift is rejected inline (400) when the template's weekday mask excludes the target day (D-03) or the template is not currently effective (P-03)
expected: Setting a usual shift is rejected inline (400) when the template's weekday mask excludes the target day (D-03) or the template is not currently effective (P-03)
result: pass
source: automated
coverage_id: D4
covered_by: integration: UsualShiftTracerTest#weekdayMaskViolation_isRejected; integration: UsualShiftTracerTest#retiredTemplate_isRejected

### 10. [16-01 D5] The write is a genuine choke point: cross-desk agent access and cross-desk template references are both rejected, and a repeated set for the same weekday leaves exactly one row
expected: The write is a genuine choke point: cross-desk agent access and cross-desk template references are both rejected, and a repeated set for the same weekday leaves exactly one row
result: pass
source: automated
coverage_id: D5
covered_by: integration: UsualShiftTracerTest#wrongDesk_throwsEntityNotFound_andWritesNoRow; integration: UsualShiftTracerTest#crossDeskTemplate_isRejected; integration: UsualShiftTracerTest#repeatedSetForSameWeekday_leavesExactlyOneRow

### 11. [16-01 D6] The seven Usual Shift export columns land at the correct indices (20-26) immediately after the day-hours group, with First/Last Name shifted to 27/28, and the exported cell carries the raw stored template name
expected: The seven Usual Shift export columns land at the correct indices (20-26) immediately after the day-hours group, with First/Last Name shifted to 27/28, and the exported cell carries the raw stored template name
result: pass
source: automated
coverage_id: D6
covered_by: integration: UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd; unit: DeskAgentExportServiceTest#headerRow_matchesTheFullExpectedOrder

### 12. [16-01 D7] The migration and entity are proven consistent by two independent mechanisms, one of which runs real Flyway migrations against real Postgres with ddl-auto=validate (G-14-1 class of bug cannot ship green again)
expected: The migration and entity are proven consistent by two independent mechanisms, one of which runs real Flyway migrations against real Postgres with ddl-auto=validate (G-14-1 class of bug cannot ship green again)
result: pass
source: automated
coverage_id: D7
covered_by: unit: MigrationEntityConsistencyTest#migrationDeclaredColumns_reconcileWithEntityMappings; integration: AgentUsualShiftPostgresTest#persistAndReload_roundTripsEveryField; integration: AgentUsualShiftPostgresTest#dayOfWeekColumn_isVariableLengthNine

### 13. [16-01 D8] Deleting a desk with stored usual shifts still succeeds — the shift_template_id cascade is not blocked by a dangling reference (T-16-03)
expected: Deleting a desk with stored usual shifts still succeeds — the shift_template_id cascade is not blocked by a dangling reference (T-16-03)
result: pass
source: automated
coverage_id: D8
covered_by: integration: AgentUsualShiftPostgresTest#deletingDesk_cascadesThroughShiftTemplateToUsualShiftRows

### 14. [16-02 D1] Deleting a shift template referenced by any agent_usual_shift row is refused with a ConflictException naming the template and the count; retiring the same template via updateShiftTemplate still succeeds
expected: Deleting a shift template referenced by any agent_usual_shift row is refused with a ConflictException naming the template and the count; retiring the same template via updateShiftTemplate still succeeds
result: pass
source: automated
coverage_id: D1
covered_by: unit: ShiftTemplateServiceTest#deleteShiftTemplate_referencedByAgentUsualShift_isRefusedAndDirectedToRetire; unit: ShiftTemplateServiceTest#updateShiftTemplate_retiringAReferencedTemplate_stillSucceeds

### 15. [16-02 D2] D-01 era-following, D-02 retirement-degrades-to-unset, USHF-04 null-safety, and USHF-01's adjacency/encoding/desk-scoping edge probes are passing assertions against the one resolution implementation
expected: D-01 era-following, D-02 retirement-degrades-to-unset, USHF-04 null-safety, and USHF-01's adjacency/encoding/desk-scoping edge probes are passing assertions against the one resolution implementation
result: pass
source: automated
coverage_id: D2
covered_by: unit: UsualShiftResolutionServiceTest (6 methods)

### 16. [16-02 D3] The roster response distinguishes all four D-16 states (NOT_SET, LIVE, STORED_INACTIVE/RETIRED, STORED_INACTIVE/NOT_WORKED) computed entirely server-side, with RETIRED taking precedence over NOT_WORKED when both hold
expected: The roster response distinguishes all four D-16 states (NOT_SET, LIVE, STORED_INACTIVE/RETIRED, STORED_INACTIVE/NOT_WORKED) computed entirely server-side, with RETIRED taking precedence over NOT_WORKED when both hold
result: pass
source: automated
coverage_id: D3
covered_by: unit: DeskAgentServiceUsualShiftTest#allFourStates_reachableOnOneAgentInOneResponse; unit: DeskAgentServiceUsualShiftTest#precedence_retiredAndPto_reportsRetiredNotNotWorked

### 17. [16-02 D4] D-05's hours advisory: fires on any-band mismatch, stays silent when at least one band matches exactly, is null for NOT_SET and NOT_WORKED, never blocks the write, and surfaces a mismatch introduced later by an unrelated contracted-hours edit
expected: D-05's hours advisory: fires on any-band mismatch, stays silent when at least one band matches exactly, is null for NOT_SET and NOT_WORKED, never blocks the write, and surfaces a mismatch introduced later by an unrelated contracted-hours edit
result: pass
source: automated
coverage_id: D4
covered_by: unit: DeskAgentServiceUsualShiftTest (10 D-05 methods)

### 18. [16-02 D5] D-04: a usual shift may be stored on a weekday the agent does not work, is stored and inert, and neither write path crosses into the other's table
expected: D-04: a usual shift may be stored on a weekday the agent does not work, is stored and inert, and neither write path crosses into the other's table
result: pass
source: automated
coverage_id: D5
covered_by: unit: DeskAgentServiceUsualShiftTest#d04_storageOnAMandatoryWeekday_succeedsAndPersists; unit: DeskAgentServiceUsualShiftTest#d04_orthogonality_neitherWriteCrossesIntoTheOtherTable

### 19. [16-02 D6] D-12: removing an agent from a desk clears their usual shifts through the same clearUsualShifts implementation clearDesk will call, idempotently, without touching agent_day_hours
expected: D-12: removing an agent from a desk clears their usual shifts through the same clearUsualShifts implementation clearDesk will call, idempotently, without touching agent_day_hours
result: pass
source: automated
coverage_id: D6
covered_by: unit: DeskAgentServiceUsualShiftTest (4 D-12/USHF-05 methods)

### 20. [16-03 D1] The generated per-desk template pre-fills each agent's stored usual shift with the raw stored name and attaches a sheet-scoped dropdown of the desk's live template names to each of the seven Usual Shift columns
expected: The generated per-desk template pre-fills each agent's stored usual shift with the raw stored name and attaches a sheet-scoped dropdown of the desk's live template names to each of the seven Usual Shift columns
result: pass
source: automated
coverage_id: D1
covered_by: unit: DeskAssignmentTemplateServiceUsualShiftTest#preFillsStoredUsualShift_blankWhereNotStored; unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_happyPath_sevenValidationsWithLiveTemplateNames; unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_adjacency_deskBValidationsExcludeDeskANames

### 21. [16-03 D2] The dropdown degrades gracefully (skips itself, keeps headers/pre-fill) at the 255-char Excel data-validation limit and for a comma/double-quote in a template name, pinned at both sides of the 255/256 boundary
expected: The dropdown degrades gracefully (skips itself, keeps headers/pre-fill) at the 255-char Excel data-validation limit and for a comma/double-quote in a template name, pinned at both sides of the 255/256 boundary
result: pass
source: automated
coverage_id: D2
covered_by: unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_boundaryAt255_stillAttachesValidations; unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_boundaryAt256_skipsValidationsButKeepsHeadersAndPreFill; unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_templateNameContainsComma_skipsValidations; unit: DeskAssignmentTemplateServiceUsualShiftTest#dropdown_templateNameContainsDoubleQuote_skipsValidations

### 22. [16-03 D3] The upload parser reads the seven Usual Shift columns with D-07 (blank valid, no warning), D-08 (unresolvable name skips only the cell, warns, row still imports), D-03 (weekday-mask violation is a cell-level skip, not a 400), and P-12 (trim+lowercase matching with an explicit ambiguity path) semantics
expected: The upload parser reads the seven Usual Shift columns with D-07 (blank valid, no warning), D-08 (unresolvable name skips only the cell, warns, row still imports), D-03 (weekday-mask violation is a cell-level skip, not a 400), and P-12 (trim+lowercase matching with an explicit ambiguity path) semantics
result: pass
source: automated
coverage_id: D3
covered_by: unit: DeskAssignmentUploadUsualShiftTest#happyPath_resolvedName_writesOneRowForThatWeekday; unit: DeskAssignmentUploadUsualShiftTest#blankCell_writesNoRow_noWarning_restOfRowImports; unit: DeskAssignmentUploadUsualShiftTest#unresolvedName_skipsCellOnly_warnsAndRestOfRowImports; unit: DeskAssignmentUploadUsualShiftTest#weekdayMaskExcludesDay_skipsCellOnly_warnsAndRestImports; unit: DeskAssignmentUploadUsualShiftTest#lowercaseSpacePaddedName_resolvesViaNormalize; unit: DeskAssignmentUploadUsualShiftTest#ambiguousNormalizedName_skipsCellOnly_warnsAmbiguity_neitherTemplateWritten; unit: DeskAssignmentUploadUsualShiftTest#numericTypedCell_matchingAllDigitsTemplateName_resolves; unit: DeskAssignmentUploadUsualShiftTest#warningsOnMultipleWeekdays_appearInDayOrderSequence

### 23. [16-03 D4] D-09 and D-11 are load-bearing for each other: clearDesk wipes usual shifts, and a download-then-re-upload of a per-desk template is a no-op because the template pre-fills exactly what the wipe clears
expected: D-09 and D-11 are load-bearing for each other: clearDesk wipes usual shifts, and a download-then-re-upload of a per-desk template is a no-op because the template pre-fills exactly what the wipe clears
result: pass
source: automated
coverage_id: D4
covered_by: unit: DeskAssignmentUploadUsualShiftTest#downloadThenReupload_isANoOp_forStoredUsualShifts; unit: DeskAssignmentUploadUsualShiftTest#allUsualShiftCellsBlank_clearDeskWipesEveryCurrentDeskAgentsUsualShifts

### 24. [16-03 D5] P-11: a sheet missing the seven Usual Shift headers is skipped with a specific notice and the desk is NOT cleared (CR-01 property), preventing a pre-Phase-16 workbook from silently wiping every stored usual shift with nothing to re-supply
expected: P-11: a sheet missing the seven Usual Shift headers is skipped with a specific notice and the desk is NOT cleared (CR-01 property), preventing a pre-Phase-16 workbook from silently wiping every stored usual shift with nothing to re-supply
result: pass
source: automated
coverage_id: D5
covered_by: unit: DeskAssignmentUploadUsualShiftTest#sheetMissingUsualShiftHeaders_reportedInSkippedSheets_deskNotCleared

### 25. [16-03 D6] Full backend suite stays green at or above the 688-test baseline after this plan's changes to clearDesk and the enriched-shape header contract
expected: Full backend suite stays green at or above the 688-test baseline after this plan's changes to clearDesk and the enriched-shape header contract
result: pass
source: automated
coverage_id: D6
covered_by: integration: ./gradlew test (709 tests, 0 failures, 2 pre-existing ignored benchmark tests)

### 26. [16-04 D1] A single canonical table enumerates all nine reachable usual-shift write paths (D-14's seven plus P-18's two planner additions), each stating what must hold and naming a test that actually exercises that path
expected: A single canonical table enumerates all nine reachable usual-shift write paths (D-14's seven plus P-18's two planner additions), each stating what must hold and naming a test that actually exercises that path
result: pass
source: automated
coverage_id: D1
covered_by: unit: UsualShiftWritePathGuardTest#theTableHasExactlyNineDataRows_withNoBlankRequiredCells; unit: UsualShiftWritePathGuardTest#everyProvingTestNamedInTheTable_resolvesToAnExistingClass

### 27. [16-04 D2] A structural completeness guard fails the build when a new writer of AgentUsualShiftRepository or the AgentUsualShift entity type appears anywhere in src/main/java without a corresponding table row, using set equality only (never subset/containment)
expected: A structural completeness guard fails the build when a new writer of AgentUsualShiftRepository or the AgentUsualShift entity type appears anywhere in src/main/java without a corresponding table row, using set equality only (never subset/containment)
result: pass
source: automated
coverage_id: D2
covered_by: unit: UsualShiftWritePathGuardTest#repositoryReferenceSet_matchesTheTableAllowlistExactly; unit: UsualShiftWritePathGuardTest#entityReferenceSet_matchesTheTableAllowlistExactly

### 28. [16-04 D3] The guard is proven able to actually fail: a test-of-the-test removes one entry from a copy of the allowlist and asserts the equality check trips, AND a real deliberate-break manual check (a field temporarily added to a class outside the allowlist) produces a failure message naming that exact class
expected: The guard is proven able to actually fail: a test-of-the-test removes one entry from a copy of the allowlist and asserts the equality check trips, AND a real deliberate-break manual check (a field temporarily added to a class outside the allowlist) produces a failure message naming that exact class
result: pass
source: automated
coverage_id: D3
covered_by: unit: UsualShiftWritePathGuardTest#deliberatelyBrokenAllowlist_isDetectedAsAMismatch; manual_procedural: Deliberate-break check against UsualShiftResolutionService.java, see Deviations section for the exact captured failure message

### 29. [16-04 D4] BambooHR refresh (row 5) leaves stored usual shifts byte-identical, proven both behaviourally (real persisted rows, real refreshDeskAgents call, re-read and compared) and structurally (no AgentUsualShiftRepository-typed field on BambooRefreshService)
expected: BambooHR refresh (row 5) leaves stored usual shifts byte-identical, proven both behaviourally (real persisted rows, real refreshDeskAgents call, re-read and compared) and structurally (no AgentUsualShiftRepository-typed field on BambooRefreshService)
result: pass
source: automated
coverage_id: D4
covered_by: integration: UsualShiftWritePathTest#refreshDeskAgents_leavesStoredUsualShiftsByteIdentical_behavioural; unit: UsualShiftWritePathTest#refreshDeskAgents_declaresNoAgentUsualShiftRepositoryField_structural

### 30. [16-04 D5] A SLOT-SHIFT-SLOT mode-switch round trip leaves every stored usual-shift row field-identical in both directions (D-13), proven the same field-by-field way MODE-04 was
expected: A SLOT-SHIFT-SLOT mode-switch round trip leaves every stored usual-shift row field-identical in both directions (D-13), proven the same field-by-field way MODE-04 was
result: pass
source: automated
coverage_id: D5
covered_by: integration: UsualShiftWritePathTest#switchSchedulingMode_roundTrip_leavesStoredUsualShiftsFieldIdentical

### 31. [16-04 D6] An actual Timefold solve run leaves stored usual-shift rows field-identical and produces non-vacuous output, plus the solver package and SolverService structurally hold no reference to AgentUsualShiftRepository/AgentUsualShift
expected: An actual Timefold solve run leaves stored usual-shift rows field-identical and produces non-vacuous output, plus the solver package and SolverService structurally hold no reference to AgentUsualShiftRepository/AgentUsualShift
result: pass
source: automated
coverage_id: D6
covered_by: integration: UsualShiftWritePathTest#solve_leavesStoredUsualShiftsUntouched_andProducesRealOutput; unit: UsualShiftWritePathTest#solverPackageAndSolverService_declareNoAgentUsualShiftReference_structural

### 32. [16-04 D7] Full backend suite stays green at or above the 709-test baseline after this plan's additions
expected: Full backend suite stays green at or above the 709-test baseline after this plan's additions
result: pass
source: automated
coverage_id: D7
covered_by: integration: ./gradlew test (720 tests, 0 failures, 2 pre-existing ignored)

### 33. [16-05 D2] D-05's hours advisory renders as an amber '!' marker with the advisory sentence as the line's title, additive to (never replacing) the state's own text and colour
expected: D-05's hours advisory renders as an amber '!' marker with the advisory sentence as the line's title, additive to (never replacing) the state's own text and colour
result: pass
source: automated
coverage_id: D2
covered_by: other: source assertion — hoursAdvisory referenced twice (branch + title override) and #92400e present exactly once inside UsualShiftLine

### 34. [16-05 D4] npm run build and npx tsc --noEmit both succeed with zero TypeScript errors and no unused-local warnings after both tasks
expected: npm run build and npx tsc --noEmit both succeed with zero TypeScript errors and no unused-local warnings after both tasks
result: pass
source: automated
coverage_id: D4
covered_by: other: cd frontend && npm run build; cd frontend && npx tsc --noEmit

## Summary

total: 34
passed: 33
issues: 0
pending: 0
skipped: 0
blocked: 1

## Gaps

[none yet]
