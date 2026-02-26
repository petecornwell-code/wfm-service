package com.wfm.solver.plugin;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

/**
 * Service provider interface for client-supplied scheduling constraints.
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * See spec Appendix I for details.
 */
public interface CustomConstraint {

    /** Unique, stable identifier for this constraint. */
    String name();

    /** Human-readable description shown in the Constraint Weights UI. */
    String description();

    /** Default score level and weight. */
    HardSoftScore defaultWeight();

    /** Define the constraint using Timefold's Constraint Streams API. */
    Constraint define(ConstraintFactory factory);
}
