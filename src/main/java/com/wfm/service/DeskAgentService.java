package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.DeskAgent;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskAgentRepository;
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
    private final SpecializationRepository specializationRepository;

    public DeskAgentService(DeskAgentRepository deskAgentRepository,
                            AgentRepository agentRepository,
                            SpecializationRepository specializationRepository) {
        this.deskAgentRepository = deskAgentRepository;
        this.agentRepository = agentRepository;
        this.specializationRepository = specializationRepository;
    }

    public List<DeskAgent> listDeskAgents(UUID deskId, String search, String cursor, int limit) {
        // TODO: implement with pagination and search
        return deskAgentRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId);
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
