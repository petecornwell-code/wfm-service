package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XCUT-03 — no test under {@code src/test/java/com/wfm/solver/} built a real solver from
 * {@code solverConfig.xml} before this phase, so the exact failure this test exists to catch (a
 * bare {@code <constructionHeuristic/>} throwing {@code IllegalArgumentException} the moment a
 * second {@code @PlanningEntity} is declared) would otherwise ship silently — the same class of
 * gap Phase 12's revert left behind (12-RESEARCH.md, carried forward as XCUT-03).
 *
 * <p>Plain JUnit 5, no Spring context — {@code SolverFactory.createFromXmlResource} reads the
 * real {@code solverConfig.xml} directly off the classpath.
 */
class SolverConfigBuildTest {

    @Test
    void solverConfigXmlBuildsARealSolverWithTwoPlanningEntityClasses() {
        SolverFactory<Schedule> factory = SolverFactory.createFromXmlResource("solverConfig.xml");
        Solver<Schedule> solver = factory.buildSolver();

        assertThat(solver).isNotNull();

        // Second assertion: the config declares both entity classes by name, so a future edit
        // that removes one is caught here rather than by a downstream symptom.
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        assertThat(solverConfig.getEntityClassList())
                .as("solverConfig.xml must declare both planning entity classes")
                .contains(AgentShiftAssignment.class, AgentAssignment.class);
    }
}
