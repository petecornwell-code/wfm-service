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
 * Phase 15 plan 15-06, Task 3 — {@code bandCapacity} (ENVL-08/D-03): a set capacity is a real
 * hard cap, a blank capacity is genuinely unlimited, capacity is scoped per date, two bands on
 * the same template have independent capacities, and a SLOT-mode desk stays silent.
 */
class BandCapacityConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 8);

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

    private static ShiftTemplateBreakBand band(ShiftTemplate template, int offsetMinutes, Integer capacity) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(template);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(60);
        b.setCapacity(capacity);
        return b;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(date);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode) {
        return new ScheduleConfig(15, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode);
    }

    // ------------------------------------------------------------------
    //  Capacity N: exactly N agent-days legal, the N+1th an exact hard violation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("capacity N with exactly N agent-days draws no penalty")
    void capacityN_exactlyNAgentDays_noPenalty() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 2);
        ShiftBandPair pair = new ShiftBandPair(t, capped);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    @Test
    @DisplayName("the N+1th agent-day on a capacity-N band draws exactly one hard violation")
    void capacityN_nPlusOneAgentDays_penalisedByOne() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 2);
        ShiftBandPair pair = new ShiftBandPair(t, capped);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(1);
    }

    @Test
    @DisplayName("far exceeding a capacity-N band scales the penalty linearly")
    void capacityN_farExceeded_linearExcess() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 2);
        ShiftBandPair pair = new ShiftBandPair(t, capped);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        for (int i = 0; i < 5; i++) {
            facts.add(shiftRow(agent(), MONDAY, pair));
        }

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(3); // 5 - 2 = 3
    }

    // ------------------------------------------------------------------
    //  D-03: blank capacity is unlimited, not zero
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a blank capacity with far more than N agent-days draws no penalty at all")
    void blankCapacity_manyAgentDays_noPenalty() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand unlimited = band(t, 240, null);
        ShiftBandPair pair = new ShiftBandPair(t, unlimited);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        for (int i = 0; i < 10; i++) {
            facts.add(shiftRow(agent(), MONDAY, pair));
        }

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Capacity is scoped per date
    // ------------------------------------------------------------------

    @Test
    @DisplayName("capacity is scoped per date -- N agent-days on Monday and N on Tuesday are both legal")
    void capacityScopedPerDate_bothDaysLegal() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 2);
        ShiftBandPair pair = new ShiftBandPair(t, capped);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), TUESDAY, pair));
        facts.add(shiftRow(agent(), TUESDAY, pair));

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Two bands on the same template have independent capacities
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two bands on the same template have independent capacities")
    void twoBandsSameTemplate_independentCapacities() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand bandA = band(t, 240, 1); // capacity 1
        ShiftTemplateBreakBand bandB = band(t, 300, 1); // capacity 1
        ShiftBandPair pairA = new ShiftBandPair(t, bandA);
        ShiftBandPair pairB = new ShiftBandPair(t, bandB);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(agent(), MONDAY, pairA)); // fills bandA's capacity exactly
        facts.add(shiftRow(agent(), MONDAY, pairB)); // fills bandB's capacity exactly

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  SLOT-mode silence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a SLOT-mode fixture draws no band-capacity penalty even when over capacity")
    void slotMode_noPenaltyEvenIfOverCapacity() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 1);
        ShiftBandPair pair = new ShiftBandPair(t, capped);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SLOT));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));
        facts.add(shiftRow(agent(), MONDAY, pair));

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Sanity: a null shiftBandPair never reaches this constraint (unassigned entities excluded
    //  by the plain forEach(AgentShiftAssignment.class) shorthand -- no NPE on getShiftBandPair())
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an unassigned (null shiftBandPair) shift row is silently excluded, not an error")
    void nullShiftBandPair_excludedWithoutError() {
        AgentShiftAssignment unassigned = shiftRow(agent(), MONDAY, null);

        assertThat(unassigned.getShiftBandPair()).isNull();

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(unassigned, scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
    }
}
