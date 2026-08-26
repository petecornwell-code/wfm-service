package com.wfm.model;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.UUID;

/**
 * One break band on a {@link ShiftTemplate} (D-01) -- one of N possible break placements the
 * solver may assign an agent to on a given day, replacing Phase 14's single
 * break_offset_minutes/break_duration_minutes pair. A flat child row FK'd to its parent (P-01),
 * mirroring {@link AgentDayHours}'s shape: tenant-scoped, no {@code @OneToMany} collection on
 * {@code ShiftTemplate} -- {@code grep -rl "OneToMany" src/main/java/com/wfm/model/} returns zero
 * files, so this codebase never maps a parent-side collection; callers load bands through {@link
 * com.wfm.repository.ShiftTemplateBreakBandRepository} instead.
 *
 * <p>{@code capacity} is a hard cap only when set; {@code null} means unlimited (D-03).
 */
@Entity
@Table(name = "shift_template_break_band")
public class ShiftTemplateBreakBand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_template_id", nullable = false)
    private ShiftTemplate shiftTemplate;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "capacity")
    private Integer capacity;

    public ShiftTemplateBreakBand() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public ShiftTemplate getShiftTemplate() { return shiftTemplate; }
    public void setShiftTemplate(ShiftTemplate shiftTemplate) { this.shiftTemplate = shiftTemplate; }

    public int getOffsetMinutes() { return offsetMinutes; }
    public void setOffsetMinutes(int offsetMinutes) { this.offsetMinutes = offsetMinutes; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    /** Break start = the template's shift start plus this band's offset (relocated from ShiftTemplate, P-02). */
    @Transient
    public LocalTime getBreakStartTime(ShiftTemplate template) {
        return template.getStartTime() == null ? null : template.getStartTime().plusMinutes(offsetMinutes);
    }

    /** Break end = this band's break start plus its own duration (relocated from ShiftTemplate, P-02). */
    @Transient
    public LocalTime getBreakEndTime(ShiftTemplate template) {
        LocalTime breakStart = getBreakStartTime(template);
        return breakStart == null ? null : breakStart.plusMinutes(durationMinutes);
    }
}
