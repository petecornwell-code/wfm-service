package com.wfm.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DeskResponse(
        UUID id,
        String name,
        String description,
        BigDecimal defaultContractedHoursPerDay
) {}
