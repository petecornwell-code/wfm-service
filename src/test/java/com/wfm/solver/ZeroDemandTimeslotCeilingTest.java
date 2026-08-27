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
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import com.wfm.service.SolverSeatExpansionAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Converted from the characterising test {@code
 * .planning/debug/characterising-tests/ZeroDemandTimeslotHasNoCeilingGapTest.java} (Phase 15 UAT
 * gap G-15-10, plan 15-09 Task 2). Records that a zero-demand timeslot has NO over-allocation
 * ceiling at all — {@code SolverService.computeTimeslotDemandConfigs} emits one
 * {@link TimeslotDemandConfig} per timeslot PRESENT in the demand-derived assignment stream, and
 * both bulk allocation constraints reach it through an inner {@code .join(...)}, so the absence of
 * the row makes them silent. This is not a ceiling of zero, it is no ceiling — and that fact is
 * still true and unchanged by this plan.
 *
 * <p><strong>Why the absence is now safe, not a hazard (Task 1's closure).</strong> Before this
 * plan, {@code SolverService.expandMinimumStaffingSeats} manufactured a fillable seat on EVERY
 * zero-demand timeslot, mode-blind — so the missing ceiling above was reachable on a SHIFT desk at
 * any hour the shift library did not cover, making the zero-demand hour the cheapest place on the
 * grid to park an out-of-envelope agent (1 hard vs. 100 hard for leaving them short of contracted
 * hours). After Task 1, on a SHIFT desk the only timeslots that reach the solver WITHOUT a
 * {@link TimeslotDemandConfig} row are ones a live {@link ShiftBandPair} covers (operator ruling
 * OR-1) — and there, occupancy is compulsory (D-04's exact-equality net-hours filter obliges an
 * agent to occupy every non-break slot of their envelope) and must not be charged an
 * over-allocation penalty for it. An uncovered timeslot carries no seat at all, so the missing
 * ceiling is structurally unreachable there — the arbitrage this test class originally diagnosed
 * has nothing left to exploit.
 */
class ZeroDemandTimeslotCeilingTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();
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

    private static Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private static ShiftTemplate lateTemplate() {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName("Late");
        t.setStartTime(LATE_START);
        t.setEndTime(LocalTime.of(21, 0));
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    private static ScheduleConfig shiftConfig() {
        return new ScheduleConfig(60, LocalTime.of(8, 0), LocalTime.of(21, 0),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70,
                SchedulingMode.SHIFT);
    }

    // ------------------------------------------------------------------
    //  H1 — the missing TimeslotDemandConfig row, not the zero value (retained, unchanged fact)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("with NO TimeslotDemandConfig row, the over-allocation ceiling is silent at any headcount")
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
    @DisplayName("CONTROL (falsification): the SAME headcount with a zero-FTE config row present IS penalised — the row's absence is the cause")
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
    @DisplayName("the under-allocation floor is silent on the same hour for the same reason")
    void noConfigRowMeansNoUnderAllocationFloor() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(privateConstraint("bulkUnderallocationHard"))
                .given(shiftConfig(), assignment(bare, null))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  G-15-10 closure — the missing ceiling is now structurally unreachable on an uncovered
    //  SHIFT timeslot, and unchanged on a SLOT desk
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: an uncovered zero-demand timeslot carries no seat at all -- the missing ceiling is unreachable there")
    void shiftMode_uncoveredZeroDemandTimeslot_carriesNoSeatAtAll() {
        Specialization english = spec("English");
        ShiftTemplate late = lateTemplate();
        ShiftBandPair onlyPairOnTheDesk = new ShiftBandPair(late, null);
        Timeslot bare = timeslot(LocalTime.of(8, 0)); // before the desk's only shift starts at 12:00

        assertThat(onlyPairOnTheDesk.covers(bare))
                .as("sanity: this desk's only shift must not reach 08:00")
                .isFalse();

        List<AgentAssignment> extra = SolverSeatExpansionAccess.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SHIFT, List.of(onlyPairOnTheDesk), Map.of(DAY, 3));

        assertThat(extra)
                .as("no live pair covers 08:00 -- OR-1 means no seat is manufactured, so this hour "
                        + "carries no AgentAssignment for bulkOverallocationLimit/minimumStaffing to "
                        + "ever join against")
                .isEmpty();
    }

    @Test
    @DisplayName("SLOT: the seat and the missing ceiling both remain exactly as today")
    void slotMode_zeroDemandTimeslot_seatAndMissingCeilingUnchanged() {
        Specialization english = spec("English");
        Timeslot bare = timeslot(LocalTime.of(8, 0));

        List<AgentAssignment> extra = SolverSeatExpansionAccess.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(),
                List.of(), List.of(english),
                SchedulingMode.SLOT, List.of(), Map.of());

        assertThat(extra)
                .as("a SLOT desk still tops up every zero-demand hour unconditionally, exactly as before")
                .hasSize(1);
        assertThat(extra.get(0).getTimeslot()).isEqualTo(bare);
        assertThat(extra.get(0).getAgent()).isNull();

        // And the ceiling above that seat is still unreachable -- no TimeslotDemandConfig row was
        // ever created for it (SolverService step 10b runs over demand-only assignments, before
        // this filler seat exists), so the SLOT desk's over-allocation behaviour is unchanged too.
        verifier.verifyThat(privateConstraint("bulkOverallocationLimit"))
                .given(shiftConfig(), assignment(bare, agent()), assignment(bare, agent()))
                .penalizesBy(0);
    }
}
