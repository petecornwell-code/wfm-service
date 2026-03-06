package com.wfm.solver;

import com.wfm.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Pre-assigns desk agents to assignment slots in a break-geometry-aware manner.
 *
 * <p>The standard FIRST_FIT_DECREASING construction heuristic assigns one
 * planning variable at a time without understanding that each agent needs
 * exactly one contiguous break gap. At scale (100+ agents) this creates
 * tangled initial solutions that local search cannot repair in reasonable time.
 *
 * <p>This class builds a feasible (or near-feasible) initial assignment by:
 * <ol>
 *   <li>Grouping assignments by day and timeslot</li>
 *   <li>Computing a valid break position for each agent on each day
 *       (respecting blocked window, alignment, duration)</li>
 *   <li>Distributing breaks round-robin across eligible positions</li>
 *   <li>Assigning agents to timeslot seats, skipping their break slots,
 *       preferring primary specialization matches</li>
 * </ol>
 *
 * <p>Called by {@code SolverService.startSolve()} before launching the solver.
 * Since all planning variables are pre-assigned, the construction heuristic
 * phase becomes a no-op and the solver goes straight to local search.
 */
public class BreakAwareConstructionPhase {

    /**
     * Pre-assign deskAgent values on all AgentAssignment entities in the schedule.
     * Modifies the assignments in-place.
     *
     * @return number of assignments that were successfully pre-assigned
     */
    public int preAssign(Schedule schedule) {
        List<DeskAgent> deskAgents = schedule.getDeskAgents();
        List<AgentAssignment> assignments = schedule.getAssignments();
        List<AgentDayConfig> dayConfigs = schedule.getAgentDayConfigs();
        List<AgentDayOff> daysOff = schedule.getAgentDaysOff();
        int increment = schedule.getIncrementMinutes();

        if (deskAgents.isEmpty() || assignments.isEmpty()) return 0;

        List<Timeslot> allTimeslots = schedule.getTimeslots();

        // Index day configs by (deskAgentId, date)
        Map<String, AgentDayConfig> dayConfigMap = new HashMap<>();
        for (AgentDayConfig dc : dayConfigs) {
            dayConfigMap.put(dc.deskAgentId() + "|" + dc.date(), dc);
        }

        // Index days off by (agentId, date)
        Set<String> daysOffSet = new HashSet<>();
        for (AgentDayOff dayOff : daysOff) {
            daysOffSet.add(dayOff.getAgent().getId() + "|" + dayOff.getDate());
        }

        // Group ALL schedule timeslots by date (for break position computation)
        Map<LocalDate, List<LocalTime>> allSlotTimesByDate = new LinkedHashMap<>();
        for (Timeslot ts : allTimeslots) {
            allSlotTimesByDate.computeIfAbsent(ts.getDate(), d -> new ArrayList<>())
                    .add(ts.getStartTime());
        }
        allSlotTimesByDate.values().forEach(list -> list.sort(LocalTime::compareTo));

        // Group assignments by (date, timeslot start time) → list of unassigned seats
        Map<LocalDate, TreeMap<LocalTime, List<AgentAssignment>>> assignmentsByDayAndSlot = new LinkedHashMap<>();
        for (AgentAssignment aa : assignments) {
            LocalDate date = aa.getTimeslot().getDate();
            LocalTime start = aa.getTimeslot().getStartTime();
            assignmentsByDayAndSlot
                    .computeIfAbsent(date, d -> new TreeMap<>())
                    .computeIfAbsent(start, t -> new ArrayList<>())
                    .add(aa);
        }

        int totalAssigned = 0;

        // Process each day independently
        for (Map.Entry<LocalDate, TreeMap<LocalTime, List<AgentAssignment>>> dayEntry
                : assignmentsByDayAndSlot.entrySet()) {
            LocalDate date = dayEntry.getKey();
            TreeMap<LocalTime, List<AgentAssignment>> slotMap = dayEntry.getValue();
            // Use ALL timeslots for break planning (includes slots with zero demand)
            List<LocalTime> allSlotTimes = allSlotTimesByDate.getOrDefault(date, List.of());
            List<LocalTime> demandSlotTimes = new ArrayList<>(slotMap.keySet());

            // Compute demand per slot (for break position scoring)
            Map<LocalTime, Integer> demandPerSlot = new HashMap<>();
            for (Map.Entry<LocalTime, List<AgentAssignment>> e : slotMap.entrySet()) {
                demandPerSlot.put(e.getKey(), e.getValue().size());
            }

            // Compute break plans using the full coverage window
            List<AgentBreakPlan> breakPlans = computeBreakPlans(
                    deskAgents, dayConfigMap, daysOffSet, date,
                    allSlotTimes, increment, demandPerSlot);

            // Build a map: demand timeslot start → queue of agents working that slot
            Map<LocalTime, Queue<DeskAgent>> agentQueues = new LinkedHashMap<>();
            for (LocalTime slotTime : demandSlotTimes) {
                agentQueues.put(slotTime, new LinkedList<>());
            }

            for (AgentBreakPlan plan : breakPlans) {
                for (LocalTime slotTime : demandSlotTimes) {
                    if (!plan.breakSlots.contains(slotTime)) {
                        agentQueues.get(slotTime).add(plan.deskAgent);
                    }
                }
            }

            // Assign agents to seats, preferring specialization match
            for (LocalTime slotTime : demandSlotTimes) {
                List<AgentAssignment> seats = slotMap.get(slotTime);
                Queue<DeskAgent> available = agentQueues.get(slotTime);

                for (AgentAssignment seat : seats) {
                    if (available.isEmpty()) break;

                    DeskAgent bestMatch = findBestMatch(seat, available);
                    if (bestMatch != null) {
                        available.remove(bestMatch);
                        seat.setDeskAgent(bestMatch);
                        seat.setAgent(bestMatch.getAgent());
                        totalAssigned++;
                    }
                }
            }
        }

        return totalAssigned;
    }

