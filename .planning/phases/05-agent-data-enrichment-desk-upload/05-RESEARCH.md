# Phase 5: Agent Data Enrichment & Desk Upload - Research

**Researched:** 2026-05-11
**Domain:** Spring Boot 3.4 + JPA + Flyway backend; React 19 + Vite frontend; BambooHR REST integration; Apache POI XLSX parsing
**Confidence:** HIGH

## Summary

This phase is entirely additive within an existing, well-organised codebase. The work splits into five concurrent slices: (1) BambooHR custom-report field expansion + `EmploymentType` enum on `Agent`, (2) a new `JobTitleConfig` table backing a Configuration-page checkbox list and a solver-time eligibility filter, (3) a parser upgrade in `DeskAssignmentUploadService` that detects 6-col vs 16-col layouts by header text and returns structured per-row results, (4) a `BambooSyncEvent` log + sync-status card + structured `BambooHRRateLimitedException` translated from RestClient `HttpServerErrorException.ServiceUnavailable`, and (5) a one-line PTO filter fix in `SolverService` so only `APPROVED` day-offs contribute to `agentDaysOffMap`.

All four key "discretion" items have unambiguous answers in the codebase: **Flyway** (V1–V24 in `src/main/resources/db/migration/`), **inline-rendered modal pattern** at `DeskAgents.tsx:382-413` (`position:fixed; inset:0; background:rgba(0,0,0,0.5); zIndex:1000`), **Configuration card layout** is currently a single `<div style={{maxWidth:'500px'}}>` of form rows — we extend with sibling cards in the same page, and **sync-status persistence** needs a new lightweight `bamboo_sync_event` table (no existing fit; `app_configuration` is key/value and wrong for time-series).

**Primary recommendation:** Treat this as 5 independent task groups. Wave-0 the schema (V25 `employment_type`, V26 `job_title_config`, V27 `bamboo_sync_event`) and the `EmploymentType` enum so downstream backend and frontend slices can proceed in parallel. The PTO filter fix is the smallest, highest-confidence change — ship it first with a dedicated unit test so downstream phases (Phase 6 fairness) inherit correct behaviour.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pull `employmentHistoryStatus` from BambooHR | API/Backend | — | Custom-report fields array lives in `HttpBambooHRClient`; mapping rule applied at the boundary in `BambooEmployee` record + `BambooRefreshService` |
| Map BambooHR status string → `EmploymentType` enum | API/Backend | — | Schema decoupling decision (D-04) — must happen at write time, not at every read |
| Persist `Agent.employmentType` | Database | API/Backend | New column via Flyway migration; JPA enum-as-string column |
| Employment type column + filter on agent table | Frontend (Browser) | API/Backend | Filter is client-side over already-loaded page (D-07, matches existing `agentSearch`/`empSearch` patterns); column data flows through `DeskAgentResponse` DTO |
| `JobTitleConfig` storage | Database | — | New table keyed on `(tenant_id, job_title)` |
| Auto-populate JobTitleConfig on refresh | API/Backend | — | Hooks into the per-agent upsert loop in `BambooRefreshService.persistRefreshData` step 3 |
| Non-Schedulable Job Titles UI | Frontend | API/Backend | New card on Configuration page; PATCH endpoint to toggle flag |
| Solver agent eligibility filter | API/Backend | — | Filter happens in `SolverService.startSolve` between line 124 (existing `eligibleAgents` filter) and line 130 — single additional `.filter()` step |
| Desk-assignment rejection for non-schedulable agents | API/Backend | — | Reject in `DeskAssignmentUploadService` per-row loop AND in `ClientManagementService.assignEmployeesToDesk` (manual assign endpoint) |
| 6-col vs 16-col spreadsheet detection | API/Backend | — | Header row read from `sheet.getRow(0)` before per-row parse; in `DeskAssignmentUploadService.uploadDeskAssignments` |
| Structured per-row upload result | API/Backend | Frontend | Replace `List<String> skippedDetails` with `List<SkippedRow>` record; frontend modal consumes it |
| Upload result modal | Frontend | — | Replace `showToast` call at `ClientManagement.tsx:162` with inline-rendered modal using the `DeskAgents.tsx:382` pattern |
| 503 → `BambooHRRateLimitedException` | API/Backend | — | `RestClient.onStatus()` interceptor in `HttpBambooHRClient` |
| Persist last-sync result | Database | API/Backend | New `bamboo_sync_event` table; written from `BambooRefreshService` finally-block |
| BambooHR sync status card | Frontend | API/Backend | New GET `/api/v1/configuration/bamboohr/sync-status` endpoint reads latest event |
| Requested-PTO badge on agent row | Frontend | API/Backend | `DeskAgentResponse` extended with `pendingPtoCount` and `pendingPtoDates: List<LocalDate>` |
| PTO filter (approved-only) in solver | API/Backend | — | One-line change at `SolverService.java:158-161` |

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Spreadsheet bulk-upload of desk assignments; UI shows per-row success/failure | Existing `DeskAssignmentUploadService` covers 80% — adds: header-based shape detection (Step "Spreadsheet Upload"), `SkippedRow` DTO (Step "Backend Patterns"), modal UI (Step "Frontend Modal Pattern") |
| DATA-02 | Sync full-time/part-time; filterable column | Add `employmentHistoryStatus` to BambooHR custom-report fields list; new `EmploymentType` enum + column; mapping rule in `BambooRefreshService` |
| DATA-03 | Sync job title; mark titles non-schedulable; exclude from solver and desk allocation | Job title already synced (line 168). New: `JobTitleConfig` table, auto-populate in refresh, Configuration UI, solver pre-filter |

## Standard Stack

