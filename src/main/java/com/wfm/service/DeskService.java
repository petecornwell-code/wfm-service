package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Desk;
import com.wfm.model.ScheduleStatus;
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
    private final DeskAgentRepository deskAgentRepository;
    private final SpecializationRepository specializationRepository;
    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final InMemoryScheduleStore inMemoryScheduleStore;

    public DeskService(DeskRepository deskRepository,
                       ConstraintWeightsRepository constraintWeightsRepository,
                       ScheduleRepository scheduleRepository,
                       DeskAgentRepository deskAgentRepository,
                       SpecializationRepository specializationRepository,
                       TimeslotRepository timeslotRepository,
                       StaffingRequirementRepository staffingRequirementRepository,
                       AgentPreferenceRepository agentPreferenceRepository,
                       AgentExceptionRepository agentExceptionRepository,
                       InMemoryScheduleStore inMemoryScheduleStore) {
        this.deskRepository = deskRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
        this.scheduleRepository = scheduleRepository;
        this.deskAgentRepository = deskAgentRepository;
        this.specializationRepository = specializationRepository;
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.inMemoryScheduleStore = inMemoryScheduleStore;
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
        agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        staffingRequirementRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNull(tenantId, deskId);
        timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNull(tenantId, deskId);
        deskAgentRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        specializationRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        constraintWeightsRepository.deleteByTenantIdAndDeskId(tenantId, deskId);

        // Remove any in-memory schedule for this desk
        inMemoryScheduleStore.getByDeskId(deskId).ifPresent(s -> inMemoryScheduleStore.remove(s.getId()));

        deskRepository.delete(desk);
    }
}
