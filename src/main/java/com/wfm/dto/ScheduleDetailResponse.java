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
    private String deskName;
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
    private OffsetDateTime feasibleAt;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private List<String> violatedHardConstraints;
    private List<StaffingSummaryEntry> staffingSummary;
    private List<AgentScheduleEntry> agentSchedule;
    private PreferenceReport preferenceReport;
    private ConsistencyReport consistencyReport;
    private List<ConstraintViolationEntry> constraintViolations;
    private List<String> warnings;
    private int version;

    // --- Output view sub-DTOs ---

    public record StaffingSummaryEntry(
            LocalDate date,
            String specializationName,
            BigDecimal predictedHours,
            BigDecimal actualHours,
            BigDecimal deltaHours,
            BigDecimal coveragePct
    ) {}

    public record AgentScheduleEntry(
            UUID agentId,
            String agentName,
            LocalDate date,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            BigDecimal totalHours,
            List<AssignmentDetail> assignments,
            List<BreakDetail> breaks
    ) {}

    public record AssignmentDetail(
            UUID timeslotId,
            LocalTime startTime,
            LocalTime endTime,
            String specializationName,
            String matchType
    ) {}

    public record BreakDetail(LocalTime startTime, LocalTime endTime, int durationMinutes) {}

    public record PreferenceReport(
            List<PreferenceReportEntry> entries,
            PreferenceSummary summary
    ) {}

    public record PreferenceReportEntry(
            UUID agentId,
            String agentName,
            LocalDate date,
            String preferenceSource,
            LocalTime preferredStartTime,
            LocalTime actualStartTime,
            boolean startTimeHonoured,
            LocalTime preferredBreakTime,
            LocalTime actualBreakTime,
            boolean breakTimeHonoured
    ) {}

    public record PreferenceSummary(
            int totalPreferences,
            int startTimeHonouredCount,
            int breakTimeHonouredCount,
            BigDecimal overallHonouredPct
    ) {}

    /**
     * 8.5 Consistency Report — per-agent start and break-offset spread across the whole
     * schedule period. Stage 3 of the consistency plan: the constraints that shape start and
     * break consistency are only visible in the aggregate soft score, which conflates every
     * constraint, so this reports the thing itself.
     *
     * <p>Whole-period by nature — a spread measured over a single day is always zero — so
     * unlike the other output views this one is not filtered by the {@code date} query
     * parameter.
     */
    public record ConsistencyReport(
            List<ConsistencyReportEntry> entries,
            ConsistencySummary summary
    ) {}

    /**
     * One agent's consistency across the days they work. Break fields are null for an agent
     * whose worked days carry no break at all.
     */
    public record ConsistencyReportEntry(
            UUID agentId,
            String agentName,
            int daysWorked,
            LocalTime earliestStart,
            LocalTime latestStart,
            int startSpreadMinutes,
            int daysWithBreak,
            Integer minBreakOffsetMinutes,
            Integer maxBreakOffsetMinutes,
            Integer breakOffsetSpreadMinutes
    ) {}

    /**
     * Aggregate consistency, phrased to answer the plan's target directly: at least 80% of
     * agents on an identical start every worked day, and no agent's spread over two hours.
     *
     * @param totalAgents agents with at least one worked day
     * @param singleDayAgents agents working exactly one day, who are trivially consistent and
     *        counted in every figure below — surfaced separately so a headline percentage
     *        cannot quietly rest on them
     * @param startSpreadTargetMinutes the target spread this summary was scored against,
     *        echoed so a client does not have to hardcode it
     * @param identicalBreakOffsetPct measured over agents with break data, not over all agents
     */
    public record ConsistencySummary(
            int totalAgents,
            int singleDayAgents,
            int identicalStartAgents,
            BigDecimal identicalStartPct,
            int startSpreadWithinTargetAgents,
            BigDecimal startSpreadWithinTargetPct,
            int startSpreadTargetMinutes,
            int maxStartSpreadMinutes,
            int agentsWithBreakData,
            int identicalBreakOffsetAgents,
            BigDecimal identicalBreakOffsetPct,
            int maxBreakOffsetSpreadMinutes
    ) {}

    public record ConstraintViolationEntry(
            String constraintName,
            String level,
            ScheduleSummary.ScoreDto weight,
            int violationCount,
            ScheduleSummary.ScoreDto totalPenalty,
            List<ViolationDetail> violations
    ) {}

    public record ViolationDetail(
            UUID agentId,
            String agentName,
            UUID timeslotId,
            String timeslotLabel,
            String description
    ) {}

    // --- Getters and setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }
    public String getDeskName() { return deskName; }
    public void setDeskName(String deskName) { this.deskName = deskName; }
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
    public OffsetDateTime getFeasibleAt() { return feasibleAt; }
    public void setFeasibleAt(OffsetDateTime v) { this.feasibleAt = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public List<String> getViolatedHardConstraints() { return violatedHardConstraints; }
    public void setViolatedHardConstraints(List<String> v) { this.violatedHardConstraints = v; }
    public List<StaffingSummaryEntry> getStaffingSummary() { return staffingSummary; }
    public void setStaffingSummary(List<StaffingSummaryEntry> v) { this.staffingSummary = v; }
    public List<AgentScheduleEntry> getAgentSchedule() { return agentSchedule; }
    public void setAgentSchedule(List<AgentScheduleEntry> v) { this.agentSchedule = v; }
    public PreferenceReport getPreferenceReport() { return preferenceReport; }
    public void setPreferenceReport(PreferenceReport v) { this.preferenceReport = v; }
    public ConsistencyReport getConsistencyReport() { return consistencyReport; }
    public void setConsistencyReport(ConsistencyReport v) { this.consistencyReport = v; }
    public List<ConstraintViolationEntry> getConstraintViolations() { return constraintViolations; }
    public void setConstraintViolations(List<ConstraintViolationEntry> v) { this.constraintViolations = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
}
