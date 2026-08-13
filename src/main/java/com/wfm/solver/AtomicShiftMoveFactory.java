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
 * {@link MoveListFactory} that, for each agent-day with zero current
 * assignments, finds free spec-matching seats and generates one
 * {@code CompositeMove} per legal shift window found by
 * {@link ShiftWindowFinder}.
 *
 * <p>Read-only against the working {@link Schedule} — only
 * {@link AssignSeatMove#doMoveOnGenuineVariables} mutates. Validity
 * (all seats free and spec-matching) is established here at generation
 * time; {@code CompositeMove.isMoveDoable} is an OR across submoves and
 * must not be relied on for all-or-nothing semantics.
 *
 * <p>In this task, only agent-days with zero existing assignments are
 * scanned and at most one window per agent-day is generated — partial-day
 * clear-and-replace and multi-window enumeration are out of scope here.
 */
public class AtomicShiftMoveFactory implements MoveListFactory<Schedule> {

    private static final Logger log = LoggerFactory.getLogger(AtomicShiftMoveFactory.class);

    public AtomicShiftMoveFactory() {}

    @Override
    public List<? extends Move<Schedule>> createMoveList(Schedule schedule) {
        Map<UUID, Agent> agentsById = schedule.getAgents().stream()
                .collect(Collectors.toMap(Agent::getId, a -> a));

        Map<LocalDate, List<AgentAssignment>> assignmentsByDate = schedule.getAssignments().stream()
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getDate()));

        List<Move<Schedule>> moves = new ArrayList<>();
        int agentDaysScanned = 0;

        for (AgentDayConfig dayConfig : schedule.getAgentDayConfigs()) {
            Agent agent = agentsById.get(dayConfig.agentId());
            if (agent == null) continue;

            List<AgentAssignment> dayAssignments =
                    assignmentsByDate.getOrDefault(dayConfig.date(), List.of());

            long existingCount = dayAssignments.stream()
                    .filter(a -> agent.equals(a.getAgent()))
                    .count();
            if (existingCount != 0) continue;

            agentDaysScanned++;

            List<AgentAssignment> candidateSeats = dayAssignments.stream()
                    .filter(a -> a.getAgent() == null)
                    .filter(a -> agentHoldsSpecialization(agent, a.getRequiredSpecialization()))
                    .toList();

            for (ShiftWindowFinder.ShiftWindow window : ShiftWindowFinder.findWindows(candidateSeats, dayConfig)) {
                List<AssignSeatMove> seatMoves = window.workSeats().stream()
                        .map(seat -> new AssignSeatMove(seat, agent))
                        .toList();
                moves.add(CompositeMove.buildMove(seatMoves));
            }
        }

        log.debug("AtomicShiftMoveFactory: scanned {} zero-assignment agent-days, generated {} moves",
                agentDaysScanned, moves.size());

        return moves;
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
