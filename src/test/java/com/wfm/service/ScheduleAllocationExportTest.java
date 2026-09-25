package com.wfm.service;

import com.wfm.dto.AgentDayOffResponse;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.*;
import com.wfm.dto.ScheduleSummary;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "Agent Allocation" sheet: agents down, timeslots across, one sheet per date — the roster
 * shape an operator reads, as opposed to the row-per-record lists the other sheets carry.
 *
 * <p>Assertions are made by reopening the generated workbook with POI rather than by inspecting
 * the builder's intermediate state, so what is checked is what Excel will actually show:
 * cell text, fill colour, frozen panes and column widths.
 */
class ScheduleAllocationExportTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 9, 21);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 9, 22);

    private static Workbook workbook;

    @BeforeAll
    static void generate() throws IOException {
        byte[] bytes = new ScheduleExportService().exportToExcel(detail());
        workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    // --- fixture ---

    private static AssignmentDetail seat(int hour, String matchType) {
        return new AssignmentDetail(UUID.randomUUID(), LocalTime.of(hour, 0),
                LocalTime.of((hour + 1) % 24, 0), "Security and Item Quality", matchType);
    }

    private static AgentScheduleEntry agent(String name, LocalDate date, BigDecimal hours,
                                            List<AssignmentDetail> seats, List<BreakDetail> breaks) {
        return new AgentScheduleEntry(UUID.randomUUID(), name, date,
                LocalTime.of(15, 0), LocalTime.MIDNIGHT, hours, seats, breaks, null, null);
    }

    private static ScheduleDetailResponse detail() {
        ScheduleDetailResponse d = new ScheduleDetailResponse();
        d.setDeskName("Vinted");
        d.setStatus("COMPLETED");
        d.setPeriodStartDate(DAY_ONE);
        d.setPeriodEndDate(DAY_TWO);
        d.setIncrementMinutes(60);
        d.setAgentSchedule(List.of(
                // Evening shift running to midnight, break at 19:00.
                agent("Zoe Last", DAY_ONE, new BigDecimal("3.0"),
                        List.of(seat(22, "PRIMARY"), seat(23, "PRIMARY"), seat(19, "SECONDARY")),
                        List.of(new BreakDetail(LocalTime.of(20, 0), LocalTime.of(21, 0), 60))),
                agent("Adam First", DAY_ONE, new BigDecimal("2.0"),
                        List.of(seat(22, "NONE"), seat(23, "PRIMARY")), List.of()),
                agent("Adam First", DAY_TWO, new BigDecimal("1.0"),
                        List.of(seat(23, "PRIMARY")), List.of())));
        d.setConstraintViolations(List.of(new ConstraintViolationEntry(
                "Unassigned assignment", "SOFT", new ScheduleSummary.ScoreDto(0, 1000), 2,
                new ScheduleSummary.ScoreDto(0, -2000),
                List.of(
                        new ViolationDetail(null, null, UUID.randomUUID(),
                                "2026-09-21 21:00-22:00", "unfilled"),
                        new ViolationDetail(null, null, UUID.randomUUID(),
                                "2026-09-21 21:00-22:00", "unfilled")))));
        return d;
    }

    private static String fill(Cell cell) {
        XSSFCellStyle style = (XSSFCellStyle) cell.getCellStyle();
        return style.getFillForegroundColorColor() == null
                ? null : style.getFillForegroundColorColor().getARGBHex();
    }

    private static Sheet dayOne() {
        return workbook.getSheet("Allocation 2026-09-21");
    }

    @Nested
    @DisplayName("sheet layout")
    class Layout {

        @Test
        @DisplayName("one sheet per date, alongside the existing sheets")
        void oneSheetPerDate() {
            assertThat(workbook.getSheet("Allocation 2026-09-21")).isNotNull();
            assertThat(workbook.getSheet("Allocation 2026-09-22")).isNotNull();
            // The sheets that existed before this change must survive it.
            assertThat(workbook.getSheet("Overview")).isNotNull();
            assertThat(workbook.getSheet("Agent Schedule")).isNotNull();
        }

        @Test
        @DisplayName("agent, shift and hours columns plus the header row are frozen")
        void panesAreFrozen() {
            PaneInformation pane = dayOne().getPaneInformation();
            assertThat(pane).isNotNull();
            // Three pinned columns now: Agent, Shift, Hours.
            assertThat(pane.getVerticalSplitLeftColumn()).isEqualTo((short) 3);
            assertThat(pane.getHorizontalSplitTopRow()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("columns have explicit widths — autoSizeColumn on a 16-column grid is slow")
        void columnsHaveExplicitWidths() {
            assertThat(dayOne().getColumnWidth(0)).isEqualTo(30 * 256);   // Agent
            assertThat(dayOne().getColumnWidth(1)).isEqualTo(14 * 256);   // Shift
            assertThat(dayOne().getColumnWidth(2)).isEqualTo(8 * 256);    // Hours
            assertThat(dayOne().getColumnWidth(3)).isEqualTo(6 * 256);    // first slot
        }

        @Test
        @DisplayName("slot columns span every hour that carries a seat, a break or a shortfall")
        void slotColumnsCoverTheDay() {
            Row header = dayOne().getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Agent");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Shift");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Hours");
            // 19:00 seat, 20:00 break, 21:00 unfilled, 22:00 and 23:00 seats.
            assertThat(List.of(
                    header.getCell(3).getStringCellValue(), header.getCell(4).getStringCellValue(),
                    header.getCell(5).getStringCellValue(), header.getCell(6).getStringCellValue(),
                    header.getCell(7).getStringCellValue()))
                    .containsExactly("19:00", "20:00", "21:00", "22:00", "23:00");
        }

        @Test
        @DisplayName("with no envelopes, every agent falls in one group and sorts by name")
        void agentsAreSorted() {
            // Neither fixture agent carries a ShiftDescriptor, so both land in the same
            // "No shift assigned" group and the name tie-break decides. Shift ordering proper is
            // asserted in ShiftGrouping below.
            assertThat(dayOne().getRow(1).getCell(0).getStringCellValue()).isEqualTo("Adam First");
            assertThat(dayOne().getRow(1).getCell(1).getStringCellValue()).isEqualTo("No shift assigned");
            assertThat(dayOne().getRow(2).getCell(0).getStringCellValue()).isEqualTo("Zoe Last");
        }
    }

    @Nested
    @DisplayName("cell colouring matches the UI palette")
    class Colours {

        @Test
        @DisplayName("a worked slot carries its match-type colour")
        void workedSlots() {
            Row adam = dayOne().getRow(1);
            assertThat(fill(adam.getCell(6))).isEqualTo("FFFEE2E2");   // 22:00 NONE
            assertThat(fill(adam.getCell(7))).isEqualTo("FFDCFCE7");   // 23:00 PRIMARY
            Row zoe = dayOne().getRow(2);
            assertThat(fill(zoe.getCell(3))).isEqualTo("FFFEF9C3");    // 19:00 SECONDARY
        }

        @Test
        @DisplayName("a break slot is grey and labelled B")
        void breakSlots() {
            Cell breakCell = dayOne().getRow(2).getCell(4);            // Zoe, 20:00
            assertThat(breakCell.getStringCellValue()).isEqualTo("B");
            assertThat(fill(breakCell)).isEqualTo("FFE5E7EB");
        }

        @Test
        @DisplayName("a slot header with unfilled seats is red")
        void shortfallHeaderIsRed() {
            assertThat(fill(dayOne().getRow(0).getCell(5))).isEqualTo("FFFECACA");   // 21:00
            assertThat(fill(dayOne().getRow(0).getCell(7))).isEqualTo("FFF3F4F6");   // 23:00 normal
        }
    }

    @Nested
    @DisplayName("totals and shortfall")
    class Totals {

        @Test
        @DisplayName("the total row counts agents and sums hours")
        void totalRow() {
            Row totals = dayOne().getRow(3);
            assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("Total: 2 agents");
            assertThat(totals.getCell(2).getNumericCellValue()).isEqualTo(5.0);
            assertThat(totals.getCell(6).getNumericCellValue()).isEqualTo(2.0);  // 22:00, both agents
            assertThat(totals.getCell(3).getNumericCellValue()).isEqualTo(1.0);  // 19:00, Zoe only
        }

        @Test
        @DisplayName("the unfilled row reports seats nobody took, from the violation list")
        void unfilledRow() {
            Row unfilled = dayOne().getRow(4);
            assertThat(unfilled.getCell(0).getStringCellValue()).isEqualTo("Unfilled");
            assertThat(unfilled.getCell(5).getNumericCellValue()).isEqualTo(2.0);   // 21:00
            assertThat(fill(unfilled.getCell(5))).isEqualTo("FFFECACA");
            // A slot with no shortfall stays blank rather than showing a zero (22:00, now col 6).
            assertThat(unfilled.getCell(6).getCellType()).isEqualTo(CellType.BLANK);
        }

        @Test
        @DisplayName("a day with no shortfall has no unfilled row at all")
        void noUnfilledRowWhenFullyCovered() {
            Sheet dayTwo = workbook.getSheet("Allocation 2026-09-22");
            // header, one agent, totals, blank, legend — no shortfall row before the legend.
            assertThat(dayTwo.getRow(2).getCell(0).getStringCellValue()).startsWith("Total:");
            assertThat(dayTwo.getRow(3)).isNull();
            assertThat(dayTwo.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Legend");
        }
    }

    @Test
    @DisplayName("a legend explains the colours, so a printed copy is readable")
    void legendIsPresent() {
        Row legend = dayOne().getRow(6);
        assertThat(legend.getCell(0).getStringCellValue()).isEqualTo("Legend");
        assertThat(legend.getCell(2).getStringCellValue()).isEqualTo("Primary speciality");
        assertThat(fill(legend.getCell(1))).isEqualTo("FFDCFCE7");
    }

    @Nested
    @DisplayName("Constraint Violations sheet")
    class Violations {

        private Sheet sheet() {
            return workbook.getSheet("Constraint Violations");
        }

        @Test
        @DisplayName("leads with a feasibility banner, green when no hard constraint fired")
        void feasibilityBanner() {
            Cell banner = sheet().getRow(0).getCell(0);
            assertThat(banner.getStringCellValue()).contains("No hard constraints violated");
            assertThat(fill(banner)).isEqualTo("FFDCFCE7");
        }

        @Test
        @DisplayName("summarises each constraint, then lists its individual violations")
        void summaryThenDetail() {
            Sheet sheet = sheet();
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Constraint");
            Row summary = sheet.getRow(3);
            assertThat(summary.getCell(0).getStringCellValue()).isEqualTo("Unassigned assignment");
            assertThat(summary.getCell(4).getNumericCellValue()).isEqualTo(2.0);

            // Detail block: title, header, then one row per violation.
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue())
                    .isEqualTo("Individual violations");
            assertThat(sheet.getRow(6).getCell(3).getStringCellValue()).isEqualTo("Timeslot");
            assertThat(sheet.getRow(7).getCell(3).getStringCellValue())
                    .isEqualTo("2026-09-21 21:00-22:00");
        }

        @Test
        @DisplayName("SOFT is amber so feasibility is visible without reading the label")
        void levelIsColoured() {
            assertThat(fill(sheet().getRow(3).getCell(1))).isEqualTo("FFFEF9C3");
        }
    }

    @Nested
    @DisplayName("PTO sheet")
    class Pto {

        @Test
        @DisplayName("omitted entirely when no day-off data is supplied")
        void omittedWithoutData() {
            assertThat(workbook.getSheet("PTO")).isNull();
        }

        @Test
        @DisplayName("agents down, dates across, coloured by type and approval status")
        void grid() throws IOException {
            List<AgentDayOffResponse> daysOff = List.of(
                    AgentDayOffResponse.withAgent(UUID.randomUUID(), DAY_ONE, "PTO", "APPROVED",
                            UUID.randomUUID(), "Zoe Last"),
                    AgentDayOffResponse.withAgent(UUID.randomUUID(), DAY_TWO, "MANDATORY", "APPROVED",
                            UUID.randomUUID(), "Zoe Last"),
                    AgentDayOffResponse.withAgent(UUID.randomUUID(), DAY_ONE, "PTO", "REQUESTED",
                            UUID.randomUUID(), "Adam First"));

            byte[] bytes = new ScheduleExportService().exportToExcel(detail(), daysOff);
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet pto = wb.getSheet("PTO");
                assertThat(pto).isNotNull();
                assertThat(pto.getRow(0).getCell(1).getStringCellValue()).isEqualTo("2026-09-21");

                // Agents sorted by name: Adam first.
                Row adam = pto.getRow(1);
                assertThat(adam.getCell(0).getStringCellValue()).isEqualTo("Adam First");
                assertThat(adam.getCell(1).getStringCellValue()).isEqualTo("PTO (req)");
                assertThat(fillOf(adam.getCell(1))).isEqualTo("FFFEFCE8");

                Row zoe = pto.getRow(2);
                assertThat(zoe.getCell(1).getStringCellValue()).isEqualTo("PTO");
                assertThat(fillOf(zoe.getCell(1))).isEqualTo("FFEFF6FF");
                assertThat(zoe.getCell(2).getStringCellValue()).isEqualTo("MANDATORY");
                assertThat(fillOf(zoe.getCell(2))).isEqualTo("FFFEF2F2");
            }
        }

        private String fillOf(Cell cell) {
            XSSFCellStyle style = (XSSFCellStyle) cell.getCellStyle();
            return style.getFillForegroundColorColor() == null
                    ? null : style.getFillForegroundColorColor().getARGBHex();
        }
    }

    @Test
    @DisplayName("an empty schedule produces no allocation sheets and does not throw")
    void emptyScheduleIsSafe() throws IOException {
        ScheduleDetailResponse empty = new ScheduleDetailResponse();
        empty.setDeskName("Empty");
        empty.setAgentSchedule(List.of());

        byte[] bytes = new ScheduleExportService().exportToExcel(empty);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("Overview")).isNotNull();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                assertThat(wb.getSheetAt(i).getSheetName()).doesNotStartWith("Allocation");
            }
        }
    }
    @Nested
    @DisplayName("shift grouping")
    class ShiftGrouping {

        /**
         * A day built with real envelopes, so the ordering rule is actually exercised: the other
         * fixture's agents all carry a null ShiftDescriptor and would sort identically either way.
         */
        private Sheet sheet() throws IOException {
            ScheduleDetailResponse d = new ScheduleDetailResponse();
            d.setDeskName("Vinted");
            d.setStatus("COMPLETED");
            d.setPeriodStartDate(DAY_ONE);
            d.setPeriodEndDate(DAY_ONE);
            d.setIncrementMinutes(60);
            d.setAgentSchedule(List.of(
                    // Deliberately out of both shift order AND name order on the way in.
                    withShift("Zed Early", LocalTime.of(8, 0), LocalTime.of(17, 0)),
                    withShift("Nora Late", LocalTime.of(15, 0), LocalTime.MIDNIGHT),
                    offRoster("Bob NoShift"),
                    withShift("Ann Early", LocalTime.of(8, 0), LocalTime.of(17, 0))));
            byte[] xlsx = new ScheduleExportService().exportToExcel(d, List.of());
            return new XSSFWorkbook(new ByteArrayInputStream(xlsx))
                    .getSheet("Allocation " + DAY_ONE);
        }

        private AgentScheduleEntry withShift(String name, LocalTime start, LocalTime end) {
            ShiftDescriptor sd = new ShiftDescriptor(UUID.randomUUID(), "T", start, end, 180, 60);
            return new AgentScheduleEntry(UUID.randomUUID(), name, DAY_ONE, start, end,
                    new BigDecimal("1.0"),
                    List.of(new AssignmentDetail(UUID.randomUUID(), start, start.plusHours(1),
                            "Security and Item Quality", "PRIMARY")),
                    List.of(), sd, null);
        }

        private AgentScheduleEntry offRoster(String name) {
            return new AgentScheduleEntry(UUID.randomUUID(), name, DAY_ONE, LocalTime.of(9, 0),
                    LocalTime.of(10, 0), new BigDecimal("1.0"),
                    List.of(new AssignmentDetail(UUID.randomUUID(), LocalTime.of(9, 0),
                            LocalTime.of(10, 0), "Security and Item Quality", "PRIMARY")),
                    List.of(), null, null);
        }

        @Test
        @DisplayName("rows group by envelope in start order, names sorting within a shift")
        void groupedByShiftThenName() throws IOException {
            Sheet s = sheet();
            assertThat(List.of(
                    s.getRow(1).getCell(0).getStringCellValue(),
                    s.getRow(2).getCell(0).getStringCellValue(),
                    s.getRow(3).getCell(0).getStringCellValue(),
                    s.getRow(4).getCell(0).getStringCellValue()))
                    .containsExactly("Ann Early", "Zed Early", "Nora Late", "Bob NoShift");
        }

        @Test
        @DisplayName("the Shift column names the envelope, and says so when there is none")
        void shiftColumnCarriesTheEnvelope() throws IOException {
            Sheet s = sheet();
            assertThat(s.getRow(1).getCell(1).getStringCellValue()).isEqualTo("08:00-17:00");
            assertThat(s.getRow(2).getCell(1).getStringCellValue()).isEqualTo("08:00-17:00");
            // Midnight reads 00:00 in an end position, matching the template's own name.
            assertThat(s.getRow(3).getCell(1).getStringCellValue()).isEqualTo("15:00-00:00");
            // Never blank: an agent-day with no envelope is the opposite of "nothing to say".
            assertThat(s.getRow(4).getCell(1).getStringCellValue()).isEqualTo("No shift assigned");
        }

        @Test
        @DisplayName("agent-days with no envelope sort last, where they can be seen")
        void offRosterSortsLast() throws IOException {
            Sheet s = sheet();
            assertThat(s.getRow(4).getCell(1).getStringCellValue()).isEqualTo("No shift assigned");
            assertThat(s.getRow(5).getCell(0).getStringCellValue()).startsWith("Total:");
        }
    }

}
