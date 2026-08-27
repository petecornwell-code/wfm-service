---
status: diagnosed
trigger: "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
created: 2026-08-27T00:00:00Z
updated: 2026-08-27T00:00:00Z
lane: "minimum-staffing seat expansion + per-timeslot demand configuration"
goal: find_root_cause_only
---

## Current Focus

hypothesis: |
  On a SHIFT desk the zero-demand hour is the cheapest place in the whole grid to park an
  out-of-envelope agent, because (a) SolverService manufactures exactly one seat there
  regardless of any envelope, (b) it is the only timeslot with NO over-allocation ceiling at
  all (no TimeslotDemandConfig row => the bulk constraints' inner join produces no tuple), and
  (c) minimumStaffing pays 1000 soft for filling it while shiftEnvelopeCompliance charges only
  1 hard for the violation.
test: |
  Two characterising test classes, both green (9/9):
  - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatGapTest.java (3 tests)
  - src/test/java/com/wfm/solver/ZeroDemandTimeslotHasNoCeilingGapTest.java (6 tests)
expecting: All three hypotheses CONFIRMED with executable evidence.
next_action: |
  Return ROOT CAUSE FOUND to the orchestrator. Diagnose-only mode — no fix applied.

bug_class: Bohrbug — fully deterministic, reproduces from data shape alone (desk operating
  window wider than the union of shift envelopes, plus at least one zero-FTE demand cell). Not
  timing-dependent, not flaky.

reasoning_checkpoint:
  hypothesis: |
    expandMinimumStaffingSeats manufactures a fillable seat on every zero-demand timeslot with
    no knowledge of shift envelopes; the resulting seat is uniquely un-penalised by the bulk
    allocation constraints and uniquely rewarded (1000 soft) by minimumStaffing, while the only
    constraint opposing it (shiftEnvelopeCompliance) is the cheapest hard weight in the system
    at 1 hard.
  confirming_evidence:
    - "SolverService.java:1175-1180 — the method's parameter list contains no SchedulingMode / ShiftTemplate / ShiftBandPair (asserted by test)"
    - "SolverService.java:322 vs 337 — demand configs computed before filler seats, by explicit design comment at 334-336"
    - "ConstraintVerifier: bulkOverallocationLimit penalizesBy(0) at 3 agents with no config row; penalizesBy(3) with a zero-FTE row present — the row's ABSENCE is the cause, not the zero value"
    - "ConstraintWeights.java:126/138 + V37/V41 DB defaults — minStaffing 0hard/1000soft, shiftEnvelopeCompliance 1hard/0soft, contractedHoursUnder 100hard"
    - "ShiftLibraryValidationService.java:88-89 — 'Only rows with requiredFTEs > 0 count as demand', so the SHIFT-mode gate certifies coverage over demand hours only"
  falsification_test: |
    Would be refuted if a TimeslotDemandConfig row existed for a zero-demand timeslot (it does
    not — the control test proves the constraint would then fire), or if
    expandMinimumStaffingSeats consulted shift state (it has no parameter through which it
    could), or if shiftEnvelopeCompliance outweighed contractedHoursUnder (1 vs 100, it does
    not).
  fix_rationale: n/a — diagnose-only mode, no fix applied.
  blind_spots: |
    - No end-to-end solve reproduces the full symptom; the evidence is per-constraint arithmetic
      plus data-shape proof, not a converged solve.
    - The "then adds breaks to fill in the gaps" half of the report is grid/break-derivation
      behaviour and belongs to another lane; not investigated here.
    - The exact live demand curve behind Example.png was not read, so which specific columns
      carry literal zero vs thin demand is inferred, not measured.
  candidate_causes:
    - "code — expandMinimumStaffingSeats is envelope-blind (SolverService.java:1175)"
    - "code/data — computeTimeslotDemandConfigs omits zero-demand timeslots, so both bulk constraints inner-join to nothing"
    - "config — shiftEnvelopeComplianceWeight defaults to 1hard (V41) against contractedHoursUnder's 100hard and minStaffing's 1000soft"
    - "data — a desk whose demand file has zero cells outside the union of its shift envelopes; the Phase 14 coverage gate explicitly permits this"
  and_gate: |
    YES — this needs all four simultaneously. Remove the filler seat and there is no entity to
    fill. Give the zero-demand hour a TimeslotDemandConfig(ts, 0) and over-allocation charges
    1 hard, doubling the cost of the parking spot. Raise shiftEnvelopeComplianceWeight above
    contractedHoursUnderWeight and the trade reverses. Give the desk a shift library spanning
    the whole operating window (as ShiftModeFixtures does) and no out-of-envelope hour exists.
    Single-cause framing is wrong here; root_cause is recorded as a set.

## Symptoms

expected: |
  A real desk in shift-scheduled mode solves to a feasible schedule; agents are seated only
  within their shift envelope and zero-demand hours do not distort the schedule.
actual: |
  Solve does not crash but produces a best-so-far solution still carrying hard penalty
  (shiftEnvelopeCompliance). Agents are seated in zero/thin-demand hours OUTSIDE their shift
  envelope, and breaks are then placed to fill the resulting gaps.
errors: "None — no exception. Hard score non-zero on the best-so-far solution."
reproduction: |
  UAT Test 10 (.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md), run
  against the live dev deployment https://d2bbtcc80peap7.cloudfront.net with real
  production-shaped cloud data. NOT reproducible in the automated suite, which only ever ran a
  4-agent x 2-day shift benchmark (ShiftModelBenchmarkTest).
started: "Discovered during UAT of Phase 15 (shift envelope / breaks / library generation)."
visual_evidence: |
  src/main/resources/sample-data/Example.png — Agent Allocation grid, group
  "Late · 12:00-21:00 · 10 agent(s)". Two agents (Evelina Yasinchuk, Melina Noemi Aparicio) are
  SEATED in a column left of 12:00 (outside the Late envelope). Column totals: 4, 2, 4, 8 for
  the early columns, then 17, 16, 15, 14, 8, 16, 16, 14, 10. The early thin columns are exactly
  where the out-of-envelope agents were pulled.

## Eliminated

## Evidence

- checked: SolverService.java:320-344 (call ordering)
  found: |
    `computeTimeslotDemandConfigs(demandAssignments)` at line 322 runs over demand-derived
    assignments ONLY. `expandMinimumStaffingSeats` at line 337 runs at 10d, AFTER it, and the
    inline comment at 334-336 says this is deliberate: "Deliberately AFTER
    computeTimeslotDemandConfigs above, so these seats are never counted as demand".
  implication: A zero-demand timeslot can never acquire a TimeslotDemandConfig row.

- checked: SolverService.java:753-763 (computeTimeslotDemandConfigs body)
  found: |
    Builds `demandPerTimeslot` by merging over the passed assignment list; emits one
    TimeslotDemandConfig per KEY PRESENT. A timeslot with no demand assignment is absent.
    FteUploadService.java:202 `if (fteValue <= 0) continue;` — no StaffingRequirement row is
    ever persisted for a zero cell, so expandAssignments creates no seat for it.
  implication: Zero-demand timeslot => zero TimeslotDemandConfig rows. Confirmed.

- checked: ScheduleConstraintProvider.java:528-546 and 558-576
  found: |
    Both bulkOverallocationLimit and bulkUnderallocationHard `.join(TimeslotDemandConfig.class,
    equal(... TimeslotDemandConfig::timeslot))` — an INNER join. No config row => no tuple =>
    constraint emits nothing at any weight.
  implication: |
    A zero-demand timeslot has NO over-allocation ceiling at all. This is not "a ceiling of
    zero" — it is the total absence of the constraint. H1 CONFIRMED.

- checked: SolverService.java:1175-1215 (expandMinimumStaffingSeats signature + body)
  found: |
    Params are (tenantId, deskId, scheduleId, timeslots, existingAssignments,
    staffingRequirements, specializations). No SchedulingMode, no ShiftTemplate, no
    ShiftBandPair, no AgentShiftAssignment. Body iterates EVERY timeslot in `timeslots` and
    tops each one up to MIN_AGENTS_PER_TIMESLOT (= 1, ScheduleConstraintProvider.java:28).
  implication: |
    Envelope-blind by construction — it cannot consult a shift envelope because it is never
    given one. H3 CONFIRMED.

- checked: ConstraintWeights.java:105, 115, 126, 138 (defaults)
  found: |
    bulkOverallocationLimitWeight = ofHard(1)
    bulkUnderallocationHardWeight = ofHard(1)
    minStaffingWeight            = ofSoft(1000)
    shiftEnvelopeComplianceWeight = ofHard(1)
    contractedHoursUnderWeight   = ofHard(100)
    contractedHoursUnderZeroWeight = ofHard(100)
    contractedHoursOverWeight    = ofHard(1001)
  implication: |
    Seating an agent outside their envelope costs 1 hard PER SLOT. Leaving that agent short of
    contracted hours costs 100 hard PER SLOT. A 100:1 asymmetry in favour of breaking the
    envelope. shiftEnvelopeCompliance is the CHEAPEST hard constraint in the file.

- checked: ShiftBandPair.java:31-47 (covers)
  found: |
    covers() is false for a slot outside [template.start, template.end) AND false for any slot
    overlapping the chosen band's break interval.
  implication: |
    A zero-demand hour inside the envelope but inside the break band is ALSO an envelope
    violation if seated. The filler seat is created there too.

- checked: ScheduleConstraintProvider.java:578-633 (minimumStaffing javadoc + body)
  found: |
    The javadoc's entire justification is slot-mode reasoning: "because an 8h shift plus a 1h
    break spans exactly 9 of a 13h window, the solver packs every shift against the first
    non-zero hour ... leaves no cover at all for walk-ins, spillover or a forecast that simply
    understates the early hours." In SHIFT mode that packing is no longer the solver's free
    choice — the operator's shift library dictates it — so the premise is void.
    The javadoc also asserts the seat is safe because "Penalising an hour the solver cannot
    staff would only make the schedule permanently infeasible" — but on a shift desk the solver
    CAN staff it, only by breaking a hard constraint.
  implication: The stated design rationale does not survive shift envelopes. H2 basis.

- checked: ScheduleConstraintClassification.java:248-252 (Phase 14 plan 02 classification)
  found: |
    "Minimum staffing" is classified MODE_AGNOSTIC — "Per-timeslot floor of at least one
    assigned agent, irrespective of forecast; mode-independent." Phase 15 reclassified
    honourPreferredStartTime/honourPreferredBreakTime/the break constraints but left this row
    untouched.
  implication: The classification that would have caught this asserted the opposite.

- checked: .planning/research/SPIKE-COUPLING.md:424-435 ("What remains open", item 2)
  found: |
    Verbatim: "It did not exercise ScheduleConstraintProvider's break constraints,
    contracted-hours constraints, `minimumStaffing`, or the `MIN_AGENTS_PER_TIMESLOT`
    seat-expansion in `SolverService`. Whether the envelope constraint interacts badly with the
    break window or contracted-hours logic is unknown."
  implication: |
    This exact interaction was named as an open risk BEFORE Phase 15 was planned, and Phase 15
    never closed it. `expandMinimumStaffingSeats` / MIN_AGENTS_PER_TIMESLOT appear nowhere in
    any Phase 15 planning artifact (grep over .planning/phases/15-*).

- checked: src/test/java/com/wfm/solver/ShiftModeFixtures.java:58-59, 123, 160-164, 245-246
  found: |
    OPERATING_START=08:00, OPERATING_END=17:00, and EVERY template is built as
    template(..., OPERATING_START, OPERATING_END) — the envelope IS the whole operating window,
    so no timeslot outside every envelope can exist in any shift-mode test.
    Line 245 sets timeslotDemandConfigs from `seats` only; the fixture NEVER calls
    expandMinimumStaffingSeats, so production's filler seats are absent from every shift test.
    Line 160-164 skips the shared break window with the comment "nobody works the shared break
    window -- zero demand, not a zero-fill seat" — the fixture explicitly opts out of the
    production behaviour it should have been exercising.
  implication: |
    ShiftModelBenchmarkTest, ShiftEnvelopeGroundTruthTest and BreakClusteringConstraintTest all
    share this fixture. The 4-agent x 2-day benchmark could not have reproduced the defect at
    any scale — the shape is excluded by construction, not by size.

- checked: |
    Live dev evidence supplied by coordinator — desk Stubhub (EN), 2026-01-05..11, 20m41s:
    Hard -19, Soft -89, NOT FEASIBLE, flat at -19 since 15m. UI: "Violated hard constraints:
    Shift envelope compliance" alone.
  found: |
    The hard weights fingerprint uniquely. -19 with shiftEnvelopeCompliance=1hard means exactly
    19 out-of-envelope seats, and NOTHING else: contractedHoursOver (1001), contractedHoursUnder
    (100), contractedHoursUnderZero (100), agentDayOff (10_000) and noOverlap (1000) cannot hide
    inside 19. Every agent is therefore working EXACTLY contracted hours while 19 of those seats
    sit outside an envelope.
  implication: |
    Confirms the trade is being made deliberately by the solver, not missed. Also confirms the
    penalty is a FLOOR (flat for 5.5 min while soft kept moving), not slow convergence.

- checked: AgentShiftAssignment.java:166-178 (getEligibleShiftBandPairs) — found while building the repro
  found: |
    The value range is filtered to pairs whose net hours EXACTLY equal the agent-day's effective
    hours (BigDecimals.normalize + compareTo == 0, no tolerance).
  implication: |
    CORRECTS the coordinator's chain step 3. A legally-shifted agent's envelope ALWAYS covers
    exactly as many timeslots as they have contracted slots — per-agent envelope length can
    never be the shortage. The shortage is SHARED SEAT CAPACITY: the AgentAssignment entities at
    those covered timeslots are fewer than agentCount x contractedSlots. Stated exactly:
      in-envelope seat supply = SUM over covered timeslots of ceil(demandFTEs * overallocPct/100)
      contracted demand       = agentCount * contractedSlots
      deficit                 = max(0, contracted demand - in-envelope seat supply)
    Second finding from the same fact: an agent whose contracted hours match NO template's net
    hours gets an EMPTY value range, keeps shiftBandPair == null, and shiftEnvelopeCompliance
    then flags EVERY seat they hold (measured: -10 hard for 2 agents x 5 seats). Phase 14's D-06
    makes that mismatch advisory at save time only. Adjacent defect, pinned by test, not the
    main root cause.

- checked: |
    src/test/java/com/wfm/service/ShiftEnvelopeSeatStarvationTest.java — full solve through the
    SHIPPED solverConfig.xml, filler seats created by the real
    SolverService.expandMinimumStaffingSeats. 6/6 green.
  found: |
    Desk: operating 06:00-14:00 hourly, library = one template Late 10:00-14:00 (net 4.00h),
    demand 2 FTE at 10..13 only, 06:00-09:00 zero demand, overalloc 100%.
    3 agents x 4 contracted slots = 12 vs in-envelope supply 8 -> deficit 4.
      * solved hard score = -4 EXACTLY, and getConstraintMatchTotalMap contains ONLY
        "Shift envelope compliance" at -4 — reproduces the live UI fingerprint verbatim
      * every agent holds exactly 4 seats (no contractedHours* term in the total at all)
      * every shift row holds a non-null pair (the null-shift defect is excluded)
      * the 4 seats outside the envelope are exactly the 4 manufactured filler seats
      * 5_000 steps and 50_000 steps both return -4 — flat under a 10x budget
      * solver log reaches "-4hard/0soft": soft is fully optimised, matching the live desk's
        small -89 soft against a stuck hard
    CONTROL: same desk, in-envelope demand 2 -> 3 FTE (supply 8 -> 12, deficit 0) solves to
    0 hard, and all 4 filler seats are left EMPTY at a standing cost of 4000 soft — proving the
    1000-soft bait is refused whenever it costs hard, and that the defect is the seat deficit,
    not the bait.
    QUANTIFIED: holding supply at 8 and walking headcount 2 -> 3, the hard floor tracks
    -max(0, 4*agents - 8) point for point.
  implication: ROOT CAUSE REPRODUCED AND QUANTIFIED. The hard score IS the seat deficit.

- checked: |
    Same test class — raising shiftEnvelopeComplianceWeight to ofHard(1000) on the deficit-4 desk.
  found: |
    Hard score becomes -400, and getConstraintMatchTotalMap now contains ONLY
    "Contracted hours (under)". The violation MIGRATED; it did not disappear.
  implication: |
    The tempting one-line remedy ("make the envelope expensive") is refuted empirically. When
    in-envelope seat supply is genuinely short, the deficit must land on SOME hard constraint;
    the weight only chooses which one, and choosing contractedHoursUnder is 100x worse. Any
    remediation must ADD in-envelope seat capacity or REMOVE the out-of-envelope seats — not
    re-rank the weights.

- checked: ConstraintWeights.java — full hard-weight ladder
  found: |
    agentDayOff 10_000 > contractedHoursOver 1001 > noOverlap 1000 > contractedHoursUnder 100 =
    contractedHoursUnderZero 100 = exactlyOneBreak 100 > breakDuration/breakBlockedWindow/
    breakAlignment 10 > specMatch 1 = bulkOverallocationLimit 1 = bulkUnderallocationHard 1 =
    shiftEnvelopeCompliance 1 = bandCapacity 1.
  implication: |
    shiftEnvelopeCompliance is not uniquely lowest — it TIES for lowest in a five-way bottom
    tier. The pairing that matters is envelope(1) vs its direct antagonist
    contractedHoursUnder(100). Phase 15's own javadoc calls the envelope "the hard constraint the
    whole Option A coupling rests on" and "an illegal schedule, not a preference", while the
    encoded model says breaking it is 100x more acceptable than leaving an agent one slot short.
    Stated intent and encoded intent contradict each other — a real modelling defect, but per the
    test above, NOT the one to fix first.

## Resolution

root_cause: |
  Four contributing causes, all required simultaneously (AND-gate fired):

  RC-1 (code) — `SolverService.expandMinimumStaffingSeats` (SolverService.java:1175) is
  structurally envelope-blind. Its parameter list carries no SchedulingMode, ShiftTemplate or
  ShiftBandPair, so on a SHIFT desk it manufactures one fillable AgentAssignment on EVERY
  timeslot short of MIN_AGENTS_PER_TIMESLOT — including hours no shift envelope can legally
  reach, and including hours inside a chosen break band (ShiftBandPair.covers() excludes both).

  RC-2 (code) — `computeTimeslotDemandConfigs` (SolverService.java:753) emits a
  TimeslotDemandConfig only for timeslots PRESENT in the demand-derived assignment stream. A
  zero-FTE cell is skipped by FteUploadService.java:202, so a zero-demand timeslot gets no row.
  `bulkOverallocationLimit` (:532) and `bulkUnderallocationHard` (:562) both reach that fact
  through an inner `.join(...)`, so BOTH are silent there. This is not a ceiling of zero — it
  is the total absence of any ceiling. The zero-demand hour is the only place on the grid where
  an extra body is free.

  RC-3 (config) — the default weight ladder makes breaking the envelope the rational choice.
  shiftEnvelopeCompliance = 1hard (V41 DB default '1hard/0soft'), the cheapest hard weight in
  the file, against contractedHoursUnder / contractedHoursUnderZero = 100hard and
  minStaffing = 0hard/1000soft (V37). Parking a contracted agent outside their envelope costs
  1 hard per slot; leaving them short costs 100 hard per slot — a 100:1 push out of the
  envelope. Among out-of-envelope destinations the zero-demand hour wins on the HARD level
  (1 hard, vs 2 hard for a thin-demand hour already at its 130% ceiling) before the 1000-soft
  minimumStaffing bonus is even consulted.

  RC-4 (data/contract) — the Phase 14 SHIFT-mode gate certifies coverage over demand-bearing
  hours ONLY. ShiftLibraryValidationService.java:88-89: "Only rows with requiredFTEs > 0 count
  as demand — a zero-FTE row is not demand." So the system explicitly blesses a shift library
  that leaves zero-demand hours outside every envelope, then SolverService manufactures seats
  on precisely those hours. The two guarantees contradict each other.

  Answer to the key question — today, a zero-demand timeslot outside every shift envelope gets
  exactly one manufactured seat, no allocation ceiling, and a 1000-soft standing offer to
  whoever will sit in it for 1 hard. It should get no seat at all: on a SHIFT desk the operator
  expresses coverage intent through the shift library, and an hour the library does not reach
  is an hour the operator has decided not to staff.

  RC-5 (the mechanism that closes the loop, proven by full solve) — the hard score IS the
  in-envelope seat deficit. Because the shift value range filters on EXACT net-hours equality
  (AgentShiftAssignment.java:166-178), a legally-shifted agent's envelope always covers exactly
  as many timeslots as they have contracted slots, so the shortage is never per-agent envelope
  length — it is shared seat capacity:

      in-envelope seat supply = SUM over covered timeslots of ceil(demandFTEs * overallocPct/100)
      contracted demand       = agentCount * contractedSlots
      deficit                 = max(0, contracted demand - in-envelope seat supply)
      hard score              = -deficit   (measured, exactly, across a headcount sweep)

  Each surplus agent-slot then picks the cheapest hard violation available — 1 (envelope) over
  100 (contractedHoursUnder) — and lands on a manufactured zero-demand filler seat, which is
  the only seat on the grid with no over-allocation ceiling to push back (RC-2). Raising
  shiftEnvelopeComplianceWeight does NOT fix this: measured, it moves the deficit onto
  "Contracted hours (under)" at -400 instead of -4.

fix: "(not applied — diagnose-only mode)"

verification: |
  15/15 tests green via ./gradlew test:
  - com.wfm.service.ShiftEnvelopeSeatStarvationTest (6) — full solves through the SHIPPED
    solverConfig.xml; reproduces the live fingerprint (hard = -deficit, "Shift envelope
    compliance" as the ONLY violated hard constraint, every agent at exactly contracted hours),
    proves the floor is flat under a 10x step budget, and refutes the weight-raising remedy.
  - com.wfm.service.ShiftModeMinimumStaffingSeatGapTest (3) — envelope-blind seat creation.
  - com.wfm.solver.ZeroDemandTimeslotHasNoCeilingGapTest (6) — the missing ceiling.
  Falsification controls included and passing:
   * a TimeslotDemandConfig(ts, 0) row DOES produce the penalty the missing row suppresses —
     the row's absence, not its value, is the cause;
   * widening in-envelope demand 2 -> 3 FTE takes the SAME desk to 0 hard with all filler seats
     left empty at 4000 soft — the 1000-soft bait is refused whenever it costs hard, so the
     defect is the seat deficit and not the bait.

files_changed:
  - src/test/java/com/wfm/service/ShiftEnvelopeSeatStarvationTest.java (new, root-cause reproduction)
  - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatGapTest.java (new, characterising)
  - src/test/java/com/wfm/solver/ZeroDemandTimeslotHasNoCeilingGapTest.java (new, characterising)
