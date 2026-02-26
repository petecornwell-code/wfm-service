package com.wfm.repository;

import com.wfm.model.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, UUID> {

    List<Timeslot> findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNull(long tenantId, UUID deskId);

    List<Timeslot> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);
}
