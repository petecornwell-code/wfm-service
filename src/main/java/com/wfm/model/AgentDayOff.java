package com.wfm.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "agent_day_off", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "date"})
})
public class AgentDayOff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOffType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOffStatus status = DayOffStatus.APPROVED;

    public AgentDayOff() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public DayOffType getType() { return type; }
    public void setType(DayOffType type) { this.type = type; }

    public DayOffStatus getStatus() { return status; }
    public void setStatus(DayOffStatus status) { this.status = status; }
}
