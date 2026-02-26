package com.wfm.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Live HTTP BambooHR client.
 * Active when bamboohr.mock=false and credentials are configured.
 */
@Component
@ConditionalOnProperty(name = "bamboohr.mock", havingValue = "false")
public class HttpBambooHRClient implements BambooHRClient {

    @Value("${bamboohr.api-key:}")
    private String apiKey;

    @Value("${bamboohr.subdomain:}")
    private String subdomain;

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        // TODO: implement HTTP call to BambooHR REST API
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        // TODO: implement HTTP call to BambooHR REST API
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        // TODO: implement HTTP call to BambooHR REST API
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
