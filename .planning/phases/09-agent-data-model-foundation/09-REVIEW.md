---
phase: 09-agent-data-model-foundation
reviewed: 2026-07-30T22:16:31Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - src/main/java/com/wfm/dto/AgentResponse.java
  - src/main/java/com/wfm/dto/DeskAgentResponse.java
  - src/main/java/com/wfm/integration/BambooRefreshService.java
  - src/main/java/com/wfm/model/Agent.java
  - src/main/java/com/wfm/model/AgentDayHours.java
  - src/main/java/com/wfm/repository/AgentDayHoursRepository.java
  - src/main/java/com/wfm/service/AgentService.java
  - src/main/java/com/wfm/service/ClientManagementService.java
  - src/main/java/com/wfm/service/DeskAgentExportService.java
  - src/main/java/com/wfm/service/DeskAgentService.java
  - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/util/AgentNameSplitter.java
  - src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql
  - src/test/java/com/wfm/model/AgentDayHoursPersistenceTest.java
  - src/test/java/com/wfm/model/AgentNamePersistenceTest.java
  - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
  - src/test/java/com/wfm/service/SolverServiceEffectiveHoursResolutionTest.java
  - src/test/java/com/wfm/util/AgentNameSplitterTest.java
findings:
  critical: 2
  warning: 4
  info: 3
  total: 9
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-07-30T22:16:31Z
**Depth:** standard
**Files Reviewed:** 22
**Status:** issues_found

## Summary

Phase 9 introduces the per-field name model (`firstName`/`lastName` via `AgentNameSplitter`),
the `agent_day_hours` per-weekday contracted-hours child table, and the D-03/D-04 effective-hours
resolution chain in `SolverService`. The core resolution logic (`resolveEffectiveHours`,
`buildAgentDaysOffMap`, `filterEligible`) is well factored and well tested, and the V29 migration
carefully mirrors the Java splitter.

However, two paths that write the new data are unguarded against realistic inputs and will throw
at runtime (a `NOT NULL` violation and a POI negative-cell-index crash — both empirically confirmed).
There is also a write-site that was missed by the "populate first/last name everywhere" change, so
agents created through that path silently persist `null` names. Details below.

## Critical Issues

### CR-01: `setContractedHours(null)` throws `NOT NULL` violation and breaks reset-to-default

**File:** `src/main/java/com/wfm/service/DeskAgentService.java:184-212`
**Issue:** `SetContractedHoursRequest.contractedHoursPerDay` is not validated (no `@NotNull`, no
`@Valid` on the controller at `DeskAgentController.java:76-81`), so a client sending `{}` or
`{"contractedHoursPerDay": null}` passes `null` straight through. `BigDecimals.normalize(null)`
returns `null` (confirmed in `BigDecimals.java`), then the fan-out loop unconditionally creates 7
`AgentDayHours` rows with `hours = null`. `AgentDayHours.hours` is `@Column(nullable = false)`
(`AgentDayHours.java:29`), so the flush raises a `DataIntegrityViolationException` → HTTP 500.
Before this phase, a `null` value simply cleared the scalar (a legitimate "revert to desk default"
operation); the new fan-out makes that operation crash. `DeskAgentServiceContractedHoursTest` only
exercises non-null values, so the regression is untested.
**Fix:** Guard the fan-out and/or validate input. For example:
```java
BigDecimal normalized = BigDecimals.normalize(hours);
agent.setContractedHoursPerDay(normalized);
Agent saved = agentRepository.save(agent);

agentDayHoursRepository.deleteByAgent_Id(agentId);
agentDayHoursRepository.flush();
if (normalized != null) {                    // null = revert to desk default: leave 0 rows
    for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(saved.getTenantId());
        dayHours.setAgent(saved);
        dayHours.setDayOfWeek(dayOfWeek);
        dayHours.setHours(normalized);
        agentDayHoursRepository.save(dayHours);
    }
}
```
Also add `@NotNull`/`@Positive` on the request record (or a range check) and `@Valid` on the
controller parameter so bad input returns 400, not 500.

