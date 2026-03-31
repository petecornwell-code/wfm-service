package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record FteUploadResult(
        int savedCount,
        int skippedCount,
        List<String> savedDetails,
        List<String> skippedDetails,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
