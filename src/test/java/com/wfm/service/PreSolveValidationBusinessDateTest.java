package com.wfm.service;

import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.AgentDayOff;
import com.wfm.model.AgentException;
import com.wfm.model.AgentPreference;
import com.wfm.model.Schedule;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Phase 20 plan 20-11 (gap closure, advisory 1 of {@code 20-VERIFICATION.md}) — proves {@link
 * SolverService#runPreSolveValidation}'s two remaining {@code Timeslot}-receiver calendar-date
 * reads (check 2's period-coverage set, check 3's same-day filter feeding the end-time
 * comparison) resolve the timeslot's BUSINESS date, not its calendar date — the identical
 * key-system mismatch already fixed in this file's sibling function {@code
 * requireShiftEnvelopeSeatSupply} (plan 20-01) and in {@code expandMinimumStaffingSeats} (plan
 * 20-10, {@link MinimumStaffingSeatsBusinessDateTest}).
 *
 * <p><strong>Deliberately anchored at 21:00, not midnight.</strong> At a midnight anchor a
 * timeslot's business date and calendar date are always the same value, so a midnight-anchored
 * fixture cannot distinguish which accessor the production code actually reads — the two checks
 * are indistinguishable there. Every scenario below except the explicit midnight control is
 * anchored at 21:00 specifically so at least one timeslot's calendar date and business date
 * differ, which is the only condition under which this defect is observable at all.
 *
 * <p>Assertions filter the raised {@link ErrorDetail} list by field name rather than asserting
 * that no exception is thrown at all — the minimal fixture below does not satisfy every one of
 * {@code runPreSolveValidation}'s thirteen checks (for example check 6, "at least one staffing
 * requirement must exist", always fires here because this fixture supplies none), so a blanket
 * no-throw assertion would be both fragile and unable to say which check fired.
 *
 * <p><strong>Observed RED before the production change</strong> (this class's own commit, before
 * check 2 and check 3 were re-pointed at the business date): {@code
 * periodCoverage_resolvesBusinessDate_not21_00AnchoredCalendarDate} observed a {@code timeslots}
 * detail reading {@code "No timeslots found for date 2026-01-05"} — the period-coverage set,
 * built from the four timeslots' calendar dates ({@code {2026-01-06}}), never contained the
 * schedule's one business-date period day ({@code 2026-01-05}), even though every one of those
 * timeslots carries business date 2026-01-05. {@code
 * endTime_resolvesBusinessDate_not21_00AnchoredCalendarDate} observed an {@code endTime} detail
 * reading {@code "Schedule endTime 02:00 does not match timeslot end 00:00"} — the same-day
 * filter, run against the first timeslot's calendar date (2026-01-05), stopped at the last
 * timeslot still carrying THAT calendar date (ending 00:00) instead of the business day's actual
 * last timeslot (ending 02:00, one calendar day later).
 */
class PreSolveValidationBusinessDateTest {

    private static final LocalTime ANCHOR_21 = LocalTime.of(21, 0);

