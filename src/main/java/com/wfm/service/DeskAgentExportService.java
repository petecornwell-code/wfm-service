package com.wfm.service;

import com.wfm.dto.DeskAgentResponse;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeskAgentExportService {

    public byte[] exportDeskAgentsToExcel(List<DeskAgentResponse> agents) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("Desk Agents");

            // Shared identity columns (BambooHR ID, Email, Department, Job Title, Active) pull
            // their header text from EnrichedColumnLayout for round-trip symmetry with the
            // upload/template shape (D-13). Export-only metadata columns stay hardcoded.
            String[] columns = {
                "ID", "Desk ID", EnrichedColumnLayout.COL_BAMBOOHR_ID, "Name", EnrichedColumnLayout.COL_EMAIL,
                EnrichedColumnLayout.COL_DEPARTMENT, EnrichedColumnLayout.COL_JOB_TITLE,
                EnrichedColumnLayout.COL_ACTIVE, "Last Refreshed At",
                "Primary Specialization", "Secondary Specializations",
                "Contracted Hours Per Day", "Effective Contracted Hours Per Day",
                EnrichedColumnLayout.COL_FIRST_NAME, EnrichedColumnLayout.COL_LAST_NAME
            };

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (DeskAgentResponse agent : agents) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(agent.id() != null ? agent.id().toString() : "");
                row.createCell(1).setCellValue(agent.deskId() != null ? agent.deskId().toString() : "");
                row.createCell(2).setCellValue(agent.bamboohrId() != null ? agent.bamboohrId() : "");
                row.createCell(3).setCellValue(agent.name() != null ? agent.name() : "");
                row.createCell(4).setCellValue(agent.email() != null ? agent.email() : "");
                row.createCell(5).setCellValue(agent.department() != null ? agent.department() : "");
                row.createCell(6).setCellValue(agent.jobTitle() != null ? agent.jobTitle() : "");
                row.createCell(7).setCellValue(agent.active() ? "Yes" : "No");
                row.createCell(8).setCellValue(agent.lastRefreshedAt() != null ? agent.lastRefreshedAt().toString() : "");
                row.createCell(9).setCellValue(agent.primarySpecialization() != null ? agent.primarySpecialization().name() : "");
                row.createCell(10).setCellValue(formatSecondarySpecializations(agent.secondarySpecializations()));
                row.createCell(11).setCellValue(agent.contractedHoursPerDay() != null ? agent.contractedHoursPerDay().doubleValue() : 0);
                row.createCell(12).setCellValue(agent.effectiveContractedHoursPerDay() != null ? agent.effectiveContractedHoursPerDay().doubleValue() : 0);
                row.createCell(13).setCellValue(agent.firstName() != null ? agent.firstName() : "");
                row.createCell(14).setCellValue(agent.lastName() != null ? agent.lastName() : "");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    private String formatSecondarySpecializations(List<DeskAgentResponse.SpecSummary> specs) {
        if (specs == null || specs.isEmpty()) return "";
        return specs.stream()
                .map(DeskAgentResponse.SpecSummary::name)
                .collect(Collectors.joining(", "));
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
