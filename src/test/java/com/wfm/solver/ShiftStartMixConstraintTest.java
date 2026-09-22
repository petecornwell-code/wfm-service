package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftStartMixTarget;
import com.wfm.model.ShiftTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Semantics of {@link ScheduleConstraintProvider#shiftStartMix}, isolated.
 *
 * <p>The behaviour worth pinning is the ASYMMETRY: over-count is charged, under-count never is.
 * That is not a simplification — total agent-days per date is fixed, so an over-count on one start
 * is always an under-count on another and charging both double-counts one imbalance. It is also
 * what makes the constraint usable by a construction heuristic that assigns one row at a time:
 * a half-built solution is under target everywhere and must not be charged for that.
 */
class ShiftStartMixConstraintTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 31);
    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime TWELVE = LocalTime.of(12, 0);

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentShiftAssignment.class, com.wfm.model.AgentAssignment.class);

    @Test
    @DisplayName("exactly on target draws nothing")
    void onTarget() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT), target(EIGHT, 2), rows(EIGHT, 2)))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("under target draws nothing — a half-built CH solution is not a violation")
    void underTarget() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT), target(EIGHT, 5), rows(EIGHT, 2)))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("over target is charged once per excess agent-day")
    void overTarget() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT), target(EIGHT, 2), rows(EIGHT, 5)))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("a zero target makes every agent-day on that start an excess")
    void zeroTarget() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT), target(TWELVE, 0), rows(TWELVE, 4)))
                .penalizesBy(4);
    }

    @Test
    @DisplayName("starts are scored independently, and only the over-count side is charged")
    void twoStartsOnlyTheOverOneIsCharged() {
        List<Object> facts = new ArrayList<>();
        facts.add(cfg(SchedulingMode.SHIFT));
        facts.add(new ShiftStartMixTarget(MONDAY, EIGHT, 2));
        facts.add(new ShiftStartMixTarget(MONDAY, TWELVE, 4));
        facts.addAll(rows(EIGHT, 4));   // 2 over
        facts.addAll(rows(TWELVE, 2));  // 2 under, charged nothing
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts.toArray())
                .penalizesBy(2);
    }

    @Test
    @DisplayName("a different date's target never binds this date's rows")
    void datesDoNotLeak() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT),
                        new ShiftStartMixTarget(MONDAY.plusDays(1), EIGHT, 0), rows(EIGHT, 3)))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a start with no target row is unconstrained, not implicitly zero")
    void noTargetRowMeansNoOpinion() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SHIFT), target(EIGHT, 1), rows(TWELVE, 6)))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("SLOT mode is silent however far the count is over")
    void slotModeIsSilent() {
        verifier.verifyThat(ScheduleConstraintProvider::shiftStartMix)
                .given(facts(cfg(SchedulingMode.SLOT), target(EIGHT, 0), rows(EIGHT, 9)))
                .penalizesBy(0);
    }

    // ---------- fixtures ----------

    /**
     * Flattens config + target + rows into ONE fact array.
     *
     * <p>Deliberately not {@code given(cfg, target, rows.toArray())}: a trailing {@code Object[]}
     * in a varargs call is passed as a single element, not spread, so the shift rows never reach
     * the verifier and every assertion expecting zero passes for the wrong reason. Two tests here
     * caught it only because they expected a non-zero penalty.
     */
    private static Object[] facts(ScheduleConfig cfg, ShiftStartMixTarget target, List<Object> rows) {
        List<Object> all = new ArrayList<>();
        all.add(cfg);
        all.add(target);
        all.addAll(rows);
        return all.toArray();
    }

    private static ScheduleConfig cfg(SchedulingMode mode) {
        return new ScheduleConfig(60, LocalTime.of(0, 0), LocalTime.of(23, 59), 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 20,
                new BigDecimal("8.00"), 130, 70, mode, 60);
    }

    private static ShiftStartMixTarget target(LocalTime start, int count) {
        return new ShiftStartMixTarget(MONDAY, start, count);
    }

    /** {@code n} assigned shift rows on {@code start}, each held by a distinct agent. */
    private static List<Object> rows(LocalTime start, int n) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("T-" + start);
        t.setStartTime(start);
        t.setEndTime(start.plusHours(9));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        t.setValidWeekdays(EnumSet.allOf(java.time.DayOfWeek.class));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Agent a = new Agent();
            a.setId(UUID.randomUUID());
            AgentShiftAssignment sa = new AgentShiftAssignment();
            sa.setId(UUID.randomUUID());
            sa.setAgent(a);
            sa.setDate(MONDAY);
            sa.setShiftBandPair(pair);
            out.add(sa);
        }
        return out;
    }
}
