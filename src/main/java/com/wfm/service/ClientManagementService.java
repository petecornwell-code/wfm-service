package com.wfm.service;

import com.wfm.dto.AgentResponse;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ClientManagementService {

    private static final Logger log = LoggerFactory.getLogger(ClientManagementService.class);

    private final BambooHRClient bambooHRClient;
    private final AppConfigurationService configurationService;
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;

    /** Cache keyed by "tenantId::department" (lowercased). */
    private final Map<String, List<BambooEmployeeResponse>> cache = new ConcurrentHashMap<>();

    public ClientManagementService(BambooHRClient bambooHRClient,
                                   AppConfigurationService configurationService,
                                   AgentRepository agentRepository,
                                   DeskRepository deskRepository) {
        this.bambooHRClient = bambooHRClient;
        this.configurationService = configurationService;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
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

    /**
     * Assign specific BambooHR employees (by their bambooHR IDs) to an existing desk.
     * Creates Agent records if they don't already exist.
     */
    @Transactional
    public List<AgentResponse> assignEmployeesToDesk(long tenantId, UUID deskId, List<String> bambooEmployeeIds) {
        if (bambooEmployeeIds == null || bambooEmployeeIds.isEmpty()) {
            throw new IllegalArgumentException("bambooEmployeeIds is required and must not be empty");
        }

        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        // Look up the selected employees from the cache (all departments)
        Set<String> requestedIds = new HashSet<>(bambooEmployeeIds);
        Map<String, BambooEmployeeResponse> employeeMap = new HashMap<>();
        for (List<BambooEmployeeResponse> cached : cache.values()) {
            for (BambooEmployeeResponse emp : cached) {
                if (requestedIds.contains(emp.id())) {
                    employeeMap.put(emp.id(), emp);
                }
            }
        }

        // For any IDs not found in cache, try fetching individually
        for (String bambooId : bambooEmployeeIds) {
            if (!employeeMap.containsKey(bambooId)) {
                try {
                    BambooEmployee emp = bambooHRClient.getEmployee(bambooId);
                    if (emp != null) {
                        employeeMap.put(bambooId, new BambooEmployeeResponse(
                                emp.id(), emp.displayName(), emp.workEmail(),
                                emp.department(), emp.jobTitle(), emp.status()));
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch employee {} from BambooHR: {}", bambooId, e.getMessage());
                }
            }
        }

        if (employeeMap.isEmpty()) {
            throw new EntityNotFoundException("None of the specified employees were found");
        }

        List<AgentResponse> results = new ArrayList<>();
        for (String bambooId : bambooEmployeeIds) {
            BambooEmployeeResponse emp = employeeMap.get(bambooId);
            if (emp == null) {
                log.warn("Employee {} not found, skipping", bambooId);
                continue;
            }

            Agent agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, bambooId)
                    .orElseGet(() -> {
                        Agent a = new Agent();
                        a.setTenantId(tenantId);
                        a.setBamboohrId(bambooId);
                        return a;
                    });

            // Update fields from BambooHR data
            agent.setName(emp.displayName());
            agent.setEmail(emp.workEmail());
            agent.setDepartment(emp.department());
            agent.setJobTitle(emp.jobTitle());
            agent.setActive("Active".equalsIgnoreCase(emp.status()));
            agent.setLastRefreshedAt(OffsetDateTime.now());

            if (agent.getDeskId() != null && !agent.getDeskId().equals(deskId)) {
                throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to another desk");
            }

            agent.setDeskId(deskId);
            agent = agentRepository.save(agent);

            results.add(new AgentResponse(
                    agent.getId(), agent.getName(), agent.getEmail(),
                    agent.getDepartment(), agent.getJobTitle(),
                    agent.isActive(), agent.getLastRefreshedAt()));
        }

        return results;
    }
}
