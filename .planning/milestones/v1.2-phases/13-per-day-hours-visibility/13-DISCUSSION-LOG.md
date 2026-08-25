# Phase 13: Per-Day Hours Visibility - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-21
**Phase:** 13-per-day-hours-visibility
**Areas discussed:** Where per-day hours appear, What the Hours/Day cell shows, Which default to fall back to, What Edit Hours should do

---

## Where per-day hours appear

| Option | Description | Selected |
|--------|-------------|----------|
| Expandable row | Click an agent to expand a sub-row showing the 7 weekdays inline. One page, no navigation, several agents comparable at once. | ✓ |
| Side panel / drawer | Slide-out panel with the full weekly pattern plus room for future detail. One agent at a time; covers the table. | |
| Hover tooltip | Reveal the 7 values on hover. Cheapest, zero layout disruption, but undiscoverable, unusable on touch, uncopyable. | |
| Separate detail view | Dedicated per-agent page. Most room to grow, but a navigation round-trip for a verification glance. | |

**User's choice:** Expandable row
**Notes:** Driven by the roster already being 13 columns wide — seven more top-level columns were rejected before the question was asked. The user selected the mockup preview showing a collapsed `6-8` summary expanding to a Mon–Sun strip with `MANDATORY` and `PTO` inline.

---

## Export shape

| Option | Description | Selected |
|--------|-------------|----------|
| Seven columns, mirror the template | Mon–Sun columns matching the upload template's shape and header order via `EnrichedColumnLayout`. Clean round-trip; reuses the layout the phase already touches for I-4. | ✓ |
| Seven columns, own headers | Locally-defined export headers. Simpler, but reintroduces the drift risk UPL-09 exists to prevent. | |
| Keep one column, show a summary | Single column rendering the same summary string as the roster cell. Smallest change, but the export stops being round-trippable. | |

**User's choice:** Seven columns, mirror the template
**Notes:** Makes export → fill → re-upload a genuine round-trip, completing the intent behind Phase 10 D-14's pre-seeded template.

---

## What the Hours/Day cell shows

This area was **redirected by the user mid-discussion.** The originally drafted questions (how to render five states; how to display the MANDATORY-plus-zero union) were withdrawn in favour of the user's own proposal.

**User's proposal:** *"can we have a dropdown for each cell 0-23, PTO, MANDATORY"*

Claude flagged three conflicts against the code before accepting the range:
- The upload clamps at **24**, not 23 (`DeskAssignmentUploadService.java:693-695`), so `24.00` is a reachable stored value a 0–23 list could not display.
- Fractions are real — `hours` is `precision=5, scale=2`, the parser does `setScale(2, HALF_UP)` (`:697`), and the existing editor already uses `step="0.25"`. An integer list could not show an agent whose upload set `7.5`.
- A fifth state exists: a weekday with **no row**, which falls back to the default (Phase 9 D-04) and is distinct from `0`.

| Option | Description | Selected |
|--------|-------------|----------|
| Type-or-pick combo | Type any value 0–24 in 0.25 steps, or pick PTO / MANDATORY / Not set. Nothing an upload can produce becomes undisplayable. | ✓ |
| Whole hours 0–24 + labels | Plain 28-option dropdown, no typing. Simplest, but 7.5 has no matching option. | |
| Quarter-hour dropdown 0–24 | Every 0.25 step as an option. Lossless without typing, but ~100 entries per cell. | |
| Number input + status picker | Separate number box and status selector. Keeps the concepts visually distinct, at two controls per day. | |

**User's choice:** Type-or-pick combo — then confirmed the range explicitly as **"0-24"** in a follow-up message.
**Notes:** The user's dropdown idea proved better than the display-only framing Claude had drafted, because it collapses display and editing into one control and closes finding I-3 structurally rather than by adding a guard.

---

## Saving behaviour

| Option | Description | Selected |
|--------|-------------|----------|
| Save per cell | Each change writes one weekday immediately. Touches one `AgentDayHours` row; cannot disturb the other six. | ✓ |
| One Save button per row | Edit all 7, commit together. Fewer round-trips, but reintroduces the multi-row write that caused I-3. | |

**User's choice:** Save per cell

---

## Which default to fall back to

Raised by Claude during codebase scouting, **not** present in the milestone audit: the solver falls back to `Schedule.defaultContractedHoursPerDay` (`SolverService.java:925`) while the roster falls back to `Desk.defaultContractedHoursPerDay` (`DeskAgentService.java:49-50`). Different entities, different values.

| Option | Description | Selected |
|--------|-------------|----------|
| Match the solver | Use the schedule default, as `resolveEffectiveHours` does. Cost: the roster has no schedule context, so one must be chosen. | ✓ |
| Keep the desk default, label it | Keep today's behaviour but render it explicitly as inherited. No schedule-selection problem, but the number can still diverge from the solver's. | |
| Show no number at all | State only what the agent has, never what they'd inherit. Impossible to be wrong; less useful. | |

**User's choice:** Match the solver
**Notes:** Reasoning accepted: keeping a second fallback rule would leave a smaller version of the exact bug the phase exists to fix. The schedule-selection mechanism was deliberately left open for research rather than guessed at here.

---

## What Edit Hours should do (I-3)

| Option | Description | Selected |
|--------|-------------|----------|
| Keep as explicit "set all days" | Retain the fan-out as a deliberate, labelled bulk action that warns before overwriting MANDATORY/PTO. Uniform weeks stay one click; Phase 9 D-10's intent survives. | ✓ |
| Remove entirely | Per-cell editing fully replaces it. Cleanest, but a uniform week becomes 7 edits and D-10 is superseded. | |
| Keep unchanged | Smallest diff, but leaves the I-3 label-wiping behaviour in place — the audit finding stays open. | |

**User's choice:** Keep it as an explicit "set all days"
**Notes:** Selected the mockup showing the bulk control beneath the expanded weekday strip. This area was largely pre-resolved by the per-cell save decision above — the remaining question was only what becomes of the legacy control.

---

## Claude's Discretion

- Exact expand/collapse affordance; whether the expanded row reuses the existing PTO-dates sub-table pattern in `DeskAgents.tsx`.
- Precise collapsed-summary rendering (range vs word vs total) — the accepted mockup showed a range, but the exact form is a planner/UI call.
- Response DTO shape for per-day hours — genuinely open, since Phase 9 D-12 deliberately deferred locking it until a real consumer existed.
- Whether the "set all days" warning is a confirm dialog or an inline notice.

## Open Questions (routed to research)

- Which schedule's default the roster reads, given the page is desk-scoped, and what to do when a desk has zero or several schedules. Constraint: whatever is chosen must not introduce a second fallback rule.

## Deferred Ideas

- **I-2** — manual "Refresh from BambooHR" bypassing `AgentMergeService`. A scoping decision, not a defect; needs its own discussion.
- **Retiring the `Agent.contractedHoursPerDay` scalar** once nothing reads it — a migration across five write sites; Phase 9 D-05 deliberately kept it.

## Todos Reviewed, Not Folded

- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — matched 0.9 on keywords but is **stale**; delivered by Phase 10 as UPL-09. Recommend closing.
- `2026-08-13-cross-agent-seat-displacement.md` — solver work, Phase 12 successor.
- `2026-08-14-terraform-db-password-drift.md` — infrastructure.
