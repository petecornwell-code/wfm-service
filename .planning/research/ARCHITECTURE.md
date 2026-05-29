# Architecture Patterns — WFM Service v1.1

**Domain:** Workforce Management Scheduling — Schedule Quality & Reporting
**Researched:** 2026-05-07
**Confidence:** HIGH (based on direct codebase inspection + Timefold 1.x official docs)

---

## Existing Architecture (v1.0 baseline)

### Component Map

```
Frontend (React SPA)
    ↕ REST /api/v1/...
Spring Boot 3.4 / Java 21 (ECS Fargate 2vCPU/4GB)
  ├── Controllers (REST)
  ├── Services
  │     ├── SolverService          ← orchestrates Timefold, pre-solve validation
  │     ├── ScheduleService        ← accept/reject, list, snapshot persistence
  │     ├── ScheduleOutputService  ← derives staffing summary, agent schedule,
  │     │                             preference report, constraint violations
  │     ├── ScheduleExportService  ← Excel (Apache POI), 4 tabs
  │     ├── BambooRefreshService   ← BambooHR sync (agents + days off)
  │     ├── DeskAssignmentUploadService ← spreadsheet-based agent-desk assignment
  │     └── FteUploadService       ← staffing requirement Excel upload
  ├── Solver (Timefold 1.16.0)
  │     ├── ScheduleConstraintProvider  ← 18 constraints (HardSoft)
  │     ├── InMemoryScheduleStore       ← running/completed schedules in JVM heap
  │     └── solverConfig.xml           ← CH + simulated annealing local search
  └── Integration
        └── BambooHRClient (DelegatingBambooHRClient → Http or Mock)

RDS PostgreSQL 16
  ├── desk, agent, specialization
  ├── timeslot, staffing_requirement (live + snapshots)
  ├── agent_assignment (snapshots only — solver works in memory)
  ├── agent_day_off, agent_preference, agent_exception, agent_day_config
  ├── schedule, accepted_schedule_date
  └── constraint_weights
```

### Key Architectural Invariants

1. **Solver runs entirely in JVM heap** — `InMemoryScheduleStore` holds the live `Schedule` object. No DB interaction during solve. Only committed on `acceptSchedule`.
2. **Snapshots on accept** — `ScheduleService.acceptSchedule()` snapshots timeslots, staffing requirements, and agent assignments into DB, all remapped to new UUIDs with `schedule_id` set.
3. **SolutionManager already wired** — `ScheduleOutputService` holds a `SolutionManager<Schedule, HardSoftScore>` constructed from the Spring `SolverFactory` bean. `explain()` is called on the live schedule for constraint violation reporting.
4. **ConstraintWeights configurable per desk** — `constraint_weights` table drives `penalizeConfigurable()` in all 18 constraints. New constraints must add columns there.
5. **Multi-tenant scoping** — every entity has `tenant_id`, set via `TenantFilter` → `TenantContext`. All queries include `tenant_id`.
6. **Apache POI already on classpath** — `poi-ooxml:5.3.0` used in `ScheduleExportService` and `FteUploadService`. No new dependency needed for improved Excel export.
7. **No PDF library present** — `iText` or `Apache PDFBox` would need adding as a new dependency; PDF export is an additive change only.

---

## v1.1 Feature Integration Map

### Feature 1: Coverage Gap Visibility (per-timeslot demand vs supply)

**Integration point:** `ScheduleOutputService.buildStaffingSummary()` already computes demand vs actual at the (date, specialization) level. Per-timeslot granularity needs a new output method.

**New component:** `ScheduleOutputService.buildCoverageGaps(Schedule)` — loops `timeslotDemandConfigs` against assigned counts.

**DB changes:** None. Data is already in the in-memory `Schedule` (via `timeslotDemandConfigs` and `assignments`). For accepted schedules, the snapshot `staffing_requirement` and `agent_assignment` rows provide the same data.

