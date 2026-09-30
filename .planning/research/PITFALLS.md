# Pitfalls Research

**Domain:** Adding overnight-shift / business-date semantics to an existing, live, calendar-day-anchored
Spring Boot + Timefold Solver 1.16.0 workforce-scheduling service
**Researched:** 2026-09-30
**Confidence:** HIGH — every pitfall below is grounded in a file read from this codebase or in the
cancelled v1.4 attempt's own research/context artifacts (`rescue/phase-18-unwind-20260930`), not
generic scheduling-domain lore.

## Context this research assumes

v1.4 ran this exact milestone scope, executed four of five Phase 18 plans cleanly, and was cancelled
2026-09-30 — not on defects, but because its BDAY-06 regression fixture design was unsound (see
Pitfall 3, the load-bearing one). v1.5 restarts with the same settled architecture (desk-level day
start, hard minimum rest, guards before re-anchoring) and a different regression-proof shape. The
phase names below (`Foundation & Guards`, `Re-anchoring`, `Overnight Templates`, `Solver Correctness`,
`Minimum Rest`) are v1.4's phase goals, carried forward as the intended shape per PROJECT.md — the
roadmapper may renumber but should not reorder without re-litigating the sequencing rationale in
Pitfall 9.

---

## Critical Pitfalls

### Pitfall 1: End-to-end `LocalTime` comparisons silently invert at midnight

**What goes wrong:**
`LocalTime` has no `24:00` — its max is `23:59:59.999999999`. This codebase's existing convention
stores an end-of-day time as `00:00`, the *smallest* value the type can hold. Any comparison that
treats an END time as an ordinary point value inverts silently: `LocalTime.MIDNIGHT.isAfter(LocalTime.of(23,0))`
is `false`. A "find the latest end time" reduction (`if (candidate.isAfter(latest)) latest = candidate`)
never updates when the true latest genuinely ends at midnight — the bug produces a plausible, too-early
answer, not a crash. This is a *live, verified defect independent of overnight shifts*:
`ScheduleExportService.shiftCode` line 366 had exactly this bug (`ad.endTime().isAfter(latest)`) before
it was fixed in `5ddd8dc` (already shipped, per git status). Overnight shifts multiply the number of
places an end time can legitimately be `00:00`, so every remaining end-to-end comparison becomes a
live landmine rather than a theoretical one.

Related failure shapes in the same family, all confirmed present in this codebase's own
`DayWindow` javadoc and the guard-extension research:
- **Duration as `end - start` going negative**: `Duration.between(15:00, 00:00)` is `-900` minutes raw.
- **Sorting a collection of times that wrap**: sorting `[23:00, 00:30, 06:00]` as raw `LocalTime`
  puts `00:30` first — correct only if you meant calendar order, wrong if you meant "order within
  this shift's span."
- **`LocalTime.plusMinutes` wrapping instead of throwing**: `23:00.plusMinutes(120)` silently becomes
  `01:00`, an *earlier* clock time than its input, which then fails every subsequent ordering check
  rather than signalling anything.

**Why it happens:**
The bug is invisible at the call site. `a.isAfter(b)` type-checks, compiles, and is correct for every
non-midnight-adjacent pair encountered during manual testing — it only misbehaves for the exact
boundary case a manual tester is least likely to construct by hand.

**How to avoid:**
Route every scheduling-time comparison through `DayWindow`'s position-aware functions
(`startsBefore`, `overlaps`, `contains`, `endMinute`, `plusWithinDay`) rather than raw `LocalTime`
methods. This codebase already has 15 files / ~88 call sites doing exactly this by design (the
"one implementation, not two that can drift" rule) — the discipline exists; the gap is in the
methods that were never audited because they predate the convention (comparison operators
`.isAfter`/`.isBefore`/`.compareTo`, as opposed to the four arithmetic forms the existing guard
already covers).

