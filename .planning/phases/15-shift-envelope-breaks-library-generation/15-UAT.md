---
status: testing
phase: 15-shift-envelope-breaks-library-generation
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md, 15-03-SUMMARY.md, 15-04-SUMMARY.md, 15-05-SUMMARY.md, 15-06-SUMMARY.md, 15-07-SUMMARY.md, 15-08-SUMMARY.md]
started: 2026-08-27T13:10:00Z
updated: 2026-08-27T13:10:00Z
---

<!--
WHY THIS FILE EXISTS

The automated suite (79 classes / 505 tests / 0 failures) runs against H2 with
`ddl-auto: create-drop` and `spring.flyway.enabled: false`. The test schema is generated
from the JPA entities, NOT from the migration files. Production runs Postgres with
`ddl-auto: validate` and Flyway enabled.

Consequence: **V40, V41, V42 and V43 have never been executed by any test.**
`MigrationEntityConsistencyTest` is a static regex reconciliation of DDL text against entity
mappings — a real guard, but it cannot catch Postgres-specific syntax, constraint violations
on real rows, or an incorrect data fan-out.

The deploy is therefore the first real execution of all four migrations, and V40 is
irreversible. Tests 1-3 exist to catch that before it matters. Everything after them covers
behaviour the suite proves in-memory but has never seen against real data at real scale.
-->

## Current Test

number: 1
name: Pre-deploy snapshot captured
expected: |
  Before deploying, you have a restorable snapshot of the `shift_template` table (at minimum
  the columns `id`, `name`, `break_offset_minutes`, `break_duration_minutes`), because V40
  DROPs the last two after fanning their data into the new `shift_template_break_band` table.
  If the fan-out is wrong, the source data is already gone.
awaiting: user response

## Tests

### 1. Pre-deploy snapshot captured
expected: A restorable copy of `shift_template` exists (including `break_offset_minutes` and `break_duration_minutes`) taken BEFORE the deploy. Ideally the migration has also been rehearsed against a restored copy of production rather than run first on live data.
result: [pending]

### 2. All four migrations apply cleanly on Postgres
expected: The app starts and Flyway reports V40, V41, V42, V43 applied with no error. `SELECT version, success FROM flyway_schema_history WHERE version IN ('40','41','42','43');` shows `success = true` for all four. No `ddl-auto: validate` failure on startup — a validate error here means a migration and its entity disagree, which is exactly the class of bug the static guard is meant to prevent but has never been proven against a real schema.
result: [pending]

### 3. V40 data fan-out preserved every existing break
expected: Every pre-existing template that had a non-zero `break_duration_minutes` now has exactly ONE band carrying its former offset and duration; templates that had no break have zero bands. Nothing was lost or duplicated. Compare against the Test 1 snapshot:

```sql
SELECT t.id, t.name, count(b.id) AS bands,
       min(b.offset_minutes) AS offset_min, min(b.duration_minutes) AS dur_min
FROM shift_template t
LEFT JOIN shift_template_break_band b ON b.shift_template_id = t.id
GROUP BY t.id, t.name
ORDER BY bands DESC, t.name;
```
result: [pending]

### 4. No Phase 14 desk's validation verdict moved
expected: For a desk that existed before this deploy and uses single-break templates, the Shift Library validation result — coverage verdict, net hours, and grid-alignment verdict — is identical to what it showed before. This is the phase's own stated invariant: a one-band template must reproduce its single-offset predecessor exactly.
result: [pending]

### 5. Break-band editor saves and reads back multiple bands
expected: On the Shift Library page, a template can be given two or more break bands. After save and page reload, all bands persist and are displayed **ordered by offset ascending** — the same order in the editor, the value range, and the template list.
result: [pending]

### 6. Band capacity: 0 rejected, blank means unlimited
expected: Saving a band with capacity `0` is refused with a clear message (a band nobody can use is a data error). Leaving capacity BLANK is accepted and means unlimited — blank and 0 are not the same value.
result: [pending]