### Core (already in build.gradle / package.json — VERIFIED)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.2 [VERIFIED: build.gradle:3] | Web/JPA/Validation starters | Already the framework — extend, don't introduce |
| Spring Framework RestClient | 6.x (bundled) [CITED: docs.spring.io] | Synchronous HTTP client used by `HttpBambooHRClient` | Already used — extend with `.onStatus()` |
| Flyway | bundled by Spring Boot [VERIFIED: build.gradle:31] | DB migrations under `classpath:db/migration` | 24 existing migrations follow `V{N}__{snake_case}.sql` convention |
| Hibernate / JPA | bundled by Spring Boot | ORM for `Agent`, `AgentDayOff`, etc. | Existing entities use `@Entity`, `@Column`, `@Enumerated(EnumType.STRING)` pattern |
| Apache POI | 5.3.0 [VERIFIED: build.gradle:40] | XLSX read/write for upload service | Already in use in `DeskAssignmentUploadService` |
| Timefold Solver | 1.16.0 BOM [VERIFIED: build.gradle:35] | Constraint solver | Pre-existing — this phase only touches problem-fact assembly, not solver internals |
| React | 19.0 [VERIFIED: frontend/package.json:12] | UI | Existing |
| React Router | 7.1 [VERIFIED: frontend/package.json:14] | Routing | Existing |
| Vite | 6.1 [VERIFIED: frontend/package.json:21] | Build | Existing |

### Supporting — none required
No new dependencies needed. The modal is hand-rolled inline (matching existing `DeskAgents.tsx:382` pattern). The CSV download for upload results uses a `Blob` + `URL.createObjectURL` pattern matching `ClientManagement.tsx:188-194` `handleExportEmployees`.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Inline modal `<div>` rendering | Headless UI / Radix Dialog | Adds dependency; project chose to stay zero-dep on the frontend (only React + Router) |
| Spring `@Retryable` on BambooHR calls | Custom 503 → exception translation | Out of scope — phase requires *surfacing* the error to the operator, not auto-retrying |
| Verbatim string storage of `employmentHistoryStatus` | Enum mapping | D-04 locks the enum path; verbatim deferred to backlog |

**Installation:** No new packages.

**Version verification:**
- Spring Boot 3.4.2 is current and supports `RestClient.onStatus(Predicate, ErrorHandler)` natively. `HttpServerErrorException.ServiceUnavailable` is the default mapped exception for 503 from `retrieve()`. [CITED: docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/HttpServerErrorException.ServiceUnavailable.html]
- Apache POI 5.3.0 supports the `XSSFWorkbook` constructor from `MultipartFile.getInputStream()` already in use.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FRONTEND (React 19 / Vite)                       │
│                                                                          │
│  DeskAgents.tsx          Configuration.tsx        ClientManagement.tsx   │
│  ┌──────────────┐       ┌──────────────────┐    ┌─────────────────────┐ │
│  │ Emp.Type col │       │ Non-Sched Titles │    │ Upload button       │ │
│  │ Filter dropd │       │ (checkbox list)  │    │   ↓                 │ │
│  │ Pending PTO  │       │ Sync Status Card │    │ Result Modal        │ │
│  │   badge      │       │                  │    │   ↓                 │ │
│  └──────────────┘       └──────────────────┘    │ CSV download blob   │ │
│         ↓                       ↓                └─────────────────────┘ │
│         │                       │                          ↓             │
└─────────┼───────────────────────┼──────────────────────────┼─────────────┘
          │                       │                          │
          │ GET /desks/{id}/agents│ GET /configuration/      │ POST /client-
          │                       │  bamboohr/sync-status    │  management/
          │                       │ GET /job-titles          │  upload-desk-
          │                       │ PATCH /job-titles/{id}   │  assignments
          ↓                       ↓                          ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                        API (Spring Boot 3.4 controllers)                 │
│                                                                          │
│  DeskAgent       AppConfiguration       NEW JobTitleConfig  ClientMgmt  │
│  Controller      Controller             Controller          Controller  │
│       ↓                ↓                       ↓                ↓        │
└───────┼────────────────┼───────────────────────┼────────────────┼────────┘
        ↓                ↓                       ↓                ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                            SERVICE LAYER                                 │
│                                                                          │
│  DeskAgentService    BambooRefreshService    NEW JobTitleConfigSvc      │
│         ↓                  ↓                      ↓                      │
│  + employmentType    + auto-populate         JobTitleConfigRepository   │
│  + pendingPto info     JobTitleConfig                                   │
│                      + map empHistStatus     SolverService              │
│                      + persist SyncEvent     (filters non-schedulable   │
│                          ↓                    agents at line ~127)      │
│                      HttpBambooHRClient                                 │
│                      ┌──────────────────┐    DeskAssignmentUploadSvc    │
│                      │ +employmentHistory│   + header-based shape       │
│                      │  field            │   + structured SkippedRow    │
│                      │ +.onStatus(503 → │   + reject non-schedulable    │
│                      │  RateLimited)     │                              │
│                      └─────────┬────────┘                              │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                       EXTERNAL: BambooHR REST API v1                     │
│   POST /reports/custom (now with employmentHistoryStatus in fields)     │
│   GET  /time_off/requests (status=approved|requested)                   │
│                503 Service Unavailable → Retry-After: N                 │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       DATABASE (PostgreSQL via Flyway)                   │
│                                                                          │
│  Existing: agent, agent_day_off (status APPROVED/REQUESTED),            │
│            app_configuration, desk, specialization, etc.                │
│                                                                          │
│  V25: agent.employment_type VARCHAR(20) DEFAULT 'FULL_TIME'             │
│  V26: job_title_config(id, tenant_id, job_title, non_schedulable,       │
│                         created_at, updated_at)                          │
│       UNIQUE (tenant_id, job_title)                                      │
│  V27: bamboo_sync_event(id, tenant_id, desk_id NULL, started_at,        │
│                          finished_at, success, error_message,           │
│                          agents_synced, time_off_pulled,                │
│                          retry_after_seconds)                            │
└─────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure (additions only)

