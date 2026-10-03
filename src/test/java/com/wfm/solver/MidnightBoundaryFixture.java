package com.wfm.solver;

import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentDayOff;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.DayOffType;
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
import com.wfm.util.DayWindow;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Constructed midnight-boundary scenarios for BDAY-06, each a fully assembled {@link Schedule}
 * with every planning variable already pinned -- this suite never runs the optimiser, and never
 * calls any helper that does, anywhere. The optimiser is non-deterministic; the score function
 * {@code SolutionManager.update}/{@code .explain} evaluate against is not, and that is the entire
 * reason exact per-constraint match counts are assertable against these scenarios at all.
 *
 * <h2>The deterministic pinning rule</h2>
 *
 * <p>Every {@link AgentAssignment#getAgent()} and {@link AgentShiftAssignment#getShiftBandPair()}
 * in every scenario below is assigned by ONE rule, implemented once in this class and applied
 * uniformly -- never varied per scenario, never by calling the solver:
 *
 * <ul>
 *   <li>Every {@link AgentAssignment} is sorted by its timeslot's calendar date, then its
 *       timeslot's start minute-of-day, then its own id. The eligible agents for a given date --
 *       every agent carrying an {@link AgentDayConfig} for that date whose effective hours are
 *       positive, the same working-day gate the solver itself applies -- are sorted by id. The
 *       i-th assignment (i counted across the whole sorted list) is given the eligible agent at
 *       index {@code i mod eligibleCount} for its own date.
 *   <li>Every {@link AgentShiftAssignment} is sorted by date then agent id. The i-th row (i
 *       counted across the whole sorted list) is given the candidate at index
 *       {@code i mod candidateCount} from that row's own
 *       {@link AgentShiftAssignment#getEligibleShiftBandPairs()} value-range list.
 * </ul>
 *
 * <p>Each scenario builder's own comment argues why its pinned solution violates no hard
 * constraint it is not deliberately testing -- this eligibility filter is what makes the pinned
 * solution one the solver could actually reach, since it is the same working-day gate that keeps
 * an ineligible agent out of a real solve.
 *
 * <h2>The four reusable structural predicates</h2>
 *
 * <p>{@link #STRUCTURAL_PREDICATES} exists so this suite cannot go vacuous: a class-load
 * validator below builds every scenario, runs every predicate across the whole set, and fails the
 * build if a predicate never fires. Each predicate reads only a {@link Schedule}'s problem facts
 * through its public accessors -- never this fixture's own construction helpers -- so the same
 * implementation is pointable at a live desk's facts later, not only at scenarios built here.
 */
final class MidnightBoundaryFixture {

    private MidnightBoundaryFixture() {}

    static final long TENANT = 1L;
    static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 5); // a Monday
    static final int INCREMENT_MINUTES = 60;

    private static final String RESOURCE = "midnight-boundary-scenarios.md";
    private static final String PREDICATE_HEADING = "### Boundary predicates";

    /**
     * The nine midnight-implicit {@link DayWindow} statics this fixture called directly are
     * private as of BDAY-04 (plan 19-08, D-06); every call site below binds this midnight-anchored
     * instance instead, per D-14. No asserted value changes -- at a {@code 00:00} anchor every
     * {@code anchored*} instance method agrees exactly with its retired static counterpart.
     */
    private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);

    /**
     * A non-midnight day-start anchor (SOLV-03, D-11), bound once so the three scenario builders
     * below can run SOLV-03's break-band, contiguity and envelope assertions at an anchor the
     * solver does not yet honour -- the join migration (plan 20-05) and the interval-anchor
     * migration it carries are what make these go green.
     */
    private static final DayWindow NINE_PM_WINDOW = DayWindow.anchoredAt(LocalTime.of(21, 0));

    /** Every constructed scenario, built once. */
    static final List<Schedule> ALL_SCENARIOS = buildAllScenarios();

    /** Name to predicate -- the single list both the validator below and a future live-data
     *  caller consume, so the two can never drift apart by tracking separate lists. */
    static final Map<String, Predicate<Schedule>> STRUCTURAL_PREDICATES = buildStructuralPredicates();

    static {
        validateBoundaryCoverage();
    }

    // ------------------------------------------------------------------
    //  Scenario builders
    // ------------------------------------------------------------------

    private static List<Schedule> buildAllScenarios() {
        List<Schedule> scenarios = new ArrayList<>();
        scenarios.add(midnightCoverageScenario());
        scenarios.add(breakBandFlushToEnvelopeEndScenario());
        scenarios.add(ptoOnShiftStartingDateScenario());
        scenarios.add(ninePmCoverageScenario());
        scenarios.add(ninePmBreakBandFlushToEnvelopeEndScenario());
        scenarios.add(ninePmOvernightContiguityScenario());
        scenarios.add(ninePmShiftCrossingMidnight());
        return List.copyOf(scenarios);
    }

    /**
     * A desk whose operating window runs 22:00-00:00 -- two hourly timeslots, the second running
     * 23:00 to 00:00, the final slot of the day whose end is exactly the end-of-day position
     * (BDAY-06). Both slots carry real demand of one seat. Two agents, each contracted to exactly
     * one slot, so the deterministic pinning rule seats agent one on the 22:00 slot and agent two
     * on the 23:00 slot with zero unintended hard violation: each agent-day's one assigned slot
     * exactly matches its one contracted slot, both agents hold the seat's required
     * specialization, and no agent carries a day-off record.
     */
    static Schedule midnightCoverageScenario() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");

        Agent agent1 = agent(ids, deskId, "A-1", "Agent-1", spec);
        Agent agent2 = agent(ids, deskId, "A-2", "Agent-2", spec);
        List<Agent> agents = List.of(agent1, agent2);

        LocalDate date = BASE_DATE;
        Timeslot slot2200 = timeslot(ids, deskId, scheduleId, date, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot slot2300 = timeslot(ids, deskId, scheduleId, date, LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        List<Timeslot> timeslots = List.of(slot2200, slot2300);

        StaffingRequirement req1 = staffingRequirement(ids, deskId, scheduleId, slot2200, spec, 1);
        StaffingRequirement req2 = staffingRequirement(ids, deskId, scheduleId, slot2300, spec, 1);

        AgentAssignment seat1 = seat(ids, deskId, scheduleId, slot2200, spec);
        AgentAssignment seat2 = seat(ids, deskId, scheduleId, slot2300, spec);
        List<AgentAssignment> assignments = new ArrayList<>(List.of(seat1, seat2));

        BigDecimal contractedHours = new BigDecimal("1.00");
        List<AgentDayConfig> dayConfigs = List.of(
                dayConfig(agent1.getId(), date, contractedHours),
                dayConfig(agent2.getId(), date, contractedHours));

        List<TimeslotDemandConfig> demandConfigs = List.of(
                new TimeslotDemandConfig(slot2200, 1), new TimeslotDemandConfig(slot2300, 1));

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, date, date);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(List.of(req1, req2));
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);

        pinPlanningVariables(schedule);
        return schedule;
    }

    /**
     * A shift template whose envelope runs 15:00-00:00 (540 minutes, an end position at the end
     * of the day, BDAY-06) with a break band at offset 480 duration 60 -- the break runs
     * 23:00-00:00, finishing EXACTLY flush to the envelope's own end, the boundary this scenario
     * exists to pin. One agent, contracted to the envelope's net hours (8.00), demand covering
     * every one of the eight non-break hourly slots. The deterministic pinning rule seats the
     * agent on the sole candidate band-pair and fills every demanded seat with that same agent,
     * so every hard constraint this scenario is not deliberately testing reads zero by
     * construction: the assigned envelope covers every demanded slot, the band's offset is
     * hour-aligned, its capacity is unset (unlimited), and the agent's eight assigned seats match
     * its eight contracted slots exactly.
     */
    static Schedule breakBandFlushToEnvelopeEndScenario() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, "A-1", "Agent-1", spec);

        LocalDate date = BASE_DATE;
        LocalTime envelopeStart = LocalTime.of(15, 0);
        LocalTime envelopeEnd = LocalTime.MIDNIGHT;
        ShiftTemplate template = template(ids, deskId, "Late", envelopeStart, envelopeEnd);
        ShiftTemplateBreakBand band = band(ids, template, 480, 60);
        ShiftBandPair pair = new ShiftBandPair(template, band);
        List<ShiftBandPair> pairs = List.of(pair);

        // An int minute-of-day cursor, never a LocalTime one: DayWindow.plusWithinDay(23:00, 60)
        // returns 00:00, whose START minute is 0, so a LocalTime cursor would wrap around the
        // clock instead of terminating at the envelope's end-of-day boundary (BDAY-06).
        List<Timeslot> timeslots = new ArrayList<>();
        for (int m = MIDNIGHT_WINDOW.anchoredStartMinute(envelopeStart);
                m < MIDNIGHT_WINDOW.anchoredEndMinute(envelopeEnd); m += INCREMENT_MINUTES) {
            timeslots.add(timeslot(ids, deskId, scheduleId, date,
                    MIDNIGHT_WINDOW.anchoredToLocalTime(m), MIDNIGHT_WINDOW.anchoredToLocalTime(m + INCREMENT_MINUTES)));
        }

        BigDecimal contractedHours = template.getNetHours(60, MIDNIGHT_WINDOW);
        AgentDayConfig dayConfig = dayConfig(agentEntity.getId(), date, contractedHours);

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(nextId(ids));
        shiftRow.setTenantId(TENANT);
        shiftRow.setDeskId(deskId);
        shiftRow.setScheduleId(scheduleId);
        shiftRow.setAgent(agentEntity);
        shiftRow.setDate(date);
        shiftRow.setDayConfig(dayConfig);
        shiftRow.setDeskShiftBandPairs(pairs);
        // shiftBandPair pinned below by pinPlanningVariables.

        LocalTime breakSlotStart = LocalTime.of(23, 0);
        List<Timeslot> nonBreakSlots = timeslots.stream()
                .filter(ts -> !ts.getStartTime().equals(breakSlotStart))
                .toList();

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : nonBreakSlots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            assignments.add(seat(ids, deskId, scheduleId, ts, spec));
        }
        List<TimeslotDemandConfig> demandConfigs = nonBreakSlots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, date, date);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));

        pinPlanningVariables(schedule);
        return schedule;
    }

    /**
     * A shift whose times sit wholly inside one calendar date even though it runs right up to the
     * day anchor -- 21:00-00:00, three hourly slots, every one stamped with the SAME calendar
     * date. The day-off record is placed on that same starting date, so it matches every one of
     * the shift's assigned seats under today's calendar-date-only attribution -- the exact
     * property {@code MidnightBoundaryRegressionTest} marks as asserting today's behaviour,
     * flipped by OVNT-03. No other hard constraint is affected: the sole agent's three contracted
     * slots match its three assigned seats regardless of the day-off record, since this hand-built
     * fixture does not derive one fact from the other the way a real solve's pre-solve pass does.
     */
    static Schedule ptoOnShiftStartingDateScenario() {
        return ptoScenario(BASE_DATE);
    }

    /**
     * The same scenario as {@link #ptoOnShiftStartingDateScenario()} with the day-off record moved
     * to the following calendar date instead -- the second half of the pinned pair
     * {@code MidnightBoundaryRegressionTest} contrasts to show today's per-calendar-date
     * attribution never lets a day-off row on one date reach a seat stamped with the previous
     * date.
     */
    static Schedule ptoOnFollowingDateScenario() {
        return ptoScenario(BASE_DATE.plusDays(1));
    }

    private static Schedule ptoScenario(LocalDate dayOffDate) {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, "A-1", "Agent-1", spec);

        LocalDate date = BASE_DATE;
        LocalTime start = LocalTime.of(21, 0);
        LocalTime end = LocalTime.MIDNIGHT;
        // An int minute-of-day cursor, never a LocalTime one -- see the same note in
        // breakBandFlushToEnvelopeEndScenario (BDAY-06).
        List<Timeslot> timeslots = new ArrayList<>();
        for (int m = MIDNIGHT_WINDOW.anchoredStartMinute(start);
                m < MIDNIGHT_WINDOW.anchoredEndMinute(end); m += INCREMENT_MINUTES) {
            timeslots.add(timeslot(ids, deskId, scheduleId, date,
                    MIDNIGHT_WINDOW.anchoredToLocalTime(m), MIDNIGHT_WINDOW.anchoredToLocalTime(m + INCREMENT_MINUTES)));
        }

        BigDecimal contractedHours = new BigDecimal("3.00");
        AgentDayConfig dayConfig = dayConfig(agentEntity.getId(), date, contractedHours);

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            assignments.add(seat(ids, deskId, scheduleId, ts, spec));
        }
        List<TimeslotDemandConfig> demandConfigs = timeslots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        AgentDayOff dayOff = new AgentDayOff();
        dayOff.setId(nextId(ids));
        dayOff.setTenantId(TENANT);
        dayOff.setAgent(agentEntity);
        dayOff.setDate(dayOffDate);
        dayOff.setType(DayOffType.PTO);

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, date, date);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setAgentDaysOff(List.of(dayOff));

        pinPlanningVariables(schedule);
        return schedule;
    }

    /**
     * A desk whose operating window runs 19:00-21:00 at a 21:00 anchor -- two hourly timeslots,
     * the second running 20:00 to 21:00, the final slot of the business day whose end is exactly
     * the end-of-day position at THIS anchor (SOLV-03, mirroring {@link #midnightCoverageScenario}'s
     * geometry at the new anchor). Both slots carry real demand of one seat. Two agents, each
     * contracted to exactly one slot, so the deterministic pinning rule seats agent one on the
     * 19:00 slot and agent two on the 20:00 slot with zero unintended hard violation, for the
     * identical reason {@code midnightCoverageScenario}'s javadoc documents for its own geometry.
     *
     * <p>Also carries two extra, unstaffed timeslots -- 20:45-21:00 and 21:00-21:15 -- that exist
     * only to pin {@link com.wfm.util.DayWindow#businessDateOf}'s adjacency rule at the anchor
     * itself: equal-to-the-anchor in a START position belongs to the NEW business day, and
     * equal-to-the-anchor in an END position closes the OLD one. Carrying no demand and no seat,
     * they add nothing to any constraint's match count ({@code minimumStaffing} groups by {@link
     * AgentAssignment}, never by a bare {@link Timeslot}).
     */
    static Schedule ninePmCoverageScenario() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");

        Agent agent1 = agent(ids, deskId, "A-1", "Agent-1", spec);
        Agent agent2 = agent(ids, deskId, "A-2", "Agent-2", spec);
        List<Agent> agents = List.of(agent1, agent2);

        LocalTime dayStart = LocalTime.of(21, 0);
        LocalDate calendarDate = BASE_DATE;
        LocalDate businessDate = DayWindow.businessDateOf(dayStart, calendarDate, LocalTime.of(19, 0));

        Timeslot slot1900 = timeslot(ids, deskId, scheduleId, calendarDate, LocalTime.of(19, 0), LocalTime.of(20, 0), dayStart);
        Timeslot slot2000 = timeslot(ids, deskId, scheduleId, calendarDate, LocalTime.of(20, 0), LocalTime.of(21, 0), dayStart);
        // Adjacency-only timeslots (no demand, no seat) -- see javadoc above.
        Timeslot slotFlushToAnchorEnd = timeslot(ids, deskId, scheduleId, calendarDate,
                LocalTime.of(20, 45), LocalTime.of(21, 0), dayStart);
        Timeslot slotFlushToAnchorStart = timeslot(ids, deskId, scheduleId, calendarDate,
                LocalTime.of(21, 0), LocalTime.of(21, 15), dayStart);
        List<Timeslot> timeslots = List.of(slot1900, slot2000, slotFlushToAnchorEnd, slotFlushToAnchorStart);

        StaffingRequirement req1 = staffingRequirement(ids, deskId, scheduleId, slot1900, spec, 1);
        StaffingRequirement req2 = staffingRequirement(ids, deskId, scheduleId, slot2000, spec, 1);

        AgentAssignment seat1 = seat(ids, deskId, scheduleId, slot1900, spec);
        AgentAssignment seat2 = seat(ids, deskId, scheduleId, slot2000, spec);
        List<AgentAssignment> assignments = new ArrayList<>(List.of(seat1, seat2));

        BigDecimal contractedHours = new BigDecimal("1.00");
        List<AgentDayConfig> dayConfigs = List.of(
                dayConfig(agent1.getId(), businessDate, contractedHours, dayStart),
                dayConfig(agent2.getId(), businessDate, contractedHours, dayStart));

        List<TimeslotDemandConfig> demandConfigs = List.of(
                new TimeslotDemandConfig(slot1900, 1), new TimeslotDemandConfig(slot2000, 1));

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, businessDate, businessDate, dayStart);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(agents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(List.of(req1, req2));
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);

        pinPlanningVariables(schedule, NINE_PM_WINDOW);
        return schedule;
    }

    /**
     * A shift template whose envelope runs 12:00-21:00 at a 21:00 anchor (540 minutes, an end
     * position at the end of the business day, mirroring {@link #breakBandFlushToEnvelopeEndScenario}'s
     * geometry) with a break band at offset 480 duration 60 -- the break runs 20:00-21:00,
     * finishing EXACTLY flush to the envelope's own end, the boundary this scenario exists to pin
     * at the new anchor. One agent, contracted to the envelope's net hours (8.00), demand covering
     * every one of the eight non-break hourly slots -- the identical reasoning
     * {@code breakBandFlushToEnvelopeEndScenario}'s javadoc gives for its own geometry.
     */
    static Schedule ninePmBreakBandFlushToEnvelopeEndScenario() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, "A-1", "Agent-1", spec);

        LocalTime dayStart = LocalTime.of(21, 0);
        LocalDate calendarDate = BASE_DATE;
        LocalTime envelopeStart = LocalTime.of(12, 0);
        LocalTime envelopeEnd = LocalTime.of(21, 0);
        ShiftTemplate template = template(ids, deskId, "Late", envelopeStart, envelopeEnd);
        ShiftTemplateBreakBand band = band(ids, template, 480, 60);
        ShiftBandPair pair = new ShiftBandPair(template, band);
        List<ShiftBandPair> pairs = List.of(pair);

        LocalDate businessDate = DayWindow.businessDateOf(dayStart, calendarDate, envelopeStart);

        // An int minute-of-day cursor, never a LocalTime one -- see the identical note in
        // breakBandFlushToEnvelopeEndScenario (BDAY-06); here the cursor is day-start-relative
        // rather than midnight-relative.
        List<Timeslot> timeslots = new ArrayList<>();
        for (int m = NINE_PM_WINDOW.anchoredStartMinute(envelopeStart);
                m < NINE_PM_WINDOW.anchoredEndMinute(envelopeEnd); m += INCREMENT_MINUTES) {
            timeslots.add(timeslot(ids, deskId, scheduleId, calendarDate,
                    NINE_PM_WINDOW.anchoredToLocalTime(m), NINE_PM_WINDOW.anchoredToLocalTime(m + INCREMENT_MINUTES),
                    dayStart));
        }

        BigDecimal contractedHours = template.getNetHours(60, NINE_PM_WINDOW);
        AgentDayConfig dayConfig = dayConfig(agentEntity.getId(), businessDate, contractedHours, dayStart);

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(nextId(ids));
        shiftRow.setTenantId(TENANT);
        shiftRow.setDeskId(deskId);
        shiftRow.setScheduleId(scheduleId);
        shiftRow.setAgent(agentEntity);
        shiftRow.setDate(businessDate);
        shiftRow.setDayConfig(dayConfig);
        shiftRow.setDeskShiftBandPairs(pairs);
        // shiftBandPair pinned below by pinPlanningVariables.

        LocalTime breakSlotStart = LocalTime.of(20, 0);
        List<Timeslot> nonBreakSlots = timeslots.stream()
                .filter(ts -> !ts.getStartTime().equals(breakSlotStart))
                .toList();

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : nonBreakSlots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            assignments.add(seat(ids, deskId, scheduleId, ts, spec));
        }
        List<TimeslotDemandConfig> demandConfigs = nonBreakSlots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, businessDate, businessDate, dayStart);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));

        pinPlanningVariables(schedule, NINE_PM_WINDOW);
        return schedule;
    }

    /**
     * A 21:00-anchored SHIFT-mode agent-day whose six-slot envelope (02:00-08:00) sits entirely in
     * the EARLY-MORNING portion of its business day -- the calendar date AFTER the one the 21:00
     * anchor itself falls on, {@link #BASE_DATE}'s business day spilling into {@code
     * BASE_DATE.plusDays(1)}. Two one-hour gaps (04:00-05:00 and 06:00-07:00) sit strictly
     * INTERIOR to the four worked seats, with no break band to explain either (a {@code null}
     * band, {@code shiftWorkContiguity}'s "at most one interior gap is free" fallback), so a
     * correctly migrated solver reads exactly ONE hole (two gaps, one free) -- this scenario is
     * the falsifiable proof that {@code shiftWorkContiguity} must see this agent-day as ONE
     * business day's worth of seats, not zero: today's solver still joins {@code
     * AgentShiftAssignment}/{@code Timeslot} pairs on CALENDAR date (this agent-day's business
     * date is {@link #BASE_DATE}, but every one of its six timeslots carries calendar date {@code
     * BASE_DATE.plusDays(1)}), so NONE of the four worked seats ever joins this agent-day's row at
     * all -- the row drops out of the constraint's grouping entirely, reading zero holes instead
     * of one (SOLV-03's "silent non-join", not a wrong number but an ABSENT one).
     *
     * <p>Deliberately NOT a literal-midnight-crossing envelope (its clock times never touch
     * {@code 00:00}): {@code shiftWorkContiguity}'s own hole-scan sorts assigned start times
     * through a plain {@code TreeSet<LocalTime>} rather than an anchor-aware comparator, which
     * would reorder a midnight-crossing span's slots back into their LITERAL-clock order --
     * {@code 00:00} sorting before {@code 21:00} -- regardless of which {@link DayWindow} is
     * eventually passed in. That reordering defect is real but belongs to a genuinely overnight
     * SHIFT TEMPLATE (OVNT-01..07, Phase 21's territory per {@code 20-CONTEXT.md}'s "Not this
     * phase" list), not to SOLV-03's join migration -- this scenario isolates the join defect
     * alone, so its RED-today/GREEN-after-20-05 story stays true without depending on a fix this
     * phase does not make.
     */
    static Schedule ninePmOvernightContiguityScenario() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, "A-1", "Agent-1", spec);

        LocalTime dayStart = LocalTime.of(21, 0);
        LocalDate businessDate = BASE_DATE;
        LocalTime envelopeStart = LocalTime.of(2, 0);
        LocalTime envelopeEnd = LocalTime.of(8, 0);
        ShiftTemplate template = template(ids, deskId, "EarlyMorning", envelopeStart, envelopeEnd);
        ShiftBandPair pair = new ShiftBandPair(template, null); // zero bands = no break (P-02)
        List<ShiftBandPair> pairs = List.of(pair);

        List<Timeslot> timeslots = new ArrayList<>();
        for (int m = NINE_PM_WINDOW.anchoredStartMinute(envelopeStart);
                m < NINE_PM_WINDOW.anchoredEndMinute(envelopeEnd); m += INCREMENT_MINUTES) {
            LocalDate slotCalendarDate = DayWindow.calendarDateAtDayStartOffset(dayStart, businessDate, m);
            timeslots.add(timeslot(ids, deskId, scheduleId, slotCalendarDate,
                    NINE_PM_WINDOW.anchoredToLocalTime(m), NINE_PM_WINDOW.anchoredToLocalTime(m + INCREMENT_MINUTES),
                    dayStart));
        }

        BigDecimal contractedHours = template.getNetHours(0, NINE_PM_WINDOW);
        AgentDayConfig dayConfig = dayConfig(agentEntity.getId(), businessDate, contractedHours, dayStart);

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(nextId(ids));
        shiftRow.setTenantId(TENANT);
        shiftRow.setDeskId(deskId);
        shiftRow.setScheduleId(scheduleId);
        shiftRow.setAgent(agentEntity);
        shiftRow.setDate(businessDate);
        shiftRow.setDayConfig(dayConfig);
        shiftRow.setDeskShiftBandPairs(pairs);

        Set<LocalTime> gapStarts = Set.of(LocalTime.of(4, 0), LocalTime.of(6, 0));
        List<Timeslot> workedSlots = timeslots.stream()
                .filter(ts -> !gapStarts.contains(ts.getStartTime()))
                .toList();

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : workedSlots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            assignments.add(seat(ids, deskId, scheduleId, ts, spec));
        }
        List<TimeslotDemandConfig> demandConfigs = workedSlots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, businessDate, businessDate, dayStart);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));

        pinPlanningVariables(schedule, NINE_PM_WINDOW);
        return schedule;
    }

    /**
     * The genuinely midnight-crossing scenario Phase 18 deferred and this plan (21-06) builds
     * (OVNT-02, OVNT-03, OVNT-04): a 21:00-anchored SHIFT-mode agent-day whose envelope runs
     * 22:00-06:00 -- eight hourly slots whose clock times DO touch {@code 00:00}, unlike {@link
     * #ninePmOvernightContiguityScenario}'s deliberately clock-{@code 00:00}-avoiding geometry
     * (that scenario's own javadoc names this defect and hands it here).
     *
     * <p>The template carries one real break band (offset 120, duration 60 -- the break runs
     * 00:00-01:00, the third generated slot) and the worked seats carry exactly one further
     * interior gap at 03:00-04:00, strictly between the agent-day's first worked seat (22:00) and
     * its last (05:00) -- the two properties this plan's contiguity and break-location assertions
     * isolate. The break slot and the gap slot are the only two of the eight generated timeslots
     * with no demand and no seat.
     *
     * <p>The first two generated slots (22:00-23:00, 23:00-00:00) carry calendar date {@link
     * #BASE_DATE}; the remaining six (00:00-01:00 through 05:00-06:00) carry {@code
     * BASE_DATE.plusDays(1)} -- two distinct calendar dates, one business date ({@code
     * BASE_DATE}), the OVNT-04 property.
     *
     * <p>This is the scenario whose clock-ordered {@code TreeSet<LocalTime>} first and last
     * invert: the worked set's clock-earliest start is 01:00 (anchored minute 240) and its
     * clock-latest is 23:00 (anchored minute 120), so a scan range derived from the CLOCK-ordered
     * ends collapses to {@code [240, 120)} -- empty -- regardless of which of the three call
     * sites (the contiguity gap scan, the break-start scan, the break-aware contiguity path)
     * derives it. Before this plan's fix, the contiguity constraint therefore read zero holes
     * for a day that has one, and the break-start scan located no break at all (P-02).
     */
    static Schedule ninePmShiftCrossingMidnight() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);
        Specialization spec = specialization(ids, deskId, "Support");
        Agent agentEntity = agent(ids, deskId, "A-1", "Agent-1", spec);

        LocalTime dayStart = LocalTime.of(21, 0);
        LocalDate businessDate = BASE_DATE;
        LocalTime envelopeStart = LocalTime.of(22, 0);
        LocalTime envelopeEnd = LocalTime.of(6, 0);
        ShiftTemplate template = template(ids, deskId, "CrossingMidnight", envelopeStart, envelopeEnd);
        ShiftTemplateBreakBand band = band(ids, template, 120, 60); // break 00:00-01:00 (OVNT-02)
        ShiftBandPair pair = new ShiftBandPair(template, band);
        List<ShiftBandPair> pairs = List.of(pair);

        List<Timeslot> timeslots = new ArrayList<>();
        for (int m = NINE_PM_WINDOW.anchoredStartMinute(envelopeStart);
                m < NINE_PM_WINDOW.anchoredEndMinute(envelopeEnd); m += INCREMENT_MINUTES) {
            LocalDate slotCalendarDate = DayWindow.calendarDateAtDayStartOffset(dayStart, businessDate, m);
            timeslots.add(timeslot(ids, deskId, scheduleId, slotCalendarDate,
                    NINE_PM_WINDOW.anchoredToLocalTime(m), NINE_PM_WINDOW.anchoredToLocalTime(m + INCREMENT_MINUTES),
                    dayStart));
        }

        BigDecimal contractedHours = template.getNetHours(60, NINE_PM_WINDOW);
        AgentDayConfig dayConfig = dayConfig(agentEntity.getId(), businessDate, contractedHours, dayStart);

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(nextId(ids));
        shiftRow.setTenantId(TENANT);
        shiftRow.setDeskId(deskId);
        shiftRow.setScheduleId(scheduleId);
        shiftRow.setAgent(agentEntity);
        shiftRow.setDate(businessDate);
        shiftRow.setDayConfig(dayConfig);
        shiftRow.setDeskShiftBandPairs(pairs);

        // The break slot (00:00-01:00, explained by the assigned band) and the interior gap
        // (03:00-04:00, explained by nothing -- the hole this plan's Task 1/2 exist to prove) are
        // both excluded from the worked/demanded set.
        Set<LocalTime> excludedStarts = Set.of(LocalTime.MIDNIGHT, LocalTime.of(3, 0));
        List<Timeslot> workedSlots = timeslots.stream()
                .filter(ts -> !excludedStarts.contains(ts.getStartTime()))
                .toList();

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : workedSlots) {
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, 1));
            assignments.add(seat(ids, deskId, scheduleId, ts, spec));
        }
        List<TimeslotDemandConfig> demandConfigs = workedSlots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, 1))
                .toList();

        Schedule schedule = baseSchedule(ids, deskId, scheduleId, businessDate, businessDate, dayStart);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(List.of(agentEntity));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setShiftBandPairs(pairs);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));

        pinPlanningVariables(schedule, NINE_PM_WINDOW);
        return schedule;
    }

    // ------------------------------------------------------------------
    //  The deterministic pinning rule, implemented once
    // ------------------------------------------------------------------

    private static void pinPlanningVariables(Schedule schedule) {
        pinPlanningVariables(schedule, MIDNIGHT_WINDOW);
    }

    /**
     * SOLV-03 (D-11): the window parameter exists so the 21:00-anchored scenarios above can
     * compute the deterministic pinning rule's start-minute key relative to their OWN anchor,
     * never the midnight-implicit one -- the rule itself (date, then day-start-relative start
     * minute, then id) stays substantively identical, never a second rule.
     */
    private static void pinPlanningVariables(Schedule schedule, DayWindow window) {
        pinAgentAssignments(schedule, window);
        pinShiftAssignments(schedule);
    }

    private static void pinAgentAssignments(Schedule schedule, DayWindow window) {
        List<AgentAssignment> sorted = new ArrayList<>(schedule.getAssignments());
        sorted.sort(Comparator
                .comparing((AgentAssignment a) -> a.getTimeslot().getBusinessDate())
                .thenComparing((AgentAssignment a) -> window.anchoredStartMinute(a.getTimeslot().getStartTime()))
                .thenComparing(AgentAssignment::getId));

        Map<LocalDate, List<Agent>> eligibleByDate = new HashMap<>();
        int i = 0;
        for (AgentAssignment a : sorted) {
            LocalDate date = a.getTimeslot().getBusinessDate();
            List<Agent> eligible = eligibleByDate.computeIfAbsent(date, d -> eligibleAgents(schedule, d));
            if (!eligible.isEmpty()) {
                a.setAgent(eligible.get(i % eligible.size()));
            }
            i++;
        }
    }

    private static List<Agent> eligibleAgents(Schedule schedule, LocalDate date) {
        Set<UUID> workingAgentIds = schedule.getAgentDayConfigs().stream()
                .filter(c -> c.date().equals(date))
                .filter(c -> c.effectiveHours() != null && c.effectiveHours().signum() > 0)
                .map(AgentDayConfig::agentId)
                .collect(Collectors.toSet());
        return schedule.getAgents().stream()
                .filter(a -> workingAgentIds.contains(a.getId()))
                .sorted(Comparator.comparing(Agent::getId))
                .toList();
    }

    private static void pinShiftAssignments(Schedule schedule) {
        List<AgentShiftAssignment> sorted = new ArrayList<>(schedule.getShiftAssignments());
        sorted.sort(Comparator
                .comparing(AgentShiftAssignment::getDate)
                .thenComparing((AgentShiftAssignment sa) -> sa.getAgent().getId()));

        int i = 0;
        for (AgentShiftAssignment sa : sorted) {
            List<ShiftBandPair> candidates = sa.getEligibleShiftBandPairs();
            if (!candidates.isEmpty()) {
                sa.setShiftBandPair(candidates.get(i % candidates.size()));
            }
            i++;
        }
    }

    // ------------------------------------------------------------------
    //  The four reusable structural predicates
    // ------------------------------------------------------------------

    /** True when any {@link Timeslot} ends exactly at the end of its calendar day. */
    static boolean containsTimeslotEndingAtDayEnd(Schedule schedule) {
        return schedule.getTimeslots().stream()
                .anyMatch(ts -> ts.getEndTime().equals(LocalTime.MIDNIGHT));
    }

    /** True when any {@link Timeslot} runs the final hour before the end of its calendar day. */
    static boolean containsFinalHourTimeslot(Schedule schedule) {
        return schedule.getTimeslots().stream()
                .anyMatch(ts -> ts.getStartTime().equals(LocalTime.of(23, 0))
                        && ts.getEndTime().equals(LocalTime.MIDNIGHT));
    }

    /** True when any {@link ShiftBandPair}'s break finishes exactly flush to its own envelope's end. */
    static boolean containsBandFlushToEnvelopeEdge(Schedule schedule) {
        return schedule.getShiftBandPairs().stream()
                .filter(pair -> pair.band() != null)
                .anyMatch(pair -> {
                    LocalTime breakStart = MIDNIGHT_WINDOW.anchoredPlusWithinDay(
                            pair.template().getStartTime(), pair.band().getOffsetMinutes());
                    LocalTime breakEnd = MIDNIGHT_WINDOW.anchoredPlusWithinDay(breakStart, pair.band().getDurationMinutes());
                    return MIDNIGHT_WINDOW.anchoredEndMinute(breakEnd)
                            == MIDNIGHT_WINDOW.anchoredEndMinute(pair.template().getEndTime());
                });
    }

    /**
     * True when any {@link ShiftBandPair}'s template stores an end time that is, read as a plain
     * chronological value, no later than its own start time -- the shape a day-anchor-crossing
     * envelope takes in a model whose end position stores {@code 00:00} for "the end of the day".
     */
    static boolean containsEnvelopeCrossingDayAnchor(Schedule schedule) {
        return schedule.getShiftBandPairs().stream()
                .map(ShiftBandPair::template)
                .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
                .anyMatch(t -> t.getEndTime().compareTo(t.getStartTime()) <= 0);
    }

    private static Map<String, Predicate<Schedule>> buildStructuralPredicates() {
        Map<String, Predicate<Schedule>> predicates = new LinkedHashMap<>();
        predicates.put("timeslot ending at day end", MidnightBoundaryFixture::containsTimeslotEndingAtDayEnd);
        predicates.put("final hour timeslot before day end", MidnightBoundaryFixture::containsFinalHourTimeslot);
        predicates.put("band flush to envelope edge", MidnightBoundaryFixture::containsBandFlushToEnvelopeEdge);
        predicates.put("envelope crossing the day anchor", MidnightBoundaryFixture::containsEnvelopeCrossingDayAnchor);
        return Collections.unmodifiableMap(predicates);
    }

    // ------------------------------------------------------------------
    //  Class-load non-vacuity validator
    // ------------------------------------------------------------------

    private static void validateBoundaryCoverage() {
        List<Schedule> scenarios = ALL_SCENARIOS;
        if (scenarios.isEmpty()) {
            throw new IllegalStateException(
                    "MidnightBoundaryFixture has zero constructed scenarios -- an empty scenario "
                            + "list would satisfy every predicate check vacuously (BDAY-06)");
        }

        Set<String> neverFired = new LinkedHashSet<>(STRUCTURAL_PREDICATES.keySet());
        for (Schedule scenario : scenarios) {
            for (Map.Entry<String, Predicate<Schedule>> entry : STRUCTURAL_PREDICATES.entrySet()) {
                if (entry.getValue().test(scenario)) {
                    neverFired.remove(entry.getKey());
                }
            }
        }
        if (!neverFired.isEmpty()) {
            throw new IllegalStateException(
                    "The following structural predicate(s) never fired across " + scenarios.size()
                            + " constructed scenario(s), so the boundary they exist to cover is not "
                            + "actually present in this fixture (BDAY-06): " + neverFired);
        }

        Set<String> resourcePredicates;
        try {
            resourcePredicates = parseFencedBlock(readResource(), PREDICATE_HEADING);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read " + RESOURCE + " to validate the boundary predicate list", e);
        }
        Set<String> codeOnly = new LinkedHashSet<>(STRUCTURAL_PREDICATES.keySet());
        codeOnly.removeAll(resourcePredicates);
        Set<String> resourceOnly = new LinkedHashSet<>(resourcePredicates);
        resourceOnly.removeAll(STRUCTURAL_PREDICATES.keySet());
        if (!codeOnly.isEmpty() || !resourceOnly.isEmpty()) {
            throw new IllegalStateException(
                    "MidnightBoundaryFixture.STRUCTURAL_PREDICATES and " + RESOURCE + "'s '"
                            + PREDICATE_HEADING + "' section must be set-equal in both directions "
                            + "(BDAY-06). In code but missing from the resource: " + codeOnly
                            + ". In the resource but no longer in code: " + resourceOnly + ".");
        }
    }

    private static String readResource() throws IOException {
        try (InputStream in = MidnightBoundaryFixture.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads the first fenced code block following {@code heading}, one entry per non-blank line. */
    private static Set<String> parseFencedBlock(String markdown, String heading) {
        List<String> lines = markdown.lines().toList();
        int headingIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        if (headingIndex < 0) {
            throw new IllegalStateException("Heading '" + heading + "' not found in " + RESOURCE);
        }
        List<String> collected = new ArrayList<>();
        boolean inFence = false;
        for (int i = headingIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().startsWith("```")) {
                if (inFence) {
                    break;
                }
                inFence = true;
                continue;
            }
            if (inFence && !line.isBlank()) {
                collected.add(line.strip());
            }
        }
        if (!inFence) {
            throw new IllegalStateException("No fenced block found after heading '" + heading + "' in " + RESOURCE);
        }
        return new LinkedHashSet<>(collected);
    }

    // ------------------------------------------------------------------
    //  Factory helpers -- every id is deterministic (see class javadoc)
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

    private static Agent agent(AtomicLong ids, UUID deskId, String bambooId, String name, Specialization spec) {
        Agent a = new Agent();
        a.setId(nextId(ids));
        a.setTenantId(TENANT);
        a.setBamboohrId(bambooId);
        a.setName(name);
        a.setActive(true);
        a.setDeskId(deskId);
        a.setPrimarySpecialization(spec);
        a.setSecondarySpecializations(new ArrayList<>());
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
        ts.setBusinessDate(date);
        return ts;
    }

    /**
     * Anchor-bearing overload (SOLV-03, D-11): derives {@code businessDate} through {@link
     * DayWindow#businessDateOf} rather than hand-computing it, so the fixture and production can
     * never disagree about what business date a timeslot belongs to. At a {@code 00:00} anchor
     * this agrees exactly with the 6-argument overload above, which keeps {@code
     * setBusinessDate(date)} unchanged.
     */
    private static Timeslot timeslot(AtomicLong ids, UUID deskId, UUID scheduleId, LocalDate calendarDate,
            LocalTime start, LocalTime end, LocalTime dayStart) {
        Timeslot ts = new Timeslot();
        ts.setId(nextId(ids));
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(calendarDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        ts.setBusinessDate(DayWindow.businessDateOf(dayStart, calendarDate, start));
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

    private static AgentDayConfig dayConfig(UUID agentId, LocalDate date, BigDecimal effectiveHours) {
        return new AgentDayConfig(agentId, date, effectiveHours, INCREMENT_MINUTES, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 500, 0);
    }

    /**
     * Anchor-bearing overload (SOLV-03, D-11): passes {@code dayStart} as {@link AgentDayConfig}'s
     * eleventh argument (plan 20-01). Every {@link AgentDayConfig} belonging to a 21:00 scenario
     * must carry this value -- falling through the 10-argument delegating constructor to midnight
     * would make plan 20-05's migrated Quad-arity constraints read midnight instead.
     */
    private static AgentDayConfig dayConfig(UUID agentId, LocalDate date, BigDecimal effectiveHours, LocalTime dayStart) {
        return new AgentDayConfig(agentId, date, effectiveHours, INCREMENT_MINUTES, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 500, 0, dayStart);
    }

    private static ConstraintWeights defaultWeights(AtomicLong ids, UUID deskId) {
        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(nextId(ids));
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);
        return weights;
    }

    private static Schedule baseSchedule(AtomicLong ids, UUID deskId, UUID scheduleId,
            LocalDate periodStart, LocalDate periodEnd) {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT_MINUTES);
        schedule.setStartTime(LocalTime.MIDNIGHT);
        schedule.setEndTime(LocalTime.MIDNIGHT);
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

    /**
     * Day-start-bearing overload (SOLV-03, D-11): calls {@code schedule.setDayStart(dayStart)}.
     * The 4-argument overload above never sets it, so {@code Schedule.getDayStart()} stays null
     * and {@code ScheduleConfig.dayStart()} arrives null -- a 21:00 scenario that skips this
     * overload is not a 21:00 scenario at all.
     */
    private static Schedule baseSchedule(AtomicLong ids, UUID deskId, UUID scheduleId,
            LocalDate periodStart, LocalDate periodEnd, LocalTime dayStart) {
        Schedule schedule = baseSchedule(ids, deskId, scheduleId, periodStart, periodEnd);
        schedule.setDayStart(dayStart);
        return schedule;
    }
}
