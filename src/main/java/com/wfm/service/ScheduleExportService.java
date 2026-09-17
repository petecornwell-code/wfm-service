package com.wfm.service;

import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a multi-tab .xlsx spreadsheet from schedule output views.
 * Uses Apache POI XSSFWorkbook.
 */
@Service
public class ScheduleExportService {

    public byte[] exportToExcel(ScheduleDetailResponse detail) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            writeOverview(workbook, headerStyle, detail);
            writeStaffingSummary(workbook, headerStyle, detail.getStaffingSummary());
            writeAgentSchedule(workbook, headerStyle, detail.getAgentSchedule());
            writePreferenceReport(workbook, headerStyle, detail.getPreferenceReport());
            writeDriftReport(workbook, headerStyle, detail.getDriftReport());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    // --- Tab 1: Overview ---

    private void writeOverview(XSSFWorkbook workbook, CellStyle headerStyle,
                               ScheduleDetailResponse detail) {
        Sheet sheet = workbook.createSheet("Overview");

        String[][] rows = {
                {"Desk", detail.getDeskName() != null ? detail.getDeskName() : ""},
                {"Status", detail.getStatus() != null ? detail.getStatus() : ""},
                {"Period", detail.getPeriodStartDate() + " to " + detail.getPeriodEndDate()},
                {"Hours", detail.getStartTime() + " - " + detail.getEndTime()},
                {"Increment (min)", String.valueOf(detail.getIncrementMinutes())},
        };

        int rowNum = 0;
        for (String[] pair : rows) {
            Row row = sheet.createRow(rowNum++);
            Cell label = row.createCell(0);
            label.setCellValue(pair[0]);
            label.setCellStyle(headerStyle);
            row.createCell(1).setCellValue(pair[1]);
        }

        autoSizeColumns(sheet, 2);
    }

    // --- Tab 2: Staffing Summary ---

    private void writeStaffingSummary(XSSFWorkbook workbook, CellStyle headerStyle,
                                      List<StaffingSummaryEntry> entries) {
        Sheet sheet = workbook.createSheet("Staffing Summary");

        // Header row
        Row header = sheet.createRow(0);
        String[] cols = {"Date", "Specialization", "Predicted Hours", "Actual Hours",
                         "Delta Hours", "Coverage %"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        if (entries == null) return;

        int rowNum = 1;
        for (StaffingSummaryEntry e : entries) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.date().toString());
            row.createCell(1).setCellValue(e.specializationName());
            row.createCell(2).setCellValue(e.predictedHours().doubleValue());
            row.createCell(3).setCellValue(e.actualHours().doubleValue());
            row.createCell(4).setCellValue(e.deltaHours().doubleValue());
            if (e.coveragePct() != null) {
                row.createCell(5).setCellValue(e.coveragePct().doubleValue());
            }
        }