### 7. Duplicate bands rejected, touching bands allowed
expected: Two bands on one template with the SAME offset AND the SAME duration are refused as duplicates. Two bands whose break windows merely touch (band A's break ends exactly when band B's begins) are accepted as distinct and legal.
result: [pending]

### 8. Off-grid band offsets are refused, never silently rounded
expected: A band offset or duration that does not land on the desk's timeslot grid produces a hard 400 with a named, readable message identifying the problem. The value is never silently rounded to fit.
result: [pending]

### 9. Capacity shortfall shows an advisory at save time
expected: When a template's band capacities total below the shift's admissible headcount, the operator sees a named advisory in the saved-template list (the Capacity column) at save time — not a bare hard score at solve time.
result: [pending]

### 10. Suggested Library returns a draft and writes nothing
expected: Requesting a suggested library for a desk returns an editable draft of templates with bands, derived from that desk's demand and its agents' contracted hours. **Nothing is persisted until you explicitly save a row.** Navigating away without saving leaves the library unchanged. Requesting twice for an unchanged desk returns the same suggestion (it is deterministic).
result: [pending]

### 11. Shift-mode solve succeeds at production scale
expected: A real desk switched to shift-scheduled mode solves to a feasible schedule in acceptable time. Note the automated benchmark ran only 4 agents x 2 days — this is the first exercise at your real agent count, day count and demand curve.
result: [pending]

### 12. No agent is seated outside their assigned shift envelope
expected: In the solved schedule, every agent works only within the envelope of the single shift assigned to them that day. Each working agent-day has exactly one shift. This is the phase's core hard-constraint guarantee.
result: [pending]

### 13. Breaks are distributed across bands, not simultaneous
expected: On a shift with multiple break bands, agents sharing that shift are spread across the bands rather than all breaking at once, and no band exceeds its capacity. This is the phase's headline user-visible behaviour change.
result: [pending]

### 14. Agent Allocation groups by shift on a shift desk
expected: On a shift-scheduled desk, the Agent Allocation view in Schedule Results groups agents under the shift they were assigned, each group naming the shift and its headcount.
result: [pending]

### 15. A slot-scheduled desk is completely unchanged
expected: A desk still in slot mode behaves exactly as before this phase — same Agent Allocation rendering, same solve behaviour, same validation. No shift-mode UI appears anywhere on it.
result: [pending]

### 16. An UPCOMING template is not assignable (CR-01 fix)
expected: A shift template whose `effectiveFrom` is in the FUTURE is NOT assigned to any agent-day before that date. If the template becomes effective partway through the schedule period, it is assignable only on dates from `effectiveFrom` onward — not on earlier days of the same schedule. A RETIRED template (past `effectiveTo`) is likewise not assignable. *This defect shipped in the first pass and was fixed late; it is newly written code with no field exposure.*
result: [pending]

### 17. Accepted schedule keeps its true mode (CR-02 fix)
expected: Accept a shift-mode schedule, then reopen it — Schedule Results reports SHIFT and renders the shift view. Critically, this must hold even for a schedule accepted with few or NO placed shifts, and must NOT change if the desk's scheduling mode is switched afterwards: the accepted schedule records the mode it was solved under. *Legacy caveat: schedules accepted BEFORE this deploy are backfilled by inference, so a pre-existing shift-mode accept that placed zero shifts will read SLOT. That is unrecoverable, not a new bug — verify only that post-deploy accepts are exact.*
result: [pending]

### 18. Deleting an accepted shift schedule leaves no orphans (CR-03 fix)
expected: Delete an accepted shift-mode schedule, then confirm no rows remain for it:
```sql
SELECT count(*) FROM agent_shift_assignment WHERE schedule_id = '<deleted-schedule-id>';
```
Expected: `0`.
result: [pending]

## Summary

total: 18
passed: 0
issues: 0
pending: 18
skipped: 0
blocked: 0

## Gaps
