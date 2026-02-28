package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record GenerateTimeslotsRequest(
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
