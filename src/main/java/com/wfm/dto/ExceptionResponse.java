package com.wfm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExceptionResponse(
        UUID id,
        LocalDate date,
        BigDecimal contractedHoursOverride,
        String reason
) {}
