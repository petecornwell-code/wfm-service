# Phase 11: BambooHR Merge Engine & Report - Research

**Researched:** 2026-08-18
**Domain:** Backend data-merge logic (Spring Boot service layer) + BambooHR integration + React modal report UI
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Sync scope & failure behaviour (MRG-01, MRG-07)**
- **D-01:** One sync up front for the whole workbook. Pre-scan sheet names → desks, then issue one `listEmployees` + `listTimeOff` fetch before the transaction opens, and merge every sheet against that single in-memory snapshot.
- **D-02:** The whole upload is the atomic unit. The sync happens before any write; a failure (503/429) aborts with a clear operator message and zero DB changes. Per-row/per-sheet parse failures keep Phase 10's skip-and-continue behaviour unchanged — only sync failure aborts everything.
- **D-03:** The sync is a read-only snapshot; the merge engine is the sole writer. Do NOT run `persistRefreshData` as part of upload.
- **D-04:** Synchronous upload with a longer timeout. No async job, no job-id polling. Explicitly rejected: reusing a recent successful sync within N minutes.

**Per-field precedence (MRG-02)**
- **D-05:** The spreadsheet's Mon–Sun day group fully REPLACES the BambooHR field-4517-derived pattern where the sheet supplies one — not unioned, supersedes it. This reverses Phase 10 D-16.
- **D-06:** "BambooHR has data" = not null, not empty, not whitespace-only. One uniform rule across all string fields. Rejected for now: field-specific sentinel handling (`Variable`, `Unknown`, `TBD`).
- **D-07:** Contested fields where BambooHR wins: the spreadsheet value is discarded, but recorded in the merge report. Only the winning value is persisted — no shadow columns.
- **D-08:** Fields carried by only one source are uncontested. BambooHR does not carry per-day contracted hours or specializations — those are spreadsheet-only. BambooHR carries `displayName`, `workEmail`, `department`, `jobTitle`, `status`, `employmentHistoryStatus`, `customWorkingdays`, plus dated time-off.

**PTO precedence (MRG-03)**
- **D-09:** BambooHR is authoritative for PTO within its synced window (`bamboohr.time-off.lookback-weeks`/`lookahead-weeks`). A date inside the window with no PTO request means the agent works, overriding a spreadsheet `PTO` day cell. Outside the window the spreadsheet's recurring weekly pattern fills.
- **D-10:** No new storage or materialisation window for recurring PTO. `SolverService.buildRecurringDaysOff` already resolves the sheet's recurring MANDATORY/PTO labels at solve time. Phase 11 arbitrates against these facts; it does not introduce a persisted PTO horizon.

