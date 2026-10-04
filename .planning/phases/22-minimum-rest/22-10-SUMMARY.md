---
phase: 22-minimum-rest
plan: 10
subsystem: ui
tags: [react, typescript, schedule-results, rest-waiver, disclosure]

# Dependency graph
requires:
  - phase: 22-minimum-rest
    provides: "22-08's ScheduleSummary.appliedRestWaiverCount/unusedRestWaiverCount and ScheduleDetail.restWaiverDisclosure (backend); 22-09's RestWaiverEntry/RestWaiverDisclosure TypeScript types on client.ts"
provides:
  - "A header badge on ScheduleResults.tsx reading 'Rest Waivers: N applied, M unused', live on the two-second summary poll, hidden entirely when rest is unconfigured, clickable through to the new tab"
  - "A Rest Waivers tab on ScheduleResults.tsx, positioned between Constraint Violations and PTO, with Applied and Unused sub-tables, six columns per UI-SPEC Section 3(b)'s normative table"
affects: []

# Actuals (#2632)
actuals:
  tokens: 2477
  tasks: 2
  commits: 3
  plan_head_before: 8d1b0f62b09b593916d034dcf28157e713f37838
  plan_head_after: 646195d0d8db74709feeb40d69b85887762bb8b2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Loose `!= null` checks for JSON-null-serialized optional Integer/DTO fields, matching DeskManagement.tsx's existing `desk.minimumRestMinutes == null` convention for the identical situation -- strict `!== undefined` is the wrong guard here because Jackson serializes an absent Integer as JSON `null`, not an omitted key"
    - "Configured-or-not derived from presence of either optional count field, rather than a dedicated snapshotted-minimum-rest field, since no such field exists on ScheduleDetail/ScheduleSummary in either the Java or TypeScript contract -- the backend's own invariant (counts present iff snapshot non-null) makes the two checks equivalent by construction"

key-files:
  created: []
  modified:
    - frontend/src/pages/ScheduleResults.tsx

key-decisions:
  - "Followed 22-UI-SPEC.md's Section 3(b) normative column table (six columns: Agent, Prior shift, Next shift, Required gap, Measured gap, Reason) rather than the UI Considerations row's 'seven columns' reference, per this plan's own planner_notes resolving that self-contradiction. Restated here per the plan's instruction so a later UI review counting seven knows why there are six -- a one-line revision to the Section 3(b) table (splitting date and time into separate columns) would be the clean fix for an operator who wants seven, but that is a UI-SPEC change, not a defect in this implementation."
  - "There is no separate 'snapshotted minimumRestMinutes' field on ScheduleDetail or ScheduleSummary in either the Java DTOs or the TypeScript client, despite the plan's <interfaces> block describing a detail-response snapshotted value to read with a summary-count fallback. Used presence of either optional count (appliedRestWaiverCount/unusedRestWaiverCount) as the single configured/unconfigured signal for both the badge and the tab, which is correct by the backend's own documented invariant (both counts are present if and only if the schedule's snapshotted minimum rest is non-null) and is simpler than reconciling two sources that would always agree."
  - "Added the `restWaivers` member to the `activeTab` state union in Task 1 (not Task 2), so the badge's click handler compiled immediately; Task 2 added the remaining three places (tab-button array, label ternary, content switch) per the plan's explicit instruction to state which ordering was chosen."
  - "[Rule 1 - Bug, self-caught before SUMMARY] Fixed strict `!== undefined` guards to loose `!= null` on all four optional-field checks (the badge's visibility guard and two count renders, the tab's configured check, and both gap-column renders). The backend serializes an absent Integer DTO field as JSON `null`, not an omitted key, so these fields arrive as `null` at runtime even though the TypeScript type declares them `?: number` (which only guarantees 'possibly absent', not which JS value represents absence). The original strict checks would have left the badge visible with blank counts for a desk that never configured rest -- the exact inverse of the UI contract's 'hidden entirely when null' requirement. Fixed to match the codebase's own established convention for this exact situation (DeskManagement.tsx's `desk.minimumRestMinutes == null`)."

