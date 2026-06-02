---
phase: 06-solver-quality-constraints
plan: 02
subsystem: integration
tags: [bamboohr, customWorkingdays, field-4517, data-pipeline, QUAL-01]
dependency_graph:
  requires: ["06-01"]
  provides: ["customWorkingdays on BambooEmployee", "field 4517 in bulk report request", "mock varied values"]
  affects: ["06-03 (BambooRefreshService MANDATORY generation consumes customWorkingdays)", "BambooRefreshServiceTest (test emp() helper update — D-08 sequencing)"]
tech_stack:
  added: []
  patterns: ["positional record component insertion", "asText(null) for nullable JSON fields", "index-modulo mock value variation"]
key_files:
  created: []
  modified:
    - src/main/java/com/wfm/integration/BambooEmployee.java
    - src/main/java/com/wfm/integration/HttpBambooHRClient.java
    - src/main/java/com/wfm/integration/MockBambooHRClient.java
decisions:
  - "asText(null) used (not asText(\"\")) so missing BambooHR field stays null — parser can distinguish data gap from blank string"
  - "getEmployee passes null for customWorkingdays — it does not request field 4517 (bulk report only, per D-02)"
  - "i%5 modulo reuses existing MockBambooHRClient pattern; same switch block duplicated to both indexed loops (listEmployees + buildVintedAgents)"
metrics:
  duration_minutes: 2
  completed_date: "2026-06-02"
  tasks_completed: 4
  tasks_total: 4
  files_changed: 3
---

# Phase 06 Plan 02: BambooHR Field 4517 Integration Layer Summary

BambooHR custom field 4517 (`customWorkingdays`) plumbed through the integration layer: record component added positionally, bulk report request extended with `"4517"`, and mock emits varied realistic values including a data-gap and a 4-day-week outlier.

## Tasks Completed

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Add customWorkingdays to BambooEmployee | 9d6f02b | Insert `String customWorkingdays` between `employmentHistoryStatus` and `wfmTenantId` |
| 2 | Pull field 4517 in HttpBambooHRClient.listEmployees | 3954109 | `"4517"` in fields array; `emp.path("customWorkingdays").asText(null)` per row; pass to constructor |
| 3 | Emit customWorkingdays from MockBambooHRClient | e163730 | All 3 sites updated; i%5 variation; `"Variable"` + `"Mon. to Thurs."` outlier; `getEmployee` fixed `"Mon-Fri"` |
| 4 | Compile-gate (compileJava exits 0) | 032e9fd | `./gradlew compileJava` BUILD SUCCESSFUL; auto-fix for missed `getEmployee` call-site in HttpBambooHRClient |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] HttpBambooHRClient.getEmployee had a missed BambooEmployee call-site**
- **Found during:** Task 4 compile gate — `./gradlew compileJava` reported "actual and formal argument lists differ in length" at line 202
- **Issue:** The plan's `<interfaces>` section listed `HttpBambooHRClient.listEmployees` (~lines 172-176) as the only call-site in HttpBambooHRClient, but `getEmployee` (~line 202) also constructs a `BambooEmployee` positionally. That call-site was not updated in Task 2.
- **Fix:** Passed `null` for `customWorkingdays` in `getEmployee` — the per-employee GET endpoint does not include field 4517 (only the bulk POST `/reports/custom` does, per D-02).
- **Files modified:** `src/main/java/com/wfm/integration/HttpBambooHRClient.java`
- **Commit:** 032e9fd

## Known Stubs

None. All three files deliver complete data for their respective responsibilities. Plan 03 consumes `customWorkingdays()` from `BambooEmployee` — that is the next wave, not a stub in this plan.

## Threat Flags

No new security-relevant surface introduced. Field 4517 value is stored on `BambooEmployee` (in-memory record only — not persisted by this plan). No logging of the value was added; existing count-only logging preserved (T-6-ID compliant). `asText(null)` means a garbled/missing field stays `null` rather than propagating corrupt data (T-6-IV compliant).

## Self-Check

### Verification

- `grep -q "customWorkingdays" BambooEmployee.java` → OK
- `grep -q '"4517"' HttpBambooHRClient.java && grep -q 'customWorkingdays' HttpBambooHRClient.java` → OK
- `grep -q '"Variable"' MockBambooHRClient.java && grep -q 'Mon-Fri' MockBambooHRClient.java` → OK
- `./gradlew compileJava` → BUILD SUCCESSFUL

## Self-Check: PASSED

All task commits verified. `compileJava` exits 0. All three files contain required tokens.
