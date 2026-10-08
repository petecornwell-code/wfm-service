package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.phase.PhaseConfig;
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
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * ROOT-CAUSE REPRODUCTION (Phase 15 UAT gap, debug session
 * {@code .planning/debug/min-staffing-seats-zero-demand.md}).
 *
 * <p>Reproduces the live dev symptom — a SHIFT-mode solve reporting a NON-ZERO hard score
 * attributable to "Shift envelope compliance" ALONE, flat under more search — from the smallest
 * desk shape that exhibits it.
 *
 * <p><b>The seat arithmetic, stated precisely.</b> An agent's shift value range is filtered to
 * pairs whose net hours EXACTLY equal their contracted hours
 * ({@link AgentShiftAssignment#getEligibleShiftBandPairs()}), so a legally-shifted agent's
 * envelope always covers exactly as many timeslots as they have contracted slots. The shortage is
 * therefore never "my envelope is too short" — it is that the {@link AgentAssignment} SEATS inside
 * that envelope are shared with every other agent on the same shift:
 *
 * <pre>
 *   in-envelope seat supply = sum over covered timeslots of ceil(demandFTEs * overallocPct / 100)
 *   contracted demand       = agentCount * contractedSlots
 *   deficit                 = max(0, contracted demand - in-envelope seat supply)
 * </pre>
 *
 * <p>Every surplus agent-slot then chooses between {@code contractedHoursUnder} at 100 hard and
 * {@code shiftEnvelopeCompliance} at 1 hard, and takes the second — landing on the filler seats
 * {@link SolverService#expandMinimumStaffingSeats} manufactured on the zero-demand hours, which lie
 * outside every envelope and carry no over-allocation ceiling to push back (a zero-demand timeslot
 * gets no {@link TimeslotDemandConfig} row at all, so both bulk constraints inner-join to nothing).
 *
 * <p>The resulting hard score is not a search failure. It is the optimum, and it equals the seat
 * deficit exactly.
 *
 * <p>Filler seats here are created by the real production function, unmodified — the point is that
 * the production pipeline supplies the parking spots.
 */
class ShiftEnvelopeSeatStarvationTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 1, 12);

    /** Desk operates 06:00-14:00 hourly; the only shift in the library is 10:00-14:00. */
    private static final LocalTime OPERATING_START = LocalTime.of(6, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(14, 0);
    private static final LocalTime SHIFT_START = LocalTime.of(10, 0);
    private static final int INCREMENT_MINUTES = 60;

    /** Net hours of the only template (10:00-14:00, no break) — every agent must match it exactly. */
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("4.00");
    private static final int CONTRACTED_SLOTS_PER_AGENT = 4;
    private static final int COVERED_TIMESLOTS = 4;      // 10, 11, 12, 13
    private static final int BARE_TIMESLOTS = 4;         // 06, 07, 08, 09 — zero demand

    private record Fixture(Schedule schedule, ShiftBandPair pair, int inEnvelopeSeats,
                           int contractedSlots, int deficit, List<AgentAssignment> fillerSeats) {}

    // ------------------------------------------------------------------
    //  The reproduction
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ROOT CAUSE: in-envelope seats < contracted slots -> hard floors at exactly the deficit, on Shift envelope compliance ALONE")
    void seatStarvationProducesAnIrreducibleEnvelopePenalty() {
        // 3 agents x 4 slots = 12 contracted, against 4 slots x 2 FTE = 8 in-envelope seats.
        Fixture f = buildDesk(3, 2);

        assertThat(f.inEnvelopeSeats()).isEqualTo(8);
        assertThat(f.contractedSlots()).isEqualTo(12);
        assertThat(f.deficit()).isEqualTo(4);

        // The production seat-expansion supplies exactly the parking spots the deficit needs.
        assertThat(f.fillerSeats())
                .as("one manufactured seat per zero-demand hour: 06:00, 07:00, 08:00, 09:00")
                .hasSize(BARE_TIMESLOTS);
        assertThat(f.fillerSeats())
                .as("and not one of them is reachable from the desk's only shift envelope")
                .allSatisfy(seat -> assertThat(f.pair().covers(seat.getTimeslot())).isFalse());

        Schedule solved = solve(f.schedule(), 20_000);

        assertThat(solved.getScore().hardScore())
                .as("the optimum is the seat deficit, negated -- not zero")
                .isEqualTo(-f.deficit());

        // The live dev fingerprint: ONE violated hard constraint, and it is the envelope.
        Map<String, Integer> hardViolations = hardConstraintMatchTotals(solved);
        assertThat(hardViolations)
                .as("exactly the UI's 'Violated hard constraints: Shift envelope compliance'")
                .containsOnlyKeys("Shift envelope compliance");
        assertThat(hardViolations.get("Shift envelope compliance")).isEqualTo(-f.deficit());

        // Every agent works EXACTLY their contracted hours while the envelope absorbs the whole
        // tension -- the live score composition, in which no contractedHours* term appears at all.
        assertThat(seatsHeldPerAgent(solved).values())
                .as("nobody short, nobody over")
                .allMatch(count -> count == CONTRACTED_SLOTS_PER_AGENT);

        // And every agent does hold a legal shift -- the violation is seat placement, not a null
        // shift row (which would flag every seat that agent holds and inflate the count).
        assertThat(solved.getShiftAssignments())
                .as("a null shiftBandPair would be a different defect -- rule it out explicitly")
                .allMatch(sa -> sa.getShiftBandPair() != null);

        // The out-of-envelope seats are precisely the manufactured zero-demand ones.
        long seatedOutsideEnvelope = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null)
                .filter(a -> a.getTimeslot().getStartTime().isBefore(SHIFT_START))
                .count();
        assertThat(seatedOutsideEnvelope)
                .as("'It pulls to fill the 0 slot' -- verbatim")
                .isEqualTo(f.deficit());
    }

    @Test
    @DisplayName("IRREDUCIBLE: 10x the search budget does not move the hard score by one point")
    void moreSolveTimeCannotClearIt() {
        int shortBudget = solve(buildDesk(3, 2).schedule(), 5_000).getScore().hardScore();
        int longBudget = solve(buildDesk(3, 2).schedule(), 50_000).getScore().hardScore();

        assertThat(shortBudget).isEqualTo(-4);
        assertThat(longBudget)
                .as("flat under a 10x budget -- a structural floor, not slow convergence")
                .isEqualTo(shortBudget);
    }

    @Test
    @DisplayName("CONTROL: widen the in-envelope seat supply and the SAME desk solves to 0 hard")
    void adequateInEnvelopeSupplySolvesClean() {
        // Same 3 agents, same 4 bare hours, same filler seats -- only the in-envelope demand moves,
        // from 2 FTE to 3 FTE, taking the supply from 8 to 12 against 12 contracted slots.
        Fixture f = buildDesk(3, 3);
        assertThat(f.inEnvelopeSeats()).isEqualTo(12);
        assertThat(f.deficit()).isZero();
        assertThat(f.fillerSeats()).hasSize(BARE_TIMESLOTS);

        Schedule solved = solve(f.schedule(), 20_000);

        assertThat(solved.getScore().hardScore()).isZero();
        assertThat(hardConstraintMatchTotals(solved)).isEmpty();

        // Proof the 1000-soft bait was genuinely REFUSED rather than absent: all four filler seats
        // sit empty, at a standing cost of 4000 soft, because filling any of them costs hard.
        long emptyFillers = solved.getAssignments().stream()
                .filter(a -> a.getTimeslot().getStartTime().isBefore(SHIFT_START))
                .filter(a -> a.getAgent() == null)
                .count();
        assertThat(emptyFillers)
                .as("hard beats soft when the two disagree -- the zero-demand hours stay bare")
                .isEqualTo(BARE_TIMESLOTS);
    }

    @Test
    @DisplayName("QUANTIFIED: the hard floor tracks the seat deficit point for point")
    void theHardFloorEqualsTheSeatDeficit() {
        // Hold the seat supply at 8 and walk the headcount. Deficit = 4*agents - 8.
        for (int agentCount = 2; agentCount <= 3; agentCount++) {
            Fixture f = buildDesk(agentCount, 2);
            assertThat(solve(f.schedule(), 20_000).getScore().hardScore())
                    .as("agents=%d, contracted=%d slots, in-envelope supply=%d, expected floor=%d",
                            agentCount, f.contractedSlots(), f.inEnvelopeSeats(), -f.deficit())
                    .isEqualTo(-f.deficit());
        }
    }

    @Test
    @DisplayName("THE TEMPTING ONE-LINE FIX FAILS: raising shiftEnvelopeComplianceWeight makes the hard score WORSE, not zero")
    void raisingTheEnvelopeWeightOnlyMovesTheViolationElsewhere() {
        // shiftEnvelopeComplianceWeight sits in the lowest hard tier (1), 100x below its direct
        // antagonist contractedHoursUnder (100) -- so "make the envelope expensive" is the obvious
        // remedy. It does not work: when the in-envelope seat supply is genuinely short, the
        // deficit has to land on SOME hard constraint. Raising the weight only relabels which.
        Fixture f = buildDesk(3, 2);
        f.schedule().getConstraintWeights()
                .setShiftEnvelopeComplianceWeight(HardSoftScore.ofHard(1000));

        Schedule solved = solve(f.schedule(), 20_000);

        assertThat(solved.getScore().hardScore())
                .as("still infeasible, and now 100x worse than the -4 it scored at weight 1")
                .isEqualTo(-400);
        assertThat(hardConstraintMatchTotals(solved))
                .as("the deficit simply migrated from the envelope onto contracted hours")
                .containsOnlyKeys("Contracted hours (under)");
    }

    @Test
    @DisplayName("ADJACENT DEFECT: contracted hours that match no template net hours empty the value range, flagging EVERY seat")
    void contractedHoursMismatchLeavesNoLegalShiftAtAll() {
        // Not the main defect, but it surfaced while building this fixture and it is worth pinning:
        // getEligibleShiftBandPairs filters on EXACT net-hours equality, so a 5h agent against a 4h
        // library gets an EMPTY value range, keeps a null shiftBandPair, and every seat they take
        // is an envelope violation. Phase 14's D-06 makes this mismatch advisory at save time only.
        Fixture f = buildDesk(2, 3, new BigDecimal("5.00"));

        AgentShiftAssignment row = f.schedule().getShiftAssignments().get(0);
        assertThat(row.getEligibleShiftBandPairs())
                .as("a 5.00h agent has no legal shift in a 4.00h library")
                .isEmpty();

        Schedule solved = solve(f.schedule(), 20_000);
        assertThat(solved.getShiftAssignments()).allMatch(sa -> sa.getShiftBandPair() == null);
        assertThat(solved.getScore().hardScore())
                .as("2 agents x 5 seats, every one flagged")
                .isEqualTo(-10);
    }

    // ------------------------------------------------------------------
    //  Fixture -- filler seats come from the real production function
    // ------------------------------------------------------------------

    private static Fixture buildDesk(int agentCount, int inEnvelopeDemandFtes) {
        return buildDesk(agentCount, inEnvelopeDemandFtes, CONTRACTED_HOURS);
    }

    private static Fixture buildDesk(int agentCount, int inEnvelopeDemandFtes, BigDecimal contractedHours) {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = new Specialization();
        spec.setId(nextId(ids));
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName("English");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            Agent a = new Agent();
            a.setId(nextId(ids));
            a.setTenantId(TENANT);
            a.setDeskId(deskId);
            a.setBamboohrId("A-" + (i + 1));
            a.setName("Agent-" + (i + 1));
            a.setActive(true);
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            a.setContractedHoursPerDay(contractedHours);
            agents.add(a);
        }

        // The desk's entire shift library: one template, 10:00-14:00, no break band -> net 4.00h.
        ShiftTemplate late = new ShiftTemplate();
        late.setId(nextId(ids));
        late.setTenantId(TENANT);
        late.setDeskId(deskId);
        late.setName("Late");
        late.setStartTime(SHIFT_START);
        late.setEndTime(OPERATING_END);
        late.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        ShiftBandPair pair = new ShiftBandPair(late, null);
        List<ShiftBandPair> pairs = List.of(pair);

        // Timeslots across the WHOLE operating window -- including the four bare early hours.
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusMinutes(INCREMENT_MINUTES)) {
            Timeslot ts = new Timeslot();
            ts.setId(nextId(ids));
            ts.setTenantId(TENANT);
            ts.setDeskId(deskId);
            ts.setScheduleId(scheduleId);
            ts.setDate(DAY);
            ts.setStartTime(t);
            ts.setEndTime(t.plusMinutes(INCREMENT_MINUTES));
            timeslots.add(ts);
        }

        // Demand exists only where the shift library reaches. 06:00-09:00 carry a zero cell, which
        // FteUploadService drops entirely: no StaffingRequirement, no seat, no TimeslotDemandConfig.
        List<StaffingRequirement> demand = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            if (ts.getStartTime().isBefore(SHIFT_START)) {
                continue;
            }
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(nextId(ids));
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(spec);
            sr.setRequiredFTEs(inEnvelopeDemandFtes);
            demand.add(sr);

            for (int i = 0; i < inEnvelopeDemandFtes; i++) {
                AgentAssignment seat = new AgentAssignment();
                seat.setId(nextId(ids));
                seat.setTenantId(TENANT);
                seat.setDeskId(deskId);
                seat.setScheduleId(scheduleId);
                seat.setTimeslot(ts);
                seat.setRequiredSpecialization(spec);
                seats.add(seat);
            }
        }

        // Mirrors SolverService step 10b exactly: demand configs BEFORE the filler seats exist.
        List<TimeslotDemandConfig> demandConfigs = demandConfigs(seats);

        // Step 10d -- the real production function, called unmodified.
        List<AgentAssignment> fillerSeats = SolverService.expandMinimumStaffingSeats(
                TENANT, deskId, scheduleId, timeslots, seats, demand, List.of(spec));
        List<AgentAssignment> allSeats = new ArrayList<>(seats);
        allSeats.addAll(fillerSeats);

        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        List<AgentShiftAssignment> shiftRows = new ArrayList<>();
        for (Agent a : agents) {
            AgentDayConfig dc = new AgentDayConfig(a.getId(), DAY, contractedHours,
                    INCREMENT_MINUTES, 0, new BigDecimal("99.00"), new BigDecimal("0.00"),
                    BreakAlignment.ON_HOUR, 100, 70);
            dayConfigs.add(dc);

            AgentShiftAssignment row = new AgentShiftAssignment();
            row.setId(nextId(ids));
            row.setTenantId(TENANT);
            row.setDeskId(deskId);
            row.setScheduleId(scheduleId);
            row.setAgent(a);
            row.setDate(DAY);
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
        schedule.setStartTime(OPERATING_START);
        schedule.setEndTime(OPERATING_END);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(new BigDecimal("0.00"));
        schedule.setBreakDurationMinutes(0);
        schedule.setBreakMinShiftHours(new BigDecimal("99.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(contractedHours);
        schedule.setOverallocationHardLimitPct(100);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(demand);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(shiftRows);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(allSeats);

        int contractedSlotsPerAgent = contractedHours
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(INCREMENT_MINUTES), 0, RoundingMode.HALF_UP)
                .intValue();
        int contractedSlots = contractedSlotsPerAgent * agentCount;
        int inEnvelopeSeats = COVERED_TIMESLOTS * inEnvelopeDemandFtes;

        return new Fixture(schedule, pair, inEnvelopeSeats, contractedSlots,
                Math.max(0, contractedSlots - inEnvelopeSeats), fillerSeats);
    }

    /** Byte-for-byte the shape of {@code SolverService.computeTimeslotDemandConfigs}. */
    private static List<TimeslotDemandConfig> demandConfigs(List<AgentAssignment> demandSeats) {
        Map<Timeslot, Integer> perTimeslot = new LinkedHashMap<>();
        for (AgentAssignment a : demandSeats) {
            perTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        List<TimeslotDemandConfig> configs = new ArrayList<>();
        perTimeslot.forEach((ts, count) -> configs.add(new TimeslotDemandConfig(ts, count)));
        return configs;
    }

    // ------------------------------------------------------------------
    //  Solve + score forensics
    // ------------------------------------------------------------------

    /** Solves through the SHIPPED solverConfig.xml, never a hand-built config. */
    private static Schedule solve(Schedule unsolved, int stepCountLimit) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(stepCountLimit));
        Solver<Schedule> solver = SolverFactory.<Schedule>create(solverConfig).buildSolver();
        return solver.solve(unsolved);
    }

    /** Constraint name -> hard score contributed, for every constraint with a non-zero hard total. */
    private static Map<String, Integer> hardConstraintMatchTotals(Schedule solved) {
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(
                SolverFactory.<Schedule>create(SolverConfig.createFromXmlResource("solverConfig.xml")));
        Map<String, Integer> violations = new LinkedHashMap<>();
        for (ConstraintMatchTotal<HardSoftScore> total
                : solutionManager.explain(solved).getConstraintMatchTotalMap().values()) {
            if (total.getScore().hardScore() != 0) {
                violations.put(total.getConstraintRef().constraintName(), total.getScore().hardScore());
            }
        }
        return violations;
    }

    private static Map<UUID, Integer> seatsHeldPerAgent(Schedule solved) {
        Map<UUID, Integer> held = new LinkedHashMap<>();
        for (Agent a : solved.getAgents()) {
            held.put(a.getId(), 0);
        }
        for (AgentAssignment seat : solved.getAssignments()) {
            if (seat.getAgent() != null) {
                held.merge(seat.getAgent().getId(), 1, Integer::sum);
            }
        }
        return held;
    }

    private static UUID nextId(AtomicLong seq) {
        return new UUID(0L, seq.getAndIncrement());
    }
}
