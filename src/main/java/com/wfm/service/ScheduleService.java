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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final InMemoryScheduleStore inMemoryStore;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentAssignmentRepository agentAssignmentRepository;
    private final ScheduleOutputService scheduleOutputService;
    private final EntityManager entityManager;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           InMemoryScheduleStore inMemoryStore,
                           TimeslotRepository timeslotRepository,
                           StaffingRequirementRepository staffingRequirementRepository,
                           AgentAssignmentRepository agentAssignmentRepository,
                           ScheduleOutputService scheduleOutputService,
                           EntityManager entityManager) {
        this.scheduleRepository = scheduleRepository;
        this.inMemoryStore = inMemoryStore;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentAssignmentRepository = agentAssignmentRepository;
        this.scheduleOutputService = scheduleOutputService;
        this.entityManager = entityManager;
    }

    // --- Task 23: listSchedules ---

    public PaginatedResponse<ScheduleSummary> listSchedules(UUID deskId, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clamped = CursorPagination.clampLimit(limit);

        // Load accepted schedules from DB (ordered by createdAt desc)
        // Use a generous limit since schedule count per desk is typically small
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

        List<ScheduleSummary> summaries = new ArrayList<>(merged.stream().map(this::toSummary).toList());

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
    public Schedule acceptSchedule(UUID deskId, UUID scheduleId) {
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

        // 1. Delete overlapping accepted schedules for same desk/date range
        List<Schedule> overlapping = scheduleRepository.findOverlapping(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
        for (Schedule old : overlapping) {
            agentAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, old.getId());
            staffingRequirementRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, old.getId());
            timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, old.getId());
            scheduleRepository.delete(old);
        }

        // 2. Save the Schedule record — flush deletes first, then persist the new entity
        entityManager.flush();
        schedule.setStatus(ScheduleStatus.ACCEPTED);
        Schedule saved = entityManager.merge(schedule);

        // 3. Snapshot live timeslots → new IDs with schedule_id set
        Map<UUID, UUID> timeslotRemap = new HashMap<>();
        List<Timeslot> liveTimeslots = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        List<Timeslot> snapshotTimeslots = new ArrayList<>(liveTimeslots.size());
        for (Timeslot live : liveTimeslots) {
            UUID snapshotId = UUID.randomUUID();
            timeslotRemap.put(live.getId(), snapshotId);

            Timeslot snapshot = new Timeslot();
            snapshot.setId(snapshotId);
            snapshot.setTenantId(tenantId);
            snapshot.setDeskId(deskId);
            snapshot.setScheduleId(saved.getId());
            snapshot.setDate(live.getDate());
            snapshot.setStartTime(live.getStartTime());
            snapshot.setEndTime(live.getEndTime());
            snapshotTimeslots.add(snapshot);
        }
        timeslotRepository.saveAll(snapshotTimeslots);

        // 4. Snapshot live staffing requirements → remap to snapshot timeslots
        List<StaffingRequirement> liveRequirements = staffingRequirementRepository
                .findLiveByDeskAndDateRange(tenantId, deskId,
                        schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        List<StaffingRequirement> snapshotRequirements = new ArrayList<>(liveRequirements.size());
        for (StaffingRequirement live : liveRequirements) {
            UUID snapshotTimeslotId = timeslotRemap.get(live.getTimeslot().getId());
            if (snapshotTimeslotId == null) continue;

            Timeslot snapshotTs = new Timeslot();
            snapshotTs.setId(snapshotTimeslotId);

            StaffingRequirement snapshot = new StaffingRequirement();
            snapshot.setId(UUID.randomUUID());
            snapshot.setTenantId(tenantId);
            snapshot.setDeskId(deskId);
            snapshot.setScheduleId(saved.getId());
            snapshot.setTimeslot(snapshotTs);
            snapshot.setSpecialization(live.getSpecialization());
            snapshot.setRequiredFTEs(live.getRequiredFTEs());
            snapshot.setSource(live.getSource());
            snapshotRequirements.add(snapshot);
        }
        staffingRequirementRepository.saveAll(snapshotRequirements);

        // 5. Write solver's agent assignments → remap to snapshot timeslots
        List<AgentAssignment> snapshotAssignments = new ArrayList<>();
        for (AgentAssignment assignment : schedule.getAssignments()) {
            if (assignment.getAgent() == null) continue;

            UUID snapshotTimeslotId = timeslotRemap.get(assignment.getTimeslot().getId());
            if (snapshotTimeslotId == null) continue;

            Timeslot snapshotTs = new Timeslot();
            snapshotTs.setId(snapshotTimeslotId);

            AgentAssignment persisted = new AgentAssignment();
            persisted.setId(UUID.randomUUID());
            persisted.setTenantId(tenantId);
            persisted.setDeskId(deskId);
            persisted.setScheduleId(saved.getId());
            persisted.setTimeslot(snapshotTs);
            persisted.setRequiredSpecialization(assignment.getRequiredSpecialization());
            persisted.setAgent(assignment.getAgent());
            snapshotAssignments.add(persisted);
        }
        agentAssignmentRepository.saveAll(snapshotAssignments);

        // 6. Remove from in-memory store
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

        schedule.setAgentPreferences(List.of());
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

        r.setErrorMessage(s.getErrorMessage());
        r.setCreatedAt(s.getCreatedAt());
        r.setWarnings(s.getWarnings() != null ? s.getWarnings() : List.of());
        return r;
    }

    private ScheduleSummary toSummary(Schedule s) {
        ScheduleSummary.ScoreDto scoreDto = null;
        Boolean feasible = null;
        if (s.getScore() != null) {
            scoreDto = new ScheduleSummary.ScoreDto(s.getScore().hardScore(), s.getScore().softScore());
            feasible = s.getScore().hardScore() >= 0;
        }
        return new ScheduleSummary(
                s.getId(), s.getDeskId(), s.getStatus().name(),
                s.getPeriodStartDate(), s.getPeriodEndDate(),
                s.getStartTime(), s.getEndTime(), s.getIncrementMinutes(),
                scoreDto, feasible, s.getCreatedAt());
    }
}
