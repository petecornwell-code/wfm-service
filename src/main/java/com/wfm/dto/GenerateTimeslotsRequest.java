package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record GenerateTimeslotsRequest(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
