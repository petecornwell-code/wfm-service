package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.ConstraintWeights;
import com.wfm.repository.ConstraintWeightsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConstraintWeightsService {

    private final ConstraintWeightsRepository constraintWeightsRepository;

    public ConstraintWeightsService(ConstraintWeightsRepository constraintWeightsRepository) {
        this.constraintWeightsRepository = constraintWeightsRepository;
    }

    public ConstraintWeights getWeights(UUID deskId) {
        return constraintWeightsRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId)
                .orElseGet(() -> {
                    ConstraintWeights defaults = new ConstraintWeights();
                    defaults.setTenantId(TenantContext.getTenantId());
                    defaults.setDeskId(deskId);
                    return defaults;
                });
    }

    @Transactional
    public ConstraintWeights updateWeights(UUID deskId, ConstraintWeights updates) {
        // TODO: implement partial update — omitted fields keep current values
        return null;
    }
}
