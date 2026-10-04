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
    private final AgentShiftAssignmentRepository agentShiftAssignmentRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;
    private final AgentRestWaiverRepository agentRestWaiverRepository;
    private final RestPredecessorService restPredecessorService;
    private final ScheduleOutputService scheduleOutputService;
    private final EntityManager entityManager;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           AcceptedScheduleDateRepository acceptedScheduleDateRepository,
                           DeskRepository deskRepository,
                           InMemoryScheduleStore inMemoryStore,
                           TimeslotRepository timeslotRepository,
                           StaffingRequirementRepository staffingRequirementRepository,
                           AgentAssignmentRepository agentAssignmentRepository,
                           AgentShiftAssignmentRepository agentShiftAssignmentRepository,
                           AgentPreferenceRepository agentPreferenceRepository,
                           AgentDayOffRepository agentDayOffRepository,
                           ConstraintWeightsRepository constraintWeightsRepository,
                           AgentRestWaiverRepository agentRestWaiverRepository,
                           RestPredecessorService restPredecessorService,
                           ScheduleOutputService scheduleOutputService,
                           EntityManager entityManager) {
        this.scheduleRepository = scheduleRepository;
        this.acceptedScheduleDateRepository = acceptedScheduleDateRepository;
        this.deskRepository = deskRepository;
        this.inMemoryStore = inMemoryStore;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentAssignmentRepository = agentAssignmentRepository;
        this.agentShiftAssignmentRepository = agentShiftAssignmentRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
        this.agentRestWaiverRepository = agentRestWaiverRepository;
        this.restPredecessorService = restPredecessorService;
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
        // REST-07/T-22-35: dbSourced tracks exactly the entries that came from dbSchedules, never
        // the in-memory entry above -- InMemoryScheduleStore hands that object back BY REFERENCE
        // and a running solve owns it, so hydration below must never reach it.
        List<Schedule> dbSourced = new ArrayList<>();
        for (Schedule db : dbSchedules) {
            if (merged.stream().noneMatch(s -> s.getId().equals(db.getId()))) {
                merged.add(db);
                dbSourced.add(db);
            }
        }

        // REST-07/P-02 (22-VERIFICATION.md gap (a)): cost gate 1 -- narrow to the DB-sourced
        // entries that actually carry a snapshotted minimum rest. Per D-04/D-14 the column is
        // nullable with no baked-in default, so on the overwhelming majority of desks this
        // narrowed set is empty and the page issues zero extra queries, exactly as before this
        // closure.
        List<Schedule> needsHydration = dbSourced.stream()
                .filter(s -> s.getMinimumRestMinutes() != null)
                .toList();
        if (!needsHydration.isEmpty()) {
            // Cost gate 2: ONE desk-wide, date-ranged waiver query spanning every narrowed
            // schedule's period, never one query per row. hydrateRestWaiverInputsFromDb's own
            // per-schedule period filter is what keeps this shared result list correct for each
            // entry it is passed to.
            LocalDate minStart = needsHydration.stream().map(Schedule::getPeriodStartDate)
                    .min(LocalDate::compareTo).orElseThrow();
            LocalDate maxEnd = needsHydration.stream().map(Schedule::getPeriodEndDate)
                    .max(LocalDate::compareTo).orElseThrow();
            List<AgentRestWaiver> candidateWaivers = agentRestWaiverRepository
                    .findWithAgentByTenantIdAndDeskIdAndDateBetween(tenantId, deskId, minStart, maxEnd);
            for (Schedule s : needsHydration) {
                hydrateRestWaiverInputsFromDb(s, tenantId, deskId, candidateWaivers);
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
        response.setDriftReport(
                schedule.getSchedulingMode() == SchedulingMode.SHIFT
                        ? scheduleOutputService.buildDriftReport(schedule)
                        : null);
        response.setConstraintViolations(scheduleOutputService.buildConstraintViolations(schedule, fromDb));
        // REST-07/D-13: computed exactly ONCE and reused for both the
        // list and the two counts below -- never a second buildRestWaiverDisclosure call and
        // never a second walk over the waiver collection -- so the badge's numbers and the tab's
        // row counts can never disagree. Gated on the schedule's own getMinimumRestMinutes(),
        // matching toSummary's existing gate verbatim in condition and in meaning: both counts
        // stay null (never 0) when rest is not configured, which is the REST-04 display extension
        // this gap closure restores to the detail response.
        var disclosure = scheduleOutputService.buildRestWaiverDisclosure(schedule, fromDb);
        response.setRestWaiverDisclosure(disclosure);
        if (schedule.getMinimumRestMinutes() != null) {
            response.setAppliedRestWaiverCount(disclosure.applied().size());
            response.setUnusedRestWaiverCount(disclosure.unused().size());
        }

        // Derive violatedHardConstraints from constraint violations (deduplicated). This
        // derivation is correct once constraintViolations is correct (G-15-32) — the invariant it
        // upholds is that a `feasible: true` response can never simultaneously name a violated
        // hard constraint, because constraintViolations itself now reports reality on both the
        // live and accepted paths (ScheduleOutputService.buildConstraintViolations). The
        // invariant is asserted structurally, by making the source correct — never by filtering
        // this derived list on `feasible`, which would hide a genuinely infeasible schedule
        // instead of fixing the misreport.
        Set<String> violatedHardSet = new LinkedHashSet<>();
        if (response.getConstraintViolations() != null) {
            for (var cv : response.getConstraintViolations()) {
                if ("HARD".equals(cv.level())) {
                    violatedHardSet.add(cv.constraintName());
                }
            }
        }
        response.setViolatedHardConstraints(new ArrayList<>(violatedHardSet));

        // Warnings are re-published HERE, after every output view above has run, and as a
        // DEDUPLICATED DEFENSIVE COPY. Three distinct problems are closed by those two properties:
        //
        //  1. GROWTH. Several producers write into the one live warnings list — the solver's
        //     capacity advisory and per-date seat-supply advisories at solve time, and the
        //     envelope-divergence headline from buildAgentSchedule above. buildAgentSchedule runs
        //     on EVERY poll of this endpoint (~2s while a solve is RUNNING) against the same
        //     Schedule instance InMemoryScheduleStore hands back by reference, so any producer that
        //     appends without a guard inflates the operator's warnings panel without bound. An
        //     order-preserving dedup here caps the panel regardless of which producer misbehaves,
        //     rather than relying on every current and future writer to police itself.
        //  2. ALIASING. buildDetailResponse assigned the LIVE list straight into the response, so
        //     the DTO aliased mutable solver state and could change under serialisation.
        //  3. ORDERING TRAP. That assignment happens BEFORE the output views run, so copying it
        //     there instead would silently DROP the divergence warning buildAgentSchedule adds.
        //     Re-publishing after the views is what makes the copy safe.
        //
        // Deduplication is on exact message equality and keeps first-occurrence order, so two
        // genuinely different advisories (e.g. per-date seat-supply lines) both survive.
        List<String> liveWarnings = schedule.getWarnings();
        if (liveWarnings == null) {
            response.setWarnings(List.of());
        } else {
            synchronized (liveWarnings) {
                response.setWarnings(new ArrayList<>(new LinkedHashSet<>(liveWarnings)));
            }
        }

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
            // DRFT-01: unlike the PreferenceReport block above (which carries its whole-schedule
            // summary through unfiltered), 17-UI-SPEC.md requires the drift summary-bar counts to
            // reflect the CURRENTLY date-filtered entry set, so the four numbers always agree with
            // the rows the operator can see -- a promise the preference report's summary does not
            // make. popularity is carried through UNTOUCHED: it reads stored usual shifts, not
            // this date's solve results, so it is never affected by a date filter (D-13).
            if (response.getDriftReport() != null) {
                var filteredDriftEntries = response.getDriftReport().entries().stream()
                        .filter(e -> e.date().equals(filterDate)).toList();
                int filteredNoUsualShiftCount = 0;
                int filteredHonouredCount = 0;
                int filteredDriftedCount = 0;
                for (var driftEntry : filteredDriftEntries) {
                    switch (driftEntry.status()) {
                        case NO_USUAL_SHIFT -> filteredNoUsualShiftCount++;
                        case HONOURED -> filteredHonouredCount++;
                        case DRIFTED -> filteredDriftedCount++;
                    }
                }
                var recomputedSummary = new ScheduleDetailResponse.DriftSummary(
                        filteredDriftEntries.size(), filteredNoUsualShiftCount,
                        filteredHonouredCount, filteredDriftedCount);
                response.setDriftReport(new ScheduleDetailResponse.DriftReport(
                        filteredDriftEntries, recomputedSummary, response.getDriftReport().popularity()));
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
        // CR-02 gap closure: schedulingMode is now a mapped column (V43), already carrying the
        // mode this in-memory schedule was actually solved under (SolverService.buildSchedule),
        // so this persist() call writes it as a genuine recorded fact — nothing else needs to
        // write or infer it, at accept time or on later reload.
        entityManager.flush();
        schedule.setStatus(ScheduleStatus.ACCEPTED);
        schedule.setId(null);
        entityManager.persist(schedule);
        Schedule saved = schedule;

        // Snapshot live timeslots → new IDs with schedule_id set
        // SOLV-01 (20-REVIEW.md CR-01, site 3): schedule.getPeriodStartDate()/getPeriodEndDate()
        // are BUSINESS dates (18-CONTEXT.md D-22), so this load goes through
        // BusinessDayPeriodLoader rather than a calendar-date-filtering finder call directly,
        // which would silently truncate a re-anchored desk's last business day's post-midnight
        // rows out of this PERMANENT accepted snapshot.
        Map<UUID, UUID> timeslotRemap = new HashMap<>();
        List<Timeslot> liveTimeslots = BusinessDayPeriodLoader.loadLiveTimeslots(
                timeslotRepository, tenantId, deskId,
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate(), schedule.getDayStart());

        for (Timeslot live : liveTimeslots) {
            Timeslot snapshot = new Timeslot();
            snapshot.setTenantId(tenantId);
            snapshot.setDeskId(deskId);
            snapshot.setScheduleId(saved.getId());
            snapshot.setDate(live.getDate());
            snapshot.setStartTime(live.getStartTime());
            snapshot.setEndTime(live.getEndTime());
            // BDAY-02/BDAY-08: this is the propagating writer -- it copies the live row's
            // already-derived business_date rather than computing a new one, since this is a
            // snapshot of an already-generated row, not a new generation.
            snapshot.setBusinessDate(live.getBusinessDate());
            entityManager.persist(snapshot);
            timeslotRemap.put(live.getId(), snapshot.getId());
        }

        // Snapshot live staffing requirements → remap to snapshot timeslots
        // SOLV-01 (20-REVIEW.md CR-01, site 4): same business-date-vs-calendar-date mismatch as
        // the timeslot snapshot above, fixed with it through BusinessDayPeriodLoader. Must land
        // in the same commit as site 3 -- the remap loop below skips any requirement whose
        // timeslot is absent from timeslotRemap, so site 3 alone would persist post-midnight
        // snapshot timeslots with no demand rows attached.
        List<StaffingRequirement> liveRequirements = BusinessDayPeriodLoader.loadLiveStaffingRequirements(
                staffingRequirementRepository, tenantId, deskId,
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate(), schedule.getDayStart());

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

        // Write the solver's shift envelope assignments — D-07's denormalised accept-time
        // snapshot. Rows whose shiftBandPair is null are skipped, mirroring how the loop above
        // skips seats with a null agent (SLOT-mode desks always reach this loop with an empty
        // schedule.getShiftAssignments(), so it is a structural no-op there). The written row
        // denormalises the resolved envelope — template name, start, end, the assigned band's
        // offset/duration — rather than copying ShiftTemplate/ShiftTemplateBreakBand rows, so it
        // depends on nothing mutable: a later updateShiftTemplate can never rewrite what history
        // says this agent actually worked (D-07, discharging Phase 14's D-09 obligation).
        for (AgentShiftAssignment shiftAssignment : schedule.getShiftAssignments()) {
            ShiftBandPair pair = shiftAssignment.getShiftBandPair();
            if (pair == null) continue;

            ShiftTemplate template = pair.template();
            ShiftTemplateBreakBand band = pair.band();

            AgentShiftAssignment persistedShift = new AgentShiftAssignment();
            persistedShift.setTenantId(tenantId);
            persistedShift.setDeskId(deskId);
            persistedShift.setScheduleId(saved.getId());
            persistedShift.setAgent(shiftAssignment.getAgent());
            persistedShift.setDate(shiftAssignment.getDate());
            persistedShift.setTemplateName(template.getName());
            persistedShift.setShiftStartTime(template.getStartTime());
            persistedShift.setShiftEndTime(template.getEndTime());
            persistedShift.setBandOffsetMinutes(band == null ? null : band.getOffsetMinutes());
            persistedShift.setBandDurationMinutes(band == null ? null : band.getDurationMinutes());
            persistedShift.setSourceTemplateId(template.getId());
            entityManager.persist(persistedShift);
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
        // CR-03 gap closure: agent_shift_assignment carries no FK to schedule (V41), so deleting
        // the schedule row below does not cascade to it — it must be deleted explicitly here,
        // mirroring agentAssignmentRepository's own delete immediately above, or every
        // shift-envelope row for a deleted SHIFT-mode accepted schedule is permanently orphaned.
        agentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, scheduleId);
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

        // Load the accepted schedule's shift envelope rows (D-07) so a reopened accepted
        // schedule carries its shift rows exactly as an in-memory one carries them. Every loaded
        // row's transient shiftBandPair is naturally null (JPA never populates @Transient
        // fields) — display reads the denormalised scalars instead; reconstructing a live pair
        // from the current template would reintroduce the history-rewriting hazard D-07 rejects.
        List<AgentShiftAssignment> shiftAssignments = agentShiftAssignmentRepository
                .findWithRelationsByTenantIdAndDeskIdAndScheduleId(tenantId, deskId, schedule.getId());
        schedule.setShiftAssignments(shiftAssignments);

        // P-32 / CR-02 gap closure: schedulingMode records the mode THIS schedule was solved
        // under, never a live desk read — a desk's mode can change after acceptance, and the
        // report must still render correctly. It is now a mapped column (V43) written once at
        // accept time from the in-memory schedule's own recorded mode, so `schedule` above already
        // carries the correct value straight from the DB row — no inference from the shift-row
        // snapshot's emptiness is needed (or safe: that inference broke for a SHIFT-mode accept
        // that placed zero shifts, since an empty snapshot is legitimately reachable in that
        // state, not just in SLOT mode).

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

        // REST-07/D-13/T-22-21: the accepted-path waiver and pre-horizon loads, gated on the
        // schedule's own snapshotted minimum rest being non-null so an unconfigured desk's
        // accepted schedule issues no extra query. Mirrors SolverService's live-path loads of the
        // same two facts (Phase 22, 22-05/22-06), so buildRestWaiverDisclosure can compute the
        // identical result on both paths from the same shape of input.
        if (schedule.getMinimumRestMinutes() != null) {
            // REST-07/P-03 (22-12): re-pointed at the relations-fetching finder so the
            // disclosure's waiver input is loaded exactly one way on every path -- the
            // non-fetching finder would yield a lazy agent proxy that throws outside a
            // transaction (spring.jpa.open-in-view: false).
            List<AgentRestWaiver> restWaivers = agentRestWaiverRepository
                    .findWithAgentByTenantIdAndDeskIdAndDateBetween(
                            tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
            schedule.setAgentRestWaivers(restWaivers);

            List<RestSpan> priorRestSpans = restPredecessorService.resolvePriorSpans(
                    tenantId, deskId, schedule.getPeriodStartDate(), schedule.getMinimumRestMinutes(),
                    schedule.getDayStart(), schedule.getWarnings());
            schedule.setPriorRestSpans(priorRestSpans);
        }
    }

    /**
     * REST-07/P-02 (22-VERIFICATION.md gap (a)): hydrates the minimal subset of {@code schedule}'s
     * transient waiver/pre-horizon inputs that {@code ScheduleOutputService.buildRestWaiverDisclosure}
     * needs, for a DB-fetched {@link Schedule} reached from {@link #listSchedules} or
     * {@link #getScheduleSummary} -- the two paths that never call {@link #loadSnapshotData}.
     * Never called at all unless the caller has already confirmed {@code
     * schedule.getMinimumRestMinutes() != null} (cost gate 1); {@code candidateWaivers} is a
     * shared, pre-fetched list the caller is responsible for batching into at most one desk-wide
     * query (cost gate 2) -- this method itself never queries the waiver repository.
     *
     * <p><b>Cost gate 3, this method's own contract.</b> {@code candidateWaivers} is first
     * narrowed to the dates inside {@code schedule}'s own inclusive period. If that narrowed list
     * is empty, this method returns immediately after assigning the empty list -- no span query,
     * no predecessor query. A schedule with no in-period waiver is correctly {@code (empty,
     * empty)}, which is the true answer {@code buildRestWaiverDisclosure} needs, not a degraded
     * one.
     *
     * <p>Per D-06, a waiver on business date D concerns the gap from D-1's end to D's start, so
     * the business-date set this method loads spans is {@code {D, D-1}} for each distinct waiver
     * date D -- never {@code {D, D+1}}. Getting this backwards would load the wrong two dates and
     * silently report every applied waiver as unused.
     *
     * <p>The collections this method assigns are deliberately PARTIAL -- scoped to exactly the
     * business dates the filtered waivers reference -- and must never be read by anything other
     * than {@code buildRestWaiverDisclosure}. The {@link Schedule} instances this method touches
     * are DB-fetched locals used only to build one {@code ScheduleSummary} and then discarded:
     * never put into {@link InMemoryScheduleStore}, never persisted, never handed to another
     * output builder.
     *
     * <p>{@link #loadSnapshotData} is deliberately NOT reused here: it issues seven loads
     * including a whole-schedule assignment read, which {@code AgentAssignmentRepository}'s own
     * javadoc measures at six figures of rows on a large SLOT desk -- calling it per row in
     * {@link #listSchedules} would be exactly the N+1 {@code 22-VERIFICATION.md} warned against.
     */
    private void hydrateRestWaiverInputsFromDb(Schedule schedule, long tenantId, UUID deskId,
            List<AgentRestWaiver> candidateWaivers) {
        List<AgentRestWaiver> inPeriod = candidateWaivers.stream()
                .filter(w -> w.getDate() != null
                        && !w.getDate().isBefore(schedule.getPeriodStartDate())
                        && !w.getDate().isAfter(schedule.getPeriodEndDate()))
                .toList();
        schedule.setAgentRestWaivers(inPeriod);

        if (inPeriod.isEmpty()) {
            return;
        }

        // D-06: {D, D-1} per distinct waiver date D -- the successor side is the waiver's own
        // date, the predecessor side is one business date back. Clamped to the schedule's own
        // period so this never reaches outside what loadSnapshotData itself would load.
        Set<LocalDate> businessDates = new TreeSet<>();
        for (AgentRestWaiver w : inPeriod) {
            LocalDate waiverDate = w.getDate();
            businessDates.add(waiverDate);
            LocalDate predecessorDate = waiverDate.minusDays(1);
            if (!predecessorDate.isBefore(schedule.getPeriodStartDate())) {
                businessDates.add(predecessorDate);
            }
        }

        if (schedule.getSchedulingMode() == SchedulingMode.SHIFT) {
            List<AgentShiftAssignment> shiftRows = new ArrayList<>();
            for (LocalDate date : businessDates) {
                shiftRows.addAll(agentShiftAssignmentRepository
                        .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                                tenantId, deskId, schedule.getId(), date));
            }
            schedule.setShiftAssignments(shiftRows);
        } else {
            List<AgentAssignment> seatRows = new ArrayList<>();
            for (LocalDate date : businessDates) {
                seatRows.addAll(agentAssignmentRepository
                        .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                                tenantId, deskId, schedule.getId(), date));
            }
            schedule.setAssignments(seatRows);
        }

        // Pre-horizon lookback is needed only when a waiver sits on the period's FIRST business
        // date -- any other waiver's predecessor date is itself inside the period and was already
        // loaded into shiftRows/seatRows above.
        boolean needsPreHorizon = inPeriod.stream()
                .anyMatch(w -> w.getDate().equals(schedule.getPeriodStartDate()));
        if (needsPreHorizon) {
            List<RestSpan> priorRestSpans = restPredecessorService.resolvePriorSpans(
                    tenantId, deskId, schedule.getPeriodStartDate(), schedule.getMinimumRestMinutes(),
                    schedule.getDayStart(), schedule.getWarnings());
            schedule.setPriorRestSpans(priorRestSpans);
        }
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
        r.setDayStart(s.getDayStart());
        // REST-07/D-14: a mapped @Column read, populated by JPA on
        // every path -- including the DB-fallback path that never runs loadSnapshotData -- which
        // is precisely what the @Transient waiver/pre-horizon collections are not. This is the
        // configured-or-not signal the header badge and Rest Waivers tab gate on.
        r.setMinimumRestMinutes(s.getMinimumRestMinutes());
        r.setIncrementMinutes(s.getIncrementMinutes());
        r.setBreakDurationMinutes(s.getBreakDurationMinutes());
        r.setBreakBlockedHours(s.getBreakBlockedHours());
        r.setBreakMinShiftHours(s.getBreakMinShiftHours());
        r.setBreakStartAlignment(s.getBreakStartAlignment() != null
                ? s.getBreakStartAlignment().name() : null);
        r.setBreakClusterThresholdPct(s.getBreakClusterThresholdPct());
        // P-32 / CR-02: non-nullable — every schedule was solved under exactly one mode. In-memory
        // schedules already carry it from SolverService.buildSchedule; an accepted schedule reads
        // it directly off its own persisted scheduling_mode column (V43, never a live desk read),
        // populated by JPA on load like any other scalar field. Falls back to SLOT only for the
        // structurally-impossible case of neither path having run.
        r.setSchedulingMode(s.getSchedulingMode() != null ? s.getSchedulingMode().name() : SchedulingMode.SLOT.name());
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

    /**
     * One schedule's summary — status, score, feasibility, nothing else.
     *
     * <p><b>Why this exists as a separate read.</b> {@link #getScheduleDetail} on a desk the size of
     * Vinted returns **4.16 MB** and takes ~2.4 s to build: 2.62 MB of {@code agentSchedule} over
     * 1 356 agent-days, plus ~0.97 MB of constraint violations and ~0.57 MB of preference and drift
     * reports. The results page polls every 2 s while a solve runs, and it polls that whole payload
     * for a score that is fourteen bytes of it.
     *
     * <p>That cost lands twice. The poll interval becomes 2 s plus build time plus transfer, so the
     * score visibly lags; and the payload is assembled by walking ~23 000 assignments on the SAME
     * two-vCPU task that is running the solve, where {@code parallelSolverCount} resolves to 1
     * precisely because there are only two cores. Polling the detail steals throughput from the
     * search it is reporting on.
     *
     * <p>Resolves from the in-memory store first, because a RUNNING schedule exists only there —
     * the same precedence {@link #listSchedules} uses when it merges the live schedule ahead of the
     * accepted ones.
     */
    public ScheduleSummary getScheduleSummary(UUID deskId, UUID scheduleId) {
        long tenantId = TenantContext.getTenantId();

        // REST-07/P-02 (22-VERIFICATION.md gap (a)): provenance is resolved explicitly -- mirroring
        // getScheduleDetail's own fromDb shape exactly -- so the hydration call below runs only for
        // a DB-fetched schedule. A live (in-memory) schedule's transients are already populated by
        // the solve path; re-loading them would both cost queries and risk disagreeing with what
        // the solver holds.
        Schedule schedule = inMemoryStore.get(scheduleId)
                .filter(s -> s.getTenantId() == tenantId && s.getDeskId().equals(deskId))
                .orElse(null);
        boolean fromDb = false;

        if (schedule == null) {
            schedule = scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException("Schedule", scheduleId));
            fromDb = true;
        }

        // Cost gate 1: zero extra queries unless this DB-fetched schedule carries a snapshotted
        // minimum rest. This is the only ScheduleOutputService-adjacent work this method does
        // beyond what toSummary itself already triggers (see that method's own javadoc) --
        // getScheduleSummary must stay cheap, never growing into a second getScheduleDetail.
        if (fromDb && schedule.getMinimumRestMinutes() != null) {
            List<AgentRestWaiver> candidateWaivers = agentRestWaiverRepository
                    .findWithAgentByTenantIdAndDeskIdAndDateBetween(
                            tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
            hydrateRestWaiverInputsFromDb(schedule, tenantId, deskId, candidateWaivers);
        }

        String deskName = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getName).orElse(null);
        return toSummary(schedule, deskName);
    }

    /**
     * REST-07/WR-02: the single public entry point for turning a {@link Schedule} into a
     * {@link ScheduleSummary} — resolves the desk name itself (identically to the deleted
     * {@code ScheduleController.toSummary}'s own lookup) and delegates to the private two-argument
     * form, which stays the ONLY expression in this file that constructs a {@code ScheduleSummary}.
     * {@code ScheduleSummaryConstructionSiteGuardTest} fails the build if a second construction
     * site appears anywhere in {@code src/main/java}.
     *
     * <p>This replaces a parity test with a structural guard for the same reason D-08 already made
     * that trade once in this phase for the waived-pair predicate: a parity test only catches a
     * divergence someone remembered to write a fixture for, and the rest-waiver counts this method
     * derives are exactly the kind of field two independently-maintained copies drift on without
     * either copy's own tests ever noticing.
     *
     * <p>{@code listSchedules} deliberately does NOT call this one-argument form per row — it keeps
     * calling the private two-argument form directly with the one desk name it already resolved
     * for the whole page, so this single-entry-point guarantee never costs a page an extra desk
     * lookup per schedule.
     */
    public ScheduleSummary toSummary(Schedule s) {
        String deskName = deskRepository.findByIdAndTenantId(s.getDeskId(), TenantContext.getTenantId())
                .map(Desk::getName).orElse(null);
        return toSummary(s, deskName);
    }

    private ScheduleSummary toSummary(Schedule s, String deskName) {
        ScheduleSummary.ScoreDto scoreDto = null;
        Boolean feasible = null;
        if (s.getScore() != null) {
            scoreDto = new ScheduleSummary.ScoreDto(s.getScore().hardScore(), s.getScore().softScore());
            feasible = s.getScore().hardScore() >= 0;
        }
        // REST-07/D-13: derived from buildRestWaiverDisclosure -- the SAME computation the detail
        // response's restWaiverDisclosure field uses -- rather than a second walk over the waiver
        // collection, so the two can never disagree. This single call is the only
        // ScheduleOutputService work this method does: it must NOT build the staffing summary,
        // the agent schedule, the preference report or the drift report, which is the whole
        // reason getScheduleSummary exists as a separate, cheap endpoint (see that method's own
        // javadoc) -- computing a disclosure here looks like a first step toward computing
        // everything here, and it must stay the only step.
        Integer appliedRestWaiverCount = null;
        Integer unusedRestWaiverCount = null;
        if (s.getMinimumRestMinutes() != null) {
            var disclosure = scheduleOutputService.buildRestWaiverDisclosure(s, s.getStatus() == ScheduleStatus.ACCEPTED);
            appliedRestWaiverCount = disclosure.applied().size();
            unusedRestWaiverCount = disclosure.unused().size();
        }
        return new ScheduleSummary(
                s.getId(), s.getDeskId(), deskName, s.getStatus().name(),
                s.getPeriodStartDate(), s.getPeriodEndDate(),
                s.getStartTime(), s.getEndTime(), s.getIncrementMinutes(), s.getDayStart(),
                appliedRestWaiverCount, unusedRestWaiverCount,
                scoreDto, feasible, s.getFeasibleAt(), s.getCreatedAt(), s.getVersion());
    }
}
