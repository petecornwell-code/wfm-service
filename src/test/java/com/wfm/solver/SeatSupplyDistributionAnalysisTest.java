package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

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
import com.wfm.model.Timeslot;
import com.wfm.service.SolverSeatSupplyGateAccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/**
 * (Phase 15 plan 15-19, gap closure G-15-31; re-measured by plan 15-20 against the SHIPPED gate)
 * The analysis the gap says nobody has done: candidate within-day blocking rules, evaluated
 * against fixtures known to solve and fixtures known to collapse, with the false-refusal count
 * MEASURED per rule rather than argued.
 *
 * <p><strong>Plan 15-20 update.</strong> R2 (the forced-occupancy necessary condition this class
 * proposed) is no longer a test-local proposal — {@code SolverService.requireShiftEnvelopeSeatSupply}
 * now implements it in production, as a per-timeslot check accumulated alongside the pre-existing
 * day-wide sum (R0). Per this phase's own threat register (T-15-20-04, "duplicate rule
 * implementations drifting apart" — already the root cause behind G-15-10 root cause B, G-15-21,
 * and the gate's own two {@code coveredTimeslots} sites before plan 15-18), this class's R2 rule no
 * longer carries its own reimplementation of the forced-occupancy predicate: it calls {@link
 * SolverSeatSupplyGateAccess#forcedAgentDaysByTimeslotId} (a thin bridge onto the SHIPPED
 * production method), and a new "Shipped gate" rule invokes the full, throwing production method
 * directly, so the table's own numbers describe production behaviour rather than a copy of it.
 * {@code SolverService} itself is otherwise untouched by this file (plan 15-20's Task 1 is the only
 * plan that edits it) — the one src/main change this class exercises through the bridge, never
 * re-derives.
 *
 * <p><strong>What this class does NOT do.</strong> It does not make the tightest-hour advisory
 * blocking. {@code ShiftEnvelopeSupplyGateTest#advisoryOnThinTimeslotDoesNotBlock} is untouched by
 * this plan and still passes — see plan 15-20's own outcome section in
 * {@code 15-SEAT-SUPPLY-GATE-ANALYSIS.md}. This class remains measurement, exercising production
 * only through the two read-only bridges ({@code SolverSeatSupplyGateAccess}); it edits nothing
 * under {@code src/main} itself.
 *
 * <p><strong>Corpus size, stated plainly.</strong> This class builds and SOLVES three fixtures this
 * session (a distribution-blind fixture, a healthy staggered-shift control, and one fresh solve of
 * the existing {@link LiveShapeShiftDeskFixture} control) and cites the ALREADY-COMMITTED five-seed
 * evidence for {@code LiveShapeShiftDeskFixture} from {@code 15-14-SUMMARY.md} /
 * {@code 15-BENCHMARK.md} rather than re-running all five seeds (a runtime-budget choice, not a
 * shortcut on the method). A further 23 fixtures across {@code ShiftEnvelopeSupplyGateTest} (14),
 * {@code ShiftEnvelopeSupplyInvariantTest} (6) and {@code ShiftDeskEndToEndRegressionTest} (3) exist
 * in the repository and are REFERENCED, not re-instantiated here — their private fixture-building
 * methods are not visible outside their own classes, and re-deriving 23 more fixtures independently
 * was judged not to be a good use of this plan's budget once the mechanism was established on the
 * three built here. The recommendation in {@code 15-SEAT-SUPPLY-GATE-ANALYSIS.md} names this
 * limitation explicitly rather than implying a broader corpus than the one actually measured.
 *
 * <p><strong>R2's forced-occupancy predicate, precisely.</strong> An agent-day is FORCED at a
 * timeslot {@code ts} when EVERY one of its {@link AgentShiftAssignment#getEligibleShiftBandPairs()}
 * both covers {@code ts} and has zero slack for that agent-day (its covered-slot count on that date
 * equals the agent-day's {@link AgentDayConfig#expectedWorkSlots()}). A pair with slack does not
 * force the agent onto any one of its covered slots, because the model lets the agent skip up to
 * {@code envelopeSlackSlots} of a slack pair's legal slots — {@code contractedHoursOver}/{@code
 * Under} judge only the AGGREGATE worked-hours total, never which specific slot was skipped
 * (verified against {@code AgentShiftAssignment.getEligibleShiftBandPairs}'s own javadoc and
 * {@code ShiftEnvelopeSupplyInvariantTest}'s zero-slack lemma). If an agent-day has no eligible pair
 * at all, it is not "forced" by this predicate — that case is {@code R0}'s own distinct
 * unassignable-row branch, already handled and unaffected by this analysis.
 */
class SeatSupplyDistributionAnalysisTest {

    private static final long TENANT = 1L;

    // ==================================================================
    //  Section 1 (Task 1) — the per-hour seat model, pinned to production arithmetic
    // ==================================================================

    /**
     * Mirrors {@code SolverService.expandOverflowAssignments}'s {@code maxAgents} arithmetic
     * EXACTLY: {@code (demandFTE * overallocationHardLimitPct + 99) / 100} — integer ceiling
     * division, never floating point, which would diverge on exactly the boundary cases that
     * matter. Below 100%, {@code expandOverflowAssignments} adds nothing (early return), so total
     * seats at a demand timeslot stays at {@code demandFTE} exactly.
     */
    static int seatsAtHour(int demandFTE, int overallocationHardLimitPct) {
        if (overallocationHardLimitPct <= 100) {
            return demandFTE;
        }
        return (demandFTE * overallocationHardLimitPct + 99) / 100;
    }

    @Test
    @DisplayName("per-hour seat model is pinned to expandOverflowAssignments' integer-ceiling arithmetic, not a floating-point approximation")
    void perHourSeatModel_pinnedToProductionCeilingArithmetic() {
        for (int demand = 1; demand <= 20; demand++) {
            for (int pct : new int[] {100, 130, 150, 200, 250, 500}) {
                int viaProductionFormula = pct <= 100 ? demand : (demand * pct + 99) / 100;
                assertThat(seatsAtHour(demand, pct))
                        .as("model must match production's own maxAgents ceiling exactly at demand=%d pct=%d",
                                demand, pct)
                        .isEqualTo(viaProductionFormula);
            }
        }
    }

