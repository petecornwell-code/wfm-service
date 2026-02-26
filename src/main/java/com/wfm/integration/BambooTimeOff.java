package com.wfm.integration;

import java.time.LocalDate;

public record BambooTimeOff(
    String employeeId,
    LocalDate date,
    String type
) {}
