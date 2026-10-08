package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live defect this service exists for, reduced to its smallest honest form: an agent seated in
 * their OWN break hour while a legal, unworked seat sits free in the same envelope on the same day.
 * Observed on Saferide (Jackeline Escobedo, 2026-09-03) and again on Vinted after a three-hour
 * solve (Viktoriia Kudla, Sun 27 Sep, seated 19:00 inside her 19:00-20:00 break with 5 free seats
 * at 23:00).
 *
 * <p>Scoring here is a STUB that counts exactly the thing {@code shiftEnvelopeCompliance} counts —
 * assigned seats their agent-day's {@link ShiftBandPair} does not cover — because these tests are
 * about the repair's own decision-making, not about the constraint provider. Using the real score
 * director would make the assertions depend on every other constraint at once and turn a failure
 * into a puzzle. The real provider's verdict is what guards the repair in PRODUCTION: every move is
 * kept only if the true hard score strictly improves.
 */
class ScheduleEnvelopeRepairServiceTest {

    private static final long TENANT = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 27); // the Sunday from the live case
    private static final LocalTime ENVELOPE_START = LocalTime.of(8, 0);
    private static final LocalTime ENVELOPE_END = LocalTime.of(17, 0);
    private static final int BREAK_OFFSET_MINUTES = 180;  // break runs 11:00-12:00
    private static final int BREAK_DURATION_MINUTES = 60;

    private final ScheduleEnvelopeRepairService service = new ScheduleEnvelopeRepairService();

    /**
     * Counts envelope violations the way the constraint does, and reports them as hard points.
     * Soft is left at zero — the repair is only ever allowed to read the hard level.
     */
    private static Function<Schedule, HardSoftScore> envelopeScorer() {
        return envelopeScorer(LocalTime.MIDNIGHT);
    }

    /**
     * The same stub at a given day-start anchor. A seat belongs to the shift row of its BUSINESS
     * date (the key {@code shiftEnvelopeCompliance} joins on), and coverage is judged through the
     * desk's own {@link DayWindow}.
     */
    private static Function<Schedule, HardSoftScore> envelopeScorer(LocalTime dayStart) {
        return schedule -> {
            int violations = 0;
            for (AgentAssignment seat : schedule.getAssignments()) {
                if (seat.getAgent() == null) {
                    continue;
                }
                ShiftBandPair pair = schedule.getShiftAssignments().stream()
                        .filter(sa -> sa.getAgent() != null
                                && sa.getAgent().getId().equals(seat.getAgent().getId())
                                && sa.getDate().equals(seat.getTimeslot().getBusinessDate()))
                        .map(AgentShiftAssignment::getShiftBandPair)
                        .findFirst().orElse(null);
                if (pair == null
                        || !pair.covers(seat.getTimeslot(), DayWindow.anchoredAt(dayStart))) {
                    violations++;
                }
            }
            HardSoftScore score = HardSoftScore.ofHard(-violations);
            schedule.setScore(score);
            return score;
        };
    }

    @Test
    void movesASeatOutOfTheAgentsOwnBreakHourOntoAFreeLegalSeat() {
        Fixture f = stuckOnOwnBreakHour();

        HardSoftScore before = envelopeScorer().apply(f.schedule());
        assertThat(before.hardScore()).isEqualTo(-1);

        var result = service.repairVerified(f.schedule(), envelopeScorer());

        assertThat(result.violationsFound()).isEqualTo(1);
        assertThat(result.violationsRepaired()).isEqualTo(1);

        // The break-hour seat is vacated and the free legal seat now holds her.
        assertThat(f.breakSeat().getAgent()).isNull();
        assertThat(f.freeLegalSeat().getAgent()).isNotNull();
        assertThat(f.freeLegalSeat().getAgent().getId()).isEqualTo(f.agent().getId());

        // Hours are unchanged by construction: one seat given up, one seat taken.
        assertThat(seatsHeldBy(f.schedule(), f.agent())).isEqualTo(8);

        assertThat(envelopeScorer().apply(f.schedule()).hardScore()).isEqualTo(0);
    }

    @Test
    void leavesTheScheduleAloneWhenNoLegalFreeSeatExists() {
        Fixture f = stuckOnOwnBreakHour();
        // Take the only legal free seat away by giving it to somebody else.
        Agent other = agent("Other");
        f.schedule().getAgents().add(other);
        f.freeLegalSeat().setAgent(other);

        var result = service.repairVerified(f.schedule(), envelopeScorer());

        assertThat(result.violationsFound()).isEqualTo(1);
        assertThat(result.violationsRepaired()).isZero();
        assertThat(f.breakSeat().getAgent()).isNotNull();
        assertThat(f.breakSeat().getAgent().getId()).isEqualTo(f.agent().getId());
        assertThat(seatsHeldBy(f.schedule(), f.agent())).isEqualTo(8);
    }

    /**
     * The safety guarantee, exercised against a scorer that reports the repair as a REGRESSION —
     * standing in for a real constraint the cheap candidate filter does not model (minimum
     * staffing at the vacated hour, the bulk overallocation ceiling, and so on). Nothing may be
     * left applied, and the score the schedule carries afterwards must describe the seats it
     * actually holds.
     */
    @Test
    void rollsEveryMoveBackWhenTheRescoreSaysItMadeThingsWorse() {
        Fixture f = stuckOnOwnBreakHour();
        Agent agent = f.agent();
        AtomicInteger calls = new AtomicInteger();
        Function<Schedule, HardSoftScore> hostileScorer = schedule -> {
            calls.incrementAndGet();
            // Any state where the free legal seat is taken scores WORSE than the stuck state.
            boolean moved = f.freeLegalSeat().getAgent() != null;
            HardSoftScore score = HardSoftScore.ofHard(moved ? -5 : -1);
            schedule.setScore(score);
            return score;
        };

        var result = service.repairVerified(f.schedule(), hostileScorer);

        assertThat(result.violationsRepaired()).isZero();
        assertThat(f.breakSeat().getAgent()).isNotNull();
        assertThat(f.breakSeat().getAgent().getId()).isEqualTo(agent.getId());
        assertThat(f.freeLegalSeat().getAgent()).isNull();
        assertThat(seatsHeldBy(f.schedule(), agent)).isEqualTo(8);
        // The stored score describes the rolled-back schedule, not the trial that was undone.
        assertThat(f.schedule().getScore().hardScore()).isEqualTo(-1);
        assertThat(calls.get()).isGreaterThan(0);
    }

    @Test
    void doesNothingInSlotMode() {
        Fixture f = stuckOnOwnBreakHour();
        f.schedule().setSchedulingMode(SchedulingMode.SLOT);

        var result = service.repairVerified(f.schedule(), envelopeScorer());

        assertThat(result.violationsFound()).isZero();
        assertThat(result.violationsRepaired()).isZero();
        assertThat(f.breakSeat().getAgent()).isNotNull();
    }

    @Test
    void leavesACompliantScheduleUntouched() {
        Fixture f = stuckOnOwnBreakHour();
        // Put her where she should have been in the first place.
        f.breakSeat().setAgent(null);
        f.freeLegalSeat().setAgent(f.agent());

        var result = service.repairVerified(f.schedule(), envelopeScorer());

        assertThat(result.violationsFound()).isZero();
        assertThat(result.violationsRepaired()).isZero();
    }

    /**
     * Post-midnight seats on a 06:00 desk. Business Monday 2026-10-05 runs 06:00 Monday to 06:00
     * Tuesday, so the 21:00-05:00 envelope's 00:00-04:00 hours are calendar TUESDAY. The service
     * used to key a seat on its calendar date, find no Tuesday envelope for the agent, and treat
     * the 05:00-06:00 seat — outside the envelope — as not a violation at all.
     */
    @Test
    void findsAndRepairsAPostMidnightSeatOutsideItsBusinessDayEnvelope() {
        OvernightDesk d = overnightDesk(true, false);
        Function<Schedule, HardSoftScore> scorer = envelopeScorer(LocalTime.of(6, 0));

        assertThat(scorer.apply(d.schedule()).hardScore()).isEqualTo(-1);

        var result = service.repairVerified(d.schedule(), scorer);

        assertThat(result.violationsFound()).isEqualTo(1);
        assertThat(result.violationsRepaired()).isEqualTo(1);
        assertThat(d.outsideSeat().getAgent()).isNull();
        assertThat(d.insideFreeSeat().getAgent()).isNotNull();
        assertThat(d.insideFreeSeat().getAgent().getId()).isEqualTo(d.agent().getId());
        assertThat(seatsHeldBy(d.schedule(), d.agent())).isEqualTo(8);
        assertThat(scorer.apply(d.schedule()).hardScore()).isEqualTo(0);
    }

    /**
     * The other direction: with the agent also on a 07:00-15:00 envelope on business TUESDAY, the
     * calendar-Tuesday 00:00-05:00 seats of business MONDAY must not be judged against it.
     */
    @Test
    void neverJudgesAPostMidnightSeatAgainstTheNextBusinessDaysEnvelope() {
        OvernightDesk d = overnightDesk(false, true);
        Function<Schedule, HardSoftScore> scorer = envelopeScorer(LocalTime.of(6, 0));
        List<UUID> holdersBefore = holderIds(d.schedule());

        assertThat(scorer.apply(d.schedule()).hardScore()).isEqualTo(0);

        var result = service.repairVerified(d.schedule(), scorer);

        assertThat(result.violationsFound()).isZero();
        assertThat(result.violationsRepaired()).isZero();
        assertThat(holderIds(d.schedule())).isEqualTo(holdersBefore);
    }

    /**
     * D7: the repair holds no state across calls. A 06:00 overnight desk and a midnight-anchored
     * day desk, each rebuilt fresh per run, reproduce their isolated outcome whether run alone,
     * alternately, or concurrently on this one service instance.
     */
    @Test
    void holdsNoStateAcrossCalls_desksRepairedInTurnOrConcurrentlyMatchTheirIsolatedRun() throws Exception {
        CallIsolation.assertNoStateSurvivesACall(
                () -> {
                    OvernightDesk d = overnightDesk(true, false);
                    var result = service.repairVerified(d.schedule(), envelopeScorer(LocalTime.of(6, 0)));
                    return List.of(result, seating(d.schedule()));
                },
                () -> {
                    Fixture f = stuckOnOwnBreakHour();
                    var result = service.repairVerified(f.schedule(), envelopeScorer());
                    return List.of(result, seating(f.schedule()));
                });
    }

    // ------------------------------------------------------------------
    //  Fixture
    // ------------------------------------------------------------------

    /** Who sits where, by calendar date, business date and hour; fixture ids are random, names are not. */
    private static List<String> seating(Schedule schedule) {
        List<String> out = new ArrayList<>();
        for (AgentAssignment s : schedule.getAssignments()) {
            Timeslot ts = s.getTimeslot();
            out.add(ts.getDate() + "/" + ts.getBusinessDate() + " " + ts.getStartTime() + " "
                    + (s.getAgent() == null ? "-" : s.getAgent().getName()));
        }
        return out;
    }

    private record Fixture(Schedule schedule, Agent agent,
                           AgentAssignment breakSeat, AgentAssignment freeLegalSeat) {}

    /**
     * One agent on an 08:00-17:00 envelope whose break band runs 11:00-12:00, holding eight hourly
     * seats — seven of them legal, plus the 11:00 seat that IS her break. The 16:00 seat is legal,
     * inside her envelope and unworked, so the one-for-one move that fixes her exists.
     */
    private Fixture stuckOnOwnBreakHour() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName("Security and Item Quality");

        Agent agent = agent("Viktoriia");
        agent.setDeskId(deskId);
        agent.setPrimarySpecialization(spec);

        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setTenantId(TENANT);
        template.setDeskId(deskId);
        template.setName("Test 08:00-17:00");
        template.setStartTime(ENVELOPE_START);
        template.setEndTime(ENVELOPE_END);
        template.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        template.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));

        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setId(UUID.randomUUID());
        band.setTenantId(TENANT);
        band.setShiftTemplate(template);
        band.setOffsetMinutes(BREAK_OFFSET_MINUTES);
        band.setDurationMinutes(BREAK_DURATION_MINUTES);

        ShiftBandPair pair = new ShiftBandPair(template, band);

        AgentShiftAssignment shift = new AgentShiftAssignment();
        shift.setId(UUID.randomUUID());
        shift.setTenantId(TENANT);
        shift.setDeskId(deskId);
        shift.setScheduleId(scheduleId);
        shift.setAgent(agent);
        shift.setDate(DATE);
        shift.setDeskShiftBandPairs(List.of(pair));
        shift.setShiftBandPair(pair);

        List<Timeslot> timeslots = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
        AgentAssignment breakSeat = null;
        AgentAssignment freeLegalSeat = null;

        for (LocalTime t = ENVELOPE_START; t.isBefore(ENVELOPE_END); t = t.plusHours(1)) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setTenantId(TENANT);
            ts.setDeskId(deskId);
            ts.setScheduleId(scheduleId);
            ts.setDate(DATE);
            ts.setBusinessDate(DATE);
            ts.setStartTime(t);
            ts.setEndTime(t.plusHours(1));
            timeslots.add(ts);

            AgentAssignment seat = new AgentAssignment();
            seat.setId(UUID.randomUUID());
            seat.setTenantId(TENANT);
            seat.setDeskId(deskId);
            seat.setScheduleId(scheduleId);
            seat.setTimeslot(ts);
            seat.setRequiredSpecialization(spec);

            if (t.equals(LocalTime.of(16, 0))) {
                freeLegalSeat = seat;            // legal, inside the envelope, left unworked
            } else {
                seat.setAgent(agent);            // 08:00-15:00 inclusive, which includes 11:00
                if (t.equals(LocalTime.of(11, 0))) {
                    breakSeat = seat;            // her own break hour — the violation
                }
            }
            seats.add(seat);
        }

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        // BDAY-04 (plan 19-05): repairVerified binds its window from
        // schedule.getScheduleConfig().dayStart() — an explicit midnight anchor here, not a
        // production fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setAgents(new ArrayList<>(List.of(agent)));
        schedule.setTimeslots(timeslots);
        schedule.setAssignments(seats);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shift)));

        // Guard the fixture itself: the shape under test must actually be the stuck shape.
        assertThat(breakSeat).isNotNull();
        assertThat(freeLegalSeat).isNotNull();
        assertThat(pair.covers(breakSeat.getTimeslot(), DayWindow.anchoredAt(LocalTime.MIDNIGHT))).isFalse();
        assertThat(pair.covers(freeLegalSeat.getTimeslot(), DayWindow.anchoredAt(LocalTime.MIDNIGHT))).isTrue();

        return new Fixture(schedule, agent, breakSeat, freeLegalSeat);
    }

    private record OvernightDesk(Schedule schedule, Agent agent,
                                 AgentAssignment outsideSeat, AgentAssignment insideFreeSeat) {}

    /**
     * A 06:00-anchored desk. One agent on business Monday 2026-10-05 holds a no-band 21:00-05:00
     * envelope; hourly seats start 21:00, 22:00, 23:00 (calendar Monday) and 00:00 through 05:00
     * (calendar Tuesday), every one of them business Monday. The agent holds eight of the nine.
     *
     * <p>{@code seatedOutsideEnvelope}: the free seat is 03:00 (legal) and she holds the 05:00-06:00
     * seat that lies outside the envelope; otherwise she holds 21:00-04:00 and 05:00 is free.
     * {@code alsoWorksBusinessTuesday}: she also holds a no-band 07:00-15:00 envelope on business
     * Tuesday 2026-10-06 and works its eight calendar-Tuesday day seats.
     */
    private OvernightDesk overnightDesk(boolean seatedOutsideEnvelope, boolean alsoWorksBusinessTuesday) {
        final LocalDate monday = LocalDate.of(2026, 10, 5);
        final LocalDate tuesday = monday.plusDays(1);
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName("Overnight desk");

        Agent agent = agent("Night owl");
        agent.setDeskId(deskId);
        agent.setPrimarySpecialization(spec);

        ShiftBandPair overnight = new ShiftBandPair(
                noBandTemplate(deskId, "Overnight 21:00-05:00", LocalTime.of(21, 0), LocalTime.of(5, 0)), null);
        ShiftBandPair day = new ShiftBandPair(
                noBandTemplate(deskId, "Day 07:00-15:00", LocalTime.of(7, 0), LocalTime.of(15, 0)), null);

        List<AgentShiftAssignment> shifts = new ArrayList<>();
        shifts.add(shiftRow(deskId, scheduleId, agent, monday, overnight));
        if (alsoWorksBusinessTuesday) {
            shifts.add(shiftRow(deskId, scheduleId, agent, tuesday, day));
        }

        List<Timeslot> timeslots = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
        AgentAssignment outside = null;
        AgentAssignment insideFree = null;

        // Business Monday: 21, 22, 23 on calendar Monday, then 0..5 on calendar Tuesday.
        int[] hours = {21, 22, 23, 0, 1, 2, 3, 4, 5};
        for (int hour : hours) {
            LocalDate calendar = hour >= 21 ? monday : tuesday;
            AgentAssignment seat = seat(deskId, scheduleId, spec, timeslots, hour, calendar, monday);
            seats.add(seat);
            boolean free = seatedOutsideEnvelope ? hour == 3 : hour == 5;
            if (!free) {
                seat.setAgent(agent);
            }
            if (hour == 5) {
                outside = seat;
            }
            if (hour == 3) {
                insideFree = seat;
            }
        }
        if (alsoWorksBusinessTuesday) {
            for (int hour = 7; hour < 15; hour++) {
                AgentAssignment seat = seat(deskId, scheduleId, spec, timeslots, hour, tuesday, tuesday);
                seat.setAgent(agent);
                seats.add(seat);
            }
        }

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setDayStart(LocalTime.of(6, 0));
        schedule.setAgents(new ArrayList<>(List.of(agent)));
        schedule.setTimeslots(timeslots);
        schedule.setAssignments(seats);
        schedule.setShiftAssignments(shifts);

        // Guard the fixture itself: the shape under test must be the post-midnight shape.
        DayWindow window = DayWindow.anchoredAt(LocalTime.of(6, 0));
        assertThat(outside.getTimeslot().getBusinessDate()).isEqualTo(monday);
        assertThat(outside.getTimeslot().getDate()).isEqualTo(tuesday);
        assertThat(overnight.covers(outside.getTimeslot(), window)).isFalse();
        assertThat(overnight.covers(insideFree.getTimeslot(), window)).isTrue();
        assertThat(insideFree.getTimeslot().getDate()).isEqualTo(tuesday);

        return new OvernightDesk(schedule, agent, outside, insideFree);
    }

    private static ShiftTemplate noBandTemplate(UUID deskId, String name, LocalTime start, LocalTime end) {
        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setTenantId(TENANT);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        template.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        return template;
    }

    private static AgentShiftAssignment shiftRow(UUID deskId, UUID scheduleId, Agent agent,
            LocalDate businessDate, ShiftBandPair pair) {
        AgentShiftAssignment shift = new AgentShiftAssignment();
        shift.setId(UUID.randomUUID());
        shift.setTenantId(TENANT);
        shift.setDeskId(deskId);
        shift.setScheduleId(scheduleId);
        shift.setAgent(agent);
        shift.setDate(businessDate);
        shift.setDeskShiftBandPairs(List.of(pair));
        shift.setShiftBandPair(pair);
        return shift;
    }

    /** One hourly seat; the 23:00 slot ends at midnight, hence the modulo. */
    private static AgentAssignment seat(UUID deskId, UUID scheduleId, Specialization spec,
            List<Timeslot> timeslots, int hour, LocalDate calendarDate, LocalDate businessDate) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(calendarDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(LocalTime.of(hour, 0));
        ts.setEndTime(LocalTime.of((hour + 1) % 24, 0));
        timeslots.add(ts);

        AgentAssignment seat = new AgentAssignment();
        seat.setId(UUID.randomUUID());
        seat.setTenantId(TENANT);
        seat.setDeskId(deskId);
        seat.setScheduleId(scheduleId);
        seat.setTimeslot(ts);
        seat.setRequiredSpecialization(spec);
        return seat;
    }

    /** Holder id (or null) of every seat, in schedule order. */
    private static List<UUID> holderIds(Schedule schedule) {
        List<UUID> out = new ArrayList<>();
        for (AgentAssignment s : schedule.getAssignments()) {
            out.add(s.getAgent() == null ? null : s.getAgent().getId());
        }
        return out;
    }

    private static Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setName(name);
        a.setActive(true);
        return a;
    }

    private static long seatsHeldBy(Schedule schedule, Agent agent) {
        return schedule.getAssignments().stream()
                .filter(s -> s.getAgent() != null && s.getAgent().getId().equals(agent.getId()))
                .count();
    }
}
