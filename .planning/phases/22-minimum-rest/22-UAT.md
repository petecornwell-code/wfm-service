---
status: complete
phase: 22-minimum-rest
source: [22-VERIFICATION.md]
started: 2026-10-04T16:05:00Z
updated: 2026-10-04T16:40:00Z
---

## Current Test

[testing complete]

## Tests

### 1. DeskManagement.tsx Min Rest column (22-04's 7-step human-check, deferred to end-of-phase)
expected: All seven sub-checks pass. Open the desk config table and confirm the Min Rest (hrs) column sits between Day Start and Actions with the em-dash for an unconfigured desk; edit a desk to 10.5, save, reload twice and confirm it persists; clear to empty and confirm it reads the em-dash (not 0); set to 0 and confirm it reads 0 (not the em-dash); type 25 and confirm the amber (not red) out-of-range message appears without disabling Save; edit only the Name and confirm Min Rest is untouched; narrow the browser until the table scrolls and confirm no clipped content. The null/zero distinction must survive a real round trip through the PUT endpoint and a page reload, not just in-memory state.
why_human: Visual rendering (column position, em-dash vs 0, amber vs red), a live round trip against a running app and database, and narrow-viewport reflow are outside what grep/tsc can confirm; screenshots do not settle reliably on this app.
result: pass
source: automated
method: live DOM measurement against a running app (backend :8081, vite :3001, throwaway pgvector DB on :55432 with all 54 migrations through V55), per the local-verification recipe. No screenshot was captured — screenshots never settle on this app's pages — so every criterion was measured instead of eyeballed.
evidence: |
  - Column position: header order measured as Name, Description, Default Hours/Day, Scheduling
    Mode, Day Start, **Min Rest (hrs)**, Actions — index 5 of 7, between Day Start (4) and
    Actions (6). Header and every body cell share identical left/width pairs (564/71).
  - Unconfigured desk renders a single character, codepoint 8212 (U+2014 EM DASH).
  - 10.5 saved through the UI -> DB `minimum_rest_minutes = 630`; survived two full page
    reloads reading back "10.5"; edit mode re-populates "10.5".
  - 0 saved -> DB `minimum_rest_minutes = 0`, `IS NULL = f`; renders "0" (codepoint 48) after
    reload, and edit mode re-populates "0", not empty.
  - Cleared to empty -> DB `IS NULL = t`; renders the em-dash (8212) after reload, not "0".
    Both directions of the null/zero distinction therefore survive the real PUT + reload.
  - 25 typed: message "Minimum rest must be less than 24 hours." in rgb(146, 64, 14) = #92400e
    (amber-800), 13px/400 — not red; Save button `disabled === false` throughout.
  - Name-only edit: exactly one request fired, `PUT /api/v1/desks/{id}`; no
    `PUT /minimum-rest` in the network log, and the cell still read 10.5.
  - At a 420px viewport the page scrolls horizontally (body scrollWidth 824 vs clientWidth 420)
    with no ancestor clipping; scrolled to the far right, the Min Rest cells sit fully inside the
    viewport with scrollWidth == clientWidth (not truncated) and the Actions column's right edge
    lands exactly on the viewport edge.

