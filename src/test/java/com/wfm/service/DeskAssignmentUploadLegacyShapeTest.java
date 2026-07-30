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
 * Tests that the 6-col legacy spreadsheet shape is accepted and produces
 * correct SkippedRow records when rows are skipped.
 *
 * Legacy headers: BambooHR ID | Name | Email | Desk Assignment | Specialty 1 | ...
 */
class DeskAssignmentUploadLegacyShapeTest {

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

        // Set up TenantContext — service calls TenantContext.getTenantId()
        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        // By default, agents are not non-schedulable
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helper: build a legacy 6-col workbook as MockMultipartFile          //
    // ------------------------------------------------------------------ //

    /**
     * Builds a legacy workbook with a single data row.
     * Row values: [bamboohrId, name, email, deskAssignment] (nulls → empty cells)
     */
    private MockMultipartFile legacyWorkbook(String bamboohrId, String name, String email, String desk)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        // Header row (legacy 6-col)
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("BambooHR ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Email");
        header.createCell(3).setCellValue("Desk Assignment");
        header.createCell(4).setCellValue("Specialty 1");
        header.createCell(5).setCellValue("Specialty 2");

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

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void legacyShape_missingDeskAssignment_producesSkippedRow() throws Exception {
        // Row has no desk → should be skipped with "Missing Desk Assignment"
        MockMultipartFile file = legacyWorkbook("B001", "Alice", "alice@example.com", null);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        when(clientManagementService.findCachedEmployee(any(), any(), any())).thenReturn(null);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedDetails()).hasSize(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.rowNumber()).isEqualTo(2);
        assertThat(skipped.reason()).isEqualTo("Missing Desk Assignment");
    }

    @Test
    void legacyShape_deskNotFound_producesSkippedRow() throws Exception {
        MockMultipartFile file = legacyWorkbook("B002", "Bob", "bob@example.com", "Nonexistent Desk");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        when(clientManagementService.findCachedEmployee(any(), any(), any())).thenReturn(null);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.rowNumber()).isEqualTo(2);
        assertThat(skipped.reason()).contains("Nonexistent Desk").contains("not found");
    }

    @Test
    void legacyShape_agentAssigned_incrementsAssignedCount() throws Exception {
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B003");
        agent.setName("Carol");
        agent.setEmail("carol@example.com");
        agent.setJobTitle("Agent");
        agent.setActive(true);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B003"))
                .thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());

        BambooEmployeeResponse cached = new BambooEmployeeResponse(
                "B003", "Carol", "carol@example.com", "Support", "Agent", "Active");
        when(clientManagementService.findCachedEmployee(anyString(), anyString(), anyString()))
                .thenReturn(cached);

        MockMultipartFile file = legacyWorkbook("B003", "Carol", "carol@example.com", "Support Desk");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.skippedDetails()).isEmpty();
    }

    @Test
    void legacyShape_skippedRowContainsBamboohrIdAndName() throws Exception {
        // Even when desk is not found, skipped row should carry bamboohrId + name from the row
        MockMultipartFile file = legacyWorkbook("B999", "Dave", "dave@example.com", "Missing Desk");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedDetails()).hasSize(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.bamboohrId()).isEqualTo("B999");
        assertThat(skipped.name()).isEqualTo("Dave");
    }
}
