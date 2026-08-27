---
status: diagnosed
trigger: "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
created: 2026-08-27T00:00:00Z
updated: 2026-08-27T00:00:00Z
goal: find_root_cause_only
lane: "break geometry governance in SHIFT mode — what enforces break shape once slot-mode constraints are gated off"
---

## Current Focus

bug_class: Bohrbug (deterministic — modelling/specification defect, reproduces from a fixed assignment
  pattern; no timing or environment dependence). SBFL skipped: no failing test exists — the suite is
  505/505 green, which is itself the finding.

hypothesis: |
  In SHIFT mode the geometry of an agent's NON-WORKED time is not governed by any constraint. It is
  only an arithmetic BY-PRODUCT of three independent hard constraints holding SIMULTANEOUSLY:
    (1) shiftEnvelopeCompliance (ofHard 1)  => held seats S is a SUBSET of legal slots L
    (2) contractedHoursOver     (ofHard 1001) => |S| <= expectedWorkSlots
    (3) contractedHoursUnder    (ofHard 100)  => |S| >= expectedWorkSlots
  plus the value-range filter AgentShiftAssignment.getEligibleShiftBandPairs (netHours EXACTLY ==
  effectiveHours), which makes |L| == expectedWorkSlots. Only the CONJUNCTION yields S == L and hence
  "contiguous except the band break". No single constraint says anything about geometry, and nothing
  penalises fragmentation per se. The moment any one conjunct is violated — which is precisely what
  "stuck on shift envelope compliance" means — the implication collapses and the unworked slots
  scatter at ZERO marginal cost.

test: ConstraintVerifier characterisation — two assignment geometries, identical seat count and
  identical out-of-envelope seat count, one operationally sane and one scattered; assert every
  geometry-relevant constraint scores them IDENTICALLY in SHIFT mode while SLOT mode separates them.
expecting: identical SHIFT-mode penalties => solver is provably indifferent to break geometry
next_action: write ShiftModeBreakGeometryCharacterisationTest and run it via ./gradlew

## Symptoms

expected: |
  On a shift-scheduled desk, each agent takes ONE break, positioned by their assigned band's
  offset. Break placement is operationally sane — not scattered, not at the very start or end
  of the shift.
actual: |
  "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots -
  It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
  Visual (src/main/resources/sample-data/Example.png): Agent Allocation group
  "Late · 12:00–21:00 · 10 agent(s)", a SINGLE-BAND template. Eight of ten agents show exactly
  ONE break cell in a shared column (correct). Evelina Yasinchuk and Melina Noemi Aparicio each
  show FOUR separate `B` blocks — three consecutive early plus one mid-shift — and are seated
  left of 12:00, outside the envelope.
errors: none — no crash
reproduction: UAT Test 10, live dev deployment https://d2bbtcc80peap7.cloudfront.net, real production-shaped cloud data
started: Discovered during UAT of Phase 15

## Eliminated

- hypothesis: "H2 as first framed — ShiftBandPair.covers() ignores the band, so the band offset is decorative"
  evidence: ShiftBandPair.java:40-46 — covers() explicitly computes breakStart/breakEnd from the band
    and returns !overlapsBreak. The band IS load-bearing. Orchestrator's own correction confirmed by
    direct read.
  timestamp: phase-1

- hypothesis: "H4 as framed — B might mean 'unassigned/empty slot inside the envelope', so the four
  B blocks could be a pure display artifact with no solver defect behind them"
  evidence: Partly right, but not as framed. B is neither 'assigned break band' NOR 'any empty slot'.
    ScheduleOutputService.findBreaks (line 465-478) emits a BreakDetail for every discontinuity
    BETWEEN CONSECUTIVE HELD SEATS — i.e. holes strictly inside the agent's own first..last worked
    span. That is why Buba (span 12:00-20:00) shows one B and Evelina (span 08:00-20:00) shows five:
    same rendering rule, different span. So each B corresponds to a REAL unworked slot; the display
    only mislabels them. There is a genuine solver defect behind it, so this is not display-only.
  timestamp: phase-1

