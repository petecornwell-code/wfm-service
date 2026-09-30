# Architecture Research: Shift-Level Planning Dimension

**Domain:** Timefold Solver constraint-satisfaction — introducing a shift-envelope planning
dimension above an existing per-timeslot seat-assignment model
**Researched:** 2026-08-25
**Confidence:** HIGH (codebase grounding: every claim about `com.wfm.*` classes below was read
directly from the file at the cited line range) / HIGH (Timefold 1.16.0 API claims: verified
against `github.com/TimefoldAI/timefold-solver` at git tag `v1.16.0`, the exact source that
compiles into the pinned JARs) / MEDIUM (soundness reasoning for Option C, which rests on
documented API *contracts and intent*, not an explicit "this is forbidden" statement in the docs —
flagged inline)

## Verdict, up front

**Recommendation: Option A — two independent `@PlanningEntity` classes (`AgentAssignment`
unchanged, new `AgentShiftAssignment`), coupled by one new HARD constraint —
layered with a `SelectionFilter` performance optimisation once Option A's baseline is measured.**
Options B, C and D are unsound or actively counter-productive for this codebase's specific shape
(specialization varies mid-shift, so seat assignment cannot be a pure function of shift choice).
Ranking and full reasoning in [The central question](#the-central-question-ranked). This is not a
close call: Timefold's own `employee-scheduling` and `school-timetabling` quickstarts both resolve
"two decisions must agree" with plain hard constraints between entities, never with a value range
that reads another entity's genuine planning variable, and never with a shadow variable computing
an assignment that requires search to resolve.

**Coverage constraints verdict: CONFIRMED — 11 of 19 constraints need zero changes.** The bulk
allocation, minimum-staffing, contracted-hours and specialization constraints all key off
`AgentAssignment.agent != null` counts per timeslot, which is agnostic to *why* an agent holds a
seat. Only the four break constraints and (optionally) the two preference constraints need to
become mode-aware; one new hard constraint is added. Full breakdown in
[Coverage constraints](#1-coverage-constraints).

**Relationship to the two prior failed attempts:** this is not a third attempt at the same thing.
`BreakAwareConstructionPhase` and Phase 12's Atomic Shift Move both tried to make a *whole shift*
emerge from, or be atomically placed into, the **same 15-minute slot lattice** `AgentAssignment`
already searches — i.e. they tried to fix a search-space problem with a smarter *move*, while
leaving the search space itself (~36 correlated micro-decisions per agent-day) unchanged. This
milestone instead **shrinks the search space itself**: shift choice becomes one small-cardinality
categorical decision made once per agent-day, and the break-geometry discovery problem that broke
both prior attempts is removed from the seat-assignment critical path entirely. See
[Search space](#4-search-space) for why this is a structurally different bet, not a retry.

---

## System overview: current vs. proposed

```
CURRENT (single planning dimension)
┌──────────────────────────────────────────────────────────────────────┐
│ Schedule (@PlanningSolution)                                         │
│  ValueRangeProvider "agentRange" ← List<Agent> (problem fact)        │
│  PlanningEntityCollectionProperty ← List<AgentAssignment>             │
│                                                                        │
│  AgentAssignment (@PlanningEntity)  — one row per (Timeslot × Spec)   │
│    identity: timeslot, requiredSpecialization                        │
│    @PlanningVariable Agent agent   (nullable, ranged "agentRange")   │
│                                                                        │
│  "Shift" = emergent: the set of AgentAssignments sharing the same    │
│  agent+date, contiguous by Timeslot.startTime. No stored entity.     │
│  Break position is *discovered* by 4 hard constraints                │
│  (exactlyOneBreak / breakDuration / breakBlockedWindow /             │
│   breakStartAlignment) acting on that emergent set.                  │
└──────────────────────────────────────────────────────────────────────┘

PROPOSED (two planning dimensions, coupled by a hard constraint)
┌──────────────────────────────────────────────────────────────────────┐
│ Schedule (@PlanningSolution)                                         │
│  ValueRangeProvider "agentRange"       ← List<Agent>        (fact)   │
│  ValueRangeProvider "shiftTemplateRange" ← List<ShiftTemplate> (fact)│
│  PlanningEntityCollectionProperty ← List<AgentShiftAssignment> (NEW) │
│  PlanningEntityCollectionProperty ← List<AgentAssignment>  (unchanged)│
│                                                                        │
│  AgentShiftAssignment (@PlanningEntity, NEW) — one row per agent-day  │
│    identity: agent, date                                             │
│    @PlanningVariable ShiftTemplate shift (nullable, entity-level     │
│      value range FILTERED to templates matching that agent-day's     │
│      AgentDayConfig.effectiveHours — a problem fact, sound use of    │
│      Timefold's entity-level ValueRangeProvider)                     │
│                                                                        │
│  AgentAssignment (@PlanningEntity, UNCHANGED shape)                  │
│    @PlanningVariable Agent agent (nullable, ranged "agentRange")     │
│    NEW hard constraint joins to AgentShiftAssignment(agent,date):    │
│    seat's timeslot must fall inside the chosen shift's envelope      │
│    and outside its break window, else penalise hard.                │
│                                                                        │
│  Break position becomes a property of ShiftTemplate (structural),    │
│  not something CH has to discover by filling slots in the right      │
│  pattern. See §2.                                                    │
└──────────────────────────────────────────────────────────────────────┘
```

Both classes remain declared on `Schedule` (the one `@PlanningSolution`); there is still one
solver, one `SolverConfig`, one `ConstraintProvider` class — see [§3](#3-per-desk-dual-mode) for
why a single mode-aware provider is the right shape rather than two solutions or two configs.

---

## The central question, ranked

Recap of the coupling problem: shift choice (per agent-day) and seat occupancy (per timeslot ×
specialization) are two decisions that must agree, and nothing in the current model prevents the
solver placing an agent in a seat outside their shift envelope.

### Ranking

| Rank | Option | Verdict |
|------|--------|---------|
| **1** | **A — two entities + hard constraint** | **Recommended.** Sound, idiomatic, matches Timefold's own quickstart precedent for "two decisions must agree." |
| 2 | **E-variant — Option A + `SelectionFilter` on the `agent` value selector** | Recommended *addition* to Option A once A's baseline is measured, not a replacement. Pure performance, cannot corrupt correctness. |
| 3 | **D — single composite entity per agent-day** | Rejected. Granularity mismatch: forces either losing per-seat specialization variability or duplicating the shift decision up to 36×, which inflates rather than shrinks the search space this milestone exists to shrink. |
| 4 | **C — shift filters `AgentAssignment.agent`'s value range** | Rejected. Compiles, but structurally unsound for this coupling: no invalidation path when the depended-on planning variable changes, a chicken-and-egg problem in construction order, and it doesn't remove the need for the same hard constraint anyway. |
| 5 | **B — shift as the genuine variable, seat as `@ShadowVariable`** | Rejected outright. A `VariableListener` must be a pure, cheap function of its source variable(s). "Which agent fills this specific seat, among several agents whose shifts all cover this timeslot" is exactly the combinatorial matching problem `AgentAssignment` exists to solve — it cannot be computed by a listener without embedding a sub-solver in it, which is what Timefold's shadow-variable contract exists to prevent. |

### A. Two independent entities + hard constraint (RECOMMENDED)

**Shape:** New `@PlanningEntity` `AgentShiftAssignment` — identity `(agent, date)`, one
`@PlanningVariable ShiftTemplate shift` (nullable). `AgentAssignment` is **structurally
unchanged** — same identity `(timeslot, requiredSpecialization)`, same `Agent agent` planning
variable ranged over `agentRange`. One new HARD constraint, `shiftEnvelopeCompliance`, joins
`AgentAssignment` (filtered to `agent != null`) to `AgentShiftAssignment` on `(agent, date)` and
penalises when the seat's timeslot falls outside the chosen shift's span, or inside its break
window, or when no shift is chosen at all for that agent-day.

**Soundness at 1.16.0:** Fully sound, zero new API surface risk. Joining one `@PlanningEntity`
class's stream to another's is the same mechanism `ScheduleConstraintProvider` already uses
pervasively — e.g. `exactlyOneBreak` already joins a grouped `AgentAssignment` stream to
`AgentDayConfig`
[`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:188-231`]. `ConstraintFactory`
does not distinguish "joined class is a genuine planning entity" from "joined class is a problem
fact" — both are `forEach(X.class)` calls. This is also the exact resolution pattern Timefold's
own `school-timetabling` quickstart uses for its analogous "two decisions must agree" problem:
`Lesson` carries **two independent `@PlanningVariable` fields** (`timeslot`, `room`), and conflicts
are resolved by hard constraints comparing pairs of `Lesson`, never by a value range or shadow
variable
[VERIFIED: `github.com/TimefoldAI/timefold-quickstarts/blob/stable/use-cases/school-timetabling/src/main/java/org/acme/schooltimetabling/domain/Lesson.java`,
read directly, lines 1-40].

**Move/neighbourhood implications:** With two declared `@PlanningEntity` classes, Timefold's
default local-search auto-configuration (confirmed at 1.16.0 by Phase 12's research,
`DefaultLocalSearchPhaseFactory.determineDefaultMoveSelectorConfig`
[`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-RESEARCH.md:522-539`]) builds
`ChangeMove`+`SwapMove` **per entity class** and unions them — nothing to hand-wire for the basic
case. The real risk is thrash, and it is narrower than it first looks: ordinary local-search
repair (reassigning *which agent* fills *a specific seat*) never needs to touch
`AgentShiftAssignment.shift` at all — it only needs to pick among agents whose *already-chosen*
shift covers that timeslot, which is envelope-compatible by construction. The only move that can
trigger the "one step invalidates up to 36 seats" thrash is a **shift rechoice** for an agent who
already holds seats that day (`ChangeMove` on `AgentShiftAssignment.shift`). That move should be
rare in local search — the shift library is small (single-digit cardinality per desk) and the new
consistency constraint (§ usual shift) actively discourages drift — so this is a survivable tail
risk, not the dominant failure mode Phase 12 hit. It is directly mitigable, and the mitigation is
already-verified 1.16.0 API: give the shift `ChangeMoveSelector` a low `fixedProbabilityWeight`
inside the `unionMoveSelector`
[VERIFIED: `github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/MoveSelectorConfig.java`,
`fixedProbabilityWeight` field — the exact mechanism Phase 12's research already confirmed exists
at this version], so local search relies on construction to get shift choice mostly right and only
rarely gambles on rechoosing it.

**Incremental-score-calculation implications:** Cheap. The new constraint's per-move
recalculation is a single join, same complexity class as the eight joins already in
`ScheduleConstraintProvider`. No custom `VariableListener`, no shadow-variable bookkeeping, no new
risk to incremental-score correctness (the exact category of bug Timefold's 1.16.0 auto-undo model
was built to remove — `AbstractMove.doMoveOnGenuineVariables` +
`VariableChangeRecordingScoreDirector`
[`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-RESEARCH.md:348-378`], unaffected since
this option needs no custom `Move` at all).

**Construction-heuristic behaviour — the one real design decision this option requires:** CH must
decide `AgentShiftAssignment.shift` values **before** `AgentAssignment.agent` values for the same
agent-day, or CH will greedily place seats before shifts exist and generate cascades of hard
violations it then has to unwind (the same failure shape as the break-wall, one level up). Timefold
supports this via **two sequential `<constructionHeuristic>` phases**, each with a
`<queuedEntityPlacer><entitySelector><entityClass>` scoped to one entity class — this is a
documented Timefold pattern for exactly this situation (processing one planning-entity class fully
before starting the next). `ConstructionHeuristicPhaseConfig` supports `queuedEntityPlacer` /
`queuedValuePlacer` / `pooledEntityPlacer` as its placer element at 1.16.0
[VERIFIED: `github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/constructionheuristic/ConstructionHeuristicPhaseConfig.java:54-57`].
**Confidence on the exact nested XML shape is MEDIUM** — the class-existence and general two-phase
pattern are verified; the precise `entitySelector`/`entityClass` nesting under
`queuedEntityPlacer` should be spiked against a small fixture before being trusted in a real plan
(flag this as a first-task spike, not an assumption to build on blind).

**Thrashing risk verdict:** Low and well-scoped, for the reason above — thrash is possible only on
shift rechoice, which is rare-by-design and has a documented, verified mitigation
(`fixedProbabilityWeight`). This is a materially different risk profile from Phase 12's, where
*every* incremental seat-fill above the break threshold hit the wall, not just an occasional
rechoice.

### E-variant: Option A + `SelectionFilter` (recommended addition, not a replacement)

Timefold's `ValueSelectorConfig` supports a `filterClass` of type `SelectionFilter`
[VERIFIED: `github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/value/ValueSelectorConfig.java:18,34,60,141-146,245`],
the documented, sanctioned mechanism for narrowing which candidate values a move selector actually
tries, based on current solution state — **as a search-guidance optimisation, not a correctness
mechanism**. Layer a filter on the `agent` value selector for `AgentAssignment` that skips
candidates whose current shift (read from the same `AgentShiftAssignment` join as the hard
constraint) doesn't cover the seat's timeslot. If the filter is ever stale or wrong, it only costs
search efficiency — the hard constraint from Option A remains the sole correctness backstop, so
this cannot introduce a new class of correctness bug. Treat this as a follow-on efficiency tune
once Option A's own seeded benchmark (see [§6](#6-suggested-build-order)) establishes a baseline —
do not build it speculatively before A is proven, per Phase 12's own carried-forward lesson about
premature optimisation and unmeasured claims.

### D. Single composite entity per agent-day (REJECTED)

**Shape considered:** Either (a) one entity per agent-day carrying both a shift field and
seat-level allocation, or (b) `AgentAssignment` itself grows a second `@PlanningVariable
ShiftTemplate shift` field alongside `Agent agent` — directly mirroring `school-timetabling`'s
`Lesson` (which has `timeslot` and `room` as two variables on one entity).

**Why it fails here, specifically:** `school-timetabling`'s two variables are at the **same
granularity** — one `Lesson`, one `timeslot`, one `room`, no fan-out. Our two decisions are at
**different granularities**: one shift choice governs up to 36 `AgentAssignment` rows for the same
agent-day (because specialization can change mid-shift, per the milestone's own framing — this is
exactly why shift does not replace `AgentAssignment`). Two ways to force this onto one entity, both
bad:
- **(a) Entity per agent-day:** cannot hold the one-to-many, per-timeslot,
  varying-specialization relationship `AgentAssignment` exists to model — this loses exactly the
  capability the milestone explicitly wants to keep.
- **(b) Add `shift` directly to `AgentAssignment`:** every one of the ~36 rows for the same
  agent-day would need to carry the *same* `shift` value redundantly, with **no native Timefold
  mechanism forcing them to agree** (local search could set seat 1's shift to S1 and seat 2's to
  S2 for the same agent-day — an internally inconsistent state needing its own new hard constraint
  to forbid, which is strictly harder to reason about than, and redundant with, Option A's single
  clean per-agent-day decision). This *inflates* the search space (up to 36 independent shift
  choices that must coincidentally agree) instead of shrinking it — the opposite of what this
  milestone is for.

### C. Shift restricts `AgentAssignment.agent`'s value range (REJECTED)

**Question asked: can an entity-level value range legally depend on another planning variable's
current value at 1.16.0, or is that unsound?** Timefold does support entity-level
`@ValueRangeProvider` — "on the planning entity, where the value range differs per planning
entity" is a documented placement option, and mechanically nothing stops the annotated getter from
reading arbitrary state, including another entity's genuine planning variable. **It compiles. It
is unsound to rely on for correctness, for three independent reasons** (structurally reasoned from
the documented API contract and intent — this is the one claim in this document at MEDIUM rather
than HIGH confidence, since no page states "this is forbidden" in so many words):

1. **No invalidation path.** Timefold's dependency-tracking machinery (`sourceVariableName` on
   `@ShadowVariable`/`VariableListener`) exists specifically to tell the framework "recompute this
   whenever that changes." `ValueRangeProvider` methods have no equivalent — nothing tells cached
   move-selector state to reconsider `AgentAssignment.agent`'s legal-value set when a *different*
   entity's `AgentShiftAssignment.shift` changes elsewhere in the same step or a later one.
2. **Construction-order chicken-and-egg.** At the moment CH is about to assign a seat, if the
   corresponding `AgentShiftAssignment` for a candidate agent hasn't been decided yet
   (`shift == null`), "agents whose shift covers this timeslot" is ill-defined for every candidate
   — the exact same shift-before-seat ordering requirement Option A already needs, so Option C
   buys nothing there while adding risk elsewhere.
3. **Timefold never re-validates a variable's *current* value against a *later-narrowed* value
   range.** The value range is consulted only when *generating* a candidate move (deciding what
   new value to try) — never as a live invariant on the value already held. So if a shift changes
   after a seat was assigned under the old envelope, the seat's now-out-of-range agent silently
   stays put; Option C does not detect or prevent the very state it was meant to prevent. **A hard
   constraint is still required as the backstop — Option C does not remove the need for Option A's
   constraint, it only adds fragile, uncited-for-this-exact-shape machinery on top of it.**

**A legitimate, sound use of the *same* mechanism exists elsewhere in this design** — see
[§1](#1-coverage-constraints) and [§5](#5-data-model): filter `AgentShiftAssignment.shift`'s own
value range to templates whose net duration matches that agent-day's
`AgentDayConfig.effectiveHours`. That dependency is on a **problem fact** (`effectiveHours` is
resolved once per solve, before solving starts, and never changes during solving), the same shape
as Timefold's own textbook example (filtering rooms by a lesson's `studentCount`) — this is exactly
what entity-level `ValueRangeProvider` is for. The distinction that matters is *problem fact
dependency* (sound) vs. *cross-entity genuine-planning-variable dependency* (unsound) — Option C is
the latter.

### B. Shift as the genuine variable, seat as `@ShadowVariable` (REJECTED)

A `VariableListener` must be a pure, cheap, order-stable function of its declared source
variable(s) — this is what makes Timefold's shadow-variable machinery safe to use inside
incremental scoring and safe to rebase during move evaluation (the vehicle-routing pattern of
deriving arrival-time shadow variables from route order is the canonical *sound* use: arrival time
truly is `previousArrival + travelTime`, a pure function with no search embedded in it). "Which
agent fills this specific specialization seat, given several agents whose *chosen shifts* all
cover this timeslot and several unfilled seats of different specializations" is **not** a pure
function of the shift choice — it is exactly the combinatorial matching problem `AgentAssignment`
exists to solve today, including cross-agent competition for scarce seats. Computing it inside a
listener would mean embedding a sub-solver in a callback that must fire on every genuine-variable
change and be cheap and deterministic — that violates the shadow-variable contract on its face and
reintroduces the exact "hand-rolled multi-pass pipeline with no backtracking" failure mode
`BreakAwareConstructionPhase`'s own javadoc already documents as tried and abandoned in this
codebase [`src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java:9-24`]. Reject without
further evaluation of move/CH mechanics — it fails at the modelling-legitimacy stage.

---

## 1. Coverage constraints

**Milestone assumption: "the per-timeslot coverage constraints largely survive." CONFIRMED, with
one important integration point to design around.**

`ScheduleConstraintProvider`'s 19 constraints, evaluated one by one against the shift model:

| Constraint | Keys off | Change needed |
|---|---|---|
| `unassignedAssignment` | `AgentAssignment.agent != null` count per timeslot | **None.** |
| `agentDayOff` | `Agent` + date | **None.** |
| `specializationMatch` | `AgentAssignment.requiredSpecialization` vs. agent's specs | **None.** |
| `oneAssignmentPerTimeslot` | `(agentId, timeslotId)` grouping | **None.** |
| `exactlyOneBreak` | emergent gap detection over `AgentAssignment` group | **Mode-gated off** in shift mode (§2) — break becomes structural |
| `breakDuration` | same | **Mode-gated off** in shift mode |
| `breakBlockedWindow` | same | **Mode-gated off** in shift mode |
| `breakStartAlignment` | same | **Mode-gated off** in shift mode |
| `preferPrimarySpecialization` | agent's primary vs. seat's required spec | **None.** |
| `honourPreferredStartTime` | `AgentPreference.preferredStartTime` | **Recommend mode-gated off** in shift mode — superseded by envelope compliance + usual-shift consistency; leaving it on is harmless (still soft) but tunes against a now-redundant signal |
| `honourPreferredBreakTime` | `AgentPreference.preferredBreakTime` | Same as above |
| `breakClustering` | no-op stub | **None** (already inert) |
| `contractedHoursOver/Under/UnderZero` | `expectedWorkSlots(AgentDayConfig)` vs. assigned-slot count | **None to the constraint itself** — see integration point below |
| `bulkOverallocationLimit` | timeslot seat count vs. demand | **None.** |
| `bulkUnderallocationSoft` / `bulkUnderallocationHard` | timeslot seat count vs. demand | **None.** |
| `minimumStaffing` | timeslot seat count | **None.** |
| **`shiftEnvelopeCompliance`** | *(new)* | **Added** — the Option A hard constraint |

**11 of 19 need zero changes** because they all reduce to "how many `AgentAssignment` rows with
`agent != null` exist for this timeslot / this agent-date," which is defined identically whether
that agent got there via a slot-by-slot decision or via an envelope. This is the mechanical reason
the milestone's own assumption holds: the coverage math was already written against the *effect*
of assignment (a filled seat), never against *how* a seat came to be filled.

**The one integration point that must be designed, not assumed away:** `contractedHoursOver` /
`contractedHoursUnder` require the agent's assigned-slot count to equal
`expectedWorkSlots(AgentDayConfig)` exactly — and that arithmetic is completely independent of
which `ShiftTemplate` was chosen. If the shift library offers a template whose net working span
(shift duration minus break) doesn't match a given agent-day's `effectiveHours`, **two hard
constraints become structurally unsatisfiable together for that agent-day** —
`shiftEnvelopeCompliance` confines the agent to a fixed-length envelope while
`contractedHoursOver`/`Under` demands a different total, and no amount of solver time fixes that,
because no legal solution exists. **Design response:** filter `AgentShiftAssignment.shift`'s value
range to templates whose net duration matches (or falls within a configured tolerance of) that
agent-day's `AgentDayConfig.effectiveHours` — the sound, problem-fact-dependent use of entity-level
`ValueRangeProvider` flagged in [§C](#c-shift-restricts-agentassignmentagents-value-range-rejected)
above. This should be validated at shift-library data-entry time too (reject/warn on a template
whose net duration cannot match any desk agent's typical contracted hours), not solely relied upon
at solve time.

---

## 2. Break modelling

**Recommendation: break placement becomes an attribute of the shift template (structural) in shift
mode.** Each `ShiftTemplate` carries its own break offset and duration (e.g. "break starts 4h into
the shift, lasts 60 minutes") as plain fields, validated once when the template is created —
exactly the same `breakBlockedHours`/`breakStartAlignment` legality rules
`ScheduleConstraintProvider.isAligned`/geometry helpers already encode
[`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:703-716`], just checked at
CRUD time against a single template instead of discovered by search across up to 36 slot
decisions per agent-day, per agent, every solve.

**What happens to the four break constraints in shift mode:** they become **dead code for
shift-scheduled desks** and stay exactly as they are today for slot-scheduled desks (see
[§3](#3-per-desk-dual-mode) for the mode-gating mechanism). They are not deleted — slot-mode desks
still need them verbatim. In shift mode, at most **one** lightweight replacement constraint is
needed: an agent's assigned seats within the chosen envelope must contain a gap exactly where the
template's break offset/duration says (a cheap join against `AgentShiftAssignment.shift`'s break
fields, not a search-discovered geometry problem) — and this is arguably redundant with
`shiftEnvelopeCompliance` itself if that constraint already treats "break window" as illegal seat
territory (a filled seat during the template's break window is already caught by envelope
compliance; the only *additional* thing to check is that the agent isn't missing a break they
should have, which reduces to comparing assigned-slot count against expected work slots —
already `contractedHoursUnder`'s job). **This is the mechanism by which the shift model
structurally removes the exact wall both prior attempts hit** — see [§4](#4-search-space).

**Why this differs from, and does not repeat, the two prior break-modelling failures:**
- `BreakAwareConstructionPhase`'s 6-pass pipeline tried to *pre-compute* a legal break position
  procedurally, outside Timefold's search, with no backtracking — abandoned because "sequential
  passes with no backtracking lost quality at each step, and repair cascades caused
  underassignment" [`src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java:9-24`]. This
  design does not pre-compute anything procedurally — the break position is a **static property of
  a template row**, decided once by an operator, not discovered per-solve.
- Phase 12's Atomic Shift Move tried to make CH place a *whole legal shift+break window* in one
  custom move on top of the **same 15-minute lattice** — technically correct (proven under
  `FULL_ASSERT`, composed cleanly with change/swap) but measured as **inert at realistic 130%
  over-allocation** and only +0.25h against a 5.00h noise spread at the generous 400% reference
  scenario [`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md:56-102`], because
  it could only claim free-or-self-held seats, never displace another agent, and seat capacity —
  not move granularity — was the actual binding constraint. This design does not add a custom move
  at all; it removes the break-geometry *search problem* from the lattice altogether by deciding it
  once, structurally, before seat-filling starts. See [§4](#4-search-space) for why this also
  plausibly (not certainly) helps the cross-agent-displacement finding Phase 12 surfaced.

**A third, previously undocumented prior attempt directly relevant here — surfaced by this
research, not mentioned in the milestone brief:** between 2026-08-13 and 2026-08-20, four
raw-commit experiments (`9207ceb` "consistent daily start", `9f4a96f` "consistent break offset",
`7861b83` "preferred start time as anchor", `6fb78c7` "consistency report API") built a
**solver-chosen-anchor, spread-based** consistency mechanism — group each agent's daily starts,
penalise the spread between the earliest and latest by whole increments — and shipped it far
enough to add `V38__add_consistent_start_weight.sql`
[`src/main/resources/db/migration/V38__add_consistent_start_weight.sql`]. **All four were fully
reverted** (`3aba7c6`, `65ccb34`, `2da56fd`, plus two perf-commit revert/redo pairs), landing back
at `a83d426`. The revert commit for the lead constraint records the mechanical reason it could not
ship: `V38` stayed on dev database as an already-applied, now-orphaned Flyway migration (dropping
it would fail checksum validation), and the accompanying note records that nothing after `9207ceb`
reached dev because `BreakAwareConstructionTest` broke CI at the very next commit in the chain and
was never fixed before the whole sequence was rolled back
[`git show 6fb78c7`: *"this ships behind a red deploy gate — BreakAwareConstructionTest fails on
CI at 9f4a96f and 15354cb, so nothing since 9207ceb has reached dev"*]. Two things carry forward
from this for the current milestone: (a) **`consistent_start_weight` is a live, unused, inert
column on `constraint_weights` today** — do not reintroduce a differently-shaped consistency
constraint under a name collision with it, and do not assume the column is available for reuse
without checking Flyway history first; (b) the abandoned design's own javadoc names its structural
weakness plainly — *"the penalty is set entirely by the two extreme days... the search sees a
plateau, not a gradient"* [`git show 9207ceb`] — a **target-shift** consistency constraint (compare
each day's chosen shift against the agent's *stored* usual shift, penalise every deviating day) has
a gradient on **every** day, not just the two extremes, which is a real, citable reason to expect
the milestone's "usual shift" formulation to behave better in local search than the abandoned
spread-based one, independent of the shift-vs-slot architecture question. **Any change to the
break/consistency constraints in this milestone must go through the normal CI/deploy gate** — the
prior attempt's failure mode was procedural (a broken test blocking deploy, followed by a full
rollback rather than a fix-forward), not a flaw in the grouping pattern itself, and the pattern
(`groupBy(agent, date, min(...))` then `groupBy(agent, min, max)`) is proven to compile and pass
307-318 tests at the time it was live.

---

## 3. Per-desk dual mode

**One `SolverConfig`, one `Schedule` solution class, one `ConstraintProvider` class — mode-gated
internally, not two of anything.** Reasoning:

- **One solution class.** `Schedule` already carries per-desk solver configuration as scalar
  fields (`incrementMinutes`, break settings, allocation limits)
  [`src/main/java/com/wfm/model/Schedule.java:37-75`], exposed to constraints via
  `ScheduleConfig` (`@ProblemFactProperty`, line 255-264). A `SchedulingMode` enum field belongs
  here, or on `ScheduleConfig`, following exactly this precedent — one more scalar the constraint
  provider reads. Splitting into two `@PlanningSolution` classes would duplicate the entire
  `Schedule`/`AgentDayConfig`/`ScheduleConfig` machinery for no benefit, since a single desk is
  *only ever* one mode per solve (the milestone's own framing) — there is no scenario where a
  single `Schedule` instance needs both models simultaneously.
- **One `SolverConfig`.** `solverConfig.xml` is loaded once at startup
  (`src/main/resources/solverConfig.xml`, declares `entityClass` and `constraintProviderClass`
  statically). Both `AgentAssignment` and the new `AgentShiftAssignment` are declared as
  `<entityClass>` elements in the **same** `<solver>` block (Timefold supports multiple
  `entityClass` declarations under one solver — this project already declares a construction
  heuristic and local search phase that must apply uniformly across whichever entity classes are
  populated for a given solve). A slot-mode desk's `Schedule` simply has an empty
  `List<AgentShiftAssignment>`; nothing in the constraint provider fires for an empty collection.
- **One `ConstraintProvider` class**, gated by reading `ScheduleConfig`'s new mode field inside
  each of the affected constraints (the four break constraints, optionally the two preference
  constraints, and the one new `shiftEnvelopeCompliance` constraint) — precisely mirroring how
  `minimumStaffing`'s hard/soft behaviour is already **configuration-driven, not code-driven**,
  per its own javadoc: *"whether this constraint is hard or soft is a per-desk configuration row,
  not a code decision"* [`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:456-461`].
  Mode-gating a handful of constraints on a `ScheduleConfig` field is the same pattern at slightly
  larger scope, not a new one.

**What the mode switch actually keys off:** a new `Desk.schedulingMode` column (`SLOT` | `SHIFT`,
default `SLOT` for zero-impact on existing desks — see [§7](#7-migrationcompat)), read once when
`SolverService.buildSchedule` constructs the `Schedule`/`ScheduleConfig` for a solve, and carried
as a plain field through to the constraint provider exactly as `incrementMinutes` and the break
settings already are [`src/main/java/com/wfm/service/SolverService.java:432-476`].

**What breaks if a desk switches mode with an accepted schedule in flight:** nothing at the data
level — `Schedule`, `AgentAssignment` and (new) `AgentShiftAssignment` rows are all
solve-scoped and tenant/desk-scoped, and an already-`ACCEPTED` `Schedule` is a persisted snapshot,
not a live view of `Desk.schedulingMode`. The risk is entirely at the **UX/expectation** level: an
operator who accepted a shift-mode schedule, then switches the desk back to `SLOT` mode, will find
the roster/export UI reading a schedule that has `AgentShiftAssignment` rows the slot-mode UI has
no concept of, and vice versa. Two concrete guards worth building alongside the mode field itself
(scope for the phase that adds the switch, not the core coupling work): (a) block a mode switch
while any `Schedule` for that desk has `status = RUNNING`, mirroring existing concurrency
assumptions; (b) surface the schedule's *own* recorded mode (not the desk's *current* mode) on the
roster/export read paths, the same "resolve from the authoritative stored record, not a live
default" lesson v1.2 Phase 13 had to learn the hard way for `contractedHoursPerDay` (see
`PROJECT.md`'s Phase 13 narrative and the "Key Decisions" row about the scalar's non-authoritative
status).

---

## 4. Search space

**Quantified change, per agent-day:**

| | Today (slot model) | Proposed (shift model) |
|---|---|---|
| Decisions per agent-day | Up to 36 independent `AgentAssignment.agent` choices (9h ÷ 15-min increments), each `nullable`, each ranged over the full `agentRange` | 1 `AgentShiftAssignment.shift` choice (nullable, ranged over a filtered handful of desk `ShiftTemplate`s — single digits per desk) |
| Break-geometry search | Discovered by combinatorial interaction of up to 36 correlated slot decisions against 4 hard constraints that only become checkable once ≥`breakThresholdSlots` are filled | Zero — a property of the chosen template, validated once at data-entry time |
| Remaining per-seat decision | Same up-to-36 seat-agent choices, *entangled with* break-geometry discovery | Same up-to-36 seat-agent choices, but *disentangled* from break-geometry — each is a simple "pick a present, spec-matching agent" choice once the envelope is fixed |

This is the mechanism by which the milestone plausibly addresses "the solver fails to find
solutions on live desks": the break-wall failure documented in both
`BreakAwareConstructionPhase`'s javadoc and Phase 12's research
[`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-RESEARCH.md:9-21`] exists because every
incremental path from a partial shift to a complete, correctly-breaked one passes through an
intermediate state the HARD `exactlyOneBreak` constraint rejects — and that wall is specifically a
consequence of discovering break geometry through independent single-slot decisions. Removing that
discovery problem from the seat-fill critical path removes the wall's cause, not just its symptom
(which is what both prior attempts, in different ways, tried to patch around instead).

**Does this address Phase 12's cross-agent seat-displacement finding? Partially, and honestly, not
by adding new displacement capability — by removing what was masking whether displacement even
gets a chance to matter.** Phase 12 found that at realistic 130% over-allocation, hours assigned
were identical (36.0h) across all ten runs regardless of the atomic move, because total seat
capacity was already below what agents needed and the atomic move's design explicitly excluded
displacing another agent's held seat
[`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md:45-59`]. The shift model does
**not** change total seat capacity — that is still `demand FTEs × overallocationHardLimitPct`, the
same formula, same `Schedule` fields
[`src/main/java/com/wfm/model/Schedule.java:71-72`]. What it changes is that **ordinary**
`ChangeMove`/`SwapMove` on `AgentAssignment.agent` — which already has full displacement capability
today (a `ChangeMove` can reassign any seat away from its current holder; `SwapMove` explicitly
trades two agents' seats) — currently never gets the chance to exercise that capability on most
desks, because local search is busy fighting the break wall before it ever reaches a state where
seat-scarcity trade-offs would be the binding concern. Once the wall is removed, ordinary local
search is far more likely to actually reach, and then work productively within, whatever seat
headroom a desk has. **This is a genuine, real effect, but it is not the same claim as "the shift
model adds cross-agent displacement" — it did not need to add it, Timefold's default moves already
had it; the shift model's contribution is removing the obstruction that was preventing those moves
from ever getting exercised.** At *structurally* insufficient seat capacity (demand permanently
exceeds what `overallocationHardLimitPct` allows, independent of any move-space question), no
amount of search-space improvement closes that gap — that remains a capacity/config problem, not a
modelling one, and should not be oversold as solved by this milestone.

---

## 5. Data model

**Current schema head: `V38`, not `V36` as `PROJECT.md` states.** `V37__add_min_staffing_weight.sql`
and `V38__add_consistent_start_weight.sql` both exist on disk and are applied (per the revert
commit's own explanation of why `V38` could not be deleted). `PROJECT.md`'s "schema at V36 after
v1.2" is stale — flag this for the roadmapper so migration numbering in the next phase plan starts
from the correct head (`V39`), not a number that collides with an already-applied file.

**New JPA entities:**

| Entity | Shape | Relative to existing model |
|---|---|---|
| `ShiftTemplate` | `id, tenantId, deskId, name, startTime, endTime, breakOffsetMinutes, breakDurationMinutes, active` | Sibling of `Specialization` — a **per-desk library**, same relationship `Desk` already has to `Specialization` (desk-scoped configuration list). Lives alongside `Desk`, referenced by FK from `deskId`, not nested inside it. |
| `AgentShiftAssignment` | `@PlanningEntity` + `@Entity`/`@Table` (same dual-purpose pattern as `AgentAssignment` — persisted so an accepted schedule can be re-read/exported), identity `(agent, date)`, `@PlanningVariable ShiftTemplate shift` (nullable) | Structural sibling of `AgentAssignment`, at agent-day granularity instead of timeslot-seat granularity. |
| `AgentUsualShift` | `id, tenantId, agentId, dayOfWeek, shiftTemplateId`, unique `(agent_id, day_of_week)` | **New table, modelled directly on `AgentDayHours`'s shape** (`src/main/java/com/wfm/model/AgentDayHours.java:9-11`, same unique constraint pattern) — not an extension of `AgentPreference`. See below for why. |

**Where the shift library sits relative to `Desk`/`Specialization`/`agent_day_hours`:**
`ShiftTemplate` is desk-scoped configuration data, structurally parallel to `Specialization` — both
are lists an operator maintains per desk, both feed into what the solver is allowed to choose from.
It is **read alongside**, not nested inside, `Desk` (matching the existing `Desk` → `Specialization`
relationship, which is also a separate table with a `desk_id` FK, not a JSON blob on `Desk`).
`AgentDayHours` (per-agent, per-weekday contracted hours) stays exactly where it is and becomes an
**input** to the new value-range filtering in [§1](#1-coverage-constraints) — `AgentDayConfig`
already resolves `AgentDayHours` into `effectiveHours` per agent-day
[`src/main/java/com/wfm/model/AgentDayConfig.java:16`]; the new shift-choice filtering reads that
same resolved fact, no new resolution pipeline needed.

**Why `AgentUsualShift` is a new table, not an extension of `AgentPreference`:** `AgentPreference`
already carries `preferredStartTime`/`preferredBreakTime` per `(agent, dayOfWeek | date)` with an
`isStanding` flag [`src/main/java/com/wfm/model/AgentPreference.java:27-40`], and its resolution
logic in `SolverService.resolvePreferences`
[`src/main/java/com/wfm/service/SolverService.java:488-555`] is structurally the exact pattern a
"usual shift per weekday" resolution needs (standing-vs-weekly precedence, resolved once per agent-
day into an exact-date fact). **Reuse that resolution pattern**, but do not overload the table:
`preferredStartTime`/`preferredBreakTime` are free-form, agent-stated wishes, independent of any
desk configuration — a preference can be any `LocalTime`, whether or not it corresponds to a real
shift. `usualShift` is a **foreign key into the desk's own shift library** (must be a valid,
active `ShiftTemplate` for that desk) and represents an operator-set or historically-derived
*target* the solver is asked to converge toward, not a self-reported wish — a materially different
constraint on the data (referential integrity to `ShiftTemplate`, desk-scoped) that does not belong
bolted onto a table whose other columns have no such constraint. Building the resolution service as
a near-copy of `resolvePreferences` (same standing/weekly precedence shape, different source table)
is the correct amount of reuse without conflating two different kinds of fact.

**Migration sequence (starting from the correct head, `V39`):**

| Migration | Purpose |
|---|---|
| `V39` | `CREATE TABLE shift_template` (desk-scoped shift library) |
| `V40` | `CREATE TABLE agent_shift_assignment` (persisted planning entity, mirrors `agent_assignment`'s shape) |
| `V41` | `ALTER TABLE desk ADD COLUMN scheduling_mode VARCHAR(10) NOT NULL DEFAULT 'SLOT'` |
| `V42` | `CREATE TABLE agent_usual_shift` (per-agent, per-weekday target, unique `(agent_id, day_of_week)`) |
| `V43` | `ALTER TABLE constraint_weights ADD COLUMN shift_envelope_compliance_weight ...` (new hard constraint weight) and a usual-shift consistency weight + tolerance-band column, per the milestone's "operator-configurable tolerance band and weight per desk" |

This is a **suggested ordering for planning purposes**, not a locked phase-numbering — the exact
split across phases is [§6](#6-suggested-build-order)'s job, not the migration numbering's.

---

## 6. Suggested build order

Ordered by dependency; items marked **[independently shippable]** can land and be verified on
their own, ahead of or in parallel with the rest.

1. **Shift template library — data model + admin CRUD.** `ShiftTemplate` entity, `V39` migration,
   desk-scoped admin UI. **[Independently shippable]** — touches no solver code, no existing desk
   behaviour; operators can start building shift libraries before anything consumes them.
2. **Desk mode switch — field + validation only.** `Desk.scheduling_mode`, `V41`, defaulting every
   existing desk to `SLOT` (inert). Validation: a desk cannot switch to `SHIFT` mode without at
   least one active `ShiftTemplate`. **[Independently shippable]** — depends only on (1), and is
   safe/inert until a desk is explicitly switched (matches the "keeps a fallback if it
   underperforms live" requirement directly).
3. **Core coupling: `AgentShiftAssignment` + two-phase CH + `shiftEnvelopeCompliance` hard
   constraint (Option A).** This is the architectural core and carries the real risk identified in
   [§4](#4-search-space) and Phase 12's precedent — **must be proven in isolation, using the same
   seeded, step-count-terminated 5×5 A/B benchmark methodology Phase 12 established**
   (`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md`'s harness shape is the
   reusable template: fixed seeds, `withStepCountLimit`, never wall-clock, median + full min/max
   spread, never a mean), gated behind a system property and not part of the default suite, before
   anything downstream depends on it. This is the single most important carried-forward lesson from
   Phase 12 — a change of this shape must not be judged on a single unseeded run.
4. **Break-as-structural-attribute-of-shift-template + mode-gating the four break constraints.**
   Depends on (3) being proven — changes what "break" means in shift mode and needs its own
   correctness proof (the `exactlyOneBreak`/`breakDuration`/`breakBlockedWindow`/
   `breakStartAlignment` mode-gating from [§1](#1-coverage-constraints)/[§2](#2-break-modelling)).
5. **Value-range filtering of shift choice by `AgentDayConfig.effectiveHours`.** Should land
   alongside or immediately after (3)/(4) — without it, the structural-unsatisfiability risk named
   in [§1](#1-coverage-constraints) is live the moment a desk's shift library and contracted-hours
   data disagree.
6. **`AgentUsualShift` storage + resolution service (mirroring `resolvePreferences`) + Usual Shift
   upload/roster columns.** **[Independently shippable relative to the consistency constraint]** —
   pure data model and a resolution service copying an existing, proven pattern; can land once (1)
   exists (needs a valid `ShiftTemplate` FK target) without needing the solver-facing consistency
   constraint to exist yet.
7. **Usual-shift consistency soft constraint.** Depends on (3) (needs `AgentShiftAssignment` to
   compare against) and (6) (needs the stored target). Should reuse the `groupBy(agent, date, ...)`
   pattern already proven to compile in the reverted Stage 1/2 commits
   (`9207ceb`/`9f4a96f`) but as a **target-deviation** formulation, not spread-based — see the
   gradient argument in [§2](#2-break-modelling). **Must go through the normal CI/deploy gate
   end-to-end before merge** — the one procedural lesson from the reverted attempt.
8. **Drift report.** **[Independently shippable]** once (6) exists — pure read-side reporting
   comparing actual assigned envelope against stored usual shift; does not need (7)'s constraint to
   exist, only the stored target and the solved schedule.

**Dependency summary:** `1 → 2`; `1 → 3 → {4, 5}`; `1 → 6` (parallel to 3/4/5); `{3, 6} → 7`;
`6 → 8`. Steps 1, 2, 6 and 8 have no dependency on the risky core (3) landing successfully first
and can proceed in parallel with its benchmarking — only 4, 5 and 7 are gated on it.

---

## 7. Migration/compat

**What must hold for existing (slot-mode) desks to be unaffected, unconditionally:**

- `Desk.scheduling_mode` defaults to `SLOT` on the migration that adds it (`V41`) — every existing
  desk row is unaffected the moment the column lands, mirroring the exact precedent
  `Agent.working_days_known DEFAULT TRUE kept permanently` already set for "avoid retro-flagging
  pre-existing rows on migration" (`PROJECT.md`'s Key Decisions table).
- `AgentShiftAssignment` is only ever populated for `SHIFT`-mode desks. For a `SLOT`-mode desk's
  `Schedule`, `List<AgentShiftAssignment> = []` — the new hard constraint's `forEach` produces
  nothing to penalise, and the four break constraints run exactly as they do today, unmodified,
  for every desk that has never been switched.
- The mode-gating inside `ScheduleConstraintProvider` must be a read of `ScheduleConfig`'s new
  field, not a structural branch that could silently change behaviour for `SLOT` desks as a side
  effect of adding shift-mode logic elsewhere in the same class — i.e. the four break constraints'
  existing bodies should be **untouched**, with only a mode filter added at the top, so a
  regression in shift-mode logic cannot leak into slot-mode desks by construction.
- `BreakAwareConstructionTest` (the existing regression gate that caught the prior consistency
  attempt's CI break) must stay green and unmodified for slot-mode scenarios throughout this
  milestone — treat it the same way Phase 12 treated it: a required, unmodified regression check,
  not a fixture to be adapted to the new model.

---

## Anti-patterns to avoid

### Anti-Pattern 1: Resurrecting a procedural pre-assignment pass for shift OR break placement
**What people do:** Compute shift/break geometry in a service method before the solver runs, the
same shape as the removed `BreakAwareConstructionPhase` pipeline.
**Why it's wrong:** Already tried twice in this codebase in different forms (the 6-pass pipeline,
and implicitly Phase 12's move-generation geometry, which stayed *inside* the solver loop
deliberately for this reason) — no backtracking, cascading quality loss.
**Do this instead:** Let Timefold's own search/acceptor machinery place shift choices via a genuine
`@PlanningVariable`, exactly as Option A does.

### Anti-Pattern 2: A value range that reads another entity's genuine planning variable
**What people do:** Option C — filter `AgentAssignment.agent`'s legal values by the current
`AgentShiftAssignment.shift` value, to "help" the solver.
**Why it's wrong:** No invalidation path when the depended-on variable changes; a
construction-order chicken-and-egg problem; and it doesn't remove the need for the hard constraint
it was meant to replace. See [§C](#c-shift-restricts-agentassignmentagents-value-range-rejected).
**Do this instead:** A hard constraint (Option A) for correctness; optionally a `SelectionFilter`
(sound, documented, performance-only) as a later efficiency layer.

### Anti-Pattern 3: A shift-derived `@ShadowVariable` for seat assignment
**What people do:** Option B — make shift the sole genuine variable and derive seat occupancy via
a `VariableListener`.
**Why it's wrong:** Seat assignment is a genuine combinatorial matching problem (cross-agent
competition for scarce specialization seats), not a pure function of one entity's shift choice —
violates the shadow-variable contract on its face.
**Do this instead:** Keep `AgentAssignment.agent` a genuine variable; couple via a hard constraint.

### Anti-Pattern 4: Judging the core coupling constraint on a single unseeded run
**What people do:** Run the solver once before/after adding `shiftEnvelopeCompliance` and eyeball
the hard/soft score.
**Why it's wrong:** Phase 12's own evidence is definitive on this point — a 0.25h effect against a
5.00h run-to-run spread under wall-clock termination looked plausible in a single run and was
noise. `EnvironmentMode.REPRODUCIBLE` (the default, unchanged here) is only reproducible under
non-time-based termination.
**Do this instead:** The seeded, step-count-terminated 5×5 A/B harness pattern from
`AtomicShiftMoveBenchmarkTest`, exactly as named in [§6, step 3](#6-suggested-build-order).

---

## Sources

- Codebase (read directly, this session): `Schedule.java`, `AgentAssignment.java`, `Timeslot.java`,
  `AgentPreference.java`, `AgentDayHours.java`, `AgentDayConfig.java`, `Desk.java`, `Agent.java`,
  `ConstraintWeights.java`, `ScheduleConstraintProvider.java`, `BreakAwareConstructionPhase.java`,
  `solverConfig.xml`, `SolverService.java` (relevant ranges), Flyway migrations `V26`–`V38`,
  `.planning/PROJECT.md`, `.planning/milestones/v1.2-phases/12-atomic-shift-move/` (all files),
  `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`,
  `.planning/HANDOFF.json`
- Git history (read directly, this session): commits `9207ceb`, `9f4a96f`, `7861b83`, `6fb78c7`
  (the reverted consistency-plan Stages 0–3) and their revert commits `3aba7c6`, `65ccb34`,
  `2da56fd`, `ac395f2`, `12315ed`, `b6188c8`, `a83d426`; Phase 12 revert `299c42c`
- `github.com/TimefoldAI/timefold-solver` at git tag `v1.16.0` (read directly via GitHub API,
  this session, and via Phase 12's own prior research): `AbstractMove.java`, `Move.java`,
  `CompositeMove.java`, `ChangeMove.java`, `MoveListFactory.java`, `MoveIteratorFactory.java`,
  `DefaultLocalSearchPhaseFactory.java`, `UnionMoveSelectorConfig.java`,
  `ConstructionHeuristicPhaseConfig.java`, `ValueSelectorConfig.java`, `EnvironmentMode.java`
- `github.com/TimefoldAI/timefold-quickstarts` (`stable` branch, read directly via GitHub API,
  this session): `use-cases/employee-scheduling/.../domain/Shift.java`, `.../EmployeeSchedule.java`,
  `use-cases/school-timetabling/.../domain/Lesson.java`
- Timefold documentation (web search, this session, MEDIUM confidence — general design-pattern
  claims not tied to a specific version tag): `docs.timefold.ai` entity-level `@ValueRangeProvider`
  placement and `SelectionFilter` general description

---
*Architecture research for: shift-level planning dimension over an existing Timefold slot model*
*Researched: 2026-08-25*
