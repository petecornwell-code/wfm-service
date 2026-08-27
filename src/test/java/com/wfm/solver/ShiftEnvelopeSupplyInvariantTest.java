package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.exception.PreSolveValidationException;
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
import com.wfm.service.SolverSeatExpansionAccess;
import com.wfm.service.SolverSeatSupplyGateAccess;

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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * (Phase 15 plan 15-11, Task 3) The zero-slack invariant this codebase's shift model relies on,
 * promoted from a characterising defect report (the original {@code
 * ShiftEnvelopeUnsatisfiableHardTest}, {@code .planning/debug/characterising-tests/}) into a
 * permanent regression suite. The rename is deliberate: the old name asserted the defect was a
 * property of the system; it is now a property the system REFUSES TO ENTER, because plan 15-11's
 * seat-supply gate ({@link SolverSeatSupplyGateAccess}) makes in-envelope seat supply a checked
 * precondition of every shift-mode solve, and plan 15-09 made the minimum-staffing filler
 * envelope-aware.
 *
 * <p><strong>The zero-slack equality itself was deliberately kept, not relaxed.</strong> {@link
 * com.wfm.model.AgentShiftAssignment#getEligibleShiftBandPairs()} admits only pairs whose net
 * hours EXACTLY equal the agent-day's effective hours, and {@link ShiftBandPair#covers} excludes
 * the break window, so a pair's legal slots always equal the agent-day's expected work slots
 * exactly — an agent has zero placement freedom inside their own envelope. Relaxing this with a
 * tolerance was considered at plan 15-11's design stage and rejected for three reasons: (1) it is
 * the definition of a shift — a tolerance would admit a pair whose legal slots outnumber the
 * agent's contracted slots, leaving some legal slots unworked with nothing to decide which,
 * undermining ENVL-04's contiguity guarantee; (2) it would not close the arithmetic — slack lets
 * an agent skip a legal slot, it does not create a seat, so a genuine supply shortfall would just
 * relocate onto {@code Contracted hours (under)} at {@code ofHard(100)} instead, the same mistake
 * the weight-raising remedy already made and was refuted for; (3) it would weaken the deliberate
 * guard that makes an hours mismatch degrade to a NAMED refusal (D-06) rather than seating an
 * agent on a shift they cannot work. The real defect was that a solve was permitted to START when
 * supply could not meet this equality — that is what the gate fixes, not the equality itself.
 */
class ShiftEnvelopeSupplyInvariantTest {

    private static final long TENANT = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 7); // Monday
    private static final int INCREMENT_MINUTES = 30;
    private static final int STEP_COUNT_LIMIT = 20_000;

    /** Mirrors {@code SolverService.MIN_AGENTS_PER_TIMESLOT} / {@code expandMinimumStaffingSeats}. */
    private static final int MIN_AGENTS_PER_TIMESLOT = 1;

    // ==================================================================
    //  LEMMA -- the zero-slack property every case below relies on
    // ==================================================================

    @Test
    @DisplayName("LEMMA: a pair in the value range covers EXACTLY expectedWorkSlots slots -- the zero-slack invariant the model relies on")
    void valueRangePairCoversExactlyExpectedWorkSlots_zeroSlack() {
        // The ordinary desk shape: Late 12:00-21:00, 60m band, 8.00h agents.
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        ShiftTemplate late = template(ids, deskId, "Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftTemplateBreakBand band = band(ids, late, 240, 60); // 16:00-17:00
        ShiftBandPair pair = new ShiftBandPair(late, band);

        BigDecimal contracted = new BigDecimal("8.00");

        assertThat(pair.netHours())
                .as("the value range admits this pair for an 8.00h agent only because net hours match exactly")
                .isEqualByComparingTo(contracted);

        int expectedWorkSlots = contracted.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(INCREMENT_MINUTES), 0, java.math.RoundingMode.HALF_UP)
                .intValue();

        long coveredSlots = gridFor(LocalTime.of(8, 0), LocalTime.of(21, 0), ids, deskId, nextId(ids), DATE)
                .stream().filter(pair::covers).count();

        assertThat(coveredSlots)
                .as("THE INVARIANT: contracted slots == covered slots exactly, so the agent must occupy "
                        + "100%% of their legal slots. Asserted here so a future tolerance in the value "
                        + "range filter fails THIS build, not a live desk.")
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
                                    + "their legal slots in EVERY configuration -- this is why the gate's "
                                    + "demand/supply check (using ANY live pair's coverage) is a genuine "
                                    + "necessary condition for a zero-hard solve, not merely a heuristic.",
                                    label)
                            .isEqualTo(contractedSlots);
                }
            }
        }
        assertThat(checked).as("the sweep must be non-vacuous").hasSizeGreaterThan(20);
    }

    // ==================================================================
    //  CASE 1 -- demand shortfall inside the envelope: NOW SOLVES TO ZERO HARD
    // ==================================================================

    @Test
    @DisplayName("CASE 1: a covered zero-demand gap now gets enough seats (plan 15-09) -- the desk solves to zero hard, not an irreducible envelope penalty")
    void zeroDemandSlotsInsideEnvelope_nowSolvesToZeroHard() {
        Fixture f = buildLateShiftDeskWithEveningDemandGap();
        Schedule schedule = f.schedule();

        // The gate itself must pass silently -- plan 15-09's envelope-aware filler now supplies
        // exactly enough covered zero-demand seats for the two agent-days the library forces
        // onto this envelope (2 agent-days x 16 slots = 32 == 24 demand seats + 8 filler seats).
        assertThatCode(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), new ArrayList<>()))
                .as("the shift-mode seat-supply gate must pass this desk, not refuse it")
                .doesNotThrowAnyException();

        Schedule solved = solve(schedule);
        HardSoftScore score = solved.getScore();

        assertThat(solved.getShiftAssignments())
                .as("non-vacuous: every agent-day did get a pair")
                .allMatch(sa -> sa.getShiftBandPair() != null);

        assertThat(score.hardScore())
                .as("THE FIX: what used to be an irreducible -4 (all attributed to Shift envelope "
                        + "compliance) is now a feasible 0hard solve. Score=%s", score)
                .isZero();

        Map<String, Long> hardByConstraint = hardPenaltiesByConstraint(solved);
        assertThat(hardByConstraint)
                .as("no constraint match is attributed to Shift envelope compliance on a feasible solve")
                .doesNotContainKey("Shift envelope compliance");

        // THE DISCRIMINATOR IDENTITY, now a stronger statement: on a feasible solve, held-but-illegal
        // and legal-but-unworked slots are not merely EQUAL, they are both ZERO.
        Map<UUID, Breach> breaches = envelopeBreachSymmetry(solved);
        assertThat(breaches.values())
                .as("on a feasible solve every agent-day holds EXACTLY its legal slots, so both halves "
                        + "of the discriminator identity are zero, not merely equal. Breaches: %s", breaches)
                .allMatch(b -> b.illegalHeld() == 0 && b.legalSurrendered() == 0);
    }

    // ==================================================================
    //  CASE 2 -- contracted hours matching no template's net hours: NOW REFUSED
    // ==================================================================

    @Test
    @DisplayName("CASE 2: a 7.50h agent on an 8.00h-net library is now REFUSED by the gate, naming that agent -- before any solve")
    void contractedHoursMatchingNoTemplate_nowRefusedByGate() {
        Fixture f = buildFullyStaffedDeskWithOneOffContractAgent();
        Schedule schedule = f.schedule();

        AgentShiftAssignment oddRow = schedule.getShiftAssignments().stream()
                .filter(sa -> sa.getDayConfig().effectiveHours().compareTo(new BigDecimal("7.50")) == 0)
                .findFirst().orElseThrow();

        assertThat(oddRow.getEligibleShiftBandPairs())
                .as("the value range still comes back EMPTY for this agent -- that fact is unchanged")
                .isEmpty();

        assertThatThrownBy(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), new ArrayList<>()))
                .as("THE FIX: the silent degrade-to-null-pair is now a named refusal BEFORE any solve")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains(oddRow.getAgent().getName());
                        assertThat(d.message()).contains(DATE.toString());
                        assertThat(d.message()).contains("7.50");
                    });
                });
    }

    // ==================================================================
    //  CASE 3 -- envelope running past closing time: NOW REFUSED
    // ==================================================================

    @Test
    @DisplayName("CASE 3: an envelope that runs past the operating window is now REFUSED by the gate, with seats abundant and contracted hours never even reached")
    void envelopeRunningPastClosingTime_nowRefusedByGate() {
        // The save-time half of this defect -- refusing a TEMPLATE whose envelope does not fit
        // inside the operating window at the point it is saved -- is deferred; plan 15-13 is
        // where that deferral is recorded. This test proves only the solve-time catch: a desk
        // that already carries such a template is refused before any solve is attempted, so the
        // solve-time catch must not be mistaken for the complete fix.
        Fixture f = buildDeskWhoseTemplateOverrunsTheOperatingWindow();
        Schedule schedule = f.schedule();
        ShiftBandPair pair = schedule.getShiftBandPairs().get(0);

        // Mechanism (ii) [seat-supply shortage] is EXCLUDED BY CONSTRUCTION: every slot the
        // envelope reaches inside the grid has a seat for every agent.
        Map<Timeslot, Integer> seatsPerSlot = seatsPerTimeslot(schedule);
        assertThat(seatsPerSlot.entrySet().stream()
                        .filter(e -> !e.getKey().getStartTime().isBefore(LocalTime.of(12, 0)))
                        .allMatch(e -> e.getValue() >= schedule.getAgents().size()))
                .as("seat supply is abundant everywhere the envelope reaches inside the grid")
                .isTrue();

        long legalSlotsPerAgentDay = schedule.getTimeslots().stream().filter(pair::covers).count();
        int contractedSlotsPerAgentDay = 16; // 8.00h / 30m
        assertThat(legalSlotsPerAgentDay)
                .as("the desk closes at 20:00 but the template runs to 21:00 -- two slots of the "
                        + "envelope have no Timeslot at all, so legal slots fall short of contracted slots")
                .isEqualTo(14L)
                .isLessThan(contractedSlotsPerAgentDay);

        assertThatThrownBy(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), new ArrayList<>()))
                .as("THE FIX: an envelope-capacity shortfall is now caught BEFORE solving, not merely "
                        + "an irreducible penalty discovered by running the solver")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> assertThat(d.message()).contains(DATE.toString()));
                });
    }

    // ==================================================================
    //  Weight-ladder -- shift envelope compliance ties for the LOWEST hard weight,
    //  deliberately NOT changed by this plan
    // ==================================================================

    @Test
    @DisplayName("MEASURED FACT: shift envelope compliance ties for the lowest hard weight alongside four other constraints -- deliberately unchanged")
    void shiftEnvelopeComplianceTiesForTheLowestHardWeight_deliberatelyNotChanged() {
        ConstraintWeights w = new ConstraintWeights();
        int envelope = w.getShiftEnvelopeComplianceWeight().hardScore();

        assertThat(envelope).isEqualTo(1);

        // The four other ofHard(1) constraints envelope compliance ties with.
        assertThat(w.getSpecMatchWeight().hardScore()).isEqualTo(1);
        assertThat(w.getBulkOverallocationLimitWeight().hardScore()).isEqualTo(1);
        assertThat(w.getBulkUnderallocationHardWeight().hardScore()).isEqualTo(1);
        assertThat(w.getBandCapacityWeight().hardScore()).isEqualTo(1);

        assertThat(w.getContractedHoursOverWeight().hardScore()).isEqualTo(1001);
        assertThat(w.getContractedHoursUnderWeight().hardScore()).isEqualTo(100);
        assertThat(w.getNoOverlapWeight().hardScore()).isEqualTo(1000);
        assertThat(w.getAgentDayOffWeight().hardScore()).isEqualTo(10_000);

        // NOT a bug to fix: once supply is a checked precondition (this plan), there is no
        // surplus agent-slot left to arbitrage between constraints, so raising this weight would
        // only choose WHICH constraint reports a genuine infeasibility the gate now catches
        // first -- it would not create a single additional feasible solve. Empirical basis
        // (measured during this plan's design, before the gate existed): raising
        // shiftEnvelopeComplianceWeight on a deficit desk moved the residual score from -4 hard
        // on Shift envelope compliance to -400 hard on Contracted hours (under) -- the exact
        // relocation the weight-raising remedy was refuted for, and the same relocation the gate
        // (not a weight change) is what actually prevents by refusing before any solve.
        assertThat(envelope)
                .as("ENVL-02 is 'the hard constraint the whole Option A coupling rests on', yet at "
                        + "ofHard(1) it ties for cheapest to break of any hard constraint. That is "
                        + "unchanged by plan 15-11, deliberately: the gate, not the weight, is the fix.")
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
     * carries NO forecast — an entirely ordinary demand curve. Filler seats for this gap are
     * generated through the REAL production expansion ({@link SolverSeatExpansionAccess}), which
     * (plan 15-09) now guarantees {@code max(MIN_AGENTS_PER_TIMESLOT, workingAgentDaysOn(date))}
     * seats there — 2 seats per gap slot for these 2 agent-days — closing the shortfall exactly:
     * 24 demand seats + 8 filler seats = 32 = the 2 agent-days' combined 32 contracted slots.
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
     * <p>Demand is 2 FTEs at every slot the envelope reaches inside the grid (12:00 through the
     * 20:00 close, including the break window), so there is a seat for every agent at every such
     * slot and mechanism (ii) (seat-supply shortage) is excluded by construction.
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
        // agent's envelope can legally reach would add a SECOND, unrelated infeasibility and
        // contaminate the measurement.
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
     * Wires the pieces into a solvable {@link Schedule}. Filler seats are generated through
     * {@link SolverSeatExpansionAccess#expandMinimumStaffingSeats} — the REAL production
     * expansion, envelope-aware since plan 15-09 — rather than a fixture-local reimplementation,
     * so this class can never quietly drift from what production actually does (mirrors {@link
     * ShiftModeFixtures}' precedent, P-19).
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

        // overallocationHardLimitPct is 100 here, so expandOverflowAssignments (production step
        // 10c, not exercised in this fixture) would contribute nothing either way.
        Map<LocalDate, Integer> workingAgentDaysByDate = shiftRows.stream()
                .collect(Collectors.groupingBy(AgentShiftAssignment::getDate, Collectors.summingInt(r -> 1)));
        List<AgentAssignment> fillerSeats = SolverSeatExpansionAccess.expandMinimumStaffingSeats(
                TENANT, deskId, scheduleId, grid, demandSeats, reqs, List.of(spec),
                SchedulingMode.SHIFT, pairs, workingAgentDaysByDate);

        List<AgentAssignment> allSeats = new ArrayList<>(demandSeats);
        allSeats.addAll(fillerSeats);

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
     * SURRENDERED. On a feasible solve both are zero — a stronger statement than merely equal.
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
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
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
