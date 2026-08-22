---
phase: 13-per-day-hours-visibility
plan: 04
subsystem: ui
tags: [react, roster, agent-day-hours, datalist, per-cell-edit]

# Dependency graph
requires:
  - phase: 13-per-day-hours-visibility (plan 01)
    provides: "DeskAgent.dayHours map, expandable per-weekday detail row with 5 display states"
  - phase: 13-per-day-hours-visibility (plan 02)
    provides: "PUT /desks/{deskId}/agents/{agentId}/day-hours/{day} single-row upsert/delete endpoint, SetDayHoursRequest"
provides:
  - "deskAgents.setDayHours(deskId, agentId, day, body) API-client call"
  - "Per-weekday type-or-pick combo (native datalist) covering number / PTO / MANDATORY / not-set, one PUT per edit"
  - "Relabelled 'Set all days to…' bulk action inside the expanded row, with amber overwrite notice and label-count-triggered confirm()"
affects: []

# Actuals (#2632)
actuals:
  tokens: 3772
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Native HTML5 <input type=\"text\" list=\"...\"> + <datalist> as the zero-dependency 'type or pick' combo, resolved on blur/Enter into one of five request shapes"
    - "Escape-guard ref (cellEscapedRef) to suppress the onBlur save handler when the operator cancels via Escape, since blur fires regardless of how focus was lost"
    - "Compound-key single-nullable-state ({ agentId, day } | null) extending the file's existing per-row nullable-id edit-state convention to per-cell"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskAgents.tsx

key-decisions:
  - "Adopted PLAN.md's P-10/P-11 planner decisions verbatim (client validates range not quarter-hour step; no frontend test framework introduced — verification is tsc -b + human-check only)"
  - "editHours (bulk-action state) changed from useState(8) to useState('') so the revealed editor opens blank per 13-UI-SPEC.md E5 'empty', rather than pre-seeded from the agent's current effective hours as the pre-existing editHoursAgentId/editHours triad did"
  - "Client-side per-cell validation error text uses #92400e (the phase's existing warning-amber text color) rather than #ef4444 — 13-UI-SPEC.md's Color table states destructive red is 'not used by any new element in this phase', which is broader than the literal Task 2-only prohibition in PLAN.md's action text, so the rule was applied phase-wide to this new inline message too"
  - "Split the two tasks' interleaved edits (both touch the same expanded-row JSX region and nearby state block) into two atomic commits by temporarily reverting Task 2's state/handler/JSX changes back to the pre-plan bulk-editor shape, committing Task 1 alone, then reapplying Task 2's changes and committing separately — both intermediate and final states were rebuilt with `npm --prefix frontend run build` to confirm each commit stands on its own"

requirements-completed: [UPL-03, UPL-04, UPL-05]

coverage:
  - id: D1
    description: "Per-weekday type-or-pick combo lets an operator set a number (0-24), PTO, MANDATORY, or Not set (default) directly in the expanded row, each edit hitting the one-row PUT .../day-hours/{day} endpoint; out-of-range input is rejected client-side before any request"
    requirement: "UPL-03"
    verification:
      - kind: automated_ui
        ref: "npm --prefix frontend run build (tsc -b + vite build)"
        status: pass
      - kind: other
        ref: "grep -q 'setDayHours:' frontend/src/api/client.ts && grep -q 'day-hours/' frontend/src/api/client.ts && grep -q '<datalist id=\"day-hours-options\">' frontend/src/pages/DeskAgents.tsx && grep -q 'Enter a number from 0 to 24, or choose PTO / MANDATORY / Not set (default).' frontend/src/pages/DeskAgents.tsx"
        status: pass
    human_judgment: true
    rationale: "This repository has no frontend test framework (zero vitest/jest/testing-library, confirmed in 13-RESEARCH.md) — behavioral claims (exactly one PUT per edit, zero requests on invalid input, correct badge/color rendering per state, datalist overflow) require a live desk with an enriched upload and a browser network panel, which was not available in this executor session. Task 1's <human-check> walkthrough is recorded as open unrun-verify entries #5 in .planning/WINDOWS.md."
  - id: D2
    description: "The seven-row fan-out is relabelled 'Set all days to…', always shows the amber overwrite notice, and warns via native confirm() only when at least one of the agent's seven weekdays carries a MANDATORY/PTO label — never merely because rows exist — while the underlying setContractedHours endpoint and its transactional/label-destructive contract are unchanged (Phase 9 D-10)"
    requirement: "UPL-05"
    verification:
      - kind: automated_ui
        ref: "npm --prefix frontend run build (tsc -b + vite build)"
        status: pass
      - kind: other
        ref: "grep -q 'Set all days to…' frontend/src/pages/DeskAgents.tsx && grep -q '#fffbeb' frontend/src/pages/DeskAgents.tsx && grep -q 'deskAgents.setContractedHours(' frontend/src/pages/DeskAgents.tsx"
        status: pass
    human_judgment: true
    rationale: "The label-count confirm() trigger (fires only when dayOffType is non-null on ≥1 of 7 days, never on 'rows exist') and the amber notice's always-visible placement are visual/interactive claims that need a live desk with mixed MANDATORY/PTO/numeric weekdays to exercise both the zero-label (no dialog) and labelled (dialog with accurate count) paths. Task 2's <human-check> walkthrough is recorded as open unrun-verify entry #6 in .planning/WINDOWS.md."

duration: 8min
completed: 2026-08-22
status: complete
---

