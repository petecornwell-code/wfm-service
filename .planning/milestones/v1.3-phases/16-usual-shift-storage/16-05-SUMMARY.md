---
phase: 16-usual-shift-storage
plan: 05
subsystem: ui
tags: [react, typescript, usual-shift, roster, inline-editing]

# Dependency graph
requires:
  - phase: 16-usual-shift-storage
    provides: "plan 16-02's DeskAgentResponse.UsualShiftEntry (status/name/reason/hoursAdvisory) server-computed discriminator and the setUsualShift choke-point endpoint (DeskAgentController/UsualShiftService, wired 16-01/16-02)"
provides:
  - "UsualShiftLine component rendering all three D-16 states as a second line inside each of the roster's seven day tiles, with the D-05 hours-advisory marker"
  - "Inline native <select> editor (D-17) for setting, changing and clearing an agent's usual shift per weekday, backed by deskAgents.setUsualShift"
  - "UsualShiftEntry TypeScript interface, DeskAgent.usualShift field, and deskAgents.setUsualShift in the frontend API client"
affects: [17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 4300
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Native <select> commits on its own change event with no dirty-tracking guard (D-17) — deliberate contrast with the neighbouring hours cell's <input>+<datalist>+cellDirtyRef/cellEscapedRef pattern, which exists only to work around G-13-DD's seeded-input-collapses-datalist quirk"
    - "Cancel-on-click-away wired via onBlur on the wrapping <div>, not the <select> element itself — blur/focusout bubbles from the select through the div in React's synthetic event system, so the literal 'no onBlur on the select' constraint and the described click-away-cancels behavior are both satisfied"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskAgents.tsx

key-decisions:
  - "Deferred the P-21 template fetch (useState/useEffect calling shiftTemplates.list) from Task 1 to Task 2 — nothing in Task 1 reads the fetched list yet, and TypeScript's noUnusedLocals would fail Task 1's standalone build on the otherwise-unread state. The plan itself names this exact escape hatch."
  - "Reworded two doc comments that accidentally tripped the plan's own literal-string acceptance-criteria greps (a mention of '#ef4444'/'#92400e' inside a UsualShiftLine comment, and a mention of 'onChange' inside a commitUsualShift comment) — same false-positive pattern 16-02-SUMMARY.md documented for its own javadoc"

patterns-established:
  - "D-17: a closed-option-set inline editor (native <select>) needs no blur-triggered save, no dirty-tracking ref, and no Escape handler — that machinery exists specifically for open-text editors working around a <datalist> quirk"

requirements-completed: [USHF-03, USHF-06, XCUT-01]

coverage:
  - id: D1
    description: "All three D-16 states (never-set / live / stored-but-not-in-effect) render as a second line inside each day tile, branching purely on the backend-computed status discriminator, with State A and State C visually distinguishable by color and italic without reading the text"
    requirement: USHF-06
    verification:
      - kind: other
        ref: "source assertion — #d1d5db/#3b82f6/#9ca3af color tokens present, 0.7rem/textOverflow/maxWidth:90px truncation tokens present, and zero occurrences of eraStatus|effectiveTo|dayOffType|effectiveFrom inside UsualShiftLine (confirms no client-side re-derivation)"
        status: pass
    human_judgment: true
    rationale: "The load-bearing visual claim — that the three states read as visually distinct at a glance — is 16-UI-SPEC.md's own 'backstop' truth (E1/populated). This repo has no frontend test framework (Phase 13 P-11, reconfirmed) and this execution environment had no browser/screenshot tooling available, so the claim is proven only by source assertion here; a human must visually confirm the rendered roster."
  - id: D2
    description: "D-05's hours advisory renders as an amber '!' marker with the advisory sentence as the line's title, additive to (never replacing) the state's own text and colour"
    requirement: USHF-06
    verification:
      - kind: other
        ref: "source assertion — hoursAdvisory referenced twice (branch + title override) and #92400e present exactly once inside UsualShiftLine"
        status: pass
    human_judgment: false
  - id: D3
    description: "An operator can set, change and clear an agent's usual shift inline via a native <select> of the desk's live CURRENT-era templates valid for that weekday, with an explicit '— none —' clear option, a disabled retired-option guard, an inline D-03 error on rejection, and no confirmation dialog anywhere"
    requirement: USHF-03
    verification:
      - kind: other
        ref: "source assertion — setUsualShift/clearRow: true/eraStatus/validWeekdays/#92400e present; confirm( count unchanged (2); onChange count +1 exactly (10->11); usual-shift editor region contains no cellDirtyRef/cellEscapedRef/datalist; no onBlur attribute on the <select> tag itself; disabled retired option present"
        status: pass
    human_judgment: true
    rationale: "The picker's actual selection/commit/error/cancel behavior in a real browser (the plan's five-point manual QA: set-and-turn-accent-blue, leave-unset-en-dash, retired-italic-muted, not-worked-italic-muted, hover-tooltip-on-clip) is unverified in this session — no browser tooling was available in this execution environment. A human must click through those five checks before this deliverable is considered UAT-complete."
  - id: D4
    description: "npm run build and npx tsc --noEmit both succeed with zero TypeScript errors and no unused-local warnings after both tasks"
    verification:
      - kind: other
        ref: "cd frontend && npm run build; cd frontend && npx tsc --noEmit"
        status: pass
    human_judgment: false

# Metrics
duration: ~13min
completed: 2026-09-03
status: complete
---

# Phase 16 Plan 05: Usual Shift Roster UI Summary

**A second line inside each of the roster's seven day tiles now shows an agent's usual shift — accent-blue bold when live, light-gray en dash when never set, italic muted with a reason when stored-but-inactive, plus an amber D-05 advisory marker — and clicking it opens a native `<select>` that sets, changes or clears the value in place via `setUsualShift`.**

## Performance

- **Duration:** ~13 min
- **Started:** ~2026-09-03T18:03:00Z (approx.)
- **Completed:** 2026-09-03T18:16:11Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- `frontend/src/api/client.ts` gains `UsualShiftEntry` (`status`/`name`/`reason`/`hoursAdvisory`), `DeskAgent.usualShift: Record<string, UsualShiftEntry>`, and `deskAgents.setUsualShift(deskId, agentId, day, body)` — the exact shape of the neighbouring `setDayHours`.
- `DeskAgents.tsx` gains a `UsualShiftLine` component rendered as a sibling directly beneath the existing hours `<div>` inside each of the seven day tiles (D-15 — tile-internal, no new collapsed-row column). It branches on `entry.status` alone and never re-derives era-current-ness or worked-day state client-side (D-16, confirmed by a zero-hit grep for `eraStatus|effectiveTo|dayOffType|effectiveFrom` inside the component).
- The three D-16 states use distinct tokens: `NOT_SET` — en dash, `#d1d5db`, non-italic; `LIVE` — template name, `#3b82f6` accent, bold; `STORED_INACTIVE` — name + `· retired`/`· not worked`, `#9ca3af`, italic. D-05's advisory renders as an amber `!` marker additive to all three states, never replacing the state's own text or colour.
- Clicking any usual-shift line opens an inline native `<select>` (D-17): `— none —` always first, a disabled `"{name} (retired)"` option when the stored value has no live successor, then every `CURRENT`-era template valid for that weekday sorted alphabetically. The select commits on its own native change event — no Save button, no blur-triggered save, no `cellDirtyRef`/`cellEscapedRef` machinery, since a closed option list has none of the `<datalist>` quirk that machinery exists to work around.
- On a 400 (D-03), an inline amber error renders beneath the still-open select and the control's displayed value reverts to what was stored before the rejected attempt (P-23), leaving the error visible until the operator picks again or clicks away.
- Clicking away with no selection change closes the editor and sends no request, implemented via `onBlur` on the wrapping `<div>` (not the `<select>` tag itself) so blur/focusout bubbles up naturally without adding a dirty-tracking guard or violating the plan's literal "no onBlur on the select" constraint.
- No confirmation dialog anywhere in this plan (Phase 14 D-12 stands) and no destructive red `#ef4444` on any new element (Phase 13's standing colour ruling).

## Task Commits

Each task was committed atomically:

1. **Task 1: The usual shift is visible on every day tile, in all three states** - `298864c` (feat)
2. **Task 2: Clicking a usual-shift line opens an inline picker that saves on selection (D-17)** - `cf266f2` (feat)

## Files Created/Modified
- `frontend/src/api/client.ts` — `UsualShiftEntry` interface, `DeskAgent.usualShift` field, `deskAgents.setUsualShift`
- `frontend/src/pages/DeskAgents.tsx` — `UsualShiftLine` component, usual-shift editor state (`usualShiftTemplates`, `editUsualShift`, `usualShiftValue`, `usualShiftSaving`, `usualShiftError`, `usualShiftCommittingRef`), the P-21 template-fetch `useEffect`, `startEditUsualShift`/`commitUsualShift` handlers, and the inline `<select>` editor block delimited by the `usual-shift editor` / `end usual-shift editor` comment markers

## Decisions Made
- Deferred the P-21 template fetch from Task 1 to Task 2 (see Deviations) — the plan's own text names this exact escape hatch for the `noUnusedLocals` conflict.
- Wired the click-away cancel behavior via `onBlur` on the wrapping `<div>` rather than the `<select>` element, so the literal "no `onBlur` added to the select" acceptance criterion is satisfied by the `<select>` tag itself carrying no `onBlur` prop, while the described cancel behavior still works through native blur/focusout event bubbling in React's synthetic event system.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Deferred Task 1's P-21 template fetch to Task 2**
- **Found during:** Task 1 (adding the `UsualShiftLine` tile display)
- **Issue:** The plan's Task 1 action describes adding the `shiftTemplates.list(deskId)` fetch and its backing `useState`/`useEffect` as part of Task 1, but nothing in Task 1 reads the fetched array (the picker that consumes it is Task 2's work). With `noUnusedLocals: true` in `tsconfig.json`, an unread local would fail `npm run build`, Task 1's own verification gate.
- **Fix:** Moved the `useState<ShiftTemplate[]>` declaration, its `useEffect`, and the `shiftTemplates`/`ShiftTemplate` imports into Task 2, where the picker's option-building functions actually read the array. The plan's own action text names this exact combination as acceptable when the compiler objects.
- **Files modified:** `frontend/src/pages/DeskAgents.tsx`
- **Verification:** `cd frontend && npm run build` passes cleanly after Task 1 alone (commit `298864c`).
- **Committed in:** `cf266f2` (Task 2's commit — the fetch effect and its consuming code landed together)

**2. [Rule 1 - Bug] Reworded two doc comments that tripped the plan's own literal-string acceptance-criteria greps**
- **Found during:** Task 1 (writing the D-05 advisory comment) and Task 2 (writing the D-17 commit-shape comment)
- **Issue:** Task 1's acceptance criteria require `grep -c '#ef4444'` to stay at the pre-task count (0) and `grep -c '#92400e'` within `UsualShiftLine` to equal exactly 1; my own explanatory comment mentioned both hex literals in prose, tripping both checks. Task 2's acceptance criteria require `grep -c 'onChange'` to increase by exactly 1; my own explanatory comment about the `<select>`'s native change event used the literal word "onChange" in prose, adding a second false match.
- **Fix:** Reworded both comments to describe the same facts without the literal matched substrings (e.g. "the destructive red used elsewhere in this app" instead of the hex code; "its own native change event" instead of the word "onChange").
- **Files modified:** `frontend/src/pages/DeskAgents.tsx`
- **Verification:** Re-ran the affected greps after each rewording; all now pass at the exact stated counts.
- **Committed in:** `298864c` (Task 1's fix), `cf266f2` (Task 2's fix)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 — blocking build-order sequencing, explicitly sanctioned by the plan text; 1 Rule 1 — comment wording to satisfy stated acceptance checks). **Impact:** No behavior changes beyond what the plan specified; both fixes were necessary for the plan's own verification gates to pass. No scope creep.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None. Both the display and the picker are wired to the live backend endpoints landed in plan 16-02 (`DeskAgentResponse.usualShift`) and plan 16-01/16-02 (`PUT /desks/{deskId}/agents/{agentId}/usual-shift/{day}`) — no mock data, no hardcoded empty values, no placeholder text.

## Manual QA (not performed this session — routes to human_needed)

This execution environment had no browser or screenshot tooling available (no `chromium`/`chromium-browser`/`google-chrome`, no Playwright installed). Per the phase-specific constraint that this repo has no frontend test framework and purely visual claims cannot be proven by assertion, the plan's five-point manual QA is **not performed** here and is left for a human:

1. Set one weekday's usual shift and confirm the tile turns accent blue and bold — **not observed**.
2. Leave another weekday unset and confirm it shows a light-gray en dash — **not observed**.
3. Retire the template behind a third weekday and confirm it shows the name in italic muted gray with "retired" — **not observed**.
4. Set a fourth weekday to MANDATORY hours and confirm its usual-shift line reads "not worked" — **not observed**.
5. Hover a tile whose template name is long enough to clip and confirm the tooltip shows the full name — **not observed**.

All five are covered by source assertion (correct tokens, correct branch structure, no client-side re-derivation of backend state) but not by visual observation. The plan-level `<verification>`'s two `<human-check>` items (roster tile three-state QA, and the XCUT-01 roster-vs-export end-to-end check) are likewise unresolved by this execution and route to `human_needed` at verify time, consistent with 16-UI-SPEC.md's own `backstop`-marked truths for this surface.

## Next Phase Readiness
- The roster half of XCUT-01 is closed: an operator can see and edit a stored usual shift in the same tile as the hours it's paired with, without a second implementation of D-16's state logic.
- Phase 17's DRFT-02 (drift reporting) can rely on the same `status`/`name`/`reason` discriminator this tile already renders — no new backend shape is needed for a drift panel to reuse.
- Blocker: the five-point manual QA and the two plan-level `<human-check>` items are unresolved pending a human with browser access — this should be the first thing verified before the phase is closed out.

## Self-Check: PASSED

Both modified files (`frontend/src/api/client.ts`, `frontend/src/pages/DeskAgents.tsx`) confirmed present
on disk. Both commit hashes (`298864c`, `cf266f2`) confirmed present in `git log --oneline`. Plan-level
verification (`cd frontend && npm run build`, `cd frontend && npx tsc --noEmit`) re-run clean after all
edits. All Task 1 and Task 2 acceptance-criteria greps re-verified at their exact stated counts after the
Rule 1 comment rewordings.

---
*Phase: 16-usual-shift-storage*
*Completed: 2026-09-03*
