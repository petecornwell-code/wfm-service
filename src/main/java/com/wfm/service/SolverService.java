package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.SolveRequest;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.*;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.ConstraintWeightsRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SolverService {

    private static final Logger log = LoggerFactory.getLogger(SolverService.class);

    private final InMemoryScheduleStore inMemoryStore;
    private final SolverManager<Schedule, UUID> solverManager;
    private final DeskRepository deskRepository;
    private final AgentRepository agentRepository;
    private final SpecializationRepository specializationRepository;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;

    public SolverService(InMemoryScheduleStore inMemoryStore,
                         SolverManager<Schedule, UUID> solverManager,
                         DeskRepository deskRepository,
                         AgentRepository agentRepository,
                         SpecializationRepository specializationRepository,
                         TimeslotRepository timeslotRepository,
                         StaffingRequirementRepository staffingRequirementRepository,
                         AgentPreferenceRepository agentPreferenceRepository,
                         AgentDayOffRepository agentDayOffRepository,
                         AgentExceptionRepository agentExceptionRepository,
                         ConstraintWeightsRepository constraintWeightsRepository) {
        this.inMemoryStore = inMemoryStore;
        this.solverManager = solverManager;
        this.deskRepository = deskRepository;
        this.agentRepository = agentRepository;
        this.specializationRepository = specializationRepository;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
    }

    /**
     * Pre-solve phase: validate, load data, expand assignments, start solver async.
     * The @Transactional(readOnly=true) ensures all data is loaded in a single read
     * and the persistence context closes after this method, detaching all entities.
     */
    @Transactional(readOnly = true)
    public Schedule startSolve(UUID deskId, SolveRequest request) {
        long tenantId = TenantContext.getTenantId();

        // 1. Check no existing non-accepted schedule for this desk with overlapping dates
        Optional<Schedule> existingOpt = inMemoryStore.getByDeskId(deskId);
        if (existingOpt.isPresent()) {
            Schedule existing = existingOpt.get();
            boolean datesOverlap = !request.periodStartDate().isAfter(existing.getPeriodEndDate())
                    && !request.periodEndDate().isBefore(existing.getPeriodStartDate());
            if (datesOverlap || existing.getStatus() == ScheduleStatus.RUNNING) {
                throw new ConflictException("A schedule already exists for this desk. "
                        + "Stop it (if running) and accept or reject it before starting a new solve.");
            }
            // Non-overlapping dates and schedule is not running — auto-reject the old one
            inMemoryStore.remove(existing.getId());
        }

        // 2. Load desk for defaultContractedHoursPerDay inheritance
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk not found: " + deskId));

        // 3. Build Schedule from request with defaults, inheriting from Desk if needed
        Schedule schedule = buildSchedule(tenantId, deskId, request, desk);

        // 4. Load all problem facts from database
        List<Agent> allAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        List<Specialization> specializations = specializationRepository.findByTenantIdAndDeskId(tenantId, deskId);
        List<Timeslot> timeslots = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
        List<StaffingRequirement> staffingRequirements = staffingRequirementRepository
                .findLiveByDeskAndDateRange(tenantId, deskId,
                        schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        // Filter agents: only active agents with specializations assigned (spec §5.12)
        List<Agent> eligibleAgents = allAgents.stream()
                .filter(Agent::isActive)
                .filter(a -> a.getPrimarySpecialization() != null)
                .filter(a -> a.getSecondarySpecializations() != null
                        && !a.getSecondarySpecializations().isEmpty())
                .toList();

        // Load agent IDs for eligible agents
        Set<UUID> eligibleAgentIds = eligibleAgents.stream()
                .map(Agent::getId)
                .collect(Collectors.toSet());

        // Load days off for eligible agents in the schedule period
        List<AgentDayOff> allDaysOff = new ArrayList<>();
        for (UUID agentId : eligibleAgentIds) {
            allDaysOff.addAll(agentDayOffRepository.findByTenantIdAndAgent_IdAndDateBetweenOrderByDateAsc(
                    tenantId, agentId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate()));
        }

        // Load exceptions for this desk in the schedule period
        List<AgentException> exceptions = agentExceptionRepository.findByTenantIdAndDeskIdAndDateBetween(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());

        // Load preferences for this desk
        List<AgentPreference> allPreferences = agentPreferenceRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // Load constraint weights
        ConstraintWeights weights = constraintWeightsRepository.findByTenantIdAndDeskId(tenantId, deskId)
                .orElseGet(() -> {
                    ConstraintWeights cw = new ConstraintWeights();
                    cw.setTenantId(tenantId);
                    cw.setDeskId(deskId);
                    return cw;
                });

        // 5. Resolve preferences: weekly overrides standing per agent-day (spec §5.8)
        // Done before validation so break alignment check uses effective preferences.
        List<AgentPreference> resolvedPreferences = resolvePreferences(allPreferences, schedule);

        // 6. Run pre-solve validation (12 checks from spec §7.11)
        runPreSolveValidation(schedule, allAgents, timeslots, staffingRequirements,
                eligibleAgents, allDaysOff, exceptions, resolvedPreferences);

        // 7. Build lookup maps for days off and exceptions
        Map<UUID, Set<LocalDate>> agentDaysOffMap = new HashMap<>();
        for (AgentDayOff d : allDaysOff) {
            agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
        }
        Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
        for (AgentException ex : exceptions) {
            agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
                    .put(ex.getDate(), ex.getContractedHoursOverride());
        }

        // 8. Pre-compute AgentDayConfig problem facts (exception-aware effective hours)
        List<AgentDayConfig> agentDayConfigs = computeAgentDayConfigs(
                eligibleAgents, schedule, agentDaysOffMap, agentExceptionMap);

        // 8b. Compute capacity warnings (demand vs supply)
        computeCapacityWarnings(schedule, staffingRequirements, agentDayConfigs);

        // 9. Detach Hibernate proxy collections into plain ArrayList/HashSet
        List<Agent> detachedAgents = new ArrayList<>();
        for (Agent agent : eligibleAgents) {
            agent.setSecondarySpecializations(new ArrayList<>(agent.getSecondarySpecializations()));
            detachedAgents.add(agent);
        }

        // 10. Expand staffing requirements into AgentAssignment planning entities
        //     Creates demand-based seats PLUS overflow seats up to overallocationHardLimitPct.
        //     Overflow seats allow agents to extend into timeslots beyond demand so that
        //     every agent can meet their exact contracted hours.
        List<AgentAssignment> demandAssignments = expandAssignments(
                tenantId, deskId, schedule.getId(), staffingRequirements);

        // 10b. Compute per-timeslot demand FTE totals from demand-only assignments
        //      (overflow seats are NOT counted as demand)
        List<TimeslotDemandConfig> timeslotDemandConfigs = computeTimeslotDemandConfigs(demandAssignments);

        // 10c. Create overflow seats up to overallocationHardLimitPct
        List<AgentAssignment> overflowAssignments = expandOverflowAssignments(
                tenantId, deskId, schedule.getId(), staffingRequirements,
                schedule.getOverallocationHardLimitPct());

        List<AgentAssignment> assignments = new ArrayList<>(demandAssignments);
        assignments.addAll(overflowAssignments);
        log.debug("Overflow assignments: {} demand + {} overflow = {} total",
                demandAssignments.size(), overflowAssignments.size(), assignments.size());

        log.debug("Solver input — schedule={}, agents={}, timeslots={}, staffingRequirements={}, assignments={}, agentDayConfigs={}, preferences={}",
                schedule.getId(), detachedAgents.size(), timeslots.size(),
                staffingRequirements.size(), assignments.size(), agentDayConfigs.size(),
                resolvedPreferences.size());
        log.debug("Constraint weights — unassigned={}, dayOff={}, specMatch={}, contractedOver={}, contractedUnder={}",
                weights.getUnassignedAssignmentWeight(), weights.getAgentDayOffWeight(),
                weights.getSpecMatchWeight(), weights.getContractedHoursOverWeight(),
                weights.getContractedHoursUnderWeight());

        // 11. Populate the schedule with all collections
        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(new ArrayList<>(specializations));
        schedule.setAgents(detachedAgents);
        schedule.setTimeslots(new ArrayList<>(timeslots));
        schedule.setStaffingRequirements(new ArrayList<>(staffingRequirements));
        schedule.setAgentPreferences(new ArrayList<>(resolvedPreferences));
        schedule.setAgentDaysOff(new ArrayList<>(allDaysOff));
        schedule.setAgentExceptions(new ArrayList<>(exceptions));
        schedule.setAgentDayConfigs(agentDayConfigs);
        schedule.setTimeslotDemandConfigs(timeslotDemandConfigs);
        schedule.setAssignments(assignments);

        // 11b. All seat assignment is delegated to the solver's construction
        // heuristic (CH) which evaluates all 18 constraints simultaneously.
        // The CH builds a feasible initial solution, then local search improves it.
        log.info("All {} assignments start unassigned — solver CH will build initial solution",
                assignments.size());

        // 11c. Pre-solve score diagnostic: verify score delta for one assignment
        // Detects broken incremental scoring that would cause CH to pick {null -> null}
        runPreSolveScoreDiagnostic(schedule);

        // 12. Store in memory and start solver asynchronously
        log.info("Starting solver — schedule={}, period={} to {}, assignments={}",
                schedule.getId(), schedule.getPeriodStartDate(), schedule.getPeriodEndDate(),
                assignments.size());
        inMemoryStore.put(schedule);

        long solverTenantId = tenantId;

        var solveBuilder = solverManager.solveBuilder()
                .withProblemId(schedule.getId())
                .withProblemFinder((UUID id) -> {
                    TenantContext.setTenantId(solverTenantId);
                    return schedule;
                })
                .withBestSolutionConsumer((Schedule bestSolution) -> {
                    TenantContext.setTenantId(solverTenantId);
                    try {
                        log.debug("Solver best solution update — schedule={}, score={}",
                                bestSolution.getId(), bestSolution.getScore());
                        bestSolution.setStatus(ScheduleStatus.RUNNING);
                        inMemoryStore.put(bestSolution);
                    } finally {
                        TenantContext.clear();
                    }
                })
                .withFinalBestSolutionConsumer((Schedule finalBestSolution) -> {
                    TenantContext.setTenantId(solverTenantId);
                    try {
                        log.info("Solver finished — schedule={}, score={}, status={}",
                                finalBestSolution.getId(), finalBestSolution.getScore(),
                                finalBestSolution.getStatus());
                        // Only set COMPLETED if not already STOPPED (avoids race with stopSolve)
                        if (finalBestSolution.getStatus() == ScheduleStatus.RUNNING) {
                            finalBestSolution.setStatus(ScheduleStatus.COMPLETED);
                        }
                        inMemoryStore.put(finalBestSolution);
                    } finally {
                        TenantContext.clear();
                    }
                })
                .withExceptionHandler((UUID problemId, Throwable throwable) -> {
                    TenantContext.setTenantId(solverTenantId);
                    try {
                        log.error("Solver failed for schedule {}", problemId, throwable);
                        schedule.setStatus(ScheduleStatus.FAILED);
                        schedule.setErrorMessage(throwable.getMessage());
                        inMemoryStore.put(schedule);
                    } finally {
                        TenantContext.clear();
                    }
                });

        if (request.solveTimeSeconds() != null && request.solveTimeSeconds() > 0) {
            long seconds = request.solveTimeSeconds();
            long unimprovedSeconds = Math.max(30, seconds * 3 / 10); // 30% of total, min 30s
            log.info("Custom solve time: {}s total, {}s unimproved limit", seconds, unimprovedSeconds);
            solveBuilder = solveBuilder.withConfigOverride(
                    new SolverConfigOverride<Schedule>()
                            .withTerminationConfig(new TerminationConfig()
                                    .withSpentLimit(Duration.ofSeconds(seconds))
                                    .withUnimprovedSpentLimit(Duration.ofSeconds(unimprovedSeconds))));
        }

        solveBuilder.run();

        return schedule;
    }

    public Schedule stopSolve(UUID deskId, UUID scheduleId) {
        Schedule schedule = inMemoryStore.get(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

        if (schedule.getStatus() != ScheduleStatus.RUNNING) {
            throw new ConflictException("Schedule is not running (status: " + schedule.getStatus() + ")");
        }

        // Set STOPPED before terminateEarly so the finalBestSolution callback won't overwrite
        schedule.setStatus(ScheduleStatus.STOPPED);
        solverManager.terminateEarly(scheduleId);
        inMemoryStore.put(schedule);
        return schedule;
    }

    // --- Schedule builder ---

    private Schedule buildSchedule(long tenantId, UUID deskId, SolveRequest request, Desk desk) {
        if (request.periodStartDate() == null || request.periodEndDate() == null
                || request.startTime() == null || request.endTime() == null) {
            throw new IllegalArgumentException(
                    "periodStartDate, periodEndDate, startTime, endTime, and incrementMinutes are required");
        }
        if (request.incrementMinutes() != 15 && request.incrementMinutes() != 30
                && request.incrementMinutes() != 60) {
            throw new IllegalArgumentException("incrementMinutes must be 15, 30, or 60");
        }

        Schedule s = new Schedule();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setDeskId(deskId);
        s.setStatus(ScheduleStatus.RUNNING);
        s.setCreatedAt(OffsetDateTime.now());

        s.setPeriodStartDate(request.periodStartDate());
        s.setPeriodEndDate(request.periodEndDate());
        s.setStartTime(request.startTime());
        s.setEndTime(request.endTime());
        s.setIncrementMinutes(request.incrementMinutes());

        if (request.breakBlockedHours() != null) s.setBreakBlockedHours(request.breakBlockedHours());
        if (request.breakDurationMinutes() != null) s.setBreakDurationMinutes(request.breakDurationMinutes());
        if (request.breakMinShiftHours() != null) s.setBreakMinShiftHours(request.breakMinShiftHours());
        if (request.breakStartAlignment() != null) {
            s.setBreakStartAlignment(BreakAlignment.valueOf(request.breakStartAlignment()));
        }
        if (request.breakClusterThresholdPct() != null) s.setBreakClusterThresholdPct(request.breakClusterThresholdPct());

        // defaultContractedHoursPerDay: use request value, else inherit from Desk, else keep
        // the Schedule field default (8.00) — null would prevent AgentDayConfig creation
        if (request.defaultContractedHoursPerDay() != null) {
            s.setDefaultContractedHoursPerDay(request.defaultContractedHoursPerDay());
        } else if (desk.getDefaultContractedHoursPerDay() != null) {
            s.setDefaultContractedHoursPerDay(desk.getDefaultContractedHoursPerDay());
        }

        if (request.overallocationHardLimitPct() != null) s.setOverallocationHardLimitPct(request.overallocationHardLimitPct());
        if (request.underallocationHardLimitPct() != null) s.setUnderallocationHardLimitPct(request.underallocationHardLimitPct());

        return s;
    }

    // --- Preference resolution (spec §5.8) ---

    /**
     * Resolves preferences per agent-day within the schedule period.
     * For each agent-day: if a weekly (non-standing) preference exists for that date
     * AND has at least one non-null preference field, use it; else fall back to the
     * standing preference for that day of week; else no preference.
     * Returns date-specific preferences (isStanding=false, date set) so constraints
     * can match on exact dates without needing standing/weekly resolution logic.
     */
    private List<AgentPreference> resolvePreferences(List<AgentPreference> allPreferences,
                                                     Schedule schedule) {
        // Index standing and weekly preferences by agent
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

            for (LocalDate d = schedule.getPeriodStartDate();
                 !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {

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

                // Create a date-specific resolved preference so constraints match on exact date
                AgentPreference rp = new AgentPreference();
                rp.setId(effective.getId());
                rp.setTenantId(effective.getTenantId());
                rp.setDeskId(effective.getDeskId());
                rp.setAgent(effective.getAgent());
                rp.setDayOfWeek(d.getDayOfWeek());
                rp.setDate(d);
                rp.setStanding(false);
                rp.setPreferredStartTime(effective.getPreferredStartTime());
                rp.setPreferredBreakTime(effective.getPreferredBreakTime());
                resolved.add(rp);
            }
        }

        return resolved;
    }

    // --- Pre-compute AgentDayConfig problem facts ---

    private List<AgentDayConfig> computeAgentDayConfigs(
            List<Agent> eligibleAgents,
            Schedule schedule,
            Map<UUID, Set<LocalDate>> agentDaysOffMap,
            Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap) {

        List<AgentDayConfig> configs = new ArrayList<>();

        for (Agent agent : eligibleAgents) {
            UUID agentId = agent.getId();
            Map<LocalDate, BigDecimal> exMap = agentExceptionMap.getOrDefault(agentId, Map.of());
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agentId, Set.of());

            for (LocalDate d = schedule.getPeriodStartDate();
                 !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {

                if (dayOffSet.contains(d)) continue;

                BigDecimal effectiveHours = getEffectiveHours(agent, d, exMap, schedule);
                if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;

                configs.add(new AgentDayConfig(
                        agent.getId(),
                        d,
                        effectiveHours,
                        schedule.getIncrementMinutes(),
                        schedule.getBreakDurationMinutes(),
                        schedule.getBreakMinShiftHours(),
                        schedule.getBreakBlockedHours(),
                        schedule.getBreakStartAlignment(),
                        schedule.getOverallocationHardLimitPct(),
                        schedule.getUnderallocationHardLimitPct()));
            }
        }

        return configs;
    }

    // --- Timeslot demand configs (per-timeslot demand totals for bulk allocation constraints) ---

    private List<TimeslotDemandConfig> computeTimeslotDemandConfigs(List<AgentAssignment> assignments) {
        Map<Timeslot, Integer> demandPerTimeslot = new LinkedHashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        List<TimeslotDemandConfig> configs = new ArrayList<>();
        for (Map.Entry<Timeslot, Integer> e : demandPerTimeslot.entrySet()) {
            configs.add(new TimeslotDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
    }

    // --- Capacity warnings (demand vs supply analysis) ---

    private void computeCapacityWarnings(Schedule schedule,
                                          List<StaffingRequirement> staffingRequirements,
                                          List<AgentDayConfig> agentDayConfigs) {
        int incrementMinutes = schedule.getIncrementMinutes();

        // Sum total demand slots from staffing requirements (1 FTE = 1 slot per timeslot)
        long totalDemandSlots = 0;
        for (StaffingRequirement sr : staffingRequirements) {
            totalDemandSlots += sr.getRequiredFTEs();
        }

        // Sum total supply slots from agent day configs
        long totalSupplySlots = 0;
        BigDecimal totalSupplyHours = BigDecimal.ZERO;
        for (AgentDayConfig adc : agentDayConfigs) {
            BigDecimal effectiveHours = adc.effectiveHours();
            long slots = effectiveHours
                    .multiply(BigDecimal.valueOf(60))
                    .divide(BigDecimal.valueOf(incrementMinutes), 0, RoundingMode.HALF_UP)
                    .longValue();
            totalSupplySlots += slots;
            totalSupplyHours = totalSupplyHours.add(effectiveHours);
        }

        List<String> warnings = new ArrayList<>();
        if (totalDemandSlots > totalSupplySlots) {
            long deficit = totalDemandSlots - totalSupplySlots;
            warnings.add(String.format(
                    "Demand (%d FTE-slots) exceeds supply (%s hrs, %d slots) by %d slots. "
                    + "The solver will produce the best partial schedule, but at least %d assignment(s) will remain unassigned.",
                    totalDemandSlots,
                    totalSupplyHours.setScale(2, RoundingMode.HALF_UP), totalSupplySlots,
                    deficit, deficit));
            log.warn("Capacity warning for schedule {}: demand={} slots, supply={} slots, deficit={}",
                    schedule.getId(), totalDemandSlots, totalSupplySlots, deficit);
        }

        schedule.setWarnings(warnings);
    }

    // --- Pre-solve validation (12 checks from spec §7.11) ---

    private void runPreSolveValidation(Schedule schedule,
                                       List<Agent> allAgents,
                                       List<Timeslot> timeslots,
                                       List<StaffingRequirement> staffingRequirements,
                                       List<Agent> eligibleAgents,
                                       List<AgentDayOff> daysOff,
                                       List<AgentException> exceptions,
                                       List<AgentPreference> preferences) {
        List<ErrorDetail> errors = new ArrayList<>();

        // 1. Period length: 1-31 days
        long periodDays = ChronoUnit.DAYS.between(schedule.getPeriodStartDate(), schedule.getPeriodEndDate()) + 1;
        if (periodDays < 1 || periodDays > 31) {
            errors.add(new ErrorDetail("periodEndDate",
                    "Schedule period must be between 1 and 31 days (got " + periodDays + ")", null));
        }

        // 2. Timeslots must exist for every day of the schedule period
        Set<LocalDate> timeslotDates = timeslots.stream()
                .map(Timeslot::getDate).collect(Collectors.toSet());
        for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
            if (!timeslotDates.contains(d)) {
                errors.add(new ErrorDetail("timeslots",
                        "No timeslots found for date " + d, null));
            }
        }

        // 3. Increment, startTime, endTime must match timeslot structure
        if (!timeslots.isEmpty()) {
            Timeslot first = timeslots.get(0);
            if (!first.getStartTime().equals(schedule.getStartTime())) {
                errors.add(new ErrorDetail("startTime",
                        "Schedule startTime " + schedule.getStartTime()
                                + " does not match timeslot start " + first.getStartTime(),
                        schedule.getStartTime().toString()));
            }
            Timeslot lastOnDay = timeslots.stream()
                    .filter(t -> t.getDate().equals(first.getDate()))
                    .reduce((a, b) -> b).orElse(first);
            if (!lastOnDay.getEndTime().equals(schedule.getEndTime())) {
                errors.add(new ErrorDetail("endTime",
                        "Schedule endTime " + schedule.getEndTime()
                                + " does not match timeslot end " + lastOnDay.getEndTime(),
                        schedule.getEndTime().toString()));
            }
            long timeslotMinutes = ChronoUnit.MINUTES.between(first.getStartTime(), first.getEndTime());
            if (timeslotMinutes != schedule.getIncrementMinutes()) {
                errors.add(new ErrorDetail("incrementMinutes",
                        "Schedule incrementMinutes " + schedule.getIncrementMinutes()
                                + " does not match timeslot increment " + timeslotMinutes,
                        String.valueOf(schedule.getIncrementMinutes())));
            }
        }

        // Build lookup maps for days off and exceptions
        Map<UUID, Set<LocalDate>> agentDaysOffMap = new HashMap<>();
        for (AgentDayOff d : daysOff) {
            agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
        }
        Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
        for (AgentException ex : exceptions) {
            agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
                    .put(ex.getDate(), ex.getContractedHoursOverride());
        }

        // 4. Every active agent must have a primary specialization and at least one secondary
        for (Agent agent : allAgents) {
            if (!agent.isActive()) continue;
            if (agent.getPrimarySpecialization() == null) {
                errors.add(new ErrorDetail("agent.specializations",
                        "Agent " + agent.getName()
                                + " must have a primary specialization assigned",
                        agent.getId().toString()));
            }
            if (agent.getSecondarySpecializations() == null || agent.getSecondarySpecializations().isEmpty()) {
                errors.add(new ErrorDetail("agent.specializations",
                        "Agent " + agent.getName()
                                + " must have at least one secondary specialization assigned",
                        agent.getId().toString()));
            }
        }

        // 5. Every agent's effective contracted hours must be a multiple of incrementMinutes/60
        BigDecimal incrementHours = BigDecimal.valueOf(schedule.getIncrementMinutes())
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
        for (Agent agent : eligibleAgents) {
            UUID agentId = agent.getId();
            Map<LocalDate, BigDecimal> exMap = agentExceptionMap.getOrDefault(agentId, Map.of());
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agentId, Set.of());

            for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                if (dayOffSet.contains(d)) continue;
                BigDecimal effectiveHours = getEffectiveHours(agent, d, exMap, schedule);
                if (effectiveHours != null && effectiveHours.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal remainder = effectiveHours.remainder(incrementHours);
                    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                        errors.add(new ErrorDetail("agent.contractedHoursPerDay",
                                "Agent " + agent.getName() + " has contracted hours "
                                        + effectiveHours + " on " + d
                                        + " which is not a multiple of " + incrementHours + " hours",
                                effectiveHours.toString()));
                    }
                }
            }
        }

        // 6. At least one staffing requirement must exist
        if (staffingRequirements.isEmpty()) {
            errors.add(new ErrorDetail("staffingRequirements",
                    "No staffing requirements found for the schedule period", null));
        }

        // 7. At least one active agent must be available (not day-off every day)
        boolean anyAvailable = false;
        for (Agent agent : eligibleAgents) {
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agent.getId(), Set.of());
            for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                if (!dayOffSet.contains(d)) {
                    anyAvailable = true;
                    break;
                }
            }
            if (anyAvailable) break;
        }
        if (!anyAvailable) {
            errors.add(new ErrorDetail("agents",
                    "No active agents are available (all are on day off for every day of the period)", null));
        }

        // 8. breakDurationMinutes must be a positive multiple of incrementMinutes
        if (schedule.getBreakDurationMinutes() <= 0
                || schedule.getBreakDurationMinutes() % schedule.getIncrementMinutes() != 0) {
            errors.add(new ErrorDetail("breakDurationMinutes",
                    "breakDurationMinutes (" + schedule.getBreakDurationMinutes()
                            + ") must be a positive multiple of incrementMinutes ("
                            + schedule.getIncrementMinutes() + ")",
                    String.valueOf(schedule.getBreakDurationMinutes())));
        }

        // 9. Break alignment conformance for effective preferred break times (spec §7.11)
        // Preferences passed here are already resolved (date-specific, never standing),
        // so we only check dates within the schedule period.
        BreakAlignment alignment = schedule.getBreakStartAlignment();
        for (AgentPreference pref : preferences) {
            if (pref.getPreferredBreakTime() == null) continue;
            if (pref.getDate() == null
                    || pref.getDate().isBefore(schedule.getPeriodStartDate())
                    || pref.getDate().isAfter(schedule.getPeriodEndDate())) {
                continue;
            }

            if (!isAligned(pref.getPreferredBreakTime(), alignment)) {
                errors.add(new ErrorDetail("agentPreference.breakTime",
                        "Agent " + pref.getAgent().getName() + " (" + pref.getDate()
                                + ") has preferred break time " + pref.getPreferredBreakTime()
                                + " which does not conform to alignment " + alignment,
                        pref.getPreferredBreakTime().toString()));
            }
        }

        // 10. Coverage window must be >= contracted hours + break for each agent-day
        long coverageMinutes = ChronoUnit.MINUTES.between(schedule.getStartTime(), schedule.getEndTime());
        BigDecimal coverageHours = BigDecimal.valueOf(coverageMinutes)
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
        BigDecimal breakHours = BigDecimal.valueOf(schedule.getBreakDurationMinutes())
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);

        for (Agent agent : eligibleAgents) {
            UUID agentId = agent.getId();
            Map<LocalDate, BigDecimal> exMap = agentExceptionMap.getOrDefault(agentId, Map.of());
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agentId, Set.of());

            for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                if (dayOffSet.contains(d)) continue;
                BigDecimal effectiveHours = getEffectiveHours(agent, d, exMap, schedule);
                if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;

                boolean needsBreak = effectiveHours.compareTo(schedule.getBreakMinShiftHours()) > 0;
                BigDecimal requiredWindow = needsBreak
                        ? effectiveHours.add(breakHours)
                        : effectiveHours;

                if (coverageHours.compareTo(requiredWindow) < 0) {
                    errors.add(new ErrorDetail("agent.contractedHoursPerDay",
                            "Agent " + agent.getName() + " on " + d
                                    + " needs " + requiredWindow + " hours window"
                                    + (needsBreak ? " (incl. break)" : "")
                                    + " but coverage window is only " + coverageHours + " hours",
                            effectiveHours.toString()));
                }
            }
        }

        // 11. No agent may have both an exception and a day off on the same date
        for (AgentException ex : exceptions) {
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(ex.getAgent().getId(), Set.of());
            if (dayOffSet.contains(ex.getDate())) {
                errors.add(new ErrorDetail("agentException.date",
                        "Agent " + ex.getAgent().getName() + " has both an exception and a day off on "
                                + ex.getDate(),
                        ex.getDate().toString()));
            }
        }

        // 12. Every specialization referenced by a staffing requirement must have an eligible agent
        Set<UUID> demandedSpecIds = staffingRequirements.stream()
                .map(sr -> sr.getSpecialization().getId())
                .collect(Collectors.toSet());
        for (UUID specId : demandedSpecIds) {
            String specName = staffingRequirements.stream()
                    .filter(sr -> sr.getSpecialization().getId().equals(specId))
                    .findFirst()
                    .map(sr -> sr.getSpecialization().getName())
                    .orElse(specId.toString());

            boolean hasEligible = eligibleAgents.stream().anyMatch(agent -> {
                boolean matchesPrimary = agent.getPrimarySpecialization() != null
                        && agent.getPrimarySpecialization().getId().equals(specId);
                boolean matchesSecondary = agent.getSecondarySpecializations().stream()
                        .anyMatch(s -> s.getId().equals(specId));
                if (!matchesPrimary && !matchesSecondary) return false;
                Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agent.getId(), Set.of());
                for (LocalDate d = schedule.getPeriodStartDate();
                     !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                    if (!dayOffSet.contains(d)) return true;
                }
                return false;
            });

            if (!hasEligible) {
                errors.add(new ErrorDetail("staffingRequirement.specialization",
                        "Specialization '" + specName + "' has staffing demand but no eligible desk-agent",
                        specId.toString()));
            }
        }

        if (!errors.isEmpty()) {
            throw new PreSolveValidationException(
                    "Pre-solve validation failed with " + errors.size() + " issue(s)", errors);
        }
    }

    private BigDecimal getEffectiveHours(Agent agent, LocalDate date,
                                         Map<LocalDate, BigDecimal> exceptionMap,
                                         Schedule schedule) {
        if (exceptionMap.containsKey(date)) {
            return exceptionMap.get(date);
        }
        return agent.getContractedHoursPerDay() != null
                ? agent.getContractedHoursPerDay()
                : schedule.getDefaultContractedHoursPerDay();
    }

    private boolean isAligned(LocalTime time, BreakAlignment alignment) {
        int minute = time.getMinute();
        return switch (alignment) {
            case ON_HOUR -> minute == 0;
            case ON_HALF_HOUR -> minute == 0 || minute == 30;
            case ON_QUARTER_HOUR -> minute % 15 == 0;
        };
    }

    // --- Expand staffing requirements into AgentAssignment planning entities ---

    private List<AgentAssignment> expandAssignments(long tenantId, UUID deskId, UUID scheduleId,
                                                      List<StaffingRequirement> staffingRequirements) {
        List<AgentAssignment> assignments = new ArrayList<>();
        for (StaffingRequirement sr : staffingRequirements) {
            int requiredAgents = sr.getRequiredFTEs();

            for (int i = 0; i < requiredAgents; i++) {
                AgentAssignment a = new AgentAssignment();
                a.setId(UUID.randomUUID());
                a.setTenantId(tenantId);
                a.setDeskId(deskId);
                a.setScheduleId(scheduleId);
                a.setTimeslot(sr.getTimeslot());
                a.setRequiredSpecialization(sr.getSpecialization());
                // agent is the planning variable — initially null, solver assigns
                assignments.add(a);
            }
        }
        return assignments;
    }

    /**
     * Creates overflow assignment entities beyond demand, up to overallocationHardLimitPct.
     * For each staffing requirement, if overallocationHardLimitPct > 100%, additional seats
     * are created with the same timeslot and specialization. These overflow seats give the
     * solver room to assign agents beyond demand so that every agent can meet their exact
     * contracted hours. The bulk over-allocation constraint penalises exceeding the limit.
     */
    private List<AgentAssignment> expandOverflowAssignments(long tenantId, UUID deskId, UUID scheduleId,
                                                             List<StaffingRequirement> staffingRequirements,
                                                             int overallocationHardLimitPct) {
        if (overallocationHardLimitPct <= 100) {
            return List.of();
        }
        List<AgentAssignment> overflow = new ArrayList<>();
        for (StaffingRequirement sr : staffingRequirements) {
            int requiredAgents = sr.getRequiredFTEs();
            int maxAgents = requiredAgents * overallocationHardLimitPct / 100;
            int overflowAgents = maxAgents - requiredAgents;

            for (int i = 0; i < overflowAgents; i++) {
                AgentAssignment a = new AgentAssignment();
                a.setId(UUID.randomUUID());
                a.setTenantId(tenantId);
                a.setDeskId(deskId);
                a.setScheduleId(scheduleId);
                a.setTimeslot(sr.getTimeslot());
                a.setRequiredSpecialization(sr.getSpecialization());
                overflow.add(a);
            }
        }
        return overflow;
    }

    /**
     * Pre-solve diagnostic: scores the initial state, assigns one agent to one seat,
     * re-scores, and logs the delta. Detects broken incremental scoring that would
     * cause the CH to pick {null -> null} for every step.
     */
    private void runPreSolveScoreDiagnostic(Schedule schedule) {
        try {
            var solverFactory = ai.timefold.solver.core.api.solver.SolverFactory.<Schedule>create(
                    new ai.timefold.solver.core.config.solver.SolverConfig()
                            .withSolutionClass(Schedule.class)
                            .withEntityClasses(AgentAssignment.class)
                            .withScoreDirectorFactory(
                                    new ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig()
                                            .withConstraintProviderClass(com.wfm.solver.ScheduleConstraintProvider.class)));

            var sm = ai.timefold.solver.core.api.solver.SolutionManager
                    .<Schedule, ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore>create(solverFactory);

            // Score initial state
            var initialScore = sm.update(schedule);
            log.info("Pre-solve diagnostic — initial score: {}", initialScore);

            // Print constraint breakdown
            var explanation = sm.explain(schedule);
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore.ZERO)) {
                    log.info("  {} => {} (count: {})", name, total.getScore(), total.getConstraintMatchCount());
                }
            });

            // Try assigning one agent to one seat
            if (!schedule.getAgents().isEmpty() && !schedule.getAssignments().isEmpty()) {
                Agent testAgent = schedule.getAgents().get(0);
                AgentAssignment testAssignment = schedule.getAssignments().get(0);

                testAssignment.setAgent(testAgent);
                var afterScore = sm.update(schedule);

                int hardDelta = afterScore.hardScore() - initialScore.hardScore();
                int softDelta = afterScore.softScore() - initialScore.softScore();

                log.info("Pre-solve diagnostic — after 1 assignment: {} (delta: {}hard/{}soft)",
                        afterScore, hardDelta, softDelta);

                if (hardDelta <= 0) {
                    log.error("DIAGNOSTIC FAILURE: assigning agent {} to timeslot {} makes score WORSE "
                            + "(delta={}hard). The CH will pick null for every step!",
                            testAgent.getName(),
                            testAssignment.getTimeslot().getStartTime(),
                            hardDelta);

                    // Print the after-assignment constraint breakdown
                    var afterExplanation = sm.explain(schedule);
                    afterExplanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                        if (!total.getScore().equals(ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore.ZERO)) {
                            log.error("  {} => {} (count: {})", name, total.getScore(), total.getConstraintMatchCount());
                        }
                    });
                }

                // Revert the test assignment
                testAssignment.setAgent(null);
                sm.update(schedule);
            }
        } catch (Exception e) {
            log.warn("Pre-solve diagnostic failed (non-fatal): {}", e.getMessage());
        }
    }
}
