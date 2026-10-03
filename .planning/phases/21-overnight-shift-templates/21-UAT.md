---
status: testing
phase: 21-overnight-shift-templates
source: [21-VERIFICATION.md]
started: 2026-10-03T13:20:00Z
updated: 2026-10-03T13:20:00Z
---

## Current Test

number: 1
name: Desk Management refusal and advisory messages render without clipping
expected: |
  Every refusal/advisory renders the backend's own full message without clipping inside the
  Toast or table cell; the lock explanation wraps rather than overflowing the desk table at
  real desk-name widths.
awaiting: user response

## Tests

### 1. Desk Management refusal and advisory messages render without clipping

test: Open Desk Management, edit a desk's day start to a value entered by keyboard (not the picker) that the backend refuses for one of the two reasons the time picker normally makes unreachable (e.g. a value carrying seconds, or a non-15-minute boundary). Also trigger the stranded-template refusal and the accepted-schedule-lock message, and the D-05 tiling-warning toast, at a narrow (~375px) viewport and with a long desk name.
expected: Every refusal/advisory renders the backend's own full message without clipping inside the Toast or table cell; the lock explanation wraps rather than overflowing the desk table at real desk-name widths.
why_human: DOM geometry (wrapping vs. clipping, container overflow) cannot be measured by grep or static analysis. No browser-automation tool was available to the 21-08 executor (WINDOWS.md #11); recorded as unrun-verify, not claimed as passed.
ledger: WINDOWS.md #11
result: [pending]

### 2. Sticky Agent column survives anchored column re-ordering

test: Open a solved schedule on a 21:00-anchored desk with 24+ anchored slot columns in the Allocation grid tab and scroll the table horizontally.
expected: The sticky Agent column's left offset is undisturbed by the anchored column re-ordering; the overnight shift's cells still read as one contiguous highlighted run.
why_human: Sticky-column geometry under horizontal scroll is a rendered-DOM fact. No browser-automation tool was available to the 21-10 executor (WINDOWS.md #12); the behavioral/text claims were proven by executing the real dayWindow.ts module against fixtures, but the geometry claim was not.
ledger: WINDOWS.md #12
result: [pending]

### 3. Business-day section heading wraps rather than forcing page scroll

test: View the per-business-day section heading on the Schedule Results grid for a 21:00-anchored desk at a narrow viewport (~375px), and check it does not force horizontal scroll on the page itself.
expected: The heading's '(business day: Sun 21:00–Mon 21:00)' parenthetical wraps onto a second line rather than clipping or forcing the page to scroll horizontally outside the grid's own scrolling container.
why_human: DOM wrapping/overflow fact, not inferable from the heading's text-generation function alone. No browser-automation tool was available to the 21-10 executor (WINDOWS.md #13).
ledger: WINDOWS.md #13
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps

All three items are DOM-geometry backstops. The phase's functional and behavioural claims are
independently verified in 21-VERIFICATION.md at 7/7 must-haves; these three are the residue the
verifier could not measure by reading source, and that the executor sessions honestly recorded as
`unrun-verify` rather than claiming.

Note: a browser-automation tool IS available to the orchestrator session, so these can be measured
directly (geometry via `browser_evaluate` — screenshots do not settle on this app and time out)
rather than walked through by hand, provided a throwaway stack is stood up first (disposable
Postgres on 55432, backend on 8081, vite on 3001; never port 8080, never migrate an existing DB).
