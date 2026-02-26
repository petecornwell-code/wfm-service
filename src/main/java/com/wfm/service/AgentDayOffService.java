package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AgentDayOff;
import com.wfm.repository.AgentDayOffRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentDayOffService {

    private final AgentDayOffRepository agentDayOffRepository;

    public AgentDayOffService(AgentDayOffRepository agentDayOffRepository) {
        this.agentDayOffRepository = agentDayOffRepository;
    }

    public List<AgentDayOff> listDaysOffForAgent(UUID agentId, String from, String to) {
        // TODO: implement with optional date range filter
        return agentDayOffRepository.findByTenantIdAndAgent_Id(TenantContext.getTenantId(), agentId);
    }

    public List<AgentDayOff> listAllDaysOff(String from, String to, String cursor, int limit) {
        // TODO: implement with pagination and date range filter
        return List.of();
    }
}
