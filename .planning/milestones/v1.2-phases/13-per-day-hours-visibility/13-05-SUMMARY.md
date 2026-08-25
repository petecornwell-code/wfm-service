---
phase: 13-per-day-hours-visibility
plan: 05
subsystem: ui
tags: [react, typescript, desk-agents, gap-closure]

# Dependency graph
requires:
  - phase: 13-per-day-hours-visibility (plans 01-04)
    provides: The collapsed/expanded roster UI, per-cell click-to-edit combo, and bulk "Set all days to…" action this plan corrects
provides:
  - Shared isEveryDayNotSet predicate driving both the collapsed cell's muted treatment and the expanded row's empty-state note
  - Collapsed Hours/Day summary rendered muted #9ca3af italic (with tooltip) when every weekday is not-set, unstyled otherwise
  - seedValueForEntry returning the "Not set (default)" picklist literal instead of an empty string for a not-set weekday
  - Client-side 0-24 range guard on the bulk "Set all days to…" input, run before the destructive confirm() dialog and before any network request
  - Corrected 13-04-PLAN.md task-1 <action> prose that previously contradicted its own must_haves E4-empty truth
affects: [13-06]

# Actuals (#2632)
actuals:
  tokens: 1185
  tasks: 3
  commits: 3

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single shared predicate (isEveryDayNotSet) extracted once and reused at two render sites instead of duplicating the same inline DAY_ORDER.every(...) condition"
    - "Client-side range guard placed strictly before a destructive confirm() dialog so an invalid value can never reach the confirmation step"

key-files:
  created: []
  modified:
    - frontend/src/pages/DeskAgents.tsx
    - .planning/phases/13-per-day-hours-visibility/13-04-PLAN.md

key-decisions:
  - "Muted treatment fires only when ALL seven weekdays are unset (isEveryDayNotSet), never on a partial week, so a partially-uploaded agent keeps the default body colour and the muted signal stays meaningful (P-13)"
  - "Bulk out-of-range error uses a truncated toast sentence ('Enter a number from 0 to 24.') rather than the per-cell combo's full Copywriting Contract sentence, since the bulk input has no PTO/MANDATORY/not-set picklist and naming them would be false copy (P-12)"
  - "13-04-PLAN.md's contradicting <action> sentence was corrected via a scoped edit with an inline 'corrected by 13-05' marker rather than a silent rewrite, so the planning record shows a correction happened (P-15)"

requirements-completed: [MDL-02, UPL-03, UPL-04, UPL-05]

coverage:
  - id: D1
    description: "Collapsed Hours/Day summary renders muted/italic with a tooltip when every weekday is not-set, and stays unstyled for any agent with at least one stored row (13-UI-SPEC.md E1 empty/partial; closes 13-VERIFICATION.md gap 1)"
    requirement: "MDL-02"
    verification:
      - kind: other
        ref: "npm --prefix frontend run build (tsc -b type-check)"
        status: pass
      - kind: other
        ref: "grep-based acceptance criteria: function isEveryDayNotSet present; isEveryDayNotSet(da) used >=2 times; fontStyle: 'italic' present twice; 'Not set — using schedule default' present twice; all 5 plan-13-01 colours retained"
        status: pass
    human_judgment: true
    rationale: "Visual muted-vs-default colour distinction on a live roster and the tooltip's hover behaviour need eyes on the rendered page; no frontend test framework exists in this repo (13-04 P-11 stands) to assert this programmatically."
  - id: D2
    description: "Clicking a not-set weekday cell opens the editor seeded with the literal 'Not set (default)', never a blank input, and opening/blurring it unchanged still sends a no-op clearRow request (13-UI-SPEC.md E4 empty; closes 13-VERIFICATION.md gap 2)"
    requirement: "UPL-04"
    verification:
      - kind: other
        ref: "npm --prefix frontend run build (tsc -b type-check)"
        status: pass
      - kind: other
        ref: "grep-based acceptance criteria: return 'Not set (default)' present; MANDATORY/PTO branches unchanged; placeholder unchanged; saveDayHours clear-row literal check unchanged"
        status: pass
    human_judgment: true
    rationale: "The full click-to-edit interaction (seeded value, Escape revert, blur no-op) requires a live browser session; no frontend test framework exists in this repo."
  - id: D3
    description: "A bulk 'Set all days to…' value outside 0-24 is rejected via toast before the destructive confirm() dialog and before any network request; in-range values (including boundary 0 and 24) still proceed normally (13-REVIEW.md WR-01, closes the E1 long-text coincidental-reliance flag)"
    requirement: "UPL-05"
    verification:
      - kind: other
        ref: "npm --prefix frontend run build (tsc -b type-check)"
        status: pass
      - kind: other
        ref: "grep-based acceptance criteria: exactly one 'Enter a number from 0 to 24.' toast literal; per-cell sentence, disabled condition, amber notice colours and confirm copy all unchanged; range test appears before labelledDayCount in source order"
        status: pass
    human_judgment: true
    rationale: "Confirming zero network requests and zero confirm() dialogs for an out-of-range value, versus exactly one PUT for in-range values, is an end-to-end browser behaviour with no frontend test framework in this repo to assert it."

duration: ~10 min
completed: 2026-08-24
status: complete
---

