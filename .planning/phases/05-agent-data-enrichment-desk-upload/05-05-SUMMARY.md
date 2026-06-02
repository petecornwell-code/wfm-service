---
phase: 05-agent-data-enrichment-desk-upload
plan: "05"
subsystem: frontend-operator-ui
tags: [frontend, react, employment-type, non-schedulable, sync-status, upload-modal, pto-badge, uat]
dependency_graph:
  requires:
    - "GET /api/v1/job-titles + PATCH /api/v1/job-titles/{id} (Plan 02)"
    - "GET /api/v1/configuration/bamboohr/sync-status (Plan 02)"
    - "DeskAgent response: employmentType + pendingPtoCount + pendingPtoDates (Plan 04)"
    - "DeskAssignmentUploadResult.skippedDetails: SkippedRow[] (Plan 04)"
  provides:
    - "api/client.ts: DeskAgent type extension + SkippedRow + JobTitleConfigResponse + BambooSyncEventResponse"
    - "api/client.ts: jobTitleConfig + bambooSyncStatus API surfaces"
    - "DeskAgents.tsx: Emp Type column + employment-type filter + pending-PTO badge column"
    - "Configuration.tsx: Non-Schedulable Job Titles section + BambooHR Sync Status card"
    - "ClientManagement.tsx: Upload Results modal (replaces toast) + CSV download"
  affects:
    - "frontend/src/api/client.ts"
    - "frontend/src/pages/DeskAgents.tsx"
    - "frontend/src/pages/Configuration.tsx"
    - "frontend/src/pages/ClientManagement.tsx"
tech_stack:
  added: []
  patterns:
    - "Inline-styled UI extending existing page patterns (no new component files, no new deps)"
    - "Optimistic PATCH with revert-on-error (job title toggle)"
    - "Blob + URL.createObjectURL CSV download (mirrors handleExportEmployees)"
    - "CSV-injection mitigation: prefix =,+,-,@ cells with single quote (T-05-05-02)"
    - "Modal: Close-button-only dismissal, no backdrop onClick"
key_files:
  created:
    - .planning/phases/05-agent-data-enrichment-desk-upload/05-05-SUMMARY.md
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskAgents.tsx
    - frontend/src/pages/Configuration.tsx
    - frontend/src/pages/ClientManagement.tsx
decisions:
  - "UAT (Task 5) performed against the LIVE deployed site (https://d2bbtcc80peap7.cloudfront.net/), not local mock servers — operator chose deploy-then-verify (session 2026-06-02)"
  - "Case 5 (503 sync-status failure path) approved on deployed-code + local-mock evidence: the live ECS task cannot be easily forced to return 503 from the real BambooHR integration; the success-state card was verified live, the failure-state rendering was verified via the deployed code path / prior local testing"
  - "autonomous: false honoured — Tasks 1-4 auto-committed, Task 5 held as a blocking human-verify gate until operator approval"
metrics:
  completed_date: "2026-06-02"
  tasks_completed: 5
  files_changed: 4
---

# Phase 5 Plan 5: Operator UI for Agent Data Enrichment & Desk Upload Summary

**One-liner:** Wired the Plan 01-04 backend surfaces into the operator UI — employment-type column + filter and pending-PTO badge on DeskAgents, Non-Schedulable Job Titles toggle + BambooHR Sync Status card on Configuration, and an Upload Results modal (with skipped-row CSV download) on ClientManagement — verified via operator UAT against the live deployment.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | api/client.ts types + jobTitleConfig + bambooSyncStatus surfaces | 3d801cd | frontend/src/api/client.ts |
| 2 | DeskAgents Emp Type column + filter + Pending PTO badge | 0762a08 | frontend/src/pages/DeskAgents.tsx |
| 3 | Configuration Non-Schedulable Job Titles + Sync Status card | 3478db5 | frontend/src/pages/Configuration.tsx |
| 4 | ClientManagement Upload Results modal | c18059a | frontend/src/pages/ClientManagement.tsx |
| 5 | Human-verify UAT (6 cases) | — (operator-approved) | live site walk-through |

## Verification Results

**Automated (Tasks 1-4, in-agent before checkpoint):**
- `cd frontend && npx tsc --noEmit` — exits 0
- `cd frontend && npm run build` — production bundle compiles
- All Task 1-4 grep acceptance criteria passed (verified at commit time; on-disk markers present: `jobTitleConfig`, `empTypeFilter`, `Non-Schedulable Job Titles`, `uploadResult`)

**Deployment:** Shipped to production via GitHub Actions run 26841279945 (2026-06-02). Frontend → S3/CloudFront (invalidated), backend → ECS stable, Flyway V25/V26/V27 applied to prod RDS cleanly. Live frontend returns HTTP 200; new API routes (`/api/v1/job-titles`, `/api/v1/configuration/bamboohr/sync-status`) routed and reachable.

**Operator UAT (Task 5, against live site):**

| Case | Behaviour | Result |
|------|-----------|--------|
| 1 | Emp Type column + Full/Part-time filter | PASS |
| 2 | Non-Schedulable toggle persists + solver exclusion + 409 manual-assign rejection | PASS |
| 3 | Upload Results modal (counts, skipped table, CSV, Close-only, no backdrop dismiss, bad-shape toast) | PASS |
| 4 | Manual per-agent assign unchanged | PASS |
| 5 | BambooHR Sync Status card (success state live; 503 failure path on deployed-code/local-mock evidence) | PASS (with caveat) |
| 6 | Pending PTO amber badge + tooltip + em-dash empty state | PASS |

Operator typed "approved" 2026-06-02.

## Deviations from Plan

**UAT venue:** Plan Task 5 specified booting local servers (`gradlew bootRun --args='--spring.profiles.active=mock'` + `npm run dev`). Operator instead chose to deploy Phase 5 to production first and run the walk-through against the live site — same 6-case checklist, different environment.

**Case 5 (503 path) caveat:** The live ECS task runs the real (non-mock) BambooHR integration, so the 503 rate-limit failure state could not be force-triggered the way the local MockBambooHRClient allows. The Sync Status card's success state was verified live; the failure-state rendering (`Status: Failed`, `Retry in N seconds`) rests on the deployed code path plus prior local-mock testing. Accepted by operator at approval.

## Known Stubs

None. All four surfaces consume live backend endpoints deployed in Plans 02 and 04.

## Threat Flags

Threat model T-05-05-01 through T-05-05-06 mitigations in place:
- T-05-05-02 (CSV injection): `handleDownloadSkippedCsv` prefixes any cell starting with `=`,`+`,`-`,`@` with a single quote
- T-05-05-06 (XSS via SkippedRow.reason): plain `{row.reason}` JSX interpolation (React auto-escapes); no `dangerouslySetInnerHTML`
- T-05-05-04 (PTO date disclosure): UI renders only what the tenant+desk-scoped API returns

## Self-Check: PASSED

Files exist:
- frontend/src/api/client.ts ✓ (`jobTitleConfig`, `bambooSyncStatus`)
- frontend/src/pages/DeskAgents.tsx ✓ (`empTypeFilter`, `#fef9c3`)
- frontend/src/pages/Configuration.tsx ✓ (`Non-Schedulable Job Titles`)
- frontend/src/pages/ClientManagement.tsx ✓ (`uploadResult`, `Upload Results`)

Commits exist: 3d801cd, 0762a08, 3478db5, c18059a ✓
Deployed: GitHub Actions run 26841279945 (success) ✓
UAT: operator-approved 2026-06-02 ✓
