package com.wfm.service;

import com.wfm.model.Schedule;
import org.springframework.stereotype.Service;

/**
 * Generates a multi-tab .xlsx spreadsheet from schedule output views.
 * Uses Apache POI XSSFWorkbook.
 */
@Service
public class ScheduleExportService {

    private final ScheduleOutputService scheduleOutputService;

    public ScheduleExportService(ScheduleOutputService scheduleOutputService) {
        this.scheduleOutputService = scheduleOutputService;
    }

    public byte[] exportToExcel(Schedule schedule) {
        // TODO: create XSSFWorkbook with 3 tabs:
        // 1. Staffing Summary
        // 2. Agent Schedule
        // 3. Preference Report
        // (Constraint violations are not included in the spreadsheet)
        return new byte[0];
    }
}
