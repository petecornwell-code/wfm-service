package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Desk;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.*;
import com.wfm.util.BigDecimals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class DeskService {

    private final DeskRepository deskRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;
    private final ScheduleRepository scheduleRepository;
    private final AgentRepository agentRepository;
    private final SpecializationRepository specializationRepository;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentAssignmentRepository agentAssignmentRepository;
    private final InMemoryScheduleStore inMemoryScheduleStore;
    private final ShiftLibraryValidationService shiftLibraryValidationService;

    public DeskService(DeskRepository deskRepository,
                       ConstraintWeightsRepository constraintWeightsRepository,
                       ScheduleRepository scheduleRepository,
                       AgentRepository agentRepository,
                       SpecializationRepository specializationRepository,
                       TimeslotRepository timeslotRepository,
                       StaffingRequirementRepository staffingRequirementRepository,
                       AgentPreferenceRepository agentPreferenceRepository,
                       AgentExceptionRepository agentExceptionRepository,
                       AgentAssignmentRepository agentAssignmentRepository,
                       InMemoryScheduleStore inMemoryScheduleStore,
                       ShiftLibraryValidationService shiftLibraryValidationService) {
        this.deskRepository = deskRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
        this.scheduleRepository = scheduleRepository;
        this.agentRepository = agentRepository;
        this.specializationRepository = specializationRepository;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentAssignmentRepository = agentAssignmentRepository;
        this.inMemoryScheduleStore = inMemoryScheduleStore;
        this.shiftLibraryValidationService = shiftLibraryValidationService;
    }

    public List<Desk> listDesks() {
        return deskRepository.findByTenantId(TenantContext.getTenantId());
    }

    @Transactional
    public Desk createDesk(String name, String description, BigDecimal defaultContractedHoursPerDay) {
        long tenantId = TenantContext.getTenantId();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Desk name is required");
        }
        if (deskRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ConflictException("A desk with name '" + name + "' already exists");
        }

        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName(name);
        desk.setDescription(description);
        desk.setDefaultContractedHoursPerDay(
                defaultContractedHoursPerDay != null
                        ? BigDecimals.normalize(defaultContractedHoursPerDay)
                        : new BigDecimal("8.00"));
        Desk saved = deskRepository.save(desk);

        ConstraintWeights weights = new ConstraintWeights();
        weights.setTenantId(tenantId);
        weights.setDeskId(saved.getId());
        constraintWeightsRepository.save(weights);

        return saved;
    }

    public Desk getDesk(UUID deskId) {
        return deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
    }

    @Transactional
    public Desk updateDesk(UUID deskId, String name, String description,
                           BigDecimal defaultContractedHoursPerDay) {
        long tenantId = TenantContext.getTenantId();
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Desk name cannot be blank");
            }
            if (!name.equals(desk.getName()) && deskRepository.existsByTenantIdAndName(tenantId, name)) {
                throw new ConflictException("A desk with name '" + name + "' already exists");
            }
            desk.setName(name);
        }
        if (description != null) {
            desk.setDescription(description);
        }
        if (defaultContractedHoursPerDay != null) {
            desk.setDefaultContractedHoursPerDay(BigDecimals.normalize(defaultContractedHoursPerDay));
        }

        return deskRepository.save(desk);
    }

    @Transactional
    public void deleteDesk(UUID deskId) {
        long tenantId = TenantContext.getTenantId();
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        if (scheduleRepository.existsByTenantIdAndDeskIdAndStatus(tenantId, deskId, ScheduleStatus.ACCEPTED)) {
            throw new ConflictException("Cannot delete desk with accepted schedules");
        }

        // Cascade-delete all desk-scoped data (order matters for FK constraints)
        // 1. Agent-scoped desk data
        agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        // 2. Schedule-scoped data (assignments before desk-agents, requirements before timeslots)
        agentAssignmentRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        staffingRequirementRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        timeslotRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        // 3. Schedules themselves
        scheduleRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        // 4. Unassign agents from this desk (don't delete them — they're tenant-level)
        for (var agent : agentRepository.findByTenantIdAndDeskId(tenantId, deskId)) {
            agent.setDeskId(null);
            agent.setPrimarySpecialization(null);
            agent.getSecondarySpecializations().clear();
            agent.setContractedHoursPerDay(null);
            agentRepository.save(agent);
        }
        specializationRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        constraintWeightsRepository.deleteByTenantIdAndDeskId(tenantId, deskId);

        // Remove any in-memory schedule for this desk
        inMemoryScheduleStore.getByDeskId(deskId).ifPresent(s -> inMemoryScheduleStore.remove(s.getId()));

        deskRepository.delete(desk);
    }

    /**
     * Switches a desk between SLOT and SHIFT scheduling mode (MODE-02).
     *
     * <p>Order matters and is deliberate (P-24): switching to the current mode is an early-return
     * no-op (P-23, no guard, no validation, no write); otherwise the in-flight-solve guard (D-13,
     * applied symmetrically per P-22) runs before the coverage gate, since it is the cheaper and
     * more specific refusal; the coverage gate (D-08, MODE-03) runs only when the target is SHIFT
     * (D-12 — SHIFT to SLOT is always ungated); the method writes exactly one column, which is the
     * whole of MODE-04's proof.
     */
    @Transactional
    public Desk switchSchedulingMode(UUID deskId, SchedulingMode target) {
        if (target == null) {
            throw new IllegalArgumentException("Scheduling mode is required");
        }

        long tenantId = TenantContext.getTenantId();
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        if (desk.getSchedulingMode() == target) {
            return desk;
        }

        boolean runningSolveInFlight = inMemoryScheduleStore.getByDeskId(deskId)
                .map(schedule -> schedule.getStatus() == ScheduleStatus.RUNNING)
                .orElse(false);
        if (runningSolveInFlight) {
            throw new ConflictException("This desk has a schedule currently solving. "
                    + "Wait for it to finish before changing scheduling mode.");
        }

        if (target == SchedulingMode.SHIFT) {
            shiftLibraryValidationService.requireShiftModeReady(deskId);
        }

        desk.setSchedulingMode(target);
        return deskRepository.save(desk);
    }
}