### CR-02: Missing optional column crashes the whole upload with a negative cell index

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:175-178`
**Issue:** Column indices are resolved with `col.getOrDefault(name, -1)` and passed straight into
`row.getCell(...)` with no `>= 0` guard for `bamboohrIdCol`, `"name"`, `"email"`, and `deskCol`.
Shape detection (lines 122-137) only requires `"desk assignment"` (legacy) or `"desk"+"monday"+"sunday"`
(enriched); it does **not** require the BambooHR-ID / Name / Email columns. So a spreadsheet that is
detected as legacy but lacks, say, an "Email" column yields index `-1`, and `row.getCell(-1)` throws
`IllegalArgumentException: Cell index must be >= 0` (empirically confirmed against POI 5.5.1). The
exception is unhandled and aborts the entire upload with an HTTP 500 instead of skipping rows. Note
the earlier desk-clearing loop (line 161-162) *does* guard with `deskColIdx >= 0`, so the omission in
the main read loop is inconsistent as well as incorrect.
**Fix:** Add a null-safe cell reader that tolerates `-1`, e.g.:
```java
private String cellAt(Row row, Map<String,Integer> col, String header) {
    int idx = col.getOrDefault(header, -1);
    return idx >= 0 ? getCellString(row.getCell(idx)) : null;
}
```
and use it for every column lookup. Alternatively validate up front that all required columns for the
detected shape are present and throw a descriptive `IllegalArgumentException` before the row loop.

## Warnings

### WR-01: `assignEmployeesToDesk` never populates `firstName`/`lastName`

**File:** `src/main/java/com/wfm/service/ClientManagementService.java:271-302`
**Issue:** This is a fourth agent write-site (it calls `agent.setName(emp.displayName())` for both
new and existing agents) but it does not call `AgentNameSplitter.split(...)`, unlike
`BambooRefreshService` (line 213-215) and `DeskAssignmentUploadService` (lines 282-284, 310-312).
The `AgentNameSplitterTest` doc even asserts the splitter is "reused by every Java write-site". Agents
created/assigned through this path persist `null` `first_name`/`last_name`, and the `AgentResponse`
built at line 299-302 returns nulls — inconsistent with the phase's whole objective.
**Fix:** After `agent.setName(emp.displayName())`, add:
```java
AgentNameSplitter.Split split = AgentNameSplitter.split(emp.displayName());
agent.setFirstName(split.firstName());
agent.setLastName(split.lastName());
```

### WR-02: No validation of negative/zero contracted hours

**File:** `src/main/java/com/wfm/service/DeskAgentService.java:184-208`
**Issue:** Beyond the null case (CR-01), a negative value (e.g. `-5`) is accepted, normalized, and
fanned out to all 7 `agent_day_hours` rows. `SolverService.resolveEffectiveHours` then returns the
negative value and `computeAgentDayConfigs` silently drops the day (`compareTo(ZERO) <= 0`), so the
agent becomes silently unschedulable with no error surfaced to the operator.
**Fix:** Reject values `<= 0` (and optionally an upper bound / increment-multiple check) with a 400,
e.g. `@Positive` on the request field plus a service-side guard.

### WR-03: `AgentNameSplitter` and V29 backfill only split on ASCII space

**File:** `src/main/java/com/wfm/util/AgentNameSplitter.java:22` (and `V29__...sql:23-27`)
**Issue:** `trimmed.indexOf(' ')` and the SQL `position(' ' IN ...)` split solely on U+0020. A display
name delimited by a tab or non-breaking space ("First\tLast") is treated as a single token, yielding
the entire string as `firstName` and an empty `lastName`. Java and SQL agree with each other (so no
drift), but both diverge from the documented intent ("first whitespace token"). Low likelihood for
BambooHR data, but worth pinning.
**Fix:** Split on `\s+` in Java (`trimmed.split("\\s+", 2)`) and mirror with a `regexp`-based split in
SQL if the whitespace-tolerant behavior is desired, or update the Javadoc/comment to say "first
space-delimited token" to match reality.

### WR-04: New agents created via upload are schedulable 7 days/week by default

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:276-290`
**Issue:** Agents created from the BambooHR cache during upload default to
`workingDaysKnown = true`, `employmentType = FULL_TIME`, and get no `AgentDayOff` MANDATORY rows and
no `agent_day_hours` rows. Unlike the BambooHR refresh path (which sets `workingDaysKnown = false`
for blank/Variable patterns, `BambooRefreshService.java:272-278`), an upload-created agent with
unknown working days will be treated as available every weekday. This can put agents into the solver
with no genuine day-off pattern.
**Fix:** Consider defaulting `workingDaysKnown = false` for upload-created agents until a BambooHR
refresh populates their pattern, or document that upload-created agents require a subsequent refresh
before solving.

## Info

### IN-01: Header scan reads one cell past the end of the row

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:112`
**Issue:** `for (int c = 0; c <= headerRow.getLastCellNum(); c++)` — POI's `getLastCellNum()` returns
`lastCellIndex + 1`, so the loop reads one non-existent trailing cell. `getCell` returns `null` for a
positive out-of-range index (no throw), so this is harmless, but the bound should be `<` not `<=`.
**Fix:** Use `c < headerRow.getLastCellNum()`.

### IN-02: V29 `trim` character set narrower than Java `String.trim()`

**File:** `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql:30`
**Issue:** `trim(both E' \t\n\r' FROM ...)` strips space/tab/LF/CR, whereas Java `String.trim()` strips
all chars `<= 0x20` (e.g. form-feed `\f`, vertical tab). A name with such an exotic leading/trailing
control char would backfill slightly differently from `AgentNameSplitter`. Practically negligible for
BambooHR display names.
**Fix:** Optional — none needed unless exact byte-parity with `String.trim()` is required.

### IN-03: `syncEvent.setAgentsSynced(employees.size())` counts all BambooHR employees, not desk matches

**File:** `src/main/java/com/wfm/integration/BambooRefreshService.java:128`
**Issue:** `agentsSynced` is set to the full BambooHR response size, but the refresh only updates
agents already assigned to the desk and matched by `bamboohrId` (lines 206-236). The recorded count
can substantially overstate how many desk agents were actually synced, which is misleading for the
sync-event audit trail.
**Fix:** Track and record the count of actually-updated desk agents (`refreshedAgentIds.size()`).

---

_Reviewed: 2026-07-30T22:16:31Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