**New endpoint:** `GET /api/v1/desks/{deskId}/schedules/{id}/coverage` — returns per-timeslot coverage rows. Alternatively, extend the existing `GET /{id}` response body with a `coverageGaps` field to avoid a second round trip.

**React:** New coverage grid view in the schedule detail page — rows = timeslots, columns = date/specialization, colour-coded (red = gap, green = covered, amber = overstaffed).

**Confidence:** HIGH — all needed data is in-memory during solve and in DB snapshots after accept.

---

### Feature 2: Shift Balance / Fairness Constraints

**Integration point:** `ScheduleConstraintProvider.defineConstraints()` — add new constraint methods, register them in the return array.

**New constraints needed:**
- `fairShiftStartDistribution` — penalise when one agent's start time distribution across the week differs significantly from others. Groups by `(agentId, dayOfWeek)`, computes start-time spread, penalises variance.
- `consistentWeeklyHours` — across days within the solve period, penalise when an agent works more than N hours more than another agent with the same contracted hours. Groups by `agentId`, counts total assignment slots.

**DB changes:** `constraint_weights` table needs new columns for each new constraint weight. Add as a new Flyway migration (V25 or next available).

**Critical constraint design rule:** All new constraints must be CH-friendly. Pattern: use `forEachIncludingUnassigned` if the constraint must fire during construction, or add an assignment-count guard like existing `exactlyOneBreak` does.

**ECS memory consideration:** Each new constraint adds scoring overhead proportional to problem size. At 2vCPU/4GB, the solver already runs 18 constraints over ~1000+ `AgentAssignment` entities. New fairness constraints that group-by-agent are O(N) not O(N²) — safe. Avoid O(N²) pair-join patterns.

**Confidence:** HIGH — standard Timefold constraint stream pattern, existing code shows the exact idiom.

---

### Feature 3: Solver Speed / Quality Tuning

**Integration point:** `solverConfig.xml` and `SolverService.startSolve()`.

**Specific changes:**

a) **Score logging** — add `<scoreDirectorFactory><initializingScoreTrend>ONLY_DOWN_OR_0</initializingScoreTrend></scoreDirectorFactory>` to `solverConfig.xml`. This lets Timefold skip score calculation when it detects monotonically improving solutions, reducing CH time.

b) **Move selector tuning** — the current local search uses simulated annealing with default move selectors. For a scheduling problem, adding an explicit `<swapMoveSelector>` (swap two agents between timeslots) alongside the implicit `changeMoveSelector` often finds better solutions faster. Add to `solverConfig.xml` inside `<localSearch>`.

c) **Unimproved termination ratio** — already implemented: `SolverService` sets `unimprovedSpentLimit` to 30% of solve time (min 30s). This is correct; expose it as a configurable parameter in `SolveRequest` if operators need control.

d) **Construction heuristic strategy** — the current `<constructionHeuristic/>` uses FIRST_FIT_DECREASING by default. For this problem, `WEAKEST_FIT_DECREASING` (assign hardest-to-fill seats first) may improve CH quality. Test empirically.

**DB changes:** None.

**ECS memory consideration:** Solver runs in-process. Simulated annealing starting temperature `0hard/3000soft` is already set. Monitor heap usage if adding more move selectors — swap moves double the entity-pair search space.

**Confidence:** MEDIUM — specific impacts of CH strategy change need empirical testing; Timefold docs confirm the options exist.

---

### Feature 4: Preference Satisfaction Tracking

**Integration point:** `ScheduleOutputService.buildPreferenceReport()` already computes this and returns a `PreferenceSummary` (totalPreferences, startTimeHonouredCount, breakTimeHonouredCount, overallHonouredPct). The data is already correct.

**Gap:** The `PreferenceSummary` is nested inside `ScheduleDetailResponse.preferenceReport.summary` but is not surfaced in the `ScheduleSummary` (list view). Operators cannot see preference satisfaction at a glance.

