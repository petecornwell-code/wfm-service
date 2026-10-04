package com.wfm.service;

import com.wfm.model.AcceptedScheduleDate;
import com.wfm.model.AcceptedScheduleDateStatus;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.RestSpan;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.repository.AcceptedScheduleDateRepository;
import com.wfm.repository.AgentAssignmentRepository;
import com.wfm.repository.AgentShiftAssignmentRepository;
import com.wfm.repository.ScheduleRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST-05/D-10: resolves the agent's real pre-horizon rest span(s) for the single business date
 * immediately before a solving period's first day, from ACCEPTED history only.
 *
 * <p><b>Exactly one business date back, never more.</b> D-05 bounds a desk's configured
 * {@code minimumRestMinutes} below 24 hours, so a shift two business dates before the period
 * start ends no later than the start of business day D-1 and is therefore at least one whole
 * business day away from any shift on the period's first day — it can never be in range of the
 * rest comparison. This is strictly TIGHTER than the ROADMAP's standing instruction to bound a
 * horizon-edge lookback to the maximum configured rest period across desks, and satisfies it: the
 * bound used here is one day, not one maximum-rest window.
 *
 * <p><b>At most three queries, regardless of agent count</b> — one accepted-date check, one
 * predecessor-schedule load, and one date-filtered row read. There is no per-agent query
 * anywhere on this path; the two new repository reads this class depends on
 * ({@link AgentShiftAssignmentRepository#findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate}
 * and
 * {@link AgentAssignmentRepository#findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate})
 * are each a single batched, date-filtered call.
 *
 * <p><b>The predecessor's own snapshotted anchor and mode, never the desk's current values.</b>
 * The anchor is part of that historical schedule's identity — a desk re-anchored since would
 * otherwise reinterpret the historical shift's instants (the same argument D-14 and the
 * {@code dayStart} snapshot already make). The mode, because what an agent actually worked does
 * not change when the desk's mode does, so a SLOT-scheduled prior period must correctly
 * constrain a SHIFT-scheduled new one; reading the desk's current mode here would silently
 * return the wrong shape after a mode switch. This class therefore reads
 * {@code getSchedulingMode()}/{@code getDayStart()} only from the loaded predecessor
 * {@link Schedule}, and never from a {@code Desk} — it has no {@code DeskRepository} dependency
 * at all.
 */
@Service
public class RestPredecessorService {

    private final AcceptedScheduleDateRepository acceptedScheduleDateRepository;
    private final ScheduleRepository scheduleRepository;
    private final AgentShiftAssignmentRepository agentShiftAssignmentRepository;
    private final AgentAssignmentRepository agentAssignmentRepository;

    public RestPredecessorService(AcceptedScheduleDateRepository acceptedScheduleDateRepository,
                                   ScheduleRepository scheduleRepository,
                                   AgentShiftAssignmentRepository agentShiftAssignmentRepository,
                                   AgentAssignmentRepository agentAssignmentRepository) {
        this.acceptedScheduleDateRepository = acceptedScheduleDateRepository;
        this.scheduleRepository = scheduleRepository;
        this.agentShiftAssignmentRepository = agentShiftAssignmentRepository;
        this.agentAssignmentRepository = agentAssignmentRepository;
    }

    /**
     * Resolves this desk's real pre-horizon rest spans for the business date immediately before
     * {@code periodStartDate}.
     *
     * @param tenantId tenant scope — carried through to every repository call.
     * @param deskId desk scope — carried through to every repository call.
     * @param periodStartDate the solving period's first business date. The lookback date is
     *        always exactly {@code periodStartDate.minusDays(1)}.
     * @param minimumRestMinutes the desk's snapshotted {@code minimumRestMinutes}. {@code null}
     *        short-circuits to an empty list with zero queries (REST-04) — an unconfigured desk
     *        must never pay for a query whose result would be discarded.
     * @param currentDayStart the anchor of the schedule currently being built — compared against
     *        the predecessor's own snapshotted anchor in step 7 below. Taken as a parameter
     *        (rather than re-derived from a {@code Desk} lookup) so this divergence check is
     *        fully exercised by this class's own unit tests rather than only reachable from a
     *        live solve.
     * @param warnings the schedule's shared warnings channel — the same list
     *        {@code SolverService.requireShiftEnvelopeSeatSupply} already writes advisories into,
     *        so a degraded lookback surfaces on the one operator-facing channel rather than a
     *        second one.
     * @return the resolved pre-horizon spans, or an empty list when there is nothing to resolve
     *         or nothing could be resolved safely.
     */
    public List<RestSpan> resolvePriorSpans(long tenantId, UUID deskId, LocalDate periodStartDate,
            Integer minimumRestMinutes, LocalTime currentDayStart, List<String> warnings) {
        // 1. REST-04: an unconfigured desk issues zero queries, not a query whose result is
        // discarded.
        if (minimumRestMinutes == null) {
            return List.of();
        }

        // 2. One business date back is provably sufficient (see class javadoc / D-05).
        LocalDate lookbackDate = periodStartDate.minusDays(1);

        // 3. Is the lookback date ACCEPTED? One call, a single-element date collection.
        List<AcceptedScheduleDate> accepted = acceptedScheduleDateRepository
                .findByTenantIdAndDeskIdAndDateInAndStatus(
                        tenantId, deskId, List.of(lookbackDate), AcceptedScheduleDateStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            // D-10: a business date with no ACCEPTED predecessor is explicitly unconstrained —
            // the normal state for a first-ever period. No warning here: a warning on the normal
            // case would train operators to ignore the channel.
            return List.of();
        }

        // 4. Load the predecessor schedule once, tenant- and desk-scoped.
        UUID predecessorScheduleId = accepted.get(0).getScheduleId();
        Optional<Schedule> predecessorScheduleOpt = scheduleRepository
                .findByIdAndTenantIdAndDeskId(predecessorScheduleId, tenantId, deskId);
        if (predecessorScheduleOpt.isEmpty()) {
            warnings.add("Rest lookback: accepted schedule " + predecessorScheduleId + " for "
                    + lookbackDate + " could not be loaded; the pre-horizon rest check is "
                    + "unconstrained for that date.");
            return List.of();
        }
        Schedule predecessorSchedule = predecessorScheduleOpt.get();

        // 5. The predecessor's own snapshotted anchor and mode (see class javadoc).
        LocalTime predecessorDayStart = predecessorSchedule.getDayStart();
        SchedulingMode predecessorMode = predecessorSchedule.getSchedulingMode();

        // 6. Branch on the predecessor's own mode and issue exactly one row read.
        List<RestSpan> spans;
        if (predecessorMode == SchedulingMode.SHIFT) {
            spans = resolveShiftSpans(tenantId, deskId, predecessorScheduleId, lookbackDate,
                    predecessorDayStart, warnings);
        } else {
            spans = resolveSlotSpans(tenantId, deskId, predecessorScheduleId, lookbackDate,
                    predecessorDayStart, warnings);
        }

        // 7. Drop any span whose anchor differs from the schedule the caller is building.
        // RestSpan.gapMinutes throws on unequal anchors, and a throw inside the score
        // calculation would abort the solve rather than surfacing a usable error — this filter
        // converts that unreachable invariant violation into a loud degradation instead.
        // Unreachable today while DeskService.setDayStart keeps its permanent ACCEPTED-schedule
        // refusal (an ACCEPTED predecessor is proof the desk's anchor cannot have changed since),
        // so this branch exists purely so a future bypass of that refusal fails visibly here
        // rather than aborting a solve.
        if (!spans.isEmpty() && !predecessorDayStart.equals(currentDayStart)) {
            warnings.add("Rest lookback: accepted schedule " + predecessorScheduleId + " for "
                    + lookbackDate + " was anchored at " + predecessorDayStart
                    + ", which differs from this schedule's anchor " + currentDayStart
                    + "; the pre-horizon rest check is unconstrained for that date.");
            return List.of();
        }

        return spans;
    }

    private List<RestSpan> resolveShiftSpans(long tenantId, UUID deskId, UUID predecessorScheduleId,
            LocalDate lookbackDate, LocalTime predecessorDayStart, List<String> warnings) {
        List<AgentShiftAssignment> rows = agentShiftAssignmentRepository
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                        tenantId, deskId, predecessorScheduleId, lookbackDate);
        List<RestSpan> spans = new ArrayList<>();
        for (AgentShiftAssignment row : rows) {
            LocalTime start = row.getShiftStartTime();
            LocalTime end = row.getShiftEndTime();
            if (start == null || end == null) {
                warnings.add("Rest lookback: accepted shift for agent " + row.getAgent().getId()
                        + " on " + lookbackDate + " has no denormalised start/end time; skipped.");
                continue;
            }
            // Do NOT call RestSpan.ofShift here: that factory reads the live planning variable's
            // template, which is null on a persisted row. The denormalised shiftStartTime/
            // shiftEndTime columns are the authoritative source here precisely because a later
            // updateShiftTemplate edit must not be able to rewrite what history says the agent
            // actually worked (D-10, mirroring ScheduleService's own accept-time denormalisation
            // comment).
            spans.add(new RestSpan(row.getAgent().getId(), row.getDate(), start, end, predecessorDayStart));
        }
        return spans;
    }

    private List<RestSpan> resolveSlotSpans(long tenantId, UUID deskId, UUID predecessorScheduleId,
            LocalDate lookbackDate, LocalTime predecessorDayStart, List<String> warnings) {
        List<AgentAssignment> rows = agentAssignmentRepository
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                        tenantId, deskId, predecessorScheduleId, lookbackDate);
        // Group by agent, preserving encounter order, then derive one RestSpan per agent via the
        // same RestSpan.ofSlots the in-horizon SLOT constraint uses (D-02) — one shared span
        // derivation, never a second one for the pre-horizon case.
        Map<UUID, List<AgentAssignment>> byAgent = new LinkedHashMap<>();
        for (AgentAssignment row : rows) {
            if (row.getAgent() == null) {
                continue;
            }
            byAgent.computeIfAbsent(row.getAgent().getId(), id -> new ArrayList<>()).add(row);
        }
        List<RestSpan> spans = new ArrayList<>();
        for (Map.Entry<UUID, List<AgentAssignment>> entry : byAgent.entrySet()) {
            spans.add(RestSpan.ofSlots(entry.getKey(), lookbackDate, entry.getValue(), predecessorDayStart));
        }
        return spans;
    }
}
