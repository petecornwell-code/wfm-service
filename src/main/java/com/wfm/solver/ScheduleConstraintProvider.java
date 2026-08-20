package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.wfm.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.*;
import static ai.timefold.solver.core.api.score.stream.Joiners.*;

public class ScheduleConstraintProvider implements ConstraintProvider {

    /**
     * Floor enforced by {@link #minimumStaffing} — at least this many agents on every
     * timeslot inside operating hours, whatever the forecast says. See that constraint
     * for why the value is fixed rather than carried in as a problem fact.
     *
     * <p>Public because {@code SolverService.expandMinimumStaffingSeats} must create the
     * same number of seats that this constraint demands. The constraint alone cannot
     * deliver the floor: it can only penalise a timeslot the solver has a planning entity
     * for, and a zero-demand hour has none.
     */
    public static final int MIN_AGENTS_PER_TIMESLOT = 1;

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
            minimumStaffing(factory),
            consistentDailyStart(factory),
            consistentBreakOffset(factory),
        };
    }

    // ============================================================
    //  HARD CONSTRAINTS
    // ============================================================

    /**
     * 0. Unassigned assignment — penalises timeslots where the total assigned
     * agents fall outside the acceptable allocation range defined by
     * {@code underallocationHardLimitPct} and {@code overallocationHardLimitPct}.
     *
     * <p>This is a SOFT constraint that complements the hard bulk allocation
     * constraints. It counts the number of timeslots where the assigned agent
     * count is below the under-allocation minimum or above the over-allocation
     * maximum, penalising by 1 per violating timeslot.
     *
     * <p>Uses forEachIncludingUnassigned() with sum so the constraint fires
     * even when all entities are null (CH-friendly).
     */
    private Constraint unassignedAssignment(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .groupBy(a -> a.getTimeslot(),
                        sum((AgentAssignment a) -> a.getAgent() != null ? 1 : 0))
                .join(TimeslotDemandConfig.class,
                        equal((ts, cnt) -> ts, TimeslotDemandConfig::timeslot))
                .join(ScheduleConfig.class)
                .filter((ts, totalAssigned, tsDemand, config) -> {
                    int minRequired = tsDemand.totalDemandFTEs()
                            * config.underallocationHardLimitPct() / 100;
                    int maxAllowed = tsDemand.totalDemandFTEs()
                            * config.overallocationHardLimitPct() / 100;
                    return totalAssigned < minRequired || totalAssigned > maxAllowed;
                })
                .penalizeConfigurable()
                .asConstraint("Unassigned assignment");
    }

    /**
     * 1. Agent day off — agent must not be assigned on a day they have off.
     */
    private Constraint agentDayOff(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .join(AgentDayOff.class,
                        equal(a -> a.getAgent().getId(), d -> d.getAgent().getId()),
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
                .filter(a -> a.getAgent() != null)
                .filter(a -> {
                    Agent da = a.getAgent();
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
     * O(N²) pairing of unassigned entities. Groups by (agentId, timeslotId)
     * and penalizes when count > 1 (agent appears in multiple seats of the
     * same timeslot). Penalty = (count - 1) so 2 seats = 1, 3 seats = 2, etc.
     */
    private Constraint oneAssignmentPerTimeslot(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
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
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
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
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
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
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
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
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
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
     * exact effective contracted hours. Agents must work exactly their contracted
     * hours per day.
     */
    private Constraint contractedHoursOver(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        count())
                .join(AgentDayConfig.class,
                        equal((daId, date, cnt) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, cnt) -> date, AgentDayConfig::date))
                .filter((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = expectedWorkSlots(dayConfig);
                    return assignmentCount > expectedSlots;
                })
                .penalizeConfigurable((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = expectedWorkSlots(dayConfig);
                    return assignmentCount - expectedSlots;
                })
                .asConstraint("Contracted hours (over)");
    }

    /**
     * 12b. Contracted hours (under) — penalises agents assigned FEWER than their
     * exact effective contracted hours. Agents must work exactly their contracted
     * hours per day.
     *
     * <p>Handles agents with at least one assignment: groups by (agent, date),
     * counts assignments, and penalises the shortfall below the expected slots.
     */
    private Constraint contractedHoursUnder(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent().getId(),
                        a -> a.getTimeslot().getDate(),
                        count())
                .join(AgentDayConfig.class,
                        equal((daId, date, cnt) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, cnt) -> date, AgentDayConfig::date))
                .filter((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = expectedWorkSlots(dayConfig);
                    return assignmentCount < expectedSlots;
                })
                .penalizeConfigurable((daId, date, assignmentCount, dayConfig) -> {
                    int expectedSlots = expectedWorkSlots(dayConfig);
                    return expectedSlots - assignmentCount;
                })
                .asConstraint("Contracted hours (under)");
    }

    /**
     * 12c. Contracted hours (under, zero assignments) — penalises agents who have
     * an {@link AgentDayConfig} (i.e. are expected to work) but have NO assignments
     * at all on that day. The standard contractedHoursUnder constraint starts from
     * {@link AgentAssignment} and cannot see agents with zero assignments. This
     * ensures the solver has a hard incentive to assign every contracted agent.
     * Penalty is the full expected slots for the agent-day.
     */
    private Constraint contractedHoursUnderZero(ConstraintFactory factory) {
        return factory.forEach(AgentDayConfig.class)
                .ifNotExists(AgentAssignment.class,
                        equal(AgentDayConfig::agentId, a -> a.getAgent() != null ? a.getAgent().getId() : null),
                        equal(AgentDayConfig::date, a -> a.getTimeslot().getDate()))
                .penalizeConfigurable(dayConfig -> expectedWorkSlots(dayConfig))
                .asConstraint("Contracted hours (under, zero)");
    }

    /**
     * 13. Bulk over-allocation limit — total assigned agents per timeslot must not
     * exceed overallocationHardLimitPct of demand FTEs. Penalises excess agents
     * beyond the limit. Uses TimeslotDemandConfig for pre-computed per-timeslot demand.
     */
    private Constraint bulkOverallocationLimit(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(a -> a.getTimeslot(), count())
                .join(TimeslotDemandConfig.class,
                        equal((ts, cnt) -> ts, TimeslotDemandConfig::timeslot))
                .join(ScheduleConfig.class)
                .filter((ts, totalAssigned, tsDemand, config) -> {
                    int maxAllowed = tsDemand.totalDemandFTEs()
                            * config.overallocationHardLimitPct() / 100;
                    return totalAssigned > maxAllowed;
                })
                .penalizeConfigurable((ts, totalAssigned, tsDemand, config) -> {
                    int maxAllowed = tsDemand.totalDemandFTEs()
                            * config.overallocationHardLimitPct() / 100;
                    return totalAssigned - maxAllowed;
                })
                .asConstraint("Bulk over-allocation limit");
    }

    /**
     * 15. Bulk under-allocation hard — total assigned agents per timeslot must not
     * fall below underallocationHardLimitPct of demand FTEs. Penalises the
     * shortfall below the limit. Uses TimeslotDemandConfig for pre-computed totals.
     *
     * <p>CH-friendly: uses forEachIncludingUnassigned with sum to count assigned
     * entities, so the constraint fires even when all entities are null. This
     * prevents a penalty cliff when the first assignment is made (0→1 transition
     * that would otherwise block the construction heuristic for large schedules).
     */
    private Constraint bulkUnderallocationHard(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .groupBy(a -> a.getTimeslot(),
                        sum((AgentAssignment a) -> a.getAgent() != null ? 1 : 0))
                .join(TimeslotDemandConfig.class,
                        equal((ts, cnt) -> ts, TimeslotDemandConfig::timeslot))
                .join(ScheduleConfig.class)
                .filter((ts, totalAssigned, tsDemand, config) -> {
                    int minRequired = tsDemand.totalDemandFTEs()
                            * config.underallocationHardLimitPct() / 100;
                    return totalAssigned < minRequired;
                })
                .penalizeConfigurable((ts, totalAssigned, tsDemand, config) -> {
                    int minRequired = tsDemand.totalDemandFTEs()
                            * config.underallocationHardLimitPct() / 100;
                    return minRequired - totalAssigned;
                })
                .asConstraint("Bulk under-allocation hard");
    }

    /**
     * 16. Minimum staffing — every timeslot inside operating hours must carry at least one
     * assigned agent, irrespective of forecast demand.
     *
     * <p>The bulk under-allocation constraints derive their floor from demand
     * ({@code underallocationHardLimitPct} of {@code totalDemandFTEs}), so a timeslot
     * forecast at zero FTEs obliges nothing — 50% of 0 is 0. A demand file with leading
     * zeros therefore leaves the desk legitimately unstaffed for those hours, and because
     * an 8h shift plus a 1h break spans exactly 9 of a 13h window, the solver packs every
     * shift against the first non-zero hour. That is correct against the forecast but
     * leaves no cover at all for walk-ins, spillover or a forecast that simply understates
     * the early hours.
     *
     * <p><strong>Deliberately weight-driven rather than hard-coded.</strong>
     * {@link ConstraintWeights} is a {@code @ConstraintConfiguration} whose weights are
     * persisted {@code HardSoftScore}s, so whether this constraint is hard or soft is a
     * per-desk configuration row, not a code decision — set {@code minStaffingWeight} to
     * {@code ofHard(n)} to make an unstaffed hour illegal, or {@code ofSoft(n)} to make it
     * merely expensive. It defaults to {@code ofSoft(1000)}: high enough to dominate the
     * other soft terms (honour-start-time 5, prefer-primary 1, break-clustering 2) and
     * reliably pull an agent onto an empty hour, without the failure mode a hard default
     * would carry. Because {@code contractedHoursOver} is hard, an agent works exactly
     * their contracted hours, so a 9h shift span cannot tile a 13h window alone; a hard
     * minimum on a day with too few available agents makes the whole solve infeasible,
     * returning no schedule rather than an imperfect one.
     *
     * <p>The threshold is one agent — the case this addresses is an hour with nobody on it.
     * A configurable minimum of N would need the threshold carried in as a problem fact
     * (this groups by timeslot, so it cannot reach {@code AgentDayConfig}); until that is
     * needed, one avoids a migration and a solver-facing config field for no gain.
     *
     * <p><strong>Depends on seats existing.</strong> This groups {@link AgentAssignment}
     * entities by timeslot, and a groupBy emits a group only for keys present in the stream —
     * so it is silent on a timeslot with no entities at all, at any weight. That is exactly
     * the zero-demand hour this constraint exists for: {@code FteUploadService} skips
     * {@code fteValue <= 0}, so no {@code StaffingRequirement} is persisted, and
     * {@code expandAssignments} therefore creates no seat. {@code SolverService}
     * .{@code expandMinimumStaffingSeats} closes that by guaranteeing
     * {@link #MIN_AGENTS_PER_TIMESLOT} seats on every timeslot before solving; without it
     * this constraint is inert on precisely the hours it was written for, and promoting it
     * to hard changes nothing because there is nothing to make hard. The seat also gives the
     * solver a planning variable to satisfy the penalty with — penalising a timeslot the
     * solver cannot assign into would just make the schedule permanently infeasible.
     *
     * <p>CH-friendly in the same way as the bulk constraints: {@code forEachIncludingUnassigned}
     * with a sum over assigned entities, so the penalty is already present while every entity
     * is still null rather than appearing as a cliff on the first assignment.
     */
    Constraint minimumStaffing(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .groupBy(a -> a.getTimeslot(),
                        sum((AgentAssignment a) -> a.getAgent() != null ? 1 : 0))
                .filter((ts, totalAssigned) -> totalAssigned < MIN_AGENTS_PER_TIMESLOT)
                .penalizeConfigurable((ts, totalAssigned) -> MIN_AGENTS_PER_TIMESLOT - totalAssigned)
                .asConstraint("Minimum staffing");
    }

    // ============================================================
    //  SOFT CONSTRAINTS
    // ============================================================

    /**
     * 8. Prefer primary specialization — penalise secondary spec assignments.
     */
    private Constraint preferPrimarySpecialization(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .filter(a -> {
                    Agent da = a.getAgent();
                    return da.getPrimarySpecialization() == null
                            || !da.getPrimarySpecialization().getId()
                                    .equals(a.getRequiredSpecialization().getId());
                })
                .penalizeConfigurable()
                .asConstraint("Prefer primary specialization");
    }

    /**
     * 9. Honour preferred start time — penalise how far an agent's actual shift start
     * on a day sits from the start time they asked for, in either direction.
     * Preferences are pre-resolved (weekly vs standing) by SolverService,
     * so all preferences have an exact date set.
     *
     * <p>Two-directional by design. The original form penalised only
     * {@code slotStart.isBefore(preferredStartTime)}, which made a preference a floor
     * rather than an anchor: an agent who asked for 09:00 paid nothing for being placed
     * at 14:00, so nothing held a start time steady from one day to the next. Deviation
     * is measured on the agent-day's earliest assigned slot and charged in whole
     * increments, which leaves the early direction at almost exactly its old magnitude
     * (a contiguous shift starting 2h early on 60-minute slots cost 2 before and costs 2
     * now) while lateness stops being free.
     *
     * <p>Rounds up: any non-zero deviation costs at least one increment. Rounding to
     * nearest would restore a band of free deviation below half an increment — the same
     * class of hole the {@code isBefore} test was. A preference no slot boundary can hit
     * (09:30 against hourly slots) therefore carries a constant floor of 1, equal on both
     * sides, so it does not bias placement one way or the other.
     *
     * <p>An agent with no assignment on a date forms no group and is not penalised: not
     * working is not a start-time deviation. Whether the day should have been worked at
     * all is {@link #contractedHoursUnder}'s concern.
     */
    // Package-private so ConstraintVerifier can target this constraint in isolation.
    Constraint honourPreferredStartTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentPreference.class,
                        equal((agent, date, assignments) -> agent.getId(),
                                p -> p.getAgent().getId()),
                        equal((agent, date, assignments) -> date,
                                AgentPreference::getDate))
                .filter((agent, date, assignments, pref) ->
                        startDeviationIncrements(assignments, pref.getPreferredStartTime()) > 0)
                .penalizeConfigurable((agent, date, assignments, pref) ->
                        startDeviationIncrements(assignments, pref.getPreferredStartTime()))
                .asConstraint("Honour preferred start time");
    }

    /**
     * 10. Honour preferred break time — penalise when an agent's break
     * does not start at their preferred break time.
     * Preferences are pre-resolved by SolverService with exact dates.
     */
    private Constraint honourPreferredBreakTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                .join(AgentPreference.class,
                        equal((agent, date, assignments) -> agent.getId(),
                                p -> p.getAgent().getId()),
                        equal((agent, date, assignments) -> date,
                                AgentPreference::getDate))
                .filter((agent, date, assignments, pref) -> {
                    if (pref.getPreferredBreakTime() == null) return false;
                    int increment = deriveIncrement(assignments);
                    LocalTime breakStart = findBreakStart(assignments, increment);
                    if (breakStart == null) return false;
                    return !breakStart.equals(pref.getPreferredBreakTime());
                })
                .penalizeConfigurable((agent, date, assignments, pref) -> 1)
                .asConstraint("Honour preferred break time");
    }

    /**
     * 17. Consistent daily start — penalise the spread between an agent's earliest and latest
     * shift start across the days they work, in whole increments.
     *
     * <p>The anchor is the solver's to choose. Nothing here says <em>which</em> start time an
     * agent should have; only that it should be the same one every day. That is what separates
     * this from {@link #honourPreferredStartTime}, which measures against a start the agent
     * asked for and applies only to agents who expressed a preference. This applies to every
     * agent, including the majority who have no preference on file.
     *
     * <p>Spread, not per-day deviation, and that has a consequence worth knowing: the penalty
     * is set entirely by the two extreme days, so moving a middle day changes nothing until it
     * becomes an extreme. The search sees a plateau rather than a gradient, and an agent with
     * one badly-placed Saturday costs the same whether the other five days agree perfectly or
     * not. If Stage 1 shows less movement than expected, summing each day's deviation from the
     * agent's own earliest start is the variant to try — it gives every day a gradient, at the
     * cost of no longer matching the "no spread over 2h" target directly.
     *
     * <p>Must stay soft. A hard consistency rule is escapable by shortening shifts — exactly
     * how the {@code breakBlockedHours} 3.0 run collapsed 51 agent-days to three-hour shifts,
     * since {@code contracted_hours_under} is soft while the hard window is not. Hard-vs-soft
     * is the {@code consistent_start_weight} column's decision, so this is a property of the
     * default, not of the code.
     *
     * <p>An agent who works a single day has an earliest equal to their latest and is not
     * penalised; an agent with no assignments at all forms no group. Both are correct — there
     * is nothing to be consistent about.
     */
    // Package-private so ConstraintVerifier can target this constraint in isolation.
    Constraint consistentDailyStart(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent(),
                        a -> a.getTimeslot().getDate(),
                        min((AgentAssignment a) -> a.getTimeslot().getStartTime()))
                .groupBy(
                        (agent, date, dayStart) -> agent,
                        min((Agent agent, LocalDate date, LocalTime dayStart) -> dayStart),
                        max((Agent agent, LocalDate date, LocalTime dayStart) -> dayStart))
                .join(ScheduleConfig.class)
                .filter((agent, earliest, latest, config) -> earliest.isBefore(latest))
                .penalizeConfigurable((agent, earliest, latest, config) ->
                        spreadIncrements(earliest, latest, config.incrementMinutes()))
                .asConstraint("Consistent daily start");
    }

    /**
     * 18. Consistent break offset — penalise the spread in how far into an agent's shift their
     * break falls, across the days they work, in whole increments.
     *
     * <p>Measured as an offset from that day's shift start, not as an absolute time of day, and
     * that choice is the whole point of the constraint. An agent who starts at 09:00 and breaks
     * at 13:00 and one who starts at 10:00 and breaks at 14:00 have the same working rhythm —
     * four hours in — and this treats them as identical. It is the contrast with
     * {@link #honourPreferredBreakTime}, whose {@code preferredBreakTime} is an absolute
     * {@code LocalTime} and so cannot express "the same distance into the shift".
     *
     * <p>Offset also keeps this independent of {@link #consistentDailyStart}. Were the break
     * measured absolutely, an agent with scattered starts would be charged twice for one fault:
     * once by that constraint, and again here because the break necessarily moved with the
     * shift. As an offset, the two constraints measure genuinely different things and their
     * weights stay independently meaningful.
     *
     * <p>The spread plateau documented on {@link #consistentDailyStart} applies here too: only
     * the two extreme days set the penalty.
     *
     * <p>An agent-day with no break contributes nothing — short shifts are not obliged to carry
     * one, and {@code exactlyOneBreak} is what decides whether a day should have had one.
     * An agent with one break-carrying day has an offset equal to itself and is not penalised.
     */
    // Package-private so ConstraintVerifier can target this constraint in isolation.
    Constraint consistentBreakOffset(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(
                        a -> a.getAgent(),
                        a -> a.getTimeslot().getDate(),
                        toList())
                // Map to the offset before filtering or aggregating. Computing it inside the
                // filter and again inside each collector would run findBreakStart three times
                // per agent-day, on every move that touches that agent-day.
                .map((agent, date, assignments) -> agent,
                        (agent, date, assignments) -> breakOffsetMinutes(assignments))
                .filter((agent, offset) -> offset != null)
                .groupBy(
                        (agent, offset) -> agent,
                        min((Agent agent, Integer offset) -> offset),
                        max((Agent agent, Integer offset) -> offset))
                .join(ScheduleConfig.class)
                .filter((agent, earliest, latest, config) -> latest > earliest)
                .penalizeConfigurable((agent, earliest, latest, config) ->
                        offsetSpreadIncrements(earliest, latest, config.incrementMinutes()))
                .asConstraint("Consistent break offset");
    }

    /**
     * 11. Break clustering — penalise when the number of agents on break in a
     * single timeslot exceeds the threshold percentage of assigned agents.
     * Evaluated as a no-op placeholder — full implementation requires cross-agent
     * aggregation per timeslot which is deferred to Phase 5 optimization.
     */
    private Constraint breakClustering(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .penalizeConfigurable(a -> 0)
                .asConstraint("Break clustering");
    }

    /**
     * 14. Bulk under-allocation soft — soft penalty proportional to the shortfall
     * against <em>full</em> demand in each timeslot.
     *
     * <p>This is the only constraint that makes covering satisfiable demand
     * preferable to leaving it bare. {@link #bulkUnderallocationHard} is a
     * feasibility floor at {@code underallocationHardLimitPct} of demand — it is
     * binary, so above that floor the solver previously saw no difference between
     * staffing an hour and ignoring it. A schedule could therefore score a flawless
     * 0soft while whole hours of demand went unstaffed and other hours were
     * over-stacked, because nothing pulled coverage in either direction.
     *
     * <p>Measured against 100% of demand, not the hard limit: the hard floor is the
     * point below which a schedule is illegal, whereas this expresses that meeting
     * demand exactly is what "good" means. Excess above demand is not rewarded here
     * — over-allocation is {@link #bulkOverallocationLimit}'s concern.
     *
     * <p>CH-friendly in the same way as the hard variant: forEachIncludingUnassigned
     * with a sum over assigned entities, so the constraint is already active while
     * every entity is still null. Using forEach here would leave the penalty absent
     * until the first assignment and then introduce it as a cliff, which stalls the
     * construction heuristic on large schedules.
     */
    // Package-private so ConstraintVerifier can target this constraint in isolation.
    Constraint bulkUnderallocationSoft(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentAssignment.class)
                .groupBy(a -> a.getTimeslot(),
                        sum((AgentAssignment a) -> a.getAgent() != null ? 1 : 0))
                .join(TimeslotDemandConfig.class,
                        equal((ts, cnt) -> ts, TimeslotDemandConfig::timeslot))
                .filter((ts, totalAssigned, tsDemand) -> totalAssigned < tsDemand.totalDemandFTEs())
                .penalizeConfigurable((ts, totalAssigned, tsDemand) ->
                        tsDemand.totalDemandFTEs() - totalAssigned)
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

    /**
     * Absolute distance between an agent-day's shift start and a preferred start time,
     * in whole timeslot increments, rounded up so that no deviation is free.
     * Returns 0 when there is nothing to compare. See {@link #honourPreferredStartTime}
     * for why it rounds this way.
     */
    private int startDeviationIncrements(List<AgentAssignment> assignments, LocalTime preferredStart) {
        if (preferredStart == null) return 0;
        LocalTime shiftStart = getShiftStart(assignments);
        if (shiftStart == null) return 0;
        long deviationMinutes = Math.abs(
                java.time.temporal.ChronoUnit.MINUTES.between(preferredStart, shiftStart));
        if (deviationMinutes == 0) return 0;
        return BigDecimal.valueOf(deviationMinutes)
                .divide(BigDecimal.valueOf(deriveIncrement(assignments)), 0, RoundingMode.CEILING)
                .intValue();
    }

    /**
     * Distance between an agent's earliest and latest daily shift start, in whole increments,
     * rounded up so that no spread is free. See {@link #consistentDailyStart}.
     */
    private int spreadIncrements(LocalTime earliest, LocalTime latest, int incrementMinutes) {
        long spreadMinutes = java.time.temporal.ChronoUnit.MINUTES.between(earliest, latest);
        if (spreadMinutes <= 0) return 0;
        return BigDecimal.valueOf(spreadMinutes)
                .divide(BigDecimal.valueOf(incrementMinutes), 0, RoundingMode.CEILING)
                .intValue();
    }

    /**
     * How far into an agent-day's shift its break starts, in minutes, or {@code null} when the
     * day has no break to place. See {@link #consistentBreakOffset} for why this is an offset
     * rather than a time of day.
     */
    private Integer breakOffsetMinutes(List<AgentAssignment> assignments) {
        LocalTime shiftStart = getShiftStart(assignments);
        if (shiftStart == null) return null;
        LocalTime breakStart = findBreakStart(assignments, deriveIncrement(assignments));
        if (breakStart == null) return null;
        return (int) java.time.temporal.ChronoUnit.MINUTES.between(shiftStart, breakStart);
    }

    /**
     * Distance between an agent's shortest and longest break offset, in whole increments,
     * rounded up so that no spread is free. See {@link #consistentBreakOffset}.
     */
    private int offsetSpreadIncrements(int earliestOffset, int latestOffset, int incrementMinutes) {
        int spreadMinutes = latestOffset - earliestOffset;
        if (spreadMinutes <= 0) return 0;
        return BigDecimal.valueOf(spreadMinutes)
                .divide(BigDecimal.valueOf(incrementMinutes), 0, RoundingMode.CEILING)
                .intValue();
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
