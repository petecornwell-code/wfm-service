# Domain Pitfalls: WFM Service v1.1

**Domain:** Timefold-based WFM scheduling tool — adding schedule quality + reporting to existing system
**Researched:** 2026-05-07
**Confidence:** HIGH (constraint/solver pitfalls verified against Timefold official docs; BambooHR pitfalls verified against official API reference; POI and JPA pitfalls verified against multiple primary sources)

---

## Critical Pitfalls

Mistakes that cause rewrites, data loss, or infeasible solves.

---

### Pitfall 1: Constraint Weight Hierarchy Corruption

**What goes wrong:** Adding fairness / shift-balance constraints with the wrong score level causes them to dominate or be dominated by existing hard constraints. For example: setting a soft fairness constraint with a weight of `softScore=5000` while the contracted-hours-under hard constraint fires with `hardScore=100` per slot creates no issue for the score level hierarchy — but flipping the levels (making fairness hard) can make previously feasible schedules infeasible overnight. Any configuration error that sets a hard weight on a constraint that is inherently soft creates an infeasible problem: the solver will never reach `0 hard` because perfect fairness is mathematically impossible when agent counts are unequal.

**Why it happens:** The `ConstraintWeights` model uses `HardSoftScore` on every constraint, so both `hardScore` and `softScore` fields are always present on every `@ConstraintWeight`. It is easy to accidentally pass `hardScore=1, softScore=0` to a fairness constraint through the REST API when the UI sends the wrong shape.

**Consequences:** Solver runs for the full time limit and returns a solution with a non-zero hard score, which the UI then shows as "infeasible". Operators cannot accept the schedule. Because hard and soft scores are stored separately per desk, retracing which weight was changed is hard without an audit log.

**Prevention:**
- Before merging any fairness/balance constraint, run a brief solve (5–10 seconds) with `FULL_ASSERT` mode enabled in test (`solver.environmentMode=FULL_ASSERT`) and assert `schedule.getScore().hardScore() == 0` in at least one test case.
- Add validation in `ConstraintWeightsService.updateWeights` to reject requests that set a non-zero `hardScore` on the soft-only constraints (preference, fairness, balance).
- Log the old and new weight values together when a weight is updated.
- Phase: **Shift balance / fairness constraints** and **Solver tuning** phases.

**Detection:** Solver log shows "best score … [-X hard / …]" — a negative hard score after a solve that previously returned `[0 hard]` is the clearest warning sign.

---

### Pitfall 2: Fairness Constraint Score Trap (Integer Rounding)

**What goes wrong:** Implementing shift-balance / consistent-hours constraints with integer arithmetic creates a score trap: multiple different solutions yield the same score even when one is clearly fairer. The solver cannot distinguish between them and makes no progress improving fairness after a certain point.

**Why it happens:** Timefold's `loadBalance` / `unfairness` metric is a rational number. Rounding it to the nearest integer loses the sub-integer differences that allow the solver to hill-climb toward fairer solutions. This is confirmed by official Timefold docs: "Rounding the unfairness value to the nearest integer would lose precision, causing a score trap."

**Specific risk in this codebase:** If a "consistent agent hours" constraint is implemented by grouping agent assignments per week and penalising deviations as integers (`Math.abs(actual - target)`), any two agents with `actual - target = 2` and `actual - target = 3` cost the same penalty, so the solver has zero incentive to fix the worse case first.

**Consequences:** Fairness metrics plateau quickly. Solver wastes remaining time limit on neutral moves.

**Prevention:**
- Use `penalize` with a **quadratic** penalty: `(actual - target)²` rather than `Math.abs(actual - target)`. Quadratic penalties are score-trap-free because larger deviations cost disproportionately more, giving the solver a gradient to follow.
- If using Timefold's built-in `loadBalance` collector (available in constraint streams), use it directly — it handles the precision issue internally.
- Do NOT multiply unfairness by a large integer "to preserve precision" and then use `HardSoftScore` — this breaks the relative weight of every other constraint.
- Phase: **Shift balance / fairness constraints**.