**Changes needed:**
- Add `preferenceSatisfactionPct: BigDecimal` field to `ScheduleSummary` DTO.
- `ScheduleService.toSummary()` and `ScheduleController.toSummary()` need to compute this (requires loading preference report, which is currently only done in `getScheduleDetail`).
- Alternative (simpler): store `preference_satisfaction_pct` as a column on `schedule` and persist it at accept time. Then `listSchedules` returns it without recomputing.

**DB changes:** Add `preference_satisfaction_pct NUMERIC(5,2)` to `schedule` table (nullable, populated at accept). Flyway V25/V26.

**Confidence:** HIGH — computation already exists, only surfacing it is needed.

---

### Feature 5: Consistent Agent Hours (day-to-day and week-to-week)

**Integration point:** Same as Feature 2 — new constraint in `ScheduleConstraintProvider`.

**Constraint design:**
- Day-to-day: group `AgentAssignment` by `(agentId, date)`, count assigned slots, penalise when count differs from the agent's contracted slot count. NOTE: this is largely already handled by `contractedHoursOver`/`contractedHoursUnder`. The new angle is week-to-week: for multi-week solve periods, penalise when total weekly hours differ more than a threshold.
- Week-to-week: group by `(agentId, weekNumber)`, sum slot counts, penalise variance across weeks. Requires computing ISO week number from `timeslot.date` inside the constraint.

**DB changes:** New `constraint_weights` columns for new constraint(s). Flyway migration.

**Confidence:** HIGH — data available, standard Timefold groupBy + sum pattern.

---

### Feature 6: Coverage Report (new API + React views)

**Integration point:** Extension of the existing `ScheduleDetailResponse`. The coverage report is a richer version of `buildStaffingSummary()` with per-timeslot granularity.

**New endpoint option A (preferred):** Extend `GET /api/v1/desks/{deskId}/schedules/{id}` response to include `coverageReport` field — avoids second round trip, consistent with how other views work.

**New endpoint option B:** `GET /api/v1/desks/{deskId}/schedules/{id}/coverage` — separate resource, easier to cache independently.

**New service method:** `ScheduleOutputService.buildCoverageReport(Schedule)` returns list of `CoverageReportEntry(date, time, specialization, demandFTEs, assignedCount, gapCount, coveragePct)`.

**DB changes:** None — derived from existing snapshot data.

**React:** Timeline grid or table with colour-coded coverage status per timeslot.

**Confidence:** HIGH.

---

### Feature 7: Agent Utilization Report (new API + React views)

**Integration point:** New output method in `ScheduleOutputService`.

**New service method:** `ScheduleOutputService.buildAgentUtilizationReport(Schedule)` — groups assignments by `agentId`, computes:
- `totalAssignedHours` (slots × incrementHours)
- `contractedHours` (from `AgentDayConfig` effective hours sum)
- `utilizationPct` = assigned / contracted × 100
- `overtimeHours` = max(0, assigned − contracted)
- `undertimeHours` = max(0, contracted − assigned)

For accepted schedules, `AgentDayConfig` is not persisted — need to recompute from `Agent.contractedHoursPerDay` and `agent_exception` records loaded separately.

**DB changes:** None required. If utilization is needed in the list view, store `avg_utilization_pct` on `schedule` (nullable, populated at accept) — same pattern as Feature 4.

**New endpoint:** Extend `ScheduleDetailResponse` with `agentUtilization` field, or `GET /{id}/utilization`.

**Confidence:** HIGH.

---

### Feature 8: Schedule Export Improvements (Excel / PDF)

**Integration point:** `ScheduleExportService.exportToExcel()` — already 4-tab Excel export using Apache POI.

**Excel improvements (no new dependencies):**
- Add 5th tab: "Coverage Report" tab from Feature 6 data.
- Add 6th tab: "Agent Utilization" tab from Feature 7 data.
- Add cell colour coding (green/amber/red for coverage %) — POI `CellStyle.setFillForegroundColor()` with `IndexedColors`.
- Add chart sheet (POI `XSSFChart`) — optional, higher effort.

