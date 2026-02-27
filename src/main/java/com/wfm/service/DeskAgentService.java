package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.model.DeskAgent;
import com.wfm.model.Specialization;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskAgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class DeskAgentService {

    private final DeskAgentRepository deskAgentRepository;
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final SpecializationRepository specializationRepository;

    public DeskAgentService(DeskAgentRepository deskAgentRepository,
                            AgentRepository agentRepository,
                            DeskRepository deskRepository,
                            SpecializationRepository specializationRepository) {
        this.deskAgentRepository = deskAgentRepository;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.specializationRepository = specializationRepository;
    }

    public List<DeskAgent> listDeskAgents(UUID deskId, String search, String cursor, int limit) {
        // TODO: implement with pagination and search
        return deskAgentRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId);
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
    public List<DeskAgent> assignAgents(UUID deskId, List<UUID> agentIds) {
        // TODO: validate agents exist, are active, not already assigned; create DeskAgent records
        return List.of();
    }

    @Transactional
    public void removeDeskAgent(UUID deskId, UUID agentId) {
        // TODO: check for non-accepted schedule, delete desk-agent and associated data
    }

    @Transactional
    public DeskAgent setSpecializations(UUID deskId, UUID agentId, UUID primaryId, List<UUID> secondaryIds) {
        // TODO: validate specializations belong to desk, update desk-agent
        return null;
    }

    @Transactional
    public DeskAgent setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
        // TODO: update desk-agent contracted hours
        return null;
    }
}
