package com.wfm.integration;

import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.service.AgentEligibilityService;
import com.wfm.service.ClientManagementService;
import com.wfm.service.DeskAssignmentUploadService;
import com.wfm.service.UsualShiftService;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * MRG-02/D-06/D-07/D-08: field-by-field precedence coverage for the six contested identity
 * fields (First name, Last name, Email, Department, Job title, Active status) across the
 * populated/blank/whitespace combinations the merge engine must resolve identically.
 */
class MergePrecedenceTest {

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
    private AgentUsualShiftRepository agentUsualShiftRepository;
    private ShiftTemplateRepository shiftTemplateRepository;
    private UsualShiftService usualShiftService;
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

        agentUsualShiftRepository = mock(AgentUsualShiftRepository.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        usualShiftService = mock(UsualShiftService.class);
        when(shiftTemplateRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService, agentMergeService, transactionTemplate,
                agentUsualShiftRepository, shiftTemplateRepository, usualShiftService);

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
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.usualShiftHeader(d));
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

    private Agent uploadAndCaptureSavedAgent(BambooEmployee employee, String bamboohrId, String firstName,
                                              String lastName, String jobTitle, String email, String department,
                                              String active) throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of(employee));
        MockMultipartFile file = buildWorkbook(bamboohrId, firstName, lastName, jobTitle, email, department, active);
        service.uploadDeskAssignments(file);
        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private BambooEmployee employee(String department, String jobTitle) {
        return new BambooEmployee("B1", "Bamboo Name", "bamboo@example.com", department, jobTitle,
                "Active", "Full-Time", null, null, null);
    }

    // ------------------------------------------------------------------ //
    //  Job title -- populated/differing, populated/blank, blank/populated  //
    // ------------------------------------------------------------------ //

    @Test
    void jobTitle_bambooPopulated_sheetDiffers_bambooWins() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", "Senior Agent"),
                "B1", "First", "Last", "Junior Agent", null, null, null);
        assertThat(saved.getJobTitle()).isEqualTo("Senior Agent");
    }

    @Test
    void jobTitle_bambooPopulated_sheetBlank_bambooWins() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", "Senior Agent"),
                "B1", "First", "Last", null, null, null, null);
        assertThat(saved.getJobTitle()).isEqualTo("Senior Agent");
    }

    @Test
    void jobTitle_bambooBlank_sheetPopulated_sheetFillsGap() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", null),
                "B1", "First", "Last", "Sheet Title", null, null, null);
        assertThat(saved.getJobTitle()).isEqualTo("Sheet Title");
    }

    // ------------------------------------------------------------------ //
    //  Department -- whitespace-only BambooHR treated as blank (D-06)     //
    // ------------------------------------------------------------------ //

    @Test
    void department_bambooWhitespaceOnly_sheetPopulated_treatedAsAbsent_sheetWins() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("   ", "Agent"),
                "B1", "First", "Last", null, null, "Sheet Dept", null);
        assertThat(saved.getDepartment()).isEqualTo("Sheet Dept");
    }

    @Test
    void department_bambooBlank_sheetBlank_storedValueUntouched() throws Exception {
        Agent existing = new Agent();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setBamboohrId("B1");
        existing.setDepartment("Previously Stored Dept");
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.of(existing));

        Agent saved = uploadAndCaptureSavedAgent(employee(null, "Agent"),
                "B1", "First", "Last", null, null, null, null);
        assertThat(saved.getDepartment()).isEqualTo("Previously Stored Dept");
    }

    // ------------------------------------------------------------------ //
    //  Email -- whitespace-only sheet difference is agreement (trim)      //
    // ------------------------------------------------------------------ //

    @Test
    void email_sheetDiffersOnlyBySurroundingWhitespace_treatedAsAgreement() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", "Agent"),
                "B1", "First", "Last", null, "  bamboo@example.com  ", null, null);
        assertThat(saved.getEmail()).isEqualTo("bamboo@example.com");
    }

    // ------------------------------------------------------------------ //
    //  First / Last name -- BambooHR's displayName split wins when it has data //
    // ------------------------------------------------------------------ //

    @Test
    void firstAndLastName_bambooPopulated_sheetDiffers_bambooWins() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", "Agent"),
                "B1", "SheetFirst", "SheetLast", null, null, null, null);
        assertThat(saved.getFirstName()).isEqualTo("Bamboo");
        assertThat(saved.getLastName()).isEqualTo("Name");
    }

    // ------------------------------------------------------------------ //
    //  Active status -- sheet cannot override an Active BambooHR status   //
    // ------------------------------------------------------------------ //

    @Test
    void activeStatus_bambooActive_sheetSaysInactive_bambooWins() throws Exception {
        Agent saved = uploadAndCaptureSavedAgent(employee("Support", "Agent"),
                "B1", "First", "Last", null, null, null, "false");
        assertThat(saved.isActive()).isTrue();
    }
}
