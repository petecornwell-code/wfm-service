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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
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
    private final AgentUsualShiftRepository agentUsualShiftRepository;
    private final UsualShiftResolutionService usualShiftResolutionService;
    private final ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    public DeskAgentService(AgentRepository agentRepository,
                            DeskRepository deskRepository,
                            SpecializationRepository specializationRepository,
                            AgentPreferenceRepository agentPreferenceRepository,
                            AgentExceptionRepository agentExceptionRepository,
                            AgentDayOffRepository agentDayOffRepository,
                            AgentDayHoursRepository agentDayHoursRepository,
                            ScheduleRepository scheduleRepository,
                            AgentUsualShiftRepository agentUsualShiftRepository,
                            UsualShiftResolutionService usualShiftResolutionService,
                            ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.specializationRepository = specializationRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.scheduleRepository = scheduleRepository;
        this.agentUsualShiftRepository = agentUsualShiftRepository;
        this.usualShiftResolutionService = usualShiftResolutionService;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
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

    /** Single bulk per-desk fetch of usual-shift rows, grouped by agent then weekday — no N+1. */
    private Map<UUID, Map<DayOfWeek, AgentUsualShift>> loadUsualShiftsByAgent(long tenantId, UUID deskId) {
        List<AgentUsualShift> rows = agentUsualShiftRepository.findByTenantIdAndDeskId(tenantId, deskId);
        return rows.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getAgent().getId(),
                        Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u)));
    }

    /**
     * Bulk band fetch for the D-05 hours advisory — ONE query for every referenced template's
     * bands, keyed by template id, called BEFORE the {@code DAY_ORDER} loop rather than from
     * inside it. {@code usualShiftRows} is every stored usual-shift row in scope for this call
     * (one agent's seven, or a whole desk's), so this never becomes an N+1 across agents/weekdays.
     */
    private Map<UUID, List<ShiftTemplateBreakBand>> loadBandsForUsualShifts(
            long tenantId, Collection<AgentUsualShift> usualShiftRows) {
        List<UUID> templateIds = usualShiftRows.stream()
                .map(u -> u.getShiftTemplate().getId())
                .distinct()
                .toList();
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdInOrderByOffsetMinutesAsc(tenantId, templateIds)
                .stream()
                .collect(Collectors.groupingBy(b -> b.getShiftTemplate().getId()));
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
        Map<UUID, Map<DayOfWeek, AgentUsualShift>> usualShiftsByAgent = loadUsualShiftsByAgent(tenantId, deskId);
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId,
                usualShiftsByAgent.values().stream().flatMap(m -> m.values().stream()).toList());

        return agents.stream()
                .map(a -> toResponse(a, scheduleDefault,
                        dayHoursByAgent.getOrDefault(a.getId(), Map.of()),
                        usualShiftsByAgent.getOrDefault(a.getId(), Map.of()),
                        bandsByTemplateId,
                        pendingByAgent.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    private DeskAgentResponse toResponse(Agent a, BigDecimal scheduleDefault,
                                          Map<DayOfWeek, AgentDayHours> dayRows,
                                          Map<DayOfWeek, AgentUsualShift> usualRows,
                                          Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                          List<LocalDate> pendingPtoDates) {
        Specialization ps = a.getPrimarySpecialization();

        // Single combined loop: the D-16 usual-shift discriminator's NOT_WORKED arm needs the
        // SAME agent_day_hours row (dayRow) this loop already resolves for dayHours — reusing it
        // here is what keeps this a read-layer correlation with no new repository call, per D-04's
        // orthogonality staying a write-layer constraint only.
        Map<DayOfWeek, DeskAgentResponse.DayHoursEntry> dayHours = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, DeskAgentResponse.UsualShiftEntry> usualShift = new EnumMap<>(DayOfWeek.class);
        BigDecimal maxEffective = null;
        LocalDate today = LocalDate.now();
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            AgentDayHours dayRow = dayRows.get(day);
            DeskAgentResponse.DayHoursEntry hoursEntry = dayRow != null
                    ? new DeskAgentResponse.DayHoursEntry(true, dayRow.getHours(), dayRow.getDayOffType(), dayRow.getHours())
                    : new DeskAgentResponse.DayHoursEntry(false, null, null, scheduleDefault);
            dayHours.put(day, hoursEntry);
            if (maxEffective == null || hoursEntry.effectiveHours().compareTo(maxEffective) > 0) {
                maxEffective = hoursEntry.effectiveHours();
            }

            usualShift.put(day, usualShiftEntry(day, usualRows.get(day), dayRow, hoursEntry.effectiveHours(),
                    bandsByTemplateId, today));
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
                dayHours,
                usualShift
        );
    }

    /**
     * D-16 three-state discriminator, with D-05's hours advisory and P-07's RETIRED-before-
     * NOT_WORKED precedence. {@code dayRow} is the SAME {@code agent_day_hours} row (possibly
     * null) this method's caller already resolved for {@code dayHours} — no second repository
     * call, keeping D-04's orthogonality a write-layer constraint only.
     */
    private DeskAgentResponse.UsualShiftEntry usualShiftEntry(DayOfWeek day, AgentUsualShift usualRow,
                                                                AgentDayHours dayRow, BigDecimal effectiveHours,
                                                                Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                                                LocalDate today) {
        if (usualRow == null) {
            return new DeskAgentResponse.UsualShiftEntry(DeskAgentResponse.UsualShiftStatus.NOT_SET, null, null, null);
        }

        String storedName = usualRow.getShiftTemplate().getName();
        boolean isLive = usualShiftResolutionService.resolve(usualRow, today).isPresent();

        // P-07: RETIRED is evaluated FIRST. The stored reference itself being dead is the more
        // fundamental fact — a template that no longer exists as a live era cannot be honoured on
        // any weekday, whereas "not worked" is a per-weekday condition that could flip back with
        // one hours edit.
        if (!isLive) {
            String advisory = hoursAdvisory(usualRow.getShiftTemplate(), day, bandsByTemplateId, effectiveHours);
            return new DeskAgentResponse.UsualShiftEntry(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE,
                    storedName, DeskAgentResponse.UsualShiftReason.RETIRED, advisory);
        }

        // P-08: "does not work this weekday" = a day-hours row exists for it AND is either
        // MANDATORY/PTO or its hours has signum() == 0. Uses signum(), never a direct BigDecimal
        // equality check against an unscaled zero constant -- the stored value is NUMERIC(5,2)
        // and reads back scaled, so that equality is false for a real stored 0.00. A weekday with
        // NO row is NOT "not worked": it resolves to the schedule default (a positive number), so
        // the agent works it and the state stays LIVE.
        boolean notWorked = dayRow != null
                && (dayRow.getDayOffType() == DayOffType.MANDATORY || dayRow.getDayOffType() == DayOffType.PTO
                    || dayRow.getHours().signum() == 0);
        if (notWorked) {
            // No contracted hours to mismatch against on a weekday the agent doesn't work.
            return new DeskAgentResponse.UsualShiftEntry(DeskAgentResponse.UsualShiftStatus.STORED_INACTIVE,
                    storedName, DeskAgentResponse.UsualShiftReason.NOT_WORKED, null);
        }

        String advisory = hoursAdvisory(usualRow.getShiftTemplate(), day, bandsByTemplateId, effectiveHours);
        return new DeskAgentResponse.UsualShiftEntry(DeskAgentResponse.UsualShiftStatus.LIVE, storedName, null, advisory);
    }

    /**
     * D-05: null when at least one of the STORED template's bands' net duration (via {@link
     * ShiftTemplate#getNetHours(int)} -- never a local duration recomputation from the template's
     * own start/end times, per Phase 14 D-08 discipline) equals the agent's effective contracted
     * hours for the weekday,
     * exact-equality via {@link BigDecimals#normalize} + {@code compareTo}, no tolerance --
     * matching {@code ShiftLibraryValidationService.findHoursAdvisories}'s own any-band quantifier
     * and SHLB-06 message register exactly. A zero-band template is measured on {@code
     * getNetHours(0)} alone (P-02 "zero bands = no break").
     *
     * <p>Deliberately computed on the READ path only: {@code UsualShiftService.setUsualShift}
     * never gains an hours check (D-05 stays advisory, never blocking, mirroring Phase 14 D-06),
     * so this is also what surfaces a mismatch introduced LATER by a contracted-hours edit under
     * an unchanged usual shift -- a write-time-only advisory could not do that.
     */
    private String hoursAdvisory(ShiftTemplate template, DayOfWeek day,
                                  Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                  BigDecimal effectiveHours) {
        List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
        List<BigDecimal> bandNetHours = bands.isEmpty()
                ? List.of(template.getNetHours(0))
                : bands.stream().map(b -> template.getNetHours(b.getDurationMinutes())).toList();

        BigDecimal normalizedEffective = BigDecimals.normalize(effectiveHours);
        boolean anyBandMatches = bandNetHours.stream()
                .anyMatch(net -> BigDecimals.normalize(net).compareTo(normalizedEffective) == 0);
        if (anyBandMatches) {
            return null;
        }

        BigDecimal reportedNetHours = BigDecimals.normalize(bandNetHours.get(0));
        return template.getName() + "'s net duration (" + reportedNetHours.toPlainString()
                + "h) doesn't match this agent's contracted hours on "
                + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " (" + normalizedEffective.toPlainString()
                + "h). The usual shift is still saved.";
    }

    /**
     * P-06: the read half of the write-then-read composition {@code DeskAgentController} performs
     * for the usual-shift endpoint -- mirrors the existing {@code refreshFromBamboo} pattern
     * (write via one service, read via {@code listDeskAgentResponses}), scoped to a single agent.
     */
    @Transactional(readOnly = true)
    public DeskAgentResponse getDeskAgentResponse(UUID deskId, UUID agentId) {
        long tenantId = TenantContext.getTenantId();
        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        Map<DayOfWeek, AgentUsualShift> usualRows = agentUsualShiftRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u));
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId, usualRows.values());

        return toResponse(agent, scheduleDefault, dayRows, usualRows, bandsByTemplateId, List.of());
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
        Map<UUID, Map<DayOfWeek, AgentUsualShift>> usualShiftsByAgent = loadUsualShiftsByAgent(tenantId, deskId);
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId,
                usualShiftsByAgent.values().stream().flatMap(m -> m.values().stream()).toList());
        return assigned.stream()
                .map(a -> toResponse(a, scheduleDefault,
                        dayHoursByAgent.getOrDefault(a.getId(), Map.of()),
                        usualShiftsByAgent.getOrDefault(a.getId(), Map.of()),
                        bandsByTemplateId, List.of()))
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
        Map<DayOfWeek, AgentUsualShift> usualRows = agentUsualShiftRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u));
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId, usualRows.values());
        return toResponse(saved, scheduleDefault, dayRows, usualRows, bandsByTemplateId, List.of());
    }

    @Transactional
    public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        BigDecimal normalized = BigDecimals.normalize(hours);
        // WR-01 alignment: mirror setDayHours' inclusive 0-24 reject-not-clamp bound so every
        // write path into agent_day_hours obeys one rule, instead of only rejecting negatives.
        if (normalized != null
                && (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("24")) > 0)) {
            throw new IllegalArgumentException("Contracted hours per day must be between 0 and 24");
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
        agentDayHoursRepository.flush();

        // Read after the delete-and-recreate block and the flush above so the returned response
        // reflects the seven rows just written, not the pre-write state.
        Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        Map<DayOfWeek, AgentUsualShift> usualRows = agentUsualShiftRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u));
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId, usualRows.values());
        return toResponse(saved, scheduleDefault, dayRows, usualRows, bandsByTemplateId, List.of());
    }

    /**
     * D-05: edit exactly one weekday's agent_day_hours row, provably leaving the other six
     * untouched. Deliberately does NOT reuse setContractedHours' delete-all-seven-and-recreate
     * fan-out — that multi-row rewrite is exactly the behaviour that caused audit finding I-3.
     * Leaves Agent.contractedHoursPerDay untouched (P-06): a single-weekday edit has no single
     * scalar value to write.
     */
    @Transactional
    public DeskAgentResponse setDayHours(UUID deskId, UUID agentId, DayOfWeek day,
                                          BigDecimal hours, DayOffType dayOffType, boolean clearRow) {
        long tenantId = TenantContext.getTenantId();

        BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

        // Mandatory access-control step (T-13-05): resolve the agent within tenant+desk scope
        // BEFORE any AgentDayHoursRepository call — findByAgent_IdAndDayOfWeek accepts a raw
        // agent id and would otherwise be an IDOR.
        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        if (clearRow) {
            // Absent row is a no-op, not an error (D-04 — absent is not the same as 0).
            agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agentId, day)
                    .ifPresent(agentDayHoursRepository::delete);
        } else if (dayOffType == DayOffType.MANDATORY || dayOffType == DayOffType.PTO) {
            // Matches the upload parser's own encoding (DeskAssignmentUploadService:673-680) --
            // any hours supplied alongside a label is ignored, not merged.
            upsertDayHoursRow(agent, day, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), dayOffType);
        } else if (hours != null) {
            BigDecimal normalized = BigDecimals.normalize(hours);
            if (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("24")) > 0) {
                // Reject, do not clamp (P-04) -- unlike the bulk upload parser's silent clamp.
                throw new IllegalArgumentException("Hours must be between 0 and 24");
            }
            upsertDayHoursRow(agent, day, normalized, null);
        } else {
            // No hours, no label, no clear -- a 400, not a silent no-op.
            throw new IllegalArgumentException("Must provide hours, a day-off type, or clearRow");
        }

        agentDayHoursRepository.flush();

        // Re-read after the write so the returned payload reflects the row just written.
        Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        Map<DayOfWeek, AgentUsualShift> usualRows = agentUsualShiftRepository
                .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
                .collect(Collectors.toMap(AgentUsualShift::getDayOfWeek, u -> u));
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsForUsualShifts(tenantId, usualRows.values());
        return toResponse(agent, scheduleDefault, dayRows, usualRows, bandsByTemplateId, List.of());
    }

    /**
     * Reuses the existing row for (agent, day) if present, otherwise constructs a fresh one --
     * never a second row for a weekday that already has one (the unique constraint on
     * (agent_id, day_of_week) is the backstop, T-13-08).
     */
    private void upsertDayHoursRow(Agent agent, DayOfWeek day, BigDecimal hours, DayOffType dayOffType) {
        AgentDayHours row = agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agent.getId(), day)
                .orElseGet(AgentDayHours::new);
        row.setTenantId(agent.getTenantId());
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setHours(hours);
        row.setDayOffType(dayOffType);
        agentDayHoursRepository.save(row);
    }
}
