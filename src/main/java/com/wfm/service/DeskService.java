package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Desk;
import com.wfm.repository.ConstraintWeightsRepository;
import com.wfm.repository.DeskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeskService {

    private final DeskRepository deskRepository;
    private final ConstraintWeightsRepository constraintWeightsRepository;

    public DeskService(DeskRepository deskRepository, ConstraintWeightsRepository constraintWeightsRepository) {
        this.deskRepository = deskRepository;
        this.constraintWeightsRepository = constraintWeightsRepository;
    }

    public List<Desk> listDesks() {
        return deskRepository.findByTenantId(TenantContext.getTenantId());
    }

    @Transactional
    public Desk createDesk(Desk desk) {
        // TODO: validate unique name, set tenantId, save desk, auto-create constraint weights
        desk.setTenantId(TenantContext.getTenantId());
        Desk saved = deskRepository.save(desk);

        ConstraintWeights weights = new ConstraintWeights();
        weights.setTenantId(saved.getTenantId());
        weights.setDeskId(saved.getId());
        constraintWeightsRepository.save(weights);

        return saved;
    }

    public Desk getDesk(UUID deskId) {
        // TODO: implement
        return deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId()).orElse(null);
    }

    @Transactional
    public Desk updateDesk(UUID deskId, Desk updates) {
        // TODO: implement partial update
        return null;
    }

    @Transactional
    public void deleteDesk(UUID deskId) {
        // TODO: check for accepted schedules, cascade delete desk-scoped data
    }
}
