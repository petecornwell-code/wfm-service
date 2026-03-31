package com.wfm.model;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.jpa.api.score.buildin.hardsoft.HardSoftScoreConverter;
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

    @ConstraintWeight("Unassigned assignment")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "unassigned_assignment_weight")
    private HardSoftScore unassignedAssignmentWeight = HardSoftScore.ofSoft(1000);

    @ConstraintWeight("Agent day off")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "agent_day_off_weight")
    private HardSoftScore agentDayOffWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Specialization match")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "spec_match_weight")
    private HardSoftScore specMatchWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("One assignment per timeslot")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "no_overlap_weight")
    private HardSoftScore noOverlapWeight = HardSoftScore.ofHard(1000);

    @ConstraintWeight("Exactly one break")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "exactly_one_break_weight")
    private HardSoftScore exactlyOneBreakWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Break duration")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_duration_weight")
    private HardSoftScore breakDurationWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Break blocked window")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_blocked_window_weight")
    private HardSoftScore breakBlockedWindowWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Break start alignment")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_alignment_weight")
    private HardSoftScore breakAlignmentWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Prefer primary specialization")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "prefer_primary_weight")
    private HardSoftScore preferPrimaryWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Honour preferred start time")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "honour_start_time_weight")
    private HardSoftScore honourStartTimeWeight = HardSoftScore.ofSoft(5);

    @ConstraintWeight("Honour preferred break time")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "honour_break_time_weight")
    private HardSoftScore honourBreakTimeWeight = HardSoftScore.ofSoft(5);

    @ConstraintWeight("Break clustering")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_clustering_weight")
    private HardSoftScore breakClusteringWeight = HardSoftScore.ofSoft(2);

    @ConstraintWeight("Contracted hours (over)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_over_weight")
    private HardSoftScore contractedHoursOverWeight = HardSoftScore.ofHard(1001);

    @ConstraintWeight("Contracted hours (under)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_under_weight")
    private HardSoftScore contractedHoursUnderWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Contracted hours (under, zero)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_under_zero_weight")
    private HardSoftScore contractedHoursUnderZeroWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Bulk over-allocation limit")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_overallocation_limit_weight")
    private HardSoftScore bulkOverallocationLimitWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Bulk under-allocation soft")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_underallocation_soft_weight")
    private HardSoftScore bulkUnderallocationSoftWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Bulk under-allocation hard")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_underallocation_hard_weight")
    private HardSoftScore bulkUnderallocationHardWeight = HardSoftScore.ofHard(1);

    public ConstraintWeights() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public HardSoftScore getUnassignedAssignmentWeight() { return unassignedAssignmentWeight; }
    public void setUnassignedAssignmentWeight(HardSoftScore unassignedAssignmentWeight) { this.unassignedAssignmentWeight = unassignedAssignmentWeight; }

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

    public HardSoftScore getContractedHoursOverWeight() { return contractedHoursOverWeight; }
    public void setContractedHoursOverWeight(HardSoftScore contractedHoursOverWeight) { this.contractedHoursOverWeight = contractedHoursOverWeight; }

    public HardSoftScore getContractedHoursUnderWeight() { return contractedHoursUnderWeight; }
    public void setContractedHoursUnderWeight(HardSoftScore contractedHoursUnderWeight) { this.contractedHoursUnderWeight = contractedHoursUnderWeight; }

    public HardSoftScore getContractedHoursUnderZeroWeight() { return contractedHoursUnderZeroWeight; }
    public void setContractedHoursUnderZeroWeight(HardSoftScore contractedHoursUnderZeroWeight) { this.contractedHoursUnderZeroWeight = contractedHoursUnderZeroWeight; }

    public HardSoftScore getBulkOverallocationLimitWeight() { return bulkOverallocationLimitWeight; }
    public void setBulkOverallocationLimitWeight(HardSoftScore bulkOverallocationLimitWeight) { this.bulkOverallocationLimitWeight = bulkOverallocationLimitWeight; }

    public HardSoftScore getBulkUnderallocationSoftWeight() { return bulkUnderallocationSoftWeight; }
    public void setBulkUnderallocationSoftWeight(HardSoftScore bulkUnderallocationSoftWeight) { this.bulkUnderallocationSoftWeight = bulkUnderallocationSoftWeight; }

    public HardSoftScore getBulkUnderallocationHardWeight() { return bulkUnderallocationHardWeight; }
    public void setBulkUnderallocationHardWeight(HardSoftScore bulkUnderallocationHardWeight) { this.bulkUnderallocationHardWeight = bulkUnderallocationHardWeight; }
}
