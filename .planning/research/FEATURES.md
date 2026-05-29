# Feature Landscape: WFM Service v1.1

**Domain:** Workforce management / constraint-based schedule optimization for contact center / BPO desks
**Researched:** 2026-05-07
**Milestone context:** Subsequent milestone — quality and reporting layer on top of shipped v1.0

---

## What Already Exists (Do Not Re-research)

The v1.0 baseline is fully shipped. These are not features to build:

- Desk management, specialization config, contracted hours
- BambooHR agent sync + PTO import (approved + requested statuses)
- FTE upload from Excel (flexible sheet names, flexible column headers)
- Agent preferences (start time, break time) and one-off exceptions
- Timefold solver: hard constraints (day-off, specialization match, one-per-slot,
  break shape, contracted hours, bulk allocation limits) plus soft constraints
  (prefer primary spec, honour preferred start time, honour preferred break time)
- Schedule output: accept/reject flow
- Excel export: Overview, Staffing Summary, Agent Schedule, Preference Report tabs
  (Apache POI XSSFWorkbook — already built in ScheduleExportService)
- ConstraintWeights: DB-backed per-desk configurable weights (already wired to solver)

---

## Table Stakes

Features users expect in any WFM reporting tool. Absence makes the product feel incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Coverage gap visibility — timeslot-level demand vs. assigned | Every WFM tool shows this; without it operators can't see where the schedule is thin | Low-Medium | Data already exists: TimeslotDemandConfig has totalDemandFTEs, AgentAssignment has count per timeslot. Gap = demand - assigned. Pure computation, no new model needed. |
| Agent utilization report — hours per agent, days worked | Standard in all WFM; needed to spot over/under-utilization | Low | Already computable from AgentAssignment rows grouped by agent. Contracted hours already in DB. |
| Overtime risk flag | Expected: any agent at or near contracted-hours ceiling should be flagged | Low | Contractual hours vs. scheduled hours per week. Same data source as utilization. |
| Coverage report — per-timeslot demand vs. actual coverage | Table-stakes output: operators share this with clients | Low-Medium | Essentially the coverage gap view rendered as a report/export. |
| Preference satisfaction tracking — overall % and per-agent | Users want to know if the solver respected preferences | Low | Preference report already exported to Excel in v1.0. What's missing is the summary metric surfaced in the UI after solve completes (honoured count / total preferences). |
| PTO sync diagnostic — which agents' PTO was/wasn't imported | Without this, operators discover PTO gaps only after publishing schedules | Medium | BambooHR API already called; gap is in error surfacing and per-agent visibility. |
| Agent desk bulk upload via spreadsheet | Expected by any ops team managing >10 agents; manual UI doesn't scale | Low-Medium | DeskAssignmentUploadService already exists in v1.0. Needs frontend UI integration and error feedback. |

---

## Differentiators

Features that distinguish this tool from "just a smarter spreadsheet."

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Solver score breakdown / constraint explanation | Operators understand WHY a schedule looks the way it does — builds trust in the optimizer | Medium | Timefold ScoreAnalysis API (v1.4+) exposes constraintMap() with match-level justifications; JSON-serializable; send over REST. UI renders constraint name → violation count → score impact. |
| Shift balance / fairness constraints | Prevents the solver from always favoring the same agents for good/bad shifts; a real fairness signal | Medium | Two constraint types needed: "Balance time worked" (sum of slots per agent) and "Balance shift count" (per-day assignments). Modeled as soft constraint using variance or sum-of-deviations from mean. Already have contracted hours constraints as a template. |
| Consistent agent hours day-to-day and week-to-week | Agents want predictability; reduces complaints and churn | Medium | Requires a new soft constraint penalizing variance in daily slot count across the week. Needs per-agent weekly context to be passed into ScheduleConfig or a new planning fact. |
| Solver speed/quality tuning — configurable termination | Operators can trade solve time for quality; e.g. quick 30s preview vs. thorough 10min solve | Low | Timefold supports: time limit, score threshold ("terminate at 0hard/*soft"), diminished-returns (ratio-based). SolverService already accepts timeLimit from SolveRequest. Expose these options in the UI. |
| Coverage heatmap visualization | Color-coded timeslot grid (green/yellow/red) communicates coverage status at a glance — far faster than a table | Medium | React component: timeslot on X axis, desk/date on Y, cell color = covered/gap/over. Data: coverage gap API endpoint needed. |

