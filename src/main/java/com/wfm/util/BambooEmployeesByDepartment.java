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
import java.util.List;

/**
 * Console application that fetches all employees in the "Vinted UA" department
 * from the BambooHR API.
 */
public final class BambooEmployeesByDepartment {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String TARGET_DEPARTMENT = "Vinted UA";

    private BambooEmployeesByDepartment() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: BambooEmployeesByDepartment <subdomain> <apiKey>");
            System.exit(1);
        }
        String subdomain = args[0];
        String apiKey = args[1];

        System.out.println("Fetching employees in department: " + TARGET_DEPARTMENT);
        System.out.println("Subdomain: " + subdomain);
        System.out.println();

        List<Employee> employees = getEmployeesByDepartment(subdomain, apiKey, TARGET_DEPARTMENT);

        System.out.printf("%-8s  %-30s  %-20s  %s%n", "ID", "NAME", "DEPARTMENT", "JOB TITLE");
        System.out.printf("%-8s  %-30s  %-20s  %s%n", "--------", "------------------------------",
                "--------------------", "--------------------");
        for (Employee emp : employees) {
            System.out.printf("%-8s  %-30s  %-20s  %s%n", emp.id, emp.displayName, emp.department, emp.jobTitle);
        }

        System.out.println();
        System.out.println("Total employees: " + employees.size());
    }

    /**
     * Fetches all employees belonging to the given department.
     *
     * @param subdomain  BambooHR company subdomain
     * @param apiKey     BambooHR API key
     * @param department department name to filter by
     * @return list of matching employees
     */
    public static List<Employee> getEmployeesByDepartment(String subdomain, String apiKey, String department) {
        String url = "https://api.bamboohr.com/api/gateway.php/"
                + subdomain + "/v1/reports/custom?format=JSON";

        String requestBody = """
                {
                  "title": "Employees by Department",
                  "fields": ["id", "displayName", "department", "jobTitle"]
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
            JsonNode employeesNode = root.path("employees");

            List<Employee> result = new ArrayList<>();
            for (JsonNode emp : employeesNode) {
                String empDepartment = emp.path("department").asText("");
                if (department.equalsIgnoreCase(empDepartment)) {
                    result.add(new Employee(
                            emp.path("id").asText(),
                            emp.path("displayName").asText(),
                            empDepartment,
                            emp.path("jobTitle").asText("")
                    ));
                }
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling BambooHR API", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch employees from BambooHR", e);
        }
    }

    public record Employee(String id, String displayName, String department, String jobTitle) {}
}
