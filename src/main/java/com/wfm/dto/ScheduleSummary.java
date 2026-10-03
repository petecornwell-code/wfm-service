package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleSummary(
        UUID id,
        UUID deskId,
        String deskName,
        String status,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes,
        // OVNT-02/D-15: the anchor the schedule was SOLVED against -- part of the solved
        // schedule's identity, not a live desk value, which is why it is read from the schedule
        // itself (Schedule.getDayStart()) rather than from the desk. Rides the summary, not only
        // the detail response, so the allocation grid can order columns during the fast 2s poll
        // without a second fetch for the slower detail payload.
        LocalTime dayStart,
        ScoreDto score,
        Boolean feasible,
        OffsetDateTime feasibleAt,
        OffsetDateTime createdAt,
        int version
) {
    public record ScoreDto(
            Integer hardScore,
            Integer softScore
    ) {}
}
