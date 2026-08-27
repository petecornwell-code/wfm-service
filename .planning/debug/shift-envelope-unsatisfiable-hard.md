---
status: diagnosed
trigger: "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
created: 2026-08-27T00:00:00Z
updated: 2026-08-27T00:00:00Z
---

## Current Focus
<!-- OVERWRITE on each update - reflects NOW -->

bug_class: Bohrbug — deterministic given the data shape; the automated suite's fixture is degenerate so it never triggers.

hypothesis: |
  ZERO-SLACK THEOREM. The value range (AgentShiftAssignment.java:170-175) pins
  netHours(pair) == effectiveHours EXACTLY. Because covers() also excludes the break window,
  the number of grid slots a pair covers equals EXACTLY expectedWorkSlots(dayConfig). So an
  agent with an assigned pair has ZERO placement freedom: they must occupy 100% of the slots
  their pair covers. Any covered slot at which a seat is not available to them (seat supply is
  derived from DEMAND, not agent supply) forces a choice between +1 hard (Shift envelope
  compliance) and +100 hard (Contracted hours (under)). The solver takes +1 hard, permanently.
  COROLLARY: if no pair's netHours equals effectiveHours the range is EMPTY, the pair stays
  null, and every seat of that agent-day is penalised — also permanently.
test: characterising JUnit test with a counting lemma (seats inside envelope < contracted slots) plus a real solve through solverConfig.xml, asserting hard != 0 and the residual is attributed to "Shift envelope compliance".
expecting: hard == -4 attributed entirely to Shift envelope compliance in the demand-shortfall case; hard <= -15 in the empty-value-range case.
next_action: write src/test/java/com/wfm/solver/ShiftEnvelopeUnsatisfiableHardTest.java and run it.

## Symptoms
<!-- Written during gathering, then IMMUTABLE -->

expected: A real desk in shift-scheduled mode solves to FEASIBLE (0 hard) in acceptable time. Every agent seated only within the envelope of the single shift assigned to them that day.
actual: Solve produces a best-so-far solution retaining hard penalty attributed to "Shift envelope compliance". Screenshot (src/main/resources/sample-data/Example.png) shows group "Late · 12:00-21:00 · 10 agent(s)", every agent contracted 8.0h, but two agents (Evelina Yasinchuk, Melina Noemi Aparicio) seated LEFT of 12:00 — outside the envelope. Since shiftEnvelopeComplianceWeight is ofHard(1), a feasible solve could never produce this.
errors: None — no crash, no exception. Residual hard penalty only.
reproduction: UAT Test 10, live dev deploy https://d2bbtcc80peap7.cloudfront.net with real production-shaped cloud data. Automated suite only exercises a 4-agent x 2-day shift benchmark and passes.
started: Discovered during UAT of Phase 15.

## Eliminated
<!-- APPEND only - prevents re-investigating -->

- hypothesis: "H1 literal form — an agent's contracted hours EXCEED the net hours of the band pair they hold, so they must overflow."
  evidence: REFUTED. AgentShiftAssignment.getEligibleShiftBandPairs() (AgentShiftAssignment.java:170-175) filters the value range to pairs whose netHours EXACTLY equals dayConfig.effectiveHours() (BigDecimals.normalize + compareTo == 0, no tolerance). A non-null pair can therefore NEVER have net hours below the contract. H1 must be restated as the zero-slack theorem (see Resolution).
  timestamp: T1

- hypothesis: "H4 — the two anomalous agents have a null shiftBandPair, so every seat of their agent-day is penalised."
  evidence: REFUTED for the live desk, quantitatively. CASE 2 of the characterising test measures the null-pair cost as EXACTLY expectedWorkSlots per agent-day (-15 hard for a 7.50h agent on a 30m grid). The Stubhub export shows 8 seats + one 60m break per agent-day, i.e. a 60-MINUTE grid, so one null agent-day costs 8 hard and a full 7-day week costs 56 — more than the observed -19 total. An hours mismatch is a property of the CONTRACT, so it would recur every working day rather than on one. The export also shows every agent-day at Hours = 8.00 against templates netting exactly 8.00, so no mismatch exists. -19 is consistent with ~1-3 stray seats across each of the 7 anomalous agent-days, with zero null pairs.
  timestamp: T4

