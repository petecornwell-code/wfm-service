package com.wfm.model;

import com.wfm.util.BigDecimals;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A desk-scoped shift definition (SHLB-01..03) — start/end envelope, a fixed break offset from
 * shift start (D-01, zero solver freedom), the weekdays it applies to, and the effective date
 * range that is the template's ENTIRE lifecycle mechanism (D-10). There is deliberately no
 * is_active/enabled/retired column here — see V39's migration header comment for why.
 */
@Entity
@Table(name = "shift_template", uniqueConstraints = @UniqueConstraint(
        columnNames = {"tenant_id", "desk_id", "name", "effective_from"}))
public class ShiftTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_offset_minutes", nullable = false)
    private int breakOffsetMinutes;

    @Column(name = "break_duration_minutes", nullable = false)
    private int breakDurationMinutes;

    /**
     * Fixed-position 7-character mask, index 0 = MONDAY .. index 6 = SUNDAY, '1' = valid day,
     * '0' = not valid (P-01). A storage detail — no caller outside this class sees the mask
     * itself; see getValidWeekdays/setValidWeekdays below.
     */
    @Column(name = "valid_weekdays", nullable = false, length = 7)
    private String validWeekdaysMask;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public ShiftTemplate() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getBreakOffsetMinutes() { return breakOffsetMinutes; }
    public void setBreakOffsetMinutes(int breakOffsetMinutes) { this.breakOffsetMinutes = breakOffsetMinutes; }

    public int getBreakDurationMinutes() { return breakDurationMinutes; }
    public void setBreakDurationMinutes(int breakDurationMinutes) { this.breakDurationMinutes = breakDurationMinutes; }

    public String getValidWeekdaysMask() { return validWeekdaysMask; }
    public void setValidWeekdaysMask(String validWeekdaysMask) { this.validWeekdaysMask = validWeekdaysMask; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    /**
     * Translates the stored mask to a Set<DayOfWeek> (P-01). DayOfWeek.values() is declared
     * MONDAY..SUNDAY, so index i of the mask corresponds directly to DayOfWeek.values()[i].
     * EnumSet always iterates in natural (Monday-first) order regardless of insertion order.
     * @Transient: field access means Hibernate never infers a column from this method.
     */
    @Transient
    public Set<DayOfWeek> getValidWeekdays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (validWeekdaysMask == null) {
            return days;
        }
        DayOfWeek[] order = DayOfWeek.values();
        for (int i = 0; i < validWeekdaysMask.length() && i < order.length; i++) {
            if (validWeekdaysMask.charAt(i) == '1') {
                days.add(order[i]);
            }
        }
        return days;
    }

    public void setValidWeekdays(Set<DayOfWeek> validWeekdays) {
        StringBuilder mask = new StringBuilder("0000000");
        if (validWeekdays != null) {
            for (DayOfWeek day : validWeekdays) {
                mask.setCharAt(day.getValue() - 1, '1');
            }
        }
        this.validWeekdaysMask = mask.toString();
    }

    /** Break start = shift start + offset (D-01). */
    @Transient
    public LocalTime getBreakStartTime() {
        return startTime == null ? null : startTime.plusMinutes(breakOffsetMinutes);
    }

    /** Break end = break start + duration (D-01). */
    @Transient
    public LocalTime getBreakEndTime() {
        LocalTime breakStart = getBreakStartTime();
        return breakStart == null ? null : breakStart.plusMinutes(breakDurationMinutes);
    }

    /** Net working duration = envelope duration minus the break, in hours. */
    @Transient
    public BigDecimal getNetHours() {
        if (startTime == null || endTime == null) {
            return null;
        }
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        long netMinutes = totalMinutes - breakDurationMinutes;
        BigDecimal hours = BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);
        return BigDecimals.normalize(hours);
    }
}
