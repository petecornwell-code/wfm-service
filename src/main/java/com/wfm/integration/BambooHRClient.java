package com.wfm.integration;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for BambooHR API operations.
 * Two implementations: MockBambooHRClient (dev) and HttpBambooHRClient (prod).
 */
public interface BambooHRClient {

    List<BambooEmployee> listEmployees(String wfmTenantId, String project);

    BambooEmployee getEmployee(String bamboohrId);

    List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to);
}
