package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UPL-03/04/05: day-cell parsing to {@code agent_day_hours} -- numeric hours
 * (including the fractional-hours regression, D-04), the {@code MANDATORY} /
 * {@code PTO} keywords writing {@code day_off_type} (D-12), case-insensitive
 * keyword matching.
 */
class DeskAssignmentUploadDayCellTest {

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

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService);

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
        return headers.toArray(new String[0]);
    }

    private static String[] agentRow(String bamboohrId, String firstName, String lastName, String[] dayCells) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, firstName, lastName, "Agent", "", "", ""));
        row.addAll(List.of(dayCells));
        return row.toArray(new String[0]);
    }

    /** Single-sheet, single-desk workbook builder for day-cell parsing tests. */
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
                if (value == null || value.isBlank()) continue;
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
        BambooEmployeeResponse cached = new BambooEmployeeResponse(id, displayName,
                displayName.toLowerCase() + "@example.com", "Support", "Agent", "Active");
        when(clientManagementService.findCachedEmployee(eq(id), isNull(), isNull())).thenReturn(cached);
    }

    private AgentDayHours capturedDay(List<AgentDayHours> captured, DayOfWeek day) {
        return captured.stream()
                .filter(h -> h.getDayOfWeek() == day)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No AgentDayHours captured for " + day));
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void numericDayCells_includingFractionalHours_persistCorrectly() throws Exception {
        stubCachedEmployee("B1", "Alice");

        // Monday = 7.5 (fractional-hours regression -- must persist as 7.50, NOT 7)
        String[] dayCells = {"7.5", "8", "0", "8", "8", "0", "0"};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", dayCells) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);
        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        ArgumentCaptor<AgentDayHours> captor = ArgumentCaptor.forClass(AgentDayHours.class);
        verify(agentDayHoursRepository, times(7)).save(captor.capture());
        List<AgentDayHours> captured = captor.getAllValues();

        AgentDayHours monday = capturedDay(captured, DayOfWeek.MONDAY);
        assertThat(monday.getHours()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(monday.getHours().toPlainString()).isEqualTo("7.50");
        assertThat(monday.getDayOffType()).isNull();

        AgentDayHours wednesday = capturedDay(captured, DayOfWeek.WEDNESDAY);
        assertThat(wednesday.getHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wednesday.getDayOffType()).isNull();
    }

    @Test
    void mandatoryAndPtoKeywords_storeZeroHoursWithLabel_caseInsensitive() throws Exception {
        stubCachedEmployee("B2", "Bob");

        // Wednesday = MANDATORY, Thursday = pto (lowercase -- case-insensitive per D-03)
        String[] dayCells = {"8", "8", "MANDATORY", "pto", "8", "0", "0"};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B2", "Bob", "B", dayCells) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);
        assertThat(result.assignedCount()).isEqualTo(1);

        ArgumentCaptor<AgentDayHours> captor = ArgumentCaptor.forClass(AgentDayHours.class);
        verify(agentDayHoursRepository, times(7)).save(captor.capture());
        List<AgentDayHours> captured = captor.getAllValues();

        AgentDayHours wednesday = capturedDay(captured, DayOfWeek.WEDNESDAY);
        assertThat(wednesday.getHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wednesday.getDayOffType()).isEqualTo(DayOffType.MANDATORY);

        AgentDayHours thursday = capturedDay(captured, DayOfWeek.THURSDAY);
        assertThat(thursday.getHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(thursday.getDayOffType()).isEqualTo(DayOffType.PTO);

        AgentDayHours monday = capturedDay(captured, DayOfWeek.MONDAY);
        assertThat(monday.getDayOffType()).isNull();
    }
}
