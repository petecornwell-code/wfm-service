package com.wfm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfm.service.AppConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Live HTTP BambooHR client.
 * Active when bamboohr.mock=false.
 *
 * Uses the BambooHR REST API v1:
 * - POST /reports/custom?format=json  (custom report with explicit fields)
 * - GET  /employees/{id}?fields=...   (single employee)
 * - GET  /time_off/requests/?...       (approved time-off)
 */
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
        return "Basic " + Base64.getEncoder().encodeToString((getApiKey() + ":x").getBytes(StandardCharsets.UTF_8));
    }

    private String baseUrl() {
        return "https://api.bamboohr.com/api/gateway.php/" + getSubdomain() + "/v1";
    }

    @Override
    public List<BambooEmployee> listEmployees(String wfmTenantId, String project) {
        log.info("Fetching employees from BambooHR custom report for tenant={}, project={}", wfmTenantId, project);

        String requestBody = """
                {
                  "title": "WFM Employee Report",
                  "fields": ["id", "displayName", "workEmail", "department", "jobTitle", "status"]
                }
                """;

        String json = restClient.post()
                .uri(baseUrl() + "/reports/custom?format=JSON")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.ACCEPT, "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        List<BambooEmployee> employees = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.path("employees");

            if (rows.isMissingNode() || !rows.isArray()) {
                log.warn("BambooHR custom report response has no 'employees' array. Response: {}",
                        json != null && json.length() > 500 ? json.substring(0, 500) + "..." : json);
                return employees;
            }

            for (JsonNode emp : rows) {
                String id = emp.path("id").asText("");
                String displayName = emp.path("displayName").asText("");
                String workEmail = emp.path("workEmail").asText("");
                String department = emp.path("department").asText("");
                String jobTitle = emp.path("jobTitle").asText("");
                String status = emp.path("status").asText("Active");

                if (id.isEmpty()) continue;

                employees.add(new BambooEmployee(
                        id, displayName, workEmail, department, jobTitle, status,
                        wfmTenantId, project
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BambooHR custom report response", e);
        }

        // Filter by department/project if specified (BambooHR custom reports API
        // does not support server-side filtering, so we filter here)
        if (project != null && !project.isBlank()) {
            employees = employees.stream()
                    .filter(e -> project.equalsIgnoreCase(e.department()))
                    .collect(Collectors.toList());
        }

        log.info("Fetched {} employees from BambooHR (project filter={})", employees.size(), project);
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
                String employeeId = req.path("employeeId").asText("");
                if (employeeId.isEmpty()) continue;

                String type = req.path("type").path("name").asText("pto");

                // BambooHR time-off responses include start/end dates per request,
                // plus a "dates" object keyed by date string → amount.
                JsonNode dates = req.path("dates");
                if (dates.isObject()) {
                    Iterator<String> fieldNames = dates.fieldNames();
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
                } else if (dates.isArray()) {
                    for (JsonNode dateNode : dates) {
                        String dateStr = dateNode.asText("");
                        try {
                            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                            if (!date.isBefore(from) && !date.isAfter(to)) {
                                timeOffs.add(new BambooTimeOff(employeeId, date, type));
                            }
                        } catch (Exception ignored) {
                            // skip unparseable dates
                        }
                    }
                } else {
                    // Fallback: generate dates from start/end on the request
                    String reqStart = req.path("start").asText("");
                    String reqEnd = req.path("end").asText("");
                    if (!reqStart.isEmpty() && !reqEnd.isEmpty()) {
                        LocalDate s = LocalDate.parse(reqStart, DateTimeFormatter.ISO_LOCAL_DATE);
                        LocalDate e = LocalDate.parse(reqEnd, DateTimeFormatter.ISO_LOCAL_DATE);
                        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                            if (!d.isBefore(from) && !d.isAfter(to)) {
                                timeOffs.add(new BambooTimeOff(employeeId, d, type));
                            }
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
