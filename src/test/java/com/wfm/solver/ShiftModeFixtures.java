package com.wfm.solver;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reusable shift-mode {@link Schedule} fixture builder (Phase 15, plan 15-04) — shared by this
 * plan's ENVL-07 ground-truth test and, per D-09/P-19, plans 15-06 (break clustering) and 15-08
 * (CH-ordering benchmark). Building this fixture independently in three places would be three
 * chances to build a subtly different one (P-19): every caller gets the SAME desk shape — one
 * operating window, a small agent roster all contracted to the same hours, a shift library of
 * {@code templateCount} identically-shaped banded templates whose net duration matches those
 * hours exactly, and staffing demand covering every non-break slot with two specializations split
 * across the break window so ENVL-03 (specialization varies freely within a shift) is exercised by
 * construction, not by accident.
 *
 * <p>Every {@link AgentAssignment} starts unassigned ({@code agent == null}) and every
 * {@link AgentShiftAssignment} starts with a {@code null shiftBandPair} — all placement is the
 * solver's job (ENVL-06, "no pre-assignment pipeline").
 *
 * <p><strong>Deterministic ids.</strong> Every entity id in a fixture built by this class is a
 * sequential {@code UUID} derived from a per-call counter, not {@code UUID.randomUUID()}. This is
 * load-bearing, not cosmetic: {@link AgentAssignmentDifficultyComparator} breaks difficulty ties
 * (same date, same timeslot start -- exactly the shape of every one of this fixture's same-slot
 * seats) by comparing entity ids, so a random-UUID tiebreak would silently randomise the
 * construction heuristic's seat placement order across otherwise-identical runs and made the same
 * fixture parameters converge to {@code 0hard} on some runs and not others under a fixed step
 * budget. Two calls with the same parameters now produce byte-identical entity graphs.
 */
final class ShiftModeFixtures {

    private ShiftModeFixtures() {}

