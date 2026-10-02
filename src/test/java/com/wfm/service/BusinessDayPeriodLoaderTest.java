package com.wfm.service;

import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 20 plan 20-12 (gap closure, CR-01 sites 1-4) — proves {@link BusinessDayPeriodLoader}'s
 * two static loaders directly against mocked repositories, no Spring context: the widened
 * calendar bound is actually requested (the half of the fix a filter alone cannot deliver), the
 * last business day's post-midnight rows survive, the two-sided filter excludes rows outside the
 * requested business-date range in BOTH directions, timeslots come back in explicit
 * business-day-chronological order while staffing requirements are filtered only (P-02), and a
 * midnight anchor is a provable no-op for both methods.
 *
 * <p><strong>Deliberately anchored at 21:00, not midnight, for every non-control scenario.</strong>
 * At a midnight anchor a timeslot's business date and calendar date are always the same value, so
 * a midnight-anchored fixture cannot distinguish which accessor the loader actually reads — the
 * only two tests that use {@code LocalTime.MIDNIGHT} (5 and 6) are explicit no-op controls.
 */
class BusinessDayPeriodLoaderTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();

    private static final LocalTime ANCHOR_21 = LocalTime.of(21, 0);

    /** The business day every non-control scenario below is built around. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    /** The calendar date a post-midnight timeslot on business-Monday carries at a 21:00 anchor. */
    private static final LocalDate TUESDAY_CALENDAR = MONDAY.plusDays(1);

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

    private static StaffingRequirement staffingRequirement(Timeslot timeslot) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(UUID.randomUUID());
        sr.setTimeslot(timeslot);
        sr.setSpecialization(new Specialization());
        sr.setRequiredFTEs(1);
        return sr;
    }

    /** The 24 timeslots of business-Monday at a 21:00 anchor: 3 on calendar MONDAY
     * (21:00/22:00/23:00), 21 on calendar TUESDAY_CALENDAR (00:00 through 20:00). The 21
     * post-midnight rows' STORED businessDate is deliberately set to their OWN calendar date
     * (TUESDAY_CALENDAR) -- DISAGREEING with their derivation (MONDAY) -- so every test built on
     * this fixture doubles as a derivation-vs-stored-column proof: only 3 of these 24 rows' stored
     * value reads MONDAY, yet all 24 must survive a loadLiveTimeslots/loadLiveStaffingRequirements
     * call for business-Monday, which is possible ONLY if the loader derives rather than reads the
     * column (the identical falsification logic ScheduleServiceShiftSnapshotTest's Test C proves
     * end-to-end through the real accept path). */
    private static List<Timeslot> fullBusinessMonday() {
        List<Timeslot> rows = new ArrayList<>();
        rows.add(timeslot(MONDAY, MONDAY, LocalTime.of(21, 0), LocalTime.of(22, 0)));
        rows.add(timeslot(MONDAY, MONDAY, LocalTime.of(22, 0), LocalTime.of(23, 0)));
        rows.add(timeslot(MONDAY, MONDAY, LocalTime.of(23, 0), LocalTime.of(23, 59)));
        for (int hour = 0; hour <= 20; hour++) {
            rows.add(timeslot(TUESDAY_CALENDAR, TUESDAY_CALENDAR, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0)));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Test 1: the widened bound is actually requested
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 1: loadLiveTimeslots requests periodEnd.plusDays(1) as its calendar upper bound")
    void loadLiveTimeslots_requestsTheWidenedCalendarUpperBound() {
        TimeslotRepository repo = mock(TimeslotRepository.class);
        when(repo.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                anyLong(), any(), any(), any())).thenReturn(List.of());

        LocalDate periodStart = MONDAY;
        LocalDate periodEnd = MONDAY.plusDays(2);
        BusinessDayPeriodLoader.loadLiveTimeslots(repo, TENANT, DESK, periodStart, periodEnd, ANCHOR_21);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(repo).findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                eq(TENANT), eq(DESK), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(periodStart);
        assertThat(to.getValue())
                .as("the calendar upper bound actually requested must be periodEnd.plusDays(1), "
                        + "not periodEnd -- without this widening the post-midnight rows are never "
                        + "fetched at all, regardless of any filter")
                .isEqualTo(periodEnd.plusDays(1));
    }

    @Test
    @DisplayName("Test 1 (staffing): loadLiveStaffingRequirements requests periodEnd.plusDays(1) as its calendar upper bound")
    void loadLiveStaffingRequirements_requestsTheWidenedCalendarUpperBound() {
        StaffingRequirementRepository repo = mock(StaffingRequirementRepository.class);
        when(repo.findLiveByDeskAndDateRange(anyLong(), any(), any(), any())).thenReturn(List.of());

        LocalDate periodStart = MONDAY;
        LocalDate periodEnd = MONDAY.plusDays(2);
        BusinessDayPeriodLoader.loadLiveStaffingRequirements(repo, TENANT, DESK, periodStart, periodEnd, ANCHOR_21);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(repo).findLiveByDeskAndDateRange(eq(TENANT), eq(DESK), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(periodStart);
        assertThat(to.getValue()).isEqualTo(periodEnd.plusDays(1));
    }

    // ------------------------------------------------------------------
    // Test 2: last business day survives, 21:00
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 2: all 24 timeslots of the last business day survive at a 21:00 anchor")
    void loadLiveTimeslots_lastBusinessDaySurvives_21_00Anchor() {
        TimeslotRepository repo = mock(TimeslotRepository.class);
        when(repo.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                anyLong(), any(), any(), any())).thenReturn(fullBusinessMonday());

        List<Timeslot> result = BusinessDayPeriodLoader.loadLiveTimeslots(
                repo, TENANT, DESK, MONDAY, MONDAY, ANCHOR_21);

        assertThat(result)
                .as("all 24 of business-Monday's timeslots must survive, even though only 3 of "
                        + "them have a STORED businessDate of MONDAY -- the other 21 store their "
                        + "own (disagreeing) calendar date, proving the DERIVED value governs")
                .hasSize(24);
    }

    // ------------------------------------------------------------------
    // Test 3: two-sided filter
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 3: the filter excludes rows outside the business-date range on BOTH sides")
    void loadLiveTimeslots_filtersBothBelowAndAboveTheRequestedRange() {
        List<Timeslot> stubbed = new ArrayList<>(fullBusinessMonday());
        // Head row: calendar MONDAY at 20:00 is BEFORE the 21:00 anchor, so its derived business
        // date is MONDAY.minusDays(1) -- out of range on the LOWER side.
        Timeslot headRow = timeslot(MONDAY, MONDAY.minusDays(1), LocalTime.of(20, 0), ANCHOR_21);
        // Tail row: calendar TUESDAY at 21:00 is AT the anchor, so its derived business date is
        // TUESDAY itself -- out of range on the UPPER side.
        Timeslot tailRow = timeslot(TUESDAY_CALENDAR, TUESDAY_CALENDAR, ANCHOR_21, LocalTime.of(22, 0));
        stubbed.add(headRow);
        stubbed.add(tailRow);

        TimeslotRepository repo = mock(TimeslotRepository.class);
        when(repo.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                anyLong(), any(), any(), any())).thenReturn(stubbed);

        List<Timeslot> result = BusinessDayPeriodLoader.loadLiveTimeslots(
                repo, TENANT, DESK, MONDAY, MONDAY, ANCHOR_21);

        assertThat(result)
                .as("exactly the 24 in-range rows must be returned -- the lower bound is filtered "
                        + "too, not only the upper")
                .hasSize(24)
                .doesNotContain(headRow, tailRow);
    }

    // ------------------------------------------------------------------
    // Test 4: explicit ordering, 21:00
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 4: timeslots are returned in explicit business-day-chronological order, not raw LocalTime order")
    void loadLiveTimeslots_ordersByBusinessDateThenMinutesFromDayStart() {
        List<Timeslot> shuffled = new ArrayList<>(fullBusinessMonday());
        // Deliberately shuffle away from both calendar order and business-day order.
        java.util.Collections.shuffle(shuffled, new java.util.Random(42));

        TimeslotRepository repo = mock(TimeslotRepository.class);
        when(repo.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                anyLong(), any(), any(), any())).thenReturn(shuffled);

        List<Timeslot> result = BusinessDayPeriodLoader.loadLiveTimeslots(
                repo, TENANT, DESK, MONDAY, MONDAY, ANCHOR_21);

        List<LocalTime> expectedOrder = new ArrayList<>(
                List.of(LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0)));
        for (int hour = 0; hour <= 20; hour++) {
            expectedOrder.add(LocalTime.of(hour, 0));
        }

        assertThat(result.stream().map(Timeslot::getStartTime).toList())
                .as("business-day chronological order: 21:00, 22:00, 23:00, then 00:00..20:00 -- "
                        + "startMinuteFromDayStart order, never raw LocalTime order")
                .containsExactlyElementsOf(expectedOrder);
    }

    // ------------------------------------------------------------------
    // Test 5: 00:00 no-op, timeslots
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 5 (CONTROL): at a midnight anchor, the timeslot list and its order are unchanged, decoy excluded")
    void loadLiveTimeslots_midnightAnchor_isANoOp() {
        // Stubbed in the real finder's own OrderByDateAscStartTimeAsc order (ascending) -- at a
        // 00:00 anchor, business-day order and raw-time order coincide, so the loader's own
        // re-sort is idempotent over already-sorted input. This is the no-op property itself:
        // re-sorting input that is already in the target order leaves it unchanged.
        List<Timeslot> mondayRowsInStubbedOrder = List.of(
                timeslot(MONDAY, MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                timeslot(MONDAY, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                timeslot(MONDAY, MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        Timeslot decoy = timeslot(MONDAY.plusDays(1), MONDAY.plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0));

        List<Timeslot> stubbed = new ArrayList<>(mondayRowsInStubbedOrder);
        stubbed.add(decoy);

        TimeslotRepository repo = mock(TimeslotRepository.class);
        when(repo.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                anyLong(), any(), any(), any())).thenReturn(stubbed);

        List<Timeslot> result = BusinessDayPeriodLoader.loadLiveTimeslots(
                repo, TENANT, DESK, MONDAY, MONDAY, LocalTime.MIDNIGHT);

        assertThat(result)
                .as("at a 00:00 anchor the returned list must equal the stubbed in-range rows in "
                        + "the SAME order (same elements, same order) with the decoy absent -- the "
                        + "widened fetch's extra calendar day filters out completely")
                .containsExactlyElementsOf(mondayRowsInStubbedOrder);
    }

    // ------------------------------------------------------------------
    // Test 6: 00:00 no-op, staffing requirements, order preserved
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 6 (CONTROL): at a midnight anchor, the staffing-requirement list's stub order is preserved verbatim")
    void loadLiveStaffingRequirements_midnightAnchor_preservesStubOrderVerbatim() {
        Timeslot ts1 = timeslot(MONDAY, MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
        Timeslot ts2 = timeslot(MONDAY, MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot ts3 = timeslot(MONDAY, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        // Deliberately NOT date/time ordered -- P-02: the unpaginated findLiveByDeskAndDateRange
        // JPQL declares no ORDER BY, so the loader must preserve whatever order the repository
        // returns, never impose one of its own.
        StaffingRequirement sr1 = staffingRequirement(ts1);
        StaffingRequirement sr2 = staffingRequirement(ts2);
        StaffingRequirement sr3 = staffingRequirement(ts3);
        List<StaffingRequirement> stubbedOrder = List.of(sr1, sr2, sr3);

        Timeslot decoyTimeslot = timeslot(MONDAY.plusDays(1), MONDAY.plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0));
        StaffingRequirement decoy = staffingRequirement(decoyTimeslot);

        List<StaffingRequirement> stubbed = new ArrayList<>(stubbedOrder);
        stubbed.add(decoy);

        StaffingRequirementRepository repo = mock(StaffingRequirementRepository.class);
        when(repo.findLiveByDeskAndDateRange(anyLong(), any(), any(), any())).thenReturn(stubbed);

        List<StaffingRequirement> result = BusinessDayPeriodLoader.loadLiveStaffingRequirements(
                repo, TENANT, DESK, MONDAY, MONDAY, LocalTime.MIDNIGHT);

        assertThat(result)
                .as("the returned list must equal the stub's own order verbatim -- filter only, "
                        + "never sort")
                .containsExactlyElementsOf(stubbedOrder);
    }

    // ------------------------------------------------------------------
    // Test 7: staffing requirements, 21:00
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Test 7: the last business day's post-midnight staffing requirements survive at a 21:00 anchor")
    void loadLiveStaffingRequirements_lastBusinessDaySurvives_21_00Anchor() {
        List<Timeslot> timeslots = fullBusinessMonday();
        List<StaffingRequirement> requirements = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            requirements.add(staffingRequirement(ts));
        }
        // A requirement whose timeslot derives to the NEXT business day must not survive.
        Timeslot nextDayTimeslot = timeslot(TUESDAY_CALENDAR, TUESDAY_CALENDAR, ANCHOR_21, LocalTime.of(22, 0));
        StaffingRequirement nextDayRequirement = staffingRequirement(nextDayTimeslot);
        requirements.add(nextDayRequirement);

        StaffingRequirementRepository repo = mock(StaffingRequirementRepository.class);
        when(repo.findLiveByDeskAndDateRange(anyLong(), any(), any(), any())).thenReturn(requirements);

        List<StaffingRequirement> result = BusinessDayPeriodLoader.loadLiveStaffingRequirements(
                repo, TENANT, DESK, MONDAY, MONDAY, ANCHOR_21);

        assertThat(result)
                .as("all 24 of business-Monday's staffing requirements must survive, and the "
                        + "next-business-day requirement must not")
                .hasSize(24)
                .doesNotContain(nextDayRequirement);
    }
}
