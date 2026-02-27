package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Manages the solver lifecycle: pre-solve validation, starting, stopping.
 */
@Service
public class SolverService {

    private final InMemoryScheduleStore inMemoryStore;
    private final DeskAgentRepository deskAgentRepository;
    private final SpecializationRepository specializationRepository;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentDayOffRepository agentDayOffRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;

    @Value("${solver.time-limit:PT5M}")
    private String timeLimit;

    public SolverService(InMemoryScheduleStore inMemoryStore,
                         DeskAgentRepository deskAgentRepository,
                         SpecializationRepository specializationRepository,
                         TimeslotRepository timeslotRepository,
                         StaffingRequirementRepository staffingRequirementRepository,
                         AgentPreferenceRepository agentPreferenceRepository,
                         AgentDayOffRepository agentDayOffRepository,
                         AgentExceptionRepository agentExceptionRepository,
                         ConstraintWeightsRepository constraintWeightsRepository) {
        this.inMemoryStore = inMemoryStore;
        this.deskAgentRepository = deskAgentRepository;
        this.specializationRepository = specializationRepository;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
    }

    public Schedule startSolve(UUID deskId, Schedule schedule) {
        // TODO: 1. Check no existing non-accepted schedule for this desk (409 if exists)
        // TODO: 2. Run pre-solve validation (section 7.11)
        // TODO: 3. Load all problem facts from database
        // TODO: 4. Expand staffing requirements into AgentAssignment entities
        // TODO: 5. Create in-memory Schedule with RUNNING status
        // TODO: 6. Start solver asynchronously via SolverManager
        // TODO: 7. Return schedule summary (202)

        if (schedule.getId() == null) {
            schedule.setId(UUID.randomUUID());
        }
        schedule.setStatus(ScheduleStatus.RUNNING);
        schedule.setTenantId(TenantContext.getTenantId());
        schedule.setDeskId(deskId);
        inMemoryStore.put(schedule);
        return schedule;
    }

    public Schedule stopSolve(UUID deskId, UUID scheduleId) {
        // TODO: validate schedule is RUNNING, terminate solver, set status to STOPPED
        return inMemoryStore.get(scheduleId).orElse(null);
    }
}
