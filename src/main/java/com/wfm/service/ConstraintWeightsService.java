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
        if (updates.getContractedHoursUnderZeroWeight() != null) {
            weights.setContractedHoursUnderZeroWeight(toScore(updates.getContractedHoursUnderZeroWeight()));
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
        if (updates.getNonWorkingDaySeatWeight() != null) {
            weights.setNonWorkingDaySeatWeight(toScore(updates.getNonWorkingDaySeatWeight()));
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
        if (updates.getConsistentStartWeight() != null) {
            weights.setConsistentStartWeight(toScore(updates.getConsistentStartWeight()));
        }
        if (updates.getConsistencyToleranceMinutes() != null) {
            weights.setConsistencyToleranceMinutes(updates.getConsistencyToleranceMinutes());
        }
        if (updates.getPreferredStartShiftModeWeight() != null) {
            weights.setPreferredStartShiftModeWeight(toScore(updates.getPreferredStartShiftModeWeight()));
        }
        if (updates.getShiftStartMixWeight() != null) {
            weights.setShiftStartMixWeight(toScore(updates.getShiftStartMixWeight()));
        }
        if (updates.getShiftStartMixMode() != null) {
            weights.setShiftStartMixMode(parseShiftStartMixMode(updates.getShiftStartMixMode()));
        }

        // Phase 17 (D-07/D-08/T-17-04): validated against the MERGED entity, after every
        // partial-update block above and before persist, inside this same @Transactional method.
        // A partial-update DTO carries nulls for fields the client did not send -- validating the
        // raw DTO would either pass a genuinely invalid merged state (a stored hard-nonzero
        // consistency weight the client never touched) or reject a value the client never sent
        // (17-RESEARCH.md Pitfall 5). Reading `weights` here, not `updates`, is the whole point.
        //
        // D-07: hard-versus-soft is normally a per-desk configuration row, not a code decision
        // (Phase 15's shiftEnvelopeComplianceWeight/bandCapacityWeight precedent) -- but here hard
        // is a documented FAILURE MODE, not a valid setting: V38's own migration comment already
        // states a hard consistency rule does not force consistent starts, it forces shorter
        // shifts. Rejecting rather than merely documenting is what makes CONS-04 structural.
        if (weights.getConsistentStartWeight().hardScore() != 0) {
            throw new IllegalArgumentException("Usual Shift Consistency's hard score must be 0 — "
                    + "a hard score would risk making an otherwise-feasible schedule infeasible.");
        }
        // D-08: a lower weight on its own is exactly the "implicit in relative constraint weights
        // a reader would have to reverse-engineer" that CONS-06 rules out -- this rejection plus
        // its named test (ConstraintWeightsServiceTest) is what converts the ordering into a
        // stated, checkable invariant instead of an emergent property of two numbers.
        if (weights.getPreferredStartShiftModeWeight().softScore()
                >= weights.getConsistentStartWeight().softScore()) {
            throw new IllegalArgumentException("Preferred Start (Shift Mode) weight must be lower "
                    + "than Usual Shift Consistency's weight.");
        }
        // T-17-04: server-side reject of a negative band -- the Constraint Weights page's
        // min="0" input attribute is a client-side hint, not a control.
        if (weights.getConsistencyToleranceMinutes() < 0) {
            throw new IllegalArgumentException(
                    "Usual Shift Consistency Tolerance must be 0 minutes or more.");
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
        dto.setContractedHoursUnderZeroWeight(fromScore(w.getContractedHoursUnderZeroWeight()));
        dto.setBulkOverallocationLimitWeight(fromScore(w.getBulkOverallocationLimitWeight()));
        dto.setBulkUnderallocationSoftWeight(fromScore(w.getBulkUnderallocationSoftWeight()));
        dto.setBulkUnderallocationHardWeight(fromScore(w.getBulkUnderallocationHardWeight()));
        dto.setMinStaffingWeight(fromScore(w.getMinStaffingWeight()));
        dto.setNonWorkingDaySeatWeight(fromScore(w.getNonWorkingDaySeatWeight()));
        dto.setShiftEnvelopeComplianceWeight(fromScore(w.getShiftEnvelopeComplianceWeight()));
        dto.setBandCapacityWeight(fromScore(w.getBandCapacityWeight()));
        dto.setShiftWorkContiguityWeight(fromScore(w.getShiftWorkContiguityWeight()));
        dto.setConsistentStartWeight(fromScore(w.getConsistentStartWeight()));
        dto.setConsistencyToleranceMinutes(w.getConsistencyToleranceMinutes());
        dto.setPreferredStartShiftModeWeight(fromScore(w.getPreferredStartShiftModeWeight()));
        dto.setShiftStartMixWeight(fromScore(w.getShiftStartMixWeight()));
        dto.setShiftStartMixMode(w.getShiftStartMixMode() == null ? null : w.getShiftStartMixMode().name());
        return dto;
    }

    /**
     * Rejects an unknown mode by name rather than letting it through or failing at deserialisation.
     * ENFORCE is the only value that changes a schedule, so a typo silently landing on it — or
     * silently NOT landing on it — is worth a clear error either way.
     */
    private static com.wfm.model.ShiftStartMixMode parseShiftStartMixMode(String raw) {
        try {
            return com.wfm.model.ShiftStartMixMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("shiftStartMixMode must be one of OFF, REPORT, ENFORCE — got '"
                    + raw + "'");
        }
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
