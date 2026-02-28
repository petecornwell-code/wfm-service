package com.wfm.dto;

import java.math.BigDecimal;

public record DeskRequest(
        String name,
        String description,
        BigDecimal defaultContractedHoursPerDay
) {}
