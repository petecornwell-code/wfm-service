# Pitfalls Research: Adding Shift-Level Scheduling to a Live Slot-Based Solver

**Domain:** Adding a second, coupled planning dimension (shift envelope) to an already-live
Timefold 1.16.0 constraint solver whose sole `@PlanningEntity` is a per-timeslot seat assignment
(`AgentAssignment`), on a single-environment deployment with no staging and no review gate.
**Researched:** 2026-08-25
**Confidence:** HIGH — every pitfall below is grounded in this codebase's own source
(`ScheduleConstraintProvider.java`, `BreakAwareConstructionPhase.java`, `SolverService.java`,
`solverConfig.xml`) and this project's own recorded milestone history (`v1.2-MILESTONE-AUDIT.md`,
Phase 12's withdrawal, `12-RESEARCH.md`'s verified Timefold 1.16.0 API notes). Nothing here is
generic scheduling-domain advice; every item names the file, constraint, or audit finding it comes
from.

---

## Critical Pitfalls

### Pitfall 1: Two coupled planning variables that can each be locally legal and jointly infeasible

**What goes wrong:**
Today `AgentAssignment.agent` is the *only* `@PlanningVariable` in the model
(`AgentAssignment.java:37`, `valueRangeProviderRefs = "agentRange", nullable = true`) and every
constraint in `ScheduleConstraintProvider` reasons about one variable's value. Adding a shift
selection (which shift, for which agent-day) as a second planning variable — even if it lives on a
different entity or as a second variable on the same entity — creates a search space where the
solver can place `AgentAssignment.agent = X` on seat `S` while `ShiftAssignment.shift = null` (or a
shift that doesn't cover `S`) for that same agent-day. Each individual move (assign a seat, assign
a shift) is legal in isolation — Timefold validates a move's *doability*, not cross-variable
semantic agreement — so the solver can walk into and sit in states where the two variables
disagree, and nothing in the move machinery prevents it. If the disagreement is only punished by a
soft or even hard *constraint* (evaluated after the move, not before), the solver spends real search
budget visiting, scoring, and only then rejecting states a well-designed value range would never
have offered it in the first place. This is a slower and noisier version of exactly what Phase 12
found in a single-variable model: local search variance already exceeded the effect of interest
(5.00h spread vs. 0.25h effect) with one variable; a second, loosely-coupled variable inflates that
noise floor further before any construction heuristic gets a foothold.

