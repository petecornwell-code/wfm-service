package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.model.*;
import com.wfm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates desk-scoped BambooHR refresh: agent upsert, desk assignment, days off.
 */
@Service
public class BambooRefreshService {

    private final BambooHRClient bambooHRClient;
    private final AgentRepository agentRepository;
    private final DeskAgentRepository deskAgentRepository;
    private final DeskRepository deskRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final SpecializationRepository specializationRepository;

    private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    private static final String DEFAULT_SPECIALIZATION_NAME = "Basic";

    public BambooRefreshService(BambooHRClient bambooHRClient,
                                AgentRepository agentRepository,
                                DeskAgentRepository deskAgentRepository,
                                DeskRepository deskRepository,
                                AgentDayOffRepository agentDayOffRepository,
                                SpecializationRepository specializationRepository) {
        this.bambooHRClient = bambooHRClient;
        this.agentRepository = agentRepository;
        this.deskAgentRepository = deskAgentRepository;
        this.deskRepository = deskRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.specializationRepository = specializationRepository;
    }

    @Transactional
    public void refreshDeskAgents(UUID deskId) {
        if (refreshInProgress.putIfAbsent(deskId, true) != null) {
            throw new IllegalStateException("A BambooHR refresh is already in progress for this desk.");
        }
        try {
            long tenantId = TenantContext.getTenantId();

            // 1. Look up desk to get its name (used as BambooHR project filter)
            Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Desk not found: " + deskId));

            // 2. Ensure a default "Basic" specialization exists for this desk
            Specialization defaultSpec = specializationRepository
                    .findByTenantIdAndDeskIdAndName(tenantId, deskId, DEFAULT_SPECIALIZATION_NAME)
                    .orElseGet(() -> {
                        Specialization spec = new Specialization();
                        spec.setTenantId(tenantId);
                        spec.setDeskId(deskId);
                        spec.setName(DEFAULT_SPECIALIZATION_NAME);
                        return specializationRepository.save(spec);
                    });

            // 3. Fetch employees from BambooHR filtered by tenant and desk name
            List<BambooEmployee> employees = bambooHRClient.listEmployees(
                    String.valueOf(tenantId), desk.getName());

            // 4. Upsert agents and create DeskAgent records
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

                if (!deskAgentRepository.existsByTenantIdAndAgent_Id(tenantId, agent.getId())) {
                    DeskAgent deskAgent = new DeskAgent();
                    deskAgent.setTenantId(tenantId);
                    deskAgent.setDeskId(deskId);
                    deskAgent.setAgent(agent);
                    deskAgent.setPrimarySpecialization(defaultSpec);
                    deskAgent.setSecondarySpecializations(List.of(defaultSpec));
                    deskAgent.setContractedHoursPerDay(desk.getDefaultContractedHoursPerDay());
                    deskAgentRepository.save(deskAgent);
                }
            }

            // 5. Refresh days off for the lookahead window
            LocalDate from = LocalDate.now();
            LocalDate to = from.plusWeeks(lookaheadWeeks);
            List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), from, to);
            for (BambooTimeOff timeOff : timeOffs) {
                agentRepository.findByTenantIdAndBamboohrId(tenantId, timeOff.employeeId())
                        .ifPresent(agent -> {
                            AgentDayOff dayOff = new AgentDayOff();
                            dayOff.setTenantId(tenantId);
                            dayOff.setAgent(agent);
                            dayOff.setDate(timeOff.date());
                            dayOff.setType("MANDATORY".equals(timeOff.type())
                                    ? DayOffType.MANDATORY : DayOffType.PTO);
                            agentDayOffRepository.save(dayOff);
                        });
            }
        } finally {
            refreshInProgress.remove(deskId);
        }
    }
}
