package com.wfm.service;

import com.wfm.dto.AgentResponse;
import com.wfm.config.TenantContext;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.DepartmentSummary;
import com.wfm.dto.DepartmentTimeOffResponse;
import com.wfm.integration.BambooTimeOff;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.util.AgentNameSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final AgentEligibilityService agentEligibilityService;

    /** Cache keyed by "tenantId::department" (lowercased). */
    private final Map<String, List<BambooEmployeeResponse>> cache = new ConcurrentHashMap<>();

    /**
     * BambooHR id -> employment status for EVERY employee seen during upload cache population,
     * including inactive ones. {@link #cache} deliberately holds only Active employees, so a
     * lookup miss there cannot distinguish "no such id" from "exists but has left" — which made
     * the upload report a misleading "BambooHR ID not found" for former staff (UAT 2026-08-12).
     * Keyed "tenantId::bamboohrId".
     */
    private final Map<String, String> statusByTenantAndId = new ConcurrentHashMap<>();

    public ClientManagementService(BambooHRClient bambooHRClient,
                                   AppConfigurationService configurationService,
                                   AgentRepository agentRepository,
                                   DeskRepository deskRepository,
                                   AgentEligibilityService agentEligibilityService) {
        this.bambooHRClient = bambooHRClient;
        this.configurationService = configurationService;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.agentEligibilityService = agentEligibilityService;
    }

    /** The distinct excluded job titles are for an operator to read, not to page through. */
    private static final int MAX_REPORTED_HIDDEN_TITLES = 10;

    /**
     * A department search narrowed to people this system can actually schedule.
     *
     * <p>Two filters, and they are not the same kind of thing. Inactive employees are already gone
     * before this method sees the list — {@link #listEmployeesByDepartment} keeps only BambooHR
     * status "Active", and has since long before this method existed. The job-title allowlist is
     * applied HERE, against the tenant's configured patterns, so the search agrees with the two
     * places that already enforce it: the roster-seeded template and the upload parser. Without
     * that agreement an operator could assign someone from this screen and then watch the upload
     * silently refuse the same person.
     *
     * <p>What was removed is returned rather than discarded — see {@link EmployeeSearchResult}.
     */
    public EmployeeSearchResult listSchedulableEmployeesByDepartment(
            String tenantId, String department, boolean refresh) {
        long numericTenantId = TenantContext.getTenantId();
        List<BambooEmployeeResponse> active = listEmployeesByDepartment(tenantId, department, refresh);

        List<BambooEmployeeResponse> included = new ArrayList<>();
        LinkedHashSet<String> hiddenTitles = new LinkedHashSet<>();
        int hidden = 0;
        boolean allowlistActive = false;

        for (BambooEmployeeResponse employee : active) {
            if (agentEligibilityService.isIncludedByTitleAllowlist(numericTenantId, employee.jobTitle())) {
                included.add(employee);
            } else {
                // Reaching here at all means the allowlist has patterns: with none configured the
                // check returns true for everyone, including a blank title.
                allowlistActive = true;
                hidden++;
                hiddenTitles.add(employee.jobTitle() == null || employee.jobTitle().isBlank()
                        ? "(no job title)" : employee.jobTitle());
            }
        }

        return new EmployeeSearchResult(included, hidden,
                hiddenTitles.stream().limit(MAX_REPORTED_HIDDEN_TITLES).toList(),
                allowlistActive);
    }

    /**
     * @param employees        the people who passed both filters
     * @param hiddenByJobTitle how many active employees the allowlist removed
     * @param hiddenJobTitles  the distinct titles removed, capped
     * @param allowlistActive  whether the tenant has any patterns configured at all
     */
    public record EmployeeSearchResult(List<BambooEmployeeResponse> employees,
                                       int hiddenByJobTitle,
                                       List<String> hiddenJobTitles,
                                       boolean allowlistActive) {}

    /** Departments change rarely; a whole-tenant BambooHR read is expensive and rate-limited. */
    private final Map<String, List<DepartmentSummary>> departmentCache = new ConcurrentHashMap<>();

    /**
     * Every department holding at least one schedulable person — active, and with a job title on
     * the tenant's allowlist — so the search box can offer a list instead of asking an operator to
     * remember exact BambooHR spelling ("SafeRide - PR", "StubHub (PT) - UA").
     *
     * <p><b>One whole-tenant read, then cached.</b> This asks BambooHR for every employee rather
     * than per department, because the set of departments is not knowable in advance. That is the
     * same shape of call the upload-triggered sync makes, and it is rate-limited, so the result is
     * cached until an explicit refresh. A department with only Team Leads in it is deliberately
     * absent: offering it would return an empty search.
     */
    public List<DepartmentSummary> listDepartmentsWithSchedulableEmployees(String tenantId, boolean refresh) {
        long numericTenantId = TenantContext.getTenantId();
        String cacheKey = tenantId + "::departments";

        if (!refresh) {
            List<DepartmentSummary> cached = departmentCache.get(cacheKey);
            if (cached != null) {
                log.info("Returning {} cached departments for tenant={}", cached.size(), tenantId);
                return cached;
            }
        }

        // project = null asks for the whole roster; the department filter is applied here instead.
        List<BambooEmployee> employees = bambooHRClient.listEmployees(tenantId, null);

        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BambooEmployee employee : employees) {
            if (!"Active".equalsIgnoreCase(employee.status())) {
                continue;
            }
            String department = employee.department();
            if (department == null || department.isBlank()) {
                continue;
            }
            if (!agentEligibilityService.isIncludedByTitleAllowlist(numericTenantId, employee.jobTitle())) {
                continue;
            }
            counts.merge(department.trim(), 1, Integer::sum);
        }

        List<DepartmentSummary> result = counts.entrySet().stream()
                .map(e -> new DepartmentSummary(e.getKey(), e.getValue()))
                .toList();

        departmentCache.put(cacheKey, result);
        log.info("Scanned {} employees into {} departments with schedulable people (tenant={})",
                employees.size(), result.size(), tenantId);
        return result;
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

    /**
     * Get time-off entries for employees in a department, using the desk cache
     * to resolve employee IDs instead of making a separate BambooHR employee list call.
     * The cache must already be populated (via listEmployeesByDepartment) for the given department.
     */
    public List<DepartmentTimeOffResponse> listTimeOffByDepartment(String tenantId, String department,
                                                                    LocalDate from, LocalDate to) {
        // 1. Get employee IDs from the desk cache
        List<BambooEmployeeResponse> cached = listEmployeesByDepartment(tenantId, department, false);
        if (cached.isEmpty()) {
            log.warn("No cached employees for tenant={}, department={}. Call listEmployeesByDepartment first.", tenantId, department);
            return List.of();
        }

        Map<String, BambooEmployeeResponse> employeeById = cached.stream()
                .collect(Collectors.toMap(BambooEmployeeResponse::id, e -> e, (a, b) -> a));
        Set<String> departmentEmployeeIds = employeeById.keySet();

        log.info("Found {} cached employees in department '{}', fetching time-off from {} to {}",
                departmentEmployeeIds.size(), department, from, to);

        // 2. Fetch all time-off from BambooHR for the date range
        List<BambooTimeOff> allTimeOff = bambooHRClient.listTimeOff(tenantId, from, to);

        // 3. Filter to employees in the department
        return allTimeOff.stream()
                .filter(t -> departmentEmployeeIds.contains(t.employeeId()))
                .map(t -> {
                    BambooEmployeeResponse emp = employeeById.get(t.employeeId());
                    return new DepartmentTimeOffResponse(
                            t.employeeId(),
                            emp != null ? emp.displayName() : null,
                            t.date(),
                            t.type());
                })
                .toList();
    }

    public void clearCache() {
        cache.clear();
        statusByTenantAndId.clear();
    }

    /**
     * BambooHR employment status for an id seen during upload cache population, or null when the
     * id was never seen at all. Lets callers distinguish a former employee ("Inactive") from a
     * genuinely unknown id (null) — {@link #findCachedEmployee} returns null for both.
     */
    public String findEmployeeStatus(long tenantId, String bamboohrId) {
        if (bamboohrId == null || bamboohrId.isBlank()) {
            return null;
        }
        return statusByTenantAndId.get(tenantId + "::" + bamboohrId.trim());
    }

    /**
     * Ensure the BambooHR cache is populated with all active employees for the given tenant.
     * Called by the desk-assignment upload flow so that {@link #findCachedEmployee} can match
     * agents even when the user hasn't manually fetched a department first.
     */
    public void ensureCachePopulatedForUpload(long tenantId) {
        String cacheKey = tenantId + "::__all__";
        if (cache.containsKey(cacheKey)) return;

        log.info("Pre-populating BambooHR cache for desk-assignment upload (tenant={})", tenantId);
        List<BambooEmployee> employees = bambooHRClient.listEmployees(String.valueOf(tenantId), null);

        // Record every id and its status BEFORE the Active filter below, so the upload can tell
        // "this person has left" apart from "this id does not exist". Not size-capped: it is one
        // short string per employee, far smaller than the response cache it accompanies.
        for (BambooEmployee e : employees) {
            if (e.id() != null) {
                statusByTenantAndId.put(tenantId + "::" + e.id().trim(),
                        e.status() == null ? "Unknown" : e.status());
            }
        }

        List<BambooEmployeeResponse> result = employees.stream()
                .filter(e -> "Active".equalsIgnoreCase(e.status()))
                .map(e -> new BambooEmployeeResponse(
                        e.id(), e.displayName(), e.workEmail(),
                        e.department(), e.jobTitle(), e.status()))
                .toList();

        int maxCacheSize = getCacheMaxSize();
        if (result.size() <= maxCacheSize) {
            cache.put(cacheKey, result);
            log.info("Cached {} employees for upload (tenant={})", result.size(), tenantId);
        } else {
            log.warn("Skipping cache for {} employees (exceeds maxCacheSize={})", result.size(), maxCacheSize);
        }
    }

    /**
     * Search all cached employee lists for a match by bambooHR ID, email, or name.
     * Name matching allows a 1-character trailing difference (e.g. "John Smith" matches "John Smith1").
     * Returns the first match found, or null.
     */
    public BambooEmployeeResponse findCachedEmployee(String bamboohrId, String email, String name) {
        BambooEmployeeResponse fuzzyMatch = null;

        for (List<BambooEmployeeResponse> cached : cache.values()) {
            for (BambooEmployeeResponse emp : cached) {
                if (bamboohrId != null && !bamboohrId.isBlank()
                        && bamboohrId.trim().equals(emp.id())) {
                    return emp;
                }
                if (email != null && !email.isBlank()
                        && email.trim().equalsIgnoreCase(emp.workEmail())) {
                    return emp;
                }
                if (name != null && !name.isBlank()) {
                    String trimmedName = name.trim();
                    if (trimmedName.equalsIgnoreCase(emp.displayName())) {
                        return emp;
                    }
                    // Allow fuzzy match: names that differ by only 1 trailing character
                    if (fuzzyMatch == null && namesMatchWithTrailingChar(trimmedName, emp.displayName())) {
                        fuzzyMatch = emp;
                    }
                }
            }
        }
        return fuzzyMatch;
    }

    /**
     * Returns true if one name is the same as the other except for a single trailing character.
     * E.g. "John Smith" and "John Smith1" match, but "John Smith" and "John Smit" do not
     * (the shorter must be a prefix of the longer, and the longer must be exactly 1 char more).
     */
    static boolean namesMatchWithTrailingChar(String a, String b) {
        if (a == null || b == null) return false;
        String lowerA = a.toLowerCase();
        String lowerB = b.toLowerCase();
        if (Math.abs(lowerA.length() - lowerB.length()) != 1) return false;
        String shorter = lowerA.length() < lowerB.length() ? lowerA : lowerB;
        String longer = lowerA.length() < lowerB.length() ? lowerB : lowerA;
        return longer.startsWith(shorter);
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
            // Populate first/last via the shared splitter (D-07) — this is the fourth
            // agent write-site and must stay consistent with BambooRefreshService /
            // DeskAssignmentUploadService, else agents assigned here persist null names.
            AgentNameSplitter.Split split = AgentNameSplitter.split(emp.displayName());
            agent.setFirstName(split.firstName());
            agent.setLastName(split.lastName());
            agent.setEmail(emp.workEmail());
            agent.setDepartment(emp.department());
            agent.setJobTitle(emp.jobTitle());
            agent.setActive("Active".equalsIgnoreCase(emp.status()));
            agent.setLastRefreshedAt(OffsetDateTime.now());

            if (agent.getDeskId() != null && !agent.getDeskId().equals(deskId)) {
                throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to another desk");
            }

            if (!agentEligibilityService.isIncludedByTitleAllowlist(tenantId, agent.getJobTitle())) {
                throw new ConflictException("Agent '" + agent.getName()
                        + "' has a job title that is not schedulable: " + agent.getJobTitle());
            }

            agent.setDeskId(deskId);
            agent = agentRepository.save(agent);

            results.add(new AgentResponse(
                    agent.getId(), agent.getName(), agent.getFirstName(), agent.getLastName(), agent.getEmail(),
                    agent.getDepartment(), agent.getJobTitle(),
                    agent.isActive(), agent.getLastRefreshedAt()));
        }

        return results;
    }
}
