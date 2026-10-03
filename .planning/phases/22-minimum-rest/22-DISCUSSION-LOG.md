# Phase 22: Minimum Rest - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-03
**Phase:** 22-minimum-rest
**Areas discussed:** SLOT-mode scope, Waiver shape, Horizon edges, Waiver disclosure

---

## Todo cross-reference

Three `todo.match-phase` hits were presented, the same three Phases 19, 20 and 21 each reviewed and
declined. The highest-scoring (cross-agent seat displacement, **0.9** — the highest match this phase
drew) carries an instruction in its own file not to fold it into an in-flight phase.

| Option | Description | Selected |
|--------|-------------|----------|
| Fold none | Record all three under Reviewed Todos; raise the two real candidates for the next milestone's roadmap | ✓ |
| Let me pick which to fold | Choose individually, with the seat-displacement evidence requirements in view | |

**User's choice:** Fold none.
**Notes:** Phase 21 ruled that two of the three be raised "as candidate phases after Phase 22". Phase 22
is the milestone's last phase, so the natural home is the next roadmap rather than a later phase of this
one. Reasoning preserved in CONTEXT.md's Reviewed Todos section.

---

## SLOT-mode scope

### Does minimum rest enforce on SLOT-mode desks, or SHIFT mode only?

| Option | Description | Selected |
|--------|-------------|----------|
| SHIFT mode only | Constraint leads with the `AgentShiftAssignment` stream, which has zero rows on a SLOT desk — REST-04 satisfied by construction; matches `requireShiftEnvelopeSeatSupply`, also SHIFT-only | |
| Both modes | SLOT desks enforced too, which requires defining "a shift" in a mode that has no shift entity and no contiguity constraint | ✓ |
| SHIFT only, but refuse on SLOT | SHIFT-only enforcement plus a refusal so a SLOT desk can never store a value that does nothing | |

**User's choice:** Both modes.
**Notes:** Chosen with the cost named — that the SHIFT-only option would have made REST-04 free, and
that `Desk.schedulingMode` defaults to `SLOT`, so this is the majority of desks rather than an edge.

### In SLOT mode, what counts as "a shift" for measuring rest?

| Option | Description | Selected |
|--------|-------------|----------|
| Whole assigned span | One shift per (agent, business date): first assigned slot's start to last assigned slot's end, intra-day break gap ignored | ✓ |
| Maximal contiguous runs | Each unbroken run is its own shift, so a break splits the day in two | |
| Runs, with the break exempted | Contiguous runs, but a gap matching the expected break duration is not a shift boundary | |

**User's choice:** Whole assigned span.
**Notes:** Measured mid-discussion and decisive: `exactlyOneBreak` is gated `!= SHIFT`, so it is the
SLOT-mode break rule, and it requires exactly one gap of break duration — in SLOT mode the break **is**
a gap in assignment. A contiguous-run definition would therefore fire on every compliant SLOT agent-day
on every SLOT desk. The third option avoids that but couples the rest constraint to break geometry.

### One constraint covering both modes, or two mode-gated constraints?

| Option | Description | Selected |
|--------|-------------|----------|
| One, two sources `concat()`ed | Map both sources into a common per-agent-day span record and `concat()` them — the `breakClustering` precedent; one weight, one name, one predicate | |
| Two mode-gated constraints | A SHIFT constraint and a SLOT constraint, each gated like every other mode-specific constraint in the provider | ✓ |

**User's choice:** Two mode-gated constraints, "but it can be overridden by agent via an exclusion".
**Notes:** Reaffirmed after the divergence cost was named — two predicates that must stay in agreement
forever. That is why CONTEXT.md D-08 is written as load-bearing rather than optional. The second half of
the answer was ambiguous and was clarified in plain text: it means REST-06's per-date waiver, **not** a
standing per-agent exclusion. The standing-exclusion reading was offered explicitly, with its knock-on
effects on REST-03 and REST-07 spelled out, and was not chosen; it is recorded as a deferred idea.

### Where does the per-desk minimum-rest value live?

