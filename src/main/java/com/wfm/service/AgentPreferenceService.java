package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AgentPreference;
import com.wfm.repository.AgentPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AgentPreferenceService {

    private final AgentPreferenceRepository agentPreferenceRepository;

    public AgentPreferenceService(AgentPreferenceRepository agentPreferenceRepository) {
        this.agentPreferenceRepository = agentPreferenceRepository;
    }

    public List<AgentPreference> listPreferences(UUID deskId, UUID agentId, String from, String to) {
        // TODO: return standing + weekly preferences for agent, optionally filtered by date range
        return agentPreferenceRepository.findByTenantIdAndDeskIdAndAgent_Id(
                TenantContext.getTenantId(), deskId, agentId);
    }

    @Transactional
    public List<AgentPreference> savePreferences(UUID deskId, UUID agentId, List<AgentPreference> preferences) {
        // TODO: handle standing replacement, derive dayOfWeek from date for weekly
        return List.of();
    }

    @Transactional
    public void deletePreference(UUID deskId, UUID agentId, UUID preferenceId) {
        // TODO: implement
    }
}
