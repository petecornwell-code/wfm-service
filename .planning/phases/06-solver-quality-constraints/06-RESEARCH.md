# Phase 6: Solver Quality Constraints (PTO & Weekends) - Research

**Researched:** 2026-06-02
**Domain:** BambooHR integration — mandatory day-off data pipeline
**Confidence:** HIGH (all findings grounded in live codebase reads)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Mandatory days off source is BambooHR custom field `4517` (`customWorkingdays`), Personal > Schedule. NOT the Monday…Sunday fields (ids 5553-5563 — empty), NOT the desk-upload spreadsheet columns.
- **D-02:** Pull via the EXISTING bulk `POST /reports/custom` in `HttpBambooHRClient.listEmployees` — add `"4517"` to the fields array. No per-employee fetch.
- **D-03:** `DayOffType.MANDATORY` rows = days NOT in the parsed working-days set, generated recurring across the schedule horizon in `BambooRefreshService`. Replaces the dead `"MANDATORY".equalsIgnoreCase(type)` match at `:263`.
- **D-04:** Honour the BambooHR value literally — days off = `{Mon..Sun}` minus working days.
- **D-05:** Flag outliers (≠ 2 contiguous days off, or 0 days off) to the operator. Block as-is, but surface the flag.
- **D-06:** Parser must be tolerant of all live formats: ranges (`Mon-Fri`, week-wrapping `Fri-Tue`), `"X to Y"` (`Mon. to Thurs.`), comma lists, trailing annotations (`HOOP`), spelling variants (`Thu`/`Thur`/`Thurs.`). `Variable`/blank → see D-07.
- **D-07:** `Variable` or blank Working days = data gap. Do NOT auto-schedule. Surface to operator. Reuse `AgentEligibilityService` exclusion pattern.
- **D-08:** PTO behaviour unchanged (APPROVED hard-blocks, REQUESTED visible-only). No change.
- **D-09:** No solver-engine change. `SolverService.buildAgentDaysOffMap()` already treats MANDATORY as always blocking.
- **D-10:** Weekends visible in PTO tab as MANDATORY — UI already built. Verify-not-build. Touch UI only if label needs clarifying (e.g. "Mandatory (Weekend)").

### Claude's Discretion

- Exact persistence shape of the per-agent weekly pattern (transient generation vs stored column/table).
- Exact surfacing mechanism for the data-gap + outlier flags (likely extends BambooHR Sync Status / diagnostics view — coordinate with Phase 7 DIAG work).

### Deferred Ideas (OUT OF SCOPE)

- QUAL-02: weekend-position fairness distribution.
- QUAL-03: day-to-day hours consistency.
- Roadmap formal split into Phase 6a/6b.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QUAL-01 | Every agent receives their fixed weekly days off (from BambooHR field 4517) honoured as hard MANDATORY blocks | Data pipeline: field pull, tolerant parser, recurring AgentDayOff generation, data-gap exclusion |
</phase_requirements>

---

## Summary

Phase 6 (re-scoped) delivers the data pipeline that makes `SolverService.buildAgentDaysOffMap()` actually effective for mandatory day-offs. The solver constraint is fully built and working — the only missing piece is real data flowing into `AgentDayOff` rows with `type = MANDATORY`. Currently the dead match at `BambooRefreshService.java:263` (`"MANDATORY".equalsIgnoreCase(type)`) never fires because BambooHR time-off types are PTO/Vacation/Sick/Holiday — never the string "MANDATORY".

The fix is a five-step pipeline: (1) add field `4517` to the existing bulk custom report request, (2) parse the free-text `customWorkingdays` value into a `Set<DayOfWeek>` of working days, (3) carry the off-day set on `BambooEmployee`, (4) generate recurring `MANDATORY` `AgentDayOff` rows across the schedule horizon in `persistRefreshData`, (5) exclude data-gap agents (Variable/blank) from scheduling via the existing `AgentEligibilityService` pattern. The PTO tab already renders MANDATORY rows correctly — verification is the only UI work.

The key engineering challenge is the tolerant parser: the live `customWorkingdays` values are free-text with at least six distinct format patterns, week-wrapping ranges, period-terminated tokens, spaces-in-ranges, and trailing non-day annotations. A pure-regex approach is brittle; a token-normalise-then-expand strategy is more defensible and table-driveable for testing.

**Primary recommendation:** Implement `WorkingDaysParser` as a standalone, package-private static utility class in `com.wfm.integration`, tested with a table-driven JUnit 5 `@MethodSource` or `@CsvSource` test covering every live-observed value. Generate MANDATORY rows inside `persistRefreshData` immediately after the existing PTO loop, using the same delete-then-reinsert idempotency pattern already in place.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pull `customWorkingdays` from BambooHR API | API/Backend (integration layer) | — | Existing bulk HTTP call in `HttpBambooHRClient` |
| Parse free-text working days value | API/Backend (integration layer) | — | Pure domain logic, no I/O needed |
| Generate MANDATORY AgentDayOff rows | API/Backend (persistence layer) | — | `BambooRefreshService.persistRefreshData` owns day-off persistence |
| Idempotency on repeated refresh | API/Backend (persistence layer) | — | delete-then-reinsert pattern already established |
| Data-gap agent exclusion from solver | API/Backend (service layer) | — | `AgentEligibilityService` + `SolverService.filterEligible` |
| Flag data-gap / outlier agents | API/Backend (service layer) | Frontend (read-only UI) | BambooSyncEvent or new diagnostics surface |
| MANDATORY row visibility in PTO tab | Frontend (ScheduleResults.tsx) | — | Already renders `type === 'MANDATORY'` cells red; verify only |

