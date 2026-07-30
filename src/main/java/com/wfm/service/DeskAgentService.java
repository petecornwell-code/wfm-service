package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.BigDecimals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeskAgentService {

    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final SpecializationRepository specializationRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;

    public DeskAgentService(AgentRepository agentRepository,
                            DeskRepository deskRepository,
                            SpecializationRepository specializationRepository,
                            AgentPreferenceRepository agentPreferenceRepository,
                            AgentExceptionRepository agentExceptionRepository,
                            AgentDayOffRepository agentDayOffRepository,
                            AgentDayHoursRepository agentDayHoursRepository) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.specializationRepository = specializationRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
    }

    @Transactional(readOnly = true)
    public List<DeskAgentResponse> listDeskAgentResponses(UUID deskId, String search, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        List<Agent> agents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // Bulk fetch pending PTO for all agents on this desk — single query (no N+1)
        List<AgentDayOff> pendingPtoRows = agentDayOffRepository
                .findByAgentDeskIdAndTypeAndStatusAndDateGreaterThanEqual(
                        deskId, DayOffType.PTO, DayOffStatus.REQUESTED, LocalDate.now());

        Map<UUID, List<LocalDate>> pendingByAgent = pendingPtoRows.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getAgent().getId(),
                        Collectors.mapping(AgentDayOff::getDate, Collectors.toList())));

        return agents.stream()
                .map(a -> toResponse(a, deskDefault,
                        pendingByAgent.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    private DeskAgentResponse toResponse(Agent a, BigDecimal deskDefault, List<LocalDate> pendingPtoDates) {
        Specialization ps = a.getPrimarySpecialization();
        BigDecimal effective = a.getContractedHoursPerDay() != null
                ? a.getContractedHoursPerDay() : deskDefault;

        return new DeskAgentResponse(
                a.getId(),
                a.getDeskId(),
                a.getBamboohrId(),
                a.getName(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getDepartment(), a.getJobTitle(),
                a.isActive(), a.getLastRefreshedAt(),
                ps != null ? new DeskAgentResponse.SpecSummary(ps.getId(), ps.getName()) : null,
                a.getSecondarySpecializations().stream()
                        .map(s -> new DeskAgentResponse.SpecSummary(s.getId(), s.getName()))
                        .toList(),
                a.getContractedHoursPerDay(),
                effective,
                a.getEmploymentType(),
                pendingPtoDates.size(),
                pendingPtoDates
        );
    }

    @Transactional
    public List<DeskAgentResponse> assignAgents(UUID deskId, List<UUID> agentIds) {
        long tenantId = TenantContext.getTenantId();

        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        if (agentIds == null || agentIds.isEmpty()) {
            throw new IllegalArgumentException("agentIds is required and must not be empty");
        }

        List<Agent> agents = agentRepository.findAllByIdInAndTenantId(agentIds, tenantId);
        if (agents.size() != agentIds.size()) {
            throw new EntityNotFoundException("One or more agents not found");
        }

        List<Agent> assigned = new ArrayList<>();
        for (Agent agent : agents) {
            if (!agent.isActive()) {
                throw new ConflictException("Agent '" + agent.getName() + "' is inactive");
            }
            if (agent.getDeskId() != null) {
                throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to a desk");
            }

            agent.setDeskId(deskId);
            assigned.add(agentRepository.save(agent));
        }

        BigDecimal deskDefault = desk.getDefaultContractedHoursPerDay();
        return assigned.stream().map(a -> toResponse(a, deskDefault, List.of())).toList();
    }

    @Transactional
    public void removeDeskAgent(UUID deskId, UUID agentId) {
        long tenantId = TenantContext.getTenantId();

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        // Clean up associated desk-scoped data for this agent
        agentPreferenceRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);
        agentExceptionRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);

        // Unassign: clear desk-specific fields
        agent.setDeskId(null);
        agent.setPrimarySpecialization(null);
        agent.getSecondarySpecializations().clear();
        agent.setContractedHoursPerDay(null);
        agentRepository.save(agent);
    }

    @Transactional
    public DeskAgentResponse setSpecializations(UUID deskId, UUID agentId,
                                                 UUID primaryId, List<UUID> secondaryIds) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        if (primaryId != null) {
            Specialization primary = specializationRepository
                    .findByIdAndTenantIdAndDeskId(primaryId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException("Specialization", primaryId));
            agent.setPrimarySpecialization(primary);
        } else {
            agent.setPrimarySpecialization(null);
        }

        if (secondaryIds != null) {
            List<Specialization> secondaries = secondaryIds.stream()
                    .map(id -> specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                            .orElseThrow(() -> new EntityNotFoundException("Specialization", id)))
                    .toList();
            agent.getSecondarySpecializations().clear();
            agent.getSecondarySpecializations().addAll(secondaries);
        } else {
            agent.getSecondarySpecializations().clear();
        }

        Agent saved = agentRepository.save(agent);
        return toResponse(saved, deskDefault, List.of());
    }

    @Transactional
    public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        BigDecimal normalized = BigDecimals.normalize(hours);
        if (normalized != null && normalized.signum() < 0) {
            throw new IllegalArgumentException("Contracted hours per day must not be negative");
        }
        agent.setContractedHoursPerDay(normalized);
        Agent saved = agentRepository.save(agent);

        // D-10: fan the new value out to all 7 agent_day_hours rows (replace, not append) so
        // the solver — which no longer reads the scalar — keeps honouring operator edits.
        // A null value is a legitimate "revert to desk default" operation: clear the rows and
        // leave zero rows so the solver falls back to the schedule default. Writing null into
        // agent_day_hours.hours (NOT NULL) would raise a DataIntegrityViolationException/500.
        agentDayHoursRepository.deleteByAgent_Id(agentId);
        agentDayHoursRepository.flush();
        if (normalized != null) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                AgentDayHours dayHours = new AgentDayHours();
                dayHours.setTenantId(saved.getTenantId());
                dayHours.setAgent(saved);
                dayHours.setDayOfWeek(dayOfWeek);
                dayHours.setHours(normalized);
                agentDayHoursRepository.save(dayHours);
            }
        }

        return toResponse(saved, deskDefault, List.of());
    }
}
