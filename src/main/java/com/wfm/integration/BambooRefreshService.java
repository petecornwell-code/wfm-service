package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.RefreshInProgressException;
import com.wfm.model.*;
import com.wfm.repository.*;
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

    private final BambooHRClient bambooHRClient;
    private final AgentRepository agentRepository;
    private final DeskAgentRepository deskAgentRepository;
    private final DeskRepository deskRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final SpecializationRepository specializationRepository;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    private static final String DEFAULT_SPECIALIZATION_NAME = "Basic";
    private static final String SECONDARY_SPECIALIZATION_NAME = "second";

    public BambooRefreshService(BambooHRClient bambooHRClient,
                                AgentRepository agentRepository,
                                DeskAgentRepository deskAgentRepository,
                                DeskRepository deskRepository,
                                AgentDayOffRepository agentDayOffRepository,
                                SpecializationRepository specializationRepository,
                                TransactionTemplate transactionTemplate) {
        this.bambooHRClient = bambooHRClient;
        this.agentRepository = agentRepository;
        this.deskAgentRepository = deskAgentRepository;
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
            List<BambooEmployee> employees = bambooHRClient.listEmployees(
                    String.valueOf(tenantId), desk.getName());

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
        // 1. Ensure a default "Basic" specialization exists for this desk
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

        // 3. Upsert agents and create DeskAgent records
        Set<UUID> refreshedAgentIds = new HashSet<>();
        for (BambooEmployee emp : employees) {
            Agent agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, emp.id())
                    .orElseGet(() -> {
                        Agent a = new Agent();
                        a.setTenantId(tenantId);
                        a.setBamboohrId(emp.id());
                        return a;
                    });
            agent.setName(emp.displayName());
            agent.setEmail(emp.workEmail());
            agent.setDepartment(emp.department());
            agent.setJobTitle(emp.jobTitle());
            agent.setActive("Active".equals(emp.status()));
            agent.setLastRefreshedAt(OffsetDateTime.now());
            agent = agentRepository.save(agent);
            refreshedAgentIds.add(agent.getId());

            // Cross-desk conflict check: if agent is already assigned to a different desk, warn and skip
            Optional<DeskAgent> existingAssignment = deskAgentRepository.findByTenantIdAndAgent_Id(tenantId, agent.getId());
            if (existingAssignment.isPresent()) {
                DeskAgent existing = existingAssignment.get();
                if (!existing.getDeskId().equals(deskId)) {
                    log.warn("Agent {} (bamboohrId={}) is already assigned to desk {}; skipping assignment to desk {}",
                            agent.getName(), emp.id(), existing.getDeskId(), deskId);
                    continue;
                }
                // Already assigned to this desk — set secondary to just "second"
                existing.setSecondarySpecializations(new ArrayList<>(List.of(secondSpec)));
                deskAgentRepository.save(existing);
            } else {
                // New assignment to this desk
                DeskAgent deskAgent = new DeskAgent();
                deskAgent.setTenantId(tenantId);
                deskAgent.setDeskId(deskId);
                deskAgent.setAgent(agent);
                deskAgent.setPrimarySpecialization(defaultSpec);
                deskAgent.setSecondarySpecializations(new ArrayList<>(List.of(secondSpec)));
                deskAgent.setContractedHoursPerDay(desk.getDefaultContractedHoursPerDay());
                deskAgentRepository.save(deskAgent);
            }
        }

        // 4. Soft-delete: mark agents assigned to this desk that are no longer in BambooHR response as inactive
        List<DeskAgent> currentDeskAgents = deskAgentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        for (DeskAgent da : currentDeskAgents) {
            Agent agent = da.getAgent();
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
