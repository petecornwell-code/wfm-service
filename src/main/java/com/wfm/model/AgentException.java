package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "agent_exception", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "agent_id", "date"})
})
public class AgentException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "contracted_hours_override", nullable = false, precision = 5, scale = 2)
    private BigDecimal contractedHoursOverride;

    @Column(nullable = false)
    private String reason;

    public AgentException() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getContractedHoursOverride() { return contractedHoursOverride; }
    public void setContractedHoursOverride(BigDecimal contractedHoursOverride) {
        this.contractedHoursOverride = contractedHoursOverride;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
