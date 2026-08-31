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

    /**
     * How many grid slots an eligible envelope may exceed this agent-day's contracted slots by
     * (D-01 bounded slack). Zero reproduces the original exact-equality rule byte for byte.
     */
    @Transient
    private int envelopeSlackSlots;

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

    public int getEnvelopeSlackSlots() { return envelopeSlackSlots; }
    public void setEnvelopeSlackSlots(int envelopeSlackSlots) { this.envelopeSlackSlots = envelopeSlackSlots; }

    public ShiftBandPair getShiftBandPair() { return shiftBandPair; }
    public void setShiftBandPair(ShiftBandPair shiftBandPair) { this.shiftBandPair = shiftBandPair; }

    /**
     * Entity-level value range (P-13/D-04): the desk's live pairs — pre-sorted by
     * {@code SolverService.buildShiftBandPairs} (template name, then {@code effectiveFrom}, then
     * band offset ascending) — filtered to those whose net hours exactly match this row's
     * {@link AgentDayConfig#effectiveHours()}, via the same exact-equality helper
     * {@code ShiftLibraryValidationService} uses, no tolerance, AND whose template is actually
     * within its effective date range on THIS row's {@code date} (CR-01 gap closure).
     *
     * <p>The effective-range check is deliberately per-row rather than a one-time filter over
     * {@code deskShiftBandPairs} at load time: {@code deskShiftBandPairs} is one shared list
     * instance reused across every {@link AgentShiftAssignment} in the schedule regardless of
     * date (see {@code SolverService.buildShiftAssignments}), so a schedule period that straddles
     * a template's {@code effectiveFrom}/{@code effectiveTo} boundary needs different eligibility
     * per agent-day, not per desk. {@link ShiftTemplate#isEffectiveOn(java.time.LocalDate)} is the
     * ONE implementation of the effective-range predicate (reused, not re-derived, from
     * {@code ShiftLibraryValidationService}); an UPCOMING template (future {@code effectiveFrom})
     * or a RETIRED template ({@code effectiveTo} before {@code date}) is excluded, matching
     * ENVL-01's "from that desk's live library" clause.
     *
     * <p>The THIRD filter is the template's {@code validWeekdays} against this row's day of week.
     * Without it the solver could seat an agent on a template explicitly marked as not applying to
     * that day — a MON-FRI "Late" on a Sunday, or a SAT/SUN "Weekend Late" on a Wednesday. That is
     * not a theoretical hole: it was observed live on desk Stubhub (EN) for period
     * 2026-01-05..2026-01-11, where ALL EIGHT residual hard violations were weekday-invalid
     * assignments, and it was the sole surviving driver of a frozen -8 hard score after G-15-10's
     * other four causes had been fixed. The mechanism is indirect, which is why it hid: giving a
     * Sunday agent the Late envelope (12:00-21:00) when Sunday demand opens at 11:00 forces exactly
     * one seat outside the envelope and surrenders exactly one legal slot inside it, so it
     * presented as a seat-supply shortfall (the 1:1 out/unworked fingerprint) rather than as a
     * weekday error.
     *
     * <p>{@code validWeekdays} was previously read ONLY by {@code ShiftLibraryValidationService}
     * (a page advisory) and {@code ShiftLibraryGenerationService} (suggestion authoring) — the
     * solver path never consulted it, so the field constrained what the library ADVISED but not
     * what the solver could DO. This filter is what makes it binding.
     *
     * <p>Filtering with {@code Stream} preserves the pre-sorted order rather than re-deriving it
     * per call. An agent-day whose hours match no live-on-that-date pair returns an empty range —
     * the variable stays unassigned ({@code allowsUnassigned}), it never throws; a desk whose
     * ENTIRE library is UPCOMING/retired for a working agent-day correctly drives
     * {@code shiftEnvelopeCompliance} to penalise every seat for that agent-day (D-06) rather than
     * silently permitting an off-library shift. Narrowing the range by weekday widens that same
     * empty-range case, which is why {@code SolverService.requireShiftEnvelopeSeatSupply} refuses
     * such a desk BEFORE solving with a named date rather than letting it degrade into envelope
     * penalty.
     */
    @ValueRangeProvider(id = "shiftBandRange")
    public List<ShiftBandPair> getEligibleShiftBandPairs() {
        if (deskShiftBandPairs == null || deskShiftBandPairs.isEmpty() || dayConfig == null) {
            return List.of();
        }
        BigDecimal effective = BigDecimals.normalize(dayConfig.effectiveHours());
        // BOUNDED SLACK (reopens D-01's exact-equality rule). An envelope may exceed the agent's
        // contracted hours by up to envelopeSlackSlots grid slots, never fall short of them.
        //
        // Exact equality made legal in-envelope slots EQUAL contracted slots, so an agent had to
        // occupy 100% of their legal slots — no margin to route around a single unavailable one.
        // Measured consequence on the live desk: Sunday 10:00 carries demand of 1, so the
        // over-allocation ceiling admits 2 agents there, yet every agent on a 10:00-starting
        // envelope was obliged to work it. Agents beyond the second breached their envelope to
        // reach contracted hours, and no library shape could avoid it — a 9-hour contiguous
        // envelope starting at 08:00, 09:00 or 10:00 necessarily contains 10:00.
        //
        // Widening WHERE an agent may work never widens HOW MUCH: contractedHoursOver is
        // ofHard(1001) and contractedHoursUnder ofHard(100), so actual hours stay pinned at
        // exactly the contracted figure. Slack buys the solver choice of slots, nothing else.
        //
        // Bounded, and never below: a pair whose net hours FALL SHORT stays ineligible, because
        // an agent physically cannot reach contracted hours inside it. The upper bound stops a
        // 4-hour agent being handed a 9-hour shift.
        BigDecimal slackHours = BigDecimal.valueOf((long) envelopeSlackSlots * dayConfig.incrementMinutes())
                .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal maxNet = effective == null ? null : effective.add(slackHours);
        return deskShiftBandPairs.stream()
                .filter(p -> {
                    BigDecimal net = BigDecimals.normalize(p.netHours());
                    return net != null && effective != null
                            && net.compareTo(effective) >= 0
                            && net.compareTo(maxNet) <= 0;
                })
                .filter(p -> date != null && p.template().isEffectiveOn(date))
                .filter(p -> date != null && p.template().appliesOn(date))
                .toList();
    }
}
