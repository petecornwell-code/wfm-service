package com.wfm.model;

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
 * A desk-scoped shift definition (SHLB-01..03) — start/end envelope, N break bands (D-01, a
 * child table read through {@link com.wfm.repository.ShiftTemplateBreakBandRepository} — this
 * entity intentionally declares no parent-side collection mapping to that table, see V40's
 * migration header comment for why), the weekdays it applies to, and the effective date range
 * that is the template's ENTIRE lifecycle mechanism (D-10). There is deliberately no
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

    /**
     * Net working duration = envelope duration minus the supplied band's break duration, in
     * hours (P-02: band-parameterised — a template with zero bands passes 0 here and its net
     * hours equal its full envelope). Rounded directly to scale 2 in a single step (D-07's
     * exact-equality comparisons all normalize to scale 2 anyway) — a scale-4 intermediate
     * followed by a second round to scale 2 is a latent double-rounding hazard for grid
     * increments not evenly divisible into whole cents of an hour.
     */
    @Transient
    public BigDecimal getNetHours(int breakDurationMinutes) {
        if (startTime == null || endTime == null) {
            return null;
        }
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        long netMinutes = totalMinutes - breakDurationMinutes;
        return BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * True when {@code date} falls inside this template's effective range — the single
     * predicate for "is this template live on this specific day" (D-10's "ENTIRE lifecycle
     * mechanism"). Both ends are checked: a template whose {@code effectiveFrom} is still in the
     * future (an UPCOMING template, a first-class UI-surfaced state) is NOT effective yet; a
     * retired template ({@code effectiveTo} before {@code date}) no longer is. {@code
     * effectiveTo == null} means "still live," matching every other effective-range check in this
     * codebase.
     *
     * <p>The ONE implementation of this predicate (CR-01 gap closure) — {@code
     * ShiftLibraryValidationService} and {@code AgentShiftAssignment#getEligibleShiftBandPairs()}
     * both call this rather than each carrying their own copy, per this project's own
     * P-19/D-02 "one implementation, not two that can drift" discipline.
     */
    @Transient
    public boolean isEffectiveOn(LocalDate date) {
        if (effectiveFrom != null && effectiveFrom.isAfter(date)) {
            return false;
        }
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }
}
