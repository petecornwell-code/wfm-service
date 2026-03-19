package com.wfm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for fetching custom field values from the BambooHR API.
 */
public final class BambooCustomFields {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String CUSTOM_FIELD_ID = "4620";

    private BambooCustomFields() {}

    /**
     * Fetches all values of custom field 4620 for every employee.
     * Returns a map of employeeId → field value.
     * Employees whose field value is null or empty are excluded.
     *
     * @param subdomain BambooHR company subdomain
     * @param apiKey    BambooHR API key
     * @return map of employee ID to custom field 4620 value
     */
    public static Map<String, String> getAllCustomField4620Values(String subdomain, String apiKey) {
        String url = "https://api.bamboohr.com/api/gateway.php/"
                + subdomain + "/v1/reports/custom?format=JSON";

        String requestBody = """
                {
                  "title": "Custom Field 4620 Report",
                  "fields": ["id", "displayName", "customField4620"]
                }
                """;

        String credentials = Base64.getEncoder()
                .encodeToString((apiKey + ":x").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("BambooHR API returned status "
                        + response.statusCode() + ": " + response.body());
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode employees = root.path("employees");

            Map<String, String> result = new LinkedHashMap<>();
            for (JsonNode emp : employees) {
                String id = emp.path("id").asText();
                String value = emp.path("customField4620").asText(null);
                if (value != null && !value.isBlank()) {
                    result.put(id, value);
                }
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling BambooHR API", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch custom field 4620 from BambooHR", e);
        }
    }

    /**
     * Returns just the distinct values (no duplicates) of custom field 4620.
     *
     * @param subdomain BambooHR company subdomain
     * @param apiKey    BambooHR API key
     * @return list of distinct non-blank values
     */
    public static List<String> getDistinctCustomField4620Values(String subdomain, String apiKey) {
        return new ArrayList<>(getAllCustomField4620Values(subdomain, apiKey)
                .values().stream().distinct().toList());
    }
}
