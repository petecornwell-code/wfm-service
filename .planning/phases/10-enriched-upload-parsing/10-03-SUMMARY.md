---
phase: 10-enriched-upload-parsing
plan: 03
subsystem: api
tags: [apache-poi, spring-transactional, flyway, agent_day_hours, bamboohr]

# Dependency graph
requires:
  - phase: 10-enriched-upload-parsing (plan 01)
    provides: agent_day_hours.day_off_type column + AgentDayHours.setDayOffType (D-12)
  - phase: 10-enriched-upload-parsing (plan 02)
    provides: EnrichedColumnLayout single source of column-header truth
provides:
  - Rewritten DeskAssignmentUploadService — multi-sheet (one desk per sheet), EnrichedColumnLayout-driven parser
  - SkippedSheet DTO for structured unmatched-sheet notices
  - parseDayCell: fractional-hours-safe day-cell parser (hours/MANDATORY/PTO, >24 clamp with surfaced warning)
  - Unbounded "Specialty N" header scan replacing the fixed 2-column lookup
  - Extended DeskAssignmentUploadResult (sheetSummaries, warnings, skippedSheets)
  - Both retired upload shapes (6-col legacy, old flat-enriched) rejected file-wide with a "download the new template" message
  - spring.servlet.multipart size limits (10MB) as a zip-bomb/oversized-upload defense
affects: [10-04, 10-05, 10-06, 11-bamboohr-merge-engine]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shape classification against EnrichedColumnLayout constants + normalize() only — no header string literal hardcoded in the parser"
    - "Day-cell parsing reads cell.getNumericCellValue() directly, never through the (long)-truncating getCellString() helper"
    - "Per-sheet clear-then-reimport: clearDesk(deskId) runs once per matched sheet, immediately before that sheet's rows are parsed"

key-files:
  created:
    - src/main/java/com/wfm/dto/SkippedSheet.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java
  modified:
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/main/resources/application.yml
    - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java

key-decisions:
  - "Identity fields (First/Last Name, Job Title, Email, Department, Active) are read via EnrichedColumnLayout constants from Task 1 onward, ahead of Task 2's specialty-scan replacement, since the new shape has no generic name/email columns to fall back on"
  - "Row validation order: BambooHR ID presence -> all 7 day cells valid -> specialty resolution -> BambooHR cache lookup -> non-schedulable check, matching D-09 whole-row skip-and-continue on first failure"
  - "DeskAssignmentUploadNonSchedulableRejectTest (pre-existing, out of this plan's declared file list) was updated to the new one-sheet-per-desk shape — its old 6-col-legacy-style workbook helper was rejected outright by the new D-15 shape classification"

requirements-completed: [UPL-01, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07, UPL-08]

# Metrics
duration: 25min
completed: 2026-07-31
---

# Phase 10 Plan 03: Enriched Upload Parser Rewrite Summary

**Rewrote DeskAssignmentUploadService into a per-desk-sheet, EnrichedColumnLayout-driven parser: fractional-hours-safe day-cell parsing (hours/MANDATORY/PTO with non-silent >24 clamping), BambooHR-ID-only agent matching, unbounded Specialty N columns, and file-wide rejection of both retired upload shapes.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-31T21:28Z (approx, per first task commit)
- **Completed:** 2026-07-31T21:41Z
- **Tasks:** 3
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments
- Multi-sheet iteration where sheet name = desk (D-01), with unmatched sheets reported as non-blocking `SkippedSheet` notices (D-02) instead of aborting the upload
- Both the 6-col legacy shape and the old flat single-sheet enriched shape (per-row `Desk` column) are now rejected file-wide with a "download the new template" message (D-15), classified purely via `EnrichedColumnLayout` constants — zero header string literals hardcoded in the parser
- Fixed the fractional-hours truncation bug: day cells now read `cell.getNumericCellValue()` directly instead of the `(long)`-casting `getCellString()`, so `7.5` stores as `7.50` not `7`
- `agent_day_hours` rows now carry `dayOffType` (MANDATORY/PTO/null) alongside hours, written per weekday, without ever touching `AgentDayOff` — preserving the D-16 union with BambooHR field-4517 blocks
- BambooHR-ID-only matching (D-08) replaces the ~45-line fuzzy name/email fallback; an unmatched ID is rejected with "BambooHR ID not found" and creates no agent
- Unbounded `Specialty N` header scan (D-06) replaces the fixed 2-column lookup
- `DeskAssignmentUploadResult` extended with `sheetSummaries` (per-desk import/skip rollup), `warnings` (clamp notices), and `skippedSheets` (D-11)
- `spring.servlet.multipart.max-file-size`/`max-request-size` (10MB) added as a defensive limit ahead of Apache POI opening the workbook stream (T-10-04)

