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

    // ------------------------------------------------------------------
    //  The deterministic pinning rule, implemented once
    // ------------------------------------------------------------------

    private static void pinPlanningVariables(Schedule schedule) {
        pinAgentAssignments(schedule);
        pinShiftAssignments(schedule);
    }

    private static void pinAgentAssignments(Schedule schedule) {
        List<AgentAssignment> sorted = new ArrayList<>(schedule.getAssignments());
        sorted.sort(Comparator
                .comparing((AgentAssignment a) -> a.getTimeslot().getDate())
                .thenComparing((AgentAssignment a) -> MIDNIGHT_WINDOW.anchoredStartMinute(a.getTimeslot().getStartTime()))
                .thenComparing(AgentAssignment::getId));

        Map<LocalDate, List<Agent>> eligibleByDate = new HashMap<>();
        int i = 0;
        for (AgentAssignment a : sorted) {
            LocalDate date = a.getTimeslot().getDate();
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
}
