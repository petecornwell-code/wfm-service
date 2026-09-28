package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The department search, narrowed to people this system can actually schedule.
 *
 * <p>The two filters have different histories and are asserted separately. The BambooHR status
 * filter has always been there. The job-title allowlist is new here, and was added because the
 * screen otherwise offered people that the upload would later refuse — with the refusal landing far
 * from the click that caused it.
 */
class ClientManagementSearchFilterTest {

    private static final long TENANT = 1L;
    private static final String TENANT_ID = "1";

    private BambooHRClient bambooHRClient;
    private AgentEligibilityService agentEligibilityService;
    private ClientManagementService service;

    @BeforeEach
    void setUp() {
        bambooHRClient = mock(BambooHRClient.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        AppConfigurationService configurationService = mock(AppConfigurationService.class);
        // The cache-size lookup; a mocked null makes the service fall back to its own default.
        when(configurationService.getConfigValue(any())).thenReturn(null);
        service = new ClientManagementService(bambooHRClient, configurationService,
                mock(AgentRepository.class), mock(DeskRepository.class), agentEligibilityService);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BambooEmployee employee(String id, String name, String jobTitle, String status) {
        return new BambooEmployee(id, name, name.replace(' ', '.') + "@example.com",
                "Vinted - UA", jobTitle, status, "Full-time", "Mon-Fri", TENANT_ID, "Vinted - UA");
    }

    private void givenBambooReturns(BambooEmployee... employees) {
        when(bambooHRClient.listEmployees(eq(TENANT_ID), any())).thenReturn(List.of(employees));
    }

    @Test
    @DisplayName("only allowlisted job titles come back, and the rest are reported")
    void nonAllowlistedTitlesAreHiddenAndCounted() {
        givenBambooReturns(
                employee("1", "Mary Watson", "Customer Support Representative", "Active"),
                employee("2", "John Blake", "Team Lead", "Active"),
                employee("3", "Ana Silva", "Subject Matter Expert", "Active"));
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT, "Customer Support Representative"))
                .thenReturn(true);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT, "Team Lead")).thenReturn(false);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT, "Subject Matter Expert"))
                .thenReturn(false);

        ClientManagementService.EmployeeSearchResult result =
                service.listSchedulableEmployeesByDepartment(TENANT_ID, "Vinted - UA", false);

        assertThat(result.employees()).extracting(BambooEmployeeResponse::displayName)
                .containsExactly("Mary Watson");
        assertThat(result.hiddenByJobTitle()).isEqualTo(2);
        // Naming the titles is the point: an operator can compare them against the patterns rather
        // than concluding BambooHR is missing people.
        assertThat(result.hiddenJobTitles()).containsExactly("Team Lead", "Subject Matter Expert");
        assertThat(result.allowlistActive()).isTrue();
    }

    @Test
    @DisplayName("inactive employees never appear, whatever their job title")
    void inactivesAreExcluded() {
        givenBambooReturns(
                employee("1", "Mary Watson", "Customer Support Representative", "Active"),
                employee("2", "John Blake", "Customer Support Representative", "Inactive"),
                employee("3", "Ana Silva", "Customer Support Representative", "Terminated"));
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        ClientManagementService.EmployeeSearchResult result =
                service.listSchedulableEmployeesByDepartment(TENANT_ID, "Vinted - UA", false);

        assertThat(result.employees()).extracting(BambooEmployeeResponse::displayName)
                .containsExactly("Mary Watson");
        // Status filtering happens before the allowlist, so an inactive person is not also counted
        // as a job-title exclusion -- the two numbers must not double-count the same person.
        assertThat(result.hiddenByJobTitle()).isZero();
    }

    @Test
    @DisplayName("a tenant with no patterns configured loses nobody")
    void allowlistInactiveHidesNothing() {
        givenBambooReturns(
                employee("1", "Mary Watson", "Customer Support Representative", "Active"),
                employee("2", "John Blake", "Team Lead", "Active"));
        // isIncludedByTitleAllowlist returns true for everyone when no patterns exist.
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        ClientManagementService.EmployeeSearchResult result =
                service.listSchedulableEmployeesByDepartment(TENANT_ID, "Vinted - UA", false);

        assertThat(result.employees()).hasSize(2);
        assertThat(result.hiddenByJobTitle()).isZero();
        assertThat(result.allowlistActive()).isFalse();
    }

    @Test
    @DisplayName("a blank job title is excluded while the allowlist is active, and named as blank")
    void blankTitleIsReportedReadably() {
        givenBambooReturns(
                employee("1", "Mary Watson", "Customer Support Representative", "Active"),
                employee("2", "John Blake", null, "Active"));
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT, "Customer Support Representative"))
                .thenReturn(true);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT, null)).thenReturn(false);

        ClientManagementService.EmployeeSearchResult result =
                service.listSchedulableEmployeesByDepartment(TENANT_ID, "Vinted - UA", false);

        assertThat(result.hiddenJobTitles()).containsExactly("(no job title)");
    }
}
