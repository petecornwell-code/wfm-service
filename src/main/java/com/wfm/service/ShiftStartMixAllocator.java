package com.wfm.service;

import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftStartMixTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns a computed shift-start mix into the thing that actually binds: a narrowed value range per
 * working agent-day.
 *
 * <p><b>Why narrowing and not a weight.</b> A weighted steer toward the same target is measured to
 * move the solved mix by exactly nothing — soft 25 through 100,000, and {@code ofHard(1)}, where
 * the solver absorbs the penalty rather than move one agent-day (see {@code
 * ShiftStartMixSteerTest}). The construction heuristic fixes the mix and nothing afterwards
 * revises it, because revising it means re-pointing an envelope AND the ~9 seats filling it while
 * {@code solverConfig.xml}'s {@code 0hard} annealing temperature refuses every intermediate state.
 * Pricing a decision the search cannot revisit achieves nothing. Removing the wrong values from
 * the range achieves it by construction.
 *
 * <p><b>Start time only.</b> Rows are narrowed to the envelopes STARTING at their allocated time,
 * never to a single envelope. Break-band choice is a real coverage trade-off, and unlike coverage
 * it is live during CH phase 1 — {@code breakClustering}'s on-break side joins
 * {@link AgentShiftAssignment} to {@code Timeslot} and needs no seats. The CH is a competent
 * band-chooser and a blind mix-chooser; this takes away only what it is blind about. Where two
 * templates share a start and differ in span, both survive narrowing and the net-hours filter
 * decides between them.
 *
 * <p><b>All or nothing, per schedule.</b> If any row would end up with an empty range the whole
 * allocation is abandoned, not just that date. {@code AgentShiftAssignment.shiftBandPair} is
 * declared {@code allowsUnassigned = true}, so an empty range does not throw — the row silently
 * stays unassigned, its seats then cannot comply with an envelope that does not exist, and
 * contracted hours go under. Silent, and far from its cause. Nothing is written to any row until
 * every row has been checked.
 */
@Service
public class ShiftStartMixAllocator {

    private static final Logger log = LoggerFactory.getLogger(ShiftStartMixAllocator.class);

    /** What an allocation did, so the caller can log it and tests can assert on it. */
    public record Allocation(int datesAllocated, int rowsNarrowed, boolean applied, String skippedReason) {
        public static Allocation notApplied(String reason) {
            return new Allocation(0, 0, false, reason);
        }
    }

    /**
     * Narrows every working agent-day's value range to its allocated start time.
     *
     * <p>Only called in {@code ENFORCE} mode, and only with targets that
     * {@link ShiftStartMixTargetService} chose to emit — which already means SHIFT mode, one
     * substitutability class, a date carrying demand, and a date whose rows share one
     * eligible-envelope set.
     */
    public Allocation allocate(List<AgentShiftAssignment> rows,
                               List<ShiftStartMixTarget> targets,
                               List<ResolvedUsualShiftTarget> usualTargets) {
        if (rows == null || rows.isEmpty() || targets == null || targets.isEmpty()) {
            return Allocation.notApplied("no rows or no targets");
        }

        Map<LocalDate, Map<LocalTime, Integer>> targetsByDate = new TreeMap<>();
        for (ShiftStartMixTarget t : targets) {
            targetsByDate.computeIfAbsent(t.date(), k -> new LinkedHashMap<>())
                    .merge(t.startTime(), t.targetCount(), Integer::sum);
        }

        Map<UUID, Map<LocalDate, LocalTime>> usualByAgentDate = new HashMap<>();
        for (ResolvedUsualShiftTarget t : usualTargets) {
            usualByAgentDate.computeIfAbsent(t.agentId(), k -> new HashMap<>())
                    .put(t.date(), t.usualStartTime());
        }

        Map<LocalDate, List<AgentShiftAssignment>> rowsByDate = new TreeMap<>();
        for (AgentShiftAssignment sa : rows) {
            rowsByDate.computeIfAbsent(sa.getDate(), k -> new ArrayList<>()).add(sa);
        }

        // Staged, never written through: an empty range discovered on the last date must not leave
        // the first date's rows already narrowed.
        Map<AgentShiftAssignment, List<ShiftBandPair>> staged = new IdentityHashMap<>();
        int datesAllocated = 0;

        for (Map.Entry<LocalDate, List<AgentShiftAssignment>> e : rowsByDate.entrySet()) {
            LocalDate date = e.getKey();
            List<AgentShiftAssignment> dateRows = e.getValue();
            Map<LocalTime, Integer> mix = targetsByDate.get(date);
            if (mix == null) {
                continue; // a date the target service declined — leave it entirely alone
            }

            int total = mix.values().stream().mapToInt(Integer::intValue).sum();
            if (total != dateRows.size()) {
                return Allocation.notApplied(String.format(
                        "%s targets total %d but %d agent-days work that date", date, total, dateRows.size()));
            }

            // Independently re-checked rather than inherited from the target service's guard: the
            // cost of being wrong here is a row narrowed to envelopes it cannot legally hold, which
            // surfaces only as an unassigned row much later.
            List<ShiftBandPair> dateRange = dateRows.get(0).getEligibleShiftBandPairs();
            for (AgentShiftAssignment row : dateRows) {
                if (!dateRange.equals(row.getEligibleShiftBandPairs())) {
                    return Allocation.notApplied(date + " agent-days do not share one eligible-envelope set");
                }
            }

            Map<LocalTime, List<ShiftBandPair>> byStart = new LinkedHashMap<>();
            for (ShiftBandPair pair : dateRange) {
                byStart.computeIfAbsent(pair.template().getStartTime(), k -> new ArrayList<>()).add(pair);
            }

            LocalTime[] offered = new LocalTime[dateRows.size()];
            int at = 0;
            for (Map.Entry<LocalTime, Integer> m : mix.entrySet()) {
                for (int i = 0; i < m.getValue(); i++) {
                    offered[at++] = m.getKey();
                }
            }
            LocalTime[] wanted = new LocalTime[dateRows.size()];
            for (int i = 0; i < dateRows.size(); i++) {
                // null means no preference — such an agent sits out pass 1 and so can never
                // displace a preference-holder. That is the whole reason this is shared with the
                // repair rather than reimplemented.
                wanted[i] = usualByAgentDate.getOrDefault(dateRows.get(i).getAgent().getId(), Map.of()).get(date);
            }

            int[] pi = ScheduleConsistencyRepairService.matchAgentsToStarts(wanted, offered);
            for (int i = 0; i < dateRows.size(); i++) {
                LocalTime start = offered[pi[i]];
                List<ShiftBandPair> narrowed = byStart.get(start);
                if (narrowed == null || narrowed.isEmpty()) {
                    return Allocation.notApplied(String.format(
                            "%s start %s has no eligible envelope to narrow to", date, start));
                }
                staged.put(dateRows.get(i), narrowed);
            }
            datesAllocated++;
        }

        if (staged.isEmpty()) {
            return Allocation.notApplied("no date produced an allocation");
        }
        staged.forEach(AgentShiftAssignment::setAllocatedShiftBandPairs);
        log.info("Shift-start mix ENFORCED — {} agent-day(s) across {} date(s) narrowed to their "
                + "allocated start time; the construction heuristic can no longer build a different mix",
                staged.size(), datesAllocated);
        return new Allocation(datesAllocated, staged.size(), true, null);
    }
}
