---
phase: 21-overnight-shift-templates
plan: 08
subsystem: ui
tags: [react, typescript, toast, desk-management]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-02)
    provides: "DeskService.setDayStart's fifth refusal (D-03), dayStartTilingWarning (D-05), the dayStartLocksByDeskId lock disclosure on DeskResponse, and the four matching optional fields on the frontend Desk interface"
provides:
  - "An editable, 15-minute-stepped day-start control inside DeskManagement.tsx's existing per-row edit mode, submitted by the row's existing Save button"
  - "A disabled render naming the blocking schedule (id and/or period, degrading to the bare sentence when either is absent) when a desk's day start is permanently locked by an ACCEPTED schedule"
  - "Server-message surfacing for all five day-start refusals plus the non-blocking tiling advisory, with the row staying in edit mode on any failure"
affects: []

# Actuals (#2632)
actuals:
  tokens: 1994
  tasks: 2
  commits: 2
  plan_head_before: 001fcd4a3cec07e915be669eec34bf99bd2794cc
  plan_head_after: 2322bee8331082071a33e5270a1ad9a7b858a7cc

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A disabled/locked render is a disclosure only, never an authorization — the server's unconditional refusal is the sole decision-maker, exercised the same way for a stale page or a direct API call (T-21-11)"
    - "One submission produces exactly one Toast: the tiling advisory's own copy carries the 'still saved' confirmation, so it replaces rather than follows the success Toast"

key-files:
  created: []
  modified:
    - frontend/src/pages/DeskManagement.tsx

key-decisions:
  - "dayStartLockExplanation treats the schedule id and the (start, end) period as two independent halves — a complete period renders its parenthetical only when BOTH dates are present, an incomplete period is treated as entirely absent (never a dangling '(2026-01-01–)' or similar), so the degrade rule in the UI-SPEC's 'partial' row is satisfied without a separate branch for every combination"
  - "handleUpdate calls desks.setDayStart only when editDayStart differs from the desk's current dayStart, after the existing name/description/hours mutation succeeds; if the day-start call then fails, the earlier update has already landed (server and local list) and editingId stays set so the operator can retry only the day start without re-entering the other fields"
  - "No client-side validation of the day-start value was added, per the plan's explicit prohibition — all five refusals stay exclusively server-side"

patterns-established: []

requirements-completed: [OVNT-01]

coverage:
  - id: D1
    description: "A desk's day start is editable from the desk page's existing edit mode, stepping in 15-minute increments, and submitted by the row's existing Save button"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "grep -c 'step=\"900\"' frontend/src/pages/DeskManagement.tsx (2) and grep -c 'desks.setDayStart' (1)"
        status: pass
      - kind: other
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
    human_judgment: true
    rationale: "Picker stepping behavior and visual confirmation that the row submits correctly are browser-rendering facts; no test runner or browser-automation tool was available in this executor session (see Known Gaps)."
  - id: D2
    description: "A desk locked by an ACCEPTED schedule renders its day-start control disabled, with the blocking schedule named beneath it, degrading correctly when the id or period is partially or fully absent"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "grep -c 'dayStartLockedByScheduleId' (3) and grep -c 'Locked — accepted schedule blocks day start.' (1) in frontend/src/pages/DeskManagement.tsx"
        status: pass
    human_judgment: true
    rationale: "The degrade branches were verified by code-path reading against all four id/period presence combinations, not by rendering a locked desk in a live browser (no backend-seeded ACCEPTED-schedule fixture was stood up in this session)."
  - id: D3
    description: "Every one of the five day-start refusals and the non-blocking tiling advisory reaches the operator as the server's own message; no refusal string is hardcoded"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "grep -cE 'with period|an accepted schedule \\(' frontend/src/pages/DeskManagement.tsx (0) and grep -niE 'must not carry seconds|is not a 15-minute boundary|day start is required|will not tile cleanly' (0)"
        status: pass
    human_judgment: false
  - id: D4
    description: "One submission produces exactly one message: a tiling-warning save shows only the warning, a refused save shows only the error, and the row stays in edit mode on failure with the entered value intact"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "code review of handleUpdate's if/else branch on dayStartTilingWarning, and confirmation setEditingId(null) does not appear in the catch block"
        status: pass
    human_judgment: true
    rationale: "The actual single-Toast behavior (no stray success Toast alongside a warning, no double Toast on a double failure) is a runtime fact that needs a live request/response cycle against the backend; not exercised end-to-end in this session."
  - id: D5
    description: "Five backstop geometry measurements (locked-explanation wrapping/overflow, tiling-warning two-line rendering, accepted-schedule refusal long-text/overflow) at real desk-name widths and a narrow viewport"
    verification: []
    human_judgment: true
    rationale: "Requires in-page geometry evaluation (getBoundingClientRect/scrollWidth) against a rendered browser DOM. No browser-automation tool (Playwright/Puppeteer, CDP) was available to this executor, and this plan's own threat model (T-21-SC) forbids introducing one as a new dependency. Recorded as WINDOWS.md entry #11 (unrun-verify) rather than claimed as passed."

duration: 30 min
completed: 2026-10-03
status: complete
---

# Phase 21 Plan 08: Editable Day-Start Control Summary

