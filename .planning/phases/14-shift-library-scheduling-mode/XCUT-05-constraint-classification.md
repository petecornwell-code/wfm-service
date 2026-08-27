# XCUT-05: Solver Constraint Mode Classification

**Phase:** 14 — Shift Library & Scheduling Mode
**Generated:** 2026-08-25
**Updated:** 2026-08-27 (Phase 15, plan 15-06, Task 1) — see "Phase 15 resolution" below. Also
folds in the "Shift envelope compliance" row plan 15-03 added to the Java classification but never
mirrored here — this document and `ScheduleConstraintClassification.java` had silently drifted
apart in the interim; both now agree again, closing that gap in the same commit as this task's own
changes.

## Derivation

The constraint set below was derived from code, twice, independently — never from a stale count
in a planning document. `ConstraintWeights` (`src/main/java/com/wfm/model/ConstraintWeights.java`)
now carries **twenty** `@ConstraintWeight` fields (nineteen at Phase 14 close, plus "Shift
envelope compliance" from plan 15-03). Independently, `ScheduleConstraintProvider.defineConstraints`
(`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`) registers **twenty**
`Constraint`-returning builder methods. The two derivations agree.

`.planning/codebase/ARCHITECTURE.md`'s figure of **eighteen** is stale — it predates the
`minimumStaffing` constraint (Phase 14) and this milestone's own addition.

**This document is a human-readable mirror, not the enforcement mechanism.**
`src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` is what actually keeps
the classification honest: it asserts, by reflection at test time, that
`ScheduleConstraintClassification`'s key set exactly equals the constraint set the provider
registers, so that a twenty-first constraint fails `./gradlew test` until someone adds a row for
it. This markdown table will rot the moment it is edited independently of that test — if this
table and the test ever disagree, the test's `ScheduleConstraintClassification` map is right and
this document is wrong.

## Phase 15 resolution (plan 15-06, Task 1, 2026-08-27)

Six rows moved this task:

- The four D-03-named break constraints ("Exactly one break", "Break duration", "Break blocked
  window", "Break start alignment") move from `NEEDS_SHIFT_VARIANT` to `MODE_GATED` (ENVL-05):
  break placement in shift mode is the assigned band's offset, not something to discover from
  assignment gaps, so there is no longer a question for a shift-mode variant to answer — each
  constraint is simply gated off for SHIFT desks (an added `ifExists(ScheduleConfig, filtering(...))`
  check; every existing body is untouched byte-for-byte, P-25) and stays fully active, unchanged,
  for SLOT desks.
- The two preference constraints ("Honour preferred start time", "Honour preferred break time")
  move from `OPEN_RESOLVE_IN_PHASE_15` to `MODE_GATED` (ENVL-05/P-26): in shift mode the start
  comes from the library and the break from the assigned band, so both would tune against a
  signal the operator no longer controls per-slot. **Zero rows remain `OPEN_RESOLVE_IN_PHASE_15`
  after this task — XCUT-05 is complete.**

**`PHASE_15_OWNER` is retained verbatim** on the two preference rows as the recorded resolver,
even though neither row is `OPEN` any more — the `Entry` record permits an owner value on any
classification, and only *requires* one on `OPEN_RESOLVE_IN_PHASE_15` rows. The constant still
reads the phase's *former* name ("Shift Envelope & Coupling") deliberately, as a historical record
of who resolved these rows; `14-VERIFICATION.md` item 21 asserts the constant matches this
document byte-for-byte, so both move in the same commit.

Later tasks in this same plan add "Band capacity" (new hard constraint) and reclassify "Break
clustering" (once it has a real body) to `MODE_GATED` as well — not yet reflected below; this
document is updated again as those tasks land.

## Classification Table

| Constraint | Classification | Basis | Owner |
|---|---|---|---|
| Unassigned assignment | MODE_AGNOSTIC | Operates per-timeslot on assigned-count vs. demand bounds; shift mode still has timeslots and demand — the constraint does not read how a day's shift was chosen. | — |
| Agent day off | MODE_AGNOSTIC | Pure agent × date join against `AgentDayOff`; unaffected by how a day's shift was chosen. | — |
| Specialization match | MODE_AGNOSTIC | ENVL-03 (Phase 15) explicitly keeps specialization variable within the shift envelope, so this per-assignment specialization check is unchanged by shift mode. | — |
| One assignment per timeslot | MODE_AGNOSTIC | Structural (agent, timeslot) uniqueness; mode-independent. | — |
| Exactly one break | MODE_GATED | **Phase 15 (was NEEDS_SHIFT_VARIANT):** break placement in shift mode is the assigned band's offset, not something to discover from assignment gaps (`countContiguousGaps`/`getGapLengths`) — gated off for SHIFT desks via an added `ifExists(ScheduleConfig, filtering(...))` check, body untouched; unchanged for SLOT desks. | — |
| Break duration | MODE_GATED | **Phase 15 (was NEEDS_SHIFT_VARIANT):** same reasoning as "Exactly one break" — the band's fixed `duration_minutes` replaces the single assignment gap's length in shift mode. | — |
| Break blocked window | MODE_GATED | **Phase 15 (was NEEDS_SHIFT_VARIANT):** same reasoning — the template's fixed envelope and band offset replace the derived shift start/end and gap position in shift mode. | — |
| Break start alignment | MODE_GATED | **Phase 15 (was NEEDS_SHIFT_VARIANT):** same reasoning — the band's offset is fixed at template-authoring time (D-01, itself grid-aligned by Phase 14's D-02), so there is no solver-chosen break start left to check in shift mode. | — |
| Shift envelope compliance | MODE_GATED | Added by plan 15-03, not previously mirrored here. Option A (SPIKE-COUPLING.md): joins `AgentAssignment` to `AgentShiftAssignment` on (agent, date), then `ScheduleConfig`, filtering to SHIFT mode before penalising a definite disagreement — the hard constraint the whole coupling rests on. Doubly inert on a SLOT-scheduled desk: `SolverService` never populates `AgentShiftAssignment` rows there, and the explicit SHIFT-mode filter means the constraint stays silent even if a shift row were present. | — |
| Prefer primary specialization | MODE_AGNOSTIC | Pure agent-attribute soft preference (primary vs. secondary specialization); unaffected by shift structure. | — |
| Honour preferred start time | MODE_GATED | **Phase 15 (was OPEN_RESOLVE_IN_PHASE_15):** in shift mode the agent's start comes from the assigned library shift, not a per-slot solver decision, so this constraint would tune against a signal the operator no longer controls per-slot — gated off for SHIFT desks. Phase 17's CONS-05 use of `preferredStartTime` at shift granularity (a tiebreak between two equally-scored shifts) is a *new* use of the preference, not a reason to leave this per-slot constraint on. | Phase 15 — Shift Envelope & Coupling |
| Honour preferred break time | MODE_GATED | **Phase 15 (was OPEN_RESOLVE_IN_PHASE_15):** same reasoning — in shift mode the break comes from the assigned band, not a solver-derived gap. | Phase 15 — Shift Envelope & Coupling |
| Break clustering | MODE_AGNOSTIC | Constraint body is `penalizeConfigurable(a -> 0)` — a documented no-op placeholder today. Classification is moot until it does something; recorded as mode-agnostic because an inert constraint has no mode-dependent behaviour to gate. A later task in this same plan gives it a real body and reclassifies it. | — |
| Contracted hours (over) | MODE_AGNOSTIC | Compares assignment count to `AgentDayConfig.effectiveHours`-derived expected slots; ROADMAP.md's joint-unsatisfiability argument (D-06) explicitly assumes this constraint still applies unchanged in shift mode. | — |
| Contracted hours (under) | MODE_AGNOSTIC | Same basis as Contracted hours (over) — expected-slots comparison, mode-independent. | — |
| Contracted hours (under, zero) | MODE_AGNOSTIC | Same basis as Contracted hours (over) — penalises agents with an `AgentDayConfig` but zero assignments; mode-independent. | — |
| Bulk over-allocation limit | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Bulk under-allocation soft | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Bulk under-allocation hard | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Minimum staffing | MODE_AGNOSTIC | Per-timeslot floor of at least one assigned agent, irrespective of forecast; mode-independent. | — |

## Summary

**Twenty constraints, twenty classification rows, zero unclassified:**

- **13 mode-agnostic** — Unassigned assignment, Agent day off, Specialization match, One
  assignment per timeslot, Prefer primary specialization, Break clustering, Contracted hours
  (over), Contracted hours (under), Contracted hours (under, zero), Bulk over-allocation limit,
  Bulk under-allocation soft, Bulk under-allocation hard, Minimum staffing.
- **7 mode-gated** — Exactly one break, Break duration, Break blocked window, Break start
  alignment, Shift envelope compliance, Honour preferred start time, Honour preferred break time.
- **0 needs-a-shift-variant.**
- **0 open.**

XCUT-05 is now complete: every constraint this project's solver evaluates is classified, and
nothing is left `OPEN_RESOLVE_IN_PHASE_15` for a future phase to inherit. (This table grows again
later in this same plan when "Band capacity" and "Break clustering" join the mode-gated set.)

## Historical record — what Phase 14 left open

Phase 14 deliberately did not guess an answer it could not verify without touching solver code it
was scoped to avoid. It tagged the four break constraints `NEEDS_SHIFT_VARIANT` and the two
preference constraints `OPEN_RESOLVE_IN_PHASE_15` with Phase 15 (then named "Shift Envelope &
Coupling") as owner — an explicit deferred classification, recorded and accountable, never a blank
left for someone to notice was missing. The "Phase 15 resolution" section above records how each
of those six rows was actually resolved, once the shift envelope this phase builds existed to
reason about.
