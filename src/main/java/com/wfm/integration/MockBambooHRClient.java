package com.wfm.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * In-memory mock BambooHR client for development.
 * Active when bamboohr.mock=true (default).
 */
@Component
@ConditionalOnProperty(name = "bamboohr.mock", havingValue = "true", matchIfMissing = true)
public class MockBambooHRClient implements BambooHRClient {

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        // TODO: return hard-coded employee data for development
        return List.of(
            new BambooEmployee("1", "Jane Smith", "jane@example.com", "Support", "Senior Agent", "Active", wfmTenantId, project),
            new BambooEmployee("2", "John Doe", "john@example.com", "Support", "Agent", "Active", wfmTenantId, project),
            new BambooEmployee("3", "Alice Brown", "alice@example.com", "Sales", "Agent", "Active", wfmTenantId, project)
        );
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        // TODO: return mock employee by id
        return new BambooEmployee(bamboohrId, "Mock Employee", "mock@example.com", "Support", "Agent", "Active", "1", "Default");
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        // TODO: return mock time-off data
        return List.of();
    }
}