---

### Pitfall 3: Score Explanation Called on Accepted (DB) Schedules

**What goes wrong:** `SolutionManager.explain()` is called on a `Schedule` object reconstructed from the database. It throws or returns empty results because the problem facts (e.g. `TimeslotDemandConfig`, `AgentDayConfig`, `ScheduleConfig`) are not present — they only exist in the in-memory solved instance.

**Why it happens:** This is already partially guarded in `ScheduleOutputService.buildConstraintViolations` by checking `schedule.getConstraintWeights() == null`. However, the check is fragile: if `constraintWeights` is non-null even for a reconstructed schedule (possible if the repository joins fetch it), the method will call `explain()` and fail or return misleading results.

**Consequences:** The "Solver score breakdown" feature silently shows empty data, or throws an unhandled exception that surfaces as a 500 to the UI.

**Prevention:**
- Store `ScheduleStatus` and only call `explain()` when `status == RUNNING || status == SOLVED` and the schedule is retrieved from `InMemoryScheduleStore`, not the DB.
- Add a separate flag to `Schedule` (e.g. `boolean hasFullProblemFacts`) or rely exclusively on `inMemoryStore.get(scheduleId).isPresent()` before calling any `SolutionManager` methods.
- Phase: **Solver score breakdown**.

---

### Pitfall 4: `solutionManager.explain()` Hangs or Is Slow on Large Schedules

**What goes wrong:** `solutionManager.explain(schedule)` triggers a full score recalculation and produces `ConstraintMatch` objects for every violation. For a 100-agent, 5-day, 15-minute-increment schedule (~2,000 `AgentAssignment` entities), the constraint match set can contain tens of thousands of objects. Iterating over `total.getConstraintMatchSet()` to build `ViolationDetail` objects blocks the request thread for seconds.

**Why it happens:** The current `buildConstraintViolations` iterates the full `constraintMatchSet` per constraint, building a `ViolationDetail` for every match. At scale this is O(violations × constraints). Timefold docs explicitly warn: use `FETCH_MATCH_COUNT` instead of `FETCH_ALL` when you do not need full justification details.

**Consequences:** The schedule detail API endpoint times out for large desks. The ECS task (2 vCPU/4 GB) may OOM if the match set is very large.

**Prevention:**
- Use `ScoreAnalysis` with `ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT` for the summary counts shown in the UI (number of violations per constraint).
- Only call `explain()` with full match detail for a "drill-down" endpoint called explicitly by the operator, not on every schedule detail page load.
- Cap the number of `ViolationDetail` objects returned per constraint (e.g. first 50) and add a `"and N more..."` indicator.
- Phase: **Solver score breakdown**.

---

### Pitfall 5: New Constraints Break Existing Accepted Schedules' Score Display

**What goes wrong:** Adding a new constraint (e.g. "shift balance" or "consistent hours week-over-week") and exposing the score breakdown for a previously-accepted schedule causes the solver to re-explain the old solution with the new constraint — which will show violations that did not exist when the schedule was generated.

**Why it happens:** The `ScheduleOutputService.buildConstraintViolations` uses the live `SolutionManager` (which uses the current `ConstraintProvider`). Any accepted schedule stored in-memory before the new constraint was deployed will now appear to violate constraints it was never scored against.

**Consequences:** Operators see unexpected "violations" on already-accepted schedules. Trust in the tool erodes.

**Prevention:**
- Score breakdown should only be exposed for the **currently solving or just-solved schedule**, not for historical accepted schedules.
- If historical breakdown is needed, store the score string (`schedule.getScore().toString()`) at acceptance time and display it as-is, without re-explaining.
- Phase: **Solver score breakdown**, **Shift balance / fairness constraints**.

---

### Pitfall 6: BambooHR 503 Rate Limiting Misidentified as Server Error

