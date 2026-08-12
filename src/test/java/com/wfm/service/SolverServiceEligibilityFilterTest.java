package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.JobTitleIncludePattern;
import com.wfm.model.Specialization;
import com.wfm.repository.JobTitleConfigRepository;
import com.wfm.repository.JobTitleIncludePatternRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the job-title eligibility filter in SolverService.
 *
 * Tests call the package-private static helper
 * {@code SolverService.filterEligible(List<Agent>, long, AgentEligibilityService)}
 * directly.  AgentEligibilityService is constructed with a Mockito mock of
 * JobTitleConfigRepository — no Spring context required.
 *
 * The helper encapsulates the three-filter pipeline:
 *   1. Agent::isActive
 *   2. agentEligibilityService.isIncludedByTitleAllowlist(tenantId, jobTitle)
 *   3. primarySpecialization != null
 */
class SolverServiceEligibilityFilterTest {

    private static final long TENANT = 1L;

    // -----------------------------------------------------------------
    // Basic inclusion/exclusion cases
    // -----------------------------------------------------------------

    @Test
    void activeAgentWithPrimarySpec_regularTitle_isIncluded() {
        AgentEligibilityService elig = eligServiceAllowing("Manager");
        Agent agent = agent(true, "Manager", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).containsExactly(agent);
    }

    @Test
    void activeAgentWithPrimarySpec_titleNotOnAllowlist_isExcluded() {
        AgentEligibilityService elig = eligServiceAllowing("Manager");
        Agent agent = agent(true, "Director", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void inactiveAgent_regularTitle_isExcluded() {
        AgentEligibilityService elig = eligServiceAllowing("Agent");
        Agent agent = agent(false, "Agent", primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void activeAgent_noPrimarySpec_isExcluded() {
        AgentEligibilityService elig = eligServiceAllowing("Agent");
        Agent agent = agent(true, "Agent", null);

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void activeAgentWithPrimarySpec_nullJobTitle_isIncluded() {
        // Empty allowlist is fail-open, so a null title still passes filter 2
        AgentEligibilityService elig = eligServiceAllowing();
        Agent agent = agent(true, null, primarySpec());

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).containsExactly(agent);
    }

    // -----------------------------------------------------------------
    // Order preservation
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // workingDaysKnown criterion (D-07)
    // -----------------------------------------------------------------

    @Test
    void activeAgentWithPrimarySpec_workingDaysUnknown_isExcluded() {
        AgentEligibilityService elig = eligServiceAllowing("Support Rep");
        Agent agent = agent(true, "Support Rep", primarySpec());
        agent.setWorkingDaysKnown(false);  // data-gap agent (blank/Variable customWorkingdays)

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void activeAgentWithPrimarySpec_workingDaysKnown_isIncluded() {
        AgentEligibilityService elig = eligServiceAllowing("Support Rep");
        Agent agent = agent(true, "Support Rep", primarySpec());
        agent.setWorkingDaysKnown(true);  // explicitly set (default is also true)

        List<Agent> result = SolverService.filterEligible(List.of(agent), TENANT, elig);

        assertThat(result).containsExactly(agent);
    }

    // -----------------------------------------------------------------
    // Order preservation
    // -----------------------------------------------------------------

    @Test
    void filterPreservesOrderOfSurvivingAgents() {
        AgentEligibilityService elig = eligServiceAllowing("Regular", "AlsoRegular");

        Agent a1 = agent(true, "Regular", primarySpec());
        Agent a2 = agent(true, "Excluded", primarySpec());   // not on the allowlist
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

    /**
     * Builds a service whose allowlist contains exactly {@code patterns}. Passing none leaves the
     * allowlist empty, which is fail-open — every title passes.
     */
    private AgentEligibilityService eligServiceAllowing(String... patterns) {
        JobTitleIncludePatternRepository repo = mock(JobTitleIncludePatternRepository.class);
        when(repo.findByTenantId(anyLong())).thenReturn(
                Arrays.stream(patterns).map(p -> {
                    JobTitleIncludePattern e = new JobTitleIncludePattern();
                    e.setTenantId(TENANT);
                    e.setPattern(p);
                    return e;
                }).toList());
        return new AgentEligibilityService(mock(JobTitleConfigRepository.class), repo);
    }
}
