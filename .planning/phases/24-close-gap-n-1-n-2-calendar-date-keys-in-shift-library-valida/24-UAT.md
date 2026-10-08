---
status: complete
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
source: [24-VERIFICATION.md]
started: 2026-10-07T19:01:04Z
updated: 2026-10-08T13:22:25.604Z
---

## Current Test

[testing complete]

## Tests

### 1. D7 per-call state isolation (see 24-VERIFICATION.md behavior_unverified_items)
expected: Concurrent or successive calls on different desks do not interfere; no map or field survives a call
result: pass
evidence: "Added CallIsolation.assertNoStateSurvivesACall plus a holdsNoStateAcrossCalls_* test in ScheduleEnvelopeRepairServiceTest, ScheduleConsistencyRepairServiceTest and ShiftStartMixTargetServiceTest (two desks, isolated baseline, 5 alternating rounds, 8 threads x 10 concurrent runs on one instance). All 32 tests in the three suites pass; mutation hoisting ShiftStartMixTargetService.reqByDate to an instance field turns the new test red."

### 2. Agent Allocation Required / Over-under rows on a 06:00-anchored desk
expected: For post-midnight slots on a middle business day and on the FINAL business day, Required equals the demand seeded for that business day's calendar-next-day hours, and the final day's post-midnight cells are populated, not blank
result: pass
evidence: "Operator read the Agent Allocation tab on a fresh throwaway stack (DB 55432, app 8081, vite 3001; 06:00 desk, 22:00-03:00 slots, business Mon-Wed 2026-10-05..07, demand 1,1,2,3,2 / 2,2,4,5,4 / 3,3,6,7,6). Required matched all 15 cells incl. final-day 6/7/6 post-midnight; Over/under = total - required in every column. Stack torn down after."

### 3. Six judgment-tier prohibitions in 24-VERIFICATION.md
expected: Operator confirms each MUST NOT held
result: pass
evidence: "Operator confirmed all six. Spot-checked: allowlist empty; Item.date calendar vs businessDate observed live (2026-10-08 / 2026-10-07); frontend commit 0ddaffa is a param and key swap only; today's stack used 55432/8081/3001 only."

### 4. 24-03 phase-gate shortfall closed
expected: Accept the orchestrator's post-24-03 clean full-suite run (218 XML files, 1535 tests, 0 failures, 0 errors, 4 skipped; fresh daemon, cleanTest, real Task :test) as the gate
result: pass
evidence: "Operator accepted the clean fresh-daemon rerun as the gate; MultiDayConstraintDiagnosticTest wall-clock flake stays open in deferred-items.md."

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
