package com.wfm.integration;

import com.wfm.dto.MergeReportEntry;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.service.AgentEligibilityService;
import com.wfm.service.ClientManagementService;
import com.wfm.service.DeskAssignmentUploadService;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MRG-04/MRG-05/D-11: report shape coverage -- entry presence only on genuine divergence or
 * gap-fill, silent-agreement suppression, the fixed field ordering, the at-most-one-entry-
 * per-(agent,field) guarantee, and deterministic output across repeated uploads of the same
 * unchanged fixture.
 */
class MergeReportTest {

    private static final long TENANT_ID = 1L;

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;
    private BambooHRClient bambooHRClient;
    private AgentMergeService agentMergeService;
    private TransactionTemplate transactionTemplate;
    private DeskAssignmentUploadService service;

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
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        bambooHRClient = mock(BambooHRClient.class);
        agentMergeService = new AgentMergeService(bambooHRClient);
        when(bambooHRClient.listTimeOff(anyString(), any(), any())).thenReturn(List.of());

        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            java.util.function.Consumer<Object> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService, agentMergeService, transactionTemplate);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Support");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String[] newShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        return headers.toArray(new String[0]);
    }

    /** bamboohrId, firstName, lastName, jobTitle, email, department, active, Mon..Sun */
    private MockMultipartFile buildWorkbook(String bamboohrId, String firstName, String lastName,
                                             String jobTitle, String email, String department, String active)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");
        String[] headers = newShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        List<String> rowValues = new ArrayList<>(
                List.of(nullToEmpty(bamboohrId), nullToEmpty(firstName), nullToEmpty(lastName),
                        nullToEmpty(jobTitle), nullToEmpty(email), nullToEmpty(department), nullToEmpty(active)));
        rowValues.addAll(List.of("8", "8", "8", "8", "8", "0", "0"));

        Row row = sheet.createRow(1);
        for (int c = 0; c < rowValues.size(); c++) {
            String value = rowValues.get(c);
            if (value == null || value.isEmpty()) continue;
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                row.createCell(c).setCellValue(Double.parseDouble(value));
            } else {
                row.createCell(c).setCellValue(value);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private BambooEmployee employee() {
        return new BambooEmployee("B1", "Bamboo Name", "bamboo@example.com", "Bamboo Dept", "Bamboo Title",
                "Active", "Full-Time", null, null, null);
    }

    @Test
    void agreementAndBlankFields_emitNoEntries() throws Exception {
        // Sheet supplies exactly what BambooHR already has for every field -- silent agreement,
        // D-11, must emit nothing.
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of(employee()));
        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", "Bamboo Title",
                "bamboo@example.com", "Bamboo Dept", "true");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.mergeReport()).isEmpty();
    }

    @Test
    void divergentFields_emitOneEntryEach_inFixedFieldOrder() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of(employee()));
        // Every identity field differs from BambooHR's value -- expect one override entry per
        // field, in the fixed order: First name, Last name, Email, Department, Job title, Active status.
        MockMultipartFile file = buildWorkbook("B1", "SheetFirst", "SheetLast", "Sheet Title",
                "sheet@example.com", "Sheet Dept", "false");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.mergeReport()).extracting(MergeReportEntry::field)
                .containsExactly("First name", "Last name", "Email", "Department", "Job title", "Active status");
        assertThat(result.mergeReport()).allSatisfy(entry ->
                assertThat(entry.outcome()).isEqualTo(AgentMergeService.OUTCOME_BAMBOOHR_OVERRIDE));
    }

    @Test
    void atMostOneEntryPerAgentPerField() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of(employee()));
        MockMultipartFile file = buildWorkbook("B1", "SheetFirst", "SheetLast", "Sheet Title",
                "sheet@example.com", "Sheet Dept", "false");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        long distinctFieldCount = result.mergeReport().stream().map(MergeReportEntry::field).distinct().count();
        assertThat(distinctFieldCount).isEqualTo(result.mergeReport().size());
    }

    @Test
    void repeatedUploadOfUnchangedFixture_producesIdenticalReport() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of(employee()));
        MockMultipartFile file1 = buildWorkbook("B1", "SheetFirst", "SheetLast", "Sheet Title",
                "sheet@example.com", "Sheet Dept", "false");
        MockMultipartFile file2 = buildWorkbook("B1", "SheetFirst", "SheetLast", "Sheet Title",
                "sheet@example.com", "Sheet Dept", "false");

        DeskAssignmentUploadService.DeskAssignmentUploadResult first = service.uploadDeskAssignments(file1);
        DeskAssignmentUploadService.DeskAssignmentUploadResult second = service.uploadDeskAssignments(file2);

        assertThat(second.mergeReport()).isEqualTo(first.mergeReport());
    }

    @Test
    void gapFilledField_emitsGapFilledOutcome_notOverride() throws Exception {
        BambooEmployee employeeNoDept = new BambooEmployee("B1", "Bamboo Name", "bamboo@example.com",
                null, "Bamboo Title", "Active", "Full-Time", null, null, null);
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employeeNoDept));
        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", "Bamboo Title",
                "bamboo@example.com", "Sheet Dept", "true");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.mergeReport()).hasSize(1);
        MergeReportEntry entry = result.mergeReport().get(0);
        assertThat(entry.field()).isEqualTo("Department");
        assertThat(entry.outcome()).isEqualTo(AgentMergeService.OUTCOME_GAP_FILLED);
    }
}