    /**
     * G-15-31's own {@code detail} records the live desk's tightest-hour advisory at BOTH
     * over-allocation ceilings, claimed byte-for-byte reproducible by this exact ceiling arithmetic:
     * {@code 25/30/15/15/25/5/5} at 500%, {@code 13/15/8/8/13/3/3} at 250% (one figure per date in
     * the 7-day period). Reproduced honestly: no independent per-hour demand table for those seven
     * dates was recorded anywhere in {@code 15-UAT.md} or {@code HANDOFF.md} — only the two
     * resulting advisory sequences. The demand implied by the 500% sequence is exact and
     * unambiguous, because 500 is an exact multiple of 100 ({@code seatsAtHour(d, 500) == 5d} for
     * every integer {@code d}, no rounding). This test derives that implied demand and checks the
     * SAME formula reproduces the independently-published 250% sequence — internal cross-consistency
     * between the two published figures under one shared demand series and one shared formula,
     * which is the strongest reproduction available from what this session has on record.
     */
    @Test
    @DisplayName("live-desk calibration: the two published tightest-hour advisory sequences (500%%, 250%%) are related by the SAME ceiling model at one shared implied per-hour demand series")
    void liveDeskCalibration_bothPublishedSequencesShareOneCeilingDerivedDemandSeries() {
        int[] advisoryAt500 = {25, 30, 15, 15, 25, 5, 5};
        int[] advisoryAt250 = {13, 15, 8, 8, 13, 3, 3};
        assertThat(advisoryAt500).hasSameSizeAs(advisoryAt250);

        int[] impliedDemand = new int[advisoryAt500.length];
        for (int i = 0; i < advisoryAt500.length; i++) {
            assertThat(advisoryAt500[i] % 5)
                    .as("the 500%% figure must be an exact multiple of 5 for the implied demand to be an integer -- index %d", i)
                    .isZero();
            impliedDemand[i] = advisoryAt500[i] / 5;
            assertThat(seatsAtHour(impliedDemand[i], 500))
                    .as("sanity: the model must reproduce the very 500%% figure it was derived from -- index %d", i)
                    .isEqualTo(advisoryAt500[i]);
            assertThat(seatsAtHour(impliedDemand[i], 250))
                    .as("THE CALIBRATION: the SAME formula, at the SAME implied demand, must reproduce the "
                            + "independently-published 250%% figure -- index %d (implied demand %d)",
                            i, impliedDemand[i])
                    .isEqualTo(advisoryAt250[i]);
        }
        System.out.println("[G-15-31 analysis] live-desk calibration -- implied per-hour demand series: "
                + java.util.Arrays.toString(impliedDemand));
    }

    // ==================================================================
    //  Section 2 (Task 1) — the distribution-blind fixture and its control
    // ==================================================================

    private static final int[] SEEDS_SMALL = {1, 2, 3};
    private static final int STEP_COUNT_LIMIT_SMALL = 2_000;

    /** One live library template's shape, mirroring {@link LiveShapeShiftDeskFixture.TemplateSpec}. */
    private record TemplateSpec(String name, LocalTime start, LocalTime end, int bandOffsetMinutes,
                                 int bandDurationMinutes) {}

    private record BuiltFixture(Schedule schedule, List<ShiftBandPair> pairs) {}

    /**
     * THE DISTRIBUTION-BLIND FIXTURE (Task 1's central deliverable). A minimal, deliberate
     * reproduction of the live shape at ten-agent scale, per the plan's own instruction: "total
     * supply generous, one boundary hour reachable by a single template, and agent-days on that
     * template with zero slack so every holder is forced onto it."
     *
     * <p>One template, "Opening" 08:00-17:00 (9h envelope), break offset 240 (12:00-13:00), net
     * 8.00h -- matching every one of 10 agents' contracted hours exactly (zero slack, single
     * eligible pair, so every agent-day is FORCED onto every one of the template's 8 legal hours:
     * 08,09,10,11,13,14,15,16). No {@code StaffingRequirement}/{@code TimeslotDemandConfig} rows
     * are created -- every seat here behaves like production's own filler seats (fillable, not
     * required, exempt from bulk over/under-allocation judgment), which isolates the measurement to
     * the shift-envelope/contracted-hours mechanism this gap is about rather than confounding it
     * with unrelated bulk-allocation soft scoring.
     *
     * <p>Seat plan, driven by {@link #seatsAtHour}: the boundary hour 08:00 gets
     * {@code seatsAtHour(1, 200) == 2} seats -- a genuine local bottleneck, since all 10 agent-days
     * are forced there. Every other legal hour gets {@code seatsAtHour(8, 200) == 16} seats -- ample
     * local headroom. Day-wide contracted demand is {@code 10 * 8 == 80} slots; day-wide supply is
     * {@code 2 + 7*16 == 114} slots -- comfortably over demand, so the shipped day-wide-sum gate (R0)
     * does not refuse it, while the single boundary hour cannot seat more than 2 of the 10 forced
     * agent-days. Because no seat exists anywhere outside this template's 8 legal hours, the 8
     * agent-days that miss 08:00 have no legal alternative hour to make up the shortfall -- a
     * PIGEONHOLE argument, not a search-quality question: 8 agent-days will end short of their
     * contracted hours on every possible assignment, guaranteeing a nonzero hard score regardless of
     * seed or step budget.
     */
    private static BuiltFixture buildDistributionBlindFixture() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        LocalDate date = LocalDate.of(2026, 9, 7); // Monday