**Merge report (MRG-04, MRG-05)**
- **D-11:** Report scope = disagreements and gap-fills only. Silent agreement is not shown.
- **D-12:** The report is a new section in the existing Upload Results modal, reusing `DeskAssignmentUploadResult`.
- **D-13:** The report is ephemeral — built during the merge, returned in `DeskAssignmentUploadResult`, gone when the modal closes. No schema change. Rejected: a persisted provenance table.
- **D-14:** Agents who became solver-eligible via the spreadsheet get a distinct callout in the report (MRG-06's headline win).

**Solver eligibility (MRG-06)**
- **D-15:** A later BambooHR refresh must not downgrade a spreadsheet-supplied pattern. Once the sheet has supplied a full Mon–Sun pattern, `workingDaysKnown` cannot be flipped back to `false` by a subsequent refresh. Implies tracking that the pattern is sheet-sourced (persisted marker needed).
- **D-16:** `workingDaysKnown` resolves true when all 7 day cells parsed — preserve current behaviour.

**Requirement revisions (⚠ this phase revises Phase 10 D-16 and D-05):**
1. Phase 10 D-16 (union/coexist) is REPLACED by true precedence — the sheet CAN un-block a BambooHR day off.
2. Phase 10 D-05 is REVISED: a `0` day cell does NOT hard-block scheduling. `0` means no contracted hours but the agent remains available — a softer signal than MANDATORY/PTO, which continue to hard-block. The code already behaves this way (`buildRecurringDaysOff` skips null `dayOffType` rows); this is a documentation/intent correction, not an implementation gap.

### Claude's Discretion
- Exact merge-engine structure (a dedicated `AgentMergeService` vs extending `DeskAssignmentUploadService`) — planner decides.
- Where the batched snapshot is fetched and how it is threaded to the per-sheet loop.
- Whether `BambooSyncEvent` records upload-triggered syncs (D-03 makes the upload sync read-only, so the existing sync-event recording in `refreshDeskAgents` does not apply automatically).
- The report DTO shape and the per-field predicate table implementing D-06.

### Deferred Ideas (OUT OF SCOPE)
- Persisted per-field provenance (queryable merge history) — rejected for Phase 11 by D-13.
- Field-specific sentinel handling for D-06 (`Variable`, `Unknown`, `TBD` treated as absent) — deferred unless research finds real BambooHR data demands it.
- Async/job-based upload with progress polling — deferred by D-04.
- Reconciling upload-time and solve-time hours validation (fractional-hours gap) — belongs with solver/validation work, not the merge engine.
- Constraint weighting for surplus hours earned via `0` days — a solver-tuning concern.
- Out of phase scope entirely: changing the day-cell contract or workbook shape (Phase 10, settled); the solver's consumption of days off beyond precedence changes; async/job-based uploads; persisting per-field provenance to the database.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MRG-01 | Uploading triggers a fresh BambooHR sync before merging, so the merge always runs against current BambooHR data | Pattern 1 (HTTP-before-transaction restructuring); Pitfall — `ensureCachePopulatedForUpload` no-op-if-warm finding; Open Question 3 (timeout) |
| MRG-02 | For every field carried by both sources, BambooHR's value is used where BambooHR has data; the spreadsheet value is used only where BambooHR's is absent | Pattern 2 (field-level precedence merge); Code Example (precedence inversion target at `DeskAssignmentUploadService.java:365-386`) |
| MRG-03 | BambooHR's dated PTO takes precedence for the dates it covers; the spreadsheet's recurring weekly PTO pattern applies only to dates with no BambooHR PTO record | Pattern 4 (dated-vs-recurring PTO arbitration, window bounds already in `BambooRefreshService`) |
| MRG-04 | Operator can see a merge report after upload showing, per field, which values came from BambooHR and which the spreadsheet supplied | Code Example (`DeskAssignmentUploadResult` extension); Modal insertion point example |
| MRG-05 | The merge report shows which spreadsheet values were overridden by BambooHR | Same as MRG-04 — one report shape covers both (D-11) |
| MRG-06 | An agent whose working pattern is unknown to BambooHR but supplied by the spreadsheet becomes eligible for solving — `workingDaysKnown` resolves true and the agent is no longer filtered out | Migration precedent (D-15 provenance column); `SolverService.filterEligible` read (`SolverService.java:1074-1082`) |
| MRG-07 | If the BambooHR sync fails during upload (e.g. 503 rate limit), the operator gets a clear message and no partial merge is written | Don't Hand-Roll (existing `BambooHRRateLimitedException` → 503 wiring); Code Example (`GlobalExceptionHandler.java:67-70`); Pitfall 1 (atomicity scope) |
</phase_requirements>

## Summary

Phase 11 is a pure in-repo refactor/extension — no new external libraries, no new API surface beyond what `BambooHRClient` already exposes. The work is concentrated in three places: (1) restructuring `DeskAssignmentUploadService.uploadDeskAssignments` so a single fresh BambooHR fetch happens **before** any transaction opens (mirroring the existing `BambooRefreshService.refreshDeskAgents` pattern exactly), (2) replacing the current per-field "spreadsheet wins when present" backfill logic with true BambooHR-first precedence, and (3) extending `DeskAssignmentUploadResult` with a merge-report section rendered as a new block in the existing Upload Results modal.

The single highest-risk finding from reading the actual source (not just the CONTEXT's line-number citations, which are close but not exact): **`uploadDeskAssignments` is currently annotated `@Transactional` directly on the public method** (`DeskAssignmentUploadService.java:77`). Spring's proxy opens the transaction *before* the method body runs, so simply adding a `bambooHRClient.listEmployees(...)` call at the top of the current method does **not** satisfy D-01/D-02's "sync happens before the transaction opens" requirement — it happens after. The method must be restructured the way `refreshDeskAgents` already is: drop `@Transactional` from the public entry point, do the two blocking HTTP calls unguarded, then wrap only the sheet-loop/persistence in a `transactionTemplate.executeWithoutResult(...)` block. This is a structural rewrite of the method's shape, not an insertion.

The second major finding: `ClientManagementService.ensureCachePopulatedForUpload` — the method the current upload path already calls — **skips the fetch entirely if the cache key already exists** (`if (cache.containsKey(cacheKey)) return;`, `ClientManagementService.java:162`). This directly contradicts MRG-01/D-04 ("always fresh," explicitly rejecting reuse of a recent sync). The merge engine cannot reuse this method as-is; it needs either a forced-refresh variant or to bypass `ClientManagementService`'s cache entirely and work off the raw `List<BambooEmployee>` returned directly by `bambooHRClient.listEmployees(...)`.

The third finding: the cached DTO `BambooEmployeeResponse` (`dto/BambooEmployeeResponse.java:3-10`) that `findCachedEmployee` returns does **not** carry `customWorkingdays` or `employmentHistoryStatus` — both required for D-05 (day-pattern precedence) and D-03/D-04 employment-type mapping. The merge engine must work against the raw `BambooEmployee` record (`integration/BambooEmployee.java:3-14`), not the cache-layer DTO.

**Primary recommendation:** Build a new `AgentMergeService` (or `BambooHRMergeService`) in `com.wfm.integration` (not `com.wfm.service`) so it can call the package-private `WorkingDaysParser.parseWorkingDays`/`offDaysFrom` directly, following the exact HTTP-before-transaction / `TransactionTemplate` shape of `BambooRefreshService.refreshDeskAgents`, fed by one `listEmployees` + one `listTimeOff` call keyed by BambooHR ID into an in-memory `Map<String, BambooEmployee>` and `Map<String, List<BambooTimeOff>>` that every sheet's row loop merges against.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Batched pre-transaction BambooHR fetch (employees + time-off) | API/Backend (integration layer) | — | External HTTP call; must happen outside DB transaction per established `refreshDeskAgents` pattern |
| Per-field precedence merge engine | API/Backend (service layer) | — | Pure business logic operating on in-memory snapshot + parsed sheet rows |
| PTO/day-pattern arbitration (dated vs recurring) | API/Backend (service layer, feeds solver) | Database (agent_day_hours, AgentDayOff are the read targets) | Consumed by `SolverService.buildRecurringDaysOff`/`buildAgentDaysOffMap` — must produce facts in the shape the solver already expects |
| Merge report construction | API/Backend (DTO assembly during upload) | — | Ephemeral, built during the same request that does the merge (D-13) |
| Merge report display | Browser/Client (React modal) | — | Pure rendering of the DTO the backend returns; no new client-side computation |
| Sync-failure error surfacing | API/Backend (exception → HTTP 503) | Browser/Client (error message display) | Reuses existing `BambooHRRateLimitedException` → `GlobalExceptionHandler` → 503 pipeline already wired for `/refresh` |
| `workingDaysKnown` provenance protection | Database (new column) | API/Backend (`BambooRefreshService` read-before-downgrade) | D-15 requires a persisted marker; existing boolean alone can't distinguish source |

## Standard Stack

No new libraries are introduced by this phase. Everything needed already exists in the dependency tree.

### Core (already in use, verified against `build.gradle`)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.2 `[VERIFIED: build.gradle:3]` | Web/DI/transactions | Already the project framework |
| Spring `TransactionTemplate` | (spring-tx, bundled) | Programmatic transaction boundary around the merge/write pass | Existing precedent in `BambooRefreshService.refreshDeskAgents` (`BambooRefreshService.java:121-125`) — avoids the self-invocation proxy problem `@Transactional` has when a class calls its own annotated method |
| Apache POI (`poi-ooxml`) | 5.3.0 `[VERIFIED: build.gradle:39]` | Spreadsheet parsing (unchanged from Phase 10) | Already the parser dependency; Phase 11 does not touch cell-reading, only what happens after a row is parsed |
| Flyway | bundled with Spring Boot 3.4.2 `[VERIFIED: build.gradle:29-30]` | Schema migration for the D-15 provenance column | Forward-only, established convention; latest migration on disk is `V35__contracted_hours_under_back_to_hard.sql` `[VERIFIED: file listing, .../db/migration/]`, so the next migration is `V36` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 + Mockito + AssertJ | via `spring-boot-starter-test` `[VERIFIED: build.gradle:43]` | Unit tests for merge precedence, mirroring existing `DeskAssignmentUpload*Test` suite style | All new merge-logic tests — no Spring context needed, pattern is plain `mock(Repository.class)` unit tests (confirmed in `DeskAssignmentUploadMultiSheetTest.java:31-68`) |
| H2 | testRuntimeOnly `[VERIFIED: build.gradle:45]` | In case any integration-style test needs a real datasource for the new provenance column | Only if a migration-level test is added; the existing merge-adjacent tests are pure-mock unit tests and don't need it |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `TransactionTemplate` wrapping the persistence pass | `@Transactional` on a private helper called via a self-injected proxy or a separate `@Component` | More moving parts (extra bean, `@Lazy` self-injection) for no benefit — `TransactionTemplate` is already the established in-repo pattern for exactly this HTTP-before-TX shape |
| New `AgentMergeService` in `com.wfm.integration` | Extend `DeskAssignmentUploadService` in place | `DeskAssignmentUploadService` is already ~640 lines (`DeskAssignmentUploadService.java`, verified by reading the full file) covering shape detection, per-row parsing, and clear-desk; a merge engine is a distinct concern and the CONTEXT explicitly leaves this to planner discretion. Placing it in `com.wfm.integration` also solves the `WorkingDaysParser` package-privacy problem below. |

**Installation:** none — no new dependencies.

## Package Legitimacy Audit

**N/A — this phase installs no new external packages.** All work uses libraries already present and verified in `build.gradle` (Spring Boot 3.4.2, Apache POI 5.3.0, Flyway, JUnit/Mockito/AssertJ/H2 test stack). No `npm`/`pip`/`cargo` install commands apply; this is a Java/Gradle project with a fixed, already-audited dependency set for this milestone.

## Architecture Patterns

### System Architecture Diagram

```
Operator uploads workbook
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│ uploadDeskAssignments()  — NOT @Transactional at the entry point │
│                                                                   │
│  1. bambooHRClient.listEmployees(tenantId, null)  ──┐            │
│  2. bambooHRClient.listTimeOff(tenantId, from, to) ──┤ BLOCKING,  │
│                                                       │ no open TX│
│     (BambooHRRateLimitedException here aborts        │           │
│      with ZERO db writes — MRG-07 — before step 3)   │           │
│                                                       ▼           │
│  3. Build in-memory snapshot:                                    │
│       Map<bamboohrId, BambooEmployee>                            │
│       Map<bamboohrId, List<BambooTimeOff>>                       │
│                                                                   │
│  4. transactionTemplate.executeWithoutResult(status -> {         │
│       for each sheet (desk):                                     │
│         for each row (agent):                                    │
│           mergeIdentityFields(bambooEmployee, sheetRow) ─┐       │
│           mergeDayPattern(bambooEmployee, sheetDayGroup) ┤merge  │
│           mergePto(bambooTimeOffs, sheetPtoDays, window) ┘engine │
│           write Agent / agent_day_hours                          │
│           accumulate MergeReportEntry per divergent field        │
│     })                                                            │
│                                                                   │
│  5. return DeskAssignmentUploadResult                            │
│       { ...existing fields..., mergeReport: [...] }              │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
Upload Results modal (React) — new "Merge Report" section
renders mergeReport rows: field | source | bambooValue | sheetValue
```

### Recommended Project Structure
```
src/main/java/com/wfm/
├── integration/
│   ├── BambooHRClient.java          # unchanged — interface already correct shape
│   ├── AgentMergeService.java       # NEW — the merge engine (package placement lets it
│   │                                #   call WorkingDaysParser directly, package-private)
│   └── WorkingDaysParser.java       # unchanged — already reusable for D-05 pattern comparison
├── service/
│   └── DeskAssignmentUploadService.java  # restructured: entry point loses @Transactional,
│                                          #   delegates identity/pattern/PTO decisions to
│                                          #   AgentMergeService, keeps row parsing as-is
├── dto/
│   └── MergeReportEntry.java        # NEW record — one row per divergence/gap-fill (D-11)
└── model/
    └── Agent.java                   # +1 column: working days provenance marker (D-15)
```

### Pattern 1: HTTP-before-transaction, then `TransactionTemplate` for the write pass
**What:** All blocking BambooHR calls happen with no Spring transaction open; only after both calls return successfully does a single `TransactionTemplate.executeWithoutResult` block open the transaction that does every DB write for the whole workbook.
**When to use:** Exactly here — D-01/D-02 require this shape, and it is the proven pattern already in production for the desk-scoped refresh.
**Example:**
```java
// Source: BambooRefreshService.java:90-145 (existing, verified in this session)
public void refreshDeskAgents(UUID deskId) {
    ...
    try {
        List<BambooEmployee> employees = bambooHRClient.listEmployees(String.valueOf(tenantId), deskName);
        List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), from, to);

        // Use TransactionTemplate instead of @Transactional on a self-invoked method,
        // which Spring proxies cannot intercept.
        transactionTemplate.executeWithoutResult(status ->
                persistRefreshData(deskId, tenantId, desk, employees, timeOffs, from, to));
        ...
    } catch (BambooHRRateLimitedException e) {
        syncEvent.setErrorMessage(e.getMessage());
        syncEvent.setRetryAfterSeconds(e.getRetryAfterSeconds());
        throw e;   // propagates uncaught to GlobalExceptionHandler -> 503, zero writes
    }
}
```
`uploadDeskAssignments` must adopt this exact shape: remove `@Transactional` from the method signature (`DeskAssignmentUploadService.java:77`), do the two fetches, then wrap the entire existing sheet-loop body (currently lines ~199-480) in `transactionTemplate.executeWithoutResult(...)`.

### Pattern 2: Field-level precedence merge (D-06/D-07/D-08)
**What:** A single predicate — "BambooHR has data" = not null, not empty, not whitespace-only — decides every contested identity field. BambooHR wins whenever it has data; the spreadsheet value is used only when BambooHR is blank, and is otherwise discarded but recorded in the report.
**When to use:** `displayName`/`firstName`/`lastName`, `workEmail`, `department`, `jobTitle`, `status`/active, `employmentHistoryStatus` — the exact field set D-08 assigns to BambooHR.
**Example (the precedence this phase must implement — inverts the current code):**
```java
// Current (WRONG for Phase 11) — DeskAssignmentUploadService.java:365-386:
//   1. backfill blank agent fields FROM the cache
//   2. THEN unconditionally overwrite with the sheet value if the sheet supplied one
// i.e. sheet wins whenever present. This is Phase 10's D-07 (identity fields optional,
// override cache when present) and is explicitly what MRG-02 replaces.

// Target shape for Phase 11 (D-06/D-07):
static String mergeField(String bambooValue, String sheetValue) {
    boolean bambooHasData = bambooValue != null && !bambooValue.isBlank();
    return bambooHasData ? bambooValue : sheetValue;   // sheet only fills BambooHR's gap
}
// Every field where mergeField returns bambooValue AND sheetValue was also non-blank
// AND sheetValue != bambooValue is a "BambooHR overrode the sheet" report row (D-07/D-11).
```

### Pattern 3: Whole day-group replace, not per-cell field precedence (D-05)
**What:** Unlike identity fields, the Mon–Sun day group is treated as one atomic unit. If the sheet supplies a full week (which Phase 10's D-04 already guarantees — every day cell is required, blank invalid), the sheet's whole pattern **replaces** the BambooHR field-4517-derived MANDATORY days, rather than being unioned (Phase 10 D-16, now reversed) or merged cell-by-cell.
**When to use:** Deciding whether an agent's weekly working pattern comes from the sheet or from BambooHR's `customWorkingdays`.
**Example:**
```java
// Since every uploaded row has all 7 day cells populated (Phase 10 D-04 — blank is invalid),
// "the sheet supplies a pattern" is unconditionally true for every successfully-parsed row.
// So per D-05, for any row that reaches the write step, the sheet's day group IS the agent's
// pattern for the week — BambooHR's field-4517 MANDATORY generation must not additionally
// block days the sheet marked as worked. This means:
//   - Do NOT let BambooRefreshService's existing MANDATORY-from-4517 AgentDayOff rows survive
//     for agents just re-imported by this upload with a full week supplied, OR
//   - Have SolverService.buildAgentDaysOffMap treat the sheet's agent_day_hours as authoritative
//     and exclude/override 4517-derived AgentDayOff rows for the same (agent, weekday) — this
//     is the "un-blocking" MRG-02/D-05 requires.
// This needs a concrete decision in planning: which layer performs the override — the merge
// engine writing agent_day_hours (already true per current code) is necessary but NOT sufficient
// by itself, because AgentDayOff rows from a PRIOR BambooHR refresh are a separate table that
// this upload does not touch. Reconciliation must happen either at merge-write time (delete/
// suppress stale 4517 MANDATORY AgentDayOff rows for re-imported agents) or at solve time
// (SolverService already reads BOTH tables — see buildAgentDaysOffMap, SolverService.java:1013-1019
// — so precedence could instead be enforced there). FLAGGED AS OPEN QUESTION below — the
// CONTEXT's decisions (D-05, D-09, D-10) describe the desired OUTCOME precisely but leave the
// mechanism ("where does un-blocking actually happen") to the planner.
```

### Pattern 4: Dated-vs-recurring PTO arbitration inside a fixed sync window (D-09)
**What:** For every date inside `[from, to]` (the same `lookback-weeks`/`lookahead-weeks` window `BambooRefreshService` already uses), BambooHR governs: no BambooHR PTO record for that date = the agent works, even if the spreadsheet's recurring pattern says PTO for that weekday. Outside the window, the spreadsheet's recurring pattern fills.
**When to use:** Reconciling `agent_day_hours.day_off_type = PTO` rows (recurring, materialized by `SolverService.buildRecurringDaysOff` at solve time — `SolverService.java:1038-1060`) against BambooHR's dated `AgentDayOff` rows for the same agent.
**Example — the window bounds already exist and must be reused, not reinvented:**
```java
// Source: BambooRefreshService.java:47-51 (existing config, verified in this session)
@Value("${bamboohr.time-off.lookahead-weeks:8}")
private int lookaheadWeeks;
@Value("${bamboohr.time-off.lookback-weeks:12}")
private int lookbackWeeks;
// LocalDate from = LocalDate.now().minusWeeks(lookbackWeeks);
// LocalDate to   = LocalDate.now().plusWeeks(lookaheadWeeks);
```
D-09's "within the synced window" boundary is exactly this `[from, to]` range. Since `SolverService.buildRecurringDaysOff` (which expands the spreadsheet's weekly PTO pattern into dated facts) is called with its own `[from, to]` solve-horizon range (typically much shorter than the 20-week BambooHR window), the arbitration is naturally scoped: any date the solve horizon touches that also falls inside the BambooHR sync window must defer to BambooHR's dated presence/absence of PTO; only solve-horizon dates *outside* the BambooHR window fall back to the recurring pattern. This arbitration must happen where `allDaysOff` is assembled (`SolverService.java:139-178`), not in the merge engine — the merge engine's job (per D-10) is only to make sure `agent_day_hours.day_off_type` correctly reflects the sheet's recurring pattern; **no new PTO materialization or storage is introduced** (D-10 is explicit: nothing is persisted for recurring PTO beyond what Phase 10 already writes).

### Anti-Patterns to Avoid
- **Adding `@Transactional` back to `uploadDeskAssignments` and calling `bambooHRClient` inside it:** silently violates D-01/D-02 — the HTTP call would run inside an already-open transaction, holding a DB connection during a potentially slow/rate-limited external call, and a mid-transaction 503 would leave partial writes from earlier sheets unless the whole method is one transaction (which defeats per-row skip-and-continue... except D-02 explicitly wants whole-upload atomicity only for the *sync* failure, not per-row parse failures — these are different failure classes and must be handled differently, see Pitfall 1 below).
- **Reusing `ClientManagementService.ensureCachePopulatedForUpload` unmodified:** it no-ops if the cache is already warm (`ClientManagementService.java:162`), which breaks the "always fresh" guarantee MRG-01 requires and D-04 explicitly locks in.
- **Reading `customWorkingdays`/`employmentHistoryStatus` off `BambooEmployeeResponse`:** that DTO doesn't carry them (`dto/BambooEmployeeResponse.java:3-10`). Use the raw `BambooEmployee` record from the fresh fetch instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Parsing BambooHR's `customWorkingdays` field-4517 string into a day set | A new parser inside the merge engine | `WorkingDaysParser.parseWorkingDays` / `offDaysFrom` (`integration/WorkingDaysParser.java`) | Already handles ranges, comma lists, "to"-phrasing, trailing annotations, and the `Variable`/blank data-gap case, tolerantly and without throwing (documented threat-model note: "never throws on any input"). Re-implementing it risks silently diverging from what `BambooRefreshService` already does for the same field. |
| Deciding whether a job title is schedulable | A second allowlist check in the merge engine | `AgentEligibilityService.isIncludedByTitleAllowlist` | Already "the single control for schedulability shared by solver, upload and template" (per Phase 10 code comment at `DeskAssignmentUploadService.java:416-417`) — a second implementation would let solver/upload/template disagree again, the exact bug fixed 2026-08-12. |
| Detecting a 503/429 from BambooHR and building an operator message | A new exception type or a try/catch around the fetch calls in the merge engine | `BambooHRRateLimitedException` propagating uncaught to `GlobalExceptionHandler.handleBambooHRRateLimited` (`GlobalExceptionHandler.java:67-70`) | Already wired end-to-end: throws → 503 `BAMBOOHR_RATE_LIMITED` with `ex.getMessage()` (documented as containing no secrets) and `retryAfterSeconds`. The merge engine just needs to let the exception propagate before opening its transaction (MRG-07 is then a natural consequence of Pattern 1, not new code). |

**Key insight:** almost everything MRG-01 through MRG-07 need already has a same-shaped precedent somewhere in this codebase (`refreshDeskAgents`'s HTTP-before-TX split, `WorkingDaysParser`'s pattern parsing, `GlobalExceptionHandler`'s rate-limit handling, `buildRecurringDaysOff`'s recurring-to-dated expansion). The actual net-new work is (1) restructuring the transaction boundary, (2) inverting one precedence direction in the existing backfill code, and (3) a new report DTO + one new schema column. Resist the temptation to design new mechanisms where an existing one already does the job.

## Common Pitfalls

### Pitfall 1: Conflating "sync failure" atomicity with "row parse failure" atomicity
**What goes wrong:** Wrapping the *entire* upload (including per-row parse/validation) in one all-or-nothing transaction would silently change Phase 10's established skip-and-continue behaviour — a single bad row would no longer be skippable; the whole upload would need to roll back or succeed as one unit.
**Why it happens:** D-02 says "the whole upload is the atomic unit" for the *sync* — easy to over-read as "wrap everything in one transaction" and lose skip-and-continue in the process.
**How to avoid:** Only the **sync** (the two BambooHR HTTP calls) gates a full abort with zero writes. Once inside `transactionTemplate.executeWithoutResult`, per-row/per-sheet skip-and-continue (Phase 10's existing behaviour) is explicitly preserved by D-02: "Per-row/per-sheet parse failures keep Phase 10's skip-and-continue behaviour unchanged — only sync failure aborts everything." A single `TransactionTemplate` block CAN still contain the full sheet loop with row-level `continue` statements exactly as today; the atomicity distinction is about *what triggers a full rollback* (nothing from inside the loop should), not about disabling per-row skipping.
**Warning signs:** A test where one bad row in an otherwise-valid sheet causes the whole upload to report 0 imported.

### Pitfall 2: The un-blocking mechanism has two plausible homes and the CONTEXT doesn't pick one
**What goes wrong:** D-05 says the sheet's day group "supersedes" BambooHR's field-4517 pattern, but the actual blocking mechanism BambooHR populates (`AgentDayOff` rows with `type=MANDATORY`, written by `BambooRefreshService.persistRefreshData` steps 5a) lives in a completely different table from what this upload writes (`agent_day_hours`). D-03 forbids the upload from calling `persistRefreshData` (no second writer), so the upload cannot simply "not write" the MANDATORY block — a stale one from a *previous* refresh may already be sitting in `agent_day_off` for that agent/date.
**Why it happens:** The requirement is stated as a data-precedence rule, but the codebase enforces day-off blocking through two separate storage mechanisms (`AgentDayOff` for BambooHR-sourced dated blocks, `agent_day_hours.day_off_type` for spreadsheet-sourced recurring blocks) that were deliberately kept separate in Phase 10 (D-12) specifically to avoid the double-write problem.
**How to avoid:** Decide explicitly during planning whether un-blocking happens (a) at merge-write time — the merge engine deletes/suppresses the agent's existing 4517-derived `AgentDayOff` rows for weekdays the sheet marks as worked, or (b) at solve time — `SolverService.buildAgentDaysOffMap`/`buildRecurringDaysOff` is taught that a sheet-sourced `agent_day_hours` "no day-off type" (worked) for a given weekday takes precedence over any `AgentDayOff` row for that same agent+weekday-within-window. Option (b) keeps D-03's "one writer" invariant airtight (the merge engine still writes nothing to `agent_day_off`) and matches how D-09's PTO arbitration is already scoped (solve-time, against `allDaysOff` assembly). Recommend (b) for consistency with D-09/D-10, but this is a planning decision, not a research-settled fact — flagged in Open Questions.
**Warning signs:** A live-test agent whose sheet says "Wednesday: 8" still shows Wednesday blocked in the solve, because a `MANDATORY` `AgentDayOff` row from an earlier BambooHR refresh is still sitting in the table untouched.

### Pitfall 3: `WorkingDaysParser` is package-private
**What goes wrong:** `WorkingDaysParser` is declared `final class WorkingDaysParser` (no `public` modifier) in `com.wfm.integration`, with the class Javadoc stating "Called only from `BambooRefreshService` in the same package." A merge engine placed in `com.wfm.service` (where `DeskAssignmentUploadService` already lives) cannot call it directly — a compile error, not a runtime surprise.
**Why it happens:** Deliberate encapsulation choice in Phase 9/10, not an oversight to "fix" by simply adding `public`.
**How to avoid:** Either (a) place the new merge-engine class in `com.wfm.integration` alongside `WorkingDaysParser` and `BambooRefreshService` (recommended — also co-locates it with `BambooEmployee`/`BambooTimeOff`, which it needs directly), or (b) widen `WorkingDaysParser` to `public` if cross-package access is unavoidable for the chosen design. Confirm during planning which package the merge engine lives in before writing code against `WorkingDaysParser`.
**Warning signs:** A compile failure citing "WorkingDaysParser is not public in com.wfm.integration; cannot be accessed from outside package."

### Pitfall 4: `agent_day_hours` has a `(agent_id, day_of_week)` unique constraint that clear-desk-then-reimport already handles — don't double-write
**What goes wrong:** `AgentDayHours` has `@UniqueConstraint(columnNames = {"agent_id", "day_of_week"})` (`model/AgentDayHours.java:9-11`). The existing `clearDesk` deletes all `agent_day_hours` rows for every agent on the desk before the sheet loop re-adds them (`agentDayHoursRepository.deleteByAgent_Id`, `DeskAssignmentUploadService.java:500`). If the merge engine's per-field precedence logic ends up calling `agentDayHoursRepository.save(...)` more than once per (agent, weekday) — e.g. once to record the sheet's raw value and again to record the merged/final value — it will violate the unique constraint.
**Why it happens:** Natural if the merge report (D-11) is built by writing a "candidate" row and later "correcting" it, instead of computing the final merged value first and writing once.
**How to avoid:** Compute the merged `(hours, dayOffType)` result per weekday *before* calling `agentDayHoursRepository.save`, exactly as the current single-write loop already does (`DeskAssignmentUploadService.java:460-473`) — only now the values going into that one save come from the merge decision rather than directly from the parsed cell.
**Warning signs:** A `DataIntegrityViolationException` on `agent_day_hours` during a test that imports a full sheet.

### Pitfall 5: Fractional hours upload-vs-solve validation mismatch (documented, out of Phase 11 scope, but visible in the merge report)
**What goes wrong:** The parser accepts fractional contracted hours (`7.5` etc., Phase 10 D-10), but `SolverService`'s pre-solve validation rejects any value that isn't a whole multiple of the solve increment. An agent with `7.5` parses cleanly, then blocks the solve later with validation errors — a gap between upload-time and solve-time acceptance.
**Why it happens:** Two independent validation layers (`DeskAssignmentUploadService.parseDayCell` and `SolverService` pre-solve checks) were built at different times against different constraints.
**How to avoid:** Not a Phase 11 fix (explicitly deferred — "belongs with solver/validation work, not the merge engine," per CONTEXT `<deferred>`). Worth surfacing in the merge report is a **planner** decision, not a requirement — MRG-04/05 are about BambooHR-vs-sheet provenance, not hours-format validation, so don't scope-creep this in unless the planner explicitly decides to.
**Warning signs:** none for Phase 11 itself — noted here purely so the planner doesn't accidentally fold this into merge-report scope.

## Code Examples

### Existing merge-adjacent precedence code to invert (D-06/D-07 target)
```java
// Source: DeskAssignmentUploadService.java:365-386 (read this session, verbatim)
// Backfill missing identity fields from the BambooHR cache
if (isBlank(agent.getEmail())) agent.setEmail(cached.workEmail());
if (isBlank(agent.getDepartment())) agent.setDepartment(cached.department());
if (isBlank(agent.getJobTitle())) agent.setJobTitle(cached.jobTitle());
if (isBlank(agent.getFirstName()) || isBlank(agent.getLastName())) {
    AgentNameSplitter.Split cachedSplit = AgentNameSplitter.split(cached.displayName());
    if (isBlank(agent.getFirstName())) agent.setFirstName(cachedSplit.firstName());
    if (isBlank(agent.getLastName())) agent.setLastName(cachedSplit.lastName());
}
if (isBlank(agent.getName())) agent.setName(cached.displayName());

// Spreadsheet-supplied identity fields are optional and override the
// cache when present (D-07)
if (!isBlank(firstName)) agent.setFirstName(firstName.trim());
if (!isBlank(lastName)) agent.setLastName(lastName.trim());
if (!isBlank(jobTitle)) agent.setJobTitle(jobTitle.trim());
if (!isBlank(email)) agent.setEmail(email.trim());
if (!isBlank(department)) agent.setDepartment(department.trim());
if (!isBlank(activeStr)) agent.setActive(parseActive(activeStr));
```
This is Phase 10's D-07 behaviour: sheet wins whenever present. Phase 11's MRG-02 needs the opposite precedence direction for every field BambooHR carries (D-08's list). This exact block is the concrete rewrite target for the planner's tasks.

### Existing exception-to-HTTP-response wiring the merge engine gets for free (MRG-07)
```java
// Source: GlobalExceptionHandler.java:67-70 (read this session, verbatim)
@ExceptionHandler(BambooHRRateLimitedException.class)
public ResponseEntity<ErrorResponse> handleBambooHRRateLimited(BambooHRRateLimitedException ex) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_RATE_LIMITED", ex.getMessage(), List.of());
}
```
As long as the fetch calls happen before `transactionTemplate.executeWithoutResult(...)` opens (Pattern 1), an uncaught `BambooHRRateLimitedException` from either `listEmployees` or `listTimeOff` reaches this handler with zero DB writes made — satisfying MRG-07 without any new exception-handling code in the merge engine itself.

### Current DTO shape the merge report extends (D-12/D-13)
```java
// Source: DeskAssignmentUploadService.java:553-561 (read this session, verbatim)
public record DeskAssignmentUploadResult(
        int assignedCount,
        int skippedCount,
        List<String> assignedDetails,
        List<SkippedRow> skippedDetails,
        List<SheetSummary> sheetSummaries,
        List<String> warnings,
        List<SkippedSheet> skippedSheets
) {}
```
Frontend mirror, field-for-field (`frontend/src/api/client.ts:468-476`, verbatim):
```typescript
export interface DeskAssignmentUploadResult {
  assignedCount: number
  skippedCount: number
  assignedDetails: string[]
  skippedDetails: SkippedRow[]
  sheetSummaries: SheetSummary[]
  warnings: string[]
  skippedSheets: SkippedSheet[]
}
```
D-12 requires the merge report to be a new field on this same record (backend) / interface (frontend) pair — e.g. `mergeReport: List<MergeReportEntry>` / `mergeReport: MergeReportEntry[]` — added alongside the existing seven fields, both sides kept in the same field-for-field-matched style the Phase 10 SUMMARY documents as an established convention ("Frontend TS interfaces matched field-for-field to the already-implemented backend DTOs").

### Modal insertion point for the merge report (D-12)
```tsx
// Source: frontend/src/pages/ClientManagement.tsx:505-516 (read this session, verbatim)
{uploadResult.sheetSummaries.length > 0 && (
  <div style={{ marginTop: '0.5rem' }}>
    <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>Per-desk rollup</div>
    <ul style={{ fontSize: '0.85rem', margin: 0, paddingLeft: '1.25rem' }}>
      {uploadResult.sheetSummaries.map((sheet: SheetSummary, idx: number) => (
        <li key={idx}>
          {sheet.deskName}: {sheet.importedCount} imported, {sheet.skippedCount} skipped
        </li>
      ))}
    </ul>
  </div>
)}
```
A new "Merge Report" block follows the same `uploadResult.X.length > 0 &&` conditional-render idiom, inserted as its own section in this same modal (not a separate modal) per D-12 — most naturally directly after the per-desk rollup block and before the warnings block, since divergences are more central to this phase's purpose than warnings.

### Migration precedent for the D-15 provenance column
```sql
-- Source: db/migration/V28__add_agent_working_days_known.sql (read this session, verbatim)
ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;
```
The D-15 provenance marker follows this exact style — a new nullable/defaulted column on `agent`, `V36__*.sql` (next available number after `V35`, confirmed via directory listing this session), with an inline comment explaining the hazard it closes (mirroring the `V30` comment style, which explains *why* the column exists, not just what it does).

## State of the Art

| Old Approach (Phase 10) | New Approach (Phase 11) | When Changed | Impact |
|--------------------------|--------------------------|---------------|--------|
| Spreadsheet day cell coexists/unions with BambooHR field-4517 MANDATORY blocks (D-16) — a day is off if *either* source says so | True precedence — BambooHR authoritative where it has data; sheet's day group replaces the 4517 pattern where the sheet supplies one, can un-block a BambooHR day off | This phase (reverses Phase 10 D-16) | Operators can now correct a wrong BambooHR pattern via the sheet; previously impossible |
| Identity fields: sheet value overrides BambooHR cache value whenever the sheet supplies one (Phase 10 D-07) | BambooHR value used wherever BambooHR has data; sheet only fills gaps (MRG-02) | This phase | Sheet can no longer silently override a populated BambooHR field; discarded sheet values are recorded in the report instead |
| Upload calls `ensureCachePopulatedForUpload`, which is a no-op if any cache entry already exists for the tenant | Fresh `listEmployees`+`listTimeOff` fetch on every upload, unconditionally | This phase (MRG-01/D-04) | Upload latency now scales with BambooHR API latency every time, not just on cold cache — this is the "Open Risk" REQUIREMENTS.md already flags under "Fresh-sync-on-upload couples upload latency to BambooHR availability" |
| `0` day cell hard-blocks scheduling (Phase 10 D-05, confirmed live 2026-08-18 as producing an *unintended* block) | `0` means no contracted hours but the agent remains available — softer than MANDATORY/PTO | Operator decision 2026-08-18, same day as Phase 11 CONTEXT gathered | Agents can now be scheduled against a `0` day cell; this already matches what `SolverService.buildRecurringDaysOff` does today (skips rows with `dayOffType == null`, and a `0`-hours row has `dayOffType = null`) — **no code change needed for this specific point**, it's a documentation/expectation correction, not an implementation gap. |

**Deprecated/outdated:**
- Phase 10 D-16 (union/coexist rule) — explicitly named in its own text as "a temporary data-safety stance pending Phase 11."
- Phase 10 D-05's literal claim that a `0` day is "NOT available as overflow" — superseded by the 2026-08-18 operator decision; the code already behaves the new way, only the stated intent was wrong.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The un-blocking mechanism for D-05 (sheet supersedes 4517 MANDATORY) should be enforced at solve time (`SolverService.buildAgentDaysOffMap`/day-off assembly) rather than by the merge engine deleting/suppressing `AgentDayOff` rows at upload time. | Pitfall 2, Pattern 3 | If the planner instead chooses upload-time suppression, the merge engine gains a dependency on `AgentDayOffRepository` that Phase 10 deliberately avoided (D-16 guard, verified by reflection in `DeskAssignmentUploadMultiSheetTest`) — a structural decision that needs to be made explicitly, not inferred from this research. This is flagged as an assumption, not a locked recommendation, precisely because the CONTEXT does not decide it. |
| A2 | A new `AgentMergeService` (or equivalently-named class) should live in `com.wfm.integration`, not `com.wfm.service`, to access package-private `WorkingDaysParser`. | Standard Stack, Pitfall 3 | If the planner places it in `com.wfm.service` instead, either `WorkingDaysParser` must be made `public` (a Phase 9/10 encapsulation decision being reopened) or the merge engine must duplicate/re-derive the field-4517 parsing logic, both of which are worse than the package placement recommended here. |
| A3 | `BambooSyncEvent.deskId` should be set to `null` for an upload-triggered whole-workbook sync (rather than one event per desk, or no event at all). | Architectural Responsibility Map, Claude's Discretion item in CONTEXT | `deskId` is already nullable (`model/BambooSyncEvent.java` field has no `nullable = false`), so this is low-risk either way; if wrong, the "latest sync" endpoint (`BambooSyncEventService.getLatest`) simply reflects a slightly different granularity than intended — cosmetic, not a data-integrity risk. |

**If this table is empty:** N/A — three assumptions above need planner/operator confirmation before becoming locked decisions; everything else in this document is `[VERIFIED]` against source read directly in this session.

## Open Questions

1. **Where does the D-05 un-blocking mechanism actually execute — merge-write time or solve time?**
   - What we know: BambooHR's field-4517 MANDATORY days live in `AgentDayOff` (written only by `BambooRefreshService.persistRefreshData`, which D-03 forbids the upload from calling). The sheet's pattern lives in `agent_day_hours` (written by the upload/merge engine). The solver already reads both tables when assembling `allDaysOff` (`SolverService.java:139-178`).
   - What's unclear: whether "the sheet un-blocks a BambooHR day off" (D-05) should be implemented by having the merge engine actively delete/suppress stale `AgentDayOff` rows for re-imported agents, or by teaching the solver's day-off assembly to prefer `agent_day_hours` over `AgentDayOff` for the same (agent, weekday) when both exist.
   - Recommendation: solve-time arbitration (matches D-09's PTO-window arbitration, which is explicitly solve-time and explicitly avoids new storage per D-10) — but this must be confirmed as a plan-time decision, not assumed silently. See Assumption A1.

2. **Should `refreshInProgress` (the per-desk guard in `BambooRefreshService`) interact with the upload's batched fetch?**
   - What we know: `refreshDeskAgents` guards against concurrent refreshes of the *same desk* via `refreshInProgress.putIfAbsent(deskId, true)`. D-03 says the upload sync is read-only and does not call `persistRefreshData`, so it never touches this guard today.
   - What's unclear: if an operator clicks "Refresh from BambooHR" on a desk (`DeskAgentController.java:83-85`) at the same moment they upload a workbook touching that same desk, both fetch from BambooHR concurrently with no coordination. This is a pre-existing race in the codebase's design (not introduced by Phase 11), but Phase 11 adds a second, more frequent trigger for BambooHR fetches, raising the odds of hitting it.
   - Recommendation: out of scope to fix in Phase 11 given the CONTEXT doesn't mention it and MRG-07's failure mode (503) already covers the rate-limit consequence of concurrent fetches; worth a one-line note in the plan's risk section rather than new synchronization code.

3. **What HTTP timeout should the batched upload-sync fetch use?**
   - What we know: D-04 says "synchronous upload with a longer timeout" but does not specify a value. `HttpBambooHRClient`'s current timeout configuration was not located in this research pass (out of the phase's cited file list).
   - What's unclear: the concrete timeout value/config key.
   - Recommendation: planner should locate `HttpBambooHRClient`'s HTTP client configuration during planning and decide whether the existing timeout is sufficient for a whole-tenant fetch (potentially larger than a single desk's `listEmployees` call) or needs an explicit override for this endpoint.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| BambooHR API (live) | Fresh sync on every upload (MRG-01) | Configured via DB-stored `bamboohr.server`/`bamboohr.api-key` `[VERIFIED: integration/DelegatingBambooHRClient.java:35-40]` | n/a (external API) | `MockBambooHRClient` — `application.yml:37` sets `bamboohr.mock: true` as the local/dev default `[VERIFIED: src/main/resources/application.yml:36-39]`; `DelegatingBambooHRClient` is `@Primary` and falls back to the mock automatically whenever server/API key aren't both configured |
| PostgreSQL | Persisting the new provenance column + all existing agent/agent_day_hours writes | ✓ (existing project dependency, unchanged by this phase) | — | — |
| H2 | Test-time datasource, if any migration-level test is added | ✓ `[VERIFIED: build.gradle:45]` | testRuntimeOnly | — |

**Missing dependencies with no fallback:** none.

**Missing dependencies with fallback:** live BambooHR API — falls back to `MockBambooHRClient` automatically; no operator action needed for dev/test, but production behaviour genuinely depends on the live API being reachable (this is the existing, already-accepted MRG-01 risk noted in REQUIREMENTS.md's Open Risks section, not new to this phase).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (`useJUnitPlatform()`, `build.gradle:47-49`) + Mockito + AssertJ, via `spring-boot-starter-test` `[VERIFIED: build.gradle:43]` |
| Config file | none — plain Gradle `test` task, no separate config file |
| Quick run command | `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*Test" --tests "com.wfm.integration.*Merge*Test"` |
| Full suite command | `./gradlew test` |

Established test style (verified by reading `DeskAssignmentUploadMultiSheetTest.java:31-68` this session): plain unit tests, no Spring context, `mock(Repository.class)`/`mock(Service.class)` collaborators wired directly into the constructor under test, `MockMultipartFile` + `XSSFWorkbook` for building test workbooks in-memory, `TenantContext.setTenantId(...)` set manually per test. New merge-engine tests should follow this exact shape — fast, no `@SpringBootTest`.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MRG-01 | Upload triggers a fresh sync (not cached) before merge | unit | `./gradlew test --tests "*UploadFreshSyncTest"` | ❌ Wave 0 |
| MRG-02 | BambooHR wins when populated; sheet fills gaps only | unit | `./gradlew test --tests "*MergePrecedenceTest"` | ❌ Wave 0 |
| MRG-03 | Dated BambooHR PTO wins within sync window; recurring sheet PTO fills outside it | unit | `./gradlew test --tests "*PtoArbitrationTest"` (likely in `SolverServiceTest` or a new class, depending on A1's resolution) | ❌ Wave 0 |
| MRG-04 | Merge report shows per-field source | unit | `./gradlew test --tests "*MergeReportTest"` | ❌ Wave 0 |
| MRG-05 | Report shows overridden sheet values | unit | same as MRG-04 (one report shape covers both) | ❌ Wave 0 |
| MRG-06 | Sheet-only pattern makes `workingDaysKnown` true and solver-eligible | unit | `./gradlew test --tests "*WorkingDaysKnownTest"` — extend or add alongside existing `filterEligible` coverage in `SolverServiceTest` | ❌ Wave 0 (existing `SolverServiceTest` file location not confirmed this session — verify during planning) |
| MRG-07 | Sync failure aborts with zero writes and a clear message | unit | `./gradlew test --tests "*UploadSyncFailureTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** the targeted `--tests` filter for the file(s) touched.
- **Per wave merge:** `./gradlew test --tests "com.wfm.service.*" --tests "com.wfm.integration.*"`.
- **Phase gate:** `./gradlew test` full suite green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] New merge-engine test file(s) — none exist yet; naming above is a suggestion, planner should confirm exact class names once the merge engine's home package/class is decided (Assumption A2).
- [ ] Confirm whether `SolverServiceTest` already exists and covers `filterEligible`/`buildRecurringDaysOff` before assuming MRG-06/MRG-03 tests are pure net-new additions vs. extensions of an existing file — not confirmed in this research pass (out of the phase's explicitly cited file list; planner should check `src/test/java/com/wfm/service/SolverServiceTest.java` or equivalent).
- [ ] No new framework install needed — JUnit/Mockito/AssertJ/H2 already present.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Trust boundary at the BambooHR HTTP client — unchanged by this phase, but the merge engine adds a second call site (`listEmployees`+`listTimeOff` for the whole tenant) alongside the existing desk-scoped one; both go through the same `BambooHRClient` interface / `DelegatingBambooHRClient` gate |
| V4 Access Control | yes | Existing `TenantContext`-scoped multi-tenancy — every query/write in the merge engine must carry `tenantId` exactly as the current upload code already does throughout (verified: every `Repository` call site in `DeskAssignmentUploadService.java` passes `tenantId`) |
| V5 Input Validation | yes | Reuses Phase 10's established day-cell/row validation (unchanged); new merge-report construction must not introduce a new unvalidated input path — the merge report only *reads* already-validated fields, it does not accept new operator input |
| V9 Communications | yes (inherited, not new) | BambooHR API key handling — **already flagged as an open, unresolved security item** in `STATE.md` ("BambooHR API key exposed 2026-06-02 was never rotated... unresolved," Backlog 999.7). Phase 11 does not touch key storage/rotation and should not be scoped to fix it, but the merge engine's *second* fetch call increases API key usage frequency — worth a one-line awareness note, not a new mitigation task. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data bleed via a missed `tenantId` filter in the new merge-engine code | Information Disclosure | Every repository call must include `tenantId`, matching the established pattern throughout `DeskAssignmentUploadService`/`BambooRefreshService`/`ClientManagementService` (all verified this session to consistently scope by `tenantId`) |
| PII (agent names/emails) logged at INFO from the merge report or new log statements | Information Disclosure | `WorkingDaysParser`'s own Javadoc already states the project's convention: "raw employee values must NOT be logged at INFO+. DEBUG only if needed" (`integration/WorkingDaysParser.java:16`) — apply the same discipline to any new merge-decision logging |
| Partial-write data corruption if a sync failure is only partially propagated (e.g. caught and swallowed somewhere in new merge code) | Tampering (data integrity) | Let `BambooHRRateLimitedException` and any other fetch-time exception propagate uncaught out of the pre-transaction fetch step — do not wrap it in a try/catch that logs-and-continues; this is precisely MRG-07's requirement |

## Sources

### Primary (HIGH confidence — direct source read this session)
- `DeskAssignmentUploadService.java` (full file) — current upload/parse implementation, transaction boundary, identity-field backfill logic
- `BambooRefreshService.java` (full file) — HTTP-before-transaction precedent, field-4517 MANDATORY generation, PTO dedup priority logic, lookback/lookahead config
- `SolverService.java` lines 1000-1083 — `buildAgentDaysOffMap`, `buildRecurringDaysOff`, `filterEligible`
- `ClientManagementService.java` (full file) — cache population, the `ensureCachePopulatedForUpload` no-op-if-warm behavior, `findCachedEmployee`
- `BambooHRClient.java`, `BambooEmployee.java`, `BambooTimeOff.java`, `dto/BambooEmployeeResponse.java` — integration contracts and the field-set gap between raw and cached DTOs
- `DayOffType.java`, `AgentDayHours.java`, `AgentDayOff.java`, `Agent.java` (fields section) — schema/model contracts
- `EnrichedColumnLayout.java` (full file) — shared column-layout constants
- `GlobalExceptionHandler.java`, `BambooHRRateLimitedException.java`, `ClientManagementController.java` lines 90-119 — exception-to-HTTP wiring
- `frontend/src/api/client.ts` lines 425-476, `frontend/src/pages/ClientManagement.tsx` lines 480-562 — Upload Results modal, existing DTO mirror
- `db/migration/V28__add_agent_working_days_known.sql`, `V30__agent_day_hours_recurring_status.sql`, directory listing confirming `V35` is latest
- `build.gradle` (full file) — dependency/version confirmation
- `application.yml` line 36-39 — BambooHR mock/live client toggle
- `.planning/phases/11-bamboohr-merge-engine-report/11-CONTEXT.md`, `.planning/phases/10-enriched-upload-parsing/10-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md` — decision/requirement provenance
- `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` lines 1-90 — established test conventions

### Secondary (MEDIUM confidence)
- None used — this phase required no external documentation lookup; all findings were verifiable directly against the codebase and project planning artifacts.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all versions confirmed directly from `build.gradle`
- Architecture: HIGH — the transaction-boundary restructuring and precedence-inversion findings are both drawn from direct reads of the exact methods being modified, not inference
- Pitfalls: HIGH — each pitfall traces to a specific, quoted line range in the current codebase; the one area of genuine ambiguity (D-05 un-blocking mechanism, Pitfall 2/Open Question 1) is explicitly flagged as unresolved rather than guessed at

**Research date:** 2026-08-18
**Valid until:** 30 days (stable internal codebase; no external API version drift risk since this phase makes no BambooHR API surface changes)
