package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ClientManagementExportService {

    public byte[] exportEmployeesToExcel(List<BambooEmployeeResponse> employees) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet("Employees");

            String[] columns = {"ID", "Name", "Email", "Department", "Job Title", "Status"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (BambooEmployeeResponse emp : employees) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.id() != null ? emp.id() : "");
                row.createCell(1).setCellValue(emp.displayName() != null ? emp.displayName() : "");
                row.createCell(2).setCellValue(emp.workEmail() != null ? emp.workEmail() : "");
                row.createCell(3).setCellValue(emp.department() != null ? emp.department() : "");
                row.createCell(4).setCellValue(emp.jobTitle() != null ? emp.jobTitle() : "");
                row.createCell(5).setCellValue(emp.status() != null ? emp.status() : "");
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
