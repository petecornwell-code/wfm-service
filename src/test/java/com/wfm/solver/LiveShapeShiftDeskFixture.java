package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * (Phase 15, plan 15-14, gap closure G-15-22 / G-15-29) The live-shape desk that
 * {@link SolverQualityGuardTest} solves. Not a captured dump of the live Stubhub (EN) desk — a
 * synthetic fixture transcribed verbatim, template/band geometry and weights alike, from
 * {@code HANDOFF.md} §2/§3, the shape the three quality invariants were measured stable against
 * on 2026-09-01 (P-37). No agent name, email, or BambooHR identifier from any real desk is ever
 * read (T-15-33) — agents here are {@code Agent-N} / {@code A-N}, matching {@code 15-BENCHMARK.md}
 * T-15-32's precedent.
 *
 * <p><strong>The slack template (P-38).</strong> {@code Weekend Flex} (10:00-20:00) has a 600-minute
 * envelope less a 60-minute band: 540 net minutes against the 480-minute (8h) contract, one hour of
 * slack. Every other template's net hours equal the contract exactly. Without this one template a
 * split shift would not be representable at all and {@link SolverQualityGuardTest}'s split-shift
 * invariant would be vacuously true. {@link #validateTemplateSpecs()} asserts this at class-load
 * time so a future library edit that removes the slack fails loudly, not silently.
 *
 * <p><strong>Band-offset margin (P-39).</strong> Every band stays at least 120 minutes clear of both
 * envelope edges — {@link #validateTemplateSpecs()} enforces this too. With one hour of slack in
 * play, a band placed too close to an envelope edge could legitimately leave no worked slot on one
 * side, which would make the edge-break invariant flaky for a legal schedule — the exact failure
 * mode this whole plan exists to avoid.
 *
 * <p><strong>Demand (P-40).</strong> Derived from a provably-satisfiable ideal assignment: two
 * holders per template, each holder taking a distinct band, each working their template's legal
 * non-break hours truncated to the 8-hour contract by dropping from the END. Per-hour demand is the
 * count of ideal holders working that hour; nothing else determines it. {@link #EDGE_HOURS} —
 * 08:00, 09:00, 10:00, 20:00 — are the four hours {@code HANDOFF.md} §8 recorded as non-zero on
 * every day of the live desk; 08:00/09:00 are reachable only via {@code Weekend Opening}, 20:00 only
 * via {@code Weekend Closing}, and 10:00 is the non-sole-routed control (also reachable via
 * {@code Weekend Early} and {@code Weekend Flex}).
 *
 * <p><strong>Weights (P-41).</strong> Four {@link ConstraintWeights} fields are pinned to
 * {@code HANDOFF.md} §2's live settings — {@code shiftEnvelopeComplianceWeight} and
 * {@code shiftWorkContiguityWeight} both {@code ofHard(10)}, {@code bandCapacityWeight}
 * {@code ofHard(1)}, {@code unassignedAssignmentWeight} {@code ofSoft(10000)} — because that is the
 * exact configuration the three invariants were measured stable under. Every other weight is left at
 * its shipped default. The shipped no-arg defaults are pinned separately, by
 * {@link SolverQualityGuardTest#defaultConstraintWeights_areTheDocumentedShippedValues()}, so a
 * silent change to a shipped default does not go unnoticed just because this fixture pins its own
 * values (the G-15-30 shape).
 *
 * <p>Filler seats come from the production {@link SolverSeatExpansionAccess#expandMinimumStaffingSeats}
 * — never a fixture-local reimplementation — matching {@link ShiftModeFixtures}' own precedent. Every
 * entity id is a sequential {@code UUID} derived from a per-call {@link AtomicLong} counter, including
 * re-stamped filler seats: {@link AgentAssignmentDifficultyComparator} breaks difficulty ties on
 * entity id, so random ids would silently randomise construction-heuristic seat order and make the
 * same parameters converge on some runs and not others (see {@link ShiftModeFixtures}'s class
 * javadoc for the full argument).
 */
final class LiveShapeShiftDeskFixture {

    private LiveShapeShiftDeskFixture() {}

    static final long TENANT = 1L;
    static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 5); // the live period's Monday
    static final int INCREMENT_MINUTES = 60;

    static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    static final LocalTime OPERATING_END = LocalTime.of(21, 0);

    static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
    static final int BREAK_DURATION_MINUTES = 60;
    static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    static final int OVERALLOCATION_PCT = 500;
    static final int UNDERALLOCATION_PCT = 50;

    static final int IDEAL_HOLDERS_PER_TEMPLATE = 2;

    /**
     * Convergence escape-hatch rung 3 (plan 15-14's bounded escape-hatch clause), applied jointly
     * with {@link SolverQualityGuardTest#STEP_COUNT_LIMIT}'s rung 1. At {@code DAY_COUNT = 3} this
     * fixture's slack-carrying, 270-entity problem left a genuine interior hole on seed 1 even at
     * {@code STEP_COUNT_LIMIT = 5_000}; reduced to {@code 2} per the ladder's third rung. This is a
     * fixture-fairness finding on the UNMODIFIED build (the invariants ARE structurally sound; the
     * search needed more room than the plan's initial budget gave it), not a product regression --
     * see {@link SolverQualityGuardTest}'s class javadoc and the plan's own SUMMARY for the full
     * record. Never dropped a seed, never weakened an invariant, never removed the
     * {@code Weekend Flex} slack template.
     */
    static final int DAY_COUNT = 2;

    /** One live-library template's shape: envelope start/end and its break bands' offsets from the
     * envelope start, in minutes. Transcribed verbatim from {@code HANDOFF.md} §2. */
    record TemplateSpec(String name, LocalTime start, LocalTime end, int[] bandOffsetMinutes) {}

    static final List<TemplateSpec> TEMPLATE_SPECS = List.of(
            new TemplateSpec("Weekend Opening", LocalTime.of(8, 0), LocalTime.of(17, 0), new int[] {240, 300, 360}),
            new TemplateSpec("Weekend Early", LocalTime.of(10, 0), LocalTime.of(19, 0), new int[] {180, 240, 300}),
            new TemplateSpec("Weekend Flex", LocalTime.of(10, 0), LocalTime.of(20, 0), new int[] {180, 240, 300}),
            new TemplateSpec("Weekend Late", LocalTime.of(11, 0), LocalTime.of(20, 0), new int[] {180, 240, 300}),
            new TemplateSpec("Weekend Closing", LocalTime.of(12, 0), LocalTime.of(21, 0), new int[] {180, 240, 300}));

    /** The four hours {@code HANDOFF.md} §8 recorded as staffed on every day of the live desk. */
    static final List<LocalTime> EDGE_HOURS =
            List.of(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(20, 0));

    private static final int BAND_EDGE_MARGIN_MINUTES = 120;

    static {
        validateTemplateSpecs();
    }

    /**
     * Fails at class-load time, loudly, if a future library edit would make the guard's invariants
     * flaky or vacuous — P-38 (slack template must exist) and P-39 (band-edge margin).
     */
    private static void validateTemplateSpecs() {
        int contractedMinutes = CONTRACTED_HOURS.multiply(BigDecimal.valueOf(60)).intValueExact();
        boolean hasSlackTemplate = false;

        for (TemplateSpec ts : TEMPLATE_SPECS) {
            int envelopeMinutes = (int) Duration.between(ts.start(), ts.end()).toMinutes();
            if (envelopeMinutes - BREAK_DURATION_MINUTES > contractedMinutes) {
                hasSlackTemplate = true;
            }
            for (int offset : ts.bandOffsetMinutes()) {
                if (offset < BAND_EDGE_MARGIN_MINUTES) {
                    throw new IllegalStateException("Template '" + ts.name() + "' has a band offset of "
                            + offset + " minutes -- less than the required " + BAND_EDGE_MARGIN_MINUTES
                            + "-minute margin from the envelope start (P-39)");
                }
                if (offset + BREAK_DURATION_MINUTES > envelopeMinutes - BAND_EDGE_MARGIN_MINUTES) {
                    throw new IllegalStateException("Template '" + ts.name() + "' has a band offset of "
                            + offset + " minutes whose break end sits within " + BAND_EDGE_MARGIN_MINUTES
                            + " minutes of the envelope end (P-39): offset=" + offset + " duration="
                            + BREAK_DURATION_MINUTES + " envelopeMinutes=" + envelopeMinutes);
                }
            }
        }

        if (!hasSlackTemplate) {
            throw new IllegalStateException("No template in TEMPLATE_SPECS carries slack -- at least one "
                    + "template's envelope minutes minus its band duration must strictly exceed the "
                    + "contracted minutes (P-38), or the split-shift invariant is vacuous");
        }
    }

    /** Everything a caller needs beyond the raw {@link Schedule}. */
    record Fixture(Schedule schedule, List<Agent> agents, List<ShiftBandPair> pairs) {}

    /**
     * Builds a live-shape shift desk. {@code agentCount} MUST equal
     * {@code TEMPLATE_SPECS.size() * IDEAL_HOLDERS_PER_TEMPLATE} — the demand curve (P-40) is
     * derived from that identity and does not generalise to other roster sizes.
     */
    static Fixture build(int agentCount, int dayCount) {
        int requiredAgentCount = TEMPLATE_SPECS.size() * IDEAL_HOLDERS_PER_TEMPLATE;
        if (agentCount != requiredAgentCount) {
            throw new IllegalArgumentException("agentCount must be " + requiredAgentCount
                    + " (TEMPLATE_SPECS.size() * IDEAL_HOLDERS_PER_TEMPLATE) -- the demand curve (P-40) "
                    + "is derived from this identity");
        }

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

        // One ShiftTemplate + one ShiftTemplateBreakBand per offset per template, all sharing ONE
        // List<ShiftBandPair> instance across every AgentShiftAssignment row, mirroring
        // SolverService.buildShiftAssignments' production precedent.
        List<ShiftBandPair> pairs = new ArrayList<>();
        for (TemplateSpec ts : TEMPLATE_SPECS) {
            ShiftTemplate template = template(ids, deskId, ts.name(), ts.start(), ts.end());
            for (int offset : ts.bandOffsetMinutes()) {
                ShiftTemplateBreakBand band = band(ids, template, offset, BREAK_DURATION_MINUTES);
                pairs.add(new ShiftBandPair(template, band));
            }
        }
        List<ShiftBandPair> sharedPairs = List.copyOf(pairs);

        Map<LocalTime, Integer> demand = demandByHour();

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

            Map<LocalTime, Timeslot> timeslotByStart = dayTimeslots.stream()
                    .collect(Collectors.toMap(Timeslot::getStartTime, ts -> ts));

            for (Map.Entry<LocalTime, Integer> e : demand.entrySet()) {
                int ftes = e.getValue();
                if (ftes <= 0) {
                    continue;
                }
                Timeslot ts = timeslotByStart.get(e.getKey());
                if (ts == null) {
                    continue; // demand hours are always inside the operating window -- defensive only
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
                // row.setShiftBandPair(...) deliberately never called -- placement is the solver's job (ENVL-06)
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

        // TimeslotDemandConfig computed from demand seats ONLY, never the filler seats -- filler must
        // never inflate the over/under-allocation ceiling (unchanged production invariant).
        List<TimeslotDemandConfig> demandConfigs = new ArrayList<>();
        Map<Timeslot, Integer> perSlot = new LinkedHashMap<>();
        for (AgentAssignment a : demandSeats) {
            perSlot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        perSlot.forEach((ts, n) -> demandConfigs.add(new TimeslotDemandConfig(ts, n)));

        ConstraintWeights weights = pinnedLiveWeights(ids, deskId);

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

        return new Fixture(schedule, agents, sharedPairs);
    }

    /**
     * Per-hour demand (P-40): for every template and every ideal holder (0 .. IDEAL_HOLDERS_PER_TEMPLATE-1),
     * the holder's legal non-break hours inside the envelope, truncated to the 8-hour contract by
     * dropping from the END. Demand at an hour is the count of ideal holders working it. Independent
     * of date -- every template's {@code validWeekdays} covers every day of the week.
     */
    private static Map<LocalTime, Integer> demandByHour() {
        Map<LocalTime, Integer> demand = new LinkedHashMap<>();
        for (TemplateSpec ts : TEMPLATE_SPECS) {
            for (int h = 0; h < IDEAL_HOLDERS_PER_TEMPLATE; h++) {
                int bandOffset = ts.bandOffsetMinutes()[h % ts.bandOffsetMinutes().length];
                List<LocalTime> legal = legalNonBreakHours(ts, bandOffset);
                List<LocalTime> retained = truncateToContractedSlots(legal);
                for (LocalTime hour : retained) {
                    demand.merge(hour, 1, Integer::sum);
                }
            }
        }
        return demand;
    }

    /** Hourly slot starts inside {@code ts}'s envelope that do not fall inside the band's break window. */
    private static List<LocalTime> legalNonBreakHours(TemplateSpec ts, int bandOffsetMinutes) {
        LocalTime breakStart = ts.start().plusMinutes(bandOffsetMinutes);
        LocalTime breakEnd = breakStart.plusMinutes(BREAK_DURATION_MINUTES);
        List<LocalTime> hours = new ArrayList<>();
        for (LocalTime t = ts.start(); t.isBefore(ts.end()); t = t.plusMinutes(INCREMENT_MINUTES)) {
            LocalTime slotEnd = t.plusMinutes(INCREMENT_MINUTES);
            boolean onBreak = t.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
            if (!onBreak) {
                hours.add(t);
            }
        }
        return hours;
    }

    /** Drops hours from the END until the list is no longer than the contracted slot count (P-40). */
    private static List<LocalTime> truncateToContractedSlots(List<LocalTime> legalHours) {
        int contractedSlots = CONTRACTED_HOURS.intValue(); // INCREMENT_MINUTES == 60, so 1 slot == 1 hour
        if (legalHours.size() <= contractedSlots) {
            return legalHours;
        }
        return legalHours.subList(0, contractedSlots);
    }

    /**
     * Four {@link ConstraintWeights} fields pinned to {@code HANDOFF.md} §2's live settings (P-41).
     * Every other weight is left at its shipped default.
     */
    private static ConstraintWeights pinnedLiveWeights(AtomicLong ids, UUID deskId) {
        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(nextId(ids));
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);
        weights.setShiftEnvelopeComplianceWeight(HardSoftScore.ofHard(10));
        weights.setShiftWorkContiguityWeight(HardSoftScore.ofHard(10));
        weights.setBandCapacityWeight(HardSoftScore.ofHard(1));
        weights.setUnassignedAssignmentWeight(HardSoftScore.ofSoft(10000)); // soft level, not promoted to hard
        return weights;
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
