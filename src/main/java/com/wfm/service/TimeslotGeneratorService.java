package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.util.DayWindow;
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

    /** Cap on ids per delete statement, to stay well clear of the JDBC parameter limit. */
    private static final int DELETE_BATCH_SIZE = 1000;

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
        // Columns 2 and 3 are minute-of-day integers, not TIME values -- see the query's javadoc.
        // DayWindow.toLocalTime maps 1440 back to 00:00, so a desk ending at midnight reports its
        // true end rather than the 23:00 that MAX(end_time) used to return.
        return Optional.of(new TimeslotBoundsResponse(
                ((java.sql.Date) cols[0]).toLocalDate(),
                ((java.sql.Date) cols[1]).toLocalDate(),
                DayWindow.toLocalTime(((Number) cols[2]).intValue()),
                DayWindow.toLocalTime(((Number) cols[3]).intValue()),
                ((Number) cols[4]).intValue()
        ));
    }

    @Transactional
    public List<Timeslot> generateTimeslots(UUID deskId, LocalDate periodStart, LocalDate periodEnd,
                                            LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (incrementMinutes != 15 && incrementMinutes != 30 && incrementMinutes != 60) {
            throw new IllegalArgumentException("incrementMinutes must be 15, 30, or 60");
        }
        // endTime 00:00 means END OF DAY (1440), so this is DayWindow arithmetic rather than
        // startTime.until(endTime) -- the raw call returns a NEGATIVE range for any desk whose
        // day runs to midnight and rejects it as "not positive".
        long rangeMinutes = (long) DayWindow.endMinute(endTime) - DayWindow.startMinute(startTime);
        if (rangeMinutes <= 0 || rangeMinutes % incrementMinutes != 0) {
            throw new IllegalArgumentException("Time range must be positive and evenly divisible by incrementMinutes");
        }

        long tenantId = TenantContext.getTenantId();

        // Load ALL live timeslots for the desk, deliberately unbounded by date.
        // A date-bounded load can only ever see slots inside the requested period,
        // so shrinking the period used to strand every slot outside the new range
        // with no way to reach it on any later call.
        List<Timeslot> existing = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(tenantId, deskId);

        // Check if existing timeslots already match the requested parameters.
        // If so, return them as-is to preserve linked staffing requirements.
        if (timeslotsMatch(existing, periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
            return existing;
        }

        // Partition into slots that survive this generation and slots that do not.
        // The surviving set is what suppresses re-creation below; obsolete slots must
        // NOT contribute keys, or a stale slot would block its own replacement.
        Map<String, Timeslot> survivingByKey = new HashMap<>();
        List<UUID> obsoleteIds = new ArrayList<>();
        for (Timeslot ts : existing) {
            if (isDesired(ts.getDate(), ts.getStartTime(), ts.getEndTime(),
                    periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
                survivingByKey.put(slotKey(ts.getDate(), ts.getStartTime(), ts.getEndTime()), ts);
            } else {
                obsoleteIds.add(ts.getId());
            }
        }
        if (!obsoleteIds.isEmpty()) {
            // Chunked: a full quarter at 15-minute granularity is thousands of ids, and
            // a single IN clause would eventually breach the JDBC parameter limit.
            for (int i = 0; i < obsoleteIds.size(); i += DELETE_BATCH_SIZE) {
                List<UUID> batch = obsoleteIds.subList(i, Math.min(i + DELETE_BATCH_SIZE, obsoleteIds.size()));
                staffingRequirementRepository.deleteLiveByDeskAndTimeslotIds(tenantId, deskId, batch);
                timeslotRepository.deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndIdIn(tenantId, deskId, batch);
            }
            entityManager.flush();
            entityManager.clear();
        }

        // Create timeslots for slots that don't already exist
        List<Timeslot> toCreate = new ArrayList<>();
        // Iterate on minute-of-day rather than on LocalTime: the previous
        // `time.isBefore(endTime)` loop produced ZERO slots for a desk ending at midnight, and
        // LocalTime.plusMinutes would have wrapped past midnight into the next morning rather
        // than stopping. The final slot of a midnight-ending day is written with endTime 00:00.
        int firstMinute = DayWindow.startMinute(startTime);
        int lastMinute = DayWindow.endMinute(endTime);
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            for (int minute = firstMinute; minute < lastMinute; minute += incrementMinutes) {
                LocalTime slotStart = DayWindow.toLocalTime(minute);
                LocalTime slotEnd = DayWindow.toLocalTime(minute + incrementMinutes);
                String key = slotKey(date, slotStart, slotEnd);
                if (!survivingByKey.containsKey(key)) {
                    Timeslot ts = new Timeslot();
                    ts.setTenantId(tenantId);
                    ts.setDeskId(deskId);
                    ts.setDate(date);
                    ts.setStartTime(slotStart);
                    ts.setEndTime(slotEnd);
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

    /**
     * Whether an existing live timeslot is still wanted under the requested parameters.
     * Anything answering {@code false} is deleted, together with its staffing requirements.
     *
     * <p>Package-private for direct unit testing: this predicate is the whole correctness
     * story of regeneration, and it previously shipped without coverage.
     *
     * <p>The duration check is load-bearing. Judging a slot solely by its start time keeps
     * any stale slot whose start happens to land on the new grid — so refining granularity
     * (60&rarr;30, 60&rarr;15, 30&rarr;15) kept <em>every</em> old slot, and the insert loop then
     * added the correctly-sized slot alongside it. The unique index includes {@code end_time},
     * so the database accepts both and the desk ends up mixing granularities.
     */
    static boolean isDesired(LocalDate date, LocalTime slotStart, LocalTime slotEnd,
                             LocalDate periodStart, LocalDate periodEnd,
                             LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (date.isBefore(periodStart) || date.isAfter(periodEnd)) return false;
        // DayWindow throughout: `!slotStart.isBefore(endTime)` is true for EVERY slot when
        // endTime is 00:00, which marked every slot on a midnight-ending desk as obsolete.
        if (slotStart.isBefore(startTime) || !DayWindow.startsBefore(slotStart, endTime)) return false;
        long minutesFromStart = (long) DayWindow.startMinute(slotStart) - DayWindow.startMinute(startTime);
        if (minutesFromStart % incrementMinutes != 0) return false;
        return DayWindow.endMinute(slotEnd) == DayWindow.startMinute(slotStart) + incrementMinutes;
    }

    /**
     * Whether the desk's live timeslots are already exactly what these parameters describe,
     * in which case generation is a no-op and linked staffing requirements are preserved.
     *
     * <p>This is an exact check rather than boundary sampling: the expected count plus
     * "every slot is one we would have produced" is complete, given the partial unique
     * index on (tenant, desk, date, start_time, end_time) rules out duplicates. The
     * previous version inspected only the first and last rows, so corruption in the middle
     * of the period could report a match and skip the cleanup entirely.
     */
    private boolean timeslotsMatch(List<Timeslot> existing, LocalDate periodStart, LocalDate periodEnd,
                                   LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        if (existing.isEmpty()) return false;

        long days = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long slotsPerDay =
                ((long) DayWindow.endMinute(endTime) - DayWindow.startMinute(startTime)) / incrementMinutes;
        if (existing.size() != days * slotsPerDay) return false;

        for (Timeslot ts : existing) {
            if (!isDesired(ts.getDate(), ts.getStartTime(), ts.getEndTime(),
                    periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
                return false;
            }
        }
        return true;
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
