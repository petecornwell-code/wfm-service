---
status: testing
phase: 14-shift-library-scheduling-mode
source: 14-01-SUMMARY.md, 14-02-SUMMARY.md, 14-03-SUMMARY.md, 14-04-SUMMARY.md, 14-05-SUMMARY.md, 14-06-SUMMARY.md
started: 2026-08-25T00:00:00Z
updated: 2026-08-25T00:00:00Z
---

## Current Test

number: 1
name: V39 migration applies cleanly to live Postgres
expected: |
  Flyway logs "Migrating schema ... to version 39". `\d shift_template` shows the eleven
  columns and the `(tenant_id, desk_id, name, effective_from)` unique constraint.
  `SELECT DISTINCT scheduling_mode FROM desk;` returns only `SLOT`.
awaiting: user response

## Tests

### 1. V39 migration applies cleanly to live Postgres
expected: Flyway migrates to version 39 without error; `\d shift_template` shows eleven columns and the `(tenant_id, desk_id, name, effective_from)` unique constraint; `SELECT DISTINCT scheduling_mode FROM desk;` returns only `SLOT`
result: [pending]
note: This is the phase's single most load-bearing unverified item. `src/test/resources/application-test.yml` sets `flyway.enabled: false` with `ddl-auto: create-drop`, so `./gradlew test` never executes V39 — a fully green 402-test suite says nothing about whether this migration applies. The SQL was reviewed line-by-line and the code reviewer independently confirmed the DDL and the `ShiftTemplate` entity mapping agree on every column, type, nullability and constraint, but syntactic soundness is not a confirmed `flyway migrate`.

### 2. Coverage panel names specific uncovered demand windows
expected: With seeded staffing demand and a partial shift library, the Coverage panel lists the specific uncovered `(date, timeslot)` windows by name — not a generic validation message
result: [pending]

### 3. Mode switch is refused with the same named windows
expected: Clicking "Shift-scheduled" leaves the toggle on "Slot-scheduled" and shows the same named uncovered windows, with no duplicate error surface
result: [pending]
note: ⚠ The code review found a Critical bug that likely breaks this test — see `14-REVIEW.md`. `ShiftLibrary.tsx:355-372` drops the `demand` and `grid` ErrorDetail fields and unconditionally forces `hasLiveDemand: true`, so a refusal for "no staffing demand loaded" or a grid-misaligned template can render "✓ All staffing-demand windows are covered" immediately after being refused, with no toast. Test this case deliberately.

### 4. Adding covering templates makes the switch succeed
expected: After adding templates that cover the previously-uncovered windows, the same click succeeds and the toggle moves to "Shift-scheduled"
result: [pending]

### 5. SHIFT → SLOT switches back immediately, with no dialog
expected: Clicking "Slot-scheduled" switches back at once. No confirmation dialog appears — D-12 rejects one deliberately, citing audit I-3
result: [pending]

### 6. Mode switch during a RUNNING solve returns a readable 409
expected: With a solve running for the desk, clicking either mode option shows a single-line 409 toast, the toggle does not move, and the in-flight solve is NOT stopped
result: [pending]

### 7. Desk Management shows mode as read-only
expected: The Scheduling Mode column shows the correct value per desk, with no control to change it there (D-14)
result: [pending]

### 8. Contracted-hours mismatch is advisory, not blocking
expected: A template whose net duration matches no agent's contracted hours shows the amber warning glyph with the correct tooltip and still saves successfully (D-06 — advisory on save, blocking only at the mode switch in the fatal case)
result: [pending]

### 9. Visual legibility with realistic data (6 backstop claims)
expected: Multiple eras of one template name read as legible eras rather than accidental duplicates (D-11); a long template name does not break the table or the input layout; a realistic count of uncovered windows stays readable; the advisory tooltip is legible
result: [pending]
note: These are the plan's own six `verification: backstop` truths. No frontend test framework exists in this codebase (`frontend/package.json` has no test script, no vitest/jest/testing-library), so these cannot be proven automatically by design, not by omission.

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps

<!-- Populated as issues are reported during /gsd-verify-work 14 -->