**Warning signs:**
`MidnightTimeArithmeticGuardTest` (extended per Pitfall 1's guard, below) fails on a NEW,
unallowlisted comparison line. Functionally: a roster cell, a coverage total, or a "latest end
time" reduction that quietly under-reports for exactly the agent-days ending at midnight, and only
those — a bug that reproduces on a specific subset of dates and never on others is this bug family.

**Phase to address:**
**Foundation & Guards** (extend the existing `MidnightTimeArithmeticGuardTest` and
`midnight-time-arithmetic.md` to the three comparison-operator tokens, using a name-based receiver
heuristic — `*Time`, `slotStart`/`slotEnd`, `envelope*`, `band*`, `break*Start` — to avoid a
100+-entry allowlist from unrelated `LocalDate`/`BigDecimal` noise). This must land and pass
**before** any interval-arithmetic re-anchoring, because it is the thing standing between that
re-anchoring and a silently wrong schedule. The prior attempt hand-verified 11 matching lines in
the live tree (9 legitimate start-to-start comparisons to allowlist, 1 genuine end-to-end bug
already fixed, 1 two-clauses-one-line edge case) — re-verify the count fresh rather than trusting
the stale line numbers, since files have moved since 2026-09-29.

---

### Pitfall 2: A constraint join on the wrong date column is a silent non-join, not an error

**What goes wrong:**
Timefold `ConstraintStream` joins that key on `timeslot.getDate()` (calendar date) instead of
`timeslot.getBusinessDate()` do not throw when a post-midnight timeslot's business date differs
from its calendar date. They simply produce **zero matching tuples** for that timeslot on that
join. A join that matches nothing is scored identically to a join whose constraint is genuinely
satisfied — Timefold has no "this join found nothing, is that expected?" signal. Twelve joins in
`ScheduleConstraintProvider` currently key on `timeslot.getDate()` (verified at lines 71, 154, 201,
489, 575, 645, 712, 883, 939, 995, 1049, 1160 in the codebase read this session). Each one that
should key on business date but doesn't will, for exactly the overnight-shift agent-days, silently
stop enforcing whatever it enforces — contracted-hours accounting, day-off blocking, usual-shift
consistency, preference matching, envelope/band capacity — while the solver reports a clean score
and an operator sees a schedule that looks solved and isn't.

This is not a hypothetical: **it is already happening today, independent of overnight shifts.**
SLOT mode already lets an overnight stretch exist (nothing stops an operator constructing a
21:00–06:00 pattern from slots today), and because `contractedHoursOver`/`contractedHoursUnder`
join `AgentDayConfig` on `timeslot.getDate()`, a Sunday 21:00–Monday 06:00 stretch counts 3h
against Sunday and 6h against Monday, registering **both** calendar days as under-allocated against
an 8h contract. The bug is live and diagnosed; fixing it is SOLV-04's job, and it is a preview of
exactly the class of defect the other 11 joins can reintroduce if migrated carelessly.

**Why it happens:**
The join key that "quietly stops matching" and the join key that "matches correctly" produce
identical code shapes — `equal(a -> a.getTimeslot().getDate(), ...)` compiles whether `getDate()`
is right or wrong for that specific constraint's semantics. Nothing about a green test suite proves
the joins fire on the data that actually exercises the boundary, because until this milestone no
fixture *has* an overnight timeslot to exercise it with.

**How to avoid:**
1. Move all 12 (or however many the fresh grep finds) `getDate()`-keyed joins in
   `ScheduleConstraintProvider` to `getBusinessDate()` in one deliberate, reviewed pass — not
   incrementally, because a mixed state (some joins on calendar date, some on business date) is
   worse than either uniform state: it makes results depend on which constraint happens to fire
   first.
2. **Build a structural guard that fails if any constraint joins on `.getDate()` where
   `.getBusinessDate()` is intended** — a source-scan guard in the same family as
   `MidnightTimeArithmeticGuardTest`, keyed on `.getDate()`/`.getDayOfWeek()`/`.plusDays(` tokens
   within `ScheduleConstraintProvider`, set-equality both directions (a new bare `.getDate()` call
   fails; a stale allowlist entry for a join that's since been fixed also fails). This is exactly
   what the prior attempt's SOLV-02 requirement names, and it is what makes "changing a join key is
   dangerous" a caught mistake instead of a silent one.
3. Pair every join-migration with a **match-count assertion**, not just a score assertion — see
   Pitfall 3 for why counts catch what scores don't.
4. **Non-vacuity assertion**: for any test proving a constraint now correctly spans midnight, first
   assert the constraint fires at all (`ConstraintVerifier...penalizesBy(nonZero)` on a
   deliberately-broken fixture) before asserting it's silent on a compliant one. A guard that can
   only prove "no penalty" can never distinguish "correctly compliant" from "never matched
   anything" — this project already has this exact pattern from Phase 15's coupling proof (a
   walker that shares no code path with the constraint it checks, plus a non-vacuity assertion "so
   a green pass cannot be empty").
5. **Mutation testing as a targeted check, not a blanket policy**: hand-mutate one join's key from
   `getBusinessDate()` back to `getDate()` and confirm the guard (or, short of a guard, the
   regression fixture) goes red. If nothing catches the mutation, the safety net has a hole at
   exactly the property it claims to protect.

**Warning signs:**
A schedule solves to a suspiciously good score for a desk with overnight shifts (too few
violations, because whole constraint categories stopped matching for the overnight population). A
per-constraint match-count that used to be proportional to headcount drops for no headcount-related
reason after a date-model change. An operator report of "the overnight agents' hours look wrong but
the solver said 0 hard violations."

**Phase to address:**
**Solver Business-Date Correctness.** This phase's own success criteria should include both "joins
moved" and "a guard proves no join was missed" as two separate, independently-checkable
deliverables — do not treat the guard as a nice-to-have appended after the joins move; build it
first if possible, exactly as the Foundation phase builds the arithmetic guard before the
re-anchoring that needs it.

---

### Pitfall 3: A regression baseline sourced from live data that barely contains the property under test — the mistake that killed v1.4

**What goes wrong:**
v1.4's BDAY-06 built its "prove nothing changed" regression fixture by capturing four **live** desks
(Saferide, Stubhub EN, Vinted, Phil-US) and asserting byte-identical output before and after the
re-anchoring. This failed the milestone in two directions simultaneously, and both matter for how
v1.5 must build its replacement:

- **Over-sensitive.** Vinted alone has 287 agents and a full demand curve. Any scoring change
  *anywhere in the solver* — including changes unrelated to the milestone — shifts the golden bytes.
  A byte-diff against a 287-agent solve gives zero diagnostic signal about *what* broke: the failure
  message is "the file differs," not "constraint X's match count moved from 4 to 7 for reason Y."
- **Under-powered on the exact property it exists to protect.** Three of the four live desks never
  cross midnight. No captured desk has a 23:00–00:00 slot. No captured break band touches an
  envelope edge. A baseline meant to catch a midnight-arithmetic regression rested on data that
  barely contains midnight — *because this milestone is what introduces overnight shifts in the
  first place.* The baseline could pass byte-identical while the underlying re-anchoring silently
  broke midnight handling, simply because none of the four fixtures ever exercised the boundary.

These two failures are not independent symptoms of one bad choice — they are the **same** bad
choice viewed from two sides. Live production data is shaped by what desks happen to exist today,
not by what a property-under-test needs to exercise. A live desk is simultaneously "has too much
irrelevant surface area to diff meaningfully" and "has too little of the relevant surface area to
prove anything," because "relevant" and "irrelevant" here are defined by the milestone, not by the
data's own history.

**Why it happens:**
"Use real data, it's more realistic" is an intuitively appealing default, and it superficially
satisfies "prove nothing changed for existing desks" (Pitfall 6 asks for exactly that proof). The
trap is conflating two different jobs that look similar: (a) proving the *general* population is
undisturbed, and (b) proving the *specific new capability's boundary conditions* are handled
correctly. Live data is a reasonable tool for (a) and a poor one for (b), because production data is
never guaranteed to contain the boundary cases a not-yet-built feature will introduce.

**How to avoid — the concrete right shape:**

**Use both, but assign each to the job it is suited for, and do not let either stand in for the
other:**

1. **Constructed minimal scenarios, chosen by the property under test, own correctness.** Before any
   re-anchoring code exists, enumerate the boundary cases that *will* exist once overnight shifts
   ship — not the cases that happen to exist in dev today:
   - a slot ending exactly `00:00` (today's end-of-day convention)
   - a `23:00–00:00` slot (the smallest possible pre-midnight slot)
   - an envelope flush to end-of-day (start-to-midnight shift)
   - a shift that starts before midnight and ends after it (the actual new capability)
   - a break band positioned so it touches an envelope edge
   - a day-off/PTO marking on the business day a shift starts vs. the calendar day it ends
   - an agent whose contracted-hours row is for the starting weekday only
   Build each by hand or via a small deterministic factory, run it through the constraint provider
   (or the interval arithmetic under test) directly, and assert on the *specific, named property*
   each case exists to prove — not a whole-object byte comparison. A failure then reads "the
   `23:00–00:00` boundary case's shift-envelope-compliance match count changed from 0 to 1," which is
   diagnosable in isolation. This is a form of **characterisation-by-construction**: the test names
   the property (e.g. "an overnight shift consumes only the starting weekday's contracted hours"),
   and the fixture is the smallest object graph that could falsify it.
   - Include a **class-load boundary-case validator** (precedent already in this codebase:
     `LiveShapeShiftDeskFixture.validateTemplateSpecs()`) that asserts the constructed scenario set
     actually contains each named boundary case, so a scenario silently dropped during a later edit
     fails the build at test-class-load time rather than passing vacuously. This is the direct fix
     for "a fixture built to catch X can quietly stop containing X."
   - Where a property is naturally universal rather than example-based (e.g. "duration is always
     computed the same way regardless of which side of midnight the interval falls on"), prefer a
     **property-based invariant** (generate many synthetic start/end/day-start combinations, assert
     the invariant holds for all of them) over another hand-picked example — this catches the
     off-by-one that a human enumerating examples forgets to think of.

2. **Live data becomes a small drift guard, not the proof.** One (or a very small number of) live
   desk(s) — v1.5 explicitly proposes "Phil-US (48 agents) is a single small drift guard" — captured
   and byte-compared, but scoped down to be diagnosable: capture **per-constraint match counts and
   scores** (not just a summed byte blob), so a failure names which constraint moved, matching the
   diagnostic-signal requirement. Keep the desk small deliberately (48 agents, not 287) so a
   byte-diff failure is small enough to read. This desk's job is "did anything *unintended* move for
   a population that resembles reality," which is a different question from "does the boundary case
   work," and it should never be the only thing answering the boundary-case question.

3. **Get diagnostic signal from a failing baseline by decomposing what you snapshot, not just
   comparing whole-object bytes.** The single most actionable lesson available from the prior
   attempt's own research: snapshot **match counts per constraint, not only the summed score**.
   Two different wrongnesses can sum to the same score (a constraint that over-penalizes by 3 and
   one that under-penalizes by 3 cancel in a total); per-constraint match counts are far harder to
   coincidentally preserve when something actually changed. A golden-file failure that names
   "`shiftEnvelopeCompliance`: count 4→7, hard score −4→−7" is diagnosable; a failure that says "the
   file differs" is not.

**Warning signs a baseline is over-sensitive:** the failure message from a broken baseline never
names a specific mechanism, only "output differs"; the fixture's construction has no `given(...)`-
style enumeration of what's in it, just "captured from desk X on date Y"; touching an unrelated part
of the solver (e.g. a fairness-weight tweak) breaks this test as often as touching the thing it's
meant to guard.

**Warning signs a baseline is under-powered:** nobody can point to which line in the fixture data
exercises the boundary case the milestone is about; the fixture was captured before the capability
existed (definitionally true here — captured live desks predate overnight-shift support, so none of
them contain a real overnight shift to test against); a class-load or setup-time check that the
fixture *contains* the boundary cases either doesn't exist or was never run.

**Phase to address:**
**Foundation & Guards**, before **Re-anchoring** begins. This is the milestone's own stated
non-negotiable sequencing ("guard tests and regression fixture must exist before the re-anchoring —
there is nothing to compare against otherwise"), and it is the single item this research answer
treats as highest-priority per the downstream consumer's quality gate. Concretely: constructed
boundary-case scenarios and their class-load validator ship as part of Foundation & Guards; the
small live-desk drift guard ships alongside it but is explicitly documented as secondary evidence,
not the primary proof.

---

### Pitfall 4: "Byte-identical" is interpreted as "reproduce a solve," which is impossible

**What goes wrong:**
Timefold solves are time-boxed and non-deterministic (this codebase's own solver rarely reaches
hard 0 even on desks it should, per `CONCERNS.md`'s documented solver-quality plateau). A
requirement worded as "every existing desk produces a byte-identical schedule before and after the
re-anchoring" cannot mean "run the solver twice and get the same solved schedule" — that property
does not exist in this system today, independent of this milestone. Building a regression fixture
that actually tries to do that will flake constantly and teach the team to ignore its failures.

**Why it happens:**
The roadmap-level wording ("byte-identical schedule") is natural language shorthand for a much more
specific technical requirement, and the gap between the two is easy to miss until someone actually
tries to implement it.

**How to avoid:**
Reinterpret "byte-identical" as applying to a **deterministic artefact derived from a fixed input**,
not a search result: build a `Schedule` object graph by hand (or via a deterministic assignment rule
— e.g. sorted-ID round-robin — plus a validator proving the assignment covers the needed boundary
cases), score it via a bare `SolutionManager`/`SolverFactory` pair with **zero search/termination
configured**, and byte-compare the resulting per-constraint match-count-and-score decomposition.
This preserves the literal "byte-identical" property against the right artefact. This codebase
already has the exact technique in `ShiftDeskEndToEndRegressionTest.hardPenaltiesByConstraint`
(hand-built solution, scored via `SolutionManager`, no solve) — it is proven, not novel.

**Warning signs:** a regression test that calls `solver.solve(...)` (actual search) rather than
`solutionManager.explain(...)` (scoring only) as its comparison mechanism; a "regression" test that
is flaky (fails intermittently on identical inputs) — flakiness in a fixture meant to prove
determinism is itself the defect, not noise to retry past.

**Phase to address:**
**Foundation & Guards**, same phase as Pitfall 3 — it is a design detail of the same fixture, not a
separate piece of work.

---

### Pitfall 5: `DayWindow` re-anchoring as 88 independent edits instead of one coordinated change

**What goes wrong:**
`DayWindow` has 15 files / roughly 88 call sites (per the earlier scoping investigation; re-verify
the exact count fresh, since it will have drifted since 2026-09-28) all routing through the same
utility by design — the heaviest concentrations are `ScheduleConstraintProvider` (~21),
`ShiftLibraryGenerationService` (~15), and `TimeslotGeneratorService` (~12). The temptation, given
that many sites, is to treat the migration as "touch each call site, verify locally, move on" —
but `DayWindow`'s entire value proposition is that re-anchoring happens **once, in the utility
itself**, and every caller inherits the new semantics for free. If even one caller has drifted from
the utility (a local re-implementation, a raw `LocalTime` comparison that slipped past the guard, an
inlined `00:00`-means-midnight assumption), that one caller silently keeps the old semantics after
everything else moves, producing a schedule that is *partially* re-anchored — the worst possible
state, because it looks uniformly changed but isn't.

**Why it happens:**
88 call sites is large enough that a piecemeal review feels safer than trusting the abstraction, but
piecemeal review is exactly the discipline the utility exists to replace, and piecemeal review is
where a missed site hides.

**How to avoid:** Treat re-anchoring as two separable proofs, not one edit: (1) the guard from
Pitfall 1 proves every *comparison and arithmetic* site routes through `DayWindow` before this phase
starts, so there is no drifted caller to miss; (2) the change itself is then confined to
`DayWindow`'s own implementation (the position-aware minute functions gain a day-start parameter/
offset instead of assuming `00:00`), and every caller is provably unchanged by construction because
it never had its own copy of the logic. This is why the milestone's own sequencing insists the guard
ships *before* re-anchoring — the guard is what turns "88 judgement calls" into "one call, safely."

**Warning signs:** a call site that computes a duration, an overlap, or a comparison using raw
`LocalTime` methods survives the guard extension (a stale allowlist entry, or the guard's heuristic
missing a real hit); after re-anchoring, one desk's schedule looks correct while a structurally
similar desk's doesn't, with no code-level difference between the two paths that should explain it.

**Phase to address:** **Re-anchoring**, with its precondition (the guard test passing clean, zero
new offences) verified explicitly as the phase's first gate, not assumed from the prior phase's
sign-off.

---

### Pitfall 6: Proving "nothing changed for existing desks" by assertion instead of by proof

**What goes wrong:** A migration or re-anchoring PR that claims "no behaviour change for desks that
haven't opted in" is easy to *assert* in a PR description and hard to *actually verify* without a
mechanism that would fail if the claim were false. This project has direct prior experience with the
gap between the two: v1.3's mode-switching work explicitly called out proving "no production solver
file changed" **structurally, not asserted**, as a Key Decision worth recording — and the milestone
`business_date` write-path work (Pitfall 2) is the same shape of claim (a wrong value there is a
silent non-join, not an error, exactly the failure class that "trust me, I checked" cannot catch).

**Why it happens:** Structural proof takes more upfront work than an assertion, and the assertion
"passes" every code review because reviewers cannot practically re-derive the full call graph by
eye across 88+ call sites and 12+ constraint joins.

**How to avoid — concrete techniques already proven in this codebase, reuse them rather than
inventing new ones:**
- **Default-and-gate**: a new column defaults to the value that reproduces today's behaviour exactly
  (`desk.day_start DEFAULT '00:00'`), and a validation gate refuses any value that would exercise the
  new code path until the phase that's actually ready for it lands (v1.4's own D-18: gate the
  accepted `day_start` value to `00:00` only in the foundation phase, so a value can be stored and
  displayed without anything yet honouring a non-default one). A desk that never touches the new
  config is provably running the old code path, by construction, not by promise.
- **Write-path allowlist guard**: for any new derived column (`business_date`), a structural,
  source-scanning test asserting the *exact set* of classes/methods permitted to write it — set
  equality in both directions, so an unexpected new writer fails loudly and a stale, no-longer-true
  allowlist entry also fails. This codebase's `UsualShiftWritePathGuardTest` /
  `ushf-05-write-paths.md` is the exact, already-proven shape to copy.
- **Migration/entity reconciliation**: any new column added to a table already tracked by
  `MigrationEntityConsistencyTest.DECLARED_TABLES` gets its SQL type checked against its JPA mapping
  automatically — a `TIME` column that a Java field maps as something incompatible fails at test
  time, not at ECS boot under `ddl-auto=validate` (this exact failure mode shipped once already in
  this project, at V39, and cost a hotfix).
- **Backfill-in-one-migration, no observable nullable window**: for `timeslot.business_date`, add the
  column nullable, backfill `= date` in the same migration, then set `NOT NULL` — all in one Flyway
  script, so no reader (including a concurrently-deploying old instance mid-rollout) ever observes an
  unset row. A separate "add nullable" migration followed by a later "backfill and constrain"
  migration creates a window where in-flight code from either version could read a null it doesn't
  expect.

**Warning signs:** a PR description that says "verified manually" or "spot-checked a few desks" as
its only evidence for a no-change claim; a new nullable column with no committed plan for when/how it
becomes NOT NULL; a derived field with more than one code path plausibly able to write it and no test
enumerating that set.

**Phase to address:** **Foundation & Guards** for the write-path guard and the migration shape;
**Re-anchoring** for the default-and-gate proof on `day_start`'s activation; both phases should treat
"structural proof, not assertion" as a phase-gate criterion, matching this project's own audited
strength ("self-enforcing structural guards... so four classes of regression now fail the suite
instead of depending on review attention").

---

### Pitfall 7: Deploying mid-migration kills in-flight solver work, and migration ordering ignores it

**What goes wrong:** Solver state is heap-only (`InMemoryScheduleStore`, per `CONCERNS.md`'s
documented fragility) — an ECS task swap during deployment silently drops any RUNNING solve with no
persisted recovery. This project already has an established mitigation discipline (`gh run cancel`
during the ~15-minute window a deploy lands) but that discipline assumes a human is watching. A
migration/deploy sequence for this milestone that lands a schema change (`V53`+) at the same moment
an operator might reasonably be mid-solve on a live desk compounds two risks: the solve is lost (an
availability problem, already known), *and* if the schema change and the code reading/writing it
aren't deployed atomically, an in-flight request from the old code against the new schema (or vice
versa) can hit a column that doesn't mean what the running code thinks it means yet.

**Why it happens:** Flyway migrations run automatically at application boot as part of the same
deploy that swaps the ECS task, so "the migration landed" and "the new code is live" are
coupled tightly enough to feel atomic — but a rolling or blue/green swap (if this environment
ever moves to one) or a long-poll client mid-request is exactly the gap where they aren't.

**How to avoid:** For this milestone specifically: (1) keep every schema change backward-compatible
with the *previous* code version for at least one deploy cycle — the nullable-then-backfill-then-
NOT-NULL-in-one-migration shape (Pitfall 6) already achieves this for `business_date` because old
code never reads the new column; (2) treat `day_start`'s activation gate (Pitfall 6) as also solving
this problem for `Re-anchoring` — because nothing honours a non-default `day_start` until that phase
explicitly un-gates it, a deploy landing partway through cannot produce a half-re-anchored live
schedule; (3) explicitly do not schedule a migration-bearing deploy during a known live solve window
on Phil-US or any other desk with an accepted-schedule dependency, and reuse the existing `gh run
cancel` discipline as the fallback if one is caught mid-flight regardless.

**Warning signs:** a deploy landing while `SolverService` reports a RUNNING solve for any desk (check
before merging a migration-bearing PR to the deploying branch); a migration that is not safely
readable by the *previous* application version.

**Phase to address:** **Foundation & Guards** (the migration shape) and as an operational discipline
applied to every phase's deploy in this milestone, not a one-time fix — restate it explicitly in each
phase's own plan given how easy it is to forget under delivery pressure.

---

### Pitfall 8: A business-day anchor quietly bakes in a fixed 24-hour day, painting DST into a corner

**What goes wrong:** Timezones are explicitly out of scope for this milestone, but the *data model*
chosen now will outlive that scoping decision, and several natural implementation choices assume a
business day is always exactly 1440 minutes:
- `DayWindow.MINUTES_PER_DAY = 1440` as a hardcoded constant, used for every end-position minute
  calculation. A DST transition produces a 23- or 25-hour calendar day; if "business day" is ever
  defined as "calendar date the day-start falls on, plus a fixed 1440-minute span," a DST-adjacent
  overnight shift computes the wrong end instant once timezones are introduced.
- A `business_date` column typed as `DATE` (no timezone, no instant) is fine as long as the whole
  system reasons in a single, implicit timezone (true today) — but a future timezone-aware model
  will need to know *which* timezone a given business date's midnight boundary was computed in, and
  a bare `DATE` column carries no such information. Retrofitting that later means every existing row
  is ambiguous.
- Contracted-hours-per-weekday (`agent_day_hours`, keyed by `DayOfWeek`) assumes each business day
  maps to exactly one weekday with no duration ambiguity — correct under a fixed-offset, no-DST
  model, silently wrong if a future DST-spanning business day needs to be "23 hours of Sunday" for
  payroll purposes.

**Why it happens:** "Timezones are out of scope" is scoped correctly for *this* milestone's
deliverables, but scoping the requirement out is not the same as scoping the *assumption* out of the
data model — a hardcoded 1440 and a bare `DATE` column are both silent, load-bearing assumptions that
nothing in this milestone's success criteria will surface as wrong, because nothing in this
milestone's test data crosses a DST boundary or a timezone.

**How to avoid — without doing timezone work now:**
- Keep `MINUTES_PER_DAY` as a `DayWindow`-internal constant, not something callers hardcode
  independently — if/when a DST-aware day length is needed, the change is confined to one class
  (exactly the reason `DayWindow` exists).
- Document, in `DayWindow`'s own javadoc (which already carries this kind of "why," per its existing
  content), that the class assumes a fixed-length day and that a timezone-aware milestone will need
  to revisit every `MINUTES_PER_DAY` use — a one-paragraph note costs nothing now and saves a
  rediscovery cost later.
- Do not let `business_date` acquire any timezone-flavoured meaning by accident (e.g. do not store it
  as a `TIMESTAMP` "for future flexibility" — that invites someone to start reasoning about instants
  before the model actually supports it, which is worse than a plain `DATE` that makes the limitation
  visible).
- When `agent_day_hours`' `DayOfWeek` keying is touched by this milestone (it isn't expected to need
  a schema change per the settled decisions, but any code that computes "which weekday does this
  business day consume" should live in one place, not be re-derived ad hoc, for the same reason
  `DayWindow` centralises interval math) — so a future DST fix has one call site to change, not many.

**Warning signs:** a new call site computing `+ 24 * 60` or `1440` as a literal instead of via
`DayWindow.MINUTES_PER_DAY`; any new column storing a business-day boundary as an instant/timestamp
rather than a plain date, introduced "just in case," without a corresponding timezone field to make
that instant meaningful.

**Phase to address:** **Foundation & Guards** and **Re-anchoring** — this is a "don't paint the
corner" review checklist applied while those two phases' schema and `DayWindow` changes are being
designed, not a deliverable of its own. No code should be written to solve it now; the ask is narrower:
don't let the *current* model's shortcuts become load-bearing assumptions that a future timezone
milestone has to work around rather than build on.

---

### Pitfall 9: Minimum rest computed across the wrong boundary, or at the edge of the solved horizon

**What goes wrong:** Several distinct ways a hard minimum-rest constraint can be wrong, each with a
different symptom:
- **Wrong boundary**: computing rest between "the last slot of calendar day D" and "the first slot
  of calendar day D+1" instead of between an agent's actual consecutive *shifts* (which, with
  overnight shifts, may not align to calendar-day edges at all) reintroduces exactly the
  calendar-vs-business-date confusion Pitfall 2 describes, now inside a brand-new hard constraint
  that didn't exist before this milestone to have inherited an old bug from.
- **Horizon edges**: an agent's *first* shift in the solved window has no visible "previous shift" —
  if the constraint naively treats "no previous shift found" as "infinite rest, no violation," that's
  correct only if the agent genuinely didn't work immediately before the horizon starts. If they did
  (their last shift from the *previous* solved period, outside this window, ended a few hours before
  this window's first shift), the constraint is blind to a real violation because the data it would
  need doesn't exist in-window. Symmetrically, the *last* shift of the horizon has no visible "next
  shift" to check rest against.
- **Making infeasibility unsolvable rather than merely penalised**: a HARD constraint (correctly
  settled at scoping — "enabling overnight shifts without it would create a way to produce illegal
  back-to-back rosters scoring `0hard`") must still leave the solver *a way out*. If minimum rest is
  wired as an unconditional hard constraint with no pre-solve feasibility check, a desk whose shift
  library and staffing demand structurally cannot avoid a rest violation for some agent (e.g. two
  templates whose only legal pairing violates the configured rest period, on a desk where that
  agent has no other eligible shift) makes the *entire solve* infeasible, not just that one
  agent-day — the solver has no gradient toward a "less bad" schedule when a hard constraint has no
  satisfiable assignment anywhere in the search space, and every other desk requirement inherits that
  infeasibility.

**Why it happens:** Rest is naturally described "between consecutive shifts," but a solver-friendly
implementation groups by some other key (calendar date, agent-day) for performance or simplicity, and
that grouping silently redefines "consecutive" to mean "adjacent in the grouping," which is not the
same thing once shifts stop aligning to calendar-day boundaries.

**How to avoid:**
- Define "consecutive shifts" explicitly as ordered by actual start instant (business-date-and-time),
  not by calendar-date bucket — reuse whatever ordering the business-date joins (Pitfall 2) already
  establish rather than inventing a second ordering.
- Treat horizon-edge agent-days as **known-incomplete data, not known-clean data**: either fetch the
  agent's actual last shift from immediately before the horizon (if the data model can answer that
  cheaply — a single lookback query per agent, bounded by the max configured rest period) or
  explicitly document and test the accepted blind spot (no visibility before the horizon start /
  after the horizon end) as a stated limitation rather than an implicit, undiscovered one. Given this
  milestone's settled decision that minimum rest is HARD, the horizon-edge blind spot is exactly the
  kind of "looks like 0 hard violations, isn't actually 0" gap this entire milestone exists to close
  elsewhere — don't reintroduce it in the constraint meant to prevent it.
- Build the **pre-solve refusal** (already scoped: REST-03, "refused pre-solve where structurally
  unavoidable, naming the agent and the two shifts") as a genuinely separate mechanism from the
  in-solve hard constraint, not a hoped-for side effect of it — a hard constraint with no
  satisfiable assignment produces a solver that runs to its time limit and returns garbage, not a
  fast, legible refusal. This project's own shift-mode work already establishes the precedent (the
  ENVL-07 seat-supply gate refuses shift-mode solves that cannot succeed *before* running, naming the
  specific shortfall) — REST-03 is the same discipline applied to a different structural
  infeasibility.
- Explicitly test: a desk that sets **no** minimum rest solves identically to today (REST-04) — this
  is the same default-and-gate discipline as Pitfall 6, applied to a new hard constraint rather than
  a new column.

**Warning signs:** a solve that used to return a schedule (even an imperfect one) now returns
"infeasible" or times out unproductively after minimum rest ships, with no refusal message naming
why; a rest violation between two shifts that straddle a calendar-date boundary is not flagged when a
functionally identical violation entirely within one calendar date is; an agent's first or last shift
in a solved period is never flagged for a rest violation regardless of what's configured, which is
the horizon-edge blind spot manifesting as "suspiciously never fires at the edges."

**Phase to address:** **Minimum Rest** — separable from the business-date phases as the milestone
scoping correctly notes ("REST needs BDAY/OVNT/SOLV code" is false; it's sequenced for delivery
order, not a technical dependency), but its own internal design must still get the business-date
ordering right independently, since it is a brand-new constraint with no legacy calendar-date
behaviour to be backward-compatible with — there's no excuse to get this one wrong from day one.

---

### Pitfall 10: Overnight shift rendering in a date-column grid and its Excel counterpart

**What goes wrong:** This codebase's roster export (`ScheduleExportService.writeRoster`) and its UI
equivalent are structured **one row per agent, one column per calendar date** — a grid model that
assumes every displayable unit belongs to exactly one date column. An overnight shift's true span
crosses two calendar-date columns. Two distinct wrong renderings are both plausible without careful
design:
- **Split into two fragments** — the shift shows as a partial block in Sunday's column and another
  partial block in Monday's column, which is exactly the "two fragments" outcome the milestone's own
  success criteria explicitly reject (OVNT-06: "one continuous block, never two fragments"), and which
  also double-books the visual space, potentially colliding with whatever else is scheduled in
  Monday's early hours.
- **Disappears from the end-date column entirely** — if the renderer only asks "what does date D's
  cell show" by checking assignments whose calendar date equals D, an overnight shift assigned to
  Sunday (business date) with assignments carrying calendar dates of both Sunday and Monday either
  needs Monday's cell to show *something* (a continuation marker, not a second full block) or risks
  looking like the agent has an unexplained gap on Monday morning if nothing renders there at all.

The Excel layout question is sharper than the UI one: a spreadsheet cell is inherently
single-valued and single-date-column-bound in a grid layout — there is no native "this visual block
spans two adjacent cells" primitive as cheap as it is in HTML/CSS (`grid-column: span 2`). POI can
merge cells, but a merged cell spanning Sunday's and Monday's columns for one agent's row
structurally conflicts with Monday's column also needing to show whatever *that* agent's Monday
shift is (a same-desk agent can easily work Sunday-night-into-Monday and then a distinct Monday-day
shift) — so a naive merge is only safe if the operator can never have two shifts whose visual spans
would overlap in the same column, which is not something this system enforces today.

**Why it happens:** The grid's calendar-date-column model was designed and has worked correctly for
years under the assumption every shift fits in one date; overnight shifts break that assumption at
the presentation layer even after the underlying data model (business date) correctly attributes the
shift to one row of truth.

**How to avoid:**
- Render every overnight shift **once**, under its **business date** (starting) column, as one
  continuous block — matching the underlying data model's own attribution (Pitfall 2's business-date
  joins) rather than re-deriving a display rule independently. Reuse the "one coverage
  validator/predicate, two callers" discipline this codebase already applies elsewhere (`ShiftBandPair
  .covers` is the single source of truth for both the solver and the report layer) — the roster
  renderer should ask "what shift does this business date's cell show," never re-derive fragment
  boundaries from raw calendar-date assignment rows.
- For the calendar date the shift *ends on* (the morning-after column), render an explicit, distinct
  visual treatment — not a second copy of the shift block, and not a blank cell that looks like an
  unexplained gap. A short continuation indicator (matching the existing legend-driven cell-code
  convention already used for day-off/other states in this exporter) is the natural fit, reusing the
  established `cell(...)`/legend pattern rather than inventing a new rendering path.
- For Excel specifically: **do not merge cells across the date-column boundary.** Keep the shift as a
  single-cell block under its business-date column (matching the UI decision above), and use the
  same continuation-indicator convention in the following day's cell, so the two renderers (UI, Excel)
  stay provably consistent rather than each solving the two-date problem differently. This sidesteps
  the overlapping-merge conflict entirely, at the cost of the block not being a single visually-merged
  span in the spreadsheet — an acceptable tradeoff given the operator-facing legend already
  communicates cell codes rather than relying on visual span alone.
- Verify against the concrete case this system already has a diagnosed defect in: `ScheduleExportService
  .shiftCode`'s "latest end time" reduction (Pitfall 1) is precisely the kind of code this rendering
  work depends on being correct — an overnight block's displayed end time must reflect the true end,
  routed through `DayWindow.endMinute`, not a raw comparison.

**Warning signs:** a captured screenshot or Excel export showing the same shift's hours appearing
twice; a roster cell for the morning-after date that is indistinguishable from "day off" when the
agent was in fact still working into that morning; an Excel column-merge that silently overwrites or
hides a second agent's legitimate Monday-morning shift in the same visual region.

**Phase to address:** **Overnight Shift Templates** (OVNT-02, OVNT-06 are explicitly this phase's
success criteria) — but design the rendering rule alongside Pitfall 2's business-date join work
(**Solver Business-Date Correctness**) rather than after it, since the renderer's correctness depends
on business date being the trustworthy attribution key by the time this phase's UI/export work
starts.

---

### Pitfall 11: An overnight shift's envelope validated against the wrong operating window, or not at all

**What goes wrong:** This project already has an acknowledged, deferred gap from v1.3: "a template's
envelope is never validated against the desk's operating window at save time" — a template that
cannot fit saves cleanly with an advisory that "literally reads 'It will still save.'" Overnight
shifts make this gap materially worse, not just carry it forward: an envelope that spans midnight has
*two* candidate operating-window boundaries to validate against (the desk's business day, which is
the correct one per the milestone's own settled decisions) and a naive validator might check the
envelope against the desk's calendar-day operating window instead — which, for an overnight shift, is
not even a coherent question, since the envelope by definition doesn't fit inside any single calendar
day.

**Why it happens:** "Operating window" and "business day" are easy to conflate when every existing
desk has operating windows that fit inside a single calendar day — the distinction only becomes
observable once a desk has both a non-trivial day-start and an envelope that crosses the resulting
business-day boundary.

**How to avoid:** Explicitly validate an overnight template's envelope against the desk's **business
day** (start-to-start-plus-1440-minutes from the desk's configured day-start), not its calendar-day
operating window — and treat this as the natural moment to also close the pre-existing v1.3 gap
(envelope-vs-operating-window validation at save time) for the overnight case specifically, since
OVNT-05 already requires "shift library validation refuses an overnight template whose envelope does
not fit inside its desk's business day" as an explicit success criterion. Do not let the old advisory-
only "it will still save" behavior persist for overnight templates — this milestone's own scope makes
the stronger validation a stated requirement, not an opportunistic bonus fix.

**Warning signs:** an overnight template saves without error against a desk whose day-start/operating-
window combination cannot actually contain it; the seat-supply/coverage gate (this project's existing
pattern for catching what save-time validation misses) is the only thing that eventually reports the
problem, at solve time, disconnected from the template edit that caused it — exactly the v1.3-era
failure mode this milestone should not reproduce.

**Phase to address:** **Overnight Shift Templates** (OVNT-05 names this explicitly).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Gate `day_start` to `00:00`-only in Foundation & Guards, land the column/API/UI unused | Ships the schema/API surface without needing the risky re-anchoring done first | A stored-but-inert config value if the gate is ever forgotten and not removed on schedule | Acceptable, and recommended (v1.4's own D-18) — but the gate removal must be a tracked, visible follow-up in the Re-anchoring phase, not an assumption |
| Keep `agent_day_hours` unchanged (no schema migration) because it's keyed by `DayOfWeek` and a Sunday-night shift "correctly" consumes Sunday's row | Zero migration risk for a well-understood table | If a future requirement needs sub-day granularity within a weekday (e.g. partial-day PTO interacting with an overnight shift), the DayOfWeek-only key has no room to represent it | Acceptable for this milestone's explicit scope; revisit only if a future requirement needs finer granularity |
| SLOT-mode's existing midnight-split bug (SOLV-04) fixed opportunistically as part of the business-date join migration rather than as its own isolated fix-first step | Avoids a redundant second pass over the same 12 joins | If the join migration and the SLOT-mode fix are conflated in one commit/test, a regression in either is harder to bisect | Only acceptable if the phase's tests separately assert SLOT-mode correctness as a named, independent success criterion (SOLV-04 already is one) — don't let it become an untested side effect |
| Defer Phil-US migration to a later milestone, keep the +3h offset hack live | Keeps this milestone desk-agnostic and avoids the highest-risk live-data proof until the rest of the capability is solid | The offset hack remains the only live desk anyone can point to as "proof it works with a real night-shift client," so the milestone's overnight-shift claims are unverified against real client data until the deferred migration lands | Acceptable exactly as scoped — deliberate, documented, and the smaller live-desk drift guard (Phil-US, 48 agents) still exercises the *arithmetic*, just not the real un-offset shift times |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|--------------|------------------|--------------------|
| Timefold `ConstraintStream` joins | Assuming a join that returns zero matches is equivalent to "constraint satisfied, no penalty" — it is, at the score level, but not at the correctness level | Pair every join-key change with a non-vacuity assertion on a deliberately-broken fixture and a per-constraint match-count check, not just a score check (Pitfall 2, Pitfall 3) |
| Flyway migrations against live ("dev is prod") data | Treating a nullable-column-then-later-NOT-NULL as two separate migrations, creating an observable unset window | One migration: add nullable, backfill, set NOT NULL, in that order, in one script (Pitfall 6) |
| `MigrationEntityConsistencyTest.DECLARED_TABLES` | Assuming a new table added to this guard is risk-free because "the types already exist in the compatibility map" | Verify the actual live schema for that table has no pre-existing, previously-undetected drift before trusting the guard's silence — adding a table can surface an old mismatch that was never caught because nothing was watching it (v1.4's own D-25 risk note) |
| ECS deploy vs. running solver | Assuming a schema-bearing deploy is safe to ship at any time because migrations "usually just work" | Check for a RUNNING solve before merging a migration-bearing deploy to the auto-deploying branch; keep the `gh run cancel` discipline live (Pitfall 7) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|-----------|------------|-----------------|
| Adding a lookback query per agent for minimum-rest horizon-edge checking (Pitfall 9) without bounding it | Query cost scales with lookback window × agent count on every solve setup | Bound the lookback to the maximum configured rest period across all desks, and batch it as one query, not N+1 per agent | Noticeable first on the largest live desk (Vinted, ~288 agents) once REST ships |
| Regression fixture golden files growing unboundedly as more boundary cases are added over time | Slow test-suite feedback, large diffs on legitimate changes | Keep the constructed-scenario fixtures small and named per boundary case (Pitfall 3) rather than one monolithic growing fixture; the live-desk drift guard stays deliberately small (48 agents, not 287) | Not yet a live concern at this milestone's scale, but worth naming so it isn't reintroduced by accident later |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| A live-desk fixture capture (for the small drift guard, Pitfall 3) accidentally writing a real agent name, email, or BambooHR `employeeNumber` into a committed test resource | Information disclosure of real tenant/employee data into a public or semi-public repo history, permanently (git history doesn't forget) | Anonymise at the capture boundary (`A-N` labels only), and add a dedicated structural guard scanning committed fixture resources for email-shaped or employeeNumber-shaped content — this project already has the exact precedent (`LiveShapeShiftDeskFixture`'s T-15-33 intent) to copy rather than trust manual review of a large diff |
| Gating a capture harness on a system property that forwards the *entire* `-D` property set to the test JVM | Credential leakage into CI logs from unrelated `-D` flags on the same Gradle invocation | Forward exactly one named property (`wfm.capture`), matching the existing `wfm.benchmark` precedent at `build.gradle`'s test block — never the whole property set |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| An overnight shift rendered as two fragments across the date grid (Pitfall 10) | Operator misreads the schedule as two short shifts instead of one continuous night shift, potentially miscounting hours by eye | One continuous block under the business-date column, with a distinct (not blank, not duplicate) continuation indicator on the morning-after column |
| A `day_start` config surfaced in the UI before it does anything (Foundation & Guards ships the `00:00`-only gate) | Operator sets a non-default value, sees it "save successfully," and reasonably assumes it took effect | Either hide the control until it's functional, or — if shown for forward-visibility — make the `00:00`-only restriction explicit and visible in the UI copy, not just enforced silently by a backend 400 |
| A minimum-rest pre-solve refusal (REST-03) that reports "infeasible" without naming the agent/shifts | Operator has no actionable next step, must guess which agent or template is the problem across potentially hundreds | Name the specific agent and the two conflicting shifts in the refusal message, matching this project's own established pattern (the shift-mode seat-supply gate already does this) |

## "Looks Done But Isn't" Checklist

- [ ] **Business-date join migration:** Often missing the guard that proves *no other* join still
      uses calendar date — verify a fresh grep of `.getDate()` inside `ScheduleConstraintProvider`
      returns exactly the allowlisted set, not "the ones I remembered to change."
- [ ] **Regression fixture:** Often missing a class-load validator proving the fixture *actually
      contains* each named boundary case — verify by temporarily deleting one boundary-case scenario
      and confirming the validator (not just a human) catches its absence.
- [ ] **Overnight shift template save:** Often missing envelope-vs-business-day validation (as
      distinct from envelope-vs-calendar-day) — verify by attempting to save a template whose
      envelope structurally cannot fit the desk's business day and confirming a save-time refusal,
      not a silent "it will still save."
- [ ] **Minimum rest:** Often missing the pre-solve refusal as a *separate* mechanism from the
      in-solve hard constraint — verify by constructing a desk where rest is structurally
      unavoidable and confirming a fast, named refusal rather than a slow, unproductive solve to the
      time limit.
- [ ] **`business_date` backfill:** Often missing verification that the backfill actually ran against
      a realistic row count before the NOT NULL constraint lands — verify against a fresh clone of
      live data (or the closest available proxy), not just the empty test database.
- [ ] **DayWindow guard extension:** Often missing the "stale allowlist entry" direction of the
      set-equality check — verify by confirming the guard fails if an allowlisted line is deleted
      without removing its allowlist entry, not just when a new offending line appears.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|------------------|
| A silent non-join shipped to `dev` before being caught (Pitfall 2) | MEDIUM | Because `dev` is production: identify affected desks/dates via the per-constraint match-count history if a regression fixture exists; re-run the relevant constraint's report for affected schedules; do not silently re-solve accepted schedules — surface the discrepancy to the operator per this project's existing "fail loud, not silent" discipline before taking any corrective action on live data |
| An over-sensitive/under-powered regression fixture discovered mid-milestone (repeating v1.4's mistake) | HIGH | Stop; do not patch the existing fixture with more live-desk captures. Re-derive the boundary-case list from the property under test (Pitfall 3's concrete list is a starting point), build constructed scenarios first, and demote the existing live-desk fixture to a drift guard rather than trying to make it carry both jobs |
| An overnight template saved before envelope-vs-business-day validation existed (Pitfall 11), now referenced by live assignments | MEDIUM | Do not retroactively invalidate; add the validation going forward and separately audit existing overnight templates (once any exist) for envelope fit, surfacing violations as an advisory rather than blocking already-accepted schedules |
| A deploy killed an in-flight solve during this milestone's migration window (Pitfall 7) | LOW | Already a known, accepted operational cost with an established recovery path (re-run the solve) — no new recovery mechanism needed, just don't let migration timing make it *more* likely than the existing baseline rate |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| 1. End-to-end `LocalTime` comparisons | Foundation & Guards | `MidnightTimeArithmeticGuardTest` extension passes with zero un-allowlisted offences; the allowlist itself is reviewed line-by-line, not trusted from stale prior-session counts |
| 2. Silent non-join on wrong date key | Solver Business-Date Correctness | A dedicated structural guard fails on any `.getDate()`-keyed join in `ScheduleConstraintProvider` not on an explicit allowlist; per-constraint match-count assertions exist for every migrated join |
| 3. Regression baseline design (the v1.4 killer) | Foundation & Guards | Constructed boundary-case scenarios exist with a class-load validator proving their presence; live-desk fixture is demonstrably small and secondary; a failing fixture names a specific constraint/property, never just "bytes differ" |
| 4. "Byte-identical" misread as reproducible solve | Foundation & Guards | Fixture uses `SolutionManager.explain(...)` scoring only, zero search/termination configured; test suite shows zero flakiness across repeated local runs |
| 5. 88-site re-anchoring as piecemeal edits | Re-anchoring | Guard from Pitfall 1 passes clean *before* re-anchoring starts, confirmed as an explicit phase-entry gate, not assumed |
| 6. "Nothing changed" by assertion not proof | Foundation & Guards, Re-anchoring | Default-and-gate on `day_start`; write-path allowlist guard on `business_date`; migration/entity reconciliation test covers both new columns |
| 7. Deploy kills in-flight solve, migration ordering | Foundation & Guards (migration shape); operational discipline every phase | Every migration in this milestone is safely readable by the pre-migration code version; deploy timing checked against RUNNING solves |
| 8. DST/timezone corner-painting | Foundation & Guards, Re-anchoring (design review only, no code) | `MINUTES_PER_DAY` stays internal to `DayWindow`; no new column stores a business-day boundary as a timezone-flavoured instant without a timezone field to back it |
| 9. Minimum rest boundary/horizon/infeasibility | Minimum Rest | Rest ordering uses business-date-and-time, not calendar-date bucketing; horizon-edge behavior is explicitly tested and documented; pre-solve refusal is a separate, tested mechanism from the hard constraint; a no-rest-configured desk is regression-tested unchanged |
| 10. Grid/Excel rendering of a spanning block | Overnight Shift Templates (design coordinated with Solver Business-Date Correctness) | UI and Excel renderers both attribute the block to business date exactly once, with a distinct (non-blank, non-duplicate) morning-after treatment, verified via a golden-output comparison for at least one constructed overnight-shift fixture |
| 11. Envelope validated against wrong window | Overnight Shift Templates | Save-time refusal exists for an overnight envelope that doesn't fit the desk's business day, tested against a deliberately-too-large constructed template |

## Sources

- `rescue/phase-18-unwind-20260930:.planning/phases/18-business-day-foundation-guards/18-RESEARCH.md`
  — the cancelled attempt's own research, including the verified 11-line `.isAfter`/`.isBefore`/
  `.compareTo` allowlist/fix candidate list and the `ScheduleExportService:366` defect (HIGH — read in
  full, cross-verified against the live tree this session)
- `rescue/phase-18-unwind-20260930:.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md`
  — the full D-01..D-26 decision log, especially D-01 through D-10 (the BDAY-06 fixture design that
  was itself sound at the decision-log level; the failure was in what data fed it) (HIGH)
- `rescue/phase-18-unwind-20260930:.planning/ROADMAP.md` — v1.4's six-phase structure, dependency
  ordering rationale, and REST-01..04/MIGR-01..04 success criteria (HIGH)
- `rescue/phase-18-unwind-20260930:.planning/REQUIREMENTS.md` — the four scoping findings (12
  `getDate()` joins, 88 `DayWindow` call sites, `agent_day_hours` needs no schema change, SLOT mode
  already-wrong) (HIGH)
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/util/DayWindow.java` — read in full this
  session; the class javadoc is itself a primary pitfalls source (HIGH)
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/model/ShiftBandPair.java` — read in
  full; the "one coverage validator, two callers" discipline and its own midnight-correctness comments
  (HIGH)
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
  — grepped this session; confirmed 12 live `getDate()`-keyed joins at the lines cited (HIGH)
- `/Users/pete/IdeaProjects/wfm-service/src/main/java/com/wfm/service/ScheduleExportService.java` —
  read this session; confirmed the `shiftCode` fix (`5ddd8dc`) is already live, and confirmed the
  roster grid's one-row-per-agent/one-column-per-date structure (HIGH)
- `.planning/codebase/CONCERNS.md` — solver-quality plateau, `InMemoryScheduleStore` fragility,
  overnight-shift tech-debt entries already diagnosed pre-milestone (HIGH)
- `.planning/codebase/TESTING.md` — the guard-test pattern family (behavioral + structural dual proof,
  set-equality-not-subset, non-vacuity) this research recommends reusing rather than reinventing (HIGH)
- `.planning/PROJECT.md` — v1.5 milestone scope, the explicit "why this shape" comparison against
  v1.4's cancellation, and the settled/deferred decision lists (HIGH)

---
*Pitfalls research for: overnight shifts and business-date semantics in an existing, live,
calendar-day-anchored Spring Boot + Timefold scheduling system*
*Researched: 2026-09-30*
