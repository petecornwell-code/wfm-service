package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
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
            unassignedAssignment(factory),
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
            contractedHoursOver(factory),
            contractedHoursUnder(factory),
            contractedHoursUnderZero(factory),
            bulkOverallocationLimit(factory),
            bulkUnderallocationSoft(factory),
            bulkUnderallocationHard(factory),
        };
    }

    // ============================================================
    //  HARD CONSTRAINTS
    // ============================================================

    /**
     * 0. Unassigned assignment — prefer filling every seat with an agent.
     *
     * This is a SOFT constraint: leaving a slot unallocated is acceptable
     * as long as the total allocation remains within the underflow/overflow
     * tolerance enforced by the bulk allocation hard constraints
     * ({@code bulkUnderallocationHard} / {@code bulkOverallocationLimit}).
     *
     * Must use forEachIncludingUnassigned() because Timefold's forEach()
     * excludes entities whose planning variable is null. Without this,
     * null-assigned entities are invisible to all constraint streams,
     * producing a 0-penalty score that the CH always prefers over any
     * real assignment.
     */
    private Constraint unassignedAssignment(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() == null)
                .penalizeConfigurable()
                .asConstraint("Unassigned assignment");
    }

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
     *
     * Uses forEach-based groupBy instead of forEachUniquePair to avoid
     * O(N²) pairing of unassigned entities. Groups by (deskAgentId, timeslotId)
     * and penalizes when count > 1 (agent appears in multiple seats of the
     * same timeslot). Penalty = (count - 1) so 2 seats = 1, 3 seats = 2, etc.
     */
    private Constraint oneAssignmentPerTimeslot(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getId(),
                        count())
                .filter((daId, tsId, cnt) -> cnt > 1)
                .penalizeConfigurable((daId, tsId, cnt) -> cnt - 1)
                .asConstraint("One assignment per timeslot");
    }

    /**
     * 4. Exactly one break — agents whose effective contracted hours strictly exceed
     * breakMinShiftHours must have exactly one contiguous gap (break).
     * Agents at or below the threshold must have no gap.
     * Uses AgentDayConfig for exception-aware effective hours.
     *
     * <p>CH-friendly: only fires once the agent has enough assigned slots to
     * form a meaningful shift. During construction, an agent with 1-2 slots
     * should NOT be penalised for missing a break — the break gap will form
     * as the shift is completed. This prevents the constraint from blocking
     * CH progress regardless of its weight.
     */
    private Constraint exactlyOneBreak(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .filter((daId, date, assignments, dayConfig) -> {
                    BigDecimal effectiveHours = dayConfig.effectiveHours();
                    boolean needsBreak = effectiveHours.compareTo(dayConfig.breakMinShiftHours()) > 0;
                    if (!needsBreak) {
                        // Agent's contracted hours don't require a break — penalise any gap
                        return countContiguousGaps(assignments, dayConfig.incrementMinutes()) != 0;
                    }

                    // Only enforce break rule once agent has enough slots to need one.
                    int breakThresholdSlots = dayConfig.breakMinShiftHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, java.math.RoundingMode.CEILING)
                            .intValue();
                    if (assignments.size() < breakThresholdSlots) {
                        // During construction: only penalise fragmented shifts (>1 gap)
                        return countContiguousGaps(assignments, dayConfig.incrementMinutes()) > 1;
                    }

                    // Fully (or nearly fully) assigned: require exactly 1 gap of correct length
                    int gaps = countContiguousGaps(assignments, dayConfig.incrementMinutes());
                    if (gaps != 1) return true;
                    int expectedBreakSlots = dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes();
                    return totalGapSlots(assignments, dayConfig.incrementMinutes()) != expectedBreakSlots;
                })
                .penalizeConfigurable((daId, date, assignments, dayConfig) -> {
                    // Penalise by TOTAL excess break slots, not just gap count.
                    // This makes longer/extra breaks proportionally more expensive,
                    // directly targeting break overallocation.
                    boolean needsBreak = dayConfig.effectiveHours()
                            .compareTo(dayConfig.breakMinShiftHours()) > 0;
                    int expectedBreakSlots = needsBreak
                            ? dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes() : 0;
                    int actualBreakSlots = totalGapSlots(assignments, dayConfig.incrementMinutes());
                    return Math.max(1, Math.abs(actualBreakSlots - expectedBreakSlots));
                })
                .asConstraint("Exactly one break");
    }

    /**
     * 5. Break duration — the single contiguous gap must be exactly
     * breakDurationMinutes / incrementMinutes timeslots long.
     * Uses AgentDayConfig for exception-aware effective hours.
     */
    private Constraint breakDuration(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .filter((daId, date, assignments, dayConfig) -> {
                    BigDecimal effectiveHours = dayConfig.effectiveHours();
                    boolean needsBreak = effectiveHours.compareTo(dayConfig.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    int expectedSlots = dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes();
                    List<Integer> gapLengths = getGapLengths(assignments, dayConfig.incrementMinutes());
                    if (gapLengths.size() != 1) return false; // exactlyOneBreak handles the count
                    return gapLengths.get(0) != expectedSlots;
                })
                .penalizeConfigurable((daId, date, assignments, dayConfig) -> 1)
                .asConstraint("Break duration");
    }

    /**
     * 6. Break blocked window — break must not fall within the first or last
     * N hours of the agent's shift.
     * Uses AgentDayConfig for exception-aware effective hours.
     */
    private Constraint breakBlockedWindow(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .filter((daId, date, assignments, dayConfig) -> {
                    BigDecimal effectiveHours = dayConfig.effectiveHours();
                    boolean needsBreak = effectiveHours.compareTo(dayConfig.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    LocalTime breakStart = findBreakStart(assignments, dayConfig.incrementMinutes());
                    if (breakStart == null) return false;
                    int breakSlots = dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes();
                    LocalTime breakEnd = breakStart.plusMinutes((long) breakSlots * dayConfig.incrementMinutes());

                    LocalTime shiftStart = getShiftStart(assignments);
                    LocalTime shiftEnd = getShiftEnd(assignments);
                    if (shiftStart == null || shiftEnd == null) return false;

                    long blockedMinutes = dayConfig.breakBlockedHours()
                            .multiply(BigDecimal.valueOf(60)).longValue();
                    LocalTime blockedStartEnd = shiftStart.plusMinutes(blockedMinutes);
                    LocalTime blockedEndStart = shiftEnd.minusMinutes(blockedMinutes);

                    return breakStart.isBefore(blockedStartEnd) || breakEnd.isAfter(blockedEndStart);
                })
                .penalizeConfigurable((daId, date, assignments, dayConfig) -> 1)
                .asConstraint("Break blocked window");
    }

    /**
     * 7. Break start alignment — break must start on a timeslot boundary
     * matching the configured alignment.
     * Uses AgentDayConfig for exception-aware effective hours.
     */
    private Constraint breakStartAlignment(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .filter((daId, date, assignments, dayConfig) -> {
                    BigDecimal effectiveHours = dayConfig.effectiveHours();
                    boolean needsBreak = effectiveHours.compareTo(dayConfig.breakMinShiftHours()) > 0;
                    if (!needsBreak) return false;

                    LocalTime breakStart = findBreakStart(assignments, dayConfig.incrementMinutes());
                    if (breakStart == null) return false;

                    return !isAligned(breakStart, dayConfig.breakStartAlignment());
                })
                .penalizeConfigurable((daId, date, assignments, dayConfig) -> 1)
                .asConstraint("Break start alignment");
    }

    /**
     * 12a. Contracted hours (over) — penalises agents assigned MORE than their
     * effective contracted hours adjusted by the overallocation tolerance.
     * The per-agent maximum is {@code expectedSlots * overallocationHardLimitPct / 100},
     * matching the bulk overallocation policy. This allows agents to be assigned
     * beyond their contracted hours (e.g. to cover break-time demand) as long as
     * the overflow limit is not exceeded.
     */
    private Constraint contractedHoursOver(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        count())
                .join(AgentDayConfig.class,
                        equal((daId, date, cnt) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, cnt) -> date, AgentDayConfig::date))
                .filter((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = dayConfig.effectiveHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue();
                    int maxAllowed = expectedSlots * dayConfig.overallocationHardLimitPct() / 100;
                    return assignmentCount > maxAllowed;
                })
                .penalizeConfigurable((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = dayConfig.effectiveHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue();
                    int maxAllowed = expectedSlots * dayConfig.overallocationHardLimitPct() / 100;
                    return assignmentCount - maxAllowed;
                })
                .asConstraint("Contracted hours (over)");
    }

    /**
     * 12b. Contracted hours (under) — penalises agents assigned FEWER than their
     * effective contracted hours adjusted by the underallocation tolerance.
     * The per-agent minimum is {@code expectedSlots * underallocationHardLimitPct / 100},
     * matching the bulk underallocation policy.
     *
     * <p>Handles agents with at least one assignment: groups by (agent, date),
     * counts assignments, and penalises the shortfall below the tolerated minimum.
     */
    private Constraint contractedHoursUnder(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(
                        a -> a.getDeskAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        count())
                .join(AgentDayConfig.class,
                        equal((daId, date, cnt) -> daId, AgentDayConfig::deskAgentId),
                        equal((daId, date, cnt) -> date, AgentDayConfig::date))
                .filter((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = dayConfig.effectiveHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue();
                    int minRequired = expectedSlots * dayConfig.underallocationHardLimitPct() / 100;
                    return assignmentCount < minRequired;
                })
                .penalizeConfigurable((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = dayConfig.effectiveHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue();
                    int minRequired = expectedSlots * dayConfig.underallocationHardLimitPct() / 100;
                    return minRequired - assignmentCount;
                })
                .asConstraint("Contracted hours (under)");
    }

    /**
     * 12c. Contracted hours (under, zero assignments) — penalises agents who have
     * an {@link AgentDayConfig} (i.e. are expected to work) but have NO assignments
     * at all on that day. The standard contractedHoursUnder constraint starts from
     * {@link AgentAssignment} and cannot see agents with zero assignments. This
     * ensures the solver has a hard incentive to assign every contracted agent.
     * Penalty is the underallocation-adjusted minimum slots for the agent-day.
     */
    private Constraint contractedHoursUnderZero(ConstraintFactory factory) {
        return factory.forEach(AgentDayConfig.class)
                .ifNotExists(AgentAssignment.class,
                        equal(AgentDayConfig::deskAgentId, a -> a.getDeskAgent() != null ? a.getDeskAgent().getId() : null),
                        equal(AgentDayConfig::date, a -> a.getTimeslot().getDate()))
                .penalizeConfigurable(dayConfig -> {
                    int expectedSlots = dayConfig.effectiveHours()
                            .multiply(BigDecimal.valueOf(60))
                            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                            .intValue();
                    return expectedSlots * dayConfig.underallocationHardLimitPct() / 100;
                })
                .asConstraint("Contracted hours (under, zero)");
    }

    /**
     * 13. Bulk over-allocation limit — total assigned slots per day must not
     * exceed overallocationHardLimitPct of demand slots. Penalises excess slots
     * beyond the limit. Uses DayDemandConfig for pre-computed demand totals.
     */
    private Constraint bulkOverallocationLimit(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .groupBy(a -> a.getTimeslot().getDate(), count())
                .join(DayDemandConfig.class,
                        equal((date, cnt) -> date, DayDemandConfig::date))
                .join(ScheduleConfig.class)
                .filter((date, totalAssigned, dayDemand, config) -> {
                    int maxAllowed = dayDemand.totalDemandSlots()
                            * config.overallocationHardLimitPct() / 100;
                    return totalAssigned > maxAllowed;
                })
                .penalizeConfigurable((date, totalAssigned, dayDemand, config) -> {
                    int maxAllowed = dayDemand.totalDemandSlots()
                            * config.overallocationHardLimitPct() / 100;
                    return totalAssigned - maxAllowed;
                })
                .asConstraint("Bulk over-allocation limit");
    }

    /**
     * 15. Bulk under-allocation hard — total assigned slots per day must not
     * fall below underallocationHardLimitPct of demand slots. Penalises the
     * shortfall below the limit. Uses DayDemandConfig for pre-computed totals.
     *
     * <p>CH-friendly: uses forEachIncludingUnassigned with sum to count assigned
     * entities, so the constraint fires even when all entities are null. This
     * prevents a penalty cliff when the first assignment is made (0→1 transition
     * that would otherwise block the construction heuristic for large schedules).
     */
    private Constraint bulkUnderallocationHard(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .groupBy(a -> a.getTimeslot().getDate(),
                        sum((AgentAssignment a) -> a.getDeskAgent() != null ? 1 : 0))
                .join(DayDemandConfig.class,
                        equal((date, cnt) -> date, DayDemandConfig::date))
                .join(ScheduleConfig.class)
                .filter((date, totalAssigned, dayDemand, config) -> {
                    int minRequired = dayDemand.totalDemandSlots()
                            * config.underallocationHardLimitPct() / 100;
                    return totalAssigned < minRequired;
                })
                .penalizeConfigurable((date, totalAssigned, dayDemand, config) -> {
                    int minRequired = dayDemand.totalDemandSlots()
                            * config.underallocationHardLimitPct() / 100;
                    return minRequired - totalAssigned;
                })
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
     * Preferences are pre-resolved (weekly vs standing) by SolverService,
     * so all preferences have an exact date set.
     */
    private Constraint honourPreferredStartTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getDeskAgent() != null)
                .join(AgentPreference.class,
                        equal(a -> a.getDeskAgent().getAgent().getId(), p -> p.getAgent().getId()),
                        equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))
                .filter((a, p) -> {
                    if (p.getPreferredStartTime() == null) return false;
                    return a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
                })
                .penalizeConfigurable((a, p) -> 1)
                .asConstraint("Honour preferred start time");
    }

    /**
     * 10. Honour preferred break time — penalise when an agent's break
     * does not start at their preferred break time.
     * Preferences are pre-resolved by SolverService with exact dates.
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
                                p -> p.getAgent().getId()),
                        equal((da, date, assignments) -> date,
                                AgentPreference::getDate))
                .filter((da, date, assignments, pref) -> {
                    if (pref.getPreferredBreakTime() == null) return false;
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

    /**
     * Computes the expected number of work slots for an agent-day.
     */
    private int expectedWorkSlots(AgentDayConfig dayConfig) {
        return dayConfig.effectiveHours()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int countContiguousGaps(List<AgentAssignment> assignments, int incrementMinutes) {
        return getGapLengths(assignments, incrementMinutes).size();
    }

    private int totalGapSlots(List<AgentAssignment> assignments, int incrementMinutes) {
        return getGapLengths(assignments, incrementMinutes).stream()
                .mapToInt(Integer::intValue).sum();
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
