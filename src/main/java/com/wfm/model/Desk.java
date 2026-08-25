package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "desk", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
public class Desk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "default_contracted_hours_per_day", precision = 5, scale = 2)
    private BigDecimal defaultContractedHoursPerDay = new BigDecimal("8.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "scheduling_mode", nullable = false, length = 10)
    private SchedulingMode schedulingMode = SchedulingMode.SLOT;

    public Desk() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getDefaultContractedHoursPerDay() { return defaultContractedHoursPerDay; }
    public void setDefaultContractedHoursPerDay(BigDecimal defaultContractedHoursPerDay) {
        this.defaultContractedHoursPerDay = defaultContractedHoursPerDay;
    }

    public SchedulingMode getSchedulingMode() { return schedulingMode; }
    public void setSchedulingMode(SchedulingMode schedulingMode) { this.schedulingMode = schedulingMode; }
}
