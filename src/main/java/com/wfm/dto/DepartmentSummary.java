package com.wfm.dto;

/**
 * A BambooHR department that holds at least one person this system can schedule.
 *
 * @param name             the department exactly as BambooHR spells it, which is what the employee
 *                         search matches on — case matters to nobody but spelling does
 * @param schedulableCount active employees in it whose job title passes the tenant's allowlist
 */
public record DepartmentSummary(String name, int schedulableCount) {}