**PDF export (new dependency required):**
- Add `org.apache.pdfbox:pdfbox:3.x` to `build.gradle` (Apache License, ~4MB).
- Alternatively `com.itextpdf:itext-core:8.x` (AGPL or commercial — check license).
- Recommendation: Apache PDFBox for open-source compatibility, simpler API for tabular data.
- New `SchedulePdfExportService` — separate service, injected into `ScheduleController` alongside `ScheduleExportService`.
- New endpoint: `GET /{id}/export/pdf` — or add `?format=pdf` query param to existing `/export`.

**DB changes:** None.

**Confidence:** HIGH for Excel improvements (zero new dependencies). MEDIUM for PDF (dependency choice needs team decision on licensing).

---

### Feature 9: Solver Score Breakdown (ScoreExplanation API)

**Integration point:** `ScheduleOutputService` already calls `solutionManager.explain(schedule)` in `buildConstraintViolations()`. The `ScoreExplanation` API is already in use.

**Gap:** The current implementation only surfaces *violating* constraints (score != ZERO). For a score breakdown, operators also want to see the score contribution of satisfied soft constraints — e.g., "preference satisfaction added +500 soft points".

**Changes needed:**
- Extend `ConstraintViolationEntry` DTO (or create new `ConstraintScoreEntry` DTO) to include zero-score constraints with their configured weight, so operators see all 18 constraints and their contribution.
- Alternatively, add a `GET /{id}/score-breakdown` endpoint that calls `solutionManager.analyze(schedule)` (returns `ScoreAnalysis` with `summarize()`) — this is the newer API in Timefold 1.x that replaces `explain()` for analysis purposes.
- `ScoreAnalysis.summarize()` returns a human-readable string — useful for debug endpoint but not structured enough for React rendering. Use `scoreAnalysis.getConstraintAnalyses()` for structured per-constraint data.

**Accepted schedule limitation:** `solutionManager.explain()` / `solutionManager.analyze()` require the full `Schedule` problem facts (agents, timeslots, constraints). For accepted schedules loaded from DB, `ScheduleService.loadSnapshotData()` loads timeslots, assignments, staffing requirements, and constraint weights — but NOT `agentDayConfigs`, `timeslotDemandConfigs`, or specializations. These are needed for bulk allocation constraints to compute correctly.

**Fix for accepted schedules:** `loadSnapshotData()` must also load/recompute `AgentDayConfig` and `TimeslotDemandConfig` problem facts before calling `explain()`. This is doable — same computation as in `SolverService.startSolve()`.

**DB changes:** None.

**Confidence:** HIGH — Timefold 1.x `SolutionManager` API confirmed in docs; `analyze()` is current (not deprecated).

---

### Feature 10: PTO Sync Diagnostic / Fix

**Integration point:** `BambooRefreshService.persistRefreshData()` and `DelegatingBambooHRClient`.

**Current behaviour:** The refresh logs individual agent updates at DEBUG level (`log.info` for soft-deletes). If BambooHR returns an employee with time-off records but the agent isn't in `refreshedAgentIds` (because their BambooHR ID doesn't match any existing desk agent), the PTO is silently dropped.

**Diagnostic endpoint:** `GET /api/v1/desks/{deskId}/bamboohr/diagnostic` — returns:
- Last refresh timestamp (needs adding to `Desk` or a separate `BambooRefreshLog` entity).
- Count of employees in last BambooHR response.
- Count of agents matched/updated.
- Count of agents skipped (bamboohrId not matched).
- Count of PTO entries imported vs skipped.
- List of agent names where PTO import failed with reason.

**New component:** `BambooRefreshLog` entity (or transient DTO if not persisted) + `BambooRefreshSummary` DTO.

**DB changes (if persisted):** New table `bamboo_refresh_log(id, tenant_id, desk_id, refreshed_at, employees_fetched, agents_matched, agents_skipped, pto_imported, pto_skipped, details JSONB)`. Flyway migration.

