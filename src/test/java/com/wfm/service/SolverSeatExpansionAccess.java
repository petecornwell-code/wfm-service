package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only bridge (plan 15-09, Task 1) exposing {@link SolverService#expandMinimumStaffingSeats}
 * — package-private in {@code com.wfm.service} — to test fixtures living in other packages
 * (namely {@code com.wfm.solver.ShiftModeFixtures}, plan 15-09 Task 2). Keeps main-source
 * visibility of the production method unchanged; this is a test-source-only forwarding call, so
 * a future regression in the real expansion method surfaces wherever this bridge is exercised,
 * not in a fixture-local reimplementation of it.
 */
public final class SolverSeatExpansionAccess {

    private SolverSeatExpansionAccess() {
    }

    public static List<AgentAssignment> expandMinimumStaffingSeats(
            long tenantId, UUID deskId, UUID scheduleId,
            List<Timeslot> timeslots,
            List<AgentAssignment> existingAssignments,
            List<StaffingRequirement> staffingRequirements,
            List<Specialization> specializations,
            SchedulingMode schedulingMode,
            List<ShiftBandPair> shiftBandPairs,
            Map<LocalDate, Integer> workingAgentDaysByDate) {
        return SolverService.expandMinimumStaffingSeats(
                tenantId, deskId, scheduleId, timeslots, existingAssignments,
                staffingRequirements, specializations,
                schedulingMode, shiftBandPairs, workingAgentDaysByDate);
    }
}