| Option | Description | Selected |
|--------|-------------|----------|
| Nullable column on `desk` | `minimum_rest_minutes` NULL, V55, `PUT /desks/{id}/minimum-rest` beside `/day-start`, sixth column on `DeskManagement.tsx`; weight still on `constraint_weights` | ✓ |
| On `constraint_weights` | Beside the new weight, following `consistencyToleranceMinutes` (17-CONTEXT D-04) | |
| Both: desk value, weights default | Desk override with a weights-level fallback | |

**User's choice:** Nullable column on `desk`.
**Notes:** Two measured arguments against the `constraint_weights` option: `ConstraintWeightsService`
materialises a defaults row when a desk has none, so absence is never an unset signal there and "unset"
would have to be encoded as `0`; and `consistencyToleranceMinutes` itself defaults to a real `60`, not
an off-sentinel. ROADMAP success criterion 1 also names the desk configuration UI specifically. The
third option conflicts with `FEATURES.md:150`'s explicit no-baked-in-default instruction.

---

## Waiver shape

### A waiver recorded for one agent on business date D — which gap does it waive?

| Option | Description | Selected |
|--------|-------------|----------|
| The rest coming into D | The gap from D-1's shift end to D's shift start — "this agent may start early on D" | ✓ |
| The rest after D's shift | D's shift end to D+1's shift start — "may work a short turnaround after D" | |
| Either end touching D | A waiver on D legalises any violating pair involving D | |

**User's choice:** The rest coming into D.
**Notes:** The deciding argument was the horizon edge — a first-day violation pairs a pre-horizon day 0
against day 1, and only this reading lets the operator waive a date that exists in the schedule. The
third option would have let one waiver clear two distinct pairs and would have left REST-07 unable to
say which waiver cleared which violation.

### How does the rest waiver ride the per-agent exception mechanism?

| Option | Description | Selected |
|--------|-------------|----------|
| New `agent_rest_waiver` table | Keyed (tenant, desk, agent, date) + reason, V55, its own `@ProblemFactCollectionProperty`; purely additive | ✓ |
| Nullable hours + `rest_waived` flag | One `agent_exception` row carries both kinds of exception; honours REST-06's wording most literally | |
| Required hours + `rest_waived` flag | Keep `contracted_hours_override` NOT NULL, UI pre-fills the agent's standard hours | |

**User's choice:** New `agent_rest_waiver` table.
**Notes:** The pull toward reuse was real and measured — `AgentException` is *already* a problem fact
(`Schedule.java:143`), so the `(agentId, date)` join would have been free. It lost on a stated invariant:
`resolveEffectiveHours`'s javadoc says "a present `AgentException` row is always a real value
(`nullable=false`)", and three sites build the lookup map unconditionally (`SolverService:302`, `:1159`,
and the resolver), so a null-hours row would make an agent's effective hours null and flow into the
`> 0` gate that decides whether a shift row exists. `SolverService:1302`'s existing
exception-coincides-with-day-off refusal would also have been inherited on a harmless date. The third
option avoids all that but would freeze a contracted-hours override as a side effect of a rest waiver.

### What happens to a waiver that waives nothing?

| Option | Description | Selected |
|--------|-------------|----------|
| Inert, reported as unused | Never matches; the REST-07 surface carries applied and unused sections | ✓ |
| Silently inert | No match, no mention — consistent with `agent_preference` when it cannot be honoured | |
| Refused at save time where provable | Reject on a day off or outside any schedule period | |

**User's choice:** Inert, reported as unused.
**Notes:** REST-07 exists so a waiver cannot silently hide a roster problem; the mirror image is a waiver
pile nobody prunes. The third option only covers the cases knowable without solving, so the report is
needed regardless.

### How do the three consumers of "is this pair waived" stay in agreement?

| Option | Description | Selected |
|--------|-------------|----------|
| Shared predicate + structural guard | One predicate, three call sites, plus a guard test that fails the build on a second implementation | ✓ |
| Shared predicate only | The `ShiftLibraryValidationService.covers` pattern, no guard | |
| Separate, with an equality test | Each written idiomatically for its stream, one test asserting all three agree | |