**Simplest approach (no DB change):** Return the summary as a response body from the existing `POST /api/v1/desks/{deskId}/agents/refresh` endpoint instead of `204 No Content`. `BambooRefreshService.refreshDeskAgents()` currently returns void — change return type to `BambooRefreshSummary`.

**Logging fix:** The silent PTO-skipping for agents with unmatched BambooHR IDs should be elevated to WARN level with agent name and bamboohrId for diagnosability.

**Confidence:** HIGH — purely additive to existing service, no solver interaction.

---

### Feature 11: Agent Desk Bulk Upload (already partially exists)

**Integration point:** `DeskAssignmentUploadService` already exists and handles bulk upload from Excel. `DeskAgentController` presumably exposes it.

**Current state:** The service parses columns: bamboohrId, name, email, deskName, specialty1..N. It clears the desk before re-importing, matches agents via BambooHR cache, creates or updates agents, assigns to desk with specializations.

**Gap from milestone context:** The feature asks for bulk upload of *just agent-desk assignments* (alongside the existing manual UI). The service already does this. The likely remaining work is:
- UI: React file upload component in the Agent/Desk management page.
- Error display: show `DeskAssignmentUploadResult.skippedDetails` to the operator.
- Template download: `GET /api/v1/desks/{deskId}/agents/upload/template` that returns a pre-filled Excel template.

**DB changes:** None — the entity model already supports bulk assignment.

**Confidence:** HIGH — backend already implemented.

---

## New vs Modified Components

| Component | Status | Change Type |
|-----------|--------|-------------|
| `ScheduleConstraintProvider` | Modify | Add 2-3 new soft constraints for fairness/consistency |
| `ScheduleOutputService` | Modify | Add `buildCoverageGaps()`, `buildCoverageReport()`, `buildAgentUtilizationReport()` |
| `ScheduleOutputService.buildConstraintViolations()` | Modify | Extend to include zero-score constraints for full score breakdown |
| `ScheduleExportService` | Modify | Add coverage + utilization tabs; cell colour coding |
| `ScheduleDetailResponse` DTO | Modify | Add `coverageReport`, `agentUtilization`, `scoreBreakdown` fields |
| `ScheduleSummary` DTO | Modify | Add `preferenceSatisfactionPct` field |
| `ScheduleService.loadSnapshotData()` | Modify | Add AgentDayConfig + TimeslotDemandConfig recomputation for accepted schedules |
| `BambooRefreshService` | Modify | Return `BambooRefreshSummary`; elevate silent skips to WARN |
| `SchedulePdfExportService` | New | PDF export (if PDF chosen) |
| `ScheduleController` | Modify | Wire new report endpoints + PDF export endpoint |
| `solverConfig.xml` | Modify | Add swapMoveSelector, tune CH strategy, add initializingScoreTrend |
| `constraint_weights` table | Modify | Add columns for new fairness constraints |
| `schedule` table | Modify | Add `preference_satisfaction_pct`, `feasible_at` already exists |
| Flyway migrations | New | V25..V27 for schema changes |

---

## DB Schema Changes Required

### Flyway V25: New constraint weight columns for fairness constraints

```sql
ALTER TABLE constraint_weights
    ADD COLUMN fair_shift_start_weight_hard_score INT NOT NULL DEFAULT 0,
    ADD COLUMN fair_shift_start_weight_soft_score INT NOT NULL DEFAULT 1,
    ADD COLUMN consistent_weekly_hours_weight_hard_score INT NOT NULL DEFAULT 0,
    ADD COLUMN consistent_weekly_hours_weight_soft_score INT NOT NULL DEFAULT 1;
```

### Flyway V26: Preference satisfaction and utilization on schedule

```sql
ALTER TABLE schedule
    ADD COLUMN preference_satisfaction_pct NUMERIC(5,2),
    ADD COLUMN avg_utilization_pct NUMERIC(5,2);
```