## Task Commits

Each task was committed atomically:

1. **Task 1: SkippedSheet DTO + workbook shape rejection + multi-sheet iteration + ID-only matching** - `ce37a2b` (feat)
2. **Task 2: Day-cell parsing, Specialty N scan, agent_day_hours writes, extended result DTO, multipart config** - `c246f85` (feat)
3. **Task 3: Retired-shape rejection tests; retire obsolete import-success tests** - `7038e2e` (test)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/dto/SkippedSheet.java` - structured unmatched-sheet notice record
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` - full rewrite: multi-sheet iteration, D-15 shape rejection, `parseDayCell`, unbounded Specialty N scan, `agent_day_hours` writes, extended result DTO
- `src/main/resources/application.yml` - `spring.servlet.multipart` size limits
- `src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java` - new: both retired shapes rejected with the template message
- `src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java` - retired the now-false old-flat-enriched import assertions; kept the valid unknown-shape fallback test
- `src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java` - replaced 4 row-level import/skip assertions with file-wide rejection assertions
- `src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java` - updated workbook helper to the new per-desk-sheet shape (deviation, see below)

## Decisions Made
- Row validation order settled as: BambooHR ID presence -> all 7 day cells valid -> specialty resolution -> BambooHR cache lookup -> already-assigned-elsewhere check -> non-schedulable check, each a whole-row skip-and-continue (D-09)
- Identity fields pulled via `EnrichedColumnLayout` constants (First/Last Name, Job Title, Email, Department, Active) starting in Task 1, since the new shape has no generic "name"/"email" columns to fall back on the way the retired shapes did
- Task 1 intentionally kept the fixed `"specialty 1"`/`"specialty 2"` column lookup (matching the plan's explicit Task 1 → Task 2 sequencing) before Task 2 replaced it with the unbounded header scan

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `DeskAssignmentUploadNonSchedulableRejectTest`, broken by the shape-rejection rewrite**
- **Found during:** Task 3 (running `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"`)
- **Issue:** This pre-existing test (not in the plan's declared `files_modified` list) built workbooks using the old legacy-style header set (`BambooHR ID | Name | Email | Desk Assignment`), which Task 1's D-15 shape classification now rejects file-wide before any row is parsed — all 3 tests failed.
- **Fix:** Updated the workbook-builder helper to the new one-sheet-per-desk shape (sheet name = desk, headers = `BambooHR ID | Name | Email | Monday..Sunday`, no per-row `Desk` column), switched `findCachedEmployee` stubs to the ID-only 1-arg-matching signature (`eq(id), isNull(), isNull()`), and made day-cell values real numeric POI cells (not strings) so `parseDayCell` accepts them as hours.
- **Files modified:** src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
- **Verification:** `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` green; full `./gradlew test` suite green (8m6s)
- **Committed in:** 7038e2e (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix, scoped strictly to fallout from this plan's own shape-classification change)
**Impact on plan:** Necessary to keep the `DeskAssignmentUpload*` test suite green per the plan's verification gate. No scope creep — the fix only updates test fixtures to the new shape contract this plan introduces.

## Issues Encountered
- Initial version of the `DeskAssignmentUploadNonSchedulableRejectTest` fix wrote day-cell values as POI string cells (`"8"`), which `parseDayCell` correctly rejected as neither numeric nor MANDATORY/PTO — resolved by writing real numeric cells for values matching a number pattern.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The parser is fully wired to the Phase 9 `agent_day_hours` model (including plan 01's `day_off_type` column) and Phase 10 plan 02's `EnrichedColumnLayout`.
- `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` and the full `./gradlew test` suite are green.
- Remaining Phase 10 work (per ROADMAP): template generation/download (D-13/D-14), export round-trip symmetry via `EnrichedColumnLayout`, and the frontend Upload Results modal extension (per-sheet rollup + warnings, D-11) are not part of this plan and remain open for subsequent plans (10-04+).
- No blockers identified.

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*

## Self-Check: PASSED
