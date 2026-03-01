package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.PreferenceResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentPreference;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.DeskAgentRepository;
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
    private final DeskAgentRepository deskAgentRepository;

    public AgentPreferenceService(AgentPreferenceRepository agentPreferenceRepository,
                                   DeskAgentRepository deskAgentRepository) {
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.deskAgentRepository = deskAgentRepository;
    }

    public List<PreferenceResponse> listPreferences(UUID deskId, UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentPreference> prefs;
        if (from != null && to != null) {
            // Return standing preferences plus weekly preferences in the date range
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

        Agent agent = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("DeskAgent not found for agent " + agentId))
                .getAgent();

        List<AgentPreference> saved = new ArrayList<>();
        for (PreferenceResponse pref : preferences) {
            AgentPreference entity;

            if (pref.isStanding()) {
                // Standing: replace any existing standing preference for same dayOfWeek
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
                // Weekly: date-specific preference
                if (pref.date() == null) {
                    throw new IllegalArgumentException("date is required for weekly preferences");
                }
                entity = new AgentPreference();
                entity.setTenantId(tenantId);
                entity.setDeskId(deskId);
                entity.setAgent(agent);
                entity.setDate(pref.date());
                entity.setDayOfWeek(pref.date().getDayOfWeek());
                entity.setStanding(false);
            }

            entity.setPreferredStartTime(pref.preferredStartTime());
            entity.setPreferredBreakTime(pref.preferredBreakTime());
            saved.add(agentPreferenceRepository.save(entity));
        }

        return saved.stream().map(this::toResponse).toList();
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