### Flyway V27 (optional): BambooHR refresh log

```sql
CREATE TABLE bamboo_refresh_log (
    id              UUID PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    desk_id         UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    refreshed_at    TIMESTAMPTZ NOT NULL,
    employees_fetched INT,
    agents_matched  INT,
    agents_skipped  INT,
    pto_imported    INT,
    pto_skipped     INT,
    details         JSONB
);
CREATE INDEX idx_bamboo_refresh_log ON bamboo_refresh_log(tenant_id, desk_id, refreshed_at DESC);
```

---

## Data Flow for New Reports

### Coverage Report Flow

```
ScheduleController.getCoverageReport(deskId, scheduleId)
  → ScheduleService.getScheduleDetail() [existing, loads schedule + snapshot data]
    → ScheduleOutputService.buildCoverageReport(schedule)
         loops schedule.timeslotDemandConfigs (demand per timeslot)
         cross-joins schedule.assignments filtered to non-null agent (supply per timeslot)
         emits CoverageReportEntry per (date, time, specialization)
  → CoverageReportResponse (list of entries, summary stats)
```

For accepted schedules: `loadSnapshotData()` loads `staffing_requirement` (demand) and `agent_assignment` (supply) from DB. The `timeslotDemandConfigs` list is not persisted — needs recomputation from the staffing requirements, same logic as `SolverService.computeTimeslotDemandConfigs()`. Extract that method to a shared utility or `ScheduleOutputService`.

### Agent Utilization Flow

```
ScheduleController.getAgentUtilization(deskId, scheduleId)
  → ScheduleService.getScheduleDetail()
    → ScheduleOutputService.buildAgentUtilizationReport(schedule)
         for each agent: contractedSlots from AgentDayConfig (or recompute from Agent)
                         assignedSlots from count of non-null assignments per agent
                         delta = assigned − contracted
  → AgentUtilizationResponse
```

---

## Recommended Build Order

Build order minimises integration risk by establishing data foundations before UI, and solver changes before report changes that depend on them.

### Phase 1: Solver Constraints (self-contained, no UI needed)
**What:** Add fairness/consistency constraints to `ScheduleConstraintProvider`. Update `constraint_weights` columns (Flyway V25).
**Why first:** Constraints are independently testable via Timefold's `ConstraintVerifier` test framework (already used in test suite). No frontend dependency.
**Risk:** Medium — new constraints affect score, can degrade existing solve quality if not carefully weighted. Test with existing `FullScale150AgentTest`.

### Phase 2: Score Breakdown + Solver Tuning
**What:** Extend `buildConstraintViolations()` to full score breakdown. Tune `solverConfig.xml`. Optionally add `GET /{id}/score-breakdown` endpoint.
**Why second:** Makes solver constraint changes from Phase 1 visible and debuggable. Solver tuning is pure config change.
**Risk:** Low — additive changes to existing SolutionManager usage.

### Phase 3: Coverage Report + Agent Utilization (Backend)
**What:** `buildCoverageReport()`, `buildAgentUtilizationReport()` in `ScheduleOutputService`. Fix `loadSnapshotData()` for accepted schedules. New endpoints. Flyway V26.
**Why third:** Foundational data APIs that frontend phases will consume. `loadSnapshotData()` fix is needed for all subsequent report features to work on accepted schedules.
**Risk:** Low — purely additive; does not touch the solver.

### Phase 4: Export Improvements
**What:** Add coverage + utilization tabs to Excel export. Cell colour coding. PDF export if desired.
**Why fourth:** Depends on Phase 3 having the data methods. Apache POI already on classpath.
**Risk:** Low (Excel). Medium (PDF — adds dependency, new service).

### Phase 5: PTO Sync Diagnostic
**What:** Return `BambooRefreshSummary` from refresh endpoint. Elevate warning logs. Optionally add `bamboo_refresh_log` table.
**Why fifth:** Independent of solver and reports. Prioritised based on pain rather than technical dependency.
**Risk:** Low — BambooHR integration is already working; this adds observability only.

