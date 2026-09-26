package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Erlang X calculation that REPLACES the live staffing requirements for its date range.
 *
 * <p>Percentages, not fractions: {@code retryRate} and {@code serviceLevelTarget} are {@code 25}
 * and {@code 80}, and the service rejects a target below 1 rather than reading it as 0.8%.
 *
 * @param adjustments shrinkage, occupancy ceiling and concurrency; null means none of them, which
 *                    is what this endpoint did unconditionally before they existed
 */
public record ErlangXRequest(
        LocalDate from,
        LocalDate to,
        List<Item> parameters,
        StaffingAdjustmentOptionsDto adjustments
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