---

## Standard Stack

### Core

No new libraries. This phase uses only existing dependencies.

| Asset | Location | Purpose |
|-------|----------|---------|
| `HttpBambooHRClient` | `com.wfm.integration` | Add field `4517` to the `/reports/custom` fields array |
| `BambooEmployee` (record) | `com.wfm.integration` | Add `String customWorkingdays` component |
| `BambooRefreshService` | `com.wfm.integration` | Generate recurring MANDATORY rows in `persistRefreshData` |
| `AgentDayOffRepository` | `com.wfm.repository` | Existing `deleteByAgent_IdAndDateBetween` + save — unchanged |
| `AgentEligibilityService` | `com.wfm.service` | Reuse `isNonSchedulable` pattern for data-gap exclusion |
| `BambooSyncEventService` | `com.wfm.service` | Natural home for data-gap / outlier flag counts |
| `DayOffType.MANDATORY` | `com.wfm.model` | Already exists — no enum change needed |
| `DayOffStatus.APPROVED` | `com.wfm.model` | MANDATORY rows set status=APPROVED (they always block) |

### New Class

| Class | Package | Purpose |
|-------|---------|---------|
| `WorkingDaysParser` | `com.wfm.integration` | Tolerant parser: `String → Set<DayOfWeek>` working days; null/empty/"Variable" → empty Optional indicating data gap |

---

## Architecture Patterns

### System Architecture Diagram

```
BambooHR API
 POST /reports/custom (fields: [..., "4517"])
       |
       v
HttpBambooHRClient.listEmployees()
  reads emp.customWorkingdays per row
       |
       v
WorkingDaysParser.parse(rawValue)
  returns Optional<Set<DayOfWeek>> workingDays
  empty Optional = data gap (Variable or blank)
       |
       +---[data gap]---> BambooEmployee carries null offDays
       |
       +---[parsed]-----> offDays = {Mon..Sun} minus workingDays
                                |
                                v
               BambooEmployee record (now has Set<DayOfWeek> offDays)
                                |
                                v
             BambooRefreshService.persistRefreshData()
               (inside TransactionTemplate)
               |
               +-- existing: deleteByAgent_IdAndDateBetween for PTO window
               |
               +-- NEW MANDATORY generation loop:
               |    for each agent in refreshedAgentIds:
               |      if offDays is null → skip (data gap; handled by eligibility)
               |      else:
               |        for each date in [from..to]:
               |          if date.getDayOfWeek() in offDays:
               |            create AgentDayOff(MANDATORY, APPROVED)
               |            put in dedup map (key = agentId|date)
               |            MANDATORY wins over PTO in existing priority logic
               |
               +-- existing: PTO loop (unchanged)
               |
               +-- NEW: outlier logging / sync event counts
                         |
                         v
                  BambooSyncEvent.mandatoryGenerated / dataGapCount
                  (or inline log.warn for MVP; exact surface = Claude's Discretion)
                         |
                         v
                 AgentDayOff rows in DB (type=MANDATORY, status=APPROVED)
                         |
                         v
               SolverService.buildAgentDaysOffMap()
               MANDATORY always blocks → agent unavailable on their off-days
                         |
                         v
               GET /desks/{deskId}/days-off?from&to
               ScheduleResults.tsx PtoTab
               type === 'MANDATORY' → red cell, "MANDATORY" label (already built)
```

### Recommended Project Structure

No new directories. New class goes alongside existing integration classes:

```
src/main/java/com/wfm/integration/
├── WorkingDaysParser.java       # NEW — package-private static util
├── HttpBambooHRClient.java      # MODIFY — add "4517" to fields array
├── BambooEmployee.java          # MODIFY — add offDays field
├── BambooRefreshService.java    # MODIFY — generate MANDATORY rows
└── MockBambooHRClient.java      # MODIFY — emit customWorkingdays-style value
src/test/java/com/wfm/integration/
└── WorkingDaysParserTest.java   # NEW — table-driven, covers all live formats
```

### Pattern 1: WorkingDaysParser — Token-Normalise-Expand Strategy

**What:** Parse free-text working-days string to a `Set<DayOfWeek>`.

**When to use:** All `customWorkingdays` values from BambooHR field 4517.

**Design rationale:** A multi-regex dispatch approach is brittle when new formats appear. The token-normalise-expand strategy handles all observed formats with a single pipeline:

1. Strip trailing annotations that are not day tokens (`HOOP`, etc.)
2. Detect format: range (contains `-` or ` to `) vs comma list
3. Normalise day tokens: strip periods, map `Thur`→`Thu`, `Thurs`→`Thu`, expand to `DayOfWeek`
4. For ranges: expand from start-day to end-day, wrapping the week if end < start (ordinal)
5. Days off = ALL_DAYS minus working days

