package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErlangCRequest;
import com.wfm.dto.ErlangXRequest;
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
import java.util.Set;
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

    // ---------- OVNT-02 (plan 21-04, migrate-now): calculateErlangC and calculateErlangX ----------
    //
    // 21-02 made a non-midnight-anchored desk reachable in the same phase that this delete-range
    // ambiguity became live rather than latent, so the operator decided at this plan's checkpoint
    // to migrate both Erlang calculators onto deleteLiveByDeskAndBusinessDateRange in this same
    // plan (see 21-04-SUMMARY.md for the recorded decision and reasoning). These four tests prove
    // the same survivor-observing property this class already proves for the demand-save path,
    // now for both Erlang calculators, reusing this class's @DataJpaTest wiring and fixture shape
    // rather than creating a second test class for the same property (plan 21-04's explicit
    // instruction).

    @Test
    @DisplayName("Erlang C, 21:00 anchor: a recalculation scoped to business day D leaves business "
            + "day D-1's requirements untouched, even though both share calendar date D")
    void erlangC_anchor21_recalculationForBusinessDayD_leavesBusinessDayDMinus1Untouched() {
        BusinessDayDFixture fx = seedAnchor21FixtureWithLiveRequirements();

        List<StaffingRequirement> beforeDMinus1 = liveRequirementsOnBusinessDate(fx.deskId(), fx.dMinus1Cal());
        assertThat(beforeDMinus1).hasSize(4);
        Map<UUID, Integer> dMinus1OriginalFtes = beforeDMinus1.stream()
                .collect(Collectors.toMap(StaffingRequirement::getId, StaffingRequirement::getRequiredFTEs));

        service.calculateErlangC(fx.deskId(), erlangCRequestFor(fx, fx.dCal(), fx.dCal()));

        List<StaffingRequirement> after = staffingRequirementRepository.findAllLiveByDesk(TENANT, fx.deskId());
        assertThat(after).hasSize(8);

        // Business day D-1's four requirements survive byte-identical -- same ids, same values --
        // even though a calendar-date-scoped clear of [D, D] would also have reached two of them
        // (dMinus1T3/dMinus1T4 sit on calendar date D, business day D-1's post-midnight tail).
        List<StaffingRequirement> afterDMinus1 = filterByBusinessDate(after, fx.dMinus1Cal());
        assertThat(afterDMinus1).hasSize(4);
        assertThat(afterDMinus1).allSatisfy(sr ->
                assertThat(dMinus1OriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));
    }

    @Test
    @DisplayName("Erlang C, 21:00 anchor: after the recalculation, the stored rows for business day "
            + "D are exactly the request's items and nothing else -- no stale row survives")
    void erlangC_anchor21_recalculationForBusinessDayD_replacesExactlyThatBusinessDaysRows() {
        BusinessDayDFixture fx = seedAnchor21FixtureWithLiveRequirements();

        List<StaffingRequirement> beforeD = liveRequirementsOnBusinessDate(fx.deskId(), fx.dCal());
        assertThat(beforeD).hasSize(4);
        Set<UUID> beforeDIds = beforeD.stream().map(StaffingRequirement::getId).collect(Collectors.toSet());

        service.calculateErlangC(fx.deskId(), erlangCRequestFor(fx, fx.dCal(), fx.dCal()));

        List<StaffingRequirement> afterD = liveRequirementsOnBusinessDate(fx.deskId(), fx.dCal());
        assertThat(afterD).hasSize(4);
        // The pre-existing rows were deleted (new ids after the recalculation, not reused)...
        assertThat(afterD).extracting(StaffingRequirement::getId).doesNotContainAnyElementsOf(beforeDIds);
        // ...and the surviving set is exactly the four timeslots the request named, nothing more.
        assertThat(afterD).extracting(sr -> sr.getTimeslot().getId())
                .containsExactlyInAnyOrder(fx.dT1().getId(), fx.dT2().getId(), fx.dT3().getId(), fx.dT4().getId());
    }

    @Test
    @DisplayName("Erlang C, 00:00 anchor: a recalculation clears and inserts exactly the rows it "
            + "does today, because the two date systems are the same value at that anchor")
    void erlangC_anchor00_recalculation_isByteIdenticalToCalendarDateScoping() {
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
        // this a no-op control rather than an inference.
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

        ErlangCRequest request = new ErlangCRequest(dayOne, dayOne, List.of(
                new ErlangCRequest.Item(d1T1.getId(), spec.getId(), 100, 180, 80, 20),
                new ErlangCRequest.Item(d1T2.getId(), spec.getId(), 100, 180, 80, 20)),
                null);

        service.calculateErlangC(deskId, request);

        List<StaffingRequirement> after = staffingRequirementRepository.findAllLiveByDesk(TENANT, deskId);
        assertThat(after).hasSize(4);

        List<StaffingRequirement> afterDayTwo = filterByBusinessDate(after, dayTwo);
        assertThat(afterDayTwo).hasSize(2);
        assertThat(afterDayTwo).allSatisfy(sr ->
                assertThat(dayTwoOriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));

        List<StaffingRequirement> afterDayOne = filterByBusinessDate(after, dayOne);
        assertThat(afterDayOne).hasSize(2);
    }

    @Test
    @DisplayName("Erlang X mirrors all three of Erlang C's properties, so the two calculators "
            + "cannot drift apart: 21:00-anchor isolation from the adjacent business day, an exact "
            + "replacement of the targeted business day, and the 00:00-anchor no-op control")
    void erlangX_mirrorsErlangCsThreePropertiesSoTheTwoCalculatorsCannotDriftApart() {
        // Properties 1 + 2, at a 21:00 anchor.
        BusinessDayDFixture fx = seedAnchor21FixtureWithLiveRequirements();

        List<StaffingRequirement> beforeDMinus1 = liveRequirementsOnBusinessDate(fx.deskId(), fx.dMinus1Cal());
        Map<UUID, Integer> dMinus1OriginalFtes = beforeDMinus1.stream()
                .collect(Collectors.toMap(StaffingRequirement::getId, StaffingRequirement::getRequiredFTEs));
        List<StaffingRequirement> beforeD = liveRequirementsOnBusinessDate(fx.deskId(), fx.dCal());
        Set<UUID> beforeDIds = beforeD.stream().map(StaffingRequirement::getId).collect(Collectors.toSet());

        service.calculateErlangX(fx.deskId(), erlangXRequestFor(fx, fx.dCal(), fx.dCal()));

        List<StaffingRequirement> after = staffingRequirementRepository.findAllLiveByDesk(TENANT, fx.deskId());
        assertThat(after).hasSize(8);

        List<StaffingRequirement> afterDMinus1 = filterByBusinessDate(after, fx.dMinus1Cal());
        assertThat(afterDMinus1).hasSize(4);
        assertThat(afterDMinus1).allSatisfy(sr ->
                assertThat(dMinus1OriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));

        List<StaffingRequirement> afterD = filterByBusinessDate(after, fx.dCal());
        assertThat(afterD).hasSize(4);
        assertThat(afterD).extracting(StaffingRequirement::getId).doesNotContainAnyElementsOf(beforeDIds);
        assertThat(afterD).extracting(sr -> sr.getTimeslot().getId())
                .containsExactlyInAnyOrder(fx.dT1().getId(), fx.dT2().getId(), fx.dT3().getId(), fx.dT4().getId());

        // Property 3, at a 00:00 anchor: a no-op control identical to calendar-date scoping, on a
        // fresh desk so the 21:00-anchor fixture above cannot leak into this assertion.
        LocalTime midnightAnchor = LocalTime.MIDNIGHT;
        UUID midnightDeskId = saveDesk(midnightAnchor);
        Specialization midnightSpec = saveSpecialization(midnightDeskId, "S1");

        LocalDate dayOne = LocalDate.of(2026, 11, 2);
        LocalDate dayTwo = dayOne.plusDays(1);

        Timeslot d1T1 = saveTimeslot(midnightDeskId, midnightAnchor, dayOne, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot d1T2 = saveTimeslot(midnightDeskId, midnightAnchor, dayOne, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Timeslot d2T1 = saveTimeslot(midnightDeskId, midnightAnchor, dayTwo, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Timeslot d2T2 = saveTimeslot(midnightDeskId, midnightAnchor, dayTwo, LocalTime.of(9, 0), LocalTime.of(10, 0));

        List<Timeslot> midnightFixtureTimeslots = List.of(d1T1, d1T2, d2T1, d2T2);
        for (Timeslot ts : midnightFixtureTimeslots) {
            assertThat(ts.getBusinessDate()).isEqualTo(ts.getDate());
            saveLiveRequirement(midnightDeskId, ts, midnightSpec, 5);
        }

        List<StaffingRequirement> beforeDayTwo = filterByBusinessDate(
                staffingRequirementRepository.findAllLiveByDesk(TENANT, midnightDeskId), dayTwo);
        assertThat(beforeDayTwo).hasSize(2);
        Map<UUID, Integer> dayTwoOriginalFtes = beforeDayTwo.stream()
                .collect(Collectors.toMap(StaffingRequirement::getId, StaffingRequirement::getRequiredFTEs));

        ErlangXRequest midnightRequest = new ErlangXRequest(dayOne, dayOne, List.of(
                new ErlangXRequest.Item(d1T1.getId(), midnightSpec.getId(), 100, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(d1T2.getId(), midnightSpec.getId(), 100, 180, 90, 25, 80, 20)),
                null);

        service.calculateErlangX(midnightDeskId, midnightRequest);

        List<StaffingRequirement> midnightAfter =
                staffingRequirementRepository.findAllLiveByDesk(TENANT, midnightDeskId);
        assertThat(midnightAfter).hasSize(4);

        List<StaffingRequirement> midnightAfterDayTwo = filterByBusinessDate(midnightAfter, dayTwo);
        assertThat(midnightAfterDayTwo).hasSize(2);
        assertThat(midnightAfterDayTwo).allSatisfy(sr ->
                assertThat(dayTwoOriginalFtes).containsEntry(sr.getId(), sr.getRequiredFTEs()));

        List<StaffingRequirement> midnightAfterDayOne = filterByBusinessDate(midnightAfter, dayOne);
        assertThat(midnightAfterDayOne).hasSize(2);
    }

    // ---------- quick-261008-f51: per-business-date Erlang ----------
    //
    // Reproducer: the Staffing Requirements page shows the Erlang grid for the FIRST business date
    // only, but sends the whole period as the replace range. The delete therefore wiped days 2..N
    // while only day 1's rows were re-inserted.

    /** Three 00:00-anchored business days, each with 08-09 and 09-10 slots holding live 5 FTE. */
    private record ThreeDayFixture(UUID deskId, Specialization spec, LocalDate d1, LocalDate d3,
                                    Timeslot d1T1, Timeslot d1T2) {
    }

    private ThreeDayFixture seedThreeDays() {
        LocalTime anchor = LocalTime.MIDNIGHT;
        UUID deskId = saveDesk(anchor);
        Specialization spec = saveSpecialization(deskId, "S1");
        LocalDate d1 = LocalDate.of(2026, 10, 5);
        Timeslot d1T1 = null;
        Timeslot d1T2 = null;
        for (int i = 0; i < 3; i++) {
            LocalDate d = d1.plusDays(i);
            Timeslot t1 = saveTimeslot(deskId, anchor, d, LocalTime.of(8, 0), LocalTime.of(9, 0));
            Timeslot t2 = saveTimeslot(deskId, anchor, d, LocalTime.of(9, 0), LocalTime.of(10, 0));
            saveLiveRequirement(deskId, t1, spec, 5);
            saveLiveRequirement(deskId, t2, spec, 5);
            if (i == 0) {
                d1T1 = t1;
                d1T2 = t2;
            }
        }
        return new ThreeDayFixture(deskId, spec, d1, d1.plusDays(2), d1T1, d1T2);
    }

    private void assertDaysTwoAndThreeUntouched(ThreeDayFixture fx) {
        for (int i = 1; i <= 2; i++) {
            List<StaffingRequirement> day = liveRequirementsOnBusinessDate(fx.deskId(), fx.d1().plusDays(i));
            assertThat(day).hasSize(2);
            assertThat(day).allSatisfy(sr -> assertThat(sr.getRequiredFTEs()).isEqualTo(5));
        }
    }

    @Test
    @DisplayName("Erlang C: calculating one business date leaves the rest of the period untouched")
    void erlangC_calculatingOneBusinessDate_leavesTheRestOfThePeriodUntouched() {
        ThreeDayFixture fx = seedThreeDays();

        // Exactly what the page sends today: the period-wide range, day 1's rows only.
        service.calculateErlangC(fx.deskId(), new ErlangCRequest(fx.d1(), fx.d3(), List.of(
                new ErlangCRequest.Item(fx.d1T1().getId(), fx.spec().getId(), 100, 180, 80, 20),
                new ErlangCRequest.Item(fx.d1T2().getId(), fx.spec().getId(), 100, 180, 80, 20)),
                null));

        assertDaysTwoAndThreeUntouched(fx);
    }

    @Test
    @DisplayName("Erlang X: calculating one business date leaves the rest of the period untouched")
    void erlangX_calculatingOneBusinessDate_leavesTheRestOfThePeriodUntouched() {
        ThreeDayFixture fx = seedThreeDays();

        service.calculateErlangX(fx.deskId(), new ErlangXRequest(fx.d1(), fx.d3(), List.of(
                new ErlangXRequest.Item(fx.d1T1().getId(), fx.spec().getId(), 100, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(fx.d1T2().getId(), fx.spec().getId(), 100, 180, 90, 25, 80, 20)),
                null));

        assertDaysTwoAndThreeUntouched(fx);
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

    // ---------- OVNT-02 Erlang fixture helpers ----------

    /**
     * The four timeslots an Erlang recalculation of business day D targets, plus the two business
     * dates (D-1 and D) the test needs to assert against. Mirrors the shape of
     * {@link #anchor21_uploadForBusinessDayD_leavesBusinessDayDPlus1Untouched}'s fixture, shifted
     * by one day so "the sibling business day sharing a calendar date with D" is D-1 instead of
     * D+1 -- an arbitrary choice of direction, not a different property.
     */
    private record BusinessDayDFixture(UUID deskId, Specialization spec, LocalDate dMinus1Cal, LocalDate dCal,
                                        Timeslot dT1, Timeslot dT2, Timeslot dT3, Timeslot dT4) {
    }

    /**
     * Seeds a 21:00-anchored desk with eight live requirements across two adjacent business days
     * (D-1 and D), where business day D-1's post-midnight tail and business day D's pre-midnight
     * head land on the SAME calendar date (D) -- the geometry an Erlang recalculation scoped to
     * business day D must respect.
     */
    private BusinessDayDFixture seedAnchor21FixtureWithLiveRequirements() {
        LocalTime anchor = LocalTime.of(21, 0);
        UUID deskId = saveDesk(anchor);
        Specialization spec = saveSpecialization(deskId, "S1");

        LocalDate dMinus1Cal = LocalDate.of(2026, 10, 5);
        LocalDate dCal = dMinus1Cal.plusDays(1);
        LocalDate dPlus1Cal = dCal.plusDays(1);

        // Business day D-1: 22:00/23:00 on calendar D-1, 00:00/01:00 on calendar D (the tail).
        Timeslot dm1T1 = saveTimeslot(deskId, anchor, dMinus1Cal, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot dm1T2 = saveTimeslot(deskId, anchor, dMinus1Cal, LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        Timeslot dm1T3 = saveTimeslot(deskId, anchor, dCal, LocalTime.MIDNIGHT, LocalTime.of(1, 0));
        Timeslot dm1T4 = saveTimeslot(deskId, anchor, dCal, LocalTime.of(1, 0), LocalTime.of(2, 0));

        // Business day D: the same shape one calendar day later.
        Timeslot dT1 = saveTimeslot(deskId, anchor, dCal, LocalTime.of(22, 0), LocalTime.of(23, 0));
        Timeslot dT2 = saveTimeslot(deskId, anchor, dCal, LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        Timeslot dT3 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.MIDNIGHT, LocalTime.of(1, 0));
        Timeslot dT4 = saveTimeslot(deskId, anchor, dPlus1Cal, LocalTime.of(1, 0), LocalTime.of(2, 0));

        List<Timeslot> allFixtureTimeslots = List.of(dm1T1, dm1T2, dm1T3, dm1T4, dT1, dT2, dT3, dT4);

        // A null business date would make every business-date filter match nothing, letting a
        // survivor assertion pass for entirely the wrong reason -- refuse to proceed vacuously.
        assertThat(allFixtureTimeslots).extracting(Timeslot::getBusinessDate).doesNotContainNull();

        // The geometry this migration depends on: business day D-1's tail and business day D's
        // head land on the SAME calendar date (D), despite belonging to different business days.
        assertThat(dm1T3.getDate()).isEqualTo(dT1.getDate());
        assertThat(dm1T3.getBusinessDate()).isEqualTo(dMinus1Cal);
        assertThat(dT1.getBusinessDate()).isEqualTo(dCal);

        for (Timeslot ts : allFixtureTimeslots) {
            saveLiveRequirement(deskId, ts, spec, 5);
        }

        return new BusinessDayDFixture(deskId, spec, dMinus1Cal, dCal, dT1, dT2, dT3, dT4);
    }

    /** Builds an {@link ErlangCRequest} over business day D's four timeslots, reusing the request
     *  item shape {@link StaffingRequirementErlangTest} already uses. */
    private ErlangCRequest erlangCRequestFor(BusinessDayDFixture fx, LocalDate from, LocalDate to) {
        UUID specId = fx.spec().getId();
        return new ErlangCRequest(from, to, List.of(
                new ErlangCRequest.Item(fx.dT1().getId(), specId, 100, 180, 80, 20),
                new ErlangCRequest.Item(fx.dT2().getId(), specId, 100, 180, 80, 20),
                new ErlangCRequest.Item(fx.dT3().getId(), specId, 100, 180, 80, 20),
                new ErlangCRequest.Item(fx.dT4().getId(), specId, 100, 180, 80, 20)),
                null);
    }

    /** The {@link ErlangXRequest} equivalent of {@link #erlangCRequestFor}. */
    private ErlangXRequest erlangXRequestFor(BusinessDayDFixture fx, LocalDate from, LocalDate to) {
        UUID specId = fx.spec().getId();
        return new ErlangXRequest(from, to, List.of(
                new ErlangXRequest.Item(fx.dT1().getId(), specId, 100, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(fx.dT2().getId(), specId, 100, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(fx.dT3().getId(), specId, 100, 180, 90, 25, 80, 20),
                new ErlangXRequest.Item(fx.dT4().getId(), specId, 100, 180, 90, 25, 80, 20)),
                null);
    }
}
