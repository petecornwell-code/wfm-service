package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeslotResponse(
        UUID id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {}
