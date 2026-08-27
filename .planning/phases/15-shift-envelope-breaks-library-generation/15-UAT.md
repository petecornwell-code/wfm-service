---
status: testing
phase: 15-shift-envelope-breaks-library-generation
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md, 15-03-SUMMARY.md, 15-04-SUMMARY.md, 15-05-SUMMARY.md, 15-06-SUMMARY.md, 15-07-SUMMARY.md, 15-08-SUMMARY.md]
started: 2026-08-27T13:10:00Z
updated: 2026-08-27T13:55:00Z
---

<!--
WHERE TO TEST

Phase 15 is ALREADY DEPLOYED to the dev environment and healthy:

  https://d2bbtcc80peap7.cloudfront.net        (/actuator/health -> UP, Postgres connected)

Deployed commit: `adaad6d` — the merge of both defect-fix worktrees. Verified to contain NO
source difference from the phase's final HEAD (`git diff adaad6d..HEAD -- . ':!.planning/'` is
empty); every commit after it touches only `.planning/**`, which `deploy.yml` excludes via
`paths-ignore`. So the live dev service runs exactly the code this phase verified, including
the CR-01/CR-02/CR-03 fixes.

WHAT THE AUTOMATED SUITE DOES AND DOES NOT COVER

The suite (79 classes / 505 tests / 0 failures) runs against H2 with `ddl-auto: create-drop`
and `spring.flyway.enabled: false` — the test schema is generated from the JPA entities, so
**no test executes V40-V43**. `MigrationEntityConsistencyTest` is a static regex reconciliation
of DDL text against entity mappings: a real guard, but it cannot catch Postgres-specific
syntax, constraint violations on real rows, or an incorrect data fan-out.

However — and this CORRECTS the original framing of this file — the migrations have now been
executed for real. Four successful dev deploys applied V40, V41, V42 and V43 against real
Postgres. Production runs `ddl-auto: validate` with Flyway enabled, so a migration disagreeing
with its entity mapping would fail startup and take the health check with it. It didn't. Dev is
therefore a successful rehearsal, not an untested leap.

What that does NOT establish is that the V40 data fan-out produced the RIGHT rows — "the
migration ran" and "the data is correct" are different claims. Test 1 is the one that closes
that gap, and it is the first thing to run.

Production remains genuinely untested, and V40's `DROP COLUMN` is irreversible there. Test 18
covers that and stays blocked until a production deploy is actually planned.
-->

## Current Test

number: 1
name: V40 data fan-out preserved every existing break (dev)
expected: |
  Against the dev database, every pre-existing template that had a non-zero
  `break_duration_minutes` now has exactly ONE band carrying its former offset and duration;
  templates that had no break have zero bands. Nothing lost, nothing duplicated.
awaiting: user response

## Tests

### 1. V40 data fan-out preserved every existing break (dev)
expected: The migration ran (see Test 2) — this checks it produced the RIGHT rows, which is a separate claim. Run against dev:

```sql
SELECT t.id, t.name, count(b.id) AS bands,
       min(b.offset_minutes) AS offset_min, min(b.duration_minutes) AS dur_min
FROM shift_template t
LEFT JOIN shift_template_break_band b ON b.shift_template_id = t.id
GROUP BY t.id, t.name
ORDER BY bands DESC, t.name;
```
Every template that previously had a break shows exactly 1 band with its former offset/duration; templates without a break show 0. **This is the highest-value item in the file** — it is the one thing about V40 that deploy success cannot tell you, and the source columns are already dropped.
result: [pending]

### 2. All four migrations apply cleanly on Postgres (dev)
expected: V40-V43 apply with no error and the app starts under `ddl-auto: validate`.
result: pass
reported: "Evidenced by deploy history, not by manual test: four successful dev deploys (runs at 00:27, 02:18, 05:07, 11:51, 12:42 UTC on 2026-08-27), with /actuator/health reporting UP and db UP on PostgreSQL. Under ddl-auto=validate + flyway.enabled=true, a migration/entity disagreement fails startup, so a passing health check is positive evidence. Confirm if desired with: SELECT version, success FROM flyway_schema_history WHERE version IN ('40','41','42','43');"

### 3. No Phase 14 desk's validation verdict moved
expected: For a desk that existed before this phase and uses single-break templates, the Shift Library validation result — coverage verdict, net hours, grid-alignment verdict — is identical to before. This is the phase's own stated invariant: a one-band template must reproduce its single-offset predecessor exactly.
result: [pending]

### 4. Break-band editor saves and reads back multiple bands
expected: On the Shift Library page, a template can be given two or more break bands. After save and reload, all bands persist and display **ordered by offset ascending** — same order in the editor, the value range, and the template list.
result: [pending]

