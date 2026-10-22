package com.wfm.service;

import com.wfm.config.TenantContext;
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
    private final ShiftStartSnapService shiftStartSnapService;

    public AgentPreferenceService(AgentPreferenceRepository agentPreferenceRepository,
                                   AgentRepository agentRepository,
                                   ShiftStartSnapService shiftStartSnapService) {
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentRepository = agentRepository;
        this.shiftStartSnapService = shiftStartSnapService;
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

        List<AgentPreference> saved = new ArrayList<>();
        // Shift-scheduled desks only: a start no shift begins at is moved to the nearest shift start,
        // and the response says so (adjustedFrom) so the page can show it.
        ShiftStartSnapService.Snapper snapper = shiftStartSnapService.forDesk(tenantId, deskId);
        java.util.Map<AgentPreference, java.time.LocalTime> adjustedFrom = new java.util.IdentityHashMap<>();
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

            java.time.LocalTime start = pref.preferredStartTime();
            var snap = snapper.snap(start, pref.isStanding() ? null : pref.date(),
                    pref.isStanding() ? pref.dayOfWeek() : (pref.date() != null ? pref.date().getDayOfWeek() : null));
            if (snap.isPresent()) {
                start = snap.get().snapped();
            }
            entity.setPreferredStartTime(start);
            entity.setPreferredBreakTime(pref.preferredBreakTime());
            AgentPreference persisted = agentPreferenceRepository.save(entity);
            saved.add(persisted);
            snap.ifPresent(s -> adjustedFrom.put(persisted, s.original()));
        }

        return saved.stream()
                .map(p -> {
                    PreferenceResponse r = toResponse(p);
                    java.time.LocalTime from = adjustedFrom.get(p);
                    return from == null ? r : new PreferenceResponse(r.id(), r.dayOfWeek(), r.date(),
                            r.isStanding(), r.preferredStartTime(), r.preferredBreakTime(), from);
                })
                .toList();
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