**What goes wrong:** BambooHR returns `503 Service Unavailable` when rate-limited — not `429 Too Many Requests`. The current `HttpBambooHRClient` uses Spring `RestClient` with no retry logic and no `Retry-After` header inspection. A rapid sequence of requests (e.g. bulk upload calling `ensureCachePopulatedForUpload` followed by an immediate manual sync) will hit the rate limit; the `RestClient` will throw a `WebClientResponseException` with status 503, which will surface as a `RuntimeException` with message "Failed to parse BambooHR custom report response" — completely unhiding the root cause.

**Why it happens:** The current code wraps all HTTP errors in a generic `RuntimeException`. There is no differentiation between "server down", "rate limited", or "auth failure" at the exception level.

**Consequences:** PTO sync diagnostic feature shows a misleading error. Operators assume BambooHR is down and retry immediately, making the situation worse.

**Prevention:**
- Inspect the HTTP status code before wrapping in a `RuntimeException`. For 503, wrap in a `BambooHRRateLimitException` with a message telling the operator to retry in 60 seconds.
- Add a minimum delay between the cache-populate call and any subsequent call in `DeskAssignmentUploadService`.
- Phase: **PTO sync diagnostic**.

---

### Pitfall 7: BambooHR PTO Sync Misses "Requested" Status vs "Approved" Only

**What goes wrong:** The current `listTimeOff` correctly fetches both `approved` and `requested` statuses. However, the PTO sync diagnostic must clearly distinguish between the two: an agent whose PTO is `requested` (not yet approved) should NOT block shift assignment, but reporting it as "synced" gives false assurance.

**Why it happens:** The `BambooTimeOff` record carries a `status` field but the downstream `AgentDayOff` import (in `BambooRefreshService`) may not propagate it. If both statuses are imported as `AgentDayOff` with no status distinction, agents with pending PTO will be treated the same as agents with approved PTO.

**Consequences:** Agent is excluded from schedule for days where PTO was requested but not yet approved. Schedule is unnecessarily constrained.

**Prevention:**
- Only import `approved` status PTO as hard `AgentDayOff` records. Optionally import `requested` as a soft indicator.
- Surface the status in the PTO diagnostic UI so operators can see "3 approved, 1 pending" for an agent.
- Phase: **PTO sync diagnostic**.

---

## Moderate Pitfalls

---

### Pitfall 8: `autoSizeColumn` Causes Excessive Memory + CPU for Large Schedules

**What goes wrong:** `ScheduleExportService.autoSizeColumns()` is called on every sheet. For the "Agent Schedule" tab with 100 agents × 5 days × 15-minute increments, this can produce thousands of rows. `Sheet.autoSizeColumn()` in Apache POI scans every cell in the column using a font metrics calculation — it is O(rows × columns) and triggers significant GC pressure.

**Why it happens:** `autoSizeColumn` is convenient for small exports but is documented in POI as a performance hazard for large sheets. The export currently runs for every schedule load (not batched).

**Consequences:** Export endpoint times out for large desks. ECS task CPU spikes to 100% during export. Concurrent exports can OOM the 4 GB container.

**Prevention:**
- Set explicit column widths for known columns (e.g. `sheet.setColumnWidth(0, 6000)`) instead of `autoSizeColumn`. The column content is known — agent names, dates, times — so static widths are safe.
- If autosizing is needed, apply it only to the first 100 rows as a sample.
- Phase: **Schedule export improvements**.

---

### Pitfall 9: Excel Date Cells Read as Numeric in Desk Assignment Upload

**What goes wrong:** `getCellString(Cell)` in `DeskAssignmentUploadService` handles `NUMERIC` cells correctly for IDs (`(long) cell.getNumericCellValue()`) but the BambooHR ID column can sometimes contain values like `12345.0` from Excel if the cell is formatted as "General" in a spreadsheet that was copy-pasted. The current code truncates to `long`, which is correct — but only if the value is actually an integer. A value like `1234567890.5` (which Excel can produce from scientific notation formatting) will silently truncate, resulting in a wrong BambooHR ID and a "not found in cache" error.