requirements-completed: [REST-07]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "The header badge reads 'Rest Waivers: N applied, M unused' as the last span in the existing header row, hidden entirely when the schedule's rest is unconfigured, showing both zeros when configured with nothing waived, sourced live from the two-second summary poll (not the slow 30-second detail fetch), and clickable through to the Rest Waivers tab"
    requirement: "REST-07"
    verification:
      - kind: integration
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
      - kind: other
        ref: "grep -c appliedRestWaiverCount frontend/src/pages/ScheduleResults.tsx == 5 (merge block + both render sites + visibility guard, each referencing it); window-scoped scan around the merge block's feasibleAt assignment shows 6 RestWaiverCount occurrences, proving both counts are inside the 2-second summary merge, not only the 30-second detail fetch"
        status: pass
    human_judgment: true
    rationale: "The live round trip (badge visibility against a real unconfigured vs configured desk, the fast-tick update during a RUNNING solve, the click-to-switch-tab behavior, and the visual read that the badge does not pull the eye ahead of Status/Feasible) is held out to the plan's own <human-check> block, deferred to end-of-phase UAT per workflow.human_verify_mode=end-of-phase (unset in .planning/config.json, default applies) -- consistent with every prior plan in this phase (22-04, 22-09). Static/compile checks confirm the wiring exists, reaches the correct fields, and compiles; they do not prove the browser behavior."
  - id: D2
    description: "The Rest Waivers tab sits between Constraint Violations and PTO, with the two distinct fixed empty-state strings, and when populated renders Applied above Unused as two sub-tables (six columns per UI-SPEC Section 3(b)), success-green Applied rows and neutral-grey Unused rows, one-decimal-hour gap formatting, and the em-dash for absent pair-derived fields while agent/dates/reason still render"
    requirement: "REST-07"
    verification:
      - kind: integration
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
      - kind: other
        ref: "grep -c restWaiverDisclosure == 3; grep -c \"restWaivers'\" == 5 (state union, tab-button array, content switch all present, positioned between violations and pto); grep -c '#15803d' increased 10->11 (Applied success treatment present, no new amber/red); grep -c 'toFixed(1)' increased 6->8 (both gap columns formatted); grep -c schedule.errorMessage unchanged at 2 (no second refusal-rendering path added); both fixed empty-state strings ('Minimum rest is not configured for this desk.' / 'No rest waivers recorded for this schedule.') present verbatim and distinct"
        status: pass
    human_judgment: true
    rationale: "The tab's visual/interaction behavior (sub-heading order, color read at a glance, gap-column unmisreadability beside date/time columns, an actual unused-waiver-with-dashed-pair-fields round trip, the never-configured vs nothing-recorded empty-state distinction, the pre-solve refusal banner, and the three held-out backstop checks for narrow-viewport reflow and the one-count-only partial case) is the plan's 9-step <human-check>, deferred to end-of-phase UAT per workflow.human_verify_mode=end-of-phase, consistent with 22-04 and 22-09. All automated <verify> commands in Task 2 ran and passed; none were skipped."

# Metrics
duration: 22min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 10: Rest Waiver Disclosure UI on ScheduleResults Summary

