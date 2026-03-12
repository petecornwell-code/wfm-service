package com.wfm.model;

/**
 * Pre-computed per-timeslot demand totals used as a problem fact during solving.
 * Holds the total number of demand FTEs for a single timeslot, enabling the
 * bulk over-allocation and under-allocation constraints to compare total
 * assigned agents against demand bounds per timeslot.
 */
public record TimeslotDemandConfig(
        Timeslot timeslot,
        int totalDemandFTEs
) {}
