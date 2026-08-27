package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHARACTERISING TEST — do not "fix" the production code to make these pass; they document the
 * defect found during Phase 15 UAT (Test 10/11: "stuck on shift envelope compliance").
 *
 * <p>Every case here demonstrates a schedule whose HARD score can never reach zero, with the
 * residual attributed to {@code "Shift envelope compliance"}, using data shapes an ordinary desk
 * produces (8.00h contracted agents, a 9h envelope with a 60m band, a demand curve that dips).
 *
 * <p>Why the shipped suite never caught it: {@link ShiftModeFixtures} builds a degenerate desk —
 * every template shares the SAME envelope as the operating window, every agent is contracted to
 * exactly that envelope's net hours, and demand is {@code agentCount} FTEs at EVERY non-break slot
 * (i.e. one seat per agent per slot, everywhere). That fixture is feasible by construction, so the
 * benchmark can only ever pass.
 */
class ShiftEnvelopeUnsatisfiableHardTest {

    private static final long TENANT = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 7); // Monday
    private static final int INCREMENT_MINUTES = 30;
    private static final int STEP_COUNT_LIMIT = 20_000;

    /** Mirrors {@code SolverService.MIN_AGENTS_PER_TIMESLOT} / {@code expandMinimumStaffingSeats}. */
    private static final int MIN_AGENTS_PER_TIMESLOT = 1;

    // ==================================================================
    //  LEMMA -- the zero-slack property that makes every case below bite
    // ==================================================================

    @Test
    @DisplayName("LEMMA: a pair in the value range covers EXACTLY expectedWorkSlots slots -- an agent has zero placement freedom inside their envelope")
    void valueRangePairCoversExactlyExpectedWorkSlots_zeroSlack() {
        // The ordinary desk shape from the UAT screenshot: Late 12:00-21:00, 60m band, 8.00h agents.
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        ShiftTemplate late = template(ids, deskId, "Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftTemplateBreakBand band = band(ids, late, 240, 60); // 16:00-17:00
        ShiftBandPair pair = new ShiftBandPair(late, band);

        BigDecimal contracted = new BigDecimal("8.00");

        assertThat(pair.netHours())
                .as("the value range admits this pair for an 8.00h agent only because net hours match exactly")
                .isEqualByComparingTo(contracted);

        // expectedWorkSlots(dayConfig) as ScheduleConstraintProvider computes it.
        int expectedWorkSlots = contracted.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(INCREMENT_MINUTES), 0, java.math.RoundingMode.HALF_UP)
                .intValue();

        long coveredSlots = gridFor(LocalTime.of(8, 0), LocalTime.of(21, 0), ids, deskId, nextId(ids), DATE)
                .stream().filter(pair::covers).count();

        assertThat(coveredSlots)
                .as("THE DEFECT'S ENABLING CONDITION: contracted slots == covered slots exactly, so the "
                        + "agent must occupy 100%% of their legal slots. There is no slack anywhere for "
                        + "the solver to route around a slot where no seat happens to exist.")
                .isEqualTo(expectedWorkSlots);
    }

    @Test
    @DisplayName("LEMMA (universal form): slack is STRUCTURALLY IMPOSSIBLE -- for EVERY pair the value range can admit, covered slots == contracted slots exactly")
    void zeroSlackIsStructural_notCoincidental() {
        // The value range admits a pair iff netHours(pair) == effectiveHours (AgentShiftAssignment
        // :170-175). So the contract is never an independent input -- it is DERIVED from the
        // envelope. Sweep every plausible desk shape and show the identity always holds.
        List<String> checked = new ArrayList<>();
        for (int increment : new int[] {15, 30, 60}) {
            for (int envelopeHours : new int[] {6, 8, 9, 10, 12}) {
                for (int breakMinutes : new int[] {0, 30, 60, 120}) {
                    if (breakMinutes % increment != 0 || breakMinutes >= envelopeHours * 60) {
                        continue; // grid alignment is enforced at save time (D-02)
                    }
                    AtomicLong ids = new AtomicLong(1);
                    UUID deskId = nextId(ids);
                    LocalTime open = LocalTime.of(6, 0);
                    LocalTime start = LocalTime.of(9, 0);
                    LocalTime end = start.plusHours(envelopeHours);

                    ShiftTemplate t = template(ids, deskId, "T", start, end);
                    ShiftBandPair pair = breakMinutes == 0
                            ? new ShiftBandPair(t, null)
                            : new ShiftBandPair(t, band(ids, t, increment, breakMinutes));

                    // The contract an agent MUST hold for this pair to be in their value range.
                    BigDecimal contract = pair.netHours();
                    int contractedSlots = contract.multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(increment), 0, java.math.RoundingMode.HALF_UP)
                            .intValue();

                    List<Timeslot> grid = new ArrayList<>();
                    for (LocalTime x = open; x.isBefore(LocalTime.of(23, 0)); x = x.plusMinutes(increment)) {
                        grid.add(timeslot(ids, deskId, deskId, DATE, x, x.plusMinutes(increment)));
                    }
                    long covered = grid.stream().filter(pair::covers).count();

                    String label = increment + "m grid, " + envelopeHours + "h envelope, "
                            + breakMinutes + "m break -> contract " + contract;
                    checked.add(label);
                    assertThat(covered)
                            .as("SLACK IS STRUCTURALLY IMPOSSIBLE (%s): the agent must occupy 100%% of "
                                    + "their legal slots in EVERY configuration, so there is no desk "
                                    + "setting an operator could choose that would absorb a single "
                                    + "missing seat inside the envelope.", label)
                            .isEqualTo(contractedSlots);
                }
            }
        }
        assertThat(checked).as("the sweep must be non-vacuous").hasSizeGreaterThan(20);
    }

    // ==================================================================
    //  CASE 1 -- demand shortfall inside the envelope (reproduces the screenshot)
    // ==================================================================

    @Test
    @DisplayName("CASE 1: zero-demand slots inside the envelope make hard(0) unreachable; the residual lands on Shift envelope compliance")
    void zeroDemandSlotsInsideEnvelope_hardScoreCannotReachZero() {
        Fixture f = buildLateShiftDeskWithEveningDemandGap();
        Schedule schedule = f.schedule();

        // --- The counting lemma: infeasibility proven by arithmetic, independent of solver effort ---
        ShiftBandPair pair = schedule.getShiftBandPairs().get(0);
        Map<Timeslot, Integer> seatsPerSlot = seatsPerTimeslot(schedule);

        int seatsInsideEnvelope = seatsPerSlot.entrySet().stream()
                .filter(e -> pair.covers(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        int contractedSlotsRequired = schedule.getAgentDayConfigs().size() * 16; // 8.00h / 30m

        assertThat(schedule.getShiftBandPairs())
                .as("the desk has exactly ONE pair, so every agent is forced onto this envelope -- "
                        + "no alternative template can absorb the shortfall")
                .hasSize(1);
        assertThat(seatsInsideEnvelope)
                .as("PROOF OF INFEASIBILITY: the seats that exist inside the envelope (%d) are fewer "
                        + "than the seats the agents' contracted hours require (%d). Seat supply is "
                        + "derived from DEMAND (SolverService.expandAssignments), never from agent "
                        + "supply, so an evening with no forecast simply has no seats to sit in.",
                        seatsInsideEnvelope, contractedSlotsRequired)
                .isLessThan(contractedSlotsRequired);

        // --- And the solver confirms it: a real solve through the shipped config ---
        Schedule solved = solve(schedule);
        HardSoftScore score = solved.getScore();

        assertThat(solved.getShiftAssignments())
                .as("non-vacuous: every agent-day did get a pair -- this is NOT the null-pair case")
                .allMatch(sa -> sa.getShiftBandPair() != null);

        // The floor is DERIVED, not merely observed: 32 contracted slots - 28 seats inside the
        // envelope = a 4-slot shortfall, and each of those 4 slots costs 1 hard once taken outside
        // the envelope (vs 100 hard each if left under-contracted).
        assertThat(score.hardScore())
                .as("THE BUG: the solve returns an INFEASIBLE best-so-far, exactly as UAT reported. Score=%s", score)
                .isEqualTo(-(contractedSlotsRequired - seatsInsideEnvelope))
                .isEqualTo(-4);

        Map<String, Long> hardByConstraint = hardPenaltiesByConstraint(solved);
        assertThat(hardByConstraint)
                .as("the residual hard penalty is attributed ENTIRELY to Shift envelope compliance -- "
                        + "the verbatim UAT report, and nothing else is broken")
                .containsExactly(java.util.Map.entry("Shift envelope compliance", -4L));

        // Under-contract costs 100 hard/slot; an out-of-envelope seat costs 1. The solver therefore
        // always prefers to seat the agent OUTSIDE their envelope, which is what the screenshot shows.
        long outsideEnvelopeSeats = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null)
                .filter(a -> !pair.covers(a.getTimeslot()))
                .count();
        assertThat(outsideEnvelopeSeats)
                .as("agents are seated OUTSIDE the 12:00-21:00 envelope -- the screenshot's two agents "
                        + "in a column left of 12:00 while their group header says Late 12:00-21:00")
                .isGreaterThan(0L);

        // THE SYMMETRY IDENTITY, and the reason it is a discriminator. Here the envelope is fully
        // grid-contained, so |legal| == expectedWorkSlots and every illegal seat taken is paid for
        // by exactly one legal slot left unworked.
        Map<UUID, Breach> breaches = envelopeBreachSymmetry(solved);
        assertThat(breaches.values())
                .as("SEAT-SUPPLY SIGNATURE: illegal seats taken == legal slots surrendered, per "
                        + "agent-day. Breaches: %s", breaches)
                .allMatch(b -> b.illegalHeld() == b.legalSurrendered());
        assertThat(breaches.values().stream().mapToLong(Breach::illegalHeld).sum())
                .as("and they sum to the hard score -- so a live desk reading -19 must carry EXACTLY "
                        + "19 unworked in-envelope slots if (and only if) this is the operative mechanism")
                .isEqualTo(4L);
    }

    // ==================================================================
    //  CASE 2 -- contracted hours that match no template's net hours
    // ==================================================================

    @Test
    @DisplayName("CASE 2: a 7.50h agent on an 8.00h-net library gets an EMPTY value range, a null pair, and every seat penalised")
    void contractedHoursMatchingNoTemplate_emptyValueRange_hardScoreCannotReachZero() {
        Fixture f = buildFullyStaffedDeskWithOneOffContractAgent();
        Schedule schedule = f.schedule();

        AgentShiftAssignment oddRow = schedule.getShiftAssignments().stream()
                .filter(sa -> sa.getDayConfig().effectiveHours().compareTo(new BigDecimal("7.50")) == 0)
                .findFirst().orElseThrow();

        assertThat(oddRow.getEligibleShiftBandPairs())
                .as("NO SOLVER-TIME GUARD: the value range simply comes back EMPTY. Nothing refuses the "
                        + "solve, nothing warns; the agent silently becomes unschedulable.")
                .isEmpty();

        Schedule solved = solve(schedule);
        HardSoftScore score = solved.getScore();

        AgentShiftAssignment solvedOddRow = solved.getShiftAssignments().stream()
                .filter(sa -> sa.getAgent().getId().equals(oddRow.getAgent().getId()))
                .findFirst().orElseThrow();
        assertThat(solvedOddRow.getShiftBandPair())
                .as("allowsUnassigned means the variable just stays null -- never an exception")
                .isNull();

        // 7.50h / 30m = 15 contracted slots, every one of them penalised because the pair is null.
        assertThat(score.hardScore())
                .as("THE BUG (second route to it): hard(0) is unreachable. Score=%s", score)
                .isEqualTo(-15);

        Map<String, Long> hardByConstraint = hardPenaltiesByConstraint(solved);
        assertThat(hardByConstraint)
                .as("residual attributed ENTIRELY to Shift envelope compliance -- the null-pair branch "
                        + "penalises EVERY seat that agent holds")
                .containsExactly(java.util.Map.entry("Shift envelope compliance", -15L));

        long oddAgentSeats = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null && a.getAgent().getId().equals(oddRow.getAgent().getId()))
                .count();
        assertThat(oddAgentSeats)
                .as("contractedHoursUnderZero (hard 100 x slots) forces the agent to be seated anyway, so "
                        + "the envelope penalty is not merely possible, it is FORCED. Leaving her unseated "
                        + "would cost 15 x 100 = 1500 hard instead of 15 x 1 = 15.")
                .isEqualTo(15L);

        // THE DISCRIMINATOR, negative arm: a null pair breaks the symmetry identity completely.
        Breach oddBreach = envelopeBreachSymmetry(solved).get(oddRow.getAgent().getId());
        assertThat(oddBreach.illegalHeld())
                .as("NULL-PAIR SIGNATURE: legal == empty set, so EVERY held seat is illegal")
                .isEqualTo(15L);
        assertThat(oddBreach.legalSurrendered())
                .as("...and NOTHING is surrendered. This is maximally asymmetric, so the identity "
                        + "|held\\legal| == |legal\\held| tells the two shapes apart on sight: a live "
                        + "desk whose illegal-seat count equals its unworked-in-envelope count has NO "
                        + "null-pair agent-days.")
                .isZero();
    }

    // ==================================================================
    //  CASE 3 -- ENVELOPE CAPACITY, with seat supply abundant everywhere.
    //  This is the one that cleanly isolates mechanism (i) from mechanism (ii).
    // ==================================================================

    @Test
    @DisplayName("CASE 3: an envelope that runs past the desk's closing time caps LEGAL slots below contracted slots -- irreducible envelope penalty with seats abundant and contracted hours fully met")
    void envelopeRunningPastClosingTime_irreducibleEnvelopePenalty_withAbundantSeats() {
        Fixture f = buildDeskWhoseTemplateOverrunsTheOperatingWindow();
        Schedule schedule = f.schedule();
        ShiftBandPair pair = schedule.getShiftBandPairs().get(0);

        // --- Mechanism (ii) is EXCLUDED BY CONSTRUCTION: every slot has a seat for every agent ---
        Map<Timeslot, Integer> seatsPerSlot = seatsPerTimeslot(schedule);
        assertThat(seatsPerSlot.entrySet().stream()
                        .filter(e -> !e.getKey().getStartTime().isBefore(LocalTime.of(12, 0)))
                        .allMatch(e -> e.getValue() >= schedule.getAgents().size()))
                .as("seat supply is abundant EVERYWHERE the envelope reaches -- one seat per agent at "
                        + "every slot from 12:00 to the close, break window included. Nothing here is a "
                        + "demand/seat-supply shortage; mechanism (ii) is excluded by construction.")
                .isTrue();

        // --- Mechanism (i): the envelope's LEGAL slot count is below the contracted slot count ---
        long legalSlotsPerAgentDay = schedule.getTimeslots().stream().filter(pair::covers).count();
        int contractedSlotsPerAgentDay = 16; // 8.00h / 30m

        assertThat(pair.netHours())
                .as("the value range admitted this pair BECAUSE its net hours match the contract exactly "
                        + "-- H1's literal form (contract EXCEEDS net hours) is impossible by construction")
                .isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(legalSlotsPerAgentDay)
                .as("BUT net hours are computed as ARITHMETIC over the envelope (ShiftTemplate.getNetHours: "
                        + "(endTime-startTime-break)/60) while covers() is a predicate over slots that "
                        + "ACTUALLY EXIST. The desk closes at 20:00 and the template runs to 21:00, so two "
                        + "slots of the envelope have no Timeslot at all. Legal=%d vs contracted=%d.",
                        legalSlotsPerAgentDay, contractedSlotsPerAgentDay)
                .isEqualTo(14L)
                .isLessThan(contractedSlotsPerAgentDay);

        int agentDays = schedule.getShiftAssignments().size();
        int irreducibleFloor = agentDays * (contractedSlotsPerAgentDay - (int) legalSlotsPerAgentDay);

        // --- The solve confirms the derived floor, and MORE SOLVER TIME CANNOT CLEAR IT ---
        Schedule shortSolve = solve(schedule, 2_000);
        Schedule longSolve = solve(buildDeskWhoseTemplateOverrunsTheOperatingWindow().schedule(), 40_000);

        assertThat(shortSolve.getScore().hardScore())
                .as("hard floor is DERIVED from the counting argument, not merely observed: every "
                        + "agent-day is short exactly %d legal slots and there is only ONE pair to choose",
                        contractedSlotsPerAgentDay - (int) legalSlotsPerAgentDay)
                .isEqualTo(-irreducibleFloor)
                .isEqualTo(-4);
        assertThat(longSolve.getScore().hardScore())
                .as("IRREDUCIBLE: a 20x larger step budget reaches the identical hard score. This is not "
                        + "a solver that needs more time -- it is a solution space with no feasible point.")
                .isEqualTo(shortSolve.getScore().hardScore());

        // --- The live signature: envelope compliance ALONE, contracted hours fully satisfied ---
        Map<String, Long> hardByConstraint = hardPenaltiesByConstraint(longSolve);
        assertThat(hardByConstraint)
                .as("EXACTLY the dev-deployment fingerprint: the ONLY violated hard constraint is Shift "
                        + "envelope compliance. contractedHoursOver (1001), contractedHoursUnder (100) and "
                        + "contractedHoursUnderZero (100) are all absent -- they are FULLY SATISFIED.")
                .containsExactly(java.util.Map.entry("Shift envelope compliance", -4L));

        // Cost arbitrage, made mechanical: every agent still works exactly their contracted slots.
        Map<UUID, Long> seatsPerAgent = new LinkedHashMap<>();
        for (AgentAssignment a : longSolve.getAssignments()) {
            if (a.getAgent() != null) {
                seatsPerAgent.merge(a.getAgent().getId(), 1L, Long::sum);
            }
        }
        assertThat(seatsPerAgent.values())
                .as("THE ARBITRAGE: the solver pays 1 hard per out-of-envelope seat rather than 100 hard "
                        + "per missing contracted slot, so contracted hours come out exact and the whole "
                        + "residual is parked on the cheapest hard constraint in the model.")
                .allMatch(n -> n == contractedSlotsPerAgentDay);

        // WHERE the strays land: at ofHard(1) flat, a seat inside the BREAK window and a seat before
        // the envelope START cost exactly the same, so the solver is indifferent between them. This
        // is why the live export shows BOTH shapes -- early 08:00/09:00 strays AND an agent seated
        // straight through her own break with no gap (Mariami Katcheishvili 01-10).
        List<AgentAssignment> strays = longSolve.getAssignments().stream()
                .filter(a -> a.getAgent() != null && !pair.covers(a.getTimeslot()))
                .toList();
        long insideBreakWindow = strays.stream()
                .filter(a -> !a.getTimeslot().getStartTime().isBefore(pair.template().getStartTime()))
                .count();
        long beforeEnvelopeStart = strays.size() - insideBreakWindow;

        assertThat(strays).hasSize(4);
        assertThat(insideBreakWindow)
                .as("shape A -- agents seated THROUGH THEIR OWN BREAK (the live export's Mariami "
                        + "Katcheishvili 01-10: 8 seats, 11:00-19:00, zero holes). Strays: %s",
                        strays.stream().map(a -> a.getTimeslot().getStartTime().toString()).sorted().toList())
                .isGreaterThan(0L);
        assertThat(beforeEnvelopeStart)
                .as("shape B -- agents seated BEFORE their envelope opens (the live export's early "
                        + "08:00/09:00 strays)")
                .isGreaterThan(0L);
        // THE DISCRIMINATOR, middle arm: envelope capacity breaks the symmetry the OTHER way --
        // illegal seats taken STRICTLY EXCEED legal slots surrendered, because |legal| is short of
        // expectedWorkSlots to begin with. This is how (i) and (ii) are told apart on live data.
        Map<UUID, Breach> breaches = envelopeBreachSymmetry(longSolve);
        assertThat(breaches.values())
                .as("ENVELOPE-CAPACITY SIGNATURE: 2 illegal seats taken, 0 legal slots surrendered "
                        + "(the agent already holds every legal slot there is). Breaches: %s", breaches)
                .allMatch(b -> b.illegalHeld() == 2 && b.legalSurrendered() == 0);

        assertThat(insideBreakWindow + beforeEnvelopeStart)
                .as("BOTH SHAPES APPEAR IN ONE SOLVE, SPLIT %d/%d. That is the direct consequence of "
                        + "shiftEnvelopeCompliance being a flat ofHard(1) per seat with no notion of "
                        + "WHICH kind of breach occurred: working through your own break and working "
                        + "outside your shift are priced identically, so the solver is indifferent and "
                        + "mixes them arbitrarily. Both shapes are visible in the live dev export.",
                        insideBreakWindow, beforeEnvelopeStart)
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("MODELLING DEFECT: shiftEnvelopeCompliance ofHard(1) is the CHEAPEST hard constraint in the model, so every unsatisfiable tension is routed onto the phase's headline guarantee")
    void shiftEnvelopeComplianceIsTheCheapestHardConstraint() {
        ConstraintWeights w = new ConstraintWeights();
        int envelope = w.getShiftEnvelopeComplianceWeight().hardScore();

        assertThat(envelope).isEqualTo(1);
        assertThat(w.getContractedHoursOverWeight().hardScore()).isEqualTo(1001);
        assertThat(w.getContractedHoursUnderWeight().hardScore()).isEqualTo(100);
        assertThat(w.getContractedHoursUnderZeroWeight().hardScore()).isEqualTo(100);
        assertThat(w.getNoOverlapWeight().hardScore()).isEqualTo(1000);
        assertThat(w.getAgentDayOffWeight().hardScore()).isEqualTo(10_000);

        assertThat(envelope)
                .as("ENVL-02 is described as 'the hard constraint the whole Option A coupling rests on', "
                        + "yet at ofHard(1) it is strictly cheaper to break than contracted hours (100x), "
                        + "overlap (1000x) or a day off (10000x). Whenever any two hard constraints "
                        + "conflict, the envelope is ALWAYS the one that gives -- which is why the live "
                        + "score reads 'Violated hard constraints: Shift envelope compliance' and nothing else.")
                .isLessThan(w.getContractedHoursUnderWeight().hardScore());
    }

    // ==================================================================
    //  Fixtures
    // ==================================================================

    private record Fixture(Schedule schedule, List<Agent> agents) {}

    /**
     * The screenshot's desk, minimised. Operating window 08:00-21:00 on a 30m grid. ONE template
     * ("Late" 12:00-21:00 with a single 60m band at 16:00-17:00, net 8.00h). TWO agents contracted
     * 8.00h, so both are forced onto that one envelope.
     *
     * <p>Demand: 2 FTEs per slot across 12:00-16:00 and 17:00-19:00 only. The evening 19:00-21:00
     * carries NO forecast — an entirely ordinary demand curve — so those four slots get only the
     * single minimum-staffing filler seat each. That is 8*2 + 4*2 + 4*1 = 28 seats inside the
     * envelope against 2*16 = 32 contracted slots.
     */
    private static Fixture buildLateShiftDeskWithEveningDemandGap() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = specialization(ids, deskId, "Voice");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            a.setContractedHoursPerDay(new BigDecimal("8.00"));
            agents.add(a);
        }

        ShiftTemplate late = template(ids, deskId, "Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftTemplateBreakBand band = band(ids, late, 240, 60); // 16:00-17:00
        List<ShiftBandPair> pairs = List.of(new ShiftBandPair(late, band));

        List<Timeslot> grid = gridFor(LocalTime.of(8, 0), LocalTime.of(21, 0), ids, deskId, scheduleId, DATE);

        // Demand only where the desk actually forecasts work.
        List<StaffingRequirement> reqs = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        for (Timeslot ts : grid) {
            boolean forecast = inWindow(ts, LocalTime.of(12, 0), LocalTime.of(16, 0))
                    || inWindow(ts, LocalTime.of(17, 0), LocalTime.of(19, 0));
            if (!forecast) {
                continue; // FteUploadService skips a zero cell -- no StaffingRequirement is persisted
            }
            reqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 2));
            for (int i = 0; i < 2; i++) {
                demandSeats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        }

        return assemble(ids, deskId, scheduleId, spec, agents, pairs, grid, reqs, demandSeats,
                new BigDecimal("8.00"), null);
    }

    /**
     * MECHANISM (i) in isolation. The desk OPERATES 08:00-20:00 on a 30m grid. Its one template,
     * "Late", runs 12:00-21:00 with a 60m band at 16:00-17:00 — net 8.00h, matching every agent's
     * contract exactly, so the value range admits it.
     *
     * <p>That template saves cleanly today: {@code ShiftTemplateService.validateGridAlignment} only
     * checks that start/end/break boundaries are an exact multiple of the increment away from the
     * grid START ({@code isAligned}), and 21:00 is 780 minutes after 08:00, a clean multiple of 30.
     * {@code TimeslotBoundsResponse.endTime()} is never read by ANY caller — nothing anywhere checks
     * that an envelope fits inside the operating window.
     *
     * <p>Demand is 2 FTEs at EVERY slot of the operating window, so there is a seat for every agent
     * at every slot and mechanism (ii) (seat-supply shortage) is excluded by construction.
     */
    private static Fixture buildDeskWhoseTemplateOverrunsTheOperatingWindow() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = specialization(ids, deskId, "Voice");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            a.setContractedHoursPerDay(new BigDecimal("8.00"));
            agents.add(a);
        }

        // Envelope 12:00-21:00; the desk closes at 20:00.
        ShiftTemplate late = template(ids, deskId, "Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftTemplateBreakBand band = band(ids, late, 240, 60); // 16:00-17:00
        List<ShiftBandPair> pairs = List.of(new ShiftBandPair(late, band));

        List<Timeslot> grid = gridFor(LocalTime.of(8, 0), LocalTime.of(20, 0), ids, deskId, scheduleId, DATE);

        // Demand 2 FTEs across the whole span the envelope reaches (12:00 to the 20:00 close),
        // INCLUDING the break window. Deliberately NOT before 12:00: putting demand on hours no
        // agent's envelope can legally reach would add a SECOND, unrelated infeasibility
        // (bulkUnderallocationHard, also ofHard(1)) and contaminate the measurement -- an earlier
        // revision of this fixture did exactly that and read -10 instead of -4.
        List<StaffingRequirement> reqs = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        for (Timeslot ts : grid) {
            if (ts.getStartTime().isBefore(LocalTime.of(12, 0))) {
                continue;
            }
            reqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 2));
            for (int i = 0; i < 2; i++) {
                demandSeats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        }

        return assemble(ids, deskId, scheduleId, spec, agents, pairs, grid, reqs, demandSeats,
                new BigDecimal("8.00"), null);
    }

    /**
     * A desk that is comfortably staffable — demand covers every slot of a single 08:00-17:00
     * template with room for all three agents — EXCEPT that one agent is contracted 7.50h rather
     * than the 8.00h the library's only template nets. Nothing about the desk is exotic; a single
     * part-time contract or a day-of-week hours override produces exactly this.
     */
    private static Fixture buildFullyStaffedDeskWithOneOffContractAgent() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = specialization(ids, deskId, "Voice");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            a.setContractedHoursPerDay(new BigDecimal("8.00"));
            agents.add(a);
        }

        ShiftTemplate day = template(ids, deskId, "Day", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand band = band(ids, day, 240, 60); // 12:00-13:00, net 8.00h
        List<ShiftBandPair> pairs = List.of(new ShiftBandPair(day, band));

        List<Timeslot> grid = gridFor(LocalTime.of(8, 0), LocalTime.of(17, 0), ids, deskId, scheduleId, DATE);

        List<StaffingRequirement> reqs = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        for (Timeslot ts : grid) {
            if (inWindow(ts, LocalTime.of(12, 0), LocalTime.of(13, 0))) {
                continue; // shared break window -- nobody works it
            }
            reqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 3));
            for (int i = 0; i < 3; i++) {
                demandSeats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        }

        // The third agent's contract is 7.50h -- matching no pair's net hours.
        return assemble(ids, deskId, scheduleId, spec, agents, pairs, grid, reqs, demandSeats,
                new BigDecimal("8.00"), new BigDecimal("7.50"));
    }

    /**
     * Wires the pieces into a solvable {@link Schedule}, adding the minimum-staffing filler seats
     * exactly as {@code SolverService.expandMinimumStaffingSeats} does (one per timeslot that has no
     * demand-derived seat, appended AFTER {@link TimeslotDemandConfig} is computed so they never
     * count as demand).
     *
     * @param oddAgentHours when non-null, the LAST agent gets these contracted hours instead of
     *                      {@code defaultHours}.
     */
    private static Fixture assemble(AtomicLong ids, UUID deskId, UUID scheduleId, Specialization spec,
            List<Agent> agents, List<ShiftBandPair> pairs, List<Timeslot> grid,
            List<StaffingRequirement> reqs, List<AgentAssignment> demandSeats,
            BigDecimal defaultHours, BigDecimal oddAgentHours) {

        List<TimeslotDemandConfig> demandConfigs = new ArrayList<>();
        Map<Timeslot, Integer> perSlot = new LinkedHashMap<>();
        for (AgentAssignment a : demandSeats) {
            perSlot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        perSlot.forEach((ts, n) -> demandConfigs.add(new TimeslotDemandConfig(ts, n)));

        List<AgentAssignment> allSeats = new ArrayList<>(demandSeats);
        // overallocationHardLimitPct is 100 here, so expandOverflowAssignments contributes nothing.
        for (Timeslot ts : grid) {
            int have = perSlot.getOrDefault(ts, 0);
            for (int i = have; i < MIN_AGENTS_PER_TIMESLOT; i++) {
                allSeats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        }

        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        List<AgentShiftAssignment> shiftRows = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            BigDecimal hours = (oddAgentHours != null && i == agents.size() - 1) ? oddAgentHours : defaultHours;
            a.setContractedHoursPerDay(hours);

            AgentDayConfig dc = new AgentDayConfig(a.getId(), DATE, hours, INCREMENT_MINUTES, 60,
                    new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 100, 70);
            dayConfigs.add(dc);

            AgentShiftAssignment row = new AgentShiftAssignment();
            row.setId(nextId(ids));
            row.setTenantId(TENANT);
            row.setDeskId(deskId);
            row.setScheduleId(scheduleId);
            row.setAgent(a);
            row.setDate(DATE);
            row.setDayConfig(dc);
            row.setDeskShiftBandPairs(pairs);
            shiftRows.add(row);
        }

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(nextId(ids));
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT_MINUTES);
        schedule.setStartTime(grid.get(0).getStartTime());
        schedule.setEndTime(grid.get(grid.size() - 1).getEndTime());
        schedule.setPeriodStartDate(DATE);
        schedule.setPeriodEndDate(DATE);
        schedule.setBreakBlockedHours(new BigDecimal("1.00"));
        schedule.setBreakDurationMinutes(60);
        schedule.setBreakMinShiftHours(new BigDecimal("4.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(defaultHours);
        schedule.setOverallocationHardLimitPct(100);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(grid);
        schedule.setStaffingRequirements(reqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(shiftRows);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(allSeats);

        return new Fixture(schedule, agents);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private static Schedule solve(Schedule unsolved) {
        return solve(unsolved, STEP_COUNT_LIMIT);
    }

    private static Schedule solve(Schedule unsolved, int stepCountLimit) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(stepCountLimit));
        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
    }

    private static Map<String, Long> hardPenaltiesByConstraint(Schedule solved) {
        SolverFactory<Schedule> scoringFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        SolutionManager<Schedule, HardSoftScore> sm = SolutionManager.create(scoringFactory);
        Map<String, Long> out = new LinkedHashMap<>();
        sm.explain(solved).getConstraintMatchTotalMap().forEach((name, total) -> {
            int hard = total.getScore().hardScore();
            if (hard != 0) {
                out.put(total.getConstraintRef().constraintName(), (long) hard);
            }
        });
        System.out.println("[DEBUG-EVIDENCE] score=" + solved.getScore() + " hardBreakdown=" + out);
        return out;
    }

    /**
     * Per agent-day: {@code (|held \ legal|, |legal \ held|)} — illegal seats TAKEN vs legal slots
     * SURRENDERED. Because contracted hours are hard-pinned to {@code expectedWorkSlots}, this pair
     * is a clean DISCRIMINATOR between the three failure mechanisms:
     *
     * <ul>
     *   <li>SEAT SUPPLY (mechanism ii): {@code |legal| == expectedWorkSlots}, so the two are EQUAL —
     *       one legal slot surrendered for every illegal seat taken.</li>
     *   <li>ENVELOPE CAPACITY (mechanism i): {@code |legal| < expectedWorkSlots}, so illegal seats
     *       STRICTLY EXCEED surrendered legal slots.</li>
     *   <li>NULL PAIR: {@code |legal| == 0}, so surrendered is zero and every held seat is illegal —
     *       maximally asymmetric.</li>
     * </ul>
     */
    private record Breach(long illegalHeld, long legalSurrendered) {}

    private static Map<UUID, Breach> envelopeBreachSymmetry(Schedule solved) {
        Map<UUID, Breach> out = new LinkedHashMap<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            ShiftBandPair pair = sa.getShiftBandPair();
            List<Timeslot> held = solved.getAssignments().stream()
                    .filter(a -> a.getAgent() != null
                            && a.getAgent().getId().equals(sa.getAgent().getId())
                            && a.getTimeslot().getDate().equals(sa.getDate()))
                    .map(AgentAssignment::getTimeslot)
                    .toList();
            List<Timeslot> legal = pair == null ? List.of()
                    : solved.getTimeslots().stream()
                            .filter(ts -> ts.getDate().equals(sa.getDate()))
                            .filter(pair::covers)
                            .toList();
            long illegalHeld = held.stream().filter(ts -> !legal.contains(ts)).count();
            long legalSurrendered = legal.stream().filter(ts -> !held.contains(ts)).count();
            out.put(sa.getAgent().getId(), new Breach(illegalHeld, legalSurrendered));
        }
        return out;
    }

    private static Map<Timeslot, Integer> seatsPerTimeslot(Schedule schedule) {
        Map<Timeslot, Integer> perSlot = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            perSlot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        return perSlot;
    }

    private static boolean inWindow(Timeslot ts, LocalTime from, LocalTime to) {
        return !ts.getStartTime().isBefore(from) && ts.getStartTime().isBefore(to);
    }

    private static List<Timeslot> gridFor(LocalTime open, LocalTime close, AtomicLong ids,
            UUID deskId, UUID scheduleId, LocalDate date) {
        List<Timeslot> grid = new ArrayList<>();
        for (LocalTime t = open; t.isBefore(close); t = t.plusMinutes(INCREMENT_MINUTES)) {
            grid.add(timeslot(ids, deskId, scheduleId, date, t, t.plusMinutes(INCREMENT_MINUTES)));
        }
        return grid;
    }

    private static UUID nextId(AtomicLong seq) {
        return new UUID(0L, seq.getAndIncrement());
    }

    private static Specialization specialization(AtomicLong ids, UUID deskId, String name) {
        Specialization s = new Specialization();
        s.setId(nextId(ids));
        s.setTenantId(TENANT);
        s.setDeskId(deskId);
        s.setName(name);
        return s;
    }

    private static Agent agent(AtomicLong ids, UUID deskId, String bambooId, String name) {
        Agent a = new Agent();
        a.setId(nextId(ids));
        a.setTenantId(TENANT);
        a.setBamboohrId(bambooId);
        a.setName(name);
        a.setActive(true);
        a.setDeskId(deskId);
        return a;
    }

    private static ShiftTemplate template(AtomicLong ids, UUID deskId, String name, LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(nextId(ids));
        t.setTenantId(TENANT);
        t.setDeskId(deskId);
        t.setName(name);
        t.setStartTime(start);
        t.setEndTime(end);
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    private static ShiftTemplateBreakBand band(AtomicLong ids, ShiftTemplate template, int offsetMinutes, int durationMinutes) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(nextId(ids));
        b.setTenantId(TENANT);
        b.setShiftTemplate(template);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(durationMinutes);
        return b;
    }

    private static Timeslot timeslot(AtomicLong ids, UUID deskId, UUID scheduleId, LocalDate date,
            LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(nextId(ids));
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }

    private static StaffingRequirement staffingRequirement(AtomicLong ids, UUID deskId, UUID scheduleId,
            Timeslot ts, Specialization spec, int ftes) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(nextId(ids));
        sr.setTenantId(TENANT);
        sr.setDeskId(deskId);
        sr.setScheduleId(scheduleId);
        sr.setTimeslot(ts);
        sr.setSpecialization(spec);
        sr.setRequiredFTEs(ftes);
        return sr;
    }

    private static AgentAssignment seat(AtomicLong ids, UUID deskId, UUID scheduleId, Timeslot ts, Specialization spec) {
        AgentAssignment a = new AgentAssignment();
        a.setId(nextId(ids));
        a.setTenantId(TENANT);
        a.setDeskId(deskId);
        a.setScheduleId(scheduleId);
        a.setTimeslot(ts);
        a.setRequiredSpecialization(spec);
        return a;
    }
}
