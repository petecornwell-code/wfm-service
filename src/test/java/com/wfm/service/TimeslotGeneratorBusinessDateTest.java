package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Timeslot;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.DayWindow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct proof of BDAY-03 against the REAL {@link TimeslotGeneratorService}, with mocked
 * repositories and entity manager -- D-19 explicitly rejects proving this against a test double
 * of the generator itself, because that would prove a property of code that gets thrown away.
 *
 * <p>Plain JUnit + Mockito, no Spring context, no database: the shape {@code
 * TimeslotGeneratorServiceTest} already uses for {@code isDesired}, extended here to drive {@link
 * TimeslotGeneratorService#generateTimeslots} end to end and capture what it actually persists.
 *
 * <p>The claim under proof (ROADMAP.md Phase 18 success criterion 2): a desk anchored at 21:00
 * produces a contiguous 24-hour business day spanning two calendar dates, with a single business
 * date for every row in it.
 */
class TimeslotGeneratorBusinessDateTest {

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final LocalTime ANCHOR_2100 = LocalTime.of(21, 0);

    private final TimeslotRepository timeslotRepository = mock(TimeslotRepository.class);
    private final StaffingRequirementRepository staffingRequirementRepository =
            mock(StaffingRequirementRepository.class);
    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final TimeslotGeneratorService service = new TimeslotGeneratorService(
            timeslotRepository, staffingRequirementRepository, scheduleRepository, entityManager);

    /**
     * Mutable backing store the {@code saveAll} and closing-read-back stubs share, so the
     * read-back returns exactly what generation just saved -- the real generator's own contract
     * between those two calls, not a re-implementation of it in the test.
     */
    private final List<Timeslot> savedRows = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        // No pre-existing live timeslots on this desk: nothing to preserve, nothing to delete.
        when(timeslotRepository.findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(
                anyLong(), any())).thenReturn(List.of());
        when(timeslotRepository.saveAll(any())).thenAnswer(inv -> {
            List<Timeslot> saved = (List<Timeslot>) inv.getArgument(0);
            savedRows.addAll(saved);
            return saved;
        });
        // The closing read-back finder returns everything generation just saved; generateTimeslots
        // does its own business-date filter and sort in memory, so the stub need not replicate it.
        when(timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        anyLong(), any(), any(), any()))
                .thenAnswer(inv -> new ArrayList<>(savedRows));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        savedRows.clear();
    }

    @Nested
    @DisplayName("a 21:00-anchored desk, one business day, 60-minute increments, window 21:00 to 21:00")
    class SingleBusinessDayAt2100 {

        private final LocalDate businessDay = LocalDate.of(2026, 10, 1);

        private List<Timeslot> generate() {
            // dayStart, startTime and endTime are all 21:00: the window is the desk's own full
            // 24-hour business day, anchored at LocalTime.of(21, 0) rather than midnight.
            return service.generateTimeslots(
                    DESK_ID, businessDay, businessDay,
                    LocalTime.of(21, 0), LocalTime.of(21, 0), LocalTime.of(21, 0), 60);
        }

        @Test
        @DisplayName("exactly 24 rows are created, all carrying the single requested business date")
        void twentyFourRowsOneBusinessDate() {
            // 24 hourly slots tile a 24-hour business day at a 60-minute increment.
            generate();
            assertThat(savedRows).hasSize(24);
            assertThat(savedRows).allSatisfy(ts ->
                    assertThat(ts.getBusinessDate()).isEqualTo(businessDay));
        }

        @Test
        @DisplayName("calendar dates take exactly two distinct values: the business day and the day after")
        void twoDistinctCalendarDates() {
            generate();
            // A 21:00 anchor places 1260 of the day's 1440 minutes on the FOLLOWING calendar
            // date -- only the first 3 hourly slots (21:00, 22:00, 23:00) stay on the business
            // day's own calendar date; the remaining 21 land on the day after.
            Set<LocalDate> calendarDates = savedRows.stream().map(Timeslot::getDate)
                    .collect(Collectors.toSet());
            assertThat(calendarDates).containsExactlyInAnyOrder(businessDay, businessDay.plusDays(1));
        }

        @Test
        @DisplayName("the first row starts at 21:00 on the business day's own calendar date")
        void firstRowStartsOnTheBusinessDaysOwnCalendarDate() {
            generate();
            Timeslot first = savedRows.stream()
                    .filter(ts -> ts.getStartTime().equals(ANCHOR_2100))
                    .findFirst().orElseThrow();
            assertThat(first.getDate()).isEqualTo(businessDay);
        }

        @Test
        @DisplayName("the last row ends at 21:00 on the following calendar date")
        void lastRowEndsOnTheFollowingCalendarDate() {
            generate();
            Timeslot last = savedRows.stream()
                    .filter(ts -> ts.getEndTime().equals(ANCHOR_2100))
                    .findFirst().orElseThrow();
            assertThat(last.getDate()).isEqualTo(businessDay.plusDays(1));
        }

        @Test
        @DisplayName("the 24 rows are contiguous: sorted by offset, each row's end equals the next "
                + "row's start, and each row's own span is exactly the increment")
        void rowsAreContiguousWithNoGapAndNoOverlap() {
            generate();
            List<Timeslot> sorted = savedRows.stream()
                    .sorted((a, b) -> Integer.compare(
                            DayWindow.startMinuteFromDayStart(ANCHOR_2100, a.getStartTime()),
                            DayWindow.startMinuteFromDayStart(ANCHOR_2100, b.getStartTime())))
                    .toList();
            assertThat(sorted).hasSize(24);
            for (int i = 0; i < sorted.size(); i++) {
                Timeslot ts = sorted.get(i);
                assertThat(DayWindow.endMinuteFromDayStart(ANCHOR_2100, ts.getEndTime())
                        - DayWindow.startMinuteFromDayStart(ANCHOR_2100, ts.getStartTime()))
                        .as("row %d's own span", i)
                        .isEqualTo(60);
                if (i > 0) {
                    Timeslot previous = sorted.get(i - 1);
                    // Continuity is judged in ANCHOR-OFFSET terms, not raw LocalTime/LocalDate
                    // equality -- a row's end (LocalTime) equals the next row's start (LocalTime)
                    // even across the calendar-date boundary, because both read the clock face,
                    // not the date.
                    assertThat(ts.getStartTime()).as("row %d starts where row %d ends", i, i - 1)
                            .isEqualTo(previous.getEndTime());
                    // The last row's end (offset 1440) has no successor in this loop, so every
                    // compared pair's offsets stay inside [0, 1380] -- no wrap-around arithmetic
                    // is needed to state this continuity check.
                    assertThat(DayWindow.startMinuteFromDayStart(ANCHOR_2100, ts.getStartTime()))
                            .as("row %d's anchor-offset start continues row %d's anchor-offset end", i, i - 1)
                            .isEqualTo(DayWindow.endMinuteFromDayStart(ANCHOR_2100, previous.getEndTime()));
                }
            }
        }

        @Test
        @DisplayName("the row whose start is 00:00 sits on the FOLLOWING calendar date and still "
                + "carries the ORIGINAL business date -- the single assertion distinguishing a "
                + "business day from a calendar day")
        void midnightStartingRowCarriesTheOriginalBusinessDate() {
            generate();
            Timeslot midnightRow = savedRows.stream()
                    .filter(ts -> ts.getStartTime().equals(LocalTime.MIDNIGHT))
                    .findFirst().orElseThrow();
            assertThat(midnightRow.getDate()).as("calendar date").isEqualTo(businessDay.plusDays(1));
            assertThat(midnightRow.getBusinessDate()).as("business date").isEqualTo(businessDay);
        }

        @Test
        @DisplayName("the generator's RETURNED list -- not only the saveAll argument -- contains "
                + "every row, including the post-midnight half on the following calendar date")
        void returnedListContainsEveryRowNotJustTheSavedOnes() {
            List<Timeslot> result = generate();
            assertThat(result).hasSize(24);
            assertThat(result.stream().map(Timeslot::getDate).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(businessDay, businessDay.plusDays(1));
        }
    }

    @Nested
    @DisplayName("a 21:00-anchored desk over three business days D..D+2 at 60 minutes")
    class ThreeBusinessDaysAt2100 {

        private final LocalDate d = LocalDate.of(2026, 10, 1);

        @Test
        @DisplayName("72 rows, exactly three distinct business dates, exactly four distinct "
                + "calendar dates (D through D+3)")
        void threeBusinessDatesFourCalendarDates() {
            service.generateTimeslots(
                    DESK_ID, d, d.plusDays(2),
                    LocalTime.of(21, 0), LocalTime.of(21, 0), LocalTime.of(21, 0), 60);

            // 3 business days * 24 hourly slots per day.
            assertThat(savedRows).hasSize(72);
            assertThat(savedRows.stream().map(Timeslot::getBusinessDate).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(d, d.plusDays(1), d.plusDays(2));
            // Each business day spills its post-midnight third onto the following calendar date,
            // so three consecutive business days sweep four consecutive calendar dates: D..D+3.
            assertThat(savedRows.stream().map(Timeslot::getDate).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(d, d.plusDays(1), d.plusDays(2), d.plusDays(3));
        }
    }

    @Nested
    @DisplayName("a 00:00-anchored desk over the same period and window: pre-BDAY-03 behaviour "
            + "is unchanged")
    class MidnightAnchorInvariance {

        @Test
        @DisplayName("every row's business date equals its calendar date -- true only at a 00:00 "
                + "anchor, which is why this case says so explicitly rather than asserting it "
                + "unconditionally elsewhere in this class")
        void businessDateEqualsCalendarDateAtMidnightAnchor() {
            LocalDate d = LocalDate.of(2026, 10, 1);
            service.generateTimeslots(
                    DESK_ID, d, d.plusDays(2),
                    LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, 60);

            assertThat(savedRows).hasSize(72);
            assertThat(savedRows).allSatisfy(ts ->
                    assertThat(ts.getBusinessDate()).isEqualTo(ts.getDate()));
        }
    }
}
