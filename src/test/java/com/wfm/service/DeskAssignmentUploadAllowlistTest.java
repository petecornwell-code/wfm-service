package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.SkippedRow;
import com.wfm.model.*;
import com.wfm.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Upload-side enforcement of the two roster filters added alongside the template allowlist:
 * inactive agents and agents whose job title fails the tenant's allowlist are skipped with
 * their own specific reasons rather than being silently imported.
 */
class DeskAssignmentUploadAllowlistTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;

    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;
    private static final String CSR = "Customer Support Representative";
    private static final String[] DAY_HEADERS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

    private Desk desk;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        clientManagementService = mock(ClientManagementService.class);
        agentPreferenceRepository = mock(AgentPreferenceRepository.class);
        agentExceptionRepository = mock(AgentExceptionRepository.class);
        agentDayHoursRepository = mock(AgentDayHoursRepository.class);
        specializationRepository = mock(SpecializationRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        // Nothing is non-schedulable in these tests — isolate the two new filters.
        when(agentEligibilityService.isNonSchedulable(anyLong(), any())).thenReturn(false);
        // Default: allowlist inactive.
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private MockMultipartFile workbookWith(String... bamboohrIds) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(desk.getName());

        List<String> headers = new ArrayList<>(List.of("BambooHR ID", "Name", "Email"));
        headers.addAll(List.of(DAY_HEADERS));
        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            header.createCell(c).setCellValue(headers.get(c));
        }

        for (int r = 0; r < bamboohrIds.length; r++) {
            Row row = sheet.createRow(r + 1);
            row.createCell(0).setCellValue(bamboohrIds[r]);
            row.createCell(1).setCellValue("Agent " + bamboohrIds[r]);
            row.createCell(2).setCellValue(bamboohrIds[r] + "@example.com");
            for (int d = 0; d < FULL_WEEK.length; d++) {
                row.createCell(3 + d).setCellValue(Double.parseDouble(FULL_WEEK[d]));
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    /**
     * Registers an agent + its BambooHR cache entry. {@code bambooStatus} drives the active flag,
     * because the parser refreshes agent.active from the cache before the inactive check runs.
     */
    private void registerAgent(String bamboohrId, String jobTitle, String bambooStatus) {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId(bamboohrId);
        agent.setName("Agent " + bamboohrId);
        agent.setEmail(bamboohrId + "@example.com");
        agent.setJobTitle(jobTitle);
        agent.setActive(true);

        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, bamboohrId))
                .thenReturn(Optional.of(agent));
        when(clientManagementService.findCachedEmployee(eq(bamboohrId), isNull(), isNull()))
                .thenReturn(new BambooEmployeeResponse(
                        bamboohrId, "Agent " + bamboohrId, bamboohrId + "@example.com",
                        "Support", jobTitle, bambooStatus));
    }

    private SkippedRow onlySkipped(DeskAssignmentUploadService.DeskAssignmentUploadResult result) {
        assertThat(result.skippedDetails()).hasSize(1);
        return result.skippedDetails().get(0);
    }

    // ------------------------------------------------------------------ //
    //  Inactive                                                            //
    // ------------------------------------------------------------------ //

    @Test
    void inactiveAgent_isSkippedWithSpecificReason() throws Exception {
        registerAgent("B100", CSR, "Inactive");

        var result = service.uploadDeskAssignments(workbookWith("B100"));

        assertThat(result.assignedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(onlySkipped(result).reason()).isEqualTo("Agent is not active");
    }

    @Test
    void activeAgent_isAssigned() throws Exception {
        registerAgent("B200", CSR, "Active");

        var result = service.uploadDeskAssignments(workbookWith("B200"));

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Allowlist                                                           //
    // ------------------------------------------------------------------ //

    @Test
    void titleFailingAllowlist_isSkippedWithSpecificReason() throws Exception {
        registerAgent("B300", "Team Lead", "Active");
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Team Lead")).thenReturn(false);

        var result = service.uploadDeskAssignments(workbookWith("B300"));

        assertThat(result.assignedCount()).isZero();
        SkippedRow skipped = onlySkipped(result);
        assertThat(skipped.bamboohrId()).isEqualTo("B300");
        assertThat(skipped.reason())
                .startsWith("Agent job title is not schedulable:")
                .contains("Team Lead");
    }

    @Test
    void titlePassingAllowlist_isAssigned() throws Exception {
        registerAgent("B400", "Senior " + CSR, "Active");
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Senior " + CSR)).thenReturn(true);

        var result = service.uploadDeskAssignments(workbookWith("B400"));

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }

    @Test
    void allowlistInactive_everyTitleStillImports() throws Exception {
        // Backwards compatibility: tenants with no configured patterns see no change.
        registerAgent("B500", "Finance Analyst", "Active");

        var result = service.uploadDeskAssignments(workbookWith("B500"));

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Interaction                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void mixedWorkbook_eachExclusionReportsItsOwnReason() throws Exception {
        registerAgent("B600", CSR, "Active");          // imported
        registerAgent("B601", CSR, "Inactive");        // inactive
        registerAgent("B602", "Team Lead", "Active");  // not allowlisted
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Team Lead")).thenReturn(false);

        var result = service.uploadDeskAssignments(workbookWith("B600", "B601", "B602"));

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.skippedDetails())
                .extracting(SkippedRow::bamboohrId, SkippedRow::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("B601", "Agent is not active"),
                        org.assertj.core.api.Assertions.tuple("B602",
                                "Agent job title is not schedulable: Team Lead"));
    }

    // ------------------------------------------------------------------ //
    //  Unmatched / inactive BambooHR ids                                   //
    // ------------------------------------------------------------------ //

    @Test
    void unknownBambooHrId_reportsNotFound() throws Exception {
        // No agent registered, and no status recorded for the id.
        when(clientManagementService.findCachedEmployee(eq("B900"), isNull(), isNull())).thenReturn(null);
        when(clientManagementService.findEmployeeStatus(TENANT_ID, "B900")).thenReturn(null);

        var result = service.uploadDeskAssignments(workbookWith("B900"));

        assertThat(onlySkipped(result).reason()).isEqualTo("BambooHR ID not found");
    }

    @Test
    void formerEmployee_reportsInactiveRatherThanNotFound() throws Exception {
        // The upload cache holds ACTIVE employees only, so a former employee misses the cache
        // exactly like a bad id. The two need different operator action, so they must read
        // differently.
        when(clientManagementService.findCachedEmployee(eq("B901"), isNull(), isNull())).thenReturn(null);
        when(clientManagementService.findEmployeeStatus(TENANT_ID, "B901")).thenReturn("Inactive");

        var result = service.uploadDeskAssignments(workbookWith("B901"));

        assertThat(onlySkipped(result).reason())
                .isEqualTo("Agent is not active in BambooHR (status: Inactive)")
                .doesNotContain("not found");
    }

    // ------------------------------------------------------------------ //
    //  Working days                                                        //
    // ------------------------------------------------------------------ //

    @Test
    void successfulImport_marksWorkingDaysKnown() throws Exception {
        // All 7 day cells parsed, so the operator has explicitly stated this agent's working
        // days. Without this the flag stayed false whenever BambooHR field 4517 was blank or
        // "Variable", and SolverService.filterEligible silently dropped the agent — the enriched
        // upload supplied their hours and the solver ignored them anyway.
        registerAgent("B800", CSR, "Active");
        Agent stored = agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B800").orElseThrow();
        stored.setWorkingDaysKnown(false); // as left by a BambooHR refresh that could not parse 4517

        var result = service.uploadDeskAssignments(workbookWith("B800"));

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(stored.isWorkingDaysKnown())
                .as("upload supplied all 7 day cells, so working days are now known")
                .isTrue();
    }

    @Test
    void skippedRow_doesNotMarkWorkingDaysKnown() throws Exception {
        // A row rejected before the day cells are accepted must not claim to establish them.
        registerAgent("B801", CSR, "Inactive");
        Agent stored = agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B801").orElseThrow();
        stored.setWorkingDaysKnown(false);

        var result = service.uploadDeskAssignments(workbookWith("B801"));

        assertThat(result.assignedCount()).isZero();
        assertThat(stored.isWorkingDaysKnown()).isFalse();
    }

    @Test
    void inactiveTakesPrecedenceOverAllowlist_singleReasonPerRow() throws Exception {
        // An agent failing both filters is reported once, by the first check to fire, so the
        // per-sheet skipped count cannot double-count a single row.
        registerAgent("B700", "Team Lead", "Inactive");
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Team Lead")).thenReturn(false);

        var result = service.uploadDeskAssignments(workbookWith("B700"));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(onlySkipped(result).reason()).isEqualTo("Agent is not active");
    }
}
