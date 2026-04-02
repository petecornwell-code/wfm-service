package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.PaginatedResponse;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleSummary;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.CursorPagination;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository scheduleRepository;
    private final AcceptedScheduleDateRepository acceptedScheduleDateRepository;
    private final DeskRepository deskRepository;
    private final InMemoryScheduleStore inMemoryStore;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentAssignmentRepository agentAssignmentRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;
    private final ScheduleOutputService scheduleOutputService;
    private final EntityManager entityManager;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           AcceptedScheduleDateRepository acceptedScheduleDateRepository,
                           DeskRepository deskRepository,
                           InMemoryScheduleStore inMemoryStore,
                           TimeslotRepository timeslotRepository,
                           StaffingRequirementRepository staffingRequirementRepository,
                           AgentAssignmentRepository agentAssignmentRepository,
                           AgentPreferenceRepository agentPreferenceRepository,
                           AgentDayOffRepository agentDayOffRepository,
                           ConstraintWeightsRepository constraintWeightsRepository,
                           ScheduleOutputService scheduleOutputService,
                           EntityManager entityManager) {
        this.scheduleRepository = scheduleRepository;
        this.acceptedScheduleDateRepository = acceptedScheduleDateRepository;
        this.deskRepository = deskRepository;
        this.inMemoryStore = inMemoryStore;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentAssignmentRepository = agentAssignmentRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
        this.scheduleOutputService = scheduleOutputService;
        this.entityManager = entityManager;
    }

    // --- Task 23: listSchedules ---

    public PaginatedResponse<ScheduleSummary> listSchedules(UUID deskId, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clamped = CursorPagination.clampLimit(limit);

        String deskName = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getName).orElse(null);

        // Load accepted schedules from DB (ordered by createdAt desc)
        List<Schedule> dbSchedules = scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(
                tenantId, deskId, PageRequest.of(0, CursorPagination.MAX_LIMIT + 1));

        // Merge in-memory schedule (if exists for this tenant) at the front
        List<Schedule> merged = new ArrayList<>();
        inMemoryStore.getByDeskId(deskId).ifPresent(s -> {
            if (s.getTenantId() == tenantId) {
                merged.add(s);
            }
        });
        for (Schedule db : dbSchedules) {
            if (merged.stream().noneMatch(s -> s.getId().equals(db.getId()))) {
                merged.add(db);
            }
        }

        final String dn = deskName;
        List<ScheduleSummary> summaries = new ArrayList<>(merged.stream().map(s -> toSummary(s, dn)).toList());

        // Apply cursor: skip past the cursor position
        if (cursor != null && !cursor.isBlank()) {
            Map<String, String> cursorValues = CursorPagination.decode(cursor);
            String cursorId = cursorValues.get("id");
            if (cursorId != null) {
                int idx = -1;
                for (int i = 0; i < summaries.size(); i++) {
                    if (summaries.get(i).id().toString().equals(cursorId)) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0 && idx + 1 < summaries.size()) {
                    summaries = new ArrayList<>(summaries.subList(idx + 1, summaries.size()));
                } else {
                    summaries = new ArrayList<>();
                }
            }
        }

        return CursorPagination.buildPage(summaries, clamped,
                s -> Map.of("id", s.id().toString()));
    }

    // --- Task 24: getScheduleDetail ---

    @Transactional(readOnly = true)
    public ScheduleDetailResponse getScheduleDetail(UUID deskId, UUID scheduleId, String dateFilter) {
        long tenantId = TenantContext.getTenantId();

        // Try in-memory first (with tenant + desk validation), then DB
        Schedule schedule = inMemoryStore.get(scheduleId)
                .filter(s -> s.getTenantId() == tenantId && s.getDeskId().equals(deskId))
                .orElse(null);
        boolean fromDb = false;

        if (schedule == null) {
            schedule = scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException("Schedule", scheduleId));
            fromDb = true;
        }

        // For accepted (DB) schedules, load snapshot data
        if (fromDb) {
            loadSnapshotData(schedule, tenantId, deskId);
        }

        // Build the detail response
        ScheduleDetailResponse response = buildDetailResponse(schedule);
        deskRepository.findByIdAndTenantId(deskId, tenantId)
                .ifPresent(desk -> response.setDeskName(desk.getName()));

        // Compute output views
        response.setStaffingSummary(scheduleOutputService.buildStaffingSummary(schedule));
        response.setAgentSchedule(scheduleOutputService.buildAgentSchedule(schedule));
        response.setPreferenceReport(scheduleOutputService.buildPreferenceReport(schedule));
        response.setConstraintViolations(scheduleOutputService.buildConstraintViolations(schedule));

        // Derive violatedHardConstraints from constraint violations (deduplicated)
        Set<String> violatedHardSet = new LinkedHashSet<>();
        if (response.getConstraintViolations() != null) {
            for (var cv : response.getConstraintViolations()) {
                if ("HARD".equals(cv.level())) {
                    violatedHardSet.add(cv.constraintName());
                }
            }
        }
        response.setViolatedHardConstraints(new ArrayList<>(violatedHardSet));

        // If date filter provided, filter output views to that date
        // Constraint violations are always returned in full regardless of date filter (spec §8.4)
        if (dateFilter != null && !dateFilter.isBlank()) {
            LocalDate filterDate;
            try {
                filterDate = LocalDate.parse(dateFilter);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date format: " + dateFilter);
            }
            response.setStaffingSummary(
                    response.getStaffingSummary().stream()
                            .filter(e -> e.date() != null && e.date().equals(filterDate)).toList());
            response.setAgentSchedule(
                    response.getAgentSchedule().stream()
                            .filter(e -> e.date().equals(filterDate)).toList());
            if (response.getPreferenceReport() != null) {
                var filteredEntries = response.getPreferenceReport().entries().stream()
                        .filter(e -> e.date().equals(filterDate)).toList();
                response.setPreferenceReport(new ScheduleDetailResponse.PreferenceReport(
                        filteredEntries, response.getPreferenceReport().summary()));
            }
        }

        return response;
    }

    // --- Task 26: acceptSchedule ---

    @Transactional
    public Schedule acceptSchedule(UUID deskId, UUID scheduleId, int expectedVersion) {
        long tenantId = TenantContext.getTenantId();

        Schedule schedule = inMemoryStore.get(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

        // Validate tenant and desk ownership
        if (schedule.getTenantId() != tenantId || !schedule.getDeskId().equals(deskId)) {
            throw new EntityNotFoundException("Schedule not found for desk: " + deskId);
        }

        if (schedule.getStatus() != ScheduleStatus.COMPLETED
                && schedule.getStatus() != ScheduleStatus.STOPPED) {
            throw new ConflictException("Schedule must be COMPLETED or STOPPED to accept (status: "
                    + schedule.getStatus() + ")");
        }

        // Validate version for optimistic locking
        if (schedule.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + schedule.getVersion());
        }

        // Compute covered dates
        List<LocalDate> coveredDates = new ArrayList<>();
        LocalDate d = schedule.getPeriodStartDate();
        while (!d.isAfter(schedule.getPeriodEndDate())) {
            coveredDates.add(d);
            d = d.plusDays(1);
        }

        // Supersede any currently-ACCEPTED entries for these (tenant, desk, date) combos
        // Old schedules and their data are preserved; only the date-level status changes
        acceptedScheduleDateRepository.updateStatusByTenantIdAndDeskIdAndDateIn(
                tenantId, deskId, coveredDates,
                AcceptedScheduleDateStatus.ACCEPTED, AcceptedScheduleDateStatus.SUPERSEDED);

        // Save the Schedule record — flush supersede updates first, then persist
        entityManager.flush();
        schedule.setStatus(ScheduleStatus.ACCEPTED);
        schedule.setId(null);
        entityManager.persist(schedule);
        Schedule saved = schedule;

        // Snapshot live timeslots → new IDs with schedule_id set
        Map<UUID, UUID> timeslotRemap = new HashMap<>();
        List<Timeslot> liveTimeslots = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        for (Timeslot live : liveTimeslots) {
            Timeslot snapshot = new Timeslot();
            snapshot.setTenantId(tenantId);
            snapshot.setDeskId(deskId);
            snapshot.setScheduleId(saved.getId());
            snapshot.setDate(live.getDate());
            snapshot.setStartTime(live.getStartTime());
            snapshot.setEndTime(live.getEndTime());
            entityManager.persist(snapshot);
            timeslotRemap.put(live.getId(), snapshot.getId());
        }

        // Snapshot live staffing requirements → remap to snapshot timeslots
        List<StaffingRequirement> liveRequirements = staffingRequirementRepository
                .findLiveByDeskAndDateRange(tenantId, deskId,
                        schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        for (StaffingRequirement live : liveRequirements) {
            UUID snapshotTimeslotId = timeslotRemap.get(live.getTimeslot().getId());
            if (snapshotTimeslotId == null) continue;

            Timeslot snapshotTs = entityManager.getReference(Timeslot.class, snapshotTimeslotId);

            StaffingRequirement snapshot = new StaffingRequirement();
            snapshot.setTenantId(tenantId);
            snapshot.setDeskId(deskId);
            snapshot.setScheduleId(saved.getId());
            snapshot.setTimeslot(snapshotTs);
            snapshot.setSpecialization(live.getSpecialization());
            snapshot.setRequiredFTEs(live.getRequiredFTEs());
            snapshot.setSource(live.getSource());
            entityManager.persist(snapshot);
        }

        // Write solver's agent assignments → remap to snapshot timeslots
        for (AgentAssignment assignment : schedule.getAssignments()) {
            if (assignment.getAgent() == null) continue;

            UUID snapshotTimeslotId = timeslotRemap.get(assignment.getTimeslot().getId());
            if (snapshotTimeslotId == null) continue;

            Timeslot snapshotTs = entityManager.getReference(Timeslot.class, snapshotTimeslotId);

            AgentAssignment persisted = new AgentAssignment();
            persisted.setTenantId(tenantId);
            persisted.setDeskId(deskId);
            persisted.setScheduleId(saved.getId());
            persisted.setTimeslot(snapshotTs);
            persisted.setRequiredSpecialization(assignment.getRequiredSpecialization());
            persisted.setAgent(assignment.getAgent());
            entityManager.persist(persisted);
        }

        // Insert accepted_schedule_date rows for all covered dates
        List<AcceptedScheduleDate> dateEntries = coveredDates.stream()
                .map(date -> new AcceptedScheduleDate(saved.getId(), tenantId, deskId, date))
                .toList();
        acceptedScheduleDateRepository.saveAll(dateEntries);

        // Remove from in-memory store
        inMemoryStore.remove(scheduleId);

        return saved;
    }

    // --- deleteSchedule (accepted schedules) ---

    @Transactional
    public void deleteSchedule(UUID deskId, UUID scheduleId) {
        long tenantId = TenantContext.getTenantId();

        Schedule schedule = scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule", scheduleId));

        if (schedule.getStatus() != ScheduleStatus.ACCEPTED) {
            throw new ConflictException("Only ACCEPTED schedules can be deleted (status: "
                    + schedule.getStatus() + ")");
        }

        agentAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
        staffingRequirementRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
        timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
        scheduleRepository.delete(schedule);
    }

    // --- Task 27: rejectSchedule ---

    public void rejectSchedule(UUID deskId, UUID scheduleId) {
        long tenantId = TenantContext.getTenantId();

        Schedule schedule = inMemoryStore.get(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

        // Validate tenant and desk ownership
        if (schedule.getTenantId() != tenantId || !schedule.getDeskId().equals(deskId)) {
            throw new EntityNotFoundException("Schedule not found for desk: " + deskId);
        }

        if (schedule.getStatus() != ScheduleStatus.COMPLETED
                && schedule.getStatus() != ScheduleStatus.STOPPED
                && schedule.getStatus() != ScheduleStatus.FAILED) {
            throw new ConflictException("Schedule must be COMPLETED, STOPPED, or FAILED to reject (status: "
                    + schedule.getStatus() + ")");
        }

        inMemoryStore.remove(scheduleId);
    }

    // --- Helpers ---

    private void loadSnapshotData(Schedule schedule, long tenantId, UUID deskId) {
        List<Timeslot> timeslots = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, schedule.getId());
        schedule.setTimeslots(timeslots);

        List<AgentAssignment> assignments = agentAssignmentRepository
                .findWithRelationsByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, schedule.getId());
        schedule.setAssignments(assignments);

        List<StaffingRequirement> requirements = staffingRequirementRepository
                .findByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, schedule.getId());
        schedule.setStaffingRequirements(requirements);

        // Load days off for the schedule period to exclude PTO days from preferences
        List<AgentDayOff> allDaysOff = agentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
        Map<UUID, Set<LocalDate>> agentDaysOffMap = new HashMap<>();
        for (AgentDayOff d : allDaysOff) {
            agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
        }

        // Load and resolve agent preferences (standing + weekly override logic per §5.8)
        // Preferences on PTO days are excluded so they don't affect constraint violation display.
        List<AgentPreference> allPreferences = agentPreferenceRepository.findByTenantIdAndDeskId(tenantId, deskId);
        schedule.setAgentPreferences(resolvePreferences(allPreferences, schedule, agentDaysOffMap));

        // Load constraint weights so buildConstraintViolations can explain the score
        constraintWeightsRepository.findByTenantIdAndDeskId(tenantId, deskId)
                .ifPresent(schedule::setConstraintWeights);
    }

    /**
     * Resolve preferences: weekly overrides standing per agent-day (spec §5.8).
     * Mirrors the logic in SolverService.resolvePreferences.
     */
    private List<AgentPreference> resolvePreferences(List<AgentPreference> allPreferences,
                                                     Schedule schedule,
                                                     Map<UUID, Set<LocalDate>> agentDaysOffMap) {
        Map<UUID, Map<DayOfWeek, AgentPreference>> standingByAgent = new HashMap<>();
        Map<UUID, Map<LocalDate, AgentPreference>> weeklyByAgent = new HashMap<>();

        for (AgentPreference p : allPreferences) {
            UUID agentId = p.getAgent().getId();
            if (p.isStanding()) {
                standingByAgent.computeIfAbsent(agentId, k -> new HashMap<>())
                        .put(p.getDayOfWeek(), p);
            } else if (p.getDate() != null) {
                weeklyByAgent.computeIfAbsent(agentId, k -> new HashMap<>())
                        .put(p.getDate(), p);
            }
        }

        Set<UUID> allAgentIds = new HashSet<>();
        allAgentIds.addAll(standingByAgent.keySet());
        allAgentIds.addAll(weeklyByAgent.keySet());

        List<AgentPreference> resolved = new ArrayList<>();

        for (UUID agentId : allAgentIds) {
            Map<DayOfWeek, AgentPreference> standing = standingByAgent.getOrDefault(agentId, Map.of());
            Map<LocalDate, AgentPreference> weekly = weeklyByAgent.getOrDefault(agentId, Map.of());
            Set<LocalDate> daysOff = agentDaysOffMap.getOrDefault(agentId, Set.of());

            for (LocalDate d = schedule.getPeriodStartDate();
                 !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {

                // Skip PTO / day-off dates — preference stays in DB but is excluded from display
                if (daysOff.contains(d)) continue;

                AgentPreference weeklyPref = weekly.get(d);
                boolean weeklyHasData = weeklyPref != null
                        && (weeklyPref.getPreferredStartTime() != null
                        || weeklyPref.getPreferredBreakTime() != null);

                AgentPreference effective;
                if (weeklyHasData) {
                    effective = weeklyPref;
                } else {
                    effective = standing.get(d.getDayOfWeek());
                }

                if (effective == null) continue;
                if (effective.getPreferredStartTime() == null && effective.getPreferredBreakTime() == null) continue;

                AgentPreference rp = new AgentPreference();
                rp.setId(effective.getId());
                rp.setTenantId(effective.getTenantId());
                rp.setDeskId(effective.getDeskId());
                rp.setAgent(effective.getAgent());
                rp.setDayOfWeek(d.getDayOfWeek());
                rp.setDate(d);
                rp.setStanding(effective.isStanding());
                rp.setPreferredStartTime(effective.getPreferredStartTime());
                rp.setPreferredBreakTime(effective.getPreferredBreakTime());
                resolved.add(rp);
            }
        }

        return resolved;
    }

    private ScheduleDetailResponse buildDetailResponse(Schedule s) {
        ScheduleDetailResponse r = new ScheduleDetailResponse();
        r.setId(s.getId());
        r.setDeskId(s.getDeskId());
        r.setStatus(s.getStatus().name());
        r.setPeriodStartDate(s.getPeriodStartDate());
        r.setPeriodEndDate(s.getPeriodEndDate());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setIncrementMinutes(s.getIncrementMinutes());
        r.setBreakDurationMinutes(s.getBreakDurationMinutes());
        r.setBreakBlockedHours(s.getBreakBlockedHours());
        r.setBreakMinShiftHours(s.getBreakMinShiftHours());
        r.setBreakStartAlignment(s.getBreakStartAlignment() != null
                ? s.getBreakStartAlignment().name() : null);
        r.setBreakClusterThresholdPct(s.getBreakClusterThresholdPct());
        r.setDefaultContractedHoursPerDay(s.getDefaultContractedHoursPerDay());
        r.setOverallocationHardLimitPct(s.getOverallocationHardLimitPct());
        r.setUnderallocationHardLimitPct(s.getUnderallocationHardLimitPct());

        if (s.getScore() != null) {
            r.setScore(new ScheduleSummary.ScoreDto(s.getScore().hardScore(), s.getScore().softScore()));
            r.setFeasible(s.getScore().hardScore() >= 0);
        }

        r.setFeasibleAt(s.getFeasibleAt());
        r.setErrorMessage(s.getErrorMessage());
        r.setCreatedAt(s.getCreatedAt());
        r.setWarnings(s.getWarnings() != null ? s.getWarnings() : List.of());
        r.setVersion(s.getVersion());
        return r;
    }

    private ScheduleSummary toSummary(Schedule s, String deskName) {
        ScheduleSummary.ScoreDto scoreDto = null;
        Boolean feasible = null;
        if (s.getScore() != null) {
            scoreDto = new ScheduleSummary.ScoreDto(s.getScore().hardScore(), s.getScore().softScore());
            feasible = s.getScore().hardScore() >= 0;
        }
        return new ScheduleSummary(
                s.getId(), s.getDeskId(), deskName, s.getStatus().name(),
                s.getPeriodStartDate(), s.getPeriodEndDate(),
                s.getStartTime(), s.getEndTime(), s.getIncrementMinutes(),
                scoreDto, feasible, s.getFeasibleAt(), s.getCreatedAt(), s.getVersion());
    }
}
