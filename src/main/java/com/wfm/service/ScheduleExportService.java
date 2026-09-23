package com.wfm.service;

import com.wfm.dto.AgentDayOffResponse;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates a multi-tab .xlsx spreadsheet from schedule output views.
 * Uses Apache POI XSSFWorkbook.
 */
@Service
public class ScheduleExportService {

    /** Backwards-compatible entry point: no PTO data available, so that sheet is omitted. */
    public byte[] exportToExcel(ScheduleDetailResponse detail) {
        return exportToExcel(detail, List.of());
    }

    /**
     * @param daysOff day-off rows for the schedule period, which the PTO sheet renders. Supplied
     *                by the caller rather than fetched here because {@link ScheduleDetailResponse}
     *                does not carry them — the UI's PTO tab issues its own request for the same
     *                reason. An empty list omits the sheet rather than writing an empty one.
     */
    public byte[] exportToExcel(ScheduleDetailResponse detail, List<AgentDayOffResponse> daysOff) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            writeOverview(workbook, headerStyle, detail);
            writeStaffingSummary(workbook, headerStyle, detail.getStaffingSummary());
            writeAgentSchedule(workbook, headerStyle, detail.getAgentSchedule());
            writeAgentAllocation(workbook, detail);
            writePreferenceReport(workbook, headerStyle, detail.getPreferenceReport());
            writeDriftReport(workbook, headerStyle, detail.getDriftReport());
            writeConstraintViolations(workbook, headerStyle, detail.getViolatedHardConstraints(),
                    detail.getConstraintViolations());
            writePto(workbook, detail, daysOff);

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
            // The GRAND TOTAL entry carries a null date by construction
            // (ScheduleOutputService.buildStaffingSummary, added whenever the schedule spans more
            // than one date), so this dereference must be guarded or EVERY multi-day schedule's
            // export throws before any later sheet is written. Blank matches how the on-screen
            // Staffing Summary renders that row's Date cell. The per-day "TOTAL" row is not
            // affected -- it carries a real date.
            row.createCell(0).setCellValue(e.date() != null ? e.date().toString() : "");
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

    // --- Constraint Violations ---

