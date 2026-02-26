package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Defines all scheduling constraints for the Timefold solver.
 * See spec section 6 for constraint definitions.
 */
public class ScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
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
            contractedHours(factory),
            bulkOverallocationLimit(factory),
            bulkUnderallocationSoft(factory),
            bulkUnderallocationHard(factory),
        };
    }

    // --- Hard constraints ---

    private Constraint agentDayOff(ConstraintFactory factory) {
        // TODO: implement — agent must not be assigned on a day off
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Agent day off");
    }

    private Constraint specializationMatch(ConstraintFactory factory) {
        // TODO: implement — agent must have matching specialization
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Specialization match");
    }

    private Constraint oneAssignmentPerTimeslot(ConstraintFactory factory) {
        // TODO: implement — agent cannot be in two seats in the same timeslot
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("One assignment per timeslot");
    }

    private Constraint exactlyOneBreak(ConstraintFactory factory) {
        // TODO: implement — agents above threshold get exactly one contiguous break
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Exactly one break");
    }

    private Constraint breakDuration(ConstraintFactory factory) {
        // TODO: implement — break must be exactly the configured duration
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Break duration");
    }

    private Constraint breakBlockedWindow(ConstraintFactory factory) {
        // TODO: implement — break cannot be in first/last N hours of shift
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Break blocked window");
    }

    private Constraint breakStartAlignment(ConstraintFactory factory) {
        // TODO: implement — break must start on aligned boundary
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Break start alignment");
    }

    private Constraint contractedHours(ConstraintFactory factory) {
        // TODO: implement — agent must work exactly contracted hours per day
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Contracted hours");
    }

    private Constraint bulkOverallocationLimit(ConstraintFactory factory) {
        // TODO: implement — total supply must not exceed demand by more than limit
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Bulk over-allocation limit");
    }

    private Constraint bulkUnderallocationHard(ConstraintFactory factory) {
        // TODO: implement — demand below hard floor is infeasible
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Bulk under-allocation hard");
    }

    // --- Soft constraints ---

    private Constraint preferPrimarySpecialization(ConstraintFactory factory) {
        // TODO: implement — prefer primary over secondary specialization
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Prefer primary specialization");
    }

    private Constraint honourPreferredStartTime(ConstraintFactory factory) {
        // TODO: implement — penalise assignments before preferred start
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Honour preferred start time");
    }

    private Constraint honourPreferredBreakTime(ConstraintFactory factory) {
        // TODO: implement — penalise break not at preferred time
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Honour preferred break time");
    }

    private Constraint breakClustering(ConstraintFactory factory) {
        // TODO: implement — penalise too many agents on break in same timeslot
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Break clustering");
    }

    private Constraint bulkUnderallocationSoft(ConstraintFactory factory) {
        // TODO: implement — soft penalty for demand shortfall
        return factory.forEach(com.wfm.model.AgentAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Bulk under-allocation soft");
    }
}
