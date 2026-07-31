package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "agent_day_hours", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "day_of_week"})
})
public class AgentDayHours {

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

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    /**
     * Descriptive flavour of this weekday's not-worked state (D-12).
     * null = a normal worked day (hours > 0) or a plain unlabelled 0 (no descriptive reason).
     * MANDATORY / PTO = the spreadsheet cell used that keyword (D-03) -- reuses the existing
     * DayOffType enum vocabulary without reusing AgentDayOff's dated materialization mechanism.
     * Reporting/label metadata only -- never read by the solver (unlike AgentDayOff.type,
     * this column is intentionally nullable: the third "no label" state has no equivalent
     * on AgentDayOff, which is always MANDATORY or PTO, never "worked").
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_off_type", length = 9)
    private DayOffType dayOffType;

    public AgentDayHours() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }

    public DayOffType getDayOffType() { return dayOffType; }
    public void setDayOffType(DayOffType dayOffType) { this.dayOffType = dayOffType; }
}