# Phase 13 Plan 04: Per-Day Hours Editing Summary

**Every weekday in the roster's expanded row is now directly editable through a native-datalist type-or-pick combo covering all five stored states, and the destructive seven-row fan-out survives only as an explicitly labelled "Set all days to…" bulk action that warns before overwriting any MANDATORY/PTO label.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-08-22T00:54:00Z
- **Completed:** 2026-08-22T01:01:30Z
- **Tasks:** 2 (both auto)
- **Files modified:** 2

## Accomplishments
- `deskAgents.setDayHours(deskId, agentId, day, body)` added to `frontend/src/api/client.ts`, calling `PUT .../day-hours/{day}` with a `{ hours? | dayOffType? | clearRow? }` body — the same one-line shape as the neighbouring `setContractedHours`
- Each expanded-row weekday cell is now a native `<input type="text" list="day-hours-options">` combo: typing a number 0–24 sets the hours, picking `PTO`/`MANDATORY` sets the label, picking `Not set (default)` (or clearing the field) deletes the row, and anything else is rejected client-side with the exact copy from the Copywriting Contract, with the field reverted and no request sent
- Zero new dependencies — the combo is HTML5's built-in `datalist`, matching the project's existing hand-rolled, no-component-library convention
- The pre-existing `editHoursAgentId`/`editHours`/`startEditHours`/`saveHours` inline editor is relabelled into an explicit `Set all days to…` control (`Apply`/`Cancel`) inside the expanded row, always showing the amber `#fffbeb`/`#fde68a`/`#92400e` overwrite notice copied verbatim from `ClientManagement.tsx:590-601`
- The bulk action's native `confirm()` fires only when the agent's seven `dayHours` entries include a non-null `dayOffType` — computed client-side, count-accurate, and never triggered merely because rows exist (13-RESEARCH.md Pitfall 4) — while `deskAgents.setContractedHours` itself is unchanged, preserving Phase 9 D-10's one-click uniform-week capability

## Task Commits

Each task was committed atomically:

1. **Task 1: Per-cell type-or-pick combo covering the five stored states** - `736989e` (feat)
2. **Task 2: "Set all days to…" bulk action with the label-overwrite warning (D-07)** - `ac1aa63` (feat)

**Plan metadata:** commit pending (this SUMMARY + STATE/ROADMAP/REQUIREMENTS update)

## Files Created/Modified
- `frontend/src/api/client.ts` - adds `deskAgents.setDayHours` API-client call
- `frontend/src/pages/DeskAgents.tsx` - per-cell combo edit state/handlers (`editCell`/`editCellValue`/`cellError`/`savingCell`), the `day-hours-options` `<datalist>`, and the relabelled bulk-action control (`editHours` now a string, `applyingBulk` flag, label-count `confirm()`)

## Decisions Made
- Adopted PLAN.md's P-10/P-11 planner decisions verbatim (reject-not-clamp client validation on range only, not quarter-hour step; no frontend test framework introduced)
- `editHours` (bulk input) changed from a numeric state seeded with the agent's current hours to a blank string, per 13-UI-SPEC.md E5's explicit "opens blank" requirement — a scope-consistent implementation detail, not a deviation from the plan's action text (which only specifies the revealed editor's contents, not the collapsed→revealed seeding)
- Per-cell validation error text uses the existing warning-amber `#92400e` rather than introducing red, since 13-UI-SPEC.md's Color table forbids `#ef4444` for "any new element in this phase," not just Task 2's control
- See "Deviations from Plan" below for the commit-splitting note (process only, no behavioral deviation)

## Deviations from Plan

None - plan executed exactly as written for both tasks' behavior. One process note, not a Rule 1-4 deviation:

**Commit atomicity across an interleaved file.** Both tasks modify the same `frontend/src/pages/DeskAgents.tsx` region (the expanded row's weekday grid and the line immediately below it) and adjacent state declarations. To keep the "one commit per task" contract despite the interleaving, Task 2's state/handler/JSX changes were temporarily reverted to the pre-plan shape, Task 1 was committed alone and rebuilt clean, then Task 2's changes were reapplied and committed separately — each commit independently passes `npm --prefix frontend run build`.

## Issues Encountered
- Both tasks' `<human-check>` walkthroughs (typed/picked per-cell states, out-of-range rejection, offline-save revert, bulk no-dialog vs labelled-dialog paths, single-line layout) were not run in this session — no live database or BambooHR-configured desk was available to the executor. Recorded as `unrun-verify` entries #5 and #6 in `.planning/WINDOWS.md` (6 open entries total across the phase as of this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- This is the last plan in Phase 13 (4 of 4). All four plans (read path, per-cell/bulk backend endpoints, Excel export, and this frontend editing UI) are now committed.
- Recommend a human UAT pass against a live desk with an enriched upload before shipping, exercising all six open `unrun-verify` items in `.planning/WINDOWS.md` — five UI walkthroughs plus one backend controller-HTTP gap — before the v1.2 milestone close resumes.

## Self-Check: PASSED

- `frontend/src/api/client.ts` and `frontend/src/pages/DeskAgents.tsx` confirmed present on disk
- Both task commit hashes (`736989e`, `ac1aa63`) confirmed present in `git log --oneline --all`
- `npm --prefix frontend run build` — exits 0, confirmed after both commits independently
- All acceptance-criteria greps for both tasks (datalist, error copy, colors, CTA text, confirm copy, Apply/Cancel labels, fan-out survival, no stray `OK`/`X` labels) — confirmed pass

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-22*
