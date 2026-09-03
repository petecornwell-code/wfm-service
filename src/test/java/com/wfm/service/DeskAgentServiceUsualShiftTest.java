package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.DayOffType;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * D-16's four reachable states (all computed server-side), P-07's RETIRED-first precedence,
 * P-08's not-worked rule, and D-05's read-side, never-blocking hours advisory (plan 16-02 Task 2).
 * Task 3 extends this class with D-12 desk-move / USHF-05 coverage.
 */
@DataJpaTest
@Import({DeskAgentService.class, UsualShiftService.class, UsualShiftResolutionService.class})
@ActiveProfiles("test")
class DeskAgentServiceUsualShiftTest {

    @Autowired
    private DeskAgentService deskAgentService;

    @Autowired
    private UsualShiftService usualShiftService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    @Autowired
    private AgentUsualShiftRepository agentUsualShiftRepository;

    @Autowired
    private AgentDayHoursRepository agentDayHoursRepository;

    private static final long TENANT_ID = 1L;

    private Desk desk;
    private Agent agent;
    private ShiftTemplate early;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        desk = saveDesk("Support Desk");
        agent = saveAgent(desk.getId());
        // Valid every weekday, zero bands -> getNetHours(0) = 9.00h (17:00 - 8:00 envelope).
        early = saveTemplate("Early", Set.of(DayOfWeek.values()), LocalDate.now().minusDays(1), null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------- D-16 four-state reachability ----------

    @Test
    void allFourStates_reachableOnOneAgentInOneResponse() {
        // MONDAY: no row -> NOT_SET (left untouched).
        // TUESDAY: live era, no day-hours row (worked by default) -> LIVE.
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.TUESDAY, early.getId(), false);
        // WEDNESDAY: only era is retired -> STORED_INACTIVE/RETIRED.
        ShiftTemplate retired = saveTemplate("Retired", Set.of(DayOfWeek.WEDNESDAY),
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        saveUsualShiftRow(DayOfWeek.WEDNESDAY, retired);
        // THURSDAY: live era, but the agent's day-hours row is MANDATORY -> STORED_INACTIVE/NOT_WORKED.
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.THURSDAY, early.getId(), false);
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.THURSDAY, null, DayOffType.MANDATORY, false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.NOT_SET);
        assertThat(response.usualShift().get(DayOfWeek.TUESDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.LIVE);
        assertThat(response.usualShift().get(DayOfWeek.WEDNESDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE);
        assertThat(response.usualShift().get(DayOfWeek.WEDNESDAY).reason())
                .isEqualTo(DeskAgentResponse.UsualShiftReason.RETIRED);
        assertThat(response.usualShift().get(DayOfWeek.THURSDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE);
        assertThat(response.usualShift().get(DayOfWeek.THURSDAY).reason())
                .isEqualTo(DeskAgentResponse.UsualShiftReason.NOT_WORKED);
    }

    @Test
    void notWorked_zeroHoursPersistedRow_usesSignumNotEquals() {
        // P-08: a real stored 0.00 (NUMERIC(5,2)) must trip signum()==0, which
        // equals(BigDecimal.ZERO) (scale 0) would miss.
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.FRIDAY, early.getId(), false);
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.FRIDAY, BigDecimal.ZERO, null, false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.FRIDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE);
        assertThat(response.usualShift().get(DayOfWeek.FRIDAY).reason())
                .isEqualTo(DeskAgentResponse.UsualShiftReason.NOT_WORKED);
    }

