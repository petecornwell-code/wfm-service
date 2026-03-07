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
 *   <li>Assigning agents to timeslot seats respecting contracted hours limits,
 *       skipping their break slots, preferring primary specialization matches</li>
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

            // Compute each agent's working slot times (contiguous block around break,
            // limited to their contracted hours).
            // Cap per-agent work slots so total supply doesn't exceed total demand.
            // Floor division ensures agents' shift windows are shorter than the full
            // day, enabling natural staggering (some agents 09-16, others 10-17).
            // This keeps breaks interior even when edge slots are over-subscribed.
            int totalDemandSlots = slotMap.values().stream().mapToInt(List::size).sum();
            int maxWorkSlotsPerAgent = breakPlans.isEmpty() ? Integer.MAX_VALUE
                    : totalDemandSlots / breakPlans.size();

            // Process sequentially with a shared remainingDemand map so that each
            // agent's window is steered toward the slots that still need coverage.
            Map<LocalTime, Integer> remainingDemand = new HashMap<>(demandPerSlot);
            Map<UUID, Set<LocalTime>> agentWorkSlots = new HashMap<>();
            for (AgentBreakPlan plan : breakPlans) {
                Set<LocalTime> workSlots = computeWorkSlots(
                        plan, demandSlotTimes, allSlotTimes, increment, dayConfigMap, date,
                        remainingDemand, maxWorkSlotsPerAgent);
                agentWorkSlots.put(plan.deskAgent.getId(), workSlots);
            }

            // --- Trim work slots to match demand ---
            // When total supply > total demand, trim agent work slots from
            // shift edges (preserving contiguity).
            trimWorkSlotsToMatchDemand(
                    breakPlans, agentWorkSlots, demandSlotTimes, slotMap, increment);

            // --- Agent-first assignment ---
            // Assign each agent to seats across their contiguous (trimmed) work slots.
            Map<UUID, Integer> assignmentCounts = new HashMap<>();
            Map<UUID, Integer> maxSlots = new HashMap<>();
            for (AgentBreakPlan plan : breakPlans) {
                Set<LocalTime> workSlots = agentWorkSlots.get(plan.deskAgent.getId());
                maxSlots.put(plan.deskAgent.getId(), workSlots.size());
            }

            // Two-pass assignment: first assign break-adjacent slots for ALL agents
            // (critical for correct break geometry), then fill remaining work slots.
            // This prevents later agents from finding break-adjacent seats stolen
            // by earlier agents' non-critical slot assignments.

            // Precompute break-adjacent slots per agent
            Map<UUID, Set<LocalTime>> breakAdjacentByAgent = new HashMap<>();
            for (AgentBreakPlan plan : breakPlans) {
                Set<LocalTime> breakAdjacent = new HashSet<>();
                for (LocalTime bs : plan.breakSlots) {
                    LocalTime before = bs.minusMinutes(increment);
                    LocalTime after = bs.plusMinutes(increment);
                    if (!plan.breakSlots.contains(before)) breakAdjacent.add(before);
                    if (!plan.breakSlots.contains(after)) breakAdjacent.add(after);
                }
                breakAdjacentByAgent.put(plan.deskAgent.getId(), breakAdjacent);
            }

            // Pass 1: break-adjacent slots only
            for (AgentBreakPlan plan : breakPlans) {
                DeskAgent da = plan.deskAgent;
                Set<LocalTime> workSlots = agentWorkSlots.get(da.getId());
                Set<LocalTime> breakAdjacent = breakAdjacentByAgent.get(da.getId());

                for (LocalTime slotTime : demandSlotTimes) {
                    if (!workSlots.contains(slotTime)) continue;
                    if (!breakAdjacent.contains(slotTime)) continue;

                    List<AgentAssignment> seats = slotMap.get(slotTime);
                    AgentAssignment bestSeat = findBestSeat(da, seats);
                    if (bestSeat != null) {
                        bestSeat.setDeskAgent(da);
                        bestSeat.setAgent(da.getAgent());
                        assignmentCounts.merge(da.getId(), 1, Integer::sum);
                        totalAssigned++;
                    }
                }
            }

            // Pass 2: remaining work slots
            for (AgentBreakPlan plan : breakPlans) {
                DeskAgent da = plan.deskAgent;
                Set<LocalTime> workSlots = agentWorkSlots.get(da.getId());
                Set<LocalTime> breakAdjacent = breakAdjacentByAgent.get(da.getId());

                for (LocalTime slotTime : demandSlotTimes) {
                    if (!workSlots.contains(slotTime)) continue;
                    if (breakAdjacent.contains(slotTime)) continue; // already done in pass 1

                    List<AgentAssignment> seats = slotMap.get(slotTime);
                    AgentAssignment bestSeat = findBestSeat(da, seats);
                    if (bestSeat != null) {
                        bestSeat.setDeskAgent(da);
                        bestSeat.setAgent(da.getAgent());
                        assignmentCounts.merge(da.getId(), 1, Integer::sum);
                        totalAssigned++;
                    }
                }
            }
            // --- Repair break geometry ---
            // After agent-first assignment, some agents may have multiple interior
            // gaps (scattered assignments). Detect and fix by un-assigning isolated
            // slots that are separated from the break by extra gaps, keeping only
            // the contiguous blocks adjacent to the planned break.
            totalAssigned -= repairBreakGeometry(breakPlans, slotMap, demandSlotTimes,
                    increment, assignmentCounts);

            // --- Mop-up pass ---
            // Fill remaining unassigned seats by extending agents' shifts.
            // Prefers agents whose existing shift is adjacent to the slot
            // (extending their shift by one slot avoids creating extra gaps).
            // This prevents the default CH from making break-unaware assignments.
            totalAssigned += mopUpUnassigned(
                    slotMap, demandSlotTimes, breakPlans, agentWorkSlots,
                    assignmentCounts, maxSlots, dayConfigMap, daysOffSet, date, increment);

            // --- Final break geometry cleanup ---
            // After mop-up, some agents may still have multiple gaps if mop-up
            // extended them non-contiguously. Run repair again — any remaining
            // multi-gap agents will have their outer blocks removed.
            totalAssigned -= repairBreakGeometry(breakPlans, slotMap, demandSlotTimes,
                    increment, assignmentCounts);
        }

        return totalAssigned;
    }

    /**
     * Compute which demand slot times an agent should work, limited to their
     * contracted hours. Picks a contiguous block of work slots around the break
     * position to form a realistic shift pattern.
     *
     * <p>Uses a shared {@code remainingDemand} map (mutated in-place) so that
     * each agent's window is steered toward the slots that still need coverage.
     * This prevents all agents from clustering their windows in the middle of
     * the day when coverage is wider than contracted hours.
     */
    private Set<LocalTime> computeWorkSlots(
            AgentBreakPlan plan,
            List<LocalTime> demandSlotTimes,
            List<LocalTime> allSlotTimes,
            int increment,
            Map<String, AgentDayConfig> dayConfigMap,
            LocalDate date,
            Map<LocalTime, Integer> remainingDemand,
            int maxWorkSlotsPerAgent) {

        AgentDayConfig config = dayConfigMap.get(plan.deskAgent.getId() + "|" + date);
        if (config == null) return Set.of();

        int workSlotCount = config.effectiveHours()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(increment), 0, RoundingMode.HALF_UP)
                .intValue();
        // Cap to avoid over-subscription when total agent supply significantly
        // exceeds total demand. Only apply when the per-agent excess is >5% of
        // contracted hours, since small rounding differences are handled fine
        // by the trimming pass and don't cause agent crowding.
        if (maxWorkSlotsPerAgent < workSlotCount
                && (workSlotCount - maxWorkSlotsPerAgent) * 20 > workSlotCount) {
            workSlotCount = maxWorkSlotsPerAgent;
        }

        Set<LocalTime> breakSlots = plan.breakSlots;

        // Build the agent's full shift: workSlotCount working slots + break slots,
        // as a contiguous block within the all-slot-times list.
        int totalShiftSlots = workSlotCount + breakSlots.size();

        if (totalShiftSlots >= allSlotTimes.size()) {
            // Agent needs the full day — return all demand slots not in break
            Set<LocalTime> result = new LinkedHashSet<>();
            for (LocalTime t : demandSlotTimes) {
                if (!breakSlots.contains(t)) {
                    result.add(t);
                }
            }
            for (LocalTime t : result) {
                remainingDemand.merge(t, -1, Integer::sum);
            }
            return result;
        }

        // Find the break position in allSlotTimes
        int breakStartIdx = -1;
        int breakEndIdx = -1;
        if (!breakSlots.isEmpty()) {
            LocalTime firstBreak = breakSlots.stream().min(LocalTime::compareTo).orElse(null);
            LocalTime lastBreak = breakSlots.stream().max(LocalTime::compareTo).orElse(null);
            for (int i = 0; i < allSlotTimes.size(); i++) {
                if (allSlotTimes.get(i).equals(firstBreak)) breakStartIdx = i;
                if (allSlotTimes.get(i).equals(lastBreak)) breakEndIdx = i;
            }
        }

        // Find the best window position that contains the break.
        // Strategy: maximize the sum of remaining demand covered by non-break
        // demand slots in the window. This steers agents toward under-served
        // slots (edges of the day) rather than always centering on break.
        // Break-center distance is used as tiebreaker for natural shift shapes.
        int bestWindowStart = -1;
        int bestDemandScore = -1;
        int bestCenterDist = Integer.MAX_VALUE;

        int breakMidIdx = (breakStartIdx >= 0 && breakEndIdx >= 0)
                ? (breakStartIdx + breakEndIdx) / 2 : allSlotTimes.size() / 2;

        Set<LocalTime> demandSet = new HashSet<>(demandSlotTimes);

        for (int windowStart = 0; windowStart <= allSlotTimes.size() - totalShiftSlots; windowStart++) {
            int windowEnd = windowStart + totalShiftSlots - 1;

            // Window must contain the break
            if (breakStartIdx >= 0 && (breakStartIdx < windowStart || breakEndIdx > windowEnd)) {
                continue;
            }

            // Score: sum of remaining demand for non-break demand slots in window.
            // Higher score = window covers more under-served slots.
            int demandScore = 0;
            for (int i = windowStart; i <= windowEnd; i++) {
                LocalTime t = allSlotTimes.get(i);
                if (!breakSlots.contains(t) && demandSet.contains(t)) {
                    demandScore += Math.max(0, remainingDemand.getOrDefault(t, 0));
                }
            }

            // Tiebreaker: distance of window center from break center
            int windowMid = (windowStart + windowEnd) / 2;
            int centerDist = Math.abs(windowMid - breakMidIdx);

            if (demandScore > bestDemandScore
                    || (demandScore == bestDemandScore && centerDist < bestCenterDist)) {
                bestDemandScore = demandScore;
                bestCenterDist = centerDist;
                bestWindowStart = windowStart;
            }
        }

        // Build the work slot set from the best window and decrement remaining demand
        Set<LocalTime> result = new LinkedHashSet<>();
        if (bestWindowStart >= 0) {
            for (int i = bestWindowStart; i < bestWindowStart + totalShiftSlots; i++) {
                LocalTime t = allSlotTimes.get(i);
                if (!breakSlots.contains(t) && demandSet.contains(t)) {
                    result.add(t);
                }
            }
        }
        for (LocalTime t : result) {
            remainingDemand.merge(t, -1, Integer::sum);
        }

        return result;
    }

    /**
     * Second-pass assignment for seats left unassigned after the primary pass.
     * Extends agents' shifts to cover slots outside their computed work window.
     * Prefers agents whose existing assignments are adjacent to the slot
     * (minimises break geometry violations by extending contiguous blocks).
     */
    private int mopUpUnassigned(
            TreeMap<LocalTime, List<AgentAssignment>> slotMap,
            List<LocalTime> demandSlotTimes,
            List<AgentBreakPlan> breakPlans,
            Map<UUID, Set<LocalTime>> agentWorkSlots,
            Map<UUID, Integer> assignmentCounts,
            Map<UUID, Integer> maxSlots,
            Map<String, AgentDayConfig> dayConfigMap,
            Set<String> daysOffSet,
            LocalDate date,
            int increment) {

        int assigned = 0;

        // Collect unassigned seats. Run multiple passes because extending one
        // agent's shift opens adjacency for neighboring slots in the next pass.
        int totalUnassigned = 0;
        for (LocalTime slotTime : demandSlotTimes) {
            for (AgentAssignment seat : slotMap.get(slotTime)) {
                if (seat.getDeskAgent() == null) totalUnassigned++;
            }
        }
        if (totalUnassigned == 0) return 0;

        // Build per-agent tracking: assigned slot times (for adjacency checks)
        // and occupied timeslot IDs (to prevent double-assignment in same timeslot)
        Map<UUID, Set<LocalTime>> agentAssignedSlots = new HashMap<>();
        Map<UUID, Set<UUID>> agentOccupiedTimeslots = new HashMap<>();
        for (LocalTime slotTime : demandSlotTimes) {
            for (AgentAssignment seat : slotMap.get(slotTime)) {
                if (seat.getDeskAgent() != null) {
                    UUID daId = seat.getDeskAgent().getId();
                    agentAssignedSlots
                            .computeIfAbsent(daId, k -> new TreeSet<>())
                            .add(seat.getTimeslot().getStartTime());
                    agentOccupiedTimeslots
                            .computeIfAbsent(daId, k -> new HashSet<>())
                            .add(seat.getTimeslot().getId());
                }
            }
        }

        // Build expected slot count per agent (from AgentDayConfig)
        Map<UUID, Integer> expectedSlots = new HashMap<>();
        for (AgentBreakPlan plan : breakPlans) {
            AgentDayConfig config = dayConfigMap.get(plan.deskAgent.getId() + "|" + date);
            if (config != null) {
                int expected = config.effectiveHours()
                        .multiply(java.math.BigDecimal.valueOf(60))
                        .divide(java.math.BigDecimal.valueOf(increment), 0,
                                java.math.RoundingMode.HALF_UP)
                        .intValue();
                expectedSlots.put(plan.deskAgent.getId(), expected);
            }
        }

        // Collect all eligible agents indexed by DeskAgent ID
        Map<UUID, DeskAgent> agentById = new HashMap<>();
        Set<LocalTime> breakSlotsByAgent;
        Map<UUID, Set<LocalTime>> agentBreakSlots = new HashMap<>();
        for (AgentBreakPlan plan : breakPlans) {
            agentById.put(plan.deskAgent.getId(), plan.deskAgent);
            agentBreakSlots.put(plan.deskAgent.getId(), plan.breakSlots);
        }

        // Multi-pass: each pass may extend agents, opening adjacency for next pass
        for (int pass = 0; pass < 20; pass++) {
            List<AgentAssignment> unassignedSeats = new ArrayList<>();
            for (LocalTime slotTime : demandSlotTimes) {
                for (AgentAssignment seat : slotMap.get(slotTime)) {
                    if (seat.getDeskAgent() == null) unassignedSeats.add(seat);
                }
            }
            if (unassignedSeats.isEmpty()) break;

            int passAssigned = 0;
            for (AgentAssignment seat : unassignedSeats) {
            LocalTime slotTime = seat.getTimeslot().getStartTime();
            UUID reqSpecId = seat.getRequiredSpecialization().getId();

            // Find the best agent to extend into this slot.
            // Conservative: only assign adjacent, under-assigned agents.
            // This prevents creating contracted hours violations (over-assigning)
            // and break geometry violations (non-contiguous work blocks).
            // Seats that can't be filled conservatively are left for the solver's
            // construction heuristic, which evaluates the full constraint model.
            DeskAgent bestAgent = null;
            int bestPriority = Integer.MAX_VALUE;
            int bestOverage = Integer.MAX_VALUE;

            UUID timeslotId = seat.getTimeslot().getId();

            for (AgentBreakPlan plan : breakPlans) {
                DeskAgent da = plan.deskAgent;
                UUID daId = da.getId();

                // Skip agents on break at this slot
                if (agentBreakSlots.getOrDefault(daId, Set.of()).contains(slotTime)) {
                    continue;
                }

                // Skip agents already assigned in this timeslot (prevents overlap)
                if (agentOccupiedTimeslots.getOrDefault(daId, Set.of()).contains(timeslotId)) {
                    continue;
                }

                int current = assignmentCounts.getOrDefault(daId, 0);
                int expected = expectedSlots.getOrDefault(daId, 0);

                // Only extend agents who are under-assigned — never over-assign
                if (current >= expected) continue;

                // Only assign to adjacent slots to maintain contiguous shift blocks
                Set<LocalTime> slots = agentAssignedSlots.getOrDefault(daId, Set.of());
                boolean adjacent = slots.contains(slotTime.minusMinutes(increment))
                        || slots.contains(slotTime.plusMinutes(increment));
                if (!adjacent) continue;

                // Check spec match
                boolean specMatch = (da.getPrimarySpecialization() != null
                        && da.getPrimarySpecialization().getId().equals(reqSpecId))
                        || da.getSecondarySpecializations().stream()
                                .anyMatch(s -> s.getId().equals(reqSpecId));

                int priority = specMatch ? 1 : 2;
                int overage = current - expected;

                if (priority < bestPriority
                        || (priority == bestPriority && overage < bestOverage)) {
                    bestPriority = priority;
                    bestOverage = overage;
                    bestAgent = da;
                }
            }

            if (bestAgent != null) {
                seat.setDeskAgent(bestAgent);
                seat.setAgent(bestAgent.getAgent());
                assignmentCounts.merge(bestAgent.getId(), 1, Integer::sum);
                agentAssignedSlots
                        .computeIfAbsent(bestAgent.getId(), k -> new TreeSet<>())
                        .add(slotTime);
                agentOccupiedTimeslots
                        .computeIfAbsent(bestAgent.getId(), k -> new HashSet<>())
                        .add(timeslotId);
                assigned++;
                passAssigned++;
            }
            } // end for seat
            if (passAssigned == 0) break; // no progress, stop iterating
        } // end for pass

        return assigned;
    }

    /**
     * Trims agents' work slot sets so that per-slot supply matches demand.
     * When more agents want to work a slot than there are seats, excess agents
     * are trimmed from the EDGES of their shift (preserving contiguity and break
     * geometry). Trims are distributed evenly across agents.
     */
    private void trimWorkSlotsToMatchDemand(
            List<AgentBreakPlan> breakPlans,
            Map<UUID, Set<LocalTime>> agentWorkSlots,
            List<LocalTime> demandSlotTimes,
            TreeMap<LocalTime, List<AgentAssignment>> slotMap,
            int increment) {

        // Compute supply per slot
        Map<LocalTime, Integer> supplyPerSlot = new LinkedHashMap<>();
        for (LocalTime t : demandSlotTimes) supplyPerSlot.put(t, 0);
        for (AgentBreakPlan plan : breakPlans) {
            for (LocalTime t : agentWorkSlots.get(plan.deskAgent.getId())) {
                supplyPerSlot.merge(t, 1, Integer::sum);
            }
        }

        // Compute excess per slot (supply - demand)
        Map<LocalTime, Integer> excessPerSlot = new LinkedHashMap<>();
        for (LocalTime t : demandSlotTimes) {
            long unassignedSeats = slotMap.get(t).stream()
                    .filter(a -> a.getDeskAgent() == null).count();
            excessPerSlot.put(t, supplyPerSlot.getOrDefault(t, 0) - (int) unassignedSeats);
        }

        // Compute total excess and per-agent trim cap for even distribution
        int totalSupply = supplyPerSlot.values().stream().mapToInt(i -> i).sum();
        int totalDemand = (int) slotMap.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getDeskAgent() == null).count();
        int totalExcess = Math.max(0, totalSupply - totalDemand);
        int numAgents = breakPlans.size();
        // Allow enough trims to balance per-slot supply distribution.
        // Even when total supply == total demand, per-slot imbalances exist
        // (e.g., edges undersupplied, mid-day oversupplied).
        // Use per-slot max excess to determine how much trimming is needed.
        int maxSlotExcess = excessPerSlot.values().stream().mapToInt(i -> i).max().orElse(0);
        int maxTrimsPerAgent;
        if (numAgents == 0 || maxSlotExcess <= 0) {
            maxTrimsPerAgent = 0;
        } else {
            // Each trim reduces one slot's excess by 1 and may cascade to neighbors.
            // Allow enough trims to peel back the worst excess, plus 1 for cascading.
            maxTrimsPerAgent = Math.min(maxSlotExcess + 1,
                    Math.max(1, (totalExcess + numAgents - 1) / Math.max(1, numAgents) + 2));
        }

        // Track how many slots each agent has been trimmed
        Map<UUID, Integer> trimCounts = new HashMap<>();

        // Iteratively trim the most over-supplied slot by removing an edge agent.
        // When a slot can't be trimmed (no edge agents), skip it and try the next.
        Set<LocalTime> untrimableSlots = new HashSet<>();
        for (int iteration = 0; iteration < 10000; iteration++) {
            // Find the most over-supplied slot that can still be trimmed
            LocalTime worstSlot = null;
            int worstExcess = 0;
            for (LocalTime t : demandSlotTimes) {
                if (untrimableSlots.contains(t)) continue;
                int excess = excessPerSlot.getOrDefault(t, 0);
                if (excess > worstExcess) {
                    worstExcess = excess;
                    worstSlot = t;
                }
            }
            if (worstSlot == null || worstExcess <= 0) break;

            // Find the best agent to trim from this slot:
            // Must have this slot as a shift edge (first or last work slot).
            // Prefer agents with fewest trims (most assignments remaining).
            DeskAgent agentToTrim = null;
            int bestTrimCount = Integer.MAX_VALUE;

            for (AgentBreakPlan plan : breakPlans) {
                Set<LocalTime> workSlots = agentWorkSlots.get(plan.deskAgent.getId());
                if (!workSlots.contains(worstSlot)) continue;
                if (workSlots.size() <= 1) continue; // don't trim last slot

                int trims = trimCounts.getOrDefault(plan.deskAgent.getId(), 0);
                if (trims >= maxTrimsPerAgent) continue; // cap per-agent trims

                // Never trim a slot directly adjacent to the agent's break —
                // doing so would extend the effective break duration beyond
                // breakDurationMinutes, causing "Break duration" violations.
                if (plan.breakSlots.contains(worstSlot.plusMinutes(increment))
                        || plan.breakSlots.contains(worstSlot.minusMinutes(increment))) {
                    continue;
                }

                // Check if worstSlot is at the OUTER edge of the shift
                // (not adjacent to break, which would extend break duration)
                boolean isEdge = isShiftEdge(workSlots, worstSlot, increment, plan.breakSlots);
                if (!isEdge) continue;

                if (trims < bestTrimCount) {
                    bestTrimCount = trims;
                    agentToTrim = plan.deskAgent;
                }
            }

            if (agentToTrim == null) {
                // No agent can be trimmed from this slot — skip it
                untrimableSlots.add(worstSlot);
                continue;
            }

            agentWorkSlots.get(agentToTrim.getId()).remove(worstSlot);
            supplyPerSlot.merge(worstSlot, -1, Integer::sum);
            excessPerSlot.put(worstSlot, supplyPerSlot.get(worstSlot)
                    - (int) slotMap.get(worstSlot).stream()
                            .filter(a -> a.getDeskAgent() == null).count());
            trimCounts.merge(agentToTrim.getId(), 1, Integer::sum);
            // Trimming this slot may create new edges at neighboring slots
            // (for the trimmed agent), so re-enable them for trimming
            untrimableSlots.remove(worstSlot.minusMinutes(increment));
            untrimableSlots.remove(worstSlot.plusMinutes(increment));
        }
    }

    /**
     * Repairs break geometry for agents with multiple interior gaps.
     * For each agent that has a planned break, checks if their actual assignments
     * create more than one gap between first and last assigned slot.
     *
     * <p>Strategy: first try to FILL non-break gaps (assign the agent to available
     * seats in gap slots). This merges disconnected blocks without freeing any
     * seats. Falls back to removing outer blocks only when gaps can't be filled.
     *
     * @return net number of un-assigned seats (negative if more were assigned)
     */
    private int repairBreakGeometry(
            List<AgentBreakPlan> breakPlans,
            TreeMap<LocalTime, List<AgentAssignment>> slotMap,
            List<LocalTime> demandSlotTimes,
            int increment,
            Map<UUID, Integer> assignmentCounts) {

        int netUnassigned = 0;
        Set<LocalTime> demandSet = new HashSet<>(demandSlotTimes);

        for (AgentBreakPlan plan : breakPlans) {
            if (plan.breakSlots.isEmpty()) continue;

            DeskAgent da = plan.deskAgent;
            UUID daId = da.getId();

            // Collect this agent's assigned slot times
            TreeSet<LocalTime> assignedTimes = new TreeSet<>();
            for (LocalTime slotTime : demandSlotTimes) {
                for (AgentAssignment seat : slotMap.get(slotTime)) {
                    if (seat.getDeskAgent() != null && seat.getDeskAgent().getId().equals(daId)) {
                        assignedTimes.add(slotTime);
                    }
                }
            }

            if (assignedTimes.size() <= 1) continue;

            // Build alternating list of contiguous blocks and gaps
            LocalTime shiftStart = assignedTimes.first();
            LocalTime shiftEnd = assignedTimes.last().plusMinutes(increment);
            List<List<LocalTime>> blocks = new ArrayList<>();
            List<List<LocalTime>> gaps = new ArrayList<>();
            List<LocalTime> currentBlock = new ArrayList<>();
            List<LocalTime> currentGap = new ArrayList<>();
            for (LocalTime t = shiftStart; t.isBefore(shiftEnd); t = t.plusMinutes(increment)) {
                if (assignedTimes.contains(t)) {
                    if (!currentGap.isEmpty()) {
                        gaps.add(currentGap);
                        currentGap = new ArrayList<>();
                    }
                    currentBlock.add(t);
                } else {
                    if (!currentBlock.isEmpty()) {
                        blocks.add(currentBlock);
                        currentBlock = new ArrayList<>();
                    }
                    currentGap.add(t);
                }
            }
            if (!currentBlock.isEmpty()) blocks.add(currentBlock);

            if (gaps.size() <= 1) continue;

            // Find the gap that best matches the planned break
            LocalTime breakMid = plan.breakSlots.stream().min(LocalTime::compareTo).orElse(shiftStart);
            int breakGapIdx = 0;
            int bestOverlap = -1;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < gaps.size(); i++) {
                List<LocalTime> gap = gaps.get(i);
                int overlap = 0;
                for (LocalTime t : gap) {
                    if (plan.breakSlots.contains(t)) overlap++;
                }
                LocalTime gapMid = gap.get(gap.size() / 2);
                long dist = Math.abs(gapMid.toSecondOfDay() - breakMid.toSecondOfDay());
                if (overlap > bestOverlap || (overlap == bestOverlap && dist < bestDistance)) {
                    bestOverlap = overlap;
                    bestDistance = dist;
                    breakGapIdx = i;
                }
            }

            // Only fill gaps when ALL non-break gaps can be filled. Partial filling
            // wastes seats for no constraint benefit (reducing 3→2 gaps still violates
            // exactlyOneBreak) and steals seats other agents need.
            boolean allFillable = true;
            for (int i = 0; i < gaps.size(); i++) {
                if (i == breakGapIdx) continue;
                List<LocalTime> gap = gaps.get(i);
                for (LocalTime t : gap) {
                    if (!demandSet.contains(t)) { allFillable = false; break; }
                    boolean hasAvailableSeat = false;
                    for (AgentAssignment seat : slotMap.get(t)) {
                        if (seat.getDeskAgent() == null) {
                            hasAvailableSeat = true;
                            break;
                        }
                    }
                    if (!hasAvailableSeat) { allFillable = false; break; }
                }
                if (!allFillable) break;
            }

            if (!allFillable) {
                // Fallback: remove outer disconnected blocks to ensure exactly one gap.
                // Keep only the two blocks adjacent to the break gap (one on each side).
                // Freed seats stay unassigned — this runs after mop-up so no re-claiming.
                Set<LocalTime> keepSlots = new HashSet<>();
                if (breakGapIdx < blocks.size()) {
                    keepSlots.addAll(blocks.get(breakGapIdx));
                }
                if (breakGapIdx + 1 < blocks.size()) {
                    keepSlots.addAll(blocks.get(breakGapIdx + 1));
                }

                for (LocalTime slotTime : demandSlotTimes) {
                    if (keepSlots.contains(slotTime)) continue;
                    if (!assignedTimes.contains(slotTime)) continue;
                    for (AgentAssignment seat : slotMap.get(slotTime)) {
                        if (seat.getDeskAgent() != null && seat.getDeskAgent().getId().equals(daId)) {
                            seat.setDeskAgent(null);
                            seat.setAgent(null);
                            assignmentCounts.merge(daId, -1, Integer::sum);
                            netUnassigned++;
                        }
                    }
                }
                continue;
            }

            // Fill all non-break gaps — merges blocks without freeing any seats
            for (int i = 0; i < gaps.size(); i++) {
                if (i == breakGapIdx) continue;
                for (LocalTime t : gaps.get(i)) {
                    AgentAssignment bestSeat = findBestSeat(da, slotMap.get(t));
                    if (bestSeat != null) {
                        bestSeat.setDeskAgent(da);
                        bestSeat.setAgent(da.getAgent());
                        assignmentCounts.merge(daId, 1, Integer::sum);
                        netUnassigned--;
                    }
                }
            }
        }
        return netUnassigned;
    }

    /**
     * Returns true if the given slotTime is at the edge of a contiguous block
     * within the agent's work slots (first slot of a block or last slot of a block).
     */
    private boolean isShiftEdge(Set<LocalTime> workSlots, LocalTime slotTime, int increment, Set<LocalTime> breakSlots) {
        boolean hasPrev = workSlots.contains(slotTime.minusMinutes(increment))
                || breakSlots.contains(slotTime.minusMinutes(increment));
        boolean hasNext = workSlots.contains(slotTime.plusMinutes(increment))
                || breakSlots.contains(slotTime.plusMinutes(increment));
        // Edge if missing predecessor OR successor (start or end of a contiguous block)
        return !hasPrev || !hasNext;
    }

    /**
     * Find the best unassigned seat for this agent in the given slot.
     * Prefers seats whose required specialization matches the agent's primary,
     * then secondary, then any unassigned seat.
     */
    private AgentAssignment findBestSeat(DeskAgent da, List<AgentAssignment> seats) {
        AgentAssignment primaryMatch = null;
        AgentAssignment secondaryMatch = null;
        AgentAssignment anyMatch = null;

        for (AgentAssignment seat : seats) {
            if (seat.getDeskAgent() != null) continue; // already assigned
            UUID reqSpecId = seat.getRequiredSpecialization().getId();

            if (da.getPrimarySpecialization() != null
                    && da.getPrimarySpecialization().getId().equals(reqSpecId)) {
                if (primaryMatch == null) primaryMatch = seat;
            } else if (da.getSecondarySpecializations().stream()
                    .anyMatch(s -> s.getId().equals(reqSpecId))) {
                if (secondaryMatch == null) secondaryMatch = seat;
            } else {
                if (anyMatch == null) anyMatch = seat;
            }
        }
        if (primaryMatch != null) return primaryMatch;
        if (secondaryMatch != null) return secondaryMatch;
        return anyMatch;
    }

    private DeskAgent findBestMatch(AgentAssignment seat, Queue<DeskAgent> available,
                                    Map<UUID, Integer> assignmentCounts,
                                    Map<UUID, Integer> maxSlots) {
        UUID reqSpecId = seat.getRequiredSpecialization().getId();

        // First pass: primary specialization match — prefer LEAST-assigned agent
        // to distribute assignments evenly and avoid starving agents at the end
        // of the queue.
        DeskAgent bestPrimary = null;
        int bestPrimaryCount = Integer.MAX_VALUE;
        for (DeskAgent da : available) {
            if (isOverAssigned(da, assignmentCounts, maxSlots)) continue;
            if (da.getPrimarySpecialization() != null
                    && da.getPrimarySpecialization().getId().equals(reqSpecId)) {
                int count = assignmentCounts.getOrDefault(da.getId(), 0);
                if (count < bestPrimaryCount) {
                    bestPrimaryCount = count;
                    bestPrimary = da;
                }
            }
        }
        if (bestPrimary != null) return bestPrimary;

        // Second pass: secondary specialization match — least-assigned
        DeskAgent bestSecondary = null;
        int bestSecondaryCount = Integer.MAX_VALUE;
        for (DeskAgent da : available) {
            if (isOverAssigned(da, assignmentCounts, maxSlots)) continue;
            if (da.getSecondarySpecializations().stream()
                    .anyMatch(s -> s.getId().equals(reqSpecId))) {
                int count = assignmentCounts.getOrDefault(da.getId(), 0);
                if (count < bestSecondaryCount) {
                    bestSecondaryCount = count;
                    bestSecondary = da;
                }
            }
        }
        if (bestSecondary != null) return bestSecondary;

        // Third pass: any agent not over-assigned — least-assigned
        DeskAgent bestAny = null;
        int bestAnyCount = Integer.MAX_VALUE;
        for (DeskAgent da : available) {
            if (isOverAssigned(da, assignmentCounts, maxSlots)) continue;
            int count = assignmentCounts.getOrDefault(da.getId(), 0);
            if (count < bestAnyCount) {
                bestAnyCount = count;
                bestAny = da;
            }
        }
        if (bestAny != null) return bestAny;

        // Fallback: any agent (may over-assign, but better than unassigned)
        return available.peek();
    }

    private boolean isOverAssigned(DeskAgent da, Map<UUID, Integer> assignmentCounts,
                                   Map<UUID, Integer> maxSlots) {
        int current = assignmentCounts.getOrDefault(da.getId(), 0);
        int max = maxSlots.getOrDefault(da.getId(), 0);
        return current >= max;
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
