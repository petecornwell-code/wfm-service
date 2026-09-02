package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Timeslot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 15 plan 15-11, Task 2 (gap closure, G-15-10, D1 half) — proves {@link SolverService
 * #requireShiftEnvelopeSeatSupply} makes in-envelope seat supply a CHECKED PRECONDITION of every
 * shift-mode solve: a shortfall or an hours-mismatch refuses before any solving occurs, a
 * healthy desk is never refused, and a slot desk is never evaluated by the gate at all.
 *
 * <p>Shared desk shape: operating 08:00-17:00 hourly, one shift template "Day" 08:00-17:00 with
 * a single 60m band at 12:00-13:00 (net 8h), so the covered non-break hours are 08:00-11:00 and
 * 13:00-16:00 (8 slots).
 */
class ShiftEnvelopeSupplyGateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7); // Monday
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final LocalTime TEMPLATE_START = LocalTime.of(8, 0);
    private static final LocalTime TEMPLATE_END = LocalTime.of(17, 0);

    // ------------------------------------------------------------------
    //  Fixture builders
    // ------------------------------------------------------------------

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static List<Timeslot> operatingWindow() {
        List<Timeslot> slots = new ArrayList<>();
        for (LocalTime t = OPEN; t.isBefore(CLOSE); t = t.plusHours(1)) {
            slots.add(timeslot(t));
        }
        return slots;
    }

    private static ShiftTemplate template(LocalTime start, LocalTime end, LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName("Day");
        t.setStartTime(start);
        t.setEndTime(end);
        t.setEffectiveFrom(effectiveFrom);
        t.setEffectiveTo(effectiveTo);
        return t;
    }

    /** Offset 240 (08:00 + 240m = 12:00), duration 60 -> break is 12:00-13:00. */
    private static ShiftTemplateBreakBand band(ShiftTemplate t) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(t);
        b.setOffsetMinutes(240);
        b.setDurationMinutes(60);
        return b;
    }

    private static Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setActive(true);
        return a;
    }

    private static AgentDayConfig dayConfig(UUID agentId, BigDecimal hours) {
        return new AgentDayConfig(agentId, DAY, hours, 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 100, 70);
    }

    private static AgentShiftAssignment shiftRow(Agent a, AgentDayConfig dc, List<ShiftBandPair> pairs) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(UUID.randomUUID());
        row.setAgent(a);
        row.setDate(DAY);
        row.setDayConfig(dc);
        row.setDeskShiftBandPairs(pairs);
        return row;
    }

    private static AgentAssignment seat(Timeslot ts) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        return a;
    }

    /** One seat at every covered non-break hour (8 hours: 08:00-11:00, 13:00-16:00) for {@code n} agents. */
    private static List<AgentAssignment> fullSupplySeats(List<Timeslot> window, int perSlot) {
        List<AgentAssignment> seats = new ArrayList<>();
        for (Timeslot ts : window) {
            LocalTime start = ts.getStartTime();
            boolean covered = !start.equals(LocalTime.of(12, 0));
            if (!covered) continue;
            for (int i = 0; i < perSlot; i++) {
                seats.add(seat(ts));
            }
        }
        return seats;
    }

    // ------------------------------------------------------------------
    //  Fixture builders for the weekend-overcount red-proof (G-15-21) -- these vary the
    //  timeslot/row DATE, which the shared fixtures above hard-code to DAY (a Monday).
    // ------------------------------------------------------------------

    private static Timeslot timeslotOnDate(LocalDate date, LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static AgentShiftAssignment shiftRowOnDate(LocalDate date, Agent a, AgentDayConfig dc,
            List<ShiftBandPair> pairs) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(UUID.randomUUID());
        row.setAgent(a);
        row.setDate(date);
        row.setDayConfig(dc);
        row.setDeskShiftBandPairs(pairs);
        return row;
    }

    private record WeekendFixture(List<ShiftBandPair> pairs, List<Timeslot> window,
            Map<LocalTime, Timeslot> bySlotStart) {}

    /**
     * Two templates sharing one desk: a weekday-only template (08:00-17:00, break 12:00-13:00,
     * net 8h) valid ONLY Mon-Fri, and a weekend-valid template (10:00-19:00, break 14:00-15:00,
     * net 8h) valid ONLY Sat/Sun. Their clock-time coverage overlaps -- the weekday template's
     * time-only footprint (08,09,10,11,13,14,15,16) unions with the weekend template's
     * (10,11,12,13,15,16,17,18) to cover the ENTIRE 08:00-19:00 operating window, which is
     * exactly the shape the calendar-blind predicate over-counts on a weekend date.
     */
    private static WeekendFixture weekdayVsWeekendFixture(LocalDate date) {
        ShiftTemplate weekdayOnly = template(LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalDate.of(2020, 1, 1), null);
        weekdayOnly.setName("Weekday");
        weekdayOnly.setValidWeekdays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        ShiftBandPair weekdayPair = new ShiftBandPair(weekdayOnly, band(weekdayOnly));

        ShiftTemplate weekend = template(LocalTime.of(10, 0), LocalTime.of(19, 0),
                LocalDate.of(2020, 1, 1), null);
        weekend.setName("Weekend");
        weekend.setValidWeekdays(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        ShiftTemplateBreakBand weekendBand = new ShiftTemplateBreakBand();
        weekendBand.setId(UUID.randomUUID());
        weekendBand.setShiftTemplate(weekend);
        weekendBand.setOffsetMinutes(240); // 10:00 + 240m = 14:00
        weekendBand.setDurationMinutes(60);
        ShiftBandPair weekendPair = new ShiftBandPair(weekend, weekendBand);

        List<Timeslot> window = new ArrayList<>();
        for (LocalTime t = LocalTime.of(8, 0); t.isBefore(LocalTime.of(19, 0)); t = t.plusHours(1)) {
            window.add(timeslotOnDate(date, t));
        }
        Map<LocalTime, Timeslot> bySlotStart = new HashMap<>();
        for (Timeslot ts : window) {
            bySlotStart.put(ts.getStartTime(), ts);
        }
        return new WeekendFixture(List.of(weekendPair, weekdayPair), window, bySlotStart);
    }

    // ------------------------------------------------------------------
    //  Test 1 -- refusal on shortfall
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a shortfall between contracted demand and library-covered seat supply refuses before solving")
    void refusesOnShortfall() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(a2, dc2, List.of(pair)));

        // Only ONE agent's worth of seats exist inside the envelope (8 slots), but TWO
        // agent-days are contracted 8h each -- 16 slots required, 8 supplied.
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).isNotEmpty();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains(DAY.toString());
                        assertThat(d.message()).containsIgnoringCase("8");
                    });
                });
    }

    // ------------------------------------------------------------------
    //  Test 2 -- message names the levers
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: the shortfall refusal names the over-allocation limit and every other lever")
    void messageNamesTheLevers() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(a2, dc2, List.of(pair)));
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 130, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    String combined = details.stream().map(ErrorDetail::message)
                            .reduce("", (x, y) -> x + " " + y);
                    assertThat(combined).as("names the desk's current over-allocation limit")
                            .contains("130");
                    assertThat(combined).as("mentions correcting the demand forecast")
                            .containsIgnoringCase("forecast");
                    assertThat(combined).as("mentions reducing rostered hours")
                            .containsIgnoringCase("reduce");
                    assertThat(combined).as("mentions changing the library")
                            .containsIgnoringCase("library");
                });
    }

    // ------------------------------------------------------------------
    //  Test 3 -- no false refusal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a desk whose library-covered supply meets contracted demand is NOT refused")
    void noFalseRefusalWhenSupplyMeetsDemand() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(a2, dc2, List.of(pair)));

        // Two agents' worth of seats -- supply meets demand exactly.
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 2));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> {
            SolverService.requireShiftEnvelopeSeatSupply(
                    SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null);
            throw new RuntimeException("SENTINEL: gate returned normally");
        }).hasMessage("SENTINEL: gate returned normally");
    }

    // ------------------------------------------------------------------
    //  Test 4 -- hours matching no live pair
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: an agent whose effective hours match no live pair is refused by name")
    void refusesAgentWhoseHoursMatchNoLivePair() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent oddOne = agent("Odd One");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        // 7.50h matches no live pair's net hours (8.00h).
        AgentDayConfig oddDc = dayConfig(oddOne.getId(), new BigDecimal("7.50"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(oddOne, oddDc, List.of(pair)));

        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 2));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains("Odd One");
                        assertThat(d.message()).contains(DAY.toString());
                        assertThat(d.message()).contains("7.50");
                    });
                });
    }

    // ------------------------------------------------------------------
    //  Uncovered weekday -- refuse up front, and blame the RIGHT thing
    //
    //  Narrowing the value range by validWeekdays widens the empty-range case: an agent rostered
    //  on a day no template lists now has nothing to take. Without this branch the gate would fall
    //  through to the "hours match no live pair" message and tell the operator to change an
    //  agent's contracted hours, when the actual defect is an uncovered weekday. Getting the
    //  DIAGNOSIS right is the point of this test, not merely that it throws.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a weekday no template covers is refused up front, naming the weekday")
    void refusesWeekdayNoTemplateCovers() {
        // DAY is a Monday; this template is valid only at the weekend, so nothing covers Monday.
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        t.setValidWeekdays(java.util.EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        // Hours match the pair's net hours EXACTLY — so if the gate blamed hours it would be wrong.
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRow(a1, dc1, List.of(pair)));
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains("No shift template applies on");
                        assertThat(d.message()).contains(DAY.toString());
                        assertThat(d.message()).contains("Monday");
                    });
                    // It must NOT mis-diagnose this as an hours mismatch — the agent's hours are
                    // a perfect match and telling the operator to change them would send them
                    // chasing the wrong fix.
                    assertThat(details).noneSatisfy(d ->
                            assertThat(d.message()).contains("matches no live shift template's net hours"));
                });
    }

    @Test
    @DisplayName("SHIFT: the template's own valid weekday is not refused")
    void doesNotRefuseWhenTemplateCoversThatWeekday() {
        // Non-regression control for the test above: same desk, same agent, same hours — the only
        // change is that the template now lists the day being solved.
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        t.setValidWeekdays(java.util.EnumSet.of(DAY.getDayOfWeek()));
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRow(a1, dc1, List.of(pair)));
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> {
            SolverService.requireShiftEnvelopeSeatSupply(
                    SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null);
            throw new RuntimeException("SENTINEL: gate returned normally");
        }).hasMessage("SENTINEL: gate returned normally");
    }

    // ------------------------------------------------------------------
    //  Test 5 -- slot desk untouched
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLOT: the gate is structurally unreachable -- the same shortfall shape still solves")
    void slotDeskNeverEvaluated() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(a2, dc2, List.of(pair)));
        // Same shortfall shape as Test 1 (only one agent's worth of seats).
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> {
            SolverService.requireShiftEnvelopeSeatSupply(
                    SchedulingMode.SLOT, rows, List.of(pair), window, assignments, 100, warnings, null);
            throw new RuntimeException("SENTINEL: gate returned normally");
        }).hasMessage("SENTINEL: gate returned normally");
    }

    // ------------------------------------------------------------------
    //  Test 6 -- empty library (wholly retired/upcoming)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a library entirely UPCOMING/RETIRED for the date is refused distinctly from a numeric shortfall")
    void refusesWhollyRetiredLibraryDistinctFromShortfall() {
        // Template retired before DAY.
        ShiftTemplate retired = template(TEMPLATE_START, TEMPLATE_END,
                LocalDate.of(2020, 1, 1), DAY.minusDays(1));
        ShiftBandPair pair = new ShiftBandPair(retired, band(retired));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRow(a1, dc1, List.of(pair)));

        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 5));

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d ->
                            assertThat(d.message()).containsIgnoringCase("no live shift"));
                    assertThat(details).noneSatisfy(d ->
                            assertThat(d.message()).contains("A-1"));
                    // Retired/weekday-invalid interaction (Task 1): one error for this date, not
                    // that distinct message PLUS a numeric shortfall restating the same root
                    // cause -- date-aware coverage makes librarySupplySlots collapse to zero for
                    // a wholly-retired date, which would otherwise ALSO trip the shortfall branch.
                    assertThat(details)
                            .as("exactly one error for this date -- not the distinct message plus a restated numeric shortfall")
                            .hasSize(1);
                });
    }

    // ------------------------------------------------------------------
    //  Test 7 -- advisory, non-blocking
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a passing desk with a thin covered timeslot records a non-blocking warning and still solves")
    void advisoryOnThinTimeslotDoesNotBlock() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRow(a1, dc1, List.of(pair)));

        // 8 covered slots; give every one 2 seats EXCEPT 09:00 which gets exactly 1 -- still
        // meets the 1-agent-day (8 slot) contracted demand, so the gate passes, but 09:00 is
        // the pinch point.
        List<AgentAssignment> assignments = new ArrayList<>();
        Timeslot thin = null;
        for (Timeslot ts : window) {
            LocalTime start = ts.getStartTime();
            if (start.equals(LocalTime.of(12, 0))) continue; // break, uncovered
            int seats = start.equals(LocalTime.of(9, 0)) ? 1 : 2;
            if (start.equals(LocalTime.of(9, 0))) thin = ts;
            for (int i = 0; i < seats; i++) {
                assignments.add(seat(ts));
            }
        }
        assertThat(thin).isNotNull();

        List<String> warnings = new ArrayList<>();
        SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null);

        assertThat(warnings)
                .as("a warning is recorded naming the tightest covered timeslot and its seat count")
                .anySatisfy(w -> {
                    assertThat(w).contains("09:00");
                    assertThat(w).contains("1");
                });
    }

    // ------------------------------------------------------------------
    //  Test 8 -- weekend over-count red-proof (G-15-21), the decisive case: exact pre/post figures
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a weekday-only template's clock-time coverage does not inflate weekend supply -- exact pre/post figures")
    void refusesWeekendOvercountFromWeekdayOnlyTemplate() {
        LocalDate saturday = LocalDate.of(2026, 9, 5);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);

        WeekendFixture f = weekdayVsWeekendFixture(saturday);

        Agent a1 = agent("A-1");
        // 8.00h matches ONLY the weekend template's net hours (8h) -- the weekday template is
        // ineligible on a Saturday regardless of hours, so this row's sole legal pair is the
        // weekend one, and the row is never "unassignable" (this stays a pure numeric-shortfall
        // case, not the distinct retired/weekday-invalid message).
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRowOnDate(saturday, a1, dc1, f.pairs()));

        // Exactly 8 seats total: 3 sit ONLY on hours the weekday-only template's clock-time
        // coverage reaches (08:00, 09:00, 14:00 -- the weekend template never covers these), and
        // 5 sit on hours the weekend template genuinely covers (10:00, 11:00, 12:00, 13:00,
        // 15:00). 16:00/17:00/18:00 (weekend-covered, weekday-blind) carry NO seats.
        List<AgentAssignment> assignments = new ArrayList<>();
        for (LocalTime t : List.of(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(14, 0),
                LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
                LocalTime.of(15, 0))) {
            assignments.add(seat(f.bySlotStart().get(t)));
        }

        // PRE-FIX (calendar-blind union of BOTH pairs' clock-time coverage, ignoring weekday
        // validity): the two templates' time-only coverage unions to the FULL 11-hour window, so
        // all 8 seats fall on "covered" hours -- librarySupplySlots = 8 == contractedSlots(8),
        // and the gate PASSES today (before this fix).
        // POST-FIX (date-aware -- only the weekend-valid pair counts on a Saturday): only the 5
        // seats at 10/11/12/13/15 count. librarySupplySlots = 5, a shortfall of 3 against 8.
        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, f.pairs(), f.window(), assignments, 100, warnings, null))
                .as("THE FIX: a weekday-only template's clock-time coverage no longer inflates "
                        + "weekend supply -- before this fix the identical fixture passed the gate")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains(saturday.toString());
                        assertThat(d.message())
                                .as("exact post-fix supply figure -- 5 slots reached, not 8")
                                .contains("only reaches 5 slot(s)");
                        assertThat(d.message())
                                .as("exact shortfall figure -- 3 slots, not merely nonzero")
                                .contains("shortfall of 3 slot(s)");
                    });
                });
    }

    // ------------------------------------------------------------------
    //  Test 9 -- advisory coherence: the tightest-hour advisory never names an hour reachable
    //  only by a weekday-invalid template (the incoherent "0 seat(s)" shape cannot recur)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: the tightest-hour advisory never names an hour only a weekday-invalid template reaches -- weekend date")
    void advisoryNeverNamesAnHourOnlyAWeekdayInvalidTemplateReaches() {
        LocalDate saturday = LocalDate.of(2026, 9, 5);
        WeekendFixture f = weekdayVsWeekendFixture(saturday);

        Agent a1 = agent("A-1");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(shiftRowOnDate(saturday, a1, dc1, f.pairs()));

        // Every weekend-covered slot gets exactly 1 seat -- meets the 8-slot contracted demand
        // exactly, so the gate passes. The weekday-only clock-time-covered hours (08:00, 09:00,
        // 14:00) get ZERO seats -- before this fix these could be counted as "covered" and
        // mistaken for the tightest hour at a nonsensical 0 seats (the live symptom: "tightest at
        // 08:00-09:00 with 0 seat(s)").
        List<AgentAssignment> assignments = new ArrayList<>();
        for (LocalTime t : List.of(LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
                LocalTime.of(13, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0),
                LocalTime.of(18, 0))) {
            assignments.add(seat(f.bySlotStart().get(t)));
        }

        List<String> warnings = new ArrayList<>();
        SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, f.pairs(), f.window(), assignments, 100, warnings, null);

        assertThat(warnings)
                .as("a genuinely tight covered hour exists, so the advisory must fire")
                .isNotEmpty();
        assertThat(warnings)
                .as("never names an hour only the weekday-invalid template reaches on the clock -- "
                        + "the incoherent zero-seat advisory shape cannot recur")
                .allSatisfy(w -> {
                    assertThat(w).doesNotContain("08:00-09:00");
                    assertThat(w).doesNotContain("09:00-10:00");
                    assertThat(w).doesNotContain("14:00-15:00");
                });
        assertThat(warnings)
                .as("the tightest genuinely-covered hour is named, with its real 1-seat count")
                .anySatisfy(w -> assertThat(w).contains("1 seat(s)"));
    }

    // ------------------------------------------------------------------
    //  Tests 10-12 -- G-15-24: the shortfall remedy reads the desk's LIVE unassignedAssignmentWeight
    //  rather than advising blind. Same shortfall shape as Test 1 (2 agents x 8h contracted = 16
    //  slots demand, 8 slots supplied), varied only by the weights argument.
    //
    //  Plan 15-20 note: this fixture's single shared pair forces BOTH agents at every one of the
    //  8 covered hours (zero slack, one eligible pair each), and only 1 seat exists at each of
    //  those hours -- so the new per-hour forced-occupancy check (G-15-25/G-15-31) now ALSO fires
    //  here, alongside the pre-existing day-wide shortfall. That is the correct, intended
    //  interaction (both checks accumulate into the same list -- 15-20-PLAN.md Task 1), not a
    //  regression: this fixture genuinely IS short both in total and at every hour. These tests
    //  isolate and pin ONLY the day-wide message's exact wording (the thing they exist to prove),
    //  via dayWideShortfallDetail below, rather than asserting the full detail list's size.
    // ------------------------------------------------------------------

    private static List<ErrorDetail> shortfallDetails(ConstraintWeights weights) {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dc1, List.of(pair)), shiftRow(a2, dc2, List.of(pair)));
        List<AgentAssignment> assignments = new ArrayList<>(fullSupplySeats(window, 1));

        java.util.concurrent.atomic.AtomicReference<List<ErrorDetail>> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, weights))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> captured.set(((PreSolveValidationException) ex).getDetails()));
        return captured.get();
    }

    /**
     * Finds the ONE day-wide shortfall detail (identified by its stable message prefix, unique to
     * that branch) among possibly several details -- plan 15-20's per-hour check now also fires on
     * this same fixture (see the class-level note above) and must not be mistaken for this one.
     */
    private static ErrorDetail dayWideShortfallDetail(List<ErrorDetail> details) {
        List<ErrorDetail> matches = details.stream()
                .filter(d -> d.message().startsWith("On " + DAY + ", rostered agent-days need"))
                .toList();
        assertThat(matches)
                .as("exactly one day-wide shortfall detail for this date, distinct from any "
                        + "per-hour forced-occupancy detail")
                .hasSize(1);
        return matches.get(0);
    }

    private static final String EXPECTED_DEFAULT_MESSAGE =
            "On " + DAY + ", rostered agent-days need 16 slot(s) (16.00h) inside the shift "
                    + "library's live envelopes, but the library only reaches 8 slot(s) (8.00h) "
                    + "there — a shortfall of 8 slot(s) (8.00h). On a shift-scheduled desk an "
                    + "agent works exactly their assigned shift, so this cannot be resolved by "
                    + "solving for longer. To fix it: raise the desk's over-allocation limit "
                    + "(currently 100%), correct the demand forecast for the hours the library "
                    + "covers, reduce rostered hours for " + DAY + ", or change the library so "
                    + "its envelopes sit over demand-bearing hours.";

    @Test
    @DisplayName("SHIFT: default (soft) unassignedAssignmentWeight -- the shortfall message is byte-identical to today's, pinned by literal equality")
    void defaultWeightsMessageIsByteIdenticalToBeforeThisPlan() {
        ConstraintWeights defaultWeights = new ConstraintWeights();
        assertThat(defaultWeights.getUnassignedAssignmentWeight().hardScore())
                .as("sanity: the shipped default's hard component is zero -- soft, not hard")
                .isZero();

        List<ErrorDetail> details = shortfallDetails(defaultWeights);
        assertThat(dayWideShortfallDetail(details).message())
                .as("literal equality -- not a substring match, so a drifting message fails loudly")
                .isEqualTo(EXPECTED_DEFAULT_MESSAGE);
    }

    @Test
    @DisplayName("SHIFT: null weights (existing solver-package callers) fall back to the default wording and never throw NPE")
    void nullWeightsFallBackToDefaultWording() {
        List<ErrorDetail> details = shortfallDetails(null);
        assertThat(dayWideShortfallDetail(details).message())
                .as("null weights must read exactly like the shipped default, not a third wording")
                .isEqualTo(EXPECTED_DEFAULT_MESSAGE);
    }

    @Test
    @DisplayName("SHIFT: hard unassignedAssignmentWeight -- the ceiling remedy is withdrawn, the consequence is named, and the percentage is still reported")
    void hardUnassignedWeightWithdrawsCeilingRemedyAndNamesConsequence() {
        ConstraintWeights hardWeights = new ConstraintWeights();
        hardWeights.setUnassignedAssignmentWeight(HardSoftScore.ofHard(10_000));

        List<ErrorDetail> details = shortfallDetails(hardWeights);
        String message = dayWideShortfallDetail(details).message();

        assertThat(message)
                .as("the ceiling suggestion is withdrawn, not merely reworded")
                .doesNotContain("raise the desk's over-allocation limit");
        assertThat(message)
                .as("the current percentage is still reported even though raising it is not advised")
                .contains("currently 100%");
        assertThat(message)
                .as("names the forecast lever")
                .containsIgnoringCase("forecast");
        assertThat(message)
                .as("names the roster lever")
                .containsIgnoringCase("rostered hours");
        assertThat(message)
                .as("names the library lever")
                .containsIgnoringCase("library");
        assertThat(message)
                .as("names the consequence: a hard violation at the desk's own configured weight, with the value")
                .containsIgnoringCase("hard violation")
                .contains("10000");
    }

    // ------------------------------------------------------------------
    //  Tests 13-14 -- G-15-25/G-15-31 (plan 15-20): supply computed against each agent-day's OWN
    //  eligible pairs, not a desk-wide anyMatch union; a per-timeslot forced-occupancy check
    //  catches what the day-wide sum structurally cannot see.
    // ------------------------------------------------------------------

    /**
     * One 9h template ({@code TEMPLATE_START}-{@code TEMPLATE_END}) with {@code
     * breakOffsetsMinutes.length} distinct single-band pairs, each excluding a DIFFERENT 1-hour
     * clock window -- every pair has identical net hours (8.00h), so an 8h-contracted agent is
     * eligible for ALL of them. Three or more distinct offsets already saturate the desk-wide
     * union to the full envelope (every hour is excluded by AT MOST one band, so some OTHER band
     * still covers it) -- the "per-template band count already saturates the desk-wide union"
     * shape G-15-25 is about.
     */
    private static List<ShiftBandPair> saturatedUnionPairs(int... breakOffsetsMinutes) {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        List<ShiftBandPair> pairs = new ArrayList<>();
        for (int offset : breakOffsetsMinutes) {
            ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
            b.setId(UUID.randomUUID());
            b.setShiftTemplate(t);
            b.setOffsetMinutes(offset);
            b.setDurationMinutes(60);
            pairs.add(new ShiftBandPair(t, b));
        }
        return pairs;
    }

    private static List<ErrorDetail> captureDetails(List<AgentShiftAssignment> rows,
            List<ShiftBandPair> pairs, List<Timeslot> window, List<AgentAssignment> assignments) {
        java.util.concurrent.atomic.AtomicReference<List<ErrorDetail>> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, pairs, window, assignments, 100, warnings, null))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> captured.set(((PreSolveValidationException) ex).getDetails()));
        return captured.get();
    }

    @Test
    @DisplayName("SHIFT: G-15-25 red-proof -- adding edge bands to an already-saturated union changes the per-agent-day forced count even though the desk-wide union figure does not")
    void bandCompositionChangesForcedCountButNotTheSaturatedUnion() {
        List<ShiftBandPair> threeBands = saturatedUnionPairs(180, 240, 300); // breaks 11-12, 12-13, 13-14
        List<ShiftBandPair> fiveBands = saturatedUnionPairs(180, 240, 300, 0, 480); // + edges 08-09, 16-17

        List<Timeslot> window = operatingWindow();
        Agent a1 = agent("A-1");
        AgentDayConfig dc1 = dayConfig(a1.getId(), new BigDecimal("8.00"));

        // THE DECISIVE FIGURE, computed directly (not inferred from whether the gate throws): at
        // the boundary hour 08:00, the agent is forced under the 3-band library (every one of its
        // 3 eligible pairs -- all zero-slack -- covers 08:00) but NOT under the 5-band library
        // (the added edge band's break falls exactly on 08:00, so THAT pair no longer covers it,
        // and "forced" requires EVERY eligible pair to cover the hour).
        Timeslot eight = window.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(8, 0)))
                .findFirst().orElseThrow();
        Map<UUID, Long> threeBandForced = SolverService.forcedAgentDaysByTimeslotId(
                List.of(shiftRow(a1, dc1, threeBands)), window);
        Map<UUID, Long> fiveBandForced = SolverService.forcedAgentDaysByTimeslotId(
                List.of(shiftRow(a1, dc1, fiveBands)), window);

        long forcedWithThreeBands = threeBandForced.getOrDefault(eight.getId(), 0L);
        long forcedWithFiveBands = fiveBandForced.getOrDefault(eight.getId(), 0L);
        assertThat(forcedWithThreeBands)
                .as("3 bands sharing this envelope: every eligible pair covers 08:00, so the agent is forced there")
                .isEqualTo(1L);
        assertThat(forcedWithFiveBands)
                .as("THE FIX: adding one edge band whose break falls on 08:00 changes this figure "
                        + "-- a desk-wide union, already saturated at 3 bands, could never see this")
                .isEqualTo(0L);
        assertThat(forcedWithThreeBands)
                .as("two DIFFERENT numbers from two runs differing ONLY in band composition -- a "
                        + "test asserting merely that the gate refuses, or that the figures are "
                        + "unchanged, would not distinguish this from the old behaviour")
                .isNotEqualTo(forcedWithFiveBands);

        // THE DESK-WIDE UNION STAYS SATURATED (byte-identical), in both configurations -- the
        // exact "byte-identical gate output" symptom G-15-25 reports, reproduced here as the
        // day-wide shortfall message computed by the shipped gate staying literally identical.
        Agent a2 = agent("A-2");
        AgentDayConfig dc2 = dayConfig(a2.getId(), new BigDecimal("8.00"));
        List<AgentAssignment> oneSeatPerEnvelopeHour = new ArrayList<>();
        for (Timeslot ts : window) {
            oneSeatPerEnvelopeHour.add(seat(ts));
        }

        String threeBandMessage = dayWideShortfallDetail(captureDetails(
                List.of(shiftRow(a1, dc1, threeBands), shiftRow(a2, dc2, threeBands)),
                threeBands, window, oneSeatPerEnvelopeHour)).message();
        String fiveBandMessage = dayWideShortfallDetail(captureDetails(
                List.of(shiftRow(a1, dc1, fiveBands), shiftRow(a2, dc2, fiveBands)),
                fiveBands, window, oneSeatPerEnvelopeHour)).message();

        assertThat(threeBandMessage)
                .as("the day-wide shortfall figure is BYTE-IDENTICAL across both band compositions "
                        + "-- the desk-wide union was already saturated at 3 bands, so it cannot "
                        + "see the 2 edge bands added on top of it")
                .isEqualTo(fiveBandMessage);
    }

    @Test
    @DisplayName("SHIFT: G-15-31 -- a distribution-blind shortfall the day-wide sum misses is caught by the per-hour forced-occupancy check, naming date/hour/forced-count/seat-count")
    void perHourForcedOccupancyRefusesWhatTheDayWideSumMisses() {
        ShiftTemplate t = template(TEMPLATE_START, TEMPLATE_END, LocalDate.of(2020, 1, 1), null);
        ShiftBandPair pair = new ShiftBandPair(t, band(t));
        List<Timeslot> window = operatingWindow();

        Agent a1 = agent("A-1");
        Agent a2 = agent("A-2");
        Agent a3 = agent("A-3");
        List<AgentShiftAssignment> rows = List.of(
                shiftRow(a1, dayConfig(a1.getId(), new BigDecimal("8.00")), List.of(pair)),
                shiftRow(a2, dayConfig(a2.getId(), new BigDecimal("8.00")), List.of(pair)),
                shiftRow(a3, dayConfig(a3.getId(), new BigDecimal("8.00")), List.of(pair)));

        // Day-wide supply is ABUNDANT (30 slots against 24 contracted = 3 agents x 8h) -- the
        // pre-existing day-wide sum PASSES this desk -- but one hour (13:00) carries only 2 seats
        // against the 3 agent-days structurally forced onto it (single shared pair, zero slack).
        List<AgentAssignment> assignments = new ArrayList<>();
        for (Timeslot ts : window) {
            LocalTime start = ts.getStartTime();
            if (start.equals(LocalTime.of(12, 0))) continue; // break, uncovered
            int seats = start.equals(LocalTime.of(13, 0)) ? 2 : 4;
            for (int i = 0; i < seats; i++) {
                assignments.add(seat(ts));
            }
        }

        List<String> warnings = new ArrayList<>();
        assertThatThrownBy(() -> SolverService.requireShiftEnvelopeSeatSupply(
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings, null))
                .as("THE FIX: a day-wide-abundant desk with one genuinely thin hour is no longer "
                        + "waved through -- before this plan only the day-wide sum was checked")
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details)
                            .as("the day-wide sum genuinely PASSES here (30 >= 24) -- this refusal "
                                    + "comes from nowhere else")
                            .noneMatch(d -> d.message().startsWith(
                                    "On " + DAY + ", rostered agent-days need"));
                    assertThat(details).anySatisfy(d -> {
                        assertThat(d.message()).contains(DAY.toString());
                        assertThat(d.message())
                                .as("names the hour")
                                .contains("13:00-14:00");
                        assertThat(d.message())
                                .as("names the forced count")
                                .contains("3 rostered agent-day(s)");
                        assertThat(d.message())
                                .as("names the seat count")
                                .contains("only 2 seat(s)");
                    });
                });
    }
}
