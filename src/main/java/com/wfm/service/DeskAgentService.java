package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.BigDecimals;
import com.wfm.util.EnrichedColumnLayout;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeskAgentService {

    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final SpecializationRepository specializationRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;
    private final ScheduleRepository scheduleRepository;

    public DeskAgentService(AgentRepository agentRepository,
                            DeskRepository deskRepository,
                            SpecializationRepository specializationRepository,
                            AgentPreferenceRepository agentPreferenceRepository,
                            AgentExceptionRepository agentExceptionRepository,
                            AgentDayOffRepository agentDayOffRepository,
                            AgentDayHoursRepository agentDayHoursRepository,
                            ScheduleRepository scheduleRepository) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.specializationRepository = specializationRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * D-06 "not set" fallback: the desk's most-recently-created persisted Schedule's own
     * default, any status (P-01). Never Desk.defaultContractedHoursPerDay — that fallback is
     * the bug this phase closes. Falls back to the literal 8.00 (matching Schedule's own field
     * default) when the desk has zero persisted schedules.
     */
    private BigDecimal resolveScheduleDefault(long tenantId, UUID deskId) {
        List<Schedule> schedules = scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(
                tenantId, deskId, PageRequest.of(0, 1));
        if (schedules.isEmpty()) {
            return new BigDecimal("8.00");
        }
        BigDecimal value = schedules.get(0).getDefaultContractedHoursPerDay();
        return value != null ? value : new BigDecimal("8.00");
    }

    /** Single bulk per-desk fetch, grouped by agent then weekday — no N+1 (mirrors pendingByAgent). */
    private Map<UUID, Map<DayOfWeek, AgentDayHours>> loadDayHoursByAgent(long tenantId, UUID deskId) {
        List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
        return rows.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getAgent().getId(),
                        Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h)));
    }

    @Transactional(readOnly = true)
    public List<DeskAgentResponse> listDeskAgentResponses(UUID deskId, String search, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        List<Agent> agents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // Bulk fetch pending PTO for all agents on this desk — single query (no N+1)
        List<AgentDayOff> pendingPtoRows = agentDayOffRepository
                .findByAgentDeskIdAndTypeAndStatusAndDateGreaterThanEqual(
                        deskId, DayOffType.PTO, DayOffStatus.REQUESTED, LocalDate.now());

        Map<UUID, List<LocalDate>> pendingByAgent = pendingPtoRows.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getAgent().getId(),
                        Collectors.mapping(AgentDayOff::getDate, Collectors.toList())));

        Map<UUID, Map<DayOfWeek, AgentDayHours>> dayHoursByAgent = loadDayHoursByAgent(tenantId, deskId);

        return agents.stream()
                .map(a -> toResponse(a, scheduleDefault,
                        dayHoursByAgent.getOrDefault(a.getId(), Map.of()),
                        pendingByAgent.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    private DeskAgentResponse toResponse(Agent a, BigDecimal scheduleDefault,
                                          Map<DayOfWeek, AgentDayHours> dayRows,
                                          List<LocalDate> pendingPtoDates) {
        Specialization ps = a.getPrimarySpecialization();

        Map<DayOfWeek, DeskAgentResponse.DayHoursEntry> dayHours = new EnumMap<>(DayOfWeek.class);
        BigDecimal maxEffective = null;
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            AgentDayHours row = dayRows.get(day);
            DeskAgentResponse.DayHoursEntry entry = row != null
                    ? new DeskAgentResponse.DayHoursEntry(true, row.getHours(), row.getDayOffType(), row.getHours())
                    : new DeskAgentResponse.DayHoursEntry(false, null, null, scheduleDefault);
            dayHours.put(day, entry);
            if (maxEffective == null || entry.effectiveHours().compareTo(maxEffective) > 0) {
                maxEffective = entry.effectiveHours();
            }
        }

        return new DeskAgentResponse(
                a.getId(),
                a.getDeskId(),
                a.getBamboohrId(),
                a.getName(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getDepartment(), a.getJobTitle(),
                a.isActive(), a.getLastRefreshedAt(),
                ps != null ? new DeskAgentResponse.SpecSummary(ps.getId(), ps.getName()) : null,
                a.getSecondarySpecializations().stream()
                        .map(s -> new DeskAgentResponse.SpecSummary(s.getId(), s.getName()))
                        .toList(),
                a.getContractedHoursPerDay(),
                BigDecimals.normalize(maxEffective),
                a.getEmploymentType(),
                pendingPtoDates.size(),
                pendingPtoDates,
                dayHours
        );
    }

    @Transactional
    public List<DeskAgentResponse> assignAgents(UUID deskId, List<UUID> agentIds) {
        long tenantId = TenantContext.getTenantId();

        // Existence/authorization guard only — not a hours fallback (D-06 no longer reads this).
        deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        if (agentIds == null || agentIds.isEmpty()) {
            throw new IllegalArgumentException("agentIds is required and must not be empty");
        }

        List<Agent> agents = agentRepository.findAllByIdInAndTenantId(agentIds, tenantId);
        if (agents.size() != agentIds.size()) {
            throw new EntityNotFoundException("One or more agents not found");
        }

        List<Agent> assigned = new ArrayList<>();
        for (Agent agent : agents) {
            if (!agent.isActive()) {
                throw new ConflictException("Agent '" + agent.getName() + "' is inactive");
            }
            if (agent.getDeskId() != null) {
                throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to a desk");
            }

            agent.setDeskId(deskId);
            assigned.add(agentRepository.save(agent));
        }

        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);
        Map<UUID, Map<DayOfWeek, AgentDayHours>> dayHoursByAgent = loadDayHoursByAgent(tenantId, deskId);
        return assigned.stream()
                .map(a -> toResponse(a, scheduleDefault,
                        dayHoursByAgent.getOrDefault(a.getId(), Map.of()), List.of()))
                .toList();
    }

    @Transactional
    public void removeDeskAgent(UUID deskId, UUID agentId) {
        long tenantId = TenantContext.getTenantId();

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        // Clean up associated desk-scoped data for this agent
        agentPreferenceRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);
        agentExceptionRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);

        // Unassign: clear desk-specific fields
        agent.setDeskId(null);
        agent.setPrimarySpecialization(null);
        agent.getSecondarySpecializations().clear();
        agent.setContractedHoursPerDay(null);
        agentRepository.save(agent);
    }

    @Transactional
    public DeskAgentResponse setSpecializations(UUID deskId, UUID agentId,
                                                 UUID primaryId, List<UUID> secondaryIds) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        if (primaryId != null) {
            Specialization primary = specializationRepository
                    .findByIdAndTenantIdAndDeskId(primaryId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException("Specialization", primaryId));
            agent.setPrimarySpecialization(primary);
        } else {
            agent.setPrimarySpecialization(null);
        }

        if (secondaryIds != null) {
            List<Specialization> secondaries = secondaryIds.stream()
                    .map(id -> specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                            .orElseThrow(() -> new EntityNotFoundException("Specialization", id)))
                    .toList();
            agent.getSecondarySpecializations().clear();
            agent.getSecondarySpecializations().addAll(secondaries);
        } else {
            agent.getSecondarySpecializations().clear();
        }

        Agent saved = agentRepository.save(agent);
        Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        return toResponse(saved, scheduleDefault, dayRows, List.of());
    }

    @Transactional
    public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        BigDecimal normalized = BigDecimals.normalize(hours);
        if (normalized != null && normalized.signum() < 0) {
            throw new IllegalArgumentException("Contracted hours per day must not be negative");
        }
        agent.setContractedHoursPerDay(normalized);
        Agent saved = agentRepository.save(agent);

        // D-10: fan the new value out to all 7 agent_day_hours rows (replace, not append) so
        // the solver — which no longer reads the scalar — keeps honouring operator edits.
        // A null value is a legitimate "revert to desk default" operation: clear the rows and
        // leave zero rows so the solver falls back to the schedule default. Writing null into
        // agent_day_hours.hours (NOT NULL) would raise a DataIntegrityViolationException/500.
        agentDayHoursRepository.deleteByAgent_Id(agentId);
        agentDayHoursRepository.flush();
        if (normalized != null) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                AgentDayHours dayHours = new AgentDayHours();
                dayHours.setTenantId(saved.getTenantId());
                dayHours.setAgent(saved);
                dayHours.setDayOfWeek(dayOfWeek);
                dayHours.setHours(normalized);
                agentDayHoursRepository.save(dayHours);
            }
        }

        // Read after the flush/writes above so the returned response reflects the seven rows
        // just written, not the pre-write state.
        Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        return toResponse(saved, scheduleDefault, dayRows, List.of());
    }
}
