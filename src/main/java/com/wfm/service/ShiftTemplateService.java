package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Full lifecycle for the shift template library (SHLB-01..04). Create and update run through
 * one shared {@link #validate} path so the two entry points cannot drift (T-14-11). Retirement
 * is an effective_to edit through {@link #updateShiftTemplate} — there is no delete or retire
 * method (P-11, D-10): the effective date range is the sole lifecycle predicate.
 */
@Service
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;

    public ShiftTemplateService(ShiftTemplateRepository shiftTemplateRepository,
                                 TimeslotGeneratorService timeslotGeneratorService) {
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
    }

    /**
     * Sorted by name ascending then effectiveFrom descending (P-14), applied here in one
     * readable line rather than a long derived-query method name. A stable sort keeps rows
     * tying on both keys in a fixed relative order between reads.
     */
    public List<ShiftTemplate> listShiftTemplates(UUID deskId) {
        List<ShiftTemplate> templates =
                shiftTemplateRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId);
        return templates.stream()
                .sorted(Comparator.comparing(ShiftTemplate::getName)
                        .thenComparing(ShiftTemplate::getEffectiveFrom, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public ShiftTemplate createShiftTemplate(UUID deskId, ShiftTemplateRequest request) {
        long tenantId = TenantContext.getTenantId();
        validate(tenantId, deskId, request, null);

        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(tenantId);
        template.setDeskId(deskId);
        applyFields(template, request);
        return shiftTemplateRepository.save(template);
    }

    /**
     * Loads through {@code findByIdAndTenantIdAndDeskId} (never a bare {@code findById}) so a
     * cross-tenant id yields {@link EntityNotFoundException} (T-14-10). Setting {@code
     * effectiveTo} through this method is how a template is retired (P-11) — there is no
     * separate retire method and no delete method on this service.
     */
    @Transactional
    public ShiftTemplate updateShiftTemplate(UUID deskId, UUID id, ShiftTemplateRequest request) {
        long tenantId = TenantContext.getTenantId();
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("ShiftTemplate", id));

        validate(tenantId, deskId, request, id);
        applyFields(template, request);
        return shiftTemplateRepository.save(template);
    }

    private void applyFields(ShiftTemplate template, ShiftTemplateRequest request) {
        template.setName(request.name());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setBreakOffsetMinutes(request.breakOffsetMinutes() != null ? request.breakOffsetMinutes() : 0);
        template.setBreakDurationMinutes(request.breakDurationMinutes() != null ? request.breakDurationMinutes() : 0);
        template.setValidWeekdays(request.validWeekdays());
        template.setEffectiveFrom(request.effectiveFrom());
        template.setEffectiveTo(request.effectiveTo());
    }

    /**
     * Shared validation for create and update (T-14-11) so the two entry points cannot drift.
     * {@code excludeId} is null on create; on update it is the row's own id, so a row never
     * collides with itself. Order: name present, times present and ordered, break within the
     * envelope, weekday set non-empty, effective range present and ordered, grid alignment,
     * identity uniqueness, same-name range non-overlap — the first failure an operator sees is
     * the most basic one.
     */
    private void validate(long tenantId, UUID deskId, ShiftTemplateRequest request, UUID excludeId) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Shift template name is required");
        }
        if (request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Shift template end time must be after its start time");
        }

        int breakOffsetMinutes = request.breakOffsetMinutes() != null ? request.breakOffsetMinutes() : 0;
        int breakDurationMinutes = request.breakDurationMinutes() != null ? request.breakDurationMinutes() : 0;
        if (breakOffsetMinutes < 0 || breakDurationMinutes < 0) {
            throw new IllegalArgumentException("Shift template break offset and duration cannot be negative");
        }
        long envelopeMinutes = ChronoUnit.MINUTES.between(request.startTime(), request.endTime());
        if (breakOffsetMinutes + breakDurationMinutes > envelopeMinutes) {
            throw new IllegalArgumentException("Shift template break must finish before the shift ends");
        }

        if (request.validWeekdays() == null || request.validWeekdays().isEmpty()) {
            throw new IllegalArgumentException("A shift template must be valid on at least one weekday");
        }

        if (request.effectiveFrom() == null) {
            throw new IllegalArgumentException("Shift template effective from date is required");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new IllegalArgumentException(
                    "Shift template effective to date cannot be before its effective from date");
        }

        validateGridAlignment(deskId, request, breakOffsetMinutes, breakDurationMinutes);
        validateIdentityAndNonOverlap(tenantId, deskId, request, excludeId);
    }

    /**
     * D-02: template start, end, and (when non-zero-duration) break boundaries must land on the
     * desk's current live timeslot grid. P-10: when the desk has no live timeslots yet, {@link
     * TimeslotGeneratorService#getLiveBounds} returns {@link Optional#empty()} and the check is
     * skipped entirely — there is nothing to align to, and failing the save would make the
     * library unbuildable before a schedule period is generated.
     */
    private void validateGridAlignment(UUID deskId, ShiftTemplateRequest request,
                                        int breakOffsetMinutes, int breakDurationMinutes) {
        Optional<TimeslotBoundsResponse> boundsOpt = timeslotGeneratorService.getLiveBounds(deskId);
        if (boundsOpt.isEmpty()) {
            return;
        }
        TimeslotBoundsResponse bounds = boundsOpt.get();

        List<ErrorDetail> details = new ArrayList<>();
        addIfMisaligned(details, "startTime", request.startTime(), bounds);
        addIfMisaligned(details, "endTime", request.endTime(), bounds);
        if (breakDurationMinutes > 0) {
            LocalTime breakStart = request.startTime().plusMinutes(breakOffsetMinutes);
            LocalTime breakEnd = breakStart.plusMinutes(breakDurationMinutes);
            addIfMisaligned(details, "breakStartTime", breakStart, bounds);
            addIfMisaligned(details, "breakEndTime", breakEnd, bounds);
        }
        if (!details.isEmpty()) {
            throw new PreSolveValidationException(
                    "Shift template times must align to the desk's timeslot grid", details);
        }
    }

    private void addIfMisaligned(List<ErrorDetail> details, String field, LocalTime value,
                                  TimeslotBoundsResponse bounds) {
        if (!isAligned(bounds.startTime(), bounds.incrementMinutes(), value)) {
            details.add(new ErrorDetail(field,
                    "Start, end, and break times must align to this desk's "
                            + bounds.incrementMinutes() + "-minute schedule grid.",
                    value.toString()));
        }
    }

    /**
     * D-02's alignment rule as one directly-testable function: a time is aligned iff its
     * whole-minute distance from the grid's start time is a non-negative exact multiple of the
     * increment. Package-private and static so this is one function rather than four inline
     * copies.
     */
    static boolean isAligned(LocalTime gridStart, int incrementMinutes, LocalTime candidate) {
        long diffMinutes = ChronoUnit.MINUTES.between(gridStart, candidate);
        return diffMinutes >= 0 && diffMinutes % incrementMinutes == 0;
    }

    /**
     * D-11: unique {@code (tenant_id, desk_id, name, effective_from)} plus a same-name
     * non-overlap check — together they guarantee exactly one era of a given name applies to any
     * given date. Both ends of a range are inclusive; a null {@code effectiveTo} is treated as
     * {@link LocalDate#MAX} only for this in-memory comparison, never persisted.
     */
    private void validateIdentityAndNonOverlap(long tenantId, UUID deskId, ShiftTemplateRequest request,
                                                UUID excludeId) {
        List<ShiftTemplate> sameName =
                shiftTemplateRepository.findByTenantIdAndDeskIdAndName(tenantId, deskId, request.name());
        List<ShiftTemplate> others = sameName.stream()
                .filter(t -> excludeId == null || !t.getId().equals(excludeId))
                .toList();

        boolean identityCollision = others.stream()
                .anyMatch(t -> t.getEffectiveFrom().equals(request.effectiveFrom()));
        if (identityCollision) {
            throw new ConflictException("A shift template named '" + request.name()
                    + "' already starts on " + request.effectiveFrom() + " for this desk");
        }

        LocalDate candidateFrom = request.effectiveFrom();
        LocalDate candidateTo = request.effectiveTo() != null ? request.effectiveTo() : LocalDate.MAX;
        for (ShiftTemplate other : others) {
            LocalDate otherFrom = other.getEffectiveFrom();
            LocalDate otherTo = other.getEffectiveTo() != null ? other.getEffectiveTo() : LocalDate.MAX;
            boolean overlaps = !candidateFrom.isAfter(otherTo) && !otherFrom.isAfter(candidateTo);
            if (overlaps) {
                String otherToText = other.getEffectiveTo() != null ? other.getEffectiveTo().toString() : "present";
                throw new ConflictException("Shift template '" + request.name()
                        + "' already has an effective range covering " + other.getEffectiveFrom()
                        + " to " + otherToText);
            }
        }
    }
}
