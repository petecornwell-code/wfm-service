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
        // REST-07/D-13: derived from the SAME buildRestWaiverDisclosure computation the detail
        // response's restWaiverDisclosure field uses -- never a second walk over the waiver
        // collection -- so the fast two-second poll and the slow detail payload can never
        // disagree about how many waivers were applied/unused. Boxed, not primitive: null is the
        // rest-not-configured signal (the schedule's snapshotted minimumRestMinutes is null) and
        // 0 means configured with nothing waived -- a primitive would collapse those two states
        // to the same number. Ride the summary specifically because the detail payload is
        // roughly 4 MB and must never be polled (see ScheduleService.getScheduleSummary's own
        // javadoc) -- a disclosure living only in detail would be invisible to an operator during
        // a running solve.
        Integer appliedRestWaiverCount,
        Integer unusedRestWaiverCount,
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
