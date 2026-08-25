# XCUT-05: Solver Constraint Mode Classification

**Phase:** 14 — Shift Library & Scheduling Mode
**Generated:** 2026-08-25

## Derivation

The constraint set below was derived from code, twice, independently — never from a stale count
in a planning document. `ConstraintWeights` (`src/main/java/com/wfm/model/ConstraintWeights.java`)
carries **nineteen** `@ConstraintWeight` fields, one per constraint name. Independently,
`ScheduleConstraintProvider.defineConstraints`
(`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`) registers **nineteen**
`Constraint`-returning builder methods. The two derivations agree.

`.planning/codebase/ARCHITECTURE.md`'s figure of **eighteen** is stale — it predates the
`minimumStaffing` constraint, which was added after that map was last written.

**This document is a human-readable mirror, not the enforcement mechanism.**
`src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` is what actually keeps
the classification honest: it asserts, by reflection at test time, that
`ScheduleConstraintClassification`'s key set exactly equals the constraint set the provider
registers, so that a twentieth constraint fails `./gradlew test` until someone adds a row for it.
This markdown table will rot the moment it is edited independently of that test — if this table
and the test ever disagree, the test's `ScheduleConstraintClassification` map is right and this
document is wrong.

## Classification Table

| Constraint | Classification | Basis | Owner |
|---|---|---|---|
| Unassigned assignment | MODE_AGNOSTIC | Operates per-timeslot on assigned-count vs. demand bounds; shift mode still has timeslots and demand — the constraint does not read how a day's shift was chosen. | — |
| Agent day off | MODE_AGNOSTIC | Pure agent × date join against `AgentDayOff`; unaffected by how a day's shift was chosen. | — |
| Specialization match | MODE_AGNOSTIC | ENVL-03 (Phase 15) explicitly keeps specialization variable within the shift envelope, so this per-assignment specialization check is unchanged by shift mode. | — |
| One assignment per timeslot | MODE_AGNOSTIC | Structural (agent, timeslot) uniqueness; mode-independent. | — |
| Exactly one break | NEEDS_SHIFT_VARIANT | One of D-03's named four break constraints. Break is currently derived from assignment gaps (`countContiguousGaps`/`getGapLengths`); D-01 makes shift-mode breaks a fixed template offset instead, so this constraint needs a shift-mode variant. | — |
| Break duration | NEEDS_SHIFT_VARIANT | One of the four. Currently derives duration from the single assignment gap's length; a shift-mode variant compares against the template's fixed `break_duration_minutes` instead. | — |
| Break blocked window | NEEDS_SHIFT_VARIANT | One of the four. Currently derives break position from assignment-gap position relative to the derived shift start/end; a shift-mode variant would compare against the template's fixed envelope instead. | — |
| Break start alignment | NEEDS_SHIFT_VARIANT | One of the four. Currently derives break start from the assignment gap's start; a shift-mode variant is unnecessary in the same form once the break start is template-fixed (D-01), but the constraint as coded still needs re-deriving. | — |
| Prefer primary specialization | MODE_AGNOSTIC | Pure agent-attribute soft preference (primary vs. secondary specialization); unaffected by shift structure. | — |
| Honour preferred start time | OPEN_RESOLVE_IN_PHASE_15 | Penalises a timeslot before `AgentPreference.preferredStartTime` — a solver-derived value compared against a preference. In shift mode the agent's start is chosen from the library, not per-timeslot; whether comparing against a template-fixed value is still the same constraint or needs its own variant cannot be answered without the shift envelope Phase 15 builds. Deliberately left open rather than guessed — this phase touches no solver code. | Phase 15 — Shift Envelope & Coupling |
| Honour preferred break time | OPEN_RESOLVE_IN_PHASE_15 | Currently derives the actual break start from assignment gaps (`findBreakStart`) and compares it to `AgentPreference.preferredBreakTime` — a solver-derived value compared against a preference, same shape as the start-time row above. In shift mode the break start is template-fixed (D-01); whether this constraint should compare the template's fixed break start against the preference, or become moot, needs solver-level judgement Phase 14 is scoped to avoid. Not automatically bundled with D-03's named four break constraints — those cannot exist without a shift envelope, whereas this one is answerable but genuinely undecided today. | Phase 15 — Shift Envelope & Coupling |
| Break clustering | MODE_AGNOSTIC | Constraint body is `penalizeConfigurable(a -> 0)` — a documented no-op placeholder today. Classification is moot until it does something; recorded as mode-agnostic because an inert constraint has no mode-dependent behaviour to gate. | — |
| Contracted hours (over) | MODE_AGNOSTIC | Compares assignment count to `AgentDayConfig.effectiveHours`-derived expected slots; ROADMAP.md's joint-unsatisfiability argument (D-06) explicitly assumes this constraint still applies unchanged in shift mode. | — |
| Contracted hours (under) | MODE_AGNOSTIC | Same basis as Contracted hours (over) — expected-slots comparison, mode-independent. | — |
| Contracted hours (under, zero) | MODE_AGNOSTIC | Same basis as Contracted hours (over) — penalises agents with an `AgentDayConfig` but zero assignments; mode-independent. | — |
| Bulk over-allocation limit | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Bulk under-allocation soft | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Bulk under-allocation hard | MODE_AGNOSTIC | Per-timeslot demand-vs-supply comparison; mode-independent. | — |
| Minimum staffing | MODE_AGNOSTIC | Per-timeslot floor of at least one assigned agent, irrespective of forecast; mode-independent. | — |

