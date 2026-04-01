package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;

public record DepartmentTimeOffResponse(
    String employeeId,
    String displayName,
    LocalDate date,
    String type
) {}
