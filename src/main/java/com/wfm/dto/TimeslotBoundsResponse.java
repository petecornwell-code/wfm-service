package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeslotBoundsResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
