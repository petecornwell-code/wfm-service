package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ErlangXRequest(
        LocalDate from,
        LocalDate to,
        List<Item> items
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
