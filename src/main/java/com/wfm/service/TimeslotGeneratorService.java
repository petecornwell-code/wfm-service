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
import java.util.List;
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

        // Parameters changed or no timeslots exist — delete and recreate
        staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, periodStart, periodEnd);
        timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetween(
                tenantId, deskId, periodStart, periodEnd);

        // Flush deletes to DB before inserting new rows — Hibernate's ActionQueue
        // processes inserts before deletes in the same flush, which would hit the
        // unique constraint on (tenant_id, desk_id, date, start_time, end_time).
        entityManager.flush();
        entityManager.clear();

        // Generate one timeslot per increment per day
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            for (LocalTime time = startTime; time.isBefore(endTime); time = time.plusMinutes(incrementMinutes)) {
                Timeslot ts = new Timeslot();
                ts.setTenantId(tenantId);
                ts.setDeskId(deskId);
                ts.setDate(date);
                ts.setStartTime(time);
                ts.setEndTime(time.plusMinutes(incrementMinutes));
                timeslots.add(ts);
            }
        }

        return timeslotRepository.saveAll(timeslots);
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
