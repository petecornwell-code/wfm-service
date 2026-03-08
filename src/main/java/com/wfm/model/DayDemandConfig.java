package com.wfm.model;

import java.time.LocalDate;

/**
 * Pre-computed per-day demand totals used as a problem fact during solving.
 * Holds the total number of demand slots for a single day, enabling the
 * bulk over-allocation and under-allocation constraints to compare total
 * assigned slots against demand bounds (70%-130%).
 */
public record DayDemandConfig(
        LocalDate date,
        int totalDemandSlots
) {}