```java
// Source: codebase analysis of live BambooHR value catalog (CONTEXT.md specifics)
// Package-private — only BambooRefreshService calls this
final class WorkingDaysParser {

    private static final List<DayOfWeek> WEEK_ORDER = List.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    );

    /** Returns empty Optional when value is null, blank, or "Variable". */
    static Optional<Set<DayOfWeek>> parseWorkingDays(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String normalised = raw.trim();
        if (normalised.equalsIgnoreCase("Variable")) return Optional.empty();

        // Strip trailing non-day annotations (e.g. "HOOP")
        // Keep stripping words from right while they don't look like day tokens
        String stripped = stripTrailingAnnotations(normalised);

        // Detect comma-list vs range
        if (stripped.contains(",")) {
            return Optional.of(parseCommaList(stripped));
        } else {
            return Optional.of(parseRange(stripped));
        }
    }

    static Set<DayOfWeek> offDaysFrom(Set<DayOfWeek> workingDays) {
        return EnumSet.allOf(DayOfWeek.class).stream()
            .filter(d -> !workingDays.contains(d))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static String stripTrailingAnnotations(String s) {
        // Remove tokens from the right that are not recognisable day-token prefixes
        // Simple approach: split by spaces/commas, pop tokens from right while
        // they don't start with Mon/Tue/Wed/Thu/Fri/Sat/Sun (case-insensitive)
        // ...
    }

    private static Set<DayOfWeek> parseRange(String s) {
        // Split on " - " or "-" or " to " (case-insensitive)
        // Normalise both ends to DayOfWeek
        // If end < start (ordinal in WEEK_ORDER), range wraps the week
        // ...
    }

    private static Set<DayOfWeek> parseCommaList(String s) {
        // Split by comma, normalise each token to DayOfWeek
        // ...
    }

    static DayOfWeek normaliseToken(String token) {
        // Strip trailing period
        // Map: Mon→MONDAY, Tue→TUESDAY, Wed→WEDNESDAY,
        //      Thu/Thur/Thurs→THURSDAY, Fri→FRIDAY, Sat→SATURDAY, Sun→SUNDAY
        // ...
    }
}
```

**Edge cases the parser must handle:**

| Input | Expected working days | Off days | Notes |
|-------|----------------------|----------|-------|
| `Mon-Fri` | Mon–Fri | Sat, Sun | Standard |
| `Wed-Sun` | Wed–Sun | Mon, Tue | Non-Mon start |
| `Sun-Thu` | Sun–Thu | Fri, Sat | Wraps? No — Sun..Thu is Mon-Thu order issue; use WEEK_ORDER index |
| `Tue-Sat` | Tue–Sat | Sun, Mon | |
| `Fri-Tue` | Fri, Sat, Sun, Mon, Tue | Wed, Thu | Week-wrapping range |
| `Mon - Sun` | All 7 days | none | 0 days off — outlier |
| `Mon - Sun HOOP` | All 7 days | none | Same + annotation to strip |
| `Mon. to Thurs.` | Mon–Thu | Fri, Sat, Sun | "to" separator, period tokens, 4-day week |
| `Mon, Tue, Wed, Thu, Sat` | Mon–Thu + Sat | Fri, Sun | Comma list, non-consecutive off |
| `Variable` | — | — | Data gap → empty Optional |
| `""` / blank | — | — | Data gap → empty Optional |

**Week-wrap logic for ranges:** Use the index in `WEEK_ORDER` (Mon=0..Sun=6). If `endIdx < startIdx`, wrap: working days = indices `[startIdx..6]` + `[0..endIdx]`. Example: `Fri-Tue` → startIdx=4, endIdx=1 → indices 4,5,6,0,1 → Fri,Sat,Sun,Mon,Tue.

**Outlier detection:** After parsing, if `offDays.size() != 2` OR the two off-days are not consecutive (adjacent in WEEK_ORDER with wrap), flag as outlier. Pass a flag/count back to `BambooRefreshService` for logging.

### Pattern 2: Recurring MANDATORY Row Generation in persistRefreshData

**What:** For each refreshed agent with a non-null off-day set, iterate the entire `[from..to]` window and create `AgentDayOff(MANDATORY, APPROVED)` for every date whose `DayOfWeek` is in the off-days set.

**Key integration point:** The existing delete-then-reinsert idempotency pattern (`deleteByAgent_IdAndDateBetween` + dedupedDaysOff map) already handles repeated refreshes cleanly. MANDATORY rows participate in the same dedup map with existing priority logic:

```
// Existing priority (BambooRefreshService.java:270-273):
// MANDATORY wins over PTO; within same type, APPROVED > REQUESTED
```

The MANDATORY generation loop must run before (or feed into) the same `dedupedDaysOff` map. The simplest approach: generate MANDATORY entries for all off-days into the dedup map BEFORE the PTO loop — since MANDATORY wins priority, any subsequent PTO entry for the same (agent, date) will not overwrite it.

**MANDATORY row status:** Always `DayOffStatus.APPROVED`. Rationale: MANDATORY rows are factual (the agent does not work that day), not a request to approve. The solver's logic `d.getType() == DayOffType.MANDATORY || d.getStatus() == DayOffStatus.APPROVED` would catch them either way, but APPROVED is semantically correct.

**Window bounds:** Use the same `from`/`to` as the existing PTO window (`LocalDate.now().minusWeeks(lookbackWeeks)` to `LocalDate.now().plusWeeks(lookaheadWeeks)`). This is already computed in `refreshDeskAgents` before the transaction. No new config needed.

**Performance note:** For 120 agents, a 20-week window = 140 days. 120 × 2 off-days = 240 MANDATORY rows per refresh. Already well within the single-transaction scope. No batch insert needed.

### Pattern 3: Data-Gap Exclusion (D-07) — Reuse AgentEligibilityService Pattern