```
src/main/java/com/wfm/
├── model/
│   ├── EmploymentType.java               # NEW enum FULL_TIME, PART_TIME
│   ├── JobTitleConfig.java               # NEW @Entity
│   └── BambooSyncEvent.java              # NEW @Entity
├── repository/
│   ├── JobTitleConfigRepository.java     # NEW
│   └── BambooSyncEventRepository.java    # NEW
├── service/
│   ├── JobTitleConfigService.java        # NEW
│   ├── AgentEligibilityService.java      # NEW — shared by SolverService & upload service
│   ├── BambooSyncEventService.java       # NEW — single write surface for sync outcomes
│   └── DeskAssignmentUploadService.java  # MODIFY — header-based shape + SkippedRow record
├── integration/
│   ├── HttpBambooHRClient.java           # MODIFY — add field + onStatus(503)
│   ├── BambooEmployee.java               # MODIFY — add employmentHistoryStatus
│   ├── BambooRefreshService.java         # MODIFY — map + JobTitleConfig + SyncEvent writes
│   └── MockBambooHRClient.java           # MODIFY — emit varied employmentHistoryStatus
├── exception/
│   └── BambooHRRateLimitedException.java # NEW (with retryAfterSeconds field)
├── controller/
│   ├── JobTitleConfigController.java     # NEW
│   ├── AppConfigurationController.java   # MODIFY — add sync-status GET endpoint OR new controller
│   └── GlobalExceptionHandler.java       # MODIFY — handle BambooHRRateLimitedException → 503
├── dto/
│   ├── SkippedRow.java                   # NEW record (rowNumber, bamboohrId, name, reason)
│   ├── DeskAgentResponse.java            # MODIFY — add employmentType + pendingPtoCount
│   ├── JobTitleConfigResponse.java       # NEW
│   └── BambooSyncEventResponse.java      # NEW

src/main/resources/db/migration/
├── V25__add_agent_employment_type.sql    # NEW
├── V26__add_job_title_config.sql         # NEW
└── V27__add_bamboo_sync_event.sql        # NEW

frontend/src/
├── pages/
│   ├── DeskAgents.tsx                    # MODIFY — column, filter, pending PTO badge
│   ├── Configuration.tsx                 # MODIFY — add 2 new cards
│   └── ClientManagement.tsx              # MODIFY — modal replaces toast on upload
└── api/client.ts                         # MODIFY — new endpoints + types
```

### Pattern 1: Flyway Migration Naming
**What:** Sequential `V{N}__{snake_case}.sql` under `src/main/resources/db/migration/`. Latest is `V24__enable_pgvector_extension.sql`.
**When to use:** Every schema change.
**Example:**
```sql
-- V25__add_agent_employment_type.sql
ALTER TABLE agent ADD COLUMN employment_type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME';
-- Per V22 convention: drop the default after backfill so new rows must set explicitly
ALTER TABLE agent ALTER COLUMN employment_type DROP DEFAULT;
```
Convention verified against `V22__add_day_off_status.sql` which uses the same default-then-drop pattern.

### Pattern 2: JPA Enum Mapping
**What:** Existing pattern in `AgentDayOff.java:33` — `private DayOffStatus status = DayOffStatus.APPROVED;` with `@Enumerated(EnumType.STRING)` and a matching `VARCHAR(20)` column.
**When to use:** `EmploymentType` follows identical pattern.
**Example:**
```java
@Enumerated(EnumType.STRING)
@Column(name = "employment_type", nullable = false, length = 20)
private EmploymentType employmentType = EmploymentType.FULL_TIME;
```

### Pattern 3: BambooHR Mapping at the Boundary (D-05)
**What:** `BambooEmployee` record extracts the BambooHR field as a `String`; `BambooRefreshService` applies the mapping rule once.
**Example:**
```java
// BambooEmployee.java
public record BambooEmployee(
    String id, String displayName, String workEmail, String department,
    String jobTitle, String status,
    String employmentHistoryStatus,   // NEW — raw BambooHR value
    String wfmTenantId, String project
) {}

// BambooRefreshService.java (constant + helper)
private static final String BAMBOO_PART_TIME = "Part-Time";
private static EmploymentType mapEmploymentType(String status) {
    return BAMBOO_PART_TIME.equals(status) ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME;
}

// Inside the per-agent update loop at line ~160:
agent.setEmploymentType(mapEmploymentType(emp.employmentHistoryStatus()));
```

### Pattern 4: Header-Based Spreadsheet Shape Detection (D-14)
**What:** Read row 0, build a lowercase Set<String> of header strings, pick the layout.
**Example:**
```java
Sheet sheet = workbook.getSheetAt(0);
Row headerRow = sheet.getRow(0);
Set<String> headers = new HashSet<>();
for (Cell c : headerRow) {
    String h = getCellString(c);
    if (h != null) headers.add(h.trim().toLowerCase());
}

UploadShape shape;
boolean hasEnrichedMarkers = headers.contains("desk")
        && headers.contains("monday") && headers.contains("sunday");
boolean hasLegacyMarkers = headers.contains("desk assignment");

if (hasEnrichedMarkers) shape = UploadShape.ENRICHED_16COL;        // prefer enriched if both match
else if (hasLegacyMarkers) shape = UploadShape.LEGACY_6COL;
else throw new IllegalArgumentException(
        "Unrecognised spreadsheet shape. Expected either 'Desk Assignment' (legacy) "
      + "or 'Desk' + day columns (enriched). Got headers: " + headers);

// Build a column index by header name so we don't depend on column ORDER:
Map<String, Integer> col = new HashMap<>();
for (int i = 0; i < headerRow.getLastCellNum(); i++) {
    String h = getCellString(headerRow.getCell(i));
    if (h != null) col.put(h.trim().toLowerCase(), i);
}
// Per-row reads then become:
String bamboohrId = getCellString(row.getCell(col.getOrDefault(
        shape == UploadShape.LEGACY_6COL ? "bamboohr id" : "employee id", -1)));
String deskName = getCellString(row.getCell(col.getOrDefault(
        shape == UploadShape.LEGACY_6COL ? "desk assignment" : "desk", -1)));
```

