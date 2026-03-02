package com.wfm.service;

import com.wfm.config.TenantContext;
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

        // Delete existing staffing requirements then timeslots for the date range
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
