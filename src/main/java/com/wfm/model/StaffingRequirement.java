package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "staffing_requirement")
public class StaffingRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id", nullable = false)
    private Specialization specialization;

    @Column(name = "required_hours", nullable = false, precision = 10, scale = 4)
    private BigDecimal requiredHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffingSource source = StaffingSource.DIRECT;

    public StaffingRequirement() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public Timeslot getTimeslot() { return timeslot; }
    public void setTimeslot(Timeslot timeslot) { this.timeslot = timeslot; }

    public Specialization getSpecialization() { return specialization; }
    public void setSpecialization(Specialization specialization) { this.specialization = specialization; }

    public BigDecimal getRequiredHours() { return requiredHours; }
    public void setRequiredHours(BigDecimal requiredHours) { this.requiredHours = requiredHours; }

    public StaffingSource getSource() { return source; }
    public void setSource(StaffingSource source) { this.source = source; }
}
