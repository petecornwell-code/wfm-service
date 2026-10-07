package com.wfm.solver;

import com.wfm.model.AgentAssignment;

import java.util.Comparator;

/**
 * Difficulty comparator for AgentAssignment planning entities.
 * Used by FIRST_FIT_DECREASING construction heuristic.
 * <p>
 * More difficult assignments are placed first. Difficulty is determined by:
 * 1. Date (earlier dates first — establishes day-by-day pattern)
 * 2. Timeslot start time (earlier timeslots first — builds shifts sequentially)
 * 3. ID tiebreaker for stability
 */
public class AgentAssignmentDifficultyComparator implements Comparator<AgentAssignment> {

    @Override
    public int compare(AgentAssignment a, AgentAssignment b) {
        // Earlier date = more constrained (placed first) = "more difficult"
        // Phase 24 D-06: the CALENDAR date is deliberate here, not a missed business-date
        // migration. Calendar date plus clock time is the true chronological construction order at
        // every day-start anchor; business date plus clock time would sort a business day's 01:00
        // seat (calendar next day) BEFORE its 21:00 seat. This comparator has no anchor to correct
        // that, and it is only a construction-ordering heuristic with no correctness effect.
        // Recorded in src/test/resources/bday-join-guard.md ("Known scope boundaries").
        int dateCompare = a.getTimeslot().getDate().compareTo(b.getTimeslot().getDate());
        if (dateCompare != 0) return dateCompare;

        // Earlier timeslot within same day
        int timeCompare = a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime());
        if (timeCompare != 0) return timeCompare;

        // Tiebreaker on ID for stability
        return a.getId().compareTo(b.getId());
    }
}
