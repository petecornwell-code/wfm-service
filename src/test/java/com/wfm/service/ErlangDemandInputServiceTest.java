package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErlangCRequest;
import com.wfm.dto.ErlangDemandInputResponse;
import com.wfm.dto.ErlangXRequest;
import com.wfm.dto.StaffingAdjustmentOptionsDto;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ErlangDemandInputRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * quick-261008-f51 (OD-3): the inputs each Erlang calculation saves per timeslot and
 * specialization, for the source date and every copyTo date, and the GET that reloads them.
 */
@DataJpaTest
@ActiveProfiles("test")
class ErlangDemandInputServiceTest {

    private static final long TENANT = 1L;
    private static final LocalDate D1 = LocalDate.of(2026, 10, 5);

    @Autowired
    private StaffingRequirementRepository staffingRequirementRepository;
    @Autowired
    private TimeslotRepository timeslotRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private DeskRepository deskRepository;
    @Autowired
    private ErlangDemandInputRepository erlangDemandInputRepository;
    @Autowired
    private TestEntityManager testEntityManager;

    private StaffingRequirementService service;
    private UUID deskId;
    private Specialization spec;
    private final List<Timeslot> firsts = new java.util.ArrayList<>();
    private final List<Timeslot> seconds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new StaffingRequirementService(
                staffingRequirementRepository, timeslotRepository, specializationRepository,
                new ErlangCalculatorService(), deskRepository, testEntityManager.getEntityManager(),
                erlangDemandInputRepository);

        Desk desk = new Desk();
        desk.setTenantId(TENANT);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDayStart(LocalTime.MIDNIGHT);
        deskId = deskRepository.save(desk).getId();

        Specialization s = new Specialization();
        s.setTenantId(TENANT);
        s.setDeskId(deskId);
        s.setName("S1");
        spec = specializationRepository.save(s);

        for (int i = 0; i < 3; i++) {
            LocalDate d = D1.plusDays(i);
            firsts.add(slot(d, LocalTime.of(8, 0), LocalTime.of(9, 0)));
            seconds.add(slot(d, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Timeslot slot(LocalDate date, LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(end);
        ts.setBusinessDate(DayWindow.businessDateOf(LocalTime.MIDNIGHT, date, start));
        return timeslotRepository.save(ts);
    }

    private ErlangXRequest erlangX(int callVolume, StaffingAdjustmentOptionsDto adj, List<LocalDate> copyTo) {
        return new ErlangXRequest(D1, List.of(
                new ErlangXRequest.Item(firsts.get(0).getId(), spec.getId(), callVolume, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(seconds.get(0).getId(), spec.getId(), callVolume, 180, 90, 25, 80, 20)),
                adj, copyTo);
    }

    @Test
    @DisplayName("Erlang X saves its inputs for the source date and each copyTo date, on each date's own timeslots")
    void savesInputsForSourceAndCopyDates() {
        service.calculateErlangX(deskId,
                erlangX(100, new StaffingAdjustmentOptionsDto(0.30, 0.85, null), List.of(D1.plusDays(2))));

        ErlangDemandInputResponse d1 = service.getErlangInputs(deskId, D1.toString());
        assertThat(d1.items()).hasSize(2);
        assertThat(d1.items()).allSatisfy(i -> {
            assertThat(i.model()).isEqualTo("ERLANG_X");
            assertThat(i.callVolume()).isEqualTo(100);
            assertThat(i.aht()).isEqualTo(180);
            assertThat(i.serviceLevelTarget()).isEqualTo(80);
            assertThat(i.serviceLevelThreshold()).isEqualTo(20);
            assertThat(i.patience()).isEqualTo(90);
            assertThat(i.retryRate()).isEqualTo(25);
            assertThat(i.shrinkage()).isEqualTo(0.30);
            assertThat(i.maxOccupancy()).isEqualTo(0.85);
            assertThat(i.concurrency()).isNull();
        });
        assertThat(d1.items()).extracting(ErlangDemandInputResponse.Item::timeslotId)
                .containsExactly(firsts.get(0).getId(), seconds.get(0).getId());
        assertThat(d1.items()).extracting(ErlangDemandInputResponse.Item::startTime)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(9, 0));
        assertThat(d1.items()).extracting(ErlangDemandInputResponse.Item::endTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));

        ErlangDemandInputResponse d3 = service.getErlangInputs(deskId, D1.plusDays(2).toString());
        assertThat(d3.items()).extracting(ErlangDemandInputResponse.Item::timeslotId)
                .containsExactly(firsts.get(2).getId(), seconds.get(2).getId());
        assertThat(d3.items()).allSatisfy(i -> {
            assertThat(i.model()).isEqualTo("ERLANG_X");
            assertThat(i.callVolume()).isEqualTo(100);
            assertThat(i.shrinkage()).isEqualTo(0.30);
        });

        assertThat(service.getErlangInputs(deskId, D1.plusDays(1).toString()).items()).isEmpty();
    }

    @Test
    @DisplayName("recalculating the source date replaces its inputs and leaves copy dates' inputs alone")
    void recalculationReplacesTheDatesInputs() {
        service.calculateErlangX(deskId, erlangX(100, null, List.of(D1.plusDays(2))));

        service.calculateErlangC(deskId, new ErlangCRequest(D1, List.of(
                new ErlangCRequest.Item(firsts.get(0).getId(), spec.getId(), 50, 180, 80, 20),
                new ErlangCRequest.Item(seconds.get(0).getId(), spec.getId(), 50, 180, 80, 20)),
                null, null));

        ErlangDemandInputResponse d1 = service.getErlangInputs(deskId, D1.toString());
        assertThat(d1.items()).hasSize(2);
        assertThat(d1.items()).allSatisfy(i -> {
            assertThat(i.model()).isEqualTo("ERLANG_C");
            assertThat(i.callVolume()).isEqualTo(50);
            assertThat(i.patience()).isNull();
            assertThat(i.retryRate()).isNull();
        });

        ErlangDemandInputResponse d3 = service.getErlangInputs(deskId, D1.plusDays(2).toString());
        assertThat(d3.items()).hasSize(2);
        assertThat(d3.items()).allSatisfy(i -> {
            assertThat(i.model()).isEqualTo("ERLANG_X");
            assertThat(i.callVolume()).isEqualTo(100);
        });
    }

    @Test
    @DisplayName("a refused request leaves the date's saved inputs unchanged")
    void refusedRequestKeepsSavedInputs() {
        service.calculateErlangX(deskId, erlangX(100, null, null));

        assertThatThrownBy(() -> service.calculateErlangX(deskId,
                erlangX(7, null, List.of(LocalDate.of(2027, 1, 1)))))
                .isInstanceOf(IllegalArgumentException.class);

        ErlangDemandInputResponse d1 = service.getErlangInputs(deskId, D1.toString());
        assertThat(d1.items()).hasSize(2);
        assertThat(d1.items()).allSatisfy(i -> assertThat(i.callVolume()).isEqualTo(100));
    }

    @Test
    @DisplayName("a malformed business date is a validation error naming the parameter")
    void malformedBusinessDateIsRefused() {
        assertThatThrownBy(() -> service.getErlangInputs(deskId, "2026-13-45"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessDate");
    }
}
