package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ExceptionResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentException;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.DeskAgentRepository;
import com.wfm.util.BigDecimals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentExceptionService {

    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final DeskAgentRepository deskAgentRepository;

    public AgentExceptionService(AgentExceptionRepository agentExceptionRepository,
                                 AgentDayOffRepository agentDayOffRepository,
                                 DeskAgentRepository deskAgentRepository) {
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.deskAgentRepository = deskAgentRepository;
    }

    public List<ExceptionResponse> listExceptions(UUID deskId, UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentException> exceptions;
        if (from != null && to != null) {
            exceptions = agentExceptionRepository.findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
                    tenantId, deskId, agentId, LocalDate.parse(from), LocalDate.parse(to));
        } else {
            exceptions = agentExceptionRepository.findByTenantIdAndDeskIdAndAgent_Id(
                    tenantId, deskId, agentId);
        }

        return exceptions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<ExceptionResponse> saveExceptions(UUID deskId, UUID agentId,
                                                    List<ExceptionResponse> exceptions) {
        long tenantId = TenantContext.getTenantId();

        Agent agent = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("DeskAgent not found for agent " + agentId))
                .getAgent();

        List<AgentException> saved = new ArrayList<>();
        for (ExceptionResponse ex : exceptions) {
            if (ex.date() == null) {
                throw new IllegalArgumentException("date is required for each exception");
            }
            if (ex.contractedHoursOverride() == null) {
                throw new IllegalArgumentException("contractedHoursOverride is required");
            }
            if (ex.reason() == null || ex.reason().isBlank()) {
                throw new IllegalArgumentException("reason is required");
            }

            // Check for conflict with days off
            if (!agentDayOffRepository.findByTenantIdAndAgent_IdAndDateBetweenOrderByDateAsc(
                    tenantId, agentId, ex.date(), ex.date()).isEmpty()) {
                throw new ConflictException("Agent has a day off on " + ex.date()
                        + "; cannot create exception");
            }

            // Upsert: update if exists for same date, create if not
            Optional<AgentException> existing = agentExceptionRepository
                    .findByTenantIdAndDeskIdAndAgent_IdAndDate(tenantId, deskId, agentId, ex.date());

            AgentException entity;
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = new AgentException();
                entity.setTenantId(tenantId);
                entity.setDeskId(deskId);
                entity.setAgent(agent);
                entity.setDate(ex.date());
            }

            entity.setContractedHoursOverride(BigDecimals.normalize(ex.contractedHoursOverride()));
            entity.setReason(ex.reason());
            saved.add(agentExceptionRepository.save(entity));
        }

        return saved.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteException(UUID deskId, UUID agentId, LocalDate date) {
        long tenantId = TenantContext.getTenantId();

        AgentException exception = agentExceptionRepository
                .findByTenantIdAndDeskIdAndAgent_IdAndDate(tenantId, deskId, agentId, date)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Exception not found for agent " + agentId + " on " + date));

        agentExceptionRepository.delete(exception);
    }

    private ExceptionResponse toResponse(AgentException e) {
        return new ExceptionResponse(e.getId(), e.getDate(), e.getContractedHoursOverride(), e.getReason());
    }
}
