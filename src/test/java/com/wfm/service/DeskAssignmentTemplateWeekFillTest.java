package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAssignmentSelectionRequest;
import com.wfm.integration.BambooHRClient;
import com.wfm.integration.BambooTimeOff;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day-hour pre-population: approved BambooHR time off as {@code PTO}/{@code MANDATORY}, every other
 * day the chosen working-day hours.
 *
 * <p>Why this matters more than it looks: every day cell is REQUIRED by
 * {@code DeskAssignmentUploadService.parseDayCell}, and a single blank one skips the whole row —
 * AFTER {@code clearDesk} has run. A template with blank day cells therefore empties the desk and
 * re-adds nobody. These tests pin the population that makes a download-edit-reupload round trip
 * survive, for both the roster download and the dynamic selection.
 */
class DeskAssignmentTemplateWeekFillTest {

    private static final long TENANT_ID = 1L;
    /** A Wednesday, deliberately: the resolver must normalise to that ISO week's Monday. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private ShiftTemplateRepository shiftTemplateRepository;
    private BambooHRClient bambooHRClient;
    private DeskAssignmentTemplateService service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        bambooHRClient = mock(BambooHRClient.class);
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        when(shiftTemplateRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(bambooHRClient.listTimeOff(any(), any(), any())).thenReturn(List.of());
        service = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService, shiftTemplateRepository,
                bambooHRClient);
        TenantContext.setTenantId(TENANT_ID);
    }

    private Desk deskWithDefaultHours(String name, String defaultHours) {
        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName(name);
        desk.setDefaultContractedHoursPerDay(defaultHours == null ? null : new BigDecimal(defaultHours));
        when(deskRepository.findByIdAndTenantId(desk.getId(), TENANT_ID)).thenReturn(Optional.of(desk));
        return desk;
    }

    private DeskAgentResponse rosterAgent(UUID deskId, String bamboohrId) {
        return new DeskAgentResponse(
                UUID.randomUUID(), deskId, bamboohrId, "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Billing", "Customer Support Representative", true,
                null, null, List.of(),
                null, null, null,
                0, List.of(), Map.of(), Map.of());
    }

    private void rosterIs(Desk desk, String... bamboohrIds) {
        when(deskAgentService.listDeskAgentResponses(eq(desk.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(java.util.Arrays.stream(bamboohrIds)
                        .map(id -> rosterAgent(desk.getId(), id)).toList());
    }

    /** The seven day cells of the first data row, read back as what the parser would see. */
    private Map<DayOfWeek, String> dayCells(byte[] xlsx) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row row = wb.getSheetAt(0).getRow(1);
            int base = EnrichedColumnLayout.identityHeaders().size();
            Map<DayOfWeek, String> out = new EnumMap<>(DayOfWeek.class);
            for (int i = 0; i < EnrichedColumnLayout.DAY_ORDER.length; i++) {
                Cell cell = row.getCell(base + i);
                String v;
                if (cell == null || cell.getCellType() == CellType.BLANK) {
                    v = null;
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    v = new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                            .stripTrailingZeros().toPlainString();
                } else {
                    v = cell.getStringCellValue();
                }
                out.put(EnrichedColumnLayout.DAY_ORDER[i], v);
            }
            return out;
        }
    }

    // --- the roster download ---------------------------------------------------------------

    @Test
    @DisplayName("approved PTO becomes a PTO cell; every other day gets the desk's default hours")
    void rosterDownload_approvedPtoAndDefaultHours() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", LocalDate.of(2026, 10, 6), "pto", "approved")));

        Map<DayOfWeek, String> cells = dayCells(service.generateTemplateForDesk(desk.getId(), WEDNESDAY, null));

        assertThat(cells.get(DayOfWeek.TUESDAY)).isEqualTo("PTO");
        assertThat(cells.get(DayOfWeek.MONDAY)).isEqualTo("8");
        assertThat(cells.get(DayOfWeek.SUNDAY)).isEqualTo("8");
        assertThat(cells.values()).doesNotContainNull();
    }

    @Test
    @DisplayName("a Wednesday weekStart is normalised to that ISO week's Monday before the BambooHR call")
    void rosterDownload_weekStartNormalisedToMonday() {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");

        service.generateTemplateForDesk(desk.getId(), WEDNESDAY, null);

        verify(bambooHRClient).listTimeOff("1", MONDAY, SUNDAY);
    }

    @Test
    @DisplayName("a holiday becomes MANDATORY, not PTO -- mirroring BambooRefreshService's mapping")
    void rosterDownload_holidayBecomesMandatory() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", MONDAY, "holiday", "approved")));

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId(), MONDAY, null))
                .get(DayOfWeek.MONDAY)).isEqualTo("MANDATORY");
    }

    @Test
    @DisplayName("a REQUESTED day is left as a working day -- a pending request is not granted leave")
    void rosterDownload_requestedTimeOffIsNotWrittenAsPto() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", MONDAY, "pto", "requested")));

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId(), MONDAY, null))
                .get(DayOfWeek.MONDAY)).isEqualTo("8");
    }

    @Test
    @DisplayName("overlapping entries for one person-day resolve MANDATORY over PTO")
    void rosterDownload_overlappingEntriesResolveMandatoryOverPto() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", MONDAY, "pto", "approved"),
                new BambooTimeOff("6023", MONDAY, "holiday", "approved")));

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId(), MONDAY, null))
                .get(DayOfWeek.MONDAY)).isEqualTo("MANDATORY");
    }

    @Test
    @DisplayName("another person's time off does not bleed onto this row")
    void rosterDownload_timeOffIsKeyedPerPerson() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("9999", MONDAY, "pto", "approved")));

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId(), MONDAY, null))
                .get(DayOfWeek.MONDAY)).isEqualTo("8");
    }

    @Test
    @DisplayName("an explicit 0 is honoured -- the all-zeros starting point, not overridden by the desk default")
    void rosterDownload_zeroWorkingDayHoursIsHonoured() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId(), MONDAY, BigDecimal.ZERO))
                .get(DayOfWeek.MONDAY)).isEqualTo("0");
    }

    @Test
    @DisplayName("no weekStart leaves every day cell blank, and never calls BambooHR")
    void rosterDownload_noWeekStartLeavesCellsBlank() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");

        assertThat(dayCells(service.generateTemplateForDesk(desk.getId())).values())
                .containsOnlyNulls();
        verify(bambooHRClient, org.mockito.Mockito.never()).listTimeOff(any(), any(), any());
    }

    @Test
    @DisplayName("hours outside 0-24 are refused rather than written and clamped downstream")
    void rosterDownload_outOfRangeHoursRefused() {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk, "6023");

        assertThatThrownBy(() -> service.generateTemplateForDesk(desk.getId(), MONDAY, new BigDecimal("25")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 24");
        assertThatThrownBy(() -> service.generateTemplateForDesk(desk.getId(), MONDAY, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a desk with no default contracted hours and no explicit value is refused, not written as blank")
    void rosterDownload_noDefaultHoursAndNoneGivenIsRefused() {
        Desk desk = deskWithDefaultHours("Billing", null);
        rosterIs(desk, "6023");

        assertThatThrownBy(() -> service.generateTemplateForDesk(desk.getId(), MONDAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no default contracted hours");
    }

    // --- the dynamic selection -------------------------------------------------------------

    private DeskAssignmentSelectionRequest.Employee employee(String id) {
        return new DeskAssignmentSelectionRequest.Employee(
                id, "Ela Bandola", "ela@example.com", "Avanos Medical - PH",
                "Customer Service Representative", "Active");
    }

    @Test
    @DisplayName("the selection download populates day cells the same way, for people not yet on the desk")
    void selection_populatesDayCellsForPeopleNotOnTheDesk() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk);
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", LocalDate.of(2026, 10, 8), "pto", "approved")));

        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(employee("6023")), WEDNESDAY, null));

        Map<DayOfWeek, String> cells = dayCells(xlsx);
        assertThat(cells.get(DayOfWeek.THURSDAY)).isEqualTo("PTO");
        assertThat(cells.get(DayOfWeek.MONDAY)).isEqualTo("8");
        assertThat(cells.values()).doesNotContainNull();
    }

    @Test
    @DisplayName("time off is resolved for the selected ids, so a selection spanning departments still works")
    void selection_resolvesByIdNotByDepartment() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk);
        when(bambooHRClient.listTimeOff("1", MONDAY, SUNDAY)).thenReturn(List.of(
                new BambooTimeOff("6023", MONDAY, "pto", "approved"),
                new BambooTimeOff("7777", MONDAY, "pto", "approved")));

        DeskAssignmentSelectionRequest.Employee other = new DeskAssignmentSelectionRequest.Employee(
                "7777", "Other Person", "other@example.com", "Convey Health - US",
                "Customer Support Representative", "Active");

        byte[] xlsx = service.generateTemplateForSelection(new DeskAssignmentSelectionRequest(
                desk.getId(), List.of(employee("6023"), other), MONDAY, null));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            int base = EnrichedColumnLayout.identityHeaders().size();
            assertThat(wb.getSheetAt(0).getRow(1).getCell(base).getStringCellValue()).isEqualTo("PTO");
            assertThat(wb.getSheetAt(0).getRow(2).getCell(base).getStringCellValue()).isEqualTo("PTO");
        }
    }

    @Test
    @DisplayName("a selection with no week keeps the pre-existing blank day cells")
    void selection_noWeekStillBlank() throws Exception {
        Desk desk = deskWithDefaultHours("Billing", "8.00");
        rosterIs(desk);

        byte[] xlsx = service.generateTemplateForSelection(
                new DeskAssignmentSelectionRequest(desk.getId(), List.of(employee("6023"))));

        assertThat(dayCells(xlsx).values()).containsOnlyNulls();
    }
}
