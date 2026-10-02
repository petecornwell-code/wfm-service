package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.util.DayWindow;
import com.wfm.model.Desk;
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

    // OVNT-01/D-08: before this milestone the midnight-anchored refusal caught 15:00-14:00 as a
    // 23-hour fat-finger for free, because that pair was never forward. On an anchored desk
    // 22:00-20:00 is now forward (offsets 60 -> 1380) and would otherwise save cleanly as a
    // 22-hour shift, which no real contracted day here approaches (~8-9h) and which would later
    // make Phase 22's minimum-rest constraint structurally unsatisfiable. Lives here, not in
    // DayWindow, which owns interval arithmetic and must hold no opinion about shift length.
    private static final int MAX_SPAN_MINUTES = 16 * 60;

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
     * The anchor source for callers that cannot reach a {@link com.wfm.model.Desk} themselves
     * (BDAY-04, P-03) -- {@link com.wfm.controller.ShiftTemplateController} in particular, so it
     * does not grow a {@code DeskRepository} of its own. Loads through the same tenant-scoped
     * {@code findByIdAndTenantId} shape this class already uses, so a cross-tenant {@code deskId}
     * produces the same not-found outcome every other method here produces (T-19-05).
     */
    public DayWindow dayWindowFor(UUID deskId) {
        Desk desk = deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        return DayWindow.anchoredAt(desk.getDayStart());
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

        // BDAY-04 (plan 19-06): one window per validate() call, bound through the existing
        // dayWindowFor(UUID) seam rather than loading the desk a second time. D-11: the
        // forward-interval refusal below must keep its exact message and its position before the
        // duration read -- preserved byte-for-byte, only the receiver changed.
        DayWindow window = dayWindowFor(deskId);

        // An endTime of 00:00 means END OF DAY, so this is DayWindow's forward-within-a-day test
        // rather than endTime.isAfter(startTime) -- the latter rejects every shift finishing at
        // midnight, because LocalTime has no 24:00 and 00:00 is the smallest value in the type.
        //
        // OVNT-01/D-02: a non-forward interval on THIS desk's anchor means it would span two
        // business days -- an overnight-capable desk (a non-midnight day start) accepts the exact
        // same pair, so the cause is the anchor, not the operator's own times. The message names
        // the anchor so the refusal does not misattribute an anchor-caused rejection to the times
        // the operator actually entered correctly (see this plan's prohibition).
        if (request.startTime() == null || request.endTime() == null) {
            throw new IllegalArgumentException("Shift template end time must be after its start time");
        }
        if (!window.anchoredIsForwardWithinDay(request.startTime(), request.endTime())) {
            throw new IllegalArgumentException("Shift template end time must be after its start time"
                    + " — this desk's business day starts at " + window.dayStart() + ", so "
                    + request.startTime() + "–" + request.endTime()
                    + " would span two business days. Give the desk an overnight day start to allow this.");
        }

        long envelopeMinutes = window.anchoredDurationMinutes(request.startTime(), request.endTime());

        // OVNT-01/D-08: refused before validateBands so the first failure an operator sees is the
        // most basic one -- an envelope this long is wrong regardless of what its bands look like.
        if (envelopeMinutes > MAX_SPAN_MINUTES) {
            throw new IllegalArgumentException("Shift template spans " + (envelopeMinutes / 60.0)
                    + " hours, which exceeds this desk's maximum shift length of "
                    + (MAX_SPAN_MINUTES / 60) + " hours.");
        }

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

        validateGridAlignment(deskId, request, window);

        // OVNT-05/D-06/D-07: blocking only for an envelope that crosses calendar midnight -- no
        // live desk holds an overnight template today, so this carries zero regression risk. A
        // same-day escape is advisory only (ShiftLibraryValidationService's findOperatingWindowEscapes);
        // `dev` is the live system with real tenant data, and a blocking check here could strand an
        // already-stored row. Skipped entirely when the desk has no live timeslots -- there is no
        // window to be contained by.
        Optional<TimeslotBoundsResponse> operatingWindowBounds = timeslotGeneratorService.getLiveBounds(deskId);
        if (operatingWindowBounds.isPresent()) {
            TimeslotBoundsResponse bounds = operatingWindowBounds.get();
            if (crossesCalendarMidnight(request.startTime(), request.endTime())
                    && !isWithinOperatingWindow(bounds, request.startTime(), request.endTime(), window)) {
                throw new IllegalArgumentException("Shift template " + request.startTime() + "–"
                        + request.endTime() + " reaches outside this desk's operating window "
                        + bounds.startTime() + "–" + bounds.endTime() + ".");
            }
        }

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
    private void validateGridAlignment(UUID deskId, ShiftTemplateRequest request, DayWindow window) {
        Optional<TimeslotBoundsResponse> boundsOpt = timeslotGeneratorService.getLiveBounds(deskId);
        if (boundsOpt.isEmpty()) {
            return;
        }
        TimeslotBoundsResponse bounds = boundsOpt.get();

        List<ErrorDetail> details = new ArrayList<>();
        addIfMisaligned(details, "startTime", request.startTime(), bounds, window);
        addIfMisaligned(details, "endTime", request.endTime(), bounds, window);
        List<BreakBandRequest> bands = request.bands();
        if (bands != null) {
            for (int i = 0; i < bands.size(); i++) {
                BreakBandRequest band = bands.get(i);
                int offsetMinutes = band.offsetMinutes() != null ? band.offsetMinutes() : 0;
                int durationMinutes = band.durationMinutes() != null ? band.durationMinutes() : 0;
                if (durationMinutes <= 0) {
                    continue;
                }
                LocalTime breakStart = window.anchoredPlusWithinDay(request.startTime(), offsetMinutes);
                LocalTime breakEnd = window.anchoredPlusWithinDay(breakStart, durationMinutes);
                addIfMisaligned(details, "bands[" + i + "].breakStartTime", breakStart, bounds, window);
                addIfMisaligned(details, "bands[" + i + "].breakEndTime", breakEnd, bounds, window);
            }
        }
        if (!details.isEmpty()) {
            throw new PreSolveValidationException(
                    "Shift template times must align to the desk's timeslot grid", details);
        }
    }

    private void addIfMisaligned(List<ErrorDetail> details, String field, LocalTime value,
                                  TimeslotBoundsResponse bounds, DayWindow window) {
        if (!isAligned(bounds.startTime(), bounds.incrementMinutes(), value, window)) {
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
     *
     * <p>BDAY-04 (plan 19-06): routed through the bound window rather than the deprecated
     * midnight-implicit statics. {@code gridStart} is the desk's live timeslot grid start, not
     * necessarily the desk's day-start anchor, but at a {@code 00:00} anchor the two measurements
     * agree exactly, and once a desk's grid itself becomes anchor-relative (BDAY-03's generator),
     * this alignment check should measure against the same anchor the grid was built from.
     */
    static boolean isAligned(LocalTime gridStart, int incrementMinutes, LocalTime candidate, DayWindow window) {
        if (incrementMinutes <= 0) {
            // A non-positive increment cannot define a grid to align to — treat this the same as
            // "no live bounds" (skip the check) rather than dividing by zero/negative below.
            return true;
        }
        // The candidate is read in an END position (00:00 -> 1440). That is safe for candidates
        // in a START position too: the only start that could be 00:00 sits on a desk whose grid
        // also starts at 00:00, and 1440 is divisible by every permitted increment (15/30/60), so
        // the verdict is "aligned" either way. Reading it as a start instead would be unsafe --
        // a genuine midnight END would come back as minute 0 and be rejected as misaligned.
        long diffMinutes = window.anchoredEndMinute(candidate) - window.anchoredStartMinute(gridStart);
        return diffMinutes >= 0 && diffMinutes % incrementMinutes == 0;
    }

    /**
     * OVNT-05/D-06: true when the submitted envelope lies entirely inside the desk's real
     * generated operating window, {@code [bounds.startTime(), bounds.endTime()]} -- the check
     * with teeth that OVNT-05's literal wording ("fits inside its business day") never performed,
     * because once {@code anchoredIsForwardWithinDay} passes, every interval fits the business
     * day by construction. This is the first caller in either validation service to read {@link
     * TimeslotBoundsResponse#endTime()}, which had zero call sites before this plan. Delegates to
     * the existing {@link DayWindow#anchoredContains} primitive rather than re-deriving the offset
     * comparison. The bounds' end time is read in an END position and the template's start in a
     * START position; containment is inclusive at both edges, so an envelope exactly filling the
     * window is legal.
     */
    static boolean isWithinOperatingWindow(TimeslotBoundsResponse bounds, LocalTime startTime, LocalTime endTime,
                                            DayWindow window) {
        return window.anchoredContains(bounds.startTime(), bounds.endTime(), startTime, endTime);
    }

    /**
     * OVNT-05/D-07: true when {@code [startTime, endTime)} crosses calendar midnight -- the split
     * key between a blocking overnight escape (this method true) and a non-blocking same-day one
     * (false). Expressed through a {@code 00:00}-anchored window rather than a direct time
     * comparison: a direct comparison would both mis-read an end of {@code 00:00} (the smallest
     * {@link LocalTime} value) and trip this codebase's raw-time-comparison guard. At a {@code
     * 00:00} desk anchor this is exactly the condition the forward-interval refusal above already
     * rejects, so no same-day template on any desk can ever be classified as overnight here.
     */
    static boolean crossesCalendarMidnight(LocalTime startTime, LocalTime endTime) {
        return !DayWindow.anchoredAt(LocalTime.MIDNIGHT).anchoredIsForwardWithinDay(startTime, endTime);
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
