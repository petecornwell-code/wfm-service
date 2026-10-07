package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.*;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
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

    public StaffingRequirementService(StaffingRequirementRepository staffingRequirementRepository,
                                      TimeslotRepository timeslotRepository,
                                      SpecializationRepository specializationRepository,
                                      ErlangCalculatorService erlangCalculatorService,
                                      DeskRepository deskRepository,
                                      EntityManager entityManager) {
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotRepository = timeslotRepository;
        this.specializationRepository = specializationRepository;
        this.erlangCalculatorService = erlangCalculatorService;
        this.deskRepository = deskRepository;
        this.entityManager = entityManager;
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

        List<StaffingRequirement> results;

        // D-04: a business-date range filters the STORED business_date column and pages on the
        // same calendar keyset (date, startTime, specialization name, id) as the calendar range,
        // so a cursor minted by either resumes in either.
        if (hasBusinessRange && hasCursor) {
            results = staffingRequirementRepository.findLiveByDeskAndBusinessDateRangeAfterCursor(
                    tenantId, deskId, LocalDate.parse(businessFrom), LocalDate.parse(businessTo),
                    LocalDate.parse(cursorValues.get("date")),
                    LocalTime.parse(cursorValues.get("startTime")),
                    cursorValues.get("specName"),
                    UUID.fromString(cursorValues.get("id")),
                    pageable);
        } else if (hasBusinessRange) {
            results = staffingRequirementRepository.findLiveByDeskAndBusinessDateRange(
                    tenantId, deskId, LocalDate.parse(businessFrom), LocalDate.parse(businessTo),
                    pageable);
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
     * Erlang C over the same grid as {@link #calculateErlangX}, and with the same consequence: the
     * live requirements for {@code [from, to]} are REPLACED, including rows for timeslots this
     * request never mentions.
     *
     * <p>Both modes delegate to {@link ErlangCalculatorService}, the same service behind the
     * read-only calculator page, so what this button writes is exactly what that page previews.
     * Erlang C is the conservative baseline and will usually ask for MORE agents than Erlang X on
     * the same inputs, because it assumes nobody ever hangs up.
     */
    @Transactional
    public StaffingRequirementResponse calculateErlangC(UUID deskId, ErlangCRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.parameters() == null || request.parameters().isEmpty()) {
            return new StaffingRequirementResponse(List.of());
        }

        LocalDate from = request.from();
        LocalDate to = request.to();

        Map<UUID, Timeslot> timeslotMap = new HashMap<>();
        Map<UUID, Specialization> specMap = new HashMap<>();

        for (ErlangCRequest.Item item : request.parameters()) {
            requirePercentageTarget(item.serviceLevelTarget());
            loadTimeslot(timeslotMap, item.timeslotId(), tenantId, deskId);
            loadSpecialization(specMap, item.specializationId(), tenantId, deskId);
        }

        // OVNT-02 (migrate-now): the inserts below are keyed by explicit timeslotId while this
        // clear is by range, so the clear must use the SAME date system the rows are attributed
        // under -- the business date, not the calendar date -- or the two disagree on a desk
        // whose day start is not midnight (a post-midnight slot of business day D carries
        // calendar date D+1, so a calendar-scoped clear of [D, D] would miss it and a
        // calendar-scoped clear of [D, D] would also reach the prior business day's tail on
        // calendar date D). The demand-save path above already uses the business-date twin for
        // exactly this reason; this call now matches it.
        staffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange(tenantId, deskId, from, to);

        // Same flush-before-insert reason as calculateErlangX: Hibernate's ActionQueue would
        // otherwise run the inserts first and hit the unique constraint on the live index.
        entityManager.flush();
        entityManager.clear();

        DayWindow window = dayWindowFor(deskId, tenantId);
        List<StaffingRequirement> saved = new ArrayList<>();
        for (ErlangCRequest.Item item : request.parameters()) {
            Timeslot ts = timeslotMap.get(item.timeslotId());
            int intervalMinutes = intervalMinutes(window, ts.getStartTime(), ts.getEndTime());

            // Delegated to the same service the read-only calculator calls, so what this button
            // writes is exactly what that page previews. The DTO speaks percentages to match this
            // screen; the calculator takes fractions.
            ErlangCalculationResponse result = erlangCalculatorService.calculateErlangC(
                    new ErlangCCalculationRequest(
                            item.callVolume(), intervalMinutes, item.aht(),
                            item.serviceLevelTarget() / 100.0, item.serviceLevelThreshold(),
                            request.adjustments()));

            StaffingRequirement sr = new StaffingRequirement();
            sr.setTenantId(tenantId);
            sr.setDeskId(deskId);
            sr.setTimeslot(ts);
            sr.setSpecialization(specMap.get(item.specializationId()));
            // scheduledAgents, not agentsRequired: the number to ROSTER, after any occupancy
            // ceiling and shrinkage. With no adjustments the two are the same.
            sr.setRequiredFTEs(result.scheduledAgents());
            sr.setSource(StaffingSource.ERLANG_C);
            saved.add(staffingRequirementRepository.save(sr));
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
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

    @Transactional
    public StaffingRequirementResponse calculateErlangX(UUID deskId, ErlangXRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.parameters() == null || request.parameters().isEmpty()) {
            return new StaffingRequirementResponse(List.of());
        }

        LocalDate from = request.from();
        LocalDate to = request.to();

        // Load all referenced timeslots and specializations
        Map<UUID, Timeslot> timeslotMap = new HashMap<>();
        Map<UUID, Specialization> specMap = new HashMap<>();

        for (ErlangXRequest.Item item : request.parameters()) {
            requirePercentageTarget(item.serviceLevelTarget());
            loadTimeslot(timeslotMap, item.timeslotId(), tenantId, deskId);
            loadSpecialization(specMap, item.specializationId(), tenantId, deskId);
        }

        // OVNT-02 (migrate-now): delete existing live requirements in the specified business-date
        // range -- the inserts below are keyed by explicit timeslotId while this clear is by
        // range, so the clear must use the SAME date system the rows are attributed under, or the
        // two disagree on a desk whose day start is not midnight. See calculateErlangC's call site
        // for the full reasoning; the two calculators must not drift apart on this.
        staffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange(tenantId, deskId, from, to);

        // Flush deletes to DB before inserting new rows — Hibernate's ActionQueue
        // processes inserts before deletes in the same flush, which would hit the
        // unique constraint on idx_staffing_requirement_live.
        entityManager.flush();
        entityManager.clear();

        // Calculate and persist
        DayWindow window = dayWindowFor(deskId, tenantId);
        List<StaffingRequirement> saved = new ArrayList<>();
        for (ErlangXRequest.Item item : request.parameters()) {
            Timeslot ts = timeslotMap.get(item.timeslotId());

            // The interval the volume belongs to comes from the timeslot itself rather than from
            // the request, so the figure the operator typed against a row is converted on that
            // row's own length. DayWindow because a slot ending at 00:00 ends the day -- a raw
            // Duration.between would make the last slot of a midnight desk negative.
            int intervalMinutes = intervalMinutes(window, ts.getStartTime(), ts.getEndTime());

            ErlangCalculationResponse result = erlangCalculatorService.calculateErlangX(
                    new ErlangXCalculationRequest(
                            item.callVolume(), intervalMinutes, item.aht(), item.patience(),
                            item.retryRate() / 100.0, item.serviceLevelTarget() / 100.0,
                            item.serviceLevelThreshold(), false, request.adjustments()));

            StaffingRequirement sr = new StaffingRequirement();
            sr.setTenantId(tenantId);
            sr.setDeskId(deskId);
            sr.setTimeslot(ts);
            sr.setSpecialization(specMap.get(item.specializationId()));
            sr.setRequiredFTEs(result.scheduledAgents());
            sr.setSource(StaffingSource.ERLANG_X);
            saved.add(staffingRequirementRepository.save(sr));
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
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
