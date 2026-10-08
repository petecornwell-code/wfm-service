package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Erlang X calculation that REPLACES the live staffing requirements of ONE business date, plus
 * those of each {@code copyTo} date, and nothing else.
 *
 * <p>Percentages, not fractions: {@code retryRate} and {@code serviceLevelTarget} are {@code 25}
 * and {@code 80}, and the service rejects a target below 1 rather than reading it as 0.8%.
 *
 * <p>A request without a {@code businessDate} is refused with 400 before anything is deleted. An
 * old client that still sends {@code from}/{@code to} has those fields ignored and is refused the
 * same way, so it can never trigger a period-wide replace.
 *
 * @param businessDate the one business date whose live requirements are replaced; every item's
 *                     timeslot must belong to it
 * @param adjustments  shrinkage, occupancy ceiling and concurrency; null means none of them, which
 *                     is what this endpoint did unconditionally before they existed
 * @param copyTo       optional (null or empty means none): other business dates that receive the
 *                     source date's per-slot result, matched by equal start and end time. Only
 *                     these dates are replaced; dates not listed are unchanged
 */
public record ErlangXRequest(
        LocalDate businessDate,
        List<Item> parameters,
        StaffingAdjustmentOptionsDto adjustments,
        List<LocalDate> copyTo
) {
    public record Item(
            UUID timeslotId,
            UUID specializationId,
            int callVolume,
            double aht,
            double patience,
            double retryRate,
            double serviceLevelTarget,
            int serviceLevelThreshold
    ) {}
}
