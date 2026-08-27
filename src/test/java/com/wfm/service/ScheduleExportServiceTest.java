package com.wfm.service;

import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.AssignmentDetail;
import com.wfm.dto.ScheduleDetailResponse.BreakDetail;
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
 * First test coverage for {@link ScheduleExportService} (Phase 15 plan 10, Task 3). Confirms the
 * Agent Schedule sheet's Break rows inherit Task 2's band-derived correction with no change to
 * the writing code, and that the sheet gains a visible Shift/Envelope column pair only on a shift
 * desk — a slot desk's sheet stays byte-identical to today, including column count and header
 * text.
 */
class ScheduleExportServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    private final ScheduleExportService service = new ScheduleExportService();

    @Test
    void exportToExcel_shiftDesk_breakRowsCarryTheBandWindowAndAssignmentRowsCarryTheShift() throws Exception {
        ShiftDescriptor shift = new ShiftDescriptor(
                UUID.randomUUID(), "Late", LocalTime.of(12, 0), LocalTime.of(21, 0), 240, 60);

        AssignmentDetail assignment = new AssignmentDetail(
                UUID.randomUUID(), LocalTime.of(12, 0), LocalTime.of(13, 0), "Chat", "PRIMARY");
        BreakDetail brk = new BreakDetail(LocalTime.of(16, 0), LocalTime.of(17, 0), 60);

        AgentScheduleEntry entry = new AgentScheduleEntry(
                UUID.randomUUID(), "Evelina", DAY, LocalTime.of(12, 0), LocalTime.of(21, 0),
                new BigDecimal("8.00"), List.of(assignment), List.of(brk), shift, null);

        ScheduleDetailResponse detail = detailWith(List.of(entry));

        Sheet sheet = exportAndReadBack(detail);

        Row header = sheet.getRow(0);
        assertThat((int) header.getLastCellNum()).isEqualTo(9);
        assertThat(cellText(header, 7)).isEqualTo("Shift Template");
        assertThat(cellText(header, 8)).isEqualTo("Shift Envelope");

        Row assignmentRow = sheet.getRow(1);
        assertThat(cellText(assignmentRow, 6)).isEqualTo("");
        assertThat(cellText(assignmentRow, 7)).isEqualTo("Late");
        assertThat(cellText(assignmentRow, 8)).isEqualTo("12:00 - 21:00");

        Row breakRow = sheet.getRow(2);
        assertThat(cellText(breakRow, 2)).isEqualTo("16:00");
        assertThat(cellText(breakRow, 3)).isEqualTo("17:00");
        assertThat(cellText(breakRow, 6)).isEqualTo("Break");
        // Shift/Envelope cells are left blank (not created) on break rows.
        assertThat(breakRow.getCell(7)).isNull();
        assertThat(breakRow.getCell(8)).isNull();
    }

    @Test
    void exportToExcel_slotDesk_sheetIsByteIdenticalToToday() throws Exception {
        AssignmentDetail assignment = new AssignmentDetail(
                UUID.randomUUID(), LocalTime.of(8, 0), LocalTime.of(9, 0), "S1", "PRIMARY");
        AssignmentDetail assignment2 = new AssignmentDetail(
                UUID.randomUUID(), LocalTime.of(10, 0), LocalTime.of(11, 0), "S1", "PRIMARY");
        BreakDetail brk = new BreakDetail(LocalTime.of(9, 0), LocalTime.of(10, 0), 60);

        AgentScheduleEntry entry = new AgentScheduleEntry(
                UUID.randomUUID(), "Ana", DAY, LocalTime.of(8, 0), LocalTime.of(11, 0),
                new BigDecimal("2.00"), List.of(assignment, assignment2), List.of(brk), null, null);

        ScheduleDetailResponse detail = detailWith(List.of(entry));

        Sheet sheet = exportAndReadBack(detail);

        Row header = sheet.getRow(0);
        assertThat((int) header.getLastCellNum()).isEqualTo(7);
        for (int i = 0; i < 7; i++) {
            assertThat(cellText(header, i)).isEqualTo(
                    new String[] {"Agent", "Date", "Start Time", "End Time", "Specialization",
                            "Match Type", "Break"}[i]);
        }

        Row assignmentRow1 = sheet.getRow(1);
        assertThat((int) assignmentRow1.getLastCellNum()).isEqualTo(7);
        assertThat(cellText(assignmentRow1, 2)).isEqualTo("08:00");
        assertThat(cellText(assignmentRow1, 3)).isEqualTo("09:00");

        Row breakRow = sheet.getRow(3);
        assertThat(cellText(breakRow, 2)).isEqualTo("09:00");
        assertThat(cellText(breakRow, 3)).isEqualTo("10:00");
        assertThat(cellText(breakRow, 6)).isEqualTo("Break");
        assertThat((int) breakRow.getLastCellNum()).isEqualTo(7);
    }

    // ---------- helpers ----------

    private ScheduleDetailResponse detailWith(List<AgentScheduleEntry> entries) {
        ScheduleDetailResponse detail = new ScheduleDetailResponse();
        detail.setDeskName("Test Desk");
        detail.setStatus("COMPLETED");
        detail.setPeriodStartDate(DAY);
        detail.setPeriodEndDate(DAY);
        detail.setStartTime(LocalTime.of(8, 0));
        detail.setEndTime(LocalTime.of(21, 0));
        detail.setIncrementMinutes(60);
        detail.setStaffingSummary(List.of());
        detail.setAgentSchedule(entries);
        detail.setPreferenceReport(null);
        return detail;
    }

    private Sheet exportAndReadBack(ScheduleDetailResponse detail) throws Exception {
        byte[] xlsx = service.exportToExcel(detail);
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        return workbook.getSheet("Agent Schedule");
    }

    private String cellText(Row row, int col) {
        var cell = row.getCell(col);
        return cell == null ? "" : cell.getStringCellValue();
    }
}
