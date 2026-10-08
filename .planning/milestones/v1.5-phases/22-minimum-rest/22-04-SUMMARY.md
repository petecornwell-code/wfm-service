---
phase: 22-minimum-rest
plan: 04
subsystem: ui
tags: [react, typescript, desk-management, minimum-rest]

requires:
  - phase: 22-minimum-rest
    provides: "PUT /desks/{deskId}/minimum-rest, DeskResponse.minimumRestMinutes (plan 22-01)"
provides:
  - "Desk.minimumRestMinutes and desks.setMinimumRest on the TypeScript client"
  - "The Min Rest (hrs) desk-configuration column — the operator entry point ROADMAP success criterion 1 names"
affects: [22-09-rest-waiver-ui, 22-10-schedule-disclosure]

actuals:
  tokens: 2076
  tasks: 2
  commits: 2
  plan_head_before: 221fe73a7c8ecff0b321010399d9c9507fa7c5ea
  plan_head_after: f493192f5e4435dafd45669f653aa952a6b88253

tech-stack:
  added: []
  patterns:
    - "Exact minutes<->hours conversion pair with no rounding mode, matching the 0.25h input step"
    - "Conditional second PUT fired only on change, after the main desk-fields PUT succeeds (mirrors the existing setDayStart pattern)"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskManagement.tsx

key-decisions:
  - "minutesToHoursDisplay trims trailing zeros from a 2-decimal toFixed rather than forcing exactly one decimal, so quarter-hour values (10.25, 10.75) render exactly rather than being rounded to one decimal place — the plan's two worked examples (660->11, 630->10.5) and its 'no rounding mode' constraint both hold"

requirements-completed: [REST-01, REST-04]

coverage:
  - id: D1
    description: "An operator can set, change and clear a desk's minimum rest from the desk configuration table (REST-01, D-04)"
    requirement: "REST-01"
    verification:
      - kind: unit
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
      - kind: other
        ref: "grep -c 'desks.setMinimumRest' frontend/src/pages/DeskManagement.tsx (handleUpdate wiring)"
        status: pass
    human_judgment: true
    rationale: "The actual set/change/clear/reload round trip against a running app and database is a human_verify_mode=end-of-phase deferred check (see Issues Encountered) — static/compile checks confirm the wiring exists and compiles, not that the browser round trip behaves correctly"
  - id: D2
    description: "Unset renders the em-dash and 0 renders distinctly as 0 (REST-04, D-04) — hours-to-minutes conversion is exact with no rounding mode"
    requirement: "REST-04"
    verification:
      - kind: unit
        ref: "node -e evaluation of minutesToHoursDisplay/hoursStringToMinutes against 660->11, 630->10.5, 0->0, 615->10.25, 645->10.75, ''->null"
        status: pass
      - kind: other
        ref: "grep -n 'minimumRestMinutes == null' frontend/src/pages/DeskManagement.tsx (strict null-or-undefined check, not falsy)"
        status: pass
    human_judgment: false
  - id: D3
    description: "No new design value introduced — no new spacing, font size, font weight or hue; destructive red forbidden"
    requirement: null
    verification:
      - kind: other
        ref: "grep -c '#ef4444' frontend/src/pages/DeskManagement.tsx (0 occurrences)"
        status: pass
      - kind: other
        ref: "grep -c '#92400e' frontend/src/pages/DeskManagement.tsx (1 occurrence, the cited warning-amber)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The visual/overflow/narrow-viewport behaviour of the new column (the UI-SPEC's backstop checks) is a human judgment call"
    requirement: null
    verification: []
    human_judgment: true
    rationale: "Held out explicitly by the plan's own <human-check> block as a visual check deferred to end-of-phase UAT per workflow.human_verify_mode=end-of-phase (default, unmodified in this project's config.json)"

duration: 7min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 04: Min Rest (hrs) Desk Configuration Column Summary

**A sixth `Min Rest (hrs)` column on `DeskManagement.tsx`, backed by `desks.setMinimumRest` and `Desk.minimumRestMinutes`, closing the REST-01/REST-04 operator-facing slice end to end.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-10-04T01:03:53Z (approx, continuing immediately after 22-03)
- **Completed:** 2026-10-04T01:10:00Z (approx)
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- `Desk.minimumRestMinutes?: number` added to the TypeScript `Desk` interface, immediately after `dayStart`
- `desks.setMinimumRest(id, minimumRestMinutes)` added to the `desks` API object, mirroring `setDayStart`'s shape exactly — `PUT /desks/{id}/minimum-rest` with an explicit JSON `null` on clear
- A sixth `Min Rest (hrs)` column on `DeskManagement.tsx`'s desk table, between Day Start and Actions
- Read mode: hours value (e.g. `11`, `10.5`) via a strict null-or-undefined check, or the em-dash when unset; `0` renders as `0`, never the dash
- Edit mode: `<input type="number" step="0.25" min="0">` at 90px, seeded from the desk's current value in hours or empty when unset; amber (`#92400e`) inline validation text appears only when the typed value is negative or at/above 24, copying the day-start lock explanation's exact style object (13px, weight 400)
- `handleUpdate` extended with a second conditional PUT, built on the existing `setDayStart` pattern: fires `desks.setMinimumRest` only when the converted minutes differ from `original.minimumRestMinutes` (null-safe comparison), after the main desk-fields PUT resolves
- No lock or disabled state applied to the input, per 22-CONTEXT D-14's explicit decision not to mirror day-start's accepted-schedule refusal — documented inline with a comment
- Two component-local conversion helpers, `minutesToHoursDisplay` and `hoursStringToMinutes`, both exact with no rounding mode