**Why it happens:** Excel stores all numbers as `double`. The `(long)` cast is a floor operation, not a round operation.

**Prevention:**
- Use `Math.round(cell.getNumericCellValue())` instead of `(long)` cast for ID columns, and validate that the rounded value equals the original within 0.001.
- Alternatively, force users to format BambooHR ID columns as Text in the template.
- Add a validation step that logs a warning if `abs(numericValue - Math.round(numericValue)) > 0.001`.
- Phase: **Agent desk bulk upload**.

---

### Pitfall 10: Workbook Not Closed on Parse Exception in FteUploadService

**What goes wrong:** `FteUploadService.uploadFtes` opens an `XSSFWorkbook` inside a try-with-resources block — this is correct. However, if the `MultipartFile.getInputStream()` call throws (e.g. due to corrupted file or upload truncation), the exception propagates before the workbook is opened, leaving no resource to close. But inside the try block, if parsing fails mid-sheet and throws a runtime exception, any internal POI resources (zip streams for OOXML) may not be fully released until GC. With concurrent uploads on a 4 GB container, this creates heap pressure.

**Why it happens:** XSSFWorkbook holds the entire OOXML zip in memory via Apache POI's PackagePart infrastructure. Exception paths during parsing leave partial PackagePart objects on the heap.

**Prevention:**
- Add `spring.servlet.multipart.max-file-size=10MB` and `max-request-size=10MB` in application properties (currently unconfigured = defaults to 1MB, which may already reject legitimate FTE files with many timeslots).
- Validate the file is a valid ZIP/OOXML before calling `new XSSFWorkbook(...)` using `file.getOriginalFilename().endsWith(".xlsx")` and `file.getSize() > 0`.
- Phase: **Agent desk bulk upload**, **Schedule export improvements** (same upload infrastructure).

---

### Pitfall 11: Coverage Gap Calculation Conflates "No Demand" With "Unmet Demand"

**What goes wrong:** A coverage gap report that shows a timeslot as "covered" when there is no demand for it (demand=0, assigned=0) and also shows it as "covered" when demand=5 and assigned=5 is correct. But if the demand data is absent for a timeslot (no `StaffingRequirement` row), the report query will produce a NULL or zero for demand and conclude "fully covered" — masking a data-loading bug.

**Why it happens:** The `buildStaffingSummary` method in `ScheduleOutputService` iterates `schedule.getStaffingRequirements()`. If FTE upload created requirements for some timeslots but not others (e.g. partial upload, or timeslots added after the upload), those missing-demand timeslots appear covered.

**Prevention:**
- In the coverage report, explicitly mark timeslots where `demand IS NULL` as "No data" rather than "0% gap".
- Cross-check total demand hours against total assignment hours in the summary — a large discrepancy is a signal of missing demand data.
- Phase: **Coverage gap visibility**, **Coverage report**.

---

### Pitfall 12: N+1 Queries for Coverage / Utilization Reports

**What goes wrong:** Generating a coverage report or agent utilization report by loading `AgentAssignment` entities with lazy-loaded `Timeslot` and `Agent` associations via JPA will trigger N+1 queries at scale. The existing `findWithRelationsByTenantIdAndDeskIdAndScheduleId` in `AgentAssignmentRepository` uses `JOIN FETCH` and is correct for single-schedule loads. But if reporting aggregates across multiple schedules (e.g. week-over-week comparison), each schedule invokes a separate JOIN FETCH query, and the outer loop fires N queries for N schedules.

**Why it happens:** JPA repositories do not aggregate across entities by default. Reporting queries that group by date/agent across multiple weeks require custom JPQL or native SQL with GROUP BY.

