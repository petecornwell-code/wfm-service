package com.wfm.model;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfigurationProvider;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.jpa.api.score.buildin.hardsoft.HardSoftScoreConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PlanningSolution
@Entity
@Table(name = "schedule")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "increment_minutes", nullable = false)
    private int incrementMinutes;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "break_blocked_hours", precision = 5, scale = 2)
    private BigDecimal breakBlockedHours = new BigDecimal("1.00");

    @Column(name = "break_duration_minutes")
    private int breakDurationMinutes = 60;

    @Column(name = "break_min_shift_hours", precision = 5, scale = 2)
    private BigDecimal breakMinShiftHours = new BigDecimal("4.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "break_start_alignment")
    private BreakAlignment breakStartAlignment = BreakAlignment.ON_HOUR;

    @Column(name = "break_cluster_threshold_pct")
    private int breakClusterThresholdPct = 20;

    @Column(name = "default_contracted_hours_per_day", precision = 5, scale = 2)
    private BigDecimal defaultContractedHoursPerDay = new BigDecimal("8.00");

    @Column(name = "overallocation_hard_limit_pct")
    private int overallocationHardLimitPct = 130;

    @Column(name = "underallocation_hard_limit_pct")
    private int underallocationHardLimitPct = 70;

    @PlanningScore
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "score", insertable = false, updatable = false)
    private HardSoftScore score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // --- Solver collections (transient for JPA, used by Timefold) ---

    @ConstraintConfigurationProvider
    @Transient
    private ConstraintWeights constraintWeights;

    @ProblemFactCollectionProperty
    @Transient
    private List<Specialization> specializations = new ArrayList<>();

    @ValueRangeProvider(id = "deskAgentRange")
    @ProblemFactCollectionProperty
    @Transient
    private List<DeskAgent> deskAgents = new ArrayList<>();

    @ProblemFactCollectionProperty
    @Transient
    private List<Timeslot> timeslots = new ArrayList<>();

    @ProblemFactCollectionProperty
    @Transient
    private List<StaffingRequirement> staffingRequirements = new ArrayList<>();

    @ProblemFactCollectionProperty
    @Transient
    private List<AgentPreference> agentPreferences = new ArrayList<>();

    @ProblemFactCollectionProperty
    @Transient
    private List<AgentDayOff> agentDaysOff = new ArrayList<>();

    @ProblemFactCollectionProperty
    @Transient
    private List<AgentException> agentExceptions = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @Transient
    private List<AgentAssignment> assignments = new ArrayList<>();

    public Schedule() {}

    // --- Getters and setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public int getIncrementMinutes() { return incrementMinutes; }
    public void setIncrementMinutes(int incrementMinutes) { this.incrementMinutes = incrementMinutes; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public LocalDate getPeriodStartDate() { return periodStartDate; }
    public void setPeriodStartDate(LocalDate periodStartDate) { this.periodStartDate = periodStartDate; }

    public LocalDate getPeriodEndDate() { return periodEndDate; }
    public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }

    public BigDecimal getBreakBlockedHours() { return breakBlockedHours; }
    public void setBreakBlockedHours(BigDecimal breakBlockedHours) { this.breakBlockedHours = breakBlockedHours; }

    public int getBreakDurationMinutes() { return breakDurationMinutes; }
    public void setBreakDurationMinutes(int breakDurationMinutes) { this.breakDurationMinutes = breakDurationMinutes; }

    public BigDecimal getBreakMinShiftHours() { return breakMinShiftHours; }
    public void setBreakMinShiftHours(BigDecimal breakMinShiftHours) { this.breakMinShiftHours = breakMinShiftHours; }

    public BreakAlignment getBreakStartAlignment() { return breakStartAlignment; }
    public void setBreakStartAlignment(BreakAlignment breakStartAlignment) { this.breakStartAlignment = breakStartAlignment; }

    public int getBreakClusterThresholdPct() { return breakClusterThresholdPct; }
    public void setBreakClusterThresholdPct(int breakClusterThresholdPct) { this.breakClusterThresholdPct = breakClusterThresholdPct; }

    public BigDecimal getDefaultContractedHoursPerDay() { return defaultContractedHoursPerDay; }
    public void setDefaultContractedHoursPerDay(BigDecimal defaultContractedHoursPerDay) { this.defaultContractedHoursPerDay = defaultContractedHoursPerDay; }

    public int getOverallocationHardLimitPct() { return overallocationHardLimitPct; }
    public void setOverallocationHardLimitPct(int overallocationHardLimitPct) { this.overallocationHardLimitPct = overallocationHardLimitPct; }

    public int getUnderallocationHardLimitPct() { return underallocationHardLimitPct; }
    public void setUnderallocationHardLimitPct(int underallocationHardLimitPct) { this.underallocationHardLimitPct = underallocationHardLimitPct; }

    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }

    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public ConstraintWeights getConstraintWeights() { return constraintWeights; }
    public void setConstraintWeights(ConstraintWeights constraintWeights) { this.constraintWeights = constraintWeights; }

    public List<Specialization> getSpecializations() { return specializations; }
    public void setSpecializations(List<Specialization> specializations) { this.specializations = specializations; }

    public List<DeskAgent> getDeskAgents() { return deskAgents; }
    public void setDeskAgents(List<DeskAgent> deskAgents) { this.deskAgents = deskAgents; }

    public List<Timeslot> getTimeslots() { return timeslots; }
    public void setTimeslots(List<Timeslot> timeslots) { this.timeslots = timeslots; }

    public List<StaffingRequirement> getStaffingRequirements() { return staffingRequirements; }
    public void setStaffingRequirements(List<StaffingRequirement> staffingRequirements) { this.staffingRequirements = staffingRequirements; }

    public List<AgentPreference> getAgentPreferences() { return agentPreferences; }
    public void setAgentPreferences(List<AgentPreference> agentPreferences) { this.agentPreferences = agentPreferences; }

    public List<AgentDayOff> getAgentDaysOff() { return agentDaysOff; }
    public void setAgentDaysOff(List<AgentDayOff> agentDaysOff) { this.agentDaysOff = agentDaysOff; }

    public List<AgentException> getAgentExceptions() { return agentExceptions; }
    public void setAgentExceptions(List<AgentException> agentExceptions) { this.agentExceptions = agentExceptions; }

    public List<AgentAssignment> getAssignments() { return assignments; }
    public void setAssignments(List<AgentAssignment> assignments) { this.assignments = assignments; }
}
