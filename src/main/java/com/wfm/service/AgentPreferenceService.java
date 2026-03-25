package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.BulkPreferenceRequest;
import com.wfm.dto.BulkPreferenceResult;
import com.wfm.dto.PreferenceResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentPreference;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgentPreferenceService {

    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentRepository agentRepository;

    public AgentPreferenceService(AgentPreferenceRepository agentPreferenceRepository,
                                   AgentRepository agentRepository) {
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentRepository = agentRepository;
    }

    public List<PreferenceResponse> listPreferences(UUID deskId, UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentPreference> prefs;
        if (from != null && to != null) {
            List<AgentPreference> standing = agentPreferenceRepository
                    .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingTrue(tenantId, deskId, agentId);
            List<AgentPreference> weekly = agentPreferenceRepository
                    .findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
                            tenantId, deskId, agentId, LocalDate.parse(from), LocalDate.parse(to));
            prefs = new ArrayList<>(standing);
            prefs.addAll(weekly);
        } else {
            prefs = agentPreferenceRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);
        }

        return prefs.stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<PreferenceResponse> savePreferences(UUID deskId, UUID agentId,
                                                     List<PreferenceResponse> preferences) {
        long tenantId = TenantContext.getTenantId();

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        List<AgentPreference> saved = savePreferencesForAgent(tenantId, deskId, agent, preferences);
        return saved.stream().map(this::toResponse).toList();
    }

    @Transactional
    public BulkPreferenceResult saveBulkPreferences(UUID deskId, BulkPreferenceRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.preferences() == null || request.preferences().isEmpty()) {
            throw new IllegalArgumentException("preferences list must not be empty");
        }

        List<Agent> agents;
        if (request.agentIds() == null || request.agentIds().isEmpty()) {
            agents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
            if (agents.isEmpty()) {
                throw new EntityNotFoundException("No agents found on desk: " + deskId);
            }
        } else {
            agents = new ArrayList<>();
            for (UUID agentId : request.agentIds()) {
                Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Agent not found for desk: " + agentId));
                agents.add(agent);
            }
        }

        int totalSaved = 0;
        for (Agent agent : agents) {
            List<AgentPreference> saved = savePreferencesForAgent(
                    tenantId, deskId, agent, request.preferences());
            totalSaved += saved.size();
        }

        return new BulkPreferenceResult(
                agents.size(),
                request.preferences().size(),
                totalSaved
        );
    }

    private List<AgentPreference> savePreferencesForAgent(long tenantId, UUID deskId,
                                                           Agent agent,
                                                           List<PreferenceResponse> preferences) {
        UUID agentId = agent.getId();
        List<AgentPreference> saved = new ArrayList<>();

        for (PreferenceResponse pref : preferences) {
            AgentPreference entity;

            if (pref.isStanding()) {
                DayOfWeek dow = pref.dayOfWeek();
                if (dow == null) {
                    throw new IllegalArgumentException("dayOfWeek is required for standing preferences");
                }
                List<AgentPreference> existing = agentPreferenceRepository
                        .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingTrueAndDayOfWeek(
                                tenantId, deskId, agentId, dow);
                if (!existing.isEmpty()) {
                    entity = existing.get(0);
                } else {
                    entity = new AgentPreference();
                    entity.setTenantId(tenantId);
                    entity.setDeskId(deskId);
                    entity.setAgent(agent);
                    entity.setDayOfWeek(dow);
                    entity.setStanding(true);
                }
            } else {
                if (pref.date() == null) {
                    throw new IllegalArgumentException("date is required for weekly preferences");
                }
                entity = agentPreferenceRepository
                        .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingFalseAndDate(
                                tenantId, deskId, agentId, pref.date())
                        .orElseGet(() -> {
                            AgentPreference ap = new AgentPreference();
                            ap.setTenantId(tenantId);
                            ap.setDeskId(deskId);
                            ap.setAgent(agent);
                            ap.setDate(pref.date());
                            ap.setDayOfWeek(pref.date().getDayOfWeek());
                            ap.setStanding(false);
                            return ap;
                        });
            }

            entity.setPreferredStartTime(pref.preferredStartTime());
            entity.setPreferredBreakTime(pref.preferredBreakTime());
            saved.add(agentPreferenceRepository.save(entity));
        }

        return saved;
    }

    @Transactional
    public void deletePreference(UUID deskId, UUID agentId, UUID preferenceId) {
        long tenantId = TenantContext.getTenantId();

        AgentPreference pref = agentPreferenceRepository.findById(preferenceId)
                .filter(p -> p.getTenantId() == tenantId && p.getDeskId().equals(deskId))
                .orElseThrow(() -> new EntityNotFoundException("Preference", preferenceId));

        agentPreferenceRepository.delete(pref);
    }

    private PreferenceResponse toResponse(AgentPreference p) {
        return new PreferenceResponse(
                p.getId(), p.getDayOfWeek(), p.getDate(), p.isStanding(),
                p.getPreferredStartTime(), p.getPreferredBreakTime());
    }
}