- hypothesis: "A fully in-envelope but fragmented agent-day can score 0 hard (the method brief's
  suggested characterising test)"
  evidence: REFUTED by counting. getEligibleShiftBandPairs (AgentShiftAssignment.java:165-178) admits
    only pairs whose netHours EXACTLY equals effectiveHours, so |L| == expectedWorkSlots exactly.
    With S subset-of L (envelope, hard) and |S| == expectedWorkSlots (contracted hours, hard),
    S == L is forced. At a true 0 hard, ENVL-04 genuinely holds. The defect is therefore NOT
    "fragmentation is feasible" but "fragmentation is UNPRICED once feasibility is already lost" —
    a strictly sharper and more dangerous claim, since it means the property has no gradient
    defending it. Characterising test retargeted accordingly.
  timestamp: phase-2

## Evidence

- timestamp: phase-1
  checked: ScheduleConstraintProvider.java — all 21 constraints in defineConstraints (lines 64-86)
  found: |
    Gated OFF in SHIFT via ifExists(ScheduleConfig, filtering(cfg.schedulingMode() != SHIFT)):
      exactlyOneBreak       217-219  (ofHard 100)
      breakDuration         274-276  (ofHard 10)
      breakBlockedWindow    307-309  (ofHard 10)
      breakStartAlignment   351-353  (ofHard 10)
      honourPreferredStart  677-678  (ofSoft 5)
      honourPreferredBreak  709-711  (ofSoft 5)
    Enumerating the remaining 15, NONE constrains per-agent geometry:
      unassignedAssignment / bulkOver / bulkUnderHard / bulkUnderSoft / minimumStaffing — all group
        by TIMESLOT, blind to which agent or where their holes fall.
      agentDayOff / specializationMatch / oneAssignmentPerTimeslot / preferPrimary — per-seat.
      shiftEnvelopeCompliance — a pure PROHIBITION (penalise seat outside envelope or inside break).
        It never REQUIRES a seat, so it cannot oblige occupancy of a legal slot.
      bandCapacity — counts agent-days per (date, pair).
      contractedHoursOver/Under/UnderZero — cardinality only, zero contiguity term.
      breakClustering — counts on-break agents from the BAND structurally; requires nothing of seats.
  implication: |
    Definitive answer to the lane's key question: NOTHING in SHIFT mode requires an agent to be
    WORKING during the non-break slots of their envelope. H1 and H3 confirmed.

