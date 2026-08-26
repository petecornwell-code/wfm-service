package com.wfm.dto;

import com.wfm.model.SchedulingMode;

import java.math.BigDecimal;
import java.util.UUID;

public record DeskResponse(
        UUID id,
        String name,
        String description,
        BigDecimal defaultContractedHoursPerDay,
        SchedulingMode schedulingMode
) {}