## Summary

**Nineteen constraints, nineteen classification rows, zero unclassified:**

- **13 mode-agnostic** — Unassigned assignment, Agent day off, Specialization match, One
  assignment per timeslot, Prefer primary specialization, Break clustering, Contracted hours
  (over), Contracted hours (under), Contracted hours (under, zero), Bulk over-allocation limit,
  Bulk under-allocation soft, Bulk under-allocation hard, Minimum staffing.
- **4 needs-a-shift-variant** — Exactly one break, Break duration, Break blocked window, Break
  start alignment (D-03's named "four emergent break constraints").
- **2 open** — Honour preferred start time, Honour preferred break time (both owned by Phase 15).
- **0 mode-gated.**

`MODE_GATED` is currently empty by design, not by oversight. ROADMAP.md's Phase 14 success
criterion 5 records that the four break constraints listed above "aren't actually mode-gated
until Phase 15" — this phase classifies them as needing a shift-mode variant, but the actual
gating (excluding them from evaluation on shift-scheduled desks, per Phase 15's ENVL-05 work)
has not happened yet. The `MODE_GATED` tag exists in the vocabulary specifically so that Phase 15
can move rows into it once the gating logic ships.

## Open rows handed to Phase 15

Two constraints — **Honour preferred start time** and **Honour preferred break time** — are
deliberately left `OPEN_RESOLVE_IN_PHASE_15`, with Phase 15 (Shift Envelope & Coupling) named as
owner.

Both constraints compare a solver-derived value against `AgentPreference`: `Honour preferred
start time` compares the assignment's timeslot start against `preferredStartTime`; `Honour
preferred break time` compares a break start derived from assignment gaps
(`findBreakStart`) against `preferredBreakTime`. In shift mode, an agent's start and break are no
longer independently solver-chosen per timeslot — they come from the shift template the agent is
assigned to (D-01). Whether comparing a template-fixed value against the same preference is still
the *same* constraint, or needs its own shift-mode variant the way the four break constraints do,
cannot be answered without the shift envelope Phase 15 builds — there is no envelope to reason
about yet in this phase.

Phase 14 deliberately did not guess an answer it cannot verify without touching solver code it is
scoped to avoid. An explicit `OPEN_RESOLVE_IN_PHASE_15` row with a named owner is itself a
classification — a decision to defer, recorded and accountable — never a blank left for someone
to notice was missing.
