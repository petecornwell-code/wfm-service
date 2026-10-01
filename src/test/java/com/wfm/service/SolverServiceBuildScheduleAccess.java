package com.wfm.service;

import com.wfm.dto.SolveRequest;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;

import java.util.UUID;

/**
 * Test-only bridge (BDAY-04, plan 19-01) exposing {@link SolverService#buildSchedule} —
 * package-private and static in {@code com.wfm.service} — to test fixtures living in other
 * packages (namely {@code com.wfm.solver.ScheduleConfigAnchorPlumbingTest}). Mirrors {@link
 * SolverSeatSupplyGateAccess}'s and {@link SolverSeatExpansionAccess}'s precedent exactly: keeps
 * main-source visibility of the production builder unchanged, so a future regression in the real
 * desk-to-schedule anchor hop surfaces wherever this bridge is exercised, not in a fixture-local
 * reimplementation of it.
 */
public final class SolverServiceBuildScheduleAccess {

    private SolverServiceBuildScheduleAccess() {
    }

    public static Schedule buildSchedule(long tenantId, UUID deskId, SolveRequest request, Desk desk) {
        return SolverService.buildSchedule(tenantId, deskId, request, desk);
    }
}
