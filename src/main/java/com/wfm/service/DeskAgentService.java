package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.model.DeskAgent;
import com.wfm.model.Specialization;
import com.wfm.repository.*;
import com.wfm.util.BigDecimals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeskAgentService {

    private final DeskAgentRepository deskAgentRepository;
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final SpecializationRepository specializationRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;

    public DeskAgentService(DeskAgentRepository deskAgentRepository,
                            AgentRepository agentRepository,
                            DeskRepository deskRepository,
                            SpecializationRepository specializationRepository,
                            AgentPreferenceRepository agentPreferenceRepository,
                            AgentExceptionRepository agentExceptionRepository) {
        this.deskAgentRepository = deskAgentRepository;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.specializationRepository = specializationRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
    }

    @Transactional(readOnly = true)
    public List<DeskAgentResponse> listDeskAgentResponses(UUID deskId, String search, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        List<DeskAgent> deskAgents = deskAgentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        return deskAgents.stream().map(da -> toResponse(da, deskDefault)).toList();
    }

    private DeskAgentResponse toResponse(DeskAgent da, BigDecimal deskDefault) {
        Agent a = da.getAgent();
        Specialization ps = da.getPrimarySpecialization();
        BigDecimal effective = da.getContractedHoursPerDay() != null
                ? da.getContractedHoursPerDay() : deskDefault;

        return new DeskAgentResponse(
                da.getId(),
                da.getDeskId(),
                new DeskAgentResponse.AgentSummary(
                        a.getId(), a.getName(), a.getEmail(),
                        a.getDepartment(), a.getJobTitle(),
                        a.isActive(), a.getLastRefreshedAt()),
                ps != null ? new DeskAgentResponse.SpecSummary(ps.getId(), ps.getName()) : null,
                da.getSecondarySpecializations().stream()
                        .map(s -> new DeskAgentResponse.SpecSummary(s.getId(), s.getName()))
                        .toList(),
                da.getContractedHoursPerDay(),
                effective
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

        List<DeskAgent> created = new ArrayList<>();
        for (Agent agent : agents) {
            if (!agent.isActive()) {
                throw new ConflictException("Agent '" + agent.getName() + "' is inactive");
            }
            if (deskAgentRepository.existsByTenantIdAndAgent_Id(tenantId, agent.getId())) {
                throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to a desk");
            }

            DeskAgent da = new DeskAgent();
            da.setTenantId(tenantId);
            da.setDeskId(deskId);
            da.setAgent(agent);
            created.add(deskAgentRepository.save(da));
        }

        BigDecimal deskDefault = desk.getDefaultContractedHoursPerDay();
        return created.stream().map(da -> toResponse(da, deskDefault)).toList();
    }

    @Transactional
    public void removeDeskAgent(UUID deskId, UUID agentId) {
        long tenantId = TenantContext.getTenantId();

        DeskAgent da = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("DeskAgent not found for agent " + agentId));

        // Clean up associated desk-scoped data for this agent
        agentPreferenceRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);
        agentExceptionRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);

        deskAgentRepository.delete(da);
    }

    @Transactional
    public DeskAgentResponse setSpecializations(UUID deskId, UUID agentId,
                                                 UUID primaryId, List<UUID> secondaryIds) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        DeskAgent da = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("DeskAgent not found for agent " + agentId));

        if (primaryId != null) {
            Specialization primary = specializationRepository
                    .findByIdAndTenantIdAndDeskId(primaryId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException("Specialization", primaryId));
            da.setPrimarySpecialization(primary);
        } else {
            da.setPrimarySpecialization(null);
        }

        if (secondaryIds != null) {
            List<Specialization> secondaries = secondaryIds.stream()
                    .map(id -> specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                            .orElseThrow(() -> new EntityNotFoundException("Specialization", id)))
                    .toList();
            da.getSecondarySpecializations().clear();
            da.getSecondarySpecializations().addAll(secondaries);
        } else {
            da.getSecondarySpecializations().clear();
        }

        DeskAgent saved = deskAgentRepository.save(da);
        return toResponse(saved, deskDefault);
    }

    @Transactional
    public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(new BigDecimal("8.00"));

        DeskAgent da = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("DeskAgent not found for agent " + agentId));

        da.setContractedHoursPerDay(BigDecimals.normalize(hours));
        DeskAgent saved = deskAgentRepository.save(da);
        return toResponse(saved, deskDefault);
    }
}
