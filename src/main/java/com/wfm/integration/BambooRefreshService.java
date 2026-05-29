package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.exception.BambooHRRateLimitedException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.RefreshInProgressException;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.service.BambooSyncEventService;
import com.wfm.service.JobTitleConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Orchestrates desk-scoped BambooHR refresh: agent upsert, desk assignment, days off.
 */
@Service
public class BambooRefreshService {

    private static final Logger log = LoggerFactory.getLogger(BambooRefreshService.class);

    // D-03/D-05: Only "Part-Time" (exact BambooHR string) maps to PART_TIME; everything else is FULL_TIME.
    private static final String BAMBOO_PART_TIME = "Part-Time";

    private final BambooHRClient bambooHRClient;
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final SpecializationRepository specializationRepository;
    private final TransactionTemplate transactionTemplate;
    private final JobTitleConfigService jobTitleConfigService;
    private final BambooSyncEventService bambooSyncEventService;

    private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    @Value("${bamboohr.time-off.lookback-weeks:12}")
    private int lookbackWeeks;

    private static final String DEFAULT_SPECIALIZATION_NAME = "Shipping and Delivery";
    private static final String SECONDARY_SPECIALIZATION_NAME = "Payments and Safety";
    private static final String TERTIARY_SPECIALIZATION_NAME = "Order Quality and Usability";
    private static final String QUATERNARY_SPECIALIZATION_NAME = "Privacy and Legal & DSA";

    public BambooRefreshService(BambooHRClient bambooHRClient,
                                AgentRepository agentRepository,
                                DeskRepository deskRepository,
                                AgentDayOffRepository agentDayOffRepository,
                                SpecializationRepository specializationRepository,
                                TransactionTemplate transactionTemplate,
                                JobTitleConfigService jobTitleConfigService,
                                BambooSyncEventService bambooSyncEventService) {
        this.bambooHRClient = bambooHRClient;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.specializationRepository = specializationRepository;
        this.transactionTemplate = transactionTemplate;
        this.jobTitleConfigService = jobTitleConfigService;
        this.bambooSyncEventService = bambooSyncEventService;
    }

    /**
     * Maps a BambooHR employmentHistoryStatus string to our EmploymentType enum.
     * Only the exact string "Part-Time" maps to PART_TIME; all other values (including
     * null, blank, "Full-time", "Probation Period") map to FULL_TIME (D-03, D-05).
     */
    private static EmploymentType mapEmploymentType(String status) {
        return BAMBOO_PART_TIME.equals(status) ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME;
    }

    /**
     * Refresh desk agents from BambooHR.
     * API calls happen BEFORE the transaction boundary to avoid holding a DB connection
     * open during potentially slow external HTTP calls.
     */
    public void refreshDeskAgents(UUID deskId) {
        if (refreshInProgress.putIfAbsent(deskId, true) != null) {
            throw new RefreshInProgressException("A BambooHR refresh is already in progress for this desk.");
        }

        long tenantId = TenantContext.getTenantId();

        // Build sync event at entry — will be persisted in finally block regardless of outcome
        BambooSyncEvent syncEvent = new BambooSyncEvent();
        syncEvent.setTenantId(tenantId);
        syncEvent.setDeskId(deskId);
        syncEvent.setStartedAt(OffsetDateTime.now());
        syncEvent.setSuccess(false);

        try {
            // 1. Look up desk name (short read-only query, not in the main transaction)
            Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

            // 2. Fetch from BambooHR BEFORE the transactional boundary
            // Do NOT filter by department — desk names often differ from BambooHR departments
            // (e.g. desk "EN I" vs department "Vinted - UA"). Matching happens by bamboohrId
            // against agents already assigned to this desk.
            String deskName = desk.getName();
            List<BambooEmployee> employees = bambooHRClient.listEmployees(
                    String.valueOf(tenantId), deskName);

            LocalDate from = LocalDate.now().minusWeeks(lookbackWeeks);
            LocalDate to = LocalDate.now().plusWeeks(lookaheadWeeks);
            List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), from, to);

            // 3. Persist everything in a single transaction
            // Use TransactionTemplate instead of @Transactional on a self-invoked method,
            // which Spring proxies cannot intercept.
            transactionTemplate.executeWithoutResult(status ->
                    persistRefreshData(deskId, tenantId, desk, employees, timeOffs, from, to));

