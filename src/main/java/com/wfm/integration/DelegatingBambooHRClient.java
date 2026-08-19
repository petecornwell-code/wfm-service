package com.wfm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfm.service.AppConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Delegates to HttpBambooHRClient when server and API key are configured in the
 * database (via the Configuration UI), otherwise falls back to MockBambooHRClient.
 *
 * Marked @Primary so all services get this by default — using live BambooHR when
 * credentials are configured and falling back to mock data otherwise.
 */
@Component
@Primary
public class DelegatingBambooHRClient implements BambooHRClient {

    private static final Logger log = LoggerFactory.getLogger(DelegatingBambooHRClient.class);

    private final AppConfigurationService configurationService;
    private final HttpBambooHRClient httpClient;
    private final MockBambooHRClient mockClient;

    public DelegatingBambooHRClient(AppConfigurationService configurationService,
                                     MockBambooHRClient mockClient,
                                     ObjectMapper objectMapper,
                                     @Value("${bamboohr.http.connect-timeout-seconds:10}") int connectTimeoutSeconds,
                                     @Value("${bamboohr.http.read-timeout-seconds:120}") int readTimeoutSeconds) {
        this.configurationService = configurationService;
        this.httpClient = new HttpBambooHRClient(
                configurationService, objectMapper, connectTimeoutSeconds, readTimeoutSeconds);
        this.mockClient = mockClient;
    }

    private boolean isConfigured() {
        String server = configurationService.getConfigValue(AppConfigurationService.BAMBOOHR_SERVER);
        String apiKey = configurationService.getConfigValue(AppConfigurationService.BAMBOOHR_API_KEY);
        return server != null && !server.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    private BambooHRClient delegate() {
        if (isConfigured()) {
            log.debug("BambooHR credentials configured — using live HTTP client");
            return httpClient;
        }
        log.debug("BambooHR credentials not configured — using mock client");
        return mockClient;
    }

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        return delegate().listEmployees(wfmTenantId, project);
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        return delegate().getEmployee(bamboohrId);
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        return delegate().listTimeOff(wfmTenantId, from, to);
    }
}
