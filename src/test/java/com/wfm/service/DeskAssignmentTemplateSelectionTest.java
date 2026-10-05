package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.integration.BambooHRClient;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAssignmentSelectionRequest;
import com.wfm.dto.DeskAssignmentSelectionRequest.Employee;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.UnprocessableException;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The workbook built from a staged selection on Client Management — people picked across several
 * department searches, who need not be on the chosen desk or on any desk.
 *
 * <p>Distinct from {@link DeskAssignmentTemplateServiceTest}, which covers the roster-seeded
 * workbook: that one has a sheet per desk and seeds each from its current agents, this one has a
 * single sheet and seeds it from the request.
 */
class DeskAssignmentTemplateSelectionTest {

    private static final long TENANT_ID = 1L;

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private ShiftTemplateRepository shiftTemplateRepository;
    private BambooHRClient bambooHRClient;
    private DeskAssignmentTemplateService service;
    private Desk desk;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        bambooHRClient = mock(BambooHRClient.class);
        // No time off by default: these classes assert the blank/pre-population shapes.
        when(bambooHRClient.listTimeOff(any(), any(), any())).thenReturn(List.of());
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        when(shiftTemplateRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        service = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService, shiftTemplateRepository,
                bambooHRClient);
        TenantContext.setTenantId(TENANT_ID);

        desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Vinted");
        when(deskRepository.findByIdAndTenantId(desk.getId(), TENANT_ID)).thenReturn(Optional.of(desk));
        when(deskAgentService.listDeskAgentResponses(any(), any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Employee employee(String id, String name, String department, String status) {
        return new Employee(id, name, name.toLowerCase().replace(' ', '.') + "@example.com",
                department, "Customer Support Representative", status);
    }

    private List<List<String>> rowsOf(byte[] xlsx) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        cells.add(row.getCell(c) == null ? "" : row.getCell(c).toString());
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    @Test
    @DisplayName("one sheet, named after the chosen desk")
    void oneSheetNamedAfterTheDesk() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(employee("101", "Mary Watson", "Vinted - UA", "Active"))));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            // The parser reads a sheet name as a desk name, so this is not cosmetic.
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetName(0)).isEqualTo("Vinted");
        }
    }

    @Test
    @DisplayName("people from different departments land on that one sheet, in order")
    void multipleDepartmentsOnOneSheet() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("202", "John Blake", "Saferide - PL", "Active"),
                        employee("303", "Ana Silva", "Stubhub - EN", "Active"))));

        List<List<String>> rows = rowsOf(xlsx);
        assertThat(rows).hasSize(4);                       // header + three people
        assertThat(rows.get(1).get(0)).isEqualTo("101");
        assertThat(rows.get(2).get(0)).isEqualTo("202");
        assertThat(rows.get(3).get(0)).isEqualTo("303");
        // The department column keeps the person's real department; the SHEET is the desk. Those
        // are different things and conflating them is what this feature exists to avoid.
        assertThat(rows.get(1).get(5)).isEqualTo("Vinted - UA");
        assertThat(rows.get(2).get(5)).isEqualTo("Saferide - PL");
    }

    @Test
    @DisplayName("the display name is split into first and last, as the parser expects")
    void displayNameIsSplit() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(employee("101", "Mary Watson", "Vinted - UA", "Active"))));

        List<String> row = rowsOf(xlsx).get(1);
        assertThat(row.get(1)).isEqualTo("Mary");
        assertThat(row.get(2)).isEqualTo("Watson");
        assertThat(row.get(4)).isEqualTo("mary.watson@example.com");
        assertThat(row.get(6)).isEqualTo("Yes");
    }

    @Test
    @DisplayName("the same person reached through two searches is one row")
    void duplicatesAreCollapsed() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("101", "Mary Watson", "Vinted - PL", "Active"),
                        employee("202", "John Blake", "Saferide - PL", "Active"))));

        // Two rows with one id would have the parser apply the second over the first, silently
        // discarding whatever the operator typed on the first.
        List<List<String>> rows = rowsOf(xlsx);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).get(0)).isEqualTo("101");
        assertThat(rows.get(2).get(0)).isEqualTo("202");
    }

    @Test
    @DisplayName("day-hour columns are left blank for the operator, as in the roster template")
    void dayColumnsAreBlank() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(employee("101", "Mary Watson", "Vinted - UA", "Active"))));

        List<String> headers = rowsOf(xlsx).get(0);
        List<String> row = rowsOf(xlsx).get(1);
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            int col = headers.indexOf(EnrichedColumnLayout.dayHeader(day));
            assertThat(col).isGreaterThan(0);
            // Blank day cells make the upload SKIP the row, which is the documented behaviour --
            // this file is a starting point to fill in, not one to upload as downloaded.
            assertThat(col >= row.size() ? "" : row.get(col)).isEmpty();
        }
    }

    @Test
    @DisplayName("someone already on the desk keeps their stored usual shift")
    void usualShiftPreservedForExistingRosterMembers() throws Exception {
        DeskAgentResponse existing = new DeskAgentResponse(
                UUID.randomUUID(), desk.getId(), "101", "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Vinted - UA", "Customer Support Representative", true,
                null, null, List.of(), null, null, null,
                0, List.of(), Map.of(),
                Map.of(DayOfWeek.MONDAY, new DeskAgentResponse.UsualShiftEntry(
                        DeskAgentResponse.UsualShiftStatus.LIVE, "Vinted 08:00-17:00", null, null)));
        when(deskAgentService.listDeskAgentResponses(any(), any(), any(), anyInt()))
                .thenReturn(List.of(existing));

        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("202", "John Blake", "Saferide - PL", "Active"))));

        List<String> headers = rowsOf(xlsx).get(0);
        int mondayShift = headers.indexOf(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
        // Without this, re-uploading would wipe the usual shift of everyone already on the desk.
        assertThat(rowsOf(xlsx).get(1).get(mondayShift)).isEqualTo("Vinted 08:00-17:00");
        // Someone new to the desk has none, and blank is the parser's "no usual shift".
        List<String> newcomer = rowsOf(xlsx).get(2);
        assertThat(mondayShift >= newcomer.size() ? "" : newcomer.get(mondayShift)).isEmpty();
    }

    @Test
    @DisplayName("an inactive person is refused, and no workbook is built")
    void inactiveSelectionIsRefused() {
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("202", "John Blake", "Saferide - PL", "Inactive")))))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("1 of 2");
    }

    @Test
    @DisplayName("a job title off the allowlist is refused, and the message names the title")
    void nonAllowlistedTitleIsRefused() {
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(false);

        // Refused rather than written, because the upload would skip the row anyway; refused
        // rather than quietly dropped, because the operator ticked this person on purpose and a
        // near-miss title is a fixable configuration problem, not a data one.
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active")))))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> assertThat(((UnprocessableException) ex).getDetails())
                        .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                        .contains("Mary Watson")
                        .contains("Customer Support Representative")
                        .contains("allowlist"));
    }

    @Test
    @DisplayName("every offender is named at once, not one per attempt")
    void allOffendersReportedTogether() {
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("202", "John Blake", "Saferide - PL", "Inactive"),
                        employee("303", "Ana Silva", "Stubhub - EN", "Terminated")))))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(ex -> assertThat(((UnprocessableException) ex).getDetails()).hasSize(2));
    }

    @Test
    @DisplayName("an empty selection, a missing desk and an absurd size are all refused")
    void requestValidation() {
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");

        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(null,
                        List.of(employee("101", "Mary Watson", "Vinted - UA", "Active")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desk");

        UUID unknown = UUID.randomUUID();
        when(deskRepository.findByIdAndTenantId(unknown, TENANT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(unknown,
                        List.of(employee("101", "Mary Watson", "Vinted - UA", "Active")))))
                .isInstanceOf(EntityNotFoundException.class);

        List<Employee> tooMany = new ArrayList<>();
        for (int i = 0; i <= DeskAssignmentTemplateService.MAX_SELECTION_ROWS; i++) {
            tooMany.add(employee(String.valueOf(i), "Person " + i, "Dept", "Active"));
        }
        assertThatThrownBy(() -> service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), tooMany)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than");
    }

    @Test
    @DisplayName("a row with no BambooHR id is skipped rather than written blank")
    void blankIdsAreSkipped() throws Exception {
        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(
                        employee("101", "Mary Watson", "Vinted - UA", "Active"),
                        employee("  ", "No Id", "Vinted - UA", "Active"))));

        // The parser rejects a blank id anyway; writing the row would just move the error later.
        assertThat(rowsOf(xlsx)).hasSize(2);
    }
}
