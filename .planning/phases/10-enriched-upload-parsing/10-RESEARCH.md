# Phase 10: Enriched Upload Parsing - Research

**Researched:** 2026-07-31
**Domain:** Apache POI multi-sheet spreadsheet parsing; recurring weekly day-off/PTO storage against an existing Timefold-solver-backed data model
**Confidence:** HIGH (all findings grounded in direct code reads of the files this phase modifies; no unverified library claims — no new external dependencies are introduced)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Workbook structure (UPL-01)**
- **D-01:** One worksheet per desk. The sheet name is the desk (authoritative); there is no per-row `Desk` column. The parser iterates every sheet (not just sheet 0 as today). Consistent with the existing FTE upload, which already parses by sheet name.
- **D-02:** A worksheet whose name matches no configured desk → skip the whole sheet and report it as a non-blocking entry in Upload Results (`Sheet "X": no matching desk — skipped`). Other valid sheets still import. Desks must be configured beforehand (upload assigns to existing desks only).

**Day-cell contract — the core model (UPL-03/04/05)**
- **D-03:** Seven day columns (`Monday`…`Sunday`), NOT the originally-planned three Mon–Sun groups (21 columns). Each cell holds exactly one of: a number `>= 0` (contracted hours), the keyword `MANDATORY` (mandatory day off), or `PTO` (recurring weekly PTO). Keywords are matched case-insensitively; exact tokens are `MANDATORY` and `PTO`.
- **D-04:** Every day cell is required — blank is never valid. A row with any blank day cell, or a cell value that is not a number/`MANDATORY`/`PTO`, is skipped with a specific reason (UPL-06).
- **D-05:** Solver semantics — `0`, `MANDATORY`, and `PTO` all mean "not working / not schedulable" that day (the agent is unavailable; no shift). A number `> 0` = worked that many hours. The three non-working states are descriptively distinct (part-time off day vs contractual day off vs recurring PTO) but the solver treats all three as a day off. Confirmed: a `0` day is NOT available as overflow.
  - `number > 0` → `agent_day_hours` row = hours (Phase 9 D-09).
  - `0` → `agent_day_hours` row = `0.00` (Phase 9 D-04 "exists and 0 = not worked").
  - `MANDATORY` → a MANDATORY day-off block (reuse the field-4517 mechanism; Phase 9 D-05).
  - `PTO` → recurring weekly PTO block — storage deferred to research (D-12).

**Specialization columns (UPL-02)**
- **D-06:** Numbered `Specialty N` columns (`Specialty 1`, `Specialty 2`, …, unbounded). Parser detects every header matching the `specialty {n}` prefix and reads all non-blank values. First non-blank = primary, rest = secondary (matches current `DeskAssignmentUploadService` lines 344–353). Backward-compatible with today's `Specialty 1`/`Specialty 2` sheets. (Rejected: a single delimited `Specializations` column — breaks compat, needs escaping.)

**Minimum valid row & identity (UPL-01/UPL-07)**
- **D-07:** Minimum valid row = BambooHR ID + all 7 day cells populated. Identity columns (first/last name, job title, email, department, active) and specialties are optional — sourced from BambooHR where blank.
- **D-08:** Match by BambooHR ID only (milestone decision — ID is always populated). A row whose BambooHR ID is not found in the BambooHR cache → rejected with reason "BambooHR ID not found" (UPL-07). This simplifies the current code's fuzzy name/email fallback matching (`DeskAssignmentUploadService` lines 249–293).

**Validation & Upload Results (UPL-06)**
- **D-09:** Whole-row skip-and-continue on the first validation failure in a row (matches current behaviour); other valid rows in the same sheet still import.
- **D-10:** Numeric hours accepted 0–24 (fractional allowed, e.g. `7.5`). A value > 24 is clamped to 24 and surfaced as a non-blocking warning (`Row 8 Tue: 32 → 24`) — clamp is not silent. Negative numbers and unrecognized words → skip row with reason.
- **D-11:** Upload Results view gains a per-sheet rollup (`Billing: 12 imported, 2 skipped`), keeps per-row skip reasons, and surfaces clamp warnings and skipped-sheet notices as non-blocking warnings. (Exact UI layout → planner / optional UI-phase.)

**Blank template generation**
- **D-13:** Blank-template download is IN SCOPE for Phase 10. Template + parser + export share one `EnrichedColumnLayout` (single source of truth for header order/names) so the shapes can never drift.
- **D-14:** The template is pre-seeded: each desk sheet arrives with the current roster's identity columns filled (BambooHR ID, first/last, job title, email, dept, active), leaving the 7 day cells + specialties blank for the operator.

**Backward-compat & shape retirement (UPL-08)**
- **D-15:** Phase 10 redefines the enriched shape. Both the 6-col legacy shape AND the old flat enriched shape (single sheet + `Desk` column) are rejected with a "download the new template" message. This revises UPL-08's literal "existing enriched sheets continue to import."

**Phase 10 / Phase 11 boundary (data safety)**
- **D-16:** Coexist / union with BambooHR-derived days off. Spreadsheet day cells do not clobber the MANDATORY blocks BambooHR derives from field 4517 — a day is off if either source says so. The sheet cannot un-block a BambooHR day off until the Phase 11 merge engine.

**Re-upload / removal semantics**
- **D-17:** Keep clear-then-reimport. Uploading a desk sheet clears the whole desk first (unassign agents, delete desk-scoped preferences/exceptions and per-day hours), then re-adds only the rows present. An agent on the desk but omitted from the sheet is removed from that desk.

### Claude's Discretion / Deferred to Research

