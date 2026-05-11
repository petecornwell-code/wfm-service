# Phase 5: Agent Data Enrichment & Desk Upload - Context

**Gathered:** 2026-05-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Operators get richer agent data from BambooHR and can bulk-assign agents to desks via spreadsheet. Specifically:

1. Pull `employmentHistoryStatus` from BambooHR and store full-time/part-time on Agent; surface as filterable column.
2. Allow operators to mark specific job titles as non-schedulable; the solver and desk allocation exclude agents holding those titles.
3. Accept a spreadsheet upload that bulk-assigns agents to desks; show per-row success/failure with reasons.
4. Surface BambooHR 503 rate-limit errors as human-readable messages, not generic server errors.
5. Fix the PTO bug: only `approved` PTO blocks scheduling; `requested` PTO is visible-only diagnostic.

</domain>

<decisions>
## Implementation Decisions

### BambooHR Refresh (DATA-02)
- **D-01:** Add `employmentHistoryStatus` to the BambooHR custom-report `fields` array in `HttpBambooHRClient.listEmployees()`. Final field set pulled per refresh: `id, displayName, workEmail, department, jobTitle, status, employmentHistoryStatus`.
- **D-02:** New enum `EmploymentType { FULL_TIME, PART_TIME }`. New column `Agent.employmentType: EmploymentType` (default `FULL_TIME`, nullable for legacy rows pre-migration).
- **D-03:** Mapping rule (applied in `BambooRefreshService`): `employmentHistoryStatus == "Part-Time"` → `PART_TIME`; any other value (Full-time, Probation Period, PIP, Notice of Resignation, null, blank) → `FULL_TIME`. Record this rule once in code with a constant; don't scatter the string compare.
- **D-04:** Do NOT store `employmentHistoryStatus` verbatim. Map at sync time. (Rationale: schema decouples from BambooHR vocabulary; if a new BambooHR status appears, mapping rule is the only place to update.)
- **D-05:** Extend `BambooEmployee` record with `employmentHistoryStatus` field so the mapping happens at the boundary, not deep in the service.

### Employment Type UI (DATA-02)
- **D-06:** Add Employment Type column to `frontend/src/pages/DeskAgents.tsx` table.
- **D-07:** Filter control: dropdown above the table with options `All / Full-time / Part-time`. Default `All`. Filter applied client-side over already-loaded page (consistent with existing table filters).

### Non-Schedulable Job Titles (DATA-03)
- **D-08:** New table `JobTitleConfig` with columns `(id, tenantId, jobTitle, nonSchedulable bool, createdAt, updatedAt)`. Unique constraint on `(tenantId, jobTitle)`.
- **D-09:** Auto-populate `JobTitleConfig` rows on BambooHR refresh: for each distinct `jobTitle` seen in the synced roster, ensure a row exists (default `nonSchedulable=false`). Never delete rows here — only operators delete via UI.
- **D-10:** New section on `frontend/src/pages/Configuration.tsx` titled "Non-Schedulable Job Titles". Lists every `JobTitleConfig` row for the tenant with a checkbox. Operator toggles → PATCH to backend.
- **D-11:** Solver filtering happens at agent-eligibility time in `SolverService` — before the solver builds its `AgentAssignment` candidates, filter out any agent whose `jobTitle` matches a `JobTitleConfig` row with `nonSchedulable=true`. Do NOT add a column on Agent.
- **D-12:** Desk allocation (`DeskAssignmentUploadService` & manual assignment endpoint) must reject attempts to assign a non-schedulable agent to a desk with a clear error reason.

### Spreadsheet Upload (DATA-01)
- **D-13:** Upload parser accepts BOTH spreadsheet shapes:
  - **6-col legacy:** `BambooHR ID, Name, Email, Desk Assignment, Specialty 1, Specialty 2` (existing `desk_assignments.xlsx`)
  - **16-col enriched:** the layout in `src/main/resources/sample-data/production_agents.xlsx` (BambooHR fields + Part-Time Employee + Project + Desk + Mon–Sun)
