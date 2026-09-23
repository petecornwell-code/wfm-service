package com.wfm.repository;

import com.wfm.config.TenantContext;
import com.wfm.model.Desk;
import com.wfm.model.Timeslot;
import com.wfm.service.TimeslotGeneratorService;
import com.wfm.support.PostgresBackedTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The midnight boundary against a REAL Postgres, because the two things it proves cannot be
 * proven anywhere else in this suite.
 *
 * <ul>
 *   <li>{@link TimeslotRepository#findLiveBoundsByDeskRaw} is Postgres-only SQL
 *       ({@code EXTRACT(EPOCH FROM ...)}, {@code ::int}, a {@code CASE} on a {@code TIME}
 *       literal) that H2 cannot execute, so {@code ShiftTemplateServiceTest} mocks it out
 *       entirely. Its previous form returned two WRONG answers for a desk ending at midnight
 *       and no test could see it: {@code MAX(end_time)} reported 23:00 because SQL orders
 *       {@code 00:00} first, and {@code MIN(end_time - start_time)} reported -1380 minutes for
 *       the final slot, which then became the desk's increment.</li>
 *   <li>Generation writes a real row whose {@code end_time} is {@code 00:00} while its
 *       {@code start_time} is {@code 23:00} — an end that sorts before its own start. The unique
 *       index on {@code (tenant_id, desk_id, date, start_time, end_time)} has to accept it.</li>
 * </ul>
 *
 * <p>A new Postgres-backed class is a cost this project deliberately keeps low (see
 * {@link PostgresBackedTest}'s lifecycle note). It is spent here because the changed SQL has no
 * other home: the existing three classes cover agents, usual shifts and constraint weights, and
 * folding timeslot-bounds assertions into any of them would hide them.
 */
@Import(TimeslotGeneratorService.class)
class MidnightTimeslotPostgresTest extends PostgresBackedTest {

    private static final long TENANT_ID = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);


    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private TimeslotGeneratorService timeslotGeneratorService;

    @Autowired
    private TestEntityManager entityManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_ID);
        desk.setName("Vinted " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    @Test
    @DisplayName("an 08:00-00:00 day generates 16 hourly slots, the last of them 23:00-00:00")
    void generatesAFullMidnightEndingDay() {
        TenantContext.setTenantId(TENANT_ID);
        UUID deskId = saveDesk();

        List<Timeslot> created = timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(8, 0), LocalTime.MIDNIGHT, 60);
        entityManager.flush();

        assertThat(created).hasSize(16);
        assertThat(created).extracting(Timeslot::getStartTime)
                .startsWith(LocalTime.of(8, 0))
                .endsWith(LocalTime.of(23, 0));
        assertThat(created).filteredOn(t -> t.getStartTime().equals(LocalTime.of(23, 0)))
                .singleElement()
                .extracting(Timeslot::getEndTime)
                .isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("regenerating the same midnight-ending day is a no-op, not a full rebuild")
    void regenerationIsIdempotent() {
        TenantContext.setTenantId(TENANT_ID);
        UUID deskId = saveDesk();

        List<Timeslot> first = timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(8, 0), LocalTime.MIDNIGHT, 60);
        entityManager.flush();
        List<Timeslot> second = timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(8, 0), LocalTime.MIDNIGHT, 60);

        // isDesired used to judge every slot on such a day obsolete, so the second call would
        // delete all 16 and recreate them -- taking any linked staffing requirements with them.
        assertThat(second).hasSize(16);
        assertThat(second).extracting(Timeslot::getId)
                .containsExactlyInAnyOrderElementsOf(first.stream().map(Timeslot::getId).toList());
    }

    @Test
    @DisplayName("the bounds query reports the day ending at midnight, not at 23:00")
    void boundsReportMidnightEnd() {
        TenantContext.setTenantId(TENANT_ID);
        UUID deskId = saveDesk();
        timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(8, 0), LocalTime.MIDNIGHT, 60);
        entityManager.flush();
        entityManager.clear();

        var bounds = timeslotGeneratorService.getLiveBounds(deskId).orElseThrow();

        assertThat(bounds.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(bounds.endTime()).isEqualTo(LocalTime.MIDNIGHT);
        // MIN(end_time - start_time) over the 23:00-00:00 slot used to yield -1380 here.
        assertThat(bounds.incrementMinutes()).isEqualTo(60);
        assertThat(bounds.periodStart()).isEqualTo(DAY);
        assertThat(bounds.periodEnd()).isEqualTo(DAY);
    }

    @Test
    @DisplayName("an ordinary 08:00-17:00 day still reports its own bounds unchanged")
    void boundsUnaffectedForAnOrdinaryDay() {
        TenantContext.setTenantId(TENANT_ID);
        UUID deskId = saveDesk();
        timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(8, 0), LocalTime.of(17, 0), 30);
        entityManager.flush();
        entityManager.clear();

        var bounds = timeslotGeneratorService.getLiveBounds(deskId).orElseThrow();

        assertThat(bounds.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(bounds.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(bounds.incrementMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("a window crossing midnight is still refused")
    void crossingMidnightStillRefused() {
        TenantContext.setTenantId(TENANT_ID);
        UUID deskId = saveDesk();

        assertThatThrownBy(() -> timeslotGeneratorService.generateTimeslots(
                deskId, DAY, DAY, LocalTime.of(22, 0), LocalTime.of(6, 0), 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time range must be positive");
    }
}