            syncEvent.setSuccess(true);
            syncEvent.setAgentsSynced(employees.size());
            syncEvent.setTimeOffPulled(timeOffs.size());

        } catch (BambooHRRateLimitedException e) {
            // T-05-02-01: use ex.getMessage() — safe message, no secrets
            syncEvent.setErrorMessage(e.getMessage());
            syncEvent.setRetryAfterSeconds(e.getRetryAfterSeconds());
            throw e;
        } catch (Exception e) {
            syncEvent.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            refreshInProgress.remove(deskId);
            syncEvent.setFinishedAt(OffsetDateTime.now());
            // REQUIRES_NEW ensures this write survives a rollback of any caller TX
            bambooSyncEventService.record(syncEvent);
        }
    }

    private void persistRefreshData(UUID deskId, long tenantId, Desk desk,
                                      List<BambooEmployee> employees,
                                      List<BambooTimeOff> timeOffs,
                                      LocalDate from, LocalDate to) {
        // 1. Ensure a default specialization exists for this desk
        Specialization defaultSpec = specializationRepository
                .findByTenantIdAndDeskIdAndName(tenantId, deskId, DEFAULT_SPECIALIZATION_NAME)
                .orElseGet(() -> {
                    Specialization spec = new Specialization();
                    spec.setTenantId(tenantId);
                    spec.setDeskId(deskId);
                    spec.setName(DEFAULT_SPECIALIZATION_NAME);
                    return specializationRepository.save(spec);
                });

        // 1b. Ensure a "second" specialization exists for this desk
        Specialization secondSpec = specializationRepository
                .findByTenantIdAndDeskIdAndName(tenantId, deskId, SECONDARY_SPECIALIZATION_NAME)
                .orElseGet(() -> {
                    Specialization spec = new Specialization();
                    spec.setTenantId(tenantId);
                    spec.setDeskId(deskId);
                    spec.setName(SECONDARY_SPECIALIZATION_NAME);
                    return specializationRepository.save(spec);
                });

        // 1c. Ensure third and fourth specializations exist for this desk
        specializationRepository
                .findByTenantIdAndDeskIdAndName(tenantId, deskId, TERTIARY_SPECIALIZATION_NAME)
                .orElseGet(() -> {
                    Specialization spec = new Specialization();
                    spec.setTenantId(tenantId);
                    spec.setDeskId(deskId);
                    spec.setName(TERTIARY_SPECIALIZATION_NAME);
                    return specializationRepository.save(spec);
                });

        specializationRepository
                .findByTenantIdAndDeskIdAndName(tenantId, deskId, QUATERNARY_SPECIALIZATION_NAME)
                .orElseGet(() -> {
                    Specialization spec = new Specialization();
                    spec.setTenantId(tenantId);
                    spec.setDeskId(deskId);
                    spec.setName(QUATERNARY_SPECIALIZATION_NAME);
                    return specializationRepository.save(spec);
                });

        // 2. Collect bamboohrIds from the response for soft-delete detection
        Set<String> bamboohrIdsInResponse = employees.stream()
                .map(BambooEmployee::id)
                .collect(Collectors.toSet());

        // 3. Update existing desk agents from BambooHR data.
        // Only agents already assigned to this desk are updated — new desk assignments
        // must come from spreadsheet upload or manual assignment, not from BambooHR refresh.
        Map<String, BambooEmployee> employeesByBambooId = employees.stream()
                .collect(Collectors.toMap(BambooEmployee::id, e -> e, (a, b) -> a));

        Set<UUID> refreshedAgentIds = new HashSet<>();
        List<Agent> currentDeskAgentsList = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        for (Agent agent : currentDeskAgentsList) {
            if (agent.getBamboohrId() == null) continue;
            BambooEmployee emp = employeesByBambooId.get(agent.getBamboohrId());
            if (emp == null) continue; // not in BambooHR response — handled by soft-delete below

            agent.setName(emp.displayName());
            agent.setEmail(emp.workEmail());
            agent.setDepartment(emp.department());
            agent.setJobTitle(emp.jobTitle());
            agent.setActive("Active".equalsIgnoreCase(emp.status()));
            agent.setLastRefreshedAt(OffsetDateTime.now());
            // Map BambooHR employmentHistoryStatus → EmploymentType enum (D-03, D-04)
            agent.setEmploymentType(mapEmploymentType(emp.employmentHistoryStatus()));

            // Preserve existing specializations and contracted hours — only set defaults if missing
            if (agent.getPrimarySpecialization() == null) {
                agent.setPrimarySpecialization(defaultSpec);
            }
            if (agent.getSecondarySpecializations().isEmpty()) {
                agent.getSecondarySpecializations().add(secondSpec);
            }
            if (agent.getContractedHoursPerDay() == null) {
                agent.setContractedHoursPerDay(desk.getDefaultContractedHoursPerDay());
            }
            agent = agentRepository.save(agent);
            refreshedAgentIds.add(agent.getId());
        }

        // 4. Soft-delete: mark agents assigned to this desk that are no longer in BambooHR response as inactive
        for (Agent agent : currentDeskAgentsList) {
            if (agent.getBamboohrId() != null && !bamboohrIdsInResponse.contains(agent.getBamboohrId())) {
                if (agent.isActive()) {
                    log.info("Soft-deleting agent {} (bamboohrId={}) — no longer in BambooHR response for desk {}",
                            agent.getName(), agent.getBamboohrId(), deskId);
                    agent.setActive(false);
                    agent.setLastRefreshedAt(OffsetDateTime.now());
                    agentRepository.save(agent);
                }
            }
        }

        // 5. Refresh days off for the lookahead window
        // Delete existing days off in the window for refreshed agents,
        // then re-insert from BambooHR (avoids unique constraint violations on repeated refresh)
        for (UUID agentId : refreshedAgentIds) {
            agentDayOffRepository.deleteByAgent_IdAndDateBetween(agentId, from, to);
        }
        agentDayOffRepository.flush();

        // Deduplicate by (agentId, date) — BambooHR can return overlapping time-off entries
        // for the same employee+date, which would violate the unique constraint.
        // When duplicates exist, prefer MANDATORY over PTO.
        Map<String, AgentDayOff> dedupedDaysOff = new LinkedHashMap<>();
        for (BambooTimeOff timeOff : timeOffs) {
            agentRepository.findByTenantIdAndBamboohrId(tenantId, timeOff.employeeId())
                    .ifPresent(agent -> {
                        if (!refreshedAgentIds.contains(agent.getId())) return;
                        String type = timeOff.type();
                        DayOffType dayOffType = "MANDATORY".equalsIgnoreCase(type)
                                || "holiday".equalsIgnoreCase(type)
                                ? DayOffType.MANDATORY : DayOffType.PTO;
                        DayOffStatus dayOffStatus = "approved".equalsIgnoreCase(timeOff.status())
                                ? DayOffStatus.APPROVED : DayOffStatus.REQUESTED;
                        String key = agent.getId() + "|" + timeOff.date();
                        AgentDayOff existing = dedupedDaysOff.get(key);
                        // Priority: MANDATORY > PTO; within same type, APPROVED > REQUESTED
                        if (existing == null
                                || (dayOffType == DayOffType.MANDATORY && existing.getType() != DayOffType.MANDATORY)
                                || (dayOffType == existing.getType() && dayOffStatus == DayOffStatus.APPROVED && existing.getStatus() != DayOffStatus.APPROVED)) {
                            AgentDayOff dayOff = new AgentDayOff();
                            dayOff.setTenantId(tenantId);
                            dayOff.setAgent(agent);
                            dayOff.setDate(timeOff.date());
                            dayOff.setType(dayOffType);
                            dayOff.setStatus(dayOffStatus);
                            dedupedDaysOff.put(key, dayOff);
                        }
                    });
        }
        for (AgentDayOff dayOff : dedupedDaysOff.values()) {
            agentDayOffRepository.save(dayOff);
        }

        // 6. Auto-populate JobTitleConfig rows for distinct non-blank job titles in this refresh.
        // Iterate the freshly-synced employees list (NOT the DB) per RESEARCH anti-pattern (D-09).
        // ensureExists is idempotent — safe to call repeatedly; does NOT modify nonSchedulable.
        employees.stream()
                .map(BambooEmployee::jobTitle)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .forEach(title -> jobTitleConfigService.ensureExists(tenantId, title));
    }
}