### Phase 6: Preference Satisfaction in List View
**What:** Persist `preference_satisfaction_pct` to `schedule` at accept time. Add to `ScheduleSummary` DTO.
**Why last:** Requires Phase 3 data methods to be stable (preference report computation). Small change but touches the accept flow.
**Risk:** Low.

---

## Integration Risks and Mitigations

### Risk 1: New constraints breaking existing solve quality
**Cause:** Adding fairness soft constraints adds score surface area. If weights are too high, they can override coverage constraints; if too low, they have no effect.
**Mitigation:** Start all new constraints at weight 0 in the default `constraint_weights` migration. Use the `ConstraintWeightsController` (already exists) to tune per-desk. Validate with `FullScale150AgentTest`.

### Risk 2: `loadSnapshotData()` missing problem facts for score explanation
**Cause:** `buildConstraintViolations()` calls `solutionManager.explain(schedule)` which needs all problem facts. Accepted schedules from DB lack `AgentDayConfig` and `TimeslotDemandConfig`.
**Mitigation:** Phase 3 adds recomputation of these facts from snapshot data. Guard `buildConstraintViolations()` with a null-check on `agentDayConfigs` for backward compatibility.

### Risk 3: ECS memory pressure from larger solve state
**Cause:** New constraints + more problem facts in `Schedule` object.
**Mitigation:** 2vCPU/4GB is adequate for current problem sizes (150 agents tested). New constraints are O(N) not O(N²). Monitor via CloudWatch memory metric after deploying Phase 1.

### Risk 4: Flyway migration ordering on ECS restart
**Cause:** ECS deploys trigger migration on startup. Multiple new migrations must not leave the schema in a broken state if the deploy fails mid-way.
**Mitigation:** All v1.1 migrations are additive (ADD COLUMN, CREATE TABLE, CREATE INDEX). No DROP or RENAME. Safe to leave partial if needed.

### Risk 5: BambooHR refresh diagnostic requiring `refreshedAgentIds` to be surfaced
**Cause:** `BambooRefreshService.persistRefreshData()` is private and runs in a `TransactionTemplate`. The summary data (skipped agents, PTO counts) is computed inside the transaction but not returned.
**Mitigation:** Change `persistRefreshData()` to return a `BambooRefreshSummary` record, propagate up through `refreshDeskAgents()`. This is a local refactor with no external dependencies.

---

## Phase-Specific Research Flags

| Feature | Research Needed? | Reason |
|---------|-----------------|--------|
| Fairness constraints | Yes (Phase 1) | Specific algorithm for fairness scoring (variance vs range vs max-min) needs empirical validation against real schedules |
| Solver tuning | Yes (Phase 2) | CH strategy change (WEAKEST_FIT_DECREASING) needs empirical comparison |
| PDF export | Yes (Phase 4) | PDFBox vs iText licence and API ergonomics decision needed |
| Score explanation for accepted schedules | No | Fix is clear: add AgentDayConfig recomputation to loadSnapshotData |
| PTO diagnostic | No | Implementation is clear |
| Coverage/utilization reports | No | All data is available; implementation is straightforward |

---

## Sources

- Direct codebase inspection: `/Users/pete/IdeaProjects/wfm-service/src/**` (HIGH confidence)
- Timefold Solver 1.x official docs — SolutionManager, ScoreAnalysis, ConstraintMatchTotal, IndictmentMap: https://docs.timefold.ai/timefold-solver/1.x/constraints-and-score/understanding-the-score (HIGH confidence, verified via Context7)
- Timefold Solver 1.x official docs — solver configuration, local search, CH strategies: https://docs.timefold.ai/timefold-solver/1.x/optimization-algorithms/overview (HIGH confidence, verified via Context7)
- Apache POI 5.3.0 (already on classpath; API confirmed by existing `ScheduleExportService` usage)
