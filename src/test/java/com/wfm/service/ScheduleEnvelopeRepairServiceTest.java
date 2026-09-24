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
        return schedule -> {
            int violations = 0;
            for (AgentAssignment seat : schedule.getAssignments()) {
                if (seat.getAgent() == null) {
                    continue;
                }
                ShiftBandPair pair = schedule.getShiftAssignments().stream()
                        .filter(sa -> sa.getAgent() != null
                                && sa.getAgent().getId().equals(seat.getAgent().getId())
                                && sa.getDate().equals(seat.getTimeslot().getDate()))
                        .map(AgentShiftAssignment::getShiftBandPair)
                        .findFirst().orElse(null);
                if (pair == null || !pair.covers(seat.getTimeslot())) {
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

    // ------------------------------------------------------------------
    //  Fixture
    // ------------------------------------------------------------------

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
        schedule.setAgents(new ArrayList<>(List.of(agent)));
        schedule.setTimeslots(timeslots);
        schedule.setAssignments(seats);
        schedule.setShiftAssignments(new ArrayList<>(List.of(shift)));

        // Guard the fixture itself: the shape under test must actually be the stuck shape.
        assertThat(breakSeat).isNotNull();
        assertThat(freeLegalSeat).isNotNull();
        assertThat(pair.covers(breakSeat.getTimeslot())).isFalse();
        assertThat(pair.covers(freeLegalSeat.getTimeslot())).isTrue();

        return new Fixture(schedule, agent, breakSeat, freeLegalSeat);
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
