package com.wfm.solver;

import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;

/**
 * Shared construction-heuristic phase builders for hand-built {@code SolverConfig} test fixtures
 * (Phase 15). Mirrors {@code solverConfig.xml}'s two explicitly-scoped phases exactly — once a
 * second {@code @PlanningEntity} class is declared on the solution, a bare
 * {@code new ConstructionHeuristicPhaseConfig()} throws {@code IllegalArgumentException} at
 * solver-build time ("no entityClass configured ... cannot be deduced automatically"), the same
 * failure XCUT-03 exists to catch in {@code solverConfig.xml}. Every hand-built
 * {@code SolverConfig} that declares both entity classes and actually calls
 * {@code buildSolver()} must use these two explicit phases, not a bare default.
 */
final class TestConstructionHeuristicPhases {

    private TestConstructionHeuristicPhases() {}

    static ConstructionHeuristicPhaseConfig shiftPhase() {
        return new ConstructionHeuristicPhaseConfig()
                .withEntityPlacerConfig(new QueuedEntityPlacerConfig()
                        .withEntitySelectorConfig(new EntitySelectorConfig()
                                .withId("shiftEntitySelector")
                                .withEntityClass(AgentShiftAssignment.class)));
    }

    static ConstructionHeuristicPhaseConfig seatPhase() {
        return new ConstructionHeuristicPhaseConfig()
                .withEntityPlacerConfig(new QueuedEntityPlacerConfig()
                        .withEntitySelectorConfig(new EntitySelectorConfig()
                                .withId("seatEntitySelector")
                                .withEntityClass(AgentAssignment.class)));
    }
}
