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

## Solution (REVISED 2026-06-02 — much smaller than first thought)

Per Pete: the signal is the time-off **`amount`** under Category=PTO, which we
ALREADY FETCH from `/time_off/requests` but DISCARD:
- amount **0** → mandatory day off (scheduled weekend)
- amount **1** → actual PTO (real leave)

`HttpBambooHRClient.fetchTimeOffByStatus` (HttpBambooHRClient.java:259-272) reads
`dates.fieldNames()` (the date keys) and never reads `dates.get(date)` (the amount)
— the code comment at :258 literally says the map is "keyed by date string →
amount". No new field pull, no work-schedule endpoint, no /meta/fields needed.

1. **Capture the amount**: in `HttpBambooHRClient` read the value at each date key
   (`dates.path(dateStr).asDouble()` / asText) in BOTH the object branch (:260) and
   the array/start-end fallbacks (:273, :285).
2. **Carry it**: add `double amount` (or a derived `boolean mandatory`) to
   `BambooTimeOff` (BambooTimeOff.java).
3. **Map by amount** in `BambooRefreshService` (replacing the dead
   `"MANDATORY".equalsIgnoreCase(type)` match at :263):
   amount==0 → `DayOffType.MANDATORY`; amount>=1 → `DayOffType.PTO`.
4. Solver unchanged: MANDATORY always blocks; PTO blocks only when APPROVED
   (SolverService.java:951-957).
5. Update `MockBambooHRClient` to emit amount-coded entries so the mock matches.

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
