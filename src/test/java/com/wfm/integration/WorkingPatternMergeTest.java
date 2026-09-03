package com.wfm.integration;

import com.wfm.dto.MergeReportEntry;
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
 * MRG-03/D-05: the merge report surfaces a {@code Working pattern (Mon–Sun)} row whenever the
 * spreadsheet's day group fills a BambooHR field-4517 gap or replaces a differing week, and
 * stays silent on agreement. Same mocked-collaborator / workbook-building style as
 * {@code MergeReportTest}; this class's {@code buildWorkbook} additionally accepts explicit
 * per-day-cell values, since MergeReportTest's fixture hardcodes every day cell to the same
 * uniform pattern and this suite needs to construct specific sheet-worked-weekday sets.
 */
class WorkingPatternMergeTest {

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

    /**
     * Same identity columns as MergeReportTest.buildWorkbook (fixed to avoid unrelated identity
     * divergence entries), but the seven Mon-Sun day cells are supplied explicitly — one value
     * per {@link EnrichedColumnLayout#DAY_ORDER} entry — so a specific sheet-worked-weekday set
     * can be constructed per test.
     */
    private MockMultipartFile buildWorkbook(String bamboohrId, String... dayCellValues) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");
        String[] headers = newShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        List<String> rowValues = new ArrayList<>(
                List.of(bamboohrId, "Bamboo", "Name", "Bamboo Title", "bamboo@example.com", "Bamboo Dept", "true"));
        rowValues.addAll(List.of(dayCellValues));

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

    private BambooEmployee employee(String customWorkingdays) {
        return new BambooEmployee("B1", "Bamboo Name", "bamboo@example.com", "Bamboo Dept", "Bamboo Title",
                "Active", "Full-Time", customWorkingdays, null, null);
    }

    private List<MergeReportEntry> patternRows(DeskAssignmentUploadService.DeskAssignmentUploadResult result) {
        return result.mergeReport().stream()
                .filter(e -> e.field().equals(AgentMergeService.FIELD_WORKING_PATTERN))
                .toList();
    }

    @Test
    void blankBambooHrPattern_sheetSuppliesAWeek_producesGapFillOutcome() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee(null)));
        // Mon-Fri worked, Sat/Sun MANDATORY off.
        MockMultipartFile file = buildWorkbook("B1", "8", "8", "8", "8", "8", "MANDATORY", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        List<MergeReportEntry> rows = patternRows(result);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).outcome()).isEqualTo(AgentMergeService.OUTCOME_GAP_FILLED);
        assertThat(rows.get(0).bambooValue()).isEqualTo("not stated");
        assertThat(rows.get(0).sheetValue()).isEqualTo("Mon, Tue, Wed, Thu, Fri");
    }

    @Test
    void literalVariableBambooHrPattern_sheetSuppliesAWeek_producesGapFillOutcome() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("Variable")));
        MockMultipartFile file = buildWorkbook("B1", "8", "8", "8", "8", "8", "MANDATORY", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        List<MergeReportEntry> rows = patternRows(result);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).outcome()).isEqualTo(AgentMergeService.OUTCOME_GAP_FILLED);
    }

    @Test
    void differingWorkingDaySets_producesReplacementOutcome() throws Exception {
        // BambooHR: Mon-Fri. Sheet: Tue-Sat worked -- differs.
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("Mon-Fri")));
        MockMultipartFile file = buildWorkbook("B1", "MANDATORY", "8", "8", "8", "8", "8", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        List<MergeReportEntry> rows = patternRows(result);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).outcome()).isEqualTo(AgentMergeService.OUTCOME_PATTERN_REPLACED);
        assertThat(rows.get(0).bambooValue()).isEqualTo("Mon, Tue, Wed, Thu, Fri");
        assertThat(rows.get(0).sheetValue()).isEqualTo("Tue, Wed, Thu, Fri, Sat");
    }

    @Test
    void matchingWorkingDaySets_producesNoPatternRow() throws Exception {
        // BambooHR: Mon-Fri. Sheet: also Mon-Fri (Sat/Sun MANDATORY off) -- same set.
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("Mon-Fri")));
        MockMultipartFile file = buildWorkbook("B1", "8", "8", "8", "8", "8", "MANDATORY", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(patternRows(result)).isEmpty();
    }

    @Test
    void bothSidesRenderedInMondayToSundayOrderRegardlessOfBambooHrListingOrder() throws Exception {
        // BambooHR's raw string lists days out of week order; the rendered comparison string
        // must still come out Mon-Sun. Sheet worked days deliberately differ (Tuesday only) so
        // a replacement row is actually emitted to inspect.
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("Wed, Mon, Fri")));
        MockMultipartFile file = buildWorkbook("B1",
                "MANDATORY", "8", "MANDATORY", "MANDATORY", "MANDATORY", "MANDATORY", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        List<MergeReportEntry> rows = patternRows(result);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bambooValue()).isEqualTo("Mon, Wed, Fri");
        assertThat(rows.get(0).sheetValue()).isEqualTo("Tue");
    }

    @Test
    void atMostOnePatternRowPerAgentPerUpload() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee("Mon-Fri")));
        MockMultipartFile file = buildWorkbook("B1", "MANDATORY", "8", "8", "8", "8", "8", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(patternRows(result)).hasSize(1);
    }

    @Test
    void repeatedUploadOfUnchangedFixture_producesIdenticalReport() throws Exception {
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee(null)));
        MockMultipartFile file1 = buildWorkbook("B1", "8", "8", "8", "8", "8", "MANDATORY", "MANDATORY");
        MockMultipartFile file2 = buildWorkbook("B1", "8", "8", "8", "8", "8", "MANDATORY", "MANDATORY");

        DeskAssignmentUploadService.DeskAssignmentUploadResult first = service.uploadDeskAssignments(file1);
        DeskAssignmentUploadService.DeskAssignmentUploadResult second = service.uploadDeskAssignments(file2);

        assertThat(second.mergeReport()).isEqualTo(first.mergeReport());
    }
}
