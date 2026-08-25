# Phase 14: Shift Library & Scheduling Mode - Context

**Gathered:** 2026-08-25
**Status:** Ready for planning

<domain>
## Phase Boundary

An operator can define a desk's shift library and switch that desk into shift-scheduled mode, with
both edits validated against real demand and contracted hours before they ever reach the solver.

**This phase touches no solver code.** `ShiftTemplate` is a plain problem fact, a structural sibling
of `Specialization` (desk-scoped list, separate table, `desk_id` FK — not nested inside `Desk`). The
one solver-adjacent deliverable is the XCUT-05 classification of existing constraints, which reads
`ScheduleConstraintProvider` without modifying it.

**Requirements:** SHLB-01…06, MODE-01…05, XCUT-01, XCUT-05 (partial)

**Not in this phase** (Phase 15 and later): the `AgentShiftAssignment` planning entity, the
`shiftEnvelopeCompliance` hard constraint, mode-gating the four break constraints, any
`solverConfig.xml` change, any benchmark.

</domain>

<decisions>
## Implementation Decisions

### Break Placement Rule

- **D-01:** A shift template's break is a **fixed offset from shift start** — `break_offset_minutes`
  + `break_duration_minutes` on the row. Given a template, the break is fully determined with zero
  solver freedom. A template `08:00–17:00` with offset `240` means a break at `12:00–13:00`.
  Rejected: fixed wall-clock break times (redundant with start/end, and needs separate validation to
  keep the break inside the envelope), and an offset *window* the solver picks inside (would keep
  break placement a live solver decision, so Phase 15's ENVL-05 could not gate the four emergent
  break constraints off, materially enlarging Phase 15).
  — **Reversibility:** costly — widening this to a window later means a new planning variable in the
  solver model plus un-gating four constraints Phase 15 will have gated off, not just a column add.

- **D-02:** Template times are **grid-validated at save and re-checked at the mode switch**.
  `TimeslotGeneratorService` takes `incrementMinutes` per generation and its own comments note the
  grid can be refined later, so alignment is not a constant. Reject a template whose start, end, or
  break boundaries do not land on the desk's current timeslot grid; run the same check inside the
  mode-switch gate, so a later regeneration at a different `incrementMinutes` surfaces as a named
  failure rather than silently misaligning the whole library. Consistent with the phase thesis:
  reported before a solve, not discovered by one.

- **D-03:** The template's break is **self-contained** — it is *not* validated against `Schedule`'s
  existing break config (`breakDurationMinutes=60`, `breakStartAlignment=ON_HOUR`,
  `breakBlockedHours=1.00`, `breakMinShiftHours=4.00`). Rationale: those four constraints are gated
  OFF for shift-scheduled desks by Phase 15's ENVL-05, so validating a template against a rule that
  will never be evaluated for it would block legitimate libraries (e.g. a 30-minute-break shift
  beside a 60-minute-break shift on one desk). Slot-scheduled desks keep the Schedule-level config
  entirely untouched, which is what MODE-05 requires. Also rejected: promoting the four break fields
  onto `Desk` in this phase — it would touch the existing solve path's config resolution, a real
  MODE-05 risk for a phase that otherwise stays clear of solver-adjacent code.

### Validation — Coverage and Contracted Hours

- **D-04:** SHLB-05 / MODE-03 coverage is **structural envelope coverage**, not capacity-aware. For
  every `(date, timeslot)` carrying `StaffingRequirement` demand > 0, assert at least one template
  whose weekday set and effective date range include that date has an envelope containing that slot
  (break excluded), and name the uncovered slots in the failure. "No combination of library shifts
  can cover this window" is a statement about envelopes, not headcount. Headcount shortfall already
  has a home — `SolverService.computeCapacityWarnings` runs pre-solve today. Rejected: a
  bin-packing capacity approximation, which needs eligible-agent + hours + days-off data at
  library-edit time, will sometimes disagree with the real solver, and blocks the operator with no
  override when it is wrong.