**The desk page's day-start cell is now a 15-minute-stepped time input wired to the existing Save button, disabled with the blocking schedule named when an ACCEPTED schedule locks it, and every refusal or advisory it can produce reaches the operator as the backend's own message.**

## Performance

- **Duration:** 30 min
- **Started:** 2026-10-03T01:08:00Z (approx.)
- **Completed:** 2026-10-03T01:38:00Z
- **Tasks:** 2 of 2
- **Files modified:** 1

## Accomplishments
- `DeskManagement.tsx`'s per-row edit mode gained an `editDayStart` state value, seeded from `desk.dayStart` in `startEdit`, rendering a `<input type="time" step="900">` when the desk is unlocked.
- A new `dayStartLockExplanation` helper renders the D-04 lock disclosure, treating the schedule id and the (start, end) period as independent halves — never a dangling separator, never the literal `undefined`, degrading all the way to the bare `"Locked — accepted schedule blocks day start."` sentence when both are absent. It is used in both the disabled edit-mode render and the read-mode cell.
- Both stale "belongs with the overnight-template phase" comments are gone, replaced with comments that explain the 15-minute step and the lock branch and cite `OVNT-01`.
- `handleUpdate` now calls `desks.setDayStart` after the existing update succeeds, only when the value changed, merging its returned desk into list state. A successful save carrying `dayStartTilingWarning` shows exactly one `warning` Toast instead of the success Toast; any refusal shows exactly one `error` Toast built from `getErrorMessage(err)`, with the row staying in edit mode (`setEditingId(null)` is never reached on the catch path).
- The Save button disables for the request's duration via a new `savingDayStart` flag — no spinner, matching the codebase's convention of reserving spinners for solver runs.
- No refusal string appears anywhere in the file as a literal; confirmed by grep for both the UI-SPEC's illustrative example and the backend's actual wording.

## Task Commits

1. **Task 1: Render the day-start control** — `bd97d28` (feat)
2. **Task 2: Submit the day start through Save, surface server messages** — `2322bee` (feat)

**Plan metadata:** this SUMMARY commit (docs: complete plan)

## Files Created/Modified
- `frontend/src/pages/DeskManagement.tsx` — editable/disabled day-start cell in both edit and read branches, `dayStartLockExplanation` helper, `handleUpdate` wired to `desks.setDayStart`, `savingDayStart` saving state, warning-Toast branch

## Decisions Made
- The lock explanation's id and period render as independent optional halves rather than one all-or-nothing block, matching the UI-SPEC's "partial" row exactly: an incomplete period (one date present, one absent) is treated as entirely absent rather than rendered with a dangling dash.
- The day-start mutation is called only when the value actually changed and only after the existing update mutation succeeds; a day-start-only failure leaves the already-saved name/description/hours change intact while the row stays open for a retry — chosen because the plan scopes "the row's existing Save button" as the single submission point for everything in the row, and partial success is less surprising than discarding a valid name change because of an unrelated day-start refusal.
- No client-side mirror of any of the five server-side refusals was added, per the plan's explicit prohibition — every message shown is caught from the response, never pre-validated locally.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None for the implementation itself. See "Known Gaps" below for a verification limitation.

## Known Gaps

**Five backstop geometry checks could not be executed in this session** (D5 in the coverage table above, WINDOWS.md ledger entry #11, kind `unrun-verify`): confirming that the lock explanation and the longest refusal/warning strings wrap rather than overflow their table cell / Toast container at real desk-name widths and a narrow viewport requires a rendered browser DOM measured with something like `getBoundingClientRect`/`scrollWidth`. This executor session had no browser-automation tool available (no Playwright/Puppeteer/CDP access), and standing one up would add a new dependency this plan's own threat model (T-21-SC) explicitly forbids without a blocking-human checkpoint. The two functional `<human-check>` items (picker stepping visually, disabled render naming the schedule) were instead verified by code-path reading rather than a live render — recorded as `human_judgment: true` in the coverage block above rather than claimed as measured.

What WAS verified mechanically, with evidence:
- `npm --prefix frontend exec -- tsc -b frontend --force` — clean, no `error TS` lines.
- `npm --prefix frontend run build` — clean, `vite build` succeeded.
- All nine of Task 1's and Task 2's `<acceptance_criteria>` grep checks pass at their expected counts (shown inline in the task commits above).
- `grep -niE "must not carry seconds|is not a 15-minute boundary|day start is required|will not tile cleanly"` and `grep -cE 'with period|an accepted schedule \('` both return 0 — no refusal string is hardcoded anywhere in the file, including the UI-SPEC's own illustrative (and intentionally inexact) example string.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `DeskManagement.tsx` now exposes the full day-start control surface this capability has needed since Phase 20; no further backend work is required for this control.
- The unrun backstop geometry checks (WINDOWS.md #11) should be cleared by a human with a browser before `/gsd-ship`, or waived with a stated reason if judged low-risk given the existing muted-grey disclosure pattern already in production use elsewhere in this file.
- No blockers to the next plan in this phase.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-03*

## Self-Check: PASSED

- `frontend/src/pages/DeskManagement.tsx` exists on disk.
- `.planning/phases/21-overnight-shift-templates/21-08-SUMMARY.md` exists on disk.
- Commits `bd97d28`, `2322bee` found in `git log --oneline --all`.
