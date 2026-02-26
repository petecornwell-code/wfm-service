package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Agent;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskAgentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final DeskAgentRepository deskAgentRepository;

    public AgentService(AgentRepository agentRepository, DeskAgentRepository deskAgentRepository) {
        this.agentRepository = agentRepository;
        this.deskAgentRepository = deskAgentRepository;
    }

    public List<Agent> listAgents(String search, boolean unassigned, String cursor, int limit) {
        // TODO: implement with pagination, search filter, unassigned filter
        return List.of();
    }

    public Agent getAgent(UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, TenantContext.getTenantId()).orElse(null);
    }
}