**A header badge reading applied/unused waiver counts live on the two-second summary poll, plus a Rest Waivers tab with Applied and Unused sub-tables between Constraint Violations and PTO — both additive to `ScheduleResults.tsx`, introducing no new design value.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-10-04T03:57:00Z (approx.)
- **Completed:** 2026-10-04T04:19:12Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Extended the summary poll's two-second merge block (the `setSchedule(prev => ...)` call inside `tickSummary`) to carry `appliedRestWaiverCount`/`unusedRestWaiverCount` alongside `status`/`score`/`feasible`/`feasibleAt`, so the header badge stays live during a RUNNING solve rather than lagging on the 30-second detail fetch (REST-07, D-13).
- Added the header badge span — `Rest Waivers: {applied} applied, {unused} unused` — as the last item in the existing header row, copying the adjacent elapsed-time span's exact muted style (`#6b7280`, `0.85rem`), hidden entirely when the schedule's rest is unconfigured, always showing both counts (including zero) when configured, and clickable through to the new tab.
- Added the `restWaivers` tab: the `activeTab` union member, the tab-button array entry, the label-ternary branch, and the content-switch branch — all four positioned between `violations` and `pto` (compliance-adjacent grouping, not PTO-adjacent).
- Built `RestWaiversTab`: two distinct fixed empty-state lines (never-configured vs. configured-with-nothing-recorded), and when populated, two sub-tables under "Applied" and "Unused" sub-headings — Applied first, success-green (`#15803d`/`#f0fdf4`) rows, Unused neutral-grey (`#6b7280`/`#f3f4f6`) rows, six columns (Agent, Prior shift, Next shift, Required gap, Measured gap, Reason) per the UI-SPEC's normative Section 3(b) table, gaps formatted as one-decimal hours with an `h` suffix.
- Absent pair-derived fields (an unused waiver with no pair — a day off, an unrostered agent, or no predecessor) render the em-dash in just that cell, while the agent, dates, and reason still render, matching the behavior spec exactly.
- No change to the existing `schedule.errorMessage` refusal-banner rendering path — confirmed by an unchanged occurrence count.
- **Self-caught deviation before writing this SUMMARY:** fixed four optional-field guards from strict `!== undefined` to loose `!= null`, matching the codebase's own convention for this exact JSON-null-vs-TypeScript-optional situation (see Deviations below).

## Task Commits

Each task was committed atomically:

1. **Task 1: The header waiver badge, live on the two-second poll** - `cfa6d2c` (feat)
2. **Task 2: The Rest Waivers tab, Applied above Unused** - `6877fab` (feat)
3. **Deviation fix: loose null checks for optional waiver-count/gap fields** - `646195d` (fix)

**Plan metadata:** (this commit)

## Files Created/Modified

- `frontend/src/pages/ScheduleResults.tsx` - two-second summary merge block extended with the two count fields; header badge span; `restWaivers` tab (union member, button, label, content-switch); `RestWaiversTab`/`RestWaiverTable` components

## Decisions Made

- Followed the UI-SPEC's Section 3(b) normative six-column table over its own UI Considerations row's "seven columns" reference, per this plan's planner_notes resolving a documented self-contradiction in the approved contract. See key-decisions above and the planner_notes block in `22-10-PLAN.md` for the full reasoning.
- Derived the configured/unconfigured signal from presence of either optional count field rather than a separate snapshotted-minimum-rest field, since no such field exists in either DTO contract — see key-decisions above.
- Added the `restWaivers` tab union member in Task 1 so the badge's click handler compiled immediately, per the plan's instruction to pick an ordering and state it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Strict `!== undefined` guards replaced with loose `!= null` on all optional waiver-count/gap fields**
- **Found during:** Self-check pass, after Task 2's automated `<verify>` commands had already passed
- **Issue:** `ScheduleSummary.appliedRestWaiverCount`/`unusedRestWaiverCount` and `RestWaiverEntry.requiredGapMinutes`/`measuredGapMinutes` are boxed `Integer` fields on the Java side; when absent, Jackson serializes them as JSON `null`, not an omitted key. The TypeScript type's `?: number` only declares "possibly absent" — it says nothing about which JS value (`null` vs `undefined`) represents that absence at runtime. The initial implementation used strict `!== undefined` checks, which treat a JSON `null` as "present," which would have left the header badge visible (with blank/empty counts) for a desk that never configured rest — the exact opposite of the UI contract's "hidden entirely when unconfigured" requirement — and would have skipped the em-dash fallback on the gap columns for genuinely absent pair-derived values.
- **Fix:** Changed all four affected checks (the badge's visibility guard, the two count-render fallbacks, the tab's `configured` check, and both gap-column render fallbacks) to loose `!= null`/`== null`, matching the exact convention `DeskManagement.tsx` already uses for the identical situation (`desk.minimumRestMinutes == null`).
- **Files modified:** `frontend/src/pages/ScheduleResults.tsx`
- **Verification:** Re-ran `npm --prefix frontend exec -- tsc -b frontend --force` (exit 0, no `error TS`) and all of Task 1 and Task 2's grep-based acceptance checks; all still pass with identical or higher counts.
- **Committed in:** `646195d`

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug, a correctness issue that would have inverted the badge's hide-when-unconfigured contract).
**Impact on plan:** Necessary for correctness; no scope creep. The bug was never exercised by the plan's own `<verify>` gates (`tsc` cannot distinguish `null` from `undefined` on an optional field, and the grep checks only assert a guard exists, not its equality operator), so it would have reached the end-of-phase human-check undetected had it not been self-caught here.