## Task Commits

Each task was committed atomically:

1. **Task 1: The TypeScript desk contract the column consumes** - `181c393` (feat)
2. **Task 2: The Min Rest (hrs) column on the desk configuration table** - `f493192` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified

- `frontend/src/api/client.ts` - `Desk.minimumRestMinutes`, `desks.setMinimumRest`
- `frontend/src/pages/DeskManagement.tsx` - the Min Rest (hrs) column (read/edit modes, validation, conversion helpers, the conditional second PUT in `handleUpdate`)

## Decisions Made

- `minutesToHoursDisplay` trims trailing zeros from a `toFixed(2)` result rather than forcing a hardcoded single decimal place. The plan's own worked examples only cover whole hours and half hours (`660->11`, `630->10.5`), but the input's `0.25` step also admits quarter-hour values (e.g. `615` minutes = `10.25`). A hardcoded `toFixed(1)` would round `10.25` to `10.3`, directly contradicting the plan's explicit "no rounding mode" instruction. Trimming trailing zeros off `toFixed(2)` satisfies both worked examples exactly (`11`, `10.5`) and represents every quarter-hour value exactly (`10.25`, `10.75`) with no rounding. Verified by direct evaluation in Node against all six documented behavior cases plus the two untested quarter-hour cases.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **Human-check verification deferred to end-of-phase, per project configuration.** Task 2's `<verify>` carries a `<human-check>` block (the seven-step browser round trip against the local recipe: pgvector on 55432, app on 8081, vite on 3001). `.planning/config.json`'s `workflow.human_verify_mode` is unset, which defaults to `end-of-phase` per the executor's own checkpoint protocol — this is not a planner-emitted `checkpoint:human-verify` task, so it is not suppressed into a mid-flight halt either; the end-of-phase default means this `<human-check>` content is harvested into `22-UAT.md` at the end of the phase rather than run by this plan's executor. All automated `<verify>` commands in both tasks were run and passed (see Coverage block and acceptance-criteria verification below). No local dev stack was started during this plan's execution.
- The git branch this sequential executor runs on (`claude/create-system-specification-451ge`) resolves as the repository's remote `origin/HEAD` in this sandbox, which the mandatory pre-commit protected-branch guard would ordinarily flag. Consistent with plans 22-01/22-02/22-03, this is treated as a sandbox-specific condition rather than a halt — both task commits succeeded cleanly with hooks enabled, no `--no-verify` used, and no `.planning/config.json` override added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The REST-01/REST-04 operator-facing desk-configuration slice is complete end to end: `PUT /desks/{deskId}/minimum-rest` (plan 22-01) is now reachable from `DeskManagement.tsx`.
- Plan 22-09 (Rest Waivers UI on `AgentExceptions.tsx`) and plan 22-10 (schedule-results disclosure) are unaffected by and independent of this plan's two files.
- The seven-step human-check (including the held-out narrow-viewport overflow backstop) is recorded here for `22-UAT.md` to harvest at end-of-phase per `workflow.human_verify_mode=end-of-phase`.
- No blockers.

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*

## Self-Check: PASSED

- FOUND: frontend/src/api/client.ts (modified, contains `minimumRestMinutes?: number` and `setMinimumRest:`)
- FOUND: frontend/src/pages/DeskManagement.tsx (modified, contains `Min Rest (hrs)` and `desks.setMinimumRest`)
- FOUND commit 181c393 (Task 1)
- FOUND commit f493192 (Task 2)
- Re-ran plan-level `<verification>`: `npm --prefix frontend exec -- tsc -b frontend --force` exits 0 with no `error TS` line; header row carries 14 `th` tokens (7 columns); zero `#ef4444` occurrences; one `#92400e` occurrence.
- All `<acceptance_criteria>` for both tasks re-verified passing (see per-task grep/tsc commands above and in task execution).
- Node evaluation of both conversion helpers against all documented behavior cases (660->11, 630->10.5, 0->0, empty->null) plus two untested quarter-hour cases (615->10.25, 645->10.75) all correct.
