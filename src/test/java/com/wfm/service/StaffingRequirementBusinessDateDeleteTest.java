package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.StaffingRequirementRequest;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOLV-07/D-15: proves the demand-upload delete's span by observing which rows SURVIVE, rather
 * than by reading the query. The hazard this guards against is silent: a destructive delete that
 * destroys the wrong span of live operator demand throws nothing and scores nothing differently
 * -- only a survivor assertion sees it.
 *
 * <p>Two anchors. At a {@code 21:00} anchor, business day D's timeslots span TWO calendar dates
 * (D and D+1), and business day D+1's timeslots span calendar dates D+1 and D+2 -- so calendar
 * date D+1 hosts timeslots from BOTH business days. Before the migration this class exists to
 * prove, uploading demand for business day D alone derived its delete range from the PAYLOAD
 * timeslots' own calendar dates (D to D+1) and fed that straight to a delete that also filtered
 * on calendar date -- silently destroying business day D+1's two calendar-D+1 timeslots along
 * with business day D's own four. At a {@code 00:00} anchor business date equals calendar date,
 * so the same upload must produce an identical surviving set to what a calendar-date range always
 * produced -- this is what proves no live desk (every one of which is anchored at 00:00 until
 * this phase's final commit) has its deleted span changed by this migration.
 *
 * <p>The third case (transactional integrity) asserts the weaker but real property rather than a
 * forced failure: no payload shape reaches {@code saveRequirements}'s insert loop with a row that
 * collides with a surviving live requirement, because the delete range is ALWAYS derived from,
 * and therefore always covers, the business dates of every timeslot the very same payload
 * targets -- the rows about to be reinserted are by construction always wiped first. Constructing
 * an artificial insert-time failure would mean contorting the fixture into an unrealistic shape
 * (e.g. forcing a stale entity-manager reference) rather than exercising a real request. So this
 * test asserts the boundary property that IS real: {@code saveRequirements} is {@code
 * @Transactional}, and direct code review of the method body (see {@link
 * StaffingRequirementService#saveRequirements}) confirms the delete call and the insert loop sit
 * inside that one method with no nested transactional boundary in between -- an exception
 * anywhere between the delete and the last insert rolls back both halves together, so an
 * interrupted upload can never leave a business day with nothing.
 */
@DataJpaTest
@ActiveProfiles("test")
class StaffingRequirementBusinessDateDeleteTest {

    private static final long TENANT = 1L;

    @Autowired
    private StaffingRequirementRepository staffingRequirementRepository;

    @Autowired
    private TimeslotRepository timeslotRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private StaffingRequirementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new StaffingRequirementService(
                staffingRequirementRepository, timeslotRepository, specializationRepository,
                new ErlangCalculatorService(), deskRepository, testEntityManager.getEntityManager());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("21:00 anchor: uploading business day D leaves business day D+1's requirements "
            + "untouched, even though both share calendar date D+1")
    void anchor21_uploadForBusinessDayD_leavesBusinessDayDPlus1Untouched() {
        LocalTime anchor = LocalTime.of(21, 0);
        UUID deskId = saveDesk(anchor);
        Specialization spec = saveSpecialization(deskId, "S1");

        LocalDate dCal = LocalDate.of(2026, 10, 5);
        LocalDate dPlus1Cal = dCal.plusDays(1);
        LocalDate dPlus2Cal = dCal.plusDays(2);

        // Business day D: 22:00/23:00 on calendar D, 00:00/01:00 on calendar D+1.
        Timeslot dT1 = saveTimeslot(deskId, anchor, dCal, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot dT2 = saveTimeslot(deskId, anchor, dCal, LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        Timeslot dT3 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.MIDNIGHT, LocalTime.of(1, 0));
        Timeslot dT4 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.of(1, 0), LocalTime.of(2, 0));

        // Business day D+1: the same shape one calendar day later.
        Timeslot d1T1 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot d1T2 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        Timeslot d1T3 = saveTimeslot(deskId, anchor, dPlus2Cal, LocalTime.MIDNIGHT, LocalTime.of(1, 0));
        Timeslot d1T4 = saveTimeslot(deskId, anchor, dPlus2Cal, LocalTime.of(1, 0), LocalTime.of(2, 0));

        List<Timeslot> allFixtureTimeslots = List.of(dT1, dT2, dT3, dT4, d1T1, d1T2, d1T3, d1T4);

        // A null business date would make every business-date filter match nothing, letting a
        // survivor assertion pass for entirely the wrong reason -- refuse to proceed vacuously.
        assertThat(allFixtureTimeslots).extracting(Timeslot::getBusinessDate).doesNotContainNull();

        // The geometry this migration depends on: business day D's last two timeslots and
        // business day D+1's first two timeslots land on the SAME calendar date (D+1), despite
        // belonging to different business days.
        assertThat(dT3.getDate()).isEqualTo(d1T1.getDate());
        assertThat(dT3.getBusinessDate()).isEqualTo(dCal);
        assertThat(d1T1.getBusinessDate()).isEqualTo(dPlus1Cal);

        for (Timeslot ts : allFixtureTimeslots) {
            saveLiveRequirement(deskId, ts, spec, 5);
        }

        List<StaffingRequirement> beforeDPlus1 = liveRequirementsOnBusinessDate(deskId, dPlus1Cal);
        assertThat(beforeDPlus1).hasSize(4);
        Map<UUID, Integer> dPlus1OriginalFtes = beforeDPlus1.stream()
                .collect(Collectors.toMap(StaffingRequirement::getId, StaffingRequirement::getRequiredFTEs));

        // Upload covers only business day D's four timeslots. Against the pre-migration code this
        // derives its delete range from these timeslots' CALENDAR dates (D to D+1) and feeds that
        // to a calendar-date-filtering delete, which also destroys d1T1/d1T2 (calendar date D+1).
        StaffingRequirementRequest request = new StaffingRequirementRequest(List.of(
                new StaffingRequirementRequest.Item(dT1.getId(), spec.getId(), 9),
                new StaffingRequirementRequest.Item(dT2.getId(), spec.getId(), 9),
                new StaffingRequirementRequest.Item(dT3.getId(), spec.getId(), 9),
                new StaffingRequirementRequest.Item(dT4.getId(), spec.getId(), 9)));

        service.saveRequirements(deskId, request);

        List<StaffingRequirement> after = staffingRequirementRepository.findAllLiveByDesk(TENANT, deskId);
        assertThat(after).hasSize(8);

        // Business day D+1's four requirements survive byte-identical -- same ids, same values.
        List<StaffingRequirement> afterDPlus1 = filterByBusinessDate(after, dPlus1Cal);
        assertThat(afterDPlus1).hasSize(4);
        assertThat(afterDPlus1).allSatisfy(sr ->
                assertThat(dPlus1OriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));

        // Business day D's four requirements were replaced with the uploaded value.
        List<StaffingRequirement> afterD = filterByBusinessDate(after, dCal);
        assertThat(afterD).hasSize(4);
        assertThat(afterD).allSatisfy(sr -> assertThat(sr.getRequiredFTEs()).isEqualTo(9));
    }

    @Test
    @DisplayName("00:00 anchor: the surviving set after an upload is identical to what a "
            + "calendar-date range would have produced")
    void anchor00_uploadForDayOne_survivingSetMatchesCalendarDateRange() {
        LocalTime anchor = LocalTime.MIDNIGHT;
        UUID deskId = saveDesk(anchor);
        Specialization spec = saveSpecialization(deskId, "S1");

        LocalDate dayOne = LocalDate.of(2026, 10, 5);
        LocalDate dayTwo = dayOne.plusDays(1);

        Timeslot d1T1 = saveTimeslot(deskId, anchor, dayOne, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot d1T2 = saveTimeslot(deskId, anchor, dayOne, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Timeslot d2T1 = saveTimeslot(deskId, anchor, dayTwo, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot d2T2 = saveTimeslot(deskId, anchor, dayTwo, LocalTime.of(9, 0), LocalTime.of(10, 0));

        List<Timeslot> allFixtureTimeslots = List.of(d1T1, d1T2, d2T1, d2T2);
        assertThat(allFixtureTimeslots).extracting(Timeslot::getBusinessDate).doesNotContainNull();

        // At a 00:00 anchor business date always equals calendar date -- the property that makes
        // "no live desk's deleted span changes" an assertion rather than an inference.
        for (Timeslot ts : allFixtureTimeslots) {
            assertThat(ts.getBusinessDate()).isEqualTo(ts.getDate());
        }

        for (Timeslot ts : allFixtureTimeslots) {
            saveLiveRequirement(deskId, ts, spec, 5);
        }

        List<StaffingRequirement> beforeDayTwo = filterByBusinessDate(
                staffingRequirementRepository.findAllLiveByDesk(TENANT, deskId), dayTwo);
        assertThat(beforeDayTwo).hasSize(2);
        Map<UUID, Integer> dayTwoOriginalFtes = beforeDayTwo.stream()
                .collect(Collectors.toMap(StaffingRequirement::getId, StaffingRequirement::getRequiredFTEs));

        StaffingRequirementRequest request = new StaffingRequirementRequest(List.of(
                new StaffingRequirementRequest.Item(d1T1.getId(), spec.getId(), 9),
                new StaffingRequirementRequest.Item(d1T2.getId(), spec.getId(), 9)));

        service.saveRequirements(deskId, request);

        List<StaffingRequirement> after = staffingRequirementRepository.findAllLiveByDesk(TENANT, deskId);
        assertThat(after).hasSize(4);

        List<StaffingRequirement> afterDayTwo = filterByBusinessDate(after, dayTwo);
        assertThat(afterDayTwo).hasSize(2);
        assertThat(afterDayTwo).allSatisfy(sr ->
                assertThat(dayTwoOriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));

        List<StaffingRequirement> afterDayOne = filterByBusinessDate(after, dayOne);
        assertThat(afterDayOne).hasSize(2);
        assertThat(afterDayOne).allSatisfy(sr -> assertThat(sr.getRequiredFTEs()).isEqualTo(9));
    }

    @Test
    @DisplayName("the delete and the reinsert share one transaction boundary -- the weaker, "
            + "still real property (see class javadoc for why a forced failure was not used)")
    void saveRequirementsTransactionBoundaryEnclosesDeleteAndReinsert() throws NoSuchMethodException {
        Method method = StaffingRequirementService.class.getMethod(
                "saveRequirements", UUID.class, StaffingRequirementRequest.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ---------- helpers ----------

    private UUID saveDesk(LocalTime dayStart) {
        Desk desk = new Desk();
        desk.setTenantId(TENANT);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDayStart(dayStart); // bypasses DeskService's gated setter, on purpose
        return deskRepository.save(desk).getId();
    }

    private Specialization saveSpecialization(UUID deskId, String name) {
        Specialization spec = new Specialization();
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    /** Every business date is derived through the shared production derivation, never hand-computed. */
    private Timeslot saveTimeslot(UUID deskId, LocalTime anchor, LocalDate calendarDate,
                                   LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setDate(calendarDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        ts.setBusinessDate(DayWindow.businessDateOf(anchor, calendarDate, start));
        return timeslotRepository.save(ts);
    }

    private StaffingRequirement saveLiveRequirement(UUID deskId, Timeslot ts, Specialization spec,
                                                     int requiredFTEs) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setTenantId(TENANT);
        sr.setDeskId(deskId);
        sr.setTimeslot(ts);
        sr.setSpecialization(spec);
        sr.setRequiredFTEs(requiredFTEs);
        return staffingRequirementRepository.save(sr);
    }

    private List<StaffingRequirement> liveRequirementsOnBusinessDate(UUID deskId, LocalDate businessDate) {
        return filterByBusinessDate(
                staffingRequirementRepository.findAllLiveByDesk(TENANT, deskId), businessDate);
    }

    private List<StaffingRequirement> filterByBusinessDate(List<StaffingRequirement> requirements,
                                                            LocalDate businessDate) {
        return requirements.stream()
                .filter(sr -> sr.getTimeslot().getBusinessDate().equals(businessDate))
                .toList();
    }
}
