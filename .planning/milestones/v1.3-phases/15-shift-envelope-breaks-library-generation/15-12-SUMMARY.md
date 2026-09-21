---
phase: 15-shift-envelope-breaks-library-generation
plan: 12
subsystem: ui
tags: [shift-envelope, react, frontend, divergence, agent-allocation, agent-schedule]

# Dependency graph
requires:
  - phase: 15 (plan 10)
    provides: "ScheduleDetailResponse.ShiftEnvelopeDivergence and the authoritative ShiftDescriptor on every AgentScheduleEntry — this plan is the frontend consumer 15-10's own Next Phase Readiness note flagged as unwired"
  - phase: 15 (plan 09)
    provides: "shift-aware minimum-staffing seat suppression at hours no envelope reaches — the reason the Agent Allocation grid needs a regenerated full-day column set"
provides:
  - "Agent Schedule table Shift column reading the authoritative shift descriptor (template envelope) instead of seat-derived min/max, agreeing with the Agent Allocation group header for the same agent-day"
  - "Divergence rendering (out-of-envelope seats / unworked legal slots) on the Agent Schedule table and per-cell in the Agent Allocation shift-grouped grid, with a shared legend"
  - "Deliberately-unstaffed-hour column treatment in the Agent Allocation shift-grouped grid, distinct from the existing unfilled-demand treatment"
