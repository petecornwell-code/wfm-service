package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import com.wfm.dto.ScheduleDetailResponse.*;
import com.wfm.dto.ScheduleSummary;
import com.wfm.model.*;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.solver.ScheduleConstraintProvider;
import com.wfm.util.DayWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
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
    private final UsualShiftResolutionService usualShiftResolutionService;
    private final AgentUsualShiftRepository agentUsualShiftRepository;

    public ScheduleOutputService(SolverFactory<Schedule> solverFactory,
            UsualShiftResolutionService usualShiftResolutionService,
            AgentUsualShiftRepository agentUsualShiftRepository) {
        this.solutionManager = SolutionManager.create(solverFactory);
        this.usualShiftResolutionService = usualShiftResolutionService;
        this.agentUsualShiftRepository = agentUsualShiftRepository;
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
                    .computeIfAbsent(sr.getTimeslot().getBusinessDate(), k -> new LinkedHashMap<>())
                    .merge(sr.getSpecialization().getName(), fteHours, BigDecimal::add);
        }

        // Group assigned (non-null agent) assignments by (date, requiredSpec name) → count
        Map<LocalDate, Map<String, Integer>> actualCounts = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            actualCounts
                    .computeIfAbsent(a.getTimeslot().getBusinessDate(), k -> new LinkedHashMap<>())
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
        // BDAY-04 (plan 19-05): one window per public method, bound from the Schedule this
        // method already receives — same accessor style as consistencyToleranceMinutes() above.
        DayWindow window = DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart());

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
            timeslotsByDate.computeIfAbsent(ts.getBusinessDate(), k -> new ArrayList<>()).add(ts);
        }

        // Group assigned assignments by (agentId, date)
        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            grouped
                    .computeIfAbsent(a.getAgent().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.getTimeslot().getBusinessDate(), k -> new ArrayList<>())
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
                    breaks = bandBreaks(shiftDescriptor, window);
                    List<Timeslot> dayTimeslots = timeslotsByDate.getOrDefault(date, List.of());
                    divergence = computeDivergence(shiftDescriptor, dayAssignments, dayTimeslots, window);
                } else {
                    // No descriptor: a slot desk, or a shift-mode agent-day the solver left
                    // unassigned. Keep today's behaviour exactly — seat-derived span, gap-derived
                    // breaks, no divergence.
                    shiftStart = first.getTimeslot().getStartTime();
                    shiftEnd = dayAssignments.get(dayAssignments.size() - 1).getTimeslot().getEndTime();
                    breaks = findBreaks(dayAssignments, window);
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
        // BDAY-04 (plan 19-06): one window per public method, bound from the Schedule this method
        // already receives — same accessor style buildAgentSchedule and buildConstraintViolations
        // use (plan 19-05).
        DayWindow window = DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart());

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
                    .computeIfAbsent(a.getTimeslot().getBusinessDate(), k -> new ArrayList<>())
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
                        ? bandBreaks(descriptor, window)
                        : findBreaks(dayAssignments, window);
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
                // OVNT-02/plan 21-12: compared as START boundaries through the anchored minute,
                // not raw isBefore -- on a desk anchored away from midnight, two start times
                // compared raw order by CLOCK, not by position in the business day, so this
                // preference-report verdict would invert. At a 00:00 anchor the anchored minute
                // equals the clock minute, so this is a no-op on every desk that exists today.
                startOk = actStart != null
                        && window.anchoredStartMinute(actStart) >= window.anchoredStartMinute(prefStart);
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
                        prefBreak, schedule.getBreakDurationMinutes(), window);
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
     * Phase 17's drift report (DRFT-01…04, D-11) — derived on read, alongside
     * {@link #buildPreferenceReport}: resolves each shift-scheduled agent-day's stored usual
     * shift through {@link UsualShiftResolutionService#resolve} at READ time (never from a solve
     * -time snapshot), so editing a stored usual shift after the fact moves a past schedule's
     * drift report too — D-11's accepted, deliberate consequence, coherent with Phase 16 D-01's
     * "Ana's usual shift is Early follows Early across eras".
     *
     * <p>Classification is the explicit {@link DriftStatus} field, never inferred from a null
     * {@code usualStartTime} (D-12). {@code NO_USUAL_SHIFT} covers both "no stored row for this
     * weekday" and "stored row resolves to no template for this date" — Phase 16 D-01/D-02 makes
     * these identical states.
     *
     * <p>Task 2 (TDD) sorts entries DATE ascending, then agent name ascending — a DELIBERATE
     * DIVERGENCE from {@link #buildPreferenceReport}'s agent-then-date order: {@code
     * 17-UI-SPEC.md}'s Component Specifications §1 fixes date-major server order for the drift
     * tab, and the table trusts server order rather than re-sorting.
     *
     * <p>{@code deltaMinutes} is populated only on {@code DRIFTED} rows and is SIGNED — positive
     * when the assigned envelope start is later than the usual start, negative when earlier —
     * with {@link ShiftBandPair#startDeviationMinutes} supplying the unsigned magnitude and this
     * method supplying the sign. That shared magnitude is DRFT-03's whole point: this report and
     * {@code ScheduleConstraintProvider.usualShiftConsistency} can never disagree about how far an
     * agent-day drifted, because both call the one static method.
     *
     * <p>{@code popularity} (DRFT-04, D-13) answers a DIFFERENT question from the rest of this
     * report: it counts, per shift template, how many DISTINCT agents currently hold it as a
     * usual shift — read from the same {@code allUsualShifts} fetch above (the tenant-and-desk
     * -scoped finder), resolved through {@link UsualShiftResolutionService#resolve} at "today" so
     * an era-renamed template ranks under the name the operator currently sees (Phase 16 D-01).
     * It is NOT derived from this solve's {@code AgentShiftAssignment} rows, so it does not change
     * when the desk is re-solved and is unaffected by {@code ScheduleService}'s date filter — that
     * is why {@code 17-UI-SPEC.md} gives it its own section heading and subtext rather than
     * folding it into the main table.
     */
    public DriftReport buildDriftReport(Schedule schedule) {
        // BDAY-04/plan 21-12: one window per public method, bound from the Schedule this method
        // already receives -- same accessor style buildAgentSchedule/buildPreferenceReport use
        // (plan 19-05/19-06). Needed so the drift-sign ternary below can compare the assigned
        // envelope start and the usual start by anchored position rather than raw clock order.
        DayWindow window = DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart());

        List<AgentUsualShift> allUsualShifts = agentUsualShiftRepository
                .findByTenantIdAndDeskId(schedule.getTenantId(), schedule.getDeskId());
        Map<UUID, Map<DayOfWeek, AgentUsualShift>> byAgentAndWeekday = new HashMap<>();
        for (AgentUsualShift u : allUsualShifts) {
            byAgentAndWeekday.computeIfAbsent(u.getAgent().getId(), k -> new HashMap<>())
                    .put(u.getDayOfWeek(), u);
        }

        // Read the SAME value the solver read (Schedule.getScheduleConfig(), which falls back to
        // ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES when constraintWeights is absent)
        // so the report's HONOURED/DRIFTED line can never disagree with what the constraint
        // actually penalised on this schedule.
        int toleranceMinutes = schedule.getScheduleConfig().consistencyToleranceMinutes();

        List<DriftReportEntry> entries = new ArrayList<>();
        int noUsualShiftCount = 0;
        int honouredCount = 0;
        int driftedCount = 0;

        for (AgentShiftAssignment sa : schedule.getShiftAssignments()) {
            if (sa.getAgent() == null) continue;
            ShiftDescriptor descriptor = resolveShiftDescriptor(sa);
            if (descriptor == null) {
                // The shift envelope itself was left unassigned (allowsUnassigned) — there is no
                // actual start time to report drift against, so this agent-day contributes no
                // entry. Structurally rare: shiftEnvelopeCompliance's hard weight drives a
                // feasible solve toward assigning every working agent-day a shift.
                continue;
            }
            UUID agentId = sa.getAgent().getId();
            LocalDate date = sa.getDate();
            LocalTime actualStartTime = descriptor.startTime();

            AgentUsualShift stored = byAgentAndWeekday
                    .getOrDefault(agentId, Map.of()).get(date.getDayOfWeek());
            Optional<ShiftTemplate> resolvedTemplate = stored == null
                    ? Optional.empty()
                    : usualShiftResolutionService.resolve(stored, date);

            DriftStatus status;
            LocalTime usualStartTime;
            Integer deltaMinutes;
            if (resolvedTemplate.isEmpty()) {
                status = DriftStatus.NO_USUAL_SHIFT;
                usualStartTime = null;
                deltaMinutes = null;
            } else {
                usualStartTime = resolvedTemplate.get().getStartTime();
                // The ONE distance calculation (DRFT-03) — never re-derived inline here. The sign
                // is this method's own addition on top of the shared magnitude: positive when the
                // assigned envelope start is later than the usual start, negative when earlier,
                // zero (impossible once deviation > 0) otherwise.
                int magnitude = ShiftBandPair.startDeviationMinutes(actualStartTime, usualStartTime);
                if (magnitude > toleranceMinutes) {
                    status = DriftStatus.DRIFTED;
                    // OVNT-02/plan 21-12: compared as START boundaries through the anchored
                    // minute, not raw isAfter/isBefore -- on a desk anchored away from midnight,
                    // two start times compared raw order by CLOCK, not by position in the
                    // business day, so the drift sign would invert (late reads as early). At a
                    // 00:00 anchor the anchored minute equals the clock minute, so this is a
                    // no-op on every desk that exists today.
                    int actualAnchored = window.anchoredStartMinute(actualStartTime);
                    int usualAnchored = window.anchoredStartMinute(usualStartTime);
                    int sign = actualAnchored > usualAnchored ? 1
                            : actualAnchored < usualAnchored ? -1 : 0;
                    deltaMinutes = sign * magnitude;
                } else {
                    status = DriftStatus.HONOURED;
                    deltaMinutes = null;
                }
            }

            switch (status) {
                case NO_USUAL_SHIFT -> noUsualShiftCount++;
                case HONOURED -> honouredCount++;
                case DRIFTED -> driftedCount++;
            }

            entries.add(new DriftReportEntry(agentId, sa.getAgent().getName(), date, status,
                    usualStartTime, actualStartTime, deltaMinutes));
        }

        // Task 2 (TDD): date ascending, then agent name ascending -- a DELIBERATE DIVERGENCE from
        // buildPreferenceReport's agent-then-date order. 17-UI-SPEC.md's Component Specifications
        // §1 fixes date-major server order for the drift tab, and the table trusts server order
        // rather than re-sorting.
        entries.sort(Comparator.comparing(DriftReportEntry::date)
                .thenComparing(DriftReportEntry::agentName));

        int workingAgentDays = noUsualShiftCount + honouredCount + driftedCount;
        DriftSummary summary = new DriftSummary(workingAgentDays, noUsualShiftCount, honouredCount, driftedCount);

        // Popularity ranking (DRFT-04, D-13) -- reuses allUsualShifts already fetched above (the
        // tenant-and-desk-scoped finder), never a second repository read (T-17-05). Counts
        // DISTINCT AGENTS per resolved template name, not stored rows: an agent whose usual
        // shift is the same template on five weekdays contributes one to that template, because
        // each weekday's row is deduplicated into a per-template Set<UUID> of agent ids.
        // Resolved at "today" (LocalDate.now()) -- the same era-following precedent as
        // DeskAgentService.toResponse's isLive check -- so an era-renamed template ranks under
        // the name the operator currently sees, not a stale pointer's name.
        LocalDate today = LocalDate.now();
        Map<String, Set<UUID>> agentIdsByTemplateName = new HashMap<>();
        for (AgentUsualShift u : allUsualShifts) {
            usualShiftResolutionService.resolve(u, today).ifPresent(t ->
                    agentIdsByTemplateName.computeIfAbsent(t.getName(), k -> new HashSet<>())
                            .add(u.getAgent().getId()));
        }
        List<ShiftPopularityEntry> popularity = agentIdsByTemplateName.entrySet().stream()
                .map(e -> new ShiftPopularityEntry(e.getKey(), e.getValue().size()))
                .sorted(Comparator.comparingInt(ShiftPopularityEntry::agentCount).reversed()
                        .thenComparing(ShiftPopularityEntry::templateName))
                .toList();

        return new DriftReport(entries, summary, popularity);
    }

    /**
     * 8.4 Constraint Violations — grouped by constraint name.
     *
     * <p>{@code isAcceptedSnapshot} is the caller's own knowledge of provenance —
     * {@link ScheduleService#getScheduleDetail}'s {@code fromDb} local, threaded through rather
     * than inferred (G-15-32 gap closure). On the LIVE-SOLVER path ({@code false}) this uses the
     * cached Timefold {@link SolutionManager} to explain the score, unchanged from before. On the
     * ACCEPTED/DB path ({@code true}) it NEVER calls {@code solutionManager.explain} — explaining
     * a schedule whose shift problem facts are not reconstituted makes every held seat fail the
     * coverage predicate (this file's own discriminator identity for a null band pair), which is
     * exactly where the constant 1104 (= 138 agent-days x 8 contracted hours, every staffed seat)
     * came from. Instead it delegates to {@link #buildAcceptedConstraintViolations}, which derives
     * the report from the persisted snapshot the schedule already carries — the same
     * {@link #resolveShiftDescriptor} and {@link #computeDivergence} the accepted path of
     * {@link #buildAgentSchedule} already calls, so envelope compliance can never disagree between
     * the two views (D-08 one-predicate discipline).
     *
     * <p>Only envelope compliance is derivable this way: the persisted snapshot carries the
     * envelope and band scalars, so envelope compliance is derivable and IS derived on the
     * accepted path. Every other constraint's problem facts are not reconstituted at accept time,
     * so nothing is reported for them there — reporting nothing for a constraint whose ground
     * truth is absent is the honest outcome; reporting it from a mis-explained score director is
     * what produced this gap. Shift-work contiguity is ALSO derivable from the same snapshot
     * (worked slots plus the descriptor's break window) but is DELIBERATELY NOT derived here: its
     * structural walker currently exists only in test source
     * ({@code SolverQualityGuardTest.findSplitShifts}), and promoting it to main source needs its
     * own guard — a named, routable limitation rather than a silent omission.
     */
    @SuppressWarnings("removal")
    public List<ConstraintViolationEntry> buildConstraintViolations(Schedule schedule, boolean isAcceptedSnapshot) {
        if (schedule.getAssignments() == null || schedule.getAssignments().isEmpty()) {
            return List.of();
        }

        if (isAcceptedSnapshot) {
            // BDAY-04 (plan 19-05): one window per public method, bound from the Schedule this
            // method already receives.
            return buildAcceptedConstraintViolations(schedule,
                    DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart()));
        }

        // Secondary safety net for the LIVE-SOLVER path only (demoted from being the
        // provenance discriminator, G-15-32): an accepted schedule DOES carry
        // ConstraintWeights — ScheduleService.loadSnapshotData loads them from
        // constraintWeightsRepository, which is precisely how every accepted schedule used to
        // reach the explain() call below. This null check can therefore never again stand in
        // for "is this an accepted schedule" — provenance is now the explicit parameter above.
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
                    // OVNT-02/D-14: the structured attribution channel the label used to be the
                    // only way to recover. businessDate is the key every consumer should group
                    // by; calendarDate is what the label below displays.
                    LocalDate businessDate = null;
                    LocalDate calendarDate = null;
                    LocalTime slotStartTime = null;
                    LocalTime slotEndTime = null;
                    String timeslotLabel = null;
                    String specName = null;

                    for (Object justification : match.getIndictedObjectList()) {
                        if (justification instanceof AgentAssignment aa) {
                            if (aa.getAgent() != null) {
                                agentId = aa.getAgent().getId();
                                agentName = aa.getAgent().getName();
                            }
                            Timeslot ts = aa.getTimeslot();
                            timeslotId = ts.getId();
                            businessDate = ts.getBusinessDate();
                            calendarDate = ts.getDate();
                            slotStartTime = ts.getStartTime();
                            slotEndTime = ts.getEndTime();
                            // OVNT-07/D-14: built by the one shared helper below, called from both
                            // violation paths, so the live path and the accepted path can never
                            // silently drift into producing different labels for the same slot.
                            // No consumer recovers data from this string any longer: the export's
                            // unfilled-seat attribution (ScheduleExportService) and the schedule
                            // grid's unfilled-seat map (ScheduleResults.tsx) both read
                            // ViolationDetail's structured businessDate/calendarDate/startTime/
                            // endTime fields directly (21-03, 21-10) — the channel that replaced
                            // this one.
                            timeslotLabel = timeslotLabel(ts);
                            if (aa.getRequiredSpecialization() != null) {
                                specName = aa.getRequiredSpecialization().getName();
                            }
                        } else if (justification instanceof Timeslot ts && timeslotId == null) {
                            // #14 (WINDOWS.md): a groupBy/join/join/filter aggregate constraint
                            // (e.g. "Unassigned assignment" -- groupBy(a -> a.getTimeslot(), ...)
                            // .join(TimeslotDemandConfig.class, ...).join(ScheduleConfig.class))
                            // never indicts an individual AgentAssignment; its ConstraintMatch
                            // indicts the Timeslot group key directly instead. Without this
                            // branch every one of its violations carried null businessDate/
                            // calendarDate/startTime/endTime end-to-end, so the operator-facing
                            // unfilled-seat marker (ScheduleResults.tsx:391-402, keyed on
                            // businessDate|startTime) could never render. The Timeslot IS already
                            // indicted -- the solver was never withholding it -- this loop simply
                            // didn't know how to read it; fixed read-side, not by reshaping
                            // ScheduleConstraintProvider's stream.
                            //
                            // Guarded on timeslotId == null so an AgentAssignment's own values
                            // (richer -- carries agent identity) always take precedence if a
                            // match ever indicts both; today no constraint in this codebase does.
                            //
                            // specName is deliberately left null here: the other indicted fact
                            // for this constraint, TimeslotDemandConfig, carries no specialization
                            // field (record TimeslotDemandConfig(Timeslot, int) -- no spec), so it
                            // cannot be derived and must never be fabricated or guessed from
                            // elsewhere in the schedule. The description below therefore falls
                            // through to the generic "<constraintName> violation" form for this
                            // constraint -- only the structured fields are fixed here.
                            timeslotId = ts.getId();
                            businessDate = ts.getBusinessDate();
                            calendarDate = ts.getDate();
                            slotStartTime = ts.getStartTime();
                            slotEndTime = ts.getEndTime();
                            timeslotLabel = timeslotLabel(ts);
                        }
                    }

                    String description;
                    if ("Unassigned assignment".equals(constraintName) && specName != null && timeslotLabel != null) {
                        description = "No agent assigned for " + specName + " at " + timeslotLabel;
                    } else {
                        description = constraintName + " violation" + (agentName != null ? " for " + agentName : "");
                    }

                    violations.add(new ViolationDetail(agentId, agentName, timeslotId, businessDate,
                            calendarDate, slotStartTime, slotEndTime, timeslotLabel, description));
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

    /**
     * The one place both violation-reporting paths build the operator-facing timeslot label
     * (OVNT-07/D-14) — called from both {@link #buildConstraintViolations}'s live-path loop and
     * {@link #buildAcceptedConstraintViolations}, so the two paths can never silently drift into
     * producing different labels for the same slot.
     *
     * <p>The calendar date stays in the leading position, byte-identical to today, because that
     * is what an operator needs to find the row on a calendar — the reason the label kept the
     * calendar date through Phase 20 (D-10). When the slot's business-day attribution differs
     * from its calendar date — a {@code 21:00}-anchored desk's {@code 02:00} slot belongs to
     * business day D but occurs on calendar day D+1 — the business date is disclosed as an
     * explicit, labelled suffix so the two can never be confused for each other. At a
     * {@code 00:00} anchor, or any slot whose business and calendar dates agree, this returns
     * exactly what was produced before this method existed.
     *
     * <p>No consumer recovers data from this string any longer (D-14's precondition, confirmed
     * before this method's text changed): the export's unfilled-seat attribution
     * ({@code ScheduleExportService.unfilledSeatsByDateAndSlot}, 21-03) and the schedule grid's
     * unfilled-seat map ({@code ScheduleResults.tsx}, 21-10) both read {@link ViolationDetail}'s
     * structured {@code businessDate}/{@code calendarDate}/{@code startTime}/{@code endTime}
     * fields directly — the channel that replaced this one.
     */
    private static String timeslotLabel(Timeslot ts) {
        LocalDate calendarDate = ts.getDate();
        LocalDate businessDate = ts.getBusinessDate();
        String plain = calendarDate + " " + ts.getStartTime() + "-" + ts.getEndTime();
        if (businessDate == null || businessDate.equals(calendarDate)) {
            return plain;
        }
        return plain + " (business day: " + businessDate + ")";
    }

    /**
     * The accepted/DB-path violation report (G-15-32 gap closure) — built from the persisted
     * snapshot, never from {@code solutionManager.explain}. Walks every agent-day exactly as
     * {@link #buildAgentSchedule} does, resolving each descriptor through the same
     * {@link #resolveShiftDescriptor} and finding out-of-envelope seats through the same
     * {@link #outOfEnvelopeAssignments} walk {@link #computeDivergence} already performs — no
     * second definition of "covered" (D-08). Emits exactly one HARD
     * {@link ConstraintViolationEntry} aggregating every out-of-envelope seat across every
     * agent-day, or an empty list when there are none (matching the live path's existing
     * "skip a zero total" behaviour) — a clean accepted schedule therefore reports an empty list
     * because it was computed, not because the path was disabled.
     */
    private List<ConstraintViolationEntry> buildAcceptedConstraintViolations(Schedule schedule, DayWindow window) {
        Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate =
                buildShiftDescriptorsByAgentDate(schedule);
        if (shiftDescriptorsByAgentDate.isEmpty()) {
            return List.of();
        }

        // Group assigned assignments by (agentId, date) — mirrors buildAgentSchedule's own
        // grouping exactly, so the two views walk identical agent-days.
        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null) continue;
            grouped
                    .computeIfAbsent(a.getAgent().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.getTimeslot().getBusinessDate(), k -> new ArrayList<>())
                    .add(a);
        }

        List<ViolationDetail> violations = new ArrayList<>();
        for (var agentEntry : grouped.entrySet()) {
            UUID agentId = agentEntry.getKey();
            Map<LocalDate, ShiftDescriptor> agentDescriptors =
                    shiftDescriptorsByAgentDate.getOrDefault(agentId, Map.of());

            for (var dateEntry : agentEntry.getValue().entrySet()) {
                ShiftDescriptor descriptor = agentDescriptors.get(dateEntry.getKey());
                // No descriptor: a shift-mode agent-day the solver left unassigned (acceptSchedule
                // never writes a row when shiftBandPair is null). Nothing to check — mirrors
                // buildAgentSchedule's own fallback branch, which reports no divergence either.
                if (descriptor == null) continue;

                for (AgentAssignment out : outOfEnvelopeAssignments(descriptor, dateEntry.getValue(), window)) {
                    Agent agent = out.getAgent();
                    Timeslot ts = out.getTimeslot();
                    // OVNT-07/D-14: built by the one shared helper below, called from both
                    // violation paths, so the live path and the accepted path can never silently
                    // drift into producing different labels for the same slot. No consumer
                    // recovers data from this string any longer: the export's unfilled-seat
                    // attribution (ScheduleExportService) and the schedule grid's unfilled-seat
                    // map (ScheduleResults.tsx) both read ViolationDetail's structured
                    // businessDate/calendarDate/startTime/endTime fields directly (21-03, 21-10) —
                    // the channel that replaced this one.
                    String timeslotLabel = timeslotLabel(ts);
                    // OVNT-02/D-14: the structured attribution channel the label above used to be
                    // the only way to recover. businessDate is the key every consumer should group
                    // by; calendarDate is what the label displays.
                    LocalDate violationBusinessDate = ts.getBusinessDate();
                    LocalDate violationCalendarDate = ts.getDate();
                    LocalTime violationStartTime = ts.getStartTime();
                    LocalTime violationEndTime = ts.getEndTime();
                    String description = ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME
                            + " violation" + (agent != null ? " for " + agent.getName() : "");
                    violations.add(new ViolationDetail(
                            agentId, agent != null ? agent.getName() : null, ts.getId(),
                            violationBusinessDate, violationCalendarDate,
                            violationStartTime, violationEndTime, timeslotLabel,
                            description));
                }
            }
        }

        if (violations.isEmpty()) {
            return List.of();
        }

        ConstraintWeights weights = schedule.getConstraintWeights();
        HardSoftScore envelopeWeight = weights != null
                ? weights.getShiftEnvelopeComplianceWeight()
                : HardSoftScore.ZERO;
        int violationCount = violations.size();
        ScheduleSummary.ScoreDto weightDto = new ScheduleSummary.ScoreDto(
                envelopeWeight.hardScore(), envelopeWeight.softScore());
        ScheduleSummary.ScoreDto totalPenalty = new ScheduleSummary.ScoreDto(
                envelopeWeight.hardScore() * violationCount, envelopeWeight.softScore() * violationCount);

        return List.of(new ConstraintViolationEntry(
                ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME,
                "HARD", weightDto, violationCount, totalPenalty, violations));
    }

    /**
     * REST-07/D-09/D-13 — the applied and unused rest-waiver sections, computed deterministically
     * from this solution's own rows on BOTH the live and accepted paths.
     *
     * <p><b>Never the solver's score-explanation channel — two independent reasons.</b> A waived
     * pair is legal to the solver by construction ({@code minimumRestShift}/{@code
     * minimumRestSlot}'s {@code ifNotExists(AgentRestWaiver.class, ...)} exclusion clause), so it
     * produces no {@code ConstraintMatch} at all — there is nothing there to read even on the live
     * path. And the accepted path ({@code isAcceptedSnapshot == true}) never calls {@code
     * solutionManager.explain} in the first place — see {@link #buildConstraintViolations}'s own
     * javadoc for the identical argument on the general violation report, and {@link
     * #buildAcceptedConstraintViolations} for the gap a prior phase already hit and closed by
     * deriving from the persisted snapshot instead. This method follows that same precedent: it
     * reads only {@code schedule}'s own rows (assignments, shift assignments, waivers and
     * pre-horizon spans), never {@code solutionManager}.
     *
     * <p><b>{@code isAcceptedSnapshot} is threaded from the caller</b> exactly as {@link
     * #buildConstraintViolations} already does — never inferred from whether some other field
     * happens to be populated, which is the proxy that has already broken once in this codebase
     * (G-15-32). It is unused by this method's OWN logic: the live-vs-accepted shift envelope
     * split is already handled, uniformly, by {@link #resolveShiftDescriptor} (reused here rather
     * than re-derived), so there is nothing left for this method itself to branch on. Kept in the
     * signature for parity with the rest of this class's provenance-threading convention and so a
     * future need for it does not require a second signature change.
     *
     * <p><b>The required gap is read once, from the schedule's own snapshot, never a desk.</b>
     * {@code schedule.getMinimumRestMinutes()} is part of what this schedule was actually measured
     * against (D-14) — reading a desk's live value here would let a later desk edit silently
     * rewrite what an already-solved or already-accepted schedule is reported to have been
     * measured against. This method holds no {@code DeskRepository} reference at all, so there is
     * nothing to retrofit that read onto by accident.
     *
     * <p><b>Unused is not merely "not applied".</b> Every waiver whose date falls inside this
     * schedule's inclusive period and does not match a sub-minimum pair reaches the identical
     * {@code unused} section, regardless of WHY it did not match: adequate rest, a day off, an
     * unrostered agent, or no predecessor at all. All four are structurally identical here — a
     * null predecessor span, a null successor span, or a measured gap at or above the minimum —
     * and are never branched into separate messages (D-09's contract is two sections, not four).
     *
     * <p><b>Period-scoped, deliberately (22-RESEARCH.md Discretion Resolution 4).</b> A waiver
     * outside {@code [schedule.getPeriodStartDate(), schedule.getPeriodEndDate()]} is skipped
     * entirely — this disclosure is computed from THIS solution, and a desk-wide listing would mix
     * in waivers this schedule's period says nothing about.
     */
    public RestWaiverDisclosure buildRestWaiverDisclosure(Schedule schedule, boolean isAcceptedSnapshot) {
        Integer requiredGapMinutes = schedule.getMinimumRestMinutes();
        if (requiredGapMinutes == null) {
            return new RestWaiverDisclosure(List.of(), List.of());
        }

        // WR-01 (22-REVIEW-DISPOSITION.md, closed by plan 22-11 Task 2): the codebase's
        // null-coalescing anchor shape, identical to ScheduleConstraintProvider.resolveAnchor and
        // ShiftLibraryGenerationService's own in-service idiom — not a raw schedule.getDayStart()
        // read. Two reasons, both load-bearing: (i) a null anchor on a schedule carrying a
        // non-null snapshotted minimum rest is not reachable through the live or accept paths
        // today, since the anchor column (V54) landed in an earlier migration than the rest
        // column (V55) — so this is a defensive same-as-before default, not a second anchor rule
        // (Rule 2), exactly as the code review recorded; (ii) the set of Schedule instances
        // reaching this method is about to widen — plan 22-12 begins calling it for DB-fetched
        // schedules on the list and summary paths, which never run loadSnapshotData, and
        // DayWindow.anchoredAt throws NullPointerException on a null anchor, so an unmigrated or
        // partially-populated Schedule would surface as a 500 on a list endpoint rather than as a
        // degraded (midnight-anchored) waiver report. No DeskRepository read is introduced — this
        // method and this class hold none, deliberately.
        LocalTime dayStart = schedule.getDayStart() != null ? schedule.getDayStart() : LocalTime.MIDNIGHT;

        // Per-(agent, business date) span the agent actually holds in THIS solution — one source
        // per scheduling mode, mirroring minimumRestShift/minimumRestSlot's own span derivation
        // exactly (D-02/D-03), never a second one invented for this report.
        Map<AgentDateKey, RestSpan> inHorizonSpans = new HashMap<>();
        if (schedule.getSchedulingMode() == SchedulingMode.SHIFT) {
            for (AgentShiftAssignment sa : schedule.getShiftAssignments()) {
                if (sa.getAgent() == null) continue;
                // Same live-vs-accepted split every other builder in this class already uses —
                // the transient shiftBandPair when present, the D-07 denormalised scalars
                // otherwise. A row with neither (an unassigned shift envelope) contributes no
                // span, matching buildAgentSchedule's own fallback.
                ShiftDescriptor descriptor = resolveShiftDescriptor(sa);
                if (descriptor == null) continue;
                RestSpan span = new RestSpan(sa.getAgent().getId(), sa.getDate(),
                        descriptor.startTime(), descriptor.endTime(), dayStart);
                inHorizonSpans.put(new AgentDateKey(span.agentId(), span.businessDate()), span);
            }
        } else {
            Map<AgentDateKey, List<AgentAssignment>> grouped = new LinkedHashMap<>();
            for (AgentAssignment a : schedule.getAssignments()) {
                if (a.getAgent() == null) continue;
                grouped.computeIfAbsent(
                        new AgentDateKey(a.getAgent().getId(), a.getTimeslot().getBusinessDate()),
                        k -> new ArrayList<>()).add(a);
            }
            for (var entry : grouped.entrySet()) {
                AgentDateKey key = entry.getKey();
                inHorizonSpans.put(key, RestSpan.ofSlots(key.agentId(), key.date(), entry.getValue(), dayStart));
            }
        }

        // Predecessor candidates widen with the pre-horizon spans (REST-05/D-10) so a waiver on
        // the period's FIRST business date can still find its predecessor. The successor side
        // (inHorizonSpans, above) never widens this way — a pre-horizon span is never itself a
        // reportable waiver target (D-11's one-directional lookback).
        Map<AgentDateKey, RestSpan> predecessorSpans = new HashMap<>(inHorizonSpans);
        for (RestSpan prior : schedule.getPriorRestSpans()) {
            predecessorSpans.put(new AgentDateKey(prior.agentId(), prior.businessDate()), prior);
        }

        List<ViolationCandidate> appliedCandidates = new ArrayList<>();
        List<ViolationCandidate> unusedCandidates = new ArrayList<>();

        for (AgentRestWaiver waiver : schedule.getAgentRestWaivers()) {
            LocalDate waiverDate = waiver.getDate();
            if (waiverDate == null
                    || waiverDate.isBefore(schedule.getPeriodStartDate())
                    || waiverDate.isAfter(schedule.getPeriodEndDate())) {
                continue;
            }
            Agent agent = waiver.getAgent();
            if (agent == null || agent.getId() == null) {
                continue;
            }
            UUID agentId = agent.getId();
            LocalDate priorBusinessDate = waiverDate.minusDays(1);

            RestSpan next = inHorizonSpans.get(new AgentDateKey(agentId, waiverDate));
            RestSpan prev = predecessorSpans.get(new AgentDateKey(agentId, priorBusinessDate));

            if (prev != null && next != null) {
                int measuredGapMinutes = RestSpan.gapMinutes(prev, next);
                RestWaiverEntry entry = new RestWaiverEntry(agentId, agent.getName(),
                        priorBusinessDate, waiverDate, prev.endTime(), next.startTime(),
                        measuredGapMinutes, requiredGapMinutes, waiver.getReason());
                if (measuredGapMinutes < requiredGapMinutes) {
                    appliedCandidates.add(new ViolationCandidate(waiverDate, agent.getName(), entry));
                } else {
                    unusedCandidates.add(new ViolationCandidate(waiverDate, agent.getName(), entry));
                }
            } else {
                // One of the three remaining inert causes — a day off, an unrostered agent, or no
                // predecessor at all — reaching the same unused section through this one path.
                RestWaiverEntry entry = new RestWaiverEntry(agentId, agent.getName(),
                        priorBusinessDate, waiverDate, null, null, null, requiredGapMinutes,
                        waiver.getReason());
                unusedCandidates.add(new ViolationCandidate(waiverDate, agent.getName(), entry));
            }
        }

        // Deterministic order — by business date then agent name — so two renders of the same
        // schedule are byte-identical, matching the sibling report builders' own ordering.
        Comparator<ViolationCandidate> order = Comparator
                .comparing(ViolationCandidate::businessDate)
                .thenComparing(ViolationCandidate::agentName);
        List<RestWaiverEntry> applied = appliedCandidates.stream().sorted(order)
                .map(ViolationCandidate::entry).toList();
        List<RestWaiverEntry> unused = unusedCandidates.stream().sorted(order)
                .map(ViolationCandidate::entry).toList();

        return new RestWaiverDisclosure(applied, unused);
    }

    /** Key for the (agent, business date) span maps {@link #buildRestWaiverDisclosure} builds. */
    private record AgentDateKey(UUID agentId, LocalDate date) {}

    /** Carries the sort key alongside the entry so the comparator need not re-derive it. */
    private record ViolationCandidate(LocalDate businessDate, String agentName, RestWaiverEntry entry) {}

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
    private List<BreakDetail> bandBreaks(ShiftDescriptor descriptor, DayWindow window) {
        Integer offset = descriptor.bandOffsetMinutes();
        Integer duration = descriptor.bandDurationMinutes();
        if (offset == null || duration == null || duration <= 0) {
            return List.of();
        }
        LocalTime breakStart = window.anchoredPlusWithinDay(descriptor.startTime(), offset);
        LocalTime breakEnd = window.anchoredPlusWithinDay(breakStart, duration);
        return List.of(new BreakDetail(breakStart, breakEnd, duration));
    }

    /**
     * The subset of {@code dayAssignments} whose held seat the coverage predicate
     * ({@link ShiftBandPair#covers}) rejects. Factored out of {@link #computeDivergence} (Task 1,
     * G-15-32 gap closure) so the accepted-path violation report
     * ({@link #buildAcceptedConstraintViolations}) can recover the {@link AgentAssignment}
     * identity (agent, timeslot) that {@code computeDivergence}'s {@code LocalTime}-only
     * {@code outOfEnvelopeSeats} list discards — without adding a second definition of "covered"
     * (D-08's one-predicate/two-callers discipline: this is the one walk, both callers use it).
     */
    private List<AgentAssignment> outOfEnvelopeAssignments(ShiftDescriptor descriptor,
            List<AgentAssignment> dayAssignments, DayWindow window) {
        LocalTime envelopeStart = descriptor.startTime();
        LocalTime envelopeEnd = descriptor.endTime();
        Integer bandOffset = descriptor.bandOffsetMinutes();
        Integer bandDuration = descriptor.bandDurationMinutes();

        List<AgentAssignment> outOfEnvelope = new ArrayList<>();
        for (AgentAssignment a : dayAssignments) {
            Timeslot ts = a.getTimeslot();
            boolean legal = ShiftBandPair.covers(envelopeStart, envelopeEnd, bandOffset, bandDuration,
                    ts.getStartTime(), ts.getEndTime(), window);
            if (!legal) {
                outOfEnvelope.add(a);
            }
        }
        return outOfEnvelope;
    }

    /**
     * Divergence between the assigned envelope and the day's actual seats (Task 2), using the one
     * static coverage predicate from {@link com.wfm.model.ShiftBandPair#covers}. A held seat the
     * predicate rejects is out-of-envelope ({@link #outOfEnvelopeAssignments}); a legal slot (from
     * the schedule's own timeslots for this date — the agent's own assignment list cannot express
     * a slot they do not hold) the agent does not hold is unworked-legal. Returns {@code null}
     * when both lists are empty so a clean agent-day carries no noise.
     */
    private ShiftEnvelopeDivergence computeDivergence(ShiftDescriptor descriptor,
            List<AgentAssignment> dayAssignments, List<Timeslot> dayTimeslots, DayWindow window) {
        LocalTime envelopeStart = descriptor.startTime();
        LocalTime envelopeEnd = descriptor.endTime();
        Integer bandOffset = descriptor.bandOffsetMinutes();
        Integer bandDuration = descriptor.bandDurationMinutes();

        List<LocalTime> outOfEnvelopeSeats = new ArrayList<>();
        Set<UUID> heldTimeslotIds = new HashSet<>();
        for (AgentAssignment a : outOfEnvelopeAssignments(descriptor, dayAssignments, window)) {
            outOfEnvelopeSeats.add(a.getTimeslot().getStartTime());
        }
        for (AgentAssignment a : dayAssignments) {
            heldTimeslotIds.add(a.getTimeslot().getId());
        }

        List<LocalTime> unworkedLegalSlots = new ArrayList<>();
        for (Timeslot ts : dayTimeslots) {
            if (heldTimeslotIds.contains(ts.getId())) continue;
            boolean legal = ShiftBandPair.covers(envelopeStart, envelopeEnd, bandOffset, bandDuration,
                    ts.getStartTime(), ts.getEndTime(), window);
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

    private List<BreakDetail> findBreaks(List<AgentAssignment> sortedAssignments, DayWindow window) {
        if (sortedAssignments.size() < 2) return List.of();

        List<BreakDetail> breaks = new ArrayList<>();
        for (int i = 0; i < sortedAssignments.size() - 1; i++) {
            LocalTime currentEnd = sortedAssignments.get(i).getTimeslot().getEndTime();
            LocalTime nextStart = sortedAssignments.get(i + 1).getTimeslot().getStartTime();
            if (window.anchoredEndMinute(currentEnd) < window.anchoredStartMinute(nextStart)) {
                int durationMinutes =
                        window.anchoredStartMinute(nextStart) - window.anchoredEndMinute(currentEnd);
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
    private boolean breaksOverlapPreferred(List<BreakDetail> breaks, LocalTime prefBreak, int breakDurationMinutes,
                                            DayWindow window) {
        if (breaks == null || breaks.isEmpty() || prefBreak == null) return false;
        LocalTime prefEnd = window.anchoredPlusWithinDay(prefBreak, breakDurationMinutes);
        for (BreakDetail bd : breaks) {
            // Overlap: actual break start < preferred end AND actual break end > preferred start
            if (window.anchoredOverlaps(bd.startTime(), bd.endTime(), prefBreak, prefEnd)) {
                return true;
            }
        }
        return false;
    }
}
