---
status: testing
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
source: [24-VERIFICATION.md]
started: 2026-10-07T19:01:04Z
updated: 2026-10-07T19:01:04Z
---

## Current Test

number: 1
name: D7 per-call state isolation
expected: |
  Concurrent or successive calls of ScheduleEnvelopeRepairService, ScheduleConsistencyRepairService
  and ShiftStartMixTargetService on different desks do not interfere; each result equals its isolated run.
awaiting: user response

## Tests

### 1. D7 per-call state isolation (see 24-VERIFICATION.md behavior_unverified_items)
expected: Concurrent or successive calls on different desks do not interfere; no map or field survives a call
result: [pending]

### 2. Agent Allocation Required / Over-under rows on a 06:00-anchored desk
expected: For post-midnight slots on a middle business day and on the FINAL business day, Required equals the demand seeded for that business day's calendar-next-day hours, and the final day's post-midnight cells are populated, not blank
result: [pending]

### 3. Six judgment-tier prohibitions in 24-VERIFICATION.md
expected: Operator confirms each MUST NOT held
result: [pending]

### 4. 24-03 phase-gate shortfall closed
expected: Accept the orchestrator's post-24-03 clean full-suite run (218 XML files, 1535 tests, 0 failures, 0 errors, 4 skipped; fresh daemon, cleanTest, real Task :test) as the gate
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
