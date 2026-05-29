package com.wfm.integration;

public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,
    String wfmTenantId,
    String project
) {}
