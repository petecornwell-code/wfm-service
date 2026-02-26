package com.wfm.dto;

public class ConstraintWeightsDto {
    public record ScoreDto(Integer hardScore, Integer softScore) {}

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
    private ScoreDto contractedHoursWeight;
    private ScoreDto bulkOverallocationLimitWeight;
    private ScoreDto bulkUnderallocationSoftWeight;
    private ScoreDto bulkUnderallocationHardWeight;

    // Getters and setters
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
    public ScoreDto getContractedHoursWeight() { return contractedHoursWeight; }
    public void setContractedHoursWeight(ScoreDto v) { this.contractedHoursWeight = v; }
    public ScoreDto getBulkOverallocationLimitWeight() { return bulkOverallocationLimitWeight; }
    public void setBulkOverallocationLimitWeight(ScoreDto v) { this.bulkOverallocationLimitWeight = v; }
    public ScoreDto getBulkUnderallocationSoftWeight() { return bulkUnderallocationSoftWeight; }
    public void setBulkUnderallocationSoftWeight(ScoreDto v) { this.bulkUnderallocationSoftWeight = v; }
    public ScoreDto getBulkUnderallocationHardWeight() { return bulkUnderallocationHardWeight; }
    public void setBulkUnderallocationHardWeight(ScoreDto v) { this.bulkUnderallocationHardWeight = v; }
}