### Pattern 5: RestClient 503 → Custom Exception (D-20)
**What:** `RestClient.onStatus(predicate, errorHandler)` translates HTTP errors before `body()`.
**Example:**
```java
// In HttpBambooHRClient — wrap each .retrieve()
String json = restClient.post()
    .uri(baseUrl() + "/reports/custom?format=JSON")
    .header(HttpHeaders.AUTHORIZATION, basicAuth())
    .header(HttpHeaders.ACCEPT, "application/json")
    .contentType(MediaType.APPLICATION_JSON)
    .body(requestBody)
    .retrieve()
    .onStatus(
        status -> status.value() == 503 || status.value() == 429,
        (req, resp) -> {
            String retryAfter = resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            int seconds = parseRetryAfterSeconds(retryAfter, 60);
            throw new BambooHRRateLimitedException(
                "BambooHR is rate-limiting requests. Retry in " + seconds + " seconds.",
                seconds);
        })
    .body(String.class);
```
[CITED: docs.spring.io — `RestClient.ResponseSpec.onStatus`]

The error handler in `onStatus` runs *before* RestClient would otherwise throw `HttpServerErrorException.ServiceUnavailable`, so our exception wins.

### Pattern 6: Inline Modal (matches existing DeskAgents.tsx:382-413)
**What:** No modal component exists — render a `<div>` with `position:fixed; inset:0` directly in the page component, gated by a boolean state.
**Example:**
```tsx
{uploadResult && (
  <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
                display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
    <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem',
                  width: '600px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
      <h3>Upload Result</h3>
      <p><strong>{uploadResult.assignedCount}</strong> assigned, <strong>{uploadResult.skippedCount}</strong> skipped</p>
      {/* Expandable skipped list, CSV download button, Close button */}
    </div>
  </div>
)}
```
Verified against `DeskAgents.tsx:382-413` (Assign Modal) and `DeskAgents.tsx:415-436` (Days Off modal) — both use identical inline-render approach.

### Pattern 7: Configuration Page Cards
**What:** Currently the page has one `<div style={{maxWidth:'500px'}}>` block with form rows. There's no "card" component — sibling `<section>`s under the `<h1>` are the established pattern. Each section gets its own `<h2>`/`<h3>` heading.
**Recommendation:** Add two new sibling sections after the BambooHR config block:
- `<section>` "BambooHR Sync Status" — single horizontal layout block with the last-sync data
- `<section>` "Non-Schedulable Job Titles" — checkbox list

### Anti-Patterns to Avoid
- **Don't reorder columns in `BambooEmployee`'s constructor:** mock client + tests pass arguments positionally. Add new fields at the END or use a named copy-with helper.
- **Don't filter non-schedulable agents AFTER solver invocation:** the solver builds `AgentAssignment` planning entities from the input list. Filter at `SolverService.startSolve` line ~127 before line 130's `eligibleAgentIds.stream()` materialisation. (Same place the existing `Active::isActive` + primary-spec filter lives.)
- **Don't drop the soft-delete logic when adding employment-type sync:** `BambooRefreshService` step 4 (lines 187-197) handles agents removed from BambooHR. The new employment-type write happens INSIDE the step 3 loop (lines 160-184), unaffected by step 4.
- **Don't try to compute `JobTitleConfig.distinct titles` from `Agent.jobTitle`:** auto-populate must run inside the same transaction as the agent upsert. Iterate the freshly-synced `employees` list, not the DB.
- **Don't replace `agentDaysOffMap`'s population loop with a stream that filters PTO status:** `MANDATORY` day-off type must remain unconditional (always blocks). Only `PTO` type filters by `APPROVED`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Modal/dialog component | Custom Portal + focus-trap library | The inline `position:fixed` pattern at `DeskAgents.tsx:382` | Zero existing dependencies on the frontend; consistent with codebase; sufficient for this UX |
| HTTP retry logic for BambooHR 503 | Spring Retry / Resilience4j | Just throw the exception and surface it to the operator | Phase requires *surfacing* not retrying (success criterion 4 is "human-readable retry message") |
| CSV generation for skipped rows | Apache Commons CSV | `Blob` + manual `\n`/`,` join in the frontend | Trivial 4-column CSV; frontend already does `Blob`-based download for employee export at `ClientManagement.tsx:188` |
| Spreadsheet header parsing | New parser framework | Build a `Map<String,Integer> col` from `sheet.getRow(0)` once | Same approach already used elsewhere in `DeskAssignmentUploadService` indirectly; one helper function |
| Per-tenant config storage for non-schedulable titles | Reusing `app_configuration` (key/value) | Dedicated `job_title_config` table | Auditability, queryability, and a row-per-title model is needed for the toggle UI; `app_configuration` is a flat string-to-string map |
| Sync event time series | Logging only | Dedicated `bamboo_sync_event` table | The sync-status card needs structured fields (last success time, last error, retry-after seconds, agents synced count); log scraping is wrong |

**Key insight:** Every "new" thing in this phase has a sibling that already exists in the codebase. The work is *extending* established patterns, not *introducing* new technology. Resist the urge to introduce dependencies.

## Runtime State Inventory

Not applicable for category-by-category audit — this is a feature-add phase, NOT a rename/refactor/migration. However, a partial inventory of touched runtime state:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `agent_day_off.status` column (APPROVED/REQUESTED) populated since V22 — `requested` rows already exist and were being treated as blocking by the solver (this is the bug being fixed) | Code-only fix in `SolverService.java:158-161`; no data migration |
| Stored data | `agent` table — new column `employment_type` defaults to `FULL_TIME` for all existing rows | V25 migration with default backfill; first refresh updates from BambooHR |
| Stored data | `job_title_config` table is new — empty after migration; populated lazily on first refresh | Auto-populate on next BambooHR refresh; no manual seed needed |
| Live service config | None — BambooHR config (server, API key) lives in `app_configuration` table and is unaffected | None |
| OS-registered state | None | None |
| Secrets/env vars | None changed; existing `bamboohr.apiKey` config key stays as-is | None |
| Build artifacts | None | None |

