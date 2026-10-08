package com.wfm.controller;

import com.wfm.config.TenantContext;
import com.wfm.dto.GenerateTimeslotsRequest;
import com.wfm.dto.TimeslotResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.service.DeskService;
import com.wfm.service.InMemoryScheduleStore;
import com.wfm.service.ShiftLibraryValidationService;
import com.wfm.service.TimeslotGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SOLV-01/D-02: proves {@link TimeslotController#generateTimeslots} itself -- the live REST entry
 * point already exposed by the frontend API client -- anchors generation on the desk's OWN stored
 * day start, resolved tenant-scoped, rather than the hardcoded midnight literal it read before
 * this plan. Drives the controller method directly on every case below; {@code
 * DeskDayStartGenerationReachabilityTest}'s own javadoc records that it proves reachability
 * through {@link TimeslotGeneratorService} instead, which is precisely the bypass this class
 * closes.
 *
 * <p>Written RED first: at the commit that creates this file, the controller still hardcodes
 * {@code LocalTime.MIDNIGHT} as the generation anchor, so the 21:00 business-day case below fails
 * with the generator's "Time range must be positive and evenly divisible by incrementMinutes"
 * refusal -- a 21:00-to-21:00 window measures zero minutes against a midnight anchor -- and the
 * 21:15 tiling-refusal case fails because no refusal fires at all against a midnight anchor. Both
 * observations are recorded in this plan's SUMMARY as the RED evidence.
 *
 * <p>Reuses {@code DeskDayStartGenerationReachabilityTest}'s {@code @DataJpaTest}/{@code
 * @MockitoBean} wiring shape for tenant context, desk creation and cleanup, additionally importing
 * {@link TimeslotController} itself so the autowired controller is the real bean with the real
 * dependency graph -- never a hand-constructed instance that could hide a wiring gap.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, TimeslotGeneratorService.class, TimeslotController.class})
@ActiveProfiles("test")
class TimeslotControllerDeskAnchorTest {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @Autowired
    private TimeslotController timeslotController;

    @Autowired
    private DeskService deskService;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private TimeslotRepository timeslotRepository;

    @MockitoBean
    private ShiftLibraryValidationService shiftLibraryValidationService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generate_2100AnchoredDesk_oneBusinessDay_returns24RowsAcrossTwoCalendarDates() {
        Desk desk = saveDesk();
        deskService.setDayStart(desk.getId(), LocalTime.of(21, 0));
        LocalDate businessDay = LocalDate.of(2026, 10, 1);

        ResponseEntity<List<TimeslotResponse>> response = timeslotController.generateTimeslots(
                desk.getId(),
                new GenerateTimeslotsRequest(businessDay, businessDay,
                        LocalTime.of(21, 0), LocalTime.of(21, 0), 60));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(24);

        List<Timeslot> persisted = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(TENANT_A, desk.getId());
        Set<LocalDate> businessDates = persisted.stream()
                .map(Timeslot::getBusinessDate).collect(Collectors.toSet());
        Set<LocalDate> calendarDates = persisted.stream()
                .map(Timeslot::getDate).collect(Collectors.toSet());
        assertThat(businessDates).containsExactly(businessDay);
        assertThat(calendarDates).containsExactlyInAnyOrder(businessDay, businessDay.plusDays(1));
        // quick-261008-f51: the response carries the business date the page groups Erlang dates by.
        assertThat(response.getBody()).extracting(TimeslotResponse::businessDate)
                .containsOnly(businessDay);
    }

    @Test
    void generate_2115AnchoredDesk_30MinuteIncrement_refusedNamingDayStartIncrementAndTile() {
        Desk desk = saveDesk();
        deskService.setDayStart(desk.getId(), LocalTime.of(21, 15));
        LocalDate day = LocalDate.of(2026, 1, 5);

        assertThatThrownBy(() -> timeslotController.generateTimeslots(
                desk.getId(),
                new GenerateTimeslotsRequest(day, day, LocalTime.of(8, 0), LocalTime.of(17, 0), 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21:15")
                .hasMessageContaining("30")
                .hasMessageContaining("tile");

        List<Timeslot> persisted = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(TENANT_A, desk.getId());
        assertThat(persisted).isEmpty();
    }

    @Test
    void generate_2130AnchoredDesk_30MinuteIncrement_succeeds_positiveControl() {
        Desk desk = saveDesk();
        deskService.setDayStart(desk.getId(), LocalTime.of(21, 30));
        LocalDate day = LocalDate.of(2026, 1, 5);

        ResponseEntity<List<TimeslotResponse>> response = timeslotController.generateTimeslots(
                desk.getId(),
                new GenerateTimeslotsRequest(day, day, LocalTime.of(8, 0), LocalTime.of(17, 0), 30));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void generate_crossTenantDeskId_refusedNotFound_noRowPersisted() {
        Desk desk = saveDesk();
        UUID deskId = desk.getId();
        LocalDate day = LocalDate.of(2026, 1, 5);

        TenantContext.setTenantId(TENANT_B);

        assertThatThrownBy(() -> timeslotController.generateTimeslots(
                deskId,
                new GenerateTimeslotsRequest(day, day, LocalTime.of(8, 0), LocalTime.of(17, 0), 30)))
                .isInstanceOf(EntityNotFoundException.class);

        List<Timeslot> persisted = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(TENANT_A, deskId);
        assertThat(persisted).isEmpty();
    }

    private Desk saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        return deskRepository.save(desk);
    }
}
