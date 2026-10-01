package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import com.wfm.support.AssertsTodaysBehaviour;
import com.wfm.util.DayWindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOLV-04's constructed proof that a cross-midnight SLOT-mode stretch is mis-attributed by
 * today's calendar-date join, and correctly attributed to a single business day once plan 20-05's
 * migration re-points that join to {@link Timeslot#getBusinessDate()}.
 *
 * <p>This suite never calls the optimiser, and never calls any helper that does. A single
 * {@link Schedule} with every planning variable already pinned is scored via
 * {@code SolutionManager.explain} only -- pure evaluation, which is what makes an exact
 * per-constraint match count assertable at all (the same discipline
 * {@link MidnightBoundaryRegressionTest} and {@link ConstraintMatchCountNonVacuityTest} already
 * use).
 *
 * <h2>The geometry (every number below is load-bearing, per the plan)</h2>
 *
 * <p>A SLOT-mode desk anchored at {@code 21:00}, {@code 60}-minute increments, one agent, one
 * specialization, period covering business days D and D+1. Business day D holds four hourly
 * timeslots -- {@code 23:00-00:00} on calendar date D, then {@code 00:00-01:00},
 * {@code 01:00-02:00} and {@code 02:00-03:00} on calendar date D+1 -- and business day D+1 holds
 * the same four-slot shape one calendar day later. The single agent is pinned onto all eight
 * timeslots directly: nothing in {@link ScheduleConstraintProvider} forbids this, because the
 * timeslot is fixed per {@link AgentAssignment} and the agent is its only planning variable. The
 * agent is contracted {@code 4.00} hours per day, so {@link AgentDayConfig#expectedWorkSlots()} is
 * exactly {@code 4} for each of the two configured business days.
 *
 * <p>Today's calendar-date grouping splits the eight assignments across THREE calendar dates (one
 * on calendar D, four on calendar D+1, three on calendar D+2), but {@link AgentDayConfig} rows
 * exist for business days D and D+1 only -- so calendar D is charged a single slot against a
 * four-slot contract (under-allocated) and the three calendar-D+2 assignments join no
 * {@link AgentDayConfig} at all (the agent working an unconfigured day). After the business-date
 * migration the same eight assignments fall on exactly two business days of four slots each, each
 * matching its four-slot contract exactly.
 */
class SlotModeOvernightContractedHoursTest {

    private static final long TENANT = 1L;
    private static final int INCREMENT_MINUTES = 60;
    private static final LocalTime DESK_ANCHOR = LocalTime.of(21, 0);
    private static final LocalDate BUSINESS_DAY_D = LocalDate.of(2026, 1, 5);
    private static final LocalDate BUSINESS_DAY_D_PLUS_1 = BUSINESS_DAY_D.plusDays(1);

    // ------------------------------------------------------------------
    //  Shared scoring plumbing (mirrors MidnightBoundaryRegressionTest's own helpers)
    // ------------------------------------------------------------------

    private static SolutionManager<Schedule, HardSoftScore> newSolutionManager() {
        SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        return SolutionManager.create(factory);
    }

    /**
     * Asserts {@code constraintName} is present in {@code schedule}'s explanation before reading
     * its match count -- a count read against an absent constraint name would otherwise pass
     * vacuously, since a constraint that matched nothing looks identical to one never looked up.
     */
    private static ConstraintMatchTotal<HardSoftScore> requireConstraint(
            SolutionManager<Schedule, HardSoftScore> solutionManager, Schedule schedule, String constraintName) {
        List<ConstraintMatchTotal<HardSoftScore>> matches = solutionManager.explain(schedule)
                .getConstraintMatchTotalMap().values().stream()
                .filter(total -> constraintName.equals(total.getConstraintName()))
                .toList();
        assertThat(matches)
                .as("constraint '%s' must be present in the explanation before its match count is read",
                        constraintName)
                .hasSize(1);
        return matches.get(0);
    }

    // ------------------------------------------------------------------
    //  Forward assertions -- RED against today's calendar-date join, GREEN after plan 20-05
    // ------------------------------------------------------------------

    @Test
    @DisplayName("after the business-date migration, the cross-midnight stretch attributes to exactly one business day each")
    void businessDateJoinAttributesTheCrossMidnightStretchToASingleBusinessDayEach() {
        Schedule schedule = buildSchedule();
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

        assertThat(requireConstraint(solutionManager, schedule, "Contracted hours (under)")
                .getConstraintMatchCount())
                .as("each business day's four assignments must exactly match its four-slot "
                        + "contract once the join resolves business date, not calendar date")
                .isEqualTo(0);
        assertThat(requireConstraint(solutionManager, schedule, "Contracted hours (over)")
                .getConstraintMatchCount())
                .as("neither business day ever exceeds its four-slot contract")
                .isEqualTo(0);
        assertThat(requireConstraint(solutionManager, schedule, "Agent not working that day")
                .getConstraintMatchCount())
                .as("every assignment's business date must resolve to one of the two configured "
                        + "agent-days -- no assignment should register as an unconfigured day")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Shift work contiguity reads zero in both states -- structural inertness, not evidence")
    void shiftWorkContiguityIsStructurallyInertInSlotMode() {
        // shiftWorkContiguity is gated to SHIFT-mode schedules (ifExists ScheduleConfig,
        // cfg.schedulingMode() == SHIFT) and this fixture is SLOT mode with zero
        // AgentShiftAssignment rows -- every tuple is dropped at the gate regardless of the
        // migration state. A zero here proves nothing about SOLV-04; it is simply unreachable,
        // the same precedent MidnightBoundaryRegressionTest states at its own :180-183.
        Schedule schedule = buildSchedule();
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

        assertThat(requireConstraint(solutionManager, schedule, "Shift work contiguity")
                .getConstraintMatchCount())
                .as("SLOT mode with zero shift-assignment rows -- structurally inert, not evidence")
                .isEqualTo(0);
    }

    // ------------------------------------------------------------------
    //  Today's wrong behaviour -- pinned literals, flips when plan 20-05 lands
    // ------------------------------------------------------------------

    /**
     * Records TODAY's actual (wrong) behaviour: this codebase cannot yet represent a cross-midnight
     * SLOT-mode agent-day as a single business day, because {@link ScheduleConstraintProvider}'s
     * contracted-hours and not-working-that-day constraints still join on calendar date. Flips when
     * plan 20-05 re-points that join to {@link Timeslot#getBusinessDate()} -- see this class's
     * {@code businessDateJoinAttributesTheCrossMidnightStretchToASingleBusinessDayEach} above for
     * the correct behaviour, asserted red against this same fixture.
     */
    @Nested
    @DisplayName("today's wrong behaviour -- pinned literal counts, until plan 20-05 migrates the join")
    class TodaysBehaviour {

        @Test
        @AssertsTodaysBehaviour(flippedBy = "SOLV-04",
                to = "both match counts read zero once the contracted-hours and "
                        + "agent-not-working-that-day joins resolve business date, not calendar date")
        @DisplayName("today: the calendar-date join mis-attributes the cross-midnight stretch")
        void todaysCalendarDateJoinMisattributesTheCrossMidnightStretch() {
            Schedule schedule = buildSchedule();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Calendar D holds one assignment (23:00-00:00) and joins AgentDayConfig(date=D),
            // whose expectedWorkSlots() is 4 -- 1 < 4, one under-allocated groupBy tuple.
            int underCount = requireConstraint(solutionManager, schedule, "Contracted hours (under)")
                    .getConstraintMatchCount();
            assertThat(underCount)
                    .as("today's calendar-date join charges calendar D's single slot against "
                            + "business day D's four-slot contract")
                    .isEqualTo(1);

            // Calendar D+2 holds three assignments (00:00-01:00, 01:00-02:00, 02:00-03:00) with no
            // AgentDayConfig for calendar date D+2 at all -- ifNotExists fires once per assignment.
            int notWorkingCount = requireConstraint(solutionManager, schedule, "Agent not working that day")
                    .getConstraintMatchCount();
            assertThat(notWorkingCount)
                    .as("today's calendar-date join finds no AgentDayConfig for calendar date D+2, "
                            + "so each of its three seats registers as an unconfigured working day")
                    .isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------
    //  Evaluation performs no search
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("evaluation performs no search")
    class NoSearchDuringEvaluation {

        @Test
        @DisplayName("scoring a deliberately sub-optimal pinned schedule mutates no planning variable")
        void scoringMutatesNoPlanningVariable() {
            Schedule schedule = buildSchedule();
            List<AgentAssignment> assignments = schedule.getAssignments();
            AgentAssignment lastSeat = assignments.get(assignments.size() - 1);

            // Deliberately pin a sub-optimal state: unassign the final seat. A real search would
            // have something to improve here (minimum staffing wants an agent on every timeslot) --
            // pure evaluation must not touch it.
            lastSeat.setAgent(null);

            UUID firstSeatAgentIdBefore = assignments.get(0).getAgent().getId();

            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            solutionManager.update(schedule);

            assertThat(assignments.get(0).getAgent().getId())
                    .as("the untouched seat's pinned agent must be unchanged after scoring")
                    .isEqualTo(firstSeatAgentIdBefore);
            assertThat(assignments.get(assignments.size() - 1).getAgent())
                    .as("the deliberately unassigned seat must STAY unassigned after scoring -- a "
                            + "search would have reassigned it")
                    .isNull();
        }
    }

    // ------------------------------------------------------------------
    //  Fixture construction -- local helpers (NOT MidnightBoundaryFixture's private ones): this
    //  SLOT-mode SOLV-04 scenario belongs to a different requirement than BDAY-06's boundary
    //  scenarios, and joining MidnightBoundaryFixture.ALL_SCENARIOS would disturb its own
    //  class-load validator, which asserts properties specific to the boundary-scenario set.
    // ------------------------------------------------------------------

    /**
     * Builds the SLOT-mode, {@code 21:00}-anchored, cross-midnight fixture described in this
     * class's own javadoc -- one agent pinned directly to all eight timeslots across two business
     * days.
     */
    private static Schedule buildSchedule() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, spec);

        LocalDate businessDPlus2Calendar = BUSINESS_DAY_D_PLUS_1.plusDays(1);

        List<Timeslot> timeslots = new ArrayList<>();
        // Business day D: calendar D 23:00-00:00, then calendar D+1's first three hours.
        timeslots.add(timeslot(ids, deskId, scheduleId, BUSINESS_DAY_D, LocalTime.of(23, 0), LocalTime.MIDNIGHT));
        timeslots.add(timeslot(ids, deskId, scheduleId, BUSINESS_DAY_D_PLUS_1, LocalTime.MIDNIGHT, LocalTime.of(1, 0)));
        timeslots.add(timeslot(ids, deskId, scheduleId, BUSINESS_DAY_D_PLUS_1, LocalTime.of(1, 0), LocalTime.of(2, 0)));
        timeslots.add(timeslot(ids, deskId, scheduleId, BUSINESS_DAY_D_PLUS_1, LocalTime.of(2, 0), LocalTime.of(3, 0)));
        // Business day D+1: calendar D+1 23:00-00:00, then calendar D+2's first three hours.
        timeslots.add(timeslot(ids, deskId, scheduleId, BUSINESS_DAY_D_PLUS_1, LocalTime.of(23, 0), LocalTime.MIDNIGHT));
        timeslots.add(timeslot(ids, deskId, scheduleId, businessDPlus2Calendar, LocalTime.MIDNIGHT, LocalTime.of(1, 0)));
        timeslots.add(timeslot(ids, deskId, scheduleId, businessDPlus2Calendar, LocalTime.of(1, 0), LocalTime.of(2, 0)));
        timeslots.add(timeslot(ids, deskId, scheduleId, businessDPlus2Calendar, LocalTime.of(2, 0), LocalTime.of(3, 0)));

        BigDecimal contractedHours = new BigDecimal("4.00"); // expectedWorkSlots() == 4 at 60-min increments
        AgentDayConfig dayConfigD = dayConfig(agentEntity.getId(), BUSINESS_DAY_D, contractedHours);
        AgentDayConfig dayConfigDPlus1 = dayConfig(agentEntity.getId(), BUSINESS_DAY_D_PLUS_1, contractedHours);

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            AgentAssignment seat = seat(ids, deskId, scheduleId, ts, spec);
            seat.setAgent(agentEntity); // pin directly -- the one agent holds every one of the 8 seats
            assignments.add(seat);
        }
        List<TimeslotDemandConfig> demandConfigs = timeslots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, BUSINESS_DAY_D, BUSINESS_DAY_D_PLUS_1, DESK_ANCHOR);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfigD, dayConfigDPlus1));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        // Zero shift-assignment rows -- SLOT mode; Shift work contiguity is structurally inert.
        schedule.setShiftBandPairs(new ArrayList<>());
        schedule.setShiftAssignments(new ArrayList<>());
        return schedule;
    }

    // ------------------------------------------------------------------
    //  Factory helpers -- local copies of MidnightBoundaryFixture's shapes (every id deterministic)
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

    private static Agent agent(AtomicLong ids, UUID deskId, Specialization spec) {
        Agent a = new Agent();
        a.setId(nextId(ids));
        a.setTenantId(TENANT);
        a.setBamboohrId("A-1");
        a.setName("Agent-1");
        a.setActive(true);
        a.setDeskId(deskId);
        a.setPrimarySpecialization(spec);
        a.setSecondarySpecializations(new ArrayList<>());
        return a;
    }

    /**
     * Derives {@code businessDate} through {@link DayWindow#businessDateOf} rather than
     * hand-computing it, so this fixture and production can never disagree about what business
     * date a timeslot belongs to (SOLV-04's own mandate, matching plan 20-01's identical overload).
     */
    private static Timeslot timeslot(AtomicLong ids, UUID deskId, UUID scheduleId, LocalDate calendarDate,
            LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(nextId(ids));
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(calendarDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        ts.setBusinessDate(DayWindow.businessDateOf(DESK_ANCHOR, calendarDate, start));
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

    /**
     * Passes {@code DESK_ANCHOR} as {@link AgentDayConfig}'s eleventh argument, mirroring plan
     * 20-01's anchor-bearing overload -- every {@link AgentDayConfig} in a 21:00 scenario must
     * carry the real anchor, not the 10-argument delegating constructor's midnight default.
     */
    private static AgentDayConfig dayConfig(UUID agentId, LocalDate date, BigDecimal effectiveHours) {
        return new AgentDayConfig(agentId, date, effectiveHours, INCREMENT_MINUTES, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 500, 0, DESK_ANCHOR);
    }

    private static ConstraintWeights defaultWeights(AtomicLong ids, UUID deskId) {
        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(nextId(ids));
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);
        return weights;
    }

    /**
     * Calls {@code schedule.setDayStart(dayStart)} -- the fixture's own mandate that a 21:00
     * scenario is not a 21:00 scenario at all if it skips this (mirrors plan 20-01's identical
     * overload on {@code MidnightBoundaryFixture}).
     */
    private static Schedule baseSchedule(AtomicLong ids, UUID deskId, UUID scheduleId,
            LocalDate periodStart, LocalDate periodEnd, LocalTime dayStart) {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT_MINUTES);
        schedule.setStartTime(LocalTime.MIDNIGHT);
        schedule.setEndTime(LocalTime.MIDNIGHT);
        schedule.setDayStart(dayStart);
        schedule.setPeriodStartDate(periodStart);
        schedule.setPeriodEndDate(periodEnd);
        schedule.setBreakBlockedHours(new BigDecimal("1.00"));
        schedule.setBreakDurationMinutes(60);
        schedule.setBreakMinShiftHours(new BigDecimal("4.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        schedule.setOverallocationHardLimitPct(500);
        schedule.setUnderallocationHardLimitPct(0);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SLOT);
        schedule.setConstraintWeights(defaultWeights(ids, deskId));
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(new ArrayList<>());
        schedule.setAgentExceptions(List.of());
        schedule.setShiftBandPairs(new ArrayList<>());
        schedule.setShiftAssignments(new ArrayList<>());
        return schedule;
    }
}
