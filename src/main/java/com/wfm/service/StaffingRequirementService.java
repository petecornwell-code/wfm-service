package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.*;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.ErlangDemandInput;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ErlangDemandInputRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.CursorPagination;
import com.wfm.util.DayWindow;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class StaffingRequirementService {

    private final StaffingRequirementRepository staffingRequirementRepository;
    private final TimeslotRepository timeslotRepository;
    private final SpecializationRepository specializationRepository;
    private final ErlangCalculatorService erlangCalculatorService;
    private final DeskRepository deskRepository;
    private final EntityManager entityManager;
    private final ErlangDemandInputRepository erlangDemandInputRepository;

    public StaffingRequirementService(StaffingRequirementRepository staffingRequirementRepository,
                                      TimeslotRepository timeslotRepository,
                                      SpecializationRepository specializationRepository,
                                      ErlangCalculatorService erlangCalculatorService,
                                      DeskRepository deskRepository,
                                      EntityManager entityManager,
                                      ErlangDemandInputRepository erlangDemandInputRepository) {
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotRepository = timeslotRepository;
        this.specializationRepository = specializationRepository;
        this.erlangCalculatorService = erlangCalculatorService;
        this.deskRepository = deskRepository;
        this.entityManager = entityManager;
        this.erlangDemandInputRepository = erlangDemandInputRepository;
    }

    /**
     * BDAY-04 (plan 19-06, Rule 3 deviation -- see this plan's SUMMARY): the anchor-source table
     * classified this class under P-02 rule 5 ("propagate a DayWindow parameter outward to the
     * caller that already resolves a desk"), but neither of this class's two callers
     * (StaffingRequirementController) resolves a desk or a dayStart either. Loading the desk here,
     * once per calculate call, is this class's own anchor source -- mirroring {@code
     * ShiftTemplateService.dayWindowFor}'s throw-on-missing-desk convention.
     */
    private DayWindow dayWindowFor(UUID deskId, long tenantId) {
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        return DayWindow.anchoredAt(desk.getDayStart());
    }

    /**
     * BDAY-04: the anchored equivalent of the deprecated {@code DayWindow.durationMinutes},
     * reproducing its exact throw-on-non-forward behaviour (message included). Unlike {@code
     * ShiftTemplateService.validate}'s save path (D-11), nothing upstream of either Erlang
     * calculation already refuses a non-forward timeslot interval, and {@code
     * StaffingRequirementErlangTest#midnightCrossingTimeslotIsRejected} pins exactly this throw.
     * {@link DayWindow#anchoredDurationMinutes} itself no longer throws (BDAY-04 criterion 2), so
     * the check is reproduced here rather than silently lost.
     */
    private static int intervalMinutes(DayWindow window, LocalTime start, LocalTime end) {
        if (!window.anchoredIsForwardWithinDay(start, end)) {
            throw new IllegalArgumentException(
                    "Interval must run forward within a single day, but got " + start + " to " + end
                            + ". A window ending at midnight is supported (end 00:00); one crossing "
                            + "midnight into the next day is not.");
        }
        return window.anchoredDurationMinutes(start, end);
    }

    public PaginatedResponse<StaffingRequirementResponse.Item> listRequirements(
            UUID deskId, String from, String to, String businessFrom, String businessTo,
            String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clampedLimit = CursorPagination.clampLimit(limit);
        Pageable pageable = PageRequest.of(0, clampedLimit + 1);

        Map<String, String> cursorValues = CursorPagination.decode(cursor);
        boolean hasCursor = !cursorValues.isEmpty();
        boolean hasDateRange = from != null && to != null;
        boolean hasBusinessRange = businessFrom != null && businessTo != null;

        // D-04: validate ONLY the business-date params, before any repository call. Stricter than
        // the calendar pair's silent-ignore precedent by design -- silently ignoring a half-supplied
        // business range would return the desk's entire demand to a caller that asked for two days.
        // The calendar from/to handling above and below is deliberately unchanged.
        LocalDate businessFromDate = null;
        LocalDate businessToDate = null;
        if ((businessFrom != null) != (businessTo != null)) {
            throw new IllegalArgumentException("businessFrom and businessTo must be supplied together");
        }
        if (hasBusinessRange) {
            if (from != null || to != null) {
                throw new IllegalArgumentException(
                        "Use one range or the other: businessFrom/businessTo cannot be combined with from/to");
            }
            businessFromDate = parseBusinessDate("businessFrom", businessFrom);
            businessToDate = parseBusinessDate("businessTo", businessTo);
            if (businessFromDate.isAfter(businessToDate)) {
                throw new IllegalArgumentException("businessFrom must not be after businessTo");
            }
        }

        List<StaffingRequirement> results;

        // D-04: a business-date range filters the STORED business_date column and pages on the
        // same calendar keyset (date, startTime, specialization name, id) as the calendar range,
        // so a cursor minted by either resumes in either.
        if (hasBusinessRange && hasCursor) {
            results = staffingRequirementRepository.findLiveByDeskAndBusinessDateRangeAfterCursor(
                    tenantId, deskId, businessFromDate, businessToDate,
                    LocalDate.parse(cursorValues.get("date")),
                    LocalTime.parse(cursorValues.get("startTime")),
                    cursorValues.get("specName"),
                    UUID.fromString(cursorValues.get("id")),
                    pageable);
        } else if (hasBusinessRange) {
            results = staffingRequirementRepository.findLiveByDeskAndBusinessDateRange(
                    tenantId, deskId, businessFromDate, businessToDate, pageable);
        } else if (hasDateRange && hasCursor) {
            results = staffingRequirementRepository.findLiveByDeskAndDateRangeAfterCursor(
                    tenantId, deskId, LocalDate.parse(from), LocalDate.parse(to),
                    LocalDate.parse(cursorValues.get("date")),
                    LocalTime.parse(cursorValues.get("startTime")),
                    cursorValues.get("specName"),
                    UUID.fromString(cursorValues.get("id")),
                    pageable);
        } else if (hasDateRange) {
            results = staffingRequirementRepository.findLiveByDeskAndDateRange(
                    tenantId, deskId, LocalDate.parse(from), LocalDate.parse(to), pageable);
        } else if (hasCursor) {
            results = staffingRequirementRepository.findLiveByDeskAfterCursor(
                    tenantId, deskId,
                    LocalDate.parse(cursorValues.get("date")),
                    LocalTime.parse(cursorValues.get("startTime")),
                    cursorValues.get("specName"),
                    UUID.fromString(cursorValues.get("id")),
                    pageable);
        } else {
            results = staffingRequirementRepository.findLiveByDesk(tenantId, deskId, pageable);
        }

        List<StaffingRequirementResponse.Item> items = results.stream()
                .map(this::toResponseItem).toList();

        return CursorPagination.buildPage(items, clampedLimit, item -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("date", item.date().toString());
            map.put("startTime", item.startTime().toString());
            map.put("specName", item.specializationName());
            map.put("id", item.id().toString());
            return map;
        });
    }

    /**
     * Parses one business-date request parameter. A {@link java.time.format.DateTimeParseException}
     * is not an {@link IllegalArgumentException} and would surface as HTTP 500; this rethrows it as
     * one (mapped to 400 {@code VALIDATION_FAILED}) naming the parameter and the expected form. The
     * raw value is deliberately not echoed -- the message is returned to the client.
     */
    private static LocalDate parseBusinessDate(String paramName, String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(paramName + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    @Transactional
    public StaffingRequirementResponse saveRequirements(UUID deskId, StaffingRequirementRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.requirements() == null || request.requirements().isEmpty()) {
            return new StaffingRequirementResponse(List.of());
        }

        // Validate uniqueness of timeslot+specialization combinations in the payload
        Set<String> seen = new HashSet<>();
        for (StaffingRequirementRequest.Item item : request.requirements()) {
            String key = item.timeslotId() + ":" + item.specializationId();
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate timeslot+specialization combination: timeslotId=" + item.timeslotId()
                        + ", specializationId=" + item.specializationId());
            }
        }

        // Load all referenced timeslots and specializations, validate they exist
        Map<UUID, Timeslot> timeslotMap = new HashMap<>();
        Map<UUID, Specialization> specMap = new HashMap<>();

        for (StaffingRequirementRequest.Item item : request.requirements()) {
            if (!timeslotMap.containsKey(item.timeslotId())) {
                Timeslot ts = timeslotRepository.findById(item.timeslotId())
                        .filter(t -> t.getTenantId() == tenantId && t.getDeskId().equals(deskId)
                                && t.getScheduleId() == null)
                        .orElseThrow(() -> new EntityNotFoundException("Timeslot", item.timeslotId()));
                timeslotMap.put(item.timeslotId(), ts);
            }
            if (!specMap.containsKey(item.specializationId())) {
                Specialization spec = specializationRepository.findByIdAndTenantIdAndDeskId(
                                item.specializationId(), tenantId, deskId)
                        .orElseThrow(() -> new EntityNotFoundException("Specialization", item.specializationId()));
                specMap.put(item.specializationId(), spec);
            }
        }

        // SOLV-07/D-15: the range derivation and the delete's filter are ONE decision, and both
        // halves move together in this edit. This delete is destructive against live operator
        // demand -- on a re-anchored desk, a range derived from the calendar date but filtered on
        // the business date (or vice versa) silently destroys the wrong business day's
        // requirements. Deriving the range from getBusinessDate() and passing it only to the
        // business-date-filtering delete method below (which filters the same column) makes that
        // half-migration structurally impossible rather than merely discouraged.
        LocalDate minDate = timeslotMap.values().stream()
                .map(Timeslot::getBusinessDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = timeslotMap.values().stream()
                .map(Timeslot::getBusinessDate).max(LocalDate::compareTo).orElseThrow();

        // Delete existing live requirements in this business-date range
        staffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange(tenantId, deskId, minDate, maxDate);

        // Flush deletes to DB before inserting new rows — Hibernate's ActionQueue
        // processes inserts before deletes in the same flush, which would hit the
        // unique constraint on idx_staffing_requirement_live.
        entityManager.flush();
        entityManager.clear();

        // Insert new requirements
        List<StaffingRequirement> saved = new ArrayList<>();
        for (StaffingRequirementRequest.Item item : request.requirements()) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setTenantId(tenantId);
            sr.setDeskId(deskId);
            sr.setTimeslot(timeslotMap.get(item.timeslotId()));
            sr.setSpecialization(specMap.get(item.specializationId()));
            sr.setRequiredFTEs(item.requiredFTEs());
            sr.setSource(StaffingSource.DIRECT);
            saved.add(staffingRequirementRepository.save(sr));
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
    }

    /**
     * Erlang C for ONE business date. The live requirements of {@code request.businessDate()} are
     * REPLACED -- including rows for that date's timeslots this request never mentions -- and so
     * are those of each {@code copyTo} date, which receive the source date's per-slot result
     * matched by start and end time. Every other business date keeps its live requirements.
     *
     * <p>Both modes delegate to {@link ErlangCalculatorService}, the same service behind the
     * read-only calculator page, so what this button writes is exactly what that page previews.
     * Erlang C is the conservative baseline and will usually ask for MORE agents than Erlang X on
     * the same inputs, because it assumes nobody ever hangs up.
     *
     * <p>Both calculators share {@link #replaceDayWithErlang}, so their delete/insert behaviour
     * cannot drift.
     */
    @Transactional
    public StaffingRequirementResponse calculateErlangC(UUID deskId, ErlangCRequest request) {
        List<ErlangLine> lines = request.parameters() == null ? List.of()
                : request.parameters().stream()
                .map(i -> new ErlangLine(i.timeslotId(), i.specializationId(), i.callVolume(), i.aht(),
                        i.serviceLevelTarget(), i.serviceLevelThreshold(), null, null))
                .toList();

        // Delegated to the same service the read-only calculator calls, so what this button
        // writes is exactly what that page previews. The DTO speaks percentages to match this
        // screen; the calculator takes fractions.
        return replaceDayWithErlang(deskId, request.businessDate(), request.copyTo(), lines,
                request.adjustments(), StaffingSource.ERLANG_C,
                (line, intervalMinutes) -> erlangCalculatorService.calculateErlangC(
                        new ErlangCCalculationRequest(
                                line.callVolume(), intervalMinutes, line.aht(),
                                line.serviceLevelTarget() / 100.0, line.serviceLevelThreshold(),
                                request.adjustments())));
    }

    /**
     * Rejects a service level target that was sent as a fraction where a percentage belongs.
     *
     * <p>Both endpoints on this screen read the target as 0-100, and both divide by 100. A value of
     * {@code 0.8} therefore asks for a 0.8% service level, which almost any headcount meets — it
     * does not fail, it silently understaffs. The frontend did exactly that for every Erlang X
     * calculation until 2026-09-25, staffing to roughly the offered load instead of to the target.
     *
     * <p>Rejecting below 1 costs nothing real: no contact centre targets under 1% answered.
     */
    private static void requirePercentageTarget(double serviceLevelTarget) {
        if (serviceLevelTarget < 1.0 || serviceLevelTarget > 100.0) {
            throw new IllegalArgumentException(
                    "serviceLevelTarget is a percentage between 1 and 100 (80 means 80%), not a "
                            + "fraction: " + serviceLevelTarget);
        }
    }

    /** Resolves a timeslot that belongs to this tenant and desk and is not tied to a schedule. */
    private void loadTimeslot(Map<UUID, Timeslot> into, UUID timeslotId, long tenantId, UUID deskId) {
        if (into.containsKey(timeslotId)) {
            return;
        }
        Timeslot ts = timeslotRepository.findById(timeslotId)
                .filter(t -> t.getTenantId() == tenantId && t.getDeskId().equals(deskId)
                        && t.getScheduleId() == null)
                .orElseThrow(() -> new EntityNotFoundException("Timeslot", timeslotId));
        into.put(timeslotId, ts);
    }

    private void loadSpecialization(Map<UUID, Specialization> into, UUID specializationId,
                                    long tenantId, UUID deskId) {
        if (into.containsKey(specializationId)) {
            return;
        }
        Specialization spec = specializationRepository
                .findByIdAndTenantIdAndDeskId(specializationId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Specialization", specializationId));
        into.put(specializationId, spec);
    }

    /**
     * Erlang X for ONE business date, with the same replace and copy semantics as
     * {@link #calculateErlangC}: the request's business date and each {@code copyTo} date are
     * replaced, and no other date is touched.
     */
    @Transactional
    public StaffingRequirementResponse calculateErlangX(UUID deskId, ErlangXRequest request) {
        List<ErlangLine> lines = request.parameters() == null ? List.of()
                : request.parameters().stream()
                .map(i -> new ErlangLine(i.timeslotId(), i.specializationId(), i.callVolume(), i.aht(),
                        i.serviceLevelTarget(), i.serviceLevelThreshold(), i.patience(), i.retryRate()))
                .toList();

        return replaceDayWithErlang(deskId, request.businessDate(), request.copyTo(), lines,
                request.adjustments(), StaffingSource.ERLANG_X,
                (line, intervalMinutes) -> erlangCalculatorService.calculateErlangX(
                        new ErlangXCalculationRequest(
                                line.callVolume(), intervalMinutes, line.aht(), line.patience(),
                                line.retryRate() / 100.0, line.serviceLevelTarget() / 100.0,
                                line.serviceLevelThreshold(), false, request.adjustments())));
    }

    /** One calculated row, model-agnostic: patience and retryRate are null for Erlang C. */
    private record ErlangLine(UUID timeslotId, UUID specializationId, int callVolume, double aht,
                              double serviceLevelTarget, int serviceLevelThreshold,
                              Double patience, Double retryRate) {
    }

    /** The model-specific half of a calculation: one line on an interval of the given length. */
    @FunctionalInterface
    private interface ErlangCalc {
        ErlangCalculationResponse calculate(ErlangLine line, int intervalMinutes);
    }

    /** A timeslot's geometry within a day, the key copies are matched on. */
    private record SlotKey(LocalTime start, LocalTime end) {
    }

    private static final int MAX_COPY_TARGETS = 366;

    /**
     * The single replace path behind both Erlang calculators.
     *
     * <p>These endpoints destroy live operator demand, so EVERY refusal happens before the first
     * delete: null business date, duplicate rows, a fractional target, a timeslot that is not on
     * {@code businessDate}, a midnight-crossing slot, and any copy target that has no live slot
     * matching a source slot. Deletes are one business date per call (never a span), inside the
     * caller's single transaction, so a failure mid-insert rolls back both halves.
     */
    private StaffingRequirementResponse replaceDayWithErlang(
            UUID deskId, LocalDate businessDate, List<LocalDate> copyTo, List<ErlangLine> lines,
            StaffingAdjustmentOptionsDto adjustments, StaffingSource model, ErlangCalc calc) {
        long tenantId = TenantContext.getTenantId();

        if (lines == null || lines.isEmpty()) {
            return new StaffingRequirementResponse(List.of());
        }
        if (businessDate == null) {
            throw new IllegalArgumentException("businessDate is required");
        }

        Set<String> seen = new HashSet<>();
        for (ErlangLine line : lines) {
            String key = line.timeslotId() + ":" + line.specializationId();
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate timeslot+specialization combination: timeslotId=" + line.timeslotId()
                                + ", specializationId=" + line.specializationId());
            }
        }

        Map<UUID, Timeslot> timeslotMap = new HashMap<>();
        Map<UUID, Specialization> specMap = new HashMap<>();
        for (ErlangLine line : lines) {
            requirePercentageTarget(line.serviceLevelTarget());
            loadTimeslot(timeslotMap, line.timeslotId(), tenantId, deskId);
            loadSpecialization(specMap, line.specializationId(), tenantId, deskId);
            // This membership check is what makes the per-date delete below provably cover every
            // insert: a row on another date would otherwise collide with a surviving live row.
            LocalDate slotBusinessDate = timeslotMap.get(line.timeslotId()).getBusinessDate();
            if (!businessDate.equals(slotBusinessDate)) {
                throw new IllegalArgumentException(
                        "Timeslot " + line.timeslotId() + " is on business date " + slotBusinessDate
                                + ", not the requested business date " + businessDate);
            }
        }

        // Computed once, before any delete: a midnight-crossing slot is refused here, with the
        // desk's demand still intact. The interval comes from the timeslot itself, so the figure
        // the operator typed against a row is converted on that row's own length. DayWindow,
        // because a slot ending at 00:00 ends the day -- a raw Duration.between would make the
        // last slot of a midnight desk negative.
        DayWindow window = dayWindowFor(deskId, tenantId);
        List<ErlangCalculationResponse> results = new ArrayList<>();
        for (ErlangLine line : lines) {
            Timeslot ts = timeslotMap.get(line.timeslotId());
            int intervalMinutes = intervalMinutes(window, ts.getStartTime(), ts.getEndTime());
            results.add(calc.calculate(line, intervalMinutes));
        }

        // OD-2: copy targets, matched by equal start AND end time (not list position), so a
        // target whose geometry differs is refused rather than silently shifted.
        Set<LocalDate> targetDates = new LinkedHashSet<>();
        if (copyTo != null) {
            for (LocalDate d : copyTo) {
                if (d == null) {
                    throw new IllegalArgumentException("copyTo must not contain null dates");
                }
                if (!d.equals(businessDate)) {
                    targetDates.add(d);
                }
            }
        }
        if (targetDates.size() > MAX_COPY_TARGETS) {
            throw new IllegalArgumentException(
                    "copyTo may name at most " + MAX_COPY_TARGETS + " dates");
        }
        Map<LocalDate, Map<SlotKey, Timeslot>> targetSlots = new LinkedHashMap<>();
        Map<LocalDate, List<UUID>> affectedSlotIds = new LinkedHashMap<>();
        affectedSlotIds.put(businessDate, timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndBusinessDateOrderByDateAscStartTimeAsc(
                        tenantId, deskId, businessDate).stream().map(Timeslot::getId).toList());
        for (LocalDate target : targetDates) {
            List<Timeslot> live = timeslotRepository
                    .findByTenantIdAndDeskIdAndScheduleIdIsNullAndBusinessDateOrderByDateAscStartTimeAsc(
                            tenantId, deskId, target);
            if (live.isEmpty()) {
                throw new IllegalArgumentException(
                        "copyTo date " + target + " has no timeslots on this desk");
            }
            Map<SlotKey, Timeslot> byKey = new HashMap<>();
            for (Timeslot t : live) {
                byKey.putIfAbsent(new SlotKey(t.getStartTime(), t.getEndTime()), t);
            }
            Set<String> targetSeen = new HashSet<>();
            for (ErlangLine line : lines) {
                Timeslot source = timeslotMap.get(line.timeslotId());
                Timeslot match = byKey.get(new SlotKey(source.getStartTime(), source.getEndTime()));
                if (match == null) {
                    throw new IllegalArgumentException(
                            "copyTo date " + target + " has no timeslot "
                                    + source.getStartTime() + "-" + source.getEndTime());
                }
                if (!targetSeen.add(match.getId() + ":" + line.specializationId())) {
                    throw new IllegalArgumentException(
                            "copyTo date " + target + " has ambiguous timeslots at "
                                    + source.getStartTime() + "-" + source.getEndTime());
                }
            }
            targetSlots.put(target, byKey);
            affectedSlotIds.put(target, live.stream().map(Timeslot::getId).toList());
        }

        // One single-date delete per affected business date -- the only delete in this helper.
        List<LocalDate> affected = new ArrayList<>();
        affected.add(businessDate);
        affected.addAll(targetSlots.keySet());
        for (LocalDate d : affected) {
            staffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange(tenantId, deskId, d, d);
            // OD-3: the date's saved inputs are replaced with its requirements.
            List<UUID> slotIds = affectedSlotIds.get(d);
            if (slotIds != null && !slotIds.isEmpty()) {
                erlangDemandInputRepository.deleteByDeskAndTimeslotIds(tenantId, deskId, slotIds);
            }
        }

        // Flush deletes to DB before inserting new rows -- Hibernate's ActionQueue processes
        // inserts before deletes in the same flush, which would hit the unique constraint on
        // idx_staffing_requirement_live.
        entityManager.flush();
        entityManager.clear();

        List<StaffingRequirement> saved = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            ErlangLine line = lines.get(i);
            saved.add(insertRequirement(tenantId, deskId, timeslotMap.get(line.timeslotId()),
                    specMap.get(line.specializationId()), results.get(i), model));
            insertInput(tenantId, deskId, line.timeslotId(), line, adjustments, model);
        }
        for (Map<SlotKey, Timeslot> byKey : targetSlots.values()) {
            for (int i = 0; i < lines.size(); i++) {
                ErlangLine line = lines.get(i);
                Timeslot source = timeslotMap.get(line.timeslotId());
                Timeslot match = byKey.get(new SlotKey(source.getStartTime(), source.getEndTime()));
                saved.add(insertRequirement(tenantId, deskId, match,
                        specMap.get(line.specializationId()), results.get(i), model));
                // OD-2: reopening a copied date shows what produced it.
                insertInput(tenantId, deskId, match.getId(), line, adjustments, model);
            }
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
    }

    private void insertInput(long tenantId, UUID deskId, UUID timeslotId, ErlangLine line,
                             StaffingAdjustmentOptionsDto adjustments, StaffingSource model) {
        ErlangDemandInput in = new ErlangDemandInput();
        in.setTenantId(tenantId);
        in.setDeskId(deskId);
        in.setTimeslotId(timeslotId);
        in.setSpecializationId(line.specializationId());
        in.setModel(model);
        in.setCallVolume(line.callVolume());
        in.setAht(line.aht());
        in.setServiceLevelTarget(line.serviceLevelTarget());
        in.setServiceLevelThreshold(line.serviceLevelThreshold());
        in.setPatience(line.patience());
        in.setRetryRate(line.retryRate());
        if (adjustments != null) {
            in.setShrinkage(adjustments.shrinkage());
            in.setMaxOccupancy(adjustments.maxOccupancy());
            in.setConcurrency(adjustments.concurrency());
        }
        erlangDemandInputRepository.save(in);
    }

    /**
     * The saved inputs of the last Erlang calculation that wrote {@code businessDate}, scoped by
     * tenant and desk. Empty items when the date has no timeslots or nothing was saved.
     *
     * <p>{@link #saveRequirements} (Direct), the FTE upload and timeslot deletes deliberately do
     * not touch saved inputs: timeslot deletion is handled by the database cascade, and a Direct
     * overwrite leaves the date's last Erlang worksheet available to reload.
     */
    @Transactional(readOnly = true)
    public ErlangDemandInputResponse getErlangInputs(UUID deskId, String businessDate) {
        long tenantId = TenantContext.getTenantId();
        if (businessDate == null) {
            throw new IllegalArgumentException("businessDate is required");
        }
        LocalDate date = parseBusinessDate("businessDate", businessDate);

        List<Timeslot> slots = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndBusinessDateOrderByDateAscStartTimeAsc(
                        tenantId, deskId, date);
        if (slots.isEmpty()) {
            return new ErlangDemandInputResponse(date, List.of());
        }

        Map<UUID, Integer> slotOrder = new HashMap<>();
        Map<UUID, Timeslot> slotById = new HashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            slotOrder.put(slots.get(i).getId(), i);
            slotById.put(slots.get(i).getId(), slots.get(i));
        }

        List<ErlangDemandInputResponse.Item> items = erlangDemandInputRepository
                .findByTenantIdAndDeskIdAndTimeslotIdIn(tenantId, deskId, slotOrder.keySet()).stream()
                .sorted(Comparator
                        .comparing((ErlangDemandInput e) -> slotOrder.get(e.getTimeslotId()))
                        .thenComparing(e -> e.getSpecializationId().toString()))
                .map(e -> new ErlangDemandInputResponse.Item(
                        e.getTimeslotId(), e.getSpecializationId(),
                        slotById.get(e.getTimeslotId()).getStartTime(),
                        slotById.get(e.getTimeslotId()).getEndTime(),
                        e.getModel().name(), e.getCallVolume(), e.getAht(),
                        e.getServiceLevelTarget(), e.getServiceLevelThreshold(),
                        e.getPatience(), e.getRetryRate(), e.getShrinkage(),
                        e.getMaxOccupancy(), e.getConcurrency()))
                .toList();
        return new ErlangDemandInputResponse(date, items);
    }

    private StaffingRequirement insertRequirement(long tenantId, UUID deskId, Timeslot ts,
                                                  Specialization spec, ErlangCalculationResponse result,
                                                  StaffingSource model) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setTenantId(tenantId);
        sr.setDeskId(deskId);
        sr.setTimeslot(ts);
        sr.setSpecialization(spec);
        // scheduledAgents, not agentsRequired: the number to ROSTER, after any occupancy ceiling
        // and shrinkage. With no adjustments the two are the same.
        sr.setRequiredFTEs(result.scheduledAgents());
        sr.setSource(model);
        return staffingRequirementRepository.save(sr);
    }

    private StaffingRequirementResponse.Item toResponseItem(StaffingRequirement sr) {
        Timeslot t = sr.getTimeslot();
        Specialization s = sr.getSpecialization();
        // SOLV-07/D-10: deliberately the calendar date, not the business date. A response field
        // an operator reads answers "when does this happen", which on a re-anchored desk is a
        // calendar question -- the same deliberate display decision the timeslot labels in
        // ScheduleOutputService carry. Any future labelling change belongs to OVNT-07 (Phase 21).
        // businessDate (phase 24 D-03) is the additive key the schedule grid and allocation rows
        // join on; it sits beside date, never in place of it.
        return new StaffingRequirementResponse.Item(
                sr.getId(),
                t.getId(),
                s.getId(),
                t.getDate(),
                t.getBusinessDate(),
                t.getStartTime(),
                t.getEndTime(),
                s.getName(),
                sr.getRequiredFTEs(),
                sr.getSource().name()
        );
    }
}
