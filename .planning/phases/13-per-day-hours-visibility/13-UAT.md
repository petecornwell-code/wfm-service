---
status: testing
phase: 13-per-day-hours-visibility
source: [13-VERIFICATION.md]
started: 2026-08-24T13:15:24Z
updated: 2026-08-24T13:15:24Z
---

## Current Test

number: 1
name: Live-desk visual walkthrough of the collapsed summary muted colour and tooltip
expected: |
  Zero-row agent renders light-grey italic with 'Not set — using schedule default' tooltip;
  any agent with >=1 stored row renders in default body colour with no tooltip
awaiting: user response

## Tests

### 1. Live-desk visual walkthrough of the collapsed summary muted colour and tooltip
expected: Zero-row agent renders light-grey italic with 'Not set — using schedule default' tooltip; any agent with >=1 stored row renders in default body colour with no tooltip
why_human: Visual colour/tooltip rendering in a real browser against real data — WINDOWS.md item 1
result: [pending]

### 2. Live-desk walkthrough of the per-cell editor seeding the 'Not set (default)' literal and the expanded 7-day grid's five display states
expected: Clicking a not-set cell opens the editor pre-filled with 'Not set (default)'; all 5 weekday states (MAND, PTO, explicit-zero, worked, not-set) are visually distinct
why_human: Requires a live DB/BambooHR-configured environment — WINDOWS.md items 2 and 5
result: [pending]

### 3. Live-desk walkthrough of the bulk "Set all days to…" range guard and confirmation dialog
expected: 1000/-1 rejected via toast before confirm()/network; 24 and 0 accepted; label-count confirm() fires only when >=1 weekday carries MANDATORY/PTO
why_human: End-to-end browser behaviour with no frontend test framework in this repo — WINDOWS.md item 6
result: [pending]

### 4. PUT .../day-hours/{day} HTTP-level dispatch for a malformed {day} segment
expected: 400 (not 500), body names only the 'day' parameter, no rejected token or internal type leaked; valid/out-of-range day-hours paths and the bulk contracted-hours 400 message all unchanged; a genuine unexpected failure still returns 500
why_human: No @WebMvcTest/MockMvc harness exists for DeskAgentController in this codebase, so Spring's actual dispatch to GlobalExceptionHandler.handleTypeMismatch for a real HTTP request is unit-tested but not integration-tested — WINDOWS.md item 7 (P-17 residual gap, declared not discovered)
result: [pending]

### 5. E1 overflow (backstop): the collapsed summary's range output still fits the existing dense 13-column row after the muted-colour change
expected: No wrap, no horizontal-scroll regression at the widest range value
why_human: insufficient_spec — visual layout claim, abstain per honest-verifier contract
result: [pending]

### 6. E3 populated (backstop): mixed-state weekday row rendering matches the CONTEXT.md mockup
expected: All 5 states visually distinct in the expanded grid
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

### 7. E3 overflow (backstop): horizontal-scroll behaviour of the expanded 7-day grid
expected: No layout break at the widest content
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

### 8. E4 overflow (backstop): the seeded 'Not set (default)' text in the browser's native datalist dropdown
expected: Text is not clipped by the weekday mini-column width, since the datalist popup is not constrained by it
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

## Summary

total: 8
passed: 0
issues: 0
pending: 8
skipped: 0
blocked: 0

## Gaps
