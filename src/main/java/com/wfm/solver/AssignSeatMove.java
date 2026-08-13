package com.wfm.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.AbstractMove;
import com.wfm.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A single reversible seat assignment: writes {@link AgentAssignment#agent}
 * to a target {@link Agent}. Composed N-at-a-time via
 * {@code CompositeMove.buildMove(...)} into one atomic shift-placement move
 * by {@link AtomicShiftMoveFactory}.
 *
 * <p>At Timefold 1.16.0, {@code AbstractMove.createUndoMove} is deprecated
 * for removal and throws if invoked — the solver auto-generates the undo via
 * {@code VariableChangeRecordingScoreDirector}, so this class implements
 * only {@link #doMoveOnGenuineVariables(ScoreDirector)} and never overrides
 * {@code createUndoMove}.
 */
final class AssignSeatMove extends AbstractMove<Schedule> {

    private final AgentAssignment assignment;
    private final Agent toAgent;

    AssignSeatMove(AgentAssignment assignment, Agent toAgent) {
        this.assignment = assignment;
        this.toAgent = toAgent;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<Schedule> scoreDirector) {
        return !Objects.equals(assignment.getAgent(), toAgent);
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Schedule> scoreDirector) {
        scoreDirector.beforeVariableChanged(assignment, "agent");
        assignment.setAgent(toAgent);
        scoreDirector.afterVariableChanged(assignment, "agent");
    }

    @Override
    public AssignSeatMove rebase(ScoreDirector<Schedule> destinationScoreDirector) {
        return new AssignSeatMove(
                destinationScoreDirector.lookUpWorkingObject(assignment),
                destinationScoreDirector.lookUpWorkingObject(toAgent));
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return List.of(assignment);
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of(toAgent);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AssignSeatMove other
                && Objects.equals(assignment, other.assignment)
                && Objects.equals(toAgent, other.toAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignment, toAgent);
    }

    @Override
    public String toString() {
        return assignment + " -> " + toAgent;
    }
}
