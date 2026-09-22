package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The repair's whole claim is "consistency improves, coverage does not move". Each test here
 * asserts BOTH halves — an improvement that quietly changed which hours are worked would be a
 * regression dressed as a win.
 */
class ScheduleConsistencyRepairServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 31);
    private final ScheduleConsistencyRepairService service = new ScheduleConsistencyRepairService();

    @Test
    void twoAgentsHoldingEachOthersUsualShift_areSwapped_andCoverageIsUnchanged() {
        Specialization spec = spec("General");
        Agent a = agent("A", spec, "8.00");
        Agent b = agent("B", spec, "8.00");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(9, 0), LocalTime.of(18, 0));

        // Each holds the OTHER's usual shift — the exact case the solver cannot fix.
        Schedule schedule = schedule(
                List.of(shift(a, late), shift(b, early)),
                List.of(seat(a, 9), seat(a, 10), seat(b, 8), seat(b, 11)),
                List.of(new ResolvedUsualShiftTarget(a.getId(), DAY, LocalTime.of(8, 0)),
                        new ResolvedUsualShiftTarget(b.getId(), DAY, LocalTime.of(9, 0))));

        List<LocalTime> coverageBefore = workedHours(schedule);

        var result = service.repair(schedule);

        assertThat(result.changedAnything()).isTrue();
        assertThat(result.exactBefore()).isZero();
        assertThat(result.exactAfter()).isEqualTo(2);
        assertThat(startOf(schedule, a)).isEqualTo(LocalTime.of(8, 0));
        assertThat(startOf(schedule, b)).isEqualTo(LocalTime.of(9, 0));
        // The multiset of worked hours is the point: same hours covered, different people.
        assertThat(workedHours(schedule)).isEqualTo(coverageBefore);
        // Seats followed their envelope rather than staying with their original holder.
        assertThat(hoursOf(schedule, a)).containsExactlyInAnyOrder(8, 11);
        assertThat(hoursOf(schedule, b)).containsExactlyInAnyOrder(9, 10);
    }

    @Test
    void anAgentWithNoUsualShift_doesNotBlockAColleagueWhoHasOne() {
        // The gap this closes. B genuinely wants 08:00 and is sitting on 12:00; A has no stored
        // usual shift at all and happens to be holding 08:00. Until this fix A was handed its
        // CURRENT start as its "want", which made it indistinguishable from an agent who had
        // actually asked for 08:00 — so A claimed the slot in the exact-match pass and B was left
        // drifted. On the live Saferide desk that accounted for most of the gap between 236
        // agent-days honoured and the 240 the chosen shift mix allowed.
        Specialization spec = spec("General");
        Agent noPreference = agent("A", spec, "8.00");
        Agent wantsEarly = agent("B", spec, "8.00");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        Schedule schedule = schedule(
                List.of(shift(noPreference, early), shift(wantsEarly, late)),
                List.of(seat(noPreference, 8), seat(noPreference, 11),
                        seat(wantsEarly, 12), seat(wantsEarly, 15)),
                // Only B has a target. A is absent from the list entirely — that IS "no usual shift".
                List.of(new ResolvedUsualShiftTarget(wantsEarly.getId(), DAY, LocalTime.of(8, 0))));

        List<LocalTime> coverageBefore = workedHours(schedule);

        var result = service.repair(schedule);

        assertThat(result.exactBefore()).isZero();
        assertThat(result.exactAfter()).isEqualTo(1);
        assertThat(startOf(schedule, wantsEarly)).isEqualTo(LocalTime.of(8, 0));
        assertThat(startOf(schedule, noPreference)).isEqualTo(LocalTime.of(12, 0));
        // Unchanged, as for every repair: same hours worked, different people on them.
        assertThat(workedHours(schedule)).isEqualTo(coverageBefore);
        assertThat(hoursOf(schedule, wantsEarly)).containsExactlyInAnyOrder(8, 11);
        assertThat(hoursOf(schedule, noPreference)).containsExactlyInAnyOrder(12, 15);
    }

    @Test
    void aGroupWithNothingToGain_isLeftCompletelyAlone() {
        // The other half of the fix. Letting no-preference agents float free in the residual pass
        // means a permutation can now be non-identity while improving nothing at all — and moving
        // a real person's roster for no benefit is a cost, not a neutral act. Neither agent here
        // has a usual shift, so there is no gain available and nothing may move.
        Specialization spec = spec("General");
        Agent a = agent("A", spec, "8.00");
        Agent b = agent("B", spec, "8.00");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        Schedule schedule = schedule(
                List.of(shift(a, early), shift(b, late)),
                List.of(seat(a, 8), seat(b, 12)),
                List.of());

        var result = service.repair(schedule);

        assertThat(result.changedAnything()).isFalse();
        assertThat(result.agentDaysMoved()).isZero();
        assertThat(startOf(schedule, a)).isEqualTo(LocalTime.of(8, 0));
        assertThat(startOf(schedule, b)).isEqualTo(LocalTime.of(12, 0));
        assertThat(hoursOf(schedule, a)).containsExactly(8);
        assertThat(hoursOf(schedule, b)).containsExactly(12);
    }

    @Test
    void envelopesSharedByManyAgents_keepEverySeatWithItsOwnHolder() {
        // Regression: envelopes are shared VALUE objects — every agent on the same template and
        // band holds the SAME ShiftBandPair instance. Keying the seat remap by envelope identity
        // collapsed those agent-days onto one entry, handing one agent everyone's seats and
        // leaving the rest with none. It scored -3,327,984 hard on the live desk.
        Specialization spec = spec("General");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(9, 0), LocalTime.of(18, 0));

        // Three on the SAME early instance, three on the SAME late instance.
        Agent e1 = agent("E1", spec, "8.00"), e2 = agent("E2", spec, "8.00"), e3 = agent("E3", spec, "8.00");
        Agent l1 = agent("L1", spec, "8.00"), l2 = agent("L2", spec, "8.00"), l3 = agent("L3", spec, "8.00");

        Schedule schedule = schedule(
                List.of(shift(e1, early), shift(e2, early), shift(e3, early),
                        shift(l1, late), shift(l2, late), shift(l3, late)),
                List.of(seat(e1, 8), seat(e2, 8), seat(e3, 8),
                        seat(l1, 9), seat(l2, 9), seat(l3, 9)),
                // Each early agent wants late and vice versa, forcing a full 3-for-3 exchange.
                List.of(new ResolvedUsualShiftTarget(e1.getId(), DAY, LocalTime.of(9, 0)),
                        new ResolvedUsualShiftTarget(e2.getId(), DAY, LocalTime.of(9, 0)),
                        new ResolvedUsualShiftTarget(e3.getId(), DAY, LocalTime.of(9, 0)),
                        new ResolvedUsualShiftTarget(l1.getId(), DAY, LocalTime.of(8, 0)),
                        new ResolvedUsualShiftTarget(l2.getId(), DAY, LocalTime.of(8, 0)),
                        new ResolvedUsualShiftTarget(l3.getId(), DAY, LocalTime.of(8, 0))));

        List<LocalTime> coverageBefore = workedHours(schedule);
        service.repair(schedule);

        // Every agent keeps EXACTLY one seat — the invariant the identity bug destroyed.
        for (Agent a : List.of(e1, e2, e3, l1, l2, l3)) {
            assertThat(hoursOf(schedule, a)).as("seat count for %s", a.getName()).hasSize(1);
        }
        // Each agent's single seat lies inside the envelope they now hold.
        for (Agent a : List.of(e1, e2, e3, l1, l2, l3)) {
            assertThat(hoursOf(schedule, a).get(0)).isEqualTo(startOf(schedule, a).getHour());
        }
        assertThat(workedHours(schedule)).isEqualTo(coverageBefore);
        assertThat(startOf(schedule, e1)).isEqualTo(LocalTime.of(9, 0));
        assertThat(startOf(schedule, l1)).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void agentsWithDifferentContractedHours_areNeverSwapped() {
        Specialization spec = spec("General");
        Agent full = agent("Full", spec, "8.00");
        Agent part = agent("Part", spec, "4.00");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(9, 0), LocalTime.of(18, 0));

        Schedule schedule = schedule(
                List.of(shift(full, late), shift(part, early)),
                List.of(seat(full, 9), seat(part, 8)),
                List.of(new ResolvedUsualShiftTarget(full.getId(), DAY, LocalTime.of(8, 0)),
                        new ResolvedUsualShiftTarget(part.getId(), DAY, LocalTime.of(9, 0))));

        var result = service.repair(schedule);

        // Swapping would satisfy both usual shifts, but would also move 8 contracted hours onto a
        // 4-hour agent. Substitutability is the guard, and it must win over the tempting swap.
        assertThat(result.changedAnything()).isFalse();
        assertThat(startOf(schedule, full)).isEqualTo(LocalTime.of(9, 0));
        assertThat(startOf(schedule, part)).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void slotModeSchedule_isLeftEntirelyAlone() {
        Specialization spec = spec("General");
        Agent a = agent("A", spec, "8.00");
        Schedule schedule = schedule(List.of(shift(a, pair("E", LocalTime.of(8, 0), LocalTime.of(17, 0)))),
                List.of(seat(a, 9)), List.of());
        schedule.setSchedulingMode(SchedulingMode.SLOT);

        assertThat(service.repair(schedule).changedAnything()).isFalse();
    }

    @Test
    void agentWithNoUsualShift_doesNotDisplaceAnAgentWhoHasOne() {
        Specialization spec = spec("General");
        Agent withUsual = agent("Wants8", spec, "8.00");
        Agent noUsual = agent("NoPref", spec, "8.00");
        ShiftBandPair early = pair("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair late = pair("Late", LocalTime.of(9, 0), LocalTime.of(18, 0));

        Schedule schedule = schedule(
                List.of(shift(withUsual, late), shift(noUsual, early)),
                List.of(seat(withUsual, 9), seat(noUsual, 8)),
                List.of(new ResolvedUsualShiftTarget(withUsual.getId(), DAY, LocalTime.of(8, 0))));

        service.repair(schedule);

        assertThat(startOf(schedule, withUsual)).isEqualTo(LocalTime.of(8, 0));
        assertThat(startOf(schedule, noUsual)).isEqualTo(LocalTime.of(9, 0));
    }

    // --- fixtures ---

    private Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private Agent agent(String name, Specialization spec, String hours) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setPrimarySpecialization(spec);
        a.setContractedHoursPerDay(new BigDecimal(hours));
        return a;
    }

    private ShiftBandPair pair(String name, LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setStartTime(start);
        t.setEndTime(end);
        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setId(UUID.randomUUID());
        band.setOffsetMinutes(180);
        band.setDurationMinutes(60);
        return new ShiftBandPair(t, band);
    }

    private AgentShiftAssignment shift(Agent agent, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(DAY);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private AgentAssignment seat(Agent agent, int hour) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(LocalTime.of(hour, 0));
        ts.setEndTime(LocalTime.of(hour + 1, 0));
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    private Schedule schedule(List<AgentShiftAssignment> shifts, List<AgentAssignment> seats,
                              List<ResolvedUsualShiftTarget> targets) {
        Schedule s = new Schedule();
        s.setId(UUID.randomUUID());
        s.setSchedulingMode(SchedulingMode.SHIFT);
        s.setShiftAssignments(new ArrayList<>(shifts));
        s.setAssignments(new ArrayList<>(seats));
        s.setResolvedUsualShiftTargets(new ArrayList<>(targets));
        return s;
    }

    private LocalTime startOf(Schedule s, Agent agent) {
        return s.getShiftAssignments().stream()
                .filter(sa -> sa.getAgent().getId().equals(agent.getId()))
                .findFirst().orElseThrow().getShiftBandPair().template().getStartTime();
    }

    private List<Integer> hoursOf(Schedule s, Agent agent) {
        return s.getAssignments().stream()
                .filter(a -> a.getAgent().getId().equals(agent.getId()))
                .map(a -> a.getTimeslot().getStartTime().getHour())
                .collect(Collectors.toList());
    }

    /** The coverage invariant: which hours are staffed, irrespective of by whom. */
    private List<LocalTime> workedHours(Schedule s) {
        return s.getAssignments().stream()
                .filter(a -> a.getAgent() != null)
                .map(a -> a.getTimeslot().getStartTime())
                .sorted().collect(Collectors.toList());
    }
}
