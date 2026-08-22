package com.wfm.dto;

import com.wfm.model.DayOffType;

import java.math.BigDecimal;

public record SetDayHoursRequest(BigDecimal hours, DayOffType dayOffType, Boolean clearRow) {}
