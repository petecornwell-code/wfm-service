package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibraryValidationResponse.CapacityAdvisory;
import com.wfm.dto.SolveRequest;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.*;
import com.wfm.solver.ScheduleConstraintProvider;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.ConstraintWeightsRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final Duration defaultTimeLimit;
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
    private final AgentDayHoursRepository agentDayHoursRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;
    private final AgentEligibilityService agentEligibilityService;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;
    private final ShiftLibraryValidationService shiftLibraryValidationService;

    // D-09/D-10: same window BambooRefreshService uses to sync PTO from BambooHR. Field
    // injection (not a constructor parameter) so every existing SolverService test — which
    // either calls the static helpers directly or constructs the service with its current
    // parameter list — keeps working untouched.
    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int bambooLookaheadWeeks;

    @Value("${bamboohr.time-off.lookback-weeks:12}")
    private int bambooLookbackWeeks;

    public SolverService(@Value("${solver.time-limit:PT5M}") Duration defaultTimeLimit,
                         InMemoryScheduleStore inMemoryStore,
                         SolverManager<Schedule, UUID> solverManager,
                         DeskRepository deskRepository,
                         AgentRepository agentRepository,
                         SpecializationRepository specializationRepository,
                         TimeslotRepository timeslotRepository,
                         StaffingRequirementRepository staffingRequirementRepository,
                         AgentPreferenceRepository agentPreferenceRepository,
                         AgentDayOffRepository agentDayOffRepository,
                         AgentExceptionRepository agentExceptionRepository,
                         AgentDayHoursRepository agentDayHoursRepository,
                         ConstraintWeightsRepository constraintWeightsRepository,
                         AgentEligibilityService agentEligibilityService,
                         ShiftTemplateRepository shiftTemplateRepository,
                         ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository,
                         ShiftLibraryValidationService shiftLibraryValidationService) {
        this.defaultTimeLimit = defaultTimeLimit;
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
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
        this.agentEligibilityService = agentEligibilityService;
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
        this.shiftLibraryValidationService = shiftLibraryValidationService;
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

        // Filter agents: active, non-schedulable jobTitle excluded, primary specialization required
        List<Agent> eligibleAgents = filterEligible(allAgents, tenantId, agentEligibilityService);

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

        // Load per-day contracted hours for this desk (D-09; MDL-02 resolution authority)
        List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // D-05: a weekday the sheet marks as worked un-blocks a stale BambooHR field-4517
        // MANDATORY row for that weekday. Runs against the persisted rows ONLY — before any
        // recurring fact from the spreadsheet has been added below — so a spreadsheet-sourced
        // recurring MANDATORY fact can never be mistaken for a BambooHR one.
        int persistedBeforeUnblock = allDaysOff.size();
        unblockSheetWorkedDays(allDaysOff, agentDayHours);
        int unblockedCount = persistedBeforeUnblock - allDaysOff.size();

        // Materialise the recurring MANDATORY/PTO labels captured by the desk-assignment upload as
        // AgentDayOff facts, so they flow into EVERYTHING that consults days off — most importantly
        // the "Agent day off" HARD constraint, which joins AgentDayOff problem facts directly
        // (ScheduleConstraintProvider.agentDayOff) rather than any lookup map.
        //
        // Added to allDaysOff here, before buildAgentDaysOffMap and before
        // schedule.setAgentDaysOff, so the map, the pre-solve validation and the constraint all
        // see one consistent set. Populating only the map left the hard constraint blind and an
        // agent marked MANDATORY on the spreadsheet was still scheduled (UAT 2026-08-12).
        //
        // D-09: before joining allDaysOff, the recurring facts pass through window arbitration —
        // BambooHR's dated PTO governs every date inside its synced window entirely, so a
        // recurring weekly PTO label asserts nothing there; the persisted BambooHR PTO rows
        // loaded above already supply whatever blocks. Outside the window the recurring pattern
        // stands. Same window bounds BambooRefreshService itself syncs against, so the solver and
        // the refresh never disagree about what "inside the window" means. D-10: nothing here is
        // persisted — both passes run on the in-memory list and are re-derived on every solve.
        LocalDate bambooWindowFrom = LocalDate.now().minusWeeks(bambooLookbackWeeks);
        LocalDate bambooWindowTo = LocalDate.now().plusWeeks(bambooLookaheadWeeks);
        List<AgentDayOff> recurringDaysOff = buildRecurringDaysOff(tenantId, agentDayHours,
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
        int recurringBeforeArbitration = recurringDaysOff.size();
        List<AgentDayOff> arbitratedRecurringDaysOff =
                arbitratePtoAgainstBambooWindow(recurringDaysOff, bambooWindowFrom, bambooWindowTo);
        int suppressedPtoCount = recurringBeforeArbitration - arbitratedRecurringDaysOff.size();

        // One problem fact per (agent, date): where BambooHR and the sheet agree an agent is off,
        // the persisted row already blocks the date and the synthesized recurring fact would be a
        // second match for the same HARD constraint, inflating the reported violation count and
        // hard-score magnitude. Runs last, so it dedupes only facts that survived both passes.
        List<AgentDayOff> dedupedRecurringDaysOff =
                dedupeAgainstPersisted(arbitratedRecurringDaysOff, allDaysOff);
        int duplicateRecurringCount = arbitratedRecurringDaysOff.size() - dedupedRecurringDaysOff.size();

        // Counts only at INFO — no agent name, no email, no date list (T-11-11). Per-agent detail
        // is not logged at all here; WorkingDaysParser's DEBUG-only convention for raw values has
        // no per-agent equivalent to emit since this arbitration only ever produces counts.
        log.info("PTO/pattern arbitration for desk {} — suppressed {} recurring PTO fact(s) inside "
                        + "the BambooHR window, un-blocked {} MANDATORY row(s) for sheet-worked weekdays, "
                        + "dropped {} recurring fact(s) duplicating an already-blocked date",
                deskId, suppressedPtoCount, unblockedCount, duplicateRecurringCount);

        allDaysOff.addAll(dedupedRecurringDaysOff);

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

        // 5. Build lookup map for days off (needed for preference resolution and later).
        // Only APPROVED PTO rows block scheduling; MANDATORY rows always block regardless of status.
        Map<UUID, Set<LocalDate>> agentDaysOffMap = buildAgentDaysOffMap(allDaysOff);


        // 6. Resolve preferences: weekly overrides standing per agent-day (spec §5.8)
        // Done before validation so break alignment check uses effective preferences.
        // Preferences on PTO days are excluded so they don't affect solver scoring.
        List<AgentPreference> resolvedPreferences = resolvePreferences(allPreferences, schedule, agentDaysOffMap);

        // 7. Run pre-solve validation (12 checks from spec §7.11)
        runPreSolveValidation(schedule, allAgents, timeslots, staffingRequirements,
                eligibleAgents, allDaysOff, exceptions, agentDayHours, resolvedPreferences);

        // 8. Build lookup map for exceptions
        Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
        for (AgentException ex : exceptions) {
            agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
                    .put(ex.getDate(), ex.getContractedHoursOverride());
        }

        // 8b. Build lookup map for per-day contracted hours (D-09; mirrors agentExceptionMap build)
        Map<UUID, Map<DayOfWeek, BigDecimal>> agentDayHoursMap = new HashMap<>();
        for (AgentDayHours h : agentDayHours) {
            agentDayHoursMap.computeIfAbsent(h.getAgent().getId(), k -> new HashMap<>())
                    .put(h.getDayOfWeek(), h.getHours());
        }

        // 9. Pre-compute AgentDayConfig problem facts (exception-aware effective hours)
        List<AgentDayConfig> agentDayConfigs = computeAgentDayConfigs(
                eligibleAgents, schedule, agentDaysOffMap, agentExceptionMap, agentDayHoursMap);

        // 9b. Compute capacity warnings (demand vs supply)
        computeCapacityWarnings(schedule, staffingRequirements, agentDayConfigs);

        // 9c. SHIFT-mode-only: the desk's live (template,band) pairs and one AgentShiftAssignment
        // row per working agent-day (D-05). A SLOT-mode desk gets both empty, keeping a
        // slot-mode solve structurally identical to today's — no new AgentShiftAssignment row,
        // nothing for shiftEnvelopeCompliance to join against.
        //
        // CR-01 gap closure: this is a coarse, desk-level pre-filter against the SCHEDULE'S
        // period (never LocalDate.now() — a schedule period can lie entirely in the future or
        // past relative to solve time), keeping templates with ANY overlap with the period so a
        // template effective only for part of a straddling period is still loaded. The precise,
        // per-agent-day enforcement (a template must be effective on THIS row's specific date,
        // not merely "sometime in the period") lives in
        // AgentShiftAssignment#getEligibleShiftBandPairs(), which every row — regardless of its
        // individual date — filters against the SAME ShiftTemplate#isEffectiveOn(LocalDate)
        // predicate this coarse filter also calls, so the two can never disagree about what
        // "effective" means.
        List<ShiftTemplate> liveShiftTemplates = filterLiveShiftTemplates(desk.getSchedulingMode(),
                shiftTemplateRepository.findByTenantIdAndDeskId(tenantId, deskId),
                schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByShiftTemplateId = liveShiftTemplates.isEmpty()
                ? Map.of()
                : shiftTemplateBreakBandRepository
                        .findByTenantIdAndShiftTemplateIdInOrderByOffsetMinutesAsc(tenantId,
                                liveShiftTemplates.stream().map(ShiftTemplate::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(b -> b.getShiftTemplate().getId()));
        List<ShiftBandPair> shiftBandPairs = buildShiftBandPairs(
                desk.getSchedulingMode(), liveShiftTemplates, bandsByShiftTemplateId);
        Map<UUID, Agent> eligibleAgentsById = eligibleAgents.stream()
                .collect(Collectors.toMap(Agent::getId, a -> a, (a, b) -> a));
        List<AgentShiftAssignment> shiftAssignments = buildShiftAssignments(desk.getSchedulingMode(),
                tenantId, deskId, schedule.getId(), eligibleAgentsById, agentDayConfigs, shiftBandPairs);

        // 10. Detach Hibernate proxy collections into plain ArrayList/HashSet
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

        // 10d. Guarantee a seat on every timeslot so the "Minimum staffing" constraint can
        // actually bite. Deliberately AFTER computeTimeslotDemandConfigs above, so these
        // seats are never counted as demand — they exist to be fillable, not to be required.
        //
        // G-15-10 (plan 15-09): on a SHIFT desk this is no longer mode-blind. The per-date
        // working agent-day count is derived here from shiftAssignments (built at step 9c) by
        // grouping on AgentShiftAssignment::getDate and counting — no repository call, no
        // reordering of any existing step.
        Map<LocalDate, Integer> workingAgentDaysByDate = shiftAssignments.stream()
                .collect(Collectors.groupingBy(AgentShiftAssignment::getDate,
                        Collectors.summingInt(sa -> 1)));
        List<AgentAssignment> minStaffingSeats = expandMinimumStaffingSeats(
                tenantId, deskId, schedule.getId(), timeslots, assignments,
                staffingRequirements, specializations,
                desk.getSchedulingMode(), shiftBandPairs, workingAgentDaysByDate);
        if (!minStaffingSeats.isEmpty()) {
            assignments.addAll(minStaffingSeats);
            log.debug("Minimum-staffing seats: {} added for timeslots with no demand-derived seat",
                    minStaffingSeats.size());
        }

        // 10e. (Phase 15 plan 15-11, G-15-10 D1 half) The shift-mode seat-supply gate. Must run
        // HERE, after seat construction (step 10d) — not inside runPreSolveValidation (step 7),
        // which runs before any seat exists. See the method's own javadoc for why moving it back
        // there would silently defeat it.
        requireShiftEnvelopeSeatSupply(desk.getSchedulingMode(), shiftAssignments, shiftBandPairs,
                timeslots, assignments, schedule.getOverallocationHardLimitPct(), schedule.getWarnings());

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
        schedule.setShiftBandPairs(new ArrayList<>(shiftBandPairs));
        schedule.setShiftAssignments(new ArrayList<>(shiftAssignments));
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
                        // Capture the first time the solution becomes feasible (hard score reaches 0)
                        if (bestSolution.getFeasibleAt() == null
                                && bestSolution.getScore() != null
                                && bestSolution.getScore().hardScore() >= 0) {
                            bestSolution.setFeasibleAt(OffsetDateTime.now());
                            log.info("Solution first became feasible — schedule={}, score={}",
                                    bestSolution.getId(), bestSolution.getScore());
                        }
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

        Duration solveTime;
        if (request.solveTimeSeconds() != null && request.solveTimeSeconds() > 0) {
            solveTime = Duration.ofSeconds(request.solveTimeSeconds());
            log.info("Custom solve time: {}", solveTime);
        } else {
            solveTime = defaultTimeLimit;
            log.info("Default solve time from solver.time-limit: {}", solveTime);
        }
        long totalSeconds = solveTime.toSeconds();
        long unimprovedSeconds = Math.max(30, totalSeconds * 3 / 10); // 30% of total, min 30s
        solveBuilder = solveBuilder.withConfigOverride(
                new SolverConfigOverride<Schedule>()
                        .withTerminationConfig(new TerminationConfig()
                                .withSpentLimit(solveTime)
                                .withUnimprovedSpentLimit(Duration.ofSeconds(unimprovedSeconds))));

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
        s.setSchedulingMode(desk.getSchedulingMode());

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
                                                     Schedule schedule,
                                                     Map<UUID, Set<LocalDate>> agentDaysOffMap) {
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

            Set<LocalDate> daysOff = agentDaysOffMap.getOrDefault(agentId, Set.of());

            for (LocalDate d = schedule.getPeriodStartDate();
                 !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {

                // Skip PTO / day-off dates — preference stays in DB but is excluded from solver scoring
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
            Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap,
            Map<UUID, Map<DayOfWeek, BigDecimal>> agentDayHoursMap) {

        List<AgentDayConfig> configs = new ArrayList<>();

        for (Agent agent : eligibleAgents) {
            UUID agentId = agent.getId();
            Map<LocalDate, BigDecimal> exMap = agentExceptionMap.getOrDefault(agentId, Map.of());
            Set<LocalDate> dayOffSet = agentDaysOffMap.getOrDefault(agentId, Set.of());
            Map<DayOfWeek, BigDecimal> dayHoursMap = agentDayHoursMap.getOrDefault(agentId, Map.of());

            for (LocalDate d = schedule.getPeriodStartDate();
                 !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {

                if (dayOffSet.contains(d)) continue;

                BigDecimal effectiveHours = resolveEffectiveHours(exMap, dayHoursMap, d, schedule.getDefaultContractedHoursPerDay());
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

    // --- Shift envelope population (Phase 15, D-04/D-05) ---

    /**
     * The desk's shift templates that overlap the schedule's period at all (CR-01 gap closure) —
     * a coarse, desk-level pre-filter, not the final per-agent-day eligibility check (that lives
     * in {@code AgentShiftAssignment#getEligibleShiftBandPairs()}). Deliberately checks OVERLAP
     * with {@code [periodStartDate, periodEndDate]}, not full containment: a template whose
     * {@code effectiveFrom} falls in the middle of the period must still be included so it
     * remains selectable for the days it IS effective on, even though it must be excluded for the
     * days before {@code effectiveFrom}. Never reads {@code LocalDate.now()} — a schedule period
     * can lie entirely in the future or the past relative to when the solve actually runs, so
     * "today" is never the right reference date for a solve-time filter (only for a live UI
     * badge). Mode-gated exactly like {@link #buildShiftBandPairs}: a SLOT-mode desk gets an
     * empty list.
     *
     * <p>Package-private static and pure, mirroring {@link #buildShiftBandPairs}'s precedent so
     * it is directly unit-testable without a repository or Spring context.
     */
    static List<ShiftTemplate> filterLiveShiftTemplates(SchedulingMode schedulingMode,
            List<ShiftTemplate> allTemplates, LocalDate periodStartDate, LocalDate periodEndDate) {
        if (schedulingMode != SchedulingMode.SHIFT) {
            return List.of();
        }
        return allTemplates.stream()
                .filter(t -> !t.getEffectiveFrom().isAfter(periodEndDate)
                        && (t.getEffectiveTo() == null || !t.getEffectiveTo().isBefore(periodStartDate)))
                .toList();
    }

    /**
     * The desk's live {@code (template, band)} pairs, sorted by template name, then
     * {@code effectiveFrom}, then band offset ascending (required for XCUT-04's seeded benchmark
     * to reproduce across runs — this plan's edge_accounting names the ordering explicitly). A
     * template with zero bands contributes exactly one pair with a {@code null} band (P-02:
     * "zero bands = no break"). Mode-gated: a SLOT-mode desk gets an empty list regardless of
     * what {@code templates}/{@code bandsByTemplateId} carry, so the caller never needs its own
     * mode check.
     *
     * <p>Package-private static and pure (no repository access) so it is directly unit-testable,
     * mirroring {@code resolveEffectiveHours}'s precedent.
     */
    static List<ShiftBandPair> buildShiftBandPairs(SchedulingMode schedulingMode,
            List<ShiftTemplate> templates, Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId) {
        if (schedulingMode != SchedulingMode.SHIFT) {
            return List.of();
        }
        List<ShiftBandPair> pairs = new ArrayList<>();
        for (ShiftTemplate template : templates) {
            List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
            if (bands.isEmpty()) {
                pairs.add(new ShiftBandPair(template, null));
            } else {
                for (ShiftTemplateBreakBand band : bands) {
                    pairs.add(new ShiftBandPair(template, band));
                }
            }
        }
        return pairs.stream()
                .sorted(Comparator
                        .comparing((ShiftBandPair p) -> p.template().getName())
                        .thenComparing(p -> p.template().getEffectiveFrom())
                        .thenComparingInt(p -> p.band() == null ? Integer.MIN_VALUE : p.band().getOffsetMinutes()))
                .toList();
    }

    /**
     * One {@link AgentShiftAssignment} row per {@code agentDayConfigs} entry whose
     * {@code effectiveHours > 0} (D-05) — the same fact
     * {@link AgentShiftAssignment#getEligibleShiftBandPairs()} filters by, so entity creation and
     * the value-range filter can never disagree. Every row shares the SAME
     * {@code deskShiftBandPairs} list instance (not a copy per row) — required so the value
     * range's pre-sorted order is never re-derived per entity. Mode-gated the same way as
     * {@link #buildShiftBandPairs} — a SLOT-mode desk gets zero rows.
     *
     * <p>Package-private static and pure, mirroring {@link #buildShiftBandPairs}.
     */
    static List<AgentShiftAssignment> buildShiftAssignments(SchedulingMode schedulingMode,
            long tenantId, UUID deskId, UUID scheduleId, Map<UUID, Agent> agentById,
            List<AgentDayConfig> agentDayConfigs, List<ShiftBandPair> deskShiftBandPairs) {
        if (schedulingMode != SchedulingMode.SHIFT) {
            return List.of();
        }
        List<AgentShiftAssignment> assignments = new ArrayList<>();
        for (AgentDayConfig config : agentDayConfigs) {
            if (config.effectiveHours() == null || config.effectiveHours().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // defensive — computeAgentDayConfigs already filters this, D-05
            }
            Agent agent = agentById.get(config.agentId());
            if (agent == null) {
                continue; // defensive — agentDayConfigs is derived from the same eligible-agent set
            }
            AgentShiftAssignment sa = new AgentShiftAssignment();
            sa.setId(UUID.randomUUID());
            sa.setTenantId(tenantId);
            sa.setDeskId(deskId);
            sa.setScheduleId(scheduleId);
            sa.setAgent(agent);
            sa.setDate(config.date());
            sa.setDayConfig(config);
            sa.setDeskShiftBandPairs(deskShiftBandPairs);
            assignments.add(sa);
        }
        return assignments;
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
                                       List<AgentDayHours> agentDayHours,
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
        Map<UUID, Map<DayOfWeek, BigDecimal>> agentDayHoursMap = new HashMap<>();
        for (AgentDayHours h : agentDayHours) {
            agentDayHoursMap.computeIfAbsent(h.getAgent().getId(), k -> new HashMap<>())
                    .put(h.getDayOfWeek(), h.getHours());
        }

        // 4. Every active agent must have a primary specialization
        for (Agent agent : allAgents) {
            if (!agent.isActive()) continue;
            if (agent.getPrimarySpecialization() == null) {
                errors.add(new ErrorDetail("agent.specializations",
                        "Agent " + agent.getName()
                                + " must have a primary specialization assigned",
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
            Map<DayOfWeek, BigDecimal> dayHoursMap = agentDayHoursMap.getOrDefault(agentId, Map.of());

            for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                if (dayOffSet.contains(d)) continue;
                BigDecimal effectiveHours = resolveEffectiveHours(exMap, dayHoursMap, d, schedule.getDefaultContractedHoursPerDay());
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
            Map<DayOfWeek, BigDecimal> dayHoursMap = agentDayHoursMap.getOrDefault(agentId, Map.of());

            for (LocalDate d = schedule.getPeriodStartDate(); !d.isAfter(schedule.getPeriodEndDate()); d = d.plusDays(1)) {
                if (dayOffSet.contains(d)) continue;
                BigDecimal effectiveHours = resolveEffectiveHours(exMap, dayHoursMap, d, schedule.getDefaultContractedHoursPerDay());
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

        // 13. (Phase 15, ENVL-08/D-03/P-29) Band capacity shortfall — SHIFT-mode only.
        appendBandCapacityErrors(schedule.getSchedulingMode(), schedule.getDeskId(),
                shiftLibraryValidationService, errors);

        if (!errors.isEmpty()) {
            throw new PreSolveValidationException(
                    "Pre-solve validation failed with " + errors.size() + " issue(s)", errors);
        }
    }

    /**
     * (Phase 15, ENVL-08/D-03/P-29) Appends one {@link ErrorDetail} per band-capacity shortfall,
     * SHIFT-mode only — a no-op on SLOT-mode desks and on SHIFT desks with no shortfall. Reuses
     * the SAME computation {@link ShiftLibraryValidationService#validate} already exposes to the
     * shift-library report (built in plan 15-01 Task 3) rather than re-deriving it here — a
     * second implementation is the audit NEW-1 shape this project has been burned by twice. The
     * message an operator read in the save-time advisory and the refusal that stops their solve
     * are therefore character-identical, one computation with two callers (D-08's discipline
     * extended to a third caller).
     *
     * <p>Package-private static, mirroring {@link #buildShiftBandPairs}/{@link
     * #buildShiftAssignments}'s precedent — directly unit-testable with a mocked {@link
     * ShiftLibraryValidationService} and no Spring context, despite depending on an injected
     * collaborator rather than being pure.
     */
    static void appendBandCapacityErrors(SchedulingMode schedulingMode, UUID deskId,
            ShiftLibraryValidationService shiftLibraryValidationService, List<ErrorDetail> errors) {
        if (schedulingMode != SchedulingMode.SHIFT) {
            return;
        }
        for (CapacityAdvisory advisory : shiftLibraryValidationService.validate(deskId).capacityAdvisories()) {
            errors.add(new ErrorDetail("bandCapacity", advisory.message(), advisory.templateName()));
        }
    }

    /**
     * (Phase 15 plan 15-11, G-15-10 D1 half) The shift-mode seat-supply gate — makes in-envelope
     * seat supply a CHECKED PRECONDITION of every shift-mode solve, rather than something the
     * model merely hopes for. A no-op entirely when {@code schedulingMode} is not {@code SHIFT}
     * (Test 5) or when there are no shift-assignment rows to check.
     *
     * <p><strong>This cannot live inside {@link #runPreSolveValidation}</strong> — that runs at
     * step 7, before any seat exists. This gate MUST be called after step 10d, once {@code
     * assignments} already includes the minimum-staffing top-up, because it counts the SEATS the
     * solver is actually about to receive rather than re-deriving them from staffing
     * requirements — so the two can never disagree. Do not "tidy" this call back into step 7;
     * that would silently defeat it, since {@code assignments} does not exist there yet.
     *
     * <p>Two checks, accumulated together and thrown once (mirrors {@link
     * #runPreSolveValidation}'s accumulate-then-throw shape):
     * <ol>
     *   <li><strong>Library-covered supply vs. contracted demand, per date.</strong> Supply is
     *       the count of {@code assignments} seats sitting at a timeslot at least one live
     *       {@link ShiftBandPair} covers ({@link ShiftBandPair#covers}, never re-derived
     *       envelope arithmetic) — counting the seats the solver actually receives, including
     *       plan 15-09's suppression and top-up, so this can never disagree with what the solver
     *       sees. Demand is the sum of {@link AgentDayConfig#expectedWorkSlots()} across that
     *       date's {@link AgentShiftAssignment} rows (the same computation Task 1 moved onto
     *       {@code AgentDayConfig}, so this gate and the hard contracted-hours constraints can
     *       never disagree either). Refusing when demand exceeds supply is a genuine necessary
     *       condition for a zero-hard solve: D-04's value range forces every agent-day to occupy
     *       exactly its expected work slots, every one of those seats inside its own pair's
     *       coverage, and every covered slot is covered by SOME live pair — so a desk that fails
     *       this check cannot reach {@code 0hard} no matter how long the solver runs. The check
     *       deliberately uses coverage by ANY live pair rather than the agent's eventual pair
     *       (still free at this point) — the looser test is the only sound one, and it errs
     *       toward permitting (Test 3).</li>
     *   <li><strong>Empty value range, per row.</strong> An {@link AgentShiftAssignment} whose
     *       {@link AgentShiftAssignment#getEligibleShiftBandPairs()} comes back empty on its own
     *       date degrades silently today to an unassigned shift whose every seat is then
     *       penalised at solve time. Refused by name instead (Test 4), distinguishing — per
     *       date, not per row — the case where NO live pair reaches the date at all (a wholly
     *       retired or wholly upcoming library, Test 6) from an ordinary hours/library
     *       mismatch, so a retired library reads as one message rather than as an hours mismatch
     *       repeated for every agent rostered that day.</li>
     * </ol>
     *
     * <p>Finally, a non-blocking advisory (Test 7): for every date with shift-assignment rows,
     * once the whole desk has passed both checks above, the covered timeslot carrying the fewest
     * seats is recorded in {@code warnings} — the schedule's own warnings collection, appended
     * to rather than replaced, since {@link #computeCapacityWarnings} already wrote to it earlier
     * in the same solve. This points at the pinch point without pretending to a precision the
     * aggregate check above does not have, and it must never throw.
     */
    static void requireShiftEnvelopeSeatSupply(
            SchedulingMode schedulingMode,
            List<AgentShiftAssignment> shiftAssignments,
            List<ShiftBandPair> shiftBandPairs,
            List<Timeslot> timeslots,
            List<AgentAssignment> assignments,
            int overallocationHardLimitPct,
            List<String> warnings) {

        if (schedulingMode != SchedulingMode.SHIFT
                || shiftAssignments == null || shiftAssignments.isEmpty()) {
            return;
        }
        List<ShiftBandPair> pairs = shiftBandPairs == null ? List.of() : shiftBandPairs;

        Map<LocalDate, List<AgentShiftAssignment>> rowsByDate = shiftAssignments.stream()
                .collect(Collectors.groupingBy(AgentShiftAssignment::getDate,
                        LinkedHashMap::new, Collectors.toList()));
        Map<LocalDate, List<Timeslot>> timeslotsByDate = (timeslots == null ? List.<Timeslot>of() : timeslots)
                .stream()
                .collect(Collectors.groupingBy(Timeslot::getDate, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Long> seatsByTimeslotId = (assignments == null ? List.<AgentAssignment>of() : assignments)
                .stream()
                .filter(a -> a.getTimeslot() != null)
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getId(), Collectors.counting()));

        List<ErrorDetail> errors = new ArrayList<>();

        for (Map.Entry<LocalDate, List<AgentShiftAssignment>> entry : rowsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<AgentShiftAssignment> rows = entry.getValue();

            List<Timeslot> coveredTimeslots = timeslotsByDate.getOrDefault(date, List.of()).stream()
                    .filter(ts -> pairs.stream().anyMatch(p -> p.covers(ts)))
                    .toList();
            int librarySupplySlots = coveredTimeslots.stream()
                    .mapToInt(ts -> seatsByTimeslotId.getOrDefault(ts.getId(), 0L).intValue())
                    .sum();
            int contractedSlots = rows.stream()
                    .mapToInt(r -> r.getDayConfig().expectedWorkSlots())
                    .sum();

            if (contractedSlots > librarySupplySlots) {
                int incrementMinutes = rows.get(0).getDayConfig().incrementMinutes();
                int shortfallSlots = contractedSlots - librarySupplySlots;
                errors.add(new ErrorDetail("shiftLibrary",
                        "On " + date + ", rostered agent-days need " + contractedSlots
                                + " slot(s) (" + slotsToHours(contractedSlots, incrementMinutes)
                                + "h) inside the shift library's live envelopes, but the library "
                                + "only reaches " + librarySupplySlots + " slot(s) ("
                                + slotsToHours(librarySupplySlots, incrementMinutes)
                                + "h) there — a shortfall of " + shortfallSlots + " slot(s) ("
                                + slotsToHours(shortfallSlots, incrementMinutes) + "h). On a "
                                + "shift-scheduled desk an agent works exactly their assigned "
                                + "shift, so this cannot be resolved by solving for longer. To "
                                + "fix it: raise the desk's over-allocation limit (currently "
                                + overallocationHardLimitPct + "%), correct the demand forecast "
                                + "for the hours the library covers, reduce rostered hours for "
                                + date + ", or change the library so its envelopes sit over "
                                + "demand-bearing hours.",
                        date.toString()));
            }

            List<AgentShiftAssignment> unassignable = rows.stream()
                    .filter(r -> r.getEligibleShiftBandPairs().isEmpty())
                    .toList();
            if (!unassignable.isEmpty()) {
                boolean anyPairLiveOnDate = pairs.stream()
                        .anyMatch(p -> p.template().isEffectiveOn(date));
                if (!anyPairLiveOnDate) {
                    errors.add(new ErrorDetail("shiftLibrary",
                            "No live shift template reaches " + date + " — the shift library is "
                                    + "entirely upcoming or retired for this date, so none of "
                                    + "the " + unassignable.size() + " agent(s) rostered that "
                                    + "day can be scheduled.",
                            date.toString()));
                } else {
                    for (AgentShiftAssignment row : unassignable) {
                        errors.add(new ErrorDetail("shiftLibrary",
                                "Agent " + row.getAgent().getName() + " is contracted "
                                        + row.getDayConfig().effectiveHours() + "h on " + date
                                        + ", which matches no live shift template's net hours. "
                                        + "On a shift-scheduled desk an agent works exactly "
                                        + "their assigned shift, so this agent cannot be "
                                        + "scheduled that day without either correcting their "
                                        + "contracted hours or adding a template whose net "
                                        + "hours equal them.",
                                row.getAgent().getId().toString()));
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new PreSolveValidationException(
                    "Shift envelope seat-supply check failed with " + errors.size() + " issue(s)",
                    errors);
        }

        // Non-blocking advisory: the covered timeslot with the fewest seats, per rostered date.
        for (LocalDate date : rowsByDate.keySet()) {
            List<Timeslot> coveredTimeslots = timeslotsByDate.getOrDefault(date, List.of()).stream()
                    .filter(ts -> pairs.stream().anyMatch(p -> p.covers(ts)))
                    .toList();
            coveredTimeslots.stream()
                    .min(Comparator.comparingLong(ts -> seatsByTimeslotId.getOrDefault(ts.getId(), 0L)))
                    .ifPresent(tightest -> {
                        long seatCount = seatsByTimeslotId.getOrDefault(tightest.getId(), 0L);
                        warnings.add("Shift library seat supply on " + date + " is tightest at "
                                + tightest.getStartTime() + "-" + tightest.getEndTime() + " with "
                                + seatCount + " seat(s) available.");
                    });
        }
    }

    private static BigDecimal slotsToHours(int slots, int incrementMinutes) {
        return BigDecimal.valueOf(slots)
                .multiply(BigDecimal.valueOf(incrementMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * Resolves effective contracted hours for an agent+date (D-03/D-04 precedence):
     *   1. AgentException override for the exact date (highest precedence)
     *   2. per-day value for that weekday from the agent_day_hours map (including 0.00 —
     *      a present 0.00 row means that weekday is not scheduled)
     *   3. schedule default (fallback when the weekday is absent from the per-day map)
     *
     * The agent's scalar contractedHoursPerDay is NOT consulted here — per-day rows are
     * the sole per-agent authority (MDL-02). Precedence checks use containsKey, never a
     * null-check: a present AgentException row is always a real value (nullable=false),
     * and a present per-day map entry is always a real value for the same reason.
     */
    static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap,
                                             Map<DayOfWeek, BigDecimal> dayHoursMap,
                                             LocalDate date,
                                             BigDecimal scheduleDefaultHours) {
        if (exceptionMap.containsKey(date)) {
            return exceptionMap.get(date);
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dayHoursMap.containsKey(dow)) {
            return dayHoursMap.get(dow);
        }
        return scheduleDefaultHours;
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
     * Creates the seats the "Minimum staffing" constraint needs in order to have any effect.
     *
     * <p>{@code FteUploadService} skips a demand cell of zero ({@code if (fteValue <= 0)
     * continue}), so no {@link StaffingRequirement} is persisted for that hour and
     * {@link #expandAssignments} creates no seat for it. {@link #expandOverflowAssignments}
     * iterates the same requirements and computes {@code (0 * pct + 99) / 100 == 0}, so it
     * adds nothing either. The timeslot still exists — {@code generateTimeslots} covers the
     * whole operating window — but it carries no planning entity at all.
     *
     * <p>That is why the constraint alone could not deliver the floor. It groups
     * {@link AgentAssignment} by timeslot, and a groupBy emits no group for a key absent
     * from the stream, so it stayed silent on exactly the hours it was written for — at any
     * weight, hard included. Creating the seat fixes both halves: the timeslot now appears
     * in the grouping, and the solver has a variable it can actually assign an agent to.
     *
     * <p><strong>Mode split (G-15-10, plan 15-09).</strong> On a SLOT desk, or a SHIFT desk
     * whose live pair list is empty, this behaves exactly as before: every timeslot short of
     * {@link ScheduleConstraintProvider#MIN_AGENTS_PER_TIMESLOT} is topped up, unconditionally
     * — the same code path a SLOT desk always took, so a SLOT desk cannot drift. On a SHIFT
     * desk with a live library, coverage intent is expressed through the library
     * (operator ruling OR-1), so the top-up branches per timeslot:
     * <ul>
     *   <li>already holding a seat (a {@link TimeslotDemandConfig} row governs it) — untouched;
     *   <li>holding no seat and covered by NO {@link ShiftBandPair#covers} in the live list —
     *       an hour the operator's library does not reach is an hour they decided not to
     *       staff, so it gets no seat; penalising a timeslot the solver cannot assign into
     *       would just make the schedule permanently infeasible;
     *   <li>holding no seat and covered by at least one pair — gets
     *       {@code max(MIN_AGENTS_PER_TIMESLOT, workingAgentDaysOn(date))} seats. The D-04
     *       value range admits only pairs whose net hours exactly equal the agent-day's
     *       effective hours, so an agent must occupy every non-break slot of their envelope;
     *       a legal slot with fewer seats than the agents the library can put there is an
     *       obligation the solver cannot discharge without a hard violation. These seats carry
     *       no {@code TimeslotDemandConfig} row, so they are free when left empty and free
     *       when filled — the over-allocation ceiling is untouched.
     * </ul>
     *
     * <p>They are appended AFTER {@code computeTimeslotDemandConfigs} has run, so they never
     * inflate {@link TimeslotDemandConfig} — a filler seat is fillable, not required, and the
     * under/over-allocation constraints continue to judge the hour on its real forecast.
     *
     * <p>Each seat must carry a real {@link Specialization}: {@code specializationMatch}
     * dereferences {@code getRequiredSpecialization().getId()} with no null guard, so a null
     * would throw during scoring rather than merely go unmatched. Seats cycle the desk's
     * specializations in ascending id order, starting at the desk's predominant specialization
     * by total required FTEs — the one demand is actually expressed in, so agents rostered on
     * the desk are the most likely to hold it — so seat index 0 at any timeslot is always the
     * predominant specialization, byte-identical to what a single-seat top-up always produced.
     * Ties break on id so a given schedule always expands identically.
     *
     * @return the extra seats to append; empty when every timeslot already has enough, or
     *         when the desk has no specialization to attribute a seat to.
     */
    static List<AgentAssignment> expandMinimumStaffingSeats(
            long tenantId, UUID deskId, UUID scheduleId,
            List<Timeslot> timeslots,
            List<AgentAssignment> existingAssignments,
            List<StaffingRequirement> staffingRequirements,
            List<Specialization> specializations,
            SchedulingMode schedulingMode,
            List<ShiftBandPair> shiftBandPairs,
            Map<LocalDate, Integer> workingAgentDaysByDate) {

        if (timeslots == null || timeslots.isEmpty()) {
            return List.of();
        }
        Specialization fillerSpec = predominantSpecialization(staffingRequirements, specializations);
        if (fillerSpec == null) {
            // No specialization exists on the desk, so any seat created here would NPE in
            // specializationMatch. Nothing safe to add.
            return List.of();
        }

        Map<UUID, Integer> seatsPerTimeslot = new HashMap<>();
        for (AgentAssignment a : existingAssignments) {
            if (a.getTimeslot() != null) {
                seatsPerTimeslot.merge(a.getTimeslot().getId(), 1, Integer::sum);
            }
        }

        boolean slotLike = schedulingMode != SchedulingMode.SHIFT
                || shiftBandPairs == null || shiftBandPairs.isEmpty();
        List<Specialization> orderedSpecs = specializationCycleFrom(specializations, fillerSpec);

        List<AgentAssignment> extra = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            int have = seatsPerTimeslot.getOrDefault(ts.getId(), 0);

            if (slotLike) {
                for (int i = have; i < ScheduleConstraintProvider.MIN_AGENTS_PER_TIMESLOT; i++) {
                    extra.add(fillerSeat(tenantId, deskId, scheduleId, ts, fillerSpec));
                }
                continue;
            }

            if (have > 0) {
                continue; // a TimeslotDemandConfig row already governs this hour
            }
            boolean covered = shiftBandPairs.stream().anyMatch(pair -> pair.covers(ts));
            if (!covered) {
                continue; // OR-1: the library does not reach this hour -- no seat
            }

            int target = Math.max(ScheduleConstraintProvider.MIN_AGENTS_PER_TIMESLOT,
                    workingAgentDaysByDate == null
                            ? 0
                            : workingAgentDaysByDate.getOrDefault(ts.getDate(), 0));
            for (int i = 0; i < target; i++) {
                Specialization spec = orderedSpecs.get(i % orderedSpecs.size());
                extra.add(fillerSeat(tenantId, deskId, scheduleId, ts, spec));
            }
        }
        return extra;
    }

    private static AgentAssignment fillerSeat(long tenantId, UUID deskId, UUID scheduleId,
            Timeslot ts, Specialization spec) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setDeskId(deskId);
        a.setScheduleId(scheduleId);
        a.setTimeslot(ts);
        a.setRequiredSpecialization(spec);
        // agent stays null — the solver fills it, driven by "Minimum staffing"
        return a;
    }

    /**
     * The desk's specializations in ascending id order, rotated so {@code start} (the
     * predominant specialization) is first — so cycling through this list assigns seat index 0
     * the predominant specialization, exactly matching the single-seat top-up's prior output.
     */
    private static List<Specialization> specializationCycleFrom(
            List<Specialization> specializations, Specialization start) {

        List<Specialization> sorted = specializations == null
                ? List.of()
                : specializations.stream()
                        .filter(s -> s.getId() != null)
                        .sorted(Comparator.comparing(s -> s.getId().toString()))
                        .toList();
        if (sorted.isEmpty()) {
            return List.of(start);
        }
        int startIndex = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getId().equals(start.getId())) {
                startIndex = i;
                break;
            }
        }
        List<Specialization> rotated = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            rotated.add(sorted.get((startIndex + i) % sorted.size()));
        }
        return rotated;
    }

    /**
     * The specialization carrying the most demand on this desk, used to attribute
     * minimum-staffing seats. Falls back to the lowest-id specialization when there is no
     * demand at all, and to null when the desk has no specializations.
     */
    private static Specialization predominantSpecialization(
            List<StaffingRequirement> staffingRequirements, List<Specialization> specializations) {

        Map<UUID, Integer> ftesBySpec = new HashMap<>();
        Map<UUID, Specialization> byId = new HashMap<>();
        if (staffingRequirements != null) {
            for (StaffingRequirement sr : staffingRequirements) {
                Specialization s = sr.getSpecialization();
                if (s == null) continue;
                byId.putIfAbsent(s.getId(), s);
                ftesBySpec.merge(s.getId(), sr.getRequiredFTEs(), Integer::sum);
            }
        }
        if (!ftesBySpec.isEmpty()) {
            return ftesBySpec.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()
                            .thenComparing(e -> e.getKey().toString()))
                    .map(e -> byId.get(e.getKey()))
                    .findFirst()
                    .orElse(null);
        }
        if (specializations == null || specializations.isEmpty()) {
            return null;
        }
        return specializations.stream()
                .filter(s -> s.getId() != null)
                .min(Comparator.comparing(s -> s.getId().toString()))
                .orElse(null);
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
            int maxAgents = (requiredAgents * overallocationHardLimitPct + 99) / 100;
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
                            .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
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

    // --- Package-private static helpers (extracted for testability) ---

    /**
     * Builds the agentDaysOffMap used by the solver and preference resolver.
     *
     * EVERY day-off row blocks its date, whatever the type or status. PTO is PTO: an agent who
     * has booked a day off is not available, and scheduling them against a request that is merely
     * awaiting a manager's click produces a roster that falls apart on approval.
     *
     * Supersedes D-22, which blocked APPROVED PTO but not REQUESTED (changed 2026-08-12). Status
     * remains meaningful for DISPLAY — the PTO tab labels requested days "(Req)" and the desk
     * agent list shows a pending-PTO count — it simply no longer affects availability.
     *
     * Consequence: an unapproved request now removes capacity immediately.
     */
    static Map<UUID, Set<LocalDate>> buildAgentDaysOffMap(List<AgentDayOff> daysOff) {
        Map<UUID, Set<LocalDate>> map = new HashMap<>();
        for (AgentDayOff d : daysOff) {
            map.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
        }
        return map;
    }

    /**
     * Expands recurring MANDATORY/PTO labels from {@code agent_day_hours} into concrete
     * {@link AgentDayOff} facts, one per matching date in [from, to].
     *
     * These come from the desk-assignment upload spreadsheet, which records a weekly pattern
     * rather than dates. Returning real AgentDayOff instances (rather than only populating a
     * lookup map) is what makes them visible to the "Agent day off" HARD constraint, which joins
     * AgentDayOff problem facts directly.
     *
     * Status is APPROVED so {@link #buildAgentDaysOffMap} treats both types as blocking: MANDATORY
     * always blocks, and PTO written directly on the spreadsheet is an operator assertion, so
     * there is no REQUESTED state to honour the way BambooHR-sourced rows have.
     *
     * The returned objects are NEVER persisted — Schedule.agentDaysOff is @Transient, and these
     * are added only to an in-memory list. Each gets a random id so identity-based equality and
     * any @PlanningId lookup behave.
     */
    static List<AgentDayOff> buildRecurringDaysOff(long tenantId, List<AgentDayHours> agentDayHours,
                                                   LocalDate from, LocalDate to) {
        List<AgentDayOff> facts = new ArrayList<>();
        for (AgentDayHours h : agentDayHours) {
            if (h.getDayOffType() == null) {
                continue; // a normal working day with contracted hours
            }
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != h.getDayOfWeek()) {
                    continue;
                }
                AgentDayOff fact = new AgentDayOff();
                fact.setId(UUID.randomUUID());
                fact.setTenantId(tenantId);
                fact.setAgent(h.getAgent());
                fact.setDate(date);
                fact.setType(h.getDayOffType());
                fact.setStatus(DayOffStatus.APPROVED);
                facts.add(fact);
            }
        }
        return facts;
    }

    /**
     * D-09: BambooHR's dated PTO governs every date inside its synced window entirely — a
     * recurring weekly PTO label from the spreadsheet asserts nothing there, because the
     * persisted BambooHR PTO rows loaded earlier in {@link #startSolve} already supply whatever
     * blocks that date. Outside the window BambooHR has no visibility, so the recurring pattern
     * stands.
     *
     * MANDATORY is deliberately out of scope: MRG-03's precedence rule names PTO only, and a
     * spreadsheet MANDATORY day is an operator assertion about the week that D-05 makes
     * authoritative regardless of BambooHR's window.
     *
     * The window is a closed interval — a date equal to either {@code windowFrom} or
     * {@code windowTo} counts as inside it, so BambooHR governs it (D-09). The input list is not
     * mutated and relative order is preserved, so two solves over the same data produce the same
     * fact sequence.
     */
    static List<AgentDayOff> arbitratePtoAgainstBambooWindow(List<AgentDayOff> recurringFacts,
                                                               LocalDate windowFrom, LocalDate windowTo) {
        List<AgentDayOff> result = new ArrayList<>();
        for (AgentDayOff fact : recurringFacts) {
            if (fact.getType() != DayOffType.PTO) {
                result.add(fact);
                continue;
            }
            boolean insideWindow = !fact.getDate().isBefore(windowFrom) && !fact.getDate().isAfter(windowTo);
            if (!insideWindow) {
                result.add(fact);
            }
        }
        return result;
    }

    /**
     * D-05: a weekday the spreadsheet states as worked un-blocks a stale BambooHR field-4517
     * MANDATORY {@link AgentDayOff} row for that weekday — an operator correcting a wrong
     * BambooHR week via the sheet is no longer silently re-blocked by the row
     * {@code BambooRefreshService.persistRefreshData} generated.
     *
     * Only MANDATORY rows are removable. A persisted PTO row is a dated fact about a specific
     * absence and is never un-blocked by a weekly pattern — MRG-03/D-05's precedence rule is
     * scoped to PTO arbitration alone, not to this un-block pass. An agent with no
     * {@code agent_day_hours} rows at all has an empty worked set, so none of their persisted
     * rows are touched — the sheet has said nothing about their week.
     *
     * "Worked" means the sheet's day-off type is null for that weekday, the same predicate
     * {@link #buildRecurringDaysOff} uses to skip a normal working day — which per the
     * 2026-08-18 operator revision includes an explicit zero-hours cell: zero means no
     * contracted hours, not unavailability.
     *
     * Mutates {@code persistedDaysOff} in place by removing entries; does not read or modify
     * {@code agentDayHours}.
     */
    static void unblockSheetWorkedDays(List<AgentDayOff> persistedDaysOff, List<AgentDayHours> agentDayHours) {
        Map<UUID, Set<DayOfWeek>> workedWeekdaysByAgent = new HashMap<>();
        for (AgentDayHours h : agentDayHours) {
            if (h.getDayOffType() == null) {
                workedWeekdaysByAgent.computeIfAbsent(h.getAgent().getId(), k -> new HashSet<>())
                        .add(h.getDayOfWeek());
            }
        }
        persistedDaysOff.removeIf(d -> d.getType() == DayOffType.MANDATORY
                && workedWeekdaysByAgent.getOrDefault(d.getAgent().getId(), Set.of())
                        .contains(d.getDate().getDayOfWeek()));
    }

    /**
     * Returns the recurring facts that do not duplicate an (agent, date) already blocked by a
     * persisted row, plus at most one fact per (agent, date) from the recurring list itself.
     *
     * The "Agent day off" HARD constraint joins {@link AgentDayOff} problem facts directly
     * (ScheduleConstraintProvider.agentDayOff) rather than through a lookup map, so two facts for
     * one real-world day-off are matched twice: the day is blocked either way, but that agent-date
     * contributes roughly double to the constraint's match count and to the hard-score magnitude
     * operators see in ConstraintViolationEntry. BambooHR and the spreadsheet agreeing on an
     * off-day (both marking Sat/Sun MANDATORY, say) is the common case, not an edge case — the
     * un-block pass above only removes a persisted row where the sheet marks that weekday
     * <em>worked</em>, so agreement leaves the persisted row standing and
     * {@link #buildRecurringDaysOff} then synthesizes a second fact for the same date.
     *
     * First-wins by (agent, date), mirroring the {@code putIfAbsent} idiom
     * {@code BambooRefreshService.persistRefreshData} already uses for the same kind of merge.
     * The persisted row wins because it is a real dated record with a database identity, and by
     * the time this runs both the un-block pass (D-05) and the window arbitration (D-09) have
     * already removed the facts that should not block — so anything surviving on both sides is
     * genuine agreement, where either fact expresses the same outcome.
     *
     * Neither input list is modified; nothing here is persisted (D-10).
     */
    static List<AgentDayOff> dedupeAgainstPersisted(List<AgentDayOff> recurringFacts,
                                                     List<AgentDayOff> persistedDaysOff) {
        Set<String> blocked = new HashSet<>();
        for (AgentDayOff d : persistedDaysOff) {
            blocked.add(agentDateKey(d));
        }
        List<AgentDayOff> result = new ArrayList<>();
        for (AgentDayOff fact : recurringFacts) {
            if (blocked.add(agentDateKey(fact))) {
                result.add(fact);
            }
        }
        return result;
    }

    private static String agentDateKey(AgentDayOff dayOff) {
        UUID agentId = dayOff.getAgent() == null ? null : dayOff.getAgent().getId();
        return agentId + "|" + dayOff.getDate();
    }

    /**
     * Filters agents for solver eligibility (D-11):
     *   1. Agent::isActive
     *   2. agentEligibilityService.isIncludedByTitleAllowlist(tenantId, jobTitle)
     *   3. primarySpecialization != null
     *   4. Agent::isWorkingDaysKnown — excludes data-gap agents (blank/Variable customWorkingdays, D-07)
     * Order of surviving agents is preserved.
     *
     * Filter 2 was the non-schedulable denylist until 2026-08-12. The job-title allowlist is now
     * the single control for schedulability across solver, upload and template, so the two can
     * never disagree about who is eligible.
     */
    static List<Agent> filterEligible(List<Agent> agents, long tenantId,
                                       AgentEligibilityService agentEligibilityService) {
        return agents.stream()
                .filter(Agent::isActive)
                .filter(a -> agentEligibilityService.isIncludedByTitleAllowlist(tenantId, a.getJobTitle()))
                .filter(a -> a.getPrimarySpecialization() != null)
                .filter(Agent::isWorkingDaysKnown)
                .toList();
    }
}
