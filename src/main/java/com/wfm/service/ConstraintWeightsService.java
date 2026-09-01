package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.config.TenantContext;
import com.wfm.dto.ConstraintWeightsDto;
import com.wfm.dto.ConstraintWeightsDto.ScoreDto;
import com.wfm.exception.EntityNotFoundException;
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

    public ConstraintWeightsDto getWeights(UUID deskId) {
        ConstraintWeights entity = constraintWeightsRepository
                .findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId)
                .orElseGet(() -> {
                    ConstraintWeights defaults = new ConstraintWeights();
                    defaults.setTenantId(TenantContext.getTenantId());
                    defaults.setDeskId(deskId);
                    return defaults;
                });
        return toDto(entity);
    }

    @Transactional
    public ConstraintWeightsDto updateWeights(UUID deskId, ConstraintWeightsDto updates) {
        long tenantId = TenantContext.getTenantId();

        ConstraintWeights weights = constraintWeightsRepository
                .findByTenantIdAndDeskId(tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("ConstraintWeights not found for desk " + deskId));

        // Partial update: only non-null fields in the DTO are applied
        if (updates.getUnassignedAssignmentWeight() != null) {
            weights.setUnassignedAssignmentWeight(toScore(updates.getUnassignedAssignmentWeight()));
        }
        if (updates.getAgentDayOffWeight() != null) {
            weights.setAgentDayOffWeight(toScore(updates.getAgentDayOffWeight()));
        }
        if (updates.getSpecMatchWeight() != null) {
            weights.setSpecMatchWeight(toScore(updates.getSpecMatchWeight()));
        }
        if (updates.getNoOverlapWeight() != null) {
            weights.setNoOverlapWeight(toScore(updates.getNoOverlapWeight()));
        }
        if (updates.getExactlyOneBreakWeight() != null) {
            weights.setExactlyOneBreakWeight(toScore(updates.getExactlyOneBreakWeight()));
        }
        if (updates.getBreakDurationWeight() != null) {
            weights.setBreakDurationWeight(toScore(updates.getBreakDurationWeight()));
        }
        if (updates.getBreakBlockedWindowWeight() != null) {
            weights.setBreakBlockedWindowWeight(toScore(updates.getBreakBlockedWindowWeight()));
        }
        if (updates.getBreakAlignmentWeight() != null) {
            weights.setBreakAlignmentWeight(toScore(updates.getBreakAlignmentWeight()));
        }
        if (updates.getPreferPrimaryWeight() != null) {
            weights.setPreferPrimaryWeight(toScore(updates.getPreferPrimaryWeight()));
        }
        if (updates.getHonourStartTimeWeight() != null) {
            weights.setHonourStartTimeWeight(toScore(updates.getHonourStartTimeWeight()));
        }
        if (updates.getHonourBreakTimeWeight() != null) {
            weights.setHonourBreakTimeWeight(toScore(updates.getHonourBreakTimeWeight()));
        }
        if (updates.getBreakClusteringWeight() != null) {
            weights.setBreakClusteringWeight(toScore(updates.getBreakClusteringWeight()));
        }
        if (updates.getContractedHoursOverWeight() != null) {
            weights.setContractedHoursOverWeight(toScore(updates.getContractedHoursOverWeight()));
        }
        if (updates.getContractedHoursUnderWeight() != null) {
            weights.setContractedHoursUnderWeight(toScore(updates.getContractedHoursUnderWeight()));
        }
        if (updates.getBulkOverallocationLimitWeight() != null) {
            weights.setBulkOverallocationLimitWeight(toScore(updates.getBulkOverallocationLimitWeight()));
        }
        if (updates.getBulkUnderallocationSoftWeight() != null) {
            weights.setBulkUnderallocationSoftWeight(toScore(updates.getBulkUnderallocationSoftWeight()));
        }
        if (updates.getBulkUnderallocationHardWeight() != null) {
            weights.setBulkUnderallocationHardWeight(toScore(updates.getBulkUnderallocationHardWeight()));
        }
        if (updates.getMinStaffingWeight() != null) {
            weights.setMinStaffingWeight(toScore(updates.getMinStaffingWeight()));
        }
        if (updates.getShiftEnvelopeComplianceWeight() != null) {
            weights.setShiftEnvelopeComplianceWeight(toScore(updates.getShiftEnvelopeComplianceWeight()));
        }
        if (updates.getBandCapacityWeight() != null) {
            weights.setBandCapacityWeight(toScore(updates.getBandCapacityWeight()));
        }
        if (updates.getShiftWorkContiguityWeight() != null) {
            weights.setShiftWorkContiguityWeight(toScore(updates.getShiftWorkContiguityWeight()));
        }

        ConstraintWeights saved = constraintWeightsRepository.save(weights);
        return toDto(saved);
    }

    private ConstraintWeightsDto toDto(ConstraintWeights w) {
        ConstraintWeightsDto dto = new ConstraintWeightsDto();
        dto.setUnassignedAssignmentWeight(fromScore(w.getUnassignedAssignmentWeight()));
        dto.setAgentDayOffWeight(fromScore(w.getAgentDayOffWeight()));
        dto.setSpecMatchWeight(fromScore(w.getSpecMatchWeight()));
        dto.setNoOverlapWeight(fromScore(w.getNoOverlapWeight()));
        dto.setExactlyOneBreakWeight(fromScore(w.getExactlyOneBreakWeight()));
        dto.setBreakDurationWeight(fromScore(w.getBreakDurationWeight()));
        dto.setBreakBlockedWindowWeight(fromScore(w.getBreakBlockedWindowWeight()));
        dto.setBreakAlignmentWeight(fromScore(w.getBreakAlignmentWeight()));
        dto.setPreferPrimaryWeight(fromScore(w.getPreferPrimaryWeight()));
        dto.setHonourStartTimeWeight(fromScore(w.getHonourStartTimeWeight()));
        dto.setHonourBreakTimeWeight(fromScore(w.getHonourBreakTimeWeight()));
        dto.setBreakClusteringWeight(fromScore(w.getBreakClusteringWeight()));
        dto.setContractedHoursOverWeight(fromScore(w.getContractedHoursOverWeight()));
        dto.setContractedHoursUnderWeight(fromScore(w.getContractedHoursUnderWeight()));
        dto.setBulkOverallocationLimitWeight(fromScore(w.getBulkOverallocationLimitWeight()));
        dto.setBulkUnderallocationSoftWeight(fromScore(w.getBulkUnderallocationSoftWeight()));
        dto.setBulkUnderallocationHardWeight(fromScore(w.getBulkUnderallocationHardWeight()));
        dto.setMinStaffingWeight(fromScore(w.getMinStaffingWeight()));
        dto.setShiftEnvelopeComplianceWeight(fromScore(w.getShiftEnvelopeComplianceWeight()));
        dto.setBandCapacityWeight(fromScore(w.getBandCapacityWeight()));
        dto.setShiftWorkContiguityWeight(fromScore(w.getShiftWorkContiguityWeight()));
        return dto;
    }

    private static ScoreDto fromScore(HardSoftScore score) {
        if (score == null) return null;
        return new ScoreDto(score.hardScore(), score.softScore());
    }

    private static HardSoftScore toScore(ScoreDto dto) {
        return HardSoftScore.of(
                dto.hardScore() != null ? dto.hardScore() : 0,
                dto.softScore() != null ? dto.softScore() : 0);
    }
}
