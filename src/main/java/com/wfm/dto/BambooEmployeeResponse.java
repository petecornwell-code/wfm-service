package com.wfm.dto;

public record BambooEmployeeResponse(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status
) {}
