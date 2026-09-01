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
    private HardSoftScore agentDayOffWeight = HardSoftScore.ofHard(10_000);

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

    /**
     * Weight for "Minimum staffing" — at least one agent on every timeslot, whatever the
     * forecast says. Soft by default so an under-supplied day still yields a schedule;
     * set to {@code ofHard(n)} to make an unstaffed hour illegal instead. 1000 outranks
     * every other soft term so it reliably pulls an agent onto an empty hour.
     */
    @ConstraintWeight("Minimum staffing")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "min_staffing_weight")
    private HardSoftScore minStaffingWeight = HardSoftScore.ofSoft(1000);

    /**
     * Weight for "Shift envelope compliance" (Phase 15, ENVL-02) — the hard constraint the whole
     * Option A coupling rests on (SPIKE-COUPLING.md). Hard by default: an agent seated outside
     * their chosen envelope is an illegal schedule, not a preference. Weight-driven like every
     * other constraint in this file, per {@code minimumStaffing}'s precedent that hard-vs-soft is
     * a per-desk configuration row, not a code decision.
     */
    @ConstraintWeight("Shift envelope compliance")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "shift_envelope_compliance_weight")
    private HardSoftScore shiftEnvelopeComplianceWeight = HardSoftScore.ofHard(1);

    /**
     * Weight for "Band capacity" (Phase 15, ENVL-08/D-03) — a band's set capacity is a hard cap
     * only when set; blank/null capacity is unlimited and never produces a tuple for this
     * constraint to penalise at all. Hard by default: an over-capacity agent-day on a band is an
     * illegal schedule, not a preference — mirroring {@code shiftEnvelopeComplianceWeight}'s
     * reasoning and V37/V38/V41's precedent that hard-vs-soft is this column's value, not a code
     * decision.
     */
    @ConstraintWeight("Band capacity")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "band_capacity_weight")
    private HardSoftScore bandCapacityWeight = HardSoftScore.ofHard(1);

    /**
     * Weight for "Shift work contiguity" (Phase 15 follow-up, G-15-27) — an agent's working hours
     * on a shift-scheduled desk must be contiguous apart from their break.
     *
     * <p>This restores for SHIFT mode a guarantee that used to hold BY ACCIDENT. D-01's
     * exact-equality eligibility rule made legal in-envelope slots equal contracted slots, so an
     * agent had to occupy every one of them and no hole was representable. V44's bounded slack
     * relaxed that equality for good reasons (see {@code AgentShiftAssignment
     * .getEligibleShiftBandPairs}) and silently took the contiguity guarantee with it — in the one
     * mode where nothing else enforces it, because {@code exactlyOneBreak}, {@code breakDuration},
     * {@code breakBlockedWindow} and {@code breakStartAlignment} are all mode-gated off in SHIFT
     * mode (the band defines the break, so seat-derived break geometry is the wrong instrument).
     *
     * <p>Hard, and weighted 100 to match {@code exactlyOneBreakWeight} — the SLOT-mode constraint
     * whose guarantee this reproduces — rather than {@code shiftEnvelopeComplianceWeight}'s
     * ofHard(1). Deliberate: at equal weight the solver would happily split a working day to save
     * one out-of-envelope seat, and a fragmented roster is categorically worse than a mis-placed
     * hour. The gap between 100 and 1 is what stops that trade.
     */
    @ConstraintWeight("Shift work contiguity")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "shift_work_contiguity_weight")
    private HardSoftScore shiftWorkContiguityWeight = HardSoftScore.ofHard(100);

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

    public HardSoftScore getMinStaffingWeight() { return minStaffingWeight; }
    public void setMinStaffingWeight(HardSoftScore minStaffingWeight) { this.minStaffingWeight = minStaffingWeight; }

    public HardSoftScore getShiftEnvelopeComplianceWeight() { return shiftEnvelopeComplianceWeight; }
    public void setShiftEnvelopeComplianceWeight(HardSoftScore shiftEnvelopeComplianceWeight) { this.shiftEnvelopeComplianceWeight = shiftEnvelopeComplianceWeight; }

    public HardSoftScore getBandCapacityWeight() { return bandCapacityWeight; }
    public void setBandCapacityWeight(HardSoftScore bandCapacityWeight) { this.bandCapacityWeight = bandCapacityWeight; }

    public HardSoftScore getShiftWorkContiguityWeight() { return shiftWorkContiguityWeight; }
    public void setShiftWorkContiguityWeight(HardSoftScore shiftWorkContiguityWeight) { this.shiftWorkContiguityWeight = shiftWorkContiguityWeight; }
}
