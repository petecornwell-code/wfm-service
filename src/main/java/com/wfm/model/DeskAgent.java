package com.wfm.model;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "desk_agent", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "agent_id"}),
    @UniqueConstraint(columnNames = {"tenant_id", "agent_id"})
})
public class DeskAgent {

    @PlanningId
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_specialization_id")
    private Specialization primarySpecialization;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "desk_agent_secondary_specialization",
        joinColumns = @JoinColumn(name = "desk_agent_id"),
        inverseJoinColumns = @JoinColumn(name = "specialization_id")
    )
    private List<Specialization> secondarySpecializations = new ArrayList<>();

    @Column(name = "contracted_hours_per_day", precision = 5, scale = 2)
    private BigDecimal contractedHoursPerDay;

    public DeskAgent() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public Specialization getPrimarySpecialization() { return primarySpecialization; }
    public void setPrimarySpecialization(Specialization primarySpecialization) {
        this.primarySpecialization = primarySpecialization;
    }

    public List<Specialization> getSecondarySpecializations() { return secondarySpecializations; }
    public void setSecondarySpecializations(List<Specialization> secondarySpecializations) {
        this.secondarySpecializations = secondarySpecializations;
    }

    public BigDecimal getContractedHoursPerDay() { return contractedHoursPerDay; }
    public void setContractedHoursPerDay(BigDecimal contractedHoursPerDay) {
        this.contractedHoursPerDay = contractedHoursPerDay;
    }
}
