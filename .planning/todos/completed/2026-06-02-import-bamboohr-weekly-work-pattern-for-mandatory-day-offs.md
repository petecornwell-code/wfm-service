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

API-FEASIBILITY FINDING (2026-06-02, BambooHR docs):
BambooHR's standard API does NOT expose work schedules. The field reference
(documentation.bamboohr.com/docs/list-of-field-names) has no work-schedule /
per-weekday / days-off field or table — only `standardHoursPerWeek` (count, not
which days) and `paySchedule` (pay frequency). So if the weekday data is in
BambooHR's built-in "Work Schedule" section (Pete's read), the API can't reach it.
The ONLY API path that could work: the customer created CUSTOM fields
(Monday…Sunday) — those WOULD appear in /meta/fields and be pullable via
/reports/custom. Decisive test = run /meta/fields and look for weekday custom
fields. Also note: production_agents.xlsx mixes BambooHR fields (Employee ID, Name,
Dept, Job Title, Status, Part-Time) with non-BambooHR/WFM columns (Desk,
Monday…Sunday) — suggesting the schedule may be operator-maintained in the upload
template, not a BambooHR API field.

RESOLVED — SOURCE CONFIRMED (live /meta/fields + /reports/custom on helpware tenant, 2026-06-02):
The 7 weekday custom fields (ids 5553-5563, "Monday".."Sunday") EXIST but are
EMPTY for all employees — red herring. The real source is custom field
**"Working days" = field id 4517, report alias `customWorkingdays`** (under
BambooHR Personal → Schedule). Sibling "Shift" = id 4516 / `customShift1` =
work HOURS (e.g. "2 pm - 11 pm"), not relevant to days off.
- Pullable via the EXISTING bulk `POST /reports/custom` — add `"4517"` to the
  fields array; it returns under key `customWorkingdays`. NO per-employee fetch.
  (Direct `GET /employees/{id}?fields=4517` also works.)
- Coverage (company-wide, 11,707 employees): 5,241 populated (45%), 6,466 empty,
  2,432 "Variable". So ~24% have a parseable fixed pattern; Variable/empty → no
  fixed weekend (no MANDATORY; leave to solver/Phase 6).
- VALUE FORMATS ARE FREE-TEXT AND MESSY — parser must handle all:
  - range: "Mon-Fri", "Wed-Sun", "Sun-Thu", "Tue-Sat", "Mon - Sun" (spaces vary;
    ranges WRAP the week, e.g. "Fri-Tue")
  - "to" form: "Mon. to Thurs." (periods, "to", "Thurs.")
  - comma list: "Mon, Tue, Wed, Thu, Sat" (explicit working days)
  - annotations/suffixes: "Mon - Sun HOOP"
  - "Variable" → no fixed schedule (skip)
  - day tokens vary: Mon/Mon./Thu/Thur/Thurs. → normalize.
- NOT always 2 consecutive days off: 4-day weeks ("Mon. to Thurs." → 3 off),
  7-day weeks ("Mon - Sun" → 0 off), and non-consecutive ("Mon,Tue,Wed,Thu,Sat"
  → off Fri+Sun) all occur. Rule = days off = {Mon..Sun} MINUS parsed working days.

Plan:
1. Add `4517` to the `/reports/custom` fields in `HttpBambooHRClient.listEmployees`
   (:133-137); read `customWorkingdays` per row.
2. Add a tolerant working-days parser → Set<DayOfWeek> off-days (handle range/wrap/
   "to"/comma/annotation/Variable/empty per formats above).
3. Carry off-days on `BambooEmployee`; persist per agent.
4. In `BambooRefreshService`, generate recurring `DayOffType.MANDATORY` AgentDayOff
   rows for each agent's off weekdays across the schedule horizon (replacing the
   dead `"MANDATORY".equalsIgnoreCase(type)` match at :263).
5. Solver unchanged: MANDATORY always blocks; PTO blocks only when APPROVED
   (SolverService.java:951-957). Update MockBambooHRClient to emit a customWorkingdays-style value.

FALLBACK (no longer needed but proven): the enriched desk-upload Monday…Sunday
columns also carry this ("Weekend"/empty) and are discarded
(DeskAssignmentUploadService.java:117-119 vs :165+).

SECURITY: BambooHR API key for helpware was pasted into chat 2026-06-02
(ad2bb…2be) — ROTATE IT.

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
