package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
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
import com.wfm.model.AgentPreference;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.ResolvedUsualShiftTarget;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XCUT-04 / D-06 — the seeded, step-count-terminated A/B benchmark that decides whether V38's
 * inherited {@code consistent_start_weight} default (sized for a PER-AGENT penalty) survives
 * D-02's PER-AGENT-DAY charging model, and at what value. This is why the harness exists: V38's
 * own migration comment sized {@code 0hard/2soft} for a per-agent spread — "on the live desk's 28
 * CSRs a four-increment spread each is 28 x 4 x 2 = 224 soft, enough to break ties ... not enough
 * to outbid ... minimum staffing." D-02 charges per agent-DAY, and 17-CONTEXT.md's own worked
 * example shows the same weight over five working days reaches ~1,120 soft — above
 * {@code minStaffingWeight}'s 1000. Inheriting the 2 unexamined is this phase's headline hazard;
 * this harness is the gate that catches it before it ships. The pass rule, noise rule, and
 * threshold live in {@code 17-BENCHMARK.md}, committed in a commit that precedes any result — the
 * same discipline {@code 15-BENCHMARK.md} established for XCUT-04's first use in this project, and
 * the discipline Phase 12 was withdrawn for skipping (a +0.25h median read as a win inside a 5.00h
 * noise spread).
 *
 * <p><strong>Fixture design — a genuine coverage-vs-consistency tension, not a rescoring
 * exercise.</strong> {@link ShiftModelBenchmarkTest}'s fixture ({@code ShiftModeFixtures}) gives
 * every shift template the SAME envelope start/end (only break-band offset varies), so the solver
 * there has no start-time CHOICE to make — the consistency constraint would be static rescoring,
 * never a real trade-off. This benchmark builds its OWN small desk instead, with two templates at
 * genuinely different starts:
 * <ul>
 *   <li>{@code Early} 08:00–17:00 (9h envelope, 1h break at 12:00, net 8h)</li>
 *   <li>{@code Late} 13:00–22:00 (9h envelope, 1h break at 17:00, net 8h)</li>
 * </ul>
 * Every one of the {@link #AGENT_COUNT} agents' stored usual-shift target is {@code Early}'s
 * start (08:00) on every working day — a realistic "everyone used to work Early" history — while
 * staffing FORECASTS (carried on {@link TimeslotDemandConfig}, deliberately decoupled from the
 * number of physical seats so headcount is never artificially capped) are shaped so the desk needs
 * roughly half the roster on {@code Late} to avoid a coverage shortfall: {@link #EARLY_ONLY_FORECAST}
 * during 08:00–12:00 (only {@code Early} agents can be there), {@link #LATE_ONLY_FORECAST} during
 * 18:00–22:00 (only {@code Late} agents), and {@link #OVERLAP_FORECAST} (= {@link #AGENT_COUNT},
 * always exactly met) during 13:00–17:00 where both templates' working hours coincide.
 *
 * <p><strong>Deliberately wide/narrow allocation percentages.</strong> {@link #OVERALLOCATION_PCT}
 * / {@link #UNDERALLOCATION_PCT} are set so the pct-based channels ({@code bulkOverallocationLimit},
 * {@code bulkUnderallocationHard}, both HARD, and {@code unassignedAssignment}, SOFT-1000) can
 * never fire at this fixture's headcounts — isolating the two channels this benchmark actually
 * measures: {@code bulkUnderallocationSoft} (shortfall against the raw forecast, SOFT-1) and
 * {@code minimumStaffing} (complete abandonment of a timeslot, SOFT-1000, the exact "cost of a
 * minimum-staffing violation" the interfaces block names as the ceiling). Both remain fully active
 * regardless of this setting since neither reads the allocation percentages. This keeps the
 * benchmark's result attributable to the ONE trade-off XCUT-04 exists to measure, not a mixture of
 * several differently-shaped coverage penalties.
 *
 * <p>Recovers {@link ShiftModelBenchmarkTest}'s proven mechanics: system-property gated so it
 * never runs in the default suite, {@code SolverConfig.withRandomSeed} per run,
 * {@code TerminationConfig.withStepCountLimit} for termination (never wall-clock), fixed seeds 1
 * through 5, and a print-a-markdown-table output style whose numbers {@code 17-BENCHMARK.md}
 * transcribes verbatim. Every fixture solves through the REAL {@code solverConfig.xml} (P-18's
 * standing convention), never a hand-built {@code SolverConfig}.
 */
class UsualShiftConsistencyBenchmarkTest {

    private static final long TENANT = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 7); // Monday

    private static final int INCREMENT_MINUTES = 60;

    private static final LocalTime EARLY_START = LocalTime.of(8, 0);
    private static final LocalTime EARLY_END = LocalTime.of(17, 0); // 9h envelope
    private static final LocalTime LATE_START = LocalTime.of(13, 0);
    private static final LocalTime LATE_END = LocalTime.of(22, 0); // 9h envelope

    private static final int BREAK_OFFSET_MINUTES = 240; // 4h in, ON_HOUR, matches ShiftModeFixtures' default offset
    private static final int BREAK_DURATION_MINUTES = 60;
    private static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00"); // matches both templates' net hours exactly

    // See class javadoc: wide enough / narrow enough that the pct-based hard and SOFT-1000
    // channels never fire at this fixture's headcounts (max 10 agents, forecasts of 5/5/10).
    private static final int OVERALLOCATION_PCT = 1000;
    private static final int UNDERALLOCATION_PCT = 0;

    private static final LocalTime OPERATING_START = EARLY_START.minusMinutes(INCREMENT_MINUTES); // 07:00
    private static final LocalTime OPERATING_END = LATE_END.plusMinutes(INCREMENT_MINUTES); // 23:00

    // Break windows, derived once — EARLY_BREAK_END coincides with LATE_START (13:00) and
    // LATE_BREAK_START coincides with EARLY_END (17:00), which is what makes 13:00-17:00 a clean
    // shared "overlap" window with no gap or double-count at either edge.
    private static final LocalTime EARLY_BREAK_START = EARLY_START.plusMinutes(BREAK_OFFSET_MINUTES); // 12:00
    private static final LocalTime EARLY_BREAK_END = EARLY_BREAK_START.plusMinutes(BREAK_DURATION_MINUTES); // 13:00
    private static final LocalTime LATE_BREAK_START = LATE_START.plusMinutes(BREAK_OFFSET_MINUTES); // 17:00
    private static final LocalTime LATE_BREAK_END = LATE_BREAK_START.plusMinutes(BREAK_DURATION_MINUTES); // 18:00

    private static final int AGENT_COUNT = 10;
    private static final int DAY_COUNT = 5; // "five working days" — mirrors 17-BENCHMARK.md's sizing arithmetic

    // Forecasts carried on TimeslotDemandConfig, decoupled from physical seat count (see class
    // javadoc). EARLY_ONLY_FORECAST + LATE_ONLY_FORECAST (5+5=10) exactly matches AGENT_COUNT, so
    // an even 5/5 split is the unique zero-shortfall point; OVERLAP_FORECAST == AGENT_COUNT since
    // every working agent (either template) is present during the overlap window, always met.
    private static final int EARLY_ONLY_FORECAST = 5;
    private static final int LATE_ONLY_FORECAST = 5;
    private static final int OVERLAP_FORECAST = AGENT_COUNT;

    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L};
    private static final int STEP_COUNT_LIMIT = 3000;

    private enum Zone { EARLY_ONLY, OVERLAP, LATE_ONLY, OUTSIDE }

    /** One arm of the A/B: a candidate {@code consistentStartWeight} soft value. */
    private record WeightArm(String label, int weight) {}

    // baseline-off-0: consistency disabled entirely, to establish the unconstrained optimum.
    // candidate-1 / v38-current-2: the two smallest positive integers, v38-current-2 being V38's
    // shipped default carried forward unexamined — exactly the number D-06 exists to check.
    // stress-10: deliberately excessive, included so this harness can be shown able to DETECT the
    // "consistency crowds out coverage" failure mode at all, not just report "safe" at every arm
    // tested (the same able-to-fail discipline SolverQualityGuardTest established, 15-BENCHMARK.md).
    private static final List<WeightArm> ARMS = List.of(
            new WeightArm("baseline-off-0", 0),
            new WeightArm("candidate-1", 1),
            new WeightArm("v38-current-2", 2),
            new WeightArm("stress-10", 10));

    /** One benchmark run's outcome. Model-specific to this fixture's coverage/consistency tension. */
    record RunMetrics(
            String arm,
            int weight,
            long seed,
            int hardScore,
            int softScore,
            int earlyAgentDays,
            int lateAgentDays,
            int unassignedShiftCount,
            int minStaffingViolationTimeslots,
            int shortfallUnits,
            int consistencyMatchCount,
            int consistencySoftTotal,
            long elapsedMillis) {}

    // ------------------------------------------------------------------
    //  Task 1 — wiring check: one seed of the baseline and v38-current arms, not a recorded result
    // ------------------------------------------------------------------

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void wiringCheck_bothWeightArmsProduceACompleteMetricRowForOneSeed() {
        System.out.println();
        System.out.println("=== WIRING CHECK — one seed, two weight arms (NOT recorded as a benchmark result) ===");

        RunMetrics off = runOnce("baseline-off-0", 0, 1L);
        RunMetrics current = runOnce("v38-current-2", 2, 1L);

        printPerRunTable(List.of(off, current));

        assertThat(off).as("baseline-off-0 arm must print a complete metric row").isNotNull();
        assertThat(current).as("v38-current-2 arm must print a complete metric row").isNotNull();
    }

    // ------------------------------------------------------------------
    //  Task 2 — the full run: four weight arms, five seeds each, plus the explain() breakdown for
    //  the first seed of every arm (criterion 2's weight-validation input, from the same run).
    // ------------------------------------------------------------------

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void fullRun_fourWeightArmsFiveSeeds_plusExplainBreakdownPerArm() {
        System.out.println();
        System.out.println("=== XCUT-04 / D-06 FULL RUN — consistentStartWeight arms 0/1/2/10, 5 seeds each ===");
        System.out.println("No fixture, seed, or step-budget value is adjusted after this point.");

        List<List<RunMetrics>> allArmRuns = new ArrayList<>();
        List<RunMetrics> allRuns = new ArrayList<>();
        for (WeightArm arm : ARMS) {
            List<RunMetrics> runs = runSeeds(arm);
            allArmRuns.add(runs);
            allRuns.addAll(runs);
        }

        printPerRunTable(allRuns);
        printSummaryTable(allArmRuns);

        // Must-pass, deterministic (mirrors ShiftModelBenchmarkTest's own division of labour): the
        // shift-envelope coupling itself must stay sound at EVERY arm — no assignment ever falls
        // outside its agent's chosen envelope, and every working agent is fully seated (an
        // unassigned shift, or a seat count short of 8 contracted hours, would show up as a hard
        // violation via shiftEnvelopeCompliance / contractedHoursUnderZero). Whether the SOLVER
        // trades a minStaffingWeight/bulkUnderallocationSoft violation for a lower consistency
        // total at the higher arms is exactly what this benchmark measures and is NOT asserted
        // here — that reading is 17-BENCHMARK.md's write-up, against the pre-committed pass rule.
        for (List<RunMetrics> runs : allArmRuns) {
            assertThat(runs)
                    .as("arm=%s: every seed must leave every agent-day fully seated (no unassigned shift)",
                            runs.get(0).arm())
                    .allSatisfy(m -> assertThat(m.unassignedShiftCount()).as("seed=%d", m.seed()).isZero());
        }

        System.out.println();
        System.out.println("=== EXPLAIN() BREAKDOWN — seed 1 of each arm (criterion 2's weight-validation input) ===");
        for (WeightArm arm : ARMS) {
            Schedule solved = solve(buildSchedule(arm.weight()), 1L);
            System.out.println();
            System.out.println("--- arm=" + arm.label() + " (consistentStartWeight=" + arm.weight() + " soft) ---");
            printExplainBlock(solved);
        }
    }

    // ------------------------------------------------------------------
    //  Run orchestration
    // ------------------------------------------------------------------

    private List<RunMetrics> runSeeds(WeightArm arm) {
        List<RunMetrics> runs = new ArrayList<>(SEEDS.length);
        for (long seed : SEEDS) {
            runs.add(runOnce(arm.label(), arm.weight(), seed));
        }
        return runs;
    }

    private RunMetrics runOnce(String armLabel, int weight, long seed) {
        Schedule unsolved = buildSchedule(weight);
        long startMillis = System.currentTimeMillis();
        Schedule solved = solve(unsolved, seed);
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        return computeMetrics(armLabel, weight, seed, solved, elapsedMillis);
    }

    private static Schedule solve(Schedule unsolved, long seed) {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml").withRandomSeed(seed);
        List<PhaseConfig> phases = config.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_COUNT_LIMIT));
        SolverFactory<Schedule> factory = SolverFactory.create(config);
        Solver<Schedule> solver = factory.buildSolver();
        return solver.solve(unsolved);
    }

    // ------------------------------------------------------------------
    //  Fixture construction
    // ------------------------------------------------------------------

    private static Schedule buildSchedule(int consistencyWeight) {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization support = specialization(ids, deskId, "Support");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < AGENT_COUNT; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(support);
            a.setContractedHoursPerDay(CONTRACTED_HOURS);
            agents.add(a);
        }

        ShiftTemplate early = template(ids, deskId, "Early", EARLY_START, EARLY_END);
        ShiftTemplate late = template(ids, deskId, "Late", LATE_START, LATE_END);
        ShiftTemplateBreakBand earlyBand = band(ids, early, BREAK_OFFSET_MINUTES, BREAK_DURATION_MINUTES);
        ShiftTemplateBreakBand lateBand = band(ids, late, BREAK_OFFSET_MINUTES, BREAK_DURATION_MINUTES);
        ShiftBandPair earlyPair = new ShiftBandPair(early, earlyBand);
        ShiftBandPair latePair = new ShiftBandPair(late, lateBand);
        List<ShiftBandPair> sharedPairs = List.of(earlyPair, latePair);

        List<Timeslot> allTimeslots = new ArrayList<>();
        List<StaffingRequirement> requirements = new ArrayList<>();
        List<TimeslotDemandConfig> demandConfigs = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
        List<AgentShiftAssignment> shiftAssignments = new ArrayList<>();
        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        List<ResolvedUsualShiftTarget> usualTargets = new ArrayList<>();
        List<AgentPreference> preferences = new ArrayList<>();

        for (int d = 0; d < DAY_COUNT; d++) {
            LocalDate date = BASE_DATE.plusDays(d);

            for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusMinutes(INCREMENT_MINUTES)) {
                Timeslot ts = timeslot(ids, deskId, scheduleId, date, t, t.plusMinutes(INCREMENT_MINUTES));
                allTimeslots.add(ts);

                Zone zone = zoneOf(t);
                int forecast = switch (zone) {
                    case EARLY_ONLY -> EARLY_ONLY_FORECAST;
                    case OVERLAP -> OVERLAP_FORECAST;
                    case LATE_ONLY -> LATE_ONLY_FORECAST;
                    case OUTSIDE -> 0;
                };
                if (zone == Zone.OUTSIDE) {
                    continue; // no demand outside the combined envelope, and (matching ShiftModeFixtures'
                    // established convention) no filler seat either
                }

                StaffingRequirement sr = new StaffingRequirement();
                sr.setId(nextId(ids));
                sr.setTenantId(TENANT);
                sr.setDeskId(deskId);
                sr.setScheduleId(scheduleId);
                sr.setTimeslot(ts);
                sr.setSpecialization(support);
                sr.setRequiredFTEs(forecast);
                requirements.add(sr);

                // TimeslotDemandConfig carries the FORECAST, deliberately decoupled from the seat
                // count below (see class javadoc) — this is what lets AGENT_COUNT seats exist at
                // every hour (no physical scarcity artificially capping the Early/Late split) while
                // the forecast threshold used by bulkUnderallocationSoft/minimumStaffing stays at
                // the smaller, genuinely tension-creating value.
                demandConfigs.add(new TimeslotDemandConfig(ts, forecast));

                for (int i = 0; i < AGENT_COUNT; i++) {
                    AgentAssignment seat = new AgentAssignment();
                    seat.setId(nextId(ids));
                    seat.setTenantId(TENANT);
                    seat.setDeskId(deskId);
                    seat.setScheduleId(scheduleId);
                    seat.setTimeslot(ts);
                    seat.setRequiredSpecialization(support);
                    seats.add(seat);
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

                // Every agent's stored usual-shift target is EARLY's start, every working day — a
                // realistic "the whole roster used to work Early" history (D-06's tension: the
                // desk NEEDS roughly half the roster on Late to avoid a coverage shortfall).
                usualTargets.add(new ResolvedUsualShiftTarget(a.getId(), date, EARLY_START));

                // Every agent also carries a standing preference for EARLY's start (same value as
                // the usual-shift target, deliberately) -- this exists so preferredStartShiftMode
                // (the OTHER Phase 17 constraint, D-09: fires independently of the usual-shift
                // target) has something to report in the explain() breakdown alongside "Usual
                // shift consistency", satisfying Task 1's acceptance criterion that the harness
                // names BOTH new constraint strings, not a fixture designed to exercise D-09's
                // "no usual shift stored" case (already covered by plan 17-02's own tests).
                AgentPreference preference = new AgentPreference();
                preference.setId(nextId(ids));
                preference.setTenantId(TENANT);
                preference.setDeskId(deskId);
                preference.setAgent(a);
                preference.setDate(date);
                preference.setStanding(false);
                preference.setPreferredStartTime(EARLY_START);
                preferences.add(preference);
            }
        }

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(nextId(ids));
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);
        weights.setConsistentStartWeight(HardSoftScore.ofSoft(consistencyWeight));

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT_MINUTES);
        schedule.setStartTime(OPERATING_START);
        schedule.setEndTime(OPERATING_END);
        schedule.setPeriodStartDate(BASE_DATE);
        schedule.setPeriodEndDate(BASE_DATE.plusDays(DAY_COUNT - 1));
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
        schedule.setSpecializations(List.of(support));
        schedule.setAgents(agents);
        schedule.setTimeslots(allTimeslots);
        schedule.setStaffingRequirements(requirements);
        schedule.setAgentPreferences(preferences);
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(new ArrayList<>(sharedPairs));
        schedule.setShiftAssignments(shiftAssignments);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(seats);
        schedule.setResolvedUsualShiftTargets(usualTargets);

        return schedule;
    }

    private static Zone zoneOf(LocalTime start) {
        if (start.isBefore(EARLY_START) || !start.isBefore(LATE_END)) {
            return Zone.OUTSIDE;
        }
        if (start.isBefore(EARLY_BREAK_START)) {
            return Zone.EARLY_ONLY; // [08:00,12:00)
        }
        if (start.isBefore(EARLY_BREAK_END)) {
            return Zone.OUTSIDE; // [12:00,13:00) — Early's break, Late hasn't started
        }
        if (start.isBefore(LATE_BREAK_START)) {
            return Zone.OVERLAP; // [13:00,17:00)
        }
        if (start.isBefore(LATE_BREAK_END)) {
            return Zone.OUTSIDE; // [17:00,18:00) — Late's break, Early has ended
        }
        return Zone.LATE_ONLY; // [18:00,22:00)
    }

    // ------------------------------------------------------------------
    //  Factory helpers — every id is deterministic, mirroring ShiftModeFixtures' own convention
    // ------------------------------------------------------------------

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
        t.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
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

    // ------------------------------------------------------------------
    //  Metrics — this fixture's own coverage/consistency vocabulary (D-14-style: model-specific,
    //  reported honestly, never thresholded here — the write-up in 17-BENCHMARK.md applies the
    //  committed pass rule against these numbers).
    // ------------------------------------------------------------------

    private RunMetrics computeMetrics(String armLabel, int weight, long seed, Schedule solved, long elapsedMillis) {
        int earlyAgentDays = 0;
        int lateAgentDays = 0;
        int unassignedShiftCount = 0;
        for (AgentShiftAssignment row : solved.getShiftAssignments()) {
            if (row.getShiftBandPair() == null) {
                unassignedShiftCount++;
            } else if ("Early".equals(row.getShiftBandPair().template().getName())) {
                earlyAgentDays++;
            } else {
                lateAgentDays++;
            }
        }

        Map<Timeslot, Integer> filledByTimeslot = new java.util.HashMap<>();
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() != null) {
                filledByTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
            }
        }

        int minStaffingViolationTimeslots = 0;
        int shortfallUnits = 0;
        for (TimeslotDemandConfig demand : solved.getTimeslotDemandConfigs()) {
            Zone zone = zoneOf(demand.timeslot().getStartTime());
            if (zone != Zone.EARLY_ONLY && zone != Zone.LATE_ONLY) {
                continue; // overlap's forecast (== AGENT_COUNT) is always exactly met by construction
            }
            int filled = filledByTimeslot.getOrDefault(demand.timeslot(), 0);
            if (filled == 0) {
                minStaffingViolationTimeslots++;
            }
            if (filled < demand.totalDemandFTEs()) {
                shortfallUnits += demand.totalDemandFTEs() - filled;
            }
        }

        var explanation = explainFactory().explain(solved);
        int consistencyMatchCount = 0;
        int consistencySoftTotal = 0;
        for (ConstraintMatchTotal<HardSoftScore> total : explanation.getConstraintMatchTotalMap().values()) {
            if ("Usual shift consistency".equals(total.getConstraintName())) {
                consistencyMatchCount += total.getConstraintMatchCount();
                consistencySoftTotal += total.getScore().softScore();
            }
        }

        return new RunMetrics(armLabel, weight, seed,
                solved.getScore().hardScore(), solved.getScore().softScore(),
                earlyAgentDays, lateAgentDays, unassignedShiftCount,
                minStaffingViolationTimeslots, shortfallUnits,
                consistencyMatchCount, consistencySoftTotal,
                elapsedMillis);
    }

    /**
     * A fresh, hand-built {@link SolutionManager} for rescoring an already-solved {@link Schedule}
     * — never a second solve — mirroring {@link ConstraintPrecedenceObservabilityTest}'s
     * {@code explainFactory} exactly.
     */
    private static SolutionManager<Schedule, HardSoftScore> explainFactory() {
        SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        return SolutionManager.create(factory);
    }

    // ------------------------------------------------------------------
    //  Reporting — markdown tables and the explain() dump, transcribed verbatim into
    //  17-BENCHMARK.md
    // ------------------------------------------------------------------

    private void printPerRunTable(List<RunMetrics> runs) {
        System.out.println();
        System.out.println("Per-run results:");
        System.out.println("| arm | weight | seed | hardScore | softScore | earlyAgentDays | lateAgentDays | "
                + "unassignedShiftCount | minStaffingViolationTimeslots | shortfallUnits | "
                + "consistencyMatchCount | consistencySoftTotal | elapsedMillis |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|---|");
        for (RunMetrics m : runs) {
            printRow(m);
        }
    }

    private void printRow(RunMetrics m) {
        System.out.println("| " + m.arm() + " | " + m.weight() + " | " + m.seed() + " | " + m.hardScore()
                + " | " + m.softScore() + " | " + m.earlyAgentDays() + " | " + m.lateAgentDays()
                + " | " + m.unassignedShiftCount() + " | " + m.minStaffingViolationTimeslots()
                + " | " + m.shortfallUnits() + " | " + m.consistencyMatchCount()
                + " | " + m.consistencySoftTotal() + " | " + m.elapsedMillis() + " |");
    }

    private void printSummaryTable(List<List<RunMetrics>> armRunLists) {
        System.out.println();
        System.out.println("Summary (median and full min/max spread, never a mean):");
        System.out.println("| arm | weight | hardScore median/min/max | lateAgentDays median/min/max | "
                + "minStaffingViolationTimeslots median/min/max | shortfallUnits median/min/max | "
                + "consistencySoftTotal median/min/max |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (List<RunMetrics> runs : armRunLists) {
            printSummaryRow(runs);
        }
    }

    private void printSummaryRow(List<RunMetrics> runs) {
        String arm = runs.get(0).arm();
        int weight = runs.get(0).weight();
        List<Double> hard = runs.stream().map(m -> (double) m.hardScore()).sorted().toList();
        List<Double> lateDays = runs.stream().map(m -> (double) m.lateAgentDays()).sorted().toList();
        List<Double> minStaffing = runs.stream().map(m -> (double) m.minStaffingViolationTimeslots()).sorted().toList();
        List<Double> shortfall = runs.stream().map(m -> (double) m.shortfallUnits()).sorted().toList();
        List<Double> consistencySoft = runs.stream().map(m -> (double) m.consistencySoftTotal()).sorted().toList();

        System.out.println("| " + arm + " | " + weight
                + " | " + median(hard) + "/" + hard.get(0) + "/" + hard.get(hard.size() - 1)
                + " | " + median(lateDays) + "/" + lateDays.get(0) + "/" + lateDays.get(lateDays.size() - 1)
                + " | " + median(minStaffing) + "/" + minStaffing.get(0) + "/" + minStaffing.get(minStaffing.size() - 1)
                + " | " + median(shortfall) + "/" + shortfall.get(0) + "/" + shortfall.get(shortfall.size() - 1)
                + " | " + median(consistencySoft) + "/" + consistencySoft.get(0) + "/"
                + consistencySoft.get(consistencySoft.size() - 1) + " |");
    }

    private static double median(List<Double> sortedAscending) {
        int n = sortedAscending.size();
        if (n == 0) {
            throw new IllegalStateException("cannot compute median of an empty run set");
        }
        if (n % 2 == 1) {
            return sortedAscending.get(n / 2);
        }
        return (sortedAscending.get(n / 2 - 1) + sortedAscending.get(n / 2)) / 2.0;
    }

    /**
     * Prints every non-zero {@link ConstraintMatchTotal} for {@code solved}, sorted by constraint
     * name — the block criterion 2 requires: the weight-validation breakdown produced by the SAME
     * run, not a separate manual exercise. Mirrors {@code SolverService.runPreSolveScoreDiagnostic}'s
     * print style.
     */
    private void printExplainBlock(Schedule solved) {
        var explanation = explainFactory().explain(solved);
        explanation.getConstraintMatchTotalMap().values().stream()
                .filter(total -> !total.getScore().equals(HardSoftScore.ZERO))
                .sorted((a, b) -> a.getConstraintName().compareTo(b.getConstraintName()))
                .forEach(total -> System.out.println("  " + total.getConstraintName() + " => "
                        + total.getScore() + " (count: " + total.getConstraintMatchCount() + ")"));
    }
}
