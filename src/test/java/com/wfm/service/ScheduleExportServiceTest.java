package com.wfm.service;

import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.AssignmentDetail;
import com.wfm.dto.ScheduleDetailResponse.BreakDetail;
import com.wfm.dto.ScheduleDetailResponse.DriftReport;
import com.wfm.dto.ScheduleDetailResponse.DriftReportEntry;
import com.wfm.dto.ScheduleDetailResponse.DriftStatus;
import com.wfm.dto.ScheduleDetailResponse.DriftSummary;
import com.wfm.dto.ScheduleDetailResponse.ShiftDescriptor;
import com.wfm.dto.ScheduleDetailResponse.ShiftPopularityEntry;
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

    // ==================================================================
    //  Plan 17-03, Task 2 -- Drift Report sheet (XCUT-01, D-14)
    // ==================================================================

    @Test
    void exportToExcel_driftReport_mainHeadersMatchTheInterfaceLiteralsInOrder() throws Exception {
        DriftReport report = new DriftReport(List.of(), new DriftSummary(0, 0, 0, 0), List.of());
        Sheet sheet = exportAndReadSheet(detailWithDrift(report), "Drift Report");

        Row header = sheet.getRow(0);
        String[] expected = {"Agent", "Date", "Status", "Usual Start", "Actual Start", "Delta (min)"};
        for (int i = 0; i < expected.length; i++) {
            assertThat(cellText(header, i)).isEqualTo(expected[i]);
        }
    }

    @Test
    void exportToExcel_driftReport_popularityHeadingAndHeadersMatch() throws Exception {
        DriftReport report = new DriftReport(List.of(), new DriftSummary(0, 0, 0, 0),
                List.of(new ShiftPopularityEntry("Early", 3)));
        Sheet sheet = exportAndReadSheet(detailWithDrift(report), "Drift Report");

        // Row 0: main header. Row 1: blank spacer. Row 2: popularity heading. Row 3: popularity
        // header. Row 4: first (and only) popularity entry.
        Row headingRow = sheet.getRow(2);
        assertThat(cellText(headingRow, 0)).isEqualTo("Most-Subscribed Usual Shifts");

        Row popularityHeader = sheet.getRow(3);
        assertThat(cellText(popularityHeader, 0)).isEqualTo("Shift Template");
        assertThat(cellText(popularityHeader, 1)).isEqualTo("Agents (Usual Shift)");

        Row popularityRow = sheet.getRow(4);
        assertThat(cellText(popularityRow, 0)).isEqualTo("Early");
        assertThat(cellText(popularityRow, 1)).isEqualTo("3");
    }

    @Test
    void exportToExcel_driftReport_popularityRowsAppearInTheSuppliedOrder() throws Exception {
        DriftReport report = new DriftReport(List.of(), new DriftSummary(0, 0, 0, 0),
                List.of(new ShiftPopularityEntry("Early", 4), new ShiftPopularityEntry("Late", 2)));
        Sheet sheet = exportAndReadSheet(detailWithDrift(report), "Drift Report");

        assertThat(cellText(sheet.getRow(4), 0)).isEqualTo("Early");
        assertThat(cellText(sheet.getRow(5), 0)).isEqualTo("Late");
    }

    @Test
    void exportToExcel_nullDriftReport_writesOnlyTheMainHeaderRow() throws Exception {
        // This exercises ScheduleExportService.writeDriftReport's own report == null branch in
        // isolation (this file constructs ScheduleDetailResponse directly, with no
        // ScheduleService/Spring context involved) -- it does NOT prove that a real SLOT-scheduled
        // desk's ScheduleService.getScheduleDetail call actually produces that null. That
        // end-to-end guarantee (CR-01) is covered separately by
        // ScheduleServiceShiftSnapshotTest#getScheduleDetail_inMemorySlotModeSchedule_driftReportIsNullAndNeverBuilt.
        ScheduleDetailResponse detail = detailWith(List.of());
        // detailWith never sets driftReport -- it stays null here by construction, not because a
        // SLOT-scheduled desk was exercised.
        Sheet sheet = exportAndReadSheet(detail, "Drift Report");

        assertThat(sheet.getRow(0)).isNotNull();
        assertThat(sheet.getRow(1)).isNull();
        assertThat(sheet.getLastRowNum()).isEqualTo(0);
    }

    @Test
    void exportToExcel_driftReport_mixedStatusRows_writeTheThreeOperatorFacingLabels() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        DriftReportEntry drifted = new DriftReportEntry(a, "Ana", DAY, DriftStatus.DRIFTED,
                LocalTime.of(8, 0), LocalTime.of(9, 30), 90);
        DriftReportEntry honoured = new DriftReportEntry(b, "Ben", DAY, DriftStatus.HONOURED,
                LocalTime.of(8, 0), LocalTime.of(8, 10), null);
        DriftReportEntry noUsualShift = new DriftReportEntry(c, "Cara", DAY, DriftStatus.NO_USUAL_SHIFT,
                null, LocalTime.of(10, 0), null);
        DriftReport report = new DriftReport(List.of(drifted, honoured, noUsualShift),
                new DriftSummary(3, 1, 1, 1), List.of());

        Sheet sheet = exportAndReadSheet(detailWithDrift(report), "Drift Report");

        assertThat(cellText(sheet.getRow(1), 2)).isEqualTo("Drifted");
        assertThat(cellText(sheet.getRow(2), 2)).isEqualTo("Honoured");
        assertThat(cellText(sheet.getRow(3), 2)).isEqualTo("No usual shift");

        // Neither raw enum constant name appears anywhere in the sheet.
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c2 = 0; c2 < row.getLastCellNum(); c2++) {
                String text = cellText(row, c2);
                assertThat(text).isNotEqualTo("DRIFTED").isNotEqualTo("HONOURED")
                        .isNotEqualTo("NO_USUAL_SHIFT");
            }
        }
    }

    @Test
    void exportToExcel_driftReport_noUsualShiftRow_leavesUsualStartAndDeltaBlankButActualStartPopulated()
            throws Exception {
        UUID agentId = UUID.randomUUID();
        DriftReportEntry entry = new DriftReportEntry(agentId, "Cara", DAY, DriftStatus.NO_USUAL_SHIFT,
                null, LocalTime.of(10, 0), null);
        DriftReport report = new DriftReport(List.of(entry), new DriftSummary(1, 1, 0, 0), List.of());

        Sheet sheet = exportAndReadSheet(detailWithDrift(report), "Drift Report");
        Row row = sheet.getRow(1);

        assertThat(cellText(row, 3)).isEqualTo(""); // Usual Start
        assertThat(cellText(row, 4)).isEqualTo("10:00"); // Actual Start
        assertThat(cellText(row, 5)).isEqualTo(""); // Delta (min)
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

    private ScheduleDetailResponse detailWithDrift(DriftReport report) {
        ScheduleDetailResponse detail = detailWith(List.of());
        detail.setDriftReport(report);
        return detail;
    }

    private Sheet exportAndReadBack(ScheduleDetailResponse detail) throws Exception {
        return exportAndReadSheet(detail, "Agent Schedule");
    }

    private Sheet exportAndReadSheet(ScheduleDetailResponse detail, String sheetName) throws Exception {
        byte[] xlsx = service.exportToExcel(detail);
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        return workbook.getSheet(sheetName);
    }

    private String cellText(Row row, int col) {
        var cell = row.getCell(col);
        return cell == null ? "" : cell.getStringCellValue();
    }
}
