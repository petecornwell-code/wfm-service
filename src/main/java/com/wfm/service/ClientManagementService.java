package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientManagementService {

    private static final Logger log = LoggerFactory.getLogger(ClientManagementService.class);

    private final BambooHRClient bambooHRClient;
    private final AppConfigurationService configurationService;

    /** Cache keyed by "tenantId::department" (lowercased). */
    private final Map<String, List<BambooEmployeeResponse>> cache = new ConcurrentHashMap<>();

    public ClientManagementService(BambooHRClient bambooHRClient,
                                   AppConfigurationService configurationService) {
        this.bambooHRClient = bambooHRClient;
        this.configurationService = configurationService;
    }

    public List<BambooEmployeeResponse> listEmployeesByDepartment(String tenantId, String department, boolean refresh) {
        String cacheKey = tenantId + "::" + department.toLowerCase();

        if (!refresh) {
            List<BambooEmployeeResponse> cached = cache.get(cacheKey);
            if (cached != null) {
                log.info("Returning {} cached employees for tenant={}, department={}", cached.size(), tenantId, department);
                return cached;
            }
        }

        List<BambooEmployee> employees = bambooHRClient.listEmployees(tenantId, department);

        List<BambooEmployeeResponse> result = employees.stream()
                .filter(e -> "Active".equalsIgnoreCase(e.status()))
                .filter(e -> department.equalsIgnoreCase(e.department()))
                .map(e -> new BambooEmployeeResponse(
                        e.id(),
                        e.displayName(),
                        e.workEmail(),
                        e.department(),
                        e.jobTitle(),
                        e.status()
                ))
                .toList();

        int maxCacheSize = getCacheMaxSize();
        if (result.size() <= maxCacheSize) {
            cache.put(cacheKey, result);
            log.info("Cached {} employees for tenant={}, department={} (maxCacheSize={})", result.size(), tenantId, department, maxCacheSize);
        } else {
            cache.remove(cacheKey);
            log.info("Skipping cache for {} employees (exceeds maxCacheSize={})", result.size(), maxCacheSize);
        }

        return result;
    }

    public void clearCache() {
        cache.clear();
    }

    private int getCacheMaxSize() {
        String value = configurationService.getConfigValue(AppConfigurationService.BAMBOOHR_CACHE_MAX_SIZE);
        if (value == null || value.isBlank()) {
            return 5000;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 5000;
        }
    }
}
