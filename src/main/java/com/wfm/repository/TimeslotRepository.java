package com.wfm.repository;

import com.wfm.model.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, UUID> {

    List<Timeslot> findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    /**
     * All live (non-snapshot) timeslots for a desk, unbounded by date.
     * Regeneration needs this: a date-bounded query cannot see slots that fall
     * outside a newly-shortened period, so those slots could never be cleaned up.
     */
    List<Timeslot> findByTenantIdAndDeskIdAndScheduleIdIsNullOrderByDateAscStartTimeAsc(
            long tenantId, UUID deskId);

    /**
     * Desk bounds as MINUTE-OF-DAY integers, not as TIME values, because a desk whose day runs to
     * midnight stores its final slot as 23:00-00:00 and {@code 00:00} is the SMALLEST value a SQL
     * TIME can hold. The previous form of this query returned two wrong answers on such a desk:
     * {@code MAX(end_time)} reported 23:00 rather than midnight, and
     * {@code MIN(end_time - start_time)} reported -1380 minutes for that slot, which became the
     * desk's increment. The CASE maps a midnight end to 1440; callers convert back through
     * {@code DayWindow.toLocalTime}.
     */
    @Query(value = "SELECT MIN(t.date) as periodStart, MAX(t.date) as periodEnd, " +
                   "MIN((EXTRACT(EPOCH FROM t.start_time) / 60)::int) as startMinute, " +
                   "MAX((CASE WHEN t.end_time = TIME '00:00:00' THEN 1440 " +
                   "          ELSE EXTRACT(EPOCH FROM t.end_time) / 60 END)::int) as endMinute, " +
                   "MIN((CASE WHEN t.end_time = TIME '00:00:00' THEN 1440 " +
                   "          ELSE EXTRACT(EPOCH FROM t.end_time) / 60 END)::int " +
                   "    - (EXTRACT(EPOCH FROM t.start_time) / 60)::int) as incrementMinutes " +
                   "FROM timeslot t WHERE t.tenant_id = :tenantId AND t.desk_id = :deskId AND t.schedule_id IS NULL",
           nativeQuery = true)
    Object[] findLiveBoundsByDeskRaw(long tenantId, UUID deskId);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM Timeslot t WHERE t.tenantId = :tenantId AND t.deskId = :deskId AND t.scheduleId IS NULL AND t.id IN :ids")
    void deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndIdIn(long tenantId, UUID deskId, Collection<UUID> ids);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNull(long tenantId, UUID deskId);

    List<Timeslot> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