- **D-12 (research — this document's primary deliverable):** Recurring-PTO storage mechanism — reuse `AgentDayOff` with a `PTO` type vs a dedicated recurring-PTO structure vs `AgentException`. Constraints locked: (a) must block the solver = day off (same effect as MANDATORY per D-05); (b) must be labelled PTO, not "mandatory", for reporting; (c) must not conflict with Phase 11's BambooHR dated-PTO precedence. **See `## Recurring PTO Storage Decision (D-12)` below — answered.**
- Exact `EnrichedColumnLayout` API shape, header casing/whitespace normalization, and how the parser tolerates a detected-but-incomplete sheet — **see `## Architecture Patterns` below.**

### Deferred Ideas (OUT OF SCOPE)

- BambooHR↔spreadsheet per-field precedence + merge report (MRG-01/02) — Phase 11. Phase 10 uses union/coexist (D-16); Phase 11 lets the sheet un-block a BambooHR day off and produces the merge report.
- Dated PTO vs recurring PTO reconciliation — Phase 11 (BambooHR dated PTO wins for covered dates; recurring pattern fills gaps). D-12's storage choice must not preclude this.
- Consolidating the two "recurring not-worked" mechanisms (per-day `0` vs MANDATORY `AgentDayOff`) into a single authority — Phase 11 merge work (carried from Phase 9 deferred).

None of these belong in Phase 10 — this phase parses the new shape and writes it (union rule), leaving true precedence to Phase 11.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UPL-01 | One workbook, one worksheet per desk (sheet name = desk); identity fields parsed; minimum row = BambooHR ID + 7 day cells | Multi-sheet iteration pattern (FTE upload precedent); `EnrichedColumnLayout` header contract; shape-rejection logic |
| UPL-02 | Unbounded `Specialty N` columns, first non-blank = primary | Existing `DeskAssignmentUploadService` primary/secondary assignment logic (lines 344–353) confirmed as the pattern to extend with an N-column header scan |
| UPL-03 | Numeric day cell `>= 0` = contracted hours, `0` = not worked | Phase 9 `agent_day_hours` contract confirmed still valid; `resolveEffectiveHours` code-read confirms `0` fully blocks solver with no extra plumbing |
| UPL-04 | `MANDATORY` day cell = mandatory day off | D-12 finding: recommend NOT re-materializing dated `AgentDayOff` rows in Phase 10; see storage decision below (flagged departure from literal D-05 wording, evidence-backed) |
| UPL-05 | `PTO` day cell = recurring weekly PTO | **D-12 answered below** — extend `agent_day_hours` with a `day_off_type` label column; do not reuse dated `AgentDayOff` |
| UPL-06 | Per-row/per-sheet validation, Upload Results rollup, clamp warnings, unmatched-sheet notices | `DeskAssignmentUploadResult` DTO extension pattern identified; frontend `ClientManagement.tsx` Upload Results modal identified as the exact UI touch point |
| UPL-07 | BambooHR-ID-only matching; unmatched → reject, no agent created | Confirmed by reading current fuzzy-match block (lines 249–293) that D-08 simplifies away |
| UPL-08 | Retire both legacy shapes; one-time re-download required | Current shape-detection code read in full (lines 104–137); rejection message pattern identified |
| UPL-09 | Pre-seeded per-desk template download, shared column layout | `DeskAgentExportService` / `ClientManagementController.exportEmployees` identified as the endpoint pattern to mirror for the new template-download endpoint |

</phase_requirements>

## Summary

Phase 10 replaces `DeskAssignmentUploadService`'s single-sheet, two-shape parser with a per-desk-sheet parser built around one shared column-layout definition (`EnrichedColumnLayout`), following the existing multi-sheet iteration precedent already used by `FteUploadService`. Three of the four "big" open questions (multi-sheet iteration, `Specialty N` detection, shape rejection) are direct, low-risk extensions of code patterns that already exist verbatim in this codebase — confirmed by reading the actual source, not inferred.

The fourth question — **D-12, recurring PTO storage** — required tracing the full solver consumption path (`SolverService.computeAgentDayConfigs` → `resolveEffectiveHours` → `AgentDayOffRepository`/`AgentDayHoursRepository`) rather than reasoning about the entities in isolation. That trace surfaced a critical, previously-undocumented fact: **`agent_day_hours` (the Phase 9 table) is queried by desk with no date-range restriction, and a `0.00` row already durably blocks the solver for every future occurrence of that weekday with zero extra code** — it is inherently the "recurring pattern" table Phase 9 already built, whereas `AgentDayOff` is *dated* and materialized only inside a rolling lookback/lookahead window (`BambooRefreshService`, default −12/+8 weeks) that gets **fully deleted and regenerated from BambooHR data only** every time a desk sync runs (`refreshDeskAgents` → `deleteByAgent_IdAndDateBetween` then reinsert). Writing spreadsheet-sourced recurring PTO (or MANDATORY) directly as dated `AgentDayOff` rows, as the literal wording of D-05/D-12 option (a) suggests, would be silently wiped the next time an operator clicks "Sync BambooHR" — and Phase 11 (MRG-01) will trigger that sync automatically on every future upload, making the data loss near-certain, not theoretical.

**Primary recommendation:** Store the day-cell's descriptive flavor (`WORKED` / `MANDATORY` / `PTO`) as a new nullable column on the existing `agent_day_hours` table (Flyway `V30`), not as dated `AgentDayOff` rows. The numeric `hours=0.00` already fully and durably blocks the solver via the existing, unmodified `resolveEffectiveHours` resolution chain; the new column is reporting/labelling metadata only, read by nothing solver-side. This requires zero changes to `SolverService`, `AgentDayOff`, `DayOffType`, or `BambooRefreshService`, cleanly satisfies all three D-12 constraints, and does not touch any Phase-11-owned reconciliation logic. See `## Recurring PTO Storage Decision (D-12)` for full reasoning, evidence, and the concrete schema/entity change.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Multi-sheet workbook parsing (Apache POI) | API / Backend | — | `DeskAssignmentUploadService` is a Spring `@Service`; parsing is server-side, mirrors `FteUploadService` |
| Column-layout definition (`EnrichedColumnLayout`) | API / Backend | — | Shared Java class consumed by parser, template generator, and export — all three are backend services |
| Day-cell → schedule-data resolution | API / Backend | Database / Storage | Parser writes `agent_day_hours` rows; solver reads them via `SolverService` (already-existing, unmodified in this phase) |
| Recurring day-off/PTO storage | Database / Storage | API / Backend | New column on `agent_day_hours` (Postgres); resolution logic already lives in `SolverService.resolveEffectiveHours` (untouched) |
| Template download / roster export | API / Backend | — | New endpoint mirrors `ClientManagementController.exportEmployees` (byte[] XLSX response) |
| Upload Results per-sheet rollup + warnings | API / Backend | Browser / Client | DTO extension is backend; rendering the extended modal is `frontend/src/pages/ClientManagement.tsx` (React) |
| BambooHR ID matching / agent cache | API / Backend | — | Unchanged — reuses existing `ClientManagementService.findCachedEmployee` / cache population |

## Recurring PTO Storage Decision (D-12)

### The three options as framed in CONTEXT.md, and why (a) is the wrong choice

**Option (a) — reuse `AgentDayOff` with `DayOffType.PTO`.** `AgentDayOff` is *dated* (`@Column LocalDate date`, unique on `(agent_id, date)`) and is materialized by `BambooRefreshService.refreshDeskAgents` only inside a rolling window (`lookbackWeeks`=12 default, `lookaheadWeeks`=8 default — `bamboohr.time-off.lookback-weeks` / `-lookahead-weeks`). That method's step 5 (`BambooRefreshService.java` lines ~251–257) does:
```java
for (UUID agentId : refreshedAgentIds) {
    agentDayOffRepository.deleteByAgent_IdAndDateBetween(agentId, from, to);
}
```
— an unconditional delete-then-regenerate of **every** `AgentDayOff` row in the window for any agent present in the BambooHR response, followed by regeneration **only** from `emp.customWorkingdays()` (field 4517) and `BambooTimeOff` entries. Any spreadsheet-sourced dated row written directly into this table would be silently deleted the next time this sync runs, with no path to regenerate it (the uploaded spreadsheet is not retained after parsing). Phase 11's MRG-01 requirement ("uploading triggers a fresh BambooHR sync before merging") turns this from a rare edge case into something that happens on effectively every future upload. `[VERIFIED: src/main/java/com/wfm/integration/BambooRefreshService.java lines 90–135, 251–333]`

**Option (c) — reuse `AgentException`.** `AgentException` is a per-date, per-desk override (`contracted_hours_override`, unique on `(tenant_id, desk_id, agent_id, date)`) — also dated, with the identical materialization problem as (a): a "recurring weekly" fact has no natural dated representation that survives indefinitely without a background job re-expanding it. It is also semantically wrong for a *pattern* (it exists to override one specific date, e.g. "worked an extra shift on this Tuesday"), so it does not carry the recurring PTO concept forward correctly either. `[VERIFIED: src/main/java/com/wfm/model/AgentException.java]`

**Option (b), refined — extend `agent_day_hours` (this phase's recommendation).** Tracing the actual solver read path confirms `agent_day_hours` is already the correct table for a durable, recurring, weekday-keyed fact:

```java
// SolverService.java line ~150 — no date range, desk-scoped only:
List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
...
// SolverService.java lines 837-849 — resolution chain per date:
static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap,
                                         Map<DayOfWeek, BigDecimal> dayHoursMap,
                                         LocalDate date, BigDecimal scheduleDefaultHours) {
    if (exceptionMap.containsKey(date)) return exceptionMap.get(date);      // AgentException — highest precedence
    DayOfWeek dow = date.getDayOfWeek();
    if (dayHoursMap.containsKey(dow)) return dayHoursMap.get(dow);         // agent_day_hours — per-weekday, recurring
    return scheduleDefaultHours;                                           // desk default fallback
}
// SolverService.java lines 501-504 (computeAgentDayConfigs) — the actual solver-blocking check:
if (dayOffSet.contains(d)) continue;                                       // field-4517 / dated AgentDayOff union (D-16)
BigDecimal effectiveHours = resolveEffectiveHours(exMap, dayHoursMap, d, schedule.getDefaultContractedHoursPerDay());
if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;  // 0 hours = no AgentDayConfig emitted = not schedulable
```
`[VERIFIED: src/main/java/com/wfm/service/SolverService.java lines 145-150, 494-521, 837-849]`

Because `agent_day_hours` is queried without any date-range restriction and resolved fresh per schedule-solve date, a `0.00` row for a weekday blocks that weekday **for every future schedule solved against this desk**, indefinitely, with no expiry, no background job, and no window to fall out of. This is exactly the durability property a "recurring weekly pattern" needs, and Phase 9 already built and wired it end-to-end.

**Recommendation:** Add a nullable label column to `agent_day_hours` (Flyway `V30`) that records *why* a `0.00`/any row exists, purely for reporting — it is never read by the solver:

```sql
-- V30__agent_day_hours_recurring_status.sql
ALTER TABLE agent_day_hours
    ADD COLUMN day_off_type VARCHAR(9);
-- NULL = a normal worked day (hours > 0) or a plain unlabelled 0 (no descriptive reason).
-- 'MANDATORY' or 'PTO' = the spreadsheet cell used that keyword (D-03); reuses the existing
-- DayOffType enum values so the label is consistent with AgentDayOff's vocabulary without
-- reusing AgentDayOff's dated materialization mechanism.
```

```java
// AgentDayHours.java — add alongside the existing `hours` field
@Enumerated(EnumType.STRING)
@Column(name = "day_off_type", length = 9)
private DayOffType dayOffType;   // null | MANDATORY | PTO — reuses com.wfm.model.DayOffType (already has both values)

public DayOffType getDayOffType() { return dayOffType; }
public void setDayOffType(DayOffType t) { this.dayOffType = t; }
```

Parser behavior per day cell (all three cases write exactly one `agent_day_hours` row per weekday, `hours` set per D-05, `dayOffType` set only for the two off-day keywords):

| Cell value | `hours` | `day_off_type` | Solver effect |
|---|---|---|---|
| `8` (or any `> 0`) | `8.00` | `null` | Worked, 8h |
| `0` | `0.00` | `null` | Not worked, no descriptive reason |
| `MANDATORY` | `0.00` | `MANDATORY` | Not worked, labelled |
| `PTO` | `0.00` | `PTO` | Not worked, labelled |

### Why this satisfies all three D-12 constraints

- **(a) Blocks the solver, same effect as MANDATORY:** `hours=0.00` already fully blocks via the unmodified `computeAgentDayConfigs` check — no new solver code needed, no risk of divergence between MANDATORY-effect and PTO-effect since both simply write `0.00`.
- **(b) Labelled PTO, not MANDATORY, for reporting:** the new `day_off_type` column is exactly that label; Upload Results (D-11) and any future "why is this agent unscheduled" surface can display it directly. Reuses the existing `DayOffType` enum, so the vocabulary (`MANDATORY`/`PTO`) is consistent app-wide even though it now appears on two different tables.
- **(c) Does not conflict with Phase 11's dated-PTO precedence:** Phase 10 makes **zero changes** to `AgentDayOff`, `DayOffType`, `BambooRefreshService`, or the `dayOffSet` union check. `AgentException` already sits above `agent_day_hours` in `resolveEffectiveHours`'s precedence chain (checked first, per-date) — this is precisely the seam Phase 11 needs to implement "BambooHR's dated PTO wins for the dates it covers, recurring pattern fills gaps": a per-date override (via `AgentException` or an equivalent Phase-11-introduced per-date table) can already win over a `agent_day_hours` weekday default without any Phase 10 rework. Phase 10's storage choice is additive-only and reversible.

### Recommended follow-up flag for the planner (D-05's literal wording)

D-05 ("MANDATORY → reuse the field-4517 mechanism") is a **locked** decision, but its literal reading ("write into `AgentDayOff`/`DayOffType.MANDATORY`") hits the **identical** `BambooRefreshService` window-wipe hazard documented above — a spreadsheet `MANDATORY` cell written as a dated `AgentDayOff` row would also be silently deleted on the next BambooHR sync, with no regeneration path. The evidence above (agent_day_hours already durably blocks with zero extra code) applies equally to `MANDATORY`. **This research recommends the planner apply the same `agent_day_hours.day_off_type=MANDATORY` mechanism to the `MANDATORY` cell too**, rather than writing dated `AgentDayOff` rows — this does not change D-05's *effect* (day is blocked, reported as MANDATORY) or its coexistence with field-4517-derived MANDATORY (D-16's union still holds: `dayOffSet.contains(d)` from field-4517 is checked independently and first), only the underlying storage mechanism. Flag this explicitly during planning/discuss-phase re-confirmation rather than silently overriding the locked wording — it is a storage-mechanism refinement, not a behavioral change. If the team prefers to honor D-05's literal wording despite the hazard, the plan MUST add a companion task that extends `BambooRefreshService`'s window-expansion step to also re-expand a durable pattern (which then requires the exact same `agent_day_hours`-based pattern storage as a source anyway) — i.e., the durable pattern column is required either way; the only open question is whether Phase 10 *additionally* materializes dated `AgentDayOff` rows for calendar-API display (`GET /agents/{id}/days-off` — see Open Questions).

## Standard Stack

No new external dependencies are introduced by this phase. All work extends libraries already present in `build.gradle`.

### Core (existing, reused)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.apache.poi:poi-ooxml` | 5.3.0 `[VERIFIED: build.gradle line 40]` | Multi-sheet `.xlsx` parsing and generation | Already used by `DeskAssignmentUploadService`, `FteUploadService`, `DeskAgentExportService`, `FteSpreadsheetGenerator` |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | (BOM-managed, no explicit version pin found in `build.gradle`) `[VERIFIED: build.gradle lines 31-32]` | Schema migration (`V30__...sql`) | Existing forward-only migration convention, latest applied is `V29` `[VERIFIED: ls src/main/resources/db/migration/]` |
| Spring Data JPA / Hibernate | (Spring Boot managed) | `agent_day_hours` entity/repository extension | Existing pattern, no new dependency |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 + AssertJ + Mockito (`spring-boot-starter-test`) | Spring Boot managed | Unit tests for parser/shape-rejection/clamp logic | Already the test stack for `DeskAssignmentUpload*Test` files |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extending `agent_day_hours` with a label column | A brand-new `agent_recurring_day_off` table (agent_id, day_of_week, type) | Functionally equivalent; rejected only because it duplicates the `(agent_id, day_of_week)` uniqueness Phase 9 already modeled, adding a join for no benefit — the label is 1:1 with the existing row |
| Reusing `DayOffType` enum on `agent_day_hours` | A new, narrower enum (`RecurringDayOffType { MANDATORY, PTO }`) | Reusing `DayOffType` keeps vocabulary consistent app-wide (same two string values already understood by `AgentDayOffResponse`, `BambooRefreshService`, and any future reporting code) at the cost of a column that also has an implicit third state (`null`) that doesn't exist on `AgentDayOff` (which is always MANDATORY or PTO, never "worked") — acceptable and worth documenting in the entity Javadoc |

**No installation step required** — no `npm install` / `pip install` equivalent; this is a Java/Gradle project with all needed libraries already declared.

## Package Legitimacy Audit

**Not applicable.** This phase introduces zero new external packages/dependencies. All work extends `org.apache.poi:poi-ooxml:5.3.0`, Flyway, and Spring Data JPA, all already present in `build.gradle` and already in production use elsewhere in this codebase. No `slopcheck`/registry verification is required.

## Architecture Patterns

### System Architecture Diagram

```
Operator (browser)
   |
   |  POST /api/v1/client-management/upload-desk-assignments (multipart .xlsx)
   v
ClientManagementController.uploadDeskAssignments()
   |
   v
DeskAssignmentUploadService.uploadDeskAssignments()
   |
   |-- 1. BambooHR cache pre-populate (clientManagementService.ensureCachePopulatedForUpload) [unchanged]
   |-- 2. Load all desks + specializations for tenant                                        [unchanged pattern]
   |-- 3. For each sheet in workbook (POI iteration, D-01):
   |         sheetName -> desk lookup (case-insensitive)
   |         no match?  -> SkippedSheet("no matching desk") notice (D-02), continue to next sheet
   |         match?     -> clearDesk(deskId)  [D-17, existing method, extended to also clear day_off_type]
   |                       for each data row:
   |                         resolve header->index via EnrichedColumnLayout (shared with template+export)
   |                         read identity cells (optional, D-07)
   |                         read BambooHR ID (required) -> BambooHR cache lookup (D-08)
   |                            not found -> skip row, reason "BambooHR ID not found"
   |                         read all "Specialty {n}" cells (D-06) -> first non-blank = primary
   |                         read all 7 day cells (Mon..Sun):
   |                            blank -> skip row (D-04)
   |                            not number/MANDATORY/PTO -> skip row (D-04)
   |                            negative number -> skip row (D-10)
   |                            number > 24 -> clamp to 24, record warning (D-10)
   |                            number in [0,24] -> agent_day_hours(hours=value, day_off_type=null)
   |                            MANDATORY -> agent_day_hours(hours=0.00, day_off_type=MANDATORY)
   |                            PTO -> agent_day_hours(hours=0.00, day_off_type=PTO)
   |                         upsert Agent (identity fields, desk assignment, specializations)
   |-- 4. Aggregate per-sheet rollup + row skip reasons + clamp warnings + skipped-sheet notices (D-11)
   v
DeskAssignmentUploadResult (extended DTO) -> JSON response
   |
   v
frontend/src/pages/ClientManagement.tsx — Upload Results modal (extended: per-sheet rollup + warnings)


Template download (D-13/D-14, new):
Operator (browser)
   |
   |  GET /api/v1/client-management/desk-assignments/template
   v
ClientManagementController.downloadDeskAssignmentTemplate()  [new, mirrors exportEmployees()]
   |
   v
DeskAssignmentTemplateService (new) — for each Desk:
   |   create sheet named after the desk
   |   write header row via EnrichedColumnLayout.headers()
   |   for each current roster agent on that desk: write identity columns (filled), day cells + specialties (blank)
   v
byte[] .xlsx -> attachment response


Solver (unchanged in this phase — confirmed no code changes required):
SolverService.solve()
   |-- agentDayHoursRepository.findByTenantIdAndDeskId() -> Map<agentId, Map<DayOfWeek, BigDecimal>>
   |-- resolveEffectiveHours(): AgentException > agent_day_hours[weekday] > schedule default
   |-- computeAgentDayConfigs(): hours <= 0 -> no AgentDayConfig emitted -> day not schedulable
```

### Recommended Project Structure

No new packages needed — this phase extends existing services in place:

```
src/main/java/com/wfm/
├── service/
│   ├── DeskAssignmentUploadService.java     # rewritten: multi-sheet, EnrichedColumnLayout-driven
│   ├── DeskAssignmentTemplateService.java   # NEW — pre-seeded template generation (D-13/D-14)
│   └── DeskAgentExportService.java          # extended to use EnrichedColumnLayout for round-trip symmetry
├── util/
│   └── EnrichedColumnLayout.java            # NEW — single source of header order/names (D-13)
├── model/
│   └── AgentDayHours.java                   # extended: + dayOffType (DayOffType, nullable)
├── dto/
│   ├── DeskAssignmentUploadResult (record)  # extended: + sheetSummaries, + warnings
│   └── SkippedSheet (record)                # NEW — "Sheet 'X': no matching desk — skipped"
└── resources/db/migration/
    └── V30__agent_day_hours_recurring_status.sql   # NEW
```

### Pattern 1: `EnrichedColumnLayout` — shared column-order/name definition

**What:** A small, dependency-free Java class that is the single source of truth for header text, header order, and header→purpose mapping. Consumed by the parser (header→index), the template generator (index→header), and `DeskAgentExportService` (export column order).

**When to use:** Any time three services must agree on the same spreadsheet shape — the exact "design tension" D-13 was written to resolve.

**Example shape (concrete proposal):**
```java
package com.wfm.util;

/** Single source of truth for the enriched per-desk upload/template/export column shape (D-13). */
public final class EnrichedColumnLayout {

    // Fixed identity columns, in header order.
    public static final String COL_BAMBOOHR_ID = "BambooHR ID";
    public static final String COL_FIRST_NAME  = "First Name";
    public static final String COL_LAST_NAME   = "Last Name";
    public static final String COL_JOB_TITLE   = "Job Title";
    public static final String COL_EMAIL       = "Email";
    public static final String COL_DEPARTMENT  = "Department";
    public static final String COL_ACTIVE      = "Active";

    // Fixed 7-column day group, in header order (D-03).
    public static final java.time.DayOfWeek[] DAY_ORDER = {
        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
        java.time.DayOfWeek.SUNDAY
    };

    /** Header cell text for a given weekday, e.g. MONDAY -> "Monday". */
    public static String dayHeader(java.time.DayOfWeek d) {
        String name = d.name(); // MONDAY
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /** Ordered fixed identity headers (before the day group). */
    public static java.util.List<String> identityHeaders() {
        return java.util.List.of(COL_BAMBOOHR_ID, COL_FIRST_NAME, COL_LAST_NAME,
                COL_JOB_TITLE, COL_EMAIL, COL_DEPARTMENT, COL_ACTIVE);
    }

    /** Matches "specialty {n}" (case-insensitive, whitespace-tolerant), e.g. "Specialty 1", "specialty  2". */
    private static final java.util.regex.Pattern SPECIALTY_HEADER =
        java.util.regex.Pattern.compile("^specialty\\s*(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    public static java.util.Optional<Integer> specialtyIndex(String headerLowerTrimmed) {
        var m = SPECIALTY_HEADER.matcher(headerLowerTrimmed);
        return m.matches() ? java.util.Optional.of(Integer.parseInt(m.group(1))) : java.util.Optional.empty();
    }

    /** Header text for the parser's normalize-then-lookup map: lowercase + trim, matching current cellAt() convention. */
    public static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase();
    }

    private EnrichedColumnLayout() {}
}
```

Parser, template generator, and export all call `EnrichedColumnLayout.identityHeaders()` / `dayHeader()` / `specialtyIndex()` — none of the three hardcode header strings independently, closing the drift risk D-13 exists to prevent.

### Pattern 2: Multi-sheet iteration (confirmed precedent — `FteUploadService`)

**What:** Iterate every sheet in the workbook by index, resolve `sheet.getSheetName()` against a pre-loaded lookup, skip (not throw) on no match.

**Source:** `src/main/java/com/wfm/service/FteUploadService.java` lines 82-90, 142-147 — this exact loop shape already exists and already implements "iterate every sheet, resolve name, skip-with-notice on no match" for the FTE upload (date-based instead of desk-name-based, but the mechanics are identical):
```java
// Source: FteUploadService.java (existing, verified pattern to mirror for D-01)
for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
    Sheet sheet = workbook.getSheetAt(s);
    String sheetName = sheet.getSheetName().trim();
    // Phase 10: resolve sheetName -> Desk via deskByName.get(sheetName.toLowerCase()) instead of parseSheetDate()
    // On no match: record a SkippedSheet notice (D-02) and `continue` — do not throw.
}
```

**Adaptation for Phase 10:** replace `parseSheetDate(sheetName)` with `deskByName.get(sheetName.trim().toLowerCase())`; on `null`, append to a `List<SkippedSheet>` (new DTO) instead of `skipped.add(String)`, then `continue`.

### Pattern 3: Shape detection / rejection (extends existing code, does not replace its structure)

**What:** `DeskAssignmentUploadService` lines 104-137 already implement header-based shape detection (`hasEnrichedMarkers` / `hasLegacyMarkers`) and throw `IllegalArgumentException` with the headers embedded in the message on no match. Phase 10 keeps this *shape* of logic but changes the outcome for legacy/old-enriched: both are **explicitly named and rejected** (D-15), not silently lumped into "unrecognised."

**Example (adaptation):**
```java
// Read header row of sheet 0 only, to classify the WHOLE workbook (D-15 rejects file-wide, not per-sheet —
// a legacy/old-enriched file has no per-desk sheets to iterate in the first place).
Set<String> headers = /* lowercase-trimmed header set of first sheet */;

boolean isLegacy6Col = headers.contains("desk assignment");
boolean isOldFlatEnriched = headers.contains("desk") && headers.contains("monday") && headers.contains("sunday");
        // ^ this is the OLD 16-col single-sheet+Desk-column shape (today's ENRICHED_16COL) — now retired too.
boolean isNewPerDeskShape = headers.contains("bamboohr id") && headers.contains("monday") && headers.contains("sunday")
        && !headers.contains("desk"); // new shape has NO Desk column (D-01 — desk is the sheet name)

if (isLegacy6Col || isOldFlatEnriched) {
    throw new IllegalArgumentException(
        "This spreadsheet uses a retired format. Please download the new template "
        + "(one worksheet per desk) and re-enter your data.");
}
if (!isNewPerDeskShape) {
    throw new IllegalArgumentException("Unrecognised spreadsheet shape. Expected the per-desk "
        + "enriched template (BambooHR ID + Monday..Sunday columns, no Desk column). Got headers: " + headers);
}
```
**Test precedent to extend, not discard:** `DeskAssignmentUploadLegacyShapeTest` and `DeskAssignmentUploadEnrichedShapeTest` already cover the current two-shape detection with this exact `assertThatThrownBy(...).hasMessageContaining(...)` style — Phase 10 should add a `DeskAssignmentUploadRetiredShapeTest` following the same structure rather than deleting the existing tests outright (some assertions, e.g. the "Unrecognised spreadsheet shape" unknown-headers case, still apply verbatim to the *new* shape's fallback path).

### Pattern 4: `Specialty N` column detection (confirmed precedent)

The current "first non-blank = primary, rest = secondary" assignment logic already exists at `DeskAssignmentUploadService` lines 344-353 and does not need to change — only the *upstream* column-detection needs to grow from a fixed `spec1Col`/`spec2Col` pair to a header-scan using `EnrichedColumnLayout.specialtyIndex()` across all columns, collecting an ordered `List<String>` of non-blank specialty names instead of the current two hardcoded lookups.

### Anti-Patterns to Avoid

- **Re-deriving a "days off" window inside `DeskAssignmentUploadService`:** do not add lookback/lookahead date-expansion logic to the parser to mimic `BambooRefreshService`'s MANDATORY generation — this reintroduces the exact durability hazard documented in `## Recurring PTO Storage Decision`. Write to `agent_day_hours` only.
- **Hardcoding header strings in more than one place:** any header literal outside `EnrichedColumnLayout` (in the parser, the template generator, or the export service) reintroduces the drift risk D-13 exists to close.
- **Treating "0" and blank as equivalent:** D-04 requires blank to be a hard validation failure (skip row) while `0` is a fully valid, meaningful value (not-worked). Do not collapse these two cases in cell-parsing logic.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multi-sheet iteration | A custom sheet-discovery/validation loop | The existing `workbook.getNumberOfSheets()` + `getSheetAt(s)` loop already proven in `FteUploadService` | Identical mechanics already tested in production; no new POI API surface to learn |
| Cell type coercion (string/number/date) | New cell-reading helpers | `DeskAssignmentUploadService.getCellString()` (existing null-safe, type-switching helper) — extend only to add numeric-hours parsing (currently returns strings for numeric cells via `(long) cell.getNumericCellValue()`, which truncates fractional hours (`7.5` → `"7"`) and MUST be fixed for D-10's fractional-hours requirement | Reuse the null-safe pattern; do not reintroduce cell-type-switch boilerplate elsewhere |
| Recurring day-off pattern storage | A new dated-row generator/scheduler mimicking `BambooRefreshService` | `agent_day_hours` extended with `day_off_type` (see D-12 decision) | The recurring-blocking behavior already exists and is solver-tested via Phase 9; do not duplicate it |
| Upload Results per-sheet rollup arithmetic | Manual counting scattered through the row loop | A `Map<String desk, RollupCounter>` accumulated once per sheet, converted to the rollup DTO at the end | Matches the existing `assigned`/`skipped` list-accumulation style already in the method |

**Key insight:** Every non-D-12 open question in this phase already has a working, tested precedent somewhere in this codebase (FTE upload for multi-sheet; existing specialty-assignment logic for primary/secondary; existing shape-detection/throw pattern for rejection messages). The only genuinely novel engineering decision is D-12, and it resolves to "extend a table Phase 9 already built" rather than "introduce new machinery."

## Common Pitfalls

### Pitfall 1: Truncated fractional hours from `getCellString()`
**What goes wrong:** The existing `getCellString()` helper (`DeskAssignmentUploadService.java` lines 390-403) converts numeric cells via `String.valueOf((long) cell.getNumericCellValue())` — this **truncates** `7.5` to `"7"`. D-10 explicitly requires fractional hours (e.g. `7.5`) to be accepted.
**Why it happens:** The helper was written for whole-number legacy fields (row numbers, employee IDs) and reused without adjustment.
**How to avoid:** The day-cell reader must call `cell.getNumericCellValue()` directly as a `double`/`BigDecimal`, not go through the truncating string helper, for the 7 day columns specifically.
**Warning signs:** A unit test uploading `7.5` in a day cell and asserting the stored `agent_day_hours.hours` equals `7.00` instead of `7.50` would catch this immediately — write that test.

### Pitfall 2: BambooRefreshService window-wipe of spreadsheet-sourced dated rows
**What goes wrong:** Writing spreadsheet MANDATORY/PTO directly as dated `AgentDayOff` rows, then having an operator (or Phase 11's auto-sync) run `refreshDeskAgents`, silently deletes them with no regeneration path.
**Why it happens:** `refreshDeskAgents`'s window-refresh step deletes and regenerates the *entire* `AgentDayOff` window from BambooHR-only sources; it has no awareness of spreadsheet-derived facts.
**How to avoid:** Do not write dated `AgentDayOff` rows from the spreadsheet parser (see D-12 decision) — use the `agent_day_hours.day_off_type` label instead, which `refreshDeskAgents` never touches or deletes.
**Warning signs:** Any test/manual-verification step that (1) uploads a sheet with a `PTO` cell, (2) triggers a BambooHR sync for the same desk, (3) re-solves a schedule, and confirms the agent is still blocked on that weekday — this is the regression this pitfall would cause if missed.

### Pitfall 3: Clamp silently swallowed instead of surfaced
**What goes wrong:** A `> 24` value gets clamped to `24` in the stored `agent_day_hours.hours`, but the clamp-warning is only logged (`log.warn`) and never added to the response DTO, so the operator never sees it (D-10/D-11 explicitly require non-silent surfacing).
**Why it happens:** The existing codebase's convention for anomalies is `log.warn` (see `BambooRefreshService`'s outlier-pattern warning) — easy to reach for that instead of adding a response-visible warning.
**How to avoid:** Clamp warnings must be accumulated into a `List<String>` (or a small warning DTO) that is part of `DeskAssignmentUploadResult`, not just logged.

### Pitfall 4: Sheet-name-to-desk matching is case- and whitespace-sensitive by accident
**What goes wrong:** A desk named "Billing" and a sheet named "billing " (trailing space, different case) fail to match, causing the whole sheet to be silently skipped (D-02) even though the operator intended a match.
**Why it happens:** Excel sheet names can carry incidental whitespace; the existing `deskByName` map is already built with `.toLowerCase()` keys (line 84) but sheet names read via `getSheetName()` need the same `.trim().toLowerCase()` treatment — `FteUploadService` already does this (`sheet.getSheetName().trim()`), so mirror it, but also lowercase before the map lookup.
**How to avoid:** Normalize both desk names (already done) and sheet names (`.trim().toLowerCase()`) before comparing, exactly matching the `FteUploadService` precedent plus the existing `deskByName` lowercase convention.

### Pitfall 5: Multipart file-size limits not configured for larger per-desk workbooks
**What goes wrong:** A workbook with one sheet per desk (potentially 10-30+ sheets vs. today's single sheet) may exceed Spring Boot's default multipart limits. No `spring.servlet.multipart.max-file-size` / `max-request-size` override was found in `application.yml` `[VERIFIED: grep of src/main/resources/application.yml — no multipart config present, so Spring Boot defaults apply]`, meaning the current default (historically 1MB per Spring Boot's `DataSize.ofMegabytes(1)`) could reject a realistic multi-desk workbook with an unhelpful 400/413 before the parser ever runs.
**How to avoid:** Add explicit `spring.servlet.multipart.max-file-size` / `max-request-size` values (e.g. `10MB`) to `application.yml` as part of this phase's plan, and flag it for verification against the current Spring Boot version's actual default (this research did not confirm the exact Spring Boot version's default value — see Open Questions).

### Pitfall 6: `clearDesk` must also clear the new `day_off_type` label (no separate migration needed, but verify)
**What goes wrong:** `clearDesk()` already calls `agentDayHoursRepository.deleteByAgent_Id(agent.getId())` (full-row delete), so the new `day_off_type` column is automatically cleared along with `hours` — no special-casing required, but this should be explicitly asserted in a test so a future refactor of `clearDesk` (e.g., switching to a partial update instead of delete+recreate) doesn't reintroduce stale labels.

## Code Examples

### Day-cell parsing and validation (D-03/D-04/D-05/D-10)
```java
// Source: adaptation of DeskAssignmentUploadService's existing cellAt()/getCellString() null-safety
// pattern (lines 178-194, 385-403), fixed for fractional hours (Pitfall 1) and extended for the
// MANDATORY/PTO keyword contract (D-03).
private record DayCellResult(BigDecimal hours, DayOffType type, String clampWarning) {}

private Optional<DayCellResult> parseDayCell(Row row, Map<String, Integer> col, DayOfWeek day) {
    int idx = col.getOrDefault(EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(day)), -1);
    Cell cell = idx >= 0 ? row.getCell(idx) : null;
    if (cell == null) return Optional.empty(); // blank -> caller skips row (D-04)

    if (cell.getCellType() == CellType.STRING) {
        String raw = cell.getStringCellValue().trim();
        if (raw.equalsIgnoreCase("MANDATORY")) return Optional.of(new DayCellResult(BigDecimal.ZERO, DayOffType.MANDATORY, null));
        if (raw.equalsIgnoreCase("PTO")) return Optional.of(new DayCellResult(BigDecimal.ZERO, DayOffType.PTO, null));
        return Optional.empty(); // unrecognized word -> caller skips row (D-04)
    }
    if (cell.getCellType() == CellType.NUMERIC) {
        BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue()); // NOT (long) — preserves fractional hours
        if (value.signum() < 0) return Optional.empty(); // negative -> caller skips row (D-10)
        if (value.compareTo(new BigDecimal("24")) > 0) {
            return Optional.of(new DayCellResult(new BigDecimal("24.00"), null,
                    day + ": " + value + " -> 24")); // clamp, non-silent (D-10)
        }
        return Optional.of(new DayCellResult(value.setScale(2, RoundingMode.HALF_UP), null, null));
    }
    return Optional.empty(); // blank/boolean/other -> caller skips row (D-04)
}
```

### Flyway migration (D-12)
```sql
-- Source: mirrors V29's additive-column style (src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql)
-- V30__agent_day_hours_recurring_status.sql
ALTER TABLE agent_day_hours
    ADD COLUMN day_off_type VARCHAR(9);
-- NULL for all existing rows (V29-migrated agents have no recurring MANDATORY/PTO label yet — correct default,
-- no backfill needed since no spreadsheet has ever populated this column before this migration).
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Single sheet (index 0), `Desk` column per row | One sheet per desk, sheet name = desk (D-01) | Phase 10 | Parser must iterate `workbook.getNumberOfSheets()`, not read `getSheetAt(0)` |
| Two hardcoded specialty columns (`spec1Col`/`spec2Col`) | Unbounded `Specialty N` header scan (D-06) | Phase 10 | Column detection becomes a header-prefix scan instead of two fixed lookups |
| Fuzzy match by name/email fallback (lines 249-293) | BambooHR-ID-only match (D-08) | Phase 10 | ~45 lines of fallback matching logic removed entirely |
| 6-col legacy + 16-col flat-enriched both accepted | Both retired; new per-desk shape only (D-15) | Phase 10 | Existing `DeskAssignmentUploadLegacyShapeTest`/`EnrichedShapeTest` need a `RetiredShapeTest` sibling, not deletion |
| Three Mon-Sun 21-column groups (originally planned per PROJECT.md/REQUIREMENTS milestone start) | One 7-column polymorphic day group (D-03) | 2026-07-31 discussion, before any code was written | Simpler parser; single cell encodes hours-or-status |

**Deprecated/outdated:**
- The three-column-group Mon-Sun design in the original REQUIREMENTS.md UPL-03/04/05 wording was superseded before implementation began (`requirement_revisions` in `10-CONTEXT.md`) — do not build against the original REQUIREMENTS.md prose; the revised note at the top of the "Upload Format & Parsing" section is authoritative.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Recommending `agent_day_hours.day_off_type` also be used for the spreadsheet's `MANDATORY` cell (not just `PTO`), revising the literal storage implication of the locked D-05 wording | Recurring PTO Storage Decision | If the team insists on literally writing dated `AgentDayOff` rows for spreadsheet MANDATORY, the plan must additionally extend `BambooRefreshService` to re-expand a durable pattern on every sync — otherwise MANDATORY-from-spreadsheet silently disappears on the next BambooHR sync, identical to the PTO hazard documented above |
| A2 | Spring Boot's default multipart `max-file-size` will be too small for a realistic multi-desk workbook | Common Pitfalls #5 | If the default is actually generous enough (not independently confirmed against the exact Spring Boot version in this project), the added config is harmless but unnecessary; if too small and unaddressed, large uploads fail with an unhelpful 413 |
| A3 | The exact `EnrichedColumnLayout` class shape (method names, header text casing) is a proposal, not verified against any existing spec | Architecture Patterns Pattern 1 | Low risk — it is new code with no compatibility constraint beyond matching the template/parser/export round-trip internally |

**If this table is empty:** N/A — see entries above. All core D-12 findings (the `BambooRefreshService` window-wipe mechanism, the `resolveEffectiveHours` resolution chain, the multi-sheet FTE-upload precedent, the specialty-assignment code, the shape-detection code, the frontend Upload Results modal) were verified by direct source reads, not assumed.

## Open Questions

1. **Should spreadsheet-sourced MANDATORY/PTO also appear in the existing `/agents/{id}/days-off` calendar API?**
   - What we know: That endpoint (`AgentDayOffController`) reads only dated `AgentDayOff` rows. The recommended `agent_day_hours.day_off_type` storage does not populate it (by design, to avoid the window-wipe hazard).
   - What's unclear: Whether any current or near-term UI consumes that endpoint for a "days off calendar" view that operators would expect to reflect spreadsheet-sourced recurring PTO/MANDATORY.
   - Recommendation: Confirm with the user/PROJECT.md whether this calendar view is an active UI surface before deciding whether to invest in a *safe* dated-materialization companion (i.e., extending `BambooRefreshService`'s window-expansion step to read `agent_day_hours.day_off_type` and re-expand it into dated rows on every sync, rather than having the parser write dated rows directly). If no UI currently reads that endpoint for this purpose, skip it — the solver-blocking and Upload Results reporting needs are already fully met without it.

2. **Exact Spring Boot version / current multipart default in this project.**
   - What we know: No `spring.servlet.multipart.*` override exists in `application.yml`.
   - What's unclear: The project's exact Spring Boot version (not independently confirmed this session) and therefore its exact default file-size limit.
   - Recommendation: Add an explicit, generous multipart size override as a low-cost defensive measure regardless of the exact default (Common Pitfall #5).

3. **Desk sheet ordering / duplicate sheet-name handling.**
   - What we know: Excel technically permits sheet names that differ only by trailing whitespace or invisible characters; `deskByName` and sheet-name normalization should handle the common case (Pitfall 4).
   - What's unclear: Whether two sheets could resolve to the same desk after normalization (e.g., "Billing" and "billing" as two separate sheets in one workbook) and what should happen (last-wins clear-then-reimport? or reject as ambiguous?).
   - Recommendation: Planner should pick one explicit behavior (recommend: process in sheet order, second match re-clears and re-populates the same desk, effectively "last sheet wins" — consistent with D-17's clear-then-reimport philosophy) and add a test for it.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito, via `spring-boot-starter-test` `[VERIFIED: build.gradle line 43]` |
| Config file | `build.gradle` (no separate JUnit platform config file found) |
| Quick run command | `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UPL-01 | Multi-sheet parse, sheet name = desk | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadMultiSheetTest"` | ❌ Wave 0 |
| UPL-02 | Unbounded `Specialty N` detection | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadSpecialtyTest"` | ❌ Wave 0 |
| UPL-03/04/05 | Day-cell parse: number/MANDATORY/PTO -> `agent_day_hours` row | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadDayCellTest"` | ❌ Wave 0 |
| UPL-06 | Validation skip reasons, clamp warning, per-sheet rollup | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadValidationTest"` | ❌ Wave 0 |
| UPL-07 | BambooHR-ID-only match, reject unmatched | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadNonSchedulableRejectTest"` (extend existing) | ✅ (extend) |
| UPL-08 | Retired shapes rejected with new-template message | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadRetiredShapeTest"` | ❌ Wave 0 (sibling of existing `LegacyShapeTest`/`EnrichedShapeTest`) |
| UPL-09 | Pre-seeded template download round-trips through `EnrichedColumnLayout` | unit + manual | `./gradlew test --tests "com.wfm.service.DeskAssignmentTemplateServiceTest"` | ❌ Wave 0 |
| D-12 (solver regression) | Uploaded `PTO`/`MANDATORY` cell survives a `refreshDeskAgents` call unchanged | integration | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` (extend existing with a case asserting `agent_day_hours` rows are untouched by refresh) | ✅ (extend) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` — covers UPL-01 (multi-sheet, sheet-name-to-desk, D-02 skip-notice)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java` — covers UPL-02 (N-column detection, first-non-blank-primary)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java` — covers UPL-03/04/05 including the fractional-hours regression (Pitfall 1) and the D-12 label storage
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java` — covers UPL-06 (blank/invalid cell skip, clamp warning surfaced non-silently, per-sheet rollup counts)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java` — covers UPL-08 (both retired shapes produce the "download the new template" message)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java` — covers UPL-09 (pre-seeded identity columns filled, day+specialty columns blank, uses same `EnrichedColumnLayout` as parser)
- [ ] Extend `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` — regression guard for the D-12 window-wipe hazard (a `refreshDeskAgents` call must not delete/alter `agent_day_hours` rows)
- [ ] Framework install: none — `spring-boot-starter-test` already present

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V4 Access Control | yes | Existing `TenantContext`-scoped queries (`findByTenantId*`) — no change needed, verify every new repository method added for `agent_day_hours`/desk-sheet lookups is tenant-scoped like its Phase 9 siblings |
| V5 Input Validation | yes | D-04/D-10's cell-content validation (number range, keyword allowlist, clamp) is the primary ASVS V5 control for this phase — implement as an allowlist (number-or-`MANDATORY`-or-`PTO`), never a denylist |
| V12 File & Resource Handling | yes | Apache POI `.xlsx` parsing of an untrusted upload — see Known Threat Patterns below |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| OOXML "zip bomb" / decompression-ratio attack via a crafted `.xlsx` (deeply nested/repeated shared-strings or many sheets) causing excessive memory/CPU use during `XSSFWorkbook` parsing | Denial of Service | Apache POI 5.x ships `ZipSecureFile` with a default `setMinInflateRatio`/entry-count guard already active by default in this POI version; do not disable it. Additionally cap the multipart request size (Common Pitfall #5) as a first line of defense before POI even opens the stream. `[CITED: Apache POI project — ZipSecureFile is POI's documented mitigation for zip-bomb-style OOXML attacks; this project does not currently override its defaults, which is the safe posture]` |
| CSV-injection-style formula payloads in cell values (`=CMD(...)`) surviving round-trip into the generated template or export `.xlsx` | Tampering | The existing frontend already sanitizes this exact class of risk for CSV export (`handleDownloadSkippedCsv`'s `sanitize()` — prefixes leading `=`/`+`/`-`/`@` with a single quote, `frontend/src/pages/ClientManagement.tsx`). Apply the identical sanitization when writing any operator-supplied string (e.g., identity fields echoed back into Upload Results or re-exported) into a new `.xlsx` cell, since Excel's formula-injection risk applies to `.xlsx` cells exactly as it does to `.csv` |
| BambooHR ID or PII (name/email) reflected verbatim into skip-reason strings that flow to logs/UI | Information Disclosure | Matches existing behavior (`SkippedRow` already carries `bamboohrId`/`name` — this is an accepted existing pattern, not new risk introduced by this phase); no new mitigation required beyond what already exists |

## Sources

### Primary (HIGH confidence — direct source reads this session)
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (full file) — current shape detection, specialty assignment, clear-desk, cell helpers
- `src/main/java/com/wfm/service/FteUploadService.java` (full file) — multi-sheet iteration precedent for D-01
- `src/main/java/com/wfm/service/DeskAgentExportService.java` (full file) — export pattern to extend for `EnrichedColumnLayout` symmetry
- `src/main/java/com/wfm/integration/BambooRefreshService.java` (lines 1-333) — window materialization mechanism, the core D-12 evidence
- `src/main/java/com/wfm/service/SolverService.java` (lines 110-150, 460-521, 837-849, 985-1004) — solver consumption of `AgentDayOff`/`agent_day_hours`, the `resolveEffectiveHours` resolution chain
- `src/main/java/com/wfm/model/AgentDayOff.java`, `DayOffType.java`, `DayOffStatus.java`, `AgentException.java`, `AgentDayHours.java` (all full files)
- `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` (full file) — migration style precedent, confirms V29 is latest
- `src/main/java/com/wfm/controller/ClientManagementController.java`, `AgentDayOffController.java` (full files) — API pattern precedents
- `frontend/src/pages/ClientManagement.tsx` (Upload Results modal + handlers), `frontend/src/api/client.ts` (DTO shapes) — existing Upload Results UI surface
- `build.gradle` — confirms POI 5.3.0, Flyway, no version drift concerns; confirms no new dependency needed
- `.planning/phases/10-enriched-upload-parsing/10-CONTEXT.md`, `.planning/phases/09-agent-data-model-foundation/09-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md` — locked decisions and requirement traceability

### Secondary (MEDIUM confidence)
- Apache POI `ZipSecureFile` zip-bomb mitigation — general knowledge of the library's documented default protections, not independently re-verified via Context7/official docs this session (training-data-informed, consistent with POI's long-standing public documentation)

### Tertiary (LOW confidence)
- None — no unverified web-search-only claims were used in this research; every architectural claim traces to a direct file read in this repository.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; existing versions read directly from `build.gradle`
- Architecture (multi-sheet, specialty columns, shape rejection): HIGH — all three patterns confirmed via direct reads of existing, already-tested code in this repository
- D-12 recurring PTO storage: HIGH — conclusion derived from tracing actual solver code (`resolveEffectiveHours`, `computeAgentDayConfigs`, `BambooRefreshService`'s delete-then-regenerate window logic), not from reasoning about the entities in isolation
- Pitfalls: HIGH for Pitfalls 1, 2, 4, 6 (all directly observed in code); MEDIUM for Pitfall 5 (multipart default not independently confirmed against exact Spring Boot version) and the POI zip-bomb mitigation claim (general library knowledge, not re-verified this session)

**Research date:** 2026-07-31
**Valid until:** Effectively indefinite for the architectural findings (they depend on this codebase's own code, not an external moving target) — recommend re-verifying the Spring Boot multipart default (Open Question 2) and the exact Apache POI zip-bomb-guard defaults before Phase 10 code review if more than ~30 days pass before implementation, in case a dependency bump occurs in the interim.
