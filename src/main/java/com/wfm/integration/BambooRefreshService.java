package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.RefreshInProgressException;
import com.wfm.model.*;
import com.wfm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final BambooHRClient bambooHRClient;
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final SpecializationRepository specializationRepository;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    private static final String DEFAULT_SPECIALIZATION_NAME = "IT Support";
    private static final String SECONDARY_SPECIALIZATION_NAME = "IT Support (Spanish)";

    public BambooRefreshService(@Qualifier("mockBambooHRClient") BambooHRClient bambooHRClient,
                                AgentRepository agentRepository,
                                DeskRepository deskRepository,
                                AgentDayOffRepository agentDayOffRepository,
                                SpecializationRepository specializationRepository,
                                TransactionTemplate transactionTemplate) {
        this.bambooHRClient = bambooHRClient;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.specializationRepository = specializationRepository;
        this.transactionTemplate = transactionTemplate;
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
        try {
            long tenantId = TenantContext.getTenantId();

            // 1. Look up desk name (short read-only query, not in the main transaction)
            Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

            // 2. Fetch from BambooHR BEFORE the transactional boundary
            String deskName = desk.getName();
            List<BambooEmployee> employees = bambooHRClient.listEmployees(
                    String.valueOf(tenantId), deskName).stream()
                    .filter(e -> deskName.equalsIgnoreCase(e.department()))
                    .toList();

            LocalDate from = LocalDate.now();
            LocalDate to = from.plusWeeks(lookaheadWeeks);
            List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), from, to);

            // 3. Persist everything in a single transaction
            // Use TransactionTemplate instead of @Transactional on a self-invoked method,
            // which Spring proxies cannot intercept.
            transactionTemplate.executeWithoutResult(status ->
                    persistRefreshData(deskId, tenantId, desk, employees, timeOffs, from, to));
        } finally {
            refreshInProgress.remove(deskId);
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
            agent.setActive("Active".equals(emp.status()));
            agent.setLastRefreshedAt(OffsetDateTime.now());

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

        for (BambooTimeOff timeOff : timeOffs) {
            agentRepository.findByTenantIdAndBamboohrId(tenantId, timeOff.employeeId())
                    .ifPresent(agent -> {
                        // Only insert days off for agents included in this desk's refresh (spec §9.4)
                        if (!refreshedAgentIds.contains(agent.getId())) return;
                        AgentDayOff dayOff = new AgentDayOff();
                        dayOff.setTenantId(tenantId);
                        dayOff.setAgent(agent);
                        dayOff.setDate(timeOff.date());
                        String type = timeOff.type();
                        dayOff.setType("MANDATORY".equalsIgnoreCase(type)
                                || "holiday".equalsIgnoreCase(type)
                                ? DayOffType.MANDATORY : DayOffType.PTO);
                        agentDayOffRepository.save(dayOff);
                    });
        }
    }
}