### 2. AgentExceptions.tsx Rest Waivers section (22-09's 9-step human-check, deferred to end-of-phase)
expected: All nine sub-checks pass, including the immediate-persistence (no explicit Save) interaction model and the double-click idempotency guard. Open one agent's exceptions page, confirm the Rest Waivers card layout and empty-state copy; add a waiver and confirm immediate persistence across a reload; double-click Add rapidly and confirm exactly one row with the button disabled between clicks; submit a blank reason and confirm the server's own error surfaces with no row created; delete a row and confirm no confirmation dialog, a success toast, and the row gone after reload; add a waiver on a day-off date and confirm acceptance and consistent dimming; add a very-long-reason waiver and confirm it wraps rather than truncates; narrow the viewport and confirm the form wraps without overflow.
why_human: Live add/delete round trip against a running app and database, toast behavior, click-debounce timing, and visual wrap/overflow behavior are outside static analysis.
result: pass
source: automated
method: live DOM measurement plus network and database inspection against the same running stack.
evidence: |
  - Card layout matches the sibling Add Exception card exactly: background rgb(255,255,255),
    padding 16px, border-radius 8px. Helper copy in rgb(107,114,128) at 13.6px. Table headers
    Date / Reason / Actions.
  - Empty state: "No rest waivers", rgb(107,114,128), text-align center, colSpan 3.
  - Add then reload: the added row was still present after a full page reload — immediate
    persistence with no explicit Save, as designed.
  - Genuine browser double-click (Playwright `dblclick`, two real click events in separate
    tasks) produced exactly ONE `POST .../rest-waivers` in the network log and exactly one row.
    Polling the live button through the request showed it go `disabled` with the label
    "Adding..." and then re-enable — the T-22-24 guard holds.
  - Blank reason: `POST` returned 400 with the server's own body
    `{"error":{"code":"VALIDATION_FAILED","message":"reason is required"}}`; the toast rendered
    that message verbatim ("reason is required"); `SELECT count(*) FROM agent_rest_waiver` stayed
    at 0 — no row created.
  - Delete: a registered Playwright dialog listener recorded ZERO dialogs (no confirmation
    prompt); toast "Waiver removed" on rgb(21,128,61) with white text; the row was gone
    immediately and still gone after a reload.
  - Day-off date (2026-11-16, seeded APPROVED PTO): the waiver was accepted and the row renders
    "2026-11-16 (day off)" with background rgb(243,244,246) = #f3f4f6 and colour
    rgb(156,163,175) = #9ca3af — byte-identical to the exceptions table's own day-off dimming
    (the same literal pair appears at AgentExceptions.tsx:194 and :236).
  - A 229-character reason wrapped to 3 lines at 24px line-height with white-space: normal and
    scrollWidth == clientWidth in both axes — wrapped, not truncated.
  - At a 400px viewport the form wraps: the date and reason inputs share one line and the Add
    Waiver button wraps to the next (tops 711/712 vs 753), flex-wrap: wrap honoured, and no
    child overflows either the form or the card.

### 3. ScheduleResults.tsx header badge and Rest Waivers tab (22-10's 11-step human-check — the surface both gap-closure plans targeted)
expected: All eleven sub-checks pass. Solve a fixture desk with configured minimum rest and one deliberately waived short-rested pair; confirm the header badge renders last in the header row in the correct muted treatment and updates on the 2-second tick while RUNNING; click it and confirm the Rest Waivers tab activates, positioned between Constraint Violations and PTO; confirm Applied (green) sits above Unused (grey) with gaps as one-decimal-hour figures; confirm an unused waiver on a day-off date renders correctly; open a schedule on a desk with NO configured rest and confirm the badge is absent and the tab shows the never-configured (not nothing-recorded) copy; trigger a pre-solve rest refusal and confirm the full untruncated string renders in the existing red block; narrow the viewport and confirm reflow rather than clipping. This is the first human-eyes confirmation that the two now-closed defects produce a correct badge/tab for a REOPENED ACCEPTED schedule, not only for a schedule caught mid-poll.
why_human: Visual color/placement checks, the live 2-second poll tick, click-to-switch-tab interaction, and viewport reflow are outside static analysis; a real solve against a fixture desk is required to produce an actual waived pair to inspect.
result: pass
source: automated
method: live DOM measurement against the running stack. The fixture was seeded as ACCEPTED, SHIFT-mode schedule rows (desk, assignments, waivers) rather than produced by a live solver run — see the limitation recorded below. `ScheduleOutputService.buildRestWaiverDisclosure` then computed the disclosure from those rows through the real reopened-accepted read path, which is precisely the path both gap-closure plans fixed.
limitation: |
  The solver was NOT run. The ACCEPTED schedule's assignment rows were seeded directly to
  construct a deliberately short-rested waived pair. Everything downstream of the DB — the
  disclosure computation, the DTO, the badge and the tab — is the real production code on the
  real reopened-accepted path. What this did NOT exercise is the solver itself producing a
  waived short-rested pair during a live solve. That part of the criterion is unverified here.
