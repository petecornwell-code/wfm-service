package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientManagementService {

    private final BambooHRClient bambooHRClient;
    private final AppConfigurationService configurationService;

    public ClientManagementService(BambooHRClient bambooHRClient,
                                   AppConfigurationService configurationService) {
        this.bambooHRClient = bambooHRClient;
        this.configurationService = configurationService;
    }

    public List<BambooEmployeeResponse> listEmployeesByDepartment(String tenantId, String department) {
        // Use the configured server/apiKey if available (passed to the client via config)
        // The BambooHRClient implementations read from their own config,
        // but we filter the results by department here.
        List<BambooEmployee> employees = bambooHRClient.listEmployees(tenantId, department);

        return employees.stream()
                .filter(e -> department == null || department.isBlank()
                        || department.equalsIgnoreCase(e.department()))
                .map(e -> new BambooEmployeeResponse(
                        e.id(),
                        e.displayName(),
                        e.workEmail(),
                        e.department(),
                        e.jobTitle(),
                        e.status()
                ))
                .toList();
    }
}
