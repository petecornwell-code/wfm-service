package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import com.wfm.dto.ScheduleDetailResponse.*;
import com.wfm.dto.ScheduleSummary;
import com.wfm.model.*;
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

        // Group staffing requirements by (date, specName) → sum of requiredHours
        Map<LocalDate, Map<String, BigDecimal>> predicted = new LinkedHashMap<>();
        for (StaffingRequirement sr : schedule.getStaffingRequirements()) {
            predicted
                    .computeIfAbsent(sr.getTimeslot().getDate(), k -> new LinkedHashMap<>())
                    .merge(sr.getSpecialization().getName(), sr.getRequiredHours(), BigDecimal::add);
        }

        // Group assigned (non-null deskAgent) assignments by (date, requiredSpec name) → count
        Map<LocalDate, Map<String, Integer>> actualCounts = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getDeskAgent() == null) continue;
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

        // Group assigned assignments by (deskAgentId, date)
        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new LinkedHashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getDeskAgent() == null) continue;
            grouped
                    .computeIfAbsent(a.getDeskAgent().getId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())
                    .add(a);
        }

        List<AgentScheduleEntry> entries = new ArrayList<>();
        for (var agentEntry : grouped.entrySet()) {
            for (var dateEntry : agentEntry.getValue().entrySet()) {
                List<AgentAssignment> dayAssignments = dateEntry.getValue();
                dayAssignments.sort(Comparator.comparing(a -> a.getTimeslot().getStartTime()));

                AgentAssignment first = dayAssignments.get(0);
                DeskAgent da = first.getDeskAgent();
                UUID agentId = da.getAgent().getId();
                String agentName = da.getAgent().getName();
                LocalDate date = dateEntry.getKey();

                LocalTime shiftStart = first.getTimeslot().getStartTime();
                LocalTime shiftEnd = dayAssignments.get(dayAssignments.size() - 1).getTimeslot().getEndTime();
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

                // Find breaks — gaps within shift span
                List<BreakDetail> breaks = findBreaks(dayAssignments);

                entries.add(new AgentScheduleEntry(
                        agentId, agentName, date, shiftStart, shiftEnd,
                        totalHours, details, breaks));
            }
        }

        entries.sort(Comparator.comparing(AgentScheduleEntry::agentName)
                .thenComparing(AgentScheduleEntry::date));
        return entries;
    }

    /**
     * 8.3 Preference Report — per-agent per-day preference resolution and honour flags.
     */
    public PreferenceReport buildPreferenceReport(Schedule schedule) {
        // Group assignments by agent + date, compute actual start times and breaks
        Map<UUID, Map<LocalDate, LocalTime>> actualStartTimes = new HashMap<>();
        Map<UUID, Map<LocalDate, List<BreakDetail>>> actualBreaks = new HashMap<>();

        Map<UUID, Map<LocalDate, List<AgentAssignment>>> grouped = new HashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getDeskAgent() == null) continue;
            UUID agentId = a.getDeskAgent().getAgent().getId();
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

                List<BreakDetail> breaks = findBreaks(dayAssignments);
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
                            if (aa.getDeskAgent() != null) {
                                agentId = aa.getDeskAgent().getAgent().getId();
                                agentName = aa.getDeskAgent().getAgent().getName();
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

            entries.sort(Comparator.comparing(ConstraintViolationEntry::level)
                    .thenComparing(ConstraintViolationEntry::constraintName));
            return entries;

        } catch (Exception e) {
            // If constraint explanation fails (e.g. incomplete solution), return empty
            return List.of();
        }
    }

    // --- Helpers ---

    private String determineMatchType(DeskAgent da, Specialization requiredSpec) {
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