- **D-05:** Coverage runs over the desk's **live** timeslots and live `StaffingRequirement` rows
  (`scheduleId IS NULL` — the same set `SolverService` already loads), intersected with each
  template's effective date range. **A desk with zero live demand rows is REFUSED**, with "no
  staffing demand loaded for this desk", never passed vacuously. This follows the rule already
  recorded for Backlog 999.5: missing demand data is reported as missing, never as a clean zero.

- **D-06:** SHLB-06 (net duration matches no agent's effective contracted hours) is **advisory
  everywhere except the guaranteed-infeasible case**. It is a non-blocking warning on save and in
  the library view — the requirement says *reported*, and a template matching nobody today may match
  after tomorrow's roster upload, so blocking would couple library editing to roster state. **But**
  the mode switch refuses when a demanded weekday has no workable `(template, agent)` pair at all:
  that is not a hint, it is a solve that cannot succeed, because `shiftEnvelopeCompliance` and the
  existing `contractedHoursOver`/`Under` constraints become jointly unsatisfiable and no amount of
  solver time fixes it (ROADMAP.md Phase 15 Notes). Rejected: blocking at save, which makes the
  library unbuildable before a roster exists and lets a routine hours edit elsewhere retroactively
  invalidate a saved template with nowhere to surface it — an XCUT-02 write-path trap.

- **D-07:** "Match" means **exact equality** of net duration (`end − start − break_duration`) to the
  agent's `AgentDayHours` value for that weekday, compared as `BigDecimal` via the existing
  `BigDecimals` util. The validator mirrors the constraint it predicts: `Contracted hours (over)` is
  1,001 hard and `(under)` is 100 hard, so any deviation is already a hard violation — an
  approximate match would report "fine" about something the solver calls infeasible. Rejected: a
  ±one-timeslot tolerance (an unpinned guess about solver behaviour that will eventually mask a real
  mismatch) and a per-desk tolerance column (a knob with no evidence behind it, sitting confusingly
  beside Phase 17's genuine per-desk tolerance band).

- **D-08:** SHLB-05's coverage check and MODE-03's refusal are **one implementation called twice** —
  from the shift-library editor and from the mode-switch endpoint — extending
  `SolverService.runPreSolveValidation`'s existing `ErrorDetail` pattern, surfaced through
  `PreSolveValidationException` → 400 `VALIDATION_FAILED` with a populated `details` array.
  (Carried from ROADMAP.md Phase 14 Notes; restated here because D-02 and D-06 add cases to it.)

### Template Lifecycle

- **D-09:** Templates are **mutable rows protected by the existing accept-time snapshot pattern**.
  `ScheduleService.acceptSchedule` already copies live `Timeslot` and `StaffingRequirement` rows
  into schedule-scoped immutable copies (`scheduleId` null = live, non-null = snapshot). Phase 15's
  `AgentShiftAssignment` gets the same treatment, so an accepted schedule carries its own frozen
  shift definitions and editing or retiring a live template cannot reach backwards — SHLB-04
  satisfied by a pattern already proven in this codebase, with no new versioning concept. Rejected:
  immutable versioning (a second identity concept that USHF-01's FK and Phase 15's assignment FK
  both then have to reason about, fragmenting the library the operator is looking at) and
  freeze-once-referenced (templates lock exactly when they become useful, turning routine
  corrections into recreate-and-remap chores across every agent's usual shift).
  — **Reversibility:** costly — the snapshot obligation lands in Phase 15's accept path, so changing
  the protection model later means reworking `acceptSchedule` and any already-accepted shift-mode
  schedules.

- **D-10:** Retirement is expressed by the **effective date range only** — `effective_from`
  (non-null) + `effective_to` (nullable = open-ended). A template applies to date D iff
  `effective_from ≤ D ≤ effective_to`. **There is no `active` boolean.** One mechanism, one
  predicate for the coverage validator to evaluate. This project has been burned twice by two fields
  that can disagree — audit NEW-1 (legacy `contractedHoursPerDay` scalar vs the per-day columns) and
  audit I-1 (model vs view) — and an `active` flag that can contradict the date range is the same
  shape of trap.
  — **Reversibility:** costly — adding an `active` column later is cheap, but every consumer written
  against the single-predicate assumption (validator, library view, Phase 15's value range, Phase
  16's usual-shift resolution) has to be revisited.

- **D-11:** Identity is **unique `(tenant_id, desk_id, name, effective_from)`**, with a check that
  effective ranges for the same name never overlap. `Specialization` uses
  `(tenant_id, desk_id, name)`, but copying that verbatim would make "S1" impossible in two eras —
  the exact thing SHLB-03's date range exists for.
  **Explicit hand-off to Phase 16, created here:** `agent_usual_shift`'s FK points at a specific
  template row, so superseding S1 with a new era leaves every agent pointing at the retired row.
  Phase 16 must decide whether to re-point on supersede or resolve by name+date. This is recorded as
  a known consequence, not left to be discovered.
  — **Reversibility:** one-way — the unique constraint is a migration, and by the time it matters
  Phase 16's `agent_usual_shift` FK and Phase 15's `agent_shift_assignment` FK both point at these
  rows; changing identity afterwards means migrating both dependent tables.

### Mode Switch Mechanics

- **D-12:** SHIFT → SLOT is **freely reversible and ungated**. The slot model has no library
  prerequisite, every constraint it uses is satisfied by construction, and MODE-04 guarantees
  accepted schedules are untouched either way. REQUIREMENTS.md Out of Scope records the reason
  directly: "per-desk optionality is the whole pilot strategy — the fallback must remain", and a
  fallback you cannot actually take is not a fallback. Gate the way in, leave the way out open.
  Rejected: a `confirm()` dialog on a non-destructive action (it trains operators to click through
  warnings that mean nothing — how audit I-3's `confirm()` became mitigation in name only), and
  one-way-once-accepted (locks the rollback exactly on the desk that most needs it).

- **D-13:** A mode switch is **refused with 409 while the desk has a RUNNING solve**, reusing the
  idiom already in the codebase — `BambooRefreshService`'s per-`deskId` `refreshInProgress` map
  throwing `RefreshInProgressException` → 409. The in-flight solve is technically immune
  (`SolverService.startSolve` loads every fact up front under `readOnly` and the entities detach),
  but without the guard an operator can accept a slot-model schedule into a desk now flagged `SHIFT`
  — an unauditable state no later reader can explain. Rejected: stopping the in-flight solve, which
  silently destroys minutes of solver work on a click that does not look destructive, and `STOPPED`
  is itself a legitimate accept state today so the discarded solve would not read as discarded.

- **D-14:** UI — a new desk-scoped **`ShiftLibrary.tsx` page mirroring `Specializations.tsx`** (131
  lines: desk selector, list, inline add/edit, delete), carrying the **mode toggle and the coverage
  validation panel on the same page**. A refusal that names uncovered demand windows is only
  actionable next to the library you would edit to fix it. `DeskManagement.tsx` shows the mode as
  read-only status so the desk list still tells the truth. The shift library page *is* desk
  configuration, so MODE-02 is satisfied. Rejected: putting everything inside `DeskManagement.tsx`
  (124 lines today, would absorb a full CRUD surface plus a validation panel — Phase 13's comparable
  expanded-row work ran to 6 plans).

### Constraint Classification (XCUT-05)

- **D-15:** The classification is a **structure enumerating every constraint with its tag
  (mode-agnostic / mode-gated / needs-a-shift-variant), plus a test asserting its key set exactly
  equals the constraint set `ScheduleConstraintProvider.defineConstraints` actually registers** — so
  adding a 20th constraint fails the build until someone classifies it. XCUT-05's own wording is "no
  constraint is left unclassified", and only a test holds that. Mirror it into a readable markdown
  table in the phase directory for humans; the test is the part that does not rot. Rejected: a
  markdown table alone (a snapshot of today that nothing keeps honest — the precise failure mode
  XCUT-05 exists to prevent) and javadoc tags alone (no completeness enforcement, and Phase 15 would
  have to reassemble the table by grep).

  **Note for the planner:** ARCHITECTURE.md says 18 constraints; ROADMAP.md success criterion 5 says
  19. `minimumStaffing` was added after that map was written. Do not trust either number — derive the
  set from `defineConstraints` at implementation time. That derivation is precisely what makes D-15's
  completeness test worth building.

### Claude's Discretion

- A template may carry a **zero-duration break** (for shifts shorter than a break would suit), and
  never more than one break. If the planner finds a reason multiple breaks are needed, that is a
  deviation worth raising rather than assuming.
- **MODE-05 ("a solve on a slot-scheduled desk produces the same result it did before")** is proven
  by the existing backend suite (315 tests) running unchanged and green, not by authoring a new
  slot-mode fixture. Note the standing caveat carried from Phase 12: no test under
  `src/test/java/com/wfm/solver/` loads the Spring context, so a scoped run cannot catch a
  `solverConfig.xml` regression — irrelevant here only because this phase changes no solver config.
- Exact wording and field naming of the `ErrorDetail` entries for uncovered windows.
- Whether the coverage validator lives as a standalone service or as a method on an existing one —
  D-08 fixes only that it is *one* implementation with two callers.
- How a template's valid-weekday set is stored on the row (a `Set<DayOfWeek>` element collection, a
  join table, or a compact encoding) — a plan-time modelling call.
- Exact Flyway migration number: confirm the actual latest-applied version before writing it. Schema
  head is recorded as V38 and the next is expected to be V39, but this project's own recorded
  discipline (V30 confirmed against V29 at Phase 10) is to verify, not assume.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and requirements
- `.planning/ROADMAP.md` § "Phase 14: Shift Library & Scheduling Mode" — goal, 5 success criteria,
  the three Notes (no solver code; `ShiftTemplate` as a `Specialization` sibling; SHLB-05 and
  MODE-03 are one check reused twice)
- `.planning/ROADMAP.md` § "Phase 15: Shift Envelope & Coupling" Notes — why break-as-structural-
  attribute and `effectiveHours` filtering belong to Phase 15, and the joint-unsatisfiability
  argument behind D-06
- `.planning/ROADMAP.md` § "Phase 16: Usual Shift Storage" Notes — the FK target D-11 hands off to
- `.planning/REQUIREMENTS.md` § "Shift Library (SHLB)", § "Scheduling Mode (MODE)",
  § "Cross-Cutting Requirements" (XCUT-01, XCUT-05), § "Out of Scope" (the pilot-fallback rule
  behind D-12)

### Project state and settled decisions
- `.planning/PROJECT.md` § "Current Milestone: v1.3", § "Key Decisions", § "Known issues after v1.2"
  — audits I-1, I-2, I-3, NEW-1, cited as rationale in D-06 and D-10
- `.planning/STATE.md` § "Accumulated Context → Decisions" — the three v1.3 items settled before
  roadmap creation
- `.planning/research/SPIKE-COUPLING.md` — the empirical coupling finding. **Phase 15 material, not
  Phase 14's**; listed so nobody re-opens it, and because Option C's silent-`0hard/0soft` failure is
  the reason D-15's completeness test is worth its cost.

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — layer boundaries, the `Schedule`/`AgentAssignment` planning
  model, the constraint inventory (stale count — see D-15's note), the accept/persist snapshot flow
  behind D-09

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/wfm/model/Specialization.java` — the desk-scoped-list template verbatim: UUID
  id, `tenant_id`, `desk_id` FK, unique constraint. `ShiftTemplate` is this shape plus times, break
  offset/duration, weekday set, and effective range (D-11 widens the unique key).
- `src/main/java/com/wfm/service/SpecializationService.java` +
  `src/main/java/com/wfm/controller/SpecializationController.java` — the CRUD service/controller
  pair to mirror.
- `frontend/src/pages/Specializations.tsx` (131 lines) — the desk-scoped CRUD page D-14 mirrors.
- `SolverService.runPreSolveValidation` (line ~657) + `ErrorDetail` +
  `PreSolveValidationException` → 400 `VALIDATION_FAILED` with `details` — the validation and error
  shape D-08 extends.
- `BambooRefreshService`'s per-`deskId` `refreshInProgress` map + `RefreshInProgressException` → 409
  — the in-flight-guard idiom D-13 reuses.
- `com.wfm.util.BigDecimals` — the `BigDecimal` comparison helper D-07 uses.
- `SolverService.computeCapacityWarnings` — the existing home for headcount shortfall, which is why
  D-04 does not build a second one.

### Established Patterns
- **Accept-time snapshot** (`ScheduleService.acceptSchedule`): live rows have `scheduleId` null;
  accepting copies them into immutable schedule-scoped rows. This is D-09's whole protection model,
  and Phase 15 inherits the obligation.
- **Multi-tenancy**: every entity carries `tenant_id`; `TenantFilter` → `TenantContext` ThreadLocal;
  isolation is enforced in application code only, with no DB row security. `ShiftTemplate` and the
  new `desk.scheduling_mode` reads must follow this without exception.
- **Flyway forward-only**: `V38__add_consistent_start_weight.sql` is applied on dev and inert;
  editing or deleting an applied migration fails validation and blocks dev deploys.
- **Typed exception → HTTP status** via `GlobalExceptionHandler`; error shape is
  `{ "error": { "code", "message", "details" } }`.

### Integration Points
- `Desk` (`src/main/java/com/wfm/model/Desk.java`) gains `scheduling_mode` (`SLOT`/`SHIFT`, default
  `SLOT`) — the entity currently has only id/tenant/name/description/defaultContractedHoursPerDay,
  so this is a clean addition.
- New `shift_template` table with `desk_id` FK; new repository, service, controller, DTOs.
- `TimeslotGeneratorService`'s `incrementMinutes` grid is what D-02 validates against.
- `StaffingRequirement` (live rows, `scheduleId IS NULL`) is the demand source D-04/D-05 read.
- `AgentDayHours` / `AgentDayConfig` supply the effective contracted hours D-06/D-07 compare against.
- `ScheduleConstraintProvider.defineConstraints` is *read* by D-15's completeness test — never
  modified in this phase.
- Frontend: new `ShiftLibrary.tsx` page + route + nav entry; `DeskManagement.tsx` gains a read-only
  mode column.

</code_context>

<specifics>
## Specific Ideas

- The operator-facing failure the phase is designed around: "switching this desk is refused because
  these specific demand windows are uncovered" — named windows, not a generic validation error. The
  refusal has to be actionable next to the library (D-14) because fixing it means editing a
  template.
- Worked example for D-01: template `08:00–17:00`, `break_offset_minutes = 240`,
  `break_duration_minutes = 60` → break `12:00–13:00`, net working duration 8h.
- The phase's own thesis, in the operator's words: problems are *reported at definition time*, never
  *discovered by a solve*. D-02, D-05 and D-06 each exist to keep that literally true rather than
  approximately true.

</specifics>

<deferred>
## Deferred Ideas

- **Multiple breaks per template** — not modelled; one optional break only (Claude's Discretion).
  If a real desk needs two, that is a data-model change, not a tweak.
- **Capacity-aware coverage validation** — considered and rejected as this phase's gate (D-04). If
  operators later find structural coverage passes on desks that then fail to solve, a capacity
  estimate is a candidate for a future reporting phase, adjacent to Backlog 999.5.
- **Promoting break config from `Schedule` to `Desk`** — rejected here (D-03) as a MODE-05 risk. A
  legitimate future cleanup once shift mode is proven.
- **Per-desk tolerance on the hours match** — rejected (D-07). Phase 17 introduces a real per-desk
  tolerance band for the consistency constraint; do not pre-empt it with a differently-motivated one.
- **Re-pointing `agent_usual_shift` when a template is superseded** — created by D-11, owned by
  Phase 16.

### Reviewed Todos (not folded)
- `2026-08-13-cross-agent-seat-displacement.md` (score 0.9, area: solver) — matched on the word
  "shift", but this phase touches no solver code, and PROJECT.md records it as explicitly **not**
  resolved by v1.3 and deliberately unlinked from any phase so a phase close cannot auto-sweep it
  away unresolved. Phase 15 *measures* the same underlying gap and explicitly does not close it.
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` (score 0.6) — upload-template work;
  overlaps Phase 16 (USHF-02 adds a Usual Shift column to that same template).
- `2026-08-14-terraform-db-password-drift.md` (score 0.6) — infrastructure; matched on the word
  "phase".

</deferred>

---

*Phase: 14-Shift Library & Scheduling Mode*
*Context gathered: 2026-08-25*