- hypothesis: "(i) ENVELOPE CAPACITY is a contributing cause of the live -19."
  evidence: REFUTED as the live cause (confirmed as a latent defect). Every shipped Stubhub template (08:00-17:00, 10:00-19:00, 11:00-20:00, 12:00-21:00) is 9h with a 60m band, netting exactly 8.00h against 8.00h contracts, and all four fit inside the desk's 08:00-21:00 operating window. There is no capacity shortfall on this desk. CASE 3 proves the defect is nonetheless REACHABLE and unguarded, but it is not what is firing at Stubhub.
  timestamp: T5

## Evidence
<!-- APPEND only - facts discovered -->

- timestamp: T0
  checked: src/main/java/com/wfm/model/ShiftBandPair.java:31-47 covers()
  found: covers(ts) returns false when slot is outside [template.start, template.end) AND ALSO false when the slot overlaps the band break window (lines 40-46).
  implication: The set of timeslots an agent-day may legally occupy = envelope slots MINUS break slots = exactly the pair's NET slots. Confirms the "legal seat cap" half of H1.

- timestamp: T0
  checked: ScheduleConstraintProvider.java:413-424 shiftEnvelopeCompliance
  found: penalizeConfigurable per (AgentShiftAssignment, ScheduleConfig, AgentAssignment) tuple where pair==null OR !pair.covers(slot). Uses forEachIncludingUnassigned so a null pair penalises EVERY seat of that agent-day.
  implication: Each seat outside the net window costs 1 hard.

- timestamp: T0
  checked: ScheduleConstraintProvider.java:462-521 contractedHoursOver/Under/UnderZero
  found: Over is a penalty on count > expectedWorkSlots; Under on count < expectedWorkSlots; UnderZero on count == 0. All are hard.
  implication: The seat COUNT for an agent-day is hard-pinned to exactly expectedWorkSlots(dayConfig), independent of any shift/band assignment.

- timestamp: T2
  checked: AgentShiftAssignment.java:165-178 getEligibleShiftBandPairs()
  found: The value range is filtered to pairs whose normalize(netHours) EXACTLY equals normalize(effectiveHours), plus template.isEffectiveOn(date). No tolerance. An empty result is documented as intended behaviour (javadoc lines 160-163) — "the variable stays unassigned, it never throws".
  implication: (a) H1's literal form is impossible; (b) contracted hours are never an independent input — they are DERIVED from the envelope; (c) an hours mismatch degrades SILENTLY to a null pair.

- timestamp: T3
  checked: ShiftTemplateService.validateGridAlignment (:216-255), isAligned (:263-271), ShiftLibraryValidationService.isTemplateAligned (:251-271), advisoryMessage (:415-420)
  found: Save-time validation checks ONLY that template start/end and band break boundaries are an exact multiple of incrementMinutes from the grid START. `TimeslotBoundsResponse.endTime()` is never read by ANY caller in src/main (verified by grep). getLiveBounds() returning empty skips the check entirely. The hours-mismatch message reads "It will still save".
  implication: Nothing anywhere validates that a template's envelope FITS INSIDE the desk's operating window, and nothing blocks a solve. H2 CONFIRMED — advisory only, no solver-time guard.

- timestamp: T4
  checked: src/test/java/com/wfm/solver/ShiftEnvelopeUnsatisfiableHardTest.java — 6 cases, all passing
  found: |
    LEMMA (universal sweep over 15/30/60m grids x 6/8/9/10/12h envelopes x 0/30/60/120m breaks):
      covered slots == expectedWorkSlots in EVERY admissible configuration. Slack is structurally impossible.
    CASE 1 (seat supply): score -4hard/-6008soft, hardBreakdown {Shift envelope compliance=-4}.
    CASE 2 (null pair):   score -15hard/-11soft, hardBreakdown {Shift envelope compliance=-15}.
    CASE 3 (envelope capacity, seats abundant): -4hard, {Shift envelope compliance=-4}; identical at
      2,000 and 40,000 step budgets — IRREDUCIBLE. Contracted hours exactly satisfied (16 seats/agent).
  implication: All three mechanisms produce the live fingerprint — envelope compliance ALONE, contracted hours fully satisfied. Reproduced at small scale.

