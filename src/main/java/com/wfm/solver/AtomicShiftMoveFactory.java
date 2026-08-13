package com.wfm.solver;

import ai.timefold.solver.core.impl.heuristic.move.CompositeMove;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import com.wfm.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link MoveListFactory} that, for each agent-day still under its
 * contracted hours, finds free and self-held spec-matching seats and
 * generates one bounded, atomic {@code CompositeMove} per legal shift
 * window found by {@link ShiftWindowFinder} — including a full
 * clear-and-replace rewrite for an agent-day pinned below the break
 * threshold with no correctly placed break, the live symptom this phase
 * exists to fix.
 *
 * <p>Read-only against the working {@link Schedule} — only
 * {@link AssignSeatMove#doMoveOnGenuineVariables} mutates. All-or-nothing
 * validity (every work seat in a window is currently free or already held
 * by this agent) is established here at generation time; {@code
 * CompositeMove.isMoveDoable} is an OR across submoves and {@code
 * doMoveOnGenuineVariables} silently skips non-doable submoves rather than
 * aborting, so neither can be relied on to enforce atomicity.
 */
public class AtomicShiftMoveFactory implements MoveListFactory<Schedule> {

    private static final Logger log = LoggerFactory.getLogger(AtomicShiftMoveFactory.class);

    /**
     * Deterministic cap on generated moves per agent-day. Bounds
     * candidate-generation cost per local-search step (move-explosion
     * failure mode, 12-RESEARCH.md Pitfall 4) while keeping break positions
     * spread across the day reachable, rather than clustering every
     * retained candidate at the earliest span start.
     */
    static final int MAX_WINDOWS_PER_AGENT_DAY = 8;

    public AtomicShiftMoveFactory() {}

    @Override
    public List<? extends Move<Schedule>> createMoveList(Schedule schedule) {
        Map<UUID, Agent> agentsById = schedule.getAgents().stream()
                .collect(Collectors.toMap(Agent::getId, a -> a));

        Map<LocalDate, List<AgentAssignment>> assignmentsByDate = schedule.getAssignments().stream()
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getDate()));

        List<Move<Schedule>> moves = new ArrayList<>();
        int agentDaysScanned = 0;
        int agentDaysSkippedSatisfied = 0;
        int windowsFound = 0;

        for (AgentDayConfig dayConfig : schedule.getAgentDayConfigs()) {
            Agent agent = agentsById.get(dayConfig.agentId());
            if (agent == null) continue;

            List<AgentAssignment> dayAssignments =
                    assignmentsByDate.getOrDefault(dayConfig.date(), List.of());

            List<AgentAssignment> currentlyHeld = dayAssignments.stream()
                    .filter(a -> agent.equals(a.getAgent()))
                    .toList();

            // Filter before any window search runs so agents already at or
            // above their contracted hours cost nothing per step —
            // createMoveList runs every local-search step.
            int requiredWorkSlots = ShiftWindowFinder.requiredWorkSlots(dayConfig);
            if (currentlyHeld.size() >= requiredWorkSlots) {
                agentDaysSkippedSatisfied++;
                continue;
            }
            agentDaysScanned++;

            // Candidate pool: seats free or already held by this agent, in
            // this agent's required specialization. Seats held by a
            // different agent are excluded outright — this phase fills and
            // rewrites the agent's own day, it does not displace another
            // agent's seat.
            List<AgentAssignment> candidateSeats = dayAssignments.stream()
                    .filter(a -> a.getAgent() == null || agent.equals(a.getAgent()))
                    .filter(a -> agentHoldsSpecialization(agent, a.getRequiredSpecialization()))
                    .toList();

            List<ShiftWindowFinder.ShiftWindow> windows =
                    ShiftWindowFinder.findWindows(candidateSeats, dayConfig);
            windowsFound += windows.size();

            for (ShiftWindowFinder.ShiftWindow window : boundWindows(windows)) {
                List<AssignSeatMove> seatMoves = buildSeatMoves(agent, currentlyHeld, window);
                if (seatMoves.isEmpty()) continue;
                moves.add(CompositeMove.buildMove(seatMoves));
            }
        }

        log.debug("AtomicShiftMoveFactory: scanned {} under-hours agent-days, skipped {} satisfied, "
                        + "found {} windows, generated {} moves",
                agentDaysScanned, agentDaysSkippedSatisfied, windowsFound, moves.size());

        return moves;
    }

    /**
     * Builds the unassign-then-assign seat-move list that rewrites an
     * agent-day into the shape of {@code window}: one unassign move (target
     * agent {@code null}) for every currently-held seat the window doesn't
     * reuse, then one assign move for every window work seat the agent
     * doesn't already hold. No-op pairs — a seat already held that the
     * window also wants — are skipped so the composite move stays minimal
     * while still leaving the agent-day in exactly the window's shape.
     * Package-private so the contract can be asserted directly without
     * depending on {@code CompositeMove} internals.
     */
    static List<AssignSeatMove> buildSeatMoves(
            Agent agent, List<AgentAssignment> currentlyHeld, ShiftWindowFinder.ShiftWindow window) {
        List<AgentAssignment> workSeats = window.workSeats();
        List<AssignSeatMove> seatMoves = new ArrayList<>();

        for (AgentAssignment held : currentlyHeld) {
            if (!workSeats.contains(held)) {
                seatMoves.add(new AssignSeatMove(held, null));
            }
        }
        for (AgentAssignment workSeat : workSeats) {
            if (!currentlyHeld.contains(workSeat)) {
                seatMoves.add(new AssignSeatMove(workSeat, agent));
            }
        }
        return seatMoves;
    }

    /**
     * Deterministically down-samples to at most
     * {@link #MAX_WINDOWS_PER_AGENT_DAY} windows by taking every
     * {@code ceil(size / MAX_WINDOWS_PER_AGENT_DAY)}-th element (rather than
     * the first N), so retained candidates spread across the enumerated
     * span starts and break offsets instead of clustering at the earliest
     * one, and repeated calls on the same input select identically.
     */
    private static List<ShiftWindowFinder.ShiftWindow> boundWindows(List<ShiftWindowFinder.ShiftWindow> windows) {
        if (windows.size() <= MAX_WINDOWS_PER_AGENT_DAY) return windows;
        int step = (int) Math.ceil((double) windows.size() / MAX_WINDOWS_PER_AGENT_DAY);
        List<ShiftWindowFinder.ShiftWindow> bounded = new ArrayList<>();
        for (int i = 0; i < windows.size(); i += step) {
            bounded.add(windows.get(i));
        }
        return bounded;
    }

    /** Mirrors ScheduleConstraintProvider.specializationMatch (lines 93-108). */
    private boolean agentHoldsSpecialization(Agent agent, Specialization required) {
        if (required == null) return false;
        UUID requiredId = required.getId();
        if (agent.getPrimarySpecialization() != null
                && agent.getPrimarySpecialization().getId().equals(requiredId)) {
            return true;
        }
        return agent.getSecondarySpecializations().stream()
                .anyMatch(s -> s.getId().equals(requiredId));
    }
}