**User's choice:** Shared predicate + structural guard.
**Notes:** Three consumers rather than two is a direct consequence of splitting the constraint in two —
the SHIFT constraint, the SLOT constraint and REST-03's refusal. The guard follows the milestone's
existing idiom (`bday-join-guard.md`, `midnight-time-arithmetic.md`, the single `resolveAnchor` helper).

---

## Horizon edges

### Horizon start: lookback at the real pre-horizon shift, or a documented blind spot?

| Option | Description | Selected |
|--------|-------------|----------|
| Lookback, ACCEPTED only | One date-filtered batched query per mode against the immediately preceding business date; no accepted predecessor means explicitly unconstrained, test-pinned | ✓ |
| Documented blind spot | Day 1's incoming rest never checked, pinned by a test — literally all REST-05 demands | |
| Lookback plus a gap advisory | As the first option, plus a non-blocking advisory when the predecessor is unaccepted | |

**User's choice:** Lookback, ACCEPTED only.
**Notes:** Buildable because `ScheduleService:398-410` denormalises `shift_start_time`/`shift_end_time`
onto accepted rows deliberately, and `AcceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus`
already answers the accepted-date question. What it needs built: neither `AgentAssignmentRepository` nor
`AgentShiftAssignmentRepository` has a date-filtered read — both only fetch a whole schedule. In SLOT
mode, which the earlier decision brought in scope, `agent_assignment` is one row per assigned seat per
slot, so a date filter is a precondition rather than an optimisation.

### The horizon's last day — its outgoing rest has no successor inside the period.

| Option | Description | Selected |
|--------|-------------|----------|
| Unconstrained, test-pinned | Nothing to check, asserted as deliberate; the next period's lookback enforces the pair | ✓ |
| Forward lookahead too | Check against an already-accepted later period, symmetric to the lookback | |
| Unconstrained plus an advisory | Unconstrained, with one warnings-channel line making the asymmetry visible | |

**User's choice:** Unconstrained, test-pinned.
**Notes:** The lookback chosen above is what makes the end edge safe rather than merely empty. The
lookahead option would have constrained a re-solve against a schedule the operator may be replacing.

### Does REST-03's pre-solve refusal reason about the pre-horizon predecessor?

| Option | Description | Selected |
|--------|-------------|----------|
| Include the edge | Refuse up front where day 0's accepted shift makes every day-1 option illegal, naming the agent and both shifts | ✓ |
| In-horizon pairs only | Refusal independent of prior-period accept state; the edge enforced only by the hard constraint | |
| Edge as advisory only | In-horizon pairs refuse; the edge produces a non-blocking advisory | |

**User's choice:** Include the edge.
**Notes:** Consequence accepted deliberately — **accepting one period can newly refuse the next period's
solve.** The argument for paying it is `requireShiftEnvelopeSeatSupply`'s own comment that refusing "is
what converts that silent, near-feasible wrong answer into an up-front, actionable refusal". Also noted
at the time: "structurally unavoidable" here is a harder claim than the ENVL-07 precedent's — it is a
product over two days' value ranges, not a per-date existence check.

---

## Waiver disclosure

### REST-07: how does a waived rest violation become visible in the output?

| Option | Description | Selected |
|--------|-------------|----------|
| Structured list + summary count | `appliedRestWaivers`/`unusedRestWaivers` computed from the solution, independent of `explain()`; counts on `/summary` | ✓ |
| Structured list plus Roster marker | As above, plus an Excel Roster cell marker and legend row, per 21-CONTEXT D-12 | |
| Zero-penalty marker constraint | A 0hard/0soft constraint matching only waived pairs, riding the `explain()` channel | |

**User's choice:** Structured list + summary count.
**Notes:** `explain()` cannot carry this — it is live-path only (`ScheduleOutputService:817+` builds the
accepted/DB-path report from persisted rows and never calls it), and a waived pair that is genuinely
legal produces no match on either path. The marker-constraint option would have repeated the gap
20-CONTEXT D-14 hit. The export was **not** decided and is recorded under Claude's Discretion.

