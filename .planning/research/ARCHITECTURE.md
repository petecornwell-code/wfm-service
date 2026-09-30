# Architecture Research: Business-Date Re-Anchoring (v1.5)

**Domain:** Constraint-solver re-anchoring in an existing Spring Boot + Timefold 1.16.0 workforce
scheduler
**Researched:** 2026-09-30
**Confidence:** HIGH — every claim below is grounded in direct inspection of `DayWindow.java`,
`ScheduleConstraintProvider.java`, `TimeslotGeneratorService.java`, the planning-entity classes,
and the actual (unwound) v1.4 implementation at tag `rescue/phase-18-unwind-20260930`, not in
general Timefold knowledge.

## Executive framing: this milestone already has a dry run

v1.4 built roughly the first third of this capability before being cancelled — not for a wrong
architecture, but for a weak regression fixture (PROJECT.md: "it did not fail on execution"). Its
commits survive on `rescue/phase-18-unwind-20260930` and are directly inspectable. That changes
this research from "what should the architecture be" to "here is the architecture v1.4 already
proved out, here is exactly where it stopped, and here is what its own code comments name as the
next step." Four of its commits are load-bearing evidence for this document:

- `84fdc3f` — `V53` migration + `Desk.dayStart` + `Timeslot.businessDate` fields, API, UI. Landed
  with **day-start gated to `00:00` only** — the schema and plumbing exist, but nothing honours a
  non-default value yet.
- `2196e40` — every production write path that constructs a `Timeslot` row set to `businessDate`,
  found by running the full suite and reading the 63 `NOT NULL` violations it produced.
