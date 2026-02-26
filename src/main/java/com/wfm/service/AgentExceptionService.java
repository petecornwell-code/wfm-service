package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AgentException;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentExceptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AgentExceptionService {

    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentDayOffRepository agentDayOffRepository;

    public AgentExceptionService(AgentExceptionRepository agentExceptionRepository,
                                 AgentDayOffRepository agentDayOffRepository) {
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayOffRepository = agentDayOffRepository;
    }

    public List<AgentException> listExceptions(UUID deskId, UUID agentId, String from, String to) {
        // TODO: implement with optional date range filter
        return agentExceptionRepository.findByTenantIdAndDeskIdAndAgent_Id(
                TenantContext.getTenantId(), deskId, agentId);
    }

    @Transactional
    public List<AgentException> saveExceptions(UUID deskId, UUID agentId, List<AgentException> exceptions) {
        // TODO: validate no conflict with days off, upsert
        return List.of();
    }

    @Transactional
    public void deleteException(UUID deskId, UUID agentId, LocalDate date) {
        // TODO: implement
    }
}