---

## Anti-Features

Features to explicitly NOT build in v1.1.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Real-time / intraday schedule adherence tracking | Requires live agent status feeds; entirely different integration layer; out of scope for a planning tool | Stick to planning-time coverage; note as future milestone |
| Shift bidding / agent self-service schedule picks | Valuable but requires agent-facing UI, notifications, approval workflows — a multi-sprint feature | Defer; preferences + exceptions already capture agent input |
| Forecast / demand prediction (ML) | BPO context uses client-provided FTE data (already uploaded via Excel); building a forecasting engine is a separate product | Stick to FTE-upload-driven demand; do not build forecasting |
| Multi-channel / Erlang-C headcount calculator | ErlangXService already exists in code; surfacing it further risks scope creep into forecasting | Use existing Erlang support only if operators already use it |
| PDF export (native) | Complex to produce well; adds iText/OpenPDF dependency; Excel already covers the reporting use case | Keep Excel; mention "print to PDF" from Excel as the workaround |
| Constraint weight editor overhaul | ConstraintWeightsController already exists; only minor UX improvements needed | Don't rebuild the weights UI — just expose solver termination controls alongside it |
| Authentication / authorization | Already scoped out for v1.1 (internal use) | Do not add |

---

## Feature Dependencies

```
Coverage gap visibility
  └─ requires: TimeslotDemandConfig + AgentAssignment counts (already in DB)
  └─ feeds:    Coverage report, Coverage heatmap

Coverage report
  └─ requires: Coverage gap visibility (same computation, different rendering)
  └─ feeds:    Excel export improvements (new tab)

Agent utilization report
  └─ requires: AgentAssignment grouped by agent + contracted hours (both in DB)
  └─ feeds:    Excel export improvements (new tab)

Preference satisfaction tracking
  └─ requires: AgentPreference + actual assignments (both in DB, already in Preference Report tab)
  └─ feeds:    Excel export (summary already written; needs UI surfacing)

Solver score breakdown
  └─ requires: Timefold SolutionManager.analyze() API (available in current Timefold version)
  └─ requires: ConstraintJustification objects on each constraint (need to be added to ScheduleConstraintProvider)
  └─ feeds:    UI constraint explanation panel

Shift balance / fairness constraints
  └─ requires: ScheduleConstraintProvider (add new soft constraints)
  └─ requires: ConstraintWeights entry for new constraints (auto-added by ConstraintWeightsService)
  └─ blocks:   Nothing; purely additive to solver

Consistent agent hours constraint
  └─ requires: Per-agent weekly context (may need new planning fact or extend ScheduleConfig)
  └─ requires: ScheduleConstraintProvider (add new soft constraint)
  └─ depends on: Contracted hours constraints (pattern already established)

Solver speed/quality tuning
  └─ requires: SolveRequest DTO extension + SolverService TerminationConfig (minimal)
  └─ feeds:    Nothing; purely additive

Coverage heatmap visualization
  └─ requires: Coverage gap visibility API endpoint
  └─ requires: React frontend component

PTO sync diagnostic
  └─ requires: HttpBambooHRClient.listTimeOff() (already calls API)
  └─ requires: Surfacing which bamboohrId → Agent mappings failed or produced no results
  └─ note:     Current code fetches "approved" + "requested"; gap is likely in bamboohrId
               matching — agents synced from BambooHR must have matching bamboohrId in DB

Agent desk bulk upload
  └─ requires: DeskAssignmentUploadService (already exists)
  └─ requires: Frontend file upload UI + error display (missing)
```

---

## Complexity Deep-Dives

### Coverage Gap Visibility and Coverage Report (Low-Medium)

The computation is trivial: for each timeslot, `gap = demand_FTEs - assigned_agent_count`. Both values are already in the DB. The work is:
1. A new `GET /schedules/{id}/coverage` API endpoint that returns per-timeslot demand, assigned count, and gap.
2. A React table component showing the data, color-coded (green = met, yellow = slight gap, red = significant gap).
3. A new "Coverage" tab in the Excel export (one row per timeslot).

Industry pattern: timeslot on rows, columns = date, plus demand/assigned/gap/coverage-pct. Color coding uses conditional formatting in Excel or CSS classes in React.

### Solver Score Breakdown (Medium)

