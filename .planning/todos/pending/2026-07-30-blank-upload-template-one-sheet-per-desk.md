---
created: 2026-07-30T00:00:00Z
title: Provide a blank upload template spreadsheet, one sheet per desk
area: upload
resolves_phase: 10
files:

  - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
  - src/main/java/com/wfm/service/DeskAgentExportService.java

audit_acknowledged:
  milestone: v1.2
  at: 2026-08-25
---

## Request (Pete, 2026-07-30, during Phase 9 execution)

Two related asks for the enriched-upload flow (Phase 10 scope, "Enriched Upload Parsing"):

1. **Blank spreadsheet template** — operators need a downloadable *empty* template
   (correct headers, no data rows) they can fill in and upload, not just an export of
   existing data. Today there is no template-generation path; export services
   (`DeskAgentExportService`, etc.) only emit populated workbooks.

2. **One sheet per desk** — the template should contain a separate worksheet per desk
   rather than a single flat sheet of all agents.

## Design tension to resolve before Phase 10 planning

The current Phase 10 goal says operators upload **"one extended spreadsheet"** with a
single per-row shape, and `DeskAssignmentUploadService` detects one enriched-16-col
sheet. "One sheet per desk" changes the workbook structure the parser must accept:

- Does the parser iterate every sheet and use the **sheet name as the desk**, dropping
  the per-row `Desk` column? Or keep the Desk column and treat sheets as cosmetic?

- Blank-template generation and the upload parser must agree on the exact header row
  and sheet layout (single source of truth for column order).

- Excel export (`DeskAgentExportService`, touched in Phase 9 plan 09-05) may want the
  same per-desk-sheet layout for round-trip symmetry — decide whether export and
  template share a workbook builder.

## Suggested handling

Fold into **Phase 10 discuss/plan** (`/gsd-discuss-phase 10`) as explicit context, or
add as UPL requirements if it expands scope. Not implemented in Phase 9 (data-model
foundation only).