# Phase 13 Plan 05: Per-Day Hours Gap Closure Summary

**Closed both `status: failed` UI-SPEC truths from 13-VERIFICATION.md — a shared `isEveryDayNotSet` predicate now mutes the collapsed roster cell when nothing was uploaded, `seedValueForEntry` now seeds the "Not set (default)" picklist literal instead of a blank input, and a client-side 0-24 range guard now blocks out-of-range bulk values before the destructive confirm() dialog.**

## Performance

- **Duration:** ~10 min
- **Completed:** 2026-08-24T12:36:10Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- Extracted a single `isEveryDayNotSet(da)` predicate and used it at both the collapsed Hours/Day `<td>` (new conditional muted/italic/tooltip styling) and the expanded-row "No per-day hours uploaded" note (replacing a duplicated inline `DAY_ORDER.every(...)` check) — closes 13-VERIFICATION.md gap 1 (E1 empty).
- Changed `seedValueForEntry`'s not-set fall-through from returning `''` to returning the literal `Not set (default)`, so clicking a not-set weekday cell now opens the editor pre-filled with the fifth picklist state instead of a blank field — closes 13-VERIFICATION.md gap 2 (E4 empty). `saveDayHours`'s existing clear-row branch already treats that literal as a no-op, so no downstream change was needed.
- Added a client-side inclusive 0-24 range guard to `saveHours`, placed strictly before `labelledDayCount` and the destructive `confirm()` call, delivering a truncated toast (`Enter a number from 0 to 24.`) rather than a new inline element — closes the `coincidental_reliance` flag against the E1 long-text truth and mirrors 13-REVIEW.md WR-01.
- Corrected `13-04-PLAN.md`'s task-1 `<action>` prose, which had described seeding an empty string and directly contradicted its own `must_haves` E4-empty truth — the edit carries an inline `corrected by 13-05` marker per P-15 rather than a silent rewrite.

## Task Commits

Each task was committed atomically:

1. **Task 1: Mute the collapsed Hours/Day summary when no per-day hours exist (UI-SPEC E1 empty)** - `8f4f528` (feat)
2. **Task 2: Seed the per-cell editor with the Not set (default) literal, and correct the plan prose that caused the defect** - `208d5c0` (fix)
3. **Task 3: Guard the bulk "Set all days to…" input against out-of-range values before any request (WR-01, client half)** - `974dc25` (fix)

**Plan metadata:** (this commit)

## Files Created/Modified

- `frontend/src/pages/DeskAgents.tsx` - Added `isEveryDayNotSet` helper; wrapped the collapsed Hours/Day value in a conditionally-styled `<span>`; replaced the expanded-row empty-state note's inline predicate with the shared helper; changed `seedValueForEntry`'s not-set branch to return the picklist literal; added a pre-`confirm()` range guard to `saveHours`
- `.planning/phases/13-per-day-hours-visibility/13-04-PLAN.md` - Corrected the task-1 `<action>` sentence describing the (wrong) empty-string seed behaviour, with an inline `corrected by 13-05` marker

## Decisions Made

- P-13 (adopted from plan): muted treatment triggers only on the all-seven-not-set case, never on a partial week — a partially-uploaded agent's summary legitimately mixes stored and resolved values and must keep the default body colour.
- P-12 (adopted from plan): the bulk out-of-range message uses the truncated sentence via `showToast`, not the per-cell combo's full Copywriting Contract sentence, since the bulk control has no PTO/MANDATORY/not-set picklist and rendering it inline would add a second line beneath the input, breaking the already-verified E5 overflow truth.
- P-14/P-15 (adopted from plan): one predicate extracted and reused at both render sites (not duplicated); the plan-prose correction is a marked scoped edit, not a silent rewrite.

## Deviations from Plan

None - plan executed exactly as written. No contradictions were found between this plan's `<action>` prose and its own `must_haves.truths` / the UI-SPEC during execution.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Both `status: failed` gaps from `13-VERIFICATION.md` are closed in code; `npm --prefix frontend run build` passes clean after each task.
- The plan's own `<human-check>` step lists (visual muted-colour distinction, editor seed literal, bulk range rejection) still require a live desk roster session to confirm visually — no frontend test framework exists in this repo (13-04 P-11 stands), so these route to human judgment in the coverage block above, consistent with how 13-VERIFICATION.md caught the original two gaps that a green build missed.
- Ready for `13-06` (server-side range hardening on `DeskAgentService.setContractedHours`, referenced by this plan's threat register T-13-18) and for re-verification against `13-VERIFICATION.md`.
- Backend untouched by this plan (no `.java` file modified); the Gradle suite was not re-run per this plan's baseline instructions, and no regression is expected.

## Self-Check: PASSED

- `frontend/src/pages/DeskAgents.tsx` — FOUND (modified, exists on disk)
- `.planning/phases/13-per-day-hours-visibility/13-04-PLAN.md` — FOUND (modified, exists on disk)
- Commit `8f4f528` — FOUND in `git log --oneline --all`
- Commit `208d5c0` — FOUND in `git log --oneline --all`
- Commit `974dc25` — FOUND in `git log --oneline --all`
- `npm --prefix frontend run build` — PASS (exit 0) after each task
- All acceptance criteria for tasks 1-3 — PASS (verified via grep/build, logged above)

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-24*
