package com.wfm.service;

import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAgentResponse.UsualShiftEntry;
import com.wfm.dto.DeskAgentResponse.UsualShiftStatus;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * D-06/D-07/D-08/D-03/P-12/P-13/D-11: the seven Usual Shift upload columns are read
 * cell-by-cell (blank = none, unresolvable = skip-and-warn, weekday-mask violation =
 * skip-and-warn) and written directly to {@code agent_usual_shift}, and {@code clearDesk}
 * wipes usual shifts -- proven together with the template's D-09 pre-fill as a
 * download-then-re-upload no-op (the load-bearing pair, T-16-15).
 */
class DeskAssignmentUploadUsualShiftTest {

    private static final long TENANT_ID = 1L;
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};
    private static final String[] NO_USUAL_SHIFTS = {"", "", "", "", "", "", ""};

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
        when(agentEligibilityService.isNonSchedulable(anyLong(), any())).thenReturn(false);

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
        when(agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(any(), any())).thenReturn(Optional.empty());
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        usualShiftService = mock(UsualShiftService.class);

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

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static String[] fullShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.usualShiftHeader(d));
        return headers.toArray(new String[0]);
    }

    private static String[] agentRow(String bamboohrId, String firstName, String lastName,
                                      String[] dayCells, String[] usualShiftCells) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, firstName, lastName, "Agent", "", "", ""));
        row.addAll(List.of(dayCells));
        row.addAll(List.of(usualShiftCells));
        return row.toArray(new String[0]);
    }

    private void writeRow(Row row, List<String> values) {
        for (int c = 0; c < values.size(); c++) {
            String value = values.get(c);
            if (value == null || value.isEmpty()) continue; // leave the cell blank
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                row.createCell(c).setCellValue(Double.parseDouble(value));
            } else {
                row.createCell(c).setCellValue(value);
            }
        }
    }

    /** Full-shape (identity + day + Usual Shift) single-sheet workbook builder. */
    private MockMultipartFile buildWorkbook(String deskName, String[][] rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(deskName);
        String[] headers = fullShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        for (int r = 0; r < rows.length; r++) {
            writeRow(sheet.createRow(r + 1), List.of(rows[r]));
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

    private ShiftTemplate liveTemplate(String name, Set<DayOfWeek> validDays) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setTenantId(TENANT_ID);
        t.setDeskId(desk.getId());
        t.setName(name);
        t.setStartTime(LocalTime.of(8, 0));
        t.setEndTime(LocalTime.of(17, 0));
        t.setValidWeekdays(validDays);
        t.setEffectiveFrom(LocalDate.now().minusDays(30));
        t.setEffectiveTo(null);
        return t;
    }

    private ShiftTemplate liveTemplateAllDays(String name) {
        return liveTemplate(name, EnumSet.allOf(DayOfWeek.class));
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void happyPath_resolvedName_writesOneRowForThatWeekday() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(liveTemplateAllDays("Early")));

        String[] usualShift = {"Early", "", "", "", "", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        ArgumentCaptor<AgentUsualShift> captor = ArgumentCaptor.forClass(AgentUsualShift.class);
        verify(agentUsualShiftRepository, times(1)).save(captor.capture());
        AgentUsualShift saved = captor.getValue();
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getShiftTemplate().getName()).isEqualTo("Early");
    }

    @Test
    void blankCell_writesNoRow_noWarning_restOfRowImports() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());

        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, NO_USUAL_SHIFTS) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
        verify(agentUsualShiftRepository, never()).save(any());
    }

    @Test
    void unresolvedName_skipsCellOnly_warnsAndRestOfRowImports() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());

        String[] usualShift = {"Ghost Shift", "", "", "", "", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w)
                .contains("B1").contains("Monday").contains("Ghost Shift"));
        verify(agentUsualShiftRepository, never()).save(any());

        // The rest of the row -- all 7 day-hours cells -- still imported (D-08: a bad optional
        // field costs only that cell, never the row's valid identity/specialty/hours data).
        verify(agentDayHoursRepository, times(7)).save(any(AgentDayHours.class));
    }

    @Test
    void weekdayMaskExcludesDay_skipsCellOnly_warnsAndRestImports() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(liveTemplate("Weekday", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))));

        String[] usualShift = {"", "", "", "", "", "Weekday", ""}; // Saturday cell
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w)
                .contains("Saturday").contains("Weekday").contains("B1"));
        verify(agentUsualShiftRepository, never()).save(any());
    }

    @Test
    void lowercaseSpacePaddedName_resolvesViaNormalize() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(liveTemplateAllDays("Early")));

        String[] usualShift = {"  early  ", "", "", "", "", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        ArgumentCaptor<AgentUsualShift> captor = ArgumentCaptor.forClass(AgentUsualShift.class);
        verify(agentUsualShiftRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getShiftTemplate().getName()).isEqualTo("Early");
    }

    @Test
    void ambiguousNormalizedName_skipsCellOnly_warnsAmbiguity_neitherTemplateWritten() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(liveTemplateAllDays("Early"), liveTemplateAllDays("EARLY")));

        String[] usualShift = {"early", "", "", "", "", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w)
                .contains("Monday").containsIgnoringCase("ambiguous"));
        verify(agentUsualShiftRepository, never()).save(any());
    }

    @Test
    void numericTypedCell_matchingAllDigitsTemplateName_resolves() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(liveTemplateAllDays("100")));

        String[] usualShift = {"100", "", "", "", "", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        ArgumentCaptor<AgentUsualShift> captor = ArgumentCaptor.forClass(AgentUsualShift.class);
        verify(agentUsualShiftRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getShiftTemplate().getName()).isEqualTo("100");
    }

    @Test
    void warningsOnMultipleWeekdays_appearInDayOrderSequence() throws Exception {
        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());

        String[] usualShift = {"", "Ghost Tuesday", "", "", "Ghost Friday", "", ""};
        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, usualShift) });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        List<String> usualShiftWarnings = result.warnings().stream()
                .filter(w -> w.contains("Ghost"))
                .toList();
        assertThat(usualShiftWarnings).hasSize(2);
        assertThat(usualShiftWarnings.get(0)).contains("Tuesday").contains("Ghost Tuesday");
        assertThat(usualShiftWarnings.get(1)).contains("Friday").contains("Ghost Friday");
    }

    @Test
    void downloadThenReupload_isANoOp_forStoredUsualShifts() throws Exception {
        // The agent is already on this desk before the round trip (mirrors the real scenario:
        // downloading a template for a desk always lists its current roster), so clearDesk has
        // a real row to wipe and the row loop's re-supply is a genuine "put back what was
        // cleared", not merely "there was nothing to clear in the first place".
        Agent existingAgent = new Agent();
        existingAgent.setId(UUID.randomUUID());
        existingAgent.setTenantId(TENANT_ID);
        existingAgent.setBamboohrId("B1");
        existingAgent.setDeskId(desk.getId());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of(existingAgent));
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.of(existingAgent));

        stubCachedEmployee("B1", "Alice");
        ShiftTemplate early = liveTemplateAllDays("Early");
        ShiftTemplate late = liveTemplateAllDays("Late");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId()))
                .thenReturn(List.of(early, late));

        DeskAgentService deskAgentService = mock(DeskAgentService.class);
        Map<DayOfWeek, UsualShiftEntry> stored = new EnumMap<>(DayOfWeek.class);
        stored.put(DayOfWeek.MONDAY, new UsualShiftEntry(UsualShiftStatus.LIVE, "Early", null, null));
        stored.put(DayOfWeek.WEDNESDAY, new UsualShiftEntry(UsualShiftStatus.LIVE, "Late", null, null));
        DeskAgentResponse rosterAgent = new DeskAgentResponse(
                UUID.randomUUID(), desk.getId(), "B1", "Alice A",
                "Alice", "A", "alice@example.com", "Support", "Agent", true,
                null, null, List.of(), null, null, null,
                0, List.of(), Map.of(), stored);
        when(deskAgentService.listDeskAgentResponses(eq(desk.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(rosterAgent));

        DeskAssignmentTemplateService templateService = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService, shiftTemplateRepository);
        byte[] templateBytes = templateService.generateTemplate();

        // The template deliberately leaves day-hours cells blank (Phase 10 scope, unchanged by
        // this phase) -- an operator fills those in before re-uploading. Simulate exactly that
        // (and nothing else) so this test isolates the Usual Shift round trip specifically.
        byte[] filledInBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(templateBytes))) {
            Row dataRow = wb.getSheet("Support").getRow(1);
            for (int i = 0; i < FULL_WEEK.length; i++) {
                dataRow.createCell(7 + i).setCellValue(Double.parseDouble(FULL_WEEK[i]));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            filledInBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", filledInBytes);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedSheets()).isEmpty();

        ArgumentCaptor<AgentUsualShift> captor = ArgumentCaptor.forClass(AgentUsualShift.class);
        verify(agentUsualShiftRepository, times(2)).save(captor.capture());
        Map<DayOfWeek, String> savedNames = captor.getAllValues().stream()
                .collect(Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u.getShiftTemplate().getName()));
        assertThat(savedNames).containsExactlyInAnyOrderEntriesOf(Map.of(
                DayOfWeek.MONDAY, "Early", DayOfWeek.WEDNESDAY, "Late"));

        // D-11: clearDesk (via the shared clearUsualShifts helper) ran for the agent's PRIOR
        // desk membership before the row loop re-supplied exactly what was cleared -- the
        // download-then-re-upload no-op.
        verify(usualShiftService).clearUsualShifts(existingAgent.getId());
    }

    @Test
    void allUsualShiftCellsBlank_clearDeskWipesEveryCurrentDeskAgentsUsualShifts() throws Exception {
        Agent existingAgent = new Agent();
        existingAgent.setId(UUID.randomUUID());
        existingAgent.setTenantId(TENANT_ID);
        existingAgent.setBamboohrId("B1");
        existingAgent.setDeskId(desk.getId());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of(existingAgent));
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B1")).thenReturn(Optional.of(existingAgent));

        stubCachedEmployee("B1", "Alice");
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of());

        MockMultipartFile file = buildWorkbook("Support",
                new String[][] { agentRow("B1", "Alice", "A", FULL_WEEK, NO_USUAL_SHIFTS) });

        service.uploadDeskAssignments(file);

        // D-11: clearDesk wipes usual shifts for the desk's PRE-upload agent(s) before the row
        // loop runs; every Usual Shift cell in this workbook is blank (D-07), so nothing is
        // re-supplied -- proving clearDesk really wipes, not that the pre-fill masks it.
        verify(usualShiftService).clearUsualShifts(existingAgent.getId());
        verify(agentUsualShiftRepository, never()).save(any());
    }

    @Test
    void sheetMissingUsualShiftHeaders_reportedInSkippedSheets_deskNotCleared() throws Exception {
        Agent existingAgent = new Agent();
        existingAgent.setId(UUID.randomUUID());
        existingAgent.setTenantId(TENANT_ID);
        existingAgent.setBamboohrId("B1");
        existingAgent.setDeskId(desk.getId());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, desk.getId())).thenReturn(List.of(existingAgent));

        stubCachedEmployee("B1", "Alice");

        // Old-shape (pre-Phase-16) workbook: identity + day headers only, no Usual Shift columns.
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) headerRow.createCell(c).setCellValue(headers.get(c));

        List<String> rowValues = new ArrayList<>(List.of("B1", "Alice", "A", "Agent", "", "", ""));
        rowValues.addAll(List.of(FULL_WEEK));
        writeRow(sheet.createRow(1), rowValues);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        MockMultipartFile file = new MockMultipartFile("file", "old.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedSheets()).hasSize(1);
        assertThat(result.skippedSheets().get(0).reason())
                .contains("missing required column")
                .contains(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
        assertThat(result.assignedCount()).isZero();

        // CR-01: desk untouched -- clearDesk (and the shared clearUsualShifts helper it calls)
        // never ran for a sheet whose required headers are missing.
        verify(usualShiftService, never()).clearUsualShifts(any());
        verify(agentRepository, never()).save(any());
    }
}
