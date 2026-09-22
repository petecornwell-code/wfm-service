package com.wfm.model;

/**
 * (Phase 18, MIX-03) How far a desk lets the pre-solve shift-start mix target reach.
 *
 * <p>Three rungs rather than a boolean, because the decisive step — taking envelope choice away
 * from the solver — deserves a measurement first. {@link #REPORT} makes the gap visible on a real
 * desk without altering a single schedule, so {@link #ENFORCE} is a decision made on that desk's
 * own numbers rather than on Saferide's.
 */
public enum ShiftStartMixMode {

    /** No targets computed. The solve is byte-identical to pre-Phase-18 behaviour. */
    OFF,

    /**
     * Targets computed and scored by {@code shiftStartMix}, but every row keeps its full value
     * range. Purely observational: with a non-zero weight the mix deviation shows up in
     * {@code explain()} while the schedule is unchanged, because a weighted steer is measured not
     * to move the mix at any weight (see {@code ShiftStartMixSteerTest}).
     */
    REPORT,

    /**
     * Targets computed AND each working agent-day's value range narrowed to the envelopes starting
     * at the time it was allocated. This is the rung that actually works: the construction
     * heuristic cannot build a mix other than the target because the wrong values are no longer
     * in the range.
     *
     * <p>It removes the solver's escape hatch. The target model reasons about coverage, band
     * capacity and start-time preference; the solve reasons about all 25 constraints. Do not turn
     * this on for a desk that has not been through {@link #REPORT} first.
     */
    ENFORCE
}
