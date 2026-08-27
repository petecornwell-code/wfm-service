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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link ScheduleConstraintProvider#shiftEnvelopeCompliance} — the hard constraint
 * Option A's whole coupling rests on ({@code SPIKE-COUPLING.md}). Every boundary case in this
 * plan's {@code must_haves.truths} is asserted explicitly, and the null-chosen-pair trace
 * RESEARCH.md flags as needing verification (Open Question 2) is asserted rather than assumed.
 *
 * <p>Note these tests assert the raw match count, not a score — mirroring
 * {@code MinimumStaffingConstraintTest}'s precedent for another {@code penalizeConfigurable()}
 * constraint.
 */
class ShiftEnvelopeComplianceConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static ShiftTemplate template(LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Template-" + UUID.randomUUID());
        t.setStartTime(start);
        t.setEndTime(end);
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

    private static Timeslot timeslot(LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }

    private static AgentAssignment seat(Agent agent, Timeslot ts) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
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
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode);
    }

    private static Specialization specialization(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    @Test
    @DisplayName("two templates with identical times but different ids are distinct planning values")
    void identicalTimesDifferentIds_areDistinctValues() {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);
        ShiftTemplate templateA = template(start, end);
        ShiftTemplate templateB = template(start, end); // same times, different id

        ShiftBandPair pairA = new ShiftBandPair(templateA, null);
        ShiftBandPair pairB = new ShiftBandPair(templateB, null);

        assertThat(pairA).isNotEqualTo(pairB);
    }

    @Test
    @DisplayName("a seat inside the envelope and outside the break is not penalised")
    void seatInsideEnvelopeOutsideBreak_notPenalised() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand b = band(t, 240, 60); // break 12:00-13:00
        ShiftBandPair pair = new ShiftBandPair(t, b);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(9, 0), LocalTime.of(10, 0));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a seat before the envelope start is penalised once")
    void seatBeforeEnvelopeStart_penalisedOnce() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(7, 0), LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a seat after the envelope end is penalised once")
    void seatAfterEnvelopeEnd_penalisedOnce() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(17, 0), LocalTime.of(18, 0));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a seat starting exactly at the envelope start is legal")
    void seatAtEnvelopeStart_legal() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a seat starting exactly at the envelope end is illegal")
    void seatAtEnvelopeEnd_illegal() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(17, 0), LocalTime.of(17, 30));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a seat starting exactly at the band's break start is forbidden")
    void seatAtBreakStart_forbidden() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand b = band(t, 240, 60); // break 12:00-13:00
        ShiftBandPair pair = new ShiftBandPair(t, b);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(12, 0), LocalTime.of(12, 30));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a seat starting exactly at the band's break end is legal")
    void seatAtBreakEnd_legal() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand b = band(t, 240, 60); // break 12:00-13:00
        ShiftBandPair pair = new ShiftBandPair(t, b);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(13, 0), LocalTime.of(13, 30));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a null chosen pair penalises every seat that agent holds that day")
    void nullChosenPair_penalisesEverySeatThatDay() {
        Agent agent = agent();
        Timeslot ts1 = timeslot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        Timeslot ts2 = timeslot(LocalTime.of(10, 0), LocalTime.of(11, 0));
        Timeslot ts3 = timeslot(LocalTime.of(11, 0), LocalTime.of(12, 0));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts1), seat(agent, ts2), seat(agent, ts3),
                        shiftRow(agent, null), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("identical envelopes with different bands are not interchangeable -- legal under one, forbidden under the other")
    void identicalEnvelopeDifferentBand_notInterchangeable() {
        ShiftTemplate templateA = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplateBreakBand bandA = band(templateA, 240, 60); // break 12:00-13:00
        ShiftBandPair pairA = new ShiftBandPair(templateA, bandA);

        ShiftTemplate templateB = template(LocalTime.of(8, 0), LocalTime.of(17, 0)); // same envelope
        ShiftTemplateBreakBand bandB = band(templateB, 300, 60); // break 13:00-14:00
        ShiftBandPair pairB = new ShiftBandPair(templateB, bandB);

        Agent agentA = agent();
        Agent agentB = agent();
        Timeslot ts = timeslot(LocalTime.of(12, 0), LocalTime.of(12, 30)); // inside templateA's break only

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agentA, ts), shiftRow(agentA, pairA),
                        seat(agentB, ts), shiftRow(agentB, pairB),
                        scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(1); // only agentA's seat falls inside its own assigned pair's break
    }

    @Test
    @DisplayName("a SLOT-mode ScheduleConfig penalises nothing, even with seats and shift rows present")
    void slotMode_penalisesNothingEvenWithShiftRowsPresent() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts = timeslot(LocalTime.of(7, 0), LocalTime.of(8, 0)); // would violate in SHIFT mode

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent, ts), shiftRow(agent, pair), scheduleConfig(SchedulingMode.SLOT))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("ENVL-03: specialization varies freely within the shift -- this constraint is silent on it")
    void specializationVariesWithinShift_noPenaltyFromThisConstraint() {
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        Agent agent = agent();
        Timeslot ts1 = timeslot(LocalTime.of(9, 0), LocalTime.of(10, 0));
        Timeslot ts2 = timeslot(LocalTime.of(10, 0), LocalTime.of(11, 0));

        AgentAssignment seat1 = seat(agent, ts1);
        seat1.setRequiredSpecialization(specialization("Chat"));
        AgentAssignment seat2 = seat(agent, ts2);
        seat2.setRequiredSpecialization(specialization("Voice"));

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat1, seat2, shiftRow(agent, pair), scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }
}
