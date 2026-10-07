package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.PaginatedResponse;
import com.wfm.dto.StaffingRequirementResponse;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Audit N-2 (phase 24, plan 03): the Agent Allocation "Required" and "Over / under" rows looked
 * demand up by CALENDAR date against a grid keyed by BUSINESS date, and the demand fetch filtered
 * on calendar dates. On a desk whose day start is not {@code 00:00} a business day straddles two
 * calendar dates, so a calendar {@code from}/{@code to} range for a schedule's period drops the
 * FINAL business day's post-midnight rows (they sit on the calendar day after the period ends) and
 * wrongly includes the first business day's predecessor's post-midnight rows.
 *
 * <p>The fix is additive-then-consume: the response gains {@code businessDate} (the {@code date}
 * field stays calendar -- Phase 20 D-10 / SOLV-07), the list endpoint gains a server-side
 * {@code businessFrom}/{@code businessTo} range filtering the STORED {@code business_date}
 * column, and the one consumer switches to both. The proof is backend-first (D-09) because the
 * frontend has no test runner (Phase 22 WR-04, deferred); every assertion below reads which rows
 * come back, by id, rather than reading the query.
 *
 * <p>Fixture geometry (06:00 desk, one specialization, one live requirement per hourly slot):
 * <pre>
 *   R0  Mon 2026-10-05 01:00-02:00   business Sun 2026-10-04   FTE 1
 *   R1  Mon 2026-10-05 22:00-23:00   business Mon 2026-10-05   FTE 2
 *   R2  Tue 2026-10-06 01:00-02:00   business Mon 2026-10-05   FTE 3
 *   R3  Tue 2026-10-06 22:00-23:00   business Tue 2026-10-06   FTE 4
 *   R4  Wed 2026-10-07 01:00-02:00   business Tue 2026-10-06   FTE 5   (final business day, post-midnight)
 *   R5  Wed 2026-10-07 22:00-23:00   business Wed 2026-10-07   FTE 6
 * </pre>
 * Business range Mon..Tue is therefore {R1, R2, R3, R4}; calendar range Mon..Tue is {R0, R1, R2,
 * R3} -- wrong in both directions.
 */
@DataJpaTest
@ActiveProfiles("test")
class StaffingRequirementListBusinessRangeTest {

    private static final long TENANT = 1L;
    private static final LocalTime SIX = LocalTime.of(6, 0);

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

    /** The 06:00 fixture; index i holds R{i}. */
    private record SixAmDesk(UUID deskId, List<StaffingRequirement> r) {
        UUID id(int i) {
            return r.get(i).getId();
        }
    }

