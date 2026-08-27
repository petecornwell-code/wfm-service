package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.Timeslot;

import java.util.List;

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
            List<String> warnings) {
        SolverService.requireShiftEnvelopeSeatSupply(schedulingMode, shiftAssignments,
                shiftBandPairs, timeslots, assignments, overallocationHardLimitPct, warnings);
    }
}
