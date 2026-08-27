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
import java.util.HashMap;
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
 * (Phase 15, plan 15-13, gap closure G-15-10) The closing evidence: a desk carrying the LIVE
 * DEFECT'S SHAPE — not its scale — either solves to zero hard or is refused with an actionable
 * message before any solve is attempted. This is the shape plans 15-09/15-10/15-11 fixed but
 * never proved end to end on: an hourly operating window wider than any single template's
 * envelope, four templates whose envelopes START at staggered times, whose spans are narrower
 * than the operating window (so early and late hours sit outside SOME envelopes and two hours sit
 * outside EVERY envelope entirely), each template one hour longer than the roster's contracted
 * hours with a single break band so its net hours exactly equal the contract (D-04 admits every
 * template for every agent), and a demand curve that is thin/zero at the edges and substantial in
 * the middle. {@code ShiftModeFixtures} deliberately does not carry this shape (its templates all
 * share one envelope) — the debug lane established that a 400-agent/60-day benchmark would have
 * missed G-15-10 identically, because the fixture missed it by SHAPE, not scale.
 *
 * <p>The desk mirrors {@code .planning/phases/15-shift-envelope-breaks-library-generation/
 * 15-UAT.md}'s {@code decisive_evidence} block: a shift library whose envelopes pack against the
 * demand-bearing hours, with zero-demand hours at the operating window's edges — exactly where
 * the pre-fix {@code expandMinimumStaffingSeats} manufactured envelope-blind filler seats.
 *
 * <p>Every solve here runs through the SHIPPED {@code solverConfig.xml} with a fixed step-count
 * termination (never wall-clock), mirroring {@code ShiftEnvelopeGroundTruthTest}'s (plan 15-04)
 * and {@code ShiftEnvelopeSupplyInvariantTest}'s (plan 15-11) own convention (P-18). The
 * containment walk deliberately does NOT call {@link ShiftBandPair#covers} — it recomputes
 * legality from raw {@link LocalTime} comparisons only, the same discipline
 * {@code ShiftEnvelopeGroundTruthTest#findEnvelopeViolations} established (P-17): a walker that
 * reuses the production predicate inherits the production predicate's own blind spot.
 */
class ShiftDeskEndToEndRegressionTest {

    private static final long TENANT = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 7); // Monday
    private static final int INCREMENT_MINUTES = 60; // hourly grid, per the plan's shape spec
    private static final int STEP_COUNT_LIMIT = 2_000;

    private static final int AGENT_COUNT = 3;
    private static final int DAY_COUNT = 3;

    /** The desk's grid — deliberately wider than the UNION of every template's envelope, so two
     * hours (06:00 and 22:00) sit outside every envelope entirely, exactly like the live desk. */
    private static final LocalTime OPERATING_START = LocalTime.of(6, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(23, 0);

    private static final int BREAK_DURATION_MINUTES = 60;
    private static final int BAND_OFFSET_MINUTES = 240; // every template's break starts 4h in
    private static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;

    /** Every template's envelope is 9h with a 1h band -> net 8h, exactly the roster's contract. */
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
    private static final int OVERALLOCATION_PCT = 130;
    private static final int UNDERALLOCATION_PCT = 70;

    /** The one window every one of the four staggered envelopes covers, legally (non-break), for
     * EVERY template regardless of which one an agent is assigned — the only slots a demand
     * figure can safely target without depending on the solver's own template choice. */
    private static final LocalTime CORE_START = LocalTime.of(14, 0);
    private static final LocalTime CORE_END = LocalTime.of(16, 0);

    private record TemplateSpec(String name, LocalTime start, LocalTime end) {}

    /** Four templates, envelopes staggered, each narrower than the operating window, together
     * covering 07:00-21:00 (the live desk's shape) while leaving 06:00 and 22:00 uncovered. */
    private static final List<TemplateSpec> TEMPLATE_SPECS = List.of(
            new TemplateSpec("Early", LocalTime.of(7, 0), LocalTime.of(16, 0)),
            new TemplateSpec("Mid", LocalTime.of(9, 0), LocalTime.of(18, 0)),
            new TemplateSpec("Late", LocalTime.of(12, 0), LocalTime.of(21, 0)),
            new TemplateSpec("Night", LocalTime.of(13, 0), LocalTime.of(22, 0)));

    private enum DemandMode { NORMAL, REDUCED }

    // ------------------------------------------------------------------
    //  Case 1 -- the shape-complete desk solves to zero hard, proven non-vacuous and contiguous
    // ------------------------------------------------------------------

    @Test
    @DisplayName("shape-complete desk: non-vacuous, zero hard, no residual envelope penalty, and held == legal in both directions")
    void shapeCompleteDesk_solvesToZeroHard_neverCarriesResidualEnvelopePenalty_andIsContiguous() {
        Fixture f = buildStaggeredLibraryDesk(AGENT_COUNT, DAY_COUNT, DemandMode.NORMAL);
        Schedule schedule = f.schedule();

        List<String> warnings = new ArrayList<>();
        assertThatCode(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), warnings))
                .as("this desk is built with ample seat supply -- the gate must pass it, not refuse it")
                .doesNotThrowAnyException();

        long startMillis = System.currentTimeMillis();
        Schedule solved = solve(schedule, STEP_COUNT_LIMIT);
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        System.out.println("[15-13 evidence] shape-complete zero-hard case: agents=" + AGENT_COUNT
                + " days=" + DAY_COUNT + " templates=" + TEMPLATE_SPECS.size()
                + " stepCountLimit=" + STEP_COUNT_LIMIT + " elapsedMillis=" + elapsedMillis
                + " score=" + solved.getScore());

        // 1. Non-vacuity first -- a schedule with no placed shifts or no seated agents trivially
        //    satisfies everything, so placement is asserted before quality.
        assertNonVacuouslyFeasible(solved);

        // 2. The disjunction that is the real acceptance criterion: on the branch where the solve
        //    completes (this test), it must reach zero hard and must NEVER carry a completed
        //    solve with residual "Shift envelope compliance" penalty -- the live desk's exact
        //    symptom (UAT Test 10: "NON-OPTIMAL SOLUTION -- Violated hard constraints: Shift
        //    envelope compliance", stuck for 5.5 minutes at -19). Asserted explicitly so a future
        //    regression that scores better overall while still carrying a nonzero envelope
        //    penalty is caught here.
        HardSoftScore score = solved.getScore();
        assertThat(score).as("solved schedule must carry a score").isNotNull();

        Map<String, Long> hardByConstraint = hardPenaltiesByConstraint(solved);
        assertThat(hardByConstraint)
                .as("THE LIVE DEFECT'S SIGNATURE must never reappear on a completed solve: %s", hardByConstraint)
                .doesNotContainKey("Shift envelope compliance");
        assertThat(score.hardScore())
                .as("the shape-complete desk must reach zero hard through the shipped solverConfig.xml alone")
                .isZero();

        // 3. On the zero-hard branch, walk the solved schedule OUTSIDE the score director and
        //    assert held == legal exactly, in BOTH directions, for every agent-day -- ENVL-04
        //    asserted observably, which the previous (tautological) contiguity test could not do.
        List<ContainmentGap> gaps = walkEnvelopeContainment(solved);
        assertThat(gaps)
                .as("every agent-day's held seats must equal its legal slots exactly, in both "
                        + "directions (no seat outside the envelope or inside the break, and no "
                        + "legal slot left unworked): %s", gaps)
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Case 2 -- the same shape, demand deliberately below what the roster needs: refused, not solved
    // ------------------------------------------------------------------

    @Test
    @DisplayName("shape-complete desk, insufficient forecast: refused before solving, naming the shortfall and every lever")
    void shapeCompleteDesk_insufficientDemand_refusedBeforeSolvingWithActionableMessage() {
        Fixture f = buildStaggeredLibraryDesk(AGENT_COUNT, DAY_COUNT, DemandMode.REDUCED);
        Schedule schedule = f.schedule();

        assertThatThrownBy(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), new ArrayList<>()))
                .as("a desk whose thin forecast cannot supply the roster's contracted hours must be "
                        + "REFUSED before solving -- never left to converge on an irreducible "
                        + "envelope penalty the way the live desk did")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).isNotEmpty();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message())
                                .as("the message must be actionable: it names the date, the "
                                        + "shortfall, and every lever the operator can pull")
                                .contains(BASE_DATE.toString())
                                .contains("shortfall")
                                .contains("over-allocation limit")
                                .contains("demand forecast")
                                .contains("rostered hours")
                                .contains("library");
                    });
                });
    }

    // ------------------------------------------------------------------
    //  Case 3 -- healthy control: a fully covered window with ample demand
    // ------------------------------------------------------------------

    @Test
    @DisplayName("healthy control: a fully covered window with ample demand solves to zero hard")
    void healthyControlCase_fullyCoveredWindowAmpleDemand_solvesToZeroHard() {
        // ShiftModeFixtures (plan 15-04/15-09) already IS the healthy control: its operating
        // window is only one increment wider than its single shared envelope on each side (not
        // the four-way staggered shape above), and demand covers every non-break in-envelope slot
        // for every agent. Reused deliberately rather than reinvented (P-19 precedent).
        Schedule schedule = ShiftModeFixtures.buildShiftModeSchedule(2, 1, 2, 1).schedule();

        assertThatCode(() -> SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                schedule.getSchedulingMode(), schedule.getShiftAssignments(), schedule.getShiftBandPairs(),
                schedule.getTimeslots(), schedule.getAssignments(),
                schedule.getOverallocationHardLimitPct(), new ArrayList<>()))
                .as("the healthy control must never be refused")
                .doesNotThrowAnyException();

        Schedule solved = solve(schedule, STEP_COUNT_LIMIT);

        assertNonVacuouslyFeasible(solved);
        assertThat(solved.getScore().hardScore())
                .as("the healthy control must reach zero hard")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Fixture construction
    // ------------------------------------------------------------------

    private record Fixture(Schedule schedule, List<Agent> agents) {}

    /**
     * Builds the shape-complete desk described in the class javadoc. {@code mode} controls only
     * the demand curve: {@link DemandMode#NORMAL} is zero everywhere covered except a
     * template-independent "core" window (substantial, matching {@code agentCount} exactly, since
     * every template's legal slots include it) — thin/zero at the edges, per the plan's shape
     * spec, with the production filler ({@link SolverSeatExpansionAccess}) supplying the rest.
     * {@link DemandMode#REDUCED} is the SAME shape with a uniformly thin forecast (1 FTE) at every
     * covered slot, deliberately insufficient for the roster's contracted hours in aggregate — a
     * demand-side origin no filler top-up can rescue, since any nonzero demand at a slot suppresses
     * the filler there entirely (production behaviour, {@code SolverService#expandMinimumStaffingSeats}).
     */
    private static Fixture buildStaggeredLibraryDesk(int agentCount, int dayCount, DemandMode mode) {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = specialization(ids, deskId, "Support");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            a.setContractedHoursPerDay(CONTRACTED_HOURS);
            agents.add(a);
        }

        List<ShiftBandPair> pairs = new ArrayList<>();
        for (TemplateSpec spec2 : TEMPLATE_SPECS) {
            ShiftTemplate template = template(ids, deskId, spec2.name(), spec2.start(), spec2.end());
            ShiftTemplateBreakBand band = band(ids, template, BAND_OFFSET_MINUTES, BREAK_DURATION_MINUTES);
            pairs.add(new ShiftBandPair(template, band));
        }
        List<ShiftBandPair> sharedPairs = List.copyOf(pairs);

        List<Timeslot> allTimeslots = new ArrayList<>();
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        List<AgentShiftAssignment> shiftAssignments = new ArrayList<>();
        List<AgentDayConfig> dayConfigs = new ArrayList<>();

        for (int d = 0; d < dayCount; d++) {
            LocalDate date = BASE_DATE.plusDays(d);

            List<Timeslot> dayTimeslots = new ArrayList<>();
            for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusMinutes(INCREMENT_MINUTES)) {
                dayTimeslots.add(timeslot(ids, deskId, scheduleId, date, t, t.plusMinutes(INCREMENT_MINUTES)));
            }
            allTimeslots.addAll(dayTimeslots);

            for (Timeslot ts : dayTimeslots) {
                boolean covered = sharedPairs.stream().anyMatch(p -> p.covers(ts));
                if (!covered) {
                    continue; // truly outside every envelope -- OR-1: no seat, ever
                }
                int ftes = demandFtesFor(ts.getStartTime(), agentCount, mode);
                if (ftes <= 0) {
                    continue; // zero demand here -- production filler expansion supplies it
                }
                staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, ftes));
                for (int i = 0; i < ftes; i++) {
                    demandSeats.add(seat(ids, deskId, scheduleId, ts, spec));
                }
            }

            for (Agent a : agents) {
                AgentDayConfig dayConfig = new AgentDayConfig(a.getId(), date, CONTRACTED_HOURS,
                        INCREMENT_MINUTES, BREAK_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                        BREAK_ALIGNMENT, OVERALLOCATION_PCT, UNDERALLOCATION_PCT);
                dayConfigs.add(dayConfig);

                AgentShiftAssignment row = new AgentShiftAssignment();
                row.setId(nextId(ids));
                row.setTenantId(TENANT);
                row.setDeskId(deskId);
                row.setScheduleId(scheduleId);
                row.setAgent(a);
                row.setDate(date);
                row.setDayConfig(dayConfig);
                row.setDeskShiftBandPairs(sharedPairs);
                shiftAssignments.add(row);
            }
        }

        Map<LocalDate, Integer> workingAgentDaysByDate = shiftAssignments.stream()
                .collect(Collectors.groupingBy(AgentShiftAssignment::getDate, Collectors.summingInt(r -> 1)));

        List<AgentAssignment> fillerSeats = SolverSeatExpansionAccess.expandMinimumStaffingSeats(
                TENANT, deskId, scheduleId, allTimeslots, demandSeats, staffingReqs, List.of(spec),
                SchedulingMode.SHIFT, sharedPairs, workingAgentDaysByDate);
        for (AgentAssignment fillerSeat : fillerSeats) {
            fillerSeat.setId(nextId(ids));
        }

        List<AgentAssignment> allAssignments = new ArrayList<>(demandSeats);
        allAssignments.addAll(fillerSeats);

        List<TimeslotDemandConfig> demandConfigs = new ArrayList<>();
        Map<Timeslot, Integer> perSlot = new LinkedHashMap<>();
        for (AgentAssignment a : demandSeats) {
            perSlot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        perSlot.forEach((ts, n) -> demandConfigs.add(new TimeslotDemandConfig(ts, n)));

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
        schedule.setPeriodStartDate(BASE_DATE);
        schedule.setPeriodEndDate(BASE_DATE.plusDays(Math.max(dayCount - 1, 0)));
        schedule.setBreakBlockedHours(BREAK_BLOCKED_HOURS);
        schedule.setBreakDurationMinutes(BREAK_DURATION_MINUTES);
        schedule.setBreakMinShiftHours(BREAK_MIN_SHIFT_HOURS);
        schedule.setBreakStartAlignment(BREAK_ALIGNMENT);
        schedule.setDefaultContractedHoursPerDay(CONTRACTED_HOURS);
        schedule.setOverallocationHardLimitPct(OVERALLOCATION_PCT);
        schedule.setUnderallocationHardLimitPct(UNDERALLOCATION_PCT);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(allTimeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(sharedPairs);
        schedule.setShiftAssignments(shiftAssignments);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(allAssignments);

        return new Fixture(schedule, agents);
    }

    private static int demandFtesFor(LocalTime slotStart, int agentCount, DemandMode mode) {
        return switch (mode) {
            case REDUCED -> 1; // thin everywhere -- deliberately insufficient for the roster
            case NORMAL -> (!slotStart.isBefore(CORE_START) && slotStart.isBefore(CORE_END)) ? agentCount : 0;
        };
    }

    // ------------------------------------------------------------------
    //  The independent containment walker (P-17) -- shares no code with shiftEnvelopeCompliance
    // ------------------------------------------------------------------

    private record ContainmentGap(String agentDate, List<String> heldOutsideLegal, List<String> legalNotHeld) {}

    /**
     * For every agent-day, computes held (from {@code AgentAssignment}) and legal (raw
     * {@link LocalTime} arithmetic over the assigned pair's template/band, never
     * {@link ShiftBandPair#covers}) and returns every agent-day where the two sets differ in
     * EITHER direction. An empty result is the observable form of ENVL-04's "held == legal".
     */
    private static List<ContainmentGap> walkEnvelopeContainment(Schedule solved) {
        Map<LocalDate, List<Timeslot>> timeslotsByDate = solved.getTimeslots().stream()
                .collect(Collectors.groupingBy(Timeslot::getDate));

        Map<String, List<Timeslot>> heldByAgentDate = new HashMap<>();
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() == null) {
                continue;
            }
            String key = a.getAgent().getId() + "@" + a.getTimeslot().getDate();
            heldByAgentDate.computeIfAbsent(key, k -> new ArrayList<>()).add(a.getTimeslot());
        }

        List<ContainmentGap> gaps = new ArrayList<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            String key = sa.getAgent().getId() + "@" + sa.getDate();
            List<Timeslot> held = heldByAgentDate.getOrDefault(key, List.of());
            ShiftBandPair pair = sa.getShiftBandPair();

            List<Timeslot> legal = pair == null ? List.of()
                    : timeslotsByDate.getOrDefault(sa.getDate(), List.of()).stream()
                            .filter(ts -> isLegalByRawComparison(ts, pair))
                            .toList();

            List<String> heldOutsideLegal = held.stream()
                    .filter(ts -> !legal.contains(ts))
                    .map(ts -> describe(sa, ts))
                    .toList();
            List<String> legalNotHeld = legal.stream()
                    .filter(ts -> !held.contains(ts))
                    .map(ts -> describe(sa, ts))
                    .toList();

            if (!heldOutsideLegal.isEmpty() || !legalNotHeld.isEmpty()) {
                gaps.add(new ContainmentGap(key, heldOutsideLegal, legalNotHeld));
            }
        }
        return gaps;
    }

    /**
     * Membership from raw {@link LocalTime} comparisons only (P-17) — deliberately does NOT call
     * {@link ShiftBandPair#covers}, so this walker cannot inherit that method's own blind spot.
     */
    private static boolean isLegalByRawComparison(Timeslot ts, ShiftBandPair pair) {
        LocalTime slotStart = ts.getStartTime();
        LocalTime slotEnd = ts.getEndTime();
        LocalTime envelopeStart = pair.template().getStartTime();
        LocalTime envelopeEnd = pair.template().getEndTime();

        if (slotStart.isBefore(envelopeStart) || slotEnd.isAfter(envelopeEnd)) {
            return false;
        }
        if (pair.band() == null) {
            return true;
        }
        LocalTime breakStart = envelopeStart.plusMinutes(pair.band().getOffsetMinutes());
        LocalTime breakEnd = breakStart.plusMinutes(pair.band().getDurationMinutes());
        return !(slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart));
    }

    private static String describe(AgentShiftAssignment sa, Timeslot ts) {
        return "agent=" + sa.getAgent().getId() + " date=" + sa.getDate()
                + " timeslot=" + ts.getStartTime() + "-" + ts.getEndTime();
    }

    // ------------------------------------------------------------------
    //  Shared helpers
    // ------------------------------------------------------------------

    private static void assertNonVacuouslyFeasible(Schedule solved) {
        assertThat(solved.getShiftAssignments())
                .as("solved schedule must actually carry shift rows -- a vacuous pass over zero rows proves nothing")
                .isNotEmpty();
        assertThat(solved.getShiftAssignments())
                .as("every shift row must hold a chosen pair")
                .allMatch(sa -> sa.getShiftBandPair() != null);
        long seatedCount = solved.getAssignments().stream().filter(a -> a.getAgent() != null).count();
        assertThat(seatedCount)
                .as("at least one seat must actually be filled -- a walker over zero seats is a vacuous pass")
                .isGreaterThan(0L);
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
        return out;
    }

    /**
     * Solves through the real {@code solverConfig.xml} (P-18), terminated by step count on the
     * trailing local-search phase only — never wall-clock, so the test is reproducible in CI.
     */
    private static Schedule solve(Schedule unsolved, int stepCountLimit) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(stepCountLimit));
        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
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

    private static Timeslot timeslot(AtomicLong ids, UUID deskId, UUID scheduleId, LocalDate date, LocalTime start, LocalTime end) {
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