## Issues Encountered

- **Not fixed in this plan (architectural, backend, out of scope, flagged in 22-08 already):** `appliedRestWaiverCount`/`unusedRestWaiverCount` are accurate only for a schedule whose transient collections are populated — true for every RUNNING/COMPLETED in-memory schedule and for `getScheduleDetail`'s accepted-path load, but an ACCEPTED schedule reached through `ScheduleService.listSchedules`/`getScheduleSummary`'s DB-fallback branch silently returns `0`/`0` regardless of the schedule's true waiver state (a bare repository read that never populates the collections `buildRestWaiverDisclosure` walks). This plan's badge is wired correctly against the fields as documented and renders whatever the backend sends — it has no way to distinguish a true `0`/`0` from this fallback's false `0`/`0`. An operator viewing an older accepted schedule's list/summary (not its detail page, which uses the populated path) could see a confident "0 applied, 0 unused" badge that is actually "unknown." Flagged here per the required_reading's explicit instruction to report rather than paper over; tracked for the phase verifier, not fixed here (out of this plan's scope per 22-08's own SUMMARY).
- **The pre-commit protected-branch guard did not fire** this session: the executing branch (`claude/create-system-specification-451ge`) is a feature branch, not one of the five protected names, so all three commits proceeded with normal hooks and no `--no-verify`. (Recorded per project convention even though the guard was not actually triggered this time, unlike prior plans in this phase.)
- **Human-check verification deferred to end-of-phase**, per `.planning/config.json`'s unset `workflow.human_verify_mode` defaulting to `end-of-phase`. Task 2's 9-step `<human-check>` (including the three held-out backstop checks: E4 overflow/partial and E5 overflow) is not run by this plan's executor; it is recorded here for `22-UAT.md` to harvest at end of phase, consistent with 22-04 and 22-09. All automated `<verify>` commands in both tasks ran and passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- REST-07 is now fully delivered end-to-end: the backend computes the disclosure (22-08), the operator can record/remove waivers (22-09), and the operator can see applied/unused waivers directly on the schedule page (this plan) — a waived rest violation can no longer hide from the person it is for.
- This is Phase 22's final plan (10 of 10). All ten plans now have SUMMARY.md files; the phase is ready for `/gsd-verify-work 22` and the accumulated 20-step human-check across 22-04/22-09/22-10 (including the known backend-limitation flag above) should be read before sign-off.
- No blockers.

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*

## Self-Check: PASSED

- FOUND: frontend/src/pages/ScheduleResults.tsx
- FOUND commit `cfa6d2c` (Task 1) in `git log --oneline --all`
- FOUND commit `6877fab` (Task 2) in `git log --oneline --all`
- FOUND commit `646195d` (deviation fix) in `git log --oneline --all`
- Re-ran `npm --prefix frontend exec -- tsc -b frontend --force`: exit 0, no `error TS` line
- Re-ran all acceptance-criteria greps for both tasks post-fix: `appliedRestWaiverCount` = 5, merge-block window scan = 6 `RestWaiverCount` occurrences, `restWaiverDisclosure` = 3, `restWaivers'` = 5, `#15803d` = 11 (baseline 10), `toFixed(1)` = 8 (baseline 6), `schedule.errorMessage` = 2 (unchanged) — all match or exceed the plan's stated thresholds
- No file deletions in any of the three commits (`git diff --diff-filter=D` empty for all)
