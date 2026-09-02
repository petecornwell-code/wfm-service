package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.Timeslot;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only bridge (Phase 15 plan 15-11, Task 3) exposing {@link SolverService
 * #requireShiftEnvelopeSeatSupply} — package-private in {@code com.wfm.service} — to test
 * fixtures living in other packages (namely {@code com.wfm.solver.ShiftEnvelopeSupplyInvariantTest}).
 * Mirrors {@link SolverSeatExpansionAccess}'s precedent (plan 15-09, Task 1) exactly: keeps
 * main-source visibility of the production gate unchanged, so a future regression in the real
 * gate surfaces wherever this bridge is exercised, not in a fixture-local reimplementation of it.
 */
public final class SolverSeatSupplyGateAccess {

    private SolverSeatSupplyGateAccess() {
    }

    public static void requireShiftEnvelopeSeatSupply(
            SchedulingMode schedulingMode,
            List<AgentShiftAssignment> shiftAssignments,
            List<ShiftBandPair> shiftBandPairs,
            List<Timeslot> timeslots,
            List<AgentAssignment> assignments,
            int overallocationHardLimitPct,
            List<String> warnings,
            ConstraintWeights weights) {
        SolverService.requireShiftEnvelopeSeatSupply(schedulingMode, shiftAssignments,
                shiftBandPairs, timeslots, assignments, overallocationHardLimitPct, warnings,
                weights);
    }

    /**
     * Test-only bridge (Phase 15 plan 15-20, gap closure G-15-25/G-15-31, threat T-15-20-04)
     * exposing {@link SolverService#forcedAgentDaysByTimeslotId} — package-private in {@code
     * com.wfm.service} — to {@code com.wfm.solver.SeatSupplyDistributionAnalysisTest}. Added
     * specifically so that class can invoke the SHIPPED per-agent-day forced-occupancy count
     * (R2, plan 15-19's analysis, promoted into production by plan 15-20) rather than keep its
     * own test-local reimplementation of the same predicate — this phase's own threat register
     * (T-15-20-04) names duplicate rule implementations drifting apart as the specific defect
     * class this bridge method closes off.
     */
    public static Map<UUID, Long> forcedAgentDaysByTimeslotId(
            List<AgentShiftAssignment> rows, List<Timeslot> dateTimeslots) {
        return SolverService.forcedAgentDaysByTimeslotId(rows, dateTimeslots);
    }
}
