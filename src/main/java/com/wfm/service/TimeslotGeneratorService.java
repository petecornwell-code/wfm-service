package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.Timeslot;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TimeslotGeneratorService {

    private final TimeslotRepository timeslotRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final ScheduleRepository scheduleRepository;
    private final EntityManager entityManager;

    public TimeslotGeneratorService(TimeslotRepository timeslotRepository,
                                    StaffingRequirementRepository staffingRequirementRepository,
                                    ScheduleRepository scheduleRepository,
                                    EntityManager entityManager) {
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.scheduleRepository = scheduleRepository;
        this.entityManager = entityManager;
    }

    public List<Timeslot> listTimeslots(UUID deskId, LocalDate from, LocalDate to) {
        return timeslotRepository.findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                TenantContext.getTenantId(), deskId, from, to);
    }

    public Optional<TimeslotBoundsResponse> getLiveBounds(UUID deskId) {
        Object[] row = timeslotRepository.findLiveBoundsByDeskRaw(TenantContext.getTenantId(), deskId);
        if (row == null || row.length == 0) return Optional.empty();
        // Native query returns a single row; columns may be null if no timeslots exist
        Object[] cols = (row[0] instanceof Object[]) ? (Object[]) row[0] : row;
        if (cols[0] == null) return Optional.empty();
        return Optional.of(new TimeslotBoundsResponse(
                ((java.sql.Date) cols[0]).toLocalDate(),
                ((java.sql.Date) cols[1]).toLocalDate(),
                ((java.sql.Time) cols[2]).toLocalTime(),
                ((java.sql.Time) cols[3]).toLocalTime(),
                ((Number) cols[4]).intValue()
        ));
    }

    @Transactional
    public List<Timeslot> generateTimeslots(UUID deskId, LocalDate periodStart, LocalDate periodEnd,
                                            LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (incrementMinutes != 15 && incrementMinutes != 30 && incrementMinutes != 60) {
            throw new IllegalArgumentException("incrementMinutes must be 15, 30, or 60");
        }
        long rangeMinutes = startTime.until(endTime, ChronoUnit.MINUTES);
        if (rangeMinutes <= 0 || rangeMinutes % incrementMinutes != 0) {
            throw new IllegalArgumentException("Time range must be positive and evenly divisible by incrementMinutes");
        }

        long tenantId = TenantContext.getTenantId();

        // Check if existing timeslots already match the requested parameters.
        // If so, return them as-is to preserve linked staffing requirements.
        List<Timeslot> existing = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, periodStart, periodEnd);

        if (timeslotsMatch(existing, periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
            return existing;
        }

        // Build lookup of existing timeslots keyed by "date|startTime|endTime"
        Map<String, Timeslot> existingByKey = new HashMap<>();
        for (Timeslot ts : existing) {
            existingByKey.put(slotKey(ts.getDate(), ts.getStartTime(), ts.getEndTime()), ts);
        }

        // Determine which existing timeslots are no longer needed and remove them
        // (along with their staffing requirements) without touching valid ones.
        List<UUID> obsoleteIds = new ArrayList<>();
        for (Timeslot ts : existing) {
            if (!isDesired(ts.getDate(), ts.getStartTime(), periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
                obsoleteIds.add(ts.getId());
            }
        }
        if (!obsoleteIds.isEmpty()) {
            staffingRequirementRepository.deleteLiveByDeskAndTimeslotIds(tenantId, deskId, obsoleteIds);
            timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndIdIn(tenantId, deskId, obsoleteIds);
            entityManager.flush();
            entityManager.clear();
        }

        // Create timeslots for slots that don't already exist
        List<Timeslot> toCreate = new ArrayList<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            for (LocalTime time = startTime; time.isBefore(endTime); time = time.plusMinutes(incrementMinutes)) {
                String key = slotKey(date, time, time.plusMinutes(incrementMinutes));
                if (!existingByKey.containsKey(key)) {
                    Timeslot ts = new Timeslot();
                    ts.setTenantId(tenantId);
                    ts.setDeskId(deskId);
                    ts.setDate(date);
                    ts.setStartTime(time);
                    ts.setEndTime(time.plusMinutes(incrementMinutes));
                    toCreate.add(ts);
                }
            }
        }
        if (!toCreate.isEmpty()) {
            timeslotRepository.saveAll(toCreate);
        }

        return timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, periodStart, periodEnd);
    }

    private static String slotKey(LocalDate date, LocalTime start, LocalTime end) {
        return date + "|" + start + "|" + end;
    }

    private static boolean isDesired(LocalDate date, LocalTime slotStart,
                                     LocalDate periodStart, LocalDate periodEnd,
                                     LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (date.isBefore(periodStart) || date.isAfter(periodEnd)) return false;
        if (slotStart.isBefore(startTime) || !slotStart.isBefore(endTime)) return false;
        long minutesFromStart = startTime.until(slotStart, ChronoUnit.MINUTES);
        return minutesFromStart % incrementMinutes == 0;
    }

    /**
     * Check if existing timeslots exactly match the requested generation parameters.
     * Verifies date coverage, start/end times, and increment are all consistent.
     */
    private boolean timeslotsMatch(List<Timeslot> existing, LocalDate periodStart, LocalDate periodEnd,
                                   LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (existing.isEmpty()) return false;

        // Calculate expected count: days × slots-per-day
        long days = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long slotsPerDay = startTime.until(endTime, ChronoUnit.MINUTES) / incrementMinutes;
        long expectedCount = days * slotsPerDay;

        if (existing.size() != expectedCount) return false;

        // Verify first and last timeslots match the expected boundaries
        Timeslot first = existing.get(0);
        Timeslot last = existing.get(existing.size() - 1);

        if (!first.getDate().equals(periodStart)) return false;
        if (!first.getStartTime().equals(startTime)) return false;
        if (!last.getDate().equals(periodEnd)) return false;
        if (!last.getEndTime().equals(endTime)) return false;

        // Verify increment by checking first timeslot's duration
        long actualIncrement = first.getStartTime().until(first.getEndTime(), ChronoUnit.MINUTES);
        return actualIncrement == incrementMinutes;
    }

    @Transactional
    public void deleteTimeslots(UUID deskId, LocalDate from, LocalDate to) {
        long tenantId = TenantContext.getTenantId();

        // Check for accepted schedules overlapping the date range
        boolean hasAccepted = scheduleRepository.findOverlapping(tenantId, deskId, from, to)
                .stream().anyMatch(s -> s.getStatus() == ScheduleStatus.ACCEPTED);
        if (hasAccepted) {
            throw new ConflictException("Cannot delete timeslots referenced by an accepted schedule");
        }

        // Delete staffing requirements first (FK to timeslot), then timeslots
        staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, from, to);
        timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetween(tenantId, deskId, from, to);
    }
}
