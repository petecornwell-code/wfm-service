package com.wfm.solver;

import ai.timefold.solver.core.api.score.stream.*;
import com.wfm.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.*;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.*;
import static ai.timefold.solver.core.api.score.stream.Joiners.*;

public class ScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            agentDayOff(factory),
            specializationMatch(factory),
            oneAssignmentPerTimeslot(factory),
            exactlyOneBreak(factory),
            breakDuration(factory),
            breakBlockedWindow(factory),
            breakStartAlignment(factory),
            preferPrimarySpecialization(factory),
            honourPreferredStartTime(factory),
            honourPreferredBreakTime(factory),
            breakClustering(factory),
            contractedHours(factory),
            bulkOverallocationLimit(factory),
            bulkUnderallocationSoft(factory),
            bulkUnderallocationHard(factory),
        };
    }

    // ============================================================
    //  HARD CONSTRAINTS
    // ============================================================

    /**
     * 1. Agent day off — agent must not be assigned on a day they have off.
     */
    private Constraint agentDayOff(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .join(AgentDayOff.class,
                        equal(a -> a.getDeskAgent().getAgent().getId(), d -> d.getAgent().getId()),
                        equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate))
                .penalizeConfigurable()
                .asConstraint("Agent day off");
    }

    /**
     * 2. Specialization match — agent must have the required specialization
     * as primary or secondary.
     */
    private Constraint specializationMatch(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .filter(a -> {
                    DeskAgent da = a.getDeskAgent();
                    UUID reqSpecId = a.getRequiredSpecialization().getId();
                    if (da.getPrimarySpecialization() != null
                            && da.getPrimarySpecialization().getId().equals(reqSpecId)) {
                        return false; // matches primary — no violation
                    }
                    return da.getSecondarySpecializations().stream()
                            .noneMatch(s -> s.getId().equals(reqSpecId));
                })
                .penalizeConfigurable()
                .asConstraint("Specialization match");
    }

    /**
     * 3. One assignment per timeslot — an agent cannot occupy two seats
     * in the same timeslot.
     */
    private Constraint oneAssignmentPerTimeslot(ConstraintFactory factory) {
        return factory.forEachUniquePair(AgentAssignment.class,
                        equal(a -> a.getDeskAgent() != null ? a.getDeskAgent().getId() : null),
                        equal(a -> a.getTimeslot().getId()))
                .filter((a1, a2) -> a1.getDeskAgent() != null)
                .penalizeConfigurable()
                .asConstraint("One assignment per timeslot");
    }

    /**
     * 4. Exactly one break — agents whose contracted hours strictly exceed
     * breakMinShiftHours must have exactly one contiguous gap (break).
     * Agents at or below the threshold must have no gap.
     */
    private Constraint exactlyOneBreak(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(ScheduleConfig.class)
                .filter((daId, date, assignments, config) -> {
                    BigDecimal effectiveHours = getEffectiveHoursFromAssignments(assignments, config);
                    if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) return false;

                    boolean needsBreak = effectiveHours.compareTo(config.breakMinShiftHours()) > 0;
                    int gapCount = countContiguousGaps(assignments, config.incrementMinutes());

                    if (needsBreak) {
                        return gapCount != 1;
                    } else {
                        return gapCount != 0;
                    }
                })
                .penalizeConfigurable((daId, date, assignments, config) -> 1)
                .asConstraint("Exactly one break");
    }

    /**
     * 5. Break duration — the single contiguous gap must be exactly
     * breakDurationMinutes / incrementMinutes timeslots long.
     */
    private Constraint breakDuration(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(ScheduleConfig.class)
                .filter((daId, date, assignments, config) -> {
                    BigDecimal effectiveHours = getEffectiveHoursFromAssignments(assignments, config);
                    if (effectiveHours == null) return false;
                    boolean needsBreak = effectiveHours.compareTo(config.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    int expectedSlots = config.breakDurationMinutes() / config.incrementMinutes();
                    List<Integer> gapLengths = getGapLengths(assignments, config.incrementMinutes());
                    if (gapLengths.size() != 1) return false; // exactlyOneBreak handles the count
                    return gapLengths.get(0) != expectedSlots;
                })
                .penalizeConfigurable((daId, date, assignments, config) -> 1)
                .asConstraint("Break duration");
    }

    /**
     * 6. Break blocked window — break must not fall within the first or last
     * N hours of the agent's shift.
     */
    private Constraint breakBlockedWindow(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(ScheduleConfig.class)
                .filter((daId, date, assignments, config) -> {
                    BigDecimal effectiveHours = getEffectiveHoursFromAssignments(assignments, config);
                    if (effectiveHours == null) return false;
                    boolean needsBreak = effectiveHours.compareTo(config.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    LocalTime breakStart = findBreakStart(assignments, config.incrementMinutes());
                    if (breakStart == null) return false;
                    int breakSlots = config.breakDurationMinutes() / config.incrementMinutes();
                    LocalTime breakEnd = breakStart.plusMinutes((long) breakSlots * config.incrementMinutes());

                    LocalTime shiftStart = getShiftStart(assignments);
                    LocalTime shiftEnd = getShiftEnd(assignments);
                    if (shiftStart == null || shiftEnd == null) return false;

                    long blockedMinutes = config.breakBlockedHours()
                            .multiply(BigDecimal.valueOf(60)).longValue();
                    LocalTime blockedStartEnd = shiftStart.plusMinutes(blockedMinutes);
                    LocalTime blockedEndStart = shiftEnd.minusMinutes(blockedMinutes);

                    return breakStart.isBefore(blockedStartEnd) || breakEnd.isAfter(blockedEndStart);
                })
                .penalizeConfigurable((daId, date, assignments, config) -> 1)
                .asConstraint("Break blocked window");
    }

    /**
     * 7. Break start alignment — break must start on a timeslot boundary
     * matching the configured alignment.
     */
    private Constraint breakStartAlignment(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(ScheduleConfig.class)
                .filter((daId, date, assignments, config) -> {
                    BigDecimal effectiveHours = getEffectiveHoursFromAssignments(assignments, config);
                    if (effectiveHours == null) return false;
                    boolean needsBreak = effectiveHours.compareTo(config.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    LocalTime breakStart = findBreakStart(assignments, config.incrementMinutes());
                    if (breakStart == null) return false;

                    return !isAligned(breakStart, config.breakStartAlignment());
                })
                .penalizeConfigurable((daId, date, assignments, config) -> 1)
                .asConstraint("Break start alignment");
    }

    /**
     * 12. Contracted hours — every desk-agent must be assigned exactly their
     * contracted hours per day. Penalty proportional to deviation.
     */
    private Constraint contractedHours(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent(),
                        a -> a.getTimeslot().getDate(),
                        count())
                .join(ScheduleConfig.class)
                .filter((da, date, assignmentCount, config) -> {
                    BigDecimal effectiveHours = da.getContractedHoursPerDay() != null
                            ? da.getContractedHoursPerDay()
                            : config.defaultContractedHoursPerDay();
                    if (effectiveHours == null) return false;
                    BigDecimal actualHours = BigDecimal.valueOf(assignmentCount)
                            .multiply(BigDecimal.valueOf(config.incrementMinutes()))
                            .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
                    return actualHours.compareTo(effectiveHours) != 0;
                })
                .penalizeConfigurable((da, date, count, config) -> {
                    BigDecimal effectiveHours = da.getContractedHoursPerDay() != null
                            ? da.getContractedHoursPerDay()
                            : config.defaultContractedHoursPerDay();
                    if (effectiveHours == null) return 0;
                    BigDecimal actualHours = BigDecimal.valueOf(count)
                            .multiply(BigDecimal.valueOf(config.incrementMinutes()))
                            .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
                    return Math.abs(actualHours.subtract(effectiveHours)
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(config.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue());
                })
                .asConstraint("Contracted hours");
    }

    /**
     * 13. Bulk over-allocation limit — total assigned hours must not exceed
     * demand hours by more than overallocationHardLimitPct. Pre-solve check handles
     * the input-based validation; this is a no-op placeholder for the solver constraint
     * since the solver cannot change the supply-demand ratio (only assignment distribution).
     */
    private Constraint bulkOverallocationLimit(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .penalizeConfigurable(a -> 0)
                .asConstraint("Bulk over-allocation limit");
    }

    /**
     * 15. Bulk under-allocation hard — demand below underallocationHardLimitPct
     * of contracted hours is a hard violation. Pre-solve check handles this.
     */
    private Constraint bulkUnderallocationHard(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .penalizeConfigurable(a -> 0)
                .asConstraint("Bulk under-allocation hard");
    }

    // ============================================================
    //  SOFT CONSTRAINTS
    // ============================================================

    /**
     * 8. Prefer primary specialization — penalise secondary spec assignments.
     */
    private Constraint preferPrimarySpecialization(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .filter(a -> {
                    DeskAgent da = a.getDeskAgent();
                    return da.getPrimarySpecialization() == null
                            || !da.getPrimarySpecialization().getId()
                                    .equals(a.getRequiredSpecialization().getId());
                })
                .penalizeConfigurable()
                .asConstraint("Prefer primary specialization");
    }

    /**
     * 9. Honour preferred start time — penalise assigning an agent to a
     * timeslot before their preferred start time for that day.
     */
    private Constraint honourPreferredStartTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .join(AgentPreference.class,
                        equal(a -> a.getDeskAgent().getAgent().getId(), p -> p.getAgent().getId()))
                .filter((a, p) -> {
                    if (p.getPreferredStartTime() == null) return false;
                    if (p.isStanding()) {
                        return a.getTimeslot().getDate().getDayOfWeek() == p.getDayOfWeek()
                                && a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
                    }
                    return a.getTimeslot().getDate().equals(p.getDate())
                            && a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
                })
                .penalizeConfigurable((a, p) -> 1)
                .asConstraint("Honour preferred start time");
    }

    /**
     * 10. Honour preferred break time — penalise when an agent's break
     * does not start at their preferred break time.
     */
    private Constraint honourPreferredBreakTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentPreference.class,
                        equal((da, date, assignments) -> da.getAgent().getId(),
                                p -> p.getAgent().getId()))
                .filter((da, date, assignments, pref) -> {
                    if (pref.getPreferredBreakTime() == null) return false;
                    if (pref.isStanding()) {
                        if (date.getDayOfWeek() != pref.getDayOfWeek()) return false;
                    } else {
                        if (!date.equals(pref.getDate())) return false;
                    }
                    int increment = deriveIncrement(assignments);
                    LocalTime breakStart = findBreakStart(assignments, increment);
                    if (breakStart == null) return false;
                    return !breakStart.equals(pref.getPreferredBreakTime());
                })
                .penalizeConfigurable((da, date, assignments, pref) -> 1)
                .asConstraint("Honour preferred break time");
    }

    /**
     * 11. Break clustering — penalise when the number of agents on break in a
     * single timeslot exceeds the threshold percentage of assigned agents.
     * Evaluated as a no-op placeholder — full implementation requires cross-agent
     * aggregation per timeslot which is deferred to Phase 5 optimization.
     */
    private Constraint breakClustering(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .penalizeConfigurable(a -> 0)
                .asConstraint("Break clustering");
    }

    /**
     * 14. Bulk under-allocation soft — soft penalty proportional to demand shortfall.
     * Pre-solve check handles the input-based validation.
     */
    private Constraint bulkUnderallocationSoft(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .penalizeConfigurable(a -> 0)
                .asConstraint("Bulk under-allocation soft");
    }

    // ============================================================
    //  HELPER METHODS
    // ============================================================

    private BigDecimal getEffectiveHoursFromAssignments(List<AgentAssignment> assignments,
                                                         ScheduleConfig config) {
        if (assignments == null || assignments.isEmpty()) return null;
        DeskAgent da = assignments.get(0).getDeskAgent();
        if (da == null) return null;
        return da.getContractedHoursPerDay() != null
                ? da.getContractedHoursPerDay()
                : config.defaultContractedHoursPerDay();
    }

    private int countContiguousGaps(List<AgentAssignment> assignments, int incrementMinutes) {
        return getGapLengths(assignments, incrementMinutes).size();
    }

    private List<Integer> getGapLengths(List<AgentAssignment> assignments, int incrementMinutes) {
        if (assignments == null || assignments.isEmpty()) return List.of();

        TreeSet<LocalTime> assignedStarts = new TreeSet<>();
        for (AgentAssignment a : assignments) {
            assignedStarts.add(a.getTimeslot().getStartTime());
        }
        if (assignedStarts.isEmpty()) return List.of();

        LocalTime shiftStart = assignedStarts.first();
        LocalTime shiftEnd = assignedStarts.last().plusMinutes(incrementMinutes);

        List<Integer> gapLengths = new ArrayList<>();
        int currentGap = 0;
        for (LocalTime t = shiftStart; t.isBefore(shiftEnd); t = t.plusMinutes(incrementMinutes)) {
            if (!assignedStarts.contains(t)) {
                currentGap++;
            } else {
                if (currentGap > 0) {
                    gapLengths.add(currentGap);
                    currentGap = 0;
                }
            }
        }
        if (currentGap > 0) {
            gapLengths.add(currentGap);
        }
        return gapLengths;
    }

    private LocalTime findBreakStart(List<AgentAssignment> assignments, int incrementMinutes) {
        if (assignments == null || assignments.isEmpty()) return null;

        TreeSet<LocalTime> assignedStarts = new TreeSet<>();
        for (AgentAssignment a : assignments) {
            assignedStarts.add(a.getTimeslot().getStartTime());
        }
        if (assignedStarts.isEmpty()) return null;

        LocalTime shiftStart = assignedStarts.first();
        LocalTime shiftEnd = assignedStarts.last().plusMinutes(incrementMinutes);

        for (LocalTime t = shiftStart; t.isBefore(shiftEnd); t = t.plusMinutes(incrementMinutes)) {
            if (!assignedStarts.contains(t)) {
                return t;
            }
        }
        return null;
    }

    private LocalTime getShiftStart(List<AgentAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return null;
        return assignments.stream()
                .map(a -> a.getTimeslot().getStartTime())
                .min(LocalTime::compareTo).orElse(null);
    }

    private LocalTime getShiftEnd(List<AgentAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return null;
        return assignments.stream()
                .map(a -> a.getTimeslot().getEndTime())
                .max(LocalTime::compareTo).orElse(null);
    }

    private boolean isAligned(LocalTime time, BreakAlignment alignment) {
        int minute = time.getMinute();
        return switch (alignment) {
            case ON_HOUR -> minute == 0;
            case ON_HALF_HOUR -> minute == 0 || minute == 30;
            case ON_QUARTER_HOUR -> minute % 15 == 0;
        };
    }

    private int deriveIncrement(List<AgentAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return 15;
        Timeslot t = assignments.get(0).getTimeslot();
        return (int) java.time.temporal.ChronoUnit.MINUTES.between(t.getStartTime(), t.getEndTime());
    }
}
