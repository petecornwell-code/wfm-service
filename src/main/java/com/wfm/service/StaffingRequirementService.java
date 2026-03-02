package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.*;
import java.math.BigDecimal;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.CursorPagination;
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
    private final ErlangXService erlangXService;

    public StaffingRequirementService(StaffingRequirementRepository staffingRequirementRepository,
                                      TimeslotRepository timeslotRepository,
                                      SpecializationRepository specializationRepository,
                                      ErlangXService erlangXService) {
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotRepository = timeslotRepository;
        this.specializationRepository = specializationRepository;
        this.erlangXService = erlangXService;
    }

    public PaginatedResponse<StaffingRequirementResponse.Item> listRequirements(
            UUID deskId, String from, String to, String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clampedLimit = CursorPagination.clampLimit(limit);
        Pageable pageable = PageRequest.of(0, clampedLimit + 1);

        Map<String, String> cursorValues = CursorPagination.decode(cursor);
        boolean hasCursor = !cursorValues.isEmpty();
        boolean hasDateRange = from != null && to != null;

        List<StaffingRequirement> results;

        if (hasDateRange && hasCursor) {
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

        // Derive the replacement date range from the timeslots in the payload
        LocalDate minDate = timeslotMap.values().stream()
                .map(Timeslot::getDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxDate = timeslotMap.values().stream()
                .map(Timeslot::getDate).max(LocalDate::compareTo).orElseThrow();

        // Delete existing live requirements in this date range
        staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, minDate, maxDate);

        // Insert new requirements
        List<StaffingRequirement> saved = new ArrayList<>();
        for (StaffingRequirementRequest.Item item : request.requirements()) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setTenantId(tenantId);
            sr.setDeskId(deskId);
            sr.setTimeslot(timeslotMap.get(item.timeslotId()));
            sr.setSpecialization(specMap.get(item.specializationId()));
            sr.setRequiredHours(item.requiredHours());
            sr.setSource(StaffingSource.DIRECT);
            saved.add(staffingRequirementRepository.save(sr));
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
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

        // Delete existing live requirements in the specified date range
        staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, from, to);

        // Calculate and persist
        List<StaffingRequirement> saved = new ArrayList<>();
        for (ErlangXRequest.Item item : request.parameters()) {
            int requiredAgents = erlangXService.calculateRequiredAgents(
                    item.callVolume(), item.aht(), item.patience(),
                    item.retryRate(), item.serviceLevelTarget(), item.serviceLevelThreshold());

            // Convert agent count to hours: hours = agents × timeslotDuration(hours)
            Timeslot ts = timeslotMap.get(item.timeslotId());
            long slotMinutes = java.time.temporal.ChronoUnit.MINUTES.between(ts.getStartTime(), ts.getEndTime());
            BigDecimal requiredHours = BigDecimal.valueOf(requiredAgents)
                    .multiply(BigDecimal.valueOf(slotMinutes))
                    .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP);

            StaffingRequirement sr = new StaffingRequirement();
            sr.setTenantId(tenantId);
            sr.setDeskId(deskId);
            sr.setTimeslot(ts);
            sr.setSpecialization(specMap.get(item.specializationId()));
            sr.setRequiredHours(requiredHours);
            sr.setSource(StaffingSource.ERLANG_X);
            saved.add(staffingRequirementRepository.save(sr));
        }

        return new StaffingRequirementResponse(saved.stream().map(this::toResponseItem).toList());
    }

    private StaffingRequirementResponse.Item toResponseItem(StaffingRequirement sr) {
        Timeslot t = sr.getTimeslot();
        Specialization s = sr.getSpecialization();
        return new StaffingRequirementResponse.Item(
                sr.getId(),
                t.getId(),
                s.getId(),
                t.getDate(),
                t.getStartTime(),
                t.getEndTime(),
                s.getName(),
                sr.getRequiredHours(),
                sr.getSource().name()
        );
    }
}