- **D-14:** Shape detection is header-based — match the actual column names, not column count. If sheet has `Desk Assignment` → treat as legacy. If sheet has `Desk` + day columns → treat as enriched. If both could match, prefer enriched. Unknown shape → reject with clear error.
- **D-15:** For both shapes, the writable fields are: **desk assignment** (the per-row desk) and **specialty assignments** (if present). All other columns are informational / ignored for the upload action. Mon–Sun schedule columns are NOT used to overwrite PTO data (PTO is BambooHR-sourced only).
- **D-16:** Existing manual per-agent desk assignment endpoint must keep working unchanged.

### Upload Failure Display (DATA-01)
- **D-17:** Replace the existing toast-only flow in `frontend/src/pages/ClientManagement.tsx` with a modal that opens immediately after the server returns the upload result. Modal shows:
  - Summary: "N assigned, M skipped" prominently
  - Expandable list of skipped rows with their row number + reason (BambooHR ID not found, agent already on another desk, non-schedulable job title, etc.)
  - "Download results as CSV" button
  - Close action; modal dismissal does not undo successful assignments.
- **D-18:** Backend `UploadResult` payload must already carry per-row reasons. Verify `DeskAssignmentUploadService.skippedDetails` includes structured `{rowNumber, bamboohrId, name, reason}` and not just a free-text string.