affects: [phase-16-usual-shift-storage, phase-17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 3600
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Descriptor-presence-as-mode-signal: AgentScheduleTab takes no scheduling-mode prop — entry.shift is non-null only on a shift desk, so its presence alone gates the authoritative-vs-seat-derived branch, and a slot desk (where shift is always null) renders exactly as before with zero new code path."
    - "Column-set regeneration strictly inside the shift-grouped branch: `slots` (used by the untouched slot-mode return above the :422 mode branch) is never mutated; a new `shiftSlots` derived variable unions it with the schedule's own start/end/increment grid, scoped entirely to the grouped-variant return block."

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/ScheduleResults.tsx

key-decisions:
  - "ShiftEnvelopeDivergence added as an optional (`divergence?: ... | null`) AgentScheduleEntry field rather than required-but-nullable like `shift`, so pre-existing mock/test fixtures that predate this plan don't need updating to satisfy the type."
  - "Fallback shiftStart/shiftEnd rendering in AgentScheduleTab is left raw (unformatted), unchanged from today — only the authoritative descriptor's times are formatted through toHHMM, since the plan's own truth targets the descriptor path, not the pre-existing slot-desk output."
  - "Envelope containment test (`isEnvelopeReached`) is deliberately NOT band-aware — a slot inside a shift's break window still counts as 'reached' for the header treatment; a held seat rejected for landing inside a band's break window surfaces instead via the per-cell out-of-envelope divergence mark. Commented in place per the plan's explicit instruction."
  - "Out-of-envelope and unworked-legal divergence marks sit strictly after the existing isWork/isBreak decisions in the same per-cell conditional chain, so a normal (non-divergent) cell's rendering is provably unaffected by this plan."

patterns-established:
  - "Amber advisory palette (#fffbeb bg / #fbbf24 border / #92400e text, and #fef3c7 / #d97706 for grid marks) reused verbatim for every new element in this plan — no destructive red introduced, per the phase colour contract."

requirements-completed: [ENVL-10, XCUT-01]

coverage:
  - id: D1
    description: "Agent Schedule table's Shift column reads the authoritative shift descriptor (formatted via toHHMM) when present, falling back to seat-derived shiftStart/shiftEnd when absent — no longer disagreeing with the Agent Allocation group header for the same agent-day."
    verification:
      - kind: other
        ref: "cd frontend && npm run build (tsc -b && vite build) — the project's automated gate per Phase 13 P-11 (no frontend test framework)"
        status: pass
    human_judgment: true
    rationale: "No frontend test framework exists (Phase 13 P-11); agreement between the two views for a live shift-mode schedule needs a human to load the Agent Schedule and Agent Allocation tabs side-by-side and confirm the spans match."
  - id: D2
    description: "An out-of-envelope seat and its surrendered legal slot are both visibly marked (row-level marker in Agent Schedule, per-cell E!/× marks with legend in Agent Allocation) rather than absorbed into a redrawn envelope."
    verification:
      - kind: other
        ref: "cd frontend && npm run build"
        status: pass
    human_judgment: true
    rationale: "Divergence-marking correctness (right cells marked, tooltips accurate, legend legible) requires a human to view a schedule with a real divergence and confirm the marks land where expected — no test framework to assert against a rendered DOM."
  - id: D3
    description: "An hour no shift envelope reaches renders as a deliberately unstaffed column (muted/italic header, tooltip) in the Agent Allocation shift-grouped grid, distinct from the unfilled-demand treatment, instead of vanishing from the grid."
    verification:
      - kind: other
        ref: "cd frontend && npm run build"
        status: pass
    human_judgment: true
    rationale: "Visual distinctness between the two treatments and correct column placement is a rendering judgment call requiring a human to view a shift desk whose library doesn't span the full operating window."
  - id: D4
    description: "The slot-mode branch of Agent Allocation is untouched, byte-for-byte — the mode branch at :422 is still the first rendering decision in the block, and every diff hunk in this plan lands at or after line 597."
    verification:
      - kind: other
        ref: "grep -n 'schedulingMode !== ' frontend/src/pages/ScheduleResults.tsx && git diff --unified=0 frontend/src/pages/ScheduleResults.tsx"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 12: Frontend Shift Envelope Divergence Rendering Summary

**The Agent Schedule table and Agent Allocation grid now render the same authoritative shift envelope plan 15-10 exposed on the backend, and surface envelope breaches and deliberately-unstaffed hours as visible, legended marks instead of a silently redrawn span.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-08-27T17:38:07Z (picked up immediately after 15-11)
- **Completed:** 2026-08-27T17:44:25Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `ShiftEnvelopeDivergence` to `client.ts`, field-for-field matching the backend record from plan 15-10 (`outOfEnvelopeSeats`, `unworkedLegalSlots`), as an optional field on `AgentScheduleEntry`.
- `AgentScheduleTab`'s Shift column now reads `entry.shift` (the authoritative template envelope, formatted through the existing `toHHMM` helper) when present, falling back unchanged to `shiftStart`/`shiftEnd` on a slot desk — closing the exact disagreement the plan's objective named between the group header (:632) and this column.
- A non-empty divergence on an `AgentScheduleTab` row renders an amber inline marker naming the out-of-envelope and unworked-legal counts, with offending slot start times on hover.
- The Agent Allocation shift-grouped grid regenerates the day's full column set from the schedule's own start time, end time and increment — scoped strictly inside the shift-grouped branch — so an hour no shift envelope reaches (which plan 15-09 now suppresses entirely from worked/break/unfilled slots) still appears as a column instead of vanishing.
- That column gets a distinct muted/italic header treatment with a tooltip explaining it is unstaffed by design, visually separate from the pre-existing unfilled-demand red treatment.
- Per-cell divergence marks (`E!` amber-ringed for an out-of-envelope held seat, `×` amber-filled for a surrendered legal slot) sit behind the existing work/break decisions in the same conditional chain, so a normal cell is provably unaffected; the legend gained three new entries naming all of the new marks.
- Verified via `grep -n "schedulingMode !== "` that the mode branch at :422 is still the sole, first-executed guard, and via `git diff --unified=0` that every changed hunk lands at line 597 or later — the slot-mode branch and everything above it is untouched.

## Task Commits

Each task was committed atomically:

1. **Task 1: Type the divergence and make the Agent Schedule table authoritative** - `a54abc3` (feat)
2. **Task 2: Show the breach and the deliberately unstaffed hour in Agent Allocation** - `ce3c502` (feat)

**Plan metadata:** commit to follow this SUMMARY.

## Files Created/Modified

- `frontend/src/api/client.ts` - added `ShiftEnvelopeDivergence` interface; added optional `divergence` field on `AgentScheduleEntry`
- `frontend/src/pages/ScheduleResults.tsx` - `AgentScheduleTab` Shift column reads the authoritative descriptor + divergence marker; `AgentAllocationTab`'s shift-grouped branch gains `shiftSlots` (full-day column regeneration), `isEnvelopeReached` (envelope-only containment test), per-cell out-of-envelope/unworked-legal marks, and three new legend entries — all strictly after the untouched slot-mode branch and mode guard

## Decisions Made

- `divergence` is optional (`?`) rather than required-but-nullable like `shift`, so no existing fixture needs updating.
- Fallback `shiftStart`/`shiftEnd` text stays raw/unformatted in `AgentScheduleTab` — only the new authoritative-descriptor path uses `toHHMM` — since the plan's own truth targets agreement on the descriptor path, not a cosmetic change to today's slot-desk output.
- The envelope containment test is explicitly not band-aware (commented in code): a slot inside a shift's break window still counts as "reached" for the header treatment, since break-window rejection is a per-cell divergence concern, not a column-visibility concern.
- Divergence marks are applied strictly after the existing `isWork`/`isBreak` branches in the same conditional chain, keeping a normal cell's rendering path unchanged.

## Deviations from Plan

None - plan executed exactly as written. Both tasks' `<verify>` commands (`npm run build`, and the `schedulingMode !== ` grep) passed on first attempt with no fix cycles required.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- G-15-10's instrument-side gap closure is complete: the backend (15-10), the seat-supply gate (15-11), and this frontend rendering (15-12) together mean an operator can now see an envelope breach and a deliberately-unstaffed hour without reading raw score data or JSON.
- The divergence rendering and unstaffed-hour treatment are new UI surface that has not yet been exercised against a live shift-mode schedule with a real divergence — flagged for the phase's UAT pass to confirm visually against the phase's own screenshot evidence, as the plan's Task 2 action itself called out.
- No blockers for any remaining 15-13 gap-closure plan in this G-15-10 sequence.

## Self-Check: PASSED

Both key files confirmed present on disk; both task commit hashes (a54abc3, ce3c502) confirmed in `git log`.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
