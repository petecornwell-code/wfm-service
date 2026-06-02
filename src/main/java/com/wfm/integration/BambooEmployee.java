package com.wfm.integration;

public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,
    String customWorkingdays,      // raw BambooHR field 4517 value; null = not populated
    String wfmTenantId,
    String project
) {}
