package com.wfm.service;

import com.wfm.integration.AgentMergeService;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.WorkingDaysSource;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
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
 * MRG-06/D-14/D-15/D-16: upload-side proof that a spreadsheet-supplied week makes an
 * agent solver-eligible (workingDaysKnown flips false-to-true), is recorded as
 * spreadsheet-owned (workingDaysSource), is named in the newly-eligible list exactly
 * once per genuine transition, and that the flag alone does not bypass the solver's
 * other eligibility filters.
 */
class WorkingDaysKnownTest {

    private static final long TENANT_ID = 1L;
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

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
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static String[] newShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        return headers.toArray(new String[0]);
    }

    private MockMultipartFile buildWorkbook(String bamboohrId, String firstName, String lastName,
                                             String[] dayCells) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");
        String[] headers = newShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        List<String> rowValues = new ArrayList<>(
                List.of(nullToEmpty(bamboohrId), nullToEmpty(firstName), nullToEmpty(lastName), "", "", "", ""));
        rowValues.addAll(List.of(dayCells));

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

    private BambooEmployee employee(String id, String jobTitle) {
        return new BambooEmployee(id, "Bamboo Name", "bamboo@example.com", "Bamboo Dept", jobTitle,
                "Active", "Full-Time", null, null, null);
    }

    // ------------------------------------------------------------------ //
    //  Upload-side workingDaysKnown / workingDaysSource / newly-eligible   //
    // ------------------------------------------------------------------ //

    @Test
    void agentWorkingDaysUnknown_sheetSuppliesFullWeek_becomesEligibleAndListed() throws Exception {
        Agent existing = new Agent();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setBamboohrId("B1");
        existing.setName("Bamboo Name");
        existing.setWorkingDaysKnown(false);
        existing.setWorkingDaysSource(WorkingDaysSource.BAMBOOHR);
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.of(existing));
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("B1", "Agent")));

        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", FULL_WEEK);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(existing.isWorkingDaysKnown()).isTrue();
        assertThat(existing.getWorkingDaysSource()).isEqualTo(WorkingDaysSource.SPREADSHEET);
        assertThat(result.newlyEligibleAgents()).containsExactly("Bamboo Name");
    }

    @Test
    void agentAlreadyWorkingDaysKnown_sourceBecomesSpreadsheet_butNotListed() throws Exception {
        Agent existing = new Agent();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setBamboohrId("B1");
        existing.setName("Bamboo Name");
        existing.setWorkingDaysKnown(true);
        existing.setWorkingDaysSource(WorkingDaysSource.BAMBOOHR);
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.of(existing));
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("B1", "Agent")));

        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", FULL_WEEK);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(existing.isWorkingDaysKnown()).isTrue();
        assertThat(existing.getWorkingDaysSource()).isEqualTo(WorkingDaysSource.SPREADSHEET);
        assertThat(result.newlyEligibleAgents()).isEmpty();
    }

    @Test
    void rowSkippedBeforeWriteStep_contributesNothingToNewlyEligibleList() throws Exception {
        // BambooHR ID unknown to the fresh snapshot -- row is skipped before the write step,
        // so it must never reach the newly-eligible accumulator.
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull())).thenReturn(List.of());

        MockMultipartFile file = buildWorkbook("UNKNOWN", "Ghost", "Agent", FULL_WEEK);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.newlyEligibleAgents()).isEmpty();
    }

    @Test
    void rowSkipped_invalidDayCell_contributesNothingToNewlyEligibleList() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("B1", "Agent")));
        String[] badWeek = {"8", "8", "8", "8", "8", "0", "not-a-number"};

        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", badWeek);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.newlyEligibleAgents()).isEmpty();
    }

    @Test
    void rowSkipped_jobTitleNotOnAllowlist_contributesNothingToNewlyEligibleList() throws Exception {
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(false);
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("B1", "Director")));

        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", FULL_WEEK);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.newlyEligibleAgents()).isEmpty();
    }

    // ------------------------------------------------------------------ //
    //  The flag opens the solver gate but does not bypass the others      //
    // ------------------------------------------------------------------ //

    @Test
    void newlyEligibleAgent_passesFilterEligible_whenOtherFiltersSatisfied() throws Exception {
        Agent captured = uploadAndCaptureSavedAgent();
        captured.setActive(true);
        captured.setJobTitle("Agent");
        captured.setPrimarySpecialization(specialization());

        AgentEligibilityService elig = mock(AgentEligibilityService.class);
        when(elig.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        List<Agent> result = SolverService.filterEligible(List.of(captured), TENANT_ID, elig);

        assertThat(result).containsExactly(captured);
    }

    @Test
    void newlyEligibleAgent_stillExcluded_whenInactive() throws Exception {
        Agent captured = uploadAndCaptureSavedAgent();
        captured.setActive(false);
        captured.setJobTitle("Agent");
        captured.setPrimarySpecialization(specialization());

        AgentEligibilityService elig = mock(AgentEligibilityService.class);
        when(elig.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        List<Agent> result = SolverService.filterEligible(List.of(captured), TENANT_ID, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void newlyEligibleAgent_stillExcluded_whenJobTitleNotAllowed() throws Exception {
        Agent captured = uploadAndCaptureSavedAgent();
        captured.setActive(true);
        captured.setJobTitle("Director");
        captured.setPrimarySpecialization(specialization());

        AgentEligibilityService elig = mock(AgentEligibilityService.class);
        when(elig.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(false);

        List<Agent> result = SolverService.filterEligible(List.of(captured), TENANT_ID, elig);

        assertThat(result).isEmpty();
    }

    @Test
    void newlyEligibleAgent_stillExcluded_whenPrimarySpecializationNull() throws Exception {
        Agent captured = uploadAndCaptureSavedAgent();
        captured.setActive(true);
        captured.setJobTitle("Agent");
        captured.setPrimarySpecialization(null);

        AgentEligibilityService elig = mock(AgentEligibilityService.class);
        when(elig.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        List<Agent> result = SolverService.filterEligible(List.of(captured), TENANT_ID, elig);

        assertThat(result).isEmpty();
    }

    private Agent uploadAndCaptureSavedAgent() throws Exception {
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.empty());
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("B1", "Agent")));

        MockMultipartFile file = buildWorkbook("B1", "Bamboo", "Name", FULL_WEEK);
        service.uploadDeskAssignments(file);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository, atLeastOnce()).save(captor.capture());
        Agent captured = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(captured.isWorkingDaysKnown()).isTrue();
        assertThat(captured.getWorkingDaysSource()).isEqualTo(WorkingDaysSource.SPREADSHEET);
        return captured;
    }

    private Specialization specialization() {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT_ID);
        s.setDeskId(desk.getId());
        s.setName("General");
        return s;
    }
}
