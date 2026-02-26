package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskAgentRepository;
import com.wfm.repository.DeskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    public BambooRefreshService(BambooHRClient bambooHRClient,
                                AgentRepository agentRepository,
                                DeskAgentRepository deskAgentRepository,
                                DeskRepository deskRepository,
                                AgentDayOffRepository agentDayOffRepository) {
        this.bambooHRClient = bambooHRClient;
        this.agentRepository = agentRepository;
        this.deskAgentRepository = deskAgentRepository;
        this.deskRepository = deskRepository;
        this.agentDayOffRepository = agentDayOffRepository;
    }

    @Transactional
    public void refreshDeskAgents(UUID deskId) {
        if (refreshInProgress.putIfAbsent(deskId, true) != null) {
            throw new IllegalStateException("A BambooHR refresh is already in progress for this desk.");
        }
        try {
            // TODO: implement refresh logic
            // 1. Fetch employees from BambooHR filtered by tenant and desk name
            // 2. Upsert agents into agent table
            // 3. Create/update DeskAgent records
            // 4. Refresh days off for the lookahead window
        } finally {
            refreshInProgress.remove(deskId);
        }
    }
}