### BambooHR Diagnostics & PTO Fix
- **D-19:** New card on `frontend/src/pages/Configuration.tsx` titled "BambooHR Sync Status". Shows: last sync timestamp (per refresh job), last sync result (success/error), last error message (e.g., "Rate-limited — retry in 60 seconds"), counts (agents synced, PTO records pulled).
- **D-20:** `HttpBambooHRClient` must catch HTTP 503 (and 429 if BambooHR uses it) and translate to a structured error: `BambooHRRateLimitedException` with a `retryAfterSeconds` field (read from `Retry-After` header if present, else default 60). Surface this error via the sync-status card.
- **D-21:** Requested PTO surfacing: add a small badge on each Agent row in `DeskAgents.tsx` — e.g., "1 pending PTO request" — with a hover tooltip listing the requested dates. Only show when count > 0.
- **D-22:** Solver PTO filter fix: `SolverService.java:158-161` currently builds `agentDaysOffMap` from ALL day-off records. Change to filter to `pto.status == 'approved'` only. Mandatory holidays remain unfiltered (they're hard blocks regardless). `requested` PTO is read but does not contribute to `agentDaysOffMap`.

### Claude's Discretion
- DB migration tool/style — follow existing project conventions (Liquibase/Flyway as already configured).
- Modal component — reuse whatever modal pattern exists in the frontend rather than introducing a new dependency.
- Sync-status card layout — single horizontal card matches existing Configuration page card style.
- Exact wording of the 503 retry message — match what's already in the success-criterion ("retry in 60 seconds") but Claude can pluralize ("Retry in N seconds") based on header value.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend — BambooHR integration
- `src/main/java/com/wfm/integration/BambooEmployee.java` — record to extend with `employmentHistoryStatus`
- `src/main/java/com/wfm/integration/HttpBambooHRClient.java` — custom-report fields list + 503 handling lives here
- `src/main/java/com/wfm/integration/BambooRefreshService.java` — sync flow; mapping rule + JobTitleConfig auto-populate happens here
- `src/main/java/com/wfm/integration/MockBambooHRClient.java` — keep parity for mock

### Backend — Agent model + solver
- `src/main/java/com/wfm/model/Agent.java` — add `employmentType` column
- `src/main/java/com/wfm/service/SolverService.java` §158-161 — the PTO filter bug fix lives here; also where non-schedulable agent filtering happens before solver invocation

### Backend — Desk upload
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` — parser must accept both shapes; `skippedDetails` must be structured
- `src/main/java/com/wfm/dto/` — likely home for `UploadResult` / `SkippedRow` DTOs

### Frontend — Pages
- `frontend/src/pages/DeskAgents.tsx` — employment-type column + filter + pending-PTO badge
- `frontend/src/pages/Configuration.tsx` — non-schedulable job titles section + BambooHR sync status card
- `frontend/src/pages/ClientManagement.tsx` — modal replaces toast-only upload result flow

### Sample data
- `src/main/resources/sample-data/production_agents.xlsx` — 16-col enriched spreadsheet shape (BambooHR + PTO + desk)
- `src/main/resources/sample-data/desk_assignments.xlsx` — existing 6-col legacy shape
- `src/main/resources/sample-data/desk-agents.xlsx` — existing 13-col format produced by current refresh

### Planning
- `.planning/REQUIREMENTS.md` — DATA-01, DATA-02, DATA-03
- `.planning/ROADMAP.md` — Phase 5 entry with success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DeskAssignmentUploadService` already returns `assignedCount/skippedCount/skippedDetails` — extend its shape rather than rebuild.
- `BambooRefreshService` already syncs name/email/department/jobTitle/active; the new `employmentType` field plugs into the existing per-agent update path.
- `MockBambooHRClient` is referenced by tests and dev mode; it must learn the new field too.

### Established Patterns
- `Agent.jobTitle` is already a string column synced from BambooHR — `employmentType` should follow the same per-refresh upsert pattern.
- Configuration page already has card-style sections for tenant-level settings; both the BambooHR sync card and the non-schedulable titles section reuse that pattern.
- DeskAgents.tsx table already supports sort/paginate; the employment-type column joins the existing table model.

### Integration Points
- Solver pre-filter: a single helper in `SolverService` (or a new `AgentEligibilityService`) decides "is this agent schedulable?" — used by both the solver run and the desk-assignment write paths.
- PTO write path: `BambooRefreshService` already stores PTO with a status; only the solver READ path needs to filter to `approved`. No data migration needed.
- 503 translation lives in `HttpBambooHRClient`'s `RestClient` exchange; the new exception type plus a structured sync-status record persisted somewhere (new `BambooSyncEvent` table? or in-memory cache?) — leave concrete storage to research/planning.

</code_context>

<specifics>
## Specific Ideas

- Validated against the live BambooHR tenant (`helpware`) during discuss-phase:
  - `employmentHistoryStatus` has 5 observed values on the StubHub - GE active roster: `Full-time` (16), `Probation Period` (9), `Part-Time` (1), `PIP` (1), `Notice of Resignation` (1). Mapping rule is unambiguous: only literal `"Part-Time"` maps to `PART_TIME`.
  - `payType` and `standardHoursPerWeek` are blank for every employee. Do not consume those fields.
  - `/time_off/requests` `dates` object has shape `{"YYYY-MM-DD": "amount"}` where `"0"` marks dates inside the request span that don't count (weekends/holidays). PTO-block logic must skip `amount == "0"` entries even within approved spans.
- Upload shape examples:
  - `production_agents.xlsx`: `Employee ID, Name, Email, Department, Job Title, Status, Part-Time Employee, Project, Desk, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday`
  - `desk_assignments.xlsx`: `BambooHR ID, Name, Email, Desk Assignment, Specialty 1, Specialty 2`

</specifics>

<deferred>
## Deferred Ideas

- Persistent imports history at `/imports` (option C in the upload-UX gray area) — deferred. If audit trail becomes a need later, this is its own phase.
- Per-agent `nonSchedulable` override — deferred. If a future phase needs to mark a single Team-Lead-by-name as still schedulable, revisit.
- Persistent app-level banner for BambooHR errors — deferred in favor of the dedicated sync-status card.
- Dedicated `/diagnostics` page — deferred; the Configuration sync-status card covers Phase 5's diagnostic needs.
- Storing `employmentHistoryStatus` verbatim alongside the mapped enum — deferred. Add only if a future phase needs the unmapped value.

</deferred>

---

*Phase: 05-agent-data-enrichment-desk-upload*
*Context gathered: 2026-05-11*
