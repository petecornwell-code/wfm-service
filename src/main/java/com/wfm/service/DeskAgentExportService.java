package com.wfm.service;

import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAgentResponse.DayHoursEntry;
import com.wfm.dto.DeskAgentResponse.UsualShiftEntry;
import com.wfm.model.DayOffType;
import com.wfm.util.EnrichedColumnLayout;
import com.wfm.util.FormulaInjectionSanitizer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DeskAgentExportService {

    private static final int FIRST_DAY_COLUMN = 13;
    private static final int FIRST_USUAL_SHIFT_COLUMN = 20;

    public byte[] exportDeskAgentsToExcel(List<DeskAgentResponse> agents) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("Desk Agents");

            // Shared identity columns (BambooHR ID, Email, Department, Job Title, Active) pull
            // their header text from EnrichedColumnLayout for round-trip symmetry with the
            // upload/template shape (D-13). Export-only metadata columns stay hardcoded. The
            // seven Mon-Sun columns (D-02) are appended programmatically from DAY_ORDER so no
            // weekday name is ever a string literal in this file (I-4's drift class).
            List<String> columns = new ArrayList<>(List.of(
                "ID", "Desk ID", EnrichedColumnLayout.COL_BAMBOOHR_ID, "Name", EnrichedColumnLayout.COL_EMAIL,
                EnrichedColumnLayout.COL_DEPARTMENT, EnrichedColumnLayout.COL_JOB_TITLE,
                EnrichedColumnLayout.COL_ACTIVE, "Last Refreshed At",
                "Primary Specialization", "Secondary Specializations",
                "Contracted Hours Per Day", "Effective Contracted Hours Per Day"
            ));
            for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
                columns.add(EnrichedColumnLayout.dayHeader(day));
            }
            // D-18: seven Usual Shift columns immediately after the day-hours group, from the
            // same DAY_ORDER loop -- export/template/parser stay one shape and the sheet is
            // directly re-uploadable.
            for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
                columns.add(EnrichedColumnLayout.usualShiftHeader(day));
            }
            columns.add(EnrichedColumnLayout.COL_FIRST_NAME);
            columns.add(EnrichedColumnLayout.COL_LAST_NAME);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (DeskAgentResponse agent : agents) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(agent.id() != null ? agent.id().toString() : "");
                row.createCell(1).setCellValue(agent.deskId() != null ? agent.deskId().toString() : "");
                row.createCell(2).setCellValue(agent.bamboohrId() != null ? agent.bamboohrId() : "");
                row.createCell(3).setCellValue(sanitize(agent.name()));
                row.createCell(4).setCellValue(sanitize(agent.email()));
                row.createCell(5).setCellValue(sanitize(agent.department()));
                row.createCell(6).setCellValue(sanitize(agent.jobTitle()));
                row.createCell(7).setCellValue(agent.active() ? "Yes" : "No");
                row.createCell(8).setCellValue(agent.lastRefreshedAt() != null ? agent.lastRefreshedAt().toString() : "");
                row.createCell(9).setCellValue(agent.primarySpecialization() != null
                        ? sanitize(agent.primarySpecialization().name()) : "");
                row.createCell(10).setCellValue(sanitize(formatSecondarySpecializations(agent.secondarySpecializations())));
                row.createCell(11).setCellValue(agent.contractedHoursPerDay() != null ? agent.contractedHoursPerDay().doubleValue() : 0);
                row.createCell(12).setCellValue(agent.effectiveContractedHoursPerDay() != null ? agent.effectiveContractedHoursPerDay().doubleValue() : 0);
                writeDayCells(row, agent);
                writeUsualShiftCells(row, agent);
                row.createCell(FIRST_USUAL_SHIFT_COLUMN + 7).setCellValue(sanitize(agent.firstName()));
                row.createCell(FIRST_USUAL_SHIFT_COLUMN + 8).setCellValue(sanitize(agent.lastName()));
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    /**
     * Writes the seven Mon-Sun columns (D-02), mirroring 13-UI-SPEC.md Section 3's display-mode
     * branching order exactly (load-bearing per 13-RESEARCH.md Pitfalls 1/2 — a labelled day is
     * stored as zero hours, so MANDATORY/PTO must be checked before any numeric read):
     * 1. entry missing (defensive only — plan 13-01 guarantees all 7 keys) -> numeric 0
     * 2. MANDATORY -> the string "MANDATORY"
     * 3. PTO -> the string "PTO"
     * 4. a stored row with no label -> its numeric hours, including an explicit 0
     * 5. no stored row -> the resolved effective value, never blank (P-08) — a blank cell makes
     *    {@code DeskAssignmentUploadService.parseDayCell} fail and the caller skip the whole row.
     *
     * These two keyword strings are produced by this class from an enum, never from operator
     * input, so they deliberately bypass {@link #sanitize(String)} (T-13-10).
     */
    private void writeDayCells(Row row, DeskAgentResponse agent) {
        Map<DayOfWeek, DayHoursEntry> dayHours = agent.dayHours();
        DayOfWeek[] order = EnrichedColumnLayout.DAY_ORDER;
        for (int i = 0; i < order.length; i++) {
            DayHoursEntry entry = dayHours != null ? dayHours.get(order[i]) : null;
            Cell cell = row.createCell(FIRST_DAY_COLUMN + i);
            if (entry == null) {
                cell.setCellValue(0);
            } else if (entry.dayOffType() == DayOffType.MANDATORY) {
                cell.setCellValue("MANDATORY");
            } else if (entry.dayOffType() == DayOffType.PTO) {
                cell.setCellValue("PTO");
            } else if (entry.hasRow()) {
                cell.setCellValue(entry.hours().doubleValue());
            } else {
                cell.setCellValue(entry.effectiveHours().doubleValue());
            }
        }
    }

    /**
     * Writes the seven Usual Shift columns (D-18). Per 16-UI-SPEC.md §3's contract this always
     * writes the RAW stored template name, never the date-resolved three-state distinction the
     * roster tile shows -- so an exported sheet stays a faithful, re-uploadable record. A missing
     * entry, a null name, or a resolved status other than a stored row (i.e. NOT_SET) all leave
     * the cell blank, matching D-07's "blank means no usual shift, and is valid" upload contract.
     * Always the String overload of {@link Cell#setCellValue(String)} -- never numeric -- so a
     * template named entirely of digits exports and re-imports as text unchanged.
     */
    private void writeUsualShiftCells(Row row, DeskAgentResponse agent) {
        Map<DayOfWeek, UsualShiftEntry> usualShift = agent.usualShift();
        DayOfWeek[] order = EnrichedColumnLayout.DAY_ORDER;
        for (int i = 0; i < order.length; i++) {
            UsualShiftEntry entry = usualShift != null ? usualShift.get(order[i]) : null;
            String name = entry != null ? entry.name() : null;
            if (name == null) {
                continue; // leave the cell blank, never created (matches writeSanitized's null behaviour)
            }
            row.createCell(FIRST_USUAL_SHIFT_COLUMN + i).setCellValue(sanitize(name));
        }
    }

    private String formatSecondarySpecializations(List<DeskAgentResponse.SpecSummary> specs) {
        if (specs == null || specs.isEmpty()) return "";
        return specs.stream()
                .map(DeskAgentResponse.SpecSummary::name)
                .collect(Collectors.joining(", "));
    }

    /**
     * Formula/CSV-injection guard (CR-02): every operator-controlled string field exported
     * here (name/email/department/jobTitle/specializations/firstName/lastName) is settable
     * verbatim from an uploaded spreadsheet via {@code DeskAssignmentUploadService}, so it must
     * receive the same {@link FormulaInjectionSanitizer} treatment as
     * {@code DeskAssignmentTemplateService} to avoid formula/DDE evaluation when this export is
     * reopened in Excel.
     */
    private String sanitize(String value) {
        return FormulaInjectionSanitizer.sanitize(value != null ? value : "");
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
