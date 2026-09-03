package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Agent;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-01/D-02/USHF-01/USHF-04 coverage for {@link UsualShiftResolutionService#resolve}, the ONE
 * era-resolution implementation for a stored usual-shift row. Complements {@link
 * UsualShiftTracerTest}, which proves the happy path end-to-end but not these edge cases.
 */
@DataJpaTest
@Import(UsualShiftResolutionService.class)
@ActiveProfiles("test")
class UsualShiftResolutionServiceTest {

    @Autowired
    private UsualShiftResolutionService service;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentUsualShiftRepository agentUsualShiftRepository;

    private static final long TENANT_ID = 1L;

    private Desk desk;
    private Agent agent;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        desk = saveDesk(TENANT_ID, "Support Desk");
        agent = saveAgent(TENANT_ID, desk.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void eraFollowing_storedRowPointsAtOldEra_resolvesToTheNewLiveEra() {
        // "Ana's usual shift is Early" (D-01's operator-language test): the stored FK points at
        // era A, but resolution follows the NAME -- era B (the live era) is what comes back.
        ShiftTemplate eraA = saveTemplate("Early", LocalDate.now().minusWeeks(2), LocalDate.now().minusDays(1));
        ShiftTemplate eraB = saveTemplate("Early", LocalDate.now(), null);
        AgentUsualShift stored = saveUsualShift(DayOfWeek.MONDAY, eraA);

        Optional<ShiftTemplate> resolved = service.resolve(stored, LocalDate.now());

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(eraB.getId());
    }

    @Test
    void retiredOutright_noSuccessor_resolvesEmpty_rowSurvivesUnchanged() {
        // D-02: no era of the stored name is effective on the asked-for date -- identical to
        // unset. The row itself is never deleted, rewritten, or re-pointed by the resolver.
        ShiftTemplate retired = saveTemplate("Retired",
                LocalDate.now().minusWeeks(4), LocalDate.now().minusDays(1));
        AgentUsualShift stored = saveUsualShift(DayOfWeek.TUESDAY, retired);
        UUID storedTemplateId = stored.getShiftTemplate().getId();

        Optional<ShiftTemplate> resolved = service.resolve(stored, LocalDate.now());

        assertThat(resolved).isEmpty();
        AgentUsualShift reread = agentUsualShiftRepository
                .findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.TUESDAY).orElseThrow();
        assertThat(reread.getShiftTemplate().getId()).isEqualTo(storedTemplateId);
    }

    @Test
    void nullStoredRow_resolvesEmpty() {
        // USHF-04: no penalty, no default -- resolve(null, ...) is simply empty.
        assertThat(service.resolve(null, LocalDate.now())).isEmpty();
    }

    @Test
    void touchingEras_bothMatchOnTheBoundaryDate_laterStartingOneWinsDeterministically() {
        // USHF-01/adjacency: era A's effectiveTo equals era B's effectiveFrom. isEffectiveOn is
        // inclusive at both ends, so BOTH match on that one date -- resolve must deterministically
        // prefer era B (the later-starting one), every time, not just once.
        LocalDate boundary = LocalDate.now();
        ShiftTemplate eraA = saveTemplate("Early", boundary.minusWeeks(4), boundary);
        ShiftTemplate eraB = saveTemplate("Early", boundary, null);
        AgentUsualShift stored = saveUsualShift(DayOfWeek.WEDNESDAY, eraA);

        for (int i = 0; i < 10; i++) {
            Optional<ShiftTemplate> resolved = service.resolve(stored, boundary);
            assertThat(resolved).isPresent();
            assertThat(resolved.get().getId()).isEqualTo(eraB.getId());
        }
    }

    @Test
    void encodingVariant_trailingSpaceLowercaseName_neverResolvesToTheOtherFamily() {
        // USHF-01/encoding: exact String equality, never EnrichedColumnLayout.normalize -- "Early"
        // and "early " (trailing space, lowercase) are two separate era families.
        ShiftTemplate early = saveTemplate("Early", LocalDate.now().minusDays(1), null);
        saveTemplate("early ", LocalDate.now().minusDays(1), null);
        AgentUsualShift stored = saveUsualShift(DayOfWeek.THURSDAY, early);

        Optional<ShiftTemplate> resolved = service.resolve(stored, LocalDate.now());

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getName()).isEqualTo("Early");
    }

    @Test
    void sameNameDifferentDesk_neverReturned() {
        Desk otherDesk = saveDesk(TENANT_ID, "Other Desk");
        ShiftTemplate onOtherDesk = new ShiftTemplate();
        onOtherDesk.setTenantId(TENANT_ID);
        onOtherDesk.setDeskId(otherDesk.getId());
        onOtherDesk.setName("Early");
        onOtherDesk.setStartTime(LocalTime.of(8, 0));
        onOtherDesk.setEndTime(LocalTime.of(17, 0));
        onOtherDesk.setValidWeekdays(Set.of(DayOfWeek.FRIDAY));
        onOtherDesk.setEffectiveFrom(LocalDate.now().minusDays(1));
        shiftTemplateRepository.save(onOtherDesk);

        ShiftTemplate onThisDesk = saveTemplate("Early", LocalDate.now().minusDays(1), null);
        AgentUsualShift stored = saveUsualShift(DayOfWeek.FRIDAY, onThisDesk);

        Optional<ShiftTemplate> resolved = service.resolve(stored, LocalDate.now());

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getDeskId()).isEqualTo(desk.getId());
    }

    // ---------- helpers ----------

    private ShiftTemplate saveTemplate(String name, LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_ID);
        template.setDeskId(desk.getId());
        template.setName(name);
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));
        template.setValidWeekdays(Set.of(DayOfWeek.values()));
        template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(effectiveTo);
        return shiftTemplateRepository.save(template);
    }

    private AgentUsualShift saveUsualShift(DayOfWeek day, ShiftTemplate template) {
        AgentUsualShift row = new AgentUsualShift();
        row.setTenantId(TENANT_ID);
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setShiftTemplate(template);
        return agentUsualShiftRepository.save(row);
    }

    private Desk saveDesk(long tenantId, String name) {
        Desk d = new Desk();
        d.setTenantId(tenantId);
        d.setName(name);
        return deskRepository.save(d);
    }

    private Agent saveAgent(long tenantId, UUID deskId) {
        Agent a = new Agent();
        a.setTenantId(tenantId);
        a.setDeskId(deskId);
        a.setBamboohrId("B" + UUID.randomUUID());
        a.setName("Ana");
        return agentRepository.save(a);
    }
}
