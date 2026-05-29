package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.JobTitleConfig;
import com.wfm.model.Specialization;
import com.wfm.repository.JobTitleConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the non-schedulable eligibility filter in SolverService.
 *
 * Tests call the package-private static helper
 * {@code SolverService.filterEligible(List<Agent>, long, AgentEligibilityService)}
 * directly.  AgentEligibilityService is constructed with a Mockito mock of
 * JobTitleConfigRepository — no Spring context required.
 *
 * The helper encapsulates the three-filter pipeline:
 *   1. Agent::isActive
 *   2. !agentEligibilityService.isNonSchedulable(tenantId, jobTitle)
 *   3. primarySpecialization != null
 */
class SolverServiceEligibilityFilterTest {

    private static final long TENANT = 1L;

    // -----------------------------------------------------------------
    // Basic inclusion/exclusion cases
    // -----------------------------------------------------------------

    @Test
    void activeAgentWithPrimarySpec_regularTitle_isIncluded() {
        AgentEligibilityService elig = eligServiceFor("Manager", false);
        Agent agent = agent(true, "Manager", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).containsExactly(agent);
    }

    @Test
    void activeAgentWithPrimarySpec_nonSchedulableTitle_isExcluded() {
        AgentEligibilityService elig = eligServiceFor("Director", true);
        Agent agent = agent(true, "Director", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void inactiveAgent_regularTitle_isExcluded() {
        AgentEligibilityService elig = eligServiceFor("Agent", false);
        Agent agent = agent(false, "Agent", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void activeAgent_noPrimarySpec_isExcluded() {
        AgentEligibilityService elig = eligServiceFor("Agent", false);
        Agent agent = agent(true, "Agent", null);

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void activeAgentWithPrimarySpec_nullJobTitle_isIncluded() {
        // AgentEligibilityService returns false for null jobTitle
        AgentEligibilityService elig = eligServiceFor(null, false);
        Agent agent = agent(true, null, primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).containsExactly(agent);
    }

    // -----------------------------------------------------------------
    // Order preservation
    // -----------------------------------------------------------------

    @Test
    void filterPreservesOrderOfSurvivingAgents() {
        JobTitleConfigRepository repo = mock(JobTitleConfigRepository.class);
        stubTitle(repo, "Regular", false);
        stubTitle(repo, "Excluded", true);
        stubTitle(repo, "AlsoRegular", false);
        AgentEligibilityService elig = new AgentEligibilityService(repo);

        Agent a1 = agent(true, "Regular", primarySpec());
        Agent a2 = agent(true, "Excluded", primarySpec());   // non-schedulable
        Agent a3 = agent(true, "AlsoRegular", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(a1, a2, a3), TENANT, elig);

        assertThat(result).containsExactly(a1, a3);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Specialization primarySpec() {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setDeskId(UUID.randomUUID());
        s.setName("General");
        return s;
    }

    private Agent agent(boolean active, String jobTitle, Specialization primary) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId(UUID.randomUUID().toString());
        a.setName("Test Agent");
        a.setActive(active);
        a.setJobTitle(jobTitle);
        a.setPrimarySpecialization(primary);
        return a;
    }

    /** Stub that returns a JobTitleConfig with the given nonSchedulable flag for the given title. */
    private AgentEligibilityService eligServiceFor(String title, boolean nonSchedulable) {
        JobTitleConfigRepository repo = mock(JobTitleConfigRepository.class);
        stubTitle(repo, title, nonSchedulable);
        return new AgentEligibilityService(repo);
    }

    private void stubTitle(JobTitleConfigRepository repo, String title, boolean nonSchedulable) {
        if (title == null) {
            // AgentEligibilityService short-circuits before calling repo for null/blank
            return;
        }
        JobTitleConfig cfg = new JobTitleConfig();
        cfg.setTenantId(TENANT);
        cfg.setJobTitle(title);
        cfg.setNonSchedulable(nonSchedulable);
        when(repo.findByTenantIdAndJobTitle(TENANT, title)).thenReturn(Optional.of(cfg));
    }
}
