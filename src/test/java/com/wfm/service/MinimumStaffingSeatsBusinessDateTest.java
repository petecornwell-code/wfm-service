package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 20 plan 20-10 (gap closure, GAP 2 of {@code 20-VERIFICATION.md}, CR-02) — proves {@link
 * SolverService#expandMinimumStaffingSeats}'s two date-sensitive reads inside its SHIFT-mode
 * branch (shift-template weekday eligibility, and the {@code workingAgentDaysByDate} count
 * lookup) resolve a timeslot's BUSINESS date, not its calendar date.
 *
 * <p><strong>Deliberately anchored at 21:00, not midnight.</strong> At a midnight anchor a
 * timeslot's business date and calendar date are always the same value — {@code getDate()} and
 * {@code getBusinessDate()} return identical results for every timeslot, so a midnight-anchored
 * test cannot distinguish which accessor the method actually reads. Every scenario below except
 * the explicit midnight control is anchored at 21:00 specifically so at least one timeslot's
 * calendar date and business date differ, which is the only condition under which this defect
 * can be observed at all.
 *
 * <p><strong>Observed RED before the production change</strong> (plan 20-10, commit sequence:
 * fixture repair, this class RED, then the production change): with {@code
 * expandMinimumStaffingSeats} still reading {@code ts.getDate()} at both sites,
 * {@code weekdayEligibility_evaluatedAgainstBusinessDate_notCalendarDate} observed 0 seats on the
 * post-midnight timeslot where 4 were expected (the weekday filter rejected Tuesday, the
 * timeslot's calendar day, even though its business day is the only weekday the desk's template
 * is valid on), and {@code workingAgentDayCount_lookedUpByBusinessDate_notCalendarDate} observed
 * exactly 1 seat — the {@code MIN_AGENTS_PER_TIMESLOT} floor — where 3 were expected (the count
 * lookup missed its key on the timeslot's calendar date and silently defaulted to zero). See
 * {@code 20-10-SUMMARY.md} for the full RED observation, including a third incidental failure in
 * the adjacency-close scenario.
 */
class MinimumStaffingSeatsBusinessDateTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();

    private static final LocalTime ANCHOR_21 = LocalTime.of(21, 0);

    /** The business day every non-control scenario below is built around. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    /** The calendar date a post-midnight timeslot on business-Monday actually carries, at a
     * 21:00 anchor: a Tuesday. */
    private static final LocalDate TUESDAY_CALENDAR = LocalDate.of(2026, 1, 6);
    /** The business day a timeslot ending exactly at the anchor closes. */
    private static final LocalDate SUNDAY = MONDAY.minusDays(1);

    private static Timeslot timeslot(LocalDate calendarDate, LocalDate businessDate,
            LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(calendarDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }

    private static Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    /**
     * An envelope starting and ending exactly at {@code anchor} spans the entire business day
     * (the anchor's own "equal-to-anchor-in-an-end-position-means-end-of-day" rule, {@link
     * DayWindow}), so every timeslot built by {@link #timeslot} above is covered by it regardless
     * of clock time -- isolating each scenario to the weekday-eligibility filter and the count
     * lookup, which is what this class exists to prove.
     */
    private static ShiftTemplate fullDayTemplate(LocalTime anchor, EnumSet<DayOfWeek> weekdays) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Full-day envelope");
        t.setValidWeekdays(weekdays);
        t.setStartTime(anchor);
        t.setEndTime(anchor);
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    // ------------------------------------------------------------------
    //  Weekday-eligibility case
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: weekday eligibility is evaluated against the timeslot's BUSINESS day, not its calendar day")
    void weekdayEligibility_evaluatedAgainstBusinessDate_notCalendarDate() {
        Specialization english = spec("English");
        ShiftTemplate mondayOnly = fullDayTemplate(ANCHOR_21, EnumSet.of(DayOfWeek.MONDAY));
        ShiftBandPair pair = new ShiftBandPair(mondayOnly, null);

        Timeslot evening = timeslot(MONDAY, MONDAY, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot postMidnight = timeslot(TUESDAY_CALENDAR, MONDAY, LocalTime.of(2, 0), LocalTime.of(3, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(evening, postMidnight), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(MONDAY, 4),
                DayWindow.anchoredAt(ANCHOR_21));

        List<AgentAssignment> atPostMidnight = extra.stream()
                .filter(a -> a.getTimeslot().equals(postMidnight))
                .toList();

        assertThat(atPostMidnight)
                .as("02:00 on calendar Tuesday belongs to business-day Monday at a 21:00 anchor, "
                        + "the only weekday the desk's one template is valid on -- it must receive "
                        + "exactly the 4 seats workingAgentDaysByDate records for Monday, never zero "
                        + "from a weekday check run against its calendar day (Tuesday) instead")
                .hasSize(4);
    }

    // ------------------------------------------------------------------
    //  Count-lookup case
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: the working-agent-day count is looked up by the timeslot's BUSINESS date, not its calendar date")
    void workingAgentDayCount_lookedUpByBusinessDate_notCalendarDate() {
        Specialization english = spec("English");
        ShiftTemplate allWeek = fullDayTemplate(ANCHOR_21, EnumSet.allOf(DayOfWeek.class));
        ShiftBandPair pair = new ShiftBandPair(allWeek, null);

        Timeslot evening = timeslot(MONDAY, MONDAY, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot postMidnight = timeslot(TUESDAY_CALENDAR, MONDAY, LocalTime.of(2, 0), LocalTime.of(3, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(evening, postMidnight), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(MONDAY, 3),
                DayWindow.anchoredAt(ANCHOR_21));

        List<AgentAssignment> atPostMidnight = extra.stream()
                .filter(a -> a.getTimeslot().equals(postMidnight))
                .toList();

        assertThat(atPostMidnight)
                .as("workingAgentDaysByDate holds exactly one entry -- business-day Monday mapped "
                        + "to 3 -- so the post-midnight timeslot must receive exactly 3 seats, never "
                        + "the MIN_AGENTS_PER_TIMESLOT=1 floor a lookup missing its key on the "
                        + "timeslot's calendar date (Tuesday) would silently default to")
                .hasSize(3);
    }

    // ------------------------------------------------------------------
    //  Midnight-anchored control -- the fix must be a no-op here
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONTROL: at a midnight anchor, calendar date and business date coincide -- this fix changes nothing here")
    void midnightAnchor_calendarAndBusinessDateCoincide_fixIsANoOp() {
        Specialization english = spec("English");
        ShiftTemplate allWeek = fullDayTemplate(LocalTime.MIDNIGHT, EnumSet.allOf(DayOfWeek.class));
        ShiftBandPair pair = new ShiftBandPair(allWeek, null);

        Timeslot morning = timeslot(MONDAY, MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot midday = timeslot(MONDAY, MONDAY, LocalTime.of(12, 0), LocalTime.of(13, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(morning, midday), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(MONDAY, 5),
                DayWindow.anchoredAt(LocalTime.MIDNIGHT));

        assertThat(extra)
                .as("both timeslots' calendar date and business date are identically Monday -- "
                        + "getDate() and getBusinessDate() return the same value for each, so both "
                        + "must receive exactly the 5 seats workingAgentDaysByDate records for "
                        + "Monday (10 total), the same answer this method gave before the fix")
                .hasSize(10);
        assertThat(extra).allSatisfy(a -> assertThat(a.getRequiredSpecialization()).isNotNull());
    }

    // ------------------------------------------------------------------
    //  Adjacency -- a timeslot at the anchor belongs to exactly one business day
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ADJACENCY: a timeslot starting exactly at the anchor opens the new business day")
    void timeslotStartingExactlyAtAnchor_opensTheNewBusinessDay() {
        Specialization english = spec("English");
        ShiftTemplate allWeek = fullDayTemplate(ANCHOR_21, EnumSet.allOf(DayOfWeek.class));
        ShiftBandPair pair = new ShiftBandPair(allWeek, null);

        // DayWindow.businessDateOf(21:00, 2026-01-05, 21:00) == 2026-01-05: a time equal to the
        // anchor in a START position opens the business day it falls on.
        Timeslot opensMonday = timeslot(MONDAY, MONDAY, ANCHOR_21, LocalTime.of(22, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(opensMonday), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(MONDAY, 6),
                DayWindow.anchoredAt(ANCHOR_21));

        assertThat(extra)
                .as("a timeslot starting exactly at the 21:00 anchor belongs to the business day "
                        + "it opens (Monday) -- it must receive exactly the 6 seats recorded for "
                        + "Monday, never a count attributed to any other business day")
                .hasSize(6);
    }

    @Test
    @DisplayName("ADJACENCY: a timeslot ending exactly at the anchor closes the previous business day")
    void timeslotEndingExactlyAtAnchor_closesThePreviousBusinessDay() {
        Specialization english = spec("English");
        ShiftTemplate allWeek = fullDayTemplate(ANCHOR_21, EnumSet.allOf(DayOfWeek.class));
        ShiftBandPair pair = new ShiftBandPair(allWeek, null);

        // DayWindow.businessDateOf(21:00, 2026-01-05, 20:00) == 2026-01-04: its own START time
        // (20:00) is before the 21:00 anchor, so it belongs to the PREVIOUS business day (Sunday)
        // -- the one it closes -- even though its calendar date is still Monday.
        Timeslot closesSunday = timeslot(MONDAY, SUNDAY, LocalTime.of(20, 0), ANCHOR_21);

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(closesSunday), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(SUNDAY, 9),
                DayWindow.anchoredAt(ANCHOR_21));

        assertThat(extra)
                .as("a timeslot ending exactly at the 21:00 anchor, whose own start time (20:00) "
                        + "is before the anchor, belongs to the business day it closes (Sunday) -- "
                        + "it must receive exactly the 9 seats recorded for Sunday, never the "
                        + "MIN_AGENTS_PER_TIMESLOT=1 floor a lookup keyed on its calendar date "
                        + "(Monday, which carries no entry) would silently default to")
                .hasSize(9);
    }
}
