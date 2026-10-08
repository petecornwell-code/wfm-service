package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The saved inputs of the last Erlang C / Erlang X calculation for one business date.
 *
 * <p>Units: {@code serviceLevelTarget} and {@code retryRate} are percentages (80 means 80%);
 * {@code shrinkage} and {@code maxOccupancy} are fractions; null means off or not applicable to
 * the model.
 */
public record ErlangDemandInputResponse(LocalDate businessDate, List<Item> items) {

    public record Item(
            UUID timeslotId,
            UUID specializationId,
            LocalTime startTime,
            LocalTime endTime,
            String model,
            int callVolume,
            double aht,
            double serviceLevelTarget,
            int serviceLevelThreshold,
            Double patience,
            Double retryRate,
            Double shrinkage,
            Double maxOccupancy,
            Double concurrency
    ) {}
}
