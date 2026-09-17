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
    private String schedulingMode;
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
    private DriftReport driftReport;
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
            List<BreakDetail> breaks,
            ShiftDescriptor shift,
            ShiftEnvelopeDivergence divergence
    ) {}

    /**
     * Divergence between the assigned envelope and what actually happened (Phase 15 plan 10,
     * G-15-10 D4 gap closure) — {@code null} whenever there is nothing to report (no shift
     * descriptor, or a clean agent-day whose held seats exactly equal its legal slots).
     * {@code outOfEnvelopeSeats} lists the start times of held seats the coverage predicate
     * ({@link com.wfm.model.ShiftBandPair#covers}) rejects — outside the assigned envelope, or
     * inside the assigned band's break window. {@code unworkedLegalSlots} lists the start times
     * of legal slots inside the envelope the agent did not work.
     *
     * <p>In a solve where contracted hours are satisfied the two lists have equal size: one
     * out-of-envelope seat forces the surrender of exactly one legal slot, because
     * {@code contractedHoursOver}/{@code contractedHoursUnder} pin the held count and the
     * exact-netHours value range pins the legal count to the same expected total. That equality
     * is the fingerprint the G-15-10 debug lanes used to separate a seat-supply shortage from an
     * envelope-capacity shortage, so it is surfaced as two lists rather than collapsed to a
     * single count.
     */
    public record ShiftEnvelopeDivergence(
            List<LocalTime> outOfEnvelopeSeats,
            List<LocalTime> unworkedLegalSlots
    ) {}

    /**
     * The shift an agent was assigned on a shift-scheduled desk (ENVL-01/XCUT-01) — null on a
     * slot-scheduled desk, or on a shift-scheduled agent-day the solver left unassigned.
     * {@code sourceTemplateId} is lineage only, nullable. Populated from one builder
     * ({@code ScheduleOutputService.buildAgentSchedule}) for both the in-memory path (reading the
     * transient {@code AgentShiftAssignment.shiftBandPair}) and the accepted path (reading the
     * D-07 denormalised scalar columns), so the two shapes cannot drift.
     */
    public record ShiftDescriptor(
            UUID sourceTemplateId,
            String templateName,
            LocalTime startTime,
            LocalTime endTime,
            Integer bandOffsetMinutes,
            Integer bandDurationMinutes
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
     * Phase 17 (D-12) — the drift status of one agent-day, an EXPLICIT field, never inferred from
     * a null {@code usualStartTime}. {@code NO_USUAL_SHIFT} covers both "no stored usual-shift
     * row for this weekday" and "stored row resolves to no template effective
     * on this date" (Phase 16 D-01/D-02 makes these identical states); {@code HONOURED} covers a
     * resolved target inside the tolerance band; {@code DRIFTED} covers a resolved target outside
     * it.
     */
    public enum DriftStatus { NO_USUAL_SHIFT, HONOURED, DRIFTED }

    /**
     * One row per working agent-day (D-12), mirroring {@link PreferenceReportEntry}.
     * {@code usualStartTime} is {@code null} iff {@code status == NO_USUAL_SHIFT};
     * {@code actualStartTime} is always present for a working agent-day; {@code deltaMinutes} is
     * populated ONLY when {@code status == DRIFTED}, and is SIGNED — positive when the assigned
     * envelope start is later than the usual start, negative when earlier. Both
     * {@code deltaMinutes}'s magnitude and the {@code usualShiftConsistency} constraint's penalty
     * are computed from the same {@code ShiftBandPair.startDeviationMinutes} value (DRFT-03).
     */
    public record DriftReportEntry(
            UUID agentId,
            String agentName,
            LocalDate date,
            DriftStatus status,
            LocalTime usualStartTime,
            LocalTime actualStartTime,
            Integer deltaMinutes
    ) {}

    /**
     * Whole-report counts (D-12) — {@code workingAgentDays} equals the sum of the other three by
     * construction (computed FROM the entry list, never as a parallel count).
     */
    public record DriftSummary(
            int workingAgentDays,
            int noUsualShiftCount,
            int honouredCount,
            int driftedCount
    ) {}

    /**
     * DRFT-04's over-subscription view (D-13) — how many DISTINCT agents currently hold a given
     * shift template as their usual shift, read from stored usual-shift rows directly, resolved
     * at "today" so an era-renamed template ranks under the name the operator currently sees
     * (Phase 16 D-01) — never from this solve's results. Sorted descending by {@code agentCount}
     * then ascending by {@code templateName}; a template no agent holds does not appear at all.
     */
    public record ShiftPopularityEntry(String templateName, int agentCount) {}

    /**
     * Phase 17's drift report (DRFT-01…04) — derived on read, alongside {@link PreferenceReport},
     * from {@code ScheduleOutputService.buildDriftReport(schedule)}. {@code null} on a
     * slot-scheduled desk.
     */
    public record DriftReport(
            List<DriftReportEntry> entries,
            DriftSummary summary,
            List<ShiftPopularityEntry> popularity
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
    public String getSchedulingMode() { return schedulingMode; }
    public void setSchedulingMode(String v) { this.schedulingMode = v; }
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
    public DriftReport getDriftReport() { return driftReport; }
    public void setDriftReport(DriftReport v) { this.driftReport = v; }
    public List<ConstraintViolationEntry> getConstraintViolations() { return constraintViolations; }
    public void setConstraintViolations(List<ConstraintViolationEntry> v) { this.constraintViolations = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
}