### Does a solved `Schedule` snapshot the minimum-rest value it was solved against?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, snapshot on `schedule` | `minimum_rest_minutes` on `schedule`, V55, as `ScheduleConfig`'s 15th component — the V54 `dayStart` precedent | ✓ |
| No, read the desk's current value | Transport the live desk value, like `consistencyToleranceMinutes` | |
| Snapshot, and refuse edits while accepted | Snapshot plus a `setDayStart`-style unconditional refusal | |

**User's choice:** Yes, snapshot on `schedule`.
**Notes:** 21-CONTEXT D-15's argument for `dayStart` applies unchanged — the value is what the score was
computed against. Without it, changing the desk setting silently rewrites history for every accepted
schedule. The third option was declined because `setDayStart`'s refusal has no bypass only because
MIGR-04 owns the documented reversal, and there is no equivalent owner for rest, which an operator may
legitimately change mid-quarter.

### Does the schedule UI surface the waiver list this phase?

| Option | Description | Selected |
|--------|-------------|----------|
| Summary count + detail section | Count on the summary the page already polls, plus an applied/unused section on `ScheduleResults.tsx` | ✓ |
| Summary count only | Count on the summary; the full list via API and export | |
| API and export only | No frontend change at all this phase | |

**User's choice:** Summary count + detail section.
**Notes:** An API-only answer hides the disclosure from exactly the person REST-07 exists for. Inherited
constraint recorded at the time: the frontend still has no test runner, `tsc -b` is the only gate, and
Phase 21's `frontend/src/utils/dayWindow.ts` branded `DayOffset` (confirmed present on HEAD) means any
time comparison in the new section must route through it or it will not compile.

---

## Claude's Discretion

The operator did not defer any whole area to Claude. These were explicitly left open during the
discussion and are recorded in CONTEXT.md:

- The exact upper bound on `minimum_rest_minutes` and the wording of its refusal.
- Whether the lookback resolves the predecessor day's anchor from that schedule's own snapshotted
  `dayStart` or from the desk's current value (recommendation recorded: the predecessor's own snapshot).
- What happens to a stored rest value when a desk switches scheduling mode.
- Whether `unusedRestWaivers` is scoped to the solved period or lists every waiver on the desk.
- Whether the Excel export also carries the waiver, per 21-CONTEXT D-12's Roster convention.
- The hard weight magnitude for the rest constraint(s) and its column name.
- Naming of the shared predicate, its home, and the guard's registry file.
- Whether the waiver entry UI extends `AgentExceptions.tsx` or gets its own page and route.
- Task ordering and plan decomposition, including how the three V55 schema changes are sequenced.

Raised at the area-closing prompts and not taken, so still open for the planner or a later discussion:
whether a SHIFT desk with no templates behaves differently; what happens on a mode switch with a rest
value set; whether a waiver is deletable once a schedule referencing it is accepted; whether the reason
field gets any structure beyond free text.

## Deferred Ideas

Raised during this discussion:

- **A standing per-agent exclusion from minimum rest** — offered explicitly when the operator's
  "overridden by agent via an exclusion" was clarified, and not chosen. No REST id; it would change
  REST-03 (skip excluded agents entirely) and REST-07 (an excluded agent's short rest would never
  register). Its own requirement and its own phase.
- **A symmetric forward lookahead at the horizon's last day**, into an already-accepted later period.
- **Refusing a desk's rest value change while an ACCEPTED schedule exists.**

Carried forward from earlier phases and restated in CONTEXT.md: the same-day operating-window
containment refusal, a frontend test harness, the `date` → `businessDate` rename across six problem
facts, the live-desk drift check, deleting the frozen `DayWindow` oracle, and blocked-break-hours having
no SHIFT-mode enforcement point. Premium-pay / consent-to-override machinery stays ruled out at
milestone scoping.
