package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleSummary(
        UUID id,
        UUID deskId,
        String status,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes,
        ScoreDto score,
        Boolean feasible,
        OffsetDateTime createdAt,
        int version
) {
    public record ScoreDto(
            Integer hardScore,
            Integer softScore
    ) {}
}