### 5. Band capacity: 0 rejected, blank means unlimited
expected: Capacity `0` is refused with a clear message (a band nobody can use is a data error). BLANK capacity is accepted and means unlimited. Blank and 0 are not the same value.
result: [pending]

### 6. Duplicate bands rejected, touching bands allowed
expected: Two bands with the SAME offset AND SAME duration are refused as duplicates. Two bands whose break windows merely touch (A ends exactly as B begins) are accepted as distinct and legal.
result: [pending]

### 7. Off-grid band offsets refused, never silently rounded
expected: An offset or duration not landing on the desk's timeslot grid produces a hard 400 with a named, readable message. The value is never silently rounded to fit.
result: [pending]

### 8. Capacity shortfall shows an advisory at save time
expected: When band capacities total below the shift's admissible headcount, the operator sees a named advisory in the Capacity column at save time — not a bare hard score at solve time.
result: [pending]

### 9. Suggested Library returns a draft and writes nothing
expected: Requesting a suggested library returns an editable draft derived from the desk's demand and its agents' contracted hours. **Nothing is persisted until a row is explicitly saved.** Navigating away leaves the library unchanged. Requesting twice for an unchanged desk returns the same suggestion (it is deterministic).
result: [pending]

### 10. Shift-mode solve succeeds at production scale
expected: A real desk in shift-scheduled mode solves to a feasible schedule in acceptable time. The automated benchmark ran only 4 agents x 2 days — this is the first exercise at your real agent count, day count and demand curve.
result: [pending]

### 11. No agent is seated outside their assigned shift envelope
expected: Every agent works only within the envelope of the single shift assigned to them that day; each working agent-day has exactly one shift. This is the phase's core hard-constraint guarantee.
result: [pending]

### 12. Breaks are distributed across bands, not simultaneous
expected: On a shift with multiple bands, agents sharing that shift are spread across bands rather than all breaking at once, and no band exceeds its capacity. This is the headline user-visible behaviour change.
result: [pending]

### 13. Agent Allocation groups by shift on a shift desk
expected: On a shift-scheduled desk, Agent Allocation in Schedule Results groups agents under their assigned shift, each group naming the shift and its headcount.
result: [pending]

### 14. A slot-scheduled desk is completely unchanged
expected: A desk still in slot mode behaves exactly as before — same Agent Allocation rendering, same solve behaviour, same validation. No shift-mode UI appears on it.
result: [pending]

### 15. UPCOMING and RETIRED templates are not assignable (CR-01 fix)
expected: A template whose `effectiveFrom` is in the FUTURE is not assigned to any agent-day before that date. If it becomes effective partway through the schedule period, it is assignable only from `effectiveFrom` onward — not on earlier days of the same schedule. A RETIRED template (past `effectiveTo`) is likewise never assignable. *Newly written code, fixed after the main phase, with the least field exposure of anything here.*
result: [pending]

### 16. Accepted schedule keeps its true mode (CR-02 fix)
expected: Accept a shift-mode schedule, reopen it — Schedule Results reports SHIFT and renders the shift view. This must hold even for a schedule accepted with few or NO placed shifts, and must not change if the desk's mode is switched afterwards. *Legacy caveat: schedules accepted BEFORE this deploy are backfilled by inference, so a pre-existing shift-mode accept that placed zero shifts will read SLOT. That is unrecoverable — the true fact was never recorded — not a new bug. Verify only that post-deploy accepts are exact.*
result: [pending]

### 17. Deleting an accepted shift schedule leaves no orphans (CR-03 fix)
expected: Delete an accepted shift-mode schedule, then confirm no rows remain:
```sql
SELECT count(*) FROM agent_shift_assignment WHERE schedule_id = '<deleted-schedule-id>';
```
Expected: `0`.
result: [pending]

### 18. Production migration executed safely
expected: Before deploying to PRODUCTION, a restorable snapshot of `shift_template` exists (including `break_offset_minutes` and `break_duration_minutes`), because V40 DROPs both after fanning their data out — if the fan-out is wrong there, the source data is already gone. After the production deploy, Test 1's fan-out query is re-run against production and passes. Dev's four successful runs are a rehearsal, not a substitute: production has different data volume, different pre-existing rows, and different edge cases.
result: blocked
blocked_by: server
reason: No production deploy has occurred. `deploy.yml` targets the dev environment (ECS cluster `wfm-service-dev`) only. Unblock when a production deploy is planned.

## Summary

total: 18
passed: 1
issues: 0
pending: 16
skipped: 0
blocked: 1

## Gaps