**What:** Agents with `customWorkingdays = null/blank/Variable` should not be auto-scheduled. The solver must skip them.

**How AgentEligibilityService currently works (confirmed by code read):**

```java
// AgentEligibilityService.java (full class — 31 lines)
public boolean isNonSchedulable(long tenantId, String jobTitle) {
    if (jobTitle == null || jobTitle.isBlank()) return false;
    return jobTitleConfigRepository.findByTenantIdAndJobTitle(tenantId, jobTitle)
        .map(config -> config.isNonSchedulable())
        .orElse(false);
}
```

This method is called from `SolverService.filterEligible`:

```java
static List<Agent> filterEligible(List<Agent> agents, long tenantId,
                                   AgentEligibilityService agentEligibilityService) {
    return agents.stream()
        .filter(Agent::isActive)
        .filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))
        .filter(a -> a.getPrimarySpecialization() != null)
        // ...
    ;
}
```

**D-07 exclusion options (Claude's Discretion area):**

Option A — Store a flag on the Agent entity (`workingDaysKnown: boolean`). Filter in `filterEligible` by adding `.filter(Agent::isWorkingDaysKnown)`. Requires DB migration + Agent field.

Option B — Treat agents with no MANDATORY rows in the window as having an incomplete profile. No schema change, but indirect and could give false negatives if the window is narrow.

Option C — Flag via `BambooSyncEvent` counts (data gap count + outlier count added to sync event response). No exclusion from solver, but operator-visible. Combine with Option A for full solution.

**Recommendation (Claude's Discretion):** Option A + Option C together. The boolean flag is a one-column migration, is explicit, and makes `filterEligible` self-documenting. `BambooSyncEvent` provides operator visibility. The flag is set during `persistRefreshData`: if `WorkingDaysParser.parseWorkingDays(emp.customWorkingdays())` returns empty Optional, set `agent.setWorkingDaysKnown(false)`; else `true`.

**Alternative lightweight approach:** Since `AgentEligibilityService` is only 31 lines and deals with `JobTitleConfig`, a separate method `isWorkingDaysMissing(Agent)` or a new filter in `filterEligible` is preferable to overloading `isNonSchedulable`. Do not conflate "job title non-schedulable" with "working days unknown" — they are separate exclusion reasons.

### Pattern 4: MockBambooHRClient Update

The mock's `listEmployees` currently returns `BambooEmployee` records without a `customWorkingdays` field (field doesn't exist yet). After the `BambooEmployee` record gains the field, the mock must emit realistic values.

**Strategy:** Use the employee index modulo to vary the pattern:
- index % 5 == 0 → `"Mon-Fri"` (standard)
- index % 5 == 1 → `"Sun-Thu"` (Sunday start)
- index % 5 == 2 → `"Tue-Sat"`
- index % 5 == 3 → `"Variable"` (data gap — for testing D-07)
- index % 5 == 4 → `"Mon. to Thurs."` (4-day week — outlier)

### Anti-Patterns to Avoid

- **String-matching MANDATORY from time-off type names:** Already dead code at `:263`. Remove it entirely — do not leave it as a fallback. It will never match real BambooHR data.
- **Per-employee API fetch for working days:** D-02 is explicit — the bulk `/reports/custom` call already fetches all employees. Per-employee GET would multiply API calls ~5,000x and trigger rate limits.
- **Hand-rolled dedup logic separate from the existing dedup map:** Feed MANDATORY rows through the same `dedupedDaysOff` map that PTO rows use. The priority logic (MANDATORY > PTO, APPROVED > REQUESTED) already handles conflicts correctly.
- **Using the Monday…Sunday custom fields (ids 5553-5563):** These are empty for all employees on the live helpware tenant. Field `4517` is the correct source.
- **Generating MANDATORY rows outside the refresh transaction:** The same `TransactionTemplate` that wraps `persistRefreshData` must include MANDATORY generation. Generating in a separate call risks partial state if the transaction rolls back.
- **Setting MANDATORY status to REQUESTED:** MANDATORY rows are facts, not requests. Use `DayOffStatus.APPROVED`. The solver checks `type == MANDATORY` anyway, but consistency matters for the PTO tab display (REQUESTED rows render yellow, not red).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Week-day range expansion with wrap | Custom date arithmetic | Simple index-in-WEEK_ORDER arithmetic (4 lines) |
| Idempotent refresh | Re-check for duplicates before insert | Existing `deleteByAgent_IdAndDateBetween` + dedup map already in `BambooRefreshService` |
| Priority resolution when MANDATORY and PTO conflict on same date | New merge logic | Existing priority map at `:270-273` — feed MANDATORY into same map |
| Solver exclusion of ineligible agents | New exclusion mechanism | Extend `filterEligible` in `SolverService` — already has three filter criteria |
| MANDATORY rendering in PTO tab | Frontend changes | Already renders `type === 'MANDATORY'` as red/"MANDATORY" — verify only |

**Key insight:** This phase is entirely about feeding correct data into existing machinery. Every consumer (solver, PTO tab, dedup logic) is already built and working. The risk is in the parser edge cases, not in the downstream infrastructure.

---

## Common Pitfalls

### Pitfall 1: Range Parser Mishandling Week-Wrapping

**What goes wrong:** `"Fri-Tue"` parsed as Fri only (or empty), because `FRIDAY.getValue()` (5) > `TUESDAY.getValue()` (2) and naive code stops immediately.

**Why it happens:** `DayOfWeek.getValue()` uses ISO ordering (Mon=1, Sun=7). A range `Fri-Tue` in the `WEEK_ORDER` list (Mon=0..Sun=6) has startIdx=4, endIdx=1. Code that does `for (DayOfWeek d : DayOfWeek.values()) if d >= start && d <= end` produces nothing.

**How to avoid:** Compute expansion in terms of `WEEK_ORDER` list indices. If `endIdx < startIdx`, the range wraps: expand `[startIdx..6]` + `[0..endIdx]`.

**Warning signs:** Parser test case `"Fri-Tue"` produces 0 or 1 days.

### Pitfall 2: "to" Separator Not Handled in Range Detection

**What goes wrong:** `"Mon. to Thurs."` not recognised as a range and falls through to comma-list parser, producing empty set.

**Why it happens:** Detection only splits on `-` not on ` to `.

**How to avoid:** Normalise separator: replace `\s+to\s+` (case-insensitive) with `-` before format detection.

### Pitfall 3: Period-Terminated Day Tokens Not Normalised

**What goes wrong:** `"Mon."` does not match `"Mon"` in the token map, causing parse failure.

**Why it happens:** `token.equals("Mon")` fails when input is `"Mon."`.

**How to avoid:** Strip trailing `.` and spaces from each token before lookup in the normalise step.

### Pitfall 4: Trailing Annotation ("HOOP") Misidentified as Day Token

**What goes wrong:** `"Mon - Sun HOOP"` produces 6 working days (treating "HOOP" as a corrupt day token that fails quietly) or throws.

**Why it happens:** Parser tries to normalise every word, including non-day words.

**How to avoid:** After splitting, attempt to normalise each token. If normalisation fails and the token appears after a valid day range, discard it as annotation. Log at DEBUG only.

### Pitfall 5: Dedup Map Key Collision Between MANDATORY and PTO

**What goes wrong:** An agent has a PTO entry and a newly-generated MANDATORY entry for the same date. Only one row is saved; wrong type wins.

**Why it happens:** The existing dedup map key is `agentId + "|" + date`. Priority logic at `:270-273` handles this correctly — MANDATORY wins. But only if MANDATORY entries are in the same dedup map as PTO entries.

**How to avoid:** Do not use separate persistence calls for MANDATORY rows. Feed MANDATORY entries into the shared `dedupedDaysOff` map before the PTO loop, so priority resolution applies.

### Pitfall 6: PTO Tab Shows Empty for New MANDATORY Rows

**What goes wrong:** After refresh, operator navigates to the PTO tab and sees no MANDATORY rows for a known agent.

**Why it happens:** The PTO tab fetches via `GET /desks/{deskId}/days-off?from&to` which calls `agentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween`. The `from`/`to` parameters are `schedule.periodStartDate` / `schedule.periodEndDate`. If the generated MANDATORY rows fall outside this window, they will not appear.

**How to avoid:** MANDATORY rows must be generated for the full `[now - lookbackWeeks .. now + lookaheadWeeks]` window, which already covers any typical schedule period. The PTO tab query is filtered to the schedule window — ensure the schedule period is within the refresh window. (It always should be if lookaheadWeeks = 8.)

### Pitfall 7: D-07 Agents Silently Included in Schedule

**What goes wrong:** Agents with blank/Variable `customWorkingdays` are silently included in the solver because no exclusion mechanism is in place.

**Why it happens:** The `filterEligible` method in `SolverService` has no check for working-days status.

**How to avoid:** Implement the Option A flag (`workingDaysKnown`) on `Agent` and add it as a filter criterion in `filterEligible`. Without this, D-07 cannot be satisfied by data alone — a MANDATORY row gap is not the same as an explicit exclusion.

### Pitfall 8: Desk-Scale Coverage Risk (StubHub-GE / Vinted-UA)

**What goes wrong:** A live desk has a high proportion of agents with blank/Variable `customWorkingdays`. Implementing D-07 exclusion could gut the schedule for that desk.

**Why it happens:** Company-wide, ~55% of employees have blank or Variable Working days. If the desks actually scheduled have this same distribution, D-07 would exclude most of their agents.

**How to avoid:** Before shipping, verify coverage at desk scale for the live desks (e.g. StubHub-GE, Vinted-UA). The CONTEXT.md confirms the 3 StubHub sample employees were all populated, but desk-wide verification is needed. Add an operator-visible warning when data-gap count exceeds a threshold (e.g. >20% of desk agents).

---

## Runtime State Inventory

> Phase involves data migration of AgentDayOff rows. No rename/refactor.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | `agent_day_off` table: existing rows have `type = MANDATORY` only from mock. In production, no MANDATORY rows exist (the dead code path never fired). After this phase, MANDATORY rows will be generated on next refresh. | Code change only — no migration of existing rows. Existing rows will be cleared and regenerated on next refresh by `deleteByAgent_IdAndDateBetween`. |
| Live service config | BambooHR API key pasted in chat 2026-06-02 (ad2bb…2be) — MUST BE ROTATED before this phase ships. | Rotate BambooHR API key in AWS Secrets Manager / env config before deploying. |
| OS-registered state | None. | None. |
| Secrets/env vars | BambooHR API key in environment config. The key credential itself is unchanged; only the value needs rotation. | Rotate key. |
| Build artifacts | If `BambooEmployee` record gains a new component, any callers that construct it by positional arguments (tests, mock) must be updated. | Update `MockBambooHRClient.listEmployees` and `BambooRefreshServiceTest.emp()` helper to pass the new field. |

**Security note (from todo):** The BambooHR API key `ad2bb…2be` was pasted into chat. Rotate it immediately — this is a blocking pre-deploy step, not optional.

---

## Code Examples

### Reading customWorkingdays in HttpBambooHRClient

```java
// Source: HttpBambooHRClient.java:133-177 (current code read in this session)
// Change: add "4517" to fields array; read customWorkingdays from each row

String requestBody = """
        {
          "title": "WFM Employee Report",
          "fields": ["id", "displayName", "workEmail", "department",
                     "jobTitle", "status", "employmentHistoryStatus", "4517"]
        }
        """;

// In the row loop (line ~162):
String customWorkingdays = emp.path("customWorkingdays").asText(null);

employees.add(new BambooEmployee(
        id, displayName, workEmail, department, jobTitle, status,
        employmentHistoryStatus, customWorkingdays,   // new field
        wfmTenantId, project
));
```

### BambooEmployee Record — New Field

```java
// Source: BambooEmployee.java (current code read — 13 lines, a record)
public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,
    String customWorkingdays,      // NEW — raw BambooHR field 4517 value
    String wfmTenantId,
    String project
) {}
```

All callers of the constructor need updating (MockBambooHRClient, test helper emp()).

### MANDATORY Row Generation in persistRefreshData

```java
// Source: BambooRefreshService.java:246-286 (existing PTO loop pattern)
// Place MANDATORY generation BEFORE the PTO loop, into the same dedupedDaysOff map

// NEW: Generate MANDATORY rows from customWorkingdays
Map<String, BambooEmployee> empByBambooId = employees.stream()
    .collect(Collectors.toMap(BambooEmployee::id, e -> e, (a, b) -> a));

int mandatoryGenerated = 0;
int dataGapCount = 0;

for (UUID agentId : refreshedAgentIds) {
    Agent agent = /* look up from refreshedAgentIds → agent map */;
    BambooEmployee emp = empByBambooId.get(agent.getBamboohrId());
    if (emp == null) continue;

    Optional<Set<DayOfWeek>> workingDaysOpt = WorkingDaysParser.parseWorkingDays(emp.customWorkingdays());

    if (workingDaysOpt.isEmpty()) {
        dataGapCount++;
        agent.setWorkingDaysKnown(false); // D-07
        agentRepository.save(agent);
        continue;
    }

    agent.setWorkingDaysKnown(true);
    agentRepository.save(agent);

    Set<DayOfWeek> offDays = WorkingDaysParser.offDaysFrom(workingDaysOpt.get());

    // Outlier flag (D-05)
    if (!WorkingDaysParser.isStandardTwoContiguousDaysOff(offDays)) {
        log.warn("Agent {} has non-standard off-day pattern: {} off-days = {}",
                 agent.getName(), offDays.size(), offDays);
    }

    LocalDate cursor = from;
    while (!cursor.isAfter(to)) {
        if (offDays.contains(cursor.getDayOfWeek())) {
            AgentDayOff dayOff = new AgentDayOff();
            dayOff.setTenantId(tenantId);
            dayOff.setAgent(agent);
            dayOff.setDate(cursor);
            dayOff.setType(DayOffType.MANDATORY);
            dayOff.setStatus(DayOffStatus.APPROVED);
            String key = agent.getId() + "|" + cursor;
            dedupedDaysOff.putIfAbsent(key, dayOff); // MANDATORY priority already handled by insert-order
            mandatoryGenerated++;
        }
        cursor = cursor.plusDays(1);
    }
}
// Then existing PTO loop (unchanged — priority logic handles MANDATORY vs PTO conflicts)
```

Note: The existing priority logic at `:270-273` puts MANDATORY ahead of PTO when there is a conflict. To use it correctly, generate MANDATORY entries first (putIfAbsent), then run PTO loop with the full priority check. Alternatively, feed MANDATORY entries through the same priority check — either works because MANDATORY always wins.

### SolverService.filterEligible — Extended for Working Days (Option A)

```java
// Source: SolverService.java:974-980 (current code read in this session)
// Add workingDaysKnown filter as fourth criterion

static List<Agent> filterEligible(List<Agent> agents, long tenantId,
                                   AgentEligibilityService agentEligibilityService) {
    return agents.stream()
        .filter(Agent::isActive)
        .filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))
        .filter(a -> a.getPrimarySpecialization() != null)
        .filter(Agent::isWorkingDaysKnown)  // NEW: D-07 — exclude data-gap agents
        .collect(Collectors.toList());
}
```

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | BambooHR `/reports/custom` response returns the custom field under the key `customWorkingdays` when field id `4517` is included | Standard Stack, Code Examples | Parser reads wrong JSON key; all values null; no MANDATORY rows generated. Verify against live API response on first deployment. |
| A2 | The `customWorkingdays` value for the 3 StubHub sample agents is representative of the full StubHub-GE desk population | Common Pitfalls §8 | If desk has high blank/Variable rate, D-07 exclusion guts the schedule. Mitigate: add operator warning if data-gap rate > threshold. |
| A3 | `DayOffStatus.APPROVED` is the correct status for MANDATORY rows (not REQUESTED) | Pattern 2, Code Examples | Cosmetic only for the solver (MANDATORY always blocks regardless of status). PTO tab renders MANDATORY rows as red either way. No functional risk. |

---

## Open Questions (RESOLVED)

1. **BambooHR API key rotation**
   - What we know: Key `ad2bb…2be` was pasted in chat 2026-06-02.
   - What's unclear: Whether it has already been rotated since.
   - Recommendation: Make key rotation a Wave 0 / pre-deploy task in the plan.
   - **RESOLVED:** Implemented as plan 06-01 Task 0 — a `[BLOCKING]`, non-autonomous Wave 0 human checkpoint (T-6-SC) with a clean-tree `git grep` acceptance check, gating deploy.

2. **Exact surfacing mechanism for data-gap / outlier flags**
   - What we know: `BambooSyncEvent` already carries `agentsSynced` and `timeOffPulled` counts; the sync status card in `Configuration.tsx` displays these. The full record and DTO are defined.
   - What's unclear: Whether to add `dataGapCount` and `outlierCount` to `BambooSyncEvent` (requires DB migration + DTO change) or just log.warn for this phase and defer to Phase 7 DIAG.
   - Recommendation: For Phase 6, add two nullable Integer fields (`mandatoryDataGapAgents`, `mandatoryOutlierAgents`) to `BambooSyncEvent` + matching DTO and UI display. This is a small DB migration (one ALTER TABLE) and avoids DIAG phase doing a breaking change to a table that could be clean from the start. However, if the planner decides this is premature, log.warn MVP is acceptable — flag as Claude's Discretion.
   - **RESOLVED:** CONTEXT.md marks this Claude's Discretion. Plan 06-03 implements the `log.warn` MVP for data-gap + outlier surfacing and defers the `BambooSyncEvent` schema change to Phase 7 DIAG (stated in 06-03 `<interfaces>`).

3. **DB migration: `workingDaysKnown` flag on Agent**
   - What we know: The Agent entity currently has no working-days field. D-07 exclusion needs a signal.
   - What's unclear: Whether to store the flag on Agent (one column, explicit) or derive it from the absence of MANDATORY rows.
   - Recommendation: Store as `working_days_known BOOLEAN NOT NULL DEFAULT TRUE`. Default TRUE means existing agents (before first refresh) are not incorrectly excluded. After refresh, the flag is set based on `WorkingDaysParser` result.
   - **RESOLVED:** Adopted as-recommended — plan 06-03 Task 1 adds `working_days_known BOOLEAN NOT NULL DEFAULT TRUE` via Flyway `V28` (analog `V25`, DEFAULT TRUE kept permanently) and appends `.filter(Agent::isWorkingDaysKnown)` to `SolverService.filterEligible`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | Flyway migration, AgentDayOff persistence | Confirmed (existing phases work) | 16.x (AWS RDS) | — |
| BambooHR API | Field 4517 pull | Confirmed live (helpware tenant, 2026-06-02 probe) | — | MockBambooHRClient (local dev) |
| Java 17+ | Record components, text blocks | Confirmed (existing codebase uses both) | 17+ | — |
| Spring Boot (existing) | TransactionTemplate, JPA | Confirmed | 3.x | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** BambooHR API not available locally → MockBambooHRClient used for dev/test.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`) |
| Config file | `build.gradle` — `testImplementation 'org.springframework.boot:spring-boot-starter-test'` |
| Quick run command | `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QUAL-01 | Parser: all live BambooHR value formats → correct working days | Unit | `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` | Wave 0 |
| QUAL-01 | Parser: data-gap values (null, blank, "Variable") → empty Optional | Unit | Same | Wave 0 |
| QUAL-01 | Parser: week-wrapping range `Fri-Tue` expands correctly | Unit | Same | Wave 0 |
| QUAL-01 | Parser: trailing annotation `Mon - Sun HOOP` stripped | Unit | Same | Wave 0 |
| QUAL-01 | MANDATORY row generation: off-days produce rows within window | Unit/Integration | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | Extend existing |
| QUAL-01 | MANDATORY rows block solver via buildAgentDaysOffMap | Unit | `./gradlew test --tests "com.wfm.service.SolverServicePtoFilterTest"` | Exists — already passing |
| QUAL-01 | Data-gap agents excluded from filterEligible | Unit | `./gradlew test --tests "com.wfm.service.SolverServiceEligibilityFilterTest"` | Extend existing |
| QUAL-01 | MockBambooHRClient emits customWorkingdays values | Unit | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | Extend existing |
| QUAL-01 | PTO tab renders MANDATORY rows (verify-only) | Manual | Navigate to ScheduleResults > PTO tab post-refresh | Manual |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` — new file, covers REQ QUAL-01 (all parser cases)

*(All other test infrastructure covers phase requirements. `SolverServicePtoFilterTest` already passes and validates the downstream consumer.)*

### WorkingDaysParserTest Design

Use JUnit 5 `@ParameterizedTest` with `@MethodSource` or `@CsvSource`. The test catalog must cover all live-observed values from CONTEXT.md specifics:

```java
// Source: CONTEXT.md specifics + todo live value catalog
@ParameterizedTest
@MethodSource("workingDaysSource")
void parseWorkingDays_allFormats(String input, Set<DayOfWeek> expectedWorking) {
    Optional<Set<DayOfWeek>> result = WorkingDaysParser.parseWorkingDays(input);
    // Variable/blank → empty
    if (expectedWorking == null) {
        assertThat(result).isEmpty();
    } else {
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderElementsOf(expectedWorking);
    }
}

static Stream<Arguments> workingDaysSource() {
    return Stream.of(
        Arguments.of("Mon-Fri",          Set.of(MON,TUE,WED,THU,FRI)),
        Arguments.of("Wed-Sun",          Set.of(WED,THU,FRI,SAT,SUN)),
        Arguments.of("Sun-Thu",          Set.of(SUN,MON,TUE,WED,THU)),
        Arguments.of("Tue-Sat",          Set.of(TUE,WED,THU,FRI,SAT)),
        Arguments.of("Fri-Tue",          Set.of(FRI,SAT,SUN,MON,TUE)),  // week-wrap
        Arguments.of("Mon - Sun",        Set.of(MON,TUE,WED,THU,FRI,SAT,SUN)),  // 0 off-days
        Arguments.of("Mon - Sun HOOP",   Set.of(MON,TUE,WED,THU,FRI,SAT,SUN)),  // annotation
        Arguments.of("Mon. to Thurs.",   Set.of(MON,TUE,WED,THU)),              // "to", periods
        Arguments.of("Mon, Tue, Wed, Thu, Sat", Set.of(MON,TUE,WED,THU,SAT)),   // comma list
        Arguments.of("Variable",         null),     // data gap
        Arguments.of("",                 null),     // blank
        Arguments.of(null,               null)      // null
    );
}
```

---

## Security Domain

> `security_enforcement` not explicitly disabled in config — section required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | BambooHR credentials already handled by existing `basicAuth()` in `HttpBambooHRClient` |
| V3 Session Management | No | No new sessions |
| V4 Access Control | No | No new endpoints |
| V5 Input Validation | Yes | `WorkingDaysParser` must never throw on any BambooHR input — all invalid formats fall through gracefully to data-gap (empty Optional) |
| V6 Cryptography | No | No new crypto |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed BambooHR API response causes parse exception | Tampering / DoS | `WorkingDaysParser` returns empty Optional on any unrecognised input; never throws. Caller treats as data gap. |
| BambooHR API key exposed in logs | Information Disclosure | Do not log `requestBody` or response bodies containing employee data. Existing `log.info` in `HttpBambooHRClient` logs counts only. |
| BambooHR API key already compromised (pasted in chat) | Elevation of Privilege | **Rotate immediately** — pre-deploy blocking requirement. |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| MANDATORY from time-off type string match | MANDATORY from `customWorkingdays` field 4517 | This phase | MANDATORY solver path becomes live in production for the first time |
| Dead code `"MANDATORY".equalsIgnoreCase(type)` at `:263` | Removed | This phase | Clean slate; no confusion about what produces MANDATORY rows |

**Deprecated/outdated after this phase:**
- `"MANDATORY".equalsIgnoreCase(type)` at `BambooRefreshService.java:263` — remove entirely. It was always dead in production (BambooHR time-off types are never the string "MANDATORY").

---

## Sources

### Primary (HIGH confidence — direct code reads in this session)

- `src/main/java/com/wfm/integration/HttpBambooHRClient.java:129-184` — exact request body and JSON parsing for `listEmployees`
- `src/main/java/com/wfm/integration/BambooEmployee.java` — full record (9 components)
- `src/main/java/com/wfm/integration/BambooRefreshService.java:246-297` — `persistRefreshData` day-off section, dedup map, priority logic
- `src/main/java/com/wfm/service/AgentEligibilityService.java` — full class (31 lines)
- `src/main/java/com/wfm/service/SolverService.java:946-980` — `buildAgentDaysOffMap` and `filterEligible` static helpers
- `src/main/java/com/wfm/model/AgentDayOff.java` — entity with unique constraint `(agent_id, date)`
- `src/main/java/com/wfm/model/DayOffType.java`, `DayOffStatus.java` — enums
- `src/main/java/com/wfm/model/BambooSyncEvent.java` — entity fields
- `src/main/java/com/wfm/service/BambooSyncEventService.java` — `record()` uses REQUIRES_NEW propagation
- `src/main/java/com/wfm/dto/BambooSyncEventResponse.java` — DTO fields
- `frontend/src/pages/ScheduleResults.tsx:766-856` — `PtoTab` component, MANDATORY cell rendering
- `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` — test pattern model
- `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` — reflection-based private method test pattern
- `.planning/phases/06-solver-quality-constraints/06-CONTEXT.md` — all decisions D-01..D-10
- `.planning/todos/pending/2026-06-02-import-bamboohr-weekly-work-pattern-for-mandatory-day-offs.md` — live BambooHR probe findings, value catalog

### Secondary (MEDIUM confidence — project planning docs)

- `.planning/REQUIREMENTS.md` — QUAL-01 definition
- `.planning/STATE.md` — phase history and accumulated decisions

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all files read directly; no external dependencies needed
- Architecture: HIGH — data flow confirmed against actual code, not assumed
- Pitfalls: HIGH — derived from concrete code paths and the live BambooHR value catalog
- Parser edge cases: HIGH — catalog of live values confirmed in CONTEXT.md specifics and todo
- Data-gap exclusion mechanism: MEDIUM — Option A (flag on Agent) is a recommendation; exact implementation is Claude's Discretion

**Research date:** 2026-06-02
**Valid until:** 2026-07-02 (stable codebase; BambooHR API field 4517 confirmed live)
