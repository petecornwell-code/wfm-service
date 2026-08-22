---
phase: 13-per-day-hours-visibility
plan: 03
subsystem: api
tags: [poi, excel-export, java, spring-boot]

# Dependency graph
requires:
  - phase: 13-per-day-hours-visibility-plan-01
    provides: "DeskAgentResponse.dayHours: Map<DayOfWeek, DayHoursEntry> — always 7 keys, hasRow/hours/dayOffType/effectiveHours"
provides:
  - "EnrichedColumnLayout.specialtyHeader(int) — single source for 'Specialty N' header text"
  - "DeskAgentExportService seven Mon-Sun export columns resolved from agent_day_hours"
  - "First-ever automated test coverage for DeskAgentExportService (9 tests)"
affects: [13-per-day-hours-visibility-plan-04]

# Actuals (#2632)
actuals:
  tokens: 5520
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Header array converted from a fixed String[] to a List<String> seeded with the fixed entries, then extended programmatically from EnrichedColumnLayout.DAY_ORDER — the same 'never hardcode weekday names' convention D-13/D-08 already establishes elsewhere"
    - "Per-cell branching order (MANDATORY -> PTO -> stored-hours -> resolved-default) mirrors 13-UI-SPEC.md Section 3's display-mode rules exactly, load-bearing because a labelled day is stored as zero hours"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAgentExportServiceTest.java
  modified:
    - src/main/java/com/wfm/util/EnrichedColumnLayout.java
    - src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
    - src/main/java/com/wfm/service/DeskAgentExportService.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java

key-decisions:
  - "Adopted PLAN.md's P-08/P-09 planner decisions verbatim — an unset weekday exports its resolved effective value (never blank, to avoid the upload parser skipping the whole row), and the 7 day columns are inserted immediately after 'Effective Contracted Hours Per Day', shifting First Name/Last Name from indices 13/14 to 20/21"
  - "Missing dayHours entry (map null or key absent) is treated as a defensive-only branch that writes numeric 0 — plan 13-01 guarantees all 7 keys are always present, so this path is unreachable in production but present for defense-in-depth"

requirements-completed: [MDL-02, UPL-09]

coverage:
  - id: D1
    description: "Specialty headers in the blank upload template are sourced from EnrichedColumnLayout.specialtyHeader(int), a single source shared with the detection regex, closing audit finding I-4"
    requirement: "UPL-09"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java#specialtyHeader_matchesTheDetectionRegex"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java#generateTemplate_headerRowMatchesEnrichedColumnLayoutPlusDaysAndSpecialties"
        status: pass
    human_judgment: false
  - id: D2
    description: "The desk-agent Excel export carries seven Mon-Sun columns sourced from EnrichedColumnLayout, positioned after the Effective Contracted Hours Per Day column, with every day cell branching MANDATORY/PTO/stored-hours/resolved-default in the order the upload parser requires for a lossless round trip"
    requirement: "MDL-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#headerRow_matchesTheFullExpectedOrder"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#mandatoryWeekday_writesTheKeywordNotZero"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#ptoWeekday_writesTheKeywordNotZero"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#explicitZeroWeekday_writesNumericZero"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#workedWeekday_writesTheFractionalValue"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#notSetWeekday_writesTheResolvedEffectiveValue_notBlank"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#everyExportedDayCell_isAcceptedByTheUploadParsersContract"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#effectiveContractedHoursColumn_reflectsThePerDayModel"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentExportServiceTest.java#identityColumnsStillSanitized"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-08-22
status: complete
---

# Phase 13 Plan 03: Excel Export Per-Day Columns Summary