**Prevention:**
- Write a dedicated `@Query` in `AgentAssignmentRepository` for each report type (coverage gap, utilization) that returns a DTO projection using `GROUP BY`, rather than loading full entities and aggregating in Java.
- Avoid multiple JOIN FETCH on the same query for different collections (Agent's secondary specializations + Timeslot) — this causes a Cartesian explosion. Fetch one collection per query and merge in Java, or use `@EntityGraph` with subgraph.
- Phase: **Coverage report**, **Agent utilization report**.

---

### Pitfall 13: Constraint Weight Mismatch After New Constraint Added to ConstraintProvider

**What goes wrong:** Adding a new constraint to `ScheduleConstraintProvider.defineConstraints()` (e.g. "Shift balance") with a corresponding `@ConstraintWeight("Shift balance")` on `ConstraintWeights` requires a Liquibase / Flyway migration to add the new column to the `constraint_weights` table. If the migration is missing, the column is absent, the JPA entity mapping fails, and the application will not start.

Even if the migration is present, existing rows in `constraint_weights` will have NULL for the new column. The default weight defined in the Java field initializer (`HardSoftScore.ofSoft(1)`) will be used at runtime — but if the initializer is not set or is set differently than desired in production, the new constraint will silently have the wrong weight.

**Prevention:**
- Always include a Liquibase migration that adds the column with an explicit `DEFAULT` value matching the Java field initializer weight.
- Write an integration test that loads the `ConstraintWeights` for a desk after every migration and asserts all weights are non-null.
- Phase: **Shift balance / fairness constraints**.

---

### Pitfall 14: BambooHR Subdomain Parsing Fails for Non-Standard Hostnames

**What goes wrong:** `HttpBambooHRClient.getSubdomain()` splits on `.` and takes `[0]` to extract the subdomain from a full hostname like `acme.bamboohr.com`. If the operator enters just the subdomain `acme`, this works. If they enter `https://acme.bamboohr.com/` (with protocol and trailing slash), `split("\\.")` on the full value returns `["https://acme", "bamboohr", "com"]` and `[0]` = `"https://acme"` — not the subdomain.

**Why it happens:** The code checks `server.contains(".")` but not whether the string starts with `https://` or has a trailing slash.

**Consequences:** All BambooHR API calls return 404. PTO sync fails silently with a misleading error.

**Prevention:**
- Normalise the server value: strip `https://`, strip `http://`, strip trailing `/`, then apply the split.
- Add a test for the cases: `"acme"`, `"acme.bamboohr.com"`, `"https://acme.bamboohr.com/"`.
- Phase: **PTO sync diagnostic**.

---

### Pitfall 15: Solver Termination Timer Resets After Construction Heuristic

**What goes wrong:** Setting `solver.time-limit=PT2M` on the `SolverManager` applies the 2-minute limit to the entire solve including the construction heuristic. On large problems (150+ agents), the construction heuristic itself takes 20–40 seconds. When the timer resets at the start of Local Search (Timefold's documented behaviour: "the termination is disabled during construction heuristics and restarts when local search begins"), the effective wall-clock time can be 2 min + CH-time, not 2 minutes. This surprises operators who set a tight limit.

**Why it happens:** The timer is configured via `SolverConfigOverride` with a `TerminationConfig` at the Solver level. Per Timefold docs, solver-level termination that relies on unimproved time does not count construction heuristic phases.

**Prevention:**
- When exposing "solver time limit" in the UI, label it "Local Search time limit (construction happens before this)". Add a UI tooltip explaining that the total runtime may be higher.
- Alternatively, configure a separate construction heuristic phase termination (e.g. `PT30S` hard cap on CH) in `solver-config.xml`.
- Phase: **Solver speed / quality tuning**.

---

### Pitfall 16: `breakClustering` and `bulkUnderallocationSoft` Are No-Op Stubs

**What goes wrong:** Two constraints in `ScheduleConstraintProvider` — `breakClustering` and `bulkUnderallocationSoft` — both call `.penalizeConfigurable(a -> 0)`, meaning they always penalise by 0 and are completely non-functional. They appear in the score breakdown as constraints with `score=0`, which confuses operators reading the breakdown in the v1.1 UI.

**Why it happens:** The stubs were intentionally deferred. The weight entries for them exist in `ConstraintWeights` (`breakClusteringWeight = HardSoftScore.ofSoft(2)` and `bulkUnderallocationSoftWeight = HardSoftScore.ofSoft(1)`), so they will show up in the breakdown with their configured weights but zero violations — misleading.

**Prevention:**
- Either implement the constraints properly before exposing the score breakdown UI, or mark them explicitly as "Inactive" in the breakdown response so operators understand they are not being evaluated.
- Add a comment on both stubs to flag them as deferred work items.
- Phase: **Solver score breakdown** (must surface these as disabled), **Shift balance / fairness constraints** (when breakClustering is implemented).

---

## Minor Pitfalls

---

### Pitfall 17: Excel Export Dates Written as Strings, Not Date Cells

**What goes wrong:** `ScheduleExportService` writes all date and time values as strings (`e.date().toString()`, `ad.startTime().toString()`). Excel cannot sort or filter these columns as dates/times because they are plain text. Operators who try to sort by date in Excel will get lexicographic order (which may work for ISO dates but fails for `HH:mm` times like "9:00" sorting after "10:00").

**Prevention:**
- Create a `CellStyle` with a date format (`workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd")`) and use `cell.setCellValue(localDate)` for date cells. This stores them as proper Excel date serials.
- Reuse one date style and one time style (max 65,000 styles per workbook) rather than creating a new style per cell.
- Phase: **Schedule export improvements**.

---

### Pitfall 18: `FORMULA` Cell Type in Uploaded Spreadsheets Returns BLANK via getCellString

**What goes wrong:** `DeskAssignmentUploadService.getCellString` and `FteUploadService` handle `STRING`, `NUMERIC`, `BOOLEAN`, and `default` (maps to null). A cell with type `FORMULA` falls into the `default` branch and returns null. Excel files saved from Google Sheets or LibreOffice frequently use `FORMULA` cells for values that display as plain text.

**Prevention:**
- Add `case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf((long) cell.getNumericCellValue());` to the switch in both services. Alternatively, call `evaluator.evaluateInCell(cell)` to resolve formulas before reading.
- Phase: **Agent desk bulk upload**.

---

### Pitfall 19: BambooHR Time-Off Response `dates` Field Shape Is Not Stable

**What goes wrong:** `HttpBambooHRClient.fetchTimeOffByStatus` already handles both object (`dates` as a map) and array shapes for the `dates` field — which is good. However, the fallback path (when `dates` is neither object nor array) iterates `start` to `end` date range, which can include weekends and holidays as PTO days. For an agent with a 2-week PTO block, this generates 14 `AgentDayOff` records including weekend days, which is usually correct but may block the agent's weekend slots unnecessarily if the desk runs on weekends.

**Prevention:**
- The fallback date-range expansion should intersect with actual desk operating days (the days for which `Timeslot` records exist). This prevents importing PTO for days the agent was never going to be scheduled anyway.
- Phase: **PTO sync diagnostic**.

---

### Pitfall 20: `InMemoryScheduleStore` Leaks Solved Schedules Across ECS Task Restarts

**What goes wrong:** `InMemoryScheduleStore` holds solved `Schedule` objects in a `ConcurrentHashMap`. When an ECS task is restarted (e.g. during a new deployment), the in-memory store is lost. Any UI polling for a running solve will receive a 404 from the new task. Any score breakdown request for a "solved" schedule will fail because the schedule is no longer in memory.

**Why it happens:** This is a known architectural constraint. However, operators may not realise that a running solve is lost during deployment.

**Prevention:**
- Detect ECS task replacement gracefully: if a `GET /schedule/{id}` returns 404 on the in-memory store but the DB has an accepted schedule, return that instead of 404.
- For v1.1, document this limitation clearly and add a pre-deployment "stop all running solves" step to the deployment runbook.
- Phase: **Solver score breakdown** (relies on in-memory store).

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Coverage gap visibility | Conflating "no demand data" with "0% gap" (Pitfall 11) | Distinguish NULL demand from 0 demand in reporting queries |
| Shift balance / fairness constraints | Score trap from integer rounding (Pitfall 2) | Use quadratic penalty, not absolute deviation |
| Shift balance / fairness constraints | Wrong score level on new constraint (Pitfall 1) | Validate hardScore=0 for soft constraints via test |
| Shift balance / fairness constraints | Missing Liquibase migration for new weight column (Pitfall 13) | Include migration with DEFAULT in same PR as new constraint |
| Solver speed / quality tuning | CH timer reset confusion (Pitfall 15) | Label limit correctly in UI, consider explicit CH termination |
| Solver score breakdown | explain() on DB schedules (Pitfall 3) | Guard on inMemoryStore.get().isPresent() |
| Solver score breakdown | explain() slow at scale (Pitfall 4) | Use FETCH_MATCH_COUNT for summary; cap ViolationDetail list |
| Solver score breakdown | Stub constraints confusing operators (Pitfall 16) | Mark breakClustering/bulkUnderallocationSoft as "Inactive" |
| Schedule export improvements | autoSizeColumn performance (Pitfall 8) | Use fixed column widths |
| Schedule export improvements | Dates as strings, not date cells (Pitfall 17) | Use Excel date cell type with proper format style |
| PTO sync diagnostic | 503 misidentified as server error (Pitfall 6) | Detect 503, surface as rate-limit message |
| PTO sync diagnostic | "Requested" PTO creating hard day-off blocks (Pitfall 7) | Only import "approved" status as hard AgentDayOff |
| PTO sync diagnostic | Subdomain parsing failure (Pitfall 14) | Normalise server field before split |
| PTO sync diagnostic | Weekend PTO days blocking never-scheduled slots (Pitfall 19) | Intersect date range with desk operating days |
| Agent desk bulk upload | Formula cells returning null (Pitfall 18) | Handle FORMULA cell type in getCellString |
| Agent desk bulk upload | Numeric BambooHR ID truncation (Pitfall 9) | Use Math.round, not (long) cast |
| Coverage report | N+1 queries for aggregation (Pitfall 12) | Use DTO projection queries with GROUP BY |
| Agent utilization report | N+1 queries for aggregation (Pitfall 12) | Use DTO projection queries with GROUP BY |

---

## Sources

- [Timefold: Understanding the Score](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/understanding-the-score) — score explanation, FETCH_MATCH_COUNT, ScoreAnalysis
- [Timefold: Load Balancing and Fairness](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/load-balancing-and-fairness) — score trap from rounding, BigDecimal requirement
- [Timefold: Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance) — cross-product pitfalls, filter-before-join
- [Timefold: Constraint Configuration](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/constraint-configuration) — ConstraintWeightOverrides, string key matching, re-solve requirement
- [BambooHR API Technical Overview](https://documentation.bamboohr.com/docs/api-details) — 503 rate limiting, 400-field limit, Accept header requirement
- [Apache POI: Spreadsheet](https://poi.apache.org/components/spreadsheet/) — cell type handling, FORMULA cells, autoSizeColumn performance
- [Baeldung: N+1 Problem in Spring Data JPA](https://www.baeldung.com/spring-hibernate-n1-problem) — JOIN FETCH vs Cartesian explosion
- [OpenSpaceServices: PostgreSQL View Optimization](https://www.openspaceservices.com/blog/postgre-sql-view-optimization-diagnosing-and-fixing-slow-aggregation-views-in-production) — pre-aggregation pattern, CTE vs sequential join