- timestamp: phase-1
  checked: breakClustering gating direction (ScheduleConstraintProvider.java:776-802)
  found: |
    breakClustering is NOT gated off in SHIFT — it is gated the OPPOSITE way. Its onBreakMarks
    branch (783-790) requires cfg.schedulingMode() == SHIFT, so it is live ONLY in shift mode and
    structurally dead in slot mode (onBreak always 0 => filter at 797 never fires). It is also the
    ONLY constraint in the file that derives "on break" from the assigned band rather than from
    assignment gaps.
  implication: |
    breakClustering is a genuine shift-mode replacement — but only for the CROSS-AGENT question
    ("are too many agents breaking at once"), never the PER-AGENT one ("does this agent have exactly
    one break, in the right place"). ofSoft(2), so it cannot bound a hard-score defect regardless.

- timestamp: phase-1
  checked: ShiftBandPair.covers (ShiftBandPair.java:31-47)
  found: |
    Returns false for a slot outside [start,end) AND false for a slot overlapping the band's break
    window. Correct on both counts.
  implication: |
    H2 first framing refuted: the band is load-bearing, covers() is not the bug. But covers()
    defines which seats are LEGAL, not which are OCCUPIED — the planner's ENVL-04 argument silently
    conflates the two.

- timestamp: phase-1
  checked: ScheduleOutputService.findBreaks (ScheduleOutputService.java:465-478) and its consumers
    ScheduleOutputService.java:191, 235; ScheduleResults.tsx:647-661, 684-686
  found: |
    findBreaks walks the agent-day's own sorted assignments and emits a BreakDetail for EVERY
    currentEnd < nextStart discontinuity. It never reads AgentShiftAssignment, ShiftBandPair or the
    band offset. The frontend expands each BreakDetail into per-slot cells and labels them 'B'
    (legend at 745: "Break B"). Break placement is therefore still gap-derived in the REPORT layer
    even though ENVL-05 moved it to the band in the SOLVER layer.
  implication: |
    H4 settled. 'B' == "hole strictly inside this agent's own first..last worked span". Explains the
    screenshot exactly: Buba spans 12:00-20:00 (1 hole => 1 B); Evelina holds an out-of-envelope seat
    at 08:00 which stretches her span leftward to 08:00, so 09:00/10:00/11:00 become "holes" and
    render as three B's, plus 13:00 and the real 16:00 band break = five B's. Also explains the
    user's verbatim phrasing "adds breaks to fill in the gaps" — in the report layer a gap literally
    IS a break, by definition. Second-order: findBreaks is the same code that feeds
    ScheduleExportService (129) and the preference report's actualBreakTime (484-498), so the
    mislabelling propagates to exports and the break-honoured KPI.

- timestamp: phase-1
  checked: Example.png cell-by-cell against the 13-column grid (totals row 4,2,4,8,17,16,15,14,8,16,16,14,10)
  found: |
    Grid = 08:00..20:00 hourly. Late envelope 12:00-21:00 = columns 5..13. Band break at col 9 (16:00).
    Buba/Lika/Maria/Omar/etc: seats cols 5-8,10-13 = 8 seats, single B at col 9. Correct.
    Evelina & Melina: seat at col 1 (08:00, OUTSIDE envelope), then cols 5,7,8,10,11,12,13.
      = 8 seats total (matches the 8.0 Hours column exactly). B at cols 2,3,4 (span artifact),
      col 6 (13:00, a REAL unworked in-envelope slot), col 9 (the real band break).
  implication: |
    Exactly ONE out-of-envelope seat is paired with exactly ONE unworked in-envelope slot. This is
    the arithmetic signature of the conjunction breaking: contractedHoursOver (hard 1001) pins the
    total at 8, so stealing a seat outside the envelope FORCES surrendering one inside it — and
    nothing decides WHICH, so it landed mid-run at 13:00 instead of adjacent to the break or at the
    envelope edge. Both agents show the identical pattern, so this is systematic, not a one-off.

- timestamp: phase-1
  checked: ConstraintWeights.java defaults (lines 50-151)
  found: |
    shiftEnvelopeComplianceWeight = ofHard(1)      <- one illegal seat costs 1
    contractedHoursUnderWeight    = ofHard(100)    <- one missing seat costs 100
    contractedHoursOverWeight     = ofHard(1001)
    bulkUnderallocationHardWeight = ofHard(1)
    minStaffingWeight             = ofSoft(1000)
    breakClusteringWeight         = ofSoft(2)
  implication: |
    Working a slot OUTSIDE the envelope is 100x cheaper than being one slot UNDER contracted hours.
    So when a zero/low-demand hour needs cover, grabbing an out-of-envelope seat is the model's
    cheapest available hard move, and the compensating in-envelope hole it creates is FREE. This is
    the quantitative form of the user's "It pulls to fill the 0 slot but then adds breaks to fill in
    the gaps". (Weight calibration itself belongs to the envelope-compliance lane; recorded here
    because it is what makes this lane's unpriced geometry actually bite in production.)

- timestamp: phase-2
  checked: AgentShiftAssignment.getEligibleShiftBandPairs (AgentShiftAssignment.java:165-178)
  found: |
    Value range admits only pairs where BigDecimals.normalize(p.netHours()) EXACTLY equals
    normalize(dayConfig.effectiveHours()) — no tolerance — and whose template is effective on the
    row's date.
  implication: |
    |L| (legal non-break envelope slots) == expectedWorkSlots for any ASSIGNED pair. This is what
    makes the planner's ENVL-04 argument accidentally true at 0 hard — and it is load-bearing but
    entirely undocumented as such in the ENVL-04 reasoning, which cites only covers().

- timestamp: phase-2
  checked: 15-06-PLAN.md:108-115 (the ENVL-04 flagged assumption, verbatim)
  found: |
    "The planner's assumption: contiguity is not enforced by any new constraint. It follows from
     shiftEnvelopeCompliance forbidding every seat outside the envelope and from the assigned band's
     break interval being forbidden inside it -- THE ONLY LEGAL SEATS ARE THE CONTIGUOUS RUN BEFORE
     THE BREAK AND THE CONTIGUOUS RUN AFTER IT. ... If the assumption is wrong, the fix is a gap in
     ShiftBandPair.covers, not a new constraint."
  implication: |
    This is the root defect, in the planner's own words. It is a quantifier slip: "the only LEGAL
    seats are ..." establishes S subset-of L; contiguity needs S == L, which requires the additional
    cardinality premise (|S| == |L|) drawn from two OTHER hard constraints plus the value-range
    filter — none of which the argument mentions. And the escape hatch is aimed at the wrong file:
    covers() is correct; the missing thing is an occupancy obligation, which is exactly the
    "new constraint" the assumption ruled out.

- timestamp: phase-2
  checked: ShiftModeBreakGatingTest.everySeatedAgentDay_contiguousExceptTheAssignedBreak (281-357)
    against its fixture ShiftModeFixtures.buildShiftModeSchedule (97-249)
  found: |
    The test asserts hardScore == 0 FIRST (285-288) and only then checks contiguity. Its fixture is
    built so contiguity is arithmetically unavoidable:
      - ShiftModeFixtures.java:65 CONTRACTED_HOURS = 8.00 "matches every template's net hours exactly"
      - :58-59 OPERATING_START/END == the template envelope EXACTLY (08:00-17:00 both), so an
        out-of-envelope seat cannot even be constructed — no timeslot exists outside the envelope
      - :160-164 no seat is created at any break slot at all ("nobody works the shared break window")
  implication: |
    The ENVL-04 proof is a tautology on its own fixture. It proves contiguity holds WHEN the
    conjunction already holds — the one condition under which it could never have failed. It cannot
    detect this defect, and no amount of re-running it will. This is the "why wasn't this caught"
    answer: the gate existed, was green, and was structurally incapable of failing.

- timestamp: phase-2
  checked: ShiftModeBreakGatingTest scenarios A-F (116-243)
  found: |
    All six prove only "penalizesBy(0) under SHIFT, penalizesBy(N) under SLOT" for each gated
    constraint in isolation.
  implication: |
    They prove the gate WORKS. They assert nothing whatsoever about what governs break geometry once
    the gate is closed. The absence of a replacement is exactly the untested space.

- timestamp: phase-3 (characterising test, executed)
  checked: src/test/java/com/wfm/solver/ShiftModeBreakGeometryCharacterisationTest.java — 5 tests,
    5 passed / 0 failed against unmodified production source
  found: |
    Fixture = the screenshot reduced to one agent (Late 12:00-21:00, band +240m/60m, contracted 8h,
    operating window 08:00-21:00 deliberately WIDER than the envelope). Three 8-seat geometries:
      SANE      12,13,14,15,_,17,18,19,20            (all in envelope)
      SCATTERED 08,_,_,_,12,_,14,15,_,17,18,19,20    (the live defect: hole mid-run at 13:00)
      EDGE      08,_,_,_,12,13,14,15,_,17,18,19,_    (same price, hole at the envelope edge)
    Measured penalties:
      SHIFT mode, exactlyOneBreak/breakDuration/breakBlockedWindow/breakStartAlignment
                                              = 0 on ALL THREE geometries
      SLOT  mode, exactlyOneBreak             = 0 (SANE) / 3 (EDGE) / 4 (SCATTERED)
      SHIFT mode, shiftEnvelopeCompliance     = 0 (SANE) / 1 (SCATTERED) / 1 (EDGE)
      SHIFT mode, all 8 reachable constraints = IDENTICAL for SCATTERED vs EDGE
      (non-vacuity guard: shiftEnvelopeCompliance = 1 and breakClustering = 1 on SCATTERED, so
       both live constraints demonstrably SEE the fixture and still cannot tell the two apart)
  implication: |
    Empirical confirmation, not inference. SLOT mode ranked the three geometries correctly and
    strictly (sane 0 < edge 3 < scattered 4); SHIFT mode is completely FLAT. All penalty values were
    hand-derived from the constraint bodies BEFORE the run and matched exactly, which independently
    validates the mechanism model.

- timestamp: phase-3 (coordinator live evidence — re-scoped the lane, H4 promoted)
  checked: full UI data path for the 'B' marker, end to end
  found: |
    1. ScheduleResults.tsx:684-686 sets label='B' iff breakSlots.has(slot).
    2. breakSlots is built ONLY from entry.breaks (647-661). Same rule in the slot-mode view
       (458-470, 495-497) — one rendering rule, both modes.
    3. entry.breaks == AgentScheduleEntry.breaks (ScheduleDetailResponse.java:61), populated at
       ScheduleOutputService.java:191 by findBreaks(dayAssignments).
    4. findBreaks (465-478) takes List<AgentAssignment> and NOTHING else. It emits a BreakDetail for
       every currentEnd < nextStart discontinuity between CONSECUTIVE HELD SEATS. It cannot read the
       band — the band is not in its signature.
    5. The authoritative band data IS present in the same DTO: ShiftDescriptor.bandOffsetMinutes /
       bandDurationMinutes (ScheduleDetailResponse.java:78-79), built by resolveShiftDescriptor from
       the real AgentShiftAssignment.
    6. But grep over ALL of frontend/src shows bandOffsetMinutes/bandDurationMinutes appear ONLY in
       the type declaration client.ts:389-390 and are READ NOWHERE. entry.shift is used solely for
       grouping and the group header (ScheduleResults.tsx:574-580, 632).
  implication: |
    H4 SETTLED, and against BOTH of the coordinator's framings. 'B' means neither "assigned break
    band" nor "empty slot inside the envelope". It means "unworked slot strictly between this
    agent's first and last HELD seat" — regardless of envelope, regardless of band. Proven by the
    discriminator now asserted in the characterisation test: three of Evelina's five 'B' cells
    (09:00, 10:00, 11:00) lie OUTSIDE the 12:00-21:00 envelope entirely. The renderer receives the
    authoritative band times and discards them.

- timestamp: phase-3
  checked: arithmetic decomposition of the live -19 hard score against the confirmed constraint set
  found: |
    Given the coordinator's facts (hard == -19, shiftEnvelopeCompliance the ONLY violated hard
    constraint at ofHard(1) => 19 illegal seats; contractedHoursOver/Under/UnderZero all absent =>
    every agent-day's held count equals expectedWorkSlots), and given the value range admits only
    pairs with netHours EXACTLY == effectiveHours (=> |legal| == expectedWorkSlots):
      |S| = |L|  =>  |S \ L| = |L \ S|
    So the desk carries EXACTLY 19 unworked in-envelope slots, one per illegal seat — a forced 1:1
    pairing, now asserted in the characterisation test.
  implication: |
    Falsifiable prediction the coordinator can check against live data. It also means the
    "extra breaks" are a strict COROLLARY of the envelope violations, not an independent defect:
    close the 19 envelope violations and every phantom 'B' disappears automatically, with no break
    constraint restored. CAVEAT / alternative decomposition: shiftEnvelopeCompliance also penalises
    EVERY seat of an agent-day whose pair is null, so -19 could instead be e.g. one null-pair
    agent-day holding 19 seats. Discriminator: a null-pair agent-day renders under the frontend's
    "No shift assigned" group (ScheduleResults.tsx:628-629). The screenshot's two agents render
    INSIDE the "Late" group, so their pair is non-null and their contribution is the 1:1 kind.

- timestamp: phase-4 (coordinator's full export — questions (a) and (b))
  checked: the exact "Date | Shift | Hours | Assignments | Breaks" table the export came from
  found: |
    (a) SHIFT COLUMN = DERIVED. ScheduleResults.tsx:868 renders `{e.shiftStart} — {e.shiftEnd}`.
        Those come from AgentScheduleEntry.shiftStart/shiftEnd, which ScheduleOutputService.java:174-175
        computes as min/max over the agent-day's own seats:
            LocalTime shiftStart = first.getTimeslot().getStartTime();
            LocalTime shiftEnd   = dayAssignments.get(dayAssignments.size() - 1).getTimeslot().getEndTime();
        (after the sort at :166). The authoritative envelope IS available on the very same record as
        entry.shift.startTime/endTime (resolveShiftDescriptor, :433-450, reads
        template.getStartTime()/getEndTime() directly) — and this table never reads it.
    (b) BREAKS COLUMN = DERIVED. ScheduleResults.tsx:882-885 renders `{b.startTime}-{b.endTime}
        ({b.durationMinutes}m)` from e.breaks == findBreaks(dayAssignments) (:191, body :465-478),
        whose signature accepts only List<AgentAssignment>. durationMinutes is
        ChronoUnit.MINUTES.between(currentEnd, nextStart) across the WHOLE run of consecutive holes,
        which is precisely how a 2-slot hole prints as a single "120m break" that no 60m band could
        ever produce.
    Same for the XLSX: ScheduleExportService:129 writes its "Break" rows from agent.breaks() and
    never touches agent.shift().
  implication: |
    Coordinator's inference CONFIRMED at source, both parts. Consequence stated plainly: the Agent
    Schedule table CANNOT render a shift-envelope violation as a violation — it silently redraws the
    envelope to fit whatever the solver did. Every one of the 8 reported anomalies decodes cleanly:
    "09:00—21:00 twelve-hour shift" is not a shift, it is min/max over seats including a stray
    out-of-envelope one; "120m break" is two adjacent holes; "three breaks" is three hole-runs.
    CROSS-CHECK that makes this airtight: the Agent ALLOCATION view's group header
    (ScheduleResults.tsx:632) DOES use the authoritative entry.shift.startTime/endTime — which is
    why the screenshot header correctly reads "Late · 12:00–21:00" for the SAME agent-days this
    table calls "09:00—21:00". Two views of one fact disagree, and the disagreement IS the envelope
    violation, displayed nowhere as such.

- timestamp: phase-4 (Mariami Katcheishvili — 8h, zero breaks)
  checked: ShiftTemplateService band validation (:177-235) and every main-source user of breakBlockedHours
  found: |
    ShiftTemplateService validates bands for: non-negative offset/duration (:190), envelope
    containment offset+duration <= envelopeMinutes (:193), capacity >= 1 (:197), duplicate
    (offset,duration) (:200-203), and grid alignment (:230-235). It does NOT check the offset
    against breakBlockedHours. Grepping main source, breakBlockedHours reaches only DTO/entity
    plumbing plus ScheduleConstraintProvider.breakBlockedWindow — which is gated OFF in SHIFT mode.
  implication: |
    In SHIFT mode breakBlockedHours has NO enforcement point anywhere in the system. A band at
    offset 0, or at offset == envelopeMinutes - duration, is fully legal at save time and scores
    0 hard at solve time — yet it produces an agent working their entire net hours unbroken with the
    "break" bolted onto the shift boundary (operationally a late start or an early finish, not a
    break). In slot mode that exact shape was a hard violation at ofHard(10). This is a REAL and
    INDEPENDENT break-geometry regression in this lane: unlike the fragmentation, it is invisible in
    the hard score, so it will survive the envelope fix untouched.
    Two candidate explanations for Mariami, with a clean discriminator:
      (i) edge-offset band (or a zero-band template — ShiftBandPair.covers :40-42 returns true for
          every in-envelope slot when band == null, and getNetHours(0) makes an 8h zero-band
          template match an 8h agent exactly) => 0 hard, contributes NOTHING to the -19;
      (ii) she is seated through her band window => a covers() violation, contributing to the -19.
    Discriminator: check whether her agent-day appears among the 19 penalised seats. Either way the
    UI cannot distinguish "legitimately no break" from "worked through the break" — both render as
    an empty Breaks cell.

## Resolution

root_cause: |
  TWO DISTINCT DEFECTS IN THIS LANE. Neither is "break geometry needs a replacement hard constraint",
  and restoring the gated slot-mode break constraints would be actively WRONG.

  RC-1 (PRIMARY, and the one the user is actually looking at) — THE REPORT LAYER STILL DERIVES BOTH
  THE SHIFT ENVELOPE AND THE BREAKS FROM SEAT GAPS, THOUGH THE AUTHORITATIVE VALUES SIT UNUSED IN
  THE SAME DTO. ENVL-05 moved break placement from "discovered from assignment gaps" to "the
  assigned band's offset" in the SOLVER, but ScheduleOutputService was never migrated with it:
    - AgentScheduleEntry.shiftStart/shiftEnd = min/max over the agent-day's own seats
      (ScheduleOutputService.java:174-175), NOT template.getStartTime()/getEndTime().
    - AgentScheduleEntry.breaks = findBreaks(dayAssignments) (:191, :465-478) = every discontinuity
      between consecutive HELD seats, NOT the band window.
  Meanwhile ShiftDescriptor (:433-450) already carries the true templateName, startTime, endTime,
  bandOffsetMinutes and bandDurationMinutes on the very same record — and the frontend reads
  bandOffsetMinutes/bandDurationMinutes NOWHERE (they exist only as type declarations at
  client.ts:389-390). Consequences: 'B' means "unworked slot between two held seats" (not a break,
  and not even necessarily inside the envelope); a 2-slot hole prints as a "120m break" no 60m band
  could produce; and the Agent Schedule table structurally cannot display an envelope violation
  because it redraws the envelope around the violating seat. This is what the user saw and described
  as "it adds breaks to fill in the gaps" — in the report layer, a gap literally IS a break.

  RC-2 (SECONDARY, latent, invisible in the hard score) — breakBlockedHours HAS NO ENFORCEMENT POINT
  IN SHIFT MODE. breakBlockedWindow (ofHard 10) is gated off (ScheduleConstraintProvider:307-309)
  and ShiftTemplateService's band validation (:177-235) never checks the offset against it. A band
  at offset 0 or at the envelope's trailing edge is legal at save time and 0 hard at solve time,
  producing a full unbroken working day. Candidate explanation for the 8h/zero-break agent-day.

  NOT A DEFECT (explicitly refuted, so gap closure does not chase it): break geometry is NOT
  unconstrained at feasibility. getEligibleShiftBandPairs' exact netHours == effectiveHours value
  range (AgentShiftAssignment:165-178) forces |legal| == expectedWorkSlots; contractedHoursOver/Under
  force |held| == expectedWorkSlots; shiftEnvelopeCompliance forces held subset-of legal. Together
  held == legal, so at 0 hard ENVL-04 genuinely holds and every 'B' collapses to exactly the band
  break. The fragmentation is a strict COROLLARY of the 19 envelope violations (|held \ legal| ==
  |legal \ held| exactly), not an independent break defect.

  REAL BUT LOWEST PRIORITY — that conjunction has NO GRADIENT defending it. Proven empirically:
  SCATTERED and EDGE geometries score IDENTICALLY on all 8 reachable shift-mode constraints while
  SLOT mode ranks them strictly 0 / 3 / 4. So while the solve is infeasible the compensating holes
  land anywhere at zero cost. This degrades the LOOK of an already-broken schedule; it does not
  cause the breakage. A soft tie-break at most — never a hard constraint, which would fight the
  envelope model and could make infeasible desks unsolvable.

  WHY NOT CAUGHT: ShiftModeBreakGatingTest#everySeatedAgentDay_contiguousExceptTheAssignedBreak
  asserts hardScore == 0 BEFORE checking contiguity, on a fixture (ShiftModeFixtures) where
  CONTRACTED_HOURS is documented as matching template net hours exactly (:65), the operating window
  EQUALS the envelope (:58-59) so an out-of-envelope seat cannot be constructed, and no seat exists
  at any break slot (:160-164). It is a tautology on its own fixture — green, and structurally
  incapable of failing. The planner's ENVL-04 assumption (15-06-PLAN.md:108-115) contains the
  quantifier slip that produced it: "the only LEGAL seats are the contiguous run before the break
  and the run after it" establishes held subset-of legal, but contiguity needs held == legal.

fix: NOT APPLIED — goal is find_root_cause_only. Direction handed to /gsd-plan-phase --gaps.
verification: |
  Characterisation test src/test/java/com/wfm/solver/ShiftModeBreakGeometryCharacterisationTest.java
  — 5 tests, 5 passed / 0 failed, against unmodified production source. All penalty values were
  hand-derived from the constraint bodies before the run and matched exactly. Neighbouring suites
  (ShiftModeBreakGatingTest, ShiftEnvelopeComplianceConstraintTest, BreakClusteringConstraintTest)
  re-run green. `git status` confirms zero production files modified.
files_changed:
  - src/test/java/com/wfm/solver/ShiftModeBreakGeometryCharacterisationTest.java (NEW, diagnostic only)
  - .planning/debug/shift-mode-break-geometry-ungoverned.md (NEW, this file)