**The desk-agent Excel export now carries seven Mon–Sun columns resolved from `agent_day_hours` (not the retired scalar), and both specialty header strings in the upload template are sourced from a new `EnrichedColumnLayout.specialtyHeader(int)` factory instead of local literals.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-08-22T00:30:00Z
- **Completed:** 2026-08-22T00:55:00Z
- **Tasks:** 2 (both auto/tdd)
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments
- Closed audit finding I-4 (D-08): `DeskAssignmentTemplateService`'s two hardcoded `"Specialty 1"`/`"Specialty 2"` constants are gone; both now come from `EnrichedColumnLayout.specialtyHeader(int)`, round-tripped against the existing `specialtyIndex` detection regex by a new test
- Closed the export half of audit finding I-1/F-1 (D-02): `DeskAgentExportService` gained seven Mon–Sun columns immediately after "Effective Contracted Hours Per Day", sourced from `EnrichedColumnLayout.dayHeader`/`DAY_ORDER` — no weekday name is a string literal anywhere in the file
- Every exported day cell branches MANDATORY → PTO → stored-hours → resolved-default in that exact order, so a labelled day exports its keyword (not `0`) and an unset day exports its resolved default (never blank) — completing a lossless export → fill → re-upload round trip
- First-ever automated test file for `DeskAgentExportService` (9 tests, plain JUnit 5 + AssertJ, no Spring context), pinning the full 22-cell header row and all five day-cell display states plus the column-shift-safe sanitizer coverage

## Task Commits

Each task followed RED (test) → GREEN (production) per its `tdd="true"` marking:

1. **Task 1: Specialty headers sourced from the shared column layout (I-4/D-08)**
   - `a0bb3d4` test(13-03): pin specialty headers to EnrichedColumnLayout factory
   - `18e771e` feat(13-03): source specialty headers from EnrichedColumnLayout (I-4/D-08)
2. **Task 2: Seven Mon-Sun export columns resolved from agent_day_hours (I-1/D-02)**
   - `b386d0d` test(13-03): add failing coverage for Mon-Sun export columns
   - `f3dc5a5` feat(13-03): export seven Mon-Sun columns from agent_day_hours (I-1/D-02)

**Plan metadata:** commit pending (this SUMMARY + STATE/ROADMAP/REQUIREMENTS update)

_TDD gate check: RED precedes GREEN for both tasks —_
_Task 1: `a0bb3d4` (test) confirmed failing to compile (`cannot find symbol: specialtyHeader(int)`) via a temporary `git stash` of the two production files, then `18e771e` (feat) made it green (5/5 template tests pass)._
_Task 2: `b386d0d` (test) confirmed 8/9 new tests failing (NPE/assertion errors — no day columns existed yet) against the pre-change service, then `f3dc5a5` (feat) made it green (9/9 pass)._

## Files Created/Modified
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java` - adds `specialtyHeader(int)` static factory beside `dayHeader(DayOfWeek)`
- `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` - removes `SPECIALTY_1_HEADER`/`SPECIALTY_2_HEADER` constants, calls the new factory
- `src/main/java/com/wfm/service/DeskAgentExportService.java` - `columns` array becomes a `List<String>` seeded then extended with 7 day headers; new `writeDayCells` helper; First Name/Last Name shift from indices 13/14 to 20/21
- `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java` - `expectedHeaders()` now calls the factory; new `specialtyHeader_matchesTheDetectionRegex` test
- `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` - new 9-test file: header-row pin, 5 day-cell states, round-trip contract, effective-hours-column, and post-shift sanitizer coverage

## Decisions Made
- Adopted PLAN.md's P-08/P-09 planner decisions verbatim (unset weekday exports its resolved effective value, never blank; 7 day columns inserted immediately after "Effective Contracted Hours Per Day", shifting the two identity columns right)
- See "Deviations from Plan" below — none required this plan; the pattern map and read_first files matched the live code exactly

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Full `./gradlew test` run took ~7.5 minutes (includes solver test suite) — ran in background, confirmed `BUILD SUCCESSFUL`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 13-04 (per-weekday editing UI, D-03/D-04/D-05/D-07) depends only on 13-01 and 13-02, not on this plan — it is unaffected by and can proceed independently of this Excel-export work
- Operators can now export a desk's roster, edit the Mon–Sun columns (including `MANDATORY`/`PTO` keywords), and re-upload without losing per-day detail — the round trip described in D-02 is complete
- Recommend a human UAT pass exporting a live desk with a mixed MANDATORY/PTO/worked/not-set roster and re-uploading the result, to confirm the round trip end-to-end against a real spreadsheet client (Excel/LibreOffice cell-type rendering was not visually inspected in this session — automated POI-level assertions only)

## Self-Check: PASSED

- All 5 key files confirmed present on disk (`[ -f ]`)
- All 4 task commit hashes confirmed present in `git log --oneline --all`

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-22*
