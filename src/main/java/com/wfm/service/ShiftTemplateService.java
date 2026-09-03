package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.repository.AgentShiftAssignmentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Full lifecycle for the shift template library (SHLB-01..04). Create and update run through
 * one shared {@link #validate} path so the two entry points cannot drift (T-14-11). Retirement
 * is an effective_to edit through {@link #updateShiftTemplate} (P-11, D-10): the effective date
 * range is the lifecycle predicate for a template that WAS used. {@link #deleteShiftTemplate}
 * exists alongside it for one that never was — a typo or duplicate that retiring would strand in
 * the library forever — and refuses any template a real schedule has already used.
 */
@Service
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;
    private final DeskRepository deskRepository;
    private final AgentShiftAssignmentRepository agentShiftAssignmentRepository;
    private final AgentUsualShiftRepository agentUsualShiftRepository;

    public ShiftTemplateService(ShiftTemplateRepository shiftTemplateRepository,
                                 ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository,
                                 TimeslotGeneratorService timeslotGeneratorService,
                                 DeskRepository deskRepository,
                                 AgentShiftAssignmentRepository agentShiftAssignmentRepository,
                                 AgentUsualShiftRepository agentUsualShiftRepository) {
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
        this.deskRepository = deskRepository;
        this.agentShiftAssignmentRepository = agentShiftAssignmentRepository;
        this.agentUsualShiftRepository = agentUsualShiftRepository;
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
        deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        validate(tenantId, deskId, request, null);

        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(tenantId);
        template.setDeskId(deskId);
        applyScalarFields(template, request);
        // The template must exist (and hold a generated id) before its bands can be persisted --
        // GenerationType.UUID assigns the id in-memory at save()/persist() time, so it is already
        // populated on the object save() returns.
        ShiftTemplate saved = shiftTemplateRepository.save(template);
        replaceBands(saved, request);
        return saved;
    }

    /**
     * Loads through {@code findByIdAndTenantIdAndDeskId} (never a bare {@code findById}) so a
     * cross-tenant id yields {@link EntityNotFoundException} (T-14-10). Setting {@code
     * effectiveTo} through this method is how a template is retired (P-11) — there is no
     * separate retire method; {@link #deleteShiftTemplate} handles the never-used case.
     */
    @Transactional
    public ShiftTemplate updateShiftTemplate(UUID deskId, UUID id, ShiftTemplateRequest request) {
        long tenantId = TenantContext.getTenantId();
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("ShiftTemplate", id));

        validate(tenantId, deskId, request, id);
        applyScalarFields(template, request);
        ShiftTemplate saved = shiftTemplateRepository.save(template);
        replaceBands(saved, request);
        return saved;
    }

    /**
     * Deletes a template outright — the escape hatch D-10's retire-only lifecycle left missing.
     *
     * <p>Retiring by {@code effectiveTo} is the right operation for a template that WAS used and
     * no longer applies: the row must survive so historical schedules remain explicable. It is the
     * wrong operation for a template that should never have existed — a typo, a duplicate, a
     * draft accepted by mistake — which retiring leaves in the library list forever. Both this
     * session and any operator hit that: a scratch template created for testing could not be
     * removed by any means the application offered.
     *
     * <p>Guarded on USE, not on age: a template referenced by any {@code agent_shift_assignment}
     * row has been part of a real schedule and is refused, with the caller directed to retire it
     * instead. That is deliberately stricter than the database requires —
     * {@code agent_shift_assignment.source_template_id} carries no FK (D-07 denormalises
     * template_name and the shift/band times onto the row precisely so history survives), so the
     * delete would succeed and leave every accepted schedule still readable. The refusal exists to
     * keep the audit trail honest, not to prevent a broken foreign key: an operator who deletes a
     * template that shaped a real roster loses the ability to explain why that roster looks the
     * way it does.
     *
     * <p>Break bands need no explicit delete — V40 declares
     * {@code shift_template_id ... ON DELETE CASCADE}.
     *
     * <p><b>Second guard (T-16-09, P-09):</b> a template referenced by any {@code
     * agent_usual_shift} row is also refused. Unlike {@code agent_shift_assignment
     * .source_template_id}, which carries no FK, {@code agent_usual_shift.shift_template_id} is a
     * real FK declared {@code ON DELETE CASCADE} (V47) — without this guard the database would
     * accept the delete and silently remove every referencing agent's stored usual shift, with no
     * exception raised at any layer. This guard is data-loss prevention, not a legibility
     * improvement. The cascade itself exists because {@code DeskService.deleteDesk} relies on
     * {@code shift_template.desk_id}'s own cascade (V39) to clean up a desk's templates; a
     * non-cascading reference from {@code agent_usual_shift} would make every such desk deletion
     * fail instead. This refusal applies to DELETE only — retiring a template through {@link
     * #updateShiftTemplate} by setting {@code effectiveTo} stays unblockable by any referencing
     * row, which is what D-02 and Phase 14's T-14-14 actually guarantee.
     */
    @Transactional
    public void deleteShiftTemplate(UUID deskId, UUID id) {
        long tenantId = TenantContext.getTenantId();
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("ShiftTemplate", id));

        long usages = agentShiftAssignmentRepository.countByTenantIdAndSourceTemplateId(tenantId, id);
        if (usages > 0) {
            throw new ConflictException("Shift template '" + template.getName() + "' cannot be deleted: it is "
                    + "used by " + usages + " agent-day assignment(s) in one or more schedules. Retire it "
                    + "instead by setting its effective-to date, which stops it being assigned to any new "
                    + "schedule while keeping existing ones explicable.");
        }

        long usualShiftUsages = agentUsualShiftRepository.countByShiftTemplate_Id(id);
        if (usualShiftUsages > 0) {
            throw new ConflictException("Shift template '" + template.getName() + "' cannot be deleted: it is "
                    + "referenced by " + usualShiftUsages + " agent-weekday usual shift(s). Retire it "
                    + "instead by setting its effective-to date, which stops it being assigned to any new "
                    + "schedule while keeping existing ones explicable.");
        }

        shiftTemplateBreakBandRepository.deleteByShiftTemplate_Id(id);
        shiftTemplateRepository.delete(template);
    }

    private void applyScalarFields(ShiftTemplate template, ShiftTemplateRequest request) {
        template.setName(request.name());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setValidWeekdays(request.validWeekdays());
        template.setEffectiveFrom(request.effectiveFrom());
        template.setEffectiveTo(request.effectiveTo());
    }

    /**
     * Replaces the template's bands wholesale on every save (delete-then-recreate) — a request
     * that omits a band means that band is gone, matching every other whole-collection edit in
     * this codebase. {@code flush()} after the loop makes read-after-write ordering deterministic
     * rather than relying on Hibernate auto-flush — the same fix Phase 13 Plan 02 needed for
     * {@code setContractedHours}.
     */
    private void replaceBands(ShiftTemplate template, ShiftTemplateRequest request) {
        shiftTemplateBreakBandRepository.deleteByShiftTemplate_Id(template.getId());
        List<BreakBandRequest> bands = request.bands();
        if (bands != null) {
            for (BreakBandRequest band : bands) {
                ShiftTemplateBreakBand entity = new ShiftTemplateBreakBand();
                entity.setTenantId(template.getTenantId());
                entity.setShiftTemplate(template);
                entity.setOffsetMinutes(band.offsetMinutes() != null ? band.offsetMinutes() : 0);
                entity.setDurationMinutes(band.durationMinutes() != null ? band.durationMinutes() : 0);
                entity.setCapacity(band.capacity());
                shiftTemplateBreakBandRepository.save(entity);
            }
        }
        shiftTemplateBreakBandRepository.flush();
    }

    /**
     * Shared validation for create and update (T-14-11) so the two entry points cannot drift.
     * {@code excludeId} is null on create; on update it is the row's own id, so a row never
     * collides with itself. Order: name present, times present and ordered, band checks (P-01/
     * P-04/P-05, replacing Phase 14's scalar break checks in the same slot), weekday set
     * non-empty, effective range present and ordered, grid alignment, identity uniqueness,
     * same-name range non-overlap — the first failure an operator sees is the most basic one.
     */
    private void validate(long tenantId, UUID deskId, ShiftTemplateRequest request, UUID excludeId) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Shift template name is required");
        }
        if (request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Shift template end time must be after its start time");
        }

        long envelopeMinutes = ChronoUnit.MINUTES.between(request.startTime(), request.endTime());
        validateBands(request.bands(), envelopeMinutes);

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

        validateGridAlignment(deskId, request);
        validateIdentityAndNonOverlap(tenantId, deskId, request, excludeId);
    }

    /**
     * A null or empty band list is legal and means no break (P-01 "zero bands = no break").
     * Per band, in order: non-negative offset/duration, envelope containment (reusing the
     * existing break-overrun message), capacity at least 1 when supplied (P-04), then duplicate
     * (offset, duration) pair detection across the whole list (P-05) — bands whose break windows
     * merely touch are distinct and legal, since a touching pair never shares both values.
     */
    private void validateBands(List<BreakBandRequest> bands, long envelopeMinutes) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (BreakBandRequest band : bands) {
            int offsetMinutes = band.offsetMinutes() != null ? band.offsetMinutes() : 0;
            int durationMinutes = band.durationMinutes() != null ? band.durationMinutes() : 0;
            if (offsetMinutes < 0 || durationMinutes < 0) {
                throw new IllegalArgumentException("Shift template break offset and duration cannot be negative");
            }
            if (offsetMinutes + durationMinutes > envelopeMinutes) {
                throw new IllegalArgumentException("Shift template break must finish before the shift ends");
            }
            if (band.capacity() != null && band.capacity() < 1) {
                throw new IllegalArgumentException(
                        "Break band capacity must be at least 1 (band at offset " + offsetMinutes + " minutes)");
            }
            String key = offsetMinutes + ":" + durationMinutes;
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate break band at offset " + offsetMinutes + " minutes with duration "
                                + durationMinutes + " minutes");
            }
        }
    }

    /**
     * D-02: template start, end, and (for each band whose duration is non-zero) that band's own
     * break boundaries must land on the desk's current live timeslot grid. P-10: when the desk
     * has no live timeslots yet, {@link TimeslotGeneratorService#getLiveBounds} returns {@link
     * Optional#empty()} and the check is skipped entirely — there is nothing to align to, and
     * failing the save would make the library unbuildable before a schedule period is generated.
     */
    private void validateGridAlignment(UUID deskId, ShiftTemplateRequest request) {
        Optional<TimeslotBoundsResponse> boundsOpt = timeslotGeneratorService.getLiveBounds(deskId);
        if (boundsOpt.isEmpty()) {
            return;
        }
        TimeslotBoundsResponse bounds = boundsOpt.get();

        List<ErrorDetail> details = new ArrayList<>();
        addIfMisaligned(details, "startTime", request.startTime(), bounds);
        addIfMisaligned(details, "endTime", request.endTime(), bounds);
        List<BreakBandRequest> bands = request.bands();
        if (bands != null) {
            for (int i = 0; i < bands.size(); i++) {
                BreakBandRequest band = bands.get(i);
                int offsetMinutes = band.offsetMinutes() != null ? band.offsetMinutes() : 0;
                int durationMinutes = band.durationMinutes() != null ? band.durationMinutes() : 0;
                if (durationMinutes <= 0) {
                    continue;
                }
                LocalTime breakStart = request.startTime().plusMinutes(offsetMinutes);
                LocalTime breakEnd = breakStart.plusMinutes(durationMinutes);
                addIfMisaligned(details, "bands[" + i + "].breakStartTime", breakStart, bounds);
                addIfMisaligned(details, "bands[" + i + "].breakEndTime", breakEnd, bounds);
            }
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
        if (incrementMinutes <= 0) {
            // A non-positive increment cannot define a grid to align to — treat this the same as
            // "no live bounds" (skip the check) rather than dividing by zero/negative below.
            return true;
        }
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

        boolean identityCollision = excludeId == null
                ? shiftTemplateRepository.existsByTenantIdAndDeskIdAndNameAndEffectiveFrom(
                        tenantId, deskId, request.name(), request.effectiveFrom())
                : others.stream().anyMatch(t -> t.getEffectiveFrom().equals(request.effectiveFrom()));
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
