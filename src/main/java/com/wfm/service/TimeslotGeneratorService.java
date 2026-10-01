package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.util.DayWindow;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
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
import java.util.Comparator;
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
    private final DeskRepository deskRepository;
    private final EntityManager entityManager;

    public TimeslotGeneratorService(TimeslotRepository timeslotRepository,
                                    StaffingRequirementRepository staffingRequirementRepository,
                                    ScheduleRepository scheduleRepository,
                                    DeskRepository deskRepository,
                                    EntityManager entityManager) {
        this.timeslotRepository = timeslotRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.scheduleRepository = scheduleRepository;
        this.deskRepository = deskRepository;
        this.entityManager = entityManager;
    }

    /**
     * The anchor source for {@link #getLiveBounds} (BDAY-04, Rule 3 deviation -- see this plan's
     * SUMMARY): the column values that method converts back to {@link LocalTime} are genuinely
     * minute-of-day, not day-start-relative offsets, but the conversion must still go through a
     * bound window rather than a midnight literal so a future non-{@code 00:00} desk is not
     * silently misreported. Throws on a missing desk, matching {@code ShiftTemplateService
     * .dayWindowFor}'s convention for a class whose other methods also throw on a missing desk.
     */
    private DayWindow dayWindowFor(UUID deskId) {
        Desk desk = deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        return DayWindow.anchoredAt(desk.getDayStart());
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
        // window.anchoredToLocalTime maps 1440 back to 00:00, so a desk ending at midnight reports
        // its true end rather than the 23:00 that MAX(end_time) used to return.
        DayWindow window = dayWindowFor(deskId);
        return Optional.of(new TimeslotBoundsResponse(
                ((java.sql.Date) cols[0]).toLocalDate(),
                ((java.sql.Date) cols[1]).toLocalDate(),
                window.anchoredToLocalTime(((Number) cols[2]).intValue()),
                window.anchoredToLocalTime(((Number) cols[3]).intValue()),
                ((Number) cols[4]).intValue()
        ));
    }

    @Transactional
    public List<Timeslot> generateTimeslots(UUID deskId, LocalDate periodStart, LocalDate periodEnd,
                                            LocalTime dayStart, LocalTime startTime, LocalTime endTime,
                                            int incrementMinutes) {
        if (incrementMinutes != 15 && incrementMinutes != 30 && incrementMinutes != 60) {
            throw new IllegalArgumentException("incrementMinutes must be 15, 30, or 60");
        }
        // BDAY-03: the refusal fires before TenantContext.getTenantId() and before any repository
        // call -- late but loud at the generation boundary, the same rule DayWindow itself
        // follows. The increment is not desk state; it arrives per call, inferred from the
        // uploaded spreadsheet's own columns, so this cannot be validated any earlier than here.
        requireDayStartTiles(dayStart, incrementMinutes);

        // endTime 00:00 means END OF the dayStart-anchored business day, so this is DayWindow
        // arithmetic anchored at dayStart rather than startTime.until(endTime) -- the raw call
        // returns a NEGATIVE range for any desk whose business day runs to its own anchor.
        long rangeMinutes = (long) DayWindow.endMinuteFromDayStart(dayStart, endTime)
                - DayWindow.startMinuteFromDayStart(dayStart, startTime);
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
        if (timeslotsMatch(dayStart, existing, periodStart, periodEnd, startTime, endTime, incrementMinutes)) {
            return existing;
        }

        // Partition into slots that survive this generation and slots that do not.
        // The surviving set is what suppresses re-creation below; obsolete slots must
        // NOT contribute keys, or a stale slot would block its own replacement.
        Map<String, Timeslot> survivingByKey = new HashMap<>();
        List<UUID> obsoleteIds = new ArrayList<>();
        for (Timeslot ts : existing) {
            if (isDesired(dayStart, ts.getDate(), ts.getStartTime(), ts.getEndTime(),
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

        // Create timeslots for slots that don't already exist. BDAY-03: periodStart and
        // periodEnd are BUSINESS dates now, so the outer cursor walks business days and each
        // slot's calendar date and times are DERIVED from the anchor plus the slot's offset --
        // never computed with local minute arithmetic, so an anchor-crossing bug cannot hide
        // outside DayWindow.
        List<Timeslot> toCreate = new ArrayList<>();
        int firstOffset = DayWindow.startMinuteFromDayStart(dayStart, startTime);
        int lastOffset = DayWindow.endMinuteFromDayStart(dayStart, endTime);
        for (LocalDate businessDate = periodStart; !businessDate.isAfter(periodEnd);
                businessDate = businessDate.plusDays(1)) {
            for (int offset = firstOffset; offset < lastOffset; offset += incrementMinutes) {
                LocalDate slotDate = DayWindow.calendarDateAtDayStartOffset(dayStart, businessDate, offset);
                LocalTime slotStart = DayWindow.timeAtDayStartOffset(dayStart, offset);
                LocalTime slotEnd = DayWindow.timeAtDayStartOffset(dayStart, offset + incrementMinutes);
                String key = slotKey(slotDate, slotStart, slotEnd);
                if (!survivingByKey.containsKey(key)) {
                    Timeslot ts = new Timeslot();
                    ts.setTenantId(tenantId);
                    ts.setDeskId(deskId);
                    ts.setDate(slotDate);
                    ts.setStartTime(slotStart);
                    ts.setEndTime(slotEnd);
                    // BDAY-02/BDAY-03: business_date is the BUSINESS-day cursor itself, not the
                    // derived calendar date -- the two diverge whenever dayStart is not 00:00. A
                    // 21:00-anchored desk's post-midnight rows carry the FOLLOWING calendar date
                    // but the ORIGINAL business date. This generator remains the sole deriving
                    // writer of business_date (BDAY-08); it never reads the column back.
                    ts.setBusinessDate(businessDate);
                    toCreate.add(ts);
                }
            }
        }
        if (!toCreate.isEmpty()) {
            timeslotRepository.saveAll(toCreate);
        }

        // BDAY-03: the closing read-back's bounds are business dates, not calendar dates.
        // A 21:00-anchored desk's last business day writes rows on calendar periodEnd + 1, so
        // the fetch widens its calendar upper bound by one day and then filters and orders on
        // the DERIVED business date -- never the stored column -- so a calendar-bounded
        // fetch cannot silently under-return the post-midnight half of the last business day.
        // At a 00:00 anchor the widened fetch's extra rows are all removed by the filter, so the
        // returned list and its order are unchanged.
        List<Timeslot> readBack = timeslotRepository
                .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                        tenantId, deskId, periodStart, periodEnd.plusDays(1));
        return readBack.stream()
                .filter(ts -> {
                    LocalDate businessDate = DayWindow.businessDateOf(dayStart, ts.getDate(), ts.getStartTime());
                    return !businessDate.isBefore(periodStart) && !businessDate.isAfter(periodEnd);
                })
                .sorted(Comparator
                        .comparing((Timeslot ts) -> DayWindow.businessDateOf(dayStart, ts.getDate(), ts.getStartTime()))
                        .thenComparingInt(ts -> DayWindow.startMinuteFromDayStart(dayStart, ts.getStartTime())))
                .toList();
    }

    /**
     * BDAY-03: whether {@code dayStart} is a whole multiple of {@code incrementMinutes} -- the
     * necessary condition for a desk's day-start to tile the generation grid without leaving a
     * fractional slot. Package-private static, the same shape as {@link #isDesired}, so a plain
     * unit test can call it directly with an arbitrary day-start.
     *
     * @throws IllegalArgumentException naming the day-start, the increment, and why the two
     *         cannot tile a day, or when {@code dayStart} is null.
     */
    static void requireDayStartTiles(LocalTime dayStart, int incrementMinutes) {
        if (dayStart == null) {
            throw new IllegalArgumentException(
                    "Day start is required to check tiling against the generation increment");
        }
        // This reads dayStart's OWN minute-of-day from midnight -- not a day-start-relative
        // offset, since the anchor IS the reference point here, not a scheduling time measured
        // against it. Binding a window at dayStart and calling its anchored accessor would always
        // read back zero (an anchor measured against itself), so this stays plain LocalTime
        // arithmetic rather than routing through DayWindow -- the one call in this plan's eight
        // files where the anchored instance API does not apply (see this plan's SUMMARY).
        int dayStartMinuteOfDay = dayStart.getHour() * 60 + dayStart.getMinute();
        if (dayStartMinuteOfDay % incrementMinutes != 0) {
            throw new IllegalArgumentException(
                    "Desk day-start " + dayStart + " is not a whole multiple of the "
                            + incrementMinutes + "-minute generation increment and cannot tile a day");
        }
    }

    // BDAY-02: slotKey stays keyed on the CALENDAR date, never the business date, because it
    // mirrors the database's partial unique index on (tenant, desk, date, start_time, end_time).
    // The survivor map above and the newly-generated keys here are built the same way, so a
    // stale slot can never block its own replacement -- the business date is not part of a
    // row's identity.
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
    static boolean isDesired(LocalTime dayStart, LocalDate date, LocalTime slotStart, LocalTime slotEnd,
                             LocalDate periodStart, LocalDate periodEnd,
                             LocalTime startTime, LocalTime endTime, int incrementMinutes) {
        // BDAY-03: classify the row's business date by DERIVING it from the row's own calendar
        // date and start time -- never by reading the stored business_date column, so
        // SOLV-01 remains the first consumer of that stored value.
        LocalDate businessDate = DayWindow.businessDateOf(dayStart, date, slotStart);
        if (businessDate.isBefore(periodStart) || businessDate.isAfter(periodEnd)) return false;

        // Anchored offset comparisons throughout: at a 00:00 anchor these agree exactly with the
        // raw LocalTime comparisons they replace, including the rule that a slot ending exactly
        // at the anchor (00:00 at a 00:00 anchor) still belongs to the business day it closes.
        int slotStartOffset = DayWindow.startMinuteFromDayStart(dayStart, slotStart);
        int windowStartOffset = DayWindow.startMinuteFromDayStart(dayStart, startTime);
        int windowEndOffset = DayWindow.endMinuteFromDayStart(dayStart, endTime);
        if (slotStartOffset < windowStartOffset || slotStartOffset >= windowEndOffset) return false;

        long minutesFromStart = (long) slotStartOffset - windowStartOffset;
        if (minutesFromStart % incrementMinutes != 0) return false;

        int slotEndOffset = DayWindow.endMinuteFromDayStart(dayStart, slotEnd);
        return slotEndOffset == slotStartOffset + incrementMinutes;
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
    private boolean timeslotsMatch(LocalTime dayStart, List<Timeslot> existing, LocalDate periodStart,
                                   LocalDate periodEnd, LocalTime startTime, LocalTime endTime,
                                   int incrementMinutes) {
        if (existing.isEmpty()) return false;

        // periodStart/periodEnd are business days now (BDAY-03), so this is a span of
        // business days -- which is what it should always have been counting.
        long days = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long slotsPerDay = ((long) DayWindow.endMinuteFromDayStart(dayStart, endTime)
                - DayWindow.startMinuteFromDayStart(dayStart, startTime)) / incrementMinutes;
        if (existing.size() != days * slotsPerDay) return false;

        for (Timeslot ts : existing) {
            if (!isDesired(dayStart, ts.getDate(), ts.getStartTime(), ts.getEndTime(),
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