- timestamp: T5
  checked: envelopeBreachSymmetry() discriminator asserted across all three cases
  found: |
    Because contracted hours pin |held| == expectedWorkSlots, the pair (|held\legal|, |legal\held|)
    separates the mechanisms cleanly:
      SEAT SUPPLY      -> |legal| == expectedWorkSlots -> EQUAL      (measured 4 and 4)
      ENVELOPE CAPACITY-> |legal| <  expectedWorkSlots -> illegal > surrendered (measured 2 and 0)
      NULL PAIR        -> |legal| == 0                 -> maximally asymmetric (measured 15 and 0)
  implication: The coordinator's predicted identity holds, but ONLY under the seat-supply mechanism — which makes it a live diagnostic, not just a restatement. A desk reading -19 with exactly 19 unworked in-envelope slots is seat-supply, has no null-pair agent-days, and has no capacity shortfall.

- timestamp: T6
  checked: Stubhub live export vs the three mechanisms
  found: Templates 08:00-17:00 / 10:00-19:00 / 11:00-20:00 / 12:00-21:00, all 9h with a 60m band -> net 8.00h == contracted 8.00h, all fitting inside the 08:00-21:00 operating window. Export shows 8 seats + one 60m break per agent-day, i.e. a 60-MINUTE grid, so expectedWorkSlots = 8 and one null agent-day would cost 8 hard (a full week for one agent, 56) against an observed total of -19.
  implication: Mechanism (i) NOT operative (no capacity shortfall). H4/null-pair NOT operative (arithmetic and the fact that an hours mismatch would recur daily). The live -19 is mechanism (ii) seat supply, amplified by the universal zero-slack property.

- timestamp: T7
  checked: CASE 3 stray placement, and ScheduleOutputService.java:174-175
  found: The 4 forced strays split 2 inside the agent's own BREAK window and 2 before the envelope START — both shapes in a single solve. shiftEnvelopeCompliance is a flat ofHard(1) per seat with no notion of which kind of breach occurred, so the solver is indifferent between them. Separately, the displayed shift span is min/max over held seats, not the assigned template.
  implication: Explains BOTH live anomaly shapes (early 08:00/09:00 strays AND Mariami Katcheishvili 01-10 seated straight through her break with no hole), and explains why the UI cannot render an envelope violation as a violation.

## Resolution
<!-- OVERWRITE as understanding evolves -->

root_cause: |
  (1) ZERO-SLACK BY CONSTRUCTION (the amplifier, this lane's finding).
      AgentShiftAssignment.getEligibleShiftBandPairs() (:170-175) admits only pairs whose netHours
      EXACTLY equals the agent-day's effectiveHours. Since ShiftBandPair.covers() also excludes the
      break window, the number of slots a pair legally covers equals EXACTLY
      expectedWorkSlots(dayConfig). Proven universal by sweep across every plausible grid/envelope/
      break combination. An agent therefore has ZERO placement freedom: they must occupy 100% of
      their legal slots. There is no desk configuration an operator could choose that would absorb
      even ONE missing seat inside the envelope.
  (2) COST ARBITRAGE (why the residual lands where it does).
      shiftEnvelopeComplianceWeight is ofHard(1) — the LOWEST hard weight in ConstraintWeights,
      against contractedHoursUnder ofHard(100), noOverlap ofHard(1000), contractedHoursOver
      ofHard(1001), agentDayOff ofHard(10_000). So whenever the two conflict, the solver pays 1
      hard per out-of-envelope seat rather than 100 per missing contracted slot. Contracted hours
      come out EXACT and the entire residual is parked on the phase's headline guarantee.
  (3) NO SOLVER-TIME GUARD (H2 confirmed).
      Hours mismatch is a Shift Library page advisory that explicitly "will still save"; envelope
      containment within the operating window is never checked at all (TimeslotBoundsResponse
      .endTime() is dead code); an empty value range degrades silently to a null pair.
  Live Stubhub -19 is mechanism (ii) seat supply (sibling lane) acting through amplifier (1) and
  routed by (2). Mechanisms (i) and null-pair are refuted for that desk but are latent and unguarded.

fix: NOT APPLIED — diagnose-only mode. See "Suggested Fix Direction" in the report.
verification: 6/6 characterising tests pass; no production source modified (git status clean apart from the new test and this file).
files_changed: []
