package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHARACTERISING test (Phase 15 UAT gap, debug session
 * {@code .planning/debug/min-staffing-seats-zero-demand.md}) — records what
 * {@link SolverService#expandMinimumStaffingSeats} actually does on a SHIFT-scheduled desk.
 *
 * <p>These are deliberately NOT written as "should" assertions. They assert the CURRENT
 * behaviour so the defect is pinned to an executable fact rather than to prose. The
 * {@code @DisplayName}s name the behaviour and the reason it is wrong on a shift desk.
 *
 * <p>The existing {@link MinimumStaffingSeatsTest} covers the same method entirely in slot-mode
 * shapes; {@code ShiftModeFixtures} (the shared shift-mode solver fixture) builds every template
 * spanning the FULL operating window and never calls this method at all, so no test in the suite
 * could observe a filler seat landing outside a shift envelope.
 */
class ShiftModeMinimumStaffingSeatGapTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 1, 12);

    /** The desk operates 08:00-21:00; the shift library only reaches 12:00-21:00. */
    private static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(21, 0);
    private static final LocalTime LATE_START = LocalTime.of(12, 0);

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
        for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusHours(1)) {
            slots.add(timeslot(t));
        }
        return slots;
    }

    private static Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private static ShiftTemplate lateTemplate() {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Late");
        t.setStartTime(LATE_START);
        t.setEndTime(OPERATING_END);
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    private static ShiftTemplateBreakBand band(ShiftTemplate t, int offsetMinutes, int durationMinutes) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(t);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(durationMinutes);
        return b;
    }

    private static StaffingRequirement requirement(Timeslot ts, Specialization s, int ftes) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(UUID.randomUUID());
        sr.setTimeslot(ts);
        sr.setSpecialization(s);
        sr.setRequiredFTEs(ftes);
        return sr;
    }

    private static AgentAssignment seat(Timeslot ts, Specialization s) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setRequiredSpecialization(s);
        return a;
    }

    @Test
    @DisplayName("CURRENT: a filler seat is created on every zero-demand hour that NO shift envelope reaches")
    void fillerSeatsLandOutsideEveryShiftEnvelope() {
        Specialization english = spec("English");
        ShiftTemplate late = lateTemplate();
        ShiftBandPair onlyPairOnTheDesk = new ShiftBandPair(late, null);

        List<Timeslot> window = operatingWindow();
        // Demand exists only where the shift library reaches: 12:00 onward. 08:00-11:00 is the
        // "0 hour slot" the operator reported.
        List<StaffingRequirement> demand = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        for (Timeslot ts : window) {
            if (ts.getStartTime().isBefore(LATE_START)) {
                continue;
            }
            demand.add(requirement(ts, english, 10));
            demandSeats.add(seat(ts, english));
        }

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, window, demandSeats, demand, List.of(english));

        assertThat(extra)
                .as("one filler seat per bare hour, 08:00-11:00")
                .hasSize(4);
        assertThat(extra).extracting(a -> a.getTimeslot().getStartTime())
                .containsExactlyInAnyOrder(LocalTime.of(8, 0), LocalTime.of(9, 0),
                        LocalTime.of(10, 0), LocalTime.of(11, 0));

        // The load-bearing assertion: NOT ONE of these seats can be filled without a hard
        // "Shift envelope compliance" penalty, because the desk's only shift starts at 12:00.
        assertThat(extra)
                .as("every manufactured seat lies outside the desk's only shift envelope")
                .allSatisfy(a -> assertThat(onlyPairOnTheDesk.covers(a.getTimeslot()))
                        .as("Late 12:00-21:00 covers %s", a.getTimeslot().getStartTime())
                        .isFalse());
    }

    @Test
    @DisplayName("CURRENT: a filler seat is created inside the break band too — also an envelope violation")
    void fillerSeatLandsInsideTheBreakBand() {
        Specialization english = spec("English");
        ShiftTemplate late = lateTemplate();
        // Break band 4h into the Late shift: 16:00-17:00. Everyone is on break, so the demand
        // file legitimately carries 0 for that hour.
        ShiftBandPair pair = new ShiftBandPair(late, band(late, 240, 60));
        Timeslot breakHour = timeslot(LocalTime.of(16, 0));

        List<Timeslot> window = List.of(timeslot(LocalTime.of(15, 0)), breakHour, timeslot(LocalTime.of(17, 0)));
        List<StaffingRequirement> demand = List.of(
                requirement(window.get(0), english, 10),
                requirement(window.get(2), english, 10));
        List<AgentAssignment> demandSeats = new ArrayList<>(List.of(
                seat(window.get(0), english), seat(window.get(2), english)));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, window, demandSeats, demand, List.of(english));

        assertThat(extra).hasSize(1);
        assertThat(extra.get(0).getTimeslot().getStartTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(pair.covers(extra.get(0).getTimeslot()))
                .as("a slot inside the chosen band's break interval is NOT covered by the envelope")
                .isFalse();
    }

    @Test
    @DisplayName("CURRENT: the method has no shift-mode parameter at all — it cannot consult an envelope")
    void theMethodIsStructurallyEnvelopeBlind() throws Exception {
        var method = SolverService.class.getDeclaredMethod("expandMinimumStaffingSeats",
                long.class, UUID.class, UUID.class, List.class, List.class, List.class, List.class);

        assertThat(method.getParameterTypes())
                .as("no SchedulingMode / ShiftTemplate / ShiftBandPair is reachable from inside")
                .doesNotContain(com.wfm.model.SchedulingMode.class,
                        ShiftTemplate.class, ShiftBandPair.class);
    }
}