## Common Pitfalls

### Pitfall 1: BambooHR `status` field vs `employmentHistoryStatus` field confusion
**What goes wrong:** Two different fields with similar names. `status` is `Active|Inactive` (already synced into `Agent.active`); `employmentHistoryStatus` is `Full-time|Part-Time|Probation Period|PIP|Notice of Resignation`.
**Why it happens:** Both contain "status"; the existing custom-report fields list at `HttpBambooHRClient.java:79` only fetches `status`.
**How to avoid:** Verify the BambooHR JSON response includes a NEW key `employmentHistoryStatus` (camelCase) — `BambooEmployee` record needs a separate field. Do not overload `status`. The discuss-phase confirmed the literal field name against the live tenant.
**Warning signs:** Tests pass but `Agent.employmentType` is always `FULL_TIME` — likely you forgot to add the field to the request body's `fields` array.

### Pitfall 2: MockBambooHRClient drift
**What goes wrong:** Real client returns new field; mock doesn't. Tests pass against mock; live integration fails.
**Why it happens:** Two `BambooEmployee` constructor sites in `MockBambooHRClient` (lines 62 and 91 — two different methods).
**How to avoid:** When extending the `BambooEmployee` record, search for ALL constructor sites: `grep -rn "new BambooEmployee" src/`. Today there are 6: lines 62, 91, 107 in `MockBambooHRClient`; lines 113, 140 in `HttpBambooHRClient`; and any test data builders.
**Warning signs:** Compiler errors in mock client after adding the new field — good signal that this safety net is working.

