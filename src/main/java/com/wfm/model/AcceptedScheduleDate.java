package com.wfm.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accepted_schedule_date")
@IdClass(AcceptedScheduleDateId.class)
public class AcceptedScheduleDate {

    @Id
    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Id
    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcceptedScheduleDateStatus status = AcceptedScheduleDateStatus.ACCEPTED;

    public AcceptedScheduleDate() {}

    public AcceptedScheduleDate(UUID scheduleId, long tenantId, UUID deskId, LocalDate date) {
        this.scheduleId = scheduleId;
        this.tenantId = tenantId;
        this.deskId = deskId;
        this.date = date;
        this.status = AcceptedScheduleDateStatus.ACCEPTED;
    }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public AcceptedScheduleDateStatus getStatus() { return status; }
    public void setStatus(AcceptedScheduleDateStatus status) { this.status = status; }
}