    private SixAmDesk sixAmDeskWithSixRows() {
        UUID deskId = saveDesk(SIX);
        Specialization spec = saveSpecialization(deskId, "S1");
        LocalDate mon = LocalDate.of(2026, 10, 5);
        LocalDate tue = mon.plusDays(1);
        LocalDate wed = mon.plusDays(2);
        List<StaffingRequirement> r = new ArrayList<>();
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, mon, LocalTime.of(1, 0), LocalTime.of(2, 0)), spec, 1));
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, mon, LocalTime.of(22, 0), LocalTime.of(23, 0)), spec, 2));
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, tue, LocalTime.of(1, 0), LocalTime.of(2, 0)), spec, 3));
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, tue, LocalTime.of(22, 0), LocalTime.of(23, 0)), spec, 4));
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, wed, LocalTime.of(1, 0), LocalTime.of(2, 0)), spec, 5));
        r.add(saveLiveRequirement(deskId, saveTimeslot(deskId, SIX, wed, LocalTime.of(22, 0), LocalTime.of(23, 0)), spec, 6));

        // A null or wrongly-derived business date would let every assertion below pass or fail
        // for the wrong reason -- guard the geometry before any listing is trusted.
        assertThat(r.get(0).getTimeslot().getBusinessDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(r.get(4).getTimeslot().getBusinessDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(r).extracting(sr -> sr.getTimeslot().getBusinessDate()).doesNotContainNull();
        return new SixAmDesk(deskId, r);
    }

    private static List<UUID> ids(PaginatedResponse<StaffingRequirementResponse.Item> page) {
        return page.data().stream().map(StaffingRequirementResponse.Item::id).toList();
    }

    @Test
    @DisplayName("06:00 desk: the business range returns the final business day's post-midnight row "
            + "and excludes the prior business day's; the calendar range gets both wrong")
    void businessRange_returnsTheFinalBusinessDaysPostMidnightRows_thatTheCalendarRangeGetsWrong() {
        SixAmDesk f = sixAmDeskWithSixRows();

        PaginatedResponse<StaffingRequirementResponse.Item> business = service.listRequirements(
                f.deskId(), null, null, "2026-10-05", "2026-10-06", null, 50);
        PaginatedResponse<StaffingRequirementResponse.Item> calendar = service.listRequirements(
                f.deskId(), "2026-10-05", "2026-10-06", null, null, null, 50);

        assertThat(ids(business)).containsExactly(f.id(1), f.id(2), f.id(3), f.id(4));
        assertThat(ids(calendar)).containsExactly(f.id(0), f.id(1), f.id(2), f.id(3));
        assertThat(business.hasMore()).isFalse();
        assertThat(business.nextCursor()).isNull();
    }

    @Test
    @DisplayName("every item carries its timeslot's business date and the calendar date is unchanged")
    void everyItemCarriesItsTimeslotsBusinessDate_andDateStaysCalendar() {
        SixAmDesk f = sixAmDeskWithSixRows();

        PaginatedResponse<StaffingRequirementResponse.Item> business = service.listRequirements(
                f.deskId(), null, null, "2026-10-05", "2026-10-06", null, 50);

        StaffingRequirementResponse.Item r2 = business.data().stream()
                .filter(i -> i.id().equals(f.id(2))).findFirst().orElseThrow();
        assertThat(r2.date()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(r2.businessDate()).isEqualTo(LocalDate.of(2026, 10, 5));

        // The companion invariant: the two fields can never silently disagree with the single
        // derivation.
        assertThat(business.data()).isNotEmpty().allSatisfy(item ->
                assertThat(item.businessDate())
                        .isEqualTo(DayWindow.businessDateOf(SIX, item.date(), item.startTime())));

        // The unranged and calendar-ranged callers gain the additive field too.
        assertThat(service.listRequirements(f.deskId(), null, null, null, null, null, 50).data())
                .hasSize(6)
                .allSatisfy(item -> assertThat(item.businessDate())
                        .isEqualTo(DayWindow.businessDateOf(SIX, item.date(), item.startTime())));
        assertThat(service.listRequirements(f.deskId(), "2026-10-05", "2026-10-06", null, null, null, 50).data())
                .extracting(StaffingRequirementResponse.Item::date)
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6));
    }

    @Test
    @DisplayName("00:00 desk: business range and calendar range return identical items, "
            + "and businessDate equals date on every item")
    void midnightDesk_businessRangeAndCalendarRangeReturnIdenticalItems() {
        UUID deskId = saveDesk(LocalTime.MIDNIGHT);
        Specialization spec = saveSpecialization(deskId, "S1");
        List<StaffingRequirement> r = new ArrayList<>();
        for (int d = 5; d <= 7; d++) {
            Timeslot ts = saveTimeslot(deskId, LocalTime.MIDNIGHT, LocalDate.of(2026, 10, d),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            r.add(saveLiveRequirement(deskId, ts, spec, d));
        }

        PaginatedResponse<StaffingRequirementResponse.Item> business = service.listRequirements(
                deskId, null, null, "2026-10-05", "2026-10-06", null, 50);
        PaginatedResponse<StaffingRequirementResponse.Item> calendar = service.listRequirements(
                deskId, "2026-10-05", "2026-10-06", null, null, null, 50);

        assertThat(ids(business)).containsExactly(r.get(0).getId(), r.get(1).getId());
        assertThat(ids(business)).isEqualTo(ids(calendar));
        assertThat(business.data()).allSatisfy(item -> assertThat(item.businessDate()).isEqualTo(item.date()));
    }

    // ---------- D-04: refusal rules, paging, empty range, tenant scope ----------

    @Test
    @DisplayName("only businessFrom, or only businessTo: refused, never silently widened to the desk's whole demand")
    void halfSuppliedBusinessRange_isRefused() {
        SixAmDesk f = sixAmDeskWithSixRows();

        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, null, "2026-10-05", null, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("together");
        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, null, null, "2026-10-06", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("together");
    }

    @Test
    @DisplayName("a business range together with from or to: refused, one range or the other")
    void calendarAndBusinessRangesTogether_areRefused() {
        SixAmDesk f = sixAmDeskWithSixRows();

        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), "2026-10-05", "2026-10-06", "2026-10-05", "2026-10-06", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one range");
        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), "2026-10-05", null, "2026-10-05", "2026-10-06", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one range");
        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, "2026-10-06", "2026-10-05", "2026-10-06", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one range");
    }

    @Test
    @DisplayName("businessFrom after businessTo: refused")
    void invertedBusinessRange_isRefused() {
        SixAmDesk f = sixAmDeskWithSixRows();

        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, null, "2026-10-07", "2026-10-05", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessFrom");
    }

    @Test
    @DisplayName("a malformed business date is an IllegalArgumentException naming the parameter, "
            + "not a DateTimeParseException, and never echoes the raw input")
    void malformedBusinessDate_isRefusedAsIllegalArgument() {
        SixAmDesk f = sixAmDeskWithSixRows();

        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, null, "2026-13-01", "2026-10-06", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessFrom")
                .hasMessageNotContaining("2026-13-01");
        assertThatThrownBy(() -> service.listRequirements(
                f.deskId(), null, null, "2026-10-05", "not-a-date", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessTo")
                .hasMessageNotContaining("not-a-date");
    }

    @Test
    @DisplayName("paging a business range at limit 1 returns every in-range row exactly once, in order")
    void businessRange_pagesToExhaustionAtLimitOne_returnsEveryRowExactlyOnceInOrder() {
        SixAmDesk f = sixAmDeskWithSixRows();

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        PaginatedResponse<StaffingRequirementResponse.Item> page;
        do {
            page = service.listRequirements(f.deskId(), null, null, "2026-10-05", "2026-10-06", cursor, 1);
            seen.addAll(ids(page));
            cursor = page.nextCursor();
            pages++;
            if (page.hasMore()) {
                assertThat(page.data()).hasSize(1);
                assertThat(cursor).isNotNull();
            }
        } while (page.hasMore() && pages < 20);

        assertThat(seen).containsExactly(f.id(1), f.id(2), f.id(3), f.id(4));
        assertThat(pages).isEqualTo(4);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("a well-formed business range with no demand returns an empty page")
    void businessRangeWithNoDemand_returnsAnEmptyPage() {
        SixAmDesk f = sixAmDeskWithSixRows();

        PaginatedResponse<StaffingRequirementResponse.Item> page = service.listRequirements(
                f.deskId(), null, null, "2026-11-01", "2026-11-02", null, 50);

        assertThat(page.data()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("the same business-range request under a different tenant reads zero rows")
    void businessRange_isTenantScoped() {
        SixAmDesk f = sixAmDeskWithSixRows();
        assertThat(service.listRequirements(f.deskId(), null, null, "2026-10-05", "2026-10-06", null, 50).data())
                .hasSize(4);

        TenantContext.setTenantId(2L);
        PaginatedResponse<StaffingRequirementResponse.Item> other = service.listRequirements(
                f.deskId(), null, null, "2026-10-05", "2026-10-06", null, 50);
        PaginatedResponse<StaffingRequirementResponse.Item> otherPaged = service.listRequirements(
                f.deskId(), null, null, "2026-10-05", "2026-10-06", null, 1);

        assertThat(other.data()).isEmpty();
        assertThat(otherPaged.data()).isEmpty();
    }

    @Test
    @DisplayName("a half-supplied calendar range is still silently ignored, exactly as before")
    void halfSuppliedCalendarRange_isStillIgnoredAsBefore() {
        SixAmDesk f = sixAmDeskWithSixRows();

        PaginatedResponse<StaffingRequirementResponse.Item> fromOnly = service.listRequirements(
                f.deskId(), "2026-10-06", null, null, null, null, 50);
        PaginatedResponse<StaffingRequirementResponse.Item> toOnly = service.listRequirements(
                f.deskId(), null, "2026-10-05", null, null, null, 50);

        assertThat(ids(fromOnly)).containsExactly(f.id(0), f.id(1), f.id(2), f.id(3), f.id(4), f.id(5));
        assertThat(ids(toOnly)).isEqualTo(ids(fromOnly));
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
}
