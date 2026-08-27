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

    // ------------------------------------------------------------------
    //  Shared grouping building blocks
    //
    //  Timefold shares a constraint-stream node between constraints only when they are
    //  built from *identical* building blocks, and it compares lambdas by instance. An
    //  inline `a -> a.getAgent().getId()` written in five places is five distinct objects,
    //  so five separate agent-day groupings were being built and maintained, each with its
    //  own map and its own list, all recomputed on every move that touched an agent-day.
    //
    //  Hoisting them to single instances lets those collapse into one shared node per
    //  distinct grouping: five constraints now share (agentId, date, toList) and two share
    //  (agentId, date, count). Purely a cost change — the grouping semantics are unchanged,
    //  and the full suite is green across it.
    //
    //  Measured on BreakAwareConstructionTest's 30-agent 30-minute scenario, move
    //  evaluation speed: 32,489/sec before, 44,000+/sec after. oneAssignmentPerTimeslot
    //  deliberately keeps its own inline lambdas — it groups by timeslot id rather than
    //  date, so it cannot share this node whatever the instances are.
    // ------------------------------------------------------------------

    private static final java.util.function.Function<AgentAssignment, UUID> AGENT_ID =
            a -> a.getAgent().getId();

    private static final java.util.function.Function<AgentAssignment, java.time.LocalDate> DATE =
            a -> a.getTimeslot().getDate();

    private static final ai.timefold.solver.core.api.score.stream.uni.UniConstraintCollector<
            AgentAssignment, ?, List<AgentAssignment>> TO_LIST = toList();

    private static final ai.timefold.solver.core.api.score.stream.uni.UniConstraintCollector<
            AgentAssignment, ?, Integer> COUNT = count();

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
            shiftEnvelopeCompliance(factory),
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
     *
     * <p>(Phase 15, ENVL-05) <b>Mode-gated off for shift-scheduled desks</b> — reclassified
     * {@code MODE_GATED} in {@link ScheduleConstraintClassification}. In shift mode a break's
     * position is the assigned band's offset, not something to discover from assignment gaps, so
     * there is no longer a question for this constraint to answer; it is simply inert. The body
     * below is untouched byte-for-byte (P-25) — only an {@code ifExists} gate is added.
     * <b>Note on mechanism:</b> the plan's own sketch called for an added {@code .join(
     * ScheduleConfig.class)} plus a filter, mirroring {@link #shiftEnvelopeCompliance}'s shape —
     * but this constraint's stream is already a 4-tuple (Quad) by the time it reaches this point,
     * and Timefold 1.16.0's public Constraint Streams API has no 5-tuple (Penta) stream type to
     * join into. {@code ifExists(ScheduleConfig.class, filtering(...))} achieves the identical
     * effect (gate on the mode singleton) without growing tuple arity, so the existing filter/
     * penalize lambdas below need no signature change at all — an even smaller diff than the
     * plan's sketch, not a larger one. The gate reads {@code != SHIFT} rather than {@code == SLOT}
     * so a never-set {@code ScheduleConfig.schedulingMode()} (every pre-Phase-15 test fixture that
     * builds a {@code Schedule} without calling {@code setSchedulingMode}, e.g.
     * {@code BreakAwareConstructionTest}) still resolves to "active", matching this constraint's
     * behaviour before this phase and keeping that test's own assertions genuinely exercising break
     * geometry rather than silently disabling it.
     */
    Constraint exactlyOneBreak(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(AGENT_ID, DATE, TO_LIST)
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .ifExists(ScheduleConfig.class,
                        filtering((daId, date, assignments, dayConfig, cfg) ->
                                cfg.schedulingMode() != SchedulingMode.SHIFT))
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
     *
     * <p>(Phase 15, ENVL-05) Mode-gated off for shift-scheduled desks — see
     * {@link #exactlyOneBreak}'s javadoc for the reclassification reasoning and the
     * {@code ifExists}-not-{@code join} mechanism note (Timefold has no Penta stream).
     */
    Constraint breakDuration(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(AGENT_ID, DATE, TO_LIST)
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .ifExists(ScheduleConfig.class,
                        filtering((daId, date, assignments, dayConfig, cfg) ->
                                cfg.schedulingMode() != SchedulingMode.SHIFT))
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
     *
     * <p>(Phase 15, ENVL-05) Mode-gated off for shift-scheduled desks — see
     * {@link #exactlyOneBreak}'s javadoc for the reclassification reasoning and the
     * {@code ifExists}-not-{@code join} mechanism note (Timefold has no Penta stream).
     */
    Constraint breakBlockedWindow(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(AGENT_ID, DATE, TO_LIST)
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .ifExists(ScheduleConfig.class,
                        filtering((daId, date, assignments, dayConfig, cfg) ->
                                cfg.schedulingMode() != SchedulingMode.SHIFT))
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
     *
     * <p>(Phase 15, ENVL-05) Mode-gated off for shift-scheduled desks — see
     * {@link #exactlyOneBreak}'s javadoc for the reclassification reasoning and the
     * {@code ifExists}-not-{@code join} mechanism note (Timefold has no Penta stream).
     */
    Constraint breakStartAlignment(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(AGENT_ID, DATE, TO_LIST)
                .join(AgentDayConfig.class,
                        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                        equal((daId, date, assignments) -> date, AgentDayConfig::date))
                .ifExists(ScheduleConfig.class,
                        filtering((daId, date, assignments, dayConfig, cfg) ->
                                cfg.schedulingMode() != SchedulingMode.SHIFT))
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
     * (Phase 15, ENVL-02) Shift envelope compliance — the hard constraint the whole Option A
     * coupling rests on (SPIKE-COUPLING.md). Plain positive-join form (CH-friendly, matching
     * this file's existing convention) rather than {@code ifNotExists}: penalises only a
     * definite disagreement between two initialised variables, which is what keeps the
     * construction heuristic able to make progress.
     *
     * <p>A {@code null} chosen {@link AgentShiftAssignment#getShiftBandPair()} forbids every
     * seat that agent-day through the {@code sa.getShiftBandPair() == null} branch below — no
     * separate "unassigned shift" constraint is needed (D-06); {@code contractedHoursUnder}/
     * {@code contractedHoursUnderZero} already penalise the resulting emptiness. This requires
     * joining against {@link ConstraintFactory#forEachIncludingUnassigned}, not the plain
     * {@code .join(AgentShiftAssignment.class, ...)} shorthand — RESEARCH.md's Open Question 2
     * left open whether the plain join's default unassigned-filtering (every {@code forEach}/
     * {@code join(Class, ...)} call silently drops planning entities with a null genuine
     * variable) would defeat the null branch below; {@code ShiftEnvelopeComplianceConstraintTest}
     * proved it does, so the explicit "including unassigned" stream is load-bearing, not
     * decorative.
     *
     * <p>Doubly inert on a SLOT-scheduled desk: {@code SolverService} only ever populates
     * {@link AgentShiftAssignment} rows for a SHIFT-mode desk, so the join below finds nothing to
     * match against there; the explicit {@code SchedulingMode.SHIFT} filter is defence in depth
     * on top of that structural fact, keeping the constraint provably silent even if a shift row
     * were ever present alongside a SLOT {@link ScheduleConfig}.
     *
     * <p><b>Stream order is a performance contract, not a style choice.</b> This constraint must
     * lead with the (empty-in-SLOT-mode) {@link AgentShiftAssignment} stream and apply the
     * {@code SchedulingMode.SHIFT} gate BEFORE touching {@link AgentAssignment}. The original
     * 15-03 form led with {@code forEach(AgentAssignment.class).filter(a -> a.getAgent() != null)}
     * and joined the shift rows second, which put all 480 seat entities of a slot-mode solve
     * through an extra filter node and a two-key join index that could never produce a match.
     * Measured on {@code BreakAwareConstructionTest}'s 30-agent slot-mode scenario, that cost
     * roughly 3x construction-heuristic throughput (1049ms -> 347ms for the same 480 steps) and
     * ~40% of local-search throughput — enough to drop a wall-clock-bounded solve from
     * {@code 0hard} to {@code -100hard} and worse. Leading with the shift stream keeps the whole
     * node network dead on a slot-mode desk. Do not reorder these joins.
     *
     * <p>Joining {@code AgentAssignment.class} directly (rather than filtering an
     * {@code AgentAssignment} lead stream) also subsumes the old explicit
     * {@code a.getAgent() != null} guard: every {@code forEach}/{@code join(Class, ...)} silently
     * drops planning entities whose genuine variable is null, which is exactly what that filter
     * did, so {@code a.getAgent().getId()} below stays safe.
     */
    // Package-private so ConstraintVerifier can target this constraint in isolation
    // (mirrors bulkUnderallocationSoft/minimumStaffing's precedent in this file).
    Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
                .join(ScheduleConfig.class)
                .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
                .join(AgentAssignment.class,
                        equal((sa, cfg) -> sa.getAgent().getId(), a -> a.getAgent().getId()),
                        equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
                .filter((sa, cfg, a) -> sa.getShiftBandPair() == null
                        || !sa.getShiftBandPair().covers(a.getTimeslot()))
                .penalizeConfigurable()
                .asConstraint("Shift envelope compliance");
    }

    /**
     * 12a. Contracted hours (over) — penalises agents assigned MORE than their
     * exact effective contracted hours. Agents must work exactly their contracted
     * hours per day.
     */
    private Constraint contractedHoursOver(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .groupBy(AGENT_ID, DATE, COUNT)
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
                .groupBy(AGENT_ID, DATE, COUNT)
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
     * 9. Honour preferred start time — penalise assigning an agent to a
     * timeslot before their preferred start time for that day.
     * Preferences are pre-resolved (weekly vs standing) by SolverService,
     * so all preferences have an exact date set.
     *
     * <p>(Phase 15, ENVL-05/P-26) <b>Mode-gated off for shift-scheduled desks</b> — reclassified
     * {@code MODE_GATED} in {@link ScheduleConstraintClassification} (moved from
     * {@code OPEN_RESOLVE_IN_PHASE_15}, {@code PHASE_15_OWNER} retained verbatim as the recorded
     * resolver, P-27). In shift mode the start comes from the assigned library shift, not a
     * per-slot solver decision, so this constraint would tune against a signal the operator no
     * longer controls per-slot; Phase 17's CONS-05 use of {@code preferredStartTime} at shift
     * granularity is a new use, not a reason to leave this per-slot constraint on. Same
     * {@code != SHIFT} null-safe gate as {@link #exactlyOneBreak} (unset {@code schedulingMode}
     * resolves to "active", matching every pre-Phase-15 fixture's implicit slot-mode behaviour).
     */
    Constraint honourPreferredStartTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                .join(AgentPreference.class,
                        equal(a -> a.getAgent().getId(), p -> p.getAgent().getId()),
                        equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))
                .ifExists(ScheduleConfig.class,
                        filtering((a, p, cfg) -> cfg.schedulingMode() != SchedulingMode.SHIFT))
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
     *
     * <p>(Phase 15, ENVL-05/P-26) Mode-gated off for shift-scheduled desks — see
     * {@link #honourPreferredStartTime}'s javadoc for the reclassification reasoning, and
     * {@link #exactlyOneBreak}'s javadoc for the {@code ifExists}-not-{@code join} mechanism note
     * (this constraint's stream is already Quad, so a literal {@code join(ScheduleConfig.class)}
     * would need a nonexistent Penta stream).
     */
    Constraint honourPreferredBreakTime(ConstraintFactory factory) {
        return factory.forEach(AgentAssignment.class)
                .filter(a -> a.getAgent() != null)
                // Keyed on the agent id rather than the Agent so this joins the shared
                // grouping node above. The Agent was only ever used for its id.
                .groupBy(AGENT_ID, DATE, TO_LIST)
                .join(AgentPreference.class,
                        equal((agentId, date, assignments) -> agentId,
                                p -> p.getAgent().getId()),
                        equal((agentId, date, assignments) -> date,
                                AgentPreference::getDate))
                .ifExists(ScheduleConfig.class,
                        filtering((agentId, date, assignments, pref, cfg) ->
                                cfg.schedulingMode() != SchedulingMode.SHIFT))
                .filter((agentId, date, assignments, pref) -> {
                    if (pref.getPreferredBreakTime() == null) return false;
                    int increment = deriveIncrement(assignments);
                    LocalTime breakStart = findBreakStart(assignments, increment);
                    if (breakStart == null) return false;
                    return !breakStart.equals(pref.getPreferredBreakTime());
                })
                .penalizeConfigurable((agentId, date, assignments, pref) -> 1)
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
