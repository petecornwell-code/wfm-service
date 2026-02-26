package com.wfm.model;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import jakarta.persistence.*;
import java.util.UUID;

@ConstraintConfiguration
@Entity
@Table(name = "constraint_weights", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id"})
})
public class ConstraintWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @ConstraintWeight("Agent day off")
    private HardSoftScore agentDayOffWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Specialization match")
    private HardSoftScore specMatchWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("One assignment per timeslot")
    private HardSoftScore noOverlapWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Exactly one break")
    private HardSoftScore exactlyOneBreakWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Break duration")
    private HardSoftScore breakDurationWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Break blocked window")
    private HardSoftScore breakBlockedWindowWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Break start alignment")
    private HardSoftScore breakAlignmentWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Prefer primary specialization")
    private HardSoftScore preferPrimaryWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Honour preferred start time")
    private HardSoftScore honourStartTimeWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Honour preferred break time")
    private HardSoftScore honourBreakTimeWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Break clustering")
    private HardSoftScore breakClusteringWeight = HardSoftScore.ofSoft(2);

    @ConstraintWeight("Contracted hours")
    private HardSoftScore contractedHoursWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Bulk over-allocation limit")
    private HardSoftScore bulkOverallocationLimitWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Bulk under-allocation soft")
    private HardSoftScore bulkUnderallocationSoftWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Bulk under-allocation hard")
    private HardSoftScore bulkUnderallocationHardWeight = HardSoftScore.ofHard(1);

    public ConstraintWeights() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public HardSoftScore getAgentDayOffWeight() { return agentDayOffWeight; }
    public void setAgentDayOffWeight(HardSoftScore agentDayOffWeight) { this.agentDayOffWeight = agentDayOffWeight; }

    public HardSoftScore getSpecMatchWeight() { return specMatchWeight; }
    public void setSpecMatchWeight(HardSoftScore specMatchWeight) { this.specMatchWeight = specMatchWeight; }

    public HardSoftScore getNoOverlapWeight() { return noOverlapWeight; }
    public void setNoOverlapWeight(HardSoftScore noOverlapWeight) { this.noOverlapWeight = noOverlapWeight; }

    public HardSoftScore getExactlyOneBreakWeight() { return exactlyOneBreakWeight; }
    public void setExactlyOneBreakWeight(HardSoftScore exactlyOneBreakWeight) { this.exactlyOneBreakWeight = exactlyOneBreakWeight; }

    public HardSoftScore getBreakDurationWeight() { return breakDurationWeight; }
    public void setBreakDurationWeight(HardSoftScore breakDurationWeight) { this.breakDurationWeight = breakDurationWeight; }

    public HardSoftScore getBreakBlockedWindowWeight() { return breakBlockedWindowWeight; }
    public void setBreakBlockedWindowWeight(HardSoftScore breakBlockedWindowWeight) { this.breakBlockedWindowWeight = breakBlockedWindowWeight; }

    public HardSoftScore getBreakAlignmentWeight() { return breakAlignmentWeight; }
    public void setBreakAlignmentWeight(HardSoftScore breakAlignmentWeight) { this.breakAlignmentWeight = breakAlignmentWeight; }

    public HardSoftScore getPreferPrimaryWeight() { return preferPrimaryWeight; }
    public void setPreferPrimaryWeight(HardSoftScore preferPrimaryWeight) { this.preferPrimaryWeight = preferPrimaryWeight; }

    public HardSoftScore getHonourStartTimeWeight() { return honourStartTimeWeight; }
    public void setHonourStartTimeWeight(HardSoftScore honourStartTimeWeight) { this.honourStartTimeWeight = honourStartTimeWeight; }

    public HardSoftScore getHonourBreakTimeWeight() { return honourBreakTimeWeight; }
    public void setHonourBreakTimeWeight(HardSoftScore honourBreakTimeWeight) { this.honourBreakTimeWeight = honourBreakTimeWeight; }

    public HardSoftScore getBreakClusteringWeight() { return breakClusteringWeight; }
    public void setBreakClusteringWeight(HardSoftScore breakClusteringWeight) { this.breakClusteringWeight = breakClusteringWeight; }

    public HardSoftScore getContractedHoursWeight() { return contractedHoursWeight; }
    public void setContractedHoursWeight(HardSoftScore contractedHoursWeight) { this.contractedHoursWeight = contractedHoursWeight; }

    public HardSoftScore getBulkOverallocationLimitWeight() { return bulkOverallocationLimitWeight; }
    public void setBulkOverallocationLimitWeight(HardSoftScore bulkOverallocationLimitWeight) { this.bulkOverallocationLimitWeight = bulkOverallocationLimitWeight; }

    public HardSoftScore getBulkUnderallocationSoftWeight() { return bulkUnderallocationSoftWeight; }
    public void setBulkUnderallocationSoftWeight(HardSoftScore bulkUnderallocationSoftWeight) { this.bulkUnderallocationSoftWeight = bulkUnderallocationSoftWeight; }

    public HardSoftScore getBulkUnderallocationHardWeight() { return bulkUnderallocationHardWeight; }
    public void setBulkUnderallocationHardWeight(HardSoftScore bulkUnderallocationHardWeight) { this.bulkUnderallocationHardWeight = bulkUnderallocationHardWeight; }
}
