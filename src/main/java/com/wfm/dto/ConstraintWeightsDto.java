package com.wfm.dto;

public class ConstraintWeightsDto {
    public record ScoreDto(Integer hardScore, Integer softScore) {}

    private ScoreDto unassignedAssignmentWeight;
    private ScoreDto agentDayOffWeight;
    private ScoreDto specMatchWeight;
    private ScoreDto noOverlapWeight;
    private ScoreDto exactlyOneBreakWeight;
    private ScoreDto breakDurationWeight;
    private ScoreDto breakBlockedWindowWeight;
    private ScoreDto breakAlignmentWeight;
    private ScoreDto preferPrimaryWeight;
    private ScoreDto honourStartTimeWeight;
    private ScoreDto honourBreakTimeWeight;
    private ScoreDto breakClusteringWeight;
    private ScoreDto contractedHoursOverWeight;
    private ScoreDto contractedHoursUnderWeight;
    private ScoreDto bulkOverallocationLimitWeight;
    private ScoreDto bulkUnderallocationSoftWeight;
    private ScoreDto bulkUnderallocationHardWeight;
    private ScoreDto minStaffingWeight;

    // Phase 15 weights. Absent from this DTO until now, which meant the three constraints the
    // shift model rests on could be neither read nor tuned through the API -- directly against the
    // intent their own migrations state ("hard-vs-soft is this column's value, never a code
    // decision"). Their omission is how a 100:1 ratio between shiftWorkContiguity and
    // shiftEnvelopeCompliance became untunable without a deploy.
    private ScoreDto shiftEnvelopeComplianceWeight;
    private ScoreDto bandCapacityWeight;
    private ScoreDto shiftWorkContiguityWeight;

    // Getters and setters
    public ScoreDto getUnassignedAssignmentWeight() { return unassignedAssignmentWeight; }
    public void setUnassignedAssignmentWeight(ScoreDto v) { this.unassignedAssignmentWeight = v; }
    public ScoreDto getAgentDayOffWeight() { return agentDayOffWeight; }
    public void setAgentDayOffWeight(ScoreDto v) { this.agentDayOffWeight = v; }
    public ScoreDto getSpecMatchWeight() { return specMatchWeight; }
    public void setSpecMatchWeight(ScoreDto v) { this.specMatchWeight = v; }
    public ScoreDto getNoOverlapWeight() { return noOverlapWeight; }
    public void setNoOverlapWeight(ScoreDto v) { this.noOverlapWeight = v; }
    public ScoreDto getExactlyOneBreakWeight() { return exactlyOneBreakWeight; }
    public void setExactlyOneBreakWeight(ScoreDto v) { this.exactlyOneBreakWeight = v; }
    public ScoreDto getBreakDurationWeight() { return breakDurationWeight; }
    public void setBreakDurationWeight(ScoreDto v) { this.breakDurationWeight = v; }
    public ScoreDto getBreakBlockedWindowWeight() { return breakBlockedWindowWeight; }
    public void setBreakBlockedWindowWeight(ScoreDto v) { this.breakBlockedWindowWeight = v; }
    public ScoreDto getBreakAlignmentWeight() { return breakAlignmentWeight; }
    public void setBreakAlignmentWeight(ScoreDto v) { this.breakAlignmentWeight = v; }
    public ScoreDto getPreferPrimaryWeight() { return preferPrimaryWeight; }
    public void setPreferPrimaryWeight(ScoreDto v) { this.preferPrimaryWeight = v; }
    public ScoreDto getHonourStartTimeWeight() { return honourStartTimeWeight; }
    public void setHonourStartTimeWeight(ScoreDto v) { this.honourStartTimeWeight = v; }
    public ScoreDto getHonourBreakTimeWeight() { return honourBreakTimeWeight; }
    public void setHonourBreakTimeWeight(ScoreDto v) { this.honourBreakTimeWeight = v; }
    public ScoreDto getBreakClusteringWeight() { return breakClusteringWeight; }
    public void setBreakClusteringWeight(ScoreDto v) { this.breakClusteringWeight = v; }
    public ScoreDto getContractedHoursOverWeight() { return contractedHoursOverWeight; }
    public void setContractedHoursOverWeight(ScoreDto v) { this.contractedHoursOverWeight = v; }
    public ScoreDto getContractedHoursUnderWeight() { return contractedHoursUnderWeight; }
    public void setContractedHoursUnderWeight(ScoreDto v) { this.contractedHoursUnderWeight = v; }
    public ScoreDto getBulkOverallocationLimitWeight() { return bulkOverallocationLimitWeight; }
    public void setBulkOverallocationLimitWeight(ScoreDto v) { this.bulkOverallocationLimitWeight = v; }
    public ScoreDto getBulkUnderallocationSoftWeight() { return bulkUnderallocationSoftWeight; }
    public void setBulkUnderallocationSoftWeight(ScoreDto v) { this.bulkUnderallocationSoftWeight = v; }
    public ScoreDto getBulkUnderallocationHardWeight() { return bulkUnderallocationHardWeight; }
    public void setBulkUnderallocationHardWeight(ScoreDto v) { this.bulkUnderallocationHardWeight = v; }
    public ScoreDto getMinStaffingWeight() { return minStaffingWeight; }
    public void setMinStaffingWeight(ScoreDto v) { this.minStaffingWeight = v; }
    public ScoreDto getShiftEnvelopeComplianceWeight() { return shiftEnvelopeComplianceWeight; }
    public void setShiftEnvelopeComplianceWeight(ScoreDto v) { this.shiftEnvelopeComplianceWeight = v; }
    public ScoreDto getBandCapacityWeight() { return bandCapacityWeight; }
    public void setBandCapacityWeight(ScoreDto v) { this.bandCapacityWeight = v; }
    public ScoreDto getShiftWorkContiguityWeight() { return shiftWorkContiguityWeight; }
    public void setShiftWorkContiguityWeight(ScoreDto v) { this.shiftWorkContiguityWeight = v; }
}