evidence: |
  - Backend on the REOPENED ACCEPTED schedule returned minimumRestMinutes 630,
    appliedRestWaiverCount 1, unusedRestWaiverCount 2, and a restWaiverDisclosure with the
    applied entry (Ada Verify, 2026-11-17 22:00 -> 2026-11-18 06:00, measured 480 vs required
    630) and two unused entries — correct arithmetic and correct applied/unused classification.
  - Header badge PRESENT on the reopened ACCEPTED schedule reading
    "Rest Waivers: 1 applied, 2 unused" — the defect both gap-closure plans targeted. It is the
    header row's LAST element (`lastElementChild === badge`, index 6 of 7) in the muted
    treatment: rgb(107,114,128) at 13.6px/400, matching the sibling elapsed-time span and
    smaller than the 16px primary spans. cursor: pointer.
  - 2-second tick while RUNNING: on a RUNNING schedule the `/summary` poll fired at 2035ms and
    2024ms intervals. Adding a waiver server-side mid-poll flipped the badge from
    "1 applied, 2 unused" to "1 applied, 3 unused" with NO page reload — the badge updates on
    the tick.
  - Clicking the badge activated the Rest Waivers tab (background rgb(59,130,246), white text).
    Tab order measured: Staffing Summary, Agent Schedule, Agent Allocation, Preference Report,
    Drift Report, Constraint Violations, **Rest Waivers**, PTO — index 6, between Constraint
    Violations (5) and PTO (7).
  - Applied section (top 367) sits ABOVE Unused (top 547). Applied rows: agent name
    rgb(21,128,61) = #15803d on background rgb(240,253,244) = #f0fdf4 (green). Unused rows:
    rgb(107,114,128) = #6b7280 on rgb(243,244,246) = #f3f4f6 (grey).
  - Gaps render as one-decimal-hour figures: required "10.5h", measured "8.0h" (applied) and
    "16.0h" (unused), right-aligned.
  - The day-off unused waiver renders "2026-11-15 —" / "2026-11-16 —" with "—" for the measured
    gap while still showing the required "10.5h" — the inert-waiver shape, correct.
  - Desk with NO configured rest: the header badge is ABSENT, the tab still exists, and its body
    reads "Minimum rest is not configured for this desk." — the never-configured copy, NOT the
    "No rest waivers recorded for this schedule." nothing-recorded copy. The two are correctly
    distinguished.
  - Pre-solve rest refusal: the full 462-character refusal string rendered verbatim and
    untruncated in the existing red block (background rgb(254,242,242) = #fef2f2, border
    1px solid rgb(252,165,165) = #fca5a5), wrapping to 5 lines with scrollWidth == clientWidth
    and scrollHeight == clientHeight — nothing clipped, ending on its real final sentence.
  - Viewport reflow at 1440 / 768 / 420: no table and no cell is clipped in either axis at any
    width, no ancestor clips content, and the long reason reflows from 70px to 539px tall rather
    than truncating.

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]

## Observations

Not a failure of any checkpoint above, recorded because it was found while exercising them.

```
item: concurrent duplicate rest-waiver POST returns a raw 500
found_during: test 2 (double-click idempotency sub-check)
severity: minor
status: outside-checkpoint-scope
detail: |
  A genuine browser double-click is correctly guarded — the disable-on-click window means the
  second click never fires a request, which is what the checkpoint asks for, and it passes.
  But two GENUINELY CONCURRENT POSTs for the same (agent, date) are not guarded: one wins with
  200 and the other loses the agent_rest_waiver unique constraint and surfaces as an unhandled
  500. Reproduced deterministically 3 times out of 3 against the API
  (A:200 B:500, B:200 A:500, A:200 B:500), with the row count correctly staying at 1 each time.
  A SEQUENTIAL duplicate POST is a clean idempotent upsert (200, reason updated, still one row),
  so only the concurrent race is affected.
impact: |
  No data corruption — the unique constraint holds and exactly one row ever exists. The cost is
  an operator-visible raw 500 toast instead of a clean conflict, reachable from two browser tabs
  on the same agent, or a retried request that was actually still in flight.
suggested_disposition: backlog follow-up — catch DataIntegrityViolationException on the waiver
  upsert and return the same 200 the sequential duplicate already returns (or a 409), rather
  than letting it reach the generic 500 handler.
```
