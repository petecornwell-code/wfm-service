package com.wfm.service;

import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAgentResponse.DayHoursEntry;
import com.wfm.model.DayOffType;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * First test coverage for {@link DeskAgentExportService}. Plain JUnit 5 + AssertJ — the class
 * has no constructor dependencies, so no Spring context is needed. Fixtures are built directly
 * against {@link DeskAgentResponse}, the DTO plan 13-01 extended with the always-7-key
 * {@code dayHours} map.
 */
class DeskAgentExportServiceTest {

    private static final int FIRST_DAY_COLUMN = 13;

    private final DeskAgentExportService service = new DeskAgentExportService();

    private DeskAgentResponse agent(String firstName, Map<DayOfWeek, DayHoursEntry> dayHours,
                                     BigDecimal contractedHoursPerDay,
                                     BigDecimal effectiveContractedHoursPerDay) {
        return new DeskAgentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "B100", "Jane Doe",
                firstName, "Doe", "jane.doe@example.com",
                "Billing", "Agent", true,
                OffsetDateTime.now(),
                null, List.of(),
                contractedHoursPerDay, effectiveContractedHoursPerDay,
                null,
                0, List.of(), dayHours, Map.of());
    }

    private Map<DayOfWeek, DayHoursEntry> uniformDayHours(DayHoursEntry entry) {
        Map<DayOfWeek, DayHoursEntry> map = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            map.put(day, entry);
        }
        return map;
    }

    private int dayColumnIndex(DayOfWeek day) {
        List<DayOfWeek> order = List.of(EnrichedColumnLayout.DAY_ORDER);
        return FIRST_DAY_COLUMN + order.indexOf(day);
    }

    private XSSFWorkbook exportAndReadBack(List<DeskAgentResponse> agents) throws Exception {
        byte[] xlsx = service.exportDeskAgentsToExcel(agents);
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private List<String> expectedHeaders() {
        List<String> headers = new ArrayList<>(List.of(
                "ID", "Desk ID", EnrichedColumnLayout.COL_BAMBOOHR_ID, "Name", EnrichedColumnLayout.COL_EMAIL,
                EnrichedColumnLayout.COL_DEPARTMENT, EnrichedColumnLayout.COL_JOB_TITLE,
                EnrichedColumnLayout.COL_ACTIVE, "Last Refreshed At",
                "Primary Specialization", "Secondary Specializations",
                "Contracted Hours Per Day", "Effective Contracted Hours Per Day"
        ));
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            headers.add(EnrichedColumnLayout.dayHeader(day));
        }
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            headers.add(EnrichedColumnLayout.usualShiftHeader(day));
        }
        headers.add(EnrichedColumnLayout.COL_FIRST_NAME);
        headers.add(EnrichedColumnLayout.COL_LAST_NAME);
        return headers;
    }

    @Test
    void headerRow_matchesTheFullExpectedOrder() throws Exception {
        XSSFWorkbook workbook = exportAndReadBack(List.of());
        Sheet sheet = workbook.getSheet("Desk Agents");
        Row headerRow = sheet.getRow(0);

        List<String> expected = expectedHeaders();
        assertThat(expected).hasSize(29);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(headerRow.getCell(i).getStringCellValue())
                    .as("header cell %d", i)
                    .isEqualTo(expected.get(i));
        }
    }

    @Test
    void mandatoryWeekday_writesTheKeywordNotZero() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        dayHours.put(DayOfWeek.SATURDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), DayOffType.MANDATORY, BigDecimal.ZERO.setScale(2)));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Cell cell = workbook.getSheet("Desk Agents").getRow(1).getCell(dayColumnIndex(DayOfWeek.SATURDAY));

        assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
        assertThat(cell.getStringCellValue()).isEqualTo("MANDATORY");
    }

    @Test
    void ptoWeekday_writesTheKeywordNotZero() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        dayHours.put(DayOfWeek.SUNDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), DayOffType.PTO, BigDecimal.ZERO.setScale(2)));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Cell cell = workbook.getSheet("Desk Agents").getRow(1).getCell(dayColumnIndex(DayOfWeek.SUNDAY));

        assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
        assertThat(cell.getStringCellValue()).isEqualTo("PTO");
    }

    @Test
    void explicitZeroWeekday_writesNumericZero() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        dayHours.put(DayOfWeek.WEDNESDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), null, BigDecimal.ZERO.setScale(2)));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Cell cell = workbook.getSheet("Desk Agents").getRow(1).getCell(dayColumnIndex(DayOfWeek.WEDNESDAY));

        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(cell.getNumericCellValue()).isEqualTo(0.0);
    }

    @Test
    void workedWeekday_writesTheFractionalValue() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        dayHours.put(DayOfWeek.THURSDAY, new DayHoursEntry(
                true, new BigDecimal("7.50"), null, new BigDecimal("7.50")));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Cell cell = workbook.getSheet("Desk Agents").getRow(1).getCell(dayColumnIndex(DayOfWeek.THURSDAY));

        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(cell.getNumericCellValue()).isEqualTo(7.5);
    }

    @Test
    void notSetWeekday_writesTheResolvedEffectiveValue_notBlank() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        dayHours.put(DayOfWeek.FRIDAY, new DayHoursEntry(
                false, null, null, new BigDecimal("9.00")));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Cell cell = workbook.getSheet("Desk Agents").getRow(1).getCell(dayColumnIndex(DayOfWeek.FRIDAY));

        assertThat(cell).isNotNull();
        assertThat(cell.getCellType()).isNotEqualTo(CellType.BLANK);
        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(cell.getNumericCellValue()).isEqualTo(9.0);
    }

    @Test
    void everyExportedDayCell_isAcceptedByTheUploadParsersContract() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = new EnumMap<>(DayOfWeek.class);
        dayHours.put(DayOfWeek.MONDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), DayOffType.MANDATORY, BigDecimal.ZERO.setScale(2)));
        dayHours.put(DayOfWeek.TUESDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), DayOffType.PTO, BigDecimal.ZERO.setScale(2)));
        dayHours.put(DayOfWeek.WEDNESDAY, new DayHoursEntry(
                true, BigDecimal.ZERO.setScale(2), null, BigDecimal.ZERO.setScale(2)));
        dayHours.put(DayOfWeek.THURSDAY, new DayHoursEntry(
                true, new BigDecimal("7.50"), null, new BigDecimal("7.50")));
        dayHours.put(DayOfWeek.FRIDAY, new DayHoursEntry(
                false, null, null, new BigDecimal("9.00")));
        dayHours.put(DayOfWeek.SATURDAY, new DayHoursEntry(
                true, new BigDecimal("24.00"), null, new BigDecimal("24.00")));
        dayHours.put(DayOfWeek.SUNDAY, new DayHoursEntry(
                true, new BigDecimal("0.50"), null, new BigDecimal("0.50")));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Row row = workbook.getSheet("Desk Agents").getRow(1);

        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            Cell cell = row.getCell(dayColumnIndex(day));
            assertThat(cell).as("cell for %s", day).isNotNull();
            if (cell.getCellType() == CellType.STRING) {
                assertThat(cell.getStringCellValue()).as("string cell for %s", day)
                        .isIn("MANDATORY", "PTO");
            } else {
                assertThat(cell.getCellType()).as("cell type for %s", day).isEqualTo(CellType.NUMERIC);
                double value = cell.getNumericCellValue();
                assertThat(value).as("numeric range for %s", day).isBetween(0.0, 24.0);
            }
        }
    }

    @Test
    void effectiveContractedHoursColumn_reflectsThePerDayModel() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        DeskAgentResponse a = agent("Jane", dayHours, new BigDecimal("5.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Row row = workbook.getSheet("Desk Agents").getRow(1);

        assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(5.0);
        assertThat(row.getCell(12).getNumericCellValue()).isEqualTo(8.0);
    }

    @Test
    void identityColumnsStillSanitized() throws Exception {
        Map<DayOfWeek, DayHoursEntry> dayHours = uniformDayHours(
                new DayHoursEntry(true, new BigDecimal("8.00"), null, new BigDecimal("8.00")));
        DeskAgentResponse a = agent("=SUM(A1)", dayHours, new BigDecimal("8.00"), new BigDecimal("8.00"));

        XSSFWorkbook workbook = exportAndReadBack(List.of(a));
        Row row = workbook.getSheet("Desk Agents").getRow(1);

        // First Name shifted from index 13 to 27 -- 13 (day hours) + 7 (usual shift) insertion.
        Cell firstNameCell = row.getCell(27);
        assertThat(firstNameCell.getCellType()).isEqualTo(CellType.STRING);
        assertThat(firstNameCell.getStringCellValue()).isEqualTo("'=SUM(A1)");
    }
}