    private static Timeslot timeslot(LocalDate calendarDate, LocalDate businessDate,
            LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setDate(calendarDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }

    private static Schedule baseSchedule(LocalTime anchor, LocalDate periodStart, LocalDate periodEnd,
            LocalTime startTime, LocalTime endTime) {
        Schedule schedule = new Schedule();
        schedule.setPeriodStartDate(periodStart);
        schedule.setPeriodEndDate(periodEnd);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setIncrementMinutes(60);
        schedule.setDayStart(anchor);
        return schedule;
    }

    /** Runs the method under test with every collaborator list empty except {@code timeslots}. */
    private static List<ErrorDetail> runAndCollectErrors(Schedule schedule, List<Timeslot> timeslots,
            DayWindow window) {
        try {
            SolverService.runPreSolveValidation(mock(ShiftLibraryValidationService.class), schedule,
                    List.<Agent>of(), timeslots, List.<StaffingRequirement>of(), List.<Agent>of(),
                    List.<AgentDayOff>of(), List.<AgentException>of(), List.<AgentDayHours>of(),
                    List.<AgentPreference>of(), window);
            return List.of();
        } catch (PreSolveValidationException e) {
            return e.getDetails();
        }
    }

    // ------------------------------------------------------------------
    //  Period-coverage case (check 2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Period coverage: a business day with no same-calendar-date timeslot is not refused")
    void periodCoverage_resolvesBusinessDate_not21_00AnchoredCalendarDate() {
        LocalDate businessMonday = LocalDate.of(2026, 1, 5);
        LocalDate calendarTuesday = LocalDate.of(2026, 1, 6);

        Schedule schedule = baseSchedule(ANCHOR_21, businessMonday, businessMonday,
                LocalTime.of(0, 0), LocalTime.of(6, 0));

        List<Timeslot> timeslots = new ArrayList<>();
        for (int hour = 0; hour < 6; hour++) {
            timeslots.add(timeslot(calendarTuesday, businessMonday,
                    LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0)));
        }

        List<ErrorDetail> errors = runAndCollectErrors(schedule, timeslots, DayWindow.anchoredAt(ANCHOR_21));

        assertThat(errors)
                .as("every timeslot carries business date 2026-01-05, the schedule's only period "
                        + "day -- the period-coverage set must be built from business dates so this "
                        + "day is found, even though none of the timeslots' calendar dates (all "
                        + "2026-01-06) match it")
                .filteredOn(d -> "timeslots".equals(d.field()))
                .isEmpty();
    }

    @Test
    @DisplayName("Period coverage, falsification control: equal calendar/business dates must still fail")
    void periodCoverage_falsificationControl_equalCalendarAndBusinessDate_stillRaises() {
        LocalDate businessMonday = LocalDate.of(2026, 1, 5);
        LocalDate calendarTuesday = LocalDate.of(2026, 1, 6);

        Schedule schedule = baseSchedule(ANCHOR_21, businessMonday, businessMonday,
                LocalTime.of(0, 0), LocalTime.of(6, 0));

        List<Timeslot> timeslots = new ArrayList<>();
        for (int hour = 0; hour < 6; hour++) {
            // Business date deliberately set EQUAL to the calendar date (2026-01-06), not the
            // schedule's actual business day -- this must still raise, proving the assertion
            // above is sensitive to the date the production code reads rather than vacuously
            // green regardless of which accessor is called.
            timeslots.add(timeslot(calendarTuesday, calendarTuesday,
                    LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0)));
        }

        List<ErrorDetail> errors = runAndCollectErrors(schedule, timeslots, DayWindow.anchoredAt(ANCHOR_21));

        assertThat(errors)
                .as("with every timeslot's business date deliberately equal to its calendar date "
                        + "(2026-01-06), the schedule's one period day (2026-01-05) is genuinely "
                        + "absent from the business-date set too -- the check must still raise, "
                        + "proving it reads the production field and is not vacuously satisfied")
                .filteredOn(d -> "timeslots".equals(d.field()))
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    //  End-time case (check 3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("End time: the last timeslot of the business day is selected, not the last of the calendar day")
    void endTime_resolvesBusinessDate_not21_00AnchoredCalendarDate() {
        LocalDate businessMonday = LocalDate.of(2026, 1, 5);
        LocalDate calendarTuesday = LocalDate.of(2026, 1, 6);

        Schedule schedule = baseSchedule(ANCHOR_21, businessMonday, businessMonday,
                LocalTime.of(22, 0), LocalTime.of(2, 0));

        List<Timeslot> timeslots = List.of(
                timeslot(businessMonday, businessMonday, LocalTime.of(22, 0), LocalTime.of(23, 0)),
                timeslot(businessMonday, businessMonday, LocalTime.of(23, 0), LocalTime.of(0, 0)),
                timeslot(calendarTuesday, businessMonday, LocalTime.of(0, 0), LocalTime.of(1, 0)),
                timeslot(calendarTuesday, businessMonday, LocalTime.of(1, 0), LocalTime.of(2, 0)));

        List<ErrorDetail> errors = runAndCollectErrors(schedule, timeslots, DayWindow.anchoredAt(ANCHOR_21));

        assertThat(errors)
                .as("all four timeslots carry business date 2026-01-05 -- the same-day filter must "
                        + "select the last of THEM (ending 02:00, matching the schedule's endTime), "
                        + "not stop at the last one still sharing the first row's calendar date "
                        + "(2026-01-05, which ends at 00:00, one calendar day before the business "
                        + "day's actual last timeslot)")
                .filteredOn(d -> "endTime".equals(d.field()))
                .isEmpty();
    }

