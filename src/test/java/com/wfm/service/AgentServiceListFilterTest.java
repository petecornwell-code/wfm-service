package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guard for {@code AgentRepository.findFiltered}'s optional {@code :search} parameter
 * (UAT test 14b).
 *
 * <p><b>Read this before trusting a green run.</b> These tests CANNOT reproduce the defect they
 * exist because of. {@code GET /api/v1/agents} with no parameters returned 500 on Postgres —
 * an untyped JDBC null is resolved to {@code bytea}, and {@code lower(bytea)} does not exist —
 * but H2, which this suite runs on, tolerates the untyped null and returns the right rows. So
 * every assertion below passed BEFORE the fix as well as after.
 *
 * <p>What they are for is the other direction: they pin the BEHAVIOUR the fix has to preserve, so
 * that a future simplification — dropping the {@code IS NULL} branch, or making the service pass
 * {@code ""} instead of null — cannot quietly change what a null search means. The Postgres
 * behaviour itself is unguarded by this suite and was verified against the live dev database
 * instead; closing that gap durably needs a Postgres-backed test (Testcontainers), which does not
 * exist in this project today.
 */
@DataJpaTest
@Import(AgentService.class)
@ActiveProfiles("test")
class AgentServiceListFilterTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listAgents_nullSearch_returnsEveryAgentInTheTenant() {
        UUID deskId = saveDesk();
        saveAgent(TENANT_A, deskId, "Alice");
        saveAgent(TENANT_A, null, "Bob");
        saveAgent(TENANT_B, null, "Carol"); // other tenant — must never appear

        PaginatedResponse<AgentResponse> page = agentService.listAgents(null, false, null, 50);

        assertThat(page.data()).extracting(AgentResponse::name)
                .containsExactly("Alice", "Bob");
    }

    @Test
    void listAgents_nullSearch_isNotAFilter_matchesTheEmptyStringCase() {
        // The two spellings of "no search term" must agree. On Postgres these took different code
        // paths for a while: omitting the parameter 500'd, passing "" worked.
        UUID deskId = saveDesk();
        saveAgent(TENANT_A, deskId, "Alice");
        saveAgent(TENANT_A, null, "Bob");

        assertThat(agentService.listAgents(null, false, null, 50).data())
                .extracting(AgentResponse::name)
                .isEqualTo(agentService.listAgents("", false, null, 50).data()
                        .stream().map(AgentResponse::name).toList());
    }

    @Test
    void listAgents_nullSearchWithUnassignedTrue_stillAppliesTheDeskFilter() {
        // The regression risk in the fix: casting :search must not disturb the OTHER optional
        // predicate. `GET /agents?unassigned=true` was one of the 500'ing calls.
        UUID deskId = saveDesk();
        saveAgent(TENANT_A, deskId, "Alice");
        saveAgent(TENANT_A, null, "Bob");

        PaginatedResponse<AgentResponse> page = agentService.listAgents(null, true, null, 50);

        assertThat(page.data()).extracting(AgentResponse::name).containsExactly("Bob");
    }

    @Test
    void listAgents_withSearchTerm_stillFiltersCaseInsensitivelyOnASubstring() {
        // The cast must not turn the LIKE into an equality or a case-sensitive match.
        UUID deskId = saveDesk();
        saveAgent(TENANT_A, deskId, "Alice Anderson");
        saveAgent(TENANT_A, deskId, "Bob Brown");

        assertThat(agentService.listAgents("ANDERS", false, null, 50).data())
                .extracting(AgentResponse::name).containsExactly("Alice Anderson");
        assertThat(agentService.listAgents("zzz", false, null, 50).data()).isEmpty();
    }

    private UUID saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    private void saveAgent(long tenantId, UUID deskId, String name) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setDeskId(deskId);
        agent.setName(name);
        agent.setBamboohrId("BHR-" + UUID.randomUUID());
        agent.setActive(true);
        agentRepository.save(agent);
    }
}
