---
created: 2026-06-02T19:23:24Z
title: Import BambooHR weekly work pattern for MANDATORY day-offs
area: integration
files:
  - src/main/java/com/wfm/integration/BambooRefreshService.java:263
  - src/main/java/com/wfm/integration/HttpBambooHRClient.java:130-137
  - src/main/java/com/wfm/integration/BambooEmployee.java
  - src/main/java/com/wfm/service/SolverService.java:951-957
  - src/main/java/com/wfm/integration/MockBambooHRClient.java:165
---

## Problem

`DayOffType.MANDATORY` day-offs are **never imported from real BambooHR**, so the
solver's "MANDATORY always blocks" path is effectively dead in production.

**Intended meaning (per Pete, 2026-06-02):** Every BambooHR employee has a fixed
weekly work pattern — 5 working days + 2 *consecutive* non-working days (their
personal "weekend", which can be any two consecutive days). Those 2 recurring days
are the MANDATORY day-offs. BambooHR is believed to store this as a per-weekday
**1/0 flag** (working/non-working).

**Root cause:** The integration only reads `/time_off/requests` (PTO/vacation/sick).
`BambooRefreshService.java:263` tries to detect MANDATORY by exact string match on
the time-off **type name** (`"MANDATORY".equalsIgnoreCase(type)`), which never
matches real BambooHR data (real types are PTO/Vacation/Sick/Holiday). The
employee's **weekly work pattern is a different BambooHR surface and is never
pulled** — the employee custom-report fetch (`HttpBambooHRClient.java:130-137`)
requests only id, displayName, workEmail, department, jobTitle, status,
employmentHistoryStatus.

**Effect:** `SolverService.buildAgentDaysOffMap()` (`SolverService.java:951-957`)
already treats `MANDATORY` as "always blocks, any status" — fully built but dead.
Only `MockBambooHRClient.java:165` ever produces MANDATORY, so this passed UAT
under the mock but does nothing in prod.

## Solution (REVISED-2 2026-06-02 — real source found: the desk-upload spreadsheet)

The weekly pattern is ALREADY in the enriched desk-assignment upload (a BambooHR
export operators upload), in the **Monday…Sunday columns** — and the parser
discards them. Confirmed against `src/main/resources/sample-data/production_agents.xlsx`:
each weekday cell is empty (= working) or contains a marker string `Weekend`
(= that employee's mandatory day off). Varies per employee (any two days).

`DeskAssignmentUploadService` REQUIRES monday+sunday headers for ENRICHED_16COL
shape detection (DeskAssignmentUploadService.java:117-119) but the row loop
(:165+) only reads bamboohrId/name/email/desk/specialty — the day columns are
never read. No BambooHR API change, no /meta/fields, no prod creds.

**DIRECTION (Pete 2026-06-02): pull from BambooHR API directly — not the upload.**
The Monday…Sunday "Weekend" columns in production_agents.xlsx are a BambooHR
*export*, which proves BambooHR holds the per-employee weekly schedule. Preferred
source of truth is the API (automated sync, no manual upload dependency).

Rejected:
- time-off `amount==0` (Pete confirmed): only appears INSIDE a PTO date range →
  employees with no upcoming leave get no mandatory blocks. Incomplete.

Open investigation — HOW is the schedule exposed by the BambooHR API?
- Likely 7 custom fields (Monday…Sunday, value "Weekend"/empty) → discover via
  `GET /meta/fields/` and pull by adding their IDs to the existing
  `POST /reports/custom` fields array (HttpBambooHRClient.java:133-137).
- OR a BambooHR work-schedule **table** → discover via `GET /meta/tables/`, pull
  via `/employees/{id}/tables/{table}`.
- BLOCKER: discovery needs subdomain+API key (prod RDS app_configuration;
  RDS private + ECS Exec disabled). Pete to run /meta lookup or supply creds.

Plan once fields known:
1. Add the weekday field(s)/table to the BambooHR pull (HttpBambooHRClient +
   BambooEmployee weekly-pattern field).
2. In `BambooRefreshService`, generate recurring `DayOffType.MANDATORY` AgentDayOff
   rows for each employee's off weekdays across the schedule horizon (replacing the
   dead `"MANDATORY".equalsIgnoreCase(type)` match at :263).
3. Solver unchanged: MANDATORY always blocks; PTO blocks only when APPROVED
   (SolverService.java:951-957).

FALLBACK (proven, no creds needed): parse the discarded Monday…Sunday columns the
enriched desk-upload already requires (DeskAssignmentUploadService.java:117-119 vs
:165+ which never reads them). Use only if the API can't expose the schedule.

**Phase 6 design implication (reconcile before planning Phase 6):** Phase 6 SC-1
says "every agent gets exactly 2 contiguous days off per week." If each agent's 2
days off are **fixed in BambooHR**, the solver should **respect** them as hard
MANDATORY blocks, not **choose** them — the contiguous-days-off constraint may
then only apply to agents *without* a defined pattern. This changes the Phase 6
solver-constraint design.

## Open questions

- **COMPLETENESS (blocking):** amount-0 rows only exist INSIDE a time-off request's
  date range. Does BambooHR record amount-0 weekend rows for an employee EVERY week,
  or only for weekends that fall within/next to a PTO request? If only near PTO, we'd
  miss most mandatory days off for employees with no upcoming leave — and would then
  need a work-schedule field after all (back to /meta/fields). Verify against the
  spreadsheet before implementing.
- Polarity confirmed (Pete 2026-06-02): amount 0 = mandatory day off, amount 1 = PTO.
- Confirm amount granularity: is it always 0/1, or can it be fractional/hours
  (e.g. 0.5, 8)? Affects the `>=1 → PTO` rule.
