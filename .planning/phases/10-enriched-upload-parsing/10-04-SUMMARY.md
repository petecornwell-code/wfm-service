---
phase: 10-enriched-upload-parsing
plan: 04
subsystem: api
tags: [poi, xlsx, spring, formula-injection, desk-assignments]

# Dependency graph
requires:
  - phase: 10-enriched-upload-parsing (plan 02)
    provides: EnrichedColumnLayout single-source-of-truth column definitions (identityHeaders, DAY_ORDER, dayHeader)
provides:
  - DeskAssignmentTemplateService — pre-seeded per-desk .xlsx template generator (D-14)
  - GET /api/v1/client-management/desk-assignments/template download endpoint
  - DeskAgentExportService aligned to EnrichedColumnLayout for round-trip header symmetry (D-13)
affects: [10-enriched-upload-parsing (frontend plan consuming the template download), 11-bamboohr-merge-engine]

# Tech tracking
tech-stack:
  added: []
  patterns: [POI XSSFWorkbook byte[] attachment response, WorkbookUtil.createSafeSheetName for desk-name-derived sheet names, server-side formula/CSV-injection sanitize() mirroring frontend guard]

key-files:
  created:
    - src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java
  modified:
    - src/main/java/com/wfm/controller/ClientManagementController.java
    - src/main/java/com/wfm/service/DeskAgentExportService.java

key-decisions:
  - "Sheet names derived from desk.getName() are passed through POI's WorkbookUtil.createSafeSheetName to avoid a runtime exception for desks whose names contain invalid Excel sheet-name characters or exceed 31 chars (Rule 2 — not explicitly specified in the plan, but required for the createSheet call the plan mandates to be correct for all tenant desk names)"
  - "Template pre-seeds exactly 2 blank specialty columns (Specialty 1/2) per D-14's minimum viable seed; the parser's EnrichedColumnLayout.specialtyIndex() still accepts unbounded Specialty N on re-upload"

requirements-completed: [UPL-09]

# Metrics
duration: 22min
completed: 2026-07-31
---

# Phase 10 Plan 04: Pre-seeded Per-Desk Template Download Summary

**Pre-seeded per-desk `.xlsx` template download (one sheet per desk, roster identity filled, schedule blank) sharing `EnrichedColumnLayout` with the parser and export, with server-side formula-injection sanitization.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-07-31T21:41:00Z
- **Completed:** 2026-07-31T22:03:21Z
- **Tasks:** 3
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- `DeskAssignmentTemplateService.generateTemplate()` builds one workbook with one worksheet per tenant desk, header row from `EnrichedColumnLayout.identityHeaders()` + `dayHeader()` per `DAY_ORDER` + two blank specialty columns, and pre-seeds each sheet's rows with the current roster's identity columns filled while day/specialty cells stay blank (D-14).
- `GET /api/v1/client-management/desk-assignments/template` returns the generated workbook as a `desk-assignment-template.xlsx` attachment, mirroring the existing `exportEmployees` byte[] response idiom exactly.
- `DeskAgentExportService`'s shared identity columns (BambooHR ID, First/Last Name, Email, Department, Job Title, Active) now source header text from `EnrichedColumnLayout` constants instead of separate hardcoded literals, closing the template/parser/export round-trip (D-13).
- Server-side `sanitize()` on `DeskAssignmentTemplateService` prefixes any cell value beginning with `=`, `+`, `-`, or `@` with a single quote, mitigating formula/CSV injection (T-10-08) for any BambooHR-sourced string written into the template.

## Task Commits

Each task was committed atomically:

1. **Task 1: DeskAssignmentTemplateService — pre-seeded per-desk template + sanitization** - `e37396c` (feat)
2. **Task 2: Template download endpoint + export column alignment** - `5dd1d19` (feat)
3. **Task 3: DeskAssignmentTemplateServiceTest** - `025a9a6` (test)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` - Generates the pre-seeded per-desk `.xlsx` template; owns the `sanitize()` formula-injection guard
- `src/main/java/com/wfm/controller/ClientManagementController.java` - Adds `GET /desk-assignments/template` download endpoint, injects `DeskAssignmentTemplateService`
- `src/main/java/com/wfm/service/DeskAgentExportService.java` - Shared identity column headers now sourced from `EnrichedColumnLayout` constants
- `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java` - Sheet-per-desk naming, header alignment, identity-filled/schedule-blank pre-seeding, and sanitization assertions

## Decisions Made
- Used `org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(desk.getName())` when calling `workbook.createSheet(...)` instead of the raw desk name, to prevent a runtime `IllegalArgumentException` for desk names containing characters POI disallows in sheet names (`\ / ? * [ ] :`) or exceeding Excel's 31-character sheet-name limit. This was not called out explicitly in the plan's action text but is required for `createSheet` (an acceptance-criteria-mandated call) to work correctly for arbitrary tenant desk names.
- Kept the template's specialty seeding at exactly 2 columns (`Specialty 1`/`Specialty 2`) per D-14's literal spec ("leave specialties blank" — two seeded columns), while the parser continues to accept unbounded `Specialty N` columns on re-upload per D-06 (no drift — the template is a starting point, not a schema constraint).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Guarded desk-name-derived sheet names against invalid/oversized Excel sheet names**
- **Found during:** Task 1 (DeskAssignmentTemplateService implementation)
- **Issue:** The plan's action text calls for `workbook.createSheet(desk.getName())` directly. POI throws `IllegalArgumentException` if a sheet name exceeds 31 characters or contains any of `\ / ? * [ ] :`. Desk names are free-text operator input (no length/character constraint enforced on `Desk.name`), so any desk with a long or punctuation-containing name would make the entire template download endpoint fail for the whole tenant, not just that desk.
- **Fix:** Used `WorkbookUtil.createSafeSheetName(desk.getName())`, POI's standard truncate-and-replace-invalid-characters helper, when creating each sheet.
- **Files modified:** src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
- **Verification:** `./gradlew compileJava` succeeds; `DeskAssignmentTemplateServiceTest` exercises normal desk names end-to-end (edge-case names not separately unit-tested, but the fix uses POI's own vetted utility rather than custom logic)
- **Committed in:** e37396c (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Necessary for correctness across all tenant desk names; no scope creep — the sanitization is a one-line substitution around the exact `createSheet` call the plan specifies.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Operators can now download the pre-seeded per-desk template via `GET /api/v1/client-management/desk-assignments/template`; the frontend wiring (button + blob-download handler in `ClientManagement.tsx`, per `10-PATTERNS.md`) is expected in a later plan in this phase.
- Template, parser (`DeskAssignmentUploadService`, plan 03), and export (`DeskAgentExportService`) all resolve identity column headers from the single `EnrichedColumnLayout` source — no drift risk remains across the three surfaces (D-13 fully satisfied).
- No blockers for the remaining phase 10 plans.

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
- FOUND: src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java
- FOUND: `desk-assignments/template` in ClientManagementController.java
- FOUND: `EnrichedColumnLayout` reference in DeskAgentExportService.java
- FOUND: commit e37396c
- FOUND: commit 5dd1d19
- FOUND: commit 025a9a6
- Full suite `./gradlew compileJava test` — BUILD SUCCESSFUL, no failures
