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
 *
 * Updated for Phase 10 (D-01/D-08): the sheet name IS the desk (no per-row
 * "Desk" column) and matching is by BambooHR ID only, via
 * findCachedEmployee(bamboohrId, null, null).
 */
class DeskAssignmentUploadNonSchedulableRejectTest {

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
    private static final String NON_SCHEDULABLE_TITLE = "Quality Assurance";
    private static final String[] DAY_HEADERS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

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

        // Configure non-schedulable title
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, NON_SCHEDULABLE_TITLE)).thenReturn(true);
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, "Agent")).thenReturn(false);
        // Handle null jobTitle gracefully
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Builds a one-sheet-per-desk workbook (D-01): sheet name = desk name, headers =
     * BambooHR ID + Monday..Sunday (no per-row Desk column), one row per agent.
     */
    private MockMultipartFile buildDeskWorkbook(String deskName, String[][] rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(deskName);

        List<String> headers = new ArrayList<>(List.of("BambooHR ID", "Name", "Email"));
        headers.addAll(List.of(DAY_HEADERS));

        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            header.createCell(c).setCellValue(headers.get(c));
        }

        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                String value = rows[r][c];
                if (value == null) continue;
                // Day cells (D-03) hold a real number for hours, or the MANDATORY/PTO
                // keyword as text — mirror that here instead of always writing a string,
                // since the parser reads day cells via getNumericCellValue() (D-10).
                if (value.matches("-?\\d+(\\.\\d+)?")) {
                    row.createCell(c).setCellValue(Double.parseDouble(value));
                } else {
                    row.createCell(c).setCellValue(value);
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private String[] agentRow(String bamboohrId, String name, String email) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, name, email));
        row.addAll(List.of(FULL_WEEK));
        return row.toArray(new String[0]);
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
        when(clientManagementService.findCachedEmployee(eq("B100"), isNull(), isNull()))
                .thenReturn(cached);

        MockMultipartFile file = buildDeskWorkbook("Support Desk",
                new String[][] { agentRow("B100", "Eve", "eve@example.com") });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedDetails()).hasSize(1);

        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.rowNumber()).isEqualTo(2);
        assertThat(skipped.bamboohrId()).isEqualTo("B100");
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
        when(clientManagementService.findCachedEmployee(eq("B200"), isNull(), isNull()))
                .thenReturn(cached);

        MockMultipartFile file = buildDeskWorkbook("Sales Desk",
                new String[][] { agentRow("B200", "Frank", "frank@example.com") });

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

        when(clientManagementService.findCachedEmployee(eq("B300"), isNull(), isNull()))
                .thenReturn(cachedQa);
        when(clientManagementService.findCachedEmployee(eq("B301"), isNull(), isNull()))
                .thenReturn(cachedReg);

        MockMultipartFile file = buildDeskWorkbook("Mixed Desk", new String[][] {
                agentRow("B300", "Grace", "grace@example.com"),
                agentRow("B301", "Henry", "henry@example.com")
        });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.bamboohrId()).isEqualTo("B300");
        assertThat(skipped.reason()).startsWith("Agent has non-schedulable job title:");
    }
}