Timefold's `SolutionManager.analyze(solution)` returns `ScoreAnalysis<HardSoftScore>` which is JSON-serializable. Each `ConstraintAnalysis` entry contains: constraint name, total score contribution, and a list of `MatchAnalysis` records each with a justification object.

**What's needed:**
1. Add `ConstraintJustification` implementations to each constraint in `ScheduleConstraintProvider` so the justification carries agent name + timeslot (not just opaque IDs). This is the bulk of the work.
2. A new `GET /schedules/{id}/score-analysis` endpoint that calls `SolutionManager.analyze()` on the in-memory schedule and returns the JSON breakdown.
3. A React accordion panel: "Hard constraints broken: N", expanding to list each constraint name, violation count, score impact, and the top 3-5 offending entities.

The `InMemoryScheduleStore` already holds the best solution — score analysis can run on it post-solve.

**Important:** `SolutionManager` is available in the open-source Timefold Solver (not Plus/Enterprise only for basic score analysis). The full match-level justification API requires the constraint streams approach already used in `ScheduleConstraintProvider`. Confidence: HIGH — verified against Timefold 1.4+ docs.

### Shift Balance / Fairness Constraints (Medium)

Two new soft constraints to add to `ScheduleConstraintProvider`:

1. **Balance time worked**: Group assignments by agent over the full solve period, compute total slots, penalize by the squared deviation from the mean (or simpler: penalize the sum of max - min). Use `penalizeConfigurable` with a new `ConstraintWeights` entry so the weight can be tuned without code changes.
2. **Balance shift count per day** (optional, lower priority): Penalize when one agent works every day while another works fewer days.

The existing `contractedHoursOver/Under` pattern is the right template. The fairness constraint needs a cross-agent aggregate — group all agents, collect total slots, compute deviation.

**Pitfall:** Cross-agent aggregation inside a constraint is expensive if done naively. Use `groupBy + toList + penalize` rather than `forEachUniquePair` to avoid O(N²) scaling. The existing `oneAssignmentPerTimeslot` constraint already demonstrates the groupBy pattern.

### Consistent Agent Hours (Medium)

Penalize when an agent's daily slot count varies by more than a threshold across the week. Requires:
- Grouping by `(agentId, date)` → slot count
- Then grouping by `agentId` → list of daily counts
- Penalizing standard deviation or max-minus-min above a threshold

This is a two-level groupBy which Timefold constraint streams support but it's non-trivial. Alternative simpler approach: penalize each day where the agent's slot count differs from their contracted-hours-implied slot count (i.e. strengthen the contracted hours constraint to apply uniformly).

The second approach (uniform contracted hours per day) is already largely handled by `contractedHoursOver/Under`. The actual gap may be week-to-week consistency, which is not expressible in a single-week solve — this suggests the feature is more about reporting (show agents whose hours vary week to week historically) than a solver constraint.

**Recommendation:** Implement as a reporting metric first (hours per agent per day vs. contracted), not a new constraint.

### PTO Sync Diagnostic (Medium)

Current code in `HttpBambooHRClient.listTimeOff()` fetches "approved" and "requested" statuses. The `BambooRefreshService` maps bamboohrId → Agent to create `AgentDayOff` records.

Likely failure modes:
1. Agent exists in BambooHR but has no matching `bamboohrId` in the WFM `Agent` table (import mismatch).
2. BambooHR time-off response uses a `dates` object with non-standard keys (already handled by fallback logic).
3. Time-off type filtering: current code accepts all types (no type filter applied). Some records may be importing correctly but not being respected by the solver if the type name doesn't match expected values.

**Diagnostic endpoint needed:** `GET /bamboohr/pto-sync-status?from=&to=` that returns:
- Total time-off records fetched from BambooHR
- Records matched to WFM agents (with agent name)
- Records unmatched (bamboohrId not found in DB) — this is the key diagnostic
- Records where date fell outside the sync window

This is a pure reporting feature, no solver changes.

### Agent Desk Bulk Upload (Low-Medium)

`DeskAssignmentUploadService` already exists. The gap is frontend: there is no file upload UI for desk assignment. What's needed:
1. A React file picker that POSTs to the existing upload endpoint.
2. Display of upload results: successes, skipped rows, errors (employee not found, desk not found, etc.).
3. A downloadable template file (CSV or Excel) that shows expected columns.