    static final long TENANT = 1L;
    static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 7); // Monday
    static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    static final LocalTime OPERATING_END = LocalTime.of(17, 0); // 9h envelope
    static final int INCREMENT_MINUTES = 15;
    static final int BREAK_DURATION_MINUTES = 60;
    static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00"); // matches every template's net hours exactly
    static final int OVERALLOCATION_PCT = 130;
    static final int UNDERALLOCATION_PCT = 70;

    /**
     * ON_HOUR-aligned offsets (minutes from envelope start) that stay clear of
     * {@link #BREAK_BLOCKED_HOURS} at either end of the 9h envelope with a
     * {@link #BREAK_DURATION_MINUTES}-minute band: valid range is [60, 420] in 60-minute steps.
     * Index 0 (240 -> 12:00) is the offset every test in this plan relies on for its single-band
     * default.
     */
    private static final int[] ON_HOUR_BAND_OFFSETS = {240, 180, 300, 120, 360, 60, 420};

    /** Everything a caller needs beyond the raw {@link Schedule} to drive assertions or mutations. */
    record ShiftFixture(Schedule schedule, List<Agent> agents, List<ShiftTemplate> templates,
                         Specialization specBeforeBreak, Specialization specAfterBreak) {}

    /**
     * A complete SHIFT-mode {@link Schedule}: {@code agentCount} agents each contracted to
     * {@link #CONTRACTED_HOURS}, working {@code dayCount} consecutive days from {@link #BASE_DATE},
     * choosing among {@code templateCount} shift templates — all sharing the same
     * {@link #OPERATING_START}/{@link #OPERATING_END} envelope, each carrying {@code bandsPerTemplate}
     * break bands of {@link #BREAK_DURATION_MINUTES} minutes at distinct ON_HOUR offsets, so every
     * template's net duration equals {@link #CONTRACTED_HOURS} regardless of which band is chosen
     * (D-04's entity-level value-range filter reads only net hours, never the band).
     *
     * <p><strong>Demand assumes every working agent takes the SAME break window</strong> — only
     * correct when every generated band shares one offset, i.e. {@code bandsPerTemplate == 1}, the
     * value every test in this plan uses. A caller passing {@code bandsPerTemplate > 1} to exercise
     * staggered breaks (e.g. plan 15-06's clustering demonstration) must build its own demand shape;
     * this method's uniform per-slot demand does not adapt to staggered break distributions.
     */
    static ShiftFixture buildShiftModeSchedule(int agentCount, int dayCount, int templateCount, int bandsPerTemplate) {
        if (bandsPerTemplate < 1 || bandsPerTemplate > ON_HOUR_BAND_OFFSETS.length) {
            throw new IllegalArgumentException("bandsPerTemplate must be in [1, " + ON_HOUR_BAND_OFFSETS.length + "]");
        }

        AtomicLong ids = new AtomicLong(1);
        UUID deskId = nextId(ids);
        UUID scheduleId = nextId(ids);

        // Two specializations split across the break (ENVL-03, case six) -- every agent qualifies
        // for both (primary + secondary), so specializationMatch stays satisfiable regardless of
        // which half of the shift a seat falls in.
        Specialization specBeforeBreak = specialization(ids, deskId, "Chat");
        Specialization specAfterBreak = specialization(ids, deskId, "Voice");

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            Agent a = agent(ids, deskId, "A-" + (i + 1), "Agent-" + (i + 1));
            a.setPrimarySpecialization(specBeforeBreak);
            a.setSecondarySpecializations(new ArrayList<>(List.of(specAfterBreak)));
            a.setContractedHoursPerDay(CONTRACTED_HOURS);
            agents.add(a);
        }

        List<ShiftTemplate> templates = new ArrayList<>();
        for (int t = 0; t < templateCount; t++) {
            templates.add(template(ids, deskId, "Shift-" + (char) ('A' + t), OPERATING_START, OPERATING_END));
        }

        List<ShiftBandPair> pairs = new ArrayList<>();
        for (ShiftTemplate shiftTemplate : templates) {
            for (int b = 0; b < bandsPerTemplate; b++) {
                ShiftTemplateBreakBand band = band(ids, shiftTemplate, ON_HOUR_BAND_OFFSETS[b], BREAK_DURATION_MINUTES);
                pairs.add(new ShiftBandPair(shiftTemplate, band));
            }
        }
        pairs.sort(Comparator.comparing((ShiftBandPair p) -> p.template().getName())
                .thenComparing(p -> p.band().getOffsetMinutes()));
        // Every AgentShiftAssignment row shares this ONE list instance -- mirrors
        // SolverService.buildShiftAssignments' production precedent (SolverServiceShiftAssignmentTest
        // #buildShiftAssignments_everyRowSharesTheSameDeskPairListInstance).
        List<ShiftBandPair> sharedPairs = List.copyOf(pairs);

        // The shared break window every working agent takes, derived from the FIRST offset -- valid
        // only for bandsPerTemplate == 1 (see method javadoc).
        LocalTime breakStart = OPERATING_START.plusMinutes(ON_HOUR_BAND_OFFSETS[0]);
        LocalTime breakEnd = breakStart.plusMinutes(BREAK_DURATION_MINUTES);

        List<Timeslot> allTimeslots = new ArrayList<>();
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
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
                boolean onBreak = !ts.getStartTime().isBefore(breakStart) && ts.getStartTime().isBefore(breakEnd);
                if (onBreak) {
                    continue; // nobody works the shared break window -- zero demand, not a zero-fill seat
                }
                Specialization required = ts.getStartTime().isBefore(breakStart) ? specBeforeBreak : specAfterBreak;

                StaffingRequirement sr = new StaffingRequirement();
                sr.setId(nextId(ids));
                sr.setTenantId(TENANT);
                sr.setDeskId(deskId);
                sr.setScheduleId(scheduleId);
                sr.setTimeslot(ts);
                sr.setSpecialization(required);
                sr.setRequiredFTEs(agentCount);
                staffingReqs.add(sr);

                for (int i = 0; i < agentCount; i++) {
                    AgentAssignment seat = new AgentAssignment();
                    seat.setId(nextId(ids));
                    seat.setTenantId(TENANT);
                    seat.setDeskId(deskId);
                    seat.setScheduleId(scheduleId);
                    seat.setTimeslot(ts);
                    seat.setRequiredSpecialization(required);
                    // seat.setAgent(...) deliberately never called -- CH places every seat (ENVL-06)
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
                // row.setShiftBandPair(...) deliberately never called -- CH places every shift (ENVL-06)
                shiftAssignments.add(row);
            }
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
        schedule.setSpecializations(List.of(specBeforeBreak, specAfterBreak));
        schedule.setAgents(agents);
        schedule.setTimeslots(allTimeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setShiftBandPairs(sharedPairs);
        schedule.setShiftAssignments(shiftAssignments);
        schedule.setTimeslotDemandConfigs(computeTimeslotDemandConfigs(seats));
        schedule.setAssignments(seats);

        return new ShiftFixture(schedule, agents, templates, specBeforeBreak, specAfterBreak);
    }

    /**
     * The SLOT-mode sibling: zero {@link AgentShiftAssignment} rows, zero {@link ShiftBandPair}s,
     * {@link SchedulingMode#SLOT}. Same operating window, agent roster and demand shape as
     * {@link #buildShiftModeSchedule} (built via the same code path, then stripped) so a caller can
     * diff the two modes' solved outcomes directly rather than maintaining two fixture builders that
     * could quietly drift apart (P-19).
     */
    static Schedule buildSlotModeSchedule(int agentCount, int dayCount) {
        Schedule schedule = buildShiftModeSchedule(agentCount, dayCount, 1, 1).schedule();
        schedule.setSchedulingMode(SchedulingMode.SLOT);
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setShiftBandPairs(new ArrayList<>());
        return schedule;
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

    private static List<com.wfm.model.TimeslotDemandConfig> computeTimeslotDemandConfigs(List<AgentAssignment> assignments) {
        java.util.Map<Timeslot, Integer> demandPerTimeslot = new java.util.LinkedHashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        List<com.wfm.model.TimeslotDemandConfig> configs = new ArrayList<>();
        for (var e : demandPerTimeslot.entrySet()) {
            configs.add(new com.wfm.model.TimeslotDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
    }
}
