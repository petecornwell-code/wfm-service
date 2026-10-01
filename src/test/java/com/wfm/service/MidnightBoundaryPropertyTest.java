package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftTemplateController;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.support.AssertsTodaysBehaviour;
import com.wfm.util.DayWindow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * BDAY-06's plain-unit midnight-boundary scenarios -- this class builds no solver solution object
 * and imports no optimiser type anywhere, because none of these properties is genuinely
 * constraint-level (only the {@code @DataJpaTest} harness below is "real" infrastructure, and it
 * is {@link ShiftTemplateService}'s own save path, not the solver).
 *
 * <p>The {@code @DataJpaTest} harness here is the same one {@code ShiftTemplateServiceTest} uses
 * -- carried on the whole class so the save-path scenario has it, even though the plain
 * {@link DayWindow} and {@link SolverService#resolveEffectiveHours} scenarios below need no
 * Spring context at all.
 */
@DataJpaTest
@Import({ShiftTemplateService.class, ShiftTemplateController.class})
@ActiveProfiles("test")
class MidnightBoundaryPropertyTest {

    @Autowired
    private ShiftTemplateService service;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    private static final long TENANT = 1L;

    /**
     * The nine midnight-implicit {@link DayWindow} statics this class called directly are private
     * as of BDAY-04 (plan 19-08, D-06); every call site below binds this midnight-anchored
     * instance instead, per D-14. No asserted value changes -- at a {@code 00:00} anchor every
     * {@code anchored*} instance method agrees exactly with its retired static counterpart.
     */
    private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        // No live timeslot grid to align against -- the save-path scenarios below test band
        // geometry and forward-interval validation, not grid alignment (BDAY-06).
        when(timeslotGeneratorService.getLiveBounds(any())).thenReturn(java.util.Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("a 23:00-to-00:00 slot")
    class FinalHourSlot {

        private static final LocalTime SLOT_START = LocalTime.of(23, 0);
        private static final LocalTime SLOT_END = LocalTime.MIDNIGHT;

        @Test
        @DisplayName("its length is exactly 60 minutes")
        void lengthIsExactlySixtyMinutes() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(SLOT_START, SLOT_END)).isEqualTo(60);
        }

        @Test
        @DisplayName("its end in an END position is minute 1440 while its start is minute 1380")
        void endIs1440StartIs1380() {
            assertThat(MIDNIGHT_WINDOW.anchoredEndMinute(SLOT_END)).isEqualTo(1440);
            assertThat(MIDNIGHT_WINDOW.anchoredStartMinute(SLOT_START)).isEqualTo(1380);
        }

        @Test
        @DisplayName("it does not overlap a 22:00-to-23:00 slot (touching, not overlapping)")
        void doesNotOverlapTheTouchingPriorSlot() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(SLOT_START, SLOT_END, LocalTime.of(22, 0), LocalTime.of(23, 0)))
                    .isFalse();
        }

        @Test
        @DisplayName("it does overlap a 22:30-to-23:30 slot")
        void overlapsAGenuinelyStraddlingSlot() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(SLOT_START, SLOT_END, LocalTime.of(22, 30), LocalTime.of(23, 30)))
                    .isTrue();
        }

        @Test
        @DisplayName("a 23:00 start is strictly before a 00:00 end")
        void startIsStrictlyBeforeTheEnd() {
            assertThat(MIDNIGHT_WINDOW.anchoredStartsBefore(SLOT_START, SLOT_END)).isTrue();
        }
    }

    @Nested
    @DisplayName("an envelope flush to end-of-day, through the real save path")
    class EnvelopeFlushToEndOfDay {

        private static final LocalTime ENVELOPE_START = LocalTime.of(15, 0);
        private static final LocalTime ENVELOPE_END = LocalTime.MIDNIGHT;

        @Test
        @DisplayName("the envelope is exactly 540 minutes")
        void envelopeIsExactly540Minutes() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(ENVELOPE_START, ENVELOPE_END)).isEqualTo(540);
        }

        @Test
        @DisplayName("a band at offset 480 duration 60 finishes exactly flush to the envelope end and saves")
        void bandFlushToEnvelopeEnd_saves() {
            UUID deskId = saveDesk();
            ShiftTemplateRequest req = new ShiftTemplateRequest("Late", ENVELOPE_START, ENVELOPE_END,
                    List.of(new BreakBandRequest(480, 60, null)),
                    Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

            ShiftTemplate created = service.createShiftTemplate(deskId, req);

            assertThat(created.getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
            // 540 envelope minutes - 60 break minutes = 480 minutes = 8.00 net hours.
            assertThat(created.getNetHours(60, DayWindow.anchoredAt(LocalTime.MIDNIGHT)))
                    .isEqualByComparingTo(new BigDecimal("8.00"));
        }

        @Test
        @DisplayName("a band at offset 481 duration 60 is refused with the service's break-must-finish message")
        void bandOneMinutePastFlush_refused() {
            UUID deskId = saveDesk();
            ShiftTemplateRequest req = new ShiftTemplateRequest("Late", ENVELOPE_START, ENVELOPE_END,
                    List.of(new BreakBandRequest(481, 60, null)),
                    Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

            assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Shift template break must finish before the shift ends");
        }

        private UUID saveDesk() {
            Desk desk = new Desk();
            desk.setTenantId(TENANT);
            desk.setName("Desk " + UUID.randomUUID());
            return deskRepository.save(desk).getId();
        }
    }

    @Nested
    @DisplayName("contracted hours consumed by the starting weekday only")
    class ContractedHoursStartingWeekdayOnly {

        @Test
        @AssertsTodaysBehaviour(flippedBy = "OVNT-04",
                to = "the whole stretch of a midnight-spanning shift consumes only the starting "
                        + "weekday's contracted-hours row, not a second row for the calendar date "
                        + "it runs into")
        @DisplayName("today: a would-be midnight-spanning stretch's two calendar dates each draw from their own independent weekday row")
        void twoCalendarDatesDrawFromTwoIndependentWeekdayRows() {
            LocalDate monday = LocalDate.of(2026, 1, 5);
            LocalDate tuesday = monday.plusDays(1);
            assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(tuesday.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);

            Map<DayOfWeek, BigDecimal> dayHoursMap = Map.of(
                    DayOfWeek.MONDAY, new BigDecimal("8.00"),
                    DayOfWeek.TUESDAY, new BigDecimal("4.00"));
            BigDecimal scheduleDefault = new BigDecimal("8.00");

            // Argued: resolveEffectiveHours takes no notion of "the business day a stretch started
            // on" -- it looks up ONLY the DayOfWeek of the LocalDate it is given, independently,
            // every time. A midnight-spanning stretch's two calendar dates therefore draw from two
            // independent weekday rows today, which is the property OVNT-04 changes.
            BigDecimal mondayHours = SolverService.resolveEffectiveHours(Map.of(), dayHoursMap, monday, scheduleDefault);
            BigDecimal tuesdayHours = SolverService.resolveEffectiveHours(Map.of(), dayHoursMap, tuesday, scheduleDefault);

            assertThat(mondayHours).isEqualByComparingTo(new BigDecimal("8.00"));
            assertThat(tuesdayHours).isEqualByComparingTo(new BigDecimal("4.00"));
            assertThat(mondayHours).isNotEqualByComparingTo(tuesdayHours);
        }
    }

    @Nested
    @DisplayName("a shift starting before and ending after midnight")
    class ShiftCrossingMidnight {

        @Test
        @AssertsTodaysBehaviour(flippedBy = "OVNT-01",
                to = "an interval whose end is earlier in the clock than its start means the "
                        + "shift crosses the day anchor into the next calendar date, not that the "
                        + "interval is malformed")
        @DisplayName("today: the shift-template save path refuses such a template (OVNT-01 still governs this); "
                + "DayWindow's own backstop throw was already removed from public surface by BDAY-04 criterion 2 "
                + "(this phase) -- see the comment below")
        void durationMinutesThrowsAndTheSavePathRefuses() {
            // BDAY-04 criterion 2 (THIS phase, plan 19-08) already removed DayWindow's own
            // crossing-interval throw from its public surface: the anchored instance method never
            // throws for a backward interval, it wraps forward across the anchor instead (D-11).
            // 19-CONTEXT.md D-11 established that this was always a backstop only -- the save-path
            // refusal below (via isForwardWithinDay, exercised through the real service) is what
            // actually governs template creation, and it is UNCHANGED here; relaxing IT for a
            // genuinely spanning template is OVNT-01's job (Phase 21), which is what this test's
            // @AssertsTodaysBehaviour marker still names. Positive proof that DayWindow's own
            // crossing-the-anchor composition already yields a value rather than throwing:
            assertThat(DayWindow.anchoredAt(LocalTime.MIDNIGHT)
                    .anchoredDurationMinutes(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                    .as("22:00 -> 06:00 now wraps forward across the anchor (8 hours) instead of throwing")
                    .isEqualTo(480);

            Desk desk = new Desk();
            desk.setTenantId(TENANT);
            desk.setName("Desk " + UUID.randomUUID());
            UUID deskId = deskRepository.save(desk).getId();

            ShiftTemplateRequest req = new ShiftTemplateRequest("Overnight", LocalTime.of(22, 0), LocalTime.of(6, 0),
                    List.of(), Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

            assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Shift template end time must be after its start time");
        }
    }
}
