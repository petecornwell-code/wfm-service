package com.wfm.model;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.util.UUID;

/**
 * An agent's usual shift TARGET for one weekday (D-01) -- a catalog-valued reference the operator
 * is aiming for, distinct from {@link AgentShiftAssignment} (the solved RESULT, Phase 15).
 *
 * <p>Stores a real FK to {@link ShiftTemplate}. There is deliberately no denormalized
 * template-name column here (P-01): this entity is a live target that must always reflect
 * current truth, so resolution (see {@code UsualShiftResolutionService}) reads the stored
 * template's name and finds whichever era of that name is effective on the date in question.
 * {@code AgentShiftAssignment} denormalizes on purpose because it is a frozen historical record;
 * this entity is the opposite.
 */
@Entity
@Table(name = "agent_usual_shift", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "day_of_week"})
})
public class AgentUsualShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_template_id", nullable = false)
    private ShiftTemplate shiftTemplate;

    public AgentUsualShift() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public ShiftTemplate getShiftTemplate() { return shiftTemplate; }
    public void setShiftTemplate(ShiftTemplate shiftTemplate) { this.shiftTemplate = shiftTemplate; }
}
