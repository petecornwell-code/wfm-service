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

## Solution

1. **Discover** the real BambooHR weekday field name(s)/alias via `GET /meta/fields`
   (BambooHR API is public; creds live in prod RDS `app_configuration`:
   `BAMBOOHR_SERVER` + `BAMBOOHR_API_KEY`). Pending — needs the live lookup
   (ECS Exec disabled + RDS private, so run from a machine with the subdomain+key,
   or Pete supplies them).
2. **Pull** the field(s) by adding their IDs/aliases to the custom-report `fields`
   array (`HttpBambooHRClient.java:133-137`).
3. **Model** the weekly pattern on `BambooEmployee` (e.g. a 7-bit working-day mask
   or Set<DayOfWeek> off-days).
4. **Generate** recurring `DayOffType.MANDATORY` `AgentDayOff` rows for each
   employee's non-working weekdays across the schedule horizon (in
   `BambooRefreshService.persistRefreshData`).
5. **Retire** the dead time-off-type string match for MANDATORY at
   `BambooRefreshService.java:263`.

**Phase 6 design implication (reconcile before planning Phase 6):** Phase 6 SC-1
says "every agent gets exactly 2 contiguous days off per week." If each agent's 2
days off are **fixed in BambooHR**, the solver should **respect** them as hard
MANDATORY blocks, not **choose** them — the contiguous-days-off constraint may
then only apply to agents *without* a defined pattern. This changes the Phase 6
solver-constraint design.

## Open questions

- Exact BambooHR field representation: 7 per-day custom fields (Mon–Sun = 1/0)? a
  single "Work Schedule"/"Days Off" field? or BambooHR's built-in work-schedule
  endpoint (`/employees/{id}/tables/...`)? — resolve via `/meta/fields`.
- Does 1 mean "working" or "day off"? Confirm polarity during discovery.
- Are patterns guaranteed to be exactly 2 consecutive days, or can they vary?
