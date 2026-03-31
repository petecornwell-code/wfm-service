package com.wfm.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a sample ftes.xlsx spreadsheet for staffing requirement upload.
 *
 * Layout per sheet (one sheet per date):
 *   - Row 0: header row with "Specialization" then time-slot columns (e.g. "08:00-09:00")
 *   - Row 1..N: one row per specialization, cells contain integer FTE values
 */
public class FteSpreadsheetGenerator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static void generate(String outputPath,
                                LocalDate startDate, LocalDate endDate,
                                LocalTime startTime, LocalTime endTime,
                                int incrementMinutes,
                                List<String> specializations) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Bold header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                Sheet sheet = workbook.createSheet(date.toString());

                // Header row: "Specialization" | "08:00-09:00" | "09:00-10:00" | ...
                Row header = sheet.createRow(0);
                Cell specHeader = header.createCell(0);
                specHeader.setCellValue("Specialization");
                specHeader.setCellStyle(headerStyle);

                int col = 1;
                for (LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(incrementMinutes)) {
                    LocalTime slotEnd = t.plusMinutes(incrementMinutes);
                    Cell cell = header.createCell(col++);
                    cell.setCellValue(t.format(TIME_FMT) + "-" + slotEnd.format(TIME_FMT));
                    cell.setCellStyle(headerStyle);
                }

                // Data rows: one per specialization with sample FTE values
                for (int r = 0; r < specializations.size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    row.createCell(0).setCellValue(specializations.get(r));
                    int slotCol = 1;
                    for (LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(incrementMinutes)) {
                        // Sample FTE value: varies by specialization and time
                        int fte = 2 + (r % 3) + (slotCol % 4);
                        row.createCell(slotCol++).setCellValue(fte);
                    }
                }

                // Auto-size columns
                for (int c = 0; c <= col; c++) {
                    sheet.autoSizeColumn(c);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "src/main/resources/sample-data/ftes.xlsx";
        generate(path,
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 1, 25),
                LocalTime.of(8, 0),
                LocalTime.of(22, 0),
                60,
                List.of("Order Quality and Usability", "Payments and Safety",
                        "Privacy and Legal & DSA", "Shipping and Delivery"));
        System.out.println("Generated " + path);
    }
}
