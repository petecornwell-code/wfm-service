package com.wfm.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.wfm.util.BigDecimals;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The second {@code @PlanningEntity} (D-04) — one shift choice per working agent-day on a
 * shift-scheduled desk. Mirrors {@link AgentAssignment}'s dual {@code @PlanningEntity} +
 * {@code @Entity} shape exactly.
 *
 * <p>One row per {@link AgentDayConfig} with {@code effectiveHours > 0} (D-05) —
 * {@code SolverService.buildShiftAssignments} consumes the same fact the value-range filter
 * below reads, so entity creation and the filter can never disagree. Identity is
 * {@code (agent, date)}, not a per-slot entity FK.
 *
 * <p>The planning variable is a single {@code @Transient} {@link ShiftBandPair} — a problem
 * fact, never a genuine planning variable read by another entity (Anti-Pattern 2,
 * {@code research/ARCHITECTURE.md}). Uses {@code allowsUnassigned()} (D-06, P-14), not the
 * deprecated {@code nullable = true} form {@link AgentAssignment} still carries.
 *
 * <p>The denormalised {@code template*}/{@code band*}/{@code sourceTemplateId} columns are
 * populated only at accept time (D-07) — every live row leaves them {@code null}.
 */
@PlanningEntity
@Entity
@Table(name = "agent_shift_assignment")
public class AgentShiftAssignment {

    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    private LocalDate date;

    // --- Accept-time denormalised snapshot (D-07) — null on every live row ---

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    @Column(name = "band_offset_minutes")
    private Integer bandOffsetMinutes;

    @Column(name = "band_duration_minutes")
    private Integer bandDurationMinutes;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    // --- Solver-only problem facts, populated by SolverService before solving ---

    @Transient
    private AgentDayConfig dayConfig;

    @Transient
    private List<ShiftBandPair> deskShiftBandPairs;

    @PlanningVariable(valueRangeProviderRefs = "shiftBandRange", allowsUnassigned = true)
    @Transient
    private ShiftBandPair shiftBandPair;

    public AgentShiftAssignment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public LocalTime getShiftStartTime() { return shiftStartTime; }
    public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }

    public LocalTime getShiftEndTime() { return shiftEndTime; }
    public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }

    public Integer getBandOffsetMinutes() { return bandOffsetMinutes; }
    public void setBandOffsetMinutes(Integer bandOffsetMinutes) { this.bandOffsetMinutes = bandOffsetMinutes; }

    public Integer getBandDurationMinutes() { return bandDurationMinutes; }
    public void setBandDurationMinutes(Integer bandDurationMinutes) { this.bandDurationMinutes = bandDurationMinutes; }

    public UUID getSourceTemplateId() { return sourceTemplateId; }
    public void setSourceTemplateId(UUID sourceTemplateId) { this.sourceTemplateId = sourceTemplateId; }

    public AgentDayConfig getDayConfig() { return dayConfig; }
    public void setDayConfig(AgentDayConfig dayConfig) { this.dayConfig = dayConfig; }

    public List<ShiftBandPair> getDeskShiftBandPairs() { return deskShiftBandPairs; }
    public void setDeskShiftBandPairs(List<ShiftBandPair> deskShiftBandPairs) { this.deskShiftBandPairs = deskShiftBandPairs; }

    public ShiftBandPair getShiftBandPair() { return shiftBandPair; }
    public void setShiftBandPair(ShiftBandPair shiftBandPair) { this.shiftBandPair = shiftBandPair; }

    /**
     * Entity-level value range (P-13/D-04): the desk's live pairs — pre-sorted by
     * {@code SolverService.buildShiftBandPairs} (template name, then {@code effectiveFrom}, then
     * band offset ascending) — filtered to those whose net hours exactly match this row's
     * {@link AgentDayConfig#effectiveHours()}, via the same exact-equality helper
     * {@code ShiftLibraryValidationService} uses, no tolerance. Filtering with {@code Stream}
     * preserves the pre-sorted order rather than re-deriving it per call. An agent-day whose
     * hours match no live pair returns an empty range — the variable stays unassigned
     * ({@code allowsUnassigned}), it never throws.
     */
    @ValueRangeProvider(id = "shiftBandRange")
    public List<ShiftBandPair> getEligibleShiftBandPairs() {
        if (deskShiftBandPairs == null || deskShiftBandPairs.isEmpty() || dayConfig == null) {
            return List.of();
        }
        BigDecimal effective = BigDecimals.normalize(dayConfig.effectiveHours());
        return deskShiftBandPairs.stream()
                .filter(p -> {
                    BigDecimal net = BigDecimals.normalize(p.netHours());
                    return net != null && effective != null && net.compareTo(effective) == 0;
                })
                .toList();
    }
}
