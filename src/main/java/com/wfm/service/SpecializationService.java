package com.wfm.service;

import com.wfm.config.TenantContext;
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
        // TODO: validate unique name per desk
        Specialization spec = new Specialization();
        spec.setTenantId(TenantContext.getTenantId());
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    @Transactional
    public Specialization updateSpecialization(UUID deskId, UUID id, String name) {
        // TODO: implement
        return null;
    }

    @Transactional
    public void deleteSpecialization(UUID deskId, UUID id) {
        // TODO: check references from desk-agents and staffing requirements
    }
}
