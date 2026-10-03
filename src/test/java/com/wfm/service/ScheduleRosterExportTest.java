package com.wfm.service;

import com.wfm.dto.AgentDayOffResponse;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.AssignmentDetail;
import com.wfm.dto.ScheduleDetailResponse.ShiftDescriptor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "Roster" sheet: the source spreadsheet's own shape — agents down, days across, one shift
 * code per cell — rather than a mirror of one of our UI tabs. This is the layout Vinted's planners
 * sent week 39 in and the one they read a schedule back in, so the cell contents and the column
 * set are the contract, not an implementation detail.
 */
class ScheduleRosterExportTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 21);
    private static final LocalDate SUN = MON.plusDays(6);

    private final ScheduleExportService service = new ScheduleExportService();

    @Test
    void rosterPutsAgentsDownDaysAcrossAndTheShiftEnvelopeInEachCell() throws Exception {
        AgentScheduleEntry mon = shiftDay("Viktoriia", MON, LocalTime.of(8, 0), LocalTime.of(17, 0));
        AgentScheduleEntry tue = shiftDay("Viktoriia", MON.plusDays(1), LocalTime.of(15, 0), LocalTime.of(0, 0));

        Sheet sheet = roster(detail(List.of(mon, tue)), List.of());

        Row header = sheet.getRow(0);
        assertThat(text(header, 0)).isEqualTo("Agent");
        // Weekday above the date — the weekend columns are where this desk's shift mix differs.
        assertThat(text(header, 1)).isEqualTo("Mon 2026-09-21");
        assertThat(text(header, 7)).isEqualTo("Sun 2026-09-27");
        // The period drives the columns, so all seven days appear even though only two carry data.
        assertThat((int) header.getLastCellNum()).isEqualTo(8);

        Row row = sheet.getRow(1);
        assertThat(text(row, 0)).isEqualTo("Viktoriia");
        assertThat(text(row, 1)).isEqualTo("08:00-17:00");
        // An envelope ending at midnight reads 15:00-00:00, matching both the template's own name
        // and what 00:00 means in an end position.
        assertThat(text(row, 2)).isEqualTo("15:00-00:00");
        // No shift, no leave — blank, which is NOT the same as a day off.
        assertThat(text(row, 3)).isEmpty();
    }

    @Test
    void leaveFillsTheCellWhenNoShiftWasAssigned() throws Exception {
        AgentScheduleEntry mon = shiftDay("Olena", MON, LocalTime.of(8, 0), LocalTime.of(17, 0));

        List<AgentDayOffResponse> off = List.of(
                AgentDayOffResponse.withAgent(UUID.randomUUID(), MON.plusDays(1), "PTO", "APPROVED",
                        UUID.randomUUID(), "Olena"),
                AgentDayOffResponse.withAgent(UUID.randomUUID(), MON.plusDays(2), "MANDATORY", "APPROVED",
                        UUID.randomUUID(), "Olena"),
                AgentDayOffResponse.withAgent(UUID.randomUUID(), MON.plusDays(3), "PTO", "REQUESTED",
                        UUID.randomUUID(), "Olena"));

        Sheet sheet = roster(detail(List.of(mon)), off);

        Row row = sheet.getRow(1);
        assertThat(text(row, 0)).isEqualTo("Olena");
        assertThat(text(row, 1)).isEqualTo("08:00-17:00");
        assertThat(text(row, 2)).isEqualTo("PTO");
        assertThat(text(row, 3)).isEqualTo("MANDATORY");
        assertThat(text(row, 4)).isEqualTo("PTO (req)");
    }

    @Test
    void anAssignedShiftWinsOverALeaveRowForTheSameDay() throws Exception {
        AgentScheduleEntry mon = shiftDay("Dmytro", MON, LocalTime.of(9, 0), LocalTime.of(18, 0));
        List<AgentDayOffResponse> off = List.of(
                AgentDayOffResponse.withAgent(UUID.randomUUID(), MON, "PTO", "APPROVED",
                        UUID.randomUUID(), "Dmytro"));

        Sheet sheet = roster(detail(List.of(mon)), off);

        assertThat(text(sheet.getRow(1), 1)).isEqualTo("09:00-18:00");
    }

    @Test
    void anAgentWithOnlyLeaveStillGetsARow() throws Exception {
        List<AgentDayOffResponse> off = List.of(
                AgentDayOffResponse.withAgent(UUID.randomUUID(), MON, "PTO", "APPROVED",
                        UUID.randomUUID(), "Absent Alla"));

        Sheet sheet = roster(detail(List.of()), off);

        assertThat(text(sheet.getRow(1), 0)).isEqualTo("Absent Alla");
        assertThat(text(sheet.getRow(1), 1)).isEqualTo("PTO");
    }

    @Test
    void slotDeskFallsBackToTheSpanActuallyWorked() throws Exception {
        // No ShiftDescriptor at all — a SLOT desk still deserves a readable roster.
        AgentScheduleEntry entry = new AgentScheduleEntry(
                UUID.randomUUID(), "Slotty", MON, LocalTime.of(8, 0), LocalTime.of(12, 0),
                new BigDecimal("4.00"),
                List.of(new AssignmentDetail(UUID.randomUUID(), LocalTime.of(8, 0), LocalTime.of(9, 0), "Chat", "PRIMARY"),
                        new AssignmentDetail(UUID.randomUUID(), LocalTime.of(11, 0), LocalTime.of(12, 0), "Chat", "PRIMARY")),
                List.of(), null, null);

        Sheet sheet = roster(detail(List.of(entry)), List.of());

        assertThat(text(sheet.getRow(1), 1)).isEqualTo("08:00-12:00");
    }

    @Test
    void noScheduleAndNoLeaveProducesNoRosterSheetAtAll() throws Exception {
        byte[] xlsx = service.exportToExcel(detail(List.of()), List.of());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheet("Roster")).isNull();
        }
    }

    // --- OVNT-06/OVNT-07/D-12: overnight roster cell discloses both calendar dates ---

    // The period MON..SUN fills all 7 date columns (1..7) regardless of which carry data, so an
    // agent-day on business date SUN (the 7th day, index 6) lands in column 7, not column 1.
    private static final int SUN_COL = 7;

    @Test
    void overnightEnvelopeCellSpellsOutBothWeekdays() throws Exception {
        // SUN == 2026-09-27, a Sunday — the business date a 21:00-anchored desk's day starts on.
        AgentScheduleEntry entry = shiftDay("A. Reyes", SUN, LocalTime.of(22, 0), LocalTime.of(6, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.of(21, 0)), List.of());

        assertThat(text(sheet.getRow(1), SUN_COL)).isEqualTo("Sun 22:00-Mon 06:00");
    }

    @Test
    void sameDayEnvelopeCellOnAnAnchoredDeskStaysByteIdentical() throws Exception {
        AgentScheduleEntry entry = shiftDay("A. Reyes", SUN, LocalTime.of(22, 0), LocalTime.of(23, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.of(21, 0)), List.of());

        assertThat(text(sheet.getRow(1), SUN_COL)).isEqualTo("22:00-23:00");
    }

    @Test
    void sameDayEnvelopeCellOnAMidnightDeskStaysByteIdentical() throws Exception {
        AgentScheduleEntry entry = shiftDay("Olena", MON, LocalTime.of(8, 0), LocalTime.of(17, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.MIDNIGHT), List.of());

        assertThat(text(sheet.getRow(1), 1)).isEqualTo("08:00-17:00");
    }

    @Test
    void envelopeEndingExactlyAtTheAnchorRendersWithoutThrowing() throws Exception {
        // Anchored end minute 1440 — the argument-range edge of calendarDateAtDayStartOffset.
        AgentScheduleEntry entry = shiftDay("A. Reyes", SUN, LocalTime.of(22, 0), LocalTime.of(21, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.of(21, 0)), List.of());

        // Crosses into the business day's own end, which lands on the next calendar day (Monday).
        assertThat(text(sheet.getRow(1), SUN_COL)).isEqualTo("Sun 22:00-Mon 21:00");
    }

    @Test
    void legendIsVerticalFiveRowsWithTheLockedCrossingRow() throws Exception {
        AgentScheduleEntry entry = shiftDay("A. Reyes", SUN, LocalTime.of(22, 0), LocalTime.of(6, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.of(21, 0)), List.of());

        // One agent row (row 1), blank spacer, "Legend" title, then 5 pair rows.
        int legendTitleRow = 3;
        assertThat(text(sheet.getRow(legendTitleRow), 0)).isEqualTo("Legend");
        assertThat(text(sheet.getRow(legendTitleRow + 1), 0)).isEqualTo("08:00-17:00");
        assertThat(text(sheet.getRow(legendTitleRow + 1), 1)).isEqualTo("assigned shift envelope");
        assertThat(text(sheet.getRow(legendTitleRow + 2), 0)).isEqualTo("Sun 22:00-Mon 06:00");
        assertThat(text(sheet.getRow(legendTitleRow + 2), 1))
                .isEqualTo("shift crossing into the next calendar day");
        assertThat(text(sheet.getRow(legendTitleRow + 3), 0)).isEqualTo("PTO");
        assertThat(text(sheet.getRow(legendTitleRow + 3), 1)).isEqualTo("approved leave");
        assertThat(text(sheet.getRow(legendTitleRow + 4), 0)).isEqualTo("MANDATORY");
        assertThat(text(sheet.getRow(legendTitleRow + 4), 1)).isEqualTo("rostered day off");
        assertThat(text(sheet.getRow(legendTitleRow + 5), 0)).isEqualTo("(blank)");
        assertThat(text(sheet.getRow(legendTitleRow + 5), 1))
                .isEqualTo("not scheduled, no leave recorded");
        // No overlap: the last pair row is still within the sheet, no agent row beyond row 1.
        assertThat(sheet.getRow(legendTitleRow + 6)).isNull();
    }

    @Test
    void dateColumnsAreWidenedToFitTheOvernightCell() throws Exception {
        AgentScheduleEntry entry = shiftDay("A. Reyes", SUN, LocalTime.of(22, 0), LocalTime.of(6, 0));

        Sheet sheet = roster(detail(List.of(entry), LocalTime.of(21, 0)), List.of());

        assertThat(sheet.getColumnWidth(1)).isEqualTo(22 * 256);
    }

    @Test
    void noEnvelopeFallbackAndBlankCellsAreUnchanged() throws Exception {
        // No ShiftDescriptor at all — fallback to the worked span; neither shift nor assignments
        // on a second agent-day falls through to blank. Mirrors slotDeskFallsBackToTheSpanActuallyWorked
        // but exercised on an anchored (21:00) desk to prove the fallback path is unaffected there too.
        AgentScheduleEntry worked = new AgentScheduleEntry(
                UUID.randomUUID(), "Slotty", SUN, LocalTime.of(22, 0), LocalTime.of(23, 0),
                new BigDecimal("1.00"),
                List.of(new AssignmentDetail(UUID.randomUUID(), LocalTime.of(22, 0), LocalTime.of(23, 0), "Chat", "PRIMARY")),
                List.of(), null, null);

        Sheet sheet = roster(detail(List.of(worked), LocalTime.of(21, 0)), List.of());

        assertThat(text(sheet.getRow(1), SUN_COL)).isEqualTo("22:00-23:00");
    }

    // ------------------------------------------------------------------

    private AgentScheduleEntry shiftDay(String agent, LocalDate date, LocalTime start, LocalTime end) {
        ShiftDescriptor shift = new ShiftDescriptor(UUID.randomUUID(), "Vinted " + start + "-" + end,
                start, end, 180, 60);
        return new AgentScheduleEntry(UUID.randomUUID(), agent, date, start, end,
                new BigDecimal("8.00"),
                List.of(new AssignmentDetail(UUID.randomUUID(), start, start.plusHours(1), "Chat", "PRIMARY")),
                List.of(), shift, null);
    }

    private ScheduleDetailResponse detail(List<AgentScheduleEntry> entries) {
        ScheduleDetailResponse d = new ScheduleDetailResponse();
        d.setDayStart(LocalTime.MIDNIGHT);
        d.setDeskName("Vinted");
        d.setStatus("COMPLETED");
        d.setPeriodStartDate(MON);
        d.setPeriodEndDate(SUN);
        d.setStartTime(LocalTime.of(8, 0));
        d.setEndTime(LocalTime.of(0, 0));
        d.setIncrementMinutes(60);
        d.setStaffingSummary(List.of());
        d.setAgentSchedule(entries);
        return d;
    }

    /** Same fixture, with the desk's anchor overridden — the only way to exercise an overnight desk. */
    private ScheduleDetailResponse detail(List<AgentScheduleEntry> entries, LocalTime dayStart) {
        ScheduleDetailResponse d = detail(entries);
        d.setDayStart(dayStart);
        return d;
    }

    private Sheet roster(ScheduleDetailResponse detail, List<AgentDayOffResponse> daysOff) throws Exception {
        byte[] xlsx = service.exportToExcel(detail, daysOff);
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheet("Roster");
    }

    private String text(Row row, int col) {
        var cell = row.getCell(col);
        return cell == null ? "" : cell.getStringCellValue();
    }
}
