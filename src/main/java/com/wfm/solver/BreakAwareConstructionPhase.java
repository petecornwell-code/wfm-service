package com.wfm.solver;

import com.wfm.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-solve phase that prepares break-related data before the solver starts.
 *
 * <p><b>Architecture (Option C):</b> This class no longer pre-assigns agents to
 * seats. All seat allocation is delegated to Timefold's construction heuristic
 * (CH) which evaluates all 18 constraints simultaneously per move. The CH
 * naturally discovers feasible break positions through the hard break constraints
 * (exactlyOneBreak, breakDuration, breakBlockedWindow, breakStartAlignment).
 *
 * <p>The previous 6-pass brute-force pipeline (computeWorkSlots → assign →
 * repairBreakGeometry → mopUpUnassigned → repairBreakGeometry → fillUnderassigned)
 * was removed because it couldn't scale: sequential passes with no backtracking
 * lost quality at each step, and repair cascades caused underassignment.
 *
 * <p>Called by {@code SolverService.startSolve()} before launching the solver.
 * All planning variables remain null so the CH builds the initial solution.
 */
public class BreakAwareConstructionPhase {

    private static final Logger log = LoggerFactory.getLogger(BreakAwareConstructionPhase.class);

    /**
     * No-op: all seat assignment is now delegated to the solver's construction
     * heuristic. Returns 0 (no pre-assignments made).
     *
     * @return 0 — no assignments are pre-assigned
     */
    public int preAssign(Schedule schedule) {
        log.info("Break-aware construction phase: delegating all assignment to solver CH "
                + "(agents={}, assignments={})",
                schedule.getDeskAgents().size(), schedule.getAssignments().size());
        return 0;
    }
}
