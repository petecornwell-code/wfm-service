package com.wfm.integration;

import com.wfm.model.Agent;
import com.wfm.model.WorkingDaysSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-15: refresh-side proof that a spreadsheet-sourced working-days pattern survives a
 * BambooHR refresh whose own field 4517 is blank or "Variable" -- the UAT 2026-08-12
 * downgrade hazard must never recur. Calls the package-private static
 * {@code BambooRefreshService.shouldDowngradeWorkingDaysKnown(Agent)} directly via
 * reflection, in the same no-Spring style {@code SolverServiceEligibilityFilterTest} uses
 * for {@code filterEligible}.
 */
class WorkingDaysSourceGuardTest {

    private static boolean invoke(Agent agent) throws Exception {
        Method m = BambooRefreshService.class.getDeclaredMethod("shouldDowngradeWorkingDaysKnown", Agent.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, agent);
    }

    private Agent agent(WorkingDaysSource source) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(1L);
        a.setBamboohrId("B1");
        a.setName("Test Agent");
        a.setWorkingDaysSource(source);
        return a;
    }

    @Test
    void spreadsheetSourced_blankBambooField_neverDowngraded() throws Exception {
        // "Blank customWorkingdays" is represented upstream by an empty parse result; this
        // method only decides whether the downgrade may proceed once that branch is reached.
        Agent agent = agent(WorkingDaysSource.SPREADSHEET);

        assertThat(invoke(agent)).isFalse();
    }

    @Test
    void spreadsheetSourced_variableBambooField_neverDowngraded() throws Exception {
        // "Variable" also produces an empty WorkingDaysParser result upstream -- the guard
        // itself does not distinguish blank from "Variable", both reach this same call site.
        Agent agent = agent(WorkingDaysSource.SPREADSHEET);

        assertThat(invoke(agent)).isFalse();
    }

    @Test
    void bambooSourced_blankBambooField_downgradedAsToday() throws Exception {
        Agent agent = agent(WorkingDaysSource.BAMBOOHR);

        assertThat(invoke(agent)).isTrue();
    }
}
