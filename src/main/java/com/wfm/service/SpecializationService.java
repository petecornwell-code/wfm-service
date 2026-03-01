package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Specialization;
import com.wfm.repository.DeskAgentRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SpecializationService {

    private final SpecializationRepository specializationRepository;
    private final DeskAgentRepository deskAgentRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;

    public SpecializationService(SpecializationRepository specializationRepository,
                                 DeskAgentRepository deskAgentRepository,
                                 StaffingRequirementRepository staffingRequirementRepository) {
        this.specializationRepository = specializationRepository;
        this.deskAgentRepository = deskAgentRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
    }

    public List<Specialization> listSpecializations(UUID deskId) {
        return specializationRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId);
    }

    @Transactional
    public Specialization createSpecialization(UUID deskId, String name) {
        long tenantId = TenantContext.getTenantId();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Specialization name is required");
        }
        if (specializationRepository.existsByTenantIdAndDeskIdAndName(tenantId, deskId, name)) {
            throw new ConflictException("A specialization with name '" + name + "' already exists for this desk");
        }

        Specialization spec = new Specialization();
        spec.setTenantId(tenantId);
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    @Transactional
    public Specialization updateSpecialization(UUID deskId, UUID id, String name) {
        long tenantId = TenantContext.getTenantId();

        Specialization spec = specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Specialization", id));

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Specialization name is required");
        }
        if (!name.equals(spec.getName())
                && specializationRepository.existsByTenantIdAndDeskIdAndName(tenantId, deskId, name)) {
            throw new ConflictException("A specialization with name '" + name + "' already exists for this desk");
        }

        spec.setName(name);
        return specializationRepository.save(spec);
    }

    @Transactional
    public void deleteSpecialization(UUID deskId, UUID id) {
        long tenantId = TenantContext.getTenantId();

        Specialization spec = specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Specialization", id));

        if (deskAgentRepository.existsByPrimarySpecialization_Id(id)
                || deskAgentRepository.existsBySecondarySpecializationsContaining(id)) {
            throw new ConflictException("Cannot delete specialization that is assigned to agents");
        }
        if (staffingRequirementRepository.existsBySpecialization_Id(id)) {
            throw new ConflictException("Cannot delete specialization that is referenced by staffing requirements");
        }

        specializationRepository.delete(spec);
    }
}