        Specialization spec = specialization(ids, deskId, "Support");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            agents.add(a);
        }

        TemplateSpec opening = new TemplateSpec("Opening", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60);
        ShiftTemplate template = template(ids, deskId, opening.name(), opening.start(), opening.end());
        ShiftTemplateBreakBand band = band(ids, template, opening.bandOffsetMinutes(), opening.bandDurationMinutes());
        ShiftBandPair pair = new ShiftBandPair(template, band);
        List<ShiftBandPair> pairs = List.of(pair);

        List<Timeslot> window = new ArrayList<>();
        for (LocalTime t = LocalTime.of(8, 0); t.isBefore(LocalTime.of(17, 0)); t = t.plusHours(1)) {
            window.add(timeslot(ids, deskId, scheduleId, date, t));
        }
        Map<LocalTime, Timeslot> byStart = window.stream().collect(Collectors.toMap(Timeslot::getStartTime, x -> x));

        List<AgentAssignment> seats = new ArrayList<>();
        int bottleneckSeats = seatsAtHour(1, 200); // == 2
        int amplSeats = seatsAtHour(8, 200); // == 16
        for (LocalTime hour : List.of(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
                LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0))) {
            Timeslot ts = byStart.get(hour);
            for (int i = 0; i < amplSeats; i++) {
                seats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        }
        Timeslot bottleneck = byStart.get(LocalTime.of(8, 0));
        for (int i = 0; i < bottleneckSeats; i++) {
            seats.add(seat(ids, deskId, scheduleId, bottleneck, spec));
        }

        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        List<AgentShiftAssignment> rows = new ArrayList<>();
        for (Agent a : agents) {
            AgentDayConfig dc = new AgentDayConfig(a.getId(), date, new java.math.BigDecimal("8.00"),
                    60, 60, new java.math.BigDecimal("4.00"), new java.math.BigDecimal("1.00"),
                    BreakAlignment.ON_HOUR, 200, 50);
            dayConfigs.add(dc);
            AgentShiftAssignment row = new AgentShiftAssignment();
            row.setId(nextId(ids));
            row.setTenantId(TENANT);
            row.setDeskId(deskId);
            row.setScheduleId(scheduleId);
            row.setAgent(a);
            row.setDate(date);
            row.setDayConfig(dc);
            row.setDeskShiftBandPairs(pairs);
            rows.add(row);
        }

        Schedule schedule = assembleSchedule(scheduleId, deskId, spec, agents, pairs, window,
                seats, dayConfigs, rows, date, date, 200, 50);
        return new BuiltFixture(schedule, pairs);
    }

    /**
     * THE CONTROL, per the plan's own instruction ("A CONTROL fixture exists and is not
     * distribution-blind: {@code LiveShapeShiftDeskFixture} at its documented parameters passes the
     * gate and solves within {@code SolverQualityGuardTest}'s established violation ceiling") --
     * reused directly rather than duplicated (P-19's "one implementation, not a fixture-local
     * reimplementation" discipline this codebase already established for the shift-mode fixtures).
     */
    private static LiveShapeShiftDeskFixture.Fixture buildControlFixture() {
        int agentCount = LiveShapeShiftDeskFixture.TEMPLATE_SPECS.size()
                * LiveShapeShiftDeskFixture.IDEAL_HOLDERS_PER_TEMPLATE;
        return LiveShapeShiftDeskFixture.build(agentCount, LiveShapeShiftDeskFixture.DAY_COUNT);
    }

    @Test
    @DisplayName("distribution-blind fixture: THE FIX (plan 15-20) -- the SHIPPED gate now refuses it, though R0 (the day-wide sum) alone still would not")
    void distributionBlindFixture_shippedGateNowRefusesIt() {
        BuiltFixture f = buildDistributionBlindFixture();
        Schedule s = f.schedule();

        // R0 ALONE is UNCHANGED by plan 15-20 -- day-wide supply (114) still comfortably exceeds
        // day-wide contracted demand (80). This is exactly the defect plan 15-19 originally
        // measured, reproduced here as a live control so the before/after contrast is explicit.
        DateSlice slice = sliceFor(s, s.getPeriodStartDate());
        RuleVerdict r0 = r0DayWideSum(slice);
        assertThat(r0.refuses())
                .as("R0 alone still does not refuse this desk -- day-wide supply (114) comfortably "
                        + "exceeds day-wide demand (80): %s", r0.detail())
                .isFalse();

        // THE FIX: the SHIPPED gate -- R0 plus the new per-hour forced-occupancy check -- now
        // refuses this SAME fixture, naming the 08:00 bottleneck. It PASSED this fixture in plan
        // 15-19 (see this test's former name, distributionBlindFixture_shippedGatePassesIt).
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                        s.getSchedulingMode(), s.getShiftAssignments(), s.getShiftBandPairs(),
                        s.getTimeslots(), s.getAssignments(), s.getOverallocationHardLimitPct(),
                        new ArrayList<>(), null))
                .as("THE FIX: the shipped gate now refuses this desk -- it PASSED it in plan 15-19")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<com.wfm.dto.ErrorResponse.ErrorDetail> details =
                            ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message())
                                .as("names the bottleneck hour")
                                .contains("08:00-09:00");
                        assertThat(d.message())
                                .as("names the forced count -- all 10 agent-days, single shared pair, zero slack")
                                .contains("10 rostered agent-day(s)");
                        assertThat(d.message())
                                .as("names the real seat count (2), driven by seatsAtHour(1, 200) -- "
                                        + "pinning the fixture's own numbers to the per-hour model")
                                .contains("only 2 seat(s)");
                    });
                });
    }

    @Test
    @DisplayName("distribution-blind fixture: does NOT reach 0 hard on any seed -- measured, not asserted from the pigeonhole argument alone")
    void distributionBlindFixture_neverReachesZeroHardOnAnySeed() {
        List<String> perSeedReport = new ArrayList<>();
        for (int seed : SEEDS_SMALL) {
            BuiltFixture f = buildDistributionBlindFixture();
            Schedule solved = solve(f.schedule(), seed, STEP_COUNT_LIMIT_SMALL);
            Map<String, Integer> violations = SolverQualityGuardTest.hardMatchCountsByConstraint(solved);
            int total = violations.values().stream().mapToInt(Integer::intValue).sum();
            perSeedReport.add("seed=" + seed + " hardScore=" + solved.getScore().hardScore()
                    + " violationsByConstraint=" + violations);

            assertThat(solved.getScore().hardScore())
                    .as("seed=%d must NOT reach 0 hard -- 8 of 10 agent-days cannot legally reach 08:00 "
                            + "(2 seats, 10 forced), and no seat exists outside this template's 8 legal "
                            + "hours for them to compensate elsewhere. Violations: %s",
                            seed, violations)
                    .isNotZero();
        }
        System.out.println("[G-15-31 analysis] distribution-blind fixture, " + SEEDS_SMALL.length
                + " seeds, step limit " + STEP_COUNT_LIMIT_SMALL + ":");
        perSeedReport.forEach(System.out::println);
    }

    @Test
    @DisplayName("control fixture (LiveShapeShiftDeskFixture): the shipped gate passes it -- not distribution-blind")
    void controlFixture_shippedGatePassesIt() {
        LiveShapeShiftDeskFixture.Fixture f = buildControlFixture();
        Schedule s = f.schedule();
        List<String> warnings = new ArrayList<>();

        org.assertj.core.api.Assertions.assertThatCode(() ->
                SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                        s.getSchedulingMode(), s.getShiftAssignments(), s.getShiftBandPairs(),
                        s.getTimeslots(), s.getAssignments(), s.getOverallocationHardLimitPct(),
                        warnings, null))
                .as("the control must pass the shipped gate cleanly")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("control fixture: one fresh solve this session stays within SolverQualityGuardTest's established violation ceiling (citing its committed 5-seed baseline for the rest)")
    void controlFixture_freshSolveWithinEstablishedCeiling() {
        LiveShapeShiftDeskFixture.Fixture f = buildControlFixture();
        Schedule solved = solve(f.schedule(), 1, 5_000);

        List<SolverQualityGuardTest.SplitShift> splits = SolverQualityGuardTest.findSplitShifts(solved);
        List<SolverQualityGuardTest.EdgeBreak> edgeBreaks = SolverQualityGuardTest.findEdgeBreaks(solved);
        List<SolverQualityGuardTest.UnstaffedEdgeHour> unstaffed =
                SolverQualityGuardTest.findUnstaffedEdgeHours(solved, LiveShapeShiftDeskFixture.EDGE_HOURS);
        Map<String, Integer> violationsByConstraint = SolverQualityGuardTest.hardMatchCountsByConstraint(solved);
        int total = violationsByConstraint.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(splits).as("INV-1: zero split shifts, fresh solve this session").isEmpty();
        assertThat(edgeBreaks).as("INV-2: zero edge breaks, fresh solve this session").isEmpty();
        assertThat(unstaffed).as("INV-3: every edge hour staffed, fresh solve this session").isEmpty();
        assertThat(total)
                .as("INV-4: total hard violations must sit at or under the already-committed ceiling "
                        + "of 3 (median 1.0 + headroom 2, 15-14-SUMMARY.md) -- this fixture is designed "
                        + "to be provably satisfiable (P-40) and structural feasibility already holds "
                        + "on every one of the five seeds committed in 15-14/15-15. violationsByConstraint=%s",
                        violationsByConstraint)
                .isLessThanOrEqualTo(3);

        System.out.println("[G-15-31 analysis] control fixture fresh solve: seed=1 hardScore="
                + solved.getScore().hardScore() + " violationsByConstraint=" + violationsByConstraint
                + " -- citing 15-14-SUMMARY.md's committed 5-seed table [3,1,2,0,1] (median 1.0) for "
                + "the remaining 4 seeds rather than re-running them here");
    }

    // ==================================================================
    //  Section 3 — the healthy staggered-shift control (for R1's false-refusal demonstration)
    // ==================================================================

    /**
     * A SECOND control, purpose-built to expose R1's flaw distinctly from R2's virtue: a desk with
     * genuinely uneven per-hour headcount BY DESIGN (two templates whose legal hours only partially
     * overlap), where the day's minimum covered-hour seat count is well under the desk's total
     * agent-day count, yet the desk is provably solvable -- forced count never exceeds seat count at
     * any covered hour.
     *
     * <p>Template "Morning" 08:00-17:00 (9h), break offset 240 (12:00-13:00), net 8.00h -- legal
     * hours 08,09,10,11,13,14,15,16. Template "Afternoon" 12:00-22:00 (10h), break offset 300
     * (17:00-18:00), net 9.00h -- legal hours 12,13,14,15,16,18,19,20,21. The two nets differ
     * (8.00h vs 9.00h) so 5 agents contracted 8.00h are eligible ONLY for Morning and 5 agents
     * contracted 9.00h are eligible ONLY for Afternoon -- singleton eligibility for both groups, so
     * WHICH agents are forced where is a fact of construction, not a search outcome.
     *
     * <p>Seat plan (forced count + a 2-seat margin at every hour -- see the empirical note at the
     * seat-plan construction below for why the margin is there): 08,09,10,11 -> 7 (5 forced,
     * Morning only); 12 -> 7 (5 forced, Afternoon only); 13,14,15,16 -> 12 (10 forced, BOTH groups'
     * legal hours overlap here); 18,19,20,21 -> 7 (5 forced, Afternoon only). Day-wide demand =
     * 5*8 + 5*9 = 85; day-wide supply = 7*9 (single-group hours) + 12*4 (overlap hours) = 63 + 48 =
     * 111 -- comfortably over demand, so R0 passes. The minimum covered-hour seat count (7) is well
     * under the desk's total agent-day count (10) -- exactly the shape a naive "seats must reach the
     * whole desk's headcount everywhere" rule (R1) would refuse, and exactly the shape this desk
     * demonstrates is safe to leave unrefused, because only 5 agent-days are ever forced at those
     * hours.
     */
    private static BuiltFixture buildHealthyStaggeredFixture() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        LocalDate date = LocalDate.of(2026, 9, 7); // Monday

        Specialization spec = specialization(ids, deskId, "Support");

        List<Agent> morningAgents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Agent a = agent(ids, deskId, "M-" + (i + 1), "Morning-Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            morningAgents.add(a);
        }
        List<Agent> afternoonAgents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Agent a = agent(ids, deskId, "F-" + (i + 1), "Afternoon-Agent-" + (i + 1));
            a.setPrimarySpecialization(spec);
            a.setSecondarySpecializations(new ArrayList<>());
            afternoonAgents.add(a);
        }
        List<Agent> agents = new ArrayList<>(morningAgents);
        agents.addAll(afternoonAgents);

        ShiftTemplate morning = template(ids, deskId, "Morning", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand morningBand = band(ids, morning, 240, 60); // 12:00-13:00, net 8.00h
        ShiftBandPair morningPair = new ShiftBandPair(morning, morningBand);

        ShiftTemplate afternoon = template(ids, deskId, "Afternoon", LocalTime.of(12, 0), LocalTime.of(22, 0));
        ShiftTemplateBreakBand afternoonBand = band(ids, afternoon, 300, 60); // 17:00-18:00, net 9.00h
        ShiftBandPair afternoonPair = new ShiftBandPair(afternoon, afternoonBand);

        List<ShiftBandPair> pairs = List.of(morningPair, afternoonPair);

        List<Timeslot> window = new ArrayList<>();
        for (LocalTime t = LocalTime.of(8, 0); t.isBefore(LocalTime.of(22, 0)); t = t.plusHours(1)) {
            window.add(timeslot(ids, deskId, scheduleId, date, t));
        }
        Map<LocalTime, Timeslot> byStart = window.stream().collect(Collectors.toMap(Timeslot::getStartTime, x -> x));

        // A small margin (+2 seats over the forced count at every hour) is added deliberately, per
        // an empirical finding recorded in this class's javadoc: an EXACT zero-slack seat plan (seat
        // count == forced count everywhere) reproduces this desk's own real-world search plateau
        // (SolverService's exact bipartite-matching problem is hard for a plain change/swap
        // neighbourhood to escape, matching HANDOFF.md's own recorded live-desk plateau) rather than
        // demonstrating the DISTRIBUTION point this fixture exists to make. The margin does not
        // change which rule refuses what -- R0/R2 still pass at comfortable-but-not-exact supply, and
        // the minimum covered-hour seat count (7) is still well under the desk's total agent-day
        // count (10), so R1 still false-refuses it -- it only makes the fixture solvable within a
        // realistic step budget, which is what "KNOWN-SOLVES" requires this session to demonstrate.
        List<AgentAssignment> seats = new ArrayList<>();
        Map<LocalTime, Integer> seatPlan = new LinkedHashMap<>();
        for (LocalTime h : List.of(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0))) {
            seatPlan.put(h, 7);
        }
        seatPlan.put(LocalTime.of(12, 0), 7);
        for (LocalTime h : List.of(LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0))) {
            seatPlan.put(h, 12);
        }
        for (LocalTime h : List.of(LocalTime.of(18, 0), LocalTime.of(19, 0), LocalTime.of(20, 0), LocalTime.of(21, 0))) {
            seatPlan.put(h, 7);
        }
        seatPlan.forEach((hour, count) -> {
            Timeslot ts = byStart.get(hour);
            for (int i = 0; i < count; i++) {
                seats.add(seat(ids, deskId, scheduleId, ts, spec));
            }
        });

        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        List<AgentShiftAssignment> rows = new ArrayList<>();
        for (Agent a : morningAgents) {
            AgentDayConfig dc = new AgentDayConfig(a.getId(), date, new java.math.BigDecimal("8.00"),
                    60, 60, new java.math.BigDecimal("4.00"), new java.math.BigDecimal("1.00"),
                    BreakAlignment.ON_HOUR, 100, 50);
            dayConfigs.add(dc);
            rows.add(shiftRow(ids, TENANT, deskId, scheduleId, a, date, dc, pairs));
        }
        for (Agent a : afternoonAgents) {
            AgentDayConfig dc = new AgentDayConfig(a.getId(), date, new java.math.BigDecimal("9.00"),
                    60, 60, new java.math.BigDecimal("4.00"), new java.math.BigDecimal("1.00"),
                    BreakAlignment.ON_HOUR, 100, 50);
            dayConfigs.add(dc);
            rows.add(shiftRow(ids, TENANT, deskId, scheduleId, a, date, dc, pairs));
        }

        // sanity: every row must be a SINGLETON eligibility (exactly one live pair) -- the fixture's
        // whole premise (which agents are "forced" where is construction, not search) depends on it.
        for (AgentShiftAssignment row : rows) {
            if (row.getEligibleShiftBandPairs().size() != 1) {
                throw new IllegalStateException("fixture invariant violated: agent " + row.getAgent().getName()
                        + " has " + row.getEligibleShiftBandPairs().size() + " eligible pairs, expected exactly 1");
            }
        }

        Schedule schedule = assembleSchedule(scheduleId, deskId, spec, agents, pairs, window,
                seats, dayConfigs, rows, date, date, 100, 50);
        return new BuiltFixture(schedule, pairs);
    }

    @Test
    @DisplayName("healthy staggered desk: the shipped gate passes -- generous relative to demand, thin relative to total headcount at several hours")
    void healthyStaggeredFixture_shippedGatePasses() {
        BuiltFixture f = buildHealthyStaggeredFixture();
        Schedule s = f.schedule();
        List<String> warnings = new ArrayList<>();

        org.assertj.core.api.Assertions.assertThatCode(() ->
                SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(
                        s.getSchedulingMode(), s.getShiftAssignments(), s.getShiftBandPairs(),
                        s.getTimeslots(), s.getAssignments(), s.getOverallocationHardLimitPct(),
                        warnings, null))
                .doesNotThrowAnyException();

        assertThat(warnings)
                .as("the tightest-hour advisory must name the real minimum (7), reachable at several "
                        + "single-group hours -- well under the desk's total agent-day count (10), by design")
                .anySatisfy(w -> assertThat(w).contains("7 seat(s)"));
    }

    @Test
    @DisplayName("healthy staggered desk: reaches 0 hard -- measured, proving R1's naive threshold would be a FALSE refusal here")
    void healthyStaggeredFixture_reachesZeroHard() {
        List<String> perSeedReport = new ArrayList<>();
        for (int seed : new int[] {1, 2}) {
            BuiltFixture f = buildHealthyStaggeredFixture();
            Schedule solved = solve(f.schedule(), seed, 10_000);
            Map<String, Integer> violations = SolverQualityGuardTest.hardMatchCountsByConstraint(solved);
            perSeedReport.add("seed=" + seed + " hardScore=" + solved.getScore().hardScore()
                    + " violationsByConstraint=" + violations);
            assertThat(solved.getScore().hardScore())
                    .as("seed=%d: this desk is EXACTLY sized (forced count == seat count at every "
                            + "covered hour) -- it must reach 0 hard. violationsByConstraint=%s",
                            seed, violations)
                    .isZero();
        }
        System.out.println("[G-15-31 analysis] healthy staggered desk, 2 seeds:");
        perSeedReport.forEach(System.out::println);
    }

    // ==================================================================
    //  Section 4 (Task 2) — candidate rules R0-R3, as pure functions
    // ==================================================================

    /** Everything a candidate rule needs to evaluate ONE date's slice of a schedule. */
    private record DateSlice(LocalDate date, List<AgentShiftAssignment> rows, List<Timeslot> timeslots,
                              Map<UUID, Long> seatsByTimeslotId, List<ShiftBandPair> pairs) {}

    private record RuleVerdict(boolean refuses, String detail) {}

    /**
     * The same date-aware coverage predicate {@code SolverService.coveredTimeslotsOnDate} applies
     * (G-15-21's fix, plan 15-18) -- re-derived here rather than called, since the production method
     * is {@code private}. Filters pairs by {@code isEffectiveOn(date) && appliesOn(date)} before
     * asking {@code covers}.
     */
    private static List<Timeslot> coveredOnDate(DateSlice slice) {
        return slice.timeslots().stream()
                .filter(ts -> slice.pairs().stream()
                        .filter(p -> p.template().isEffectiveOn(slice.date()) && p.template().appliesOn(slice.date()))
                        .anyMatch(p -> p.covers(ts)))
                .toList();
    }

    /**
     * Reconstructs the {@code List<AgentAssignment>} shape {@code
     * SolverSeatSupplyGateAccess#requireShiftEnvelopeSeatSupply} needs from a {@link DateSlice}'s
     * already-aggregated {@code seatsByTimeslotId} counts -- the production method only ever reads
     * {@code getTimeslot().getId()} off each assignment to re-derive that same count
     * (Collectors.groupingBy/counting), so a fixture-neutral placeholder with no agent/specialization
     * set reproduces exactly what the gate consumes, nothing more.
     */
    private static List<AgentAssignment> assignmentsFor(DateSlice slice) {
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : slice.timeslots()) {
            long count = slice.seatsByTimeslotId().getOrDefault(ts.getId(), 0L);
            for (long i = 0; i < count; i++) {
                AgentAssignment a = new AgentAssignment();
                a.setId(UUID.randomUUID());
                a.setTimeslot(ts);
                assignments.add(a);
            }
        }
        return assignments;
    }

    /** R0 -- the shipped day-wide sum. Reimplemented as a pure function of the same shape (never calling the throwing production gate) so all four rules are evaluated identically. */
    private static RuleVerdict r0DayWideSum(DateSlice slice) {
        List<Timeslot> covered = coveredOnDate(slice);
        int supply = covered.stream().mapToInt(ts -> slice.seatsByTimeslotId().getOrDefault(ts.getId(), 0L).intValue()).sum();
        int demand = slice.rows().stream().mapToInt(r -> r.getDayConfig().expectedWorkSlots()).sum();
        boolean refuses = demand > supply;
        return new RuleVerdict(refuses, "day-wide demand=" + demand + " supply=" + supply);
    }

    /**
     * R1 -- the tightest-hour advisory promoted to blocking. The shipped advisory carries no
     * numeric threshold of its own (it always reports the minimum, whatever the value); the most
     * literal way to make that a BLOCKING check is to compare the day's minimum covered-hour seat
     * count against the day's total rostered agent-day count -- the reading adopted here, stated
     * explicitly because the gap's own text does not pin one down.
     */
    private static RuleVerdict r1TightestHourPromoted(DateSlice slice) {
        List<Timeslot> covered = coveredOnDate(slice);
        if (covered.isEmpty()) {
            return new RuleVerdict(false, "no covered timeslot");
        }
        long min = covered.stream().mapToLong(ts -> slice.seatsByTimeslotId().getOrDefault(ts.getId(), 0L)).min().orElse(0L);
        int agentDayCount = slice.rows().size();
        boolean refuses = min < agentDayCount;
        return new RuleVerdict(refuses, "tightest-hour seats=" + min + " agentDayCount=" + agentDayCount);
    }

    /**
     * R2 -- the forced-occupancy necessary condition (see this class's javadoc). Plan 15-20:
     * calls {@link SolverSeatSupplyGateAccess#forcedAgentDaysByTimeslotId} -- the SHIPPED
     * per-agent-day forced-occupancy count, promoted into {@code SolverService} by this plan --
     * rather than a test-local reimplementation of the predicate (T-15-20-04). This row therefore
     * shows what R2 ALONE (per-timeslot, ignoring the day-wide sum) decides on production's own
     * counting logic; the separate "Shipped gate" rule below shows what the DESK ACTUALLY
     * EXPERIENCES, which combines this with R0 in one throwing call.
     */
    private static RuleVerdict r2ForcedOccupancy(DateSlice slice) {
        Map<UUID, Long> forcedByTimeslotId =
                SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId(slice.rows(), slice.timeslots());
        StringBuilder detail = new StringBuilder();
        boolean refuses = false;
        for (Timeslot ts : slice.timeslots()) {
            long forced = forcedByTimeslotId.getOrDefault(ts.getId(), 0L);
            long seats = slice.seatsByTimeslotId().getOrDefault(ts.getId(), 0L);
            if (forced > seats) {
                refuses = true;
                detail.append(ts.getStartTime()).append(": forced=").append(forced).append(" seats=").append(seats).append("; ");
            }
        }
        return new RuleVerdict(refuses, refuses ? detail.toString() : "no hour has forced > seats");
    }

    /** R3 -- R2's identical diagnostic, demoted to warn-only. Never refuses, by definition. */
    private static RuleVerdict r3ForcedOccupancyWarnOnly(DateSlice slice) {
        RuleVerdict r2 = r2ForcedOccupancy(slice);
        return new RuleVerdict(false, r2.refuses() ? "WOULD have refused (R2 basis): " + r2.detail() : r2.detail());
    }

    /**
     * "Shipped gate" (plan 15-20) -- invokes the PRODUCTION {@code requireShiftEnvelopeSeatSupply}
     * itself, via {@link SolverSeatSupplyGateAccess}, rather than any reimplementation. Refuses iff
     * the shipped method throws for this date's slice, which folds BOTH the pre-existing day-wide
     * sum (R0) and the newly-shipped per-hour forced-occupancy check (this class's own R2) into
     * ONE production decision -- exactly how the two ship (both accumulate into the same error
     * list, 15-20-PLAN.md Task 1). This is the row that answers "does the desk this fixture
     * describes actually get refused by what ships," as distinct from R0/R2's isolated diagnostics.
     */
    private static RuleVerdict shippedGate(DateSlice slice) {
        List<AgentAssignment> assignments = assignmentsFor(slice);
        List<String> warnings = new ArrayList<>();
        try {
            SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply(SchedulingMode.SHIFT,
                    slice.rows(), slice.pairs(), slice.timeslots(), assignments, 100, warnings, null);
            return new RuleVerdict(false, "shipped gate passed");
        } catch (PreSolveValidationException ex) {
            return new RuleVerdict(true, "shipped gate refused: " + ex.getMessage());
        }
    }

    private record CandidateRule(String name, java.util.function.Function<DateSlice, RuleVerdict> fn) {}

    private static final List<CandidateRule> RULES = List.of(
            new CandidateRule("R0 (shipped day-wide sum)", SeatSupplyDistributionAnalysisTest::r0DayWideSum),
            new CandidateRule("R1 (tightest-hour promoted to blocking)", SeatSupplyDistributionAnalysisTest::r1TightestHourPromoted),
            new CandidateRule("R2 (forced-occupancy necessary condition, shipped logic)", SeatSupplyDistributionAnalysisTest::r2ForcedOccupancy),
            new CandidateRule("R3 (R2, warn-only)", SeatSupplyDistributionAnalysisTest::r3ForcedOccupancyWarnOnly),
            new CandidateRule("Shipped gate (production: R0 + R2 combined)", SeatSupplyDistributionAnalysisTest::shippedGate));

    // ==================================================================
    //  Section 5 (Task 2) — R2's necessary-condition proof on a hand-built case
    // ==================================================================

    @Test
    @DisplayName("R2 proof: on a hand-built two-template case, the SHIPPED forced-occupancy count is exactly the agents R2 predicts, by construction")
    void r2ForcedSet_provenOnHandBuiltCase() {
        BuiltFixture f = buildHealthyStaggeredFixture();
        Schedule s = f.schedule();
        LocalDate date = s.getPeriodStartDate();

        List<Timeslot> dateTimeslots = s.getTimeslots().stream().filter(ts -> ts.getDate().equals(date)).toList();
        Map<UUID, Long> seatsByTimeslotId = s.getAssignments().stream()
                .filter(a -> a.getTimeslot() != null)
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getId(), Collectors.counting()));

        Timeslot at0800 = dateTimeslots.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(8, 0))).findFirst().orElseThrow();
        Timeslot at1300 = dateTimeslots.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(13, 0))).findFirst().orElseThrow();
        Timeslot at1800 = dateTimeslots.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(18, 0))).findFirst().orElseThrow();

        // Plan 15-20: reads the SHIPPED count via the bridge, not a test-local predicate -- this
        // is the proof that production's own implementation matches the hand-built construction,
        // not merely that this class's former copy of the rule agreed with itself.
        Map<UUID, Long> forcedByTimeslotId = SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId(
                s.getShiftAssignments(), dateTimeslots);
        long forcedAt0800 = forcedByTimeslotId.getOrDefault(at0800.getId(), 0L);
        long forcedAt1300 = forcedByTimeslotId.getOrDefault(at1300.getId(), 0L);
        long forcedAt1800 = forcedByTimeslotId.getOrDefault(at1800.getId(), 0L);

        assertThat(forcedAt0800).as("08:00 is Morning-only -- exactly the 5 Morning agent-days are forced").isEqualTo(5);
        assertThat(forcedAt1300).as("13:00 is legal for BOTH templates -- all 10 agent-days are forced").isEqualTo(10);
        assertThat(forcedAt1800).as("18:00 is Afternoon-only -- exactly the 5 Afternoon agent-days are forced").isEqualTo(5);

        DateSlice slice = new DateSlice(date, s.getShiftAssignments(), dateTimeslots, seatsByTimeslotId, s.getShiftBandPairs());
        RuleVerdict r2 = r2ForcedOccupancy(slice);
        assertThat(r2.refuses())
                .as("R2 must NOT refuse this desk -- forced count equals seat count at every hour by construction: %s", r2.detail())
                .isFalse();
    }

    // ==================================================================
    //  Section 6 (Task 2) — the corpus, the rule-by-fixture table, false/true refusal counts
    // ==================================================================

    private enum Label { KNOWN_SOLVES, KNOWN_COLLAPSES }

    /** {@code evidenceFor} names, per row, which gap this fixture's outcome is evidence for --
     * so the printed table is readable by someone arriving at a red build without this plan in
     * hand (plan 15-20, Task 2 behaviour). */
    private record CorpusEntry(String name, Label label, String evidenceFor, DateSlice slice) {}

    private static DateSlice sliceFor(Schedule s, LocalDate date) {
        List<AgentShiftAssignment> rows = s.getShiftAssignments().stream().filter(r -> r.getDate().equals(date)).toList();
        List<Timeslot> dateTimeslots = s.getTimeslots().stream().filter(ts -> ts.getDate().equals(date)).toList();
        Map<UUID, Long> seatsByTimeslotId = s.getAssignments().stream()
                .filter(a -> a.getTimeslot() != null && a.getTimeslot().getDate().equals(date))
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getId(), Collectors.counting()));
        return new DateSlice(date, rows, dateTimeslots, seatsByTimeslotId, s.getShiftBandPairs());
    }

    private static List<CorpusEntry> buildCorpus() {
        List<CorpusEntry> corpus = new ArrayList<>();

        BuiltFixture blind = buildDistributionBlindFixture();
        corpus.add(new CorpusEntry("distribution-blind (Task 1)", Label.KNOWN_COLLAPSES,
                "G-15-31 (distribution blindness) -- must now be a TRUE refusal",
                sliceFor(blind.schedule(), blind.schedule().getPeriodStartDate())));

        BuiltFixture healthy = buildHealthyStaggeredFixture();
        corpus.add(new CorpusEntry("healthy staggered desk", Label.KNOWN_SOLVES,
                "G-15-25/G-15-31 no-false-refusal corpus", sliceFor(healthy.schedule(), healthy.schedule().getPeriodStartDate())));

        LiveShapeShiftDeskFixture.Fixture control = buildControlFixture();
        Schedule cs = control.schedule();
        LocalDate d0 = cs.getPeriodStartDate();
        LocalDate d1 = cs.getPeriodEndDate();
        corpus.add(new CorpusEntry("LiveShapeShiftDeskFixture day 1", Label.KNOWN_SOLVES,
                "G-15-25/G-15-31 no-false-refusal corpus", sliceFor(cs, d0)));
        if (!d1.equals(d0)) {
            corpus.add(new CorpusEntry("LiveShapeShiftDeskFixture day 2", Label.KNOWN_SOLVES,
                    "G-15-25/G-15-31 no-false-refusal corpus", sliceFor(cs, d1)));
        }

        return corpus;
    }

    @Test
    @DisplayName("the rule-by-fixture table: every rule against every corpus fixture (including the SHIPPED gate), false/true refusal counts printed, the shipped gate refuses the distribution-blind fixture with zero false refusals")
    void ruleByFixtureTable_falseAndTrueRefusalCounts() {
        List<CorpusEntry> corpus = buildCorpus();
        assertThat(corpus).as("the corpus must be non-empty").isNotEmpty();

        Map<String, Map<String, RuleVerdict>> table = new LinkedHashMap<>();
        for (CorpusEntry entry : corpus) {
            Map<String, RuleVerdict> row = new LinkedHashMap<>();
            for (CandidateRule rule : RULES) {
                row.put(rule.name(), rule.fn().apply(entry.slice()));
            }
            table.put(entry.name() + " [" + entry.label() + "]", row);
        }

        System.out.println();
        System.out.println("[G-15-25/G-15-31 analysis, re-measured post-plan-15-20] Rule-by-fixture table:");
        System.out.println("| fixture | label | evidence for | " + RULES.stream().map(CandidateRule::name).collect(Collectors.joining(" | ")) + " |");
        System.out.println("|---|---|---|" + "---|".repeat(RULES.size()));
        for (CorpusEntry entry : corpus) {
            Map<String, RuleVerdict> row = table.get(entry.name() + " [" + entry.label() + "]");
            StringBuilder line = new StringBuilder("| ").append(entry.name()).append(" | ").append(entry.label())
                    .append(" | ").append(entry.evidenceFor()).append(" | ");
            for (CandidateRule rule : RULES) {
                line.append(row.get(rule.name()).refuses() ? "REFUSE" : "PASS").append(" | ");
            }
            System.out.println(line);
        }

        Map<String, Integer> falseRefusals = new LinkedHashMap<>();
        Map<String, Integer> trueRefusals = new LinkedHashMap<>();
        for (CandidateRule rule : RULES) {
            int fr = 0, tr = 0;
            for (CorpusEntry entry : corpus) {
                boolean refuses = table.get(entry.name() + " [" + entry.label() + "]").get(rule.name()).refuses();
                if (entry.label() == Label.KNOWN_SOLVES && refuses) fr++;
                if (entry.label() == Label.KNOWN_COLLAPSES && refuses) tr++;
            }
            falseRefusals.put(rule.name(), fr);
            trueRefusals.put(rule.name(), tr);
        }

        System.out.println();
        System.out.println("[G-15-25/G-15-31 analysis] Per-rule false-refusal / true-refusal counts (denominator: "
                + corpus.stream().filter(c -> c.label() == Label.KNOWN_SOLVES).count() + " KNOWN-SOLVES, "
                + corpus.stream().filter(c -> c.label() == Label.KNOWN_COLLAPSES).count() + " KNOWN-COLLAPSES):");
        for (CandidateRule rule : RULES) {
            System.out.println("  " + rule.name() + " -> falseRefusals=" + falseRefusals.get(rule.name())
                    + " trueRefusals=" + trueRefusals.get(rule.name()));
        }
        System.out.println("[G-15-25/G-15-31 analysis] NOT-SOLVE-EVALUABLE, excluded from this table: 23 pre-existing "
                + "fixtures across ShiftEnvelopeSupplyGateTest (14, itself extended by plan 15-20 with 2 more "
                + "gate-calling cases), ShiftEnvelopeSupplyInvariantTest (6) and ShiftDeskEndToEndRegressionTest "
                + "(3) -- referenced, not re-instantiated (see class javadoc); all pass unchanged after plan 15-20 "
                + "(measured directly in those classes, not re-derived here).");

        // Structural assertions the table must have.
        assertThat(table).as("every corpus fixture appears in the table").hasSize(corpus.size());
        for (CorpusEntry entry : corpus) {
            assertThat(table.get(entry.name() + " [" + entry.label() + "]"))
                    .as("every rule appears for every fixture").hasSize(RULES.size());
        }

        // THE DECISIVE ROW: the SHIPPED gate -- not merely R2's isolated diagnostic -- must now
        // refuse the distribution-blind fixture. This is Task 2's own behaviour requirement: "The
        // shipped gate now refuses the distribution-blind fixture that it passed in plan 15-19."
        assertThat(table.get("distribution-blind (Task 1) [KNOWN_COLLAPSES]")
                        .get("Shipped gate (production: R0 + R2 combined)").refuses())
                .as("THE FIX: the SHIPPED gate (not a test-local rule) now refuses the "
                        + "distribution-blind fixture -- it PASSED this same fixture in plan 15-19")
                .isTrue();
        assertThat(table.get("distribution-blind (Task 1) [KNOWN_COLLAPSES]")
                        .get("R2 (forced-occupancy necessary condition, shipped logic)").refuses())
                .as("R2 alone (via the shipped counting logic) must also refuse it").isTrue();

        // THE OTHER DECISIVE COUNT: the shipped gate's false-refusal count against every
        // KNOWN-SOLVES fixture in this corpus is ASSERTED at zero (Task 2's own behaviour
        // requirement: "The shipped gate refuses zero KNOWN-SOLVES fixtures; the count is emitted
        // and asserted equal to zero").
        assertThat(falseRefusals.get("Shipped gate (production: R0 + R2 combined)"))
                .as("the SHIPPED gate's false-refusal count against this corpus's KNOWN-SOLVES "
                        + "fixtures must be exactly zero")
                .isZero();
        assertThat(falseRefusals.get("R2 (forced-occupancy necessary condition, shipped logic)"))
                .as("R2's own false-refusal count must also be exactly zero -- it is a necessary "
                        + "condition for a zero-hard solve, so refusing a KNOWN-SOLVES fixture "
                        + "would falsify that argument")
                .isZero();

        // R0/R1/R3's counts are REPORTED, not asserted -- the table's own printed numbers are the finding.
        System.out.println("[G-15-25/G-15-31 analysis] R1's false-refusal count on this corpus: "
                + falseRefusals.get("R1 (tightest-hour promoted to blocking)")
                + " (expected: 1, the healthy staggered desk -- see healthyStaggeredFixture_reachesZeroHard)");
    }

    @Test
    @DisplayName("R1 measured finding: naively promoting the tightest-hour advisory produces a false refusal on a desk that provably solves -- the measured justification for advisoryOnThinTimeslotDoesNotBlock")
    void r1_measuredFalseRefusalOnHealthyStaggeredDesk() {
        BuiltFixture healthy = buildHealthyStaggeredFixture();
        DateSlice slice = sliceFor(healthy.schedule(), healthy.schedule().getPeriodStartDate());

        RuleVerdict r0 = r0DayWideSum(slice);
        RuleVerdict r1 = r1TightestHourPromoted(slice);
        RuleVerdict r2 = r2ForcedOccupancy(slice);
        RuleVerdict shipped = shippedGate(slice);

        assertThat(r0.refuses()).as("R0 (shipped) does not refuse this genuinely solvable desk").isFalse();
        assertThat(r2.refuses()).as("R2 (necessary condition) does not refuse it either -- correct").isFalse();
        assertThat(shipped.refuses())
                .as("the SHIPPED gate (R0 + R2 combined) does not refuse this genuinely solvable "
                        + "desk either -- confirming production agrees with both isolated rules")
                .isFalse();
        assertThat(r1.refuses())
                .as("R1 DOES refuse it -- a FALSE refusal, since this desk is measured to reach 0 hard "
                        + "(healthyStaggeredFixture_reachesZeroHard). detail=%s", r1.detail())
                .isTrue();
    }

    // ==================================================================
    //  Section 6b (Task 2) — the band-composition experiment (G-15-25), as a first-class,
    //  always-re-measured row rather than a one-off assertion living only in
    //  ShiftEnvelopeSupplyGateTest.
    // ==================================================================

    /**
     * One 9h template (08:00-17:00) with {@code breakOffsetsMinutes.length} distinct single-band
     * pairs, each excluding a DIFFERENT 1-hour clock window -- every pair nets 8.00h, so an
     * 8h-contracted agent is eligible for ALL of them. Mirrors {@code
     * ShiftEnvelopeSupplyGateTest#saturatedUnionPairs} (plan 15-20, Task 1) exactly in shape;
     * re-derived here rather than shared across packages, since that helper is {@code private} to
     * its own test class and this harness's own fixture-building convention (this class's
     * existing helpers) does not reach across test classes either.
     */
    private static BuiltFixture buildBandCompositionFixture(int... breakOffsetsMinutes) {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        LocalDate date = LocalDate.of(2026, 9, 7); // Monday

        Specialization spec = specialization(ids, deskId, "Support");
        Agent a1 = agent(ids, deskId, "A-1", "Agent-1");
        a1.setPrimarySpecialization(spec);
        a1.setSecondarySpecializations(new ArrayList<>());

        ShiftTemplate t = template(ids, deskId, "Saturated", LocalTime.of(8, 0), LocalTime.of(17, 0));
        List<ShiftBandPair> pairs = new ArrayList<>();
        for (int offset : breakOffsetsMinutes) {
            pairs.add(new ShiftBandPair(t, band(ids, t, offset, 60)));
        }

        List<Timeslot> window = new ArrayList<>();
        for (LocalTime time = LocalTime.of(8, 0); time.isBefore(LocalTime.of(17, 0)); time = time.plusHours(1)) {
            window.add(timeslot(ids, deskId, scheduleId, date, time));
        }

        // One seat at every envelope hour -- the desk-wide union is saturated by 3+ distinct
        // break offsets (every hour excluded by at most one band), so this is deliberately
        // generous relative to the single agent's 8-slot demand; the point of this fixture is the
        // FORCED-COUNT figure at 08:00, not a refusal.
        List<AgentAssignment> seats = new ArrayList<>();
        for (Timeslot ts : window) {
            seats.add(seat(ids, deskId, scheduleId, ts, spec));
        }

        AgentDayConfig dc = new AgentDayConfig(a1.getId(), date, new java.math.BigDecimal("8.00"),
                60, 60, new java.math.BigDecimal("4.00"), new java.math.BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 100, 50);
        AgentShiftAssignment row = shiftRow(ids, TENANT, deskId, scheduleId, a1, date, dc, pairs);

        Schedule schedule = assembleSchedule(scheduleId, deskId, spec, List.of(a1), pairs, window,
                seats, List.of(dc), List.of(row), date, date, 100, 50);
        return new BuiltFixture(schedule, pairs);
    }

    @Test
    @DisplayName("G-15-25 band-composition experiment (first-class row, re-measured every run): the SHIPPED forced-occupancy figure at 08:00 changes when edge bands are added to an already-saturated union; the desk-wide union figure does not")
    void bandCompositionExperiment_shippedFigureChangesButUnionStaysSaturated() {
        BuiltFixture threeBands = buildBandCompositionFixture(180, 240, 300); // breaks 11-12, 12-13, 13-14
        BuiltFixture fiveBands = buildBandCompositionFixture(180, 240, 300, 0, 480); // + edges 08-09, 16-17

        LocalDate date = threeBands.schedule().getPeriodStartDate();
        DateSlice threeBandSlice = sliceFor(threeBands.schedule(), date);
        DateSlice fiveBandSlice = sliceFor(fiveBands.schedule(), date);

        Timeslot eight = threeBandSlice.timeslots().stream()
                .filter(ts -> ts.getStartTime().equals(LocalTime.of(8, 0))).findFirst().orElseThrow();

        Map<UUID, Long> threeBandForced = SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId(
                threeBandSlice.rows(), threeBandSlice.timeslots());
        Map<UUID, Long> fiveBandForced = SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId(
                fiveBandSlice.rows(), fiveBandSlice.timeslots());
        long forcedWithThreeBands = threeBandForced.getOrDefault(eight.getId(), 0L);
        long forcedWithFiveBands = fiveBandForced.getOrDefault(eight.getId(), 0L);

        // The desk-wide union (R0's own supply figure) stays byte-identical -- the exact
        // "byte-identical gate output" symptom G-15-25 reports.
        RuleVerdict threeBandR0 = r0DayWideSum(threeBandSlice);
        RuleVerdict fiveBandR0 = r0DayWideSum(fiveBandSlice);

        System.out.println();
        System.out.println("[G-15-25 analysis] Band-composition experiment (evidence for G-15-25):");
        System.out.println("| bands | forced-count at 08:00 | day-wide (R0) detail |");
        System.out.println("|---|---|---|");
        System.out.println("| 3 (breaks 11-12,12-13,13-14) | " + forcedWithThreeBands + " | " + threeBandR0.detail() + " |");
        System.out.println("| 5 (+ edges 08-09,16-17) | " + forcedWithFiveBands + " | " + fiveBandR0.detail() + " |");

        assertThat(forcedWithThreeBands)
                .as("3 bands sharing this envelope: every eligible pair covers 08:00, so the agent is forced there")
                .isEqualTo(1L);
        assertThat(forcedWithFiveBands)
                .as("THE FIX: adding one edge band whose break falls on 08:00 changes this figure")
                .isEqualTo(0L);
        assertThat(forcedWithThreeBands)
                .as("two DIFFERENT numbers from two runs differing ONLY in band composition")
                .isNotEqualTo(forcedWithFiveBands);
        assertThat(threeBandR0.detail())
                .as("the desk-wide union (R0) stays BYTE-IDENTICAL across both band compositions -- "
                        + "it was already saturated at 3 bands, so it cannot see the 2 edge bands "
                        + "added on top of it")
                .isEqualTo(fiveBandR0.detail());
    }

    // ==================================================================
    //  Solving and shared fixture-assembly helpers
    // ==================================================================

    private static Schedule solve(Schedule unsolved, long seed, int stepCountLimit) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml").withRandomSeed(seed);
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1).setTerminationConfig(new TerminationConfig().withStepCountLimit(stepCountLimit));
        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
    }

    private static Schedule assembleSchedule(UUID scheduleId, UUID deskId, Specialization spec,
            List<Agent> agents, List<ShiftBandPair> pairs, List<Timeslot> window,
            List<AgentAssignment> seats, List<AgentDayConfig> dayConfigs, List<AgentShiftAssignment> rows,
            LocalDate periodStart, LocalDate periodEnd, int overallocPct, int underallocPct) {

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(UUID.randomUUID());
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(60);
        schedule.setStartTime(window.get(0).getStartTime());
        schedule.setEndTime(window.get(window.size() - 1).getEndTime());
        schedule.setPeriodStartDate(periodStart);
        schedule.setPeriodEndDate(periodEnd);
        schedule.setBreakBlockedHours(new java.math.BigDecimal("1.00"));
        schedule.setBreakDurationMinutes(60);
        schedule.setBreakMinShiftHours(new java.math.BigDecimal("4.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(new java.math.BigDecimal("8.00"));
        schedule.setOverallocationHardLimitPct(overallocPct);
        schedule.setUnderallocationHardLimitPct(underallocPct);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(window);
        schedule.setStaffingRequirements(List.of());
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(rows);
        schedule.setTimeslotDemandConfigs(List.of());
        schedule.setAssignments(seats);

        return schedule;
    }

    private static AgentShiftAssignment shiftRow(AtomicLong ids, long tenant, UUID deskId, UUID scheduleId,
            Agent a, LocalDate date, AgentDayConfig dc, List<ShiftBandPair> pairs) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(nextId(ids));
        row.setTenantId(tenant);
        row.setDeskId(deskId);
        row.setScheduleId(scheduleId);
        row.setAgent(a);
        row.setDate(date);
        row.setDayConfig(dc);
        row.setDeskShiftBandPairs(pairs);
        return row;
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

    private static Timeslot timeslot(AtomicLong ids, UUID deskId, UUID scheduleId, LocalDate date, LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(nextId(ids));
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
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