**Why it happens:**
It is tempting to bolt shift selection on as an independent `@PlanningVariable` because it *is*
conceptually independent (an availability envelope), but Timefold does not know that a shift
"contains" a set of legal seats unless that containment is expressed structurally — as a filtered
value range, a shadow variable, or a hard constraint joining the two. Treating the coupling as "just
another constraint" is the same category of decision that made `BreakAwareConstructionPhase`'s
6-pass pipeline fail: sequential, uncoordinated passes over related state lose quality and cannot
backtrack (`BreakAwareConstructionPhase.java:17-20`, "sequential passes with no backtracking lost
quality at each step, and repair cascades caused underassignment"). A constraint-only coupling is
the same anti-pattern in reverse: instead of sequencing assignment passes, it sequences *scoring* —
the solver discovers the incompatibility only after committing to both moves.

**How to avoid:**
Before writing any constraint, settle the coupling mechanism as an explicit design decision, not an
emergent property of adding two `@PlanningVariable`s and a penalty:
- **Filtered value range (preferred first choice):** make the seat's legal agent range for a given
  timeslot depend on which shift that agent is assigned that day — i.e. the shift variable
  constrains what `agentRange` can even offer for seats outside its envelope. This is the
  Timefold-idiomatic way to make an illegal combination *unreachable* rather than *penalized*, and
  it directly avoids the wasted-search-budget problem above.
  Timefold 1.16.0 also supports genuine-variable-to-genuine-variable dependencies. If the shift
  variable's value range must react to the seat assignments (rather than the other way around),
  investigate whether that dependency can be inverted (shift chosen first, seats filtered by it) —
  which is very likely the correct direction here anyway, since the milestone's own framing is
  "shift ... does not replace `AgentAssignment` — it constrains it" (`PROJECT.md`).
- **Shadow variable, if the shift is fully derivable from the seat assignments:** if you can compute
  "the shift" as a function of the seat pattern (contiguous span + break) rather than choosing it
  independently, a shadow variable removes the second genuine variable and the whole thrashing risk
  disappears — at the cost of losing the ability to pick from a *fixed library* (the milestone's own
  stated design), so this is likely not viable here, but rule it out explicitly rather than by
  default.
- **Hard constraint as a last resort, never as the primary mechanism:** if neither of the above is
  structurally achievable in the 1.16.0 API, a hard "seat implies shift coverage" constraint is
  acceptable *only* alongside evidence (from the diagnostic in "Warning signs" below) that it does
  not stall the construction heuristic the way `contractedHoursOver`/`Under` combined with
  `exactlyOneBreak` already required CH-friendliness workarounds for
  (`ScheduleConstraintProvider.java:182-186`, the documented comment on why `exactlyOneBreak` only
  fires once an agent has "enough slots to need one").

**Warning signs — detect before UAT, not at UAT:**
- Run `SolverService.runPreSolveScoreDiagnostic`-style logic (already exists at
  `SolverService.java:1109-1171`, "detects broken incremental scoring that would cause the CH to
  pick `{null -> null}` for every step") extended to the shift variable: assign one shift *and* the
  seats it should imply, re-score, and assert the hard-score delta is negative (improving), not
  flat or positive. A flat/positive delta on the very first assignment is the same failure mode this
  diagnostic already catches for the single-variable model — it will catch a badly-coupled shift
  model identically.
- Write a tiny (5–10 agent) integration test that runs the *actual* `solverConfig.xml` construction
  heuristic to completion and asserts every produced solution is feasible (hard score = 0) before
  any local search runs. If the CH cannot reach feasibility on a trivial fixture where feasibility is
  known to exist, the coupling is over-constrained or the value-range filtering is wrong — this is
  the earliest, cheapest possible detection point, weeks before a benchmark or UAT would surface it.
- Watch for a specific regression class in code review: any custom move or value-range provider that
  reads `AgentAssignment.agent` while computing the shift's legal range, or vice versa, in a way that
  is evaluated *after* both variables have independently changed in the same solver step (as opposed
  to before/during). That ordering dependency is exactly the kind of bug `AbstractMove
  .doMoveOnGenuineVariables` with framework-generated undo (`12-RESEARCH.md`, confirmed against this
  project's pinned 1.16.0) is supposed to make hard to get wrong for a *single* move — it becomes
  possible again once two independently-selected variables both feed each other's legality.

**Phase to address:** the phase that introduces the shift planning variable and its value-range/
coupling mechanism (the milestone's "shift as an availability envelope" feature). This decision must
be made and tested before any consistency-constraint or dual-mode work begins — everything else in
this milestone depends on the coupling being sound.

---

### Pitfall 2: A shift library that is stricter than actual demand makes a previously-solvable desk infeasible

**What goes wrong:**
The slot model today can tile any staffing curve one 15/30/60-minute increment at a time, subject
only to contracted hours and break placement. A shift library is a *discretization* of that
continuous freedom into a small enumerated set (e.g. `08:00–17:00, 8h + 1h break`). If the library
does not contain a shift whose envelope actually covers the desk's demand curve — a demand spike
that starts before the earliest library shift, or a coverage gap no combination of library shifts
can fill without over-staffing every other hour — the desk goes from solvable (slot model, any
tiling) to infeasible (shift model, no legal combination) the moment an operator switches it into
shift-scheduled mode. This is a strictly worse failure than a bad schedule: `minimumStaffing`
(`ScheduleConstraintProvider.java:491-498`) already documents exactly this class of problem for
seats — "a hard minimum on a day with too few available agents makes the whole solve infeasible,
returning no schedule rather than an imperfect one" — and that constraint was deliberately made
soft-by-default (`ofSoft(1000)`) specifically to avoid it. A shift library is a much larger
infeasibility surface than a single minimum-staffing floor: it constrains *every* agent-day
simultaneously, not one timeslot.

**Why it happens:**
Shift libraries are usually designed from what agents' contracts and preferences look like (round,
memorable start times), not from the desk's actual per-15-minute demand curve, which is uploaded
independently via the FTE spreadsheet (`FteUploadService`) and can have arbitrary shape — spikes,
troughs, asymmetric days. Nothing currently connects "the shifts an operator has defined for this
desk" to "the demand curve this desk's FTE upload describes." The two are edited in entirely
separate UI surfaces by (plausibly) different people at different times, so the mismatch is
structurally invisible until solve time — or worse, until an operator has already accepted a
degraded schedule that quietly under-covers real hours, because a soft-only shift-fit constraint
absorbed the mismatch instead of refusing to solve.

**How to avoid:**
- Build a demand-coverage validator that runs whenever an operator (a) edits a desk's shift library
  or (b) switches a desk into shift-scheduled mode, and (c) as an addition to
  `runPreSolveValidation`'s existing 12 checks (`SolverService.java:657-899`) — this project already
  has the pattern of pre-solve validation throwing a `PreSolveValidationException` with structured
  `ErrorDetail`s rather than silently degrading (`SolverService.java:895-898`); extend it, don't
  invent a parallel mechanism.
- The validator should answer a concrete question per timeslot in the desk's operating window: is
  there *some* combination of library shifts (respecting each shift's break) whose union of covered
  timeslots can, in principle, reach the minimum staffing / under-allocation floor at that timeslot?
  This is a coverage/interval-union computation independent of the solver — it does not need to run
  the actual solver, just check that the library's envelope union is not structurally short against
  the demand curve's non-zero hours. Cheap, fast, and can run synchronously on save.
- When the check fails, name the specific gap — which timeslot(s), which weekday — the same way
  `runPreSolveValidation` already names the specific agent/date/field for every one of its 12 checks
  (`ErrorDetail` triples), not a generic "infeasible" message. This is what stops the failure mode
  the milestone question worries about: "silently returning a bad schedule." A soft-only shift-fit
  constraint that merely penalizes the gap is not a substitute for this check — it converts a hard
  design defect (the library cannot cover demand) into a quietly bad schedule instead of a loud,
  actionable error, which is precisely what `minimumStaffing`'s own documentation warns a hard
  default would do in the opposite direction.
- Do not let the shift-fit check double as the operator's *only* signal — a desk that passes the
  structural coverage check can still be infeasible for solver reasons unrelated to the library
  (agent day-offs, specialization scarcity — see the existing check #12, "every specialization
  referenced by a staffing requirement must have an eligible desk-agent"). The library check
  narrows the space of *possible* failures before solve; it does not replace `runPreSolveValidation`.

**Warning signs:**
- A desk that solved cleanly under the slot model returns a hard-infeasible or near-zero-hard-score
  result immediately after being switched to shift-scheduled mode, with no change to its agents or
  demand upload — the library is the only thing that changed, so it is the first place to look.
- The pre-solve score diagnostic (`runPreSolveScoreDiagnostic`) logs a hard-delta ≤ 0 on the very
  first shift assignment for a desk with a narrow library — the same signature already used to
  detect broken incremental scoring, equally valid for detecting an over-constrained library.
- An operator support request describing a schedule where whole hours show zero coverage that used
  to be staffed — the qualitative echo of `minimumStaffing`'s own motivating bug report (a demand
  file with leading zeros left a desk "legitimately unstaffed for those hours" under the old model;
  a library gap produces the identical symptom under the new one, but caused by shift breadth rather
  than demand-file zeros).

**Phase to address:** the phase that introduces the desk shift library (before the per-desk mode
switch is exposed to operators) — feasibility validation must exist before any operator can select
shift-scheduled mode on a live desk, not be discovered by them afterward.

---

### Pitfall 3: Benchmarking the shift model the way Phase 12 did, and reaching the same untrustworthy conclusion

**What goes wrong:**
Phase 12's `AtomicShiftMoveBenchmarkTest` did almost everything right — 5 seeds, step-count
termination (`TerminationConfig.withStepCountLimit(2000)`, never wall-clock), median + full
min/max spread reported instead of a mean — and still nearly shipped a false positive: the
with-move median beat baseline by +0.25h, and only reporting the *spread* (5.00h) rather than just
the median made it obvious that 0.25h was noise, not signal
(`12-BENCHMARK.md:56-66`). A shift-model benchmark that reports only "median hours assigned went up"
without also reporting the baseline's own run-to-run spread repeats the exact mistake this project
already caught once. Worse, the shift model has *more* surface for reporting the wrong number than
Phase 12's single move did: hours assigned, hard score, consistency-constraint soft score, and drift
(distance from usual shift) are all plausible headline metrics, and it is easy to cherry-pick
whichever one moved favorably in one run.

**Why it happens:**
A single wall-clock solve looks like evidence because it produces a concrete before/after number,
and under deadline pressure "the schedule got better" is an appealing story to tell without running
it five more times to see if it holds. Phase 12's own root-cause finding for why this fails is
already on record: `EnvironmentMode.REPRODUCIBLE` (Timefold's default) only guarantees repeatable
results across runs "except if they use a time based termination and they have a sufficiently large
difference in allocated CPU time" (`12-RESEARCH.md:594-596`, verified against the actual javadoc)
— which is exactly what `SolverService.java` uses in production
(`TerminationConfig().withSpentLimit(Duration)`). Wall-clock solves on a shared or variably-loaded
machine are not comparable to each other, full stop, regardless of how careful the surrounding
methodology is.

**How to avoid — the concrete benchmark design this milestone must reuse, not reinvent:**
- **Seeded, step-count-terminated runs, never wall-clock**, using `SolverConfig.withRandomSeed`
  and `TerminationConfig.withStepCountLimit`, exactly as `AtomicShiftMoveBenchmarkTest` did.
  Wall-clock numbers (like the ROADMAP's own discarded `-4,930`/`-29,810`/`-29,810` three-run
  history, `12-BENCHMARK.md:149-171`) are explicitly *not* directly comparable to seeded runs and
  should not be used as a pass/fail baseline even for context, except with the same "not
  like-for-like" caveat Phase 12's report attached to its own historical figure.
- **Report median AND the full min/max spread of the baseline**, not just the with-shift-model
  number. The must-pass bar should be phrased the way `12-VALIDATION.md`'s threshold 1 was: *the
  new model's median must exceed the old model's median by more than the old model's own min/max
  spread.* An effect inside the noise floor is not an effect.
- **Sample size ≥ 5 per configuration**, matching Phase 12's 5×5 — fewer than 5 seeds makes median
  and spread both unstable; there is no evidence this project needs more, but there is direct
  evidence 5 was sufficient to catch a false positive once.
- **Set the must-pass threshold BEFORE running, in a VALIDATION.md-equivalent document, and record
  it verbatim in the benchmark report afterward** — exactly as `12-VALIDATION.md` stated "Pass /
  Fail Thresholds" ahead of `12-BENCHMARK.md`'s run, so the after-the-fact report could be checked
  against a pre-committed bar rather than a bar chosen to fit the result. Do not adjust the
  threshold, the seed list, or the step budget after seeing the numbers — `12-BENCHMARK.md:10-12`
  explicitly states "No fixture, seed, or step-budget value was adjusted after seeing the result,"
  and that discipline is what makes its own FAIL verdict credible rather than suspicious.
- **Benchmark at realistic over-allocation, not just a generous reference scenario.** Phase 12's
  400% reference scenario showed a (noise-sized) effect while its own 130% conservative variant —
  "the `Schedule` class's own default," i.e. the realistic regime — showed *zero* measurable
  difference (`12-BENCHMARK.md:106-145`). Any shift-model benchmark must include the realistic
  over-allocation percentage as a first-class scenario, not an optional "informational" appendix,
  because that is precisely where Phase 12's headline effect evaporated.
- **Include a `FULL_ASSERT` correctness test as a separate must-pass gate**, independent of the
  performance benchmark (see Pitfall 9) — Phase 12 kept this gate even though the performance
  threshold failed, and it is what let the operator confidently say "the move itself was never
  wrong" even while withdrawing it.
- **Standard ways teams fool themselves, named explicitly so this milestone's authors recognize
  them:** (a) running once and calling it done: only pass 1/5 threshold data ever ends up in a
  report if the plan actually specifies N≥5 runs, not "run it and see"; (b) reporting mean instead
  of median on a bimodal distribution (the baseline distribution in `12-BENCHMARK.md:58` was
  explicitly noted as bimodal — a mean would have hidden that); (c) comparing against a stale or
  differently-terminated historical number instead of an in-run baseline (the exact trap
  `12-BENCHMARK.md`'s threshold #5 caveat spends four paragraphs disentangling,
  `12-BENCHMARK.md:80-98`); (d) moving the goalposts — picking whichever of several plausible
  headline metrics (hours assigned vs. hard score vs. soft score vs. drift) moved favorably, after
  the fact, instead of committing to one pre-declared metric.

**Warning signs:**
- Any benchmark report for this milestone that shows only one run, or only a mean, or a wall-clock
  duration as the termination criterion.
- A benchmark report written *after* deciding the shift model "worked" rather than before running
  it — order of operations is itself a leading indicator of motivated reasoning.
- A must-pass threshold that changes wording between the design doc and the results doc.

**Phase to address:** whichever phase claims the shift model improves schedule quality, coverage, or
consistency over the slot model — almost certainly the phase that ships the coupled shift variable
and, separately, the phase that ships the consistency constraint. Each needs its own benchmark
against this design, not one benchmark covering the whole milestone's claims at once.

---

### Pitfall 4: Dual-mode coexistence — config drift, untested mode combinations, and orphaned accepted schedules

**What goes wrong:**
A per-desk mode switch (shift-scheduled vs. slot-scheduled) means the solver, the constraint
provider, and the UI must all behave correctly for three states, not one: pure slot desks, pure
shift desks, and — the state everyone forgets — a *tenant* with both kinds of desk in the same
solve batch, upload template, or roster view, since desks are scoped by `tenant_id` but solved and
reported on together in most surfaces. Concretely:
- A constraint that reasons about `AgentAssignment` groupings (nearly all 18 in
  `ScheduleConstraintProvider` today) must either be shift-aware for shift-scheduled desks or must
  provably not fire incorrectly for them — `exactlyOneBreak`, `breakDuration`,
  `breakBlockedWindow`, and `breakStartAlignment` in particular all assume the break is *discovered*
  from the seat pattern (`getGapLengths`, `findBreakStart`); a shift model that fixes the break
  location structurally could make these constraints either redundant (fine) or actively
  contradictory (not fine, if the shift's break placement and the constraint's independently-derived
  expectation can disagree).
- A test suite that only ever constructs slot-scheduled fixtures (which is the entire existing test
  suite, since shift mode does not exist yet) will stay green while shift-scheduled desks silently
  misbehave — this is the exact shape of the BambooHR field-4517 alias bug: "every unit test stays
  green, because the fixtures hand-construct the object" without the real integration ever being
  exercised (`PROJECT.md`, Known issues). The equivalent risk here is any test fixture that builds a
  `Schedule` without ever setting the new per-desk mode flag, defaulting it implicitly to whatever
  the field's Java default happens to be, and never noticing that shift-mode code paths are
  untested.
- A desk with an **already-accepted schedule** (published, operator-facing, possibly exported)
  whose mode is switched afterward: does the accepted schedule's stored `AgentAssignment` rows still
  mean what the UI now claims they mean? Does drift reporting or usual-shift consistency scoring
  apply retroactively to a schedule generated before the mode existed? This is squarely the kind of
  gap Phase 13's audit finding I-1 illustrates — "the milestone built a storage model and a parser
  but never migrated the *display*" — except here the risk is a stored schedule outliving a mode
  change rather than a stored value outliving a schema change. The lesson generalizes identically:
  building the shift model and building what happens to *already-existing* schedules under a mode
  switch are separate jobs, and the second one is easy to forget because there's no natural moment
  in development where you're forced to think about pre-existing data.

**Why it happens:**
The "pilot on one desk without touching the rest" framing (`PROJECT.md`, target features) is
correct and valuable, but it invites treating shift mode as strictly additive — new code path,
existing code path unchanged, no interaction. In a solver with 18 constraints that mostly reason
over `AgentAssignment` regardless of the entity's owning desk's mode, that assumption needs to be
proven per constraint, not assumed globally.

**How to avoid:**
- Enumerate every one of the 18 existing constraints and mark each one, explicitly, as: (a)
  mode-agnostic (behaves identically and correctly regardless of desk mode — most of the
  specialization/coverage/preference ones likely qualify), (b) shift-mode-must-be-bypassed
  (the four break constraints are the prime candidates — if the shift envelope structurally fixes
  the break, these either need a mode guard or need to be proven redundant-not-contradictory), or
  (c) shift-mode-needs-a-new-variant. This enumeration should be a deliverable of the phase that
  introduces the mode switch, not an incidental discovery during code review.
- Write at least one integration test per constraint category that constructs a mixed-mode
  `Schedule` (some desks shift-scheduled, some slot-scheduled, solved together) — not because mixed
  desks are the common case, but because a test suite where *every* fixture is single-mode is
  structurally blind to interaction bugs, the same blindness that let the field-4517 alias ship
  broken while every hand-constructed fixture passed.
- Decide and document, before building the mode switch UI, what happens to an already-accepted
  schedule when its desk's mode changes: forbid the switch while an accepted schedule exists for
  that desk (simplest, safest), or explicitly define how existing `AgentAssignment` rows are
  interpreted/migrated/re-tagged. "Undecided" is the wrong answer to ship with, because the audit
  precedent (I-1) shows undecided-by-omission surfaces as an operator-visible defect, not a build
  failure.
- Treat the mode flag as a first-class dimension in every new test fixture builder from day one —
  the same discipline this project already applies to tenant scoping (`tenant_id` on every entity)
  should apply to desk mode, precisely because tenant scoping is the thing that made desks with both
  modes reachable in the same tenant a real, not hypothetical, case.

**Warning signs:**
- A new constraint or move added for shift mode with no corresponding assertion about what it does
  on a slot-scheduled desk — it should be a no-op; that needs to be *verified*, not assumed.
- Any test file for shift-mode logic where every fixture in the file sets the same mode — the same
  code smell as the BambooHR fixture gap.
- Code review surfacing a constraint that reads `AgentAssignment` fields the shift model changes the
  meaning of (e.g., break gap detection) without a mode branch or a comment proving equivalence.

**Phase to address:** the phase that introduces the per-desk mode switch — but the constraint
enumeration described above should start as soon as the shift variable and its constraints exist,
since it is analysis work, not new code, and is cheapest done early.

---

### Pitfall 5: Soft-constraint weight interactions — the consistency weight dominating or being dominated

**What goes wrong:**
`ScheduleConstraintProvider` already has a documented weight hierarchy among soft constraints:
`minimumStaffing` defaults to `ofSoft(1000)` specifically because it must "dominate the other soft
terms (honour-start-time 5, prefer-primary 1, break-clustering 2)"
(`ScheduleConstraintProvider.java:461-463`). Adding a new soft "distance from usual shift"
consistency constraint into this hierarchy without deliberately choosing where it sits relative to
the existing weights risks two failure modes: (a) it is weighted too low and is *always* traded away
by every other soft constraint, making the "operator-configurable tolerance band and weight per
desk" feature (`PROJECT.md`) inert in practice — a knob that never moves the schedule; or (b) it is
weighted too high (especially if it uses a quadratic penalty, per the already-recorded decision
"quadratic penalties for hours consistency ... avoids score traps," `PROJECT.md` Key Decisions) and
it silently overrides coverage or break correctness for agents whose usual shift the solver cannot
currently honour, producing a schedule that is "consistent" but understaffed or break-illegal in a
way that a human reading only the accepted schedule would not immediately notice, because coverage
constraints are hard and consistency is soft — the operator has to specifically look for it.

**Why it happens:**
Weight tuning for a new soft constraint is usually done in isolation ("does raising this weight
produce more consistent shifts in my test scenario?") rather than against the *existing* hierarchy.
`bulkUnderallocationSoft`'s own commentary is instructive here: it exists precisely because, before
it was added, "a schedule could ... score a flawless 0soft while whole hours of demand went
unstaffed ... because nothing pulled coverage in either direction"
(`ScheduleConstraintProvider.java:592-595`). A consistency weight introduced without checking its
interaction against that same coverage-pulling mechanism can recreate an equivalent blind spot in
reverse: a schedule that scores well on consistency while quietly under-covering demand, if
consistency's pull is strong enough relative to `bulkUnderallocationSoft`'s.

**How to avoid:**
- Do not pick the consistency weight's default value from first principles or a single test run.
  Use the constraint-match breakdown Timefold already exposes via `SolutionManager.explain()` —
  exactly the mechanism `SolverService.runPreSolveScoreDiagnostic` already uses
  (`SolverService.java:1126-1132`, `explanation.getConstraintMatchTotalMap()`) — to print the
  per-constraint soft-score contribution on a representative solved schedule, both with and without
  the new constraint active, at several candidate weights, and confirm the *ranking* of constraint
  influence matches intent (coverage > breaks > consistency > preferences, or whatever order the
  product decision actually is) rather than eyeballing the final total.
- Quadratic penalties (already decided for hours-consistency, and the natural default for shift
  consistency too, "because linear penalties create score traps," `PROJECT.md`) change *how* a
  weight interacts with others as the violation grows — a small drift is cheap, a large drift is
  disproportionately expensive. That non-linearity must be accounted for when comparing against the
  existing *linear* soft constraints (`honourPreferredStartTime`, `preferPrimarySpecialization`,
  etc. all penalize a flat `1` per violation, `ScheduleConstraintProvider.java:507-538`). A quadratic
  constraint compared against linear ones at "the same weight number" does not mean the same thing
  at small vs. large violation magnitudes — document the weight as a coefficient of a known curve,
  not a bare number that superficially looks comparable to the linear weights next to it in a config
  table.
- Provide the operator-configurable tolerance band as a genuine dead zone (zero penalty within
  tolerance), not just a low-weight taper — a taper still nudges the solver even inside the "should
  be free" zone and makes the configured tolerance a lie by degrees.
- Avoid a full seeded A/B re-benchmark (Pitfall 3's method) for every weight tweak — that is
  disproportionate for iterative tuning. Instead, use the same `SolutionManager.explain()`
  constraint-match breakdown as a fast, deterministic (single-schedule, no solver run needed for the
  *breakdown* itself once a schedule exists) signal for whether a weight change moved the ranking in
  the intended direction, and reserve the full seeded benchmark for confirming the *final* chosen
  weight doesn't regress coverage/quality metrics before it ships, not for every intermediate
  iteration.

**Warning signs:**
- A UAT schedule where every agent matches their usual shift but a `bulkUnderallocationSoft` or
  `minimumStaffing` violation is present that would not have existed under the slot model with the
  same input data — the signature of consistency out-competing coverage.
- An operator reports the tolerance-band weight slider "doesn't seem to do anything" — the signature
  of the constraint being weighted too low to ever win a trade-off, or of the tolerance band being
  implemented as a taper rather than a true dead zone.
- Constraint-match breakdown on a representative schedule showing the consistency constraint's total
  soft-score contribution is either ~0 (never binds) or an order of magnitude larger than every
  other soft constraint combined (always wins) — both are signs the weight was never checked against
  the existing hierarchy.

**Phase to address:** the phase that introduces the consistency constraint and its per-desk weight/
tolerance configuration.

---

### Pitfall 6: Model built, view never migrated — "shift assigned" true in the database, invisible to the operator

**What goes wrong:**
This is the single most directly-precedented risk in this milestone, because it already happened
once in this exact codebase. Phase 9/10 of v1.2 made `agent_day_hours` authoritative and had it
populated correctly, but `DeskAgentService.toResponse` and `DeskAgentExportService` kept reading the
retired `Agent.contractedHoursPerDay` scalar — so "an operator verifying their own upload saw a
stale value unrelated to what they submitted" (`PROJECT.md`, audit finding I-1/F-1), and it took a
whole additional phase (13) to close. The shift feature has *at least* as many display surfaces as
the hours feature did, and each one is an independent opportunity to repeat I-1:
- The roster/desk-agent view (equivalent to `DeskAgentService.toResponse`) must show the agent's
  assigned shift for a shift-scheduled desk, not silently keep showing per-slot seat detail as if
  nothing changed.
- The Excel export (equivalent to `DeskAgentExportService`) must include the shift, not just the
  underlying seat assignments — an operator reconciling an exported schedule against the shift
  library needs to see which shift was picked, not reverse-engineer it from timeslot rows.
- The accepted-schedule view an operator reviews before accepting must show the shift alongside
  per-slot specialization detail (the milestone's own framing: "seat/specialization assignment stays
  per-slot inside the envelope, so an agent can still change specialization mid-day" — that
  within-shift variation needs its own visible representation, or an operator cannot verify it).
- The drift report (its own named feature) is *itself* a view of the model — "a panel naming which
  agents broke their usual shift, when, and by how much" — and needs the same scrutiny: does it read
  from the same authoritative source the solver used, or from a stale/derived copy?
- The Usual Shift population paths — "a Usual Shift column in the per-desk upload template *and*
  inline editing in the roster UI" (`PROJECT.md`) — mirror exactly what v1.2 built for contracted
  hours, which means they inherit the same risk v1.2 had: a value can be *written* correctly by the
  parser or the inline editor while a *different, unmigrated* read path shows something else. This
  is not a hypothetical parallel — it is the literal precedent this milestone explicitly names
  itself against.

**Why it happens:**
Building the write path (model + parser + solver consumption) is naturally sequenced first because
the solver cannot use data that doesn't exist yet, and it produces a satisfying, demoable "the
solver picks a shift" result well before every display surface has been touched. The v1.2 audit's
own generalized lesson states this precisely: "building the model and building the view of the model
are separate jobs" (`PROJECT.md`). The mistake is treating the second as an afterthought or a
follow-up bug rather than as a first-class, separately-verified deliverable of the same phase (or an
explicitly scoped closure phase, as Phase 13 had to become).

**How to avoid:**
- Before this milestone's phases are planned, enumerate every read path for shift/usual-shift data
  the same way this document just did — roster view, export, accepted-schedule view, drift report,
  any solver-facing summary/diagnostic — and require each one to be an explicit UAT/verification
  criterion in whichever phase touches shift data, not an implicit assumption that "the model is
  done so the UI will just work."
- Reuse the shared-definition pattern this project already adopted for exactly this class of bug:
  `EnrichedColumnLayout` became "the single column-layout definition shared by template, parser and
  export" specifically because "header drift between the three was the standing risk"
  (`PROJECT.md` Key Decisions) — and even that needed Phase 13 to close the last two literal-string
  holdouts (audit finding I-4). If shift/usual-shift gets an equivalent shared definition (a single
  place that maps a shift's identity to its display label, envelope, and break), route every read
  path through it rather than letting each surface re-derive shift display independently.
- Add an explicit end-to-end trace as a verification step, the same way the v1.2 audit did for I-1's
  closure: "full flow template → fill → upload → sync → merge → results → roster verification traced
  end to end without a break" (`v1.2-MILESTONE-AUDIT.md`). For this milestone: shift library defined
  → agent assigned a shift by the solver → roster shows it → export shows it → drift report
  (if applicable) shows it — traced by reading current source, not accepted from a phase's own
  self-report.
- Do not let "the solver produces correct `AgentAssignment`/shift data" be the phase's sole
  verification criterion. Require a specific, named UI/export assertion per surface as a must-have,
  mirroring how Phase 13 was scoped specifically around "roster and Excel export resolve contracted
  hours from `agent_day_hours`... per-weekday values, `MANDATORY` and `PTO` visible in the UI"
  (`PROJECT.md`, UPL-03/04/05 equivalent framing).

**Warning signs:**
- Any phase plan whose acceptance criteria are all backend/solver-facing (constraint tests, solver
  output assertions) with no corresponding roster/export/UI assertion for the same data.
- A verification report that confirms "the solver assigns shifts correctly" without a screenshot,
  API-response assertion, or export-cell assertion showing the shift value as an operator would see
  it.
- A milestone audit (this milestone's own eventual audit) finding a critical integration gap between
  a "complete" model phase and a "complete" UI phase — the exact shape of I-1, which should be
  preventable this time given it is a *named, called-out* risk rather than a surprise.

**Phase to address:** every phase that writes shift or usual-shift data must include its own display
verification as a must-have, not deferred to a later "visibility" closure phase. If sequencing
pressure forces model-then-view anyway, name the view work as its own phase explicitly (as Phase 13
was) rather than assuming it falls out of the model phase's UAT.

---

### Pitfall 7: A guarantee that holds on the upload/inline-edit path but not on every other write path

**What goes wrong:**
v1.2's still-open audit finding I-2 is the second directly-precedented risk this milestone inherits:
the merge-precedence guarantee (MRG-02) was implemented and verified on the upload path
(`AgentMergeService`, wired into `DeskAssignmentUploadService`), while the "Refresh from BambooHR"
button reaches `BambooRefreshService.persistRefreshData` and "overwrites seven identity fields with
no precedence rule and emits no `MergeReportEntry`" — a second, entirely reachable, ordinary
operator action that silently bypasses the guarantee (`v1.2-MILESTONE-AUDIT.md`). The project's own
generalized lesson: "a requirement verified `passed` in its own phase, in isolation, while a second
reachable entry point quietly violates it... the merge engine works. It is simply not the only
writer" (`v1.2-MILESTONE-AUDIT.md`). This milestone creates *at least* the following write paths for
shift/usual-shift data, and every one of them is a candidate for the same class of gap:
1. **Usual Shift upload template column** — the milestone's own stated first population path.
2. **Inline roster editing** — the milestone's own stated second population path, "mirroring exactly
   what v1.2 built for contracted hours" (which itself had `setDayHours` for safe single-cell edits
   *and* a separately destructive bulk "Set all days" path that survives as audit finding I-3,
   `PROJECT.md`) — meaning this milestone should expect an equivalent bulk-edit-destroys-labels risk
   for shift assignment/usual shift unless deliberately designed against it from the start.
3. **The solver itself**, writing the chosen shift as part of solving — does this write path respect
   or ignore the stored "usual shift" the same way it should? Does a solve overwrite an
   operator-corrected usual shift, or only the *solved* shift assignment?
4. **BambooHR refresh**, if usual shift or any shift-adjacent field is ever sourced from or
   overwritten by BambooHR sync — given `BambooRefreshService` is the exact component that already
   bypasses a precedence guarantee once, any new field it touches inherits the same risk by default
   unless explicitly routed through whatever merge/precedence mechanism this milestone builds.
5. **Mode switch itself** (Pitfall 4) — does switching a desk out of shift mode and back in preserve
   or discard the stored usual shift and any accepted shift assignments?

**Why it happens:**
Each write path is typically built by whoever is working the feature that naturally needs it, at
different times, without a standing checklist of "every place that can touch this field." The
project's I-2 gap arose specifically because `AgentMergeService`'s precedence logic was correctly
scoped to the upload flow it was built for, and nobody separately audited every *other* caller that
could write the same fields until a full milestone audit specifically went looking
(`v1.2-MILESTONE-AUDIT.md`: "Root cause I-2... never in any phase's scope, including Phase 13").
Precedence/consistency guarantees do not enforce themselves across call sites in a codebase with no
single choke-point write method for these fields.

**How to avoid:**
- Before building any write path, enumerate all of them (the five above, adjusted for what this
  milestone actually implements) as an explicit table in the phase(s) that touch shift/usual-shift
  data, and state, for each, what invariant must hold after that path executes (e.g., "usual shift
  set via upload must not silently override an operator's more recent inline edit," "a solve must
  never overwrite the stored usual-shift *target*, only the solved shift assignment," "switching
  desk mode off and back on must not silently discard usual-shift history").
- Prefer a single choke-point service method for each mutation (analogous to what `AgentMergeService`
  *should* have been for every identity-field writer, not just the upload one) — e.g., one
  `ShiftAssignmentService.setUsualShift(...)` that every UI/upload/sync path calls, rather than each
  caller writing the entity directly. `DeskAgentService.setDayHours` is this project's own precedent
  for a safe, narrowly-scoped write method (edits exactly one weekday, per Phase 13's D-10 decision)
  — reuse that shape rather than letting each new path invent its own write logic.
- Add an explicit "every write path" verification step to the phase(s) that build shift write paths,
  modeled on what this milestone's own audit will eventually check: grep for every caller that can
  mutate the shift/usual-shift fields, and confirm each one is either (a) routed through the
  choke-point method, or (b) has an explicit, documented reason it is exempt.
- Do not let "the upload path is tested and correct" close out the requirement the way MRG-02 was
  initially treated as satisfied — the audit's own correction was narrow and specific: MRG-02's
  wording was unscoped ("For every field carried by both sources...") while six of seven MRG
  requirements were scoped to the upload event and therefore not literally violated by Refresh
  (`v1.2-MILESTONE-AUDIT.md:179-189`). Write this milestone's equivalent requirements with the scope
  made *explicit in the wording itself* ("on the upload path" vs. "on every write path") so a future
  audit does not have to adjudicate an ambiguity the requirement's author could have resolved by
  being precise the first time.

**Warning signs:**
- Any second UI control, endpoint, or sync job that can set a shift or usual-shift value discovered
  during code review *after* the "primary" path (upload or inline edit) was already verified as
  correct — treat this as a stop-and-check moment, not a footnote.
- A requirement document phrased in universal language ("the system ensures X") about a guarantee
  that was actually only implemented and tested on one path — the exact ambiguity the I-2 audit had
  to resolve after the fact.
- `grep` for the shift/usual-shift entity's setters turning up more call sites than the phase's own
  test suite covers.

**Phase to address:** the phase(s) that build the Usual Shift population paths (upload column +
inline edit) and the phase that wires the solver's shift assignment — the write-path enumeration
should be a named deliverable of whichever phase is first to introduce a second writer, and
revisited every time a new writer is added later (mode switch, BambooHR sync extension, etc.).

---

### Pitfall 8: New Flyway migrations on a live, single-environment, no-review-gate deployment

**What goes wrong:**
This milestone needs new schema — at minimum a per-desk shift library table, a stored usual-shift
table (per-agent, per-weekday, mirroring `agent_day_hours`'s shape per `PROJECT.md`), and a per-desk
mode flag. The deployment context makes any migration mistake immediately production-facing: "Push
to `main` **or `claude/create-system-specification-451ge`** → GitHub Actions → Docker build → ECS +
S3/CloudFront... pushing that working branch deploys straight to the live environment with no review
gate" (`PROJECT.md`, Context section), on `RDS PostgreSQL 16, db.t4g.medium, single AZ`, with no
staging environment at all ("Multi-environment staging — single environment only," Out of Scope).
There is no environment in which a migration can be rehearsed against production-shaped data before
it runs against production data itself. This project is already at schema version V36 after v1.2,
and its own migration history includes at least one non-trivial data-preserving migration under
exactly these constraints (V29's name-split + per-day fan-out) — so the risk is not hypothetical,
it is the normal mode of operation here.

**Why it happens:**
Single-environment-by-design (a deliberate, accepted cost-control decision, `PROJECT.md` Key
Decisions: "Single environment ('dev') | Internal use, cost control | ✓ Good") means every migration
is a first-and-only run against real data, and the no-review-gate branch trigger means there is no
human checkpoint between "migration looks right in code" and "migration ran against production."

**How to avoid:**
- Every new migration for this milestone must be additive-only and backward-compatible with the
  slot-only code path until the corresponding application code deploys in the *same* release — the
  same pattern this project already used for `Agent.workingDaysSource` (V36), which "defaults to
  `BAMBOOHR`... keeps every existing agent's eligibility unchanged at deploy time" (`PROJECT.md`).
  A new shift-library or usual-shift table with no rows yet is inherently safe in this sense; a
  migration that *alters* an existing table (e.g., adding a NOT NULL mode column to `desks` without
  a default) is not, and must ship with an explicit default matching current (slot-scheduled)
  behavior for every existing desk.
- Verify the next Flyway version number against the actual latest applied version before writing the
  migration, the same discipline already recorded for this codebase ("V30 confirmed as the correct
  next Flyway migration version — V29 verified as latest applied version before creating V30,"
  `STATE.md`, Phase 10 decisions) — a version collision or gap on a single-environment deployment
  with no rollback rehearsal is a self-inflicted outage.
- Any migration touching existing rows (as opposed to adding new tables/columns) needs a dry-run
  discipline equivalent to what Phase 9 used for its name-split/fan-out migration: "Manual dry-run...
  asserts exact row counts, NULL-scalar zero-row rule, and SQL-vs-Java split agreement before this
  ships to real data" (`09-06-PLAN.md`, T-09-16). Given this milestone's schema additions are mostly
  new tables (shift library, usual shift, mode flag) rather than data transformations of existing
  columns, this is a lower-probability risk than v1.2's name-split was — but should still be
  confirmed explicitly per migration rather than assumed safe by category.
- Given the no-review-gate branch deploys straight to production, treat the migration file itself as
  the review gate: write it, re-read it specifically for "does this fail or change behavior for any
  row that exists today," and only then let it merge — there is no second chance to catch a bad
  migration in a lower environment first.

**Warning signs:**
- A migration that adds a `NOT NULL` column with no `DEFAULT` to a table with existing rows —
  Flyway/Postgres will simply fail the migration outright in production with no environment to have
  caught it first.
- A migration and the application code that depends on it landing in different deploys — the
  in-between state (schema present, code not yet reading/writing it, or vice versa) is exactly the
  window where a stale scalar or unmigrated read path (Pitfall 6) becomes possible.
- Any migration whose author cannot state, in one sentence, what happens to every existing desk/agent
  row when it runs — that is the sentence a staging environment would normally let you verify
  empirically; here it has to be verified by reading the migration.

**Phase to address:** every phase that adds schema — the shift library and usual-shift phases most
directly — should treat the migration as a reviewed artifact in its own right, not an implementation
detail folded silently into a broader plan.

---

### Pitfall 9: Timefold 1.16.0-specific traps for a multi-entity/coupled-variable model

**What goes wrong:**
Several 1.16.0-specific facts this project has already verified against source and its own running
solver create sharp edges specific to adding a second planning dimension:
- **This project's `solverConfig.xml` currently has no `<unionMoveSelector>` at all** — Timefold
  "only auto-builds the default change+swap union when localSearch declares no moveSelector at all"
  (`STATE.md`, Phase 12 decision log). The moment this milestone adds *any* custom move or a
  second value-range provider and needs to declare a `moveSelector`, that auto-default silently
  disappears and must be explicitly re-declared alongside whatever new move is added — Phase 12
  already had to do exactly this ("Explicit `unionMoveSelector` re-declares
  `changeMoveSelector`/`swapMoveSelector` alongside `AtomicShiftMoveFactory`," `STATE.md`). Forgetting
  this silently removes the solver's basic repair capability for the *existing* seat-assignment
  variable, not just the new one — a regression that would not show up in a shift-focused test but
  would degrade every slot-scheduled desk's solve quality.
- **No test under `src/test/java/com/wfm/solver/` loads the Spring context**, so "a scoped
  solver-package run cannot catch a `solverConfig.xml` regression" — this already bit Phase 12 once
  ("six `@SpringBootTest` suites failed on an XSD element-ordering error that the scoped run
  passed," `12-VERIFICATION.md:127-130`). Any change to `solverConfig.xml` for the shift model
  (new move selectors, new value-range providers, changed termination) must be validated with the
  full suite, not a `com.wfm.solver.*`-scoped run — this is a standing, explicitly recorded
  constraint (`STATE.md`, Phase 12 Decisions: "any change to that file must be validated with the
  full suite").
- **Undo correctness for a coupled two-variable move is a materially new correctness question**, not
  a repeat of Phase 12's. `AbstractMove.doMoveOnGenuineVariables` with framework-generated undo
  (confirmed as this pinned version's actual custom-move API, not the `Neighborhoods` API from
  1.31.0 which is unavailable, `PROJECT.md`) auto-generates undo from *recorded variable changes* via
  `VariableChangeRecordingScoreDirector`. A move that changes both the shift variable and one or more
  seat variables together (e.g., a shift-pick move that also assigns/clears seats to match the new
  envelope) needs every one of those variable changes correctly wrapped in `beforeVariableChanged`/
  `afterVariableChanged` pairs for the auto-undo to reconstruct correctly — a single omitted pair on
  either variable produces a corrupted-undo bug that is silent until `FULL_ASSERT` (or worse, a
  production solve) surfaces it. The project's own cross-agent-seat-displacement follow-up todo
  independently flagged this exact risk for a *simpler* single-variable displacement move: "undo
  correctness under displacement is a new correctness question `AtomicShiftMoveFullAssertTest`-style
  coverage will need to re-prove" — a two-variable coupled move needs that proof at least as much.
- **`EnvironmentMode.FULL_ASSERT` is not free and is not the default** — Timefold's own enum ordering
  (`TRACKED_FULL_ASSERT` > `FULL_ASSERT` > `NON_INTRUSIVE_FULL_ASSERT` > `FAST_ASSERT` >
  `REPRODUCIBLE` [default] > `NON_REPRODUCIBLE`, verified against 1.16.0 source,
  `12-RESEARCH.md:585-601`) means correctness assertion is opt-in per test, and this project's
  existing pattern for it (`IncrementalScoringDiagnosticTest.java`) is a dedicated, separate test
  from the main suite — that pattern must be extended for the shift model's coupled move(s), not
  assumed to be covered by whatever ran for the seat-only model.
- **This project already has a lighter-weight, always-on corruption tripwire that is easy to
  overlook because it isn't named "FULL_ASSERT": `SolverService.runPreSolveScoreDiagnostic`**
  (`SolverService.java:1109-1171`). It builds a bare `SolverFactory`/`SolutionManager`, scores the
  initial state, assigns one agent to one seat, re-scores, and logs an error if the hard-score delta
  is `<= 0` — explicitly designed to "detect broken incremental scoring that would cause the CH to
  pick `{null -> null}` for every step." This is *not* `FULL_ASSERT` (it does not re-derive the score
  from scratch and compare) but it is a live, running, production-path diagnostic that already exists
  and already runs before every solve. Extending it to also probe the shift variable (assign one
  shift, check the delta) is a cheap way to get an early, production-representative corruption
  signal that complements, rather than replaces, a dedicated `FULL_ASSERT` test.

**Why it happens:**
Timefold's move/undo/environment-mode machinery is designed around single or already-composable
moves; nothing in the API stops a developer from writing a move that mutates two variables without
correctly bracketing both, and the failure mode (corrupted incremental score) is by design invisible
under the default `REPRODUCIBLE` mode — that is precisely why `FULL_ASSERT` exists as an opt-in, and
precisely why a team under deadline pressure skips it.

**How to avoid:**
- Explicitly re-declare `<unionMoveSelector>` in `solverConfig.xml` the moment any custom move for
  the shift model is added, including the pre-existing `changeMoveSelector`/`swapMoveSelector` inside
  it — do not rely on the auto-default once any custom selector is present.
- Any `solverConfig.xml` change must be validated by running the full test suite (`./gradlew build`
  or equivalent), not a `com.wfm.solver.*`-scoped run — restate this as an explicit checklist item in
  every phase plan that touches the file, since the codebase itself cannot enforce it structurally
  (no Spring-context-loading test exists in that package).
- Build a `FULL_ASSERT` integration test for the shift-pick move (or whatever move mechanism Pitfall
  1 settles on) before claiming the coupling correct, following the existing
  `IncrementalScoringDiagnosticTest.java` pattern — this is a must-pass gate independent of, and
  prior to, any performance benchmark (Pitfall 3), exactly as Phase 12 kept `AtomicShiftMoveFullAssertTest`
  green even after its performance threshold failed.
- Extend `runPreSolveScoreDiagnostic` to also probe the shift variable's first-assignment delta, so
  the always-on production diagnostic covers both planning dimensions, not just the legacy one.
- Do not port any move code from pre-1.16.0 Timefold/OptaPlanner examples verbatim — `12-RESEARCH.md`
  already flags that `Move.doMove(ScoreDirector)` returning an undo move and hand-written
  `createUndoMove` are deprecated-for-removal patterns that "will compile... but represent a stale
  pattern signal, not something to imitate" (`12-RESEARCH.md:914-919`). Any shift-model move code
  must implement `doMoveOnGenuineVariables` only, matching this project's pinned-version convention.

**Warning signs:**
- A local search run that never seems to change the shift variable at all, or changes it but never
  the seats — the `unionMoveSelector` auto-default silently dropped, or the new move/selector isn't
  actually wired in.
- Any test failure whose stack trace mentions score corruption or an assertion mismatch that only
  reproduces under `FULL_ASSERT` and not under the default suite run — do not treat this as flaky;
  treat it as the exact signal `FULL_ASSERT` exists to produce.
- A `solverConfig.xml` diff reviewed and merged on the strength of a green `com.wfm.solver.*`-scoped
  test run alone — this project has direct, on-record evidence that this specific verification gap
  already caused a false-green once (Phase 12, `12-VERIFICATION.md:127-130`).

**Phase to address:** the phase that introduces the coupled shift move/value-range mechanism — the
`FULL_ASSERT` test and the `solverConfig.xml` full-suite-validation discipline must both be in place
before that phase is considered complete, not retrofitted after a bug report.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Coupling shift↔seat only via a soft penalty constraint, skipping a filtered value range | Faster to ship — one more constraint, no value-range plumbing | Solver wastes search budget on jointly-infeasible states it should never have offered itself; reproduces the noise-floor problem Phase 12 already hit | Never as the shipped design — acceptable only as a throwaway spike to sanity-check constraint wording, discarded before the real implementation |
| Shipping the shift-scheduled mode switch without the demand-coverage validator (Pitfall 2) | Mode switch ships sooner, pilot desk can be flipped immediately | An operator can flip a desk into an infeasible or silently-undercovered state with zero warning — directly the failure mode this milestone needs to avoid | Never for the pilot desk (it is exactly the case this needs to protect); marginally more acceptable for a desk with a known-generous, already-validated-by-eye shift library, but still risky |
| Reusing v1.2's bulk-edit-destroys-labels pattern (I-3) for shift/usual-shift bulk operations instead of designing a safe bulk path from the start | Saves design time by copying an existing UI pattern | Ships a *known*, already-audited defect class into a new feature on day one, rather than merely repeating an old mistake by accident | Never — this is the one shortcut this milestone has no excuse to take, since the defect it would copy is already documented as unresolved (999.9) |
| Deferring the "every write path" enumeration (Pitfall 7) until after the primary upload/inline-edit paths ship | Ships the headline feature sooner | Reproduces I-2 exactly — a guarantee that holds on one path, discovered broken on another only at the next milestone audit | Acceptable only if the enumeration is scheduled as a named, tracked follow-up with an owner and a deadline inside *this* milestone — not left implicit the way I-2 was |
| Weighting the consistency constraint by guesswork rather than the `SolutionManager.explain()` breakdown (Pitfall 5) | Faster initial tuning | Either an inert feature (weight too low) or coverage-displacing consistency (weight too high), discovered only in UAT or live use | Acceptable only for a first internal spike, never for the weight that ships to the pilot desk |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|-------------------|
| Shift library ↔ FTE demand upload | Treating the two as independently editable with no cross-check, as they are today (separate services, separate UI surfaces) | Validate library coverage against the demand curve on every library edit and mode switch (Pitfall 2), reusing `runPreSolveValidation`'s `ErrorDetail` pattern |
| Usual Shift upload template ↔ existing per-desk upload template (`EnrichedColumnLayout`) | Bolting a Usual Shift column onto a separate, new template format instead of extending the shared layout definition this project already built specifically to prevent header drift (`EnrichedColumnLayout`) | Extend `EnrichedColumnLayout` (or its direct successor) rather than introducing a second, parallel column-layout source of truth |
| BambooHR refresh ↔ any new shift-adjacent field | Assuming `BambooRefreshService` will "just not touch" new fields it doesn't know about, without verifying | Explicitly confirm (by reading `persistRefreshData`, not by assumption) that the refresh path cannot touch shift/usual-shift fields at all, or route it through the same choke-point write method every other path uses |
| Solver ↔ stored "usual shift" | Letting the solver's chosen shift assignment silently overwrite the stored usual-shift *target* field, conflating "what was solved" with "what the agent's target is" | Keep "usual shift" (target, operator/upload-set) and "assigned shift" (solved, per schedule) as distinct fields/tables from day one, mirroring how `AgentDayHours` (contracted target) and `AgentAssignment` (solved seat) are already distinct today |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| A second `groupBy(AGENT_ID, DATE, ...)`-shaped constraint stream for shift logic that doesn't reuse the shared grouping instances (`AGENT_ID`, `DATE`, `TO_LIST`, `COUNT` constants, `ScheduleConstraintProvider.java:49-59`) | Move-evaluation throughput drops noticeably once shift constraints are added — this project already measured a real cost here once (32,489/sec → 44,000+/sec after consolidating five duplicate groupings into shared nodes, per the comment at `ScheduleConstraintProvider.java:43-46`) | Any new constraint that groups by `(agentId, date)` must reuse the existing static `AGENT_ID`/`DATE`/`TO_LIST`/`COUNT` function instances, not inline lambdas — Timefold shares constraint-stream nodes only for identical lambda *instances* | Immediately, at whatever agent/timeslot count the benchmark scenario uses — this is a correctness-of-measurement issue as much as a performance one, since a slower move-eval rate directly reduces how many steps a step-count-terminated benchmark run actually explores |
| Two coupled planning variables inflating the effective branching factor of local search | Benchmark (Pitfall 3) shows *more* noise/spread than Phase 12's single-variable baseline, not less, even before any shift-specific quality claim is evaluated | Budget for this explicitly in the benchmark's step-count and seed-count choices — do not assume Phase 12's 2000-step/5-seed configuration transfers unchanged to a strictly larger search space | At whatever point the benchmark's baseline spread grows large enough to swallow the effect size being measured — exactly Phase 12's own failure mode, now with a structurally larger search space to start from |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Exposing shift-library or usual-shift edit endpoints without the same tenant-scoping discipline every other entity in this codebase already follows (`tenant_id` on every entity, JWT-derived) | Cross-tenant shift-library or usual-shift leakage/tampering | Confirm every new entity and endpoint carries and enforces `tenant_id` exactly like existing desk/agent entities — treat this as a checklist item in each new phase's security review, not an assumption |
| Shipping a shift phase with no `SECURITY.md` (this project already has two open instances of exactly this gap — Phase 9 and, functionally, Phase 12) | An active write/read surface with no threat model on record, discovered only at the next milestone audit | Run the security-review step for every phase that adds a write path or a new entity, before milestone close, not deferred to backlog |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Showing "shift assigned" with no indication of *which* seats within it changed specialization, when the milestone's own design explicitly allows specialization to vary within a shift | Operator cannot verify the within-shift detail the model actually computed — a narrower version of Pitfall 6 | Roster/export views should show both the shift envelope and the per-slot specialization detail inside it, not collapse to shift-only |
| A drift report that names *that* an agent broke their usual shift but not *what the operator can do about it* (accept the drift, or the specific action to realign it) | A report an operator reads once and then ignores, the same way data-gap/outlier agents are currently "surfaced only as CloudWatch `log.warn` lines — no operator UI" (`PROJECT.md`, Known issues) | Design the drift report as an actionable panel from the start, not a passive log-equivalent — this project already has a standing, acknowledged debt item of exactly the passive-report failure mode to avoid repeating |
| A tolerance-band/weight control with no visible feedback loop (operator sets a weight, has no way to see whether it changed anything) | Recreates the "the slider doesn't seem to do anything" warning sign from Pitfall 5, as a UX problem even when the underlying weighting is technically correct | Surface the constraint-match breakdown (or a simplified version of it) back to the operator, or at minimum log/display the drift-report delta before/after a weight change |

## "Looks Done But Isn't" Checklist

- [ ] **Shift assigned by the solver:** Often missing a roster-view surface — verify the desk-agent
  view (not just the export, not just an API response) renders the assigned shift for a
  shift-scheduled desk.
- [ ] **Usual Shift upload column:** Often missing wiring into the shared column-layout definition —
  verify template, parser, and export all resolve the Usual Shift column through one shared
  definition, not three independently-maintained literals (the exact defect Phase 13 had to close
  for specialty headers, audit finding I-4).
- [ ] **Consistency constraint weight:** Often missing a check against the *existing* soft-constraint
  hierarchy — verify via `SolutionManager.explain()` that the new weight's actual influence, on a
  representative schedule, ranks where the product decision intends relative to coverage/break/
  preference constraints, not just that raising the number "does something."
- [ ] **Per-desk mode switch:** Often missing an explicit answer for what happens to an existing
  accepted schedule on switch — verify this is a tested, documented behavior (forbid, migrate, or
  re-tag), not an unhandled case.
- [ ] **Every write path for usual shift:** Often missing the second, third, and fourth writer —
  verify by grepping every setter/mutator for the usual-shift entity and confirming each caller is
  either routed through one choke-point or explicitly, deliberately exempted.
- [ ] **`FULL_ASSERT` coverage for the coupled move:** Often missing entirely, because the default
  test suite runs under `REPRODUCIBLE` mode and will not surface corrupted-undo bugs — verify a
  dedicated `FULL_ASSERT` test exists for whatever custom move mechanism the shift-seat coupling
  uses, following the `IncrementalScoringDiagnosticTest.java` pattern already in this codebase.
- [ ] **Shift library vs. demand coverage:** Often missing entirely as a pre-solve check — verify a
  desk cannot be switched into shift-scheduled mode, nor have its library edited, without a coverage
  check against the desk's actual demand upload.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|------------------|
| Two coupled planning variables shipped with a constraint-only coupling (Pitfall 1) discovered post-ship via solver thrashing/poor quality | MEDIUM–HIGH | Convert to a filtered value range without changing the stored data model (the coupling mechanism is a solver-config/constraint-provider change, not a schema change) — recoverable without a migration, but requires the same `FULL_ASSERT`/benchmark re-proof as if built correctly the first time |
| Shift library shipped without demand-coverage validation, a desk goes infeasible in production (Pitfall 2) | LOW | Add the coverage validator retroactively and re-run it against every existing shift-scheduled desk to surface which ones are currently in a bad state, rather than waiting for the next operator complaint — the check is read-only against existing data, so it is cheap to backfill |
| Model shipped, view not migrated (Pitfall 6), discovered at the next milestone audit exactly as I-1 was | MEDIUM | Follow the Phase 13 precedent directly: scope a dedicated closure phase against the specific named surfaces (roster, export, drift report), verify by reading current source end-to-end rather than accepting a self-report, as the audit itself already did once |
| A write path bypasses the precedence/consistency guarantee (Pitfall 7), discovered at audit exactly as I-2 was | LOW–MEDIUM (fix) but has proven HIGH in practice for *closure* (I-2 has survived two audits open) | Route the offending path through the choke-point method built for the primary path — this project's own recommended options for its own I-2 gap (route through the merge/precedence service, constrain what the bypass path can touch, or explicitly document the scope) are directly reusable as options for any equivalent gap this milestone creates |
| Weight tuning produces coverage-displacing consistency in production (Pitfall 5) | LOW | Reduce the consistency weight and confirm via `SolutionManager.explain()` that coverage/break constraints regain their intended precedence — no schema or model change required, purely a constraint-config change |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| 1. Two coupled planning variables (thrashing, infeasible CH) | Shift variable + coupling-mechanism phase | Trivial-fixture CH-feasibility test; extended `runPreSolveScoreDiagnostic` probe on the shift variable |
| 2. Shift library stricter than demand → infeasible desk | Shift library phase (before mode switch is operator-facing) | Coverage validator runs on every library edit and mode switch; named, specific `ErrorDetail`s, not a generic infeasibility message |
| 3. Untrustworthy benchmark (Phase 12 repeat) | Whichever phase claims a quality/coverage/consistency improvement (shift model phase; consistency constraint phase) | Seeded 5-seed, step-count-terminated A/B, median + full spread, must-pass threshold set and recorded before the run, realistic over-allocation scenario included as first-class, not an appendix |
| 4. Dual-mode coexistence gaps | Per-desk mode-switch phase (constraint enumeration starts as soon as shift constraints exist) | Mixed-mode integration test; explicit per-constraint mode-behavior table; documented behavior for mode-switch on a desk with an accepted schedule |
| 5. Soft-constraint weight interactions | Consistency-constraint phase | `SolutionManager.explain()` constraint-match ranking check against the existing hierarchy, at the chosen weight, before shipping the default |
| 6. Model-vs-view gap (I-1 repeat) | Every phase that writes shift/usual-shift data, each with its own display verification as a must-have | End-to-end trace: solver assigns → roster shows it → export shows it → drift report shows it, read from current source, not self-reported |
| 7. Guarantee holding on one write path only (I-2 repeat) | The phase(s) building Usual Shift population paths and solver wiring | Enumerated write-path table with a stated invariant per path; grep-verified choke-point routing |
| 8. Migration risk on live single-environment deploy | Every phase adding schema (shift library, usual shift, mode flag) | Migration reviewed as additive-only with safe defaults for existing rows; Flyway version confirmed against latest applied version before authoring |
| 9. Timefold 1.16.0-specific traps (union selector, undo correctness, corruption detection) | The phase introducing the coupled shift move/value-range mechanism | Explicit `unionMoveSelector` re-declaration; full-suite (not solver-package-scoped) validation on any `solverConfig.xml` change; dedicated `FULL_ASSERT` test before the coupling is considered correct |

## Sources

- `/Users/pete/IdeaProjects/wfm-service/.planning/PROJECT.md` — Known issues, Key Decisions, Constraints, v1.3 milestone framing
- `/Users/pete/IdeaProjects/wfm-service/.planning/STATE.md` — Accumulated Context, Decisions, Blockers/Concerns (Phase 12 entries)
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — all 18 constraints, shared grouping-node rationale, `minimumStaffing`/`bulkUnderallocationSoft` documentation
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java` — documented no-op, 6-pass pipeline removal rationale
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/service/SolverService.java` — `runPreSolveValidation` (12 checks), `runPreSolveScoreDiagnostic` (score-corruption/incremental-scoring detection hook)
- `/Users/pete/IdeaProjects/wfm-service/.planning/milestones/v1.2-MILESTONE-AUDIT.md` — I-1/I-2/I-3/I-4/NEW-1 findings and generalized lessons
- `/Users/pete/IdeaProjects/wfm-service/.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md` — benchmark methodology, threshold assessment, operator verdict
- `/Users/pete/IdeaProjects/wfm-service/.planning/milestones/v1.2-phases/12-atomic-shift-move/12-VERIFICATION.md` — withdrawal record, carried-forward lessons, solverConfig.xml detection gap
- `/Users/pete/IdeaProjects/wfm-service/.planning/milestones/v1.2-phases/12-atomic-shift-move/12-RESEARCH.md` — verified Timefold 1.16.0 API details (`EnvironmentMode` enum, `AbstractMove.doMoveOnGenuineVariables`, `unionMoveSelector` auto-default behavior)
- `/Users/pete/IdeaProjects/wfm-service/.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md` — displacement-move follow-up scoping, undo-correctness risk note

---
*Pitfalls research for: shift-level scheduling addition to a live Timefold slot-based WFM solver*
*Researched: 2026-08-25*
