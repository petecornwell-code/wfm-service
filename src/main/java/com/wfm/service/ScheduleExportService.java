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