- `7d42f23` — `BusinessDateWritePathGuardTest`, a structural set-equality guard over
  `Timeslot#setBusinessDate` call sites, split into DERIVING vs PROPAGATING because a second
  legitimate writer (`ScheduleService`'s accept-time snapshot copy) turned out to exist that the
  plan hadn't anticipated.
- `63d85a6` — extended the pre-existing `MidnightTimeArithmeticGuardTest` to comparison operators,
  in preparation for the actual re-anchoring.

None of these commits touch `DayWindow`'s anchor or `ScheduleConstraintProvider`'s joins — v1.4
stopped at exactly the boundary this research is asked to cross. Everything below builds on top of
that stopping point rather than re-deriving it from first principles.

## System Overview: where the new capability attaches

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│  Desk (existing entity, MODIFIED)                                            │
│  + dayStart: LocalTime  (NEW column, v1.4's V53 pattern; default 00:00)      │
└───────────────────────────────┬───────────────────────────────────────────-─┘
                                │ read at generation time
                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  TimeslotGeneratorService (MODIFIED — sole writer of business_date, D-24)     │
│  generateTimeslots(): for each calendar date x desk grid slot, computes       │
│  businessDate = the date whose [dayStart, dayStart+24h) window contains this  │
│  slot's start — identity function when dayStart == 00:00                     │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ persists
                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Timeslot (existing entity, MODIFIED)                                        │
│  date: LocalDate        (unchanged — the calendar day this row physically     │
│                           sits on; UI/export grid, staffing upload key)       │
│  + businessDate: LocalDate (NEW — v1.4's V53 column; the day-off/contracted-  │
│                              hours/consistency day this row counts against)   │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ read by
                ┌───────────────┼────────────────────────────┐
                ▼                                             ▼
┌────────────────────────────────────┐   ┌──────────────────────────────────────┐
│  DayWindow (MODIFIED, riskiest      │   │  ScheduleConstraintProvider           │
│  edit — 112 call sites, 16 files)   │   │  (MODIFIED — ~12 call sites re-point  │
│  Re-anchored on a per-call dayStart │   │  from Timeslot::getDate to            │
│  parameter, retiring the implicit   │   │  Timeslot::getBusinessDate; see       │
│  00:00 anchor                       │   │  "The DATE choke point" below)        │
└──────────────────────────────────────┘   └──────────────────────────────────────┘
                │ consumed by
                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  ShiftTemplate (MODIFIED — endTime may now be < startTime, meaning overnight) │
│  AgentShiftAssignment.date (UNCHANGED FIELD, REINTERPRETED — already means    │
│    "the agent-day this shift belongs to"; becomes business date by            │
│    construction once SolverService derives it from business-dated facts)      │
│  AgentDayConfig.date / AgentDayOff.date / AgentPreference.date /              │
│    ResolvedUsualShiftTarget.date / ShiftStartMixTarget.date — ALL UNCHANGED   │
│    TYPES, because every one of them is already a per-agent-day fact with no   │
│    competing calendar-date meaning (see "The narrow blast radius" below)      │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Component responsibilities (new vs modified)

| Component | Status | Responsibility |
|-----------|--------|-----------------|
| `Desk.dayStart` | **NEW field** | Per-desk anchor time; `LocalTime`, default `00:00` (v1.4 `84fdc3f`) |
| `DeskService.setDayStart` | **NEW method** | Validates, gates, refuses on an accepted schedule conflict; v1.4 built the gate half in `84fdc3f`, deferred the refusal to "Task 2" |
| `Timeslot.businessDate` | **NEW column** | The business day a slot counts against; sole writer `TimeslotGeneratorService` (D-24) |
| `TimeslotGeneratorService.generateTimeslots` | **MODIFIED** | Computes and stamps `businessDate` per slot using the desk's `dayStart`, not just the calendar loop it already runs |
| `ScheduleService.acceptSchedule` snapshot copy | **MODIFIED** | Already patched in v1.4 (`2196e40`) to copy `businessDate` across; needs no further change once the generator is correct |
| `DayWindow` | **MODIFIED — highest risk** | Every `startMinute`/`endMinute`/interval function re-anchored on a caller-supplied `dayStart`; `durationMinutes` stops throwing on a midnight-crossing interval (that is the whole point of this milestone) |
| `ScheduleConstraintProvider` | **MODIFIED** | ~12 call sites switch from `Timeslot::getDate` to `Timeslot::getBusinessDate` — see the choke-point analysis below, which shows this is far fewer than 12 *edits* |
| `ShiftTemplate` | **MODIFIED** | `getNetHours` / `isEffectiveOn` / overnight validity: `endTime < startTime` becomes a legal, meaningful state instead of a `DayWindow.durationMinutes` throw |
| `AgentDayOff`, `AgentDayConfig`, `AgentPreference`, `AgentShiftAssignment`, `ResolvedUsualShiftTarget`, `ShiftStartMixTarget` | **UNCHANGED TYPE, REINTERPRETED MEANING** | Every `date`/`LocalDate` field on these already means "the agent-day this fact belongs to" — see below for why this is the load-bearing narrowing fact of the whole milestone |
| A new hard **minimum-rest constraint** | **NEW constraint** | Per desk; compares the end of one agent-day's assigned envelope to the start of the next agent-day's, using `DayWindow`-correct arithmetic across the business-date boundary |
| A new **business-date join guard test** | **NEW test** | Structural, mirrors `MidnightTimeArithmeticGuardTest`/`BusinessDateWritePathGuardTest` — see Q3 below |

## The narrow blast radius (the single most important finding)

`Timeslot` is the **only** entity in this codebase that carries two competing notions of "date" —
`date` (the calendar day the row physically sits in the grid) and the new `businessDate` (the day
it counts against). Every other date-bearing problem fact the solver reads —
`AgentDayOff.date`, `AgentDayConfig.date`, `AgentPreference.date`, `AgentShiftAssignment.date`,
`ResolvedUsualShiftTarget.date`, `ShiftStartMixTarget.date` — was already modelled, since v1.2/v1.3,
as a **per-agent-day fact with no timeslot underneath it**. None of them has ever had a second
"calendar date" to disagree with. Confirmed by direct inspection:

```java
// AgentDayOff.java — one row per (agent, date); "date" already IS the day off, full stop
@Column(nullable = false) private LocalDate date;

// ResolvedUsualShiftTarget.java — plain record, one date field, no timeslot reference
public record ResolvedUsualShiftTarget(UUID agentId, LocalDate date, LocalTime usualStartTime) {}

// AgentShiftAssignment.java — identity is (agent, date), "not a per-slot entity FK" (its own javadoc)
@Column(nullable = false) private LocalDate date;
```

This means the re-anchoring work is **not** "touch every date field in the domain model." It is:
(1) make `Timeslot` carry both dates correctly, (2) make every constraint join that currently reads
`Timeslot`'s calendar `date` to compare against one of those per-agent-day facts read `businessDate`
instead, and (3) make `SolverService`'s pre-solve derivation of those per-agent-day facts (which
today walks calendar dates) walk business dates instead so the values it hands the solver were
never wrong in the first place. The domain model for agent-day facts does not change shape at all —
only what value flows into their existing `date` field.

## Q1 — Where the business date is computed and where it lives

**Recommendation: a stored `business_date` column on `Timeslot`, populated once at generation
time, exactly as v1.4 built it (`84fdc3f`, `2196e40`).** Not a derived getter, not a shadow
variable, not a problem-fact lookup map.

Reasoning, weighed against the alternatives named in the question:

- **A derived getter** (`Timeslot.getBusinessDate()` computed on read from `date`/`startTime` and
  the desk's `dayStart`) requires the desk to be reachable from every call site that needs a
  business date — but `Timeslot` has no reference back to `Desk` beyond a `deskId` UUID, and
  Timefold's constraint streams operate on detached, in-memory problem facts with no repository
  access. Every constraint that needs the desk's `dayStart` would have to join `Desk` in *as well
  as* `Timeslot` just to compute a value that was knowable at generation time and never changes
  for a persisted row. That is strictly worse than the current `endTime`-means-midnight
  `DayWindow` convention it is meant to replace: it trades one implicit convention for a runtime
  dependency the constraint provider does not otherwise have (`ScheduleConstraintProvider` never
  touches `Desk` today — introducing it here for a value with a fixed answer is exactly the kind
  of coupling this codebase's P-19/D-02 discipline exists to prevent).
- **A shadow variable** is Timefold's mechanism for a value that must be *recomputed by the solver
  itself* whenever a genuine planning variable it depends on changes (e.g., derived arrival time
  after a shift is moved). `business_date` depends on nothing the solver chooses — it is fixed the
  moment the timeslot is generated, before any planning entity exists. Declaring it a shadow
  variable would ask Timefold to re-derive it on every move for no reason and would put a Timefold
  annotation on a field whose value a shadow variable listener cannot even compute (it has no
  visibility into `Desk.dayStart` either, for the same detachment reason above).
- **A problem-fact lookup map** (`Map<UUID timeslotId, LocalDate businessDate>` carried alongside
  the schedule, mirroring `TimeslotDemandConfig`'s pattern) is the closest real alternative. It was
  rejected for this milestone because it reintroduces exactly the failure mode Q3 asks about: a
  join that is supposed to go through the map but instead reads `timeslot.getDate()` directly
  produces a **type-correct, silently wrong** result — `LocalDate` compares fine against
  `LocalDate` either way, so nothing fails loudly. A stored column is joinable directly by
  `equal(a -> a.getTimeslot().getBusinessDate(), ...)`, which is exactly the same shape every
  existing join already has — no new join arity, no new problem-fact collection to register, and
  the two-line diff per call site is auditable by `git diff` the same way MODE-05's "additive, not
  a rewrite" claim was proven in v1.3.

**Stability for Timefold joins.** A stored column is set once, before the schedule is built, and
never mutated during solving — `Timeslot` is a `@ProblemFactCollectionProperty`, not a
`@PlanningEntity`, so this is exactly the stability profile Timefold constraint joins require: a
join key must not change mid-solve, and a stored, generation-time value structurally cannot.

**Sole writer, not a default.** v1.4's `D-24` decision — `TimeslotGeneratorService` is the only
class permitted to *derive* a fresh `business_date`; `ScheduleService`'s accept-time snapshot copy
is the only other writer, and it only *propagates* an already-derived value forward, never computes
one. This is worth keeping as a named rule (`business_date` gets no Java-side default, so a missed
write path fails as a `NOT NULL` constraint violation, loudly, in the test suite — which is exactly
how v1.4 discovered its six missed test fixtures).

## Q2 — Re-anchoring `DayWindow` safely across 112 call sites

**Recommendation: (c) parameterise every function with an explicit day-start, and let the
compiler find every call site — but implement it as a *new overload set*, not an in-place
signature change, so the migration is driven by an exhaustive compiler-checked list rather than by
memory.**

Concretely: `DayWindow`'s existing eight public functions
(`startMinute`, `endMinute`, `durationMinutes`, `isForwardWithinDay`, `overlaps`, `contains`,
`startsBefore`, `plusWithinDay`) all currently assume the day begins at `00:00` implicitly. Add a
second parameter, `LocalTime dayStart`, to the two functions where the anchor actually matters
(`startMinute`, `endMinute` — everything else is built on top of those two and inherits the
correction automatically once they change), and change `durationMinutes` to stop throwing on
`end.isBefore(start)` when the two are being read as an overnight interval.

Compare the three options against **what actually breaks if a call site is missed**:

- **(a) Change in place, rely on tests.** `DayWindow`'s own javadoc is explicit that this class was
  written specifically because raw `LocalTime` arithmetic fails *silently* (`Duration.between`
  returns a plausible negative number; `isAfter` returns a plausible `false`). Changing the
  anchor's meaning in place without a compiler signal reproduces exactly that failure shape one
  level up: every one of the 112 call sites still compiles, still runs, and silently computes
  against the *old* implicit `00:00` anchor for every desk that has not opted into a non-default
  `dayStart` — which is currently 100% of desks, so nothing in the test suite that exercises only
  today's desks would even notice. That is the single worst possible failure mode for a milestone
  whose stated goal is "any desk, any day start" — it would pass exactly the regression suite that
  exists today and fail only on the untested case.
- **(b) Introduce an anchored variant, deprecate the old one, migrate incrementally.** This
  guarantees the OLD behaviour keeps working (good for a phased rollout) but does **not** guarantee
  every call site gets migrated — a `@Deprecated` annotation is a warning, not a build failure, and
  this codebase's own `MidnightTimeArithmeticGuardTest` javadoc exists precisely because "warned,
  not prevented" has already been this project's stated regret pattern once (`Bulk "Set all
  days to…" destroys MANDATORY/PTO labels`, audit I-3, "warned via `confirm()`, not prevented").
  Leaving two anchor-aware and anchor-unaware implementations coexisting indefinitely is also a
  direct violation of P-19/D-02 ("one implementation, not two that can drift") — the exact rule
  this milestone's own scope doc cites as the reason `DayWindow` was built as a single utility in
  the first place.
- **(c) Parameterise and let the compiler enumerate every site.** Changing `startMinute(LocalTime)`
  to `startMinute(LocalTime, LocalTime dayStart)` (or, more precisely, removing the one-argument
  overload entirely rather than keeping it as a `00:00`-defaulting convenience) makes every one of
  the 112 call sites a compile error until it supplies a `dayStart`. This is the only option where
  "the compiler finds them all" is literally true rather than aspirational. It converts a 112-site
  audit into a 112-site *build failure list* — `./gradlew compileJava` enumerates every site that
  needs a decision, and nothing can be missed by oversight the way a passing-but-wrong test suite
  or an unheeded `@Deprecated` warning can be.

**The cost this buys down.** Most of the 112 sites do not actually need a *desk*-specific
`dayStart` — they need `LocalTime.MIDNIGHT`, because they operate on values that are already
desk-agnostic (e.g., `ShiftBandPair.covers`, which compares two envelopes both expressed relative
to the same template). The compiler-forced migration is exactly where that distinction gets made
explicit, one call site at a time, instead of being assumed. A grep-based audit could produce the
same list, but only a compile error is a hard gate that cannot be skipped by an agent running low
on context mid-migration — which matters given this milestone's own retrospective note that v1.4's
regression fixture problem came from *insufficient* proof of coverage, not too much.

**Sequencing implication.** Because removing the one-argument overload is what generates the
compiler-enumerated list, this change should land as **one atomic commit that touches all 16 files
at once**, immediately followed by (or combined with) the constraint-provider join fix — see Q4's
build order. There is no safe way to "partially" do a compiler-forced migration; the build is red
until it is complete. This is precisely why the milestone's own settled scoping note says "guard
tests land before the re-anchoring" — the guard tests (Q3) must exist and be green against
`00:00`-only behaviour *first*, so that the moment `DayWindow`'s anchor changes, any behavioural
drift for the (currently 100%-of-desks) default case shows up as a red guard test, not as a silent
new bug shipped alongside the migration.

## Q3 — Guarding the silent-non-join failure mode

**Recommendation: a structural source-scan guard test, in the same family as
`MidnightTimeArithmeticGuardTest` and `BusinessDateWritePathGuardTest` — not a type-level
`BusinessDate` wrapper.** This is a deliberate departure from "the strongest possible guarantee"
in favour of "the guarantee this codebase has already proven it can build, maintain, and trust,"
and the reasoning is specific to why a type wrapper would *not* actually be stronger here.

**Why a type wrapper looks attractive but buys less than it appears to.** A `BusinessDate(LocalDate
value)` record around every per-agent-day date field would make `Joiners.equal(calendarDateFn,
businessDateFn)` a compile error, which is a genuinely stronger property than a text scan can offer
— *if* the two sides of every mismatched join actually had different static types. But per the
"narrow blast radius" finding above, they don't, cleanly: `AgentDayOff.date`,
`AgentDayConfig.date`, `AgentPreference.date`, `AgentShiftAssignment.date`,
`ResolvedUsualShiftTarget.date`, and `ShiftStartMixTarget.date` are ALL already meant to be business
dates — wrapping them in `BusinessDate` changes nothing about *their* correctness, it only helps at
the one boundary where `Timeslot` supplies a date to compare against one of them. So the wrapper's
entire value collapses onto exactly one type: wrap `Timeslot.businessDate` as `BusinessDate` and
leave `Timeslot.date` as plain `LocalDate`, and now `Joiners.equal(a -> a.getTimeslot().getDate(),
AgentDayOff::getDate)` fails to compile because `LocalDate` and `LocalDate` — wait, it still
compiles, because `AgentDayOff.date` is still plain `LocalDate`, not `BusinessDate`. **Getting the
full compile-time guarantee therefore requires wrapping BOTH sides** — every one of those six
per-agent-day fields, plus `Timeslot.businessDate`, all become `BusinessDate`, while
`Timeslot.date` stays plain `LocalDate`. That is a real, if bounded, refactor: six entity/record
field types, every constructor call in `SolverService` that builds them (`computeAgentDayConfigs`,
`buildShiftAssignments`, the day-off loader, the preference resolver, the usual-shift resolver, the
shift-start-mix target service), every repository query parameter that compares against them, and
every DTO/export path that reads them back out for display. It is not a two-file change, and it
is the kind of "wide refactor during a migration" this project has explicitly chosen to defer
before (`Agent.contractedHoursPerDay` scalar, Phase 9 D-05 — "deferred deliberately to avoid a wide
refactor during the migration," later regretted as audit finding I-1's direct root cause, but
regretted for being *deferred silently*, not for the refactor itself being wrong to skip *when
paired with a working structural guard*, which contracted hours did not have and this milestone
would).

**Why the test-level guard is not the weaker fallback it sounds like — it already caught what it
was built to catch, twice.** This project's own established pattern for exactly this
"type-compatible, semantically-wrong" failure class is not an ad-hoc test but a specific, repeated
recipe:

1. `MidnightTimeArithmeticGuardTest` — a textual, comment-stripped scan of `src/main/java` for a
   fixed token list (`Duration.between(`, `.plusMinutes(`, etc.), asserted via
   `containsExactlyInAnyOrderElementsOf` against an explicit markdown allowlist, **in both
   directions** (new offender fails, stale allowlist entry also fails). Its own javadoc names the
   exact incident it exists to prevent (an `OutOfMemoryError` from a wrapping `LocalTime` loop
   cursor) and includes a dedicated test (`theScanDetectsAFreshOccurrence`) proving the matcher can
   actually go red, not merely that it has never been observed to.
2. `BusinessDateWritePathGuardTest` (v1.4, `rescue/phase-18-unwind-20260930`, commit `7d42f23`) —
   the identical technique applied to `Timeslot#setBusinessDate` call sites, and its commit message
   is direct evidence the pattern *already caught a real gap this milestone*: the plan assumed one
   legitimate writer, the guard's own construction surfaced a second (the accept-time snapshot
   copy), and the fix was to split the allowlist into DERIVING/PROPAGATING rather than weaken the
   assertion to a subset check.
3. `ScheduleConstraintClassificationTest` — the same "derive the expected set reflectively from
   production code, assert set-equality against an explicit, reasoned record" shape, applied one
   layer up (constraint-name completeness, not source text).

**The concrete new guard this milestone needs:** a `BusinessDateJoinGuardTest` that scans
`ScheduleConstraintProvider.java` (and any future solver-package file) for the token
`.getTimeslot().getDate()` — the calendar accessor — used anywhere inside a `Joiners.equal(...)`,
`.groupBy(...)`, or `.filter(...)` lambda, and asserts that set is **empty**, with a named,
reasoned allowlist for any call that is a genuine, deliberate calendar-date use (if any survive —
plausibly none do, since every join target is a per-agent-day business-date fact). This is a direct
structural port of `MidnightTimeArithmeticGuardTest`'s technique, reusing its comment-stripping and
set-equality machinery, and it inherits the same failure-safety property: a false positive (a
comment or string literal containing the token) fails the build and forces a human look, which is
the correct trade against a silent miss.

**Recommendation, stated plainly:** ship the test-level guard as the primary structural mechanism
for this milestone — it is proven, cheap (a few hours, following an existing template exactly), and
scoped precisely to the one boundary (`Timeslot.date` vs `Timeslot.businessDate`) where the ambiguity
actually lives. Do **not** introduce a `BusinessDate` wrapper type this milestone; flag it as a
candidate for a later hardening pass if a *third* date-bearing entity with a genuine calendar/
business split ever appears (Phil-US's deferred migration, per PROJECT.md, might be exactly that
trigger — its real overnight shifts are the reason the wrapper's future value is real, just not
yet). Layer one additional, cheap non-vacuity check alongside the scan guard, matching v1.3's
`ShiftEnvelopeComplianceConstraintTest` precedent (proving a null-branch guard was load-bearing by
constructing a fixture that only a correctly-joined constraint could catch): an integration test
that builds an overnight-shift fixture and asserts the relevant constraints produce a **non-empty**
match set on a deliberately-broken schedule — a silent non-join reports zero matches and a green
score, so a guard that only checks "does the code compile against `getBusinessDate()`" still needs
a companion that checks "does the resulting constraint actually fire when it should."

## Q4 — Build order and dependency structure

Ordered by hard dependency, not preference. Each stage names what it structurally cannot proceed
without.

1. **Guard tests, written against today's `00:00`-only behaviour.** `MidnightTimeArithmeticGuardTest`
   already exists; it needs extending (v1.4's `63d85a6` did exactly this — comparison operators)
   and the new `BusinessDateJoinGuardTest` (Q3) needs writing. These must be green *before* any
   re-anchoring lands, so that the re-anchoring commit is provably the first commit that could make
   them red — this is the milestone's own settled decision ("guard tests land before the
   re-anchoring... `DayWindow` is the only thing standing between this change and a silently wrong
   schedule") and it is a real dependency, not just discipline: a guard written after the change it
   guards cannot prove it would have caught the defect.
2. **Schema + `Desk.dayStart` + `Timeslot.businessDate`, gated to `00:00`-only.** This is v1.4's
   `84fdc3f` shape almost exactly: add both columns, wire `TimeslotGeneratorService` and
   `ScheduleService`'s snapshot copy as the two sole writers (Q1), and refuse any `dayStart` other
   than `00:00` at the service layer. Nothing downstream can be tested meaningfully before this
   lands, because `businessDate` does not exist as a value until this stage — but because it is
   gated to always equal `date`, this stage is provably a no-op for every existing desk, which is
   what makes it safe to ship on its own and is exactly why v1.4 sequenced it first.
3. **`BusinessDateWritePathGuardTest`.** Depends on stage 2 existing (there is nothing to scan
   for yet otherwise). v1.4's `7d42f23` is a directly reusable reference implementation.
4. **`DayWindow` re-anchoring (Q2).** Depends on stage 1 (the guard that will prove it correct) and
   is independent of stages 2–3 in principle — `DayWindow` knows nothing about `Desk` or
   `Timeslot` — but should land *after* `businessDate` exists so the immediately-following stage
   (5) has both halves available in the same window and the milestone's "the riskiest edit" gets
   its own isolated commit, reviewable and revertible on its own (see Q5).
5. **`ScheduleConstraintProvider` re-point (the DATE choke point) + `TimeslotGeneratorService`
   business-date computation using the desk's real `dayStart`.** Depends on stages 2 and 4 both
   being in place: the constraint provider needs `Timeslot.getBusinessDate()` to exist (stage 2)
   and needs `DayWindow` to correctly express overnight intervals for the constraints that do
   interval math, not just date-equality joins (stage 4). This is also the stage that finally lifts
   the `dayStart`-gate from stage 2 — the single line `DeskService.setDayStart` currently uses to
   refuse anything but `00:00` is deleted here, and v1.4's own code comment names this as "the one
   validation line Phase 19 deletes; its removal is meant to be a visible, reviewable act."
6. **Overnight shift templates (`ShiftTemplate.endTime < startTime` becomes legal), envelope
   validation against the desk's operating window, contracted-hours-consumes-starting-weekday.**
   Depends on stages 4–5: nothing about an overnight envelope can be represented correctly until
   `DayWindow.durationMinutes` stops throwing on it, and nothing about which business day it counts
   against can be correct until the constraint provider and generator agree on `businessDate`.
7. **SLOT-mode correctness fix** (an overnight stretch counting against one business day instead
   of splitting across two calendar days). Depends on 2, 4 and 5 — it is a narrower instance of the
   same join fix, isolable as its own commit/test because SLOT mode's constraints
   (`contractedHoursOver`/`Under`, `agentDayOff`, `agentNotWorkingThatDay`) are a strict subset of
   the ~12 call sites touched in stage 5.
8. **Minimum rest, hard constraint.** Depends on 4 (interval arithmetic across a business-date
   boundary) and 6 (overnight shift templates existing to actually be adjacent to each other) —
   this is the first genuinely *new* constraint, not a re-pointed join, so it is the natural final
   stage: everything it needs to reason about correctly must already be correct.
9. **Regression fixtures — constructed midnight-spanning scenarios plus the Phil-US small drift
   guard.** Can be authored in parallel with stages 6–8 once stage 5 exists (constructing a fixture
   needs a real `businessDate`-aware schedule to run against), but should be the last stage to be
   *finalized*, since it is what proves the preceding stages, not what they depend on.

**What is separable from what, explicitly:** stages 2–3 (schema + write-path guard) and stage 4
(`DayWindow` re-anchoring) have **no dependency on each other** and could be built as parallel
workstreams if desired — this is exactly the shape v1.4 took, and its cancellation had nothing to
do with this pairing. Stage 5 is the join point where both must be present. Stages 6–8 are strictly
sequential after 5. Stage 9 threads through all of them but is not a build-order dependency in the
same sense — it is verification, not construction.

## Q5 — Blast radius and rollback of the `DayWindow` re-anchoring

**What isolates it.** `DayWindow` has zero outbound dependencies — it imports only
`java.time.LocalTime` and touches no Spring bean, no repository, no entity. Its 112 call sites are
all *inbound*. That means the re-anchoring's correctness is fully determined by `DayWindow.java`
alone plus its own unit tests; it does not require a database, a solver run, or any of the 16
consuming files to be simultaneously correct in order to verify `DayWindow` itself. This is the
single strongest isolation property available in this codebase for a change of this size, and it
is why Q2's recommendation (compiler-forced parameterisation, landed as one atomic commit) is
practical rather than reckless: the "blast" is wide (16 files) but shallow (each site's fix is a
mechanical "supply the right `dayStart`" decision, not a redesign).

**What makes it reversible.**

- **Git-level:** because `DayWindow.java` itself has no other dependencies, a revert of the
  `DayWindow` commit plus the mechanical call-site commit is a clean, isolated `git revert` with no
  entanglement risk to unrelated files — unlike, say, a schema migration, which cannot be reverted
  once other migrations have been written on top of it.
- **Behavioural:** because every existing desk defaults `dayStart` to `00:00` and stage 2 (Q4)
  ships that gate *before* the re-anchoring lands, the re-anchored `DayWindow` is mathematically
  required to produce byte-identical output to the current implementation for every desk that has
  not opted in — `startMinute`/`endMinute` collapse to their current formulas exactly when
  `dayStart == LocalTime.MIDNIGHT`. This is not a coincidence to verify after the fact; it is a
  property that should be asserted directly, as a parameterised unit test running `DayWindow`'s
  full existing test suite through the new anchored functions with `dayStart` fixed at midnight,
  before the migration is considered complete. A guard of this shape is cheap because
  `DayWindow`'s existing tests already enumerate the behaviour to preserve.
- **Operationally:** because no desk in production has a non-default `dayStart` at the moment this
  ships (it is a brand-new column, defaulted to `00:00` everywhere), there is no live data whose
  *interpretation* changes the moment this deploys — the risk is entirely in *new* desks/agents
  choosing to set a non-default anchor, which is an opt-in action an operator takes deliberately,
  not a retroactive reinterpretation of existing schedules. That is a materially smaller blast
  radius than, for example, v1.3's shift-envelope coupling change, which had to reason about
  already-accepted schedules.

**Recommended isolation practice for execution, not just architecture:** land `DayWindow`'s
re-anchoring as its own commit, separate from the `ScheduleConstraintProvider` re-point that
consumes it (Q4 stages 4 and 5), even though they will land close together — this mirrors this
project's own established practice of keeping a schema-only or utility-only change in a "P-25:
body untouched byte-for-byte" style commit that is independently `git diff --name-only` provable,
the same discipline MODE-05 used for v1.3's mode-switching changes.

## Q6 — New vs modified components (summary table)

| Component | New / Modified | Notes |
|-----------|------------------|-------|
| `Desk.dayStart` | **New field + migration** | v1.4 `84fdc3f` pattern; default `00:00` |
| `DeskService.setDayStart` | **New method + endpoint** | Gate removed in build-order stage 5 |
| `Timeslot.businessDate` | **New field + migration** | Sole deriving writer: `TimeslotGeneratorService`; sole propagating writer: `ScheduleService` accept-time snapshot |
| `TimeslotGeneratorService` | **Modified** | Computes real `businessDate` from `dayStart` (currently identity-only under v1.4's gate) |
| `DayWindow` | **Modified — riskiest** | Anchor parameterised; `durationMinutes` stops throwing on overnight intervals |
| `ScheduleConstraintProvider` | **Modified** | ~12 call sites re-point to `getBusinessDate()`; the shared `DATE` lambda (line 71) is the single highest-leverage edit, since 7 of those sites already flow through it |
| `ShiftTemplate` | **Modified** | Overnight envelope (`endTime < startTime`) becomes valid; net-hours/effective-range logic must account for it |
| `AgentShiftAssignment` | **Modified (semantics only, not schema)** | `date` field reinterpreted as business date; no type change |
| `AgentDayOff`, `AgentDayConfig`, `AgentPreference`, `ResolvedUsualShiftTarget`, `ShiftStartMixTarget` | **Unchanged** | Already business-date-shaped facts; only the values `SolverService` computes into them change |
| `agent_day_hours` / `AgentDayConfig` resolution | **Unchanged, per milestone scoping** | Keyed by `DayOfWeek`; a Sunday-night shift correctly consumes Sunday's row with no schema change |
| Minimum-rest hard constraint | **New constraint** | First genuinely new solver rule this milestone adds |
| `MidnightTimeArithmeticGuardTest` | **Modified** | Already extended once by v1.4 (`63d85a6`); needs re-landing |
| `BusinessDateWritePathGuardTest` | **New (reference implementation exists)** | v1.4 `7d42f23`, directly reusable |
| `BusinessDateJoinGuardTest` | **New — this research's primary recommendation** | Q3; no v1.4 precedent, must be authored fresh, following the two existing guards' exact template |
| SLOT-mode overnight fix | **Modified** | Narrower re-application of the same join fix to `contractedHoursOver/Under`, `agentDayOff`, `agentNotWorkingThatDay` |
| Export / UI overnight rendering | **Modified** | Render one continuous block, not two calendar-day fragments; consumes `businessDate` for grouping, `date`/`startTime`/`endTime` for the literal grid position |

## Patterns to follow

### Pattern 1: Compiler-enumerated migration for a wide-fan-in utility change

**What:** Remove or change the signature of a shared, dependency-free utility function so every
call site becomes a compile error, rather than relying on tests or deprecation to catch stragglers.
**When:** A utility with a large, purely-inbound call graph (no downstream dependents of its own)
whose meaning is changing in a way tests over today's data cannot detect (Q2's "would pass exactly
the regression suite that exists today" problem).
**Why it fits here:** `DayWindow` has exactly this shape — 112 inbound call sites, zero outbound
dependencies — and the failure mode of missing one is silent, not loud.

### Pattern 2: Split a write-path guard by call-site *kind*, not just presence

**What:** When a set-equality write-path guard's real-world call sites turn out to include both
"derives a fresh value" and "propagates an already-derived value forward" (as `Timeslot.businessDate`
does — generator vs accept-time snapshot), split the allowlist into two named sets rather than
merging them into one broader, less precise allowlist.
**Why it fits here:** v1.4 hit this exact situation (`7d42f23`'s commit message documents it
directly) and its resolution — two allowlists, each still exactly-one-entry, rather than one
allowlist accepting either — is the reason the guard stayed precise instead of degrading into "any
two writers are fine."

### Pattern 3: Gate a new capability to a behaviour-preserving default before wiring it live

**What:** Land the schema and plumbing for a new configuration axis (here, `Desk.dayStart`) with a
hard-coded refusal of any value except the one that reproduces today's behaviour, then remove the
refusal in a later, separately-reviewable commit once the consuming code is actually ready.
**Why it fits here:** This is exactly v1.4's `84fdc3f`/`D-18` shape, and it is what makes stage 2 of
the build order (Q4) safe to ship well before the riskier `DayWindow` re-anchoring lands — the
column exists and is exercised by every write path, with zero behavioural change, before it is
ever allowed to diverge from `00:00`.

## Anti-patterns to avoid

### Anti-pattern 1: A problem-fact lookup map for a value with a fixed, generation-time answer

**What people might do:** Carry `businessDate` as a side map (`Map<UUID, LocalDate>`) alongside the
schedule instead of a stored column, reasoning that it avoids a migration.
**Why it's wrong:** It reintroduces the silent-non-join failure mode one level up — a join that
should route through the map but instead reads `timeslot.getDate()` directly is just as wrong and
just as silent, and now there are *two* places (`Timeslot` and the map) that must agree instead of
one authoritative column.
**Instead:** A stored column, per Q1.

### Anti-pattern 2: Widening the business-date guard test to a subset/containment check

**What people might do:** When the new `BusinessDateJoinGuardTest` (or the existing
`MidnightTimeArithmeticGuardTest`/`BusinessDateWritePathGuardTest`) produces a large allowlist and
gets noisy during development, relax `containsExactlyInAnyOrderElementsOf` to `isSubsetOf` or a
bare `.contains(...)` to stop chasing stale entries.
**Why it's wrong:** Every one of this codebase's existing structural guards documents, in its own
javadoc, that this exact relaxation is the failure mode that turns the guard into decoration. It is
named explicitly and repeatedly enough across `ScheduleConstraintClassificationTest`,
`UsualShiftWritePathGuardTest`, and `MidnightTimeArithmeticGuardTest` to be treated as settled
project doctrine, not a one-off opinion.
**Instead:** Keep set-equality in both directions; a stale allowlist entry is itself a signal that
something else changed and needs re-verifying, not noise to suppress.

### Anti-pattern 3: Reordering the `AgentShiftAssignment`/`ScheduleConfig`-leading stream shape

**What people might do:** While re-pointing joins from `getDate()` to `getBusinessDate()`, restructure
a constraint's stream order for readability (e.g., lead with `AgentAssignment` instead of
`AgentShiftAssignment`).
**Why it's wrong:** `shiftEnvelopeCompliance`'s own javadoc measures this precisely: leading with
the wrong stream cost roughly 3x construction-heuristic throughput and dropped a wall-clock-bounded
solve from `0hard` to `-100hard` on a real fixture. Several constraints in this file carry an
explicit "do not reorder these joins" comment for exactly this reason. A business-date join fix
should change the join *key*, never the join *order*.
**Instead:** Change `equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate())` to
`equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getBusinessDate())` in place, leaving every
`.forEachIncludingUnassigned(...)`/`.join(...)` ordering untouched.

## Integration points

### Internal boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `Desk` ↔ `TimeslotGeneratorService` | Direct field read (`desk.getDayStart()`) at generation time | The only place `dayStart` is read outside `DeskService` itself |
| `TimeslotGeneratorService` ↔ `Timeslot` | Direct field write (`setBusinessDate`) | Sole deriving writer per D-24 |
| `ScheduleService` (accept flow) ↔ `Timeslot` | Direct field copy (`setBusinessDate(live.getBusinessDate())`) | Sole propagating writer; already patched in v1.4 |
| `ScheduleConstraintProvider` ↔ `Timeslot` | Constraint-stream `equal(...)` joins | The re-point boundary; ~12 call sites, 7 flowing through one shared `DATE` lambda |
| `ScheduleConstraintProvider` ↔ `DayWindow` | Static utility calls (`durationMinutes`, `overlaps`, `contains`, `plusWithinDay`) | Interval math for break-window/envelope/contiguity constraints; needs `dayStart`-aware calls post re-anchor |
| `SolverService` ↔ `AgentDayConfig`/`AgentDayOff`/`AgentPreference`/`ResolvedUsualShiftTarget`/`ShiftStartMixTarget` construction | Pre-solve derivation loops | Must walk business dates, not calendar dates, when deciding which agent-day a fact belongs to — no type change, only a value-source change |
| `ScheduleOutputService`/`ScheduleExportService` | Reads `Timeslot.date` for grid position, `Timeslot.businessDate` for grouping | Rendering "one continuous block, not two fragments" is a grouping-key change, not a new data model |

## Sources

- Direct inspection: `src/main/java/com/wfm/util/DayWindow.java`,
  `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (full file, 1347 lines),
  `src/main/java/com/wfm/service/TimeslotGeneratorService.java`,
  `src/main/java/com/wfm/model/{Timeslot,Desk,AgentAssignment,AgentShiftAssignment,AgentDayConfig,
  AgentDayOff,ShiftTemplate,ShiftBandPair,ResolvedUsualShiftTarget,ShiftStartMixTarget}.java`
- Direct inspection: `src/test/java/com/wfm/solver/ScheduleConstraintClassification{,Test}.java`,
  `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java`
- Direct inspection of the unwound v1.4 branch `rescue/phase-18-unwind-20260930`, specifically
  commits `84fdc3f`, `2196e40`, `7d42f23`, `985e365`, `63d85a6`, and their full diffs — this is
  primary-source evidence of an already-attempted, partially-validated implementation of this exact
  capability on this exact codebase, not general Timefold guidance
- `.planning/PROJECT.md` (v1.5 scope, v1.4 cancellation rationale, settled decisions carried
  forward), `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/CONVENTIONS.md`

---
*Architecture research for: business-date re-anchoring in wfm-service (v1.5)*
*Researched: 2026-09-30*
