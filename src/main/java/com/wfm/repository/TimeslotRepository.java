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

    @Query(value = "SELECT MIN(t.date) as periodStart, MAX(t.date) as periodEnd, " +
                   "MIN(t.start_time) as startTime, MAX(t.end_time) as endTime, " +
                   "MIN(EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 60)::int as incrementMinutes " +
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
