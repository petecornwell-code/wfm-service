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
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UPL-01: multi-sheet iteration, sheet name = desk, unmatched sheet skipped
 * with a notice (D-02); sheet-name -> desk matching is case/whitespace
 * insensitive. Also covers the D-17 colliding-sheet-name last-wins behaviour
 * and the D-16 structural guard (no AgentDayOffRepository collaborator, so
 * upload/clearDesk cannot delete BambooHR field-4517 MANDATORY blocks).
 */
class DeskAssignmentUploadMultiSheetTest {

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
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /** New per-desk-sheet header shape: identity headers + Monday..Sunday (no Desk column). */
    private static String[] newShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        return headers.toArray(new String[0]);
    }

    private static String[] agentRow(String bamboohrId, String firstName, String lastName) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, firstName, lastName, "Agent", "", "", ""));
        row.addAll(List.of(FULL_WEEK));
        return row.toArray(new String[0]);
    }

    /** Builds a workbook with one sheet per entry in {@code sheets} (sheetName -> data rows). */
    private MockMultipartFile buildMultiSheetWorkbook(Map<String, String[][]> sheets) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        String[] headers = newShapeHeaders();

        for (Map.Entry<String, String[][]> entry : sheets.entrySet()) {
            Sheet sheet = wb.createSheet(entry.getKey());
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                headerRow.createCell(c).setCellValue(headers[c]);
            }
            String[][] rows = entry.getValue();
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
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private Desk desk(String name) {
        Desk d = new Desk();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT_ID);
        d.setName(name);
        return d;
    }

    private void stubCachedEmployee(String id, String displayName) {
        BambooEmployeeResponse cached = new BambooEmployeeResponse(id, displayName,
                displayName.toLowerCase() + "@example.com", "Dept", "Agent", "Active");
        when(clientManagementService.findCachedEmployee(eq(id), isNull(), isNull())).thenReturn(cached);
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void multiSheetImport_unmatchedSheetSkipped_othersStillImport() throws Exception {
        Desk billing = desk("Billing");
        Desk sales = desk("Sales");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing, sales));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        stubCachedEmployee("B1", "Alice");
        stubCachedEmployee("B2", "Bob");

        Map<String, String[][]> sheets = new LinkedHashMap<>();
        sheets.put("Billing", new String[][] { agentRow("B1", "Alice", "A") });
        sheets.put("Sales", new String[][] { agentRow("B2", "Bob", "B") });
        // "Marketing" is not a configured desk -- must be skipped, not abort the other sheets
        sheets.put("Marketing", new String[][] { agentRow("B3", "Carol", "C") });

        MockMultipartFile file = buildMultiSheetWorkbook(sheets);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(2);
        assertThat(result.skippedSheets()).hasSize(1);
        assertThat(result.skippedSheets().get(0).sheetName()).isEqualTo("Marketing");
        assertThat(result.skippedSheets().get(0).reason()).containsIgnoringCase("no matching desk");

        Set<String> importedDesks = result.sheetSummaries().stream()
                .map(DeskAssignmentUploadService.SheetSummary::deskName)
                .collect(Collectors.toSet());
        assertThat(importedDesks).containsExactlyInAnyOrder("Billing", "Sales");
    }

    @Test
    void sheetNameMatching_isCaseAndWhitespaceInsensitive() throws Exception {
        Desk billing = desk("Billing");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
        stubCachedEmployee("B1", "Alice");

        Map<String, String[][]> sheets = new LinkedHashMap<>();
        // Trailing whitespace, matches desk "Billing" after trim (D-02 interface contract)
        sheets.put("Billing ", new String[][] { agentRow("B1", "Alice", "A") });

        MockMultipartFile file = buildMultiSheetWorkbook(sheets);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedSheets()).isEmpty();
        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.sheetSummaries()).hasSize(1);
        assertThat(result.sheetSummaries().get(0).deskName()).isEqualTo("Billing");
    }

    @Test
    void collidingSheetNames_normalizeToSameDesk_lastSheetWins() throws Exception {
        Desk billing = desk("Billing");
        UUID deskId = billing.getId();

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());

        // Stateful mock registry standing in for the DB: agents keyed by bamboohrId.
        Map<String, Agent> agentsById = new LinkedHashMap<>();
        when(agentRepository.findByTenantIdAndBamboohrId(eq(TENANT_ID), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(agentsById.get((String) inv.getArgument(1))));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> {
            Agent a = inv.getArgument(0);
            agentsById.put(a.getBamboohrId(), a);
            return a;
        });
        when(agentRepository.findByTenantIdAndDeskId(eq(TENANT_ID), eq(deskId))).thenAnswer(inv ->
                agentsById.values().stream()
                        .filter(a -> deskId.equals(a.getDeskId()))
                        .collect(Collectors.toList()));

        stubCachedEmployee("B1", "Alice");
        stubCachedEmployee("B2", "Bob");

        Map<String, String[][]> sheets = new LinkedHashMap<>();
        sheets.put("Billing", new String[][] { agentRow("B1", "Alice", "A") });
        // Normalizes to the same desk as "Billing" via trim+lowercase
        sheets.put("billing ", new String[][] { agentRow("B2", "Bob", "B") });

        MockMultipartFile file = buildMultiSheetWorkbook(sheets);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        assertThat(result.skippedSheets()).isEmpty();
        // Both sheets matched and were processed (2 sheet summaries, both for "Billing")
        assertThat(result.sheetSummaries()).hasSize(2);
        assertThat(result.sheetSummaries())
                .allSatisfy(s -> assertThat(s.deskName()).isEqualTo("Billing"));

        // clearDesk ran once per sheet that resolved to this desk (D-17 last-wins mechanism)
        verify(agentRepository, times(2)).findByTenantIdAndDeskId(eq(TENANT_ID), eq(deskId));

        // Final roster reflects only the LAST sheet's agent
        List<Agent> finalRoster = agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId);
        assertThat(finalRoster).extracting(Agent::getBamboohrId).containsExactly("B2");
        assertThat(agentsById.get("B1").getDeskId()).isNull();
        assertThat(agentsById.get("B2").getDeskId()).isEqualTo(deskId);
    }

    @Test
    void service_declaresNoAgentDayOffRepositoryField_d16StructuralGuard() {
        // D-16: upload/clearDesk must never be able to delete BambooHR field-4517
        // MANDATORY AgentDayOff blocks. Since Mockito can't verify(...never()) a
        // collaborator that doesn't exist, this proves absence structurally instead
        // (mirrors plan 10-01 Task 3's structural-absence approach).
        boolean hasAgentDayOffRepositoryField = Arrays.stream(DeskAssignmentUploadService.class.getDeclaredFields())
                .anyMatch(f -> AgentDayOffRepository.class.isAssignableFrom(f.getType()));
        assertThat(hasAgentDayOffRepositoryField)
                .as("DeskAssignmentUploadService must not depend on AgentDayOffRepository (D-16)")
                .isFalse();
    }
}
