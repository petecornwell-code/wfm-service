---
status: testing
phase: 22-minimum-rest
source: [22-VERIFICATION.md]
started: 2026-10-04T16:05:00Z
updated: 2026-10-04T16:05:00Z
---

## Current Test

number: 1
name: DeskManagement.tsx Min Rest column (22-04's 7-step human-check)
expected: |
  All seven sub-checks pass as described; the null/zero distinction in particular must survive a
  real round trip through the PUT endpoint and a page reload, not just the in-memory state.
awaiting: user response

## Tests

### 1. DeskManagement.tsx Min Rest column (22-04's 7-step human-check, deferred to end-of-phase)
expected: All seven sub-checks pass. Open the desk config table and confirm the Min Rest (hrs) column sits between Day Start and Actions with the em-dash for an unconfigured desk; edit a desk to 10.5, save, reload twice and confirm it persists; clear to empty and confirm it reads the em-dash (not 0); set to 0 and confirm it reads 0 (not the em-dash); type 25 and confirm the amber (not red) out-of-range message appears without disabling Save; edit only the Name and confirm Min Rest is untouched; narrow the browser until the table scrolls and confirm no clipped content. The null/zero distinction must survive a real round trip through the PUT endpoint and a page reload, not just in-memory state.
why_human: Visual rendering (column position, em-dash vs 0, amber vs red), a live round trip against a running app and database, and narrow-viewport reflow are outside what grep/tsc can confirm; screenshots do not settle reliably on this app.
result: [pending]

### 2. AgentExceptions.tsx Rest Waivers section (22-09's 9-step human-check, deferred to end-of-phase)
expected: All nine sub-checks pass, including the immediate-persistence (no explicit Save) interaction model and the double-click idempotency guard. Open one agent's exceptions page, confirm the Rest Waivers card layout and empty-state copy; add a waiver and confirm immediate persistence across a reload; double-click Add rapidly and confirm exactly one row with the button disabled between clicks; submit a blank reason and confirm the server's own error surfaces with no row created; delete a row and confirm no confirmation dialog, a success toast, and the row gone after reload; add a waiver on a day-off date and confirm acceptance and consistent dimming; add a very-long-reason waiver and confirm it wraps rather than truncates; narrow the viewport and confirm the form wraps without overflow.
why_human: Live add/delete round trip against a running app and database, toast behavior, click-debounce timing, and visual wrap/overflow behavior are outside static analysis.
result: [pending]

### 3. ScheduleResults.tsx header badge and Rest Waivers tab (22-10's 11-step human-check — the surface both gap-closure plans targeted)
expected: All eleven sub-checks pass. Solve a fixture desk with configured minimum rest and one deliberately waived short-rested pair; confirm the header badge renders last in the header row in the correct muted treatment and updates on the 2-second tick while RUNNING; click it and confirm the Rest Waivers tab activates, positioned between Constraint Violations and PTO; confirm Applied (green) sits above Unused (grey) with gaps as one-decimal-hour figures; confirm an unused waiver on a day-off date renders correctly; open a schedule on a desk with NO configured rest and confirm the badge is absent and the tab shows the never-configured (not nothing-recorded) copy; trigger a pre-solve rest refusal and confirm the full untruncated string renders in the existing red block; narrow the viewport and confirm reflow rather than clipping. This is the first human-eyes confirmation that the two now-closed defects produce a correct badge/tab for a REOPENED ACCEPTED schedule, not only for a schedule caught mid-poll.
why_human: Visual color/placement checks, the live 2-second poll tick, click-to-switch-tab interaction, and viewport reflow are outside static analysis; a real solve against a fixture desk is required to produce an actual waived pair to inspect.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
