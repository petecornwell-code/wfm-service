package com.wfm.service;

import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.DayWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * SOLV-01 (20-REVIEW.md CR-01): the single shared widen-then-derive loader for the four call
 * sites whose fetch bounds ({@code schedule.getPeriodStartDate()}/{@code getPeriodEndDate()}) are
 * BUSINESS dates (18-CONTEXT.md D-22), but whose underlying repository finders filter the
 * entity's CALENDAR date column. On a desk anchored away from midnight, the last business day of
 * a period writes some of its rows on the calendar date one day past {@code periodEnd} -- a
 * calendar-bounded finder truncates those rows unless the fetch is widened by one calendar day
 * and then narrowed back down on the DERIVED business date.
 *
 * <p>Mirrors {@code TimeslotGeneratorService.generateTimeslots}' closing read-back (BDAY-03)
 * exactly: widen the calendar upper bound by one day, then filter (and, for timeslots, sort) on
 * {@link DayWindow#businessDateOf} -- never on the stored {@code business_date} column. At a
 * {@code 00:00} anchor every derived business date equals the row's calendar date, so the
 * widened fetch's extra rows are all removed by the filter and both the returned list and its
 * order are unchanged; the fix is a provable no-op on a midnight-anchored desk.
 *
 * <p>Package-private: this class and its two callers in this phase ({@code SolverService},
 * {@code ScheduleService}) all live in {@code com.wfm.service}, and {@code BusinessDayPeriodLoaderTest}
 * lives in the same package to exercise both methods directly against mocked repositories.
 */
final class BusinessDayPeriodLoader {

    private BusinessDayPeriodLoader() {
    }

    /**
     * Live timeslots whose DERIVED business date falls within {@code [periodStart, periodEnd]},
     * ordered by business date then by minutes-from-day-start -- the business-day-chronological
     * order {@code TimeslotRepository}'s own {@code OrderByDateAscStartTimeAsc} finder gives for
     * free at a {@code 00:00} anchor, and which this method makes an explicit guarantee rather
     * than an accident of calendar-date ordering at any other anchor.
     */
    static List<Timeslot> loadLiveTimeslots(TimeslotRepository timeslotRepository, long tenantId, UUID deskId,
            LocalDate periodStart, LocalDate periodEnd, LocalTime dayStart) {
        List<Timeslot> widened = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, periodStart, periodEnd.plusDays(1));
        return widened.stream()
                .filter(ts -> {
                    LocalDate businessDate = DayWindow.businessDateOf(dayStart, ts.getDate(), ts.getStartTime());
                    return !businessDate.isBefore(periodStart) && !businessDate.isAfter(periodEnd);
                })
                .sorted(Comparator
                        .comparing((Timeslot ts) -> DayWindow.businessDateOf(dayStart, ts.getDate(), ts.getStartTime()))
                        .thenComparingInt(ts -> DayWindow.startMinuteFromDayStart(dayStart, ts.getStartTime())))
                .toList();
    }

    /**
     * Live staffing requirements whose DERIVED business date falls within
     * {@code [periodStart, periodEnd]}. Filter only, never sort (P-02): the underlying
     * unpaginated {@code findLiveByDeskAndDateRange} overload declares no {@code ORDER BY}, so
     * imposing one here would change the returned order at a {@code 00:00} anchor and break the
     * no-op invariant -- a stream filter preserves relative order instead. That query's
     * {@code JOIN FETCH sr.timeslot} is what makes {@code sr.getTimeslot()} safe to dereference
     * outside a persistence session.
     */
    static List<StaffingRequirement> loadLiveStaffingRequirements(
            StaffingRequirementRepository staffingRequirementRepository, long tenantId, UUID deskId,
            LocalDate periodStart, LocalDate periodEnd, LocalTime dayStart) {
        List<StaffingRequirement> widened = staffingRequirementRepository
                .findLiveByDeskAndDateRange(tenantId, deskId, periodStart, periodEnd.plusDays(1));
        return widened.stream()
                .filter(sr -> {
                    LocalDate businessDate = DayWindow.businessDateOf(
                            dayStart, sr.getTimeslot().getDate(), sr.getTimeslot().getStartTime());
                    return !businessDate.isBefore(periodStart) && !businessDate.isAfter(periodEnd);
                })
                .toList();
    }
}
