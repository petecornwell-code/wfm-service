package com.wfm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ScheduleDetailResponse {
    private UUID id;
    private UUID deskId;
    private String status;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int incrementMinutes;
    private int breakDurationMinutes;
    private BigDecimal breakBlockedHours;
    private BigDecimal breakMinShiftHours;
    private String breakStartAlignment;
    private int breakClusterThresholdPct;
    private BigDecimal defaultContractedHoursPerDay;
    private int overallocationHardLimitPct;
    private int underallocationHardLimitPct;
    private ScheduleSummary.ScoreDto score;
    private Boolean feasible;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private List<String> violatedHardConstraints;
    private List<Object> staffingSummary;
    private List<Object> agentSchedule;
    private Object preferenceReport;
    private List<Object> constraintViolations;

    // Getters and setters for all fields
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getPeriodStartDate() { return periodStartDate; }
    public void setPeriodStartDate(LocalDate v) { this.periodStartDate = v; }
    public LocalDate getPeriodEndDate() { return periodEndDate; }
    public void setPeriodEndDate(LocalDate v) { this.periodEndDate = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public int getIncrementMinutes() { return incrementMinutes; }
    public void setIncrementMinutes(int v) { this.incrementMinutes = v; }
    public int getBreakDurationMinutes() { return breakDurationMinutes; }
    public void setBreakDurationMinutes(int v) { this.breakDurationMinutes = v; }
    public BigDecimal getBreakBlockedHours() { return breakBlockedHours; }
    public void setBreakBlockedHours(BigDecimal v) { this.breakBlockedHours = v; }
    public BigDecimal getBreakMinShiftHours() { return breakMinShiftHours; }
    public void setBreakMinShiftHours(BigDecimal v) { this.breakMinShiftHours = v; }
    public String getBreakStartAlignment() { return breakStartAlignment; }
    public void setBreakStartAlignment(String v) { this.breakStartAlignment = v; }
    public int getBreakClusterThresholdPct() { return breakClusterThresholdPct; }
    public void setBreakClusterThresholdPct(int v) { this.breakClusterThresholdPct = v; }
    public BigDecimal getDefaultContractedHoursPerDay() { return defaultContractedHoursPerDay; }
    public void setDefaultContractedHoursPerDay(BigDecimal v) { this.defaultContractedHoursPerDay = v; }
    public int getOverallocationHardLimitPct() { return overallocationHardLimitPct; }
    public void setOverallocationHardLimitPct(int v) { this.overallocationHardLimitPct = v; }
    public int getUnderallocationHardLimitPct() { return underallocationHardLimitPct; }
    public void setUnderallocationHardLimitPct(int v) { this.underallocationHardLimitPct = v; }
    public ScheduleSummary.ScoreDto getScore() { return score; }
    public void setScore(ScheduleSummary.ScoreDto v) { this.score = v; }
    public Boolean getFeasible() { return feasible; }
    public void setFeasible(Boolean v) { this.feasible = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public List<String> getViolatedHardConstraints() { return violatedHardConstraints; }
    public void setViolatedHardConstraints(List<String> v) { this.violatedHardConstraints = v; }
    public List<Object> getStaffingSummary() { return staffingSummary; }
    public void setStaffingSummary(List<Object> v) { this.staffingSummary = v; }
    public List<Object> getAgentSchedule() { return agentSchedule; }
    public void setAgentSchedule(List<Object> v) { this.agentSchedule = v; }
    public Object getPreferenceReport() { return preferenceReport; }
    public void setPreferenceReport(Object v) { this.preferenceReport = v; }
    public List<Object> getConstraintViolations() { return constraintViolations; }
    public void setConstraintViolations(List<Object> v) { this.constraintViolations = v; }
}
