package com.wfm.repository;

import com.wfm.config.TenantContext;
import com.wfm.model.Desk;
import com.wfm.model.ErlangDemandInput;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.support.PostgresBackedTest;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V56 against a REAL Postgres: the table the H2 profile builds from the entity cannot prove the
 * migration's own FK cascade, its unique key, or that {@code ddl-auto=validate} accepts its column
 * types.
 */
class ErlangDemandInputPostgresTest extends PostgresBackedTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private DeskRepository deskRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private TimeslotRepository timeslotRepository;
    @Autowired
    private ErlangDemandInputRepository erlangDemandInputRepository;
    @Autowired
    private TestEntityManager entityManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private record Fixture(UUID deskId, Specialization spec, Timeslot slot) {
    }

    private Fixture seed() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT);
        desk.setName("Erlang " + UUID.randomUUID());
        UUID deskId = deskRepository.save(desk).getId();

        Specialization spec = new Specialization();
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName("S1");
        spec = specializationRepository.save(spec);

        Timeslot ts = new Timeslot();
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setDate(DAY);
        ts.setStartTime(LocalTime.of(8, 0));
        ts.setEndTime(LocalTime.of(9, 0));
        ts.setBusinessDate(DayWindow.businessDateOf(LocalTime.MIDNIGHT, DAY, LocalTime.of(8, 0)));
        ts = timeslotRepository.save(ts);
        return new Fixture(deskId, spec, ts);
    }

    private ErlangDemandInput input(Fixture fx) {
        ErlangDemandInput in = new ErlangDemandInput();
        in.setTenantId(TENANT);
        in.setDeskId(fx.deskId());
        in.setTimeslotId(fx.slot().getId());
        in.setSpecializationId(fx.spec().getId());
        in.setModel(StaffingSource.ERLANG_X);
        in.setCallVolume(100);
        in.setAht(180.5);
        in.setServiceLevelTarget(80);
        in.setServiceLevelThreshold(20);
        in.setPatience(90.0);
        in.setRetryRate(25.0);
        in.setShrinkage(0.3);
        in.setMaxOccupancy(0.85);
        in.setConcurrency(null);
        return in;
    }

    @Test
    @DisplayName("every column round-trips on real Postgres, nulls included")
    void roundTripsEveryColumn() {
        Fixture fx = seed();
        ErlangDemandInput saved = erlangDemandInputRepository.saveAndFlush(input(fx));
        entityManager.clear();

        ErlangDemandInput read = erlangDemandInputRepository.findById(saved.getId()).orElseThrow();
        assertThat(read.getTenantId()).isEqualTo(TENANT);
        assertThat(read.getDeskId()).isEqualTo(fx.deskId());
        assertThat(read.getTimeslotId()).isEqualTo(fx.slot().getId());
        assertThat(read.getSpecializationId()).isEqualTo(fx.spec().getId());
        assertThat(read.getModel()).isEqualTo(StaffingSource.ERLANG_X);
        assertThat(read.getCallVolume()).isEqualTo(100);
        assertThat(read.getAht()).isEqualTo(180.5);
        assertThat(read.getServiceLevelTarget()).isEqualTo(80);
        assertThat(read.getServiceLevelThreshold()).isEqualTo(20);
        assertThat(read.getPatience()).isEqualTo(90.0);
        assertThat(read.getRetryRate()).isEqualTo(25.0);
        assertThat(read.getShrinkage()).isEqualTo(0.3);
        assertThat(read.getMaxOccupancy()).isEqualTo(0.85);
        assertThat(read.getConcurrency()).isNull();
    }

    @Test
    @DisplayName("deleting the timeslot cascades the saved input away (FK ON DELETE CASCADE)")
    void timeslotDeleteCascades() {
        Fixture fx = seed();
        erlangDemandInputRepository.saveAndFlush(input(fx));
        assertThat(erlangDemandInputRepository.findByTenantIdAndDeskIdAndTimeslotIdIn(
                TENANT, fx.deskId(), List.of(fx.slot().getId()))).hasSize(1);

        timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndIdIn(
                TENANT, fx.deskId(), List.of(fx.slot().getId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(erlangDemandInputRepository.findByTenantIdAndDeskIdAndTimeslotIdIn(
                TENANT, fx.deskId(), List.of(fx.slot().getId()))).isEmpty();
    }

    @Test
    @DisplayName("a second row for the same tenant, desk, timeslot and specialization is refused")
    void uniqueKeyIsEnforced() {
        Fixture fx = seed();
        erlangDemandInputRepository.saveAndFlush(input(fx));

        assertThatThrownBy(() -> erlangDemandInputRepository.saveAndFlush(input(fx)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
