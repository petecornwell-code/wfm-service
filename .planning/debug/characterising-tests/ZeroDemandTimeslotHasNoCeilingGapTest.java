package com.wfm.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHARACTERISING test (Phase 15 UAT gap, debug session
 * {@code .planning/debug/min-staffing-seats-zero-demand.md}) — records that a zero-demand
 * timeslot has NO over-allocation ceiling at all, and works out what that costs on a
 * shift-scheduled desk.
 *
 * <p>{@code SolverService.computeTimeslotDemandConfigs} emits one {@link TimeslotDemandConfig}
 * per timeslot PRESENT in the demand-derived assignment stream. {@code FteUploadService} skips a
 * demand cell of zero, so a zero-demand hour has no {@code StaffingRequirement}, no demand seat,
 * and therefore no config row. Both bulk allocation constraints reach {@link TimeslotDemandConfig}
 * through an inner {@code .join(...)}, so the absence of the row makes them silent — this is not a
 * ceiling of zero, it is no ceiling.
 *
 * <p>These assertions pin the CURRENT behaviour. They are not a statement that it is correct.
 */
class ZeroDemandTimeslotHasNoCeilingGapTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 1, 12);
    private static final LocalTime LATE_START = LocalTime.of(12, 0);

    /** Reaches a {@code private} constraint method without touching production source. */
    private static BiFunction<ScheduleConstraintProvider, ConstraintFactory, Constraint> privateConstraint(String name) {
        return (provider, factory) -> {
            try {
                Method m = ScheduleConstraintProvider.class.getDeclaredMethod(name, ConstraintFactory.class);
                m.setAccessible(true);
                return (Constraint) m.invoke(provider, factory);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot reach " + name, e);
            }
        };
    }

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AgentAssignment assignment(Timeslot ts, Agent agent) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    private static ShiftTemplate lateTemplate() {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Late");
        t.setStartTime(LATE_START);
        t.setEndTime(LocalTime.of(21, 0));
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(DAY);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static ScheduleConfig shiftConfig() {
        return new ScheduleConfig(60, LocalTime.of(8, 0), LocalTime.of(21, 0),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70,
                SchedulingMode.SHIFT);
    }

    // ------------------------------------------------------------------
    //  H1 — the missing TimeslotDemandConfig row, not the zero value
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CURRENT: with NO TimeslotDemandConfig row, the over-allocation ceiling is silent at any headcount")
    void noConfigRowMeansNoOverAllocationCeiling() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(privateConstraint("bulkOverallocationLimit"))
                .given(shiftConfig(),
                        assignment(bare, agent()),
                        assignment(bare, agent()),
                        assignment(bare, agent()))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("CONTROL: the SAME headcount with a zero-FTE config row present IS penalised — the row's absence is the cause")
    void aZeroFteConfigRowWouldHavePenalisedIt() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(privateConstraint("bulkOverallocationLimit"))
                .given(shiftConfig(),
                        new TimeslotDemandConfig(bare, 0),
                        assignment(bare, agent()),
                        assignment(bare, agent()),
                        assignment(bare, agent()))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("CURRENT: the under-allocation floor is silent on the same hour for the same reason")
    void noConfigRowMeansNoUnderAllocationFloor() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(privateConstraint("bulkUnderallocationHard"))
                .given(shiftConfig(), assignment(bare, null))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  H2 — what that silence is worth against the envelope
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CURRENT: minimum staffing pays 1000 soft to put ANY agent on the bare hour")
    void minimumStaffingRewardsFillingTheBareHour() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(bare, null))
                .penalizesBy(1);
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(bare, agent()))
                .penalizesBy(0);

        assertThat(new ConstraintWeights().getMinStaffingWeight().softScore())
                .as("the swing the solver books for filling one bare hour")
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("CURRENT: filling it from a 12:00 shift costs exactly 1 hard — the cheapest hard weight in the file")
    void seatingOutsideTheEnvelopeCostsOneHard() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        Agent lateAgent = agent();
        ShiftBandPair late = new ShiftBandPair(lateTemplate(), null);

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(shiftConfig(), shiftRow(lateAgent, late), assignment(bare, lateAgent))
                .penalizesBy(1);

        ConstraintWeights w = new ConstraintWeights();
        assertThat(w.getShiftEnvelopeComplianceWeight().hardScore()).isEqualTo(1);
        assertThat(w.getContractedHoursUnderWeight().hardScore())
                .as("leaving that agent one slot short instead costs 100x more hard")
                .isEqualTo(100);
        assertThat(w.getContractedHoursUnderZeroWeight().hardScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("CURRENT: the bare hour is the CHEAPEST place on the grid to park an out-of-envelope agent")
    void theBareHourIsCheaperThanAnyDemandHourAtItsCeiling() {
        // Two candidate out-of-envelope destinations for one Late-shift agent:
        //   bare  08:00 — zero demand, no config row, one min-staffing filler seat
        //   thin  09:00 — demand 1, already at its 130% ceiling (1 * 130 / 100 == 1)
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        Timeslot thin = timeslot(LocalTime.of(9, 0));
        Agent lateAgent = agent();
        Agent incumbent = agent();
        ShiftBandPair late = new ShiftBandPair(lateTemplate(), null);

        // Destination A: the bare hour. Over-allocation stays silent.
        verifier.verifyThat(privateConstraint("bulkOverallocationLimit"))
                .given(shiftConfig(),
                        new TimeslotDemandConfig(thin, 1),
                        assignment(thin, incumbent),
                        assignment(bare, lateAgent))
                .penalizesBy(0, "no config row for 08:00 -> the extra body there is free");

        // Destination B: the thin demand hour, already full. Over-allocation bites.
        verifier.verifyThat(privateConstraint("bulkOverallocationLimit"))
                .given(shiftConfig(),
                        new TimeslotDemandConfig(thin, 1),
                        assignment(thin, incumbent),
                        assignment(thin, lateAgent))
                .penalizesBy(1, "2 assigned vs a ceiling of 1");

        // Both destinations violate the envelope identically...
        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(shiftConfig(), shiftRow(lateAgent, late), assignment(bare, lateAgent))
                .penalizesBy(1);
        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(shiftConfig(), shiftRow(lateAgent, late), assignment(thin, lateAgent))
                .penalizesBy(1);

        // ...so A costs 1 hard and B costs 2 hard, and A additionally banks 1000 soft.
        ConstraintWeights w = new ConstraintWeights();
        int destinationA = w.getShiftEnvelopeComplianceWeight().hardScore()
                + 0 * w.getBulkOverallocationLimitWeight().hardScore();
        int destinationB = w.getShiftEnvelopeComplianceWeight().hardScore()
                + 1 * w.getBulkOverallocationLimitWeight().hardScore();
        assertThat(destinationA)
                .as("the zero-demand hour wins on the HARD level before soft is even consulted")
                .isLessThan(destinationB);
    }
}
