package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import com.wfm.dto.ScheduleDetailResponse.*;
import com.wfm.dto.ScheduleSummary;
import com.wfm.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Computes output views from raw AgentAssignment data.
 * Views are derived on-the-fly, not pre-computed.
 */
@Service
public class ScheduleOutputService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleOutputService.class);

    private final SolutionManager<Schedule, HardSoftScore> solutionManager;

    public ScheduleOutputService(SolverFactory<Schedule> solverFactory) {
        this.solutionManager = SolutionManager.create(solverFactory);
    }

    /**
     * 8.1 Staffing Summary — per-day per-specialization predicted vs actual hours.
     * Includes per-day totals and a grand total row per spec §8.1.
     */
    public List<StaffingSummaryEntry> buildStaffingSummary(Schedule schedule) {
        List<StaffingSummaryEntry> entries = new ArrayList<>();
        BigDecimal incrementHours = BigDecimal.valueOf(schedule.getIncrementMinutes())
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);

        // Group staffing requirements by (date, specName) → sum of FTE-hours
        // Convert FTEs to hours for the summary: FTEs × slotDurationHours
        Map<LocalDate, Map<String, BigDecimal>> predicted = new LinkedHashMap<>();
        for (StaffingRequirement sr : schedule.getStaffingRequirements()) {
            BigDecimal fteHours = BigDecimal.valueOf(sr.getRequiredFTEs()).multiply(incrementHours);
            predicted
                    .computeIfAbsent(sr.getTimeslot().getDate(), k -> new LinkedHashMap<>())
                    .merge(sr.getSpecialization().getName(), fteHours, BigDecimal::add);
        }

        // Group assigned (non-null agent) assignments by (date, requiredSpec name) → count
        Map<LocalDate, Map<String, Integer>> actualCounts = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            actualCounts
                    .computeIfAbsent(a.getTimeslot().getDate(), k -> new LinkedHashMap<>())
                    .merge(a.getRequiredSpecialization().getName(), 1, Integer::sum);
        }

        // Build entries with per-day totals and grand total
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(predicted.keySet());
        allDates.addAll(actualCounts.keySet());

        BigDecimal grandPred = BigDecimal.ZERO;
        BigDecimal grandActual = BigDecimal.ZERO;

        for (LocalDate date : allDates) {
            Map<String, BigDecimal> dayPredicted = predicted.getOrDefault(date, Map.of());
            Map<String, Integer> dayCounts = actualCounts.getOrDefault(date, Map.of());

            Set<String> specs = new TreeSet<>();
            specs.addAll(dayPredicted.keySet());
            specs.addAll(dayCounts.keySet());

            BigDecimal dayPred = BigDecimal.ZERO;
            BigDecimal dayActual = BigDecimal.ZERO;

            for (String specName : specs) {
                BigDecimal pred = dayPredicted.getOrDefault(specName, BigDecimal.ZERO);
                int count = dayCounts.getOrDefault(specName, 0);
                BigDecimal actual = BigDecimal.valueOf(count).multiply(incrementHours);
                BigDecimal delta = actual.subtract(pred);
                BigDecimal coverage = pred.compareTo(BigDecimal.ZERO) != 0
                        ? actual.divide(pred, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : null;

                entries.add(new StaffingSummaryEntry(date, specName, pred, actual, delta, coverage));
                dayPred = dayPred.add(pred);
                dayActual = dayActual.add(actual);
            }

            // Per-day total row
            if (specs.size() > 1) {
                BigDecimal dayDelta = dayActual.subtract(dayPred);
                BigDecimal dayCoverage = dayPred.compareTo(BigDecimal.ZERO) != 0
                        ? dayActual.divide(dayPred, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        : null;
                entries.add(new StaffingSummaryEntry(date, "TOTAL", dayPred, dayActual, dayDelta, dayCoverage));
            }

            grandPred = grandPred.add(dayPred);
            grandActual = grandActual.add(dayActual);
        }

        // Grand total row
        if (allDates.size() > 1) {
            BigDecimal grandDelta = grandActual.subtract(grandPred);
            BigDecimal grandCoverage = grandPred.compareTo(BigDecimal.ZERO) != 0
                    ? grandActual.divide(grandPred, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP)
                    : null;
            entries.add(new StaffingSummaryEntry(null, "GRAND TOTAL", grandPred, grandActual, grandDelta, grandCoverage));
        }

        return entries;
    }

    /**
     * 8.2 Agent Schedule — per-agent per-day assignments + breaks.
     */
    public List<AgentScheduleEntry> buildAgentSchedule(Schedule schedule) {
        BigDecimal incrementHours = BigDecimal.valueOf(schedule.getIncrementMinutes())
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);

        // Shift descriptor lookup by (agentId, date) — the ONE place this response's shift
        // descriptor is built, covering both the in-memory path (reading the transient
        // AgentShiftAssignment.shiftBandPair) and the accepted path (reading the D-07
        // denormalised scalar columns) identically, so the two shapes can never disagree. On a
        // slot-scheduled desk schedule.getShiftAssignments() is structurally empty and this map
        // stays empty — every entry's shift stays null, unchanged from today.
        Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate =
                buildShiftDescriptorsByAgentDate(schedule);

        // Timeslots for the schedule, grouped by date — the candidate slot set divergence
        // computation walks to find legal slots the agent did NOT hold (Task 2). The agent's own
        // assignment list cannot express a slot they do not hold, so this must come from the
        // schedule's own timeslots.
        Map<LocalDate, List<Timeslot>> timeslotsByDate = new HashMap<>();
        for (Timeslot ts : schedule.getTimeslots()) {
            timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);
        }

        // Group assigned assignments by (agentId, date)
        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            grouped
                    .computeIfAbsent(a.getAgent().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())
                    .add(a);
        }

        List<AgentScheduleEntry> entries = new ArrayList<>();
        for (var agentEntry : grouped.entrySet()) {
            for (var dateEntry : agentEntry.getValue().entrySet()) {
                List<AgentAssignment> dayAssignments = dateEntry.getValue();
                dayAssignments.sort(Comparator.comparing(a -> a.getTimeslot().getStartTime()));

                AgentAssignment first = dayAssignments.get(0);
                Agent da = first.getAgent();
                UUID agentId = da.getId();
                String agentName = da.getName();
                LocalDate date = dateEntry.getKey();

                BigDecimal totalHours = BigDecimal.valueOf(dayAssignments.size()).multiply(incrementHours);

                // Build assignment details
                List<AssignmentDetail> details = new ArrayList<>();
                for (AgentAssignment a : dayAssignments) {
                    String matchType = determineMatchType(da, a.getRequiredSpecialization());
                    details.add(new AssignmentDetail(
                            a.getTimeslot().getId(),
                            a.getTimeslot().getStartTime(),
                            a.getTimeslot().getEndTime(),
                            a.getRequiredSpecialization().getName(),
                            matchType));
                }

                ShiftDescriptor shiftDescriptor = shiftDescriptorsByAgentDate
                        .getOrDefault(agentId, Map.of()).get(date);

                LocalTime shiftStart;
                LocalTime shiftEnd;
                List<BreakDetail> breaks;
                ShiftEnvelopeDivergence divergence = null;

                if (shiftDescriptor != null) {
                    // Authoritative envelope and band-derived break (Task 2) — the report layer
                    // reads the same facts the solver already resolved, instead of re-deriving
                    // both from the seat pattern.
                    shiftStart = shiftDescriptor.startTime();
                    shiftEnd = shiftDescriptor.endTime();
                    breaks = bandBreaks(shiftDescriptor);
                    List<Timeslot> dayTimeslots = timeslotsByDate.getOrDefault(date, List.of());
                    divergence = computeDivergence(shiftDescriptor, dayAssignments, dayTimeslots);
                } else {
                    // No descriptor: a slot desk, or a shift-mode agent-day the solver left
                    // unassigned. Keep today's behaviour exactly — seat-derived span, gap-derived
                    // breaks, no divergence.
                    shiftStart = first.getTimeslot().getStartTime();
                    shiftEnd = dayAssignments.get(dayAssignments.size() - 1).getTimeslot().getEndTime();
                    breaks = findBreaks(dayAssignments);
                }

                entries.add(new AgentScheduleEntry(
                        agentId, agentName, date, shiftStart, shiftEnd,
                        totalHours, details, breaks, shiftDescriptor, divergence));
            }
        }

        entries.sort(Comparator.comparing(AgentScheduleEntry::agentName)
                .thenComparing(AgentScheduleEntry::date));

        // Roll the divergence total up into the schedule's own warnings collection — gives the
        // operator a headline without scanning every row (Task 2, T-15-10-01).
        int outOfEnvelopeSeatCount = 0;
        int unworkedLegalSlotCount = 0;
        for (AgentScheduleEntry entry : entries) {
            if (entry.divergence() != null) {
                outOfEnvelopeSeatCount += entry.divergence().outOfEnvelopeSeats().size();
                unworkedLegalSlotCount += entry.divergence().unworkedLegalSlots().size();
            }
        }
        publishDivergenceWarning(schedule, outOfEnvelopeSeatCount, unworkedLegalSlotCount);

        return entries;
    }

    /**
     * Stable marker identifying THIS method's warning among the schedule's other warnings (the
     * solver's capacity advisory, the seat-supply advisory). Matching on a fixed substring rather
     * than on whole-string equality is the point: the two counts change between polls while a
     * solve is still running, so equality-based dedup would let every changed count append a new
     * distinct line and the list would still grow — just less obviously.
     */
    private static final String ENVELOPE_DIVERGENCE_MARKER =
            " agent-day seat(s) fall outside their assigned shift envelope; ";

    /**
     * Publishes the envelope-divergence headline IDEMPOTENTLY (UAT test 20 / review CR-04).
     *
     * <p>{@link #buildAgentSchedule} is a READ path: {@code ScheduleService} calls it on every
     * GET of the schedule results, the results page polls that endpoint roughly every 2 seconds
     * while a solve is RUNNING, and {@code InMemoryScheduleStore.get} hands back the SAME live
     * {@link Schedule} instance by reference rather than a copy. A bare {@code warnings.add(...)}
     * therefore appended one more identical line to the operator's warnings panel on every
     * refresh, without bound, for as long as the page stayed open.
     *
     * <p>Replace-then-add rather than add-if-absent, so the published line always reflects the
     * CURRENT counts and a divergence that clears (count back to zero) removes its stale warning
     * instead of leaving a fixed one behind.
     *
     * <p>Synchronised on the list because concurrent polls — two browser tabs, or a poll racing
     * the solve's own completion write — would otherwise interleave {@code removeIf}'s iteration
     * with another thread's {@code add} on a plain {@code ArrayList}. The previous single
     * {@code add} was already unsafe here; iterating makes that latent race worth closing.
     */
    private void publishDivergenceWarning(Schedule schedule, int outOfEnvelopeSeatCount,
                                           int unworkedLegalSlotCount) {
        List<String> warnings = schedule.getWarnings();
        if (warnings == null) {
            return;
        }
        synchronized (warnings) {
            warnings.removeIf(w -> w != null && w.contains(ENVELOPE_DIVERGENCE_MARKER));
            if (outOfEnvelopeSeatCount > 0) {
                warnings.add(outOfEnvelopeSeatCount
                        + ENVELOPE_DIVERGENCE_MARKER
                        + unworkedLegalSlotCount + " legal envelope slot(s) went unworked.");
            }
        }
    }

    /**
     * 8.3 Preference Report — per-agent per-day preference resolution and honour flags.
     */
    public PreferenceReport buildPreferenceReport(Schedule schedule) {
        // Group assignments by agent + date, compute actual start times and breaks
        Map<UUID, Map<LocalDate, LocalTime>> actualStartTimes = new HashMap<>();
        Map<UUID, Map<LocalDate, List<BreakDetail>>> actualBreaks = new HashMap<>();

        // Same descriptor resolution buildAgentSchedule uses — resolved once, from the one
        // shared helper, so a shift desk's actual-break bookkeeping and its Agent Schedule
        // entries can never disagree about which agent-days carry an assigned shift (Task 2).
        Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate =
                buildShiftDescriptorsByAgentDate(schedule);

        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new HashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            UUID agentId = a.getAgent().getId();
            grouped
                    .computeIfAbsent(agentId, k -> new HashMap<>())
                    .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())
                    .add(a);
        }

        for (var agentEntry : grouped.entrySet()) {
            for (var dateEntry : agentEntry.getValue().entrySet()) {
                List<AgentAssignment> dayAssignments = dateEntry.getValue();
                dayAssignments.sort(Comparator.comparing(a -> a.getTimeslot().getStartTime()));

                LocalTime earliest = dayAssignments.get(0).getTimeslot().getStartTime();
                actualStartTimes
                        .computeIfAbsent(agentEntry.getKey(), k -> new HashMap<>())
                        .put(dateEntry.getKey(), earliest);

                // On a shift desk the actual break is the assigned band's window, not a
                // gap-derived hole — a shift-desk agent-day's actualBreakTime and the
                // break-honoured count now measure the real break, not a hole (Task 2,
                // T-15-10-05). This is a correction, not a regression: a band-derived break will
                // honour or miss a preferred break time differently from a hole-derived one.
                ShiftDescriptor descriptor = shiftDescriptorsByAgentDate
                        .getOrDefault(agentEntry.getKey(), Map.of()).get(dateEntry.getKey());
                List<BreakDetail> breaks = descriptor != null
                        ? bandBreaks(descriptor)
                        : findBreaks(dayAssignments);
                if (!breaks.isEmpty()) {
                    actualBreaks
                            .computeIfAbsent(agentEntry.getKey(), k -> new HashMap<>())
                            .put(dateEntry.getKey(), breaks);
                }
            }
        }

        // Build entries from resolved preferences
        List<PreferenceReportEntry> entries = new ArrayList<>();
        int totalPrefs = 0;
        int startHonoured = 0;
        int breakHonoured = 0;
        int totalFields = 0;
        int honouredFields = 0;

        for (AgentPreference pref : schedule.getAgentPreferences()) {
            if (pref.getDate() == null) continue;
            UUID agentId = pref.getAgent().getId();
            LocalDate date = pref.getDate();

            LocalTime prefStart = pref.getPreferredStartTime();
            LocalTime prefBreak = pref.getPreferredBreakTime();
            if (prefStart == null && prefBreak == null) continue;

            totalPrefs++;

            LocalTime actStart = actualStartTimes
                    .getOrDefault(agentId, Map.of()).get(date);

            // Find the actual break closest to preferred break time (spec §8.3)
            LocalTime actBreak = findClosestBreak(
                    actualBreaks.getOrDefault(agentId, Map.of()).get(date),
                    prefBreak);

            // Start time honoured: actualStartTime >= preferredStartTime (spec §8.3)
            boolean startOk;
            if (prefStart == null) {
                startOk = true;
            } else {
                startOk = actStart != null && !actStart.isBefore(prefStart);
            }
            if (prefStart != null) {
                totalFields++;
                if (startOk) { startHonoured++; honouredFields++; }
            }

            // Break time honoured: agent's break overlaps the preferred break timeslot (spec §8.3)
            boolean breakOk;
            if (prefBreak == null) {
                breakOk = true;
            } else {
                breakOk = breaksOverlapPreferred(
                        actualBreaks.getOrDefault(agentId, Map.of()).get(date),
                        prefBreak, schedule.getBreakDurationMinutes());
            }
            if (prefBreak != null) {
                totalFields++;
                if (breakOk) { breakHonoured++; honouredFields++; }
            }

            String source = pref.isStanding() ? "STANDING" : "WEEKLY";

            entries.add(new PreferenceReportEntry(
                    agentId, pref.getAgent().getName(), date, source,
                    prefStart, actStart, startOk,
                    prefBreak, actBreak, breakOk));
        }

        entries.sort(Comparator.comparing(PreferenceReportEntry::agentName)
                .thenComparing(PreferenceReportEntry::date));

        BigDecimal overallPct = totalFields > 0
                ? BigDecimal.valueOf(honouredFields)
                        .divide(BigDecimal.valueOf(totalFields), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100).setScale(2, RoundingMode.UNNECESSARY);

        PreferenceSummary summary = new PreferenceSummary(
                totalPrefs, startHonoured, breakHonoured, overallPct);

        return new PreferenceReport(entries, summary);
    }

    /**
     * 8.4 Constraint Violations — grouped by constraint name.
     * Uses the cached Timefold SolutionManager to explain the score.
     * Only works for in-memory schedules with complete solver data;
     * accepted (DB) schedules should not call this method.
     */
    @SuppressWarnings("removal")
    public List<ConstraintViolationEntry> buildConstraintViolations(Schedule schedule) {
        if (schedule.getAssignments() == null || schedule.getAssignments().isEmpty()) {
            return List.of();
        }

        // Cannot explain accepted schedules — they lack solver problem facts
        if (schedule.getConstraintWeights() == null) {
            return List.of();
        }

        try {
            var explanation = solutionManager.explain(schedule);
            Map<String, ConstraintMatchTotal<HardSoftScore>> totals = explanation.getConstraintMatchTotalMap();

            List<ConstraintViolationEntry> entries = new ArrayList<>();
            for (var entry : totals.entrySet()) {
                ConstraintMatchTotal<HardSoftScore> total = entry.getValue();
                HardSoftScore totalScore = total.getScore();

                if (totalScore.equals(HardSoftScore.ZERO)) continue;

                String constraintName = total.getConstraintName();
                String level = totalScore.hardScore() != 0 ? "HARD" : "SOFT";

                // weight = configured constraint weight (from ConstraintWeights)
                // totalPenalty = sum of all match scores
                ScheduleSummary.ScoreDto totalPenalty = new ScheduleSummary.ScoreDto(
                        totalScore.hardScore(), totalScore.softScore());

                // Derive per-constraint weight from configured ConstraintWeights
                ScheduleSummary.ScoreDto weight = totalPenalty; // fallback

                List<ViolationDetail> violations = new ArrayList<>();
                for (ConstraintMatch<HardSoftScore> match : total.getConstraintMatchSet()) {
                    UUID agentId = null;
                    String agentName = null;
                    UUID timeslotId = null;
                    String timeslotLabel = null;
                    String specName = null;

                    for (Object justification : match.getIndictedObjectList()) {
                        if (justification instanceof AgentAssignment aa) {
                            if (aa.getAgent() != null) {
                                agentId = aa.getAgent().getId();
                                agentName = aa.getAgent().getName();
                            }
                            timeslotId = aa.getTimeslot().getId();
                            timeslotLabel = aa.getTimeslot().getDate() + " "
                                    + aa.getTimeslot().getStartTime() + "-"
                                    + aa.getTimeslot().getEndTime();
                            if (aa.getRequiredSpecialization() != null) {
                                specName = aa.getRequiredSpecialization().getName();
                            }
                        }
                    }

                    String description;
                    if ("Unassigned assignment".equals(constraintName) && specName != null && timeslotLabel != null) {
                        description = "No agent assigned for " + specName + " at " + timeslotLabel;
                    } else {
                        description = constraintName + " violation" + (agentName != null ? " for " + agentName : "");
                    }

                    violations.add(new ViolationDetail(agentId, agentName, timeslotId, timeslotLabel, description));
                }

                int violationCount = violations.size();
                entries.add(new ConstraintViolationEntry(
                        constraintName, level, weight, violationCount, totalPenalty, violations));
            }

            // Time-related hard constraints surface first for quick visibility.
            Set<String> timeConstraints = Set.of(
                    "Contracted hours (over)",
                    "Contracted hours (under)",
                    "Contracted hours (under, zero)",
                    "Honour preferred start time",
                    "Honour preferred break time",
                    "Break duration",
                    "Break blocked window",
                    "Break start alignment",
                    "Break clustering",
                    "Exactly one break"
            );
            entries.sort(Comparator.comparing(ConstraintViolationEntry::level)
                    .thenComparing((ConstraintViolationEntry e) ->
                            timeConstraints.contains(e.constraintName()) ? 0 : 1)
                    .thenComparing(ConstraintViolationEntry::constraintName));
            return entries;

        } catch (Exception e) {
            log.warn("Constraint explanation failed for schedule {}: {}", schedule.getId(), e.getMessage());
            return List.of();
        }
    }

    // --- Helpers ---

    /**
     * Shift descriptor lookup by (agentId, date) — the ONE place this response's shift descriptor
     * is built, shared by {@link #buildAgentSchedule} and {@link #buildPreferenceReport} (Task 2)
     * so the two builders resolve descriptors identically instead of each holding their own
     * chance to disagree. Covers both the in-memory path (reading the transient
     * {@code AgentShiftAssignment.shiftBandPair}) and the accepted path (reading the D-07
     * denormalised scalar columns) identically. On a slot-scheduled desk
     * {@code schedule.getShiftAssignments()} is structurally empty and this map stays empty —
     * every entry's shift stays null, unchanged from today.
     */
    private Map<UUID, Map<LocalDate, ShiftDescriptor>> buildShiftDescriptorsByAgentDate(Schedule schedule) {
        Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate = new HashMap<>();
        for (AgentShiftAssignment sa : schedule.getShiftAssignments()) {
            if (sa.getAgent() == null) continue;
            ShiftDescriptor descriptor = resolveShiftDescriptor(sa);
            if (descriptor == null) continue;
            shiftDescriptorsByAgentDate
                    .computeIfAbsent(sa.getAgent().getId(), k -> new HashMap<>())
                    .put(sa.getDate(), descriptor);
        }
        return shiftDescriptorsByAgentDate;
    }

    /**
     * The assigned band's window as a single break, derived from the descriptor's offset and
     * duration — never the gap-walking helper (Task 2). Empty when the band offset or duration is
     * absent or non-positive, which is the legitimate no-break template shape P-02 permits.
     */
    private List<BreakDetail> bandBreaks(ShiftDescriptor descriptor) {
        Integer offset = descriptor.bandOffsetMinutes();
        Integer duration = descriptor.bandDurationMinutes();
        if (offset == null || duration == null || duration <= 0) {
            return List.of();
        }
        LocalTime breakStart = descriptor.startTime().plusMinutes(offset);
        LocalTime breakEnd = breakStart.plusMinutes(duration);
        return List.of(new BreakDetail(breakStart, breakEnd, duration));
    }

    /**
     * Divergence between the assigned envelope and the day's actual seats (Task 2), using the one
     * static coverage predicate from {@link com.wfm.model.ShiftBandPair#covers}. A held seat the
     * predicate rejects is out-of-envelope; a legal slot (from the schedule's own timeslots for
     * this date — the agent's own assignment list cannot express a slot they do not hold) the
     * agent does not hold is unworked-legal. Returns {@code null} when both lists are empty so a
     * clean agent-day carries no noise.
     */
    private ShiftEnvelopeDivergence computeDivergence(ShiftDescriptor descriptor,
            List<AgentAssignment> dayAssignments, List<Timeslot> dayTimeslots) {
        LocalTime envelopeStart = descriptor.startTime();
        LocalTime envelopeEnd = descriptor.endTime();
        Integer bandOffset = descriptor.bandOffsetMinutes();
        Integer bandDuration = descriptor.bandDurationMinutes();

        List<LocalTime> outOfEnvelopeSeats = new ArrayList<>();
        Set<UUID> heldTimeslotIds = new HashSet<>();
        for (AgentAssignment a : dayAssignments) {
            Timeslot ts = a.getTimeslot();
            heldTimeslotIds.add(ts.getId());
            boolean legal = ShiftBandPair.covers(envelopeStart, envelopeEnd, bandOffset, bandDuration,
                    ts.getStartTime(), ts.getEndTime());
            if (!legal) {
                outOfEnvelopeSeats.add(ts.getStartTime());
            }
        }

        List<LocalTime> unworkedLegalSlots = new ArrayList<>();
        for (Timeslot ts : dayTimeslots) {
            if (heldTimeslotIds.contains(ts.getId())) continue;
            boolean legal = ShiftBandPair.covers(envelopeStart, envelopeEnd, bandOffset, bandDuration,
                    ts.getStartTime(), ts.getEndTime());
            if (legal) {
                unworkedLegalSlots.add(ts.getStartTime());
            }
        }

        if (outOfEnvelopeSeats.isEmpty() && unworkedLegalSlots.isEmpty()) {
            return null;
        }
        return new ShiftEnvelopeDivergence(outOfEnvelopeSeats, unworkedLegalSlots);
    }

    /**
     * One descriptor shape for both schedule states (Task 2's own done-criterion): a live
     * in-memory row's transient {@code shiftBandPair} — set only while the schedule is still
     * unaccepted — is preferred when present; otherwise the D-07 denormalised scalar columns an
     * accepted row carries are used. A row with neither (an unassigned shift envelope, or a
     * skipped SLOT-mode row that never reaches this method) has no assigned shift.
     */
    private ShiftDescriptor resolveShiftDescriptor(AgentShiftAssignment sa) {
        ShiftBandPair pair = sa.getShiftBandPair();
        if (pair != null) {
            ShiftTemplate template = pair.template();
            ShiftTemplateBreakBand band = pair.band();
            return new ShiftDescriptor(
                    template.getId(), template.getName(), template.getStartTime(), template.getEndTime(),
                    band == null ? null : band.getOffsetMinutes(),
                    band == null ? null : band.getDurationMinutes());
        }
        if (sa.getTemplateName() != null) {
            return new ShiftDescriptor(
                    sa.getSourceTemplateId(), sa.getTemplateName(),
                    sa.getShiftStartTime(), sa.getShiftEndTime(),
                    sa.getBandOffsetMinutes(), sa.getBandDurationMinutes());
        }
        return null;
    }

    private String determineMatchType(Agent da, Specialization requiredSpec) {
        if (da.getPrimarySpecialization() != null
                && da.getPrimarySpecialization().getId().equals(requiredSpec.getId())) {
            return "PRIMARY";
        }
        if (da.getSecondarySpecializations() != null
                && da.getSecondarySpecializations().stream()
                        .anyMatch(s -> s.getId().equals(requiredSpec.getId()))) {
            return "SECONDARY";
        }
        return "NONE";
    }

    private List<BreakDetail> findBreaks(List<AgentAssignment> sortedAssignments) {
        if (sortedAssignments.size() < 2) return List.of();

        List<BreakDetail> breaks = new ArrayList<>();
        for (int i = 0; i < sortedAssignments.size() - 1; i++) {
            LocalTime currentEnd = sortedAssignments.get(i).getTimeslot().getEndTime();
            LocalTime nextStart = sortedAssignments.get(i + 1).getTimeslot().getStartTime();
            if (currentEnd.isBefore(nextStart)) {
                int durationMinutes = (int) ChronoUnit.MINUTES.between(currentEnd, nextStart);
                breaks.add(new BreakDetail(currentEnd, nextStart, durationMinutes));
            }
        }
        return breaks;
    }

    /**
     * Find the actual break start time closest to the preferred break time.
     * Returns null if no breaks exist.
     */
    private LocalTime findClosestBreak(List<BreakDetail> breaks, LocalTime preferredBreak) {
        if (breaks == null || breaks.isEmpty()) return null;
        if (preferredBreak == null) return breaks.get(0).startTime();

        BreakDetail closest = breaks.get(0);
        long minDistance = Math.abs(ChronoUnit.MINUTES.between(closest.startTime(), preferredBreak));
        for (int i = 1; i < breaks.size(); i++) {
            long dist = Math.abs(ChronoUnit.MINUTES.between(breaks.get(i).startTime(), preferredBreak));
            if (dist < minDistance) {
                minDistance = dist;
                closest = breaks.get(i);
            }
        }
        return closest.startTime();
    }

    /**
     * Check if any actual break overlaps with the preferred break timeslot.
     * The preferred break timeslot spans [prefBreak, prefBreak + breakDurationMinutes).
     * An actual break overlaps if its time range intersects the preferred range.
     */
    private boolean breaksOverlapPreferred(List<BreakDetail> breaks, LocalTime prefBreak, int breakDurationMinutes) {
        if (breaks == null || breaks.isEmpty() || prefBreak == null) return false;
        LocalTime prefEnd = prefBreak.plusMinutes(breakDurationMinutes);
        for (BreakDetail bd : breaks) {
            // Overlap: actual break start < preferred end AND actual break end > preferred start
            if (bd.startTime().isBefore(prefEnd) && bd.endTime().isAfter(prefBreak)) {
                return true;
            }
        }
        return false;
    }
}
