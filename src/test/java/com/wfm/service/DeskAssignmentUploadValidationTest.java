package com.wfm.service;

import com.wfm.dto.SkippedRow;
import com.wfm.integration.AgentMergeService;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.*;
import com.wfm.repository.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UPL-06: validation skip reasons (blank/negative/unrecognized day cells),
 * the &gt;24-hours clamp surfaced as a non-silent warning (D-10), and
 * per-sheet rollup counts (D-11).
 */
class DeskAssignmentUploadValidationTest {

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
    private Map<String, BambooEmployee> bambooEmployees;
    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();

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
        // Job-title allowlist inactive for this suite; without this stub the mock defaults
        // to false and every row would be skipped as "not in the configured allowlist".
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        bambooEmployees = new LinkedHashMap<>();
        bambooHRClient = mock(BambooHRClient.class);
        when(bambooHRClient.listEmployees(anyString(), isNull()))
                .thenAnswer(inv -> new ArrayList<>(bambooEmployees.values()));
        when(bambooHRClient.listTimeOff(anyString(), any(), any())).thenReturn(List.of());
        agentMergeService = new AgentMergeService(bambooHRClient);

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
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);

        Desk desk = new Desk();
        desk.setId(DESK_ID);
        desk.setTenantId(TENANT_ID);
        desk.setName("Support");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static String[] newShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.usualShiftHeader(d));
        return headers.toArray(new String[0]);
    }

    private static String[] agentRow(String bamboohrId, String firstName, String lastName, String[] dayCells) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, firstName, lastName, "Agent", "", "", ""));
        row.addAll(List.of(dayCells));
        return row.toArray(new String[0]);
    }

    private MockMultipartFile buildWorkbook(String deskName, String[][] rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(deskName);
        String[] headers = newShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                String value = rows[r][c];
                // "" is a deliberate blank cell (must NOT be skipped from writing --
                // it's what makes a day cell blank in the workbook); null skips column entirely.
                if (value == null) continue;
                if (value.isEmpty()) continue; // leave the cell absent -> blank per D-04
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

    private void stubCachedEmployee(String id, String displayName) {
        bambooEmployees.put(id, new BambooEmployee(id, displayName,
                displayName.toLowerCase() + "@example.com", "Support", "Agent", "Active",
                "Full-Time", null, null, null));
    }

    private SkippedRow skippedFor(DeskAssignmentUploadService.DeskAssignmentUploadResult result, String bamboohrId) {
        return result.skippedDetails().stream()
                .filter(sr -> bamboohrId.equals(sr.bamboohrId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SkippedRow for id " + bamboohrId));
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void mixedValidity_skipReasonsClampWarningAndRollupAreCorrect() throws Exception {
        stubCachedEmployee("V1", "Valid");
        stubCachedEmployee("V5", "Clamped");
        // V2 (blank), V3 (negative), V4 (unknown word) never reach the cache lookup --
        // they fail day-cell validation first, so no stub is needed for them.

        String[][] rows = new String[][] {
                agentRow("V1", "Valid", "Row", new String[] {"8", "8", "8", "8", "8", "0", "0"}),
                agentRow("V2", "Blank", "Row", new String[] {"8", "8", "", "8", "8", "0", "0"}),
                agentRow("V3", "Negative", "Row", new String[] {"-1", "8", "8", "8", "8", "0", "0"}),
                agentRow("V4", "Unknown", "Row", new String[] {"FOO", "8", "8", "8", "8", "0", "0"}),
                agentRow("V5", "Clamped", "Row", new String[] {"32", "8", "8", "8", "8", "0", "0"}),
        };
        MockMultipartFile file = buildWorkbook("Support", rows);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        // 2 imported (V1 clean, V5 clamped-but-imported), 3 skipped (V2/V3/V4)
        assertThat(result.assignedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(3);

        // WR-01: each rejection case gets a distinct, specific reason rather than one
        // generic "blank or invalid" message.
        SkippedRow blank = skippedFor(result, "V2");
        assertThat(blank.reason()).contains("Wednesday").contains("is blank");

        SkippedRow negative = skippedFor(result, "V3");
        assertThat(negative.reason()).contains("Monday").contains("is negative").contains("-1");

        SkippedRow unknown = skippedFor(result, "V4");
        assertThat(unknown.reason()).contains("Monday").contains("unrecognized value").contains("FOO");

        // >24 row imports with a non-silent clamp warning (D-10) -- never just logged
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w)
                .contains("Monday")
                .contains("clamped to 24"));

        // Per-sheet rollup (D-11): 2 imported, 3 skipped for the "Support" desk
        assertThat(result.sheetSummaries()).hasSize(1);
        DeskAssignmentUploadService.SheetSummary summary = result.sheetSummaries().get(0);
        assertThat(summary.deskName()).isEqualTo("Support");
        assertThat(summary.importedCount()).isEqualTo(2);
        assertThat(summary.skippedCount()).isEqualTo(3);
    }
}
