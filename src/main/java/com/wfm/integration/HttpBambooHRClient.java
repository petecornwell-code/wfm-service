package com.wfm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfm.service.AppConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Live HTTP BambooHR client.
 * Active when bamboohr.mock=false and credentials are configured.
 *
 * Uses the BambooHR REST API v1:
 * - GET /api/gateway.php/{subdomain}/v1/employees/directory  (employee directory)
 * - GET /api/gateway.php/{subdomain}/v1/employees/{id}?fields=...  (single employee)
 * - GET /api/gateway.php/{subdomain}/v1/time_off/requests?start=...&end=...&status=approved
 */
@Component
@ConditionalOnProperty(name = "bamboohr.mock", havingValue = "false")
public class HttpBambooHRClient implements BambooHRClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBambooHRClient.class);

    private final AppConfigurationService configurationService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpBambooHRClient(AppConfigurationService configurationService, ObjectMapper objectMapper) {
        this.configurationService = configurationService;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    private String getSubdomain() {
        String server = configurationService.getConfigValue(AppConfigurationService.BAMBOOHR_SERVER);
        if (server == null || server.isBlank()) {
            throw new IllegalStateException("BambooHR server is not configured. Please set it in Configuration.");
        }
        // Support full hostname (e.g. "acme.bamboohr.com") or just subdomain ("acme")
        return server.contains(".") ? server.split("\\.")[0] : server;
    }

    private String getApiKey() {
        String key = configurationService.getConfigValue(AppConfigurationService.BAMBOOHR_API_KEY);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("BambooHR API key is not configured. Please set it in Configuration.");
        }
        return key;
    }

    private String basicAuth() {
        // BambooHR uses the API key as username with "x" as the password
        return "Basic " + Base64.getEncoder().encodeToString((getApiKey() + ":x").getBytes());
    }

    private String baseUrl() {
        return "https://api.bamboohr.com/api/gateway.php/" + getSubdomain() + "/v1";
    }

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        log.info("Fetching employee directory from BambooHR for tenant={}, project={}", wfmTenantId, project);

        String json = restClient.get()
                .uri(baseUrl() + "/employees/directory")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(String.class);

        List<BambooEmployee> employees = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode fields = root.path("fields");
            JsonNode rows = root.path("employees");

            for (JsonNode emp : rows) {
                String id = emp.path("id").asText();
                String displayName = emp.path("displayName").asText("");
                String workEmail = emp.path("workEmail").asText("");
                String department = emp.path("department").asText("");
                String jobTitle = emp.path("jobTitle").asText("");
                String status = emp.path("status").asText("Active");

                employees.add(new BambooEmployee(
                        id, displayName, workEmail, department, jobTitle, status,
                        wfmTenantId, project
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BambooHR employee directory response", e);
        }

        log.info("Fetched {} employees from BambooHR directory", employees.size());
        return employees;
    }

    @Override
    public BambooEmployee getEmployee(String bamboohrId) {
        log.info("Fetching employee {} from BambooHR", bamboohrId);

        String fields = "displayName,workEmail,department,jobTitle,status";
        String json = restClient.get()
                .uri(baseUrl() + "/employees/" + bamboohrId + "?fields=" + fields)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(String.class);

        try {
            JsonNode emp = objectMapper.readTree(json);
            return new BambooEmployee(
                    emp.path("id").asText(bamboohrId),
                    emp.path("displayName").asText(""),
                    emp.path("workEmail").asText(""),
                    emp.path("department").asText(""),
                    emp.path("jobTitle").asText(""),
                    emp.path("status").asText("Active"),
                    "", ""
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BambooHR employee response for id=" + bamboohrId, e);
        }
    }

    @Override
    public List<BambooTimeOff> listTimeOff(String wfmTenantId, LocalDate from, LocalDate to) {
        log.info("Fetching time-off requests from BambooHR ({} to {})", from, to);

        String start = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String end = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        String json = restClient.get()
                .uri(baseUrl() + "/time_off/requests/?start=" + start + "&end=" + end + "&status=approved")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(String.class);

        List<BambooTimeOff> timeOffs = new ArrayList<>();
        try {
            JsonNode rows = objectMapper.readTree(json);
            for (JsonNode req : rows) {
                String employeeId = req.path("employeeId").asText();
                String type = req.path("type").path("name").asText("pto");
                JsonNode dates = req.path("dates");

                // BambooHR returns individual dates within each request
                for (JsonNode dateKey : dates) {
                    String dateStr = dateKey.asText();
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    if (!date.isBefore(from) && !date.isAfter(to)) {
                        timeOffs.add(new BambooTimeOff(employeeId, date, type));
                    }
                }

                // Alternatively, if dates is an object with date keys
                if (dates.isObject()) {
                    var fieldNames = dates.fieldNames();
                    while (fieldNames.hasNext()) {
                        String dateStr = fieldNames.next();
                        try {
                            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                            if (!date.isBefore(from) && !date.isAfter(to)) {
                                timeOffs.add(new BambooTimeOff(employeeId, date, type));
                            }
                        } catch (Exception ignored) {
                            // skip unparseable date keys
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BambooHR time-off response", e);
        }

        log.info("Fetched {} time-off entries from BambooHR", timeOffs.size());
        return timeOffs;
    }
}
