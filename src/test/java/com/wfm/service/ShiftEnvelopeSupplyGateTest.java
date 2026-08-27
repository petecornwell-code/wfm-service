package com.wfm.service;

import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Timeslot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings))
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
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 130, warnings))
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
                    SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings);
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
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings))
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
                    SchedulingMode.SLOT, rows, List.of(pair), window, assignments, 100, warnings);
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
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    List<ErrorDetail> details = ((PreSolveValidationException) ex).getDetails();
                    assertThat(details).anySatisfy(d ->
                            assertThat(d.message()).containsIgnoringCase("no live shift"));
                    assertThat(details).noneSatisfy(d ->
                            assertThat(d.message()).contains("A-1"));
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
                SchedulingMode.SHIFT, rows, List.of(pair), window, assignments, 100, warnings);

        assertThat(warnings)
                .as("a warning is recorded naming the tightest covered timeslot and its seat count")
                .anySatisfy(w -> {
                    assertThat(w).contains("09:00");
                    assertThat(w).contains("1");
                });
    }
}
