package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAgentResponse.UsualShiftEntry;
import com.wfm.dto.DeskAgentResponse.UsualShiftReason;
import com.wfm.dto.DeskAgentResponse.UsualShiftStatus;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-09/D-10/P-14/P-15: the generated per-desk template pre-fills each agent's stored usual shift
 * and attaches a sheet-scoped Excel dropdown of the desk's live template names, degrading
 * gracefully rather than corrupting the workbook (16-RESEARCH.md Pitfall 5).
 */
class DeskAssignmentTemplateServiceUsualShiftTest {

    private static final long TENANT_ID = 1L;

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private ShiftTemplateRepository shiftTemplateRepository;
    private DeskAssignmentTemplateService service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        service = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService, shiftTemplateRepository);
        TenantContext.setTenantId(TENANT_ID);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private Desk desk(String name) {
        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName(name);
        return desk;
    }

    private ShiftTemplate liveTemplate(UUID deskId, String name) {
        return template(deskId, name, LocalDate.now().minusDays(30), null);
    }

    private ShiftTemplate template(UUID deskId, String name, LocalDate from, LocalDate to) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setTenantId(TENANT_ID);
        t.setDeskId(deskId);
        t.setName(name);
        t.setStartTime(LocalTime.of(8, 0));
        t.setEndTime(LocalTime.of(17, 0));
        t.setValidWeekdaysMask("1111111");
        t.setEffectiveFrom(from);
        t.setEffectiveTo(to);
        return t;
    }

    private UsualShiftEntry liveEntry(String name) {
        return new UsualShiftEntry(UsualShiftStatus.LIVE, name, null, null);
    }

    private DeskAgentResponse rosterAgent(UUID deskId, String bamboohrId,
                                           Map<DayOfWeek, UsualShiftEntry> usualShift) {
        return new DeskAgentResponse(
                UUID.randomUUID(), deskId, bamboohrId, "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Billing", "Agent", true,
                null, null, List.of(),
                null, null, null,
                0, List.of(), Map.of(), usualShift);
    }

    private XSSFWorkbook generateAndReadBack() throws Exception {
        byte[] xlsx = service.generateTemplate();
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private String cellString(Row row, int index) {
        if (row == null) return null;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        return cell.getStringCellValue();
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void preFillsStoredUsualShift_blankWhereNotStored() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId())).thenReturn(List.of());

        Map<DayOfWeek, UsualShiftEntry> usualShift = new EnumMap<>(DayOfWeek.class);
        usualShift.put(DayOfWeek.MONDAY, liveEntry("Early"));
        usualShift.put(DayOfWeek.WEDNESDAY, liveEntry("Late"));

        DeskAgentResponse agent = rosterAgent(billing.getId(), "4517", usualShift);
        when(deskAgentService.listDeskAgentResponses(eq(billing.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(agent));

        XSSFWorkbook workbook = generateAndReadBack();
        Row dataRow = workbook.getSheet("Billing").getRow(1);

        assertThat(cellString(dataRow, 14)).isEqualTo("Early");   // Usual Shift Monday
        assertThat(cellString(dataRow, 16)).isEqualTo("Late");    // Usual Shift Wednesday
        for (int i : new int[] {15, 17, 18, 19, 20}) {
            assertThat(cellString(dataRow, i)).as("column %d must stay blank", i).isNull();
        }
    }

    @Test
    void headerPositions_usualShiftBetweenDayHoursAndSpecialty() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId())).thenReturn(List.of());
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());

        XSSFWorkbook workbook = generateAndReadBack();
        Row headerRow = workbook.getSheet("Billing").getRow(0);

        assertThat(cellString(headerRow, 13)).isEqualTo(EnrichedColumnLayout.dayHeader(DayOfWeek.SUNDAY));
        assertThat(cellString(headerRow, 14)).isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
        assertThat(cellString(headerRow, 20)).isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.SUNDAY));
        assertThat(cellString(headerRow, 21)).isEqualTo(EnrichedColumnLayout.specialtyHeader(1));
    }

    @Test
    void dropdown_happyPath_sevenValidationsWithLiveTemplateNames() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), "Early"), liveTemplate(billing.getId(), "Late")));

        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");

        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertThat(validations).hasSize(7);
        for (DataValidation v : validations) {
            assertThat(v.getValidationConstraint().getExplicitListValues())
                    .containsExactly("Early", "Late");
            assertThat(v.getRegions().getCellRangeAddresses()).hasSize(1);
        }

        List<Integer> coveredColumns = validations.stream()
                .map(v -> v.getRegions().getCellRangeAddresses()[0].getFirstColumn())
                .sorted()
                .collect(Collectors.toList());
        assertThat(coveredColumns).containsExactly(14, 15, 16, 17, 18, 19, 20);
    }

    @Test
    void dropdown_boundaryAt255_stillAttachesValidations() throws Exception {
        Desk billing = desk("Billing");
        String name = "A".repeat(255);
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), name)));

        XSSFWorkbook workbook = generateAndReadBack();
        assertThat(workbook.getSheet("Billing").getDataValidations()).hasSize(7);
    }

    @Test
    void dropdown_boundaryAt256_skipsValidationsButKeepsHeadersAndPreFill() throws Exception {
        Desk billing = desk("Billing");
        String name = "A".repeat(256);
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));

        Map<DayOfWeek, UsualShiftEntry> usualShift = new EnumMap<>(DayOfWeek.class);
        usualShift.put(DayOfWeek.MONDAY, liveEntry(name));
        DeskAgentResponse agent = rosterAgent(billing.getId(), "4517", usualShift);
        when(deskAgentService.listDeskAgentResponses(eq(billing.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(agent));
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), name)));

        // Must not throw -- the workbook still generates and re-opens with POI.
        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");
        assertThat(sheet.getDataValidations()).isEmpty();

        Row headerRow = sheet.getRow(0);
        assertThat(cellString(headerRow, 14)).isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
        Row dataRow = sheet.getRow(1);
        assertThat(cellString(dataRow, 14)).isEqualTo(name);
    }

    @Test
    void dropdown_zeroLiveTemplates_blankCellsNoValidations() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId())).thenReturn(List.of());

        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");

        assertThat(sheet.getDataValidations()).isEmpty();
        Row headerRow = sheet.getRow(0);
        assertThat(cellString(headerRow, 14)).isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
    }

    @Test
    void dropdown_templateNameContainsComma_skipsValidations() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), "Early, Late")));

        XSSFWorkbook workbook = generateAndReadBack();
        assertThat(workbook.getSheet("Billing").getDataValidations()).isEmpty();
    }

    @Test
    void dropdown_adjacency_deskBValidationsExcludeDeskANames() throws Exception {
        Desk billing = desk("Billing");
        Desk support = desk("Support");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing, support));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), "Early")));
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, support.getId()))
                .thenReturn(List.of(liveTemplate(support.getId(), "Late")));

        XSSFWorkbook workbook = generateAndReadBack();

        List<? extends DataValidation> billingValidations = workbook.getSheet("Billing").getDataValidations();
        List<? extends DataValidation> supportValidations = workbook.getSheet("Support").getDataValidations();

        assertThat(billingValidations).isNotEmpty().allSatisfy(v ->
                assertThat(v.getValidationConstraint().getExplicitListValues()).containsExactly("Early"));
        assertThat(supportValidations).isNotEmpty().allSatisfy(v ->
                assertThat(v.getValidationConstraint().getExplicitListValues()).containsExactly("Late"));
    }

    @Test
    void retiredEraName_stillPreFills_rawStoredValue() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId())).thenReturn(List.of());

        Map<DayOfWeek, UsualShiftEntry> usualShift = new EnumMap<>(DayOfWeek.class);
        usualShift.put(DayOfWeek.FRIDAY,
                new UsualShiftEntry(UsualShiftStatus.STORED_INACTIVE, "Retired Shift",
                        UsualShiftReason.RETIRED, null));
        DeskAgentResponse agent = rosterAgent(billing.getId(), "4517", usualShift);
        when(deskAgentService.listDeskAgentResponses(eq(billing.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(agent));

        XSSFWorkbook workbook = generateAndReadBack();
        Row dataRow = workbook.getSheet("Billing").getRow(1);
        assertThat(cellString(dataRow, 18)).isEqualTo("Retired Shift"); // Usual Shift Friday = 14 + 4
    }

    @Test
    void dropdown_templateNameContainsDoubleQuote_skipsValidations() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());
        when(shiftTemplateRepository.findByTenantIdAndDeskId(TENANT_ID, billing.getId()))
                .thenReturn(List.of(liveTemplate(billing.getId(), "\"Early\"")));

        XSSFWorkbook workbook = generateAndReadBack();
        assertThat(workbook.getSheet("Billing").getDataValidations()).isEmpty();
    }
}
