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
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDAY-07's drift guard (D-14): a 48-agent, 44.6-FTE, {@code 00:00}-anchored fixture CONSTRUCTED
 * to the Phil-US desk's shape -- never captured from its bytes, never reading a live data source.
 * The only two facts this fixture takes from the live desk are numbers (48 agents, 44.6 total
 * contracted FTE); everything else -- template names, envelope times, break offsets, demand
 * distribution -- is this fixture author's own choice, made solely to keep the fixture internally
 * consistent (plan 20-03's own instruction).
 *
 * <p><strong>Why {@code 00:00} (D-14).</strong> The live Phil-US desk has never set a non-midnight
 * day start -- it carries its real shift times with a fixed +3h offset applied precisely because
 * it could not -- so a {@code 00:00} anchor is the faithful shape. It is also what makes this a
 * DRIFT guard rather than a second copy of plan 20-01's work: at {@code 00:00} business date
 * equals calendar date, so a correct re-anchoring migration must leave every count and the score
 * untouched. This guard is expected GREEN before AND after plan 20-05's migration.
 *
 * <p><strong>Why this is not v1.4's mistake (D-14, {@code REQUIREMENTS.md} "Relationship to the
 * cancelled v1.4").</strong> v1.4's BDAY-06 fixture was wrong in two directions at once because it
 * was CAPTURED from four live desks' actual bytes: over-sensitive (any unrelated scoring change
 * anywhere shifts the golden bytes, with no diagnostic signal) and under-powered on midnight (the
 * captured desks barely contained it). This fixture avoids both: it is CONSTRUCTED to a shape, not
 * captured from bytes, and it asserts PER-CONSTRAINT MATCH COUNTS -- which name which constraint
 * moved -- never a golden-byte or whole-solution comparison. It must never be reduced to one.
 *
 * <p><strong>Construction, not capture (no real tenant data anywhere in this file).</strong> Every
 * agent is named {@code Agent-N} (never a real name), no external-HR identifier field is
 * populated at all, every id is a {@code UUID} from a seeded {@link AtomicLong} counter, and no
 * desk, specialization or template identifier from any live desk appears here -- following
 * {@link LiveShapeShiftDeskFixture}'s own naming discipline in spirit (that class's javadoc has
 * the full argument). The fixture author opens no live data source while writing this file.
 *
 * <p><strong>Composition (the two numbers this fixture takes from the live desk).</strong> 48
 * agents: 44 full-time ({@link #FULL_TIME_HOURS}/day, FTE 1.0 each) and 4 part-time
 * ({@link #PART_TIME_HOURS}/day, FTE 0.15 each) -- {@code 44 x 1.0 + 4 x 0.15 = 44.6}, the total
 * contracted FTE. {@link #validateComposition()} asserts both numbers (and the shift-library
 * properties below) at class load, so a later edit that quietly weakens either fails loudly.
 *
 * <p><strong>The shift library.</strong> Five templates: four main templates ({@link
 * #TEMPLATE_SPECS} indices 0-3) each a 9-hour envelope with a 1-hour break, net exactly {@link
 * #FULL_TIME_HOURS} -- matching every full-time agent's contract exactly, following {@link
 * ShiftModeFixtures}' own "every template's net duration equals the contract" convention -- and
 * one SLACK template ({@code "Flex"}, index 4) whose net duration (9h) exceeds the full-time
 * contract (8h) by one hour, following {@link LiveShapeShiftDeskFixture}'s {@code "Weekend Flex"}
 * precedent: a structural property this fixture's {@code validateComposition()} asserts directly,
 * independent of whether the solver's zero-slack {@code envelopeSlackSlots} default (unset here,
 * matching every other construction-only fixture in this package) ever makes it reachable by any
 * one agent-day -- {@code LiveShapeShiftDeskFixture}'s own "Weekend Flex" is in the identical
 * position. Every band sits at least {@link #BAND_EDGE_MARGIN_MINUTES} clear of both its own
 * envelope's edges, the same margin {@code LiveShapeShiftDeskFixture} enforces, for the same
 * reason: with slack in play, a band placed too close to an edge could legitimately leave no
 * worked slot on one side.
 *
 * <p><strong>Construction discipline -- fully pinned, cohort-derived, never solved.</strong> Every
 * full-time agent is assigned exactly one of the four main templates by a simple round-robin rule
 * ({@code agent index mod 4}), giving each template an equal 11-agent cohort. Demand at every
 * hourly timeslot is then DERIVED from cohort membership -- the count of agents whose chosen
 * template covers that hour outside its own break window -- and every one of that count's seats is
 * pre-assigned directly to exactly those agents. This is a single, uniform, deterministic pinning
 * rule in the spirit of {@link MidnightBoundaryFixture}'s own rule (one rule, applied uniformly,
 * never varied ad hoc), chosen for this fixture's own cohort-shaped demand rather than reusing that
 * class's inaccessible private implementation. The four part-time agents match no template's net
 * hours (1.2h against every template's 8h/9h) and so hold no eligible candidate; their {@code
 * AgentShiftAssignment} rows are left with a {@code null} shiftBandPair ({@code allowsUnassigned}),
 * following the same fixture-wide pinning discipline -- {@code MidnightBoundaryFixture}'s own rule
 * leaves a planning variable null exactly when its candidate list is empty. They hold no seats
 * either, so they do not trip {@code shiftEnvelopeCompliance} (which can only fire where an {@code
 * AgentAssignment} actually joins their shift row); they are expected to trip only "Contracted
 * hours (under, zero)" (SOLV-04's own named constraint), four times, one per part-time agent.
 *
 * <p>Never calls the optimiser: {@link #newSolutionManager()} builds a {@link SolverFactory} and
 * {@link SolutionManager} exactly as {@code ShiftDeskEndToEndRegressionTest}'s scoring-only helper
 * does (never its separate {@code solve(...)} helper, which runs the real optimiser), and only
 * {@code SolutionManager.update}/{@code .explain} are called anywhere in this class --
 * {@link NoSearchDuringEvaluation} proves no planning variable moves across a read.
 *
 * <p><strong>The literal baseline (D-12's discipline, applied to this fixture).</strong> {@link
 * #EXPECTED_MATCH_COUNTS} and {@link #EXPECTED_SCORE} are literals captured by running this exact
 * construction against the tree as it stood on 2026-10-01, before plan 20-05's join-and-anchor
 * migration. They are the pre-migration record. The same literals are asserted again after the
 * migration lands -- a red run after the migration means it moved something on a realistic
 * 48-agent {@code 00:00}-anchored desk, which it must not, and the failing assertion names which
 * constraint moved. These literals must NEVER be regenerated from post-migration output to make an
 * assertion pass; doing so would destroy the only drift evidence this milestone ships (D-14).
 */
class PhilUsShapedDriftGuardTest {

    private static final long TENANT = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 5); // a Monday
    private static final int INCREMENT_MINUTES = 60;
    private static final int BREAK_DURATION_MINUTES = 60;
    private static final int BAND_EDGE_MARGIN_MINUTES = 120;

    private static final int FULL_TIME_COUNT = 44;
    private static final int PART_TIME_COUNT = 4;
    private static final int AGENT_COUNT = FULL_TIME_COUNT + PART_TIME_COUNT;
    private static final BigDecimal FULL_TIME_HOURS = new BigDecimal("8.00");
    private static final BigDecimal PART_TIME_HOURS = new BigDecimal("1.20");
    private static final BigDecimal FULL_TIME_FTE = new BigDecimal("1.00");
    private static final BigDecimal PART_TIME_FTE = new BigDecimal("0.15");
    private static final BigDecimal EXPECTED_TOTAL_FTE = new BigDecimal("44.60");

    /** One main template's shape: envelope start/end and its break band's offset from the start. */
    private record TemplateSpec(String name, LocalTime start, LocalTime end, int bandOffsetMinutes) {}

    private static final List<TemplateSpec> MAIN_TEMPLATE_SPECS = List.of(
            new TemplateSpec("Morning", LocalTime.of(8, 0), LocalTime.of(17, 0), 240),
            new TemplateSpec("Day", LocalTime.of(9, 0), LocalTime.of(18, 0), 240),
            new TemplateSpec("Afternoon", LocalTime.of(10, 0), LocalTime.of(19, 0), 240),
            new TemplateSpec("Evening", LocalTime.of(11, 0), LocalTime.of(20, 0), 240));

    /** The slack template (LiveShapeShiftDeskFixture's "Weekend Flex" precedent): net 9h vs the
     *  8h full-time contract -- one hour of slack, present as a composition fact, never chosen by
     *  any agent in this fixture's own cohort-derived pinning. */
    private static final TemplateSpec SLACK_TEMPLATE_SPEC =
            new TemplateSpec("Flex", LocalTime.of(7, 0), LocalTime.of(17, 0), 240);

    static {
        validateComposition();
    }

    /**
     * Fails at class load, loudly, if a future edit would quietly weaken the composition
     * properties this fixture exists to carry (acceptance criteria: "a class-load validator
     * asserts the composition properties ... so a later edit that destroys one of them fails
     * loudly rather than silently weakening the guard").
     */
    private static void validateComposition() {
        if (AGENT_COUNT != 48) {
            throw new IllegalStateException(
                    "PhilUsShapedDriftGuardTest's agent count is " + AGENT_COUNT
                            + ", not 48 -- the fixture's own composition fact (BDAY-07, D-14)");
        }
        BigDecimal totalFte = FULL_TIME_FTE.multiply(BigDecimal.valueOf(FULL_TIME_COUNT))
                .add(PART_TIME_FTE.multiply(BigDecimal.valueOf(PART_TIME_COUNT)));
        if (totalFte.compareTo(EXPECTED_TOTAL_FTE) != 0) {
            throw new IllegalStateException(
                    "PhilUsShapedDriftGuardTest's total contracted FTE is " + totalFte + ", not "
                            + EXPECTED_TOTAL_FTE + " -- the fixture's own composition fact (BDAY-07, D-14)");
        }

        boolean hasSlackTemplate = false;
        int fullTimeMinutes = FULL_TIME_HOURS.multiply(BigDecimal.valueOf(60)).intValueExact();
        for (TemplateSpec ts : allTemplateSpecs()) {
            int envelopeMinutes = (int) Duration.between(ts.start(), ts.end()).toMinutes();
            int netMinutes = envelopeMinutes - BREAK_DURATION_MINUTES;
            if (netMinutes > fullTimeMinutes) {
                hasSlackTemplate = true;
            }
            int offset = ts.bandOffsetMinutes();
            if (offset < BAND_EDGE_MARGIN_MINUTES) {
                throw new IllegalStateException("Template '" + ts.name() + "' has a band offset of "
                        + offset + " minutes -- less than the required " + BAND_EDGE_MARGIN_MINUTES
                        + "-minute margin from the envelope start (band-offset margin property)");
            }
            if (offset + BREAK_DURATION_MINUTES > envelopeMinutes - BAND_EDGE_MARGIN_MINUTES) {
                throw new IllegalStateException("Template '" + ts.name() + "' has a band offset of "
                        + offset + " minutes whose break end sits within " + BAND_EDGE_MARGIN_MINUTES
                        + " minutes of the envelope end (band-offset margin property)");
            }
        }
        if (!hasSlackTemplate) {
            throw new IllegalStateException(
                    "No template in this fixture's library carries slack -- at least one template's "
                            + "envelope minutes minus its band duration must strictly exceed the "
                            + "full-time contracted minutes, or the slack-template composition property "
                            + "is vacuous");
        }
    }

    private static List<TemplateSpec> allTemplateSpecs() {
        List<TemplateSpec> all = new ArrayList<>(MAIN_TEMPLATE_SPECS);
        all.add(SLACK_TEMPLATE_SPEC);
        return all;
    }

    // ------------------------------------------------------------------
    //  Fixture construction
    // ------------------------------------------------------------------

    /** Non-break hourly slot starts inside {@code spec}'s envelope. */
    private static List<LocalTime> nonBreakHours(TemplateSpec spec) {
        LocalTime breakStart = spec.start().plusMinutes(spec.bandOffsetMinutes());
        LocalTime breakEnd = breakStart.plusMinutes(BREAK_DURATION_MINUTES);
        List<LocalTime> hours = new ArrayList<>();
        for (LocalTime t = spec.start(); t.isBefore(spec.end()); t = t.plusMinutes(INCREMENT_MINUTES)) {
            LocalTime slotEnd = t.plusMinutes(INCREMENT_MINUTES);
            boolean onBreak = t.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
            if (!onBreak) {
                hours.add(t);
            }
        }
        return hours;
    }

    private static Schedule buildSchedule() {
        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        Specialization spec = specialization(ids, deskId, "Support");

        // Five shift templates (four main + the slack template), one ShiftBandPair each, all
        // sharing ONE List<ShiftBandPair> instance -- mirrors SolverService.buildShiftAssignments'
        // production precedent (shared list instance across every AgentShiftAssignment row).
        List<ShiftTemplate> mainTemplates = new ArrayList<>();
        List<ShiftBandPair> pairs = new ArrayList<>();
        for (TemplateSpec ts : MAIN_TEMPLATE_SPECS) {
            ShiftTemplate template = template(ids, deskId, ts.name(), ts.start(), ts.end());
            mainTemplates.add(template);
            ShiftTemplateBreakBand band = band(ids, template, ts.bandOffsetMinutes(), BREAK_DURATION_MINUTES);
            pairs.add(new ShiftBandPair(template, band));
        }
        ShiftTemplate slackTemplate = template(ids, deskId, SLACK_TEMPLATE_SPEC.name(),
                SLACK_TEMPLATE_SPEC.start(), SLACK_TEMPLATE_SPEC.end());
        ShiftTemplateBreakBand slackBand = band(ids, slackTemplate, SLACK_TEMPLATE_SPEC.bandOffsetMinutes(),
                BREAK_DURATION_MINUTES);
        pairs.add(new ShiftBandPair(slackTemplate, slackBand));
        List<ShiftBandPair> sharedPairs = List.copyOf(pairs);

        // 44 full-time agents, round-robin assigned to one of the four main templates (index mod
        // 4) -- an equal 11-agent cohort per template.
        List<Agent> fullTimeAgents = new ArrayList<>();
        for (int i = 0; i < FULL_TIME_COUNT; i++) {
            Agent a = agent(ids, deskId, "Agent-" + (i + 1), spec);
            a.setContractedHoursPerDay(FULL_TIME_HOURS);
            fullTimeAgents.add(a);
        }
        // 4 part-time agents, contracted to 1.20h/day (FTE 0.15) -- below every template's net
        // duration, so they hold no eligible shift band pair (see class javadoc).
        List<Agent> partTimeAgents = new ArrayList<>();
        for (int i = 0; i < PART_TIME_COUNT; i++) {
            Agent a = agent(ids, deskId, "Agent-" + (FULL_TIME_COUNT + i + 1), spec);
            a.setContractedHoursPerDay(PART_TIME_HOURS);
            partTimeAgents.add(a);
        }
        List<Agent> allAgents = new ArrayList<>(fullTimeAgents);
        allAgents.addAll(partTimeAgents);

        // Cohort assignment: full-time agent i (0-indexed, sorted by creation order == id order)
        // takes MAIN_TEMPLATE_SPECS.get(i % 4).
        Map<Agent, Integer> templateIndexByAgent = new LinkedHashMap<>();
        for (int i = 0; i < fullTimeAgents.size(); i++) {
            templateIndexByAgent.put(fullTimeAgents.get(i), i % MAIN_TEMPLATE_SPECS.size());
        }

        // Demand, derived from cohort membership: at each non-break hourly start any cohort
        // covers, demand == that hour's working-cohort headcount, and every one of those seats is
        // pre-assigned directly to exactly the agents whose chosen template covers it. This is a
        // single uniform pinning rule, cohort-shaped, following MidnightBoundaryFixture's own
        // "one rule, applied uniformly" discipline (see class javadoc).
        Map<LocalTime, List<Agent>> workingAgentsByHour = new TreeMap<>();
        for (Map.Entry<Agent, Integer> entry : templateIndexByAgent.entrySet()) {
            TemplateSpec spec0 = MAIN_TEMPLATE_SPECS.get(entry.getValue());
            for (LocalTime hour : nonBreakHours(spec0)) {
                workingAgentsByHour.computeIfAbsent(hour, h -> new ArrayList<>()).add(entry.getKey());
            }
        }

        List<Timeslot> timeslots = new ArrayList<>();
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        List<TimeslotDemandConfig> demandConfigs = new ArrayList<>();
        for (Map.Entry<LocalTime, List<Agent>> entry : workingAgentsByHour.entrySet()) {
            LocalTime start = entry.getKey();
            LocalTime end = start.plusMinutes(INCREMENT_MINUTES);
            List<Agent> working = entry.getValue();

            Timeslot ts = timeslot(ids, deskId, scheduleId, BASE_DATE, start, end);
            timeslots.add(ts);
            staffingReqs.add(staffingRequirement(ids, deskId, scheduleId, ts, spec, working.size()));
            demandConfigs.add(new TimeslotDemandConfig(ts, working.size()));
            for (Agent a : working) {
                AgentAssignment seat = seat(ids, deskId, scheduleId, ts, spec);
                seat.setAgent(a);
                assignments.add(seat);
            }
        }

        // One AgentDayConfig per agent, anchor passed explicitly as the eleventh argument (D-14 /
        // plan 20-01) -- never falling through the ten-argument delegating constructor.
        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        Map<UUID, AgentDayConfig> dayConfigByAgentId = new LinkedHashMap<>();
        for (Agent a : fullTimeAgents) {
            AgentDayConfig dc = dayConfig(a.getId(), BASE_DATE, FULL_TIME_HOURS);
            dayConfigs.add(dc);
            dayConfigByAgentId.put(a.getId(), dc);
        }
        for (Agent a : partTimeAgents) {
            AgentDayConfig dc = dayConfig(a.getId(), BASE_DATE, PART_TIME_HOURS);
            dayConfigs.add(dc);
            dayConfigByAgentId.put(a.getId(), dc);
        }

        // One AgentShiftAssignment per agent. Full-time rows get their cohort template's
        // ShiftBandPair pinned directly (an exact, deterministic match -- no candidate-list
        // ambiguity, since cohort membership and template choice are the same fact here).
        // Part-time rows are left with a null shiftBandPair -- no template's net hours match
        // their 1.20h contract, so getEligibleShiftBandPairs() would return empty regardless.
        List<AgentShiftAssignment> shiftAssignments = new ArrayList<>();
        for (Agent a : fullTimeAgents) {
            int templateIndex = templateIndexByAgent.get(a);
            AgentShiftAssignment row = shiftAssignmentRow(ids, deskId, scheduleId, a, BASE_DATE,
                    dayConfigByAgentId.get(a.getId()), sharedPairs);
            row.setShiftBandPair(sharedPairs.get(templateIndex));
            shiftAssignments.add(row);
        }
        for (Agent a : partTimeAgents) {
            AgentShiftAssignment row = shiftAssignmentRow(ids, deskId, scheduleId, a, BASE_DATE,
                    dayConfigByAgentId.get(a.getId()), sharedPairs);
            // shiftBandPair deliberately left null -- see class javadoc.
            shiftAssignments.add(row);
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
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(20, 0));
        schedule.setPeriodStartDate(BASE_DATE);
        schedule.setPeriodEndDate(BASE_DATE);
        schedule.setDayStart(LocalTime.MIDNIGHT); // D-14: states its 00:00 anchor explicitly
        schedule.setBreakBlockedHours(new BigDecimal("1.00"));
        schedule.setBreakDurationMinutes(BREAK_DURATION_MINUTES);
        schedule.setBreakMinShiftHours(new BigDecimal("4.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(FULL_TIME_HOURS);
        schedule.setOverallocationHardLimitPct(150);
        schedule.setUnderallocationHardLimitPct(50);
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(spec));
        schedule.setAgents(allAgents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(new ArrayList<>());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setTimeslotDemandConfigs(demandConfigs);
        schedule.setAssignments(assignments);
        schedule.setShiftBandPairs(sharedPairs);
        schedule.setShiftAssignments(shiftAssignments);

        return schedule;
    }

    // ------------------------------------------------------------------
    //  Factory helpers -- every id is deterministic (seeded AtomicLong counter, never random)
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

    private static Agent agent(AtomicLong ids, UUID deskId, String name, Specialization spec) {
        // No external-HR identifier is ever set on this synthetic agent -- the only identity data
        // this fixture carries is the synthetic Agent-N name and a seeded UUID (class javadoc).
        Agent a = new Agent();
        a.setId(nextId(ids));
        a.setTenantId(TENANT);
        a.setName(name);
        a.setActive(true);
        a.setDeskId(deskId);
        a.setPrimarySpecialization(spec);
        a.setSecondarySpecializations(new ArrayList<>());
        return a;
    }

    private static ShiftTemplate template(AtomicLong ids, UUID deskId, String name, LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(EnumSet.allOf(java.time.DayOfWeek.class));
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
        ts.setBusinessDate(date); // 00:00 anchor: business date == calendar date (D-14)
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
        // Anchor passed explicitly as the eleventh argument (D-14 / plan 20-01) -- never falling
        // through the ten-argument delegating constructor to an inherited midnight default.
        return new AgentDayConfig(agentId, date, effectiveHours, INCREMENT_MINUTES, BREAK_DURATION_MINUTES,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 150, 50,
                LocalTime.MIDNIGHT);
    }

    private static AgentShiftAssignment shiftAssignmentRow(AtomicLong ids, UUID deskId, UUID scheduleId,
            Agent agent, LocalDate date, AgentDayConfig dayConfig, List<ShiftBandPair> sharedPairs) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(nextId(ids));
        row.setTenantId(TENANT);
        row.setDeskId(deskId);
        row.setScheduleId(scheduleId);
        row.setAgent(agent);
        row.setDate(date);
        row.setDayConfig(dayConfig);
        row.setDeskShiftBandPairs(sharedPairs);
        return row;
    }

    // ------------------------------------------------------------------
    //  Reading match counts (D-12's helper, applied to this fixture)
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
     * Normalises {@code shiftStartMixWeight} away from {@link HardSoftScore#ZERO} -- Timefold
     * elides a zero-weight constraint from {@code explain()} entirely rather than reporting it at
     * zero (empirically confirmed against this tree; see {@code ConstraintMatchCountNonVacuityTest}'s
     * identical finding and javadoc). This fixture carries no {@code ShiftStartMixTarget} facts, so
     * the match count is zero either way; this only stops the entry being elided.
     */
    private static Schedule normalizeZeroWeightConstraints(Schedule schedule) {
        if (HardSoftScore.ZERO.equals(schedule.getConstraintWeights().getShiftStartMixWeight())) {
            schedule.getConstraintWeights().setShiftStartMixWeight(HardSoftScore.ofSoft(1));
        }
        return schedule;
    }

    private static int matchCountOf(SolutionManager<Schedule, HardSoftScore> solutionManager,
            Schedule schedule, String constraintName) {
        List<ConstraintMatchTotal<HardSoftScore>> matches = solutionManager.explain(schedule)
                .getConstraintMatchTotalMap().values().stream()
                .filter(total -> constraintName.equals(total.getConstraintName()))
                .toList();
        assertThat(matches)
                .as("constraint '%s' must be present in the explanation before its match count is read",
                        constraintName)
                .hasSize(1);
        return matches.get(0).getConstraintMatchCount();
    }

    // ------------------------------------------------------------------
    //  The literal baseline (D-12 / D-14) -- captured 2026-10-01, pre-migration.
    //  Never regenerate these from post-migration output.
    // ------------------------------------------------------------------

    private static final Map<String, Integer> EXPECTED_MATCH_COUNTS = buildExpectedMatchCounts();
    /**
     * {@code -400hard}: four part-time agents each read zero assigned slots against an
     * {@code expectedWorkSlots()} of one (HALF_UP of 1.2h on a 60-minute grid), penalised at
     * {@code contractedHoursUnderZeroWeight}'s shipped default of {@code ofHard(100)} -- 4 x -100.
     * {@code -40soft}: four hours (one per main template's own break window) each read an 11-agent
     * on-break cohort against a 33-agent assigned total, 11 > 33 x 20% (the shipped
     * {@code breakClusterThresholdPct} default), penalised {@code onBreak - floor(totalAssigned x
     * 20 / 100)} = {@code 11 - 6 = 5} base points at {@code breakClusteringWeight}'s shipped
     * default of {@code ofSoft(2)} -- 4 x (5 x 2) = -40.
     */
    private static final HardSoftScore EXPECTED_SCORE = HardSoftScore.of(-400, -40);

    private static Map<String, Integer> buildExpectedMatchCounts() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("Unassigned assignment", 0);
        expected.put("Agent day off", 0);
        expected.put("Agent not working that day", 0);
        expected.put("Specialization match", 0);
        expected.put("One assignment per timeslot", 0);
        expected.put("Exactly one break", 0);
        expected.put("Break duration", 0);
        expected.put("Break blocked window", 0);
        expected.put("Break start alignment", 0);
        expected.put("Shift envelope compliance", 0);
        expected.put("Band capacity", 0);
        expected.put("Shift work contiguity", 0);
        expected.put("Prefer primary specialization", 0);
        expected.put("Honour preferred start time", 0);
        expected.put("Honour preferred break time", 0);
        expected.put("Break clustering", 4);
        expected.put("Contracted hours (over)", 0);
        expected.put("Contracted hours (under)", 0);
        expected.put("Contracted hours (under, zero)", 4);
        expected.put("Bulk over-allocation limit", 0);
        expected.put("Bulk under-allocation soft", 0);
        expected.put("Bulk under-allocation hard", 0);
        expected.put("Minimum staffing", 0);
        expected.put("Usual shift consistency", 0);
        expected.put("Preferred start (shift mode)", 0);
        expected.put("Shift start mix", 0);
        return java.util.Collections.unmodifiableMap(expected);
    }

    // ------------------------------------------------------------------
    //  No search during evaluation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("evaluation performs no search")
    class NoSearchDuringEvaluation {

        @Test
        @DisplayName("scoring this fixture mutates no planning variable")
        void scoringMutatesNoPlanningVariable() {
            Schedule schedule = normalizeZeroWeightConstraints(buildSchedule());

            List<UUID> assignedAgentIdsBefore = schedule.getAssignments().stream()
                    .map(a -> a.getAgent() == null ? null : a.getAgent().getId())
                    .toList();
            List<UUID> shiftBandPairTemplateIdsBefore = schedule.getShiftAssignments().stream()
                    .map(sa -> sa.getShiftBandPair() == null ? null : sa.getShiftBandPair().template().getId())
                    .toList();

            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            solutionManager.update(schedule);

            assertThat(schedule.getAssignments().stream()
                    .map(a -> a.getAgent() == null ? null : a.getAgent().getId())
                    .toList())
                    .as("every seat's pinned agent must be unchanged after scoring")
                    .isEqualTo(assignedAgentIdsBefore);
            assertThat(schedule.getShiftAssignments().stream()
                    .map(sa -> sa.getShiftBandPair() == null ? null : sa.getShiftBandPair().template().getId())
                    .toList())
                    .as("every shift row's band-pair choice (including the part-timers' null choice) "
                            + "must be unchanged after scoring")
                    .isEqualTo(shiftBandPairTemplateIdsBefore);
        }
    }

    // ------------------------------------------------------------------
    //  Composition, re-asserted as a test (not only at class load)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("composition: 48 agents, 44.6 total contracted FTE, a slack template, band-offset margins")
    void composition_fixtureMatchesItsOwnDeclaredShape() {
        Schedule schedule = buildSchedule();

        assertThat(schedule.getAgents()).hasSize(48);

        BigDecimal totalFte = schedule.getAgents().stream()
                .map(a -> a.getContractedHoursPerDay().divide(FULL_TIME_HOURS, 2, java.math.RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalFte).isEqualByComparingTo(EXPECTED_TOTAL_FTE);

        assertThat(schedule.getShiftBandPairs()).hasSize(5);
        boolean hasSlack = schedule.getShiftBandPairs().stream()
                .anyMatch(p -> p.netHours().compareTo(FULL_TIME_HOURS) > 0);
        assertThat(hasSlack)
                .as("the shift library must carry at least one template whose net hours exceed the "
                        + "full-time contract (the slack-template composition property)")
                .isTrue();
    }

    // ------------------------------------------------------------------
    //  The drift guard itself -- expected GREEN before and after the migration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every registered constraint's match count, and the score, exactly match the pre-migration literal baseline")
    void driftGuard_everyConstraintMatchCountAndTheScoreMatchTheLiteralBaseline() {
        Schedule schedule = normalizeZeroWeightConstraints(buildSchedule());
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
        HardSoftScore actualScore = solutionManager.update(schedule);

        Map<String, Integer> actual = new TreeMap<>();
        Map<String, String> mismatches = new TreeMap<>();
        for (Map.Entry<String, Integer> expected : EXPECTED_MATCH_COUNTS.entrySet()) {
            int count = matchCountOf(solutionManager, schedule, expected.getKey());
            actual.put(expected.getKey(), count);
            if (count != expected.getValue()) {
                mismatches.put(expected.getKey(), count + " actual vs " + expected.getValue() + " expected");
            }
        }

        assertThat(mismatches)
                .as("every registered constraint's match count must equal its literal committed "
                        + "expected value from EXPECTED_MATCH_COUNTS -- a count differing by one in "
                        + "either direction is a drift this guard exists to catch. Full actual map: %s",
                        actual)
                .isEmpty();
        assertThat(actualScore)
                .as("the total score must equal the literal committed EXPECTED_SCORE exactly")
                .isEqualTo(EXPECTED_SCORE);
    }
}