        autoSizeColumns(sheet, cols.length);
    }

    // --- Tab 2: Agent Schedule ---

    private void writeAgentSchedule(XSSFWorkbook workbook, CellStyle headerStyle,
                                     List<AgentScheduleEntry> entries) {
        Sheet sheet = workbook.createSheet("Agent Schedule");

        // Shift/Envelope columns only exist when at least one entry carries an assigned shift
        // (Phase 15 plan 10, Task 3) — a slot desk's sheet stays byte-identical to today,
        // including column count and header text, since no entry on a slot desk ever carries a
        // shift descriptor.
        boolean hasShiftMode = entries != null && entries.stream().anyMatch(e -> e.shift() != null);

        List<String> colsList = new ArrayList<>(List.of(
                "Agent", "Date", "Start Time", "End Time", "Specialization", "Match Type", "Break"));
        if (hasShiftMode) {
            colsList.add("Shift Template");
            colsList.add("Shift Envelope");
        }
        String[] cols = colsList.toArray(new String[0]);

        Row header = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        if (entries == null) return;

        int rowNum = 1;
        for (AgentScheduleEntry agent : entries) {
            String shiftTemplateName = agent.shift() != null ? agent.shift().templateName() : "";
            String shiftEnvelope = agent.shift() != null
                    ? agent.shift().startTime() + " - " + agent.shift().endTime()
                    : "";

            // Write assignment rows — the shift an agent was assigned is visible here, when
            // present, so an operator reading the export can tell which envelope an agent was on.
            for (AssignmentDetail ad : agent.assignments()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(agent.agentName());
                row.createCell(1).setCellValue(agent.date().toString());
                row.createCell(2).setCellValue(ad.startTime().toString());
                row.createCell(3).setCellValue(ad.endTime().toString());
                row.createCell(4).setCellValue(ad.specializationName());
                row.createCell(5).setCellValue(ad.matchType());
                row.createCell(6).setCellValue("");
                if (hasShiftMode) {
                    row.createCell(7).setCellValue(shiftTemplateName);
                    row.createCell(8).setCellValue(shiftEnvelope);
                }
            }

            // Write break rows — Task 3's own done-criterion: these already read
            // AgentScheduleEntry.breaks(), so Task 2's band-derived correction reaches the XLSX
            // with no change to this loop. The Shift/Envelope cells are left blank on break rows
            // (not created) even in shift mode — they belong to the assignment rows.
            for (BreakDetail bd : agent.breaks()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(agent.agentName());
                row.createCell(1).setCellValue(agent.date().toString());
                row.createCell(2).setCellValue(bd.startTime().toString());
                row.createCell(3).setCellValue(bd.endTime().toString());
                row.createCell(4).setCellValue("");
                row.createCell(5).setCellValue("");
                row.createCell(6).setCellValue("Break");
            }
        }

        autoSizeColumns(sheet, cols.length);
    }

    // --- Tab 3: Preference Report ---

    private void writePreferenceReport(XSSFWorkbook workbook, CellStyle headerStyle,
                                        PreferenceReport report) {
        Sheet sheet = workbook.createSheet("Preference Report");

        Row header = sheet.createRow(0);
        String[] cols = {"Agent", "Date", "Source", "Preferred Start", "Actual Start",
                         "Start Honoured", "Preferred Break", "Actual Break", "Break Honoured"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        if (report == null || report.entries() == null) return;

        int rowNum = 1;
        for (PreferenceReportEntry e : report.entries()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.agentName());
            row.createCell(1).setCellValue(e.date().toString());
            row.createCell(2).setCellValue(e.preferenceSource());
            row.createCell(3).setCellValue(e.preferredStartTime() != null
                    ? e.preferredStartTime().toString() : "");
            row.createCell(4).setCellValue(e.actualStartTime() != null
                    ? e.actualStartTime().toString() : "");
            row.createCell(5).setCellValue(e.startTimeHonoured() ? "Yes" : "No");
            row.createCell(6).setCellValue(e.preferredBreakTime() != null
                    ? e.preferredBreakTime().toString() : "");
            row.createCell(7).setCellValue(e.actualBreakTime() != null
                    ? e.actualBreakTime().toString() : "");
            row.createCell(8).setCellValue(e.breakTimeHonoured() ? "Yes" : "No");
        }

        // Summary row
        if (report.summary() != null) {
            rowNum++; // blank row
            Row summaryHeader = sheet.createRow(rowNum++);
            summaryHeader.createCell(0).setCellValue("Summary");
            summaryHeader.getCell(0).setCellStyle(headerStyle);

            Row summaryRow = sheet.createRow(rowNum);
            summaryRow.createCell(0).setCellValue("Total Preferences: " + report.summary().totalPreferences());
            summaryRow.createCell(2).setCellValue("Start Honoured: " + report.summary().startTimeHonouredCount());
            summaryRow.createCell(4).setCellValue("Break Honoured: " + report.summary().breakTimeHonouredCount());
            summaryRow.createCell(6).setCellValue("Overall %: " + report.summary().overallHonouredPct());
        }

        autoSizeColumns(sheet, cols.length);
    }

    // --- Tab 4: Drift Report ---

    /**
     * XCUT-01/D-14: the same drift data visible on the Drift Report tab, also in the export.
     * Modelled line-for-line on {@link #writePreferenceReport} — the same guard-then-header-only
     * early return on a null report/entry-list, the same status-label mapping precedent (that
     * method's {@code startTimeHonoured ? "Yes" : "No"}), and the same trailing
     * {@link #autoSizeColumns} call so template names size naturally with no truncation.
     *
     * <p>The six main-table header strings and the two popularity-table header strings are
     * BYTE-IDENTICAL to {@code 17-UI-SPEC.md}'s Drift Report tab literals (XCUT-01) — a change to
     * either side without the other is exactly the kind of drift a copy-pasted literal invites,
     * so both surfaces must be edited together.
     *
     * <p>The popularity block is written whenever {@code report} itself is non-null, REGARDLESS
     * of whether the main entry list is empty — it answers a different question (D-13: which
     * templates are currently over-subscribed, read from stored usual shifts) that is independent
     * of this schedule's drift entries, so an empty entry list must not suppress it. Only a
     * {@code null} report (a SLOT-scheduled desk, where no drift report exists at all) produces
     * the header-only sheet.
     */
    private void writeDriftReport(XSSFWorkbook workbook, CellStyle headerStyle, DriftReport report) {
        Sheet sheet = workbook.createSheet("Drift Report");

        Row header = sheet.createRow(0);
        String[] cols = {"Agent", "Date", "Status", "Usual Start", "Actual Start", "Delta (min)"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        // Reachable for SLOT-scheduled desks: ScheduleService.getScheduleDetail (CR-01 fix) gates
        // buildDriftReport on SchedulingMode.SHIFT and passes null through for SLOT-mode desks, so
        // this is live production behaviour, not dead code -- do not remove as unreachable.
        if (report == null || report.entries() == null) {
            autoSizeColumns(sheet, cols.length);
            return;
        }

        int rowNum = 1;
        for (DriftReportEntry e : report.entries()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.agentName());
            row.createCell(1).setCellValue(e.date().toString());
            row.createCell(2).setCellValue(driftStatusLabel(e.status()));
            row.createCell(3).setCellValue(e.usualStartTime() != null ? e.usualStartTime().toString() : "");
            row.createCell(4).setCellValue(e.actualStartTime() != null ? e.actualStartTime().toString() : "");
            row.createCell(5).setCellValue(e.deltaMinutes() != null ? String.valueOf(e.deltaMinutes()) : "");
        }

        // Most-Subscribed Usual Shifts (DRFT-04, D-13) -- one blank spacer row, then the section
        // heading, then its own two-column header row, then one row per popularity entry in the
        // order buildDriftReport already sorted it. Never re-sorted here -- that would be a
        // second implementation of the ordering rule the report itself owns.
        rowNum++;
        Row popularityHeading = sheet.createRow(rowNum++);
        popularityHeading.createCell(0).setCellValue("Most-Subscribed Usual Shifts");

        Row popularityHeader = sheet.createRow(rowNum++);
        String[] popularityCols = {"Shift Template", "Agents (Usual Shift)"};
        for (int i = 0; i < popularityCols.length; i++) {
            Cell cell = popularityHeader.createCell(i);
            cell.setCellValue(popularityCols[i]);
            cell.setCellStyle(headerStyle);
        }

        if (report.popularity() != null) {
            for (ShiftPopularityEntry p : report.popularity()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.templateName());
                row.createCell(1).setCellValue(String.valueOf(p.agentCount()));
            }
        }

        autoSizeColumns(sheet, Math.max(cols.length, popularityCols.length));
    }

    private String driftStatusLabel(DriftStatus status) {
        return switch (status) {
            case NO_USUAL_SHIFT -> "No usual shift";
            case HONOURED -> "Honoured";
            case DRIFTED -> "Drifted";
        };
    }

    // --- Helpers ---

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
