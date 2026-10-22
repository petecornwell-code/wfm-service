package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The break repair is verified against the REAL constraint provider (solverConfig.xml via a
 * SolutionManager), not a stub scorer: the point of the repair is a three-part move the real rules
 * must accept — new band, seat vacated in the new break hour, seat taken in the old break hour —
 * and only the real envelope, contiguity, band-capacity and under-allocation constraints can prove
 * that the move leaves the schedule strictly better.
 *
 * <p>Fixture: one business day, a First-Shift-shaped template 08:00-17:00 with three one-hour break
 * bands at 11:00, 12:00 and 13:00, and four agents contracted for 8 hours, ALL on the 11:00 band —
 * the shape that left Vinted (P&A) stuck on 2026-10-22.
 */
class ScheduleBreakRepairServiceTest {

    private static final SolverFactory<Schedule> SOLVER_FACTORY =
            SolverFactory.createFromXmlResource("solverConfig.xml");
    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 26);
    private static final DayWindow MIDNIGHT = DayWindow.anchoredAt(LocalTime.MIDNIGHT);

    private final ScheduleBreakRepairService service = new ScheduleBreakRepairService();

    private record Fixture(Schedule schedule, ShiftBandPair at11, ShiftBandPair at12, ShiftBandPair at13,
                           List<Agent> agents) {}

    private static SolutionManager<Schedule, HardSoftScore> manager() {
        return SolutionManager.create(SOLVER_FACTORY);
    }

    /** Real re-score, counting how many times the repair asked for one. */
    private static Function<Schedule, HardSoftScore> realScorer(AtomicInteger calls) {
        SolutionManager<Schedule, HardSoftScore> manager = manager();
        return schedule -> {
            calls.incrementAndGet();
            return manager.update(schedule);
        };
    }

    @Test
    void overCapacityBand_movesTheExcessBreaksToBandsWithRoom_andTheRealScoreImproves() {
        Fixture f = fixture(2, /* demand per hour */ 3, /* spare free seats per hour */ 2);
        AtomicInteger calls = new AtomicInteger();
        Function<Schedule, HardSoftScore> scorer = realScorer(calls);
        HardSoftScore before = scorer.apply(f.schedule());

        ScheduleBreakRepairService.RepairResult result = service.repairVerified(f.schedule(), scorer);
        HardSoftScore after = manager().update(f.schedule());

        assertThat(result.breaksMoved()).isEqualTo(2);
        assertThat(bandCounts(f).get(f.at11())).isEqualTo(2);           // back within capacity
        assertThat(after.hardScore()).isGreaterThan(before.hardScore());
        assertEveryAgentStillWorksEightHoursInsideTheirEnvelope(f);
    }

    @Test
    void breaksBunchedInAnUnderStaffedHour_areSpreadOut_evenWithNoCapacitySet() {
        // No capacity anywhere; demand 4 at every hour puts the floor at 4 * 70 / 100 = 2, and with
        // all four agents on break at 11:00 that hour is seated 0 -- under the floor.
        Fixture f = fixture(null, 4, 2);
        AtomicInteger calls = new AtomicInteger();
        Function<Schedule, HardSoftScore> scorer = realScorer(calls);
        HardSoftScore before = scorer.apply(f.schedule());

        ScheduleBreakRepairService.RepairResult result = service.repairVerified(f.schedule(), scorer);
        HardSoftScore after = manager().update(f.schedule());

        assertThat(result.breaksMoved()).isPositive();
        assertThat(after.hardScore()).isGreaterThan(before.hardScore());
        assertThat(seatedAt(f.schedule(), 11)).isGreaterThanOrEqualTo(2);
        assertEveryAgentStillWorksEightHoursInsideTheirEnvelope(f);
    }

    @Test
    void nothingOverCapacityOrUnderStaffed_isLeftAlone_withoutASingleRescore() {
        // Cap 4: four on the band is fine. Demand 1 puts the floor at 1 * 70 / 100 = 0, so 11:00
        // being empty is not under-staffed either.
        Fixture f = fixture(4, 1, 2);
        List<UUID> seatingBefore = holders(f.schedule());
        AtomicInteger calls = new AtomicInteger();

        ScheduleBreakRepairService.RepairResult result = service.repairVerified(f.schedule(), realScorer(calls));

        assertThat(result.changedAnything()).isFalse();
        assertThat(calls.get()).isZero();
        assertThat(holders(f.schedule())).isEqualTo(seatingBefore);
    }

    @Test
    void noFreeSeatInTheOldBreakHour_meansNoMove_andTheScheduleIsUntouched() {
        // Zero spare seats: there is no seat at all at 11:00 to put an agent back on the floor in,
        // so any move would cost them an hour -- it must be refused. (The 11:00 timeslot still
        // exists on the schedule even though no seat references it.)
        Fixture f = fixture(2, 3, 0);
        List<UUID> seatingBefore = holders(f.schedule());
        Map<ShiftBandPair, Long> bandsBefore = bandCounts(f);

        ScheduleBreakRepairService.RepairResult result = service.repairVerified(f.schedule(), realScorer(new AtomicInteger()));

        assertThat(result.breaksMoved()).isZero();
        assertThat(holders(f.schedule())).isEqualTo(seatingBefore);
        assertThat(bandCounts(f)).isEqualTo(bandsBefore);
    }

    @Test
    void slotModeSchedule_isNeverTouched() {
        Fixture f = fixture(2, 3, 2);
        f.schedule().setSchedulingMode(SchedulingMode.SLOT);
        AtomicInteger calls = new AtomicInteger();

        assertThat(service.repairVerified(f.schedule(), realScorer(calls)).changedAnything()).isFalse();
        assertThat(calls.get()).isZero();
    }

    // ------------------------------------------------------------------------------------------

    private static void assertEveryAgentStillWorksEightHoursInsideTheirEnvelope(Fixture f) {
        for (AgentShiftAssignment row : f.schedule().getShiftAssignments()) {
            List<AgentAssignment> mine = f.schedule().getAssignments().stream()
                    .filter(s -> s.getAgent() != null && s.getAgent().getId().equals(row.getAgent().getId()))
                    .toList();
            assertThat(mine).as("%s keeps 8 worked hours", row.getAgent().getName()).hasSize(8);
            assertThat(mine).allSatisfy(s ->
                    assertThat(row.getShiftBandPair().covers(s.getTimeslot(), MIDNIGHT))
                            .as("%s seated at %s outside envelope/break", row.getAgent().getName(),
                                    s.getTimeslot().getStartTime())
                            .isTrue());
        }
    }

    private static long seatedAt(Schedule schedule, int hour) {
        return schedule.getAssignments().stream()
                .filter(s -> s.getAgent() != null && s.getTimeslot().getStartTime().getHour() == hour)
                .count();
    }

    private static Map<ShiftBandPair, Long> bandCounts(Fixture f) {
        Map<ShiftBandPair, Long> out = new HashMap<>();
        for (AgentShiftAssignment row : f.schedule().getShiftAssignments()) {
            out.merge(row.getShiftBandPair(), 1L, Long::sum);
        }
        return out;
    }

    private static List<UUID> holders(Schedule schedule) {
        List<UUID> out = new ArrayList<>();
        for (AgentAssignment s : schedule.getAssignments()) {
            out.add(s.getAgent() == null ? null : s.getAgent().getId());
        }
        return out;
    }

    /**
     * @param capacityAt11 capacity of the 11:00 band (null = unlimited); the other two are unlimited
     * @param demand       demand FTEs at every hour 08:00-16:00
     * @param spare        free (unassigned) seats per hour on top of the seats the agents hold
     */
    private static Fixture fixture(Integer capacityAt11, int demand, int spare) {
        UUID deskId = UUID.randomUUID();
        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setTenantId(TENANT);
        spec.setDeskId(deskId);
        spec.setName("P&A");

        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setTenantId(TENANT);
        template.setDeskId(deskId);
        template.setName("First-Shift");
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));
        template.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        template.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        ShiftBandPair at11 = new ShiftBandPair(template, band(template, 180, capacityAt11));
        ShiftBandPair at12 = new ShiftBandPair(template, band(template, 240, null));
        ShiftBandPair at13 = new ShiftBandPair(template, band(template, 300, null));
        List<ShiftBandPair> deskPairs = List.of(at11, at12, at13);

        List<Timeslot> timeslots = new ArrayList<>();
        for (int h = 8; h < 17; h++) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setTenantId(TENANT);
            ts.setDeskId(deskId);
            ts.setDate(DAY);
            ts.setBusinessDate(DAY);
            ts.setStartTime(LocalTime.of(h, 0));
            ts.setEndTime(LocalTime.of(h + 1, 0));
            timeslots.add(ts);
        }

        List<Agent> agents = new ArrayList<>();
        List<AgentShiftAssignment> rows = new ArrayList<>();
        List<AgentAssignment> seats = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Agent a = new Agent();
            a.setId(UUID.randomUUID());
            a.setTenantId(TENANT);
            a.setDeskId(deskId);
            a.setName("Agent-" + i);
            a.setActive(true);
            a.setPrimarySpecialization(spec);
            agents.add(a);

            AgentShiftAssignment row = new AgentShiftAssignment();
            row.setId(UUID.randomUUID());
            row.setTenantId(TENANT);
            row.setDeskId(deskId);
            row.setAgent(a);
            row.setDate(DAY);
            row.setDeskShiftBandPairs(deskPairs);
            row.setDayConfig(new AgentDayConfig(a.getId(), DAY, new BigDecimal("8.00"), 60, 60,
                    new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 220, 70,
                    LocalTime.MIDNIGHT));
            row.setShiftBandPair(at11);                                   // everyone breaks at 11:00
            rows.add(row);

            for (Timeslot ts : timeslots) {
                if (ts.getStartTime().getHour() != 11) {
                    seats.add(seat(deskId, ts, spec, a));
                }
            }
        }
        for (Timeslot ts : timeslots) {
            for (int s = 0; s < spare; s++) seats.add(seat(deskId, ts, spec, null));
        }

        List<TimeslotDemandConfig> demandConfigs = timeslots.stream()
                .map(ts -> new TimeslotDemandConfig(ts, demand)).toList();

        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setIncrementMinutes(60);
        schedule.setOverallocationHardLimitPct(220);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setConstraintWeights(new ConstraintWeights());
        schedule.setAgents(agents);
        schedule.setTimeslots(timeslots);
        schedule.setTimeslotDemandConfigs(new ArrayList<>(demandConfigs));
        schedule.setAssignments(seats);
        schedule.setShiftAssignments(rows);
        return new Fixture(schedule, at11, at12, at13, agents);
    }

    private static ShiftTemplateBreakBand band(ShiftTemplate template, int offsetMinutes, Integer capacity) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setTenantId(TENANT);
        b.setShiftTemplate(template);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(60);
        b.setCapacity(capacity);
        return b;
    }

    private static AgentAssignment seat(UUID deskId, Timeslot ts, Specialization spec, Agent holder) {
        AgentAssignment s = new AgentAssignment();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setDeskId(deskId);
        s.setTimeslot(ts);
        s.setRequiredSpecialization(spec);
        s.setAgent(holder);
        return s;
    }
}