    private DeskAgent findBestMatch(AgentAssignment seat, Queue<DeskAgent> available) {
        UUID reqSpecId = seat.getRequiredSpecialization().getId();

        // First pass: primary specialization match
        for (DeskAgent da : available) {
            if (da.getPrimarySpecialization() != null
                    && da.getPrimarySpecialization().getId().equals(reqSpecId)) {
                return da;
            }
        }

        // Second pass: secondary specialization match
        for (DeskAgent da : available) {
            if (da.getSecondarySpecializations().stream()
                    .anyMatch(s -> s.getId().equals(reqSpecId))) {
                return da;
            }
        }

        // Fallback: any agent
        return available.peek();
    }

    /**
     * For each agent on this day, compute which timeslot start times form their break.
     * Distributes break positions round-robin across eligible positions to avoid clustering.
     */
    private List<AgentBreakPlan> computeBreakPlans(
            List<DeskAgent> deskAgents,
            Map<String, AgentDayConfig> dayConfigMap,
            Set<String> daysOffSet,
            LocalDate date,
            List<LocalTime> sortedSlotTimes,
            int increment,
            Map<LocalTime, Integer> demandPerSlot) {

        List<AgentBreakPlan> plans = new ArrayList<>();
        Map<LocalTime, Integer> breakPositionCounts = new TreeMap<>();
        List<AgentBreakCandidate> candidates = new ArrayList<>();

        for (DeskAgent da : deskAgents) {
            String dayConfigKey = da.getId() + "|" + date;
            AgentDayConfig config = dayConfigMap.get(dayConfigKey);
            if (config == null) continue;

            // Skip agents with day off
            if (daysOffSet.contains(da.getAgent().getId() + "|" + date)) continue;

            BigDecimal effectiveHours = config.effectiveHours();
            if (effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean needsBreak = effectiveHours.compareTo(config.breakMinShiftHours()) > 0;

            if (!needsBreak) {
                plans.add(new AgentBreakPlan(da, Set.of()));
                continue;
            }

            int breakSlotCount = config.breakDurationMinutes() / increment;

            List<LocalTime> eligibleStarts = findEligibleBreakStarts(
                    sortedSlotTimes, breakSlotCount, increment, config);

            if (eligibleStarts.isEmpty()) {
                // No valid break position — assign without break; constraints will penalize
                plans.add(new AgentBreakPlan(da, Set.of()));
                continue;
            }

            candidates.add(new AgentBreakCandidate(da, eligibleStarts, breakSlotCount));
        }

        // Assign break positions:
        // 1. Strongly prefer positions where ALL break slots have zero demand
        //    (breaking there costs nothing — no seats go unfilled)
        // 2. Among positions with demand, distribute evenly (round-robin)
        for (AgentBreakCandidate candidate : candidates) {
            LocalTime bestStart = null;
            int bestScore = Integer.MAX_VALUE;
            for (LocalTime start : candidate.eligibleStarts) {
                // Check if this break position has zero demand
                boolean zeroDemand = true;
                LocalTime t = start;
                for (int i = 0; i < candidate.breakSlotCount; i++) {
                    if (demandPerSlot.getOrDefault(t, 0) > 0) {
                        zeroDemand = false;
                        break;
                    }
                    t = t.plusMinutes(increment);
                }
                // Score: zero-demand positions get score 0..N (cluster count only)
                // Non-zero-demand positions get score 1_000_000 + cluster count
                int clusterCount = breakPositionCounts.getOrDefault(start, 0);
                int score = zeroDemand ? clusterCount : 1_000_000 + clusterCount;
                if (score < bestScore) {
                    bestScore = score;
                    bestStart = start;
                }
            }

            Set<LocalTime> breakSlots = new HashSet<>();
            LocalTime t = bestStart;
            for (int i = 0; i < candidate.breakSlotCount; i++) {
                breakSlots.add(t);
                t = t.plusMinutes(increment);
            }

            breakPositionCounts.merge(bestStart, 1, Integer::sum);
            plans.add(new AgentBreakPlan(candidate.deskAgent, breakSlots));
        }

        return plans;
    }

    private List<LocalTime> findEligibleBreakStarts(
            List<LocalTime> sortedSlotTimes,
            int breakSlotCount,
            int increment,
            AgentDayConfig config) {

        if (sortedSlotTimes.isEmpty()) return List.of();

        LocalTime shiftStart = sortedSlotTimes.get(0);
        LocalTime shiftEnd = sortedSlotTimes.get(sortedSlotTimes.size() - 1)
                .plusMinutes(increment);

        long blockedMinutes = config.breakBlockedHours()
                .multiply(BigDecimal.valueOf(60)).longValue();
        LocalTime blockedStartEnd = shiftStart.plusMinutes(blockedMinutes);
        LocalTime blockedEndStart = shiftEnd.minusMinutes(blockedMinutes);

        Set<LocalTime> slotTimeSet = new HashSet<>(sortedSlotTimes);
        List<LocalTime> eligible = new ArrayList<>();

        for (int i = 0; i <= sortedSlotTimes.size() - breakSlotCount; i++) {
            LocalTime candidateStart = sortedSlotTimes.get(i);
            LocalTime candidateEnd = candidateStart.plusMinutes((long) breakSlotCount * increment);

            if (candidateStart.isBefore(blockedStartEnd)) continue;
            if (candidateEnd.isAfter(blockedEndStart)) continue;
            if (!isAligned(candidateStart, config.breakStartAlignment())) continue;

            boolean allPresent = true;
            LocalTime t = candidateStart;
            for (int j = 0; j < breakSlotCount; j++) {
                if (!slotTimeSet.contains(t)) {
                    allPresent = false;
                    break;
                }
                t = t.plusMinutes(increment);
            }
            if (!allPresent) continue;

            eligible.add(candidateStart);
        }

        return eligible;
    }

    private boolean isAligned(LocalTime time, BreakAlignment alignment) {
        int minute = time.getMinute();
        return switch (alignment) {
            case ON_HOUR -> minute == 0;
            case ON_HALF_HOUR -> minute == 0 || minute == 30;
            case ON_QUARTER_HOUR -> minute % 15 == 0;
        };
    }

    private record AgentBreakPlan(DeskAgent deskAgent, Set<LocalTime> breakSlots) {}

    private record AgentBreakCandidate(
            DeskAgent deskAgent,
            List<LocalTime> eligibleStarts,
            int breakSlotCount) {}
}
