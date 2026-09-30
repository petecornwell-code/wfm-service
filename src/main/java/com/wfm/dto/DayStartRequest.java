package com.wfm.dto;

import java.time.LocalTime;

public record DayStartRequest(
        LocalTime dayStart
) {}