The service already processes Excel files with flexible column matching. The main design question is the column format: recommend `bamboohr_id` or `email` as the agent identifier (both are in the Agent table), plus `desk_name` and optional `primary_specialization`.

---

## MVP Recommendation for v1.1

**Must have (table stakes, low-medium complexity):**
1. Coverage gap visibility — timeslot-level demand vs. assigned (API + React table)
2. Coverage report — Excel export tab (extends existing ScheduleExportService)
3. Agent utilization report — hours per agent, overtime flag (API + Excel tab)
4. PTO sync diagnostic — unmatched bamboohrId surfaced in UI
5. Agent desk bulk upload — frontend for existing backend service

**Should have (differentiators, medium complexity):**
6. Solver score breakdown — constraint explanation panel in React
7. Shift balance / fairness constraints — two new soft constraints in ScheduleConstraintProvider
8. Solver speed/quality tuning — termination options in solve UI (time limit, score threshold)

**Nice to have (medium complexity, can defer to v1.2):**
9. Coverage heatmap visualization — color-coded timeslot grid
10. Preference satisfaction rate — surfaced as a metric in the schedule view (computation already done in export)
11. Consistent agent hours — report first, constraint later

**Defer:**
- Consistent agent hours as a solver constraint (complex cross-week aggregation, low ROI in a weekly scheduler)
- PDF export (print from Excel covers the need)

---

## UX Patterns

### Coverage Gap Visualization
**Pattern:** Timeslot grid, demand/assigned/gap columns, color-coded rows.
- Green: assigned >= demand
- Yellow: assigned = demand - 1 (within tolerance)
- Red: assigned < demand - tolerance

Industry standard: rows = timeslots (15-min or 30-min increments), columns = date + desk. Filterable by desk and date. A totals row shows coverage percentage per day.

**Simpler alternative for v1.1:** A table below the schedule view with columns: Time, Demand, Assigned, Gap, Coverage%. Color on the Gap column. No new component library needed.

### Solver Score Breakdown
**Pattern:** Summary bar + accordion detail.
- Top level: "Score: 0 hard violations, 432 soft points" with a traffic-light icon
- Accordion: one row per constraint that has violations
  - Constraint name | Violation count | Score impact
  - Expand: list of entities (agent name + date) responsible

Users do NOT want to see raw Timefold constraint IDs or Java class names. Surface human-readable names (already the `.asConstraint("...")` labels in `ScheduleConstraintProvider`).

### Agent Utilization Report
**Pattern:** Table with one row per agent.
Columns: Agent Name | Days Scheduled | Total Hours | Contracted Hours/Week | Delta | Overtime Risk (flag)

Overtime risk flag: simple threshold, e.g. scheduled > contracted + 5%. Color red. No complex calculation needed.

### PTO Sync Diagnostic
**Pattern:** Two-section panel on the BambooHR sync page.
- Section 1: "Successfully synced" — agent name, dates count
- Section 2: "Not synced / unmatched" — bamboohrId, reason (agent not found in WFM)

Actionable: clicking an unmatched record could link to the agent assignment UI.

---

## Sources

- [Timefold Score Analysis API](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/understanding-the-score) — HIGH confidence
- [Timefold Explainable Score (v1.4)](https://timefold.ai/blog/timefold-solver-1-4-brings-explainable-score) — HIGH confidence
- [Timefold Fairness Constraints](https://docs.timefold.ai/employee-shift-scheduling/latest/employee-resource-constraints/fairness/fairness) — HIGH confidence
- [Timefold Termination Configuration](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking) — HIGH confidence
- [BambooHR Time Off API](https://documentation.bamboohr.com/reference/time-off-get-time-off-requests) — HIGH confidence (status, date range filters confirmed)
- [WFM Coverage Visualization Patterns](https://www.myshyft.com/blog/schedule-visualization-tools/) — MEDIUM confidence (vendor blog, pattern is consistent with industry)
- [WFM Table Stakes vs Differentiators](https://cxfoundation.com/blog/contact-center-workforce-management-software-providers) — MEDIUM confidence
- [Preference Satisfaction Rate Metric](https://www.myshyft.com/blog/schedule-optimization-metrics/) — MEDIUM confidence (vendor source, but metric definition is industry-standard)
- Existing codebase: ScheduleConstraintProvider.java, ScheduleExportService.java, HttpBambooHRClient.java — HIGH confidence (ground truth)
