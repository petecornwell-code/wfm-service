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
        LocalTime preferredBreakTime
) {}