    @Test
    void live_noAgentDayHoursRow_resolvesToScheduleDefault_staysWorkedAndLive() {
        // P-08: NO row is not "not worked" -- it resolves to the schedule default (positive), so
        // the weekday stays LIVE.
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.SATURDAY, early.getId(), false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.SATURDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.LIVE);
    }

    @Test
    void precedence_retiredAndPto_reportsRetiredNotNotWorked() {
        // P-07: RETIRED wins over NOT_WORKED when both hold.
        ShiftTemplate retired = saveTemplate("Retired", Set.of(DayOfWeek.SUNDAY),
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        saveUsualShiftRow(DayOfWeek.SUNDAY, retired);
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.SUNDAY, null, DayOffType.PTO, false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.SUNDAY).reason())
                .isEqualTo(DeskAgentResponse.UsualShiftReason.RETIRED);
    }

    // ---------- D-04 storage and orthogonality ----------

    @Test
    void d04_storageOnAMandatoryWeekday_succeedsAndPersists() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, DayOffType.MANDATORY, false);

        assertThatCode(() ->
                usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false))
                .doesNotThrowAnyException();

        assertThat(agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.MONDAY)).isPresent();
    }

    @Test
    void d04_orthogonality_neitherWriteCrossesIntoTheOtherTable() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        Map<UUID, BigDecimal> dayHoursBefore = dayHoursSnapshot();

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.TUESDAY, early.getId(), false);

        assertThat(dayHoursSnapshot()).isEqualTo(dayHoursBefore);

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.WEDNESDAY, early.getId(), false);
        Map<UUID, UUID> usualShiftBefore = usualShiftSnapshot();

        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.THURSDAY, new BigDecimal("5"), null, false);

        assertThat(usualShiftSnapshot()).isEqualTo(usualShiftBefore);
    }

    // ---------- USHF-04 ----------

    @Test
    void noStoredRows_allSevenAreNotSet_noSubstitutedTemplateName() {
        Agent freshAgent = saveAgent(desk.getId());

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), freshAgent.getId());

        for (DayOfWeek day : DayOfWeek.values()) {
            DeskAgentResponse.UsualShiftEntry entry = response.usualShift().get(day);
            assertThat(entry.status()).as("weekday %s", day).isEqualTo(DeskAgentResponse.UsualShiftStatus.NOT_SET);
            assertThat(entry.name()).as("weekday %s", day).isNull();
            assertThat(entry.reason()).as("weekday %s", day).isNull();
        }
    }

    @Test
    void name_isTheRawStoredTemplateName_inBothLiveAndStoredInactiveStates() {
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);
        ShiftTemplate retired = saveTemplate("Retired Name", Set.of(DayOfWeek.TUESDAY),
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        saveUsualShiftRow(DayOfWeek.TUESDAY, retired);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).name()).isEqualTo("Early");
        assertThat(response.usualShift().get(DayOfWeek.TUESDAY).name()).isEqualTo("Retired Name");
    }

    // ---------- D-05 hours advisory ----------

    @Test
    void d05_advisoryFires_whenNoBandNetHoursMatchesContractedHours() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        ShiftTemplate nineHour = saveTemplate("NineHour", Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(1), null);
        saveBand(nineHour, 0, 0, null); // zero-duration band -> getNetHours(0) = 9.00h

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, nineHour.getId(), false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        DeskAgentResponse.UsualShiftEntry entry = response.usualShift().get(DayOfWeek.MONDAY);

        assertThat(entry.status()).isEqualTo(DeskAgentResponse.UsualShiftStatus.LIVE);
        assertThat(entry.hoursAdvisory()).isNotNull().contains("9").contains("8");
    }

    @Test
    void d05_advisoryStaysSilent_whenABandNetHoursMatchesExactly() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        ShiftTemplate eightHour = saveTemplate("EightHour", Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(1), null);
        saveBand(eightHour, 240, 60, null); // getNetHours(60) = 8.00h

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, eightHour.getId(), false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNull();
    }

    @Test
    void d05_anyBandQuantifier_aLaterMatchingBandSuppressesTheAdvisory() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        ShiftTemplate multiBand = saveTemplate("MultiBand", Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(1), null);
        saveBand(multiBand, 0, 0, null);    // FIRST by offset ascending -> net 9.00h (mismatch)
        saveBand(multiBand, 100, 60, null); // LATER band -> net 8.00h (match)

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, multiBand.getId(), false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNull();
    }

    @Test
    void d05_zeroBandTemplate_isMeasuredOnGetNetHoursZeroAlone() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        // 08:00-16:00, zero bands -> getNetHours(0) = 8.00h, matching the agent's contracted hours.
        ShiftTemplate eightHourEnvelope = saveTemplateWithTimes("EightHourEnvelope", Set.of(DayOfWeek.MONDAY),
                LocalTime.of(8, 0), LocalTime.of(16, 0), LocalDate.now().minusDays(1), null);

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, eightHourEnvelope.getId(), false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNull();
    }

    @Test
    void d05_neverBlocksTheWrite_aMismatchedTemplateStillPersists() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        ShiftTemplate nineHour = saveTemplate("NineHourBlockCheck", Set.of(DayOfWeek.MONDAY),
                LocalDate.now().minusDays(1), null);

        assertThatCode(() ->
                usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, nineHour.getId(), false))
                .doesNotThrowAnyException();

        assertThat(agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.MONDAY)).isPresent();
    }

    @Test
    void d05_isNull_forNotSet() {
        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());

        assertThat(response.usualShift().get(DayOfWeek.MONDAY).status())
                .isEqualTo(DeskAgentResponse.UsualShiftStatus.NOT_SET);
        assertThat(response.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNull();
    }

    @Test
    void d05_isNull_forNotWorked_evenWithAMismatchedTemplate() {
        ShiftTemplate nineHour = saveTemplate("NineHourNotWorked", Set.of(DayOfWeek.MONDAY),
                LocalDate.now().minusDays(1), null);
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, nineHour.getId(), false);
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, DayOffType.MANDATORY, false);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        DeskAgentResponse.UsualShiftEntry entry = response.usualShift().get(DayOfWeek.MONDAY);

        assertThat(entry.status()).isEqualTo(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE);
        assertThat(entry.reason()).isEqualTo(DeskAgentResponse.UsualShiftReason.NOT_WORKED);
        assertThat(entry.hoursAdvisory()).isNull();
    }

    @Test
    void d05_surfacesAMismatchIntroducedAfterTheWrite_byALaterContractedHoursEdit() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("8"), null, false);
        ShiftTemplate eightHour = saveTemplate("EightHourLater", Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(1), null);
        saveBand(eightHour, 240, 60, null); // getNetHours(60) = 8.00h -- matches at write time
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, eightHour.getId(), false);

        DeskAgentResponse beforeEdit = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        assertThat(beforeEdit.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNull();

        // The contracted-hours edit goes through the unrelated hours choke point -- the usual
        // shift row itself is never touched, yet the mismatch it creates becomes visible.
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, new BigDecimal("6"), null, false);

        DeskAgentResponse afterEdit = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        assertThat(afterEdit.usualShift().get(DayOfWeek.MONDAY).hoursAdvisory()).isNotNull();
        assertThat(afterEdit.usualShift().get(DayOfWeek.MONDAY).name()).isEqualTo("EightHourLater");
    }

    // ---------- helpers ----------

    private Map<UUID, BigDecimal> dayHoursSnapshot() {
        return agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId()).stream()
                .collect(Collectors.toMap(AgentDayHours::getId, AgentDayHours::getHours));
    }

    private Map<UUID, UUID> usualShiftSnapshot() {
        return agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId()).stream()
                .collect(Collectors.toMap(AgentUsualShift::getId, u -> u.getShiftTemplate().getId()));
    }

    private void saveUsualShiftRow(DayOfWeek day, ShiftTemplate template) {
        AgentUsualShift row = new AgentUsualShift();
        row.setTenantId(TENANT_ID);
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setShiftTemplate(template);
        agentUsualShiftRepository.save(row);
    }

    private ShiftTemplate saveTemplate(String name, Set<DayOfWeek> weekdays,
                                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        return saveTemplateWithTimes(name, weekdays, LocalTime.of(8, 0), LocalTime.of(17, 0),
                effectiveFrom, effectiveTo);
    }

    private ShiftTemplate saveTemplateWithTimes(String name, Set<DayOfWeek> weekdays,
                                                 LocalTime startTime, LocalTime endTime,
                                                 LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_ID);
        template.setDeskId(desk.getId());
        template.setName(name);
        template.setStartTime(startTime);
        template.setEndTime(endTime);
        template.setValidWeekdays(weekdays);
        template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(effectiveTo);
        return shiftTemplateRepository.save(template);
    }

    private void saveBand(ShiftTemplate template, int offsetMinutes, int durationMinutes, Integer capacity) {
        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setTenantId(TENANT_ID);
        band.setShiftTemplate(template);
        band.setOffsetMinutes(offsetMinutes);
        band.setDurationMinutes(durationMinutes);
        band.setCapacity(capacity);
        shiftTemplateBreakBandRepository.save(band);
    }

    private Desk saveDesk(String name) {
        Desk d = new Desk();
        d.setTenantId(TENANT_ID);
        d.setName(name);
        return deskRepository.save(d);
    }

    private Agent saveAgent(UUID deskId) {
        Agent a = new Agent();
        a.setTenantId(TENANT_ID);
        a.setDeskId(deskId);
        a.setBamboohrId("B" + UUID.randomUUID());
        a.setName("Jane Doe");
        return agentRepository.save(a);
    }
}
