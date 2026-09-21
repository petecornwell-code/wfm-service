# Seat-Supply Gate: Distribution Analysis (G-15-31)

**Deliverable for gap `G-15-31`.** Produced by plan 15-19. Executable evidence lives in
`src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java`; this document is the
readable write-up of what that class measured. Nothing under `src/main` changed while producing
this analysis (`git diff --stat -- src/main` is empty for this plan's commits — verified below).

## 1. The Question

`SolverService.requireShiftEnvelopeSeatSupply`'s blocking check is one comparison, per date:

```
if (contractedSlots > librarySupplySlots) { refuse }
```

Both sides are **day totals**. `contractedSlots` sums every rostered agent's expected work slots
for the date; `librarySupplySlots` sums seats across every timeslot the live library reaches that
date. Distribution **within** the day is invisible to a sum-vs-sum comparison by construction — two
desks with identical day totals can differ arbitrarily in how those totals are spread across the
day's hours, and the comparison cannot tell them apart.

This is a **different defect** from the two the gate's calendar-blindness already had fixed this
round:

- **G-15-21** (fixed, plan 15-18): the supply **number** was wrong — a weekday-only template's
  clock-time footprint was counted on weekend dates it does not apply to, over-counting supply.
- **G-15-25** (still open, out of this plan's scope): the supply **model** is wrong — it unions the
  desk-wide pair list rather than computing what each agent's own eligible pairs can achieve, so it
  cannot see band composition.
- **G-15-31** (this plan): even given a **perfectly correct** per-date supply total, computed
  correctly and modelling band composition correctly, a **sum-vs-sum test still cannot see
  distribution** within the day. Fixing G-15-21 and G-15-25 in full does not touch this.

The operator observed this directly: solving at `overallocationHardLimitPct` 250 (the UI's own
default) was **not refused** by the gate — day-wide supply dwarfed day-wide demand by roughly 1.8x
even at 250% — yet the solve ran to -120 hard, violating contracted-hours-under, envelope compliance
and contiguity. The system **computes** the number that would have predicted this (the tightest-hour
advisory) and **declines to act on it**, by explicit design (`advisoryOnThinTimeslotDoesNotBlock`).
The gap's own instruction is not to reverse that design choice unexamined, but to do the analysis
that was missing: measure candidate blocking rules against fixtures known to solve and fixtures
known to collapse, and report the false-refusal rate rather than argue it.

## 2. What Was Measured

### 2.1 The distribution-blind fixture (this plan's own deliverable)

A minimal, deliberate reproduction of the live shape at ten-agent scale (not a 138-agent-day
replica): one shift template ("Opening", 08:00–17:00, break 12:00–13:00, net 8.00h), ten agents all
contracted 8.00h and therefore all eligible **only** for that one template — zero slack, so every
agent-day is forced onto every one of the template's eight legal hours. Seats are placed so the
boundary hour 08:00 gets only 2 seats (`seatsAtHour(1, 200)`, the per-hour model below) while every
other legal hour gets 16 (`seatsAtHour(8, 200)`) — day-wide supply 114 against day-wide demand 80.

**Measured, both halves, this session** (`SeatSupplyDistributionAnalysisTest`):

- The shipped gate **passes** this fixture (`distributionBlindFixture_shippedGatePassesIt`) —
  114 ≥ 80, no refusal, exactly the defect the gap describes.
- The fixture **never reaches 0 hard**, on any of 3 fixed seeds (1, 2, 3), step-count-terminated at
  2,000 steps through the shipped `solverConfig.xml`
  (`distributionBlindFixture_neverReachesZeroHardOnAnySeed`). All three seeds converge to the
  identical `-800 hard` (`Contracted hours (under)` × 8, weight `ofHard(100)`) — not merely close to
  zero, the same score on every seed, which is the signature of a **structural** impossibility
  rather than a search-quality miss. The reasoning is a pigeonhole argument, not a search-quality
  claim: 8 of the 10 forced agent-days cannot get a legal seat at 08:00 (2 seats, 10 forced), and no
  seat exists anywhere outside this template's 8 legal hours for them to make up the missing hour
  elsewhere — so a nonzero hard score is guaranteed on every possible assignment, not merely
  probable on the seeds tried.

### 2.2 The control fixtures

- **`LiveShapeShiftDeskFixture`** (reused directly, per this plan's own instruction — "the live-shape
  control that solves"). The shipped gate passes it (`controlFixture_shippedGatePassesIt`). One
  fresh solve this session (seed 1, step limit 5,000) reached `-120 hard` / 3 violations (2 `Shift
  envelope compliance`, 1 `Contracted hours (under)`) — **within** the already-committed
  `TOTAL_VIOLATION_CEILING = 3` from `15-14-SUMMARY.md`/`15-BENCHMARK.md`'s five-seed baseline
  (`[3, 1, 2, 0, 1]`, median 1.0). The remaining four seeds were **not** re-run this session — the
  existing five-seed table is cited rather than reproduced, a runtime-budget choice recorded plainly
  rather than hidden.
- **The healthy staggered desk** (new, purpose-built to separate R1's flaw from R2's virtue — see
  §3). Two templates with only partially overlapping legal hours ("Morning" 08:00–17:00 net 8.00h,
  "Afternoon" 12:00–22:00 net 9.00h), 5 agents singleton-eligible for each. Seat plan: single-group
  hours get 7 seats (5 forced + a 2-seat margin), overlap hours get 12 (10 forced + margin — see the
  empirical note in the fixture's own javadoc: an **exact**-fit version of this same shape reproduces
  a genuine local-search plateau, the same shape `HANDOFF.md` records for the live desk, which would
  have measured a search-quality artefact rather than the distribution point this fixture exists to
  make). The minimum covered-hour seat count (7) is well under the desk's total agent-day count (10)
  — by design. **Measured**: the shipped gate passes it; it reaches `0 hard` on both seeds tried (1,
  2), step limit 10,000.

### 2.3 Labelling method

A fixture is `KNOWN-SOLVES` only if this session observed it reach an acceptable outcome (0 hard, or
— for the reused control — within the already-committed violation-count ceiling) through the shipped
`solverConfig.xml` across a seed set; `KNOWN-COLLAPSES` only if observed not to, on every seed tried.
No fixture here is labelled from its name or its javadoc.

### 2.4 Corpus size and exclusions, stated plainly

**The measured corpus is four date-slices across three fixtures**: the distribution-blind fixture (1
date, `KNOWN-COLLAPSES`), the healthy staggered desk (1 date, `KNOWN-SOLVES`), and
`LiveShapeShiftDeskFixture`'s two dates (`KNOWN-SOLVES` each). This is a **small corpus** and this
document does not overstate it.

**23 further fixtures exist in the repository and are excluded from this table, not silently
absorbed into it**: the 14 in `ShiftEnvelopeSupplyGateTest`, the 6 in
`ShiftEnvelopeSupplyInvariantTest`, and the 3 in `ShiftDeskEndToEndRegressionTest`. These are
labelled `NOT-SOLVE-EVALUABLE` and excluded from the false-refusal denominator for two reasons
recorded together, not conflated: (a) most are 1–3 agent-day gate-arithmetic unit tests, exactly the
kind of degenerate scale the plan's own methodology names as excludable ("a single agent-day, no
specialization"); (b) their fixture-building methods are `private` inside their own test classes and
were not re-derived here, a scope decision this document states rather than hides. They are
**referenced**, not silently ignored: `ShiftDeskEndToEndRegressionTest`'s three cases in particular
are genuinely-solved (not degenerate) fixtures whose own SUMMARY-level evidence already establishes
labels equivalent to `KNOWN-SOLVES`/`KNOWN-COLLAPSES` — a natural extension of this corpus for
whoever next revisits this analysis, named explicitly in §7.

## 3. The Rule-by-Fixture Table

Transcribed verbatim from `SeatSupplyDistributionAnalysisTest`'s own printed output
(`ruleByFixtureTable_falseAndTrueRefusalCounts`), run in this session:

| fixture | label | R0 (shipped day-wide sum) | R1 (tightest-hour promoted) | R2 (forced-occupancy) | R3 (R2, warn-only) |
|---|---|---|---|---|---|
| distribution-blind (Task 1) | KNOWN_COLLAPSES | PASS | REFUSE | REFUSE | PASS |
| healthy staggered desk | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS |
| LiveShapeShiftDeskFixture day 1 | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS |
| LiveShapeShiftDeskFixture day 2 | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS |

**Per-rule false-refusal / true-refusal counts** (denominator: 3 `KNOWN-SOLVES`, 1
`KNOWN-COLLAPSES`):

| Rule | False refusals | True refusals |
|---|---|---|
| R0 (shipped day-wide sum) | 0 | 0 |
| R1 (tightest-hour promoted to blocking) | 3 | 1 |
| R2 (forced-occupancy necessary condition) | 0 | 1 |
| R3 (R2, warn-only) | 0 | 0 |

**Rule definitions, stated precisely** (each a pure static function over one date's slice — no rule
touches `src/main`):

- **R0 — the shipped day-wide sum.** `contractedSlots > librarySupplySlots` for the date, computed
  from the date-aware coverage helper (G-15-21's fix). The control against which the other three are
  measured.
- **R1 — the tightest-hour advisory promoted to blocking.** The shipped advisory carries no numeric
  threshold of its own — it always reports the minimum covered-hour seat count, whatever the value.
  The most literal way to make that a *blocking* check, adopted here because the gap's own text does
  not pin one down: refuse when the date's minimum covered-hour seat count is less than the date's
  total rostered agent-day count. This is exactly the naive promotion the gap explicitly warns
  against adopting unexamined — and the table shows why: it refuses **every** fixture in this corpus
  whose per-hour headcount is genuinely staggered by design, including two that are provably
  solvable.
- **R2 — the forced-occupancy necessary condition.** See §4 for the full argument. An agent-day is
  *forced* at timeslot `ts` when every one of its eligible pairs both covers `ts` and has zero slack
  for that agent-day. R2 refuses when, at any covered timeslot, the count of forced agent-days
  exceeds the seat count there.
- **R3 — R2, demoted to warn-only.** Identical diagnostic computation to R2, never refusing (by
  definition, `falseRefusals = trueRefusals = 0`). Included to show the conservative end of the
  spectrum — the same information R2 blocks on, surfaced only as an advisory, which is operationally
  equivalent to today's shipped behaviour for this specific signal.

## 4. R2's Necessary-Condition Argument, in Full

**Claim.** If, for some timeslot `ts` on a rostered date, the count of agent-days *forced* at `ts`
exceeds the number of seats at `ts`, then no zero-hard solve exists for that date.

**Definition of forced.** An agent-day is forced at `ts` when every one of its
`AgentShiftAssignment.getEligibleShiftBandPairs()` both (a) covers `ts`, and (b) has **zero slack**
for that agent-day — its covered-slot count on that date equals the agent-day's
`AgentDayConfig.expectedWorkSlots()` exactly. Condition (b) matters because of bounded slack
(`envelopeSlackSlots`): a pair with slack lets the agent legally skip up to `envelopeSlackSlots` of
its own covered slots, and `contractedHoursOver`/`Under` judge only the aggregate worked-hours total,
never which specific slot was skipped (`AgentShiftAssignment`'s own javadoc; verified against
`ShiftEnvelopeSupplyInvariantTest`'s zero-slack lemma). A pair with slack therefore does **not** force
the agent onto `ts` even if it covers it — the agent has a legal escape route through that same pair.
An agent-day with **no** eligible pair at all is not "forced" by this predicate; that case is R0's own
distinct unassignable-row branch, unaffected by this analysis.

**Why the claim holds.** If an agent-day is forced at `ts`, then by definition every legal choice
available to it (every eligible pair) requires it to occupy `ts` — there is no pair it could be
assigned that lets it legally skip `ts`. If the count of such agent-days at `ts` exceeds the seats
available there, at least one forced agent-day cannot be seated at `ts` on **any** assignment,
because every pair it could hold obligates it to that seat and no additional seat exists. That
agent-day therefore ends its day short of its expected work slots (a `Contracted hours (under)`
violation, if no other seat can compensate) or occupies an out-of-envelope seat elsewhere (a `Shift
envelope compliance` violation) — either way, a nonzero hard score is unavoidable. The direction of
the inequality only ever makes the check **stricter** if implemented as a blocking gate — like R0's
own day-wide sum, it can refuse more than today, never less, so its risk is false refusal, not false
permission.

**Proven on a hand-built case, not merely asserted**
(`r2ForcedSet_provenOnHandBuiltCase`, using the healthy staggered desk's own construction): at 08:00
(reachable only by "Morning"), exactly the 5 Morning agent-days are forced — measured, not assumed,
by literally computing the predicate. At 13:00 (legal for both templates), all 10 agent-days are
forced. At 18:00 (reachable only by "Afternoon"), exactly the 5 Afternoon agent-days are forced. In
every case R2's own count matches construction exactly, and since seats were placed to meet or exceed
each of these forced counts, R2 correctly does not refuse this desk.

**Measured, not merely argued, on the corpus**: R2's false-refusal count is **zero** across every
`KNOWN-SOLVES` fixture measured, and it refuses the one `KNOWN-COLLAPSES` fixture measured — the
outcome the necessary-condition argument predicts. This is the standard plan 15-11 held the original
day-wide check to (a genuine necessary condition, argued from the model's own zero-slack identity),
and R2 meets the identical bar for the finer-grained, per-timeslot version of the same question.

## 5. Recommendation

**Adopt R2 (the forced-occupancy necessary condition) as an additional, per-timeslot blocking check
alongside the existing day-wide sum (R0) — but this analysis does not implement it.** That is plan
15-20's decision to make with this table in hand, per this plan's own scope boundary (candidate rules
live in test source in this plan; moving one into `SolverService` is deliberately out of scope here).

**Confidence, stated in terms of corpus size, not hidden behind it.** This recommendation rests on
**four date-slices across three fixtures** — one collapsing, three solving. That is a small corpus.
R2's zero-false-refusal result is **not** a statistical claim strengthened by sample size; it follows
from R2 being a *proven* necessary condition (§4's argument), so a single counterexample would
falsify it structurally, not merely dilute a rate. The corpus's real job here was to confirm the
argument's *predictions* match measured solver behaviour on both a collapsing and several solving
fixtures — which it did — not to establish a statistically confident false-refusal rate across desk
shapes this analysis never constructed (multi-template desks beyond two, mixed specializations,
weekday-restricted templates, bounded slack in combination with R2). Those remain untested by this
plan and are named individually in §7.

**Do not adopt R1** (the naive tightest-hour promotion) in its measured form. The table shows it
would refuse three of four measured `KNOWN-SOLVES` date-slices — a 75% false-refusal rate on this
small corpus, entirely explained by desks with genuinely staggered per-hour headcount by design. This
is the measured version of exactly the risk the gap's own text warned about.

## 6. Verdict on `advisoryOnThinTimeslotDoesNotBlock`

**It should stay exactly as it is: non-blocking.** This is a legitimate, useful outcome of this
analysis, not a failure to deliver a fix, per the gap's own explicit permission for that answer.

The measured reason: the *specific* signal that test protects — the raw tightest-hour seat count,
compared against nothing in particular — is precisely R1 in this analysis, and R1 is measured to
false-refuse 3 of 4 `KNOWN-SOLVES` date-slices in this corpus. Promoting the advisory to blocking **in
its current form** would have been the wrong fix, exactly as the gap's own `fix:` field warned. If a
per-timeslot blocking check is adopted, it should be **R2's forced-occupancy predicate**, not the raw
minimum the advisory currently reports — a materially different computation (R2 compares seats
against the count of agent-days with **no legal alternative**, not against total headcount or the
day's average). Adopting R2 would not change `advisoryOnThinTimeslotDoesNotBlock`'s own fixture's
verdict: that fixture's single agent-day is forced at its 09:00 hour, seats there (1) meet exactly the
forced count (1), so R2 would not refuse it either — the advisory-only design and R2's own
verdict agree on the fixture that named it.

## 7. What This Analysis Does Not Settle

Named individually, not gestured at:

1. **The 23 referenced-but-not-re-instantiated fixtures** (§2.4) were not run against R1/R2/R3. In
   particular, `ShiftDeskEndToEndRegressionTest`'s three genuinely-solved cases are a natural,
   cheap extension of this corpus for whoever next revisits it — they were not incorporated here
   because their fixture-building methods are private to that class, a scope decision named rather
   than worked around.
2. **Multi-template desks beyond two templates**, desks mixing specializations, and desks combining
   R2 with nonzero `envelopeSlackSlots` in more elaborate configurations than this corpus's two
   fixtures are untested. R2's slack-awareness (via the "zero slack for this agent-day" clause) is
   argued and exercised on the healthy staggered desk (which uses zero slack throughout) but not on a
   fixture that mixes forced and slack-bearing agent-days on the *same* date.
3. **R2's interaction with G-15-25** (band-composition blindness, still open) is not evaluated. R2 as
   defined here still reasons over the desk-wide coverage question at the level `coveredOnDate`
   already reasons at; whether R2 needs the per-agent-eligible-pair achievable-assignment computation
   G-15-25's `fix:` field describes, rather than the simpler forced-vs-seats count used here, is an
   open question this plan does not answer.
4. **Runtime cost of R2 in production.** This analysis's R2 implementation iterates every timeslot on
   every date and, per timeslot, every agent-day's eligible pairs — an extra O(timeslots × agent-days
   × pairs) pass beyond today's O(timeslots) day-wide sum. Whether this is negligible at real desk
   scale (138 agent-days, 7 days, ~13 timeslots/day) was not measured.
5. **The live-desk calibration** (per-hour seat model reproducing the live desk's tightest-hour
   advisory figures) is a cross-consistency check between two already-published sequences, not an
   independent replication against a raw per-hour demand table — no such table was recorded anywhere
   in `15-UAT.md` or `HANDOFF.md` for this desk. See `SeatSupplyDistributionAnalysisTest
   #liveDeskCalibration_bothPublishedSequencesShareOneCeilingDerivedDemandSeries` for the derivation
   and this caveat stated in the same place as the result.
6. **Whether R2, if adopted, should replace or supplement R0.** This analysis treats them as
   additive (R0 unchanged, R2 added) throughout; whether R0 becomes redundant once R2 is adopted (R2
   dominating R0's refusal set) was not checked, and is a natural next question for plan 15-20.

## 8. Outcome (Plan 15-20)

**Appended, never an edit to §1-§7 above** — those sections describe the state this document
measured BEFORE plan 15-20 changed production code, and their value is that they predate the
change they justified.

### 8.1 What was implemented

**R2 (the forced-occupancy necessary condition) was adopted exactly as recommended**: as an
ADDITIONAL, per-timeslot blocking check accumulated alongside the pre-existing day-wide sum (R0),
never replacing it. `SolverService.forcedAgentDaysByTimeslotId` (new, package-private) computes,
for each date and each of that date's timeslots, how many rostered agent-days are FORCED there —
every one of the agent-day's `getEligibleShiftBandPairs()` both covers the timeslot and has zero
slack for that agent-day (its own covered-slot count on that date equals the agent-day's
`expectedWorkSlots()` exactly) — and `requireShiftEnvelopeSeatSupply` refuses when that count
exceeds the seats at that timeslot, consolidated to the single worst timeslot per date (largest
forced-minus-seats deficit), mirroring the trailing tightest-hour advisory's own "worst" precedent.
The refusal message names the date, the hour, the forced count and the seat count, and follows the
same weight-aware remedy branching plan 15-18 established (the over-allocation-ceiling suggestion
is withdrawn when `unassignedAssignmentWeight` carries a hard component).

This one check closes BOTH gaps this plan carried, exactly as `15-20-PLAN.md`'s own key_links
predicted: it is band-composition-sensitive by construction (G-15-25, since it is computed
per-agent-day against each agent's own eligible pairs, never a desk-wide union), and it is
evaluated per covered timeslot rather than as a day-wide sum (G-15-31, so it sees distribution
within the day a sum-vs-sum comparison structurally cannot).

**The analysis harness itself was re-measured against the SHIPPED implementation**, not left as a
test-local copy: `SeatSupplyDistributionAnalysisTest`'s R2 row now calls
`SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId` (a new, minimal bridge exposing the
package-private production method, mirroring that class's existing
`requireShiftEnvelopeSeatSupply` bridge exactly), and a new "Shipped gate" row invokes the full,
throwing production method directly — this phase's own threat register (T-15-20-04) names
duplicate rule implementations drifting apart as a defect class already hit three times (G-15-10
root cause B, G-15-21, and the gate's own two `coveredTimeslots` sites before plan 15-18); this is
the specific countermeasure for a fourth recurrence.

### 8.2 The post-change rule-by-fixture table

Re-run this session (`SeatSupplyDistributionAnalysisTest#ruleByFixtureTable_falseAndTrueRefusalCounts`),
against the SAME four-date-slice, three-fixture corpus §2.4 defined (no fixture added or removed):

| fixture | label | R0 (day-wide sum) | R1 (tightest-hour) | R2 (shipped logic) | R3 (warn-only) | **Shipped gate** |
|---|---|---|---|---|---|---|
| distribution-blind (Task 1) | KNOWN_COLLAPSES | PASS | REFUSE | REFUSE | PASS | **REFUSE** |
| healthy staggered desk | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS | PASS |
| LiveShapeShiftDeskFixture day 1 | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS | PASS |
| LiveShapeShiftDeskFixture day 2 | KNOWN_SOLVES | PASS | REFUSE | PASS | PASS | PASS |

**Per-rule false-refusal / true-refusal counts** (denominator: 3 KNOWN-SOLVES, 1 KNOWN-COLLAPSES):

| Rule | False refusals | True refusals |
|---|---|---|
| R0 (shipped day-wide sum) | 0 | 0 |
| R1 (tightest-hour promoted to blocking) | 3 | 1 |
| R2 (forced-occupancy necessary condition, shipped logic) | 0 | 1 |
| R3 (R2, warn-only) | 0 | 0 |
| **Shipped gate (production: R0 + R2 combined)** | **0** | **1** |

**THE DECISIVE ROW is "Shipped gate," not R2 in isolation**: it is the actual, throwing production
method (`SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply`), asserted to refuse the
distribution-blind fixture — which it PASSED in plan 15-19 — with a false-refusal count of exactly
zero against this corpus's three KNOWN-SOLVES fixtures
(`SeatSupplyDistributionAnalysisTest#distributionBlindFixture_shippedGateNowRefusesIt`,
`#ruleByFixtureTable_falseAndTrueRefusalCounts`).

### 8.3 The band-composition figures (G-15-25's decisive evidence)

Added as a first-class, always-re-measured test
(`SeatSupplyDistributionAnalysisTest#bandCompositionExperiment_shippedFigureChangesButUnionStaysSaturated`,
mirrored in `ShiftEnvelopeSupplyGateTest#bandCompositionChangesForcedCountButNotTheSaturatedUnion`),
re-run this session:

| bands | forced-count at 08:00 (shipped) | day-wide (R0) detail |
|---|---|---|
| 3 (breaks 11-12, 12-13, 13-14) | **1** | demand=8 supply=9 |
| 5 (+ edges 08-09, 16-17) | **0** | demand=8 supply=9 |

Two DIFFERENT figures (1, then 0) from two runs differing ONLY in band composition, while the
desk-wide union figure (`day-wide demand=8 supply=9`) stays BYTE-IDENTICAL across both — the exact
inverse of the byte-identical live measurement that filed G-15-25.

### 8.4 What was NOT implemented, named rather than omitted

**§7 item 6 (whether R0 becomes redundant once R2 is adopted) was NOT resolved.** R0 is kept
exactly as the recommendation specified — additive, unchanged, never replaced. This session's own
table (§8.2) shows R0 never independently contributes a refusal beyond what R2 already provides on
this SMALL four-date-slice corpus (R0's own true-refusal count is 0, even on the one
KNOWN_COLLAPSES fixture), but that observation is not evidence R0 is redundant in general — it is
evidence about this corpus's four date-slices, and the corpus was never large enough to settle it
(§7's own honesty framing). R0 stays, per the plan's own instruction, because it is "a sound
necessary condition in its own right" independent of whether R2 happens to dominate it on the
fixtures measured so far.

**§7 items 1-2, 4-5 (the 23 referenced fixtures, multi-template-beyond-two configurations, R2's
runtime cost at real desk scale, and independent live-desk demand-table replication) remain
unmeasured by this plan**, exactly as scoped. Item 4 (runtime cost) is addressed structurally
rather than empirically: `requireShiftEnvelopeSeatSupply`'s threat register (T-15-20-02) bounds the
added cost at O(agent-days × eligible pairs × timeslots) — on the live desk's own numbers roughly
138 × 15 × 13, trivially small — but no wall-clock benchmark was run against it. **§7 item 3 (R2's
interaction with G-15-25) is now settled by construction**, not merely argued: `R2` in production
IS the per-agent-eligible-pairs computation, not the desk-wide `coveredOnDate` question — §1's own
account of "the likely shape is a per-hour (or per-agent-day, per G-15-25's fix) achievable-
assignment check" is exactly what shipped.

### 8.5 `advisoryOnThinTimeslotDoesNotBlock`, settled

**It stays exactly as it is: non-blocking, untouched.** `git diff` on
`src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java`'s
`advisoryOnThinTimeslotDoesNotBlock` method across this plan's commits is empty. §6's prediction —
"Adopting R2 would not change `advisoryOnThinTimeslotDoesNotBlock`'s own fixture's verdict: that
fixture's single agent-day is forced at its 09:00 hour, seats there (1) meet exactly the forced
count (1), so R2 would not refuse it either" — is now a fact about shipped production code, not a
projection from a test-local reimplementation: that test still passes, unedited, against the
SHIPPED per-hour check. Its name was indeed not an accident, and this is the second, independent
confirmation of that (the first being §6's own analysis-time prediction).

### 8.6 False-refusal measurement, headline

Zero. Across every KNOWN-SOLVES fixture in plan 15-19's labelled corpus (3 date-slices), the
SHIPPED gate refuses none of them (§8.2). Corpus size is unchanged from plan 15-19 (4 date-slices,
3 fixtures) — this plan re-measured the existing corpus against production code, it did not grow
the corpus.

---
*Phase: 15-shift-envelope-breaks-library-generation. Analysis for plan 15-19, gap G-15-31. Outcome
appended by plan 15-20, gap closure G-15-25/G-15-31.*
