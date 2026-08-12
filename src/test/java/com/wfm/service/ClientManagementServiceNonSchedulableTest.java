package com.wfm.service;

import com.wfm.exception.ConflictException;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that ClientManagementService.assignEmployeesToDesk rejects agents
 * whose job titles are not on the schedulable-titles allowlist with a ConflictException.
 */
class ClientManagementServiceNonSchedulableTest {

    private BambooHRClient bambooHRClient;
    private AppConfigurationService configurationService;
    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private AgentEligibilityService agentEligibilityService;

    private ClientManagementService service;

    private static final long TENANT_ID = 42L;
    private static final String NON_SCHEDULABLE_TITLE = "Quality Inspector";
    private static final String REGULAR_TITLE = "Customer Support Agent";

    @BeforeEach
    void setUp() {
        bambooHRClient = mock(BambooHRClient.class);
        configurationService = mock(AppConfigurationService.class);
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        // Job-title allowlist inactive for this suite; without this stub the mock defaults
        // to false and every row would be skipped as "not in the configured allowlist".
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        service = new ClientManagementService(
                bambooHRClient, configurationService, agentRepository,
                deskRepository, agentEligibilityService);

        // The job-title allowlist replaced the non-schedulable denylist as the single control
        // for schedulability (2026-08-12). REGULAR_TITLE is on the allowlist; the other is not.
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, NON_SCHEDULABLE_TITLE)).thenReturn(false);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, REGULAR_TITLE)).thenReturn(true);
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), isNull())).thenReturn(true);

        // Config service returns default cache size
        when(configurationService.getConfigValue(anyString())).thenReturn(null);
    }

    // ------------------------------------------------------------------ //
    //  Helper factories                                                    //
    // ------------------------------------------------------------------ //

    private Desk desk(UUID id) {
        Desk d = new Desk();
        d.setId(id);
        d.setTenantId(TENANT_ID);
        d.setName("Test Desk");
        return d;
    }

    private Agent agent(String bambooId, String name, String jobTitle) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT_ID);
        a.setBamboohrId(bambooId);
        a.setName(name);
        a.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        a.setJobTitle(jobTitle);
        a.setActive(true);
        return a;
    }

    // ------------------------------------------------------------------ //
    //  Pre-populate cache with a BambooHR employee so assignEmployeesToDesk
    //  can find it. We do this by injecting it via listEmployeesByDepartment.
    // ------------------------------------------------------------------ //

    private void seedCache(String bambooId, String name, String jobTitle) {
        com.wfm.integration.BambooEmployee emp = new com.wfm.integration.BambooEmployee(
                bambooId, name, name.toLowerCase() + "@example.com", "Dept", jobTitle, "Active",
                "Active", "Mon-Fri", String.valueOf(TENANT_ID), null);
        when(bambooHRClient.listEmployees(anyString(), anyString())).thenReturn(List.of(emp));
        service.listEmployeesByDepartment(String.valueOf(TENANT_ID), "Dept", true);
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void nonSchedulableAgent_throwsConflictException() {
        UUID deskId = UUID.randomUUID();
        String bambooId = "NS001";
        String agentName = "Victor";

        Desk d = desk(deskId);
        when(deskRepository.findByIdAndTenantId(deskId, TENANT_ID)).thenReturn(Optional.of(d));

        Agent agent = agent(bambooId, agentName, NON_SCHEDULABLE_TITLE);
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, bambooId))
                .thenReturn(Optional.of(agent));

        // Seed cache so the employee can be found
        seedCache(bambooId, agentName, NON_SCHEDULABLE_TITLE);

        assertThatThrownBy(() -> service.assignEmployeesToDesk(TENANT_ID, deskId, List.of(bambooId)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("job title that is not schedulable:")
                .hasMessageContaining(NON_SCHEDULABLE_TITLE);
    }

    @Test
    void schedulableAgent_assignsSuccessfully() {
        UUID deskId = UUID.randomUUID();
        String bambooId = "S001";
        String agentName = "Wendy";

        Desk d = desk(deskId);
        when(deskRepository.findByIdAndTenantId(deskId, TENANT_ID)).thenReturn(Optional.of(d));

        Agent agent = agent(bambooId, agentName, REGULAR_TITLE);
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, bambooId))
                .thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        seedCache(bambooId, agentName, REGULAR_TITLE);

        var results = service.assignEmployeesToDesk(TENANT_ID, deskId, List.of(bambooId));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo(agentName);
    }

    @Test
    void multipleAgents_oneNonSchedulable_throwsBeforePartialAssignment() {
        UUID deskId = UUID.randomUUID();
        String nsId = "NS002";
        String regularId = "S002";

        Desk d = desk(deskId);
        when(deskRepository.findByIdAndTenantId(deskId, TENANT_ID)).thenReturn(Optional.of(d));

        Agent nsAgent = agent(nsId, "Xavier", NON_SCHEDULABLE_TITLE);
        Agent regularAgent = agent(regularId, "Yara", REGULAR_TITLE);

        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, nsId))
                .thenReturn(Optional.of(nsAgent));
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, regularId))
                .thenReturn(Optional.of(regularAgent));

        com.wfm.integration.BambooEmployee empNs = new com.wfm.integration.BambooEmployee(
                nsId, "Xavier", "xavier@example.com", "Dept", NON_SCHEDULABLE_TITLE, "Active",
                "Active", "Mon-Fri", String.valueOf(TENANT_ID), null);
        com.wfm.integration.BambooEmployee empReg = new com.wfm.integration.BambooEmployee(
                regularId, "Yara", "yara@example.com", "Dept", REGULAR_TITLE, "Active",
                "Active", "Mon-Fri", String.valueOf(TENANT_ID), null);
        when(bambooHRClient.listEmployees(anyString(), anyString())).thenReturn(List.of(empNs, empReg));
        service.listEmployeesByDepartment(String.valueOf(TENANT_ID), "Dept", true);

        // The non-schedulable is first in the list
        assertThatThrownBy(() ->
                service.assignEmployeesToDesk(TENANT_ID, deskId, List.of(nsId, regularId)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("job title that is not schedulable:");

        // No save should have been called
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void conflictExceptionMessage_containsAgentNameAndTitle() {
        UUID deskId = UUID.randomUUID();
        String bambooId = "NS003";
        String agentName = "Zoe Tester";

        Desk d = desk(deskId);
        when(deskRepository.findByIdAndTenantId(deskId, TENANT_ID)).thenReturn(Optional.of(d));

        Agent agent = agent(bambooId, agentName, NON_SCHEDULABLE_TITLE);
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, bambooId))
                .thenReturn(Optional.of(agent));

        seedCache(bambooId, agentName, NON_SCHEDULABLE_TITLE);

        assertThatThrownBy(() -> service.assignEmployeesToDesk(TENANT_ID, deskId, List.of(bambooId)))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    assertThat(msg).contains(agentName);
                    assertThat(msg).contains("job title that is not schedulable:");
                    assertThat(msg).contains(NON_SCHEDULABLE_TITLE);
                });
    }
}
