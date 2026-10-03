package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalTime;
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

    // The desk's business day begins at this time (BDAY-01). Every existing desk defaults to
    // 00:00 -- today's behaviour, unchanged. Only 00:00 is accepted until BDAY-04 re-anchors
    // DayWindow onto 15-minute boundaries; DeskService.setDayStart refuses anything else.
    @Column(name = "day_start", nullable = false)
    private LocalTime dayStart = LocalTime.MIDNIGHT;

    // REST-01/REST-04, D-04: the per-desk minimum shift-to-shift rest floor, in minutes.
    // Deliberately NULLable with no field initialiser -- NULL is the unambiguous "no legal minimum
    // is being assumed" state the solver's rest constraints depend on structurally (a NULL here
    // means neither constraint's stream produces a tuple at all, not merely a zero-weight penalty).
    // No baked-in default per research/FEATURES.md:150.
    @Column(name = "minimum_rest_minutes")
    private Integer minimumRestMinutes;

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

    public LocalTime getDayStart() { return dayStart; }
    public void setDayStart(LocalTime dayStart) { this.dayStart = dayStart; }

    public Integer getMinimumRestMinutes() { return minimumRestMinutes; }
    public void setMinimumRestMinutes(Integer minimumRestMinutes) { this.minimumRestMinutes = minimumRestMinutes; }
}
