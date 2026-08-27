package com.wfm.solver;

import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import ai.timefold.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.timefold.solver.core.config.heuristic.selector.common.SelectionOrder;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySorterManner;
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
 *
 * <p><b>The sorting triple on {@link #seatPhase()} is load-bearing.</b> Naming an entity selector
 * explicitly REPLACES the selector config the solver would otherwise have derived:
 * {@code QueuedEntityPlacerFactory.buildEntitySelectorConfig()} only falls through to
 * {@code AbstractFromConfigFactory.getDefaultEntitySelectorConfigForEntity()} when the placer's
 * entity selector config is {@code null}, and it is that default builder — not the phase — that
 * stamps on {@code cacheType=PHASE}, {@code selectionOrder=SORTED} and
 * {@code sorterManner=DECREASING_DIFFICULTY_IF_AVAILABLE} (the phase policy's manner, derived
 * from the default {@code ConstructionHeuristicType.ALLOCATE_ENTITY_FROM_QUEUE}) whenever
 * {@code EntitySelectorConfig.hasSorter(manner, entityDescriptor)} holds. So swapping a bare
 * {@code new ConstructionHeuristicPhaseConfig()} for an explicit placer silently dropped
 * {@link AgentAssignment}'s {@code difficultyComparatorClass}, degrading the initial solution.
 * {@link AgentShiftAssignment} declares no difficulty comparison, so {@code hasSorter()} is false
 * for it and the default builder emits a bare selector — hence no triple on {@link #shiftPhase()},
 * deliberately. Verified against timefold-solver-core-1.16.0 sources.
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
                                .withEntityClass(AgentAssignment.class)
                                .withCacheType(SelectionCacheType.PHASE)
                                .withSelectionOrder(SelectionOrder.SORTED)
                                .withSorterManner(EntitySorterManner.DECREASING_DIFFICULTY_IF_AVAILABLE)));
    }
}