    /**
     * The UI's "Constraint Violations" tab: a summary block of every constraint that scored, then
     * the individual violations beneath it. Two blocks rather than one flat table because a
     * constraint with 11 000 violations would otherwise bury the eleven that matter, and the
     * summary is what an operator reads first — which constraints fired, how hard, how often.
     *
     * <p>HARD and SOFT are coloured rather than merely labelled: whether a schedule is feasible
     * turns entirely on the hard block being empty, and that should be visible at a glance.
     */
    private void writeConstraintViolations(XSSFWorkbook workbook, CellStyle headerStyle,
                                            List<String> violatedHard,
                                            List<ConstraintViolationEntry> violations) {
        Sheet sheet = workbook.createSheet("Constraint Violations");
        ViolationStyles styles = new ViolationStyles(workbook);

        int rowNum = 0;
        Row banner = sheet.createRow(rowNum++);
        boolean feasible = violatedHard == null || violatedHard.isEmpty();
        cell(banner, 0, feasible
                        ? "No hard constraints violated — the schedule is feasible"
                        : "Hard constraints violated: " + String.join(", ", violatedHard),
                feasible ? styles.feasible : styles.infeasible);

        rowNum++;
        String[] summaryCols = {"Constraint", "Level", "Hard Weight", "Soft Weight",
                                "Violations", "Total Hard", "Total Soft"};
        Row summaryHeader = sheet.createRow(rowNum++);
        for (int i = 0; i < summaryCols.length; i++) {
            cell(summaryHeader, i, summaryCols[i], headerStyle);
        }

        if (violations != null) {
            for (ConstraintViolationEntry cv : violations) {
                Row row = sheet.createRow(rowNum++);
                cell(row, 0, cv.constraintName(), null);
                cell(row, 1, cv.level(), "HARD".equalsIgnoreCase(cv.level())
                        ? styles.hardLevel : styles.softLevel);
                numeric(row, 2, cv.weight() == null ? null : (double) cv.weight().hardScore());
                numeric(row, 3, cv.weight() == null ? null : (double) cv.weight().softScore());
                numeric(row, 4, (double) cv.violationCount());
                numeric(row, 5, cv.totalPenalty() == null ? null : (double) cv.totalPenalty().hardScore());
                numeric(row, 6, cv.totalPenalty() == null ? null : (double) cv.totalPenalty().softScore());
            }
        }

        int summaryLastRow = rowNum - 1;
        rowNum++;
        Row detailTitle = sheet.createRow(rowNum++);
        cell(detailTitle, 0, "Individual violations", headerStyle);
        String[] detailCols = {"Constraint", "Level", "Agent", "Timeslot", "Description"};
        Row detailHeader = sheet.createRow(rowNum++);
        for (int i = 0; i < detailCols.length; i++) {
            cell(detailHeader, i, detailCols[i], headerStyle);
        }

        if (violations != null) {
            for (ConstraintViolationEntry cv : violations) {
                if (cv.violations() == null) continue;
                for (ViolationDetail v : cv.violations()) {
                    Row row = sheet.createRow(rowNum++);
                    cell(row, 0, cv.constraintName(), null);
                    cell(row, 1, cv.level(), "HARD".equalsIgnoreCase(cv.level())
                            ? styles.hardLevel : styles.softLevel);
                    cell(row, 2, v.agentName() == null ? "" : v.agentName(), null);
                    cell(row, 3, v.timeslotLabel() == null ? "" : v.timeslotLabel(), null);
                    cell(row, 4, v.description() == null ? "" : v.description(), null);
                }
            }
        }

        for (int i = 0; i < Math.max(summaryCols.length, detailCols.length); i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.max(12 * 256, Math.min(sheet.getColumnWidth(i) + 512, 60 * 256)));
        }
        // Freeze below the summary block so the detail list scrolls under it.
        sheet.createFreezePane(0, Math.min(summaryLastRow + 1, 20));
    }

    // --- PTO / days off ---

    /**
     * The UI's "PTO" tab: agents down, dates across, each cell the day-off type. Sourced from
     * day-off rows passed in by the caller, because {@link ScheduleDetailResponse} does not carry
     * them.
     *
     * <p>Only agents with at least one day off appear — the UI does the same. A sheet listing all
     * 287 agents to show that 261 of them have nothing would bury the 26 that do.
     */
    private void writePto(XSSFWorkbook workbook, ScheduleDetailResponse detail,
                          List<AgentDayOffResponse> daysOff) {
        if (daysOff == null || daysOff.isEmpty()) {
            return;
        }

        Set<LocalDate> dates = new TreeSet<>();
        Map<String, Map<LocalDate, AgentDayOffResponse>> byAgent = new java.util.TreeMap<>(
                String.CASE_INSENSITIVE_ORDER);
        for (AgentDayOffResponse off : daysOff) {
            if (off.date() == null) continue;
            String name = off.agent() != null && off.agent().name() != null
                    ? off.agent().name() : "(unknown)";
            dates.add(off.date());
            byAgent.computeIfAbsent(name, n -> new java.util.HashMap<>()).put(off.date(), off);
        }
        // Prefer the schedule's own period so a week with no leave on its last day still shows
        // that day as a column, rather than the grid silently ending early.
        if (detail.getPeriodStartDate() != null && detail.getPeriodEndDate() != null) {
            for (LocalDate d = detail.getPeriodStartDate();
                 !d.isAfter(detail.getPeriodEndDate()); d = d.plusDays(1)) {
                dates.add(d);
            }
        }
        if (byAgent.isEmpty() || dates.isEmpty()) {
            return;
        }

        Sheet sheet = workbook.createSheet("PTO");
        PtoStyles styles = new PtoStyles(workbook);
        List<LocalDate> dateList = new ArrayList<>(dates);

        Row header = sheet.createRow(0);
        cell(header, 0, "Agent", styles.header);
        for (int i = 0; i < dateList.size(); i++) {
            cell(header, 1 + i, dateList.get(i).toString(), styles.header);
        }

        int rowNum = 1;
        for (Map.Entry<String, Map<LocalDate, AgentDayOffResponse>> e : byAgent.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            cell(row, 0, e.getKey(), styles.agentName);
            for (int i = 0; i < dateList.size(); i++) {
                AgentDayOffResponse off = e.getValue().get(dateList.get(i));
                if (off == null) {
                    cell(row, 1 + i, "", styles.empty);
                } else {
                    boolean requested = "REQUESTED".equalsIgnoreCase(off.status());
                    cell(row, 1 + i, requested ? off.type() + " (req)" : off.type(),
                            requested ? styles.requested
                                    : "PTO".equalsIgnoreCase(off.type()) ? styles.pto : styles.mandatory);
                }
            }
        }

        rowNum++;
        Row legend = sheet.createRow(rowNum);
        cell(legend, 0, "Legend", styles.header);
        cell(legend, 1, "PTO", styles.pto);
        cell(legend, 2, "approved leave", null);
        cell(legend, 3, "MANDATORY", styles.mandatory);
        cell(legend, 4, "rostered day off", null);
        cell(legend, 5, "(req)", styles.requested);
        cell(legend, 6, "requested, not yet approved", null);

        sheet.createFreezePane(1, 1);
        sheet.setColumnWidth(0, 30 * 256);
        for (int i = 0; i < dateList.size(); i++) {
            sheet.setColumnWidth(1 + i, 14 * 256);
        }
    }

    private static void numeric(Row row, int index, Double value) {
        Cell c = row.createCell(index);
        if (value != null) c.setCellValue(value);
    }

    /** Level colouring for the violations sheet. */
    private static final class ViolationStyles {
        final CellStyle hardLevel;
        final CellStyle softLevel;
        final CellStyle feasible;
        final CellStyle infeasible;

        ViolationStyles(XSSFWorkbook wb) {
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            hardLevel = AllocationStyles.filled(wb, "fee2e2", bold, HorizontalAlignment.CENTER);
            softLevel = AllocationStyles.filled(wb, "fef9c3", null, HorizontalAlignment.CENTER);
            feasible = AllocationStyles.filled(wb, "dcfce7", bold, HorizontalAlignment.LEFT);
            infeasible = AllocationStyles.filled(wb, "fee2e2", bold, HorizontalAlignment.LEFT);
        }
    }

    /** Day-off colouring, matching the UI's PTO tab. */
    private static final class PtoStyles {
        final CellStyle header;
        final CellStyle agentName;
        final CellStyle pto;
        final CellStyle mandatory;
        final CellStyle requested;
        final CellStyle empty;

        PtoStyles(XSSFWorkbook wb) {
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            header = AllocationStyles.filled(wb, "f3f4f6", bold, HorizontalAlignment.CENTER);
            agentName = AllocationStyles.filled(wb, null, null, HorizontalAlignment.LEFT);
            pto = AllocationStyles.filled(wb, "eff6ff", null, HorizontalAlignment.CENTER);
            mandatory = AllocationStyles.filled(wb, "fef2f2", null, HorizontalAlignment.CENTER);
            requested = AllocationStyles.filled(wb, "fefce8", null, HorizontalAlignment.CENTER);
            empty = AllocationStyles.filled(wb, null, null, HorizontalAlignment.CENTER);
        }
    }

    // --- Agent Allocation: one sheet per date, mirroring the UI's allocation grid ---

    /**
     * The spreadsheet counterpart of the UI's "Agent Allocation" tab: agents down, timeslots
     * across, one sheet per date. The other sheets in this workbook are row-per-record lists,
     * which answer "what is this agent doing" but not "what does the day look like" — the shape
     * an operator actually reads a roster in.
     *
     * <p>Colours are the UI's own values ({@code MATCH_COLORS} in {@code ScheduleResults.tsx}) so
     * the printed grid and the screen agree. The UI prefers a specialization's configured colour
     * and falls back to these; this export has only the match type available, because
     * {@link ScheduleDetailResponse} carries no specialization palette. For any desk whose
     * specializations have no colour set, the two are identical — extending the DTO is the
     * follow-up that would close the gap for the rest.
     *
     * <p>One sheet per date rather than the UI's stacked blocks: a frozen header and agent column
     * cannot span stacked tables, and freezing is what makes a 16-column grid readable at all.
     */
    private void writeAgentAllocation(XSSFWorkbook workbook, ScheduleDetailResponse detail) {
        List<AgentScheduleEntry> entries = detail.getAgentSchedule();
        if (entries == null || entries.isEmpty()) {
            return;
        }

        Map<String, Map<LocalTime, Integer>> unfilled = unfilledSeatsByDateAndSlot(detail);

        AllocationStyles styles = new AllocationStyles(workbook);

        Set<LocalDate> dates = new TreeSet<>();
        for (AgentScheduleEntry e : entries) {
            if (e.date() != null) dates.add(e.date());
        }

        for (LocalDate date : dates) {
            List<AgentScheduleEntry> dayEntries = new ArrayList<>();
            for (AgentScheduleEntry e : entries) {
                if (date.equals(e.date())) dayEntries.add(e);
            }
            dayEntries.sort(Comparator.comparing(
                    AgentScheduleEntry::agentName, Comparator.nullsLast(String::compareToIgnoreCase)));

            Map<LocalTime, Integer> dayUnfilled =
                    unfilled.getOrDefault(date.toString(), Map.of());
            writeAllocationSheet(workbook, styles, date, dayEntries, dayUnfilled);
        }
    }

    private void writeAllocationSheet(XSSFWorkbook workbook, AllocationStyles styles, LocalDate date,
                                       List<AgentScheduleEntry> dayEntries,
                                       Map<LocalTime, Integer> unfilledPerSlot) {
        // Slot columns come from assigned seats, break spans AND unfilled seats, so a timeslot
        // nobody was assigned to still appears as a column rather than silently vanishing from
        // the day — that column is precisely where the shortfall is.
        Set<LocalTime> slotSet = new TreeSet<>(unfilledPerSlot.keySet());
        int increment = incrementMinutes(dayEntries);
        for (AgentScheduleEntry e : dayEntries) {
            for (AssignmentDetail a : e.assignments()) slotSet.add(a.startTime());
            for (BreakDetail b : e.breaks()) slotSet.addAll(slotStarts(b, increment));
        }
        if (slotSet.isEmpty()) {
            return;
        }
        List<LocalTime> slots = new ArrayList<>(slotSet);

        Sheet sheet = workbook.createSheet(
                WorkbookUtil.createSafeSheetName("Allocation " + date));

        Row header = sheet.createRow(0);
        cell(header, 0, "Agent", styles.header);
        cell(header, 1, "Hours", styles.header);
        for (int i = 0; i < slots.size(); i++) {
            boolean shortfall = unfilledPerSlot.getOrDefault(slots.get(i), 0) > 0;
            cell(header, 2 + i, hhmm(slots.get(i)),
                    shortfall ? styles.headerShortfall : styles.headerSlot);
        }

        int rowNum = 1;
        Map<LocalTime, Integer> agentsPerSlot = new HashMap<>();
        BigDecimal totalHours = BigDecimal.ZERO;

        for (AgentScheduleEntry entry : dayEntries) {
            Row row = sheet.createRow(rowNum++);
            cell(row, 0, entry.agentName() == null ? "" : entry.agentName(), styles.agentName);

            BigDecimal hours = entry.totalHours() == null ? BigDecimal.ZERO : entry.totalHours();
            totalHours = totalHours.add(hours);
            Cell hoursCell = row.createCell(1);
            hoursCell.setCellValue(hours.doubleValue());
            hoursCell.setCellStyle(styles.hours);

            Map<LocalTime, String> matchBySlot = new HashMap<>();
            for (AssignmentDetail a : entry.assignments()) {
                matchBySlot.put(a.startTime(), a.matchType());
                agentsPerSlot.merge(a.startTime(), 1, Integer::sum);
            }
            Set<LocalTime> breakSlots = new LinkedHashSet<>();
            for (BreakDetail b : entry.breaks()) breakSlots.addAll(slotStarts(b, increment));

            for (int i = 0; i < slots.size(); i++) {
                LocalTime slot = slots.get(i);
                if (matchBySlot.containsKey(slot)) {
                    cell(row, 2 + i, "", styles.forMatchType(matchBySlot.get(slot)));
                } else if (breakSlots.contains(slot)) {
                    cell(row, 2 + i, "B", styles.breakCell);
                } else {
                    cell(row, 2 + i, "", styles.emptyCell);
                }
            }
        }

        Row totals = sheet.createRow(rowNum++);
        cell(totals, 0, "Total: " + dayEntries.size() + " agents", styles.totalLabel);
        Cell totalHoursCell = totals.createCell(1);
        totalHoursCell.setCellValue(totalHours.doubleValue());
        totalHoursCell.setCellStyle(styles.totalHours);
        for (int i = 0; i < slots.size(); i++) {
            int working = agentsPerSlot.getOrDefault(slots.get(i), 0);
            Cell c = totals.createCell(2 + i);
            if (working > 0) c.setCellValue(working);
            c.setCellStyle(styles.totalCell);
        }

        boolean anyShortfall = unfilledPerSlot.values().stream().anyMatch(n -> n > 0);
        if (anyShortfall) {
            Row short_ = sheet.createRow(rowNum++);
            cell(short_, 0, "Unfilled", styles.shortfallLabel);
            cell(short_, 1, "", styles.shortfallLabel);
            for (int i = 0; i < slots.size(); i++) {
                int n = unfilledPerSlot.getOrDefault(slots.get(i), 0);
                Cell c = short_.createCell(2 + i);
                if (n > 0) c.setCellValue(n);
                c.setCellStyle(n > 0 ? styles.shortfallCell : styles.shortfallEmpty);
            }
        }

        // Legend, two rows below the grid — the colours carry all the meaning in this sheet, so a
        // printed copy without a key is unreadable.
        rowNum++;
        Row legend = sheet.createRow(rowNum);
        cell(legend, 0, "Legend", styles.header);
        String[][] key = {
                {"Primary speciality", "PRIMARY"}, {"Secondary speciality", "SECONDARY"},
                {"No speciality match", "NONE"}, {"B = break", "BREAK"}, {"Unfilled seat", "SHORTFALL"}};
        int col = 1;
        for (String[] item : key) {
            cell(legend, col, "", styles.forLegend(item[1]));
            cell(legend, col + 1, item[0], null);
            col += 3;
        }

        // Freeze the agent name, the hours total and the header row: a 16-column day is unreadable
        // scrolled without them, which is the whole reason this is one sheet per date.
        sheet.createFreezePane(2, 1);
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 8 * 256);
        for (int i = 0; i < slots.size(); i++) {
            sheet.setColumnWidth(2 + i, 6 * 256);
        }
    }

    /**
     * Unfilled seats per date and slot, read from the "Unassigned assignment" constraint
     * violations exactly as the UI does — {@code timeslotLabel} is {@code "YYYY-MM-DD HH:MM-HH:MM"}.
     * A label that does not parse is skipped rather than throwing: this sheet is a report, and
     * losing one shortfall marker is a far better outcome than failing the whole export.
     */
    private Map<String, Map<LocalTime, Integer>> unfilledSeatsByDateAndSlot(
            ScheduleDetailResponse detail) {
        Map<String, Map<LocalTime, Integer>> result = new HashMap<>();
        List<ConstraintViolationEntry> violations = detail.getConstraintViolations();
        if (violations == null) return result;

        for (ConstraintViolationEntry cv : violations) {
            if (!"Unassigned assignment".equals(cv.constraintName()) || cv.violations() == null) {
                continue;
            }
            for (ViolationDetail v : cv.violations()) {
                String label = v.timeslotLabel();
                if (label == null) continue;
                int space = label.indexOf(' ');
                if (space < 0) continue;
                String datePart = label.substring(0, space);
                String timePart = label.substring(space + 1).trim();
                int dash = timePart.indexOf('-');
                if (dash > 0) timePart = timePart.substring(0, dash).trim();
                try {
                    LocalTime slot = LocalTime.parse(timePart.length() == 5 ? timePart + ":00" : timePart);
                    result.computeIfAbsent(datePart, d -> new HashMap<>())
                            .merge(slot, 1, Integer::sum);
                } catch (RuntimeException ignored) {
                    // Unparseable label — skip this marker, keep the sheet.
                }
            }
        }
        return result;
    }

    /** Grid increment, derived from the first assignment of the day; 0 when unknown. */
    private int incrementMinutes(List<AgentScheduleEntry> dayEntries) {
        for (AgentScheduleEntry e : dayEntries) {
            for (AssignmentDetail a : e.assignments()) {
                if (a.startTime() != null && a.endTime() != null) {
                    return com.wfm.util.DayWindow.durationMinutes(a.startTime(), a.endTime());
                }
            }
        }
        return 0;
    }

    /** Every grid slot a break covers. A break can span several slots (D-01 bands). */
    private List<LocalTime> slotStarts(BreakDetail b, int incrementMinutes) {
        List<LocalTime> out = new ArrayList<>();
        if (b.startTime() == null) return out;
        if (incrementMinutes <= 0 || b.endTime() == null) {
            out.add(b.startTime());
            return out;
        }
        int from = com.wfm.util.DayWindow.startMinute(b.startTime());
        int to = com.wfm.util.DayWindow.endMinute(b.endTime());
        for (int m = from; m < to; m += incrementMinutes) {
            out.add(com.wfm.util.DayWindow.toLocalTime(m));
        }
        return out;
    }

    private static String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static void cell(Row row, int index, String value, CellStyle style) {
        Cell c = row.createCell(index);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    /**
     * The allocation grid's palette, built once per workbook. POI caps a workbook at 64k cell
     * styles and creating one per cell exhausts that on a desk this size, so every style here is
     * created once and shared across all date sheets.
     *
     * <p>Hex values are copied from {@code ScheduleResults.tsx} so screen and spreadsheet agree.
     */
    private static final class AllocationStyles {
        final CellStyle header;
        final CellStyle headerSlot;
        final CellStyle headerShortfall;
        final CellStyle agentName;
        final CellStyle hours;
        final CellStyle primary;
        final CellStyle secondary;
        final CellStyle noMatch;
        final CellStyle breakCell;
        final CellStyle emptyCell;
        final CellStyle totalLabel;
        final CellStyle totalHours;
        final CellStyle totalCell;
        final CellStyle shortfallLabel;
        final CellStyle shortfallCell;
        final CellStyle shortfallEmpty;

        AllocationStyles(XSSFWorkbook wb) {
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            XSSFFont boldRed = wb.createFont();
            boldRed.setBold(true);
            boldRed.setColor(new XSSFColor(rgb("991b1b"), null));

            header = filled(wb, "f3f4f6", bold, HorizontalAlignment.LEFT);
            headerSlot = filled(wb, "f3f4f6", bold, HorizontalAlignment.CENTER);
            headerShortfall = filled(wb, "fecaca", boldRed, HorizontalAlignment.CENTER);
            agentName = filled(wb, null, null, HorizontalAlignment.LEFT);
            hours = numeric(wb, "f9fafb");
            primary = filled(wb, "dcfce7", null, HorizontalAlignment.CENTER);
            secondary = filled(wb, "fef9c3", null, HorizontalAlignment.CENTER);
            noMatch = filled(wb, "fee2e2", null, HorizontalAlignment.CENTER);
            breakCell = filled(wb, "e5e7eb", null, HorizontalAlignment.CENTER);
            emptyCell = filled(wb, null, null, HorizontalAlignment.CENTER);
            totalLabel = filled(wb, "f9fafb", bold, HorizontalAlignment.LEFT);
            totalHours = numeric(wb, "f9fafb");
            totalCell = filled(wb, "f9fafb", bold, HorizontalAlignment.CENTER);
            shortfallLabel = filled(wb, "fef2f2", boldRed, HorizontalAlignment.LEFT);
            shortfallCell = filled(wb, "fecaca", boldRed, HorizontalAlignment.CENTER);
            shortfallEmpty = filled(wb, "fef2f2", null, HorizontalAlignment.CENTER);
        }

        CellStyle forMatchType(String matchType) {
            if ("SECONDARY".equals(matchType)) return secondary;
            if ("NONE".equals(matchType)) return noMatch;
            return primary;
        }

        CellStyle forLegend(String kind) {
            return switch (kind) {
                case "SECONDARY" -> secondary;
                case "NONE" -> noMatch;
                case "BREAK" -> breakCell;
                case "SHORTFALL" -> shortfallCell;
                default -> primary;
            };
        }

        static CellStyle filled(XSSFWorkbook wb, String hex, XSSFFont font,
                                        HorizontalAlignment align) {
            XSSFCellStyle style = wb.createCellStyle();
            if (hex != null) {
                style.setFillForegroundColor(new XSSFColor(rgb(hex), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (font != null) style.setFont(font);
            style.setAlignment(align);
            thinBorders(style);
            return style;
        }

        private static CellStyle numeric(XSSFWorkbook wb, String hex) {
            XSSFCellStyle style = (XSSFCellStyle) filled(wb, hex, null, HorizontalAlignment.RIGHT);
            style.setDataFormat(wb.createDataFormat().getFormat("0.0"));
            return style;
        }

        private static void thinBorders(XSSFCellStyle style) {
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderColor(XSSFCellBorder.BorderSide.LEFT, new XSSFColor(rgb("e5e7eb"), null));
            style.setBorderColor(XSSFCellBorder.BorderSide.RIGHT, new XSSFColor(rgb("e5e7eb"), null));
            style.setBorderColor(XSSFCellBorder.BorderSide.TOP, new XSSFColor(rgb("e5e7eb"), null));
            style.setBorderColor(XSSFCellBorder.BorderSide.BOTTOM, new XSSFColor(rgb("e5e7eb"), null));
        }

        static byte[] rgb(String hex) {
            return new byte[]{
                    (byte) Integer.parseInt(hex.substring(0, 2), 16),
                    (byte) Integer.parseInt(hex.substring(2, 4), 16),
                    (byte) Integer.parseInt(hex.substring(4, 6), 16)};
        }
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

        // Style the header-only sheet too, matching writeDriftReport's early-return path: a
        // schedule with no preferences still gets a sheet that looks like the others.
        if (report == null || report.entries() == null) {
            finishSheet(sheet, cols.length);
            return;
        }

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

    /**
     * Header style shared by every list sheet: bold on the same grey the UI uses for table
     * headers, with a rule underneath. Previously bold alone, which left a header
     * indistinguishable from a bolded data row once a sheet was scrolled.
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(AllocationStyles.rgb("f3f4f6"), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderColor(XSSFCellBorder.BorderSide.BOTTOM,
                new XSSFColor(AllocationStyles.rgb("d1d5db"), null));
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Column widths, a frozen header row and a filter dropdown — applied to every list sheet, so
     * they behave like something a person is meant to read rather than a data dump.
     *
     * <p>{@code autoSizeColumn} is clamped: a free-text column (a violation description, a long
     * agent name) otherwise produces a column hundreds of characters wide that pushes every other
     * column off the screen. The floor stops a narrow header like "Date" collapsing.
     */
    private void finishSheet(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.max(10 * 256, Math.min(width + 512, 46 * 256)));
        }
        sheet.createFreezePane(0, 1);
        if (sheet.getLastRowNum() > 0 && columnCount > 0) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, sheet.getLastRowNum(), 0, columnCount - 1));
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        finishSheet(sheet, columnCount);
    }
}
