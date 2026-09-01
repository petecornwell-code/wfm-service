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
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#shiftWorkContiguity} — G-15-27.
 *
 * <p>WHY THIS TEST EXISTS AT ALL, stated plainly because the phase has been bitten by its absence
 * twice. V44's bounded envelope slack removed a contiguity guarantee that D-01's exact-equality
 * rule had been providing BY ACCIDENT, and nothing caught it: no test in the suite asserted that a
 * SHIFT-mode agent-day was contiguous, so 24 of 138 agent-days on the live desk were split before
 * an operator noticed. G-15-22 records the same shape for solver tuning — a change that regressed
 * the live desk sevenfold shipped with a fully green build. The guard ships with the fix.
 *
 * <p>It also earned its keep immediately: the first implementation counted interior GAPS and
 * allowed one for "the break". {@link #breakAtEnvelopeBoundary_stillDetectsTheHole} failed against
 * it, because a band breaking at the envelope's first hour creates no interior gap, so the single
 * permitted gap was a real hole. That is the live Adaeze Dawari shape from schedule 709fd8b4, and
 * a counting rule scores it zero. The constraint now resolves the actual break window instead.
 *
 * <p>Assertions are on raw match counts rather than scores, mirroring
 * {@code ShiftEnvelopeComplianceConstraintTest} and {@code MinimumStaffingConstraintTest}'s
 * precedent for {@code penalizeConfigurable()} constraints.
 */
class ShiftWorkContiguityConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 1, 11);
    private static final int INCREMENT = 60;

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static Timeslot timeslot(int hour) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(LocalTime.of(hour, 0));
        ts.setEndTime(LocalTime.of(hour + 1, 0));
        return ts;
    }

    private static AgentAssignment seat(Agent agent, int hour) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(timeslot(hour));
        a.setAgent(agent);
        return a;
    }

    /** Envelope {@code startHour}-{@code endHour} with a one-hour break at {@code breakHour}. */
    private static ShiftBandPair pair(int startHour, int endHour, int breakHour) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("T-" + UUID.randomUUID());
        t.setStartTime(LocalTime.of(startHour, 0));
        t.setEndTime(LocalTime.of(endHour, 0));
        t.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(t);
        b.setOffsetMinutes((breakHour - startHour) * 60);
        b.setDurationMinutes(60);
        return new ShiftBandPair(t, b);
    }

    private static AgentShiftAssignment shiftRow(Agent agent, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(DAY);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static ScheduleConfig config(SchedulingMode mode) {
        return new ScheduleConfig(INCREMENT, LocalTime.of(8, 0), LocalTime.of(21, 0),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 250, 50, mode);
    }

    private static Object[] facts(Agent agent, ShiftBandPair pair, SchedulingMode mode, int... hours) {
        List<Object> f = new ArrayList<>();
        for (int h : hours) {
            f.add(seat(agent, h));
        }
        f.add(shiftRow(agent, pair));
        f.add(config(mode));
        return f.toArray();
    }

    @Test
    @DisplayName("A contiguous day whose only gap is the assigned break is legal")
    void contiguousDayWithBreakGap_scoresNothing() {
        Agent a = agent();
        // Envelope 10:00-19:00, break 14:00. Works every legal slot.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 19, 14), SchedulingMode.SHIFT,
                        10, 11, 12, 13, 15, 16, 17, 18))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("THE DEFECT: an unworked slot that is not the break splits the day")
    void nonBreakHole_isPenalised() {
        Agent a = agent();
        // Break is 14:00, but 12:00 is also unworked — a genuine hole.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 14), SchedulingMode.SHIFT,
                        10, 11, 13, 15, 16, 17, 18, 19))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("Break at the envelope boundary still detects the hole — the gap-counting trap")
    void breakAtEnvelopeBoundary_stillDetectsTheHole() {
        Agent a = agent();
        // Live shape, schedule 709fd8b4 (Adaeze Dawari): band breaks at 10:00, the envelope's
        // FIRST hour, so it creates no interior gap. Working 11, idle 12, then 13-19 leaves
        // exactly one interior gap — which a "one gap allowed for the break" rule would permit.
        // It is a hole, and must be caught.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 10), SchedulingMode.SHIFT,
                        11, 13, 14, 15, 16, 17, 18, 19))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("A day fragmented into three pieces is penalised once per hole")
    void doublySplitShift_isPenalisedPerHole() {
        Agent a = agent();
        // Break 14:00; holes at 12:00 and 17:00.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 14), SchedulingMode.SHIFT,
                        10, 11, 13, 15, 16, 18, 19))
                .penalizesBy(2);
    }

    @Test
    @DisplayName("Slack spent at the START boundary is legal — a later start adds no hole")
    void slackSpentAtStartBoundary_scoresNothing() {
        Agent a = agent();
        // Envelope 10:00-20:00 (9h net), 8 contracted hours, break 14:00.
        // Agent starts at 11:00, leaving 10:00 unworked at the boundary.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 14), SchedulingMode.SHIFT,
                        11, 12, 13, 15, 16, 17, 18, 19))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("Slack spent at the END boundary is legal — an earlier finish adds no hole")
    void slackSpentAtEndBoundary_scoresNothing() {
        Agent a = agent();
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 14), SchedulingMode.SHIFT,
                        10, 11, 12, 13, 15, 16, 17, 18))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("SLOT mode is untouched — exactlyOneBreak already owns contiguity there")
    void slotMode_isNotPenalisedByThisConstraint() {
        Agent a = agent();
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(facts(a, pair(10, 20, 14), SchedulingMode.SLOT,
                        10, 11, 13, 15, 16, 17, 18, 19))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("A null band pair is NOT an exemption — the loophole that let the solver launder splits")
    void nullBandPair_stillPenalisedByGapFallback() {
        Agent a = agent();
        // Exempting null pairs made dropping the shift assignment the cheapest way to hide a split:
        // a null pair costs ofHard(1) per seat via envelope compliance (~8) against ofHard(100) for
        // one hole here, so the solver simply stopped choosing pairs. ShiftDeskEndToEndRegressionTest
        // caught it. With no band there is no identifiable break, so the day falls back to
        // "at most one interior gap": 10,11,13,15 has gaps at 12 and 14 -> one allowed, one charged.
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(seat(a, 10), seat(a, 11), seat(a, 13), seat(a, 15),
                        shiftRow(a, null), config(SchedulingMode.SHIFT))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("A null band pair with a single contiguous gap is still tolerated by the fallback")
    void nullBandPair_singleGapTolerated() {
        Agent a = agent();
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(seat(a, 10), seat(a, 11), seat(a, 13), seat(a, 14),
                        shiftRow(a, null), config(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("Two agents split on the same day are penalised independently")
    void holesAreCountedPerAgentDay() {
        Agent a = agent();
        Agent b = agent();
        ShiftBandPair p = pair(10, 20, 14);
        verifier.verifyThat(ScheduleConstraintProvider::shiftWorkContiguity)
                .given(seat(a, 10), seat(a, 12), seat(a, 13), seat(a, 15),
                        seat(b, 10), seat(b, 11), seat(b, 13), seat(b, 16),
                        shiftRow(a, p), shiftRow(b, p), config(SchedulingMode.SHIFT))
                .penalizesBy(3);
    }
}
