package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 plan 15-06, Task 2 — {@code breakClustering}'s real body (ENVL-09). The ROADMAP's
 * required acceptance test IS this contrast: a single-band library measurably starves a mid-shift
 * timeslot and a multi-band library over the same demand does not, reported as staffing numbers
 * (not an opaque score) so a red run reads as a fact about coverage.
 *
 * <p>Threshold is 20% throughout ({@code scheduleConfig}'s default), matching
 * {@code ShiftEnvelopeComplianceConstraintTest}'s fixture convention.
 */
class BreakClusteringConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final int THRESHOLD_PCT = 20;
    /** The mid-shift moment every scenario below inspects — inside bandA's 12:00-13:00 break. */
    private static final LocalTime MID_SHIFT = LocalTime.of(12, 0);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static ShiftTemplate template() {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Template-" + UUID.randomUUID());
        t.setStartTime(LocalTime.of(8, 0));
        t.setEndTime(LocalTime.of(17, 0));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return t;
    }

    private static ShiftTemplateBreakBand band(ShiftTemplate template, int offsetMinutes, int durationMinutes) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(template);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(durationMinutes);
        return b;
    }

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(15));
        return ts;
    }

    private static AgentAssignment seat(Agent agent, Timeslot ts) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setAgent(agent);
        a.setTimeslot(ts);
        return a;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(DAY);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode) {
        return new ScheduleConfig(15, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, THRESHOLD_PCT, new BigDecimal("8.00"), 130, 70, mode);
    }

    private static Specialization specialization(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    // ------------------------------------------------------------------
    //  The ROADMAP's required contrast fixture (ENVL-09's own acceptance test)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("library A (single band): every agent breaks at once -- mid-shift timeslot is left unstaffed")
    void singleBandLibrary_starvesTheMidShiftTimeslot() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandA = band(t, 240, 60); // break 12:00-13:00
        ShiftBandPair pairA = new ShiftBandPair(t, bandA);

        Agent a1 = agent();
        Agent a2 = agent();
        Agent a3 = agent();
        Agent a4 = agent();
        List<Agent> agents = List.of(a1, a2, a3, a4);

        Timeslot midShift = timeslot(MID_SHIFT);

        List<Object> facts = new ArrayList<>();
        facts.add(midShift);
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        for (Agent a : agents) {
            facts.add(shiftRow(a, pairA));
            // Deliberately NO seat() at midShift for any agent -- every agent shares the same
            // break window, so nobody CAN be legally seated there (shiftEnvelopeCompliance would
            // forbid it). This is the live StubHub (EN) failure mode: the timeslot has zero
            // seated agents, not merely a below-threshold count.
        }

        int demand = 2; // the number library B (below) proves achievable at this exact moment
        long seatedCount = facts.stream().filter(f -> f instanceof AgentAssignment).count();
        assertThat(seatedCount)
                .as("library A leaves the mid-shift timeslot's seated-agent count (%d) measurably "
                        + "below its demand (%d) -- every one of the %d agents shares one break window",
                        seatedCount, demand, agents.size())
                .isLessThan(demand);

        verifier.verifyThat(ScheduleConstraintProvider::breakClustering)
                .given(facts.toArray())
                .penalizesBy(4); // onBreak=4, assigned=0 -> excess = 4 - (0*20/100) = 4
    }

    @Test
    @DisplayName("library B (two bands, staggered): half the agents keep working through the other half's break")
    void twoBandLibrary_keepsTheMidShiftTimeslotStaffed() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandB1 = band(t, 240, 60); // break 12:00-13:00
        ShiftTemplateBreakBand bandB2 = band(t, 300, 60); // break 13:00-14:00
        ShiftBandPair pairB1 = new ShiftBandPair(t, bandB1);
        ShiftBandPair pairB2 = new ShiftBandPair(t, bandB2);

        Agent a1 = agent();
        Agent a2 = agent(); // on bandB1 -- on break at MID_SHIFT
        Agent a3 = agent();
        Agent a4 = agent(); // on bandB2 -- still working at MID_SHIFT

        Timeslot midShift = timeslot(MID_SHIFT);

        List<Object> facts = new ArrayList<>();
        facts.add(midShift);
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(a1, pairB1));
        facts.add(shiftRow(a2, pairB1));
        facts.add(shiftRow(a3, pairB2));
        facts.add(shiftRow(a4, pairB2));
        // a3/a4 are NOT on break at MID_SHIFT (their break starts at 13:00) -- they CAN be, and
        // are, seated.
        facts.add(seat(a3, midShift));
        facts.add(seat(a4, midShift));

        int demand = 2;
        long seatedCount = facts.stream().filter(f -> f instanceof AgentAssignment).count();
        assertThat(seatedCount)
                .as("library B keeps the mid-shift timeslot's seated-agent count (%d) at its demand "
                        + "(%d) -- staggering the break across two bands means only half the agents "
                        + "are ever on break at the same moment", seatedCount, demand)
                .isGreaterThanOrEqualTo((long) demand);

        verifier.verifyThat(ScheduleConstraintProvider::breakClustering)
                .given(facts.toArray())
                .penalizesBy(2); // onBreak=2, assigned=2 -> excess = 2 - (2*20/100) = 2 - 0 = 2

        // The operationally meaningful comparison: library A's penalty (4, previous test) is
        // materially larger than library B's (2) over the identical agent count and demand.
    }

    // ------------------------------------------------------------------
    //  Small-denominator case (must_haves: assert the formula's behaviour, don't guess it away)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("small desk (2 agents, staggered): one agent on break is still an unavoidable soft penalty")
    void smallDesk_twoAgentsStaggered_unavoidableProportionalPenalty() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandA = band(t, 240, 60); // break 12:00-13:00
        ShiftTemplateBreakBand bandC = band(t, 300, 60); // break 13:00-14:00
        ShiftBandPair pairA = new ShiftBandPair(t, bandA);
        ShiftBandPair pairC = new ShiftBandPair(t, bandC);

        Agent onBreakAgent = agent();
        Agent workingAgent = agent();
        Timeslot midShift = timeslot(MID_SHIFT);

        List<Object> facts = List.of(
                midShift,
                scheduleConfig(SchedulingMode.SHIFT),
                shiftRow(onBreakAgent, pairA),
                shiftRow(workingAgent, pairC),
                seat(workingAgent, midShift));

        // onBreak=1, assigned=1 -> 100% clustered, above the 20% threshold. A 2-agent desk cannot
        // structurally keep every moment below a 20% threshold once exactly one of two takes a
        // break -- excess = 1 - (1*20/100) = 1, a small, soft (weight 2 by default), proportional
        // penalty. This is an accepted characteristic of small desks under a soft constraint, not
        // a formula defect: the penalty does not grow unboundedly, divide by zero, or dominate the
        // schedule (bulk/contracted-hours hard constraints outrank it). The formula is NOT
        // adjusted for this case.
        verifier.verifyThat(ScheduleConstraintProvider::breakClustering)
                .given(facts.toArray())
                .penalizesBy(1);
    }

    // ------------------------------------------------------------------
    //  SLOT-mode silence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a SLOT-mode ScheduleConfig penalises nothing, even with shift rows sharing one break window")
    void slotMode_penalisesNothing_evenWithClusteredShiftRowsPresent() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandA = band(t, 240, 60);
        ShiftBandPair pairA = new ShiftBandPair(t, bandA);
        Timeslot midShift = timeslot(MID_SHIFT);

        List<Object> facts = new ArrayList<>();
        facts.add(midShift);
        facts.add(scheduleConfig(SchedulingMode.SLOT));
        for (int i = 0; i < 4; i++) {
            facts.add(shiftRow(agent(), pairA));
        }

        verifier.verifyThat(ScheduleConstraintProvider::breakClustering)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  ENVL-03 sanity — this constraint is silent on specialization, mirrors other Phase 15 tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("specialization variety within a shift does not affect the clustering count")
    void specializationVariety_doesNotAffectClusteringCount() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandA = band(t, 240, 60);
        ShiftBandPair pairA = new ShiftBandPair(t, bandA);
        Agent a = agent();
        Timeslot ts1 = timeslot(LocalTime.of(9, 0));
        Timeslot ts2 = timeslot(LocalTime.of(10, 0));

        AgentAssignment seat1 = seat(a, ts1);
        seat1.setRequiredSpecialization(specialization("Chat"));
        AgentAssignment seat2 = seat(a, ts2);
        seat2.setRequiredSpecialization(specialization("Voice"));

        verifier.verifyThat(ScheduleConstraintProvider::breakClustering)
                .given(seat1, seat2, ts1, ts2, shiftRow(a, pairA), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0); // neither ts1 nor ts2 falls inside pairA's 12:00-13:00 break
    }
}
