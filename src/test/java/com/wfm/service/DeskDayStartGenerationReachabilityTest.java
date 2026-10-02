package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Desk;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-02/SOLV-01: proves the generation-time tiling refusal ({@link
 * TimeslotGeneratorService#requireDayStartTiles}) is REACHABLE through the real
 * save-then-generate path, not merely unit-tested in isolation against the static method (see
 * {@code TimeslotGeneratorServiceTest}'s existing "requireDayStartTiles" nested class, which
 * calls it directly).
 *
 * <p>Before this phase no desk could hold a non-midnight day start, so this refusal -- written
 * and unit-tested in an earlier phase -- had never been reached by any real caller chain. This
 * test drives {@link DeskService#setDayStart} (the save path) and then {@link
 * TimeslotGeneratorService#generateTimeslots} (the real generation entry point {@code
 * FteUploadService} itself calls, passing the desk's own day start) to prove the refusal fires
 * end to end -- it does not call {@code requireDayStartTiles} directly, and it does not write the
 * desk row directly; both would prove reachability through a path no operator can take.
 *
 * <p>A positive control (21:30 tiles a 30-minute increment, since 1290 is a whole multiple of 30)
 * sits alongside the negative case (21:15 does not, since 1275 is not a whole multiple of 30) so
 * a reader cannot mistake "fires on everything" for "fires correctly".
 *
 * <p>Written RED: at the commit that creates this file, {@code DeskService.setDayStart} still
 * refuses any value other than {@code 00:00}, so setting the day start to 21:15 or 21:30 below
 * fails at the save step, before generation is ever reached. Both cases go green together with
 * {@code DeskServiceDayStartTest}'s rewritten gate cases, on this plan's final commit.
 *
 * <p>Reuses {@code DeskServiceDayStartTest}'s {@code @DataJpaTest}/{@code @MockitoBean} wiring
 * shape for tenant context, desk creation and cleanup, rather than inventing a second style.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, TimeslotGeneratorService.class})
@ActiveProfiles("test")
class DeskDayStartGenerationReachabilityTest {

    private static final long TENANT_A = 1L;

    @Autowired
    private DeskService deskService;

    @Autowired
    private TimeslotGeneratorService timeslotGeneratorService;

    @Autowired
    private DeskRepository deskRepository;

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
    void dayStart2115_with30MinuteIncrement_refusedAtGeneration_namingDayStartIncrementAndReason() {
        Desk desk = saveDesk();
        deskService.setDayStart(desk.getId(), LocalTime.of(21, 15));
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();

        assertThatThrownBy(() -> timeslotGeneratorService.generateTimeslots(
                reloaded.getId(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                reloaded.getDayStart(), LocalTime.of(8, 0), LocalTime.of(17, 0), 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21:15")
                .hasMessageContaining("30")
                .hasMessageContaining("tile");
    }

    @Test
    void dayStart2130_with30MinuteIncrement_generationSucceeds_positiveControl() {
        Desk desk = saveDesk();
        deskService.setDayStart(desk.getId(), LocalTime.of(21, 30));
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();

        List<Timeslot> result = timeslotGeneratorService.generateTimeslots(
                reloaded.getId(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                reloaded.getDayStart(), LocalTime.of(8, 0), LocalTime.of(17, 0), 30);

        assertThat(result).isNotEmpty();
    }

    private Desk saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        return deskRepository.save(desk);
    }
}
