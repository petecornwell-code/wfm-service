package com.wfm.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PreferenceResponse(
        UUID id,
        DayOfWeek dayOfWeek,
        LocalDate date,
        boolean isStanding,
        LocalTime preferredStartTime,
        LocalTime preferredBreakTime,
        /**
         * Set only in a save response, when the preferred start was moved to the nearest shift start
         * on a shift-scheduled desk: the start as entered. Null otherwise, and ignored on input.
         */
        LocalTime adjustedFrom
) {
    public PreferenceResponse(java.util.UUID id, DayOfWeek dayOfWeek, LocalDate date, boolean isStanding,
                              LocalTime preferredStartTime, LocalTime preferredBreakTime) {
        this(id, dayOfWeek, date, isStanding, preferredStartTime, preferredBreakTime, null);
    }
}