### Pitfall 3: `agentDaysOffMap` filter doesn't apply to mandatory holidays
**What goes wrong:** A naive `.filter(d -> d.getStatus() == APPROVED)` would drop mandatory holidays whose status is not set, breaking holiday handling.
**Why it happens:** `BambooRefreshService.java:219-220` sets PTO records to APPROVED or REQUESTED, but `AgentDayOff.status` defaults to APPROVED for any path that doesn't set it explicitly.
**How to avoid:** Filter must include BOTH `MANDATORY` type OR `APPROVED` status:
```java
for (AgentDayOff d : allDaysOff) {
    boolean blocks = d.getType() == DayOffType.MANDATORY
                  || d.getStatus() == DayOffStatus.APPROVED;
    if (blocks) {
        agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
    }
}
```
**Warning signs:** Existing test `ResolvePreferencesPtoFilterTest` still passes (it doesn't test mandatory) — write a NEW test that mixes `MANDATORY` + `REQUESTED` PTO and verifies mandatory still blocks.

### Pitfall 4: `JobTitleConfig` auto-populate happens INSIDE the existing transaction
**What goes wrong:** Auto-populating in a separate transaction/method risks losing rows if the main refresh transaction rolls back, or leaves the table in an inconsistent state.
**Why it happens:** `BambooRefreshService.refreshDeskAgents` uses `TransactionTemplate.executeWithoutResult` to wrap the persistence step (line 93). The current pattern keeps all writes in that single TX.
**How to avoid:** Add the JobTitleConfig upsert call INSIDE `persistRefreshData` (line 100+), iterating the synced `employees` list to derive distinct job titles. Use `findByTenantIdAndJobTitle` + create-if-absent. Never delete rows here (D-09).
**Warning signs:** Two transactions in logs around refresh time → wrong path.

### Pitfall 5: Spreadsheet shape detection breaks on uppercase-only header rows
**What goes wrong:** Real-world spreadsheets may use `DESK ASSIGNMENT` or `desk assignment`; comparing literally to `"Desk Assignment"` misses these.
**Why it happens:** Operators paste data from various sources.
**How to avoid:** Lowercase + trim before comparison (the example in Pattern 4 does this). Also store the column-name → column-index map at parse-start time, not per-row.
**Warning signs:** Upload silently treats all rows as "missing Desk Assignment".

### Pitfall 6: Soft-delete interaction with non-schedulable filter
**What goes wrong:** A non-schedulable Team Lead is correctly excluded from solver runs, but they're also `Active=true` in BambooHR. The existing soft-delete logic in `BambooRefreshService` lines 187-197 shouldn't deactivate them.
**Why it happens:** "Non-schedulable" and "active" are orthogonal — non-schedulable means "don't put them on a shift", not "they don't exist".
**How to avoid:** Keep `Agent.active` purely BambooHR-status-driven. The non-schedulable filter is a SOLVER-INPUT filter only (`SolverService.startSolve` line 127 area). Same for desk allocation rejection — reject the assignment, do not deactivate the agent.
**Warning signs:** Non-schedulable agents disappear from the Configuration agents table.

### Pitfall 7: RestClient `onStatus` does NOT fire on connection errors
**What goes wrong:** If BambooHR is fully down (DNS failure, connection refused), `onStatus` is never called — those throw `ResourceAccessException` directly.
**Why it happens:** `onStatus` only handles HTTP responses, not transport errors.
**How to avoid:** The sync-event write should be in a `try/catch (Exception e)` in `BambooRefreshService.refreshDeskAgents`, so ANY exception (rate-limit, connection error, parse failure) results in a `BambooSyncEvent` row with the error message. Then the rate-limit *specifically* has a typed exception that surfaces "retry in N seconds"; everything else gets a generic "sync failed: {message}".
**Warning signs:** Sync card shows "Last sync: success" right after a network outage.

## Code Examples

### Adding `employmentHistoryStatus` to BambooHR custom report fields (D-01)
```java
// HttpBambooHRClient.java — line 76, replace requestBody
String requestBody = """
        {
          "title": "WFM Employee Report",
          "fields": ["id", "displayName", "workEmail", "department", "jobTitle", "status", "employmentHistoryStatus"]
        }
        """;
// And in the row-parsing loop (line 103-117), add:
String employmentHistoryStatus = emp.path("employmentHistoryStatus").asText("");
// Then update the BambooEmployee constructor call.
```

### `JobTitleConfig` entity
```java
@Entity
@Table(name = "job_title_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "job_title"})
})
public class JobTitleConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "non_schedulable", nullable = false)
    private boolean nonSchedulable = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // getters / setters
}
```

### Solver eligibility filter (D-11)
```java
// SolverService.java around line 124-127, AFTER the existing filter
List<Agent> eligibleAgents = allAgents.stream()
        .filter(Agent::isActive)
        .filter(a -> a.getPrimarySpecialization() != null)
        .filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))
        .toList();
```
Or inject `AgentEligibilityService` with a method `boolean isNonSchedulable(long tenantId, String jobTitle)` backed by a tenant-scoped Set<String> cache rebuilt per request (or @Cacheable if hot-path).

### Structured `SkippedRow` DTO (D-18)
```java
public record SkippedRow(
    int rowNumber,
    String bamboohrId,    // nullable
    String name,           // nullable
    String reason          // e.g. "BambooHR ID 12345 not found in cache"
) {}

public record DeskAssignmentUploadResult(
    int assignedCount,
    int skippedCount,
    List<String> assignedDetails,
    List<SkippedRow> skippedDetails   // CHANGED from List<String>
) {}
```
Note: this is a breaking JSON shape change. The frontend type `DeskAssignmentUploadResult` at `frontend/src/api/client.ts:430` must be updated in lockstep.

### Sync event persistence (D-19)
```java
// BambooRefreshService — wrap refreshDeskAgents body in try/catch
BambooSyncEvent event = new BambooSyncEvent();
event.setTenantId(tenantId);
event.setDeskId(deskId);
event.setStartedAt(OffsetDateTime.now());
try {
    // ... existing refresh logic ...
    event.setSuccess(true);
    event.setAgentsSynced(employees.size());
    event.setTimeOffPulled(timeOffs.size());
} catch (BambooHRRateLimitedException e) {
    event.setSuccess(false);
    event.setErrorMessage(e.getMessage());
    event.setRetryAfterSeconds(e.getRetryAfterSeconds());
    throw e;   // still surface to caller
} catch (Exception e) {
    event.setSuccess(false);
    event.setErrorMessage(e.getMessage());
    throw e;
} finally {
    event.setFinishedAt(OffsetDateTime.now());
    bambooSyncEventRepository.save(event);  // separate TX preferred
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Toast-only upload result | Modal with structured per-row data + CSV download | This phase | UX improvement; requires backend DTO shape change |
| All BambooHR PTO blocks scheduling | Only `APPROVED` PTO blocks; `REQUESTED` is visible-only | This phase (bug fix) | DATA-03 prerequisite; affects solver output |
| `employmentHistoryStatus` not pulled | Pulled and mapped to enum at boundary | This phase | New BambooHR API field consumed; backwards-compatible if BambooHR omits it (`asText("")` defaults to empty string → FULL_TIME) |
| Free-text skipped-row strings | Structured `SkippedRow` records | This phase | Enables CSV download and filtering in UI |

**Deprecated/outdated:** Nothing being removed in this phase. Existing toast helper (`showToast`) stays for non-upload flows; only the upload result path moves to a modal.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The BambooHR custom-report endpoint returns `employmentHistoryStatus` as a top-level field in each row when included in the `fields` array | "Adding employmentHistoryStatus" pattern | [VERIFIED via discuss-phase against live `helpware` tenant per CONTEXT.md `<specifics>`] — Low risk |
| A2 | `RestClient.onStatus()` is sufficient to intercept 503 before `HttpServerErrorException.ServiceUnavailable` is thrown | Pitfall 7 + Pattern 5 | [CITED: docs.spring.io] — well-documented; low risk |
| A3 | A separate transaction for `bamboo_sync_event` writes (to capture failures even when the main TX rolls back) is acceptable | Code example "Sync event persistence" | Low risk — pattern is standard; planner may choose `REQUIRES_NEW` propagation or just write before the main TX commits |
| A4 | Adding `employmentHistoryStatus` to BambooHR's `fields` array does not increase the response size enough to break the existing 5000-row cache | "Common Pitfalls" | Low risk — one short string field per row; cache size is row-count-based not byte-based |
| A5 | The `desk-agents.xlsx` 13-col shape (current refresh export) does NOT need to be accepted by the upload parser | Architecture map | [VERIFIED: CONTEXT.md D-13 lists only 6-col legacy and 16-col enriched as accepted; `desk-agents.xlsx` is the *output* of the existing employee export, not an input] |
| A6 | The `position:fixed; inset:0` modal pattern in `DeskAgents.tsx:382` is the intended convention going forward | Pattern 6 | [VERIFIED: identical pattern used in 2 places in `DeskAgents.tsx` already; no other modal library in `package.json`] — Low risk |
| A7 | Auto-populating `JobTitleConfig` inside the same transaction as agent upsert does not create lock-contention with concurrent refreshes | Pitfall 4 | Low risk — existing `refreshInProgress` ConcurrentHashMap in `BambooRefreshService:35` prevents concurrent refresh per desk |

**If this table is empty:** N/A — 7 assumptions documented. None are high-risk; all are either verified upstream (discuss-phase) or are standard Spring/JPA patterns. Planner should not need to surface any to the user for re-confirmation.

## Open Questions

1. **Where does the "current desk" come from when the sync-status card is on the Configuration page?**
   - What we know: `BambooSyncEvent` has a `desk_id` column because the existing refresh is desk-scoped (`refreshDeskAgents(UUID deskId)`).
   - What's unclear: The Configuration page is tenant-level, not desk-level — should the card show the latest event across all desks for the tenant, or per-desk breakdown?
   - Recommendation: Show the latest event regardless of desk in the main card, with a small "by desk" expand toggle if desk-level data is useful. Planner can decide.

2. **Does the operator need to manually trigger a sync to test the 503 error path, or does that happen on the scheduled refresh?**
   - What we know: Refresh today is triggered manually via the "Refresh" button on `DeskAgents.tsx:72-84`. There is no `@Scheduled` job in `BambooRefreshService` (verified — `grep` returned no results).
   - What's unclear: Is the manual button still the only refresh trigger after this phase?
   - Recommendation: Stay manual for this phase; "scheduled BambooHR sync" is its own feature.

3. **Should the requested-PTO badge link to a detail view?**
   - What we know: D-21 says "small badge ... with a hover tooltip listing the requested dates".
   - What's unclear: Whether the existing "Days Off" modal (`DeskAgents.tsx:415`) should also surface requested PTO, or whether the badge click does something distinct.
   - Recommendation: Reuse the existing Days Off modal — extend `agent_day_off` API response to include `status`, and render REQUESTED rows with a visual differentiator (italics / amber tag) inside the same modal. Plan-time decision.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java JDK | All backend code | Need to verify in CI | 21 [VERIFIED: build.gradle:12] | — |
| PostgreSQL | Flyway, JPA | Live in prod (RDS) [VERIFIED: STATE.md] | 16.6 | — |
| Node.js / npm | Vite build | Local dev — assume available | — | — |
| BambooHR API access (live tenant) | Integration test | Yes [VERIFIED: discuss-phase] | API v1 | `MockBambooHRClient` for unit + dev tests |
| Apache POI | XLSX read/write | Yes [VERIFIED: build.gradle:40] | 5.3.0 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 via `spring-boot-starter-test` + Timefold solver test helpers [VERIFIED: build.gradle:43-44] |
| Backend test runner | `./gradlew test` (`tasks.named('test') { useJUnitPlatform() }`) |
| Backend test DB | H2 in-memory [VERIFIED: build.gradle:45 `testRuntimeOnly 'com.h2database:h2'`] |
| Frontend framework | NONE — no test framework currently installed [VERIFIED: `frontend/package.json` has no test deps; no `*.test.*` files exist] |
| Quick run command | `./gradlew test --tests "*ResolvePreferencesPtoFilterTest"` |
| Full suite command | `./gradlew test` |
| Manual smoke | `./gradlew bootRun` then drive UI in `frontend/` via `npm run dev` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-02 | `Part-Time` BambooHR value maps to `EmploymentType.PART_TIME`; everything else (Full-time, Probation Period, PIP, Notice of Resignation, null, blank) maps to `FULL_TIME` | unit | `./gradlew test --tests "*BambooRefreshServiceEmploymentTypeMappingTest*"` | Wave 0 |
| DATA-02 | `Agent.employmentType` column persists round-trip through `AgentRepository` | unit (H2) | `./gradlew test --tests "*AgentEmploymentTypePersistenceTest*"` | Wave 0 |
| DATA-02 | `DeskAgentResponse` includes `employmentType` field | unit | `./gradlew test --tests "*DeskAgentServiceMappingTest*"` | Wave 0 |
| DATA-02 | Frontend filter dropdown narrows table rows | manual-only | UI test in `npm run dev` — no frontend test framework exists | — |
| DATA-03 | `JobTitleConfig` rows auto-populate from synced job titles on refresh, with `nonSchedulable=false` default; existing rows are NOT deleted | unit | `./gradlew test --tests "*JobTitleConfigAutoPopulateTest*"` | Wave 0 |
| DATA-03 | `SolverService.startSolve` excludes agents whose `jobTitle` matches a `JobTitleConfig` with `nonSchedulable=true` | unit | `./gradlew test --tests "*SolverServiceEligibilityFilterTest*"` | Wave 0 |
| DATA-03 | `DeskAssignmentUploadService` rejects rows for non-schedulable agents with a clear reason | unit | `./gradlew test --tests "*DeskAssignmentUploadNonSchedulableRejectTest*"` | Wave 0 |
| DATA-03 | Manual `assignEmployeesToDesk` endpoint rejects non-schedulable agents | unit | `./gradlew test --tests "*ClientManagementServiceNonSchedulableTest*"` | Wave 0 |
| DATA-01 | Header `Desk Assignment` → parsed as legacy 6-col shape; uses `BambooHR ID, Name, Email, Desk Assignment, Specialty 1, Specialty 2` semantics | unit | `./gradlew test --tests "*DeskAssignmentUploadLegacyShapeTest*"` | Wave 0 |
| DATA-01 | Header `Desk` + `Monday`…`Sunday` → parsed as enriched 16-col shape; reads `Employee ID, Desk` (Mon-Sun ignored for upload action) | unit | `./gradlew test --tests "*DeskAssignmentUploadEnrichedShapeTest*"` | Wave 0 |
| DATA-01 | Spreadsheet with both `Desk` + day cols AND `Desk Assignment` → prefers enriched shape | unit | (same test class) | Wave 0 |
| DATA-01 | Unknown header set → throws `IllegalArgumentException` with a message naming the headers seen | unit | (same test class) | Wave 0 |
| DATA-01 | `DeskAssignmentUploadResult.skippedDetails` is `List<SkippedRow>` with `rowNumber, bamboohrId, name, reason` | unit | (covered by shape tests above) | Wave 0 |
| DATA-01 | Modal renders after upload; CSV download produces N+1-line CSV (header + skipped row count) | manual-only | UI walk-through | — |
| Success Criterion 4 | BambooHR 503 → `BambooHRRateLimitedException(retryAfterSeconds=N)` where N is from `Retry-After` header, default 60 | unit | `./gradlew test --tests "*HttpBambooHRClient503Test*"` (uses Spring's MockRestServiceServer or a stub RestClient) | Wave 0 |
| Success Criterion 4 | GlobalExceptionHandler maps `BambooHRRateLimitedException` to a 503 response with human-readable body | unit | `./gradlew test --tests "*GlobalExceptionHandler503Test*"` | Wave 0 |
| Success Criterion 4 | Sync-status card surfaces "Rate-limited — retry in N seconds" | manual-only | UI walk-through | — |
| Success Criterion 5 | `agentDaysOffMap` excludes `PTO` rows with status `REQUESTED` | unit | `./gradlew test --tests "*SolverServicePtoFilterTest*"` | Wave 0 |
| Success Criterion 5 | `agentDaysOffMap` includes `MANDATORY` rows regardless of status | unit | (same test class — mandatory-still-blocks case) | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*<relevant-class>*"` — runs in < 10 seconds for individual unit tests against H2.
- **Per wave merge:** `./gradlew test` — runs the full backend suite including existing solver-suite tests; budget < 5 minutes (existing `FullScale150AgentTest` is the longest).
- **Phase gate:** `./gradlew test` green; plus manual smoke against `MockBambooHRClient` covering the 5 success criteria.

### Wave 0 Gaps
- [ ] `BambooRefreshServiceEmploymentTypeMappingTest.java` — covers DATA-02 mapping rule (Part-Time → PART_TIME; everything else → FULL_TIME)
- [ ] `AgentEmploymentTypePersistenceTest.java` — covers DATA-02 JPA round-trip on H2
- [ ] `DeskAgentServiceMappingTest.java` — covers DTO mapping
- [ ] `JobTitleConfigAutoPopulateTest.java` — covers DATA-03 refresh-time auto-populate
- [ ] `SolverServiceEligibilityFilterTest.java` — covers DATA-03 solver filter
- [ ] `DeskAssignmentUploadNonSchedulableRejectTest.java` — covers DATA-03 upload-time rejection
- [ ] `ClientManagementServiceNonSchedulableTest.java` — covers DATA-03 manual-assign rejection
- [ ] `DeskAssignmentUploadLegacyShapeTest.java` — covers DATA-01 legacy parsing
- [ ] `DeskAssignmentUploadEnrichedShapeTest.java` — covers DATA-01 enriched parsing + tie-breaker + unknown rejection
- [ ] `HttpBambooHRClient503Test.java` — covers SC-4 (use Spring's `MockRestServiceServer` or a fake `RestClient.Builder`)
- [ ] `GlobalExceptionHandler503Test.java` — covers SC-4 (standalone @WebMvcTest is NOT currently used in this project; alternative: instantiate handler directly and assert response entity — matches the existing test style)
- [ ] `SolverServicePtoFilterTest.java` — covers SC-5 PTO filter + mandatory-still-blocks
- [ ] **No frontend test framework setup** — explicitly out of scope for this phase per project precedent (zero frontend tests in repo today). Frontend behavior is verified via manual smoke. Planner should NOT introduce Vitest/Jest in this phase.

## Project Constraints (from CLAUDE.md)

No `CLAUDE.md` file exists in the project root. The constraints below are sourced from `.planning/STATE.md` and project-history decisions visible in the repository.

- **Timefold solver version:** Currently 1.16.0 [VERIFIED: build.gradle:35]. `STATE.md` references a pin at 1.33.0 (Score Analysis paid-tier concern at 2.0) — DO NOT recommend version changes in this phase. The discrepancy between the recorded pin and the actual `build.gradle` value should be flagged to the user separately; for Phase 5 it's irrelevant (no solver dependency changes needed).
- **PDF export library:** OpenPDF 3.0.4 chosen (LGPL/MPL). Phase 5 does not touch PDF export.
- **Java version:** 21 [VERIFIED: build.gradle:12].
- **Spring Boot version:** 3.4.2 [VERIFIED: build.gradle:3].
- **Migration tool:** Flyway, never edit historical migrations, always add new sequential `V{N}__*.sql`.
- **DDL auto:** `validate` only [VERIFIED: application.yml:11]. Schema changes MUST go through Flyway.

## Sources

### Primary (HIGH confidence)
- Repository inspection — all `.java`, `.tsx`, `.sql`, `.gradle`, `.yml`, and `.xlsx` files cited above were read directly during research.
- [Spring Framework `HttpServerErrorException.ServiceUnavailable` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/HttpServerErrorException.ServiceUnavailable.html) — confirms 503 default exception mapping.
- [Spring Framework `RestClient.ResponseSpec` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestClient.ResponseSpec.html) — confirms `onStatus(Predicate, ErrorHandler)` API.

### Secondary (MEDIUM confidence)
- [Spring REST template error handling — Baeldung](https://www.baeldung.com/spring-rest-template-error-handling) — corroborates onStatus pattern (RestTemplate predecessor with same API surface).

### Tertiary (LOW confidence)
- None used for load-bearing claims.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every version verified directly from `build.gradle` / `package.json`.
- Architecture: HIGH — every "MODIFY/NEW" location verified by direct file inspection with line numbers.
- Pitfalls: HIGH — derived from reading the actual code that will be modified, not from generic best practices.
- Test strategy: MEDIUM — backend test style verified (one existing test, JUnit 5 + AssertJ + reflection on private methods); frontend test strategy is "none" by precedent.
- Sync-status table design: MEDIUM — the planner has latitude to choose REQUIRES_NEW transaction propagation vs separate write, and to choose whether to record desk-level or tenant-level events.

**Research date:** 2026-05-11
**Valid until:** 2026-06-11 (30 days — stable Spring Boot 3.4 ecosystem; BambooHR API v1 stable for years)

Sources:
- [Spring Framework HttpServerErrorException.ServiceUnavailable](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/HttpServerErrorException.ServiceUnavailable.html)
- [Spring Framework RestClient.ResponseSpec](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestClient.ResponseSpec.html)
- [Spring REST template error handling — Baeldung](https://www.baeldung.com/spring-rest-template-error-handling)
