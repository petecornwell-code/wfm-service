---
phase: 10-enriched-upload-parsing
plan: 06
subsystem: ui
tags: [react, typescript, fetch, xlsx-upload, client-management]

# Dependency graph
requires:
  - phase: 10-enriched-upload-parsing (plan 03)
    provides: extended DeskAssignmentUploadResult DTO (sheetSummaries, warnings, skippedSheets) from the rewritten parser
  - phase: 10-enriched-upload-parsing (plan 04)
    provides: GET /client-management/desk-assignments/template pre-seeded per-desk template endpoint
provides:
  - Upload Results modal renders per-sheet rollup, clamp warnings, and unmatched-sheet notices
  - "Download template" button on Client Management page (UPL-09)
  - Updated helper text describing the one-sheet-per-desk shape and retired-shape rejection (D-15)
affects: [10-enriched-upload-parsing (remaining UI-facing plans), future desk-assignment upload UX changes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Blob-download handler pattern (handleDownloadTemplate mirrors handleExportEmployees) for xlsx attachment endpoints"
    - "Non-blocking warnings rendered in a distinct amber block, separate from the blocking skippedDetails table"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/ClientManagement.tsx

key-decisions:
  - "Matched frontend TS interfaces field-for-field to the backend DeskAssignmentUploadResult/SheetSummary/SkippedSheet records (verified against DeskAssignmentUploadService.java and dto/SkippedSheet.java, dto/SkippedRow.java)"
  - "Rendered warnings and skippedSheets in a single combined amber notice block rather than two separate blocks, since both are non-blocking upload-time notices per D-11"

patterns-established:
  - "New xlsx attachment downloads (template, export) always mirror the existing blob-fetch + createObjectURL + anchor-click pattern in ClientManagement.tsx"

requirements-completed: [UPL-06, UPL-09]

# Metrics
duration: 15min
completed: 2026-07-31
---

# Phase 10 Plan 06: Upload Results UI + Template Download Summary

**Extended the Client Management Upload Results modal to render the backend's per-sheet rollup, clamp warnings, and unmatched-sheet notices, and added a pre-seeded per-desk template download button.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 2 automated tasks + 1 checkpoint (auto-approved per AUTO_MODE)
- **Files modified:** 2

## Accomplishments
- `frontend/src/api/client.ts` extended with `SkippedSheet`, `SheetSummary` interfaces and `sheetSummaries`/`warnings`/`skippedSheets` fields on `DeskAssignmentUploadResult`, matching the backend record shape exactly (verified against `DeskAssignmentUploadService.java` lines 452-463 and `dto/SkippedSheet.java`/`dto/SkippedRow.java`).
- Added `clientManagement.downloadDeskAssignmentTemplate()` blob-fetch call mirroring the existing `exportEmployees` pattern, targeting the Wave 2 `GET /client-management/desk-assignments/template` endpoint.
- `ClientManagement.tsx` Upload Results modal now renders a per-desk rollup list (`"Billing: 12 imported, 2 skipped"`), plus a combined amber warnings block for clamp warnings (`Row 8 Tue: 32 -> 24` style strings) and unmatched-sheet notices (`Sheet "X": no matching desk — skipped`), both non-blocking and shown only when non-empty.
- Added a "Download template" button next to the upload control, wired to a new `handleDownloadTemplate` handler that mirrors `handleExportEmployees`'s blob-download pattern, saving `desk-assignment-template.xlsx`.
- Updated the static helper text to describe the new one-sheet-per-desk shape (BambooHR ID + Monday..Sunday day cells holding hours/MANDATORY/PTO, optional Specialty N columns) and note that both retired shapes (6-col legacy, old flat enriched) are rejected server-side.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend client.ts DTO types + template download call** - `bd7948b` (feat)
2. **Task 2: Extend Upload Results modal + template download button + helper text** - `fccf665` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `frontend/src/api/client.ts` - Extended `DeskAssignmentUploadResult`, added `SkippedSheet`/`SheetSummary` interfaces, added `downloadDeskAssignmentTemplate()` call
- `frontend/src/pages/ClientManagement.tsx` - Extended Upload Results modal (rollup + warnings blocks), added `handleDownloadTemplate` + button, updated helper text

## Decisions Made
- Field names in the TS interfaces were taken directly from the already-implemented backend DTOs (`DeskAssignmentUploadService.DeskAssignmentUploadResult`, `dto.SheetSummary`, `dto.SkippedSheet`, `dto.SkippedRow`) rather than from the plan's `<interfaces>` sketch, since the backend (Wave 2) was already merged and its exact shape takes precedence over the plan's approximation. No field-name drift was found — the plan's sketch matched the implementation exactly.
- Combined `warnings` (clamp) and `skippedSheets` (unmatched-sheet) into one amber "Warnings" block in the modal for a simpler, single non-blocking notice area, rather than two visually separate sections — both convey the same "review but not blocking" semantics per D-11.

## Deviations from Plan

None - plan executed exactly as written. The backend DTO shape (`sheetSummaries`, `warnings`, `skippedSheets`, `SkippedSheet`, `SheetSummary`) referenced in the plan's `<interfaces>` section was already present in the codebase (Wave 2 plans 03/04 had already landed), so this plan only needed frontend changes as scoped.

## Issues Encountered
None. `cd frontend && npm run build` (tsc -b && vite build) passed cleanly after both tasks.

## User Setup Required
None - no external service configuration required.

## Human Verification Deferred (HUMAN-UAT)

Task 3 was a `checkpoint:human-verify` gated task. Per AUTO_MODE execution rules, this was auto-approved and the code work completed; the actual visual/functional verification is deferred to the human operator:

1. Run the app (backend + `cd frontend && npm run dev`), open Client Management.
2. Click "Download template" — confirm an `.xlsx` downloads with one worksheet per desk, identity columns filled for the current roster, and the 7 day cells + Specialty columns blank.
3. Fill a template sheet with a mix: a valid row, a blank day cell, a `32` day-cell value (clamp), an unknown BambooHR ID, and optionally an extra sheet named for no configured desk. Upload it.
4. Confirm the Upload Results modal shows: a per-sheet rollup, per-row skip reasons, an amber clamp warning (`... 32 -> 24` style), and an unmatched-sheet notice.

**This is a HUMAN-UAT item** — mark 10-06 fully verified only after a human confirms the above renders correctly in a running instance.

## Next Phase Readiness
- UPL-06/UPL-09 UI surface is complete for the enriched upload parser and template download.
- Frontend build passes cleanly (`tsc -b && vite build`).
- No blockers for downstream Phase 10 plans or Phase 11 (MRG) work.

## Self-Check: PASSED

- FOUND: frontend/src/api/client.ts
- FOUND: frontend/src/pages/ClientManagement.tsx
- FOUND: .planning/phases/10-enriched-upload-parsing/10-06-SUMMARY.md
- FOUND commit: bd7948b
- FOUND commit: fccf665

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*