    @Test
    @DisplayName("End time, falsification control: equal calendar/business dates must still fail")
    void endTime_falsificationControl_equalCalendarAndBusinessDate_stillRaises() {
        LocalDate businessMonday = LocalDate.of(2026, 1, 5);
        LocalDate calendarTuesday = LocalDate.of(2026, 1, 6);

        Schedule schedule = baseSchedule(ANCHOR_21, businessMonday, businessMonday,
                LocalTime.of(22, 0), LocalTime.of(2, 0));

        List<Timeslot> timeslots = List.of(
                timeslot(businessMonday, businessMonday, LocalTime.of(22, 0), LocalTime.of(23, 0)),
                timeslot(businessMonday, businessMonday, LocalTime.of(23, 0), LocalTime.of(0, 0)),
                // Business date deliberately set EQUAL to calendar date (2026-01-06) for the
                // post-midnight pair, rather than the business day (2026-01-05) they actually
                // belong to -- this must still raise, proving the assertion above is sensitive to
                // the field the production code reads.
                timeslot(calendarTuesday, calendarTuesday, LocalTime.of(0, 0), LocalTime.of(1, 0)),
                timeslot(calendarTuesday, calendarTuesday, LocalTime.of(1, 0), LocalTime.of(2, 0)));

        List<ErrorDetail> errors = runAndCollectErrors(schedule, timeslots, DayWindow.anchoredAt(ANCHOR_21));

        assertThat(errors)
                .as("with the post-midnight pair's business date deliberately equal to its "
                        + "calendar date, the first row's business date (2026-01-05) is shared by "
                        + "only the first two timeslots -- the selected last-of-day row still ends "
                        + "at 00:00, not 02:00, so the check must still raise")
                .filteredOn(d -> "endTime".equals(d.field()))
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    //  Midnight-anchored control -- the fix must be a no-op here
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONTROL: at a midnight anchor, calendar date and business date coincide -- this fix changes nothing here")
    void midnightAnchor_calendarAndBusinessDateCoincide_fixIsANoOp() {
        LocalDate monday = LocalDate.of(2026, 1, 5);

        Schedule schedule = baseSchedule(LocalTime.MIDNIGHT, monday, monday,
                LocalTime.of(0, 0), LocalTime.of(2, 0));

        List<Timeslot> timeslots = List.of(
                timeslot(monday, monday, LocalTime.of(0, 0), LocalTime.of(1, 0)),
                timeslot(monday, monday, LocalTime.of(1, 0), LocalTime.of(2, 0)));

        List<ErrorDetail> errors = runAndCollectErrors(schedule, timeslots, DayWindow.anchoredAt(LocalTime.MIDNIGHT));

        assertThat(errors)
                .as("both timeslots' calendar date and business date are identically 2026-01-05 -- "
                        + "getDate() and getBusinessDate() return the same value for each, so "
                        + "neither the period-coverage nor the end-time check can observe a "
                        + "difference between the two accessors here, before or after the fix")
                .filteredOn(d -> "timeslots".equals(d.field()) || "endTime".equals(d.field()))
                .isEmpty();
    }
}
