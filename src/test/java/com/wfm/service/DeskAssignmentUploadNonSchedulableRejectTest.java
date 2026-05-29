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
 * Tests that desk-assignment upload rejects agents whose job titles are
 * configured as non-schedulable, producing a structured SkippedRow with
 * reason "Agent has non-schedulable job title: <title>".
 */
class DeskAssignmentUploadNonSchedulableRejectTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;

    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;
    private static final String NON_SCHEDULABLE_TITLE = "Quality Assurance";

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        clientManagementService = mock(ClientManagementService.class);
        agentPreferenceRepository = mock(AgentPreferenceRepository.class);
        agentExceptionRepository = mock(AgentExceptionRepository.class);
        specializationRepository = mock(SpecializationRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository,
                specializationRepository, agentEligibilityService);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        // Configure non-schedulable title
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, NON_SCHEDULABLE_TITLE)).thenReturn(true);
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, "Agent")).thenReturn(false);
        // Handle null jobTitle gracefully
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private MockMultipartFile legacyWorkbook(String bamboohrId, String name, String email, String desk)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("BambooHR ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Email");
        header.createCell(3).setCellValue("Desk Assignment");

        Row row = sheet.createRow(1);
        if (bamboohrId != null) row.createCell(0).setCellValue(bamboohrId);
        if (name != null) row.createCell(1).setCellValue(name);
        if (email != null) row.createCell(2).setCellValue(email);
        if (desk != null) row.createCell(3).setCellValue(desk);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    /** Builds a legacy workbook with two data rows. */
    private MockMultipartFile legacyWorkbookTwoRows(
            String b1, String n1, String e1, String d1,
            String b2, String n2, String e2, String d2) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("BambooHR ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Email");
        header.createCell(3).setCellValue("Desk Assignment");

        String[][] rows = {{b1, n1, e1, d1}, {b2, n2, e2, d2}};
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void nonSchedulableAgent_isSkippedWithStructuredReason() throws Exception {
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B100");
        agent.setName("Eve");
        agent.setEmail("eve@example.com");
        agent.setJobTitle(NON_SCHEDULABLE_TITLE);
        agent.setActive(true);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B100"))
                .thenReturn(Optional.of(agent));

        BambooEmployeeResponse cached = new BambooEmployeeResponse(
                "B100", "Eve", "eve@example.com", "QA", NON_SCHEDULABLE_TITLE, "Active");
        when(clientManagementService.findCachedEmployee(anyString(), anyString(), anyString()))
                .thenReturn(cached);

        MockMultipartFile file = legacyWorkbook("B100", "Eve", "eve@example.com", "Support Desk");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedDetails()).hasSize(1);

        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.rowNumber()).isEqualTo(2);
        assertThat(skipped.bamboohrId()).isEqualTo("B100");
        assertThat(skipped.name()).isEqualTo("Eve");
        assertThat(skipped.reason()).startsWith("Agent has non-schedulable job title:");
        assertThat(skipped.reason()).contains(NON_SCHEDULABLE_TITLE);
    }

    @Test
    void schedulableAgent_isAssigned() throws Exception {
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Sales Desk");

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B200");
        agent.setName("Frank");
        agent.setEmail("frank@example.com");
        agent.setJobTitle("Agent");
        agent.setActive(true);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B200"))
                .thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        BambooEmployeeResponse cached = new BambooEmployeeResponse(
                "B200", "Frank", "frank@example.com", "Sales", "Agent", "Active");
        when(clientManagementService.findCachedEmployee(anyString(), anyString(), anyString()))
                .thenReturn(cached);

        MockMultipartFile file = legacyWorkbook("B200", "Frank", "frank@example.com", "Sales Desk");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
    }

    @Test
    void mixedAgents_nonSchedulableSkipped_schedulableAssigned() throws Exception {
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Mixed Desk");

        Agent qaAgent = new Agent();
        qaAgent.setId(UUID.randomUUID());
        qaAgent.setTenantId(TENANT_ID);
        qaAgent.setBamboohrId("B300");
        qaAgent.setName("Grace");
        qaAgent.setEmail("grace@example.com");
        qaAgent.setJobTitle(NON_SCHEDULABLE_TITLE);
        qaAgent.setActive(true);

        Agent regularAgent = new Agent();
        regularAgent.setId(UUID.randomUUID());
        regularAgent.setTenantId(TENANT_ID);
        regularAgent.setBamboohrId("B301");
        regularAgent.setName("Henry");
        regularAgent.setEmail("henry@example.com");
        regularAgent.setJobTitle("Agent");
        regularAgent.setActive(true);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());

        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B300"))
                .thenReturn(Optional.of(qaAgent));
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B301"))
                .thenReturn(Optional.of(regularAgent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        BambooEmployeeResponse cachedQa = new BambooEmployeeResponse(
                "B300", "Grace", "grace@example.com", "QA", NON_SCHEDULABLE_TITLE, "Active");
        BambooEmployeeResponse cachedReg = new BambooEmployeeResponse(
                "B301", "Henry", "henry@example.com", "Sales", "Agent", "Active");

        when(clientManagementService.findCachedEmployee(eq("B300"), anyString(), anyString()))
                .thenReturn(cachedQa);
        when(clientManagementService.findCachedEmployee(eq("B301"), anyString(), anyString()))
                .thenReturn(cachedReg);

        MockMultipartFile file = legacyWorkbookTwoRows(
                "B300", "Grace", "grace@example.com", "Mixed Desk",
                "B301", "Henry", "henry@example.com", "Mixed Desk"
        );

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.bamboohrId()).isEqualTo("B300");
        assertThat(skipped.reason()).startsWith("Agent has non-schedulable job title:");
    }
}
